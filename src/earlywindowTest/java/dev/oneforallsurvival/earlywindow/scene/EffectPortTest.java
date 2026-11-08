package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.oneforallsurvival.earlywindow.scene.effects.EffectMath;
import dev.oneforallsurvival.earlywindow.scene.effects.EmberEffect;
import dev.oneforallsurvival.earlywindow.scene.effects.FireEffect;
import dev.oneforallsurvival.earlywindow.scene.effects.SmokeEffect;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Le port des effets contre compose.py. Valeurs attendues relevées en exécutant le vrai {@code draw_smoke},
 * {@code draw_fires} et {@code draw_embers} de compose.py (blit_over et resized remplacés par des enregistreurs),
 * avec le vrai manifeste, à t = 12 et t = 22 (temps réel = temps du récit, comme la vidéo).
 */
class EffectPortTest {
    private static final Path MANIFESTE = Path.of("art/loading-screen/assets/manifest.json");
    private static final float[] EMBER = Ambiance.rgb("#ff8a2c");

    private static SceneManifest manifest() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(MANIFESTE), "manifest.json absent : test ignore");
        try (Reader r = Files.newBufferedReader(MANIFESTE, StandardCharsets.UTF_8)) {
            return SceneManifest.parse(r);
        }
    }

    @Test
    void lesFeuxSuiventLeRecit() {
        assertEquals(0.0, EffectMath.fireLevel(4.0f), 1e-9);
        assertEquals(1.0, EffectMath.fireLevel(12f), 1e-6);
        assertEquals(0.8088948633273125, EffectMath.fireLevel(22f), 1e-5);
        assertEquals(0.2, EffectMath.fireLevel(StoryClock.HOLD), 1e-6);
    }

    @Test
    void laFumeeEstCelleDeCompose() throws IOException {
        SceneManifest m = manifest();
        check(m, 12f, 713, 741, 48, 0.045177056445697396, new float[] {1.0f, 0.5411765f, 0.23529412f},
                1249, 241, 114, 0.0012929360817098355, new float[] {0.16470768f, 0.17255022f, 0.21960911f});
        check(m, 22f, 706, 752, 42, 0.040860272672435034, new float[] {0.8905335f, 0.50428575f, 0.26754463f},
                1227, 242, 114, 0.0011693927184077448, new float[] {0.4271922f, 0.34813747f, 0.40405193f});
    }

    private static void check(SceneManifest m, float t, int x0, int y0, int s0, double a0, float[] tint0,
                              int x1, int y1, int s1, double a1, float[] tint1) {
        List<SmokeEffect.Puff> puffs = SmokeEffect.puffs(m, Ambiance.at(t, t), t, t);
        assertEquals(203, puffs.size());
        SmokeEffect.Puff first = puffs.get(0);
        SmokeEffect.Puff last = puffs.get(puffs.size() - 1);
        assertEquals(x0, first.x(), 1f);
        assertEquals(y0, first.y(), 1f);
        assertEquals(s0, first.size());
        assertEquals(a0, first.alpha(), 1e-5);
        assertArrayEquals(tint0, first.tint(), 1e-4f);
        assertEquals(x1, last.x(), 1f);
        assertEquals(y1, last.y(), 1f);
        assertEquals(s1, last.size());
        assertEquals(a1, last.alpha(), 1e-5);
        assertArrayEquals(tint1, last.tint(), 1e-4f);
    }

    @Test
    void lesFlammesEtLesBraisesSontCellesDeCompose() throws IOException {
        SceneManifest m = manifest();
        List<FireEffect.Flame> flames = FireEffect.flames(m, Ambiance.at(12f, 12.0), 12f, 12.0);
        assertEquals(51, flames.size());
        assertEquals(891, flames.get(0).x(), 1f);
        assertEquals(850, flames.get(0).y(), 1f);
        assertEquals(17, flames.get(0).size());
        assertEquals(1.0, flames.get(0).alpha(), 1e-6);
        assertEquals(0.35, flames.get(0).addAlpha(), 1e-6);
        // Somme des trois canaux ajoutés par draw_embers sur toute l'image.
        assertEquals(1606.4746344462037, emberEnergy(m, 12f), 0.5);
        assertEquals(1490.2987787760794, emberEnergy(m, 22f), 0.5);
    }

    private static double emberEnergy(SceneManifest m, float t) {
        double sum = 0;
        for (EmberEffect.Spark s : EmberEffect.sparks(m, Ambiance.at(t, t), t, t)) {
            sum += s.size() * s.size() * s.alpha() * (EMBER[0] + EMBER[1] + EMBER[2]);
        }
        return sum;
    }

    @Test
    void surLAubeTenueLaFumeeEtLesFlammesBougentEncore() throws IOException {
        SceneManifest m = manifest();
        float story = StoryClock.HOLD;
        for (double real : new double[] {60.0, 120.0}) {
            Ambiance a0 = Ambiance.at(story, real);
            Ambiance a1 = Ambiance.at(story, real + 0.1);
            // Puff porte un tableau (teinte) : on compare les positions et les alphas.
            assertNotEquals(placement(SmokeEffect.puffs(m, a0, story, real)),
                    placement(SmokeEffect.puffs(m, a1, story, real + 0.1)));
            assertNotEquals(FireEffect.flames(m, a0, story, real), FireEffect.flames(m, a1, story, real + 0.1));
            assertNotEquals(EmberEffect.sparks(m, a0, story, real), EmberEffect.sparks(m, a1, story, real + 0.1));
            // L'aube tenue garde des panaches complets (pas d'extinction après le récit).
            assertEquals(203, SmokeEffect.puffs(m, a0, story, real).size());
        }
    }

    private static List<String> placement(List<SmokeEffect.Puff> puffs) {
        return puffs.stream().map(p -> p.x() + "," + p.y() + "," + p.size() + "," + p.alpha()).toList();
    }
}
