package dev.oneforallsurvival.explore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MechanismRulesTest {

    @Test
    void alimenteAuSeuilAvance() {
        assertEquals(1, MechanismRules.next(0, 100, 100, 200));
        assertEquals(6, MechanismRules.next(5, 250, 100, 200));
    }

    @Test
    void sousLeSeuilRepartAZero() {
        assertEquals(0, MechanismRules.next(150, 99, 100, 200));
        assertEquals(0, MechanismRules.next(3, 0, 100, 200));
    }

    @Test
    void plafonneALaDuree() {
        assertEquals(200, MechanismRules.next(200, 100, 100, 200));
        assertEquals(200, MechanismRules.next(199, 100, 100, 200));
    }

    @Test
    void completAPileALaDuree() {
        assertFalse(MechanismRules.isComplete(199, 200));
        assertTrue(MechanismRules.isComplete(200, 200));
    }

    @Test
    void lEtageSuitLaProgression() {
        assertEquals(0, MechanismRules.stage(0, 200));
        assertEquals(0, MechanismRules.stage(60, 200));
        assertEquals(1, MechanismRules.stage(70, 200));
        assertEquals(2, MechanismRules.stage(140, 200));
        assertEquals(3, MechanismRules.stage(200, 200));
    }
}
