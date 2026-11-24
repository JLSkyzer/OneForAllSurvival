package dev.oneforallsurvival.phone.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.oneforallsurvival.phone.net.OperatorActionPacket;
import dev.oneforallsurvival.phone.net.OperatorActionPacket.Action;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Relit un geste de l'appli « Antenne » (lot T2, tâche 10) avant d'en faire un {@link OperatorActionPacket}, au numéro
 * du panneau que Java tient (jamais celui de la page). Contrat : {@code {"a":"opTariff","value":N}} (entier JSON de 0 à
 * 1 000 000) ; {@code opIssue}, {@code opRefuse}, {@code opSuspend}, {@code opReactivate}, {@code opReplace},
 * {@code opMain} avec {@code "id"} (UUID canonique en minuscules) ; {@code {"a":"opAnonymous"}} ;
 * {@code {"a":"opPage","n":N}} (0 à 63) ; {@code {"a":"opClose"}}. Exactement ces champs : un champ de plus, un type
 * faux, un nombre écrit autrement qu'un entier ({@code 5.0}, {@code 5e0}, {@code "5"}) ou un identifiant mal formé, et
 * rien ne part. Ni nom ni caisse : ces gestes n'ont pas de message. Le serveur rejoue tout de toute façon.
 */
public final class AntennaBridgeMessages {

    public static final long MAX_TARIFF = 1_000_000L;
    public static final int MAX_PAGE = 63;

    private static final Set<String> ONLY_A = Set.of("a");
    private static final Set<String> ID = Set.of("a", "id");
    private static final Set<String> VALUE = Set.of("a", "value");
    private static final Set<String> N = Set.of("a", "n");
    /** Un entier JSON écrit sans signe, sans zéro de tête, sans partie décimale ni exposant. */
    private static final Pattern INTEGER = Pattern.compile("0|[1-9][0-9]{0,6}");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private AntennaBridgeMessages() {}

    /** Ce message est-il un geste de l'appli (son nom commence par {@code op}) ? */
    public static boolean isAntenna(@Nullable String a) {
        return a != null && a.startsWith("op");
    }

    public static Optional<OperatorActionPacket> toAction(@Nullable JsonObject message, int session) {
        if (message == null) {
            return Optional.empty();
        }
        String a = string(message.get("a"));
        if (a == null) {
            return Optional.empty();
        }
        return switch (a) {
            case "opTariff" -> {
                long value = integer(message.get("value"), MAX_TARIFF);
                yield exactly(message, VALUE) && value >= 0L
                        ? Optional.of(new OperatorActionPacket(session, Action.SET_TARIFF, "", value))
                        : Optional.empty();
            }
            case "opIssue" -> withId(message, session, Action.ISSUE);
            case "opRefuse" -> withId(message, session, Action.REFUSE);
            case "opSuspend" -> withId(message, session, Action.SUSPEND);
            case "opReactivate" -> withId(message, session, Action.REACTIVATE);
            case "opReplace" -> withId(message, session, Action.REPLACE);
            case "opMain" -> withId(message, session, Action.SET_MAIN);
            case "opAnonymous" -> exactly(message, ONLY_A)
                    ? Optional.of(new OperatorActionPacket(session, Action.ISSUE_ANONYMOUS, "", 0L))
                    : Optional.empty();
            case "opPage" -> {
                long n = integer(message.get("n"), MAX_PAGE);
                yield exactly(message, N) && n >= 0L
                        ? Optional.of(new OperatorActionPacket(session, Action.PAGE, "", n))
                        : Optional.empty();
            }
            case "opClose" -> exactly(message, ONLY_A)
                    ? Optional.of(new OperatorActionPacket(session, Action.CLOSE, "", 0L))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static Optional<OperatorActionPacket> withId(JsonObject message, int session, Action action) {
        String id = string(message.get("id"));
        return exactly(message, ID) && canonicalUuid(id)
                ? Optional.of(new OperatorActionPacket(session, action, id, 0L))
                : Optional.empty();
    }

    /** Un UUID écrit comme Java l'écrit : 36 caractères, minuscules, tirets à leur place. */
    static boolean canonicalUuid(@Nullable String id) {
        if (id == null || !UUID_TEXT.matcher(id).matches()) {
            return false;
        }
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Un entier JSON de 0 à {@code max}, tel que la page l'a écrit (Gson garde l'écriture d'un nombre lu), ou -1 pour
     * tout autre type, écriture ou valeur.
     */
    private static long integer(@Nullable JsonElement e, long max) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return -1L;
        }
        String text = e.getAsJsonPrimitive().getAsString();
        if (!INTEGER.matcher(text).matches()) {
            return -1L;
        }
        long n = Long.parseLong(text);
        return n <= max ? n : -1L;
    }

    private static boolean exactly(JsonObject message, Set<String> keys) {
        return message.keySet().equals(keys);
    }

    @Nullable
    private static String string(@Nullable JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        return p.isString() ? p.getAsString() : null;
    }
}
