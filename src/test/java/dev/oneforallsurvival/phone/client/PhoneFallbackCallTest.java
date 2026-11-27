package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import dev.oneforallsurvival.phone.client.PhoneFallbackCall.Mode;
import dev.oneforallsurvival.phone.client.PhoneFallbackCall.Text;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Lot T3, tâche 7 : l'appel dans l'écran de repli, sans Minecraft. États reçus → mode, boutons (grisés verrouillé
 * comme dans la page), titre, ligne d'état, message de fin pendant deux secondes ; la règle {@code here} de la page.
 */
class PhoneFallbackCallTest {

    private static final String K = PhoneFallbackCall.KEY;

    private static PhoneCallStatePacket etat(Phase phase, int id, String number, String name, long answered,
            boolean here, String detail) {
        return new PhoneCallStatePacket(phase, id, number, name, answered, false, false, here, null, null, detail);
    }

    @Test
    void unAppelEntrantMontreRefuserEtRepondreMemeVerrouille() {
        PhoneFallbackCall c = new PhoneFallbackCall();
        assertEquals(Mode.NONE, c.mode(0));
        assertEquals("", c.accept(etat(Phase.INCOMING, 7, "0612345678", "", -1, true, ""), "", 0));
        assertEquals(Mode.INCOMING, c.mode(0));
        assertEquals(List.of("call-decline", "call-answer"), PhoneFallbackCall.buttons(c.mode(0)));
        for (String b : PhoneFallbackCall.buttons(Mode.INCOMING)) {
            assertTrue(PhoneFallbackCall.active(b, false), b + " vif verrouillé");
        }
        assertEquals(new Text(null, "06 12 34 56 78"), c.title(0), "le numéro, lisible");
        assertEquals(new Text(K + "incoming", ""), c.sub(0, 0));
        c.accept(etat(Phase.INCOMING, 7, "0612345678", "Alice", -1, true, ""), "", 0);
        assertEquals(new Text(null, "Alice"), c.title(0), "le nom du carnet d'abord");
    }

    @Test
    void enCoursSilenceEtHautParleurGrisesVerrouille() {
        PhoneFallbackCall c = new PhoneFallbackCall();
        c.accept(etat(Phase.OUTGOING, 3, "0612345678", "", -1, true, ""), "", 0);
        assertEquals(Mode.LIVE, c.mode(0));
        assertEquals(new Text(K + "outgoing", ""), c.sub(0, 0));
        assertEquals(List.of("call-mute", "call-speaker", "call-hangup"), PhoneFallbackCall.buttons(Mode.LIVE));
        assertFalse(PhoneFallbackCall.active("call-mute", false));
        assertFalse(PhoneFallbackCall.active("call-speaker", false));
        assertTrue(PhoneFallbackCall.active("call-hangup", false), "raccrocher toujours");
        for (String b : PhoneFallbackCall.buttons(Mode.LIVE)) {
            assertTrue(PhoneFallbackCall.active(b, true), b + " vif déverrouillé");
        }
        c.accept(etat(Phase.ACTIVE, 3, "0612345678", "", 10_000L, true, ""), "", 0);
        assertEquals(new Text(null, "1:05"), c.sub(0, 75_000L), "le minuteur depuis le décroché");
        assertTrue(PhoneFallbackCall.buttons(Mode.ENDED).isEmpty() && PhoneFallbackCall.buttons(Mode.NONE).isEmpty());
    }

    @Test
    void laFinResteDeuxSecondesAvecSonMessage() {
        PhoneFallbackCall c = new PhoneFallbackCall();
        c.accept(etat(Phase.ACTIVE, 3, "0612345678", "Alice", 0L, true, ""), "", 0);
        assertEquals("", c.accept(etat(Phase.NONE, 3, "0612345678", "", -1, true, ""), "Appel terminé", 1000));
        assertEquals(Mode.ENDED, c.mode(1000));
        assertNull(c.live());
        assertEquals(new Text(null, "Alice"), c.title(1000), "le titre de l'appel fini");
        assertEquals(new Text(null, "Appel terminé"), c.sub(1500, 0));
        assertEquals(Mode.ENDED, c.mode(1000 + PhoneFallbackCall.ENDING_MS - 1));
        assertEquals(Mode.NONE, c.mode(1000 + PhoneFallbackCall.ENDING_MS), "environ deux secondes");
        assertEquals(new Text(null, ""), c.title(5000));
    }

    @Test
    void unAppelMarqueAilleursNeSeMontrePas() {
        PhoneFallbackCall c = new PhoneFallbackCall();
        assertEquals("", c.accept(etat(Phase.INCOMING, 4, "0612345678", "", -1, false, ""), "", 0));
        assertEquals(Mode.NONE, c.mode(0), "porté par un autre téléphone");
        // Une fin ailleurs ne laisse aucun message ; la réponse à un geste d'ici (phase non nulle) garde le sien.
        assertEquals("", c.accept(etat(Phase.NONE, 4, "0612345678", "", -1, false, ""), "Appel manqué", 0));
        assertEquals("Raccrochez d'abord", c.accept(etat(Phase.ACTIVE, 4, "0612345678", "", 0, false, ""),
                "Raccrochez d'abord", 0));
        assertEquals(Mode.NONE, c.mode(0));
    }

    @Test
    void unAppelRefuseHorsAppelRendSonMessage() {
        PhoneFallbackCall c = new PhoneFallbackCall();
        assertEquals("Numéro inconnu", c.accept(etat(Phase.NONE, 0, "", "", -1, true, ""), "Numéro inconnu", 0));
        assertEquals(Mode.NONE, c.mode(0), "aucun écran d'appel pour un refus");
    }

    @Test
    void lesUrgencesOntLeurTitreEtLaPosition() {
        PhoneFallbackCall appelant = new PhoneFallbackCall();
        appelant.accept(etat(Phase.OUTGOING, 1_000_000_000, "17", "", -1, true, ""), "", 0);
        assertEquals(new Text(K + "sos_police", ""), appelant.title(0), "« Police 17 » chez l'appelant");
        PhoneFallbackCall secours = new PhoneFallbackCall();
        String detail = "Bob · 1 64 -3 · overworld";
        secours.accept(etat(Phase.INCOMING, 1_000_000_000, "15", "Bob", -1, true, detail), "", 0);
        assertEquals(new Text("message.oneforallsurvival.call.emergency.medical", ""), secours.title(0),
                "« Urgence secours », le texte du serveur");
        assertEquals(new Text(null, detail), secours.sub(0, 0), "la position sous le titre");
        secours.accept(etat(Phase.ACTIVE, 1_000_000_000, "15", "Bob", 0L, true, detail), "", 0);
        assertEquals(new Text(null, detail + " · 0:42"), secours.sub(0, 42_000L), "et la durée en ligne");
    }

    @Test
    void lesDureesSEcriventCommeDansLaPage() {
        assertEquals("0:00", PhoneFallbackCall.duration(0));
        assertEquals("12:40", PhoneFallbackCall.duration(760));
        assertEquals("1:02:09", PhoneFallbackCall.duration(3729));
        assertEquals("06 12 34 56 78", PhoneFallbackCall.pairs("0612345678"));
        assertEquals("17", PhoneFallbackCall.pairs("17"));
        assertEquals("06 1", PhoneFallbackCall.pairs("061"));
    }
}
