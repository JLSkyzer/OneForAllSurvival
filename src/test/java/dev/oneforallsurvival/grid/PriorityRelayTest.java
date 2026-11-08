package dev.oneforallsurvival.grid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** La répartition par priorité du tableau de délestage, sans Minecraft. */
class PriorityRelayTest {

    /** Une sortie qui accepte encore {@code room} FE au total. */
    private static final class Sortie implements PriorityRelay.Sink {

        private int room;
        private int recu;

        Sortie(int room) {
            this.room = room;
        }

        @Override
        public int accept(int amount, boolean simulate) {
            int pris = Math.min(amount, room);
            if (!simulate) {
                room -= pris;
                recu += pris;
            }
            return pris;
        }
    }

    @Test
    void lesSortiesSontServiesDansLOrdreEtLesDernieresManquent() {
        Sortie a = new Sortie(60);
        Sortie b = new Sortie(60);
        Sortie c = new Sortie(60);
        int[] livre = new int[3];
        boolean[] affame = new boolean[3];
        int total = PriorityRelay.distribute(100, List.of(a, b, c), false, livre, affame);
        assertEquals(100, total);
        assertArrayEquals(new int[] {60, 40, 0}, livre);
        assertArrayEquals(new boolean[] {false, true, true}, affame);
        assertEquals(60, a.recu);
        assertEquals(40, b.recu);
        assertEquals(0, c.recu);
    }

    @Test
    void uneOffreSuffisanteNAffamePersonne() {
        int[] livre = new int[3];
        boolean[] affame = new boolean[3];
        int total = PriorityRelay.distribute(500, List.of(new Sortie(60), new Sortie(60), new Sortie(60)), false,
                livre, affame);
        assertEquals(180, total);
        assertArrayEquals(new int[] {60, 60, 60}, livre);
        assertArrayEquals(new boolean[] {false, false, false}, affame);
    }

    @Test
    void uneSortiePleineNEstJamaisAffamee() {
        int[] livre = new int[3];
        boolean[] affame = new boolean[3];
        // La pleine est atteinte une fois l'offre épuisée : elle n'accepte plus rien, elle ne manque de rien.
        int total = PriorityRelay.distribute(60, List.of(new Sortie(60), new Sortie(0), new Sortie(0)), false,
                livre, affame);
        assertEquals(60, total);
        assertArrayEquals(new boolean[] {false, false, false}, affame);
        // Et atteinte avec de l'offre en main, pas davantage.
        livre = new int[2];
        affame = new boolean[2];
        PriorityRelay.distribute(100, List.of(new Sortie(0), new Sortie(10)), false, livre, affame);
        assertArrayEquals(new int[] {0, 10}, livre);
        assertArrayEquals(new boolean[] {false, false}, affame);
    }

    @Test
    void uneSimulationNeChangeRien() {
        Sortie a = new Sortie(60);
        Sortie b = new Sortie(60);
        Sortie c = new Sortie(60);
        int[] livre = {7, 7, 7};
        boolean[] affame = {false, false, false};
        int simule = PriorityRelay.distribute(100, List.of(a, b, c), true, livre, affame);
        assertArrayEquals(new int[] {7, 7, 7}, livre);
        assertArrayEquals(new boolean[] {false, false, false}, affame);
        assertEquals(0, a.recu + b.recu + c.recu);
        int reel = PriorityRelay.distribute(100, List.of(a, b, c), false, new int[3], new boolean[3]);
        assertEquals(reel, simule);
    }

    @Test
    void uneOffreNulleAffameToutesLesSortiesQuiAcceptent() {
        int[] livre = new int[3];
        boolean[] affame = new boolean[3];
        int total = PriorityRelay.distribute(0, List.of(new Sortie(60), new Sortie(0), new Sortie(5)), false,
                livre, affame);
        assertEquals(0, total);
        assertArrayEquals(new int[] {0, 0, 0}, livre);
        assertArrayEquals(new boolean[] {true, false, true}, affame);
    }

    @Test
    void lesLivraisonsSAdditionnentDUnAppelALAutre() {
        Sortie a = new Sortie(60);
        Sortie b = new Sortie(60);
        int[] livre = new int[2];
        PriorityRelay.distribute(50, List.of(a, b), false, livre, new boolean[2]);
        PriorityRelay.distribute(50, List.of(a, b), false, livre, new boolean[2]);
        assertArrayEquals(new int[] {60, 40}, livre);
    }

    @Test
    void lEtatDUneSortie() {
        assertEquals(OutputStatus.NONE, PriorityRelay.status(false, 0, false));
        assertEquals(OutputStatus.NONE, PriorityRelay.status(false, 10, true));
        assertEquals(OutputStatus.FED, PriorityRelay.status(true, 10, false));
        assertEquals(OutputStatus.FED, PriorityRelay.status(true, 0, false));
        assertEquals(OutputStatus.PARTIAL, PriorityRelay.status(true, 10, true));
        assertEquals(OutputStatus.CUT, PriorityRelay.status(true, 0, true));
    }
}
