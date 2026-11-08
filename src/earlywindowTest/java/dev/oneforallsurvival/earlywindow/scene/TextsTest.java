package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fichiers de langue du jar de service et choix de la langue (ruling 32). */
class TextsTest {
    private static final Path LANG = Path.of("src/earlywindow/resources/lostisland/lang");

    /** Ouvre {@code lostisland/lang/<code>.json} dans les sources, comme le classloader du jar de service. */
    private static final Function<String, InputStream> SOURCES = path -> {
        Path file = LANG.resolve(path.substring(Texts.DIRECTORY.length()));
        try {
            return Files.isRegularFile(file) ? Files.newInputStream(file) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    };

    private static Map<String, String> file(String code) throws IOException {
        return Texts.parse(Files.newBufferedReader(LANG.resolve(code + ".json"), StandardCharsets.UTF_8));
    }

    @Test
    void lesDeuxFichiersOntExactementLesMemesClesQueLesTextesIntegres() throws IOException {
        Map<String, String> fr = file("fr_fr");
        Map<String, String> en = file("en_us");
        assertEquals(Texts.BUILT_IN.keySet(), fr.keySet());
        assertEquals(Texts.BUILT_IN.keySet(), en.keySet());
        for (Map.Entry<String, String> e : en.entrySet()) {
            assertFalse(e.getValue().isBlank(), e.getKey());
        }
    }

    @Test
    void leFrancaisDuFichierEstCeluiDeLaSpec() throws IOException {
        assertEquals(Texts.BUILT_IN, file("fr_fr"));
    }

    @Test
    void laLangueDuJeuQuandOnASonFichier() {
        List<String> logs = new ArrayList<>();
        Texts fr = Texts.load("fr_fr", SOURCES, logs::add);
        assertEquals("fr_fr", fr.origin());
        assertEquals("Les moteurs toussent… (démarrage)",
                StatusMessages.of(fr, Phase.FORGE_START, "", 0, 0).line());
        assertEquals("Tri des caisses de l'épave… (mods 23/154 : create)",
                StatusMessages.of(fr, Phase.MOD_LOADING, "create", 23, 154).line());
        Texts en = Texts.load("en_us", SOURCES, logs::add);
        assertEquals("en_us", en.origin());
        assertEquals("Exploring the shoreline… (textures)", StatusMessages.of(en, Phase.RESOURCES, "", 0, 0).line());
        assertEquals("Sorting the crates from the wreck… (mods 23/154 : create)",
                StatusMessages.of(en, Phase.MOD_LOADING, "create", 23, 154).line());
        assertTrue(logs.isEmpty(), logs.toString());
    }

    @Test
    void uneLangueSansFichierRetombeSurLAnglais() {
        Texts t = Texts.load("de_de", SOURCES, line -> { });
        assertEquals("en_us (repli de de_de)", t.origin());
        assertEquals("Welcome to Lost Island.", t.get(Texts.STATUS_DONE));
        assertEquals("en_us", Texts.load(null, SOURCES, line -> { }).origin());
    }

    @Test
    void unCodeDouteuxNEntreJamaisDansUnChemin() {
        assertEquals(List.of("en_us"), Texts.candidates("../../secret"));
        assertEquals(List.of("en_us"), Texts.candidates(null));
        assertEquals(List.of("fr_fr", "en_us"), Texts.candidates("fr_fr"));
        assertEquals(List.of("en_us"), Texts.candidates("en_us"));
    }

    @Test
    void sansAucunFichierLeFrancaisIntegre() {
        Texts t = Texts.load("fr_fr", path -> null, line -> { });
        assertEquals("fr_fr (intégré)", t.origin());
        assertEquals("Le camp s'organise…", t.get(Texts.STATUS_REGISTRIES));
    }

    @Test
    void uneCleAbsenteSeLitDansLeFichierSuivant() {
        Function<String, InputStream> partial = path -> path.endsWith("fr_fr.json")
                ? new java.io.ByteArrayInputStream("{\"lostisland.loading.ui.end\": \"ÎLE\"}"
                        .getBytes(StandardCharsets.UTF_8))
                : null;
        Texts t = Texts.load("fr_fr", partial, line -> { });
        assertEquals("ÎLE", t.get(Texts.UI_END));
        assertEquals("Là où tout recommence.", t.get(Texts.UI_MOTTO));
    }

    @Test
    void unFichierIllisibleEstSaute() {
        List<String> logs = new ArrayList<>();
        Texts t = Texts.load("fr_fr", path -> new java.io.ByteArrayInputStream(
                "pas du json".getBytes(StandardCharsets.UTF_8)), logs::add);
        assertEquals("fr_fr (intégré)", t.origin());
        assertEquals(2, logs.size(), logs.toString());
    }

    @Test
    void laLangueSeLitDansOptionsTxt(@TempDir Path dir) throws IOException {
        assertNull(Texts.gameLanguage(dir));
        Files.writeString(dir.resolve("options.txt"), "version:3465\nlang:FR_FR\nfov:0.0\n");
        assertEquals("fr_fr", Texts.gameLanguage(dir));
        Files.writeString(dir.resolve("options.txt"), "version:3465\nfov:0.0\n");
        assertNull(Texts.gameLanguage(dir));
        assertNull(Texts.gameLanguage(null));
    }
}
