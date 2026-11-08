package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Police bitmap du jar du client : glyphes, accents et mise en page comparés à la classe {@code Font} de
 * compose.py (valeurs relevées en exécutant compose.py sur le même jar de la 1.20.1). Le jar du client est sur
 * le classpath de test (ForgeGradle) ; le test est ignoré s'il manque. Décodage par ImageIO : pas de
 * bibliothèque native ici.
 */
class BitmapFontTest {
    private static final Function<String, InputStream> CLASSPATH =
            path -> BitmapFontTest.class.getClassLoader().getResourceAsStream("assets/minecraft/" + path);

    private static BitmapFont.Alpha imageIo(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        byte[] alpha = new byte[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                alpha[y * image.getWidth() + x] = (byte) (image.getRGB(x, y) >>> 24);
            }
        }
        return new BitmapFont.Alpha(image.getWidth(), image.getHeight(), alpha);
    }

    private static BitmapFont gameFont() throws IOException {
        Assumptions.assumeTrue(CLASSPATH.apply("font/include/default.json") != null,
                "jar du client absent du classpath de test");
        return BitmapFont.load(CLASSPATH, BitmapFontTest::imageIo);
    }

    private static int ink(BitmapFont.Glyph g) {
        int n = 0;
        for (byte b : g.alpha()) {
            n += (b & 0xff) > 0 ? 1 : 0;
        }
        return n;
    }

    @Test
    void lesGlyphesEtLesAccentsSontCeuxDeComposePy() throws IOException {
        BitmapFont font = gameFont();
        assertEquals(2413, font.glyphs().size());
        // caractère, largeur, hauteur, rangée du haut, pixels allumés (compose.py)
        Object[][] expected = {
                {'A', 5, 8, 4, 18}, {'i', 1, 8, 4, 6}, {'l', 2, 8, 4, 7},
                {'Î', 3, 12, 1, 14}, {'é', 5, 12, 1, 17}, {'à', 5, 12, 1, 16},
                {'…', 7, 8, 4, 3}, {'%', 5, 8, 4, 11}, {'\'', 1, 8, 4, 2},
        };
        for (Object[] e : expected) {
            BitmapFont.Glyph g = font.glyph((char) e[0]);
            String what = "glyphe « " + e[0] + " »";
            assertEquals(e[1], g.width(), what);
            assertEquals(e[2], g.height(), what);
            assertEquals(e[3], g.top(), what);
            assertEquals(e[4], ink(g), what);
        }
    }

    @Test
    void lesLargeursDeLigneSontCellesDeComposePy() throws IOException {
        BitmapFont font = gameFont();
        assertEquals(80, font.width("LOST ISLAND RP"));
        assertEquals(204, font.width("ÎLE INCONNUE", 3));
        assertEquals(177, font.width("Exploration du littoral… (textures)"));
        assertEquals(72, font.width("69 %", 3));
        assertEquals(360, font.width("Là où tout recommence.", 3));
        assertEquals(263, font.width("Tri des caisses de l'épave… (mods 23/154 : create)"));
    }

    @Test
    void unCaractereInconnuDevientUnPointDInterrogation() throws IOException {
        BitmapFont font = gameFont();
        assertSame(font.glyph('?'), font.glyph(0x1F600));
    }

    /** Police minimale de deux glyphes (« ? » et « A »), décodée sans image : la mise en page seule. */
    @Test
    void laMiseEnPageAvanceDeLaLargeurPlusUnEtDeQuatrePourLEspace() throws IOException {
        String json = "{\"providers\": [{\"type\": \"bitmap\", \"file\": \"minecraft:font/t.png\", \"ascent\": 7,"
                + " \"chars\": [\"?A\"]}]}";
        // Image 16x8 : « ? » 3 colonnes pleines, « A » 2 colonnes.
        byte[] alpha = new byte[16 * 8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 3; x++) {
                alpha[y * 16 + x] = (byte) 255;
            }
            for (int x = 8; x < 10; x++) {
                alpha[y * 16 + x] = (byte) 255;
            }
        }
        BitmapFont font = BitmapFont.load(path -> switch (path) {
            case "font/include/default.json" -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            case "textures/font/t.png" -> new ByteArrayInputStream(new byte[0]);
            default -> null;
        }, png -> new BitmapFont.Alpha(16, 8, alpha));
        List<BitmapFont.Placed> placed = font.layout("A A?");
        assertEquals(3, placed.size());
        assertEquals(0, placed.get(0).x());
        assertEquals(2 + 1 + 4, placed.get(1).x());
        assertEquals(7 + 3, placed.get(2).x());
        assertEquals(4, placed.get(0).glyph().top());
        assertEquals(10 + 3 + 1 + 2, font.width("A A?"));
    }

    @Test
    void sansDescriptionDeLaPoliceLErreurEstClaire() {
        IOException e = assertThrows(IOException.class, () -> BitmapFont.load(path -> null, png -> null));
        assertTrue(e.getMessage().contains("default.json"), e.getMessage());
    }
}
