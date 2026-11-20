package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.web.WebScreen.Rect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * L'écran de repli tient dans son cadre de l'interface la plus petite (320 × 240) aux plus grandes : clavier sous le
 * titre et les points, Réglages avec un libellé au-dessus de chaque champ de code, rien qui déborde ni se chevauche.
 */
class PhoneFallbackLayoutTest {

    private static final int LH = 9; // hauteur de ligne de la police de Minecraft

    private static Rect inner(int guiW, int guiH) {
        return PhoneFallbackLayout.inner(PhoneFrameLayout.fit(guiW, guiH), LH);
    }

    private static void dedans(Rect r, Rect zone, String quoi) {
        assertTrue(r.x() >= zone.x() && r.y() >= zone.y() && r.x() + r.width() <= zone.x() + zone.width()
                && r.y() + r.height() <= zone.y() + zone.height(), quoi + " " + r + " hors de " + zone);
    }

    private static boolean chevauche(Rect a, Rect b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width() && a.y() < b.y() + b.height()
                && b.y() < a.y() + a.height();
    }

    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void lesReglagesTiennentAvecLeursLibelles(int w, int h) {
        Rect zone = inner(w, h);
        PhoneFallbackLayout.Settings s = PhoneFallbackLayout.settings(zone, LH);
        Map<String, Rect> rows = s.rows();
        assertEquals(PhoneFallbackLayout.SETTINGS_ROWS, List.copyOf(rows.keySet()));
        int bas = s.textTop() + 2 * (LH + 1); // propriétaire et message au-dessus des lignes
        for (String nom : PhoneFallbackLayout.SETTINGS_ROWS) {
            Rect r = rows.get(nom);
            dedans(r, zone, nom);
            assertTrue(r.height() >= PhoneFallbackLayout.MIN_ROW, nom + " trop bas : " + r);
            if (PhoneFallbackLayout.LABELLED.contains(nom)) {
                int haut = s.labelTops().get(nom);
                assertTrue(haut >= bas, "libellé de " + nom + " sur la ligne précédente");
                assertTrue(haut + LH <= r.y(), "libellé de " + nom + " sur son champ");
            } else {
                assertFalse(s.labelTops().containsKey(nom), nom + " n'a pas de libellé");
            }
            assertTrue(r.y() >= bas, nom + " chevauche la ligne précédente");
            bas = r.y() + r.height();
        }
        assertEquals(PhoneFallbackLayout.LABELLED, List.copyOf(s.labelTops().keySet()));
    }

    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080"})
    void leClavierTientSousLeTitre(int w, int h) {
        Rect zone = inner(w, h);
        List<Rect> touches = PhoneFallbackLayout.keypad(zone);
        assertEquals(11, touches.size());
        // Titre, points (6 px au plus), et deux lignes de blocage au-dessus du clavier.
        int texte = zone.y() + 6 + LH + 6 + 6 + 6 + 2 * (LH + 2);
        for (int i = 0; i < touches.size(); i++) {
            Rect t = touches.get(i);
            dedans(t, zone, "touche " + i);
            assertTrue(t.y() >= texte, "touche " + i + " sur le texte du code : " + t);
            assertTrue(t.height() >= PhoneFallbackLayout.MIN_ROW, "touche " + i + " trop basse");
            for (int j = i + 1; j < touches.size(); j++) {
                assertFalse(chevauche(t, touches.get(j)), "touches " + i + " et " + j);
            }
        }
        // Effacer sous le 1, le 0 sous le 2.
        assertEquals(touches.get(0).x(), touches.get(9).x());
        assertEquals(touches.get(1).x(), touches.get(10).x());
    }

    /**
     * Au choix du code (téléphone neuf), le bouton « Fond suivant » se glisse entre le message (une ligne : pas de
     * blocage possible avant le premier code) et le clavier, sans toucher ni l'un ni l'autre.
     */
    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void leFondDuChoixDuCodeTientEntreLeTexteEtLeClavier(int w, int h) {
        Rect zone = inner(w, h);
        Rect fond = PhoneFallbackLayout.setupWallpaper(zone, LH);
        dedans(fond, zone, "fond suivant");
        assertTrue(fond.height() >= PhoneFallbackLayout.MIN_ROW, "fond suivant trop bas : " + fond);
        assertTrue(fond.height() <= PhoneFallbackLayout.MAX_ROW, "fond suivant trop haut : " + fond);
        // Titre, points (6 px au plus) et une ligne de message au-dessus.
        int texte = zone.y() + 6 + LH + 6 + 6 + 6 + LH + 2;
        assertTrue(fond.y() >= texte, "fond suivant sur le texte du code : " + fond + ", texte jusqu'à " + texte);
        for (Rect t : PhoneFallbackLayout.keypad(zone)) {
            assertFalse(chevauche(fond, t), "fond suivant sur la touche " + t);
            assertTrue(fond.y() + fond.height() < t.y(), "fond suivant sous le haut du clavier : " + fond + " / " + t);
        }
    }

    @Test
    void effacerDevientUneFlecheQuandSonLibelleNeTientPlus() {
        // « Effacer » fait 40 px dans la police de Minecraft ; touche de 28 px à 320 x 240 : 24 px de place.
        assertTrue(PhoneFallbackLayout.clearAsSymbol(40, 28));
        assertFalse(PhoneFallbackLayout.clearAsSymbol(40, 44), "assez de place : le mot, à peine rétréci");
        assertFalse(PhoneFallbackLayout.clearAsSymbol(30, 34), "tient à l'échelle 1");
        int petite = PhoneFallbackLayout.keypad(inner(320, 240)).get(9).width();
        assertTrue(PhoneFallbackLayout.clearAsSymbol(40, petite), "la touche à 320 x 240 fait " + petite + " px");
    }

    @Test
    void echelleDAjustement() {
        assertEquals(1f, PhoneFallbackLayout.fit(50, 86, 1f));
        assertEquals(0.5f, PhoneFallbackLayout.fit(172, 86, 1f));
        assertEquals(1f, PhoneFallbackLayout.fit(0, 86, 1f));
        assertEquals(82, PhoneFallbackLayout.textRoom(86));
    }
}
