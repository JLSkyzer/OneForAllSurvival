package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StoryClockTest {
    @Test
    void leRecitSuitLeTempsReel() {
        assertEquals(10f, new StoryClock().storyTime(10.0, false), 1e-6f);
    }

    @Test
    void apresLeRecitLAubeTient() {
        StoryClock c = new StoryClock();
        assertEquals(StoryClock.HOLD, c.storyTime(100.0, false), 0f);
        assertEquals(StoryClock.HOLD, c.storyTime(200.0, false), 0f);
        assertTrue(c.storyFinished(200.0, false));
        assertFalse(new StoryClock().storyFinished(20.0, false));
    }

    @Test
    void unChargementCourtAccelereJusquALAube() {
        StoryClock c = new StoryClock();
        double fini = 10.0;
        double finRecit = fini + (StoryClock.HOLD - fini) / StoryClock.ACCELERATION;
        float prec = 0f;
        for (double t = 0; t < fini; t += 1.0 / 60) {
            prec = c.storyTime(t, false);
        }
        boolean atteint = false;
        for (double t = fini; t < finRecit + 5; t += 1.0 / 60) {
            float v = c.storyTime(t, true);
            assertTrue(Math.abs(v - prec) < 0.1f, "saut a t=" + t + " : " + (v - prec));
            prec = v;
            if (t < finRecit - 0.05) {
                assertTrue(v < StoryClock.HOLD, "trop tot a t=" + t);
            }
            if (t > finRecit + 0.05) {
                assertEquals(StoryClock.HOLD, v, 0f);
                atteint = true;
            }
        }
        assertTrue(atteint);
    }

    @Test
    void uneHorlogeSurLAubeTientDesLePremierInstant() {
        StoryClock c = StoryClock.atDawn();
        assertEquals(StoryClock.HOLD, c.storyTime(0.0, false), 0f);
        assertEquals(StoryClock.HOLD, c.storyTime(3.0, true), 0f);
        assertTrue(c.storyFinished(100.0, true));
    }

    @Test
    void leTempsNeReculeJamais() {
        StoryClock c = new StoryClock();
        float prec = -1f;
        for (double t = 0; t <= 200.0; t += 1.0 / 60) {
            float v = c.storyTime(t, t >= 20.0);
            assertTrue(v >= prec, "recul a t=" + t);
            prec = v;
        }
    }

    // Valeurs calculees depuis progress_at de compose.py (LOAD_START 5.8, LOAD_END 29.0) :
    // t=17.4 -> x=0.5, brut=0.5+0.035*sin(4.5)+0.018*sin(11.5)=0.45002,
    // p=0.45002*0.75+0.5*0.25=0.46252. t=11.6 -> 0.26130 ; t=23.2 -> 0.74804.
    @Test
    void laProgressionDAmbianceSuitComposePy() {
        assertEquals(0f, StoryClock.storyProgress(0f), 0f);
        assertEquals(0f, StoryClock.storyProgress(5.8f), 1e-5f); // 5.8f est un peu au-dessus de 5.8
        assertEquals(0.26130f, StoryClock.storyProgress(11.6f), 1e-3f);
        assertEquals(0.46252f, StoryClock.storyProgress(17.4f), 1e-3f);
        assertEquals(0.74804f, StoryClock.storyProgress(23.2f), 1e-3f);
        assertEquals(1f, StoryClock.storyProgress(29f), 0f);
        assertEquals(1f, StoryClock.storyProgress(StoryClock.HOLD), 0f);
    }
}
