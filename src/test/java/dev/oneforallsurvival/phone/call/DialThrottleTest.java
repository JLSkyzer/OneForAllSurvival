package dev.oneforallsurvival.phone.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Le frein des appels composés : fenêtre glissante, borne de la fenêtre, oubli des seules entrées périmées. */
class DialThrottleTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void laFenetreExpireASaBorne() {
        DialThrottle frein = new DialThrottle(1_200);
        for (int i = 0; i < 10; i++) {
            assertTrue(frein.allow(A, 100 + i, 10), "appel " + i);
        }
        assertFalse(frein.allow(A, 110, 10), "11e dans la minute");
        assertFalse(frein.allow(A, 1_299, 10), "un tic avant la fin de la fenêtre du premier");
        assertTrue(frein.allow(A, 1_300, 10), "la fenêtre du premier est passée (borne comprise)");
        assertFalse(frein.allow(A, 1_300, 10), "un seul est sorti");
        assertTrue(frein.allow(B, 110, 10), "un autre joueur n'est pas freiné");
    }

    @Test
    void lePurgeNOublieQueCeQuiEstSorti() {
        DialThrottle frein = new DialThrottle(1_200);
        for (int i = 0; i < 10; i++) {
            frein.allow(A, 0, 10);
        }
        frein.allow(B, 500, 10);
        frein.purge(1_199);
        assertEquals(2, frein.tracked(), "rien n'est encore sorti");
        assertFalse(frein.allow(A, 1_199, 10), "toujours freiné après la purge");
        frein.purge(1_200);
        assertEquals(1, frein.tracked(), "A est oublié, B reste");
        frein.purge(1_700);
        assertEquals(0, frein.tracked());
    }

    @Test
    void uneHorlogeQuiReculeOublieTout() {
        DialThrottle frein = new DialThrottle(1_200);
        for (int i = 0; i < 10; i++) {
            frein.allow(A, 5_000, 10);
        }
        assertTrue(frein.allow(A, 3, 10), "nouveau monde : l'horloge repart de zéro");
    }
}
