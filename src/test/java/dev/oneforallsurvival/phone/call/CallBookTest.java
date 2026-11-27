package dev.oneforallsurvival.phone.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.call.CallBook.Call;
import dev.oneforallsurvival.phone.call.CallBook.Party;
import dev.oneforallsurvival.phone.call.CallBook.State;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Les appels en cours, machine à états pure : qui est en appel, sonnerie, décroché, fin, expiration. */
class CallBookTest {

    private static UUID uuid(int n) {
        return UUID.fromString(String.format(Locale.ROOT, "00000000-0000-0000-0000-%012d", n));
    }

    /** Une partie dont joueur, téléphone et carte sont tous propres à {@code n}. */
    private static Party party(int n) {
        return new Party(uuid(100 + n), uuid(200 + n), uuid(300 + n), String.format(Locale.ROOT, "06%08d", n));
    }

    private static Party with(Party base, UUID player, UUID phone, UUID sim) {
        return new Party(player, phone, sim, base.number());
    }

    private static Call started(CallBook book, Party caller, Party callee, long tick) {
        Optional<Call> call = book.start(caller, callee, tick);
        assertTrue(call.isPresent(), "l'appel devait commencer");
        return call.get();
    }

    @Test
    void unAppelCommenceEnSonnerie() {
        CallBook book = new CallBook();
        Call call = started(book, party(1), party(2), 40L);
        assertEquals(State.RINGING, call.state());
        assertEquals(party(1), call.caller());
        assertEquals(party(2), call.callee());
        assertEquals(40L, call.startedTick());
        assertEquals(-1L, call.answeredTick());
        assertFalse(call.callerMuted() || call.calleeMuted() || call.callerSpeaker() || call.calleeSpeaker());
        assertEquals(Optional.of(call), book.of(party(1).player()));
        assertEquals(Optional.of(call), book.of(party(2).player()));
        assertEquals(Optional.of(call), book.get(call.id()));
        assertEquals(Optional.empty(), book.of(party(3).player()));
        assertEquals(List.of(call), book.all());
    }

    @Test
    void chaqueAppelASonNumero() {
        CallBook book = new CallBook();
        Call a = started(book, party(1), party(2), 0L);
        Call b = started(book, party(3), party(4), 0L);
        assertNotEquals(a.id(), b.id());
        assertTrue(a.id() > 0 && b.id() > 0);
        book.end(a.id());
        Call c = started(book, party(1), party(2), 0L);
        assertNotEquals(a.id(), c.id(), "un numéro rendu n'est pas repris aussitôt");
        assertEquals(List.of(b, c), book.all());
    }

    @Test
    void occupeParLeJoueur() {
        CallBook book = new CallBook();
        started(book, party(1), party(2), 0L);
        // Le même joueur, avec un autre téléphone et une autre carte, des deux côtés.
        for (Party busy : List.of(party(1), party(2))) {
            Party other = with(party(5), busy.player(), uuid(901), uuid(902));
            assertEquals(Optional.empty(), book.start(other, party(6), 1L), "appelant " + busy);
            assertEquals(Optional.empty(), book.start(party(6), other, 1L), "appelé " + busy);
        }
        assertEquals(1, book.all().size());
    }

    @Test
    void occupeParLeTelephone() {
        CallBook book = new CallBook();
        started(book, party(1), party(2), 0L);
        // Une copie du téléphone (même identifiant) chez un autre joueur, avec une autre carte.
        for (Party busy : List.of(party(1), party(2))) {
            Party copy = with(party(5), uuid(901), busy.phoneId(), uuid(902));
            assertEquals(Optional.empty(), book.start(copy, party(6), 1L), "appelant " + busy);
            assertEquals(Optional.empty(), book.start(party(6), copy, 1L), "appelé " + busy);
        }
        assertEquals(1, book.all().size());
    }

    @Test
    void occupeParLaCarte() {
        CallBook book = new CallBook();
        started(book, party(1), party(2), 0L);
        for (Party busy : List.of(party(1), party(2))) {
            Party sameSim = with(party(5), uuid(901), uuid(902), busy.simId());
            assertEquals(Optional.empty(), book.start(sameSim, party(6), 1L), "appelant " + busy);
            assertEquals(Optional.empty(), book.start(party(6), sameSim, 1L), "appelé " + busy);
        }
        // Témoin : deux autres parties appellent.
        started(book, party(5), party(6), 1L);
        assertEquals(2, book.all().size());
    }

    @Test
    void onNeSAppellePasSoiMeme() {
        CallBook book = new CallBook();
        Party me = party(1);
        assertEquals(Optional.empty(), book.start(me, with(party(2), me.player(), uuid(901), uuid(902)), 0L));
        assertEquals(Optional.empty(), book.start(me, with(party(2), uuid(901), me.phoneId(), uuid(902)), 0L));
        assertEquals(Optional.empty(), book.start(me, with(party(2), uuid(901), uuid(902), me.simId()), 0L));
        assertEquals(List.of(), book.all());
    }

    @Test
    void seulLAppeleDecrocheEtSeulementEnSonnerie() {
        CallBook book = new CallBook();
        Call call = started(book, party(1), party(2), 10L);
        assertEquals(Optional.empty(), book.answer(call.id(), party(1).player(), 11L), "l'appelant ne décroche pas");
        assertEquals(Optional.empty(), book.answer(call.id(), party(3).player(), 11L), "un tiers ne décroche pas");
        assertEquals(Optional.empty(), book.answer(call.id() + 1, party(2).player(), 11L), "autre numéro");
        assertEquals(State.RINGING, book.get(call.id()).orElseThrow().state());

        Call active = book.answer(call.id(), party(2).player(), 25L).orElseThrow();
        assertEquals(State.ACTIVE, active.state());
        assertEquals(25L, active.answeredTick());
        assertEquals(10L, active.startedTick());
        assertEquals(Optional.of(active), book.of(party(1).player()));
        // Déjà décroché : un second décroché ne change rien.
        assertEquals(Optional.empty(), book.answer(call.id(), party(2).player(), 30L));
        assertEquals(25L, book.get(call.id()).orElseThrow().answeredTick());
    }

    @Test
    void laFinLibereLesDeux() {
        CallBook book = new CallBook();
        Call call = started(book, party(1), party(2), 0L);
        assertEquals(Optional.of(call), book.end(call.id()));
        assertEquals(Optional.empty(), book.end(call.id()), "une seule fin");
        assertEquals(Optional.empty(), book.of(party(1).player()));
        assertEquals(Optional.empty(), book.of(party(2).player()));
        assertEquals(List.of(), book.all());
        // Libres : ils se rappellent.
        started(book, party(2), party(1), 1L);
    }

    @Test
    void laSonnerieExpireALaBorne() {
        CallBook book = new CallBook();
        Call ringing = started(book, party(1), party(2), 100L);
        Call later = started(book, party(3), party(4), 150L);
        Call answered = started(book, party(5), party(6), 0L);
        book.answer(answered.id(), party(6).player(), 1L);
        assertEquals(List.of(), book.expired(699L, 600));
        assertEquals(List.of(ringing), book.expired(700L, 600));
        assertEquals(List.of(ringing, later), book.expired(750L, 600));
        // Rien n'est retiré : le service y met fin lui-même.
        assertEquals(3, book.all().size());
        assertEquals(List.of(), book.expired(99L, 600), "une horloge qui recule n'expire rien");
    }

    @Test
    void silenceEtHautParleurParJoueur() {
        CallBook book = new CallBook();
        Call call = started(book, party(1), party(2), 0L);
        Call muted = book.setMuted(party(1).player(), true).orElseThrow();
        assertTrue(muted.callerMuted());
        assertFalse(muted.calleeMuted());
        Call speaker = book.setSpeaker(party(2).player(), true).orElseThrow();
        assertTrue(speaker.calleeSpeaker() && speaker.callerMuted());
        assertFalse(speaker.callerSpeaker());
        assertEquals(Optional.of(speaker), book.of(party(1).player()));
        assertFalse(book.setMuted(party(1).player(), false).orElseThrow().callerMuted());
        assertEquals(Optional.empty(), book.setMuted(party(3).player(), true), "hors appel");
        assertEquals(Optional.empty(), book.setSpeaker(party(3).player(), true), "hors appel");
        assertEquals(call.id(), speaker.id());
    }

    @Test
    void toutSEfface() {
        CallBook book = new CallBook();
        started(book, party(1), party(2), 0L);
        started(book, party(3), party(4), 0L);
        book.clear();
        assertEquals(List.of(), book.all());
        assertEquals(Optional.empty(), book.of(party(1).player()));
        started(book, party(1), party(4), 0L);
    }

    @Test
    void lesListesRenduesSontDesCopies() {
        CallBook book = new CallBook();
        started(book, party(1), party(2), 0L);
        List<Call> all = book.all();
        assertThrows(UnsupportedOperationException.class, all::clear);
        started(book, party(3), party(4), 0L);
        assertEquals(1, all.size());
    }

    @Test
    void unePartieEstEntiere() {
        assertThrows(NullPointerException.class, () -> new Party(null, uuid(1), uuid(2), "0611111111"));
        assertThrows(NullPointerException.class, () -> new Party(uuid(1), null, uuid(2), "0611111111"));
        assertThrows(NullPointerException.class, () -> new Party(uuid(1), uuid(2), null, "0611111111"));
        assertThrows(NullPointerException.class, () -> new Party(uuid(1), uuid(2), uuid(3), null));
    }

    @Test
    void niNumeroNiCarteDansLesJournaux() {
        CallBook book = new CallBook();
        Call call = started(book, party(1), party(2), 0L);
        String text = call.toString() + call.caller() + call.callee();
        for (Party p : List.of(party(1), party(2))) {
            assertFalse(text.contains(p.number()), "numéro : " + text);
            assertFalse(text.contains(p.simId().toString()), "carte : " + text);
        }
        assertTrue(text.contains(party(1).player().toString()), "le joueur reste lisible : " + text);
    }
}
