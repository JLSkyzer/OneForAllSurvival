package dev.oneforallsurvival.earlywindow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WindowPlacementTest {

    @Test
    void lAutreEcranEstLePremierQuiNEstPasLePrincipal() {
        assertEquals(22L, WindowPlacement.otherMonitor(new long[] {11L, 22L, 33L}, 11L));
        assertEquals(11L, WindowPlacement.otherMonitor(new long[] {11L, 22L}, 22L));
    }

    @Test
    void unSeulEcranOuAucunNeDonneRien() {
        assertEquals(0L, WindowPlacement.otherMonitor(new long[] {11L}, 11L));
        assertEquals(0L, WindowPlacement.otherMonitor(new long[0], 11L));
        assertEquals(0L, WindowPlacement.otherMonitor(null, 11L));
        assertEquals(0L, WindowPlacement.otherMonitor(new long[] {0L, 11L}, 11L));
    }

    @Test
    void centreeSurLePrincipalCommeAvant() {
        assertArrayEquals(new int[] {640, 336}, WindowPlacement.centered(0, 0, 2560, 1392, 1280, 720));
        assertArrayEquals(new int[] {3200, 336}, WindowPlacement.centered(2560, 0, 2560, 1392, 1280, 720));
    }

    @Test
    void auCoinDeLaZoneDeTravailDeLAutreEcran() {
        assertArrayEquals(new int[] {2600, 40}, WindowPlacement.onWorkArea(2560, 0));
        assertArrayEquals(new int[] {-1880, 140}, WindowPlacement.onWorkArea(-1920, 100));
    }

    @Test
    void sansLaProprieteRienNeChange() {
        String avant = System.getProperty(WindowPlacement.PROPERTY);
        try {
            System.clearProperty(WindowPlacement.PROPERTY);
            assertFalse(WindowPlacement.secondScreenRequested());
            System.setProperty(WindowPlacement.PROPERTY, "true");
            assertTrue(WindowPlacement.secondScreenRequested());
        } finally {
            if (avant == null) {
                System.clearProperty(WindowPlacement.PROPERTY);
            } else {
                System.setProperty(WindowPlacement.PROPERTY, avant);
            }
        }
    }
}
