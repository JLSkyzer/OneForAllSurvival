package dev.oneforallsurvival.phone.client;

import com.google.gson.JsonObject;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;

/**
 * La vue passée à {@code window.phone.render} (contrat de {@code html/phone/phone.js}) : {@code session}, {@code lock}
 * ({@code "SETUP"}, {@code "LOCKED"} ou {@code "UNLOCKED"}), {@code battery}, {@code serverMillis},
 * {@code zoneOffsetSeconds}, {@code wallpaper}, {@code airplane}, {@code owner}, {@code ownerName},
 * {@code attemptsLeft}, {@code lockoutSeconds}, {@code last} (nom de {@link PhoneStatePacket.Result}). Hors
 * {@link Lock#UNLOCKED}, rien du propriétaire ni des réglages, même si le paquet en portait.
 */
public final class PhoneViewJson {

    private PhoneViewJson() {}

    public static JsonObject toJson(PhoneStatePacket state) {
        boolean unlocked = state.lock() == Lock.UNLOCKED;
        JsonObject v = new JsonObject();
        v.addProperty("session", state.session());
        v.addProperty("lock", state.lock().name());
        v.addProperty("battery", state.battery());
        v.addProperty("serverMillis", state.serverMillis());
        v.addProperty("zoneOffsetSeconds", state.zoneOffsetSeconds());
        v.addProperty("wallpaper", state.wallpaper());
        v.addProperty("airplane", unlocked && state.airplane());
        v.addProperty("owner", unlocked && state.owner());
        v.addProperty("ownerName", unlocked ? state.ownerName() : "");
        v.addProperty("attemptsLeft", state.attemptsLeft());
        v.addProperty("lockoutSeconds", state.lockoutSeconds());
        v.addProperty("last", state.last().name());
        return v;
    }
}
