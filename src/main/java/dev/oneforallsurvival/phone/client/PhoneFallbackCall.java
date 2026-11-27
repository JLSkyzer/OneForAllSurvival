package dev.oneforallsurvival.phone.client;

import dev.oneforallsurvival.phone.call.Emergency;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import dev.oneforallsurvival.phone.net.SimNumbers;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * L'appel dans l'écran de repli (lot T3, tâche 7), sans Minecraft (testable) : ce que l'écran montre selon les états
 * d'appel reçus, et quels boutons. Mêmes règles que la page : un état marqué ailleurs ({@code here} faux) se lit « aucun
 * appel » et ne garde son message que s'il n'est pas une fin ({@link PhoneCallJson#call}) ; une fin reste à l'écran
 * {@value #ENDING_MS} ms avec son message ; Silence et Haut-parleur sont grisés écran verrouillé (le serveur les
 * refuse). Aucun numéro n'est écrit nulle part.
 */
public final class PhoneFallbackCall {

    /** Le message de fin reste environ deux secondes (comme {@code ENDING_MS} de la page). */
    public static final long ENDING_MS = 2000L;
    /** Les textes propres au repli des appels ; les mots sont ceux de la page. */
    public static final String KEY = "screen.oneforallsurvival.phone.call.";

    public enum Mode { NONE, INCOMING, LIVE, ENDED }

    /** Les boutons de l'écran d'appel par mode, de gauche à droite puis de haut en bas. */
    public static List<String> buttons(Mode mode) {
        return switch (mode) {
            case INCOMING -> List.of("call-decline", "call-answer");
            case LIVE -> List.of("call-mute", "call-speaker", "call-hangup");
            case NONE, ENDED -> List.of();
        };
    }

    /** Silence et Haut-parleur ne sont vifs que déverrouillé ; répondre, refuser et raccrocher le sont toujours. */
    public static boolean active(String button, boolean unlocked) {
        return unlocked || !(button.equals("call-mute") || button.equals("call-speaker"));
    }

    /** Un texte de l'écran : une clé à traduire (ou rien), puis un texte tel quel. */
    public record Text(@Nullable String key, String literal) {
        static Text of(String literal) {
            return new Text(null, literal);
        }

        static Text key(String key) {
            return new Text(key, "");
        }
    }

    @Nullable private PhoneCallStatePacket current;
    @Nullable private PhoneCallStatePacket ended;
    private String endMessage = "";
    private long endUntil;

    /**
     * Reçoit un état d'appel ({@code message} : son message déjà traduit, vide sans message). Rend le message à montrer
     * hors de l'écran d'appel (un appel composé refusé, par exemple), vide sinon.
     */
    public String accept(PhoneCallStatePacket next, String message, long now) {
        boolean here = next.here();
        Phase phase = here ? next.phase() : Phase.NONE;
        String shown = here || next.phase() != Phase.NONE ? message : "";
        if (phase != Phase.NONE) {
            current = next;
            ended = null;
            return "";
        }
        if (current != null) {
            ended = current;
            endMessage = shown;
            endUntil = now + ENDING_MS;
            current = null;
            return "";
        }
        return shown;
    }

    public Mode mode(long now) {
        if (current != null) {
            return current.phase() == Phase.INCOMING ? Mode.INCOMING : Mode.LIVE;
        }
        return ended != null && now < endUntil ? Mode.ENDED : Mode.NONE;
    }

    /** L'appel montré (en cours ou sonnant), nul sans appel. */
    @Nullable
    public PhoneCallStatePacket live() {
        return current;
    }

    /** Le titre : le service d'une urgence, sinon le nom du carnet, sinon le numéro lisible. */
    public Text title(long now) {
        PhoneCallStatePacket c = current != null ? current : mode(now) == Mode.ENDED ? ended : null;
        return c == null ? Text.of("") : title(c.number(), c.name(), c.detail());
    }

    static Text title(String number, String name, String detail) {
        Optional<Emergency.Service> sos = Emergency.Service.of(number);
        if (sos.isPresent()) {
            // Chez l'appelant « Police 17 » ; chez le secours (qui a le détail) le texte du serveur, « Urgence police ».
            return detail.isEmpty() ? Text.key(KEY + "sos_" + sos.get().key())
                    : Text.key("message.oneforallsurvival.call.emergency." + sos.get().key());
        }
        if (!name.isEmpty()) {
            return Text.of(name);
        }
        return Text.of(number.isEmpty() ? "—" : SimNumbers.format(number));
    }

    /**
     * La ligne sous le titre : le message de fin ; la position d'une urgence (et sa durée en ligne) ; « Appel entrant »,
     * « Appel… » ou la durée. {@code serverNow} : l'heure du serveur estimée chez le client.
     */
    public Text sub(long now, long serverNow) {
        if (current == null) {
            return mode(now) == Mode.ENDED ? Text.of(endMessage) : Text.of("");
        }
        return sub(current, serverNow);
    }

    static Text sub(PhoneCallStatePacket c, long serverNow) {
        if (!c.detail().isEmpty()) {
            return Text.of(c.phase() == Phase.ACTIVE
                    ? c.detail() + " · " + duration(PhoneCallJson.seconds(c, serverNow)) : c.detail());
        }
        return switch (c.phase()) {
            case INCOMING -> Text.key(KEY + "incoming");
            case OUTGOING -> Text.key(KEY + "outgoing");
            default -> Text.of(duration(PhoneCallJson.seconds(c, serverNow)));
        };
    }

    /** « 1:05 », « 12:40 », « 1:02:09 » (comme la page). */
    static String duration(int s) {
        int h = s / 3600;
        int m = s / 60 % 60;
        int r = s % 60;
        return (h > 0 ? h + ":" + two(m) : String.valueOf(m)) + ":" + two(r);
    }

    /** « 0612345678 » s'écrit « 06 12 34 56 78 » (paires depuis la gauche, comme la page). */
    public static String pairs(String digits) {
        if (digits.length() <= 2) {
            return digits;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i += 2) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(digits, i, Math.min(digits.length(), i + 2));
        }
        return sb.toString();
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
