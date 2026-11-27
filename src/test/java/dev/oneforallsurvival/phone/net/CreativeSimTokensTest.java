package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Retours d'essai du 27/11, section 3 : l'empreinte publique d'un jeton de carte SIM, que le client reçoit à sa place,
 * et la mémoire des jetons vus chez un joueur, qui rend à une pile de l'inventaire créatif le jeton de son empreinte.
 * Elle ne rend qu'à ce joueur ce qui a été vu chez lui, elle est bornée et s'oublie.
 */
class CreativeSimTokensTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e5");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");

    @Test
    void lEmpreinteEstLeDebutDuSha256DuJeton() {
        // Valeurs calculées hors du code testé (SHA-256 des 16 octets, 128 premiers bits).
        assertEquals(UUID.fromString("374708ff-f771-9dd5-979e-c875d56cd228"), CreativeSimTokens.mark(new UUID(0, 0)));
        assertEquals(UUID.fromString("84f7e5c1-5968-6635-b92f-b3d9cda85970"), CreativeSimTokens.mark(ALEX));
    }

    @Test
    void deuxJetonsOntDeuxEmpreintesAutresQueEux() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(CreativeSimTokens.mark(a), CreativeSimTokens.mark(a), "stable");
        assertNotEquals(CreativeSimTokens.mark(a), CreativeSimTokens.mark(b));
        assertNotEquals(a, CreativeSimTokens.mark(a), "l'empreinte n'est pas le jeton");
    }

    @Test
    void unJetonVuChezUnJoueurLuiEstRendu() {
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        UUID jeton = UUID.randomUUID();
        memoire.note(ALEX, jeton);
        assertEquals(Optional.of(jeton), memoire.token(ALEX, CreativeSimTokens.mark(jeton)));
    }

    @Test
    void uneEmpreinteInconnueDeCeJoueurNeRendRien() {
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        UUID jeton = UUID.randomUUID();
        memoire.note(ALEX, jeton);
        memoire.note(BOB, UUID.randomUUID());
        assertEquals(Optional.empty(), memoire.token(BOB, CreativeSimTokens.mark(jeton)), "vu chez un autre");
        assertEquals(Optional.empty(), memoire.token(ALEX, CreativeSimTokens.mark(UUID.randomUUID())), "jamais vu");
        assertEquals(Optional.empty(), memoire.token(ALEX, jeton), "le jeton n'est pas une empreinte");
    }

    @Test
    void chaqueJetonGardeSonEmpreinte() {
        // Deux piles de la même carte, deux jetons : aucun ne remplace l'autre, quel que soit l'ordre des vues.
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        UUID ancien = UUID.randomUUID();
        UUID neuf = UUID.randomUUID();
        memoire.note(ALEX, neuf);
        memoire.note(ALEX, ancien);
        assertEquals(Optional.of(neuf), memoire.token(ALEX, CreativeSimTokens.mark(neuf)));
        assertEquals(Optional.of(ancien), memoire.token(ALEX, CreativeSimTokens.mark(ancien)));
        memoire.note(ALEX, neuf);
        assertEquals(2, memoire.size(ALEX), "un jeton revu ne compte pas deux fois");
    }

    @Test
    void laMemoireDUnJoueurEstBornee() {
        CreativeSimTokens memoire = new CreativeSimTokens(3);
        UUID[] jetons = new UUID[5];
        for (int i = 0; i < jetons.length; i++) {
            jetons[i] = UUID.randomUUID();
            memoire.note(ALEX, jetons[i]);
        }
        assertEquals(3, memoire.size(ALEX));
        assertEquals(Optional.empty(), memoire.token(ALEX, CreativeSimTokens.mark(jetons[0])), "le plus ancien oublié");
        assertEquals(Optional.empty(), memoire.token(ALEX, CreativeSimTokens.mark(jetons[1])));
        assertTrue(memoire.token(ALEX, CreativeSimTokens.mark(jetons[4])).isPresent(), "le plus récent est gardé");
    }

    @Test
    void unJetonRevuRedevientRecent() {
        CreativeSimTokens memoire = new CreativeSimTokens(2);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        memoire.note(ALEX, a);
        memoire.note(ALEX, b);
        memoire.note(ALEX, a);
        memoire.note(ALEX, c);
        assertTrue(memoire.token(ALEX, CreativeSimTokens.mark(a)).isPresent(), "revu, il reste");
        assertEquals(Optional.empty(), memoire.token(ALEX, CreativeSimTokens.mark(b)));
    }

    @Test
    void laDeconnexionOublieLeJoueurSeul() {
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        UUID jeton = UUID.randomUUID();
        memoire.note(ALEX, jeton);
        memoire.note(BOB, jeton);
        memoire.forget(ALEX);
        assertEquals(Optional.empty(), memoire.token(ALEX, CreativeSimTokens.mark(jeton)));
        assertEquals(0, memoire.size(ALEX));
        assertTrue(memoire.token(BOB, CreativeSimTokens.mark(jeton)).isPresent(), "Bob garde ce qu'il a vu");
        memoire.clear();
        assertEquals(Optional.empty(), memoire.token(BOB, CreativeSimTokens.mark(jeton)));
    }

    @Test
    void leNombreDeJoueursSuitLesJoueursConnus() {
        CreativeSimTokens memoire = new CreativeSimTokens(8);
        memoire.note(ALEX, UUID.randomUUID());
        memoire.note(BOB, UUID.randomUUID());
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
