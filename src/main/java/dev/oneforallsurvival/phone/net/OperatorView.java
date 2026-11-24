package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.machine.MachineState;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * La vue de l'opérateur (lot T2, tâche 9) : <b>la seule</b> que montrent le panneau Minecraft de l'antenne, le panneau
 * ouvert depuis le téléphone et l'appli « Antenne » (tâche 10). Elle n'est construite que par le serveur
 * ({@link OperatorViews#build}) ; les écrans n'y décident rien, ils cachent un bouton selon {@link #rights()}.
 *
 * <p>Une carte n'y figure que par son {@code sim_id} et seulement si elle est de cet opérateur : ni jeton, ni téléphone.
 * Bornes : {@value #MAX_LIST} antennes, cartes (par page), demandes et opérateurs au relais ; textes de
 * {@value #MAX_TEXT} caractères. {@link #encode} refuse d'écrire au-delà et {@link #decode} lève.
 *
 * @param source d'où le panneau est ouvert : le bloc ou le téléphone
 * @param pos l'antenne cliquée ({@link BlockPos#ZERO} depuis le téléphone)
 * @param session le numéro du panneau ouvert ({@link OperatorPanels}) : un geste d'un autre numéro est périmé
 * @param operatorId l'opérateur (vide : antenne à fonder)
 * @param name son nom (vide : antenne à fonder)
 * @param tariff le tarif du jour, en cuivres
 * @param nextTariff le tarif demandé pour demain, nul s'il ne change pas
 * @param cap le plafond qui bornera le tarif de demain (celui du maire pour demain s'il l'a changé)
 * @param till la valeur de la caisse, en cuivres (formatée par l'écran dans la langue du joueur)
 * @param cardPage la page des cartes montrée (0 pour la première) ; @param cardPages leur nombre ; @param cardTotal
 *        le nombre de cartes de l'opérateur
 */
public record OperatorView(Source source, BlockPos pos, int session, String operatorId, String name, Rights rights,
        long tariff, @Nullable Long nextTariff, long cap, long till, List<AntennaLine> antennas, int cardPage,
        int cardPages, int cardTotal, List<CardLine> cards, List<RequestLine> requests,
        List<PhoneNetwork.RelayState> relay) {

    public static final int MAX_LIST = 32;
    public static final int MAX_TEXT = 32;

    public enum Source { BLOCK, PHONE }

    /**
     * Ce que le regardeur peut faire, tel que le serveur l'a jugé ({@link AntennaRules}) : les cartes (le fondateur), le
     * tarif (fondateur, maire, équipe), la caisse (fondateur et équipe, sur le bloc de l'antenne principale), le nom
     * (une antenne à fonder, sur le bloc).
     */
    public record Rights(boolean cards, boolean tariff, boolean till, boolean name) {

        public static final Rights NONE = new Rights(false, false, false, false);
    }

    /**
     * Une antenne de l'opérateur.
     *
     * @param dimension la dimension (identifiant, coupé à {@value #MAX_TEXT} caractères)
     * @param state son état de machine (celui du bloc s'il est chargé, sinon d'après le registre)
     * @param load le détail de la consommation du dernier tic, en FE par tic
     * @param main est-ce l'antenne principale (la caisse s'y ouvre) ?
     * @param overReceive consomme-t-elle par tic plus que ce qu'elle peut recevoir ?
     */
    public record AntennaLine(UUID id, String dimension, BlockPos pos, MachineState state, AntennaLoad.Breakdown load,
            long feToday, long feYesterday, boolean main, boolean overReceive) {}

    /** Une carte de l'opérateur : son identifiant, son numéro (« 06 » et huit chiffres), son titulaire, son état. */
    public record CardLine(UUID simId, String number, String holderName, SimCard.State state) {}

    /** Une demande de carte en attente. */
    public record RequestLine(UUID id, String playerName, long at) {}

    public OperatorView {
        operatorId = operatorId == null ? "" : operatorId;
        name = name == null ? "" : name;
        rights = rights == null ? Rights.NONE : rights;
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        antennas = List.copyOf(antennas);
        cards = List.copyOf(cards);
        requests = List.copyOf(requests);
        relay = List.copyOf(relay);
    }

    /** Coupe un texte à {@value #MAX_TEXT} caractères (jamais au milieu d'un caractère double). */
    public static String clip(@Nullable String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_TEXT) {
            return text;
        }
        int end = MAX_TEXT;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    // ---------------------------------------------------------------------------------------------
    // Réseau
    // ---------------------------------------------------------------------------------------------

    /** Écrit la vue. @throws IllegalArgumentException si une liste dépasse sa borne (jamais un paquet illisible) */
    public void encode(FriendlyByteBuf buf) {
        if (antennas.size() > MAX_LIST || cards.size() > MAX_LIST || requests.size() > MAX_LIST
                || relay.size() > MAX_LIST) {
            throw new IllegalArgumentException("vue de l'opérateur au-delà de ses bornes");
        }
        encodeUnbounded(buf);
    }

    /** Écrit la vue sans contrôler les listes (essais du décodage seulement). */
    void encodeUnbounded(FriendlyByteBuf buf) {
        buf.writeByte(source.ordinal());
        buf.writeBlockPos(pos);
        buf.writeVarInt(session);
        buf.writeUtf(operatorId);
        buf.writeUtf(name);
        buf.writeByte((rights.cards() ? 1 : 0) | (rights.tariff() ? 2 : 0) | (rights.till() ? 4 : 0)
                | (rights.name() ? 8 : 0));
        buf.writeVarLong(tariff);
        buf.writeBoolean(nextTariff != null);
        if (nextTariff != null) {
            buf.writeVarLong(nextTariff);
        }
        buf.writeVarLong(cap);
        buf.writeVarLong(till);
        buf.writeVarInt(antennas.size());
        for (AntennaLine a : antennas) {
            buf.writeUUID(a.id());
            buf.writeUtf(a.dimension());
            buf.writeBlockPos(a.pos());
            buf.writeByte(a.state().ordinal());
            buf.writeVarInt(a.load().standby());
            buf.writeVarInt(a.load().clients());
            buf.writeVarInt(a.load().traffic());
            buf.writeVarLong(a.feToday());
            buf.writeVarLong(a.feYesterday());
            buf.writeBoolean(a.main());
            buf.writeBoolean(a.overReceive());
        }
        buf.writeVarInt(cardPage);
        buf.writeVarInt(cardPages);
        buf.writeVarInt(cardTotal);
        buf.writeVarInt(cards.size());
        for (CardLine c : cards) {
            buf.writeUUID(c.simId());
            buf.writeUtf(c.number());
            buf.writeUtf(c.holderName());
            buf.writeByte(c.state().ordinal());
        }
        buf.writeVarInt(requests.size());
        for (RequestLine r : requests) {
            buf.writeUUID(r.id());
            buf.writeUtf(r.playerName());
            buf.writeVarLong(r.at());
        }
        buf.writeVarInt(relay.size());
        for (PhoneNetwork.RelayState s : relay) {
            buf.writeUtf(s.operatorId());
            buf.writeUtf(s.name());
            buf.writeVarInt(s.antennas());
            buf.writeVarInt(s.inService());
        }
    }

    public static OperatorView decode(FriendlyByteBuf buf) {
        Source source = of(Source.values(), buf.readByte(), "source");
        BlockPos pos = buf.readBlockPos();
        int session = buf.readVarInt();
        String operatorId = buf.readUtf(MAX_TEXT);
        String name = buf.readUtf(MAX_TEXT);
        int bits = buf.readByte();
        if ((bits & ~15) != 0) {
            throw new DecoderException("droits mal formés");
        }
        Rights rights = new Rights((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0);
        long tariff = buf.readVarLong();
        Long nextTariff = buf.readBoolean() ? buf.readVarLong() : null;
        long cap = buf.readVarLong();
        long till = buf.readVarLong();
        int n = count(buf, "antennes");
        List<AntennaLine> antennas = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            String dimension = buf.readUtf(MAX_TEXT);
            BlockPos at = buf.readBlockPos();
            MachineState state = of(MachineState.values(), buf.readByte(), "état d'antenne");
            AntennaLoad.Breakdown load = new AntennaLoad.Breakdown(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            antennas.add(new AntennaLine(id, dimension, at, state, load, buf.readVarLong(), buf.readVarLong(),
                    buf.readBoolean(), buf.readBoolean()));
        }
        int cardPage = buf.readVarInt();
        int cardPages = buf.readVarInt();
        int cardTotal = buf.readVarInt();
        n = count(buf, "cartes");
        List<CardLine> cards = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            String number = buf.readUtf(MAX_TEXT);
            String holder = buf.readUtf(MAX_TEXT);
            cards.add(new CardLine(id, number, holder, of(SimCard.State.values(), buf.readByte(), "état de carte")));
        }
        n = count(buf, "demandes");
        List<RequestLine> requests = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            requests.add(new RequestLine(buf.readUUID(), buf.readUtf(MAX_TEXT), buf.readVarLong()));
        }
        n = count(buf, "relais");
        List<PhoneNetwork.RelayState> relay = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            relay.add(new PhoneNetwork.RelayState(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readVarInt(),
                    buf.readVarInt()));
        }
        return new OperatorView(source, pos, session, operatorId, name, rights, tariff, nextTariff, cap, till,
                antennas, cardPage, cardPages, cardTotal, cards, requests, relay);
    }

    private static int count(FriendlyByteBuf buf, String what) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_LIST) {
            throw new DecoderException(what + " : " + n + " au lieu de " + MAX_LIST + " au plus");
        }
        return n;
    }

    private static <E> E of(E[] values, int ordinal, String what) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException(what + " inconnu : " + ordinal);
        }
        return values[ordinal];
    }
}
