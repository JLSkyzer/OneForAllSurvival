package dev.oneforallsurvival.explore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DetectorRulesTest {

    @Test
    void auBordQuarante() {
        assertEquals(40, DetectorRules.beepInterval(24.0, 24.0));
    }

    @Test
    void auContactDeux() {
        assertEquals(2, DetectorRules.beepInterval(0.0, 24.0));
    }

    @Test
    void horsPorteeMoinsUn() {
        assertEquals(-1, DetectorRules.beepInterval(24.01, 24.0));
        assertEquals(-1, DetectorRules.beepInterval(100.0, 24.0));
    }

    @Test
    void plusPresPlusRapide() {
        int precedent = Integer.MAX_VALUE;
        for (double d = 24.0; d >= 0.0; d -= 0.5) {
            int intervalle = DetectorRules.beepInterval(d, 24.0);
            assertTrue(intervalle >= 2 && intervalle <= 40, "hors bornes : " + intervalle);
            assertTrue(intervalle <= precedent, "pas monotone à " + d);
            precedent = intervalle;
        }
        assertEquals(21, DetectorRules.beepInterval(12.0, 24.0));
    }

    @Test
    void lePitchMonte() {
        assertEquals(0.5F, DetectorRules.pitch(24.0, 24.0), 1e-6F);
        assertEquals(2.0F, DetectorRules.pitch(0.0, 24.0), 1e-6F);
        float precedent = 0.0F;
        for (double d = 24.0; d >= 0.0; d -= 1.0) {
            float pitch = DetectorRules.pitch(d, 24.0);
            assertTrue(pitch >= precedent, "le pitch doit monter en approchant");
            precedent = pitch;
        }
    }
}
