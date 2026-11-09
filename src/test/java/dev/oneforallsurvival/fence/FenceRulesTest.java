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
}
