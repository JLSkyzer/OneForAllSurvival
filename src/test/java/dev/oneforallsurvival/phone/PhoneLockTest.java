package dev.oneforallsurvival.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhoneLockTest {

    private static final int MAX = 5;
    private static final long LOCK = 60_000L;

    private static PhoneLock.Outcome wrong(int failures, long until, long now) {
        return PhoneLock.attempt(false, failures, until, now, MAX, LOCK);
    }

    @Test
    void quatreFauxNeBloquentPas() {
        int failures = 0;
        long until = 0;
        for (int i = 0; i < 4; i++) {
            PhoneLock.Outcome out = wrong(failures, until, 1_000L);
            assertFalse(out.accepted());
            failures = out.failures();
            until = out.lockedUntil();
        }
        assertEquals(4, failures);
        assertEquals(0L, until);
    }

    @Test
    void cinquiemeFauxBloque() {
        PhoneLock.Outcome out = wrong(4, 0L, 1_000L);
        assertFalse(out.accepted());
        assertEquals(61_000L, out.lockedUntil());
        assertEquals(0, out.failures());
    }

    @Test
    void pendantLeBlocageMemeLeBonCodeEstRefuse() {
        PhoneLock.Outcome out = PhoneLock.attempt(true, 0, 61_000L, 30_000L, MAX, LOCK);
        assertFalse(out.accepted());
        assertEquals(61_000L, out.lockedUntil());
    }

    @Test
    void pendantLeBlocageUnFauxNeCompteNiNeProlonge() {
        PhoneLock.Outcome out = wrong(0, 61_000L, 30_000L);
        assertFalse(out.accepted());
        assertEquals(0, out.failures());
        assertEquals(61_000L, out.lockedUntil());
    }

    @Test
    void lePremierInstantLibreAccepte() {
        assertTrue(PhoneLock.attempt(true, 0, 61_000L, 61_000L, MAX, LOCK).accepted());
    }

    @Test
    void leBonCodeRemetLesEchecsAZero() {
        PhoneLock.Outcome out = PhoneLock.attempt(true, 2, 0L, 5_000L, MAX, LOCK);
        assertTrue(out.accepted());
        assertEquals(0, out.failures());
    }

    @Test
    void sansBlocageConfigureUnEssaiUniqueBloqueDeSuite() {
        PhoneLock.Outcome out = PhoneLock.attempt(false, 0, 0L, 1_000L, 1, 0L);
        assertEquals(1_000L, out.lockedUntil());
        assertEquals(0, out.failures());
    }

    @Test
    void blocageEnormeRamenePlafond() {
        PhoneLock.Outcome out = PhoneLock.attempt(true, 0, Long.MAX_VALUE, 1_000L, MAX, LOCK);
        assertFalse(out.accepted());
        assertEquals(61_000L, out.lockedUntil());
    }

    @Test
    void horlogeQuiReculeNeBloquePasPlusQueLaDuree() {
        // blocage posé jusqu'à 1 060 000, puis l'horloge revient à 5 000
        PhoneLock.Outcome out = PhoneLock.attempt(false, 0, 1_060_000L, 5_000L, MAX, LOCK);
        assertEquals(65_000L, out.lockedUntil());
        assertTrue(PhoneLock.attempt(true, 0, out.lockedUntil(), 65_000L, MAX, LOCK).accepted());
    }

    @Test
    void plafondSansDepassementDeLong() {
        PhoneLock.Outcome out = PhoneLock.attempt(true, 0, Long.MAX_VALUE, Long.MAX_VALUE - 10, MAX, LOCK);
        assertEquals(Long.MAX_VALUE, out.lockedUntil());
    }

    @Test
    void blocageNegatifNeBloquePas() {
        assertTrue(PhoneLock.attempt(true, 0, -5L, 1_000L, MAX, LOCK).accepted());
    }
}
