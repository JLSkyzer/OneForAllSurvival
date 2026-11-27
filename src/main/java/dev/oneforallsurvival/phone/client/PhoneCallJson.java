package dev.oneforallsurvival.phone.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.oneforallsurvival.phone.call.Contact;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Book;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import dev.oneforallsurvival.phone.call.Recent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Lot T3, tâche 4b : ce que Java passe à la page du téléphone (contrat de {@code html/phone/phone.js}).
 * {@code phone.call} : {@code {phase: "NONE"|"OUTGOING"|"INCOMING"|"ACTIVE", id, number, name, seconds (écoulées depuis
 * le décroché, 0 avant), muted, speaker, message (déjà traduit, "" sans message)}} ; {@code phone.directory} :
 * {@code {contacts: [{number, name}], recents: [{number, name, kind: "OUTGOING"|"INCOMING"|"MISSED", at (ms), seconds}]}}
 * dans l'ordre du serveur, le nom d'un récent pris au carnet (vide s'il n'y est pas). Aucune règle ici : l'état est
 * recopié.
 */
public final class PhoneCallJson {

    private PhoneCallJson() {}

    /**
     * @param serverNow l'heure du serveur estimée chez le client ({@code EconomyClock} du serveur), en ms
     * @param message le message de l'état, déjà traduit ; nul se lit vide
     */
    public static JsonObject call(PhoneCallStatePacket call, long serverNow, @Nullable String message) {
        if (!call.here()) {
            // Revue de la tâche 4a, I3 : un appel porté par un autre téléphone n'est pas montré par cet écran. Seule la
            // réponse à un geste d'ici garde son message ; une fin ailleurs passe par la barre d'action.
            return blank(call.phase() == Phase.NONE ? "" : message);
        }
        JsonObject v = new JsonObject();
        v.addProperty("phase", call.phase().name());
        v.addProperty("id", call.callId());
        v.addProperty("number", call.number());
        v.addProperty("name", call.name());
        v.addProperty("seconds", seconds(call, serverNow));
        v.addProperty("muted", call.muted());
        v.addProperty("speaker", call.speaker());
        v.addProperty("message", message == null ? "" : message);
        return v;
    }

    private static JsonObject blank(@Nullable String message) {
        JsonObject v = new JsonObject();
        v.addProperty("phase", Phase.NONE.name());
        v.addProperty("id", 0);
        v.addProperty("number", "");
        v.addProperty("name", "");
        v.addProperty("seconds", 0);
        v.addProperty("muted", false);
        v.addProperty("speaker", false);
        v.addProperty("message", message == null ? "" : message);
        return v;
    }

    /** Les secondes depuis le décroché, d'un appel en cours seulement ; jamais négatives, bornées à un {@code int}. */
    static int seconds(PhoneCallStatePacket call, long serverNow) {
        if (call.phase() != Phase.ACTIVE || call.answeredMillis() < 0 || serverNow <= call.answeredMillis()) {
            return 0;
        }
        long elapsed = (serverNow - call.answeredMillis()) / 1000L;
        return elapsed >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed;
    }

    /**
     * Le carnet à passer à la page avec cet état : celui que le serveur a joint, s'il en a joint un. La règle est au
     * serveur, qui ne joint un carnet qu'à bon droit (écran déverrouillé, carte du téléphone de la session) : il ne se
     * juge pas ici, et surtout pas sur {@link PhoneCallStatePacket#here()} (revue de la tâche 4b, I2 : pendant un appel
     * porté par un autre téléphone, la réponse arrive marquée ailleurs et l'application restait sur « Chargement… »).
     */
    static Optional<JsonObject> directoryOf(PhoneCallStatePacket call) {
        return Optional.ofNullable(call.book()).map(PhoneCallJson::directory);
    }

    public static JsonObject directory(Book book) {
        Map<String, String> names = new HashMap<>();
        JsonArray contacts = new JsonArray();
        for (Contact c : book.contacts()) {
            names.putIfAbsent(c.number(), c.name());
            JsonObject o = new JsonObject();
            o.addProperty("number", c.number());
            o.addProperty("name", c.name());
            contacts.add(o);
        }
        JsonArray recents = new JsonArray();
        for (Recent r : book.recents()) {
            JsonObject o = new JsonObject();
            o.addProperty("number", r.number());
            o.addProperty("name", names.getOrDefault(r.number(), ""));
            o.addProperty("kind", r.kind().name());
            o.addProperty("at", r.atMillis());
            o.addProperty("seconds", r.seconds());
            recents.add(o);
        }
        JsonObject d = new JsonObject();
        d.add("contacts", contacts);
        d.add("recents", recents);
        return d;
    }
}
