package dev.oneforallsurvival.client.title;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.regex.Pattern;

/**
 * Partie pure de la lecture de l'adresse du serveur sur le site : analyse de la réponse JSON, validation
 * « hôte[:port] » et choix entre l'adresse du site, la dernière adresse gardée et le repli du code. Aucune classe de
 * Minecraft ni accès réseau ou disque ici.
 */
public final class SiteAddress {
    /** Taille maximale d'une réponse lue (en caractères) ; au-delà, elle est ignorée. */
    public static final int MAX_BODY = 4096;
    /** Longueur maximale d'une adresse « hôte[:port] ». */
    public static final int MAX_ADDRESS = 255;
    /** Longueur maximale du message de maintenance conservé. */
    public static final int MAX_MESSAGE = 200;

    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    /** Réponse du site : adresse valide ou {@code null}, maintenance, message de maintenance ({@code null} si vide). */
    public record Reply(String address, boolean maintenance, String message) {}

    /** État retenu pour le bouton : adresse à utiliser ({@code null} = aucune), maintenance en direct, message. */
    public record Status(String address, boolean maintenance, String message) {
        public boolean joinable() {
            return address != null && !maintenance;
        }
    }

    private SiteAddress() {}

    /**
     * Analyse la réponse du site. {@code null} si ce n'est pas un objet JSON exploitable. Une adresse invalide donne
     * une réponse sans adresse (la maintenance reste lue) ; le message est du texte brut, nettoyé et tronqué.
     */
    public static Reply parse(String body) {
        if (body == null || body.length() > MAX_BODY) {
            return null;
        }
        JsonObject json;
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return null;
            }
            json = root.getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
        String address = valid(stringOf(json, "adresse"));
        JsonElement flag = json.get("maintenance");
        boolean maintenance = flag != null && flag.isJsonPrimitive() && flag.getAsJsonPrimitive().isBoolean()
                && flag.getAsBoolean();
        return new Reply(address, maintenance, cleanMessage(stringOf(json, "message")));
    }

    private static String stringOf(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    /** Adresse nettoyée (espaces retirés) si elle est un « hôte[:port] » valide, sinon {@code null}. */
    public static String valid(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || s.length() > MAX_ADDRESS) {
            return null;
        }
        String host = s;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            host = s.substring(0, colon);
            String port = s.substring(colon + 1);
            if (port.isEmpty() || port.length() > 5 || !port.chars().allMatch(c -> c >= '0' && c <= '9')) {
                return null;
            }
            int p = Integer.parseInt(port);
            if (p < 1 || p > 65535) {
                return null;
            }
        }
        return validHost(host) ? s : null;
    }

    private static boolean validHost(String host) {
        if (host.isEmpty() || host.length() > 253) {
            return false;
        }
        var ip = IPV4.matcher(host);
        if (ip.matches()) {
            for (int i = 1; i <= 4; i++) {
                if (Integer.parseInt(ip.group(i)) > 255) {
                    return false;
                }
            }
            return true;
        }
        for (String label : host.split("\\.", -1)) {
            if (!LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return !host.chars().allMatch(c -> c >= '0' && c <= '9' || c == '.'); // « 1.2.3 » n'est pas un nom
    }

    /** Texte brut : caractères de contrôle et espaces fusionnés, tronqué ; {@code null} si vide. */
    static String cleanMessage(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() > MAX_MESSAGE) {
            s = s.substring(0, MAX_MESSAGE - 1).stripTrailing() + "…";
        }
        return s;
    }

    /**
     * Choix de l'état : adresse du site si valide, sinon dernière adresse gardée si valide, sinon repli du code, sinon
     * aucune. La maintenance ne vient que de la réponse en direct ({@code reply} nul : pas de maintenance).
     */
    public static Status choose(Reply reply, String cached, String fallback) {
        String address = reply != null ? reply.address() : null;
        if (address == null) {
            address = valid(cached);
        }
        if (address == null) {
            address = valid(fallback);
        }
        boolean maintenance = reply != null && reply.maintenance();
        return new Status(address, maintenance, maintenance ? reply.message() : null);
    }
}
