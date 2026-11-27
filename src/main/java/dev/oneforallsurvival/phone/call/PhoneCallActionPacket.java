package dev.oneforallsurvival.phone.call;

import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur (lot T3, tâche 4a) : un geste d'appel ou de carnet depuis l'écran du téléphone ouvert sous le
 * numéro {@link #session}. Rien n'est cru : {@link CallActions#act} revérifie la session, le téléphone en main, le
 * verrou et le frein, puis {@link CallService} et {@link PhoneDirectory} jugent le geste.
 *
 * @param first {@link Action#DIAL} : le numéro composé ; {@link Action#ANSWER}, {@link Action#DECLINE},
 *     {@link Action#HANGUP} : le numéro de l'appel visé, en chiffres décimaux ; {@link Action#MUTE},
 *     {@link Action#SPEAKER} : {@code "on"} ou {@code "off"} ; {@link Action#SAVE_CONTACT},
 *     {@link Action#REMOVE_CONTACT} : le numéro du contact ; {@link Action#SHARE_SEND} : le numéro d'entité du
 *     destinataire, en chiffres décimaux ; sinon vide
 * @param second {@link Action#SAVE_CONTACT} : le nom du contact ; sinon vide
 */
public record PhoneCallActionPacket(int session, Action action, String first, String second) {

    /** Borne de chaque texte, à la construction (coupé) et à la lecture (refusé au-delà). */
    public static final int MAX_TEXT = 32;

    public enum Action {
        DIAL, ANSWER, DECLINE, HANGUP, MUTE, SPEAKER, SAVE_CONTACT, REMOVE_CONTACT, OPEN_PHONE_APP,
        // Retours d'essai du 27/11, « Partager mon numéro » : la liste des personnes à proximité, puis l'envoi à l'une.
        SHARE_SCAN, SHARE_SEND
    }

    public PhoneCallActionPacket {
        if (action == null) {
            throw new IllegalArgumentException("action");
        }
        first = bound(first, MAX_TEXT);
        second = bound(second, MAX_TEXT);
    }

    /** {@code text} coupé à {@code max} unités UTF-16, sans couper un caractère en deux ; nul se lit vide. */
    static String bound(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        int cut = max;
        if (Character.isHighSurrogate(text.charAt(cut - 1))) {
            cut--;
        }
        return text.substring(0, cut);
    }

    /** Sans numéro ni nom : seuls le silence et le haut-parleur ({@code on}/{@code off}) restent lisibles. */
    @Override
    public String toString() {
        boolean plain = (action == Action.MUTE || action == Action.SPEAKER)
                && (first.equals("on") || first.equals("off"));
        return "PhoneCallActionPacket[session=" + session + ", action=" + action + ", first="
                + (plain ? first : "***") + ", second=***]";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(session);
        buf.writeByte(action.ordinal());
        buf.writeUtf(first, MAX_TEXT);
        buf.writeUtf(second, MAX_TEXT);
    }

    public static PhoneCallActionPacket decode(FriendlyByteBuf buf) {
        int session = buf.readVarInt();
        int ordinal = buf.readUnsignedByte();
        if (ordinal >= Action.values().length) {
            throw new DecoderException("geste d'appel inconnu : " + ordinal);
        }
        return new PhoneCallActionPacket(session, Action.values()[ordinal], buf.readUtf(MAX_TEXT),
                buf.readUtf(MAX_TEXT));
    }

    /** Sur le fil du serveur (consommateur du fil principal). */
    public static void handle(PhoneCallActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            CallActions.act(sender, packet);
        }
    }
}
