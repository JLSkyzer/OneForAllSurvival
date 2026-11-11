package dev.oneforallsurvival.light;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LightRulesTest {

    private static final int MER = 63;
    private static final int MIN = 8;

    @Test
    void tourLibre() {
        assertTrue(LightRules.isFreeTower(true, 90, MER, MIN));
    }

    /** Pile à la hauteur minimale, le phare marche ; un bloc plus bas, non. */
    @Test
    void laHauteurMinimaleCompte() {
        assertTrue(LightRules.isFreeTower(true, MER + MIN, MER, MIN));
        assertFalse(LightRules.isFreeTower(true, MER + MIN - 1, MER, MIN));
    }

    @Test
    void sousUnToitNon() {
        assertFalse(LightRules.isFreeTower(false, 200, MER, MIN));
    }

    @Test
    void tropBasNon() {
        assertFalse(LightRules.isFreeTower(true, MER, MER, MIN));
        assertFalse(LightRules.isFreeTower(true, 20, MER, MIN));
    }

    /** Le phare veut briller seulement quand la tour est libre ET qu'il fait nuit : les quatre cas. */
    @Test
    void brilleSeulementLaNuitEnTourLibre() {
        assertTrue(LightRules.shouldShine(true, true));
        assertFalse(LightRules.shouldShine(true, false));
        assertFalse(LightRules.shouldShine(false, true));
        assertFalse(LightRules.shouldShine(false, false));
    }
}
