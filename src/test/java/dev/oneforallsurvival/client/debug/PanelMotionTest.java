package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PanelMotionTest {

    @Test
    void lesPanneauxArriventEnDecale() {
        assertEquals(0, PanelMotion.alpha(0, 0), 0);
        assertEquals(6, PanelMotion.offset(0, 0), 1e-9);
        // Le rang 2 n'a pas commencé avant 0,08 s.
        assertEquals(0, PanelMotion.alpha(0.08, 2), 0);
        assertTrue(PanelMotion.alpha(0.08, 0) > PanelMotion.alpha(0.08, 1));
        assertTrue(PanelMotion.alpha(0.08, 1) > PanelMotion.alpha(0.08, 2));
        // Arrivée complète après 0,25 s (+ décalage).
        assertEquals(1, PanelMotion.alpha(0.25, 0), 0);
        assertEquals(0, PanelMotion.offset(0.25, 0), 1e-9);
        assertEquals(1, PanelMotion.alpha(0.25 + 0.08, 2), 0);
        assertFalse(PanelMotion.settled(0.2, 3));
        assertTrue(PanelMotion.settled(0.34, 3));
    }
}
