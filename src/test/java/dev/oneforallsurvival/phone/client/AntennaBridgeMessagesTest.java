package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.phone.net.OperatorActionPacket;
import dev.oneforallsurvival.phone.net.OperatorActionPacket.Action;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Les gestes de l'appli « Antenne » (lot T2, tâche 10) : chaque message {@code op*} du contrat donne exactement le geste
 * attendu ; chaque geste du panneau, sauf {@code NAME} et {@code OPEN_TILL} (ni nom ni caisse à distance), a son
 * message ; tout autre champ, type, nombre ou identifiant ne donne rien.
 */
class AntennaBridgeMessagesTest {

    private static final String ID = "0b9a1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d";
    private static final int SESSION = 41;

    private static Optional<OperatorActionPacket> lire(String json) {
        return AntennaBridgeMessages.toAction(JsonParser.parseString(json).getAsJsonObject(), SESSION);
    }

    private static void donne(String json, Action action, String arg, long value) {
        Optional<OperatorActionPacket> p = lire(json);
        assertTrue(p.isPresent(), "aucun geste pour " + json);
        assertEquals(new OperatorActionPacket(SESSION, action, arg, value), p.get(), json);
    }

    private static void rien(String json) {
        assertEquals(Optional.empty(), lire(json), "un geste pour " + json);
    }

    /** Les messages du contrat, et le geste que chacun donne. */
    private static Map<String, Action> contrat() {
        Map<String, Action> m = new LinkedHashMap<>();
        m.put("{\"a\":\"opTariff\",\"value\":15}", Action.SET_TARIFF);
        m.put("{\"a\":\"opIssue\",\"id\":\"" + ID + "\"}", Action.ISSUE);
        m.put("{\"a\":\"opRefuse\",\"id\":\"" + ID + "\"}", Action.REFUSE);
        m.put("{\"a\":\"opAnonymous\"}", Action.ISSUE_ANONYMOUS);
        m.put("{\"a\":\"opSuspend\",\"id\":\"" + ID + "\"}", Action.SUSPEND);
        m.put("{\"a\":\"opReactivate\",\"id\":\"" + ID + "\"}", Action.REACTIVATE);
        m.put("{\"a\":\"opReplace\",\"id\":\"" + ID + "\"}", Action.REPLACE);
        m.put("{\"a\":\"opDeactivate\",\"id\":\"" + ID + "\"}", Action.DEACTIVATE);
        m.put("{\"a\":\"opMain\",\"id\":\"" + ID + "\"}", Action.SET_MAIN);
        m.put("{\"a\":\"opPage\",\"n\":2}", Action.PAGE);
        m.put("{\"a\":\"opClose\"}", Action.CLOSE);
        return m;
    }

    @Test
    void chaqueMessageDonneSonGeste() {
        donne("{\"a\":\"opTariff\",\"value\":15}", Action.SET_TARIFF, "", 15L);
        donne("{\"a\":\"opTariff\",\"value\":0}", Action.SET_TARIFF, "", 0L);
        donne("{\"a\":\"opTariff\",\"value\":1000000}", Action.SET_TARIFF, "", 1_000_000L);
        donne("{\"a\":\"opIssue\",\"id\":\"" + ID + "\"}", Action.ISSUE, ID, 0L);
        donne("{\"a\":\"opRefuse\",\"id\":\"" + ID + "\"}", Action.REFUSE, ID, 0L);
        donne("{\"a\":\"opAnonymous\"}", Action.ISSUE_ANONYMOUS, "", 0L);
        donne("{\"a\":\"opSuspend\",\"id\":\"" + ID + "\"}", Action.SUSPEND, ID, 0L);
        donne("{\"a\":\"opReactivate\",\"id\":\"" + ID + "\"}", Action.REACTIVATE, ID, 0L);
        donne("{\"a\":\"opReplace\",\"id\":\"" + ID + "\"}", Action.REPLACE, ID, 0L);
        donne("{\"a\":\"opDeactivate\",\"id\":\"" + ID + "\"}", Action.DEACTIVATE, ID, 0L);
        donne("{\"a\":\"opMain\",\"id\":\"" + ID + "\"}", Action.SET_MAIN, ID, 0L);
        donne("{\"a\":\"opPage\",\"n\":0}", Action.PAGE, "", 0L);
        donne("{\"a\":\"opPage\",\"n\":63}", Action.PAGE, "", 63L);
        donne("{\"a\":\"opClose\"}", Action.CLOSE, "", 0L);
    }

    @Test
    void chaqueGesteDeLaPageAUnAction() {
        Set<Action> vus = EnumSet.noneOf(Action.class);
        for (Map.Entry<String, Action> e : contrat().entrySet()) {
            Optional<OperatorActionPacket> p = lire(e.getKey());
            assertTrue(p.isPresent(), "aucun geste pour " + e.getKey());
            assertEquals(e.getValue(), p.get().action(), e.getKey());
            assertEquals(SESSION, p.get().session(), "le numéro du panneau vient de Java, pas de la page");
            vus.add(p.get().action());
        }
        Set<Action> attendus = EnumSet.allOf(Action.class);
        attendus.remove(Action.NAME);
        attendus.remove(Action.OPEN_TILL);
        assertEquals(attendus, vus, "chaque geste du panneau, sauf le nom et la caisse, a son message");
    }

    @Test
    void unTarifHorsDesBornesOuMalEcritNeDonneRien() {
        rien("{\"a\":\"opTariff\",\"value\":-1}");
        rien("{\"a\":\"opTariff\",\"value\":1000001}");
        rien("{\"a\":\"opTariff\",\"value\":\"5\"}");
        rien("{\"a\":\"opTariff\",\"value\":5.5}");
        rien("{\"a\":\"opTariff\",\"value\":5.0}");
        rien("{\"a\":\"opTariff\",\"value\":5e0}");
        rien("{\"a\":\"opTariff\",\"value\":05}");
        rien("{\"a\":\"opTariff\",\"value\":true}");
        rien("{\"a\":\"opTariff\"}");
        rien("{\"a\":\"opTariff\",\"value\":5,\"x\":1}");
    }

    @Test
    void unIdentifiantMalFormeNeDonneRien() {
        for (String a : List.of("opIssue", "opRefuse", "opSuspend", "opReactivate", "opReplace", "opDeactivate",
                "opMain")) {
            rien("{\"a\":\"" + a + "\",\"id\":\"" + ID.toUpperCase() + "\"}");
            rien("{\"a\":\"" + a + "\",\"id\":\"" + ID.substring(0, 35) + "\"}");
            rien("{\"a\":\"" + a + "\",\"id\":\"../x\"}");
            rien("{\"a\":\"" + a + "\",\"id\":\"\"}");
            rien("{\"a\":\"" + a + "\",\"id\":5}");
            rien("{\"a\":\"" + a + "\"}");
            rien("{\"a\":\"" + a + "\",\"id\":\"" + ID + "\",\"value\":3}");
            rien("{\"a\":\"" + a + "\",\"id\":\"" + ID.replace('-', '0') + "\"}");
        }
    }

    @Test
    void unePageHorsDesBornesNeDonneRien() {
        rien("{\"a\":\"opPage\",\"n\":64}");
        rien("{\"a\":\"opPage\",\"n\":-1}");
        rien("{\"a\":\"opPage\",\"n\":\"1\"}");
        rien("{\"a\":\"opPage\",\"n\":1.5}");
        rien("{\"a\":\"opPage\"}");
    }

    /**
     * « Désactiver » (tâche 15) : la confirmation vit dans la page, le geste qui part est déjà confirmé ; il n'a pas
     * d'autre champ que l'identifiant (ni drapeau de confirmation, ni état), et aucun autre nom ne le donne.
     */
    @Test
    void desactiverNePorteQueLIdentifiant() {
        rien("{\"a\":\"opDeactivate\",\"id\":\"" + ID + "\",\"confirm\":true}");
        rien("{\"a\":\"opDeactivate\",\"id\":\"" + ID + "\",\"n\":1}");
        rien("{\"a\":\"opDeactivate\"}");
        rien("{\"a\":\"opDisable\",\"id\":\"" + ID + "\"}");
        rien("{\"a\":\"opdeactivate\",\"id\":\"" + ID + "\"}");
    }

    /** Ni nom ni caisse : ces gestes n'existent pas dans la page ; un geste sans champ n'en accepte aucun. */
    @Test
    void lesGestesQuiNExistentPasNeDonnentRien() {
        rien("{\"a\":\"opName\"}");
        rien("{\"a\":\"opName\",\"id\":\"Mairie\"}");
        rien("{\"a\":\"opTill\"}");
        rien("{\"a\":\"opAnonymous\",\"id\":\"" + ID + "\"}");
        rien("{\"a\":\"opClose\",\"n\":1}");
        rien("{\"a\":\"unlock\",\"code\":\"1234\"}");
        rien("{\"a\":5}");
        rien("{}");
        assertEquals(Optional.empty(), AntennaBridgeMessages.toAction(null, SESSION));
        assertEquals(Optional.empty(), AntennaBridgeMessages.toAction(new JsonObject(), SESSION));
    }

    @Test
    void seulsLesMessagesOpSontPourLAppli() {
        assertTrue(AntennaBridgeMessages.isAntenna("opClose") && AntennaBridgeMessages.isAntenna("opTariff"));
        assertTrue(!AntennaBridgeMessages.isAntenna("antennaApp") && !AntennaBridgeMessages.isAntenna("layout"));
    }
}
