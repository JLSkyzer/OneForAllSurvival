package dev.oneforallsurvival.phone.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneCode;
import dev.oneforallsurvival.phone.PhoneWallpapers;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Relit un message de la page du téléphone avant d'en faire un geste pour le serveur. Seuls {@code unlock},
 * {@code setup} et {@code erase} ({@code code}), {@code lock}, {@code changeCode} ({@code old}, {@code new}),
 * {@code wallpaper} ({@code id}) et {@code airplane} ({@code on}, booléen JSON) partent, avec exactement leurs champs :
 * un champ de plus, un type faux, un code qui n'est pas de quatre chiffres ASCII ou un fond inconnu, et rien ne part.
 * {@code ready}, {@code layout} et {@code close} restent dans le client. Le serveur revérifie tout de toute façon.
 */
public final class PhoneBridgeMessages {

    private static final Set<String> ONLY_A = Set.of("a");
    private static final Set<String> CODE = Set.of("a", "code");
    private static final Set<String> CHANGE = Set.of("a", "old", "new");
    private static final Set<String> WALLPAPER = Set.of("a", "id");
    private static final Set<String> AIRPLANE = Set.of("a", "on");

    static final int MAX_RECTS = 64;
    private static final Pattern RECT_ID = Pattern.compile("[a-z0-9-]{1,32}");

    private PhoneBridgeMessages() {}

    public static Optional<PhoneActionPacket> toAction(@Nullable JsonObject message, int session) {
        if (message == null) {
            return Optional.empty();
        }
        String a = string(message.get("a"));
        if (a == null) {
            return Optional.empty();
        }
        return switch (a) {
            case "unlock" -> code(message, session, Action.UNLOCK);
            case "setup" -> code(message, session, Action.SETUP);
            case "erase" -> code(message, session, Action.ERASE);
            case "lock" -> exactly(message, ONLY_A)
                    ? Optional.of(new PhoneActionPacket(session, Action.LOCK, "", "", false))
                    : Optional.empty();
            case "changeCode" -> {
                String old = string(message.get("old"));
                String fresh = string(message.get("new"));
                yield exactly(message, CHANGE) && PhoneCode.isValid(old) && PhoneCode.isValid(fresh)
                        ? Optional.of(new PhoneActionPacket(session, Action.CHANGE_CODE, old, fresh, false))
                        : Optional.empty();
            }
            case "wallpaper" -> {
                String id = string(message.get("id"));
                yield exactly(message, WALLPAPER) && PhoneWallpapers.isValid(id)
                        ? Optional.of(new PhoneActionPacket(session, Action.WALLPAPER, id, "", false))
                        : Optional.empty();
            }
            case "airplane" -> {
                JsonElement on = message.get("on");
                yield exactly(message, AIRPLANE) && on != null && on.isJsonPrimitive()
                        && on.getAsJsonPrimitive().isBoolean()
                        ? Optional.of(new PhoneActionPacket(session, Action.AIRPLANE, "", "", on.getAsBoolean()))
                        : Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    /**
     * Les boîtes d'un message {@code layout} ({@code "rects":{"<id>":[x,y,w,h],…}}, px CSS) : identifiants de 1 à 32
     * caractères {@code [a-z0-9-]}, quatre nombres finis (arrondis) ; tout autre élément est ignoré, au plus
     * {@value #MAX_RECTS} boîtes. Carte non modifiable, vide si {@code rects} manque.
     */
    public static Map<String, int[]> layout(@Nullable JsonObject message) {
        JsonElement raw = message == null ? null : message.get("rects");
        if (raw == null || !raw.isJsonObject()) {
            return Map.of();
        }
        Map<String, int[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : raw.getAsJsonObject().entrySet()) {
            if (out.size() >= MAX_RECTS || !RECT_ID.matcher(e.getKey()).matches() || !e.getValue().isJsonArray()) {
                continue;
            }
            JsonArray box = e.getValue().getAsJsonArray();
            int[] r = new int[4];
            boolean ok = box.size() == 4;
            for (int i = 0; ok && i < 4; i++) {
                JsonElement n = box.get(i);
                ok = n.isJsonPrimitive() && n.getAsJsonPrimitive().isNumber()
                        && Double.isFinite(n.getAsDouble()) && Math.abs(n.getAsDouble()) < 100_000;
                r[i] = ok ? (int) Math.round(n.getAsDouble()) : 0;
            }
            if (ok) {
                out.put(e.getKey(), r);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Optional<PhoneActionPacket> code(JsonObject message, int session, Action action) {
        String code = string(message.get("code"));
        return exactly(message, CODE) && PhoneCode.isValid(code)
                ? Optional.of(new PhoneActionPacket(session, action, code, "", false))
                : Optional.empty();
    }

    private static boolean exactly(JsonObject message, Set<String> keys) {
        return message.keySet().equals(keys);
    }

    /** La chaîne JSON, ou {@code null} pour tout autre type (nombre, booléen, tableau, objet, null). */
    @Nullable
    private static String string(@Nullable JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        return p.isString() ? p.getAsString() : null;
    }
}
