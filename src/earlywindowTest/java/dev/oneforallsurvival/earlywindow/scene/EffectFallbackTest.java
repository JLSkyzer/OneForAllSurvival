package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.earlywindow.scene.effects.EffectChain;
import dev.oneforallsurvival.earlywindow.scene.effects.SceneEffect;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Repli effet par effet, sans contexte GL : la compilation des shaders est simulée par un {@link ShaderLoader}. */
class EffectFallbackTest {
    // Gradle lance les tests depuis la racine du projet ; les ressources du jar de service n'y sont pas copiées.
    private static final Path SHADERS = Path.of("src/earlywindow/resources/lostisland/shaders");

    /**
     * Pilote simulé : lit la vraie source GLSL (elle doit exister), remplace celle de {@code broken} par une
     * source fautive, et « compile » en refusant toute source sans {@code main}, comme le ferait le pilote.
     */
    private static final class FakeDriver implements ShaderLoader {
        final String broken;
        final List<String> compiled = new ArrayList<>();
        int next = 1;

        FakeDriver(String broken) {
            this.broken = broken;
        }

        @Override
        public int load(String vertex, String fragment) {
            String vert = read(vertex + ".vert");
            String frag = fragment.equals(broken) ? "#version 150\nout vec4 fragColor;\nvoid mian() {}\n"
                    : read(fragment + ".frag");
            for (String src : new String[] {vert, frag}) {
                if (!src.contains("void main()")) {
                    throw new ShaderException("0:3(1): error: function `main' not defined (" + fragment + ")");
                }
            }
            compiled.add(fragment);
            return next++;
        }

        private static String read(String name) {
            try {
                return Files.readString(SHADERS.resolve(name), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("source GLSL absente : " + name, e);
            }
        }
    }

    private static SceneManifest manifest() {
        return new SceneManifest(1920, 1080, 2112, 1188, new SceneManifest.Sea(48, 2, 163),
                new float[] {0.5f, 0.4f}, new float[] {0.6f, 0.2f},
                List.of(new SceneManifest.Fire(950, 900, 18, 3, 1)),
                List.of(new SceneManifest.Plume(831, 824, 7, 6)),
                List.of(new SceneManifest.Camp(548, 744, 0.42f)),
                new float[] {1250, 463}, new float[] {957, 863},
                new SceneManifest.Logo(List.of(), 593, 298, 60));
    }

    @Test
    void unShaderRefuseRetireSeulementLEffet() {
        SceneManifest m = manifest();
        TextureStore textures = new TextureStore(m, path -> null); // jamais démarré : aucun appel GL
        FakeDriver driver = new FakeDriver("smoke");
        List<String> log = new ArrayList<>();

        SceneRenderer renderer = new SceneRenderer(m, textures, driver, log::add);

        assertEquals(List.of("halo", "fire", "embers"), renderer.activeEffects());
        assertEquals(1, log.size(), "un seul effet retiré, journalisé une fois : " + log);
        assertTrue(log.get(0).contains("smoke"), log.get(0));
        // Les shaders de base et ceux des autres effets ont bien été « compilés » depuis leurs vraies sources.
        assertTrue(driver.compiled.containsAll(List.of("layer", "present", "halo", "fire", "fireglow", "ember")),
                driver.compiled.toString());
    }

    @Test
    void sansRefusTousLesEffetsSontActifs() {
        SceneManifest m = manifest();
        SceneRenderer renderer = new SceneRenderer(m, new TextureStore(m, path -> null), new FakeDriver(""),
                line -> { });
        assertEquals(List.of("halo", "smoke", "fire", "embers"), renderer.activeEffects());
    }

    /** Effet de test : lève à son n-ième rendu. */
    private static final class Fragile implements SceneEffect {
        final String name;
        final int failAt;
        int renders;
        int closes;

        Fragile(String name, int failAt) {
            this.name = name;
            this.failAt = failAt;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void init(TextureStore t, ShaderLoader s) {
        }

        @Override
        public void render(SceneState s, View v) {
            if (++renders == failAt) {
                throw new IllegalStateException("texture absente : fx/smoke_3.png");
            }
        }

        @Override
        public void close() {
            closes++;
        }
    }

    @Test
    void unRenduQuiLeveRetireLEffetEtLeJournaliseUneFois() {
        Fragile a = new Fragile("halo", -1);
        Fragile b = new Fragile("smoke", 2);
        Fragile c = new Fragile("fire", -1);
        List<String> log = new ArrayList<>();
        EffectChain chain = new EffectChain(List.of(a, b, c), log::add);
        chain.init(null, null);
        SceneState state = new SceneState(12f, 0.5f, null, 1f, 12.0);
        for (int i = 0; i < 5; i++) {
            chain.render(state, null);
        }
        assertEquals(List.of("halo", "fire"), chain.active());
        assertEquals(5, a.renders);
        assertEquals(5, c.renders, "l'effet suivant est dessiné même à l'image où le précédent a levé");
        assertEquals(2, b.renders, "un effet retiré n'est plus appelé");
        assertEquals(1, b.closes, "un effet retiré est libéré");
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.get(0).contains("smoke") && log.get(0).contains("smoke_3.png"), log.get(0));
    }
}
