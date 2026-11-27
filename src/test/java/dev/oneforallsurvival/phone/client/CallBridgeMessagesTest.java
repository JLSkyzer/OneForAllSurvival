package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket.Action;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Lot T3, tâche 4b : les gestes d'appel et de carnet de la page du téléphone. Seuls les gestes du contrat, aux champs
 * exacts et bien typés, textes de 32 au plus ; le numéro de l'appel visé vient du dernier état reçu par Java, jamais de
 * la page. Tout le reste ne part pas (le serveur revérifie de toute façon).
 */
class CallBridgeMessagesTest {

    private static final int SESSION = 17;
    private static final PhoneCallStatePacket ENTRANT = etat(Phase.INCOMING, 42);
    private static final PhoneCallStatePacket SORTANT = etat(Phase.OUTGOING, 43);
    private static final PhoneCallStatePacket EN_COURS = etat(Phase.ACTIVE, 44);
    private static final PhoneCallStatePacket FINI = etat(Phase.NONE, 44);

    private static PhoneCallStatePacket etat(Phase phase, int id) {
        return new PhoneCallStatePacket(phase, id, "0612345678", "Maman", -1L, false, false, true, null, null);
    }

    private static Optional<PhoneCallActionPacket> action(String json, PhoneCallStatePacket last) {
        JsonObject message = JsonParser.parseString(json).getAsJsonObject();
        return CallBridgeMessages.toAction(message, SESSION, last);
    }

    private static Optional<PhoneCallActionPacket> action(String json) {
        return action(json, null);
    }

    @Test
    void lesGestesDuContratSontReconnus() {
        for (String a : new String[] {"callDial", "callAnswer", "callDecline", "callHangup", "callMute",
                "callSpeaker", "contactSave", "contactRemove", "phoneApp"}) {
            assertTrue(CallBridgeMessages.isCall(a), a);
        }
        for (String a : new String[] {"unlock", "opClose", "call", "CallDial", "callDIAL", "contact", "", "phone"}) {
            assertFalse(CallBridgeMessages.isCall(a), a);
        }
        assertFalse(CallBridgeMessages.isCall(null));
    }

    @Test
    void composerDonneLeNumeroEtLaSession() {
        PhoneCallActionPacket p = action("{\"a\":\"callDial\",\"number\":\"0612345678\"}").orElseThrow();
        assertEquals(Action.DIAL, p.action());
        assertEquals("0612345678", p.first());
        assertEquals("", p.second());
        assertEquals(SESSION, p.session());
        assertEquals("17", action("{\"a\":\"callDial\",\"number\":\"17\"}").orElseThrow().first());
        String trenteDeux = "1".repeat(32);
        assertEquals(trenteDeux, action("{\"a\":\"callDial\",\"number\":\"" + trenteDeux + "\"}").orElseThrow()
                .first());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"callDial\"}",
        "{\"a\":\"callDial\",\"number\":\"\"}",
        "{\"a\":\"callDial\",\"number\":\"111111111111111111111111111111111\"}",
        "{\"a\":\"callDial\",\"number\":\"06 12 34 56 78\"}",
        "{\"a\":\"callDial\",\"number\":\"06a2345678\"}",
        "{\"a\":\"callDial\",\"number\":\"+33612345678\"}",
        "{\"a\":\"callDial\",\"number\":\"０６１２３４５６７８\"}",
        "{\"a\":\"callDial\",\"number\":612345678}",
        "{\"a\":\"callDial\",\"number\":null}",
        "{\"a\":\"callDial\",\"number\":[\"0612345678\"]}",
        "{\"a\":\"callDial\",\"number\":\"0612345678\",\"name\":\"Maman\"}",
        "{\"a\":\"callDial\",\"number\":\"0612345678\",\"session\":3}"
    })
    void unNumeroComposeFauxNePartPas(String json) {
        assertEquals(Optional.empty(), action(json), json);
    }

    @Test
    void repondreEtRefuserVisentLAppelEntrantDuDernierEtat() {
        PhoneCallActionPacket repondre = action("{\"a\":\"callAnswer\"}", ENTRANT).orElseThrow();
        assertEquals(Action.ANSWER, repondre.action());
        assertEquals("42", repondre.first());
        assertEquals(SESSION, repondre.session());
        PhoneCallActionPacket refuser = action("{\"a\":\"callDecline\"}", ENTRANT).orElseThrow();
        assertEquals(Action.DECLINE, refuser.action());
        assertEquals("42", refuser.first());
    }

    @Test
    void raccrocherViseLAppelSortantOuEnCours() {
        PhoneCallActionPacket sortant = action("{\"a\":\"callHangup\"}", SORTANT).orElseThrow();
        assertEquals(Action.HANGUP, sortant.action());
        assertEquals("43", sortant.first());
        assertEquals("44", action("{\"a\":\"callHangup\"}", EN_COURS).orElseThrow().first());
    }

    /** Sans appel de la bonne phase dans le dernier état, rien ne part : la page ne donne jamais le numéro d'appel. */
    @Test
    void sansAppelDeLaBonnePhaseRienNePart() {
        for (PhoneCallStatePacket last : new PhoneCallStatePacket[] {null, FINI, SORTANT, EN_COURS}) {
            assertEquals(Optional.empty(), action("{\"a\":\"callAnswer\"}", last), String.valueOf(last));
            assertEquals(Optional.empty(), action("{\"a\":\"callDecline\"}", last), String.valueOf(last));
        }
        for (PhoneCallStatePacket last : new PhoneCallStatePacket[] {null, FINI, ENTRANT}) {
            assertEquals(Optional.empty(), action("{\"a\":\"callHangup\"}", last), String.valueOf(last));
        }
        assertEquals(Optional.empty(), action("{\"a\":\"callAnswer\"}", etat(Phase.INCOMING, 0)));
        assertEquals(Optional.empty(), action("{\"a\":\"callHangup\"}", etat(Phase.ACTIVE, -5)));
        // Revue de la tâche 4a, I3 : un appel porté par un autre téléphone que celui de l'écran.
        for (Phase phase : new Phase[] {Phase.INCOMING, Phase.OUTGOING, Phase.ACTIVE}) {
            PhoneCallStatePacket ailleurs = new PhoneCallStatePacket(phase, 42, "0612345678", "", -1L, false, false,
                    false, null, null);
            for (String a : new String[] {"callAnswer", "callDecline", "callHangup"}) {
                assertEquals(Optional.empty(), action("{\"a\":\"" + a + "\"}", ailleurs), a + " " + phase);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"callAnswer\",\"id\":42}",
        "{\"a\":\"callAnswer\",\"id\":\"42\"}",
        "{\"a\":\"callDecline\",\"x\":1}",
        "{\"a\":\"callHangup\",\"number\":\"0612345678\"}"
    })
    void unGesteDAppelAvecUnChampDePlusNePartPas(String json) {
        assertEquals(Optional.empty(), action(json, ENTRANT), json);
        assertEquals(Optional.empty(), action(json, EN_COURS), json);
    }

    @Test
    void silenceEtHautParleurEnOnOff() {
        PhoneCallActionPacket silence = action("{\"a\":\"callMute\",\"on\":true}").orElseThrow();
        assertEquals(Action.MUTE, silence.action());
        assertEquals("on", silence.first());
        assertEquals("off", action("{\"a\":\"callMute\",\"on\":false}").orElseThrow().first());
        PhoneCallActionPacket hautParleur = action("{\"a\":\"callSpeaker\",\"on\":true}").orElseThrow();
        assertEquals(Action.SPEAKER, hautParleur.action());
        assertEquals("on", hautParleur.first());
        assertEquals("off", action("{\"a\":\"callSpeaker\",\"on\":false}").orElseThrow().first());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"callMute\"}",
        "{\"a\":\"callMute\",\"on\":\"on\"}",
        "{\"a\":\"callMute\",\"on\":\"true\"}",
        "{\"a\":\"callMute\",\"on\":1}",
        "{\"a\":\"callMute\",\"on\":null}",
        "{\"a\":\"callSpeaker\",\"on\":true,\"x\":1}",
        "{\"a\":\"callSpeaker\",\"on\":[true]}"
    })
    void unInterrupteurFauxNePartPas(String json) {
        assertEquals(Optional.empty(), action(json, EN_COURS), json);
    }

    @Test
    void enregistrerUnContactDonneNumeroEtNom() {
        PhoneCallActionPacket p = action("{\"a\":\"contactSave\",\"number\":\"0612345678\",\"name\":\"Maman\"}")
                .orElseThrow();
        assertEquals(Action.SAVE_CONTACT, p.action());
        assertEquals("0612345678", p.first());
        assertEquals("Maman", p.second());
        assertEquals(SESSION, p.session());
        // Un nom de 32 unités UTF-16 (un emoji en prend deux) passe tel quel ; le serveur le nettoie.
        String nom = "Capitaine Barbe-Grise du Lago 😀";
        assertEquals(32, nom.length());
        assertEquals(nom, action("{\"a\":\"contactSave\",\"number\":\"0612345678\",\"name\":\"" + nom + "\"}")
                .orElseThrow().second());
        // Le nettoyage revient au serveur : un code de couleur passe le pont, ContactRules le retire.
        assertEquals("§cRouge", action("{\"a\":\"contactSave\",\"number\":\"0612345678\",\"name\":\"§cRouge\"}")
                .orElseThrow().second());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"contactSave\",\"number\":\"0612345678\"}",
        "{\"a\":\"contactSave\",\"name\":\"Maman\"}",
        "{\"a\":\"contactSave\",\"number\":\"0612345678\",\"name\":\"\"}",
        "{\"a\":\"contactSave\",\"number\":\"0612345678\",\"name\":\"123456789012345678901234567890123\"}",
        "{\"a\":\"contactSave\",\"number\":\"0612345678\",\"name\":5}",
        "{\"a\":\"contactSave\",\"number\":\"0612345678\",\"name\":null}",
        "{\"a\":\"contactSave\",\"number\":\"06 12 34 56 78\",\"name\":\"Maman\"}",
        "{\"a\":\"contactSave\",\"number\":\"\",\"name\":\"Maman\"}",
        "{\"a\":\"contactSave\",\"number\":612345678,\"name\":\"Maman\"}",
        "{\"a\":\"contactSave\",\"number\":\"0612345678\",\"name\":\"Maman\",\"old\":\"0611111111\"}"
    })
    void unContactFauxNePartPas(String json) {
        assertEquals(Optional.empty(), action(json), json);
    }

    @Test
    void retirerUnContactDonneSonNumero() {
        PhoneCallActionPacket p = action("{\"a\":\"contactRemove\",\"number\":\"0612345678\"}").orElseThrow();
        assertEquals(Action.REMOVE_CONTACT, p.action());
        assertEquals("0612345678", p.first());
        assertEquals("", p.second());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"contactRemove\"}",
        "{\"a\":\"contactRemove\",\"number\":\"\"}",
        "{\"a\":\"contactRemove\",\"number\":\"x\"}",
        "{\"a\":\"contactRemove\",\"number\":\"0612345678\",\"name\":\"Maman\"}",
        "{\"a\":\"contactRemove\",\"number\":true}"
    })
    void unRetraitFauxNePartPas(String json) {
        assertEquals(Optional.empty(), action(json), json);
    }

    @Test
    void lAppliDemandeLeCarnetSansChamp() {
        PhoneCallActionPacket p = action("{\"a\":\"phoneApp\"}").orElseThrow();
        assertEquals(Action.OPEN_PHONE_APP, p.action());
        assertEquals("", p.first());
        assertEquals(SESSION, p.session());
        assertEquals(Optional.empty(), action("{\"a\":\"phoneApp\",\"x\":1}"));
        assertEquals(Optional.empty(), action("{\"a\":\"phoneApp\",\"number\":\"0612345678\"}"));
    }

    @Test
    void unMessageSansActionOuInconnuNePartPas() {
        assertEquals(Optional.empty(), action("{\"number\":\"0612345678\"}"));
        assertEquals(Optional.empty(), action("{\"a\":7}"));
        assertEquals(Optional.empty(), action("{\"a\":\"unlock\",\"code\":\"1234\"}"));
        assertEquals(Optional.empty(), CallBridgeMessages.toAction(null, SESSION, EN_COURS));
    }
}
