package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.web.WebScreen.Rect;
import org.junit.jupiter.api.Test;

/** Le cadre du téléphone : rapport 390 : 844, 92 % de la hauteur au plus, 90 % de la largeur au plus, centré. */
class PhoneFrameLayoutTest {

    private static void centre(Rect r, int w, int h) {
        assertTrue(Math.abs(r.x() * 2 + r.width() - w) <= 1, "centré en largeur : " + r);
        assertTrue(Math.abs(r.y() * 2 + r.height() - h) <= 1, "centré en hauteur : " + r);
    }

    private static void dedans(Rect r, int w, int h) {
        assertTrue(r.x() >= 0 && r.y() >= 0 && r.x() + r.width() <= w && r.y() + r.height() <= h, "dans la fenêtre : " + r);
        assertTrue(r.height() <= Math.floor(h * 0.92), "92 % de la hauteur au plus : " + r);
        assertTrue(r.width() <= Math.floor(w * 0.90), "90 % de la largeur au plus : " + r);
    }

    private static void rapport(Rect r) {
        double attendu = r.height() * 390.0 / 844.0;
        assertTrue(Math.abs(r.width() - attendu) <= 1, "rapport 390 : 844 : " + r);
    }

    @Test
    void pleinEcran1080ALEchelle4() {
        Rect r = PhoneFrameLayout.fit(480, 270);
        assertEquals(248, r.height());
        assertTrue(r.width() == 114 || r.width() == 115, "largeur " + r.width());
        centre(r, 480, 270);
        dedans(r, 480, 270);
    }

    @Test
    void petiteFenetre() {
        Rect r = PhoneFrameLayout.fit(320, 240);
        dedans(r, 320, 240);
        centre(r, 320, 240);
        rapport(r);
    }

    @Test
    void fenetreEtroiteLimiteeParLaLargeur() {
        Rect r = PhoneFrameLayout.fit(100, 400);
        dedans(r, 100, 400);
        centre(r, 100, 400);
        rapport(r);
        assertEquals(90, r.width());
    }

    @Test
    void toutesLesTaillesCourantesTiennent() {
        for (int w = 40; w <= 1000; w += 7) {
            for (int h = 40; h <= 600; h += 11) {
                Rect r = PhoneFrameLayout.fit(w, h);
                dedans(r, w, h);
                centre(r, w, h);
            }
        }
    }

    @Test
    void echelle() {
        assertEquals(1.0, PhoneFrameLayout.scale(390));
        assertEquals(2.0, PhoneFrameLayout.scale(780));
        assertEquals(0.5, PhoneFrameLayout.scale(195));
    }
}
