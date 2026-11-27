package dev.oneforallsurvival.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Les durées attendues sont écrites en clair : jamais recalculées par le code testé. */
class PhoneLockTest {

    private static final int MAX = 5;
    /** Les réglages livrés : une minute à la première série, une heure au plus. */
    private static final long BASE = 60_000L;
    private static final long CAP = 3_600_000L;

    private static PhoneLock.Outcome wrong(int failures, long until, long now) {
        return PhoneLock.attempt(false, failures, until, 0, now, MAX, BASE, CAP);
    }

    private static PhoneLock.Outcome essai(boolean bon, PhoneLock.Outcome avant, long now) {
        return PhoneLock.attempt(bon, avant.failures(), avant.lockedUntil(), avant.level(), now, MAX, BASE, CAP);
    }

    /** Cinq faux codes à l'instant {@code now}, blocage précédent expiré : les quatre premiers ne bloquent pas. */
    private static PhoneLock.Outcome serie(PhoneLock.Outcome avant, long now, long base, long max) {
        PhoneLock.Outcome out = avant;
        for (int i = 1; i <= MAX; i++) {
            assertTrue(now >= out.lockedUntil(), "faux code " + i + " donné pendant un blocage : " + out);
            out = PhoneLock.attempt(false, out.failures(), out.lockedUntil(), out.level(), now, MAX, base, max);
            assertFalse(out.accepted());
            if (i < MAX) {
                assertEquals(i, out.failures());
                assertEquals(avant.level(), out.level(), "le niveau ne monte qu'à la série entière");
            }
        }
        assertEquals(0, out.failures());
        return out;
    }

    private static final PhoneLock.Outcome NEUF = new PhoneLock.Outcome(false, 0, 0L, 0);

    @Test
    void quatreFauxNeBloquentPas() {
        PhoneLock.Outcome out = NEUF;
        for (int i = 0; i < 4; i++) {
            out = essai(false, out, 1_000L);
            assertFalse(out.accepted());
        }
        assertEquals(4, out.failures());
        assertEquals(0L, out.lockedUntil());
        assertEquals(0, out.level());
    }

    @Test
    void cinquiemeFauxBloque() {
        PhoneLock.Outcome out = wrong(4, 0L, 1_000L);
        assertFalse(out.accepted());
        assertEquals(61_000L, out.lockedUntil());
        assertEquals(0, out.failures());
        assertEquals(1, out.level());
    }

    @Test
    void laDureeDoubleJusquAuPlafond() {
        long[] attendues = {60_000L, 120_000L, 240_000L, 480_000L, 960_000L, 1_920_000L, 3_600_000L, 3_600_000L,
                3_600_000L};
        for (int serie = 0; serie < attendues.length; serie++) {
            assertEquals(attendues[serie], PhoneLock.duration(serie, BASE, CAP), "série " + serie);
        }
    }

    @Test
    void lesSeriesSuccessivesBloquentDeuxFoisPlusLongtemps() {
        long[] instants = {1_000L, 61_000L, 181_000L, 421_000L, 901_000L, 1_861_000L, 3_781_000L, 7_381_000L};
        long[] fins = {61_000L, 181_000L, 421_000L, 901_000L, 1_861_000L, 3_781_000L, 7_381_000L, 10_981_000L};
        PhoneLock.Outcome out = NEUF;
        for (int i = 0; i < instants.length; i++) {
            out = serie(out, instants[i], BASE, CAP);
            assertEquals(fins[i], out.lockedUntil(), "fin de la série " + (i + 1));
            assertEquals(i + 1, out.level());
            // Une milliseconde avant la fin, même le bon code est refusé, et rien ne change.
            PhoneLock.Outcome tot = essai(true, out, fins[i] - 1);
            assertFalse(tot.accepted(), "série " + (i + 1) + " finie trop tôt");
            assertEquals(out, tot);
        }
    }

    @Test
    void leBonCodeRemetLeNiveauAZero() {
        PhoneLock.Outcome deux = serie(serie(NEUF, 1_000L, BASE, CAP), 61_000L, BASE, CAP);
        assertEquals(2, deux.level());
        PhoneLock.Outcome bon = essai(true, deux, 181_000L);
        assertTrue(bon.accepted());
        assertEquals(0, bon.level());
        assertEquals(0, bon.failures());
        PhoneLock.Outcome apres = serie(bon, 200_000L, BASE, CAP);
        assertEquals(260_000L, apres.lockedUntil(), "après un bon code, de nouveau une minute");
        assertEquals(1, apres.level());
    }

    @Test
    void niLeTempsNiUnFauxNeBaissentLeNiveau() {
        PhoneLock.Outcome une = serie(NEUF, 1_000L, BASE, CAP);
        long unAnPlusTard = 31_536_000_000L;
        assertEquals(1, essai(false, une, unAnPlusTard).level());
        PhoneLock.Outcome deux = serie(une, unAnPlusTard, BASE, CAP);
        assertEquals(unAnPlusTard + 120_000L, deux.lockedUntil());
        assertEquals(2, deux.level());
    }

    @Test
    void pendantLeBlocageMemeLeBonCodeEstRefuse() {
        PhoneLock.Outcome out = PhoneLock.attempt(true, 0, 61_000L, 1, 30_000L, MAX, BASE, CAP);
        assertFalse(out.accepted());
        assertEquals(61_000L, out.lockedUntil());
        assertEquals(1, out.level(), "un bon code refusé ne remet pas le niveau à zéro");
    }

    @Test
    void pendantLeBlocageUnFauxNeCompteNiNeProlonge() {
        PhoneLock.Outcome out = PhoneLock.attempt(false, 0, 61_000L, 1, 30_000L, MAX, BASE, CAP);
        assertFalse(out.accepted());
        assertEquals(0, out.failures());
        assertEquals(61_000L, out.lockedUntil());
        assertEquals(1, out.level());
    }

    @Test
    void lePremierInstantLibreAccepte() {
        assertTrue(PhoneLock.attempt(true, 0, 61_000L, 1, 61_000L, MAX, BASE, CAP).accepted());
    }

    @Test
    void leBonCodeRemetLesEchecsAZero() {
        PhoneLock.Outcome out = PhoneLock.attempt(true, 2, 0L, 0, 5_000L, MAX, BASE, CAP);
        assertTrue(out.accepted());
        assertEquals(0, out.failures());
    }

    @Test
    void sansBlocageConfigureRienNeBloqueQuelQueSoitLeNiveau() {
        for (int level : new int[] {0, 1, 7, Integer.MAX_VALUE}) {
            assertEquals(0L, PhoneLock.duration(level, 0L, CAP), "niveau " + level);
            PhoneLock.Outcome out = PhoneLock.attempt(false, 0, 0L, level, 1_000L, 1, 0L, CAP);
            assertEquals(1_000L, out.lockedUntil(), "niveau " + level);
            assertEquals(0, out.failures());
            assertTrue(PhoneLock.attempt(true, 0, out.lockedUntil(), out.level(), 1_000L, 1, 0L, CAP).accepted(),
                    "niveau " + level + " : le bon code passe au même instant");
        }
        // Une fin de blocage restée d'un réglage précédent ne bloque plus.
        assertTrue(PhoneLock.attempt(true, 0, 3_000_000L, 4, 1_000L, MAX, 0L, CAP).accepted());
    }

    @Test
    void unPlafondSousLaBaseVautLaBase() {
        for (long plafond : new long[] {0L, 10_000L, 59_999L, 60_000L}) {
            for (int serie : new int[] {0, 1, 5, Integer.MAX_VALUE}) {
                assertEquals(60_000L, PhoneLock.duration(serie, BASE, plafond),
                        "plafond " + plafond + ", série " + serie);
            }
        }
        PhoneLock.Outcome deux = serie(serie(NEUF, 1_000L, BASE, 10_000L), 61_000L, BASE, 10_000L);
        assertEquals(121_000L, deux.lockedUntil());
    }

    @Test
    void blocageEnormeRamenePlafond() {
        PhoneLock.Outcome out = PhoneLock.attempt(true, 0, Long.MAX_VALUE, 0, 1_000L, MAX, BASE, CAP);
        assertFalse(out.accepted());
        assertEquals(61_000L, out.lockedUntil(), "niveau 0 (ancienne sauvegarde) : la base");
    }

    @Test
    void leBlocageEstBorneParLaSerieQuiLAPose() {
        // niveau : séries comptées, la dernière a posé le blocage (niveau 3 : la troisième, 240 s)
        int[] niveaux = {0, 1, 2, 3, 6, 7, 40};
        long[] plafonds = {65_000L, 65_000L, 125_000L, 245_000L, 1_925_000L, 3_605_000L, 3_605_000L};
        for (int i = 0; i < niveaux.length; i++) {
            assertEquals(plafonds[i], PhoneLock.capped(Long.MAX_VALUE, 5_000L, niveaux[i], BASE, CAP),
                    "niveau " + niveaux[i]);
        }
        assertEquals(30_000L, PhoneLock.capped(30_000L, 5_000L, 3, BASE, CAP), "sous la borne : inchangé");
    }

    @Test
    void horlogeQuiReculeNeBloquePasPlusQueLaDuree() {
        // blocage de la troisième série posé jusqu'à 1 240 000, puis l'horloge revient à 5 000
        PhoneLock.Outcome out = PhoneLock.attempt(false, 0, 1_240_000L, 3, 5_000L, MAX, BASE, CAP);
        assertEquals(245_000L, out.lockedUntil());
        assertEquals(3, out.level(), "l'horloge qui recule ne touche pas au niveau");
        assertTrue(PhoneLock.attempt(true, 0, out.lockedUntil(), out.level(), 245_000L, MAX, BASE, CAP).accepted());
    }

    @Test
    void unNiveauEnormeResteAuPlafondSansDepassement() {
        assertEquals(3_605_000L, PhoneLock.capped(Long.MAX_VALUE, 5_000L, Integer.MAX_VALUE, BASE, CAP));
        PhoneLock.Outcome out = PhoneLock.attempt(false, 4, 0L, Integer.MAX_VALUE, 5_000L, MAX, BASE, CAP);
        assertEquals(3_605_000L, out.lockedUntil());
        assertEquals(Integer.MAX_VALUE, out.level(), "le niveau s'arrête au plus grand entier");
    }

    @Test
    void unNiveauNegatifVautZero() {
        for (int niveau : new int[] {-1, -5, Integer.MIN_VALUE}) {
            assertEquals(65_000L, PhoneLock.capped(Long.MAX_VALUE, 5_000L, niveau, BASE, CAP), "borne, " + niveau);
            PhoneLock.Outcome out = PhoneLock.attempt(false, 4, 0L, niveau, 5_000L, MAX, BASE, CAP);
            assertEquals(65_000L, out.lockedUntil(), "série, " + niveau);
            assertEquals(1, out.level(), "niveau après la série, " + niveau);
        }
    }

    @Test
    void lesDureesEnormesNeDepassentPasUnLong() {
        long moitie = Long.MAX_VALUE / 2 + 1;
        assertEquals(Long.MAX_VALUE, PhoneLock.duration(1, moitie, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, PhoneLock.duration(Integer.MAX_VALUE, 1L, Long.MAX_VALUE));
        assertEquals(4_611_686_018_427_387_904L, PhoneLock.duration(62, 1L, Long.MAX_VALUE));
        PhoneLock.Outcome out = PhoneLock.attempt(false, 4, 0L, 200, 5_000L, MAX, 1L, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, out.lockedUntil());
    }

    @Test
    void plafondSansDepassementDeLong() {
        PhoneLock.Outcome out = PhoneLock.attempt(true, 0, Long.MAX_VALUE, 9, Long.MAX_VALUE - 10, MAX, BASE, CAP);
        assertEquals(Long.MAX_VALUE, out.lockedUntil());
        PhoneLock.Outcome pose = PhoneLock.attempt(false, 4, 0L, 9, Long.MAX_VALUE - 10, MAX, BASE, CAP);
        assertEquals(Long.MAX_VALUE, pose.lockedUntil(), "un blocage posé au bout du temps ne repasse pas négatif");
    }

    @Test
    void blocageNegatifNeBloquePas() {
        assertTrue(PhoneLock.attempt(true, 0, -5L, 0, 1_000L, MAX, BASE, CAP).accepted());
    }

    /**
     * Un compte d'échecs au dernier essai ou au-delà (réglage abaissé, sauvegarde corrompue jusqu'au plus grand entier)
     * bloque au premier faux code : il ne repasse jamais négatif en laissant des essais libres.
     */
    @Test
    void unCompteDEchecsCorrompuBloqueAussitot() {
        for (int echecs : new int[] {4, 5, 7, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            PhoneLock.Outcome out = PhoneLock.attempt(false, echecs, 0L, 0, 5_000L, MAX, BASE, CAP);
            assertFalse(out.accepted(), "échecs " + echecs);
            assertEquals(65_000L, out.lockedUntil(), "échecs " + echecs);
            assertEquals(0, out.failures(), "échecs " + echecs);
            assertEquals(1, out.level(), "échecs " + echecs);
        }
        assertTrue(PhoneLock.attempt(true, Integer.MAX_VALUE, 0L, 0, 5_000L, MAX, BASE, CAP).accepted(),
                "le bon code passe encore");
    }

    /** Les réglages livrés, lus sans fichier chargé. */
    @Test
    void lesReglagesDuBlocageOntLeursValeursParDefaut() {
        assertEquals(5, PhoneConfig.maxAttempts());
        assertEquals(60, PhoneConfig.lockoutSeconds());
        assertEquals(3_600, PhoneConfig.lockoutMaxSeconds());
    }
}
