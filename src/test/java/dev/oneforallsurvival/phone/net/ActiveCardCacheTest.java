package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * Le décompte des cartes actives gardé par une antenne : il ne parcourt plus les cartes à chaque tic, mais il suit
 * aussitôt toute émission, suspension, réactivation, désactivation et tout retrait d'opérateur.
 */
class ActiveCardCacheTest {

    private static final UUID FONDATEUR = new UUID(0L, 1L);
    private static final UUID AUTRE = new UUID(0L, 2L);

    private static String fonder(PhoneOperators registre, UUID fondateur, String nom) {
        return registre.found(fondateur, nom, nom, 0L).operator().id();
    }

    @Test
    void lesLecturesSuccessivesNeRecomptentPas() {
        PhoneOperators registre = new PhoneOperators();
        String op = fonder(registre, FONDATEUR, "Antenne");
        RandomSource hasard = RandomSource.create(7L);
        for (int i = 0; i < 3; i++) {
            registre.issue(op, null, "", hasard, 0L);
        }
        ActiveCardCache cache = new ActiveCardCache();
        for (int i = 0; i < 100; i++) {
            assertEquals(3, cache.count(registre, op));
        }
        assertEquals(1, cache.recounts(), "cent tics sans changement : un seul décompte");
    }

    @Test
    void chaqueChangementDeCarteSeVoitAuTicSuivant() {
        PhoneOperators registre = new PhoneOperators();
        String op = fonder(registre, FONDATEUR, "Antenne");
        String autre = fonder(registre, AUTRE, "Voisin");
        RandomSource hasard = RandomSource.create(7L);
        ActiveCardCache cache = new ActiveCardCache();
        assertEquals(0, cache.count(registre, op));

        SimCard a = registre.issue(op, null, "", hasard, 0L);
        assertEquals(1, cache.count(registre, op), "émission");
        SimCard b = registre.issue(op, null, "", hasard, 0L);
        assertEquals(2, cache.count(registre, op), "seconde émission");
        registre.setState(a.simId(), SimCard.State.SUSPENDED);
        assertEquals(1, cache.count(registre, op), "suspension");
        registre.setState(a.simId(), SimCard.State.ACTIVE);
        assertEquals(2, cache.count(registre, op), "réactivation");
        registre.setState(b.simId(), SimCard.State.DEACTIVATED);
        assertEquals(1, cache.count(registre, op), "désactivation");
        registre.issue(autre, null, "", hasard, 0L);
        assertEquals(1, cache.count(registre, op), "une carte d'un autre opérateur ne compte pas");
        assertEquals(1, cache.count(registre, autre), "changement d'opérateur demandé : recompté");
        registre.remove(op);
        assertEquals(0, cache.count(registre, op), "opérateur retiré : ses cartes sont désactivées");
    }

    @Test
    void unRegistreReluEstRecompte() {
        PhoneOperators registre = new PhoneOperators();
        String op = fonder(registre, FONDATEUR, "Antenne");
        registre.issue(op, null, "", RandomSource.create(7L), 0L);
        ActiveCardCache cache = new ActiveCardCache();
        assertEquals(1, cache.count(registre, op));
        // Un autre registre (monde rechargé) dont la révision vaut la même chose : il ne doit pas rendre l'ancien compte.
        PhoneOperators neuf = new PhoneOperators();
        assertEquals(0, cache.count(neuf, op));
    }

    @Test
    void uneLectureOuUnRefusNeChangeNiLaRevisionNiLeCompte() {
        PhoneOperators registre = new PhoneOperators();
        String op = fonder(registre, FONDATEUR, "Antenne");
        SimCard carte = registre.issue(op, null, "", RandomSource.create(7L), 0L);
        int avant = registre.cardRevision();
        registre.activeCards(op);
        registre.cardsOf(op);
        registre.setState(carte.simId(), SimCard.State.ACTIVE);
        registre.setState(new UUID(9L, 9L), SimCard.State.SUSPENDED);
        assertEquals(avant, registre.cardRevision(), "rien n'a changé d'état");
    }
}
