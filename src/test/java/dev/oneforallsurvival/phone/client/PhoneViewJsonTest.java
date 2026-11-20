package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.client.web.WebScript;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** La vue envoyée à {@code window.phone.render} : clés du contrat de la page, énumérations en noms, rien de trop. */
class PhoneViewJsonTest {

    private static PhoneStatePacket etat(Lock lock, String ownerName, Result last) {
        return new PhoneStatePacket(9, Kind.UPDATE, lock, 76, 1_763_600_000_000L, 3600, "nuit", true, true, ownerName, 4,
                0, last, CloseReason.NONE);
    }

    @Test
    void lesClesSontCellesDuContrat() {
        JsonObject v = PhoneViewJson.toJson(etat(Lock.UNLOCKED, "Alex", Result.NONE));
        assertEquals(Set.of("session", "lock", "battery", "serverMillis", "zoneOffsetSeconds", "wallpaper", "airplane",
                "owner", "ownerName", "attemptsLeft", "lockoutSeconds", "last"), v.keySet());
        assertEquals(9, v.get("session").getAsInt());
        assertEquals(76, v.get("battery").getAsInt());
        assertEquals(1_763_600_000_000L, v.get("serverMillis").getAsLong());
        assertEquals(3600, v.get("zoneOffsetSeconds").getAsInt());
        assertEquals("nuit", v.get("wallpaper").getAsString());
        assertTrue(v.get("airplane").getAsBoolean());
        assertTrue(v.get("owner").getAsBoolean());
        assertEquals("Alex", v.get("ownerName").getAsString());
        assertEquals(4, v.get("attemptsLeft").getAsInt());
        assertEquals(0, v.get("lockoutSeconds").getAsInt());
    }

    @Test
    void lesEnumerationsSontEnNoms() {
        JsonObject v = PhoneViewJson.toJson(etat(Lock.LOCKED, "", Result.WRONG_CODE));
        assertTrue(v.get("lock").getAsJsonPrimitive().isString());
        assertEquals("LOCKED", v.get("lock").getAsString());
        assertEquals("WRONG_CODE", v.get("last").getAsString());
        assertEquals("SETUP", PhoneViewJson.toJson(etat(Lock.SETUP, "", Result.NONE)).get("lock").getAsString());
        assertEquals("UNLOCKED", PhoneViewJson.toJson(etat(Lock.UNLOCKED, "", Result.CODE_CHANGED)).get("lock")
                .getAsString());
    }

    @Test
    void uneVueVerrouilleeNeDitRienDuProprietaire() {
        for (Lock lock : new Lock[] {Lock.LOCKED, Lock.SETUP}) {
            JsonObject v = PhoneViewJson.toJson(etat(lock, "Alex", Result.NONE));
            assertEquals("", v.get("ownerName").getAsString(), lock.name());
            assertFalse(v.get("owner").getAsBoolean(), lock.name());
            assertFalse(v.get("airplane").getAsBoolean(), lock.name());
            assertEquals("nuit", v.get("wallpaper").getAsString(), "le fond reste visible");
        }
    }

    @Test
    void unNomPiegeRevientALIdentiqueApresWebScript() {
        String piege = "</script> ";
        String script = WebScript.call("phone.render", PhoneViewJson.toJson(etat(Lock.UNLOCKED, piege, Result.NONE)));
        assertTrue(script.startsWith("phone.render(") && script.endsWith(")"), script);
        assertFalse(script.contains("</script>"), script);
        assertFalse(script.contains(" "), "U+2028 brut dans le script");
        String argument = script.substring("phone.render(".length(), script.length() - 1);
        assertEquals(piege, JsonParser.parseString(argument).getAsJsonObject().get("ownerName").getAsString());
    }
}
