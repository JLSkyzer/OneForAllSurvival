package dev.oneforallsurvival.phone.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.oneforallsurvival.phone.PhoneNetView;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.net.PhoneSims;

/**
 * La vue passée à {@code window.phone.render} (contrat de {@code html/phone/phone.js}) : {@code session}, {@code lock}
 * ({@code "SETUP"}, {@code "LOCKED"} ou {@code "UNLOCKED"}), {@code battery}, {@code serverMillis},
 * {@code zoneOffsetSeconds}, {@code wallpaper}, {@code airplane}, {@code faceId} (retours d'essai du 27/11 : Face ID
 * actif, vue déverrouillée du propriétaire seulement), {@code owner}, {@code ownerName},
 * {@code attemptsLeft}, {@code lockoutSeconds}, {@code last} (nom de {@link PhoneStatePacket.Result}) et, depuis le lot
 * T2, {@code net} : {@code {sim, operator, bars, number, holder, founderApp, founderAppAvailable, choices: [{slot,
 * number, operator, validity}], requestable: [{id, name, bars}]}} ({@code sim} et {@code validity} : noms de
 * {@link PhoneSims.Validity} ; {@code founderApp} : l'icône « Antenne » se montre ; {@code founderAppAvailable} : le
 * serveur l'ouvrirait, sinon la page la grise).
 *
 * <p>Hors {@link Lock#UNLOCKED}, rien du propriétaire ni des réglages, même si le paquet en portait ; la vue réseau y
 * est réduite une seconde fois ({@link PhoneNetView#locked()} : la page ne croit pas le paquet sur parole) et n'a ni
 * {@code number} ni {@code holder} : seuls l'état grossier de la carte ({@code NONE}, {@code UNKNOWN}, ou
 * {@code ACTIVE} à lire « présente »), l'opérateur et les barres restent, sans cartes, sans opérateurs, sans appli.
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
        v.addProperty("faceId", unlocked && state.owner() && state.faceId());
        v.addProperty("owner", unlocked && state.owner());
        v.addProperty("ownerName", unlocked ? state.ownerName() : "");
        v.addProperty("attemptsLeft", state.attemptsLeft());
        v.addProperty("lockoutSeconds", state.lockoutSeconds());
        v.addProperty("last", state.last().name());
        v.add("net", net(unlocked ? state.net() : state.net().locked(), unlocked));
        return v;
    }

    private static JsonObject net(PhoneNetView n, boolean unlocked) {
        JsonObject o = new JsonObject();
        o.addProperty("sim", n.sim().name());
        o.addProperty("operator", n.operator());
        o.addProperty("bars", n.bars());
        if (unlocked) {
            o.addProperty("number", n.number());
            o.addProperty("holder", n.holder());
        }
        o.addProperty("founderApp", unlocked && n.founderApp());
        o.addProperty("founderAppAvailable", unlocked && n.founderAppAvailable());
        JsonArray choices = new JsonArray();
        JsonArray requestable = new JsonArray();
        if (unlocked) {
            for (PhoneSims.SimChoice c : n.choices()) {
                JsonObject card = new JsonObject();
                card.addProperty("slot", c.slot());
                card.addProperty("number", c.number());
                card.addProperty("operator", c.operator());
                card.addProperty("validity", c.validity().name());
                choices.add(card);
            }
            for (PhoneNetView.OperatorChoice op : n.requestable()) {
                JsonObject r = new JsonObject();
                r.addProperty("id", op.id());
                r.addProperty("name", op.name());
                r.addProperty("bars", op.bars());
                requestable.add(r);
            }
        }
        o.add("choices", choices);
        o.add("requestable", requestable);
        return o;
    }
}
