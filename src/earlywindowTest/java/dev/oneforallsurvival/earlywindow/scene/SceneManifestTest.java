package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SceneManifestTest {
    // Gradle lance les tests depuis la racine du projet.
    private static final Path MANIFESTE = Path.of("art/loading-screen/assets/manifest.json");

    @Test
    void leManifesteReelSeLit() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(MANIFESTE),
                "art/loading-screen/assets/manifest.json absent : test ignore");
        SceneManifest m;
        try (Reader r = Files.newBufferedReader(MANIFESTE, StandardCharsets.UTF_8)) {
            m = SceneManifest.parse(r);
        }
        assertEquals(1920, m.outW());
        assertEquals(1080, m.outH());
        assertEquals(2112, m.plateW());
        assertEquals(1188, m.plateH());
        assertEquals(48, m.sea().frames());
        assertEquals(2, m.sea().scale());
        assertEquals(163, m.sea().horizonRow());
        assertEquals(2, m.sunUv().length);
        assertEquals(2, m.moonUv().length);
        assertTrue(m.fires().size() > 0);
        assertEquals(51, m.fires().size());
        assertEquals(5, m.plumes().size());
        assertEquals(8, m.camp().size());
        assertEquals(2, m.temple().length);
        assertEquals(2, m.wreck().length);
        assertEquals(10, m.logo().letters().size());
        assertEquals("ui/logo/letter_00_L.png", m.logo().letters().get(0).file());
        assertEquals(593, m.logo().width());
        assertEquals(298, m.logo().height());
        assertEquals(60, m.logo().glowPad());
        assertEquals(0.42f, m.camp().get(0).at(), 1e-6f);
    }

    @Test
    void unChampManquantDonneUneErreurClaire() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SceneManifest.parse(new StringReader("{\"plate\":[2112,1188]}")));
        assertTrue(e.getMessage().contains("output"), e.getMessage());
    }

    @Test
    void unJsonInvalideDonneUneErreurClaire() {
        assertThrows(IllegalArgumentException.class,
                () -> SceneManifest.parse(new StringReader("pas du json")));
    }
}
