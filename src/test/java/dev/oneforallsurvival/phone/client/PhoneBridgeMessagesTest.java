package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Test
    void unMessageSansActionNePartPas() {
        assertEquals(Optional.empty(), action("{\"code\":\"4821\"}"));
        assertEquals(Optional.empty(), action("{\"a\":7}"));
        assertEquals(Optional.empty(), PhoneBridgeMessages.toAction(null, SESSION));
    }
}
