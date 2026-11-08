package dev.oneforallsurvival.client.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.title.TitleLayout.Rect;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class TitleLayoutTest {
    /** Manifeste minimal : seules les dimensions de l'image de référence et du logo comptent ici. */
    private static final SceneManifest SCENE = new SceneManifest(1920, 1080, 2112, 1188,
            new SceneManifest.Sea(48, 2, 163), new float[] {0f, 0f}, new float[] {0f, 0f},
            List.of(), List.of(), List.of(), new float[0], new float[0],
            new SceneManifest.Logo(List.of(), 593, 298, 60));

    private static Rect logo(int guiW, int guiH, int scale) {
        return TitleLayout.logoRect(guiW, guiH, scale, SCENE);
    }

    private static List<int[]> widgets(int n, int w, int h) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new int[] {w, h});
        }
        return out;
    }

    @Test
    void quatreBoutonsSousLeLogo() {
        Rect logo = logo(960, 540, 2); // 1920x1080 en échelle 2
        List<Rect> bottom = TitleLayout.bottomRow(960, 540, widgets(3, 20, 20));
        List<Rect> b = TitleLayout.mainButtons(960, 540, logo, bottom);
        assertEquals(4, b.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(logo.x(), b.get(i).x());
            assertEquals(TitleLayout.MAIN_W, b.get(i).w());
            assertEquals(TitleLayout.MAIN_H, b.get(i).h());
            assertFalse(b.get(i).intersects(logo), "le bouton " + i + " coupe le logo");
            if (i > 0) {
                assertEquals(24, b.get(i).y() - b.get(i - 1).y());
            }
        }
        assertEquals(logo.bottom() + TitleLayout.LOGO_GAP, b.get(0).y());
    }

    @Test
    void petiteFenetreEchelleMaximale() {
        // 854x480 en échelle 2 : interface de 427x240.
        Rect logo = logo(427, 240, 2);
        List<Rect> bottom = TitleLayout.bottomRow(427, 240, widgets(3, 98, 20));
        List<Rect> b = TitleLayout.mainButtons(427, 240, logo, bottom);
        int rowTop = bottom.stream().mapToInt(Rect::y).min().orElseThrow();
        assertTrue(b.get(0).w() >= 120);
        assertTrue(b.get(3).bottom() <= rowTop, "le 4e bouton déborde sur la rangée du bas");
    }

    @Test
    void uneRangeeTropLargePasseALaLigne() {
        List<Rect> row = TitleLayout.bottomRow(427, 240, widgets(10, 20, 20));
        long lignes = row.stream().mapToInt(Rect::y).distinct().count();
        assertEquals(2, lignes);
        // Ordre de lecture : la première ligne est au-dessus, la dernière touche le bas.
        assertTrue(row.get(0).y() < row.get(9).y());
        assertEquals(240 - TitleLayout.ROW_MARGIN, row.get(9).bottom());
        for (Rect r : row) {
            assertTrue(r.right() <= 427 * TitleLayout.ROW_MAX_SHARE + 1e-9, "dépasse 45 % de la largeur");
        }
        // Une rangée courte reste sur une seule ligne, collée à gauche.
        List<Rect> court = TitleLayout.bottomRow(427, 240, widgets(3, 20, 20));
        assertEquals(1, court.stream().mapToInt(Rect::y).distinct().count());
        assertEquals(TitleLayout.ROW_MARGIN, court.get(0).x());
        assertEquals(TitleLayout.ROW_MARGIN + 24, court.get(1).x());
    }

    @Test
    void rienNeDeborde() {
        int[][] fenetres = {{854, 480}, {1280, 720}, {1366, 768}, {1920, 1080}, {2560, 1440}, {3840, 2160},
                {1000, 700}, {1600, 600}};
        for (int[] f : fenetres) {
            for (int scale = 1; scale <= 4; scale++) {
                int w = (f[0] + scale - 1) / scale;
                int h = (f[1] + scale - 1) / scale;
                if (w < 320 || h < 240) {
                    continue; // Minecraft impose 320x240 au minimum d'interface
                }
                Rect logo = logo(w, h, scale);
                for (int n = 0; n <= 12; n++) {
                    String ctx = w + "x" + h + " (échelle " + scale + "), " + n + " widgets";
                    List<Rect> bottom = TitleLayout.bottomRow(w, h, widgets(n, 20, 20));
                    List<Rect> main = TitleLayout.mainButtons(w, h, logo, bottom);
                    List<Rect> all = new ArrayList<>(main);
                    all.addAll(bottom);
                    for (Rect r : all) {
                        assertTrue(r.x() >= 0 && r.y() >= 0 && r.right() <= w && r.bottom() <= h,
                                ctx + " : hors de l'écran " + r);
                    }
                    for (Rect m : main) {
                        for (Rect r : bottom) {
                            assertFalse(m.intersects(r), ctx + " : chevauchement " + m + " / " + r);
                        }
                    }
                    for (int i = 0; i < bottom.size(); i++) {
                        for (int j = i + 1; j < bottom.size(); j++) {
                            assertFalse(bottom.get(i).intersects(bottom.get(j)), ctx + " : rangée");
                        }
                    }
                }
            }
        }
    }

    @Test
    void sansSceneLaColonneResteCentreeEtDansLEcran() {
        Rect repli = TitleLayout.fallbackLogoRect(960, 540);
        List<Rect> grand = TitleLayout.mainButtons(960, 540, repli, List.of());
        int total = grand.get(3).bottom() - grand.get(0).y();
        assertEquals((540 - total) / 2, grand.get(0).y(), "colonne centrée verticalement");
        assertEquals(36, grand.get(0).x(), "même marge relative que le logo de la scène (72/1920)");

        int[][] fenetres = {{854, 480}, {1280, 720}, {1920, 1080}, {3840, 2160}, {1600, 600}};
        for (int[] f : fenetres) {
            for (int scale = 1; scale <= 4; scale++) {
                int w = (f[0] + scale - 1) / scale;
                int h = (f[1] + scale - 1) / scale;
                if (w < 320 || h < 240) {
                    continue;
                }
                for (int n = 0; n <= 8; n++) {
                    String ctx = w + "x" + h + " (échelle " + scale + "), " + n + " étrangers";
                    // rangée réaliste : mods réduit (44 px), langue, accessibilité, puis n boutons étrangers
                    List<int[]> sizes = new ArrayList<>(List.of(new int[] {44, 20}, new int[] {20, 20},
                            new int[] {20, 20}));
                    sizes.addAll(widgets(n, 20, 20));
                    List<Rect> bottom = TitleLayout.bottomRow(w, h, sizes);
                    List<Rect> main = TitleLayout.mainButtons(w, h, TitleLayout.fallbackLogoRect(w, h), bottom);
                    for (Rect m : main) {
                        assertTrue(m.x() >= 0 && m.y() >= 0 && m.right() <= w && m.bottom() <= h, ctx + " " + m);
                        for (Rect r : bottom) {
                            assertFalse(m.intersects(r), ctx + " : chevauchement " + m + " / " + r);
                        }
                    }
                }
            }
        }
    }

    @Test
    void logoAligneSurLaScene() {
        // À 1920x1080 en échelle 1, le recadrage est neutre : le logo est à sa place d'origine (compose.py).
        assertEquals(new Rect(72, 58, 593, 298), logo(1920, 1080, 1));
        // 2560x1440 : recadrage de 4/3, calcul indépendant du rendu (centre de l'image de référence au centre).
        double s = 2560.0 / 1920;
        Rect r = logo(2560, 1440, 1);
        // Tolérance d'un pixel : le rendu calcule l'échelle en float, le test en double.
        assertEquals((72 - 960) * s + 1280, r.x(), 1.0);
        assertEquals((58 - 540) * s + 720, r.y(), 1.0);
        assertEquals((72 + 593 - 960) * s + 1280, r.right(), 1.0);
        assertEquals((58 + 298 - 540) * s + 720, r.bottom(), 1.0);
        // Fenêtre plus haute que large : le recadrage coupe les côtés, le logo y reste lié à la hauteur.
        Rect h = logo(500, 800, 1);
        double sh = 800.0 / 1080;
        assertEquals((72 - 960) * sh + 250, h.x(), 1.0);
        // Échelle d'interface : même position qu'en pixels de fenêtre, divisée par l'échelle.
        Rect g = logo(1280, 720, 2);
        Rect p = logo(2560, 1440, 1);
        assertTrue(Math.abs(g.x() * 2 - p.x()) <= 2 && Math.abs(g.right() * 2 - p.right()) <= 2);
    }

    @Test
    void logoDuManifesteReel() throws IOException {
        Path chemin = Path.of("art/loading-screen/assets/manifest.json");
        Assumptions.assumeTrue(Files.isRegularFile(chemin), "manifeste absent : test ignoré");
        try (Reader r = Files.newBufferedReader(chemin, StandardCharsets.UTF_8)) {
            SceneManifest m = SceneManifest.parse(r);
            assertEquals(new Rect(72, 58, m.logo().width(), m.logo().height()),
                    TitleLayout.logoRect(1920, 1080, 1, m));
        }
    }
}
