package dev.oneforallsurvival.food;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoodDecayTest {

    private static final long FRESH = 432000L;
    private static final long STALE = 432000L;

    @Test
    void roundDownGardeLeQuartDHeureEntier() {
        assertEquals(18000L, FoodDecay.roundDown(18000L));
        assertEquals(18000L, FoodDecay.roundDown(35999L));
        assertEquals(0L, FoodDecay.roundDown(17999L));
        assertEquals(0L, FoodDecay.roundDown(0L));
    }

    @Test
    void unAlimentEstFraisJusquAFreshMoinsUn() {
        assertEquals(DecayState.FRESH, FoodDecay.stateAt(0, 0, FRESH, STALE));
        assertEquals(DecayState.FRESH, FoodDecay.stateAt(0, FRESH - 1, FRESH, STALE));
    }

    @Test
    void ilEstRassisDeFreshAFreshPlusStaleMoinsUn() {
        assertEquals(DecayState.STALE, FoodDecay.stateAt(0, FRESH, FRESH, STALE));
        assertEquals(DecayState.STALE, FoodDecay.stateAt(0, FRESH + STALE - 1, FRESH, STALE));
    }

    @Test
    void ilEstAvarieDeFreshPlusStale() {
        assertEquals(DecayState.SPOILED, FoodDecay.stateAt(0, FRESH + STALE, FRESH, STALE));
        assertEquals(DecayState.SPOILED, FoodDecay.stateAt(0, FRESH + STALE + 1_000_000L, FRESH, STALE));
    }

    @Test
    void uneDateFutureResteFraiche() {
        assertEquals(DecayState.FRESH, FoodDecay.stateAt(100, 50, FRESH, STALE));
    }

    @Test
    void lAgeSeCompteDepuisLaDate() {
        assertEquals(DecayState.FRESH, FoodDecay.stateAt(1_000_000L, 1_000_000L + FRESH - 1, FRESH, STALE));
        assertEquals(DecayState.STALE, FoodDecay.stateAt(1_000_000L, 1_000_000L + FRESH, FRESH, STALE));
    }

    @Test
    void laMoyennePondereeSuitLesQuantites() {
        assertEquals(18000L, FoodDecay.weightedStamp(0, 3, 72000, 1));
        assertEquals(0L, FoodDecay.weightedStamp(0, 1, 18000, 1));
    }

    @Test
    void lOrdreDesPilesNeChangeRien() {
        assertEquals(FoodDecay.weightedStamp(0, 3, 72000, 1), FoodDecay.weightedStamp(72000, 1, 0, 3));
        assertEquals(FoodDecay.weightedStamp(36000, 7, 90000, 2), FoodDecay.weightedStamp(90000, 2, 36000, 7));
    }

    @Test
    void deuxPilesDeMemeDateGardentCetteDate() {
        assertEquals(54000L, FoodDecay.weightedStamp(54000, 5, 54000, 60));
    }

    @Test
    void pasDeDebordementAGrandsTicks() {
        long big = Long.MAX_VALUE - 1000;
        long stamp = FoodDecay.roundDown(big);
        assertEquals(stamp, FoodDecay.weightedStamp(stamp, 64, stamp, 64));
        long mixed = FoodDecay.weightedStamp(stamp, 64, stamp - 4 * FoodDecay.BUCKET_TICKS, 64);
        assertEquals(stamp - 2 * FoodDecay.BUCKET_TICKS, mixed);
        assertEquals(DecayState.FRESH, FoodDecay.stateAt(big, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(DecayState.SPOILED, FoodDecay.stateAt(Long.MIN_VALUE, Long.MAX_VALUE, FRESH, STALE));
        assertEquals(DecayState.STALE, FoodDecay.stateAt(0, Long.MAX_VALUE - 1, Long.MAX_VALUE - 10, Long.MAX_VALUE));
    }
}
