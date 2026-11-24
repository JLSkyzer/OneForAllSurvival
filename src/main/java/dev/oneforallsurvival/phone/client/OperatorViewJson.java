package dev.oneforallsurvival.phone.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.oneforallsurvival.phone.net.OperatorView;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.SimNumbers;

/**
 * La vue passée à {@code window.phone.antenna} (appli « Antenne », lot T2, tâche 10 ; contrat de
 * {@code html/phone/phone.js}) : {@code {session, name, rights: {cards, tariff}, tariff, nextTariff (nombre ou null),
 * cap, antennas: [{id, dimension, x, y, z, state, standby, clients, traffic, total, feToday, feYesterday, main,
 * overReceive}], cards: {page, pages, total, items: [{id, number, holder, state}]}, requests: [{id, name, at}], relay:
 * [{name, antennas, inService, status}]}}.
 *
 * <p>Absents par choix (liste gardée par {@code OperatorViewJsonTest}) : {@code source}, {@code pos} (l'antenne
 * cliquée), {@code operatorId}, {@code till} (la caisse ne se voit ni ne se vide à distance), les droits {@code till} et
 * {@code name}, l'identifiant d'opérateur du relais. {@code number} est formaté ({@code 06 12 34 56 78}), {@code holder}
 * vide pour une carte sans nom, {@code state} au nom de l'énumération, {@code status} en français
 * (« joignable », « hors tension », « sans antenne »). Aucune règle ici : la vue est recopiée.
 */
public final class OperatorViewJson {

    private OperatorViewJson() {}

    public static JsonObject toJson(OperatorView view) {
        JsonObject v = new JsonObject();
        v.addProperty("session", view.session());
        v.addProperty("name", view.name());
        JsonObject rights = new JsonObject();
        rights.addProperty("cards", view.rights().cards());
        rights.addProperty("tariff", view.rights().tariff());
        v.add("rights", rights);
        v.addProperty("tariff", view.tariff());
        if (view.nextTariff() == null) {
            v.add("nextTariff", JsonNull.INSTANCE);
        } else {
            v.addProperty("nextTariff", view.nextTariff());
        }
        v.addProperty("cap", view.cap());
        JsonArray antennas = new JsonArray();
        for (OperatorView.AntennaLine a : view.antennas()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", a.id().toString());
            o.addProperty("dimension", a.dimension());
            o.addProperty("x", a.pos().getX());
            o.addProperty("y", a.pos().getY());
            o.addProperty("z", a.pos().getZ());
            o.addProperty("state", a.state().name());
            o.addProperty("standby", a.load().standby());
            o.addProperty("clients", a.load().clients());
            o.addProperty("traffic", a.load().traffic());
            o.addProperty("total", a.load().total());
            o.addProperty("feToday", a.feToday());
            o.addProperty("feYesterday", a.feYesterday());
            o.addProperty("main", a.main());
            o.addProperty("overReceive", a.overReceive());
            antennas.add(o);
        }
        v.add("antennas", antennas);
        JsonObject cards = new JsonObject();
        cards.addProperty("page", view.cardPage());
        cards.addProperty("pages", view.cardPages());
        cards.addProperty("total", view.cardTotal());
        JsonArray items = new JsonArray();
        for (OperatorView.CardLine c : view.cards()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", c.simId().toString());
            o.addProperty("number", SimNumbers.format(c.number()));
            o.addProperty("holder", c.holderName());
            o.addProperty("state", c.state().name());
            items.add(o);
        }
        cards.add("items", items);
        v.add("cards", cards);
        JsonArray requests = new JsonArray();
        for (OperatorView.RequestLine r : view.requests()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id().toString());
            o.addProperty("name", r.playerName());
            o.addProperty("at", r.at());
            requests.add(o);
        }
        v.add("requests", requests);
        JsonArray relay = new JsonArray();
        for (PhoneNetwork.RelayState s : view.relay()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", s.name());
            o.addProperty("antennas", s.antennas());
            o.addProperty("inService", s.inService());
            o.addProperty("status", status(s));
            relay.add(o);
        }
        v.add("relay", relay);
        return v;
    }

    /** L'état d'un opérateur au relais, comme le panneau Minecraft ({@code OperatorPanelLayout.relayStatus}). */
    static String status(PhoneNetwork.RelayState s) {
        return s.inService() > 0 ? "joignable" : s.antennas() > 0 ? "hors tension" : "sans antenne";
    }
}
