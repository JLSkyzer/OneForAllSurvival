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
 * Relit un message de la page du téléphone avant d'en faire un geste pour le serveur. Seuls {@code unlock} et
 * {@code erase} ({@code code}), {@code setup} ({@code code}, et {@code wallpaper} facultatif : le fond choisi par
 * l'assistant de première configuration), {@code lock}, {@code changeCode} ({@code old}, {@code new}),
 * {@code wallpaper} ({@code id}) et {@code airplane} ({@code on}, booléen JSON) partent, avec exactement leurs champs :
 * un champ de plus, un type faux, un code qui n'est pas de quatre chiffres ASCII ou un fond inconnu, et rien ne part.
 * Lot T2 : {@code insertSim} ({@code slot}, entier JSON de 0 à 40 écrit sans signe, sans zéro de tête, sans partie
 * décimale ni exposant), {@code removeSim} et {@code antennaApp} (sans champ), {@code requestSim} ({@code op},
 * identifiant d'opérateur de 12 caractères hexadécimaux en minuscules, la forme du registre).
 * Retours d'essai du 27/11 : {@code faceId} ({@code on} booléen JSON ; avec {@code on} vrai, {@code code} aussi, quatre
 * chiffres ; avec {@code on} faux, aucun code).
 * {@code ready}, {@code layout}, {@code close} et {@code textFocus} ({@link #textFocus}) restent dans le client. Le
 * serveur revérifie tout de toute façon.
 */
public final class PhoneBridgeMessages {

    private static final Set<String> ONLY_A = Set.of("a");
    private static final Set<String> CODE = Set.of("a", "code");
    private static final Set<String> SETUP_WITH_WALLPAPER = Set.of("a", "code", "wallpaper");
    private static final Set<String> CHANGE = Set.of("a", "old", "new");
    private static final Set<String> WALLPAPER = Set.of("a", "id");
    private static final Set<String> AIRPLANE = Set.of("a", "on");
    private static final Set<String> TEXT_FOCUS = Set.of("a", "on");
    private static final Set<String> SLOT = Set.of("a", "slot");
    private static final Set<String> OPERATOR = Set.of("a", "op");
    private static final Set<String> FACE_ON = Set.of("a", "on", "code");
    /** Une case de l'inventaire telle que JSON l'écrit : 0 à 40, sans signe, zéro de tête, décimale ni exposant. */
    private static final Pattern SLOT_TEXT = Pattern.compile("0|[1-9]|[1-3][0-9]|40");
    /** Un identifiant d'opérateur ({@code PhoneOperators.found}) : 12 caractères hexadécimaux en minuscules. */
    private static final Pattern OPERATOR_ID = Pattern.compile("[0-9a-f]{12}");

    static final int MAX_RECTS = 64;
    private static final Pattern RECT_ID = Pattern.compile("[a-z0-9-]{1,48}");

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
            case "setup" -> setup(message, session);
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
            case "insertSim" -> {
                String slot = slot(message.get("slot"));
                yield exactly(message, SLOT) && slot != null
                        ? Optional.of(new PhoneActionPacket(session, Action.INSERT_SIM, slot, "", false))
                        : Optional.empty();
            }
            case "removeSim" -> exactly(message, ONLY_A)
                    ? Optional.of(new PhoneActionPacket(session, Action.REMOVE_SIM, "", "", false))
                    : Optional.empty();
            case "requestSim" -> {
                String op = string(message.get("op"));
                yield exactly(message, OPERATOR) && op != null && OPERATOR_ID.matcher(op).matches()
                        ? Optional.of(new PhoneActionPacket(session, Action.REQUEST_SIM, op, "", false))
                        : Optional.empty();
            }
            case "faceId" -> faceId(message, session);
            case "antennaApp" -> exactly(message, ONLY_A)
                    ? Optional.of(new PhoneActionPacket(session, Action.OPEN_OPERATOR_APP, "", "", false))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * La case d'un {@code insertSim} en texte ({@code "0"} à {@code "40"}), ou {@code null} : seul un nombre JSON écrit
     * comme un entier de 0 à 40 passe ({@code 5.0}, {@code 5e0}, {@code "5"}, {@code -1} et {@code 41} ne passent pas).
     * Le texte du nombre est lu tel que la page l'a écrit (Gson garde l'écriture d'un nombre lu).
     */
    @Nullable
    private static String slot(@Nullable JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        String text = e.getAsJsonPrimitive().getAsString();
        return SLOT_TEXT.matcher(text).matches() ? text : null;
    }

    /**
     * Les boîtes d'un message {@code layout} ({@code "rects":{"<id>":[x,y,w,h],…}}, px CSS) : identifiants de 1 à 48
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

    /**
     * Retours d'essai du 27/11 : {@code {"a":"textFocus","on":true|false}}, la page dit qu'un champ de texte a la main
     * (ou ne l'a plus), pour que l'écran laisse sa saisie au champ ({@link PhoneKeys#press}). Exactement ces deux champs,
     * {@code on} booléen JSON ; sinon rien. Ce message reste dans le client.
     */
    public static Optional<Boolean> textFocus(@Nullable JsonObject message) {
        if (message == null || !"textFocus".equals(string(message.get("a"))) || !exactly(message, TEXT_FOCUS)) {
            return Optional.empty();
        }
        JsonElement on = message.get("on");
        return on.isJsonPrimitive() && on.getAsJsonPrimitive().isBoolean()
                ? Optional.of(on.getAsBoolean()) : Optional.empty();
    }

    /**
     * {@code setup} : le code et, s'il y est, le fond ({@code second} du paquet). Un fond présent mais hors de
     * {@link PhoneWallpapers#IDS} (ou d'un autre type) fait tout refuser, comme un champ faux ailleurs.
     */
    private static Optional<PhoneActionPacket> setup(JsonObject message, int session) {
        if (!message.has("wallpaper")) {
            return code(message, session, Action.SETUP);
        }
        String code = string(message.get("code"));
        String wallpaper = string(message.get("wallpaper"));
        return exactly(message, SETUP_WITH_WALLPAPER) && PhoneCode.isValid(code) && PhoneWallpapers.isValid(wallpaper)
                ? Optional.of(new PhoneActionPacket(session, Action.SETUP, code, wallpaper, false))
                : Optional.empty();
    }

    /** {@code faceId} : couper sans code ({@code {"a","on":false}}), activer avec le code ({@code on}, {@code code}). */
    private static Optional<PhoneActionPacket> faceId(JsonObject message, int session) {
        JsonElement on = message.get("on");
        if (on == null || !on.isJsonPrimitive() || !on.getAsJsonPrimitive().isBoolean()) {
            return Optional.empty();
        }
        if (!on.getAsBoolean()) {
            return exactly(message, AIRPLANE)
                    ? Optional.of(new PhoneActionPacket(session, Action.FACE_ID, "", "", false))
                    : Optional.empty();
        }
        String code = string(message.get("code"));
        return exactly(message, FACE_ON) && PhoneCode.isValid(code)
                ? Optional.of(new PhoneActionPacket(session, Action.FACE_ID, code, "", true))
                : Optional.empty();
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
