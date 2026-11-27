package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneWallpapers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Ce que la page du téléphone peut demander au serveur : seuls les gestes connus, aux champs exacts et bien typés, avec
 * des codes de quatre chiffres ASCII et un fond connu. Tout le reste ne part pas (le serveur revérifie de toute façon).
 */
class PhoneBridgeMessagesTest {

    private static final int SESSION = 17;

    private static Optional<PhoneActionPacket> action(String json) {
        JsonObject message = JsonParser.parseString(json).getAsJsonObject();
        return PhoneBridgeMessages.toAction(message, SESSION);
    }

    @Test
    void unlockDonneLeCodeEtLaSession() {
        PhoneActionPacket p = action("{\"a\":\"unlock\",\"code\":\"4821\"}").orElseThrow();
        assertEquals(Action.UNLOCK, p.action());
        assertEquals("4821", p.first());
        assertEquals(SESSION, p.session());
    }

    @Test
    void chaqueGesteConnuPasse() {
        assertEquals(Action.SETUP, action("{\"a\":\"setup\",\"code\":\"0000\"}").orElseThrow().action());
        assertEquals(Action.LOCK, action("{\"a\":\"lock\"}").orElseThrow().action());
        PhoneActionPacket change = action("{\"a\":\"changeCode\",\"old\":\"1234\",\"new\":\"5678\"}").orElseThrow();
        assertEquals(Action.CHANGE_CODE, change.action());
        assertEquals("1234", change.first());
        assertEquals("5678", change.second());
        PhoneActionPacket fond = action("{\"a\":\"wallpaper\",\"id\":\"nuit\"}").orElseThrow();
        assertEquals(Action.WALLPAPER, fond.action());
        assertEquals("nuit", fond.first());
        PhoneActionPacket avion = action("{\"a\":\"airplane\",\"on\":true}").orElseThrow();
        assertEquals(Action.AIRPLANE, avion.action());
        assertTrue(avion.flag());
        assertEquals(false, action("{\"a\":\"airplane\",\"on\":false}").orElseThrow().flag());
        PhoneActionPacket efface = action("{\"a\":\"erase\",\"code\":\"4821\"}").orElseThrow();
        assertEquals(Action.ERASE, efface.action());
        assertEquals("4821", efface.first());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"unlock\",\"code\":\"482\"}",
        "{\"a\":\"unlock\",\"code\":4821}",
        "{\"a\":\"unlock\",\"code\":\"１２３４\"}",
        "{\"a\":\"unlock\"}",
        "{\"a\":\"unlock\",\"code\":null}",
        "{\"a\":\"unlock\",\"code\":[\"4821\"]}",
        "{\"a\":\"setup\",\"code\":\"48210\"}",
        "{\"a\":\"wallpaper\",\"id\":\"x\"}",
        "{\"a\":\"wallpaper\",\"id\":\"NUIT\"}",
        "{\"a\":\"airplane\",\"on\":\"true\"}",
        "{\"a\":\"airplane\",\"on\":1}",
        "{\"a\":\"airplane\"}",
        "{\"a\":\"rm -rf\"}",
        "{\"a\":\"ready\"}",
        "{\"a\":\"layout\",\"rects\":{}}",
        "{\"a\":\"close\"}",
        "{\"a\":\"Unlock\",\"code\":\"4821\"}",
        "{\"a\":\"changeCode\",\"old\":\"1234\",\"new\":\"56a8\"}",
        "{\"a\":\"changeCode\",\"old\":\"12345\",\"new\":\"5678\"}",
        "{\"a\":\"changeCode\",\"old\":\"1234\"}",
        "{\"a\":\"erase\",\"code\":\" 4821\"}"
    })
    void lesAutresNePartentPas(String json) {
        assertEquals(Optional.empty(), action(json), json);
    }

    @Test
    void setupPorteLeFondChoisi() {
        PhoneActionPacket p = action("{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":\"lagon\"}").orElseThrow();
        assertEquals(Action.SETUP, p.action());
        assertEquals("4821", p.first());
        assertEquals("lagon", p.second());
        assertEquals(SESSION, p.session());
        for (String id : PhoneWallpapers.IDS) {
            assertEquals(id, action("{\"a\":\"setup\",\"code\":\"0000\",\"wallpaper\":\"" + id + "\"}")
                    .orElseThrow().second(), id);
        }
    }

    @Test
    void setupSansFondResteCommeAvant() {
        PhoneActionPacket p = action("{\"a\":\"setup\",\"code\":\"4821\"}").orElseThrow();
        assertEquals("4821", p.first());
        assertEquals("", p.second());
    }

    /** Le fond de {@code setup} : une chaîne de {@link PhoneWallpapers#IDS}, sinon rien ne part (pas de repli muet). */
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":\"plage\"}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":\"\"}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":\"LAGON\"}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":\"Nuit\"}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":\" nuit\"}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":2}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":true}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":null}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":[\"nuit\"]}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":{\"id\":\"nuit\"}}",
        "{\"a\":\"setup\",\"code\":\"１２３４\",\"wallpaper\":\"nuit\"}",
        "{\"a\":\"setup\",\"code\":\"482\",\"wallpaper\":\"nuit\"}",
        "{\"a\":\"setup\",\"wallpaper\":\"nuit\"}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"wallpaper\":\"nuit\",\"id\":\"nuit\"}",
        "{\"a\":\"setup\",\"code\":\"4821\",\"id\":\"nuit\"}",
        "{\"a\":\"unlock\",\"code\":\"4821\",\"wallpaper\":\"nuit\"}",
        "{\"a\":\"erase\",\"code\":\"4821\",\"wallpaper\":\"nuit\"}",
        "{\"a\":\"wallpaper\",\"wallpaper\":\"nuit\"}"
    })
    void unFondDeSetupFauxEmpecheLeDepart(String json) {
        assertEquals(Optional.empty(), action(json), json);
    }

    @Test
    void changeCodeAvecUnNouveauCodeInvalideNePartPas() {
        assertEquals(Optional.empty(), action("{\"a\":\"changeCode\",\"old\":\"1234\",\"new\":\"567\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"unlock\",\"code\":\"4821\",\"x\":1}",
        "{\"a\":\"lock\",\"code\":\"4821\"}",
        "{\"a\":\"airplane\",\"on\":true,\"id\":\"nuit\"}",
        "{\"a\":\"wallpaper\",\"id\":\"nuit\",\"on\":true}",
        "{\"a\":\"changeCode\",\"old\":\"1234\",\"new\":\"5678\",\"code\":\"0000\"}",
        "{\"a\":\"erase\",\"code\":\"4821\",\"session\":3}"
    })
    void unChampInconnuEmpecheLeDepart(String json) {
        assertEquals(Optional.empty(), action(json), json);
    }

    // --- Lot T2 : la carte SIM, la demande de carte et l'appli « Antenne » -----------------------------------------

    @Test
    void insertSimDonneLaCaseEnTexte() {
        PhoneActionPacket p = action("{\"a\":\"insertSim\",\"slot\":5}").orElseThrow();
        assertEquals(Action.INSERT_SIM, p.action());
        assertEquals("5", p.first());
        assertEquals("", p.second());
        assertEquals(SESSION, p.session());
        assertEquals("0", action("{\"a\":\"insertSim\",\"slot\":0}").orElseThrow().first());
        assertEquals("40", action("{\"a\":\"insertSim\",\"slot\":40}").orElseThrow().first());
    }

    /** La case : un entier JSON de 0 à 40, écrit sans fioriture ; sinon rien ne part. */
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"insertSim\",\"slot\":41}",
        "{\"a\":\"insertSim\",\"slot\":-1}",
        "{\"a\":\"insertSim\",\"slot\":\"5\"}",
        "{\"a\":\"insertSim\",\"slot\":5.5}",
        "{\"a\":\"insertSim\",\"slot\":5.0}",
        "{\"a\":\"insertSim\",\"slot\":5e0}",
        "{\"a\":\"insertSim\",\"slot\":1e9}",
        "{\"a\":\"insertSim\",\"slot\":true}",
        "{\"a\":\"insertSim\",\"slot\":null}",
        "{\"a\":\"insertSim\",\"slot\":[5]}",
        "{\"a\":\"insertSim\"}",
        "{\"a\":\"insertSim\",\"slot\":5,\"op\":\"3f2a9c1b0d4e\"}",
        "{\"a\":\"insertSim\",\"slot\":5,\"code\":\"4821\"}"
    })
    void uneCaseFausseNePartPas(String json) {
        assertEquals(Optional.empty(), action(json), json);
    }

    @Test
    void removeSimEtAntennaAppSansChamp() {
        PhoneActionPacket retrait = action("{\"a\":\"removeSim\"}").orElseThrow();
        assertEquals(Action.REMOVE_SIM, retrait.action());
        assertEquals("", retrait.first());
        PhoneActionPacket appli = action("{\"a\":\"antennaApp\"}").orElseThrow();
        assertEquals(Action.OPEN_OPERATOR_APP, appli.action());
        assertEquals("", appli.first());
        assertEquals(SESSION, appli.session());
    }

    /**
     * L'identifiant d'opérateur : 12 caractères hexadécimaux en minuscules, la forme du registre
     * ({@code PhoneOperators.found}) et celle que le serveur relit ({@code PhoneService}). Écart au brief, qui écrit
     * 8 : un identifiant de 8 ne désigne aucun opérateur et serait toujours refusé par le serveur.
     */
    @Test
    void requestSimDonneLIdentifiant() {
        PhoneActionPacket p = action("{\"a\":\"requestSim\",\"op\":\"3f2a9c1b0d4e\"}").orElseThrow();
        assertEquals(Action.REQUEST_SIM, p.action());
        assertEquals("3f2a9c1b0d4e", p.first());
        assertEquals(SESSION, p.session());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"requestSim\",\"op\":\"3F2A9C1B0D4E\"}",
        "{\"a\":\"requestSim\",\"op\":\"3f2a9c1b\"}",
        "{\"a\":\"requestSim\",\"op\":\"3f2a9c1\"}",
        "{\"a\":\"requestSim\",\"op\":\"3f2a9c1b0d4\"}",
        "{\"a\":\"requestSim\",\"op\":\"3f2a9c1b0d4e0\"}",
        "{\"a\":\"requestSim\",\"op\":\"../x\"}",
        "{\"a\":\"requestSim\",\"op\":\"3f2a9c1b0d4g\"}",
        "{\"a\":\"requestSim\",\"op\":\" 3f2a9c1b0d4e\"}",
        "{\"a\":\"requestSim\",\"op\":\"\"}",
        "{\"a\":\"requestSim\",\"op\":3}",
        "{\"a\":\"requestSim\",\"op\":null}",
        "{\"a\":\"requestSim\"}",
        "{\"a\":\"requestSim\",\"op\":\"3f2a9c1b0d4e\",\"slot\":5}"
    })
    void unIdentifiantFauxNePartPas(String json) {
        assertEquals(Optional.empty(), action(json), json);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"a\":\"removeSim\",\"slot\":5}",
        "{\"a\":\"removeSim\",\"x\":1}",
        "{\"a\":\"antennaApp\",\"op\":\"3f2a9c1b0d4e\"}",
        "{\"a\":\"antennaApp\",\"on\":true}",
        "{\"a\":\"RemoveSim\"}",
        "{\"a\":\"openOperatorApp\"}",
        "{\"a\":\"insert_sim\",\"slot\":5}"
    })
    void unGesteDeCarteMalFormeNePartPas(String json) {
        assertEquals(Optional.empty(), action(json), json);
    }

    @Test
    void laMiseEnPageGardeLesBoitesValides() {
        JsonObject m = JsonParser.parseString("{\"a\":\"layout\",\"rects\":{\"key-4\":[10,20.6,30,40],"
                + "\"Mauvais\":[1,2,3,4],\"court\":[1,2,3],\"texte\":[\"1\",2,3,4],\"objet\":{\"x\":1},"
                + "\"enorme\":[1e9,2,3,4],\"home-bar\":[0,800,390,20]}}").getAsJsonObject();
        var rects = PhoneBridgeMessages.layout(m);
        assertEquals(Set.of("key-4", "home-bar"), rects.keySet());
        assertArrayEquals(new int[] {10, 21, 30, 40}, rects.get("key-4"));
        assertEquals(Map.of(), PhoneBridgeMessages.layout(JsonParser.parseString("{\"a\":\"layout\"}")
                .getAsJsonObject()));
        StringBuilder beaucoup = new StringBuilder("{\"a\":\"layout\",\"rects\":{");
        for (int i = 0; i < 100; i++) {
            beaucoup.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":[1,2,3,4]");
        }
        assertEquals(PhoneBridgeMessages.MAX_RECTS, PhoneBridgeMessages.layout(
                JsonParser.parseString(beaucoup.append("}}").toString()).getAsJsonObject()).size());
    }

    /** Tâche 10 : les boîtes de l'appli « Antenne » portent un UUID ({@code op-replace-<id>}, 47 caractères). */
    @Test
    void lesBoitesDeLAppliAntennePassent() {
        String id = "0b9a1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d";
        JsonObject m = JsonParser.parseString("{\"a\":\"layout\",\"rects\":{\"op-replace-" + id + "\":[1,2,3,4],"
                + "\"op-refuse-" + id + "\":[1,2,3,4],\"x" + "y".repeat(48) + "\":[1,2,3,4]}}").getAsJsonObject();
        assertEquals(Set.of("op-replace-" + id, "op-refuse-" + id), PhoneBridgeMessages.layout(m).keySet());
    }

    /**
     * Tâche 16 (vu dans un vrai client MCEF : « Désactiver » sans boîte, son identifiant à 50 caractères) : chaque bouton
     * que la page nomme d'un UUID ({@code button('op-…-' + ….id}) a une boîte que le jeu garde.
     */
    @Test
    void chaqueBoutonQueLaPageNommeDUnUuidPasse() throws IOException {
        String racine = System.getProperty("oneforallsurvival.mainResources");
        assertNotNull(racine, "propriété oneforallsurvival.mainResources absente");
        String page = Files.readString(Paths.get(racine).resolve("assets/oneforallsurvival/html/phone/phone.js"),
                StandardCharsets.UTF_8);
        Set<String> boutons = new TreeSet<>();
        Matcher m = Pattern.compile("button\\('(op-[a-z-]+-)' \\+ [a-z]+\\.id\\b").matcher(page);
        while (m.find()) {
            boutons.add(m.group(1) + "0b9a1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d");
        }
        // Témoin de la lecture : désigner, émettre, refuser, suspendre ou réactiver, remplacer, désactiver.
        assertEquals(6, boutons.size(), "boutons nommés d'un UUID lus dans phone.js : " + boutons);
        JsonObject rects = new JsonObject();
        boutons.forEach(id -> rects.add(id, JsonParser.parseString("[1,2,3,4]")));
        JsonObject message = new JsonObject();
        message.addProperty("a", "layout");
        message.add("rects", rects);
        assertEquals(boutons, PhoneBridgeMessages.layout(message).keySet());
    }

    /** Retours d'essai du 27/11 : la page dit quand un champ de texte a la main ; rien n'en part vers le serveur. */
    @Test
    void textFocusSeLitEtNePartPas() {
        JsonObject on = JsonParser.parseString("{\"a\":\"textFocus\",\"on\":true}").getAsJsonObject();
        JsonObject off = JsonParser.parseString("{\"a\":\"textFocus\",\"on\":false}").getAsJsonObject();
        assertEquals(Optional.of(true), PhoneBridgeMessages.textFocus(on));
        assertEquals(Optional.of(false), PhoneBridgeMessages.textFocus(off));
        assertEquals(Optional.empty(), PhoneBridgeMessages.toAction(on, SESSION), "rien pour le serveur");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"a\":\"textFocus\"}",
            "{\"a\":\"textFocus\",\"on\":\"true\"}",
            "{\"a\":\"textFocus\",\"on\":1}",
            "{\"a\":\"textFocus\",\"on\":null}",
            "{\"a\":\"textFocus\",\"on\":true,\"x\":1}",
            "{\"a\":\"lock\",\"on\":true}",
            "{\"on\":true}"
    })
    void textFocusMalFormeNeSeLitPas(String json) {
        assertEquals(Optional.empty(), PhoneBridgeMessages.textFocus(JsonParser.parseString(json).getAsJsonObject()));
    }

    @Test
    void textFocusNulNeSeLitPas() {
        assertEquals(Optional.empty(), PhoneBridgeMessages.textFocus(null));
    }

    @Test
    void unMessageSansActionNePartPas() {
        assertEquals(Optional.empty(), action("{\"code\":\"4821\"}"));
        assertEquals(Optional.empty(), action("{\"a\":7}"));
        assertEquals(Optional.empty(), PhoneBridgeMessages.toAction(null, SESSION));
    }
}
