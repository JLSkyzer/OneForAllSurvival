package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Retours d'essai du 27/11, section 3 : la mémoire des jetons vus chez un joueur, qui permet de rendre son jeton à une
 * carte que l'inventaire créatif renvoie sans. Elle ne rend qu'à ce joueur ce qui a été vu chez lui, elle est bornée
 * et s'oublie.
 */
class CreativeSimTokensTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e5");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");

    @Test
    void unJetonVuChezUnJoueurLuiEstRendu() {
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        UUID carte = UUID.randomUUID();
        UUID jeton = UUID.randomUUID();
        memoire.note(ALEX, carte, jeton);
        assertEquals(Optional.of(jeton), memoire.token(ALEX, carte));
    }

    @Test
    void unAutreJoueurNeRecoitRien() {
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        UUID carte = UUID.randomUUID();
        memoire.note(ALEX, carte, UUID.randomUUID());
        assertEquals(Optional.empty(), memoire.token(BOB, carte));
        assertEquals(Optional.empty(), memoire.token(ALEX, UUID.randomUUID()), "carte jamais vue");
    }

    @Test
    void leDernierJetonVuLEmporte() {
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        UUID carte = UUID.randomUUID();
        UUID ancien = UUID.randomUUID();
        UUID neuf = UUID.randomUUID();
        memoire.note(ALEX, carte, ancien);
        memoire.note(ALEX, carte, neuf);
        assertEquals(Optional.of(neuf), memoire.token(ALEX, carte));
        assertEquals(1, memoire.size(ALEX));
    }

    @Test
    void laMemoireDUnJoueurEstBornee() {
        CreativeSimTokens memoire = new CreativeSimTokens(3);
        UUID[] cartes = new UUID[5];
        for (int i = 0; i < cartes.length; i++) {
            cartes[i] = UUID.randomUUID();
            memoire.note(ALEX, cartes[i], UUID.randomUUID());
        }
        assertEquals(3, memoire.size(ALEX));
        assertEquals(Optional.empty(), memoire.token(ALEX, cartes[0]), "la plus ancienne est oubliée");
        assertEquals(Optional.empty(), memoire.token(ALEX, cartes[1]));
        assertTrue(memoire.token(ALEX, cartes[4]).isPresent(), "la plus récente est gardée");
    }

    @Test
    void uneCarteRevueRedevientRecente() {
        CreativeSimTokens memoire = new CreativeSimTokens(2);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        memoire.note(ALEX, a, UUID.randomUUID());
        memoire.note(ALEX, b, UUID.randomUUID());
        memoire.note(ALEX, a, UUID.randomUUID());
        memoire.note(ALEX, c, UUID.randomUUID());
        assertTrue(memoire.token(ALEX, a).isPresent(), "revue, elle reste");
        assertEquals(Optional.empty(), memoire.token(ALEX, b));
    }

    @Test
    void laDeconnexionOublieLeJoueurSeul() {
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        UUID carte = UUID.randomUUID();
        memoire.note(ALEX, carte, UUID.randomUUID());
        memoire.note(BOB, carte, UUID.randomUUID());
        memoire.forget(ALEX);
        assertEquals(Optional.empty(), memoire.token(ALEX, carte));
        assertEquals(0, memoire.size(ALEX));
        assertTrue(memoire.token(BOB, carte).isPresent(), "Bob garde ce qu'il a vu");
        memoire.clear();
        assertEquals(Optional.empty(), memoire.token(BOB, carte));
    }

    @Test
    void leNombreDeJoueursSuitLesJoueursConnus() {
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        memoire.note(ALEX, UUID.randomUUID(), UUID.randomUUID());
        memoire.note(BOB, UUID.randomUUID(), UUID.randomUUID());
        assertEquals(2, memoire.players());
        memoire.forget(BOB);
        assertEquals(1, memoire.players());
    }

    @Test
    void uneCapaciteNulleEstRefusee() {
        try {
            new CreativeSimTokens(0);
        } catch (IllegalArgumentException e) {
            return;
        }
        throw new AssertionError("capacité 0 acceptée");
    }
}
