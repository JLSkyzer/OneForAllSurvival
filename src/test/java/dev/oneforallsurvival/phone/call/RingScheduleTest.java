package dev.oneforallsurvival.phone.call;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.call.CallBook.Call;
import dev.oneforallsurvival.phone.call.CallBook.Party;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Quand un appel sonne (revue des tâches 4a et 4b) : au premier tic où il est vu sonnant, même composé à un tic
 * antérieur (un vrai paquet {@code DIAL} est joué entre deux tics, après la fin du tic de {@code startedTick}), puis
 * toutes les périodes ; jamais une fois décroché.
 */
class RingScheduleTest {

    private static final int PERIODE = 40;

    private static Call appel(int id, CallBook.State etat, long compose) {
        Party appelant = new Party(new UUID(1, id), new UUID(2, id), new UUID(3, id), "0611111111");
        Party appele = new Party(new UUID(4, id), new UUID(5, id), new UUID(6, id), "0622222222");
        return new Call(id, appelant, appele, etat, compose, etat == CallBook.State.ACTIVE ? compose + 5 : -1L, false,
                false, false, false);
    }

    @Test
    void laPremiereSonnerieEstAuPremierTicVuMemeComposeAvant() {
        for (int retard : new int[] {1, 2, 39, 41}) {
            RingSchedule sonneries = new RingSchedule(PERIODE);
            Call call = appel(7, CallBook.State.RINGING, 100L);
            assertTrue(sonneries.due(call, 100 + retard), "vu " + retard + " tic(s) après la composition : il sonne");
        }
    }

    @Test
    void puisUneFoisParPeriode() {
        RingSchedule sonneries = new RingSchedule(PERIODE);
        Call call = appel(7, CallBook.State.RINGING, 100L);
        assertTrue(sonneries.due(call, 101));
        for (int tic = 101; tic < 101 + PERIODE; tic++) {
            assertFalse(sonneries.due(call, tic), "pas encore au tic " + tic);
        }
        assertTrue(sonneries.due(call, 101 + PERIODE), "la seconde, une période après la première");
        assertFalse(sonneries.due(call, 102 + PERIODE));
        assertTrue(sonneries.due(call, 101 + 2 * PERIODE), "la troisième");
    }

    @Test
    void chaqueAppelASonPropreCompte() {
        RingSchedule sonneries = new RingSchedule(PERIODE);
        assertTrue(sonneries.due(appel(1, CallBook.State.RINGING, 100L), 101));
        assertTrue(sonneries.due(appel(2, CallBook.State.RINGING, 110L), 111), "le second appel sonne aussitôt vu");
        assertFalse(sonneries.due(appel(1, CallBook.State.RINGING, 100L), 111));
    }

    @Test
    void decrocheOuOublieIlNeSonnePlusDeSonAncienCompte() {
        RingSchedule sonneries = new RingSchedule(PERIODE);
        assertTrue(sonneries.due(appel(7, CallBook.State.RINGING, 100L), 101));
        assertFalse(sonneries.due(appel(7, CallBook.State.ACTIVE, 100L), 141), "décroché : plus de sonnerie");
        assertTrue(sonneries.due(appel(7, CallBook.State.RINGING, 100L), 142), "le compte a été oublié au décroché");
        sonneries.forget(7);
        assertTrue(sonneries.due(appel(7, CallBook.State.RINGING, 100L), 143), "oublié à la fin de l'appel");
    }
}
