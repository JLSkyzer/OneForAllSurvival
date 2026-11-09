package dev.oneforallsurvival.fence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FenceRulesTest {

    @Test
    void sansClotureAucuneConsommation() {
        assertEquals(0, FenceRules.energyPerTick(0, 0, true, 2, 0.25, 1, 2, 200));
    }

    @Test
    void laConsommationSuitLaLongueur() {
        assertEquals(18, FenceRules.energyPerTick(64, 0, false, 2, 0.25, 1, 2, 200));
    }

    @Test
    void l_orageMultiplie() {
        assertEquals(36, FenceRules.energyPerTick(64, 0, true, 2, 0.25, 1, 2, 200));
    }

    @Test
    void laVegetationAjoute() {
        assertEquals(23, FenceRules.energyPerTick(64, 5, false, 2, 0.25, 1, 2, 200));
    }

    @Test
    void laConsommationEstBornee() {
        assertEquals(200, FenceRules.energyPerTick(64, 500, true, 2, 0.25, 1, 2, 200));
    }

    @Test
    void arrondiAuSuperieur() {
        assertEquals(3, FenceRules.energyPerTick(1, 0, false, 2, 0.25, 1, 2, 200)); // 2,25 -> 3
    }

    @Test
    void lesDegatsRespectentLePlancher() {
        assertEquals(1f, FenceRules.damageFor(20f, 1f, 4f, false));
        assertEquals(0.5f, FenceRules.damageFor(4.5f, 1f, 4f, false));
        assertEquals(0f, FenceRules.damageFor(4f, 1f, 4f, false));
        assertEquals(0f, FenceRules.damageFor(1f, 1f, 4f, false));
    }

    @Test
    void ceQuiPeutTuerIgnoreLePlancher() {
        assertEquals(1f, FenceRules.damageFor(1f, 1f, 4f, true));
    }

    @Test
    void lesMembresCritiquesGardentUnePointDeMarge() {
        // Vie vanilla haute, tête à 3 : au plus 2 points, la tête garde 1.
        assertEquals(1f, FenceRules.damageForLimbs(20f, 3f, 1f, 4f, 1f));
        assertEquals(0.5f, FenceRules.damageForLimbs(20f, 1.5f, 1f, 4f, 1f));
        // La marge atteinte : plus rien, la tête presque vide ne reçoit aucun choc.
        assertEquals(0f, FenceRules.damageForLimbs(20f, 1f, 1f, 4f, 1f));
        assertEquals(0f, FenceRules.damageForLimbs(20f, 0.0035f, 1f, 4f, 1f));
    }

    @Test
    void leMoinsPermissifDesDeuxPlanchersGagne() {
        // Membres sains : le plancher vanilla décide.
        assertEquals(0.5f, FenceRules.damageForLimbs(4.5f, 4f, 1f, 4f, 1f));
        assertEquals(0f, FenceRules.damageForLimbs(4f, 6f, 1f, 4f, 1f));
        // Membre faible : le plancher des membres décide, malgré une vie haute.
        assertEquals(0.25f, FenceRules.damageForLimbs(15f, 1.25f, 1f, 4f, 1f));
    }
}
