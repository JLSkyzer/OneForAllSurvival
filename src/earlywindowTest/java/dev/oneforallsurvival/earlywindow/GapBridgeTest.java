package dev.oneforallsurvival.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.earlywindow.GapBridge.FenceOutcome;
import org.junit.jupiter.api.Test;

class GapBridgeTest {
    // Retours de glClientWaitSync (spécification OpenGL 3.2), écrits en dur pour être lisibles ici.
    private static final int ALREADY_SIGNALED = 0x911A;
    private static final int TIMEOUT_EXPIRED = 0x911B;
    private static final int CONDITION_SATISFIED = 0x911C;
    private static final int WAIT_FAILED = 0x911D;
    private static final long FRAME = 1_000_000_000L / 30;

    @Test
    void barriereFranchieOnDessine() {
        assertEquals(FenceOutcome.DRAW, GapBridge.fenceOutcome(ALREADY_SIGNALED));
        assertEquals(FenceOutcome.DRAW, GapBridge.fenceOutcome(CONDITION_SATISFIED));
    }

    @Test
    void delaiDepasseOnSauteLImage() {
        assertEquals(FenceOutcome.SKIP, GapBridge.fenceOutcome(TIMEOUT_EXPIRED));
    }

    @Test
    void attenteEnEchecArreteLePont() {
        assertEquals(FenceOutcome.STOP, GapBridge.fenceOutcome(WAIT_FAILED));
        assertEquals(FenceOutcome.STOP, GapBridge.fenceOutcome(0));
    }

    @Test
    void imageSauteeDonneUneImageEntiereHorsDuVerrou() {
        // lastFrame n'a pas avancé depuis longtemps : sans la règle, la pause serait nulle (verrou repris aussitôt).
        long now = 10_000_000_000L;
        long lastFrame = now - 5_000_000_000L;
        assertEquals(0L, GapBridge.pauseNanos(false, lastFrame, now));
        assertEquals(FRAME, GapBridge.pauseNanos(true, lastFrame, now));
    }

    @Test
    void imageDessineeAttendLImageSuivante() {
        long now = 10_000_000_000L;
        long pause = GapBridge.pauseNanos(false, now - 10_000_000L, now);
        assertEquals(FRAME - 10_000_000L, pause);
        // Une image sautée juste après une image dessinée attend quand même une image entière.
        assertTrue(GapBridge.pauseNanos(true, now - 10_000_000L, now) >= pause);
    }
}
