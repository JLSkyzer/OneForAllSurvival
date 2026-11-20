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
 *     ({@link Action#CHANGE_CODE}) ou le fond ({@link Action#WALLPAPER}) ; sinon vide
 * @param second le nouveau code ({@link Action#CHANGE_CODE}) ; sinon vide
 * @param flag le mode avion voulu ({@link Action#AIRPLANE})
 */
public record PhoneActionPacket(int session, Action action, String first, String second, boolean flag) {

    /** Borne de chaque texte sur le réseau : au-delà, le paquet ne se lit pas. */
    public static final int MAX_WIRE_TEXT = 16;

    public enum Action { UNLOCK, SETUP, LOCK, CHANGE_CODE, WALLPAPER, AIRPLANE, ERASE, CLOSE }

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
