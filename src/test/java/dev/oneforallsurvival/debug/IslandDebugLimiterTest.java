package dev.oneforallsurvival.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IslandDebugLimiterTest {

    private final IslandDebugLimiter limiter = new IslandDebugLimiter();
    private final UUID player = UUID.randomUUID();

    @Test
    void auPlusQuatreDansUneRafale() {
        for (int i = 0; i < IslandDebugLimiter.MAX_PER_WINDOW; i++) {
            assertTrue(limiter.allow(player, 100));
        }
        assertFalse(limiter.allow(player, 100));
    }

    @Test
    void unRythmeDeQuatreParSecondePasseSansPerte() {
        for (long tick = 0; tick < 200; tick += 5) {
            assertTrue(limiter.allow(player, tick), "tick " + tick);
        }
    }

    @Test
    void laFenetreGlisse() {
        for (long tick = 0; tick < IslandDebugLimiter.MAX_PER_WINDOW; tick++) {
            assertTrue(limiter.allow(player, tick));
        }
        assertFalse(limiter.allow(player, IslandDebugLimiter.WINDOW_TICKS - 1));
        assertTrue(limiter.allow(player, IslandDebugLimiter.WINDOW_TICKS));
        assertFalse(limiter.allow(player, IslandDebugLimiter.WINDOW_TICKS));
    }

    @Test
    void uneRequeteRefuseeNeRepousseRien() {
        for (int i = 0; i < IslandDebugLimiter.MAX_PER_WINDOW; i++) {
            limiter.allow(player, 0);
        }
        for (long tick = 1; tick < IslandDebugLimiter.WINDOW_TICKS; tick++) {
            assertFalse(limiter.allow(player, tick));
        }
        assertTrue(limiter.allow(player, IslandDebugLimiter.WINDOW_TICKS));
    }

    @Test
    void chaqueJoueurASonCompte() {
        for (int i = 0; i < IslandDebugLimiter.MAX_PER_WINDOW; i++) {
            limiter.allow(player, 0);
        }
        assertTrue(limiter.allow(UUID.randomUUID(), 0));
    }

    @Test
    void unServeurRedemarreRepartDeZero() {
        for (int i = 0; i < IslandDebugLimiter.MAX_PER_WINDOW; i++) {
            limiter.allow(player, 5000);
        }
        assertTrue(limiter.allow(player, 3));
    }

    @Test
    void oublierRendLeCompte() {
        for (int i = 0; i < IslandDebugLimiter.MAX_PER_WINDOW; i++) {
            limiter.allow(player, 0);
        }
        limiter.forget(player);
        assertTrue(limiter.allow(player, 0));
    }
}
