package dev.oneforallsurvival.phone.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket.Action;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Relit un geste d'appel ou de carnet de la page du téléphone (lot T3, tâche 4b) avant d'en faire un
 * {@link PhoneCallActionPacket}, au numéro de session que Java tient. Contrat : {@code {"a":"callDial","number":"…"}} ;
 * {@code callAnswer}, {@code callDecline}, {@code callHangup} sans champ ; {@code {"a":"callMute"|"callSpeaker",
 * "on":true|false}} (booléen JSON) ; {@code {"a":"contactSave","number":"…","name":"…"}} ;
 * {@code {"a":"contactRemove","number":"…"}} ; {@code {"a":"phoneApp"}}. Un numéro : de 1 à
 * {@value PhoneCallActionPacket#MAX_TEXT} chiffres ASCII, sans espace (la page les retire) ; un nom : une chaîne de 1 à
 * {@value PhoneCallActionPacket#MAX_TEXT} unités UTF-16, nettoyée par le serveur ({@code ContactRules}). Exactement ces
 * champs : un champ de plus, un type faux ou un texte trop long, et rien ne part (jamais coupé ici).
 *
 * <p>Le numéro de l'appel visé par {@code callAnswer}, {@code callDecline} et {@code callHangup} vient du dernier état
 * d'appel reçu par Java, jamais de la page : répondre et refuser visent un appel {@link Phase#INCOMING}, raccrocher un
 * appel {@link Phase#OUTGOING} ou {@link Phase#ACTIVE}, porté par le téléphone de cet écran
 * ({@link PhoneCallStatePacket#here()}) ; sans un tel appel, rien ne part. Le serveur rejoue tout.
 */
public final class CallBridgeMessages {

    private static final Set<String> GESTURES = Set.of("callDial", "callAnswer", "callDecline", "callHangup",
            "callMute", "callSpeaker", "contactSave", "contactRemove", "phoneApp");
    private static final Set<String> ONLY_A = Set.of("a");
    private static final Set<String> NUMBER = Set.of("a", "number");
    private static final Set<String> NUMBER_NAME = Set.of("a", "number", "name");
    private static final Set<String> ON = Set.of("a", "on");
    private static final Pattern DIGITS = Pattern.compile("[0-9]{1," + PhoneCallActionPacket.MAX_TEXT + "}");

    private CallBridgeMessages() {}

    /** Ce message est-il un geste d'appel ou de carnet ? */
    public static boolean isCall(@Nullable String a) {
        return a != null && GESTURES.contains(a);
    }

    /**
     * @param last le dernier état d'appel reçu ({@code PhoneClient.lastCall()}), ou nul
     */
    public static Optional<PhoneCallActionPacket> toAction(@Nullable JsonObject message, int session,
            @Nullable PhoneCallStatePacket last) {
        if (message == null) {
            return Optional.empty();
        }
        String a = string(message.get("a"));
        if (a == null) {
            return Optional.empty();
        }
        return switch (a) {
            case "callDial" -> {
                String number = number(message.get("number"));
                yield exactly(message, NUMBER) && number != null
                        ? Optional.of(new PhoneCallActionPacket(session, Action.DIAL, number, ""))
                        : Optional.empty();
            }
            case "callAnswer" -> onCall(message, session, last, Action.ANSWER, Phase.INCOMING, null);
            case "callDecline" -> onCall(message, session, last, Action.DECLINE, Phase.INCOMING, null);
            case "callHangup" -> onCall(message, session, last, Action.HANGUP, Phase.OUTGOING, Phase.ACTIVE);
            case "callMute" -> onOff(message, session, Action.MUTE);
            case "callSpeaker" -> onOff(message, session, Action.SPEAKER);
            case "contactSave" -> {
                String number = number(message.get("number"));
                String name = string(message.get("name"));
                yield exactly(message, NUMBER_NAME) && number != null && name != null && !name.isEmpty()
                        && name.length() <= PhoneCallActionPacket.MAX_TEXT
                        ? Optional.of(new PhoneCallActionPacket(session, Action.SAVE_CONTACT, number, name))
                        : Optional.empty();
            }
            case "contactRemove" -> {
                String number = number(message.get("number"));
                yield exactly(message, NUMBER) && number != null
                        ? Optional.of(new PhoneCallActionPacket(session, Action.REMOVE_CONTACT, number, ""))
                        : Optional.empty();
            }
            case "phoneApp" -> exactly(message, ONLY_A)
                    ? Optional.of(new PhoneCallActionPacket(session, Action.OPEN_PHONE_APP, "", ""))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    /** Un geste sur l'appel en cours : sans champ, et seulement si le dernier état est un appel de l'une des phases. */
    private static Optional<PhoneCallActionPacket> onCall(JsonObject message, int session,
            @Nullable PhoneCallStatePacket last, Action action, Phase phase, @Nullable Phase other) {
        boolean live = last != null && last.here() && last.callId() > 0
                && (last.phase() == phase || last.phase() == other);
        return exactly(message, ONLY_A) && live
                ? Optional.of(new PhoneCallActionPacket(session, action, Integer.toString(last.callId()), ""))
                : Optional.empty();
    }

    private static Optional<PhoneCallActionPacket> onOff(JsonObject message, int session, Action action) {
        JsonElement on = message.get("on");
        return exactly(message, ON) && on != null && on.isJsonPrimitive() && on.getAsJsonPrimitive().isBoolean()
                ? Optional.of(new PhoneCallActionPacket(session, action, on.getAsBoolean() ? "on" : "off", ""))
                : Optional.empty();
    }

    /** Un numéro écrit par la page : de 1 à 32 chiffres ASCII, sinon {@code null}. */
    @Nullable
    private static String number(@Nullable JsonElement e) {
        String s = string(e);
        return s != null && DIGITS.matcher(s).matches() ? s : null;
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
