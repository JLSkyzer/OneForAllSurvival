package dev.oneforallsurvival.phone.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.call.ShareBook.Request;
import dev.oneforallsurvival.phone.call.ShareBook.Send;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * « Partager mon numéro » (retours d'essai du 27/11), la machine à états pure des demandes : une seule en attente d'un
 * envoyeur vers un destinataire, une demande posée toutes les 3 s par envoyeur, 60 s de vie, une réponse qui ne vaut
 * que pour son destinataire, le départ d'un joueur, le plafond du serveur.
 */
class ShareBookTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID SIM = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final long T0 = 1_800_000_000_000L;

    private static ShareBook.SendResult envoi(ShareBook book, UUID de, UUID a, long now) {
        return book.send(de, "De", SIM, "0612345678", "Alex", a, "A", now);
    }

    @Test
    void uneDemandePoseeSeRetrouveChezSonDestinataireSeulement() {
        ShareBook book = new ShareBook();
        ShareBook.SendResult r = envoi(book, A, B, T0);
        assertEquals(Send.SENT, r.outcome());
        Request req = r.request().orElseThrow();
        assertTrue(req.id() > 0);
        assertEquals("0612345678", req.number());
        assertEquals("Alex", req.name());
        assertTrue(book.forReceiver(req.id(), B).isPresent(), "le destinataire");
        assertTrue(book.forReceiver(req.id(), A).isEmpty(), "l'envoyeur ne répond pas pour lui");
        assertTrue(book.forReceiver(req.id(), C).isEmpty(), "un tiers non plus");
        assertTrue(book.forReceiver(req.id() + 1, B).isEmpty(), "un autre numéro");
        assertEquals(1, book.all().size(), "rien n'a changé");
        assertTrue(book.remove(req.id()).isPresent());
        assertTrue(book.remove(req.id()).isEmpty(), "une demande ne se retire qu'une fois");
    }

    @Test
    void uneSeuleEnAttenteVersLeMemeDestinataire() {
        ShareBook book = new ShareBook();
        assertEquals(Send.SENT, envoi(book, A, B, T0).outcome());
        assertEquals(Send.PENDING, envoi(book, A, B, T0 + ShareBook.GAP_MILLIS * 5).outcome());
        // Dans l'autre sens, c'est une autre demande.
        assertEquals(Send.SENT, envoi(book, B, A, T0 + 10).outcome());
        assertEquals(2, book.all().size());
    }

    @Test
    void uneDemandeToutesLesTroisSecondesParEnvoyeur() {
        ShareBook book = new ShareBook();
        assertEquals(Send.SENT, envoi(book, A, B, T0).outcome());
        assertEquals(Send.THROTTLED, envoi(book, A, C, T0 + ShareBook.GAP_MILLIS - 1).outcome());
        assertEquals(1, book.all().size(), "le refus ne pose rien");
        // Un autre envoyeur n'est pas freiné par A.
        assertEquals(Send.SENT, envoi(book, C, B, T0 + 1).outcome());
        assertEquals(Send.SENT, envoi(book, A, C, T0 + ShareBook.GAP_MILLIS).outcome(), "3 s pile : permis");
    }

    @Test
    void unRefusNeRemetPasLeFrein() {
        ShareBook book = new ShareBook();
        assertEquals(Send.SENT, envoi(book, A, B, T0).outcome());
        assertEquals(Send.PENDING, envoi(book, A, B, T0 + ShareBook.GAP_MILLIS).outcome());
        // Le refus d'à l'instant n'a pas remis le frein : une autre demande passe aussitôt.
        assertEquals(Send.SENT, envoi(book, A, C, T0 + ShareBook.GAP_MILLIS).outcome());
    }

    @Test
    void uneHorlogeQuiReculeNeBloquePas() {
        ShareBook book = new ShareBook();
        assertEquals(Send.SENT, envoi(book, A, B, T0).outcome());
        assertEquals(Send.SENT, envoi(book, A, C, T0 - 3_600_000L).outcome());
    }

    @Test
    void soiMemeEstRefuse() {
        assertEquals(Send.SELF, envoi(new ShareBook(), A, A, T0).outcome());
    }

    @Test
    void soixanteSecondesDeVie() {
        ShareBook book = new ShareBook();
        Request r = envoi(book, A, B, T0).request().orElseThrow();
        assertTrue(book.expire(T0 + ShareBook.TTL_MILLIS - 1).isEmpty());
        List<Request> tombees = book.expire(T0 + ShareBook.TTL_MILLIS);
        assertEquals(List.of(r), tombees);
        assertTrue(book.forReceiver(r.id(), B).isEmpty(), "une demande tombée ne se répond plus");
    }

    @Test
    void leDepartDUnJoueurFaitTomberSesDemandes() {
        ShareBook book = new ShareBook();
        Request ab = envoi(book, A, B, T0).request().orElseThrow();
        Request cb = envoi(book, C, B, T0).request().orElseThrow();
        Request ca = envoi(book, C, A, T0 + ShareBook.GAP_MILLIS).request().orElseThrow();
        assertEquals(List.of(ab, ca), book.dropPlayer(A));
        assertEquals(List.of(cb), book.all());
        // Le frein de A est oublié avec lui.
        assertEquals(Send.SENT, envoi(book, A, C, T0 + 1).outcome());
    }

    @Test
    void lePlafondDuServeur() {
        ShareBook book = new ShareBook();
        for (int i = 0; i < ShareBook.MAX_PENDING; i++) {
            assertEquals(Send.SENT, envoi(book, new UUID(1, i), B, T0).outcome());
        }
        assertEquals(Send.FULL, envoi(book, A, B, T0).outcome());
        assertFalse(book.all().stream().anyMatch(r -> r.sender().equals(A)));
    }

    @Test
    void niNumeroNiCarteDansUnJournal() {
        Request r = envoi(new ShareBook(), A, B, T0).request().orElseThrow();
        assertFalse(r.toString().contains("0612345678"));
        assertFalse(r.toString().contains(SIM.toString()));
    }
}
