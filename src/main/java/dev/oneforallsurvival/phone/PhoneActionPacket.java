package dev.oneforallsurvival.phone;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : un geste de l'écran du téléphone, pour la session {@link #session}. Un client n'est jamais cru
 * sur parole : {@link PhoneService#act} revérifie la session, le joueur, le téléphone en main, l'état du verrou, le
 * propriétaire, le format du code et le blocage.
 *
 * @param first le code ({@link Action#UNLOCK}, {@link Action#SETUP}, {@link Action#ERASE}), l'ancien code
 *     ({@link Action#CHANGE_CODE}), le fond ({@link Action#WALLPAPER}), la case de l'inventaire de {@code "0"} à
 *     {@code "40"} ({@link Action#INSERT_SIM}) ou l'identifiant d'opérateur, 12 caractères hexadécimaux en minuscules
 *     ({@link Action#REQUEST_SIM}) ; sinon vide
 * @param second le nouveau code ({@link Action#CHANGE_CODE}), le fond choisi par l'assistant de première
 *     configuration ({@link Action#SETUP}, vide sans assistant ; un fond hors de {@link PhoneWallpapers#IDS} est
 *     ignoré par le serveur) ; sinon vide
 * @param flag le mode avion voulu ({@link Action#AIRPLANE})
 */
public record PhoneActionPacket(int session, Action action, String first, String second, boolean flag) {

    /** Borne de chaque texte sur le réseau : au-delà, le paquet ne se lit pas. */
    public static final int MAX_WIRE_TEXT = 16;

    public enum Action {
        UNLOCK, SETUP, LOCK, CHANGE_CODE, WALLPAPER, AIRPLANE, ERASE, CLOSE,
        // Lot T2 : la carte SIM, la demande de carte et l'appli « Antenne » (déverrouillé seulement).
        INSERT_SIM, REMOVE_SIM, REQUEST_SIM, OPEN_OPERATOR_APP
    }

    public PhoneActionPacket {
        first = first == null ? "" : first;
        second = second == null ? "" : second;
    }

    /** Sans les codes : {@code first} et {@code second} valent {@code ****} pour un geste qui porte un code. */
    @Override
    public String toString() {
        boolean secret = action == Action.UNLOCK || action == Action.SETUP || action == Action.ERASE
                || action == Action.CHANGE_CODE;
        return "PhoneActionPacket[session=" + session + ", action=" + action + ", first="
                + (secret ? "****" : first) + ", second=" + (secret ? "****" : second) + ", flag=" + flag + "]";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(session);
        buf.writeByte(action.ordinal());
        buf.writeUtf(first, MAX_WIRE_TEXT);
        buf.writeUtf(second, MAX_WIRE_TEXT);
        buf.writeBoolean(flag);
    }

    public static PhoneActionPacket decode(FriendlyByteBuf buf) {
        int session = buf.readVarInt();
        Action action = PhoneStatePacket.pick(Action.values(), buf.readUnsignedByte(), "action");
        String first = buf.readUtf(MAX_WIRE_TEXT);
        String second = buf.readUtf(MAX_WIRE_TEXT);
        boolean flag = buf.readBoolean();
        return new PhoneActionPacket(session, action, first, second, flag);
    }

    /** Sur le thread du serveur (consommateur du fil principal). */
    public static void handle(PhoneActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            PhoneService.act(sender, packet);
        }
    }
}
