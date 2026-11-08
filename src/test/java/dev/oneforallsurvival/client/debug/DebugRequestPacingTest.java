package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class DebugRequestPacingTest {

    private static final BlockPos A = new BlockPos(1, 2, 3);
    private static final BlockPos B = new BlockPos(4, 5, 6);

    private final DebugRequestPacing pacing = new DebugRequestPacing();

    @Test
    void rienQuandLeF3EstFerme() {
        assertFalse(pacing.due(false, false, 10_000, A));
    }

    @Test
    void rienEnModeReduit() {
        assertFalse(pacing.due(true, true, 10_000, A));
    }

    @Test
    void lOuvertureDemandeAussitot() {
        assertTrue(pacing.due(true, false, 10_000, null));
    }

    @Test
    void memeCibleMemeSecondeRienDePlus() {
        assertTrue(pacing.due(true, false, 10_000, A));
        assertFalse(pacing.due(true, false, 10_400, A));
        assertFalse(pacing.due(true, false, 10_999, A));
    }

    @Test
    void uneNouvelleSecondeRedemande() {
        assertTrue(pacing.due(true, false, 10_500, A));
        assertTrue(pacing.due(true, false, 11_000, A));
    }

    @Test
    void uneNouvelleCibleRedemandeApresLIntervalle() {
        assertTrue(pacing.due(true, false, 10_000, A));
        assertFalse(pacing.due(true, false, 10_249, B));
        assertTrue(pacing.due(true, false, 10_250, B));
    }

    @Test
    void perdreLaCibleCompteCommeUnChangement() {
        assertTrue(pacing.due(true, false, 10_000, A));
        assertTrue(pacing.due(true, false, 10_300, null));
    }

    @Test
    void jamaisPlusDeQuatreParSeconde() {
        int envois = 0;
        for (long t = 0; t < 10_000; t += 50) {
            if (pacing.due(true, false, 20_000 + t, t % 100 == 0 ? A : B)) {
                envois++;
            }
        }
        assertTrue(envois <= 40, "envois en 10 s : " + envois);
    }
}
