package dev.oneforallsurvival.phone.net;

import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : un geste du panneau de l'opérateur (lot T2, tâche 9), quel que soit l'écran qui l'envoie
 * (panneau Minecraft du bloc, panneau ouvert depuis le téléphone, appli « Antenne »). Rien n'y est cru : tout est rejoué
 * par {@link OperatorActions#apply}, seul jeu de règles.
 *
 * @param session le numéro du panneau ouvert ({@link OperatorView#session()})
 * @param arg le nom ({@link Action#NAME}), l'identifiant d'une demande ({@link Action#ISSUE}, {@link Action#REFUSE}),
 *        d'une carte ({@link Action#SUSPEND}, {@link Action#REACTIVATE}, {@link Action#REPLACE}) ou d'une antenne
 *        ({@link Action#SET_MAIN}) ; vide sinon
 * @param value le tarif de demain en cuivres ({@link Action#SET_TARIFF}) ou la page ({@link Action#PAGE})
 */
public record OperatorActionPacket(int session, Action action, String arg, long value) {

    /** Un identifiant (36 caractères) ou un nom (24 au plus). */
    public static final int MAX_ARG = 36;

    /**
     * Les gestes. {@code REPLACE} (« Remplacer la carte ») n'est pas dans la liste du plan : décision de Killian du
     * 2026-11-24, il branche {@link PhoneSims#replaceCard} sur le panneau.
     */
    public enum Action {
        NAME, SET_TARIFF, ISSUE, REFUSE, ISSUE_ANONYMOUS, SUSPEND, REACTIVATE, REPLACE, SET_MAIN, PAGE, OPEN_TILL, CLOSE
    }

    public OperatorActionPacket {
        arg = arg == null ? "" : arg;
        if (action == null) {
            throw new IllegalArgumentException("geste sans action");
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(session);
        buf.writeVarInt(action.ordinal());
        buf.writeUtf(arg, MAX_ARG);
        buf.writeLong(value);
    }

    public static OperatorActionPacket decode(FriendlyByteBuf buf) {
        int session = buf.readVarInt();
        int ordinal = buf.readVarInt();
        Action[] actions = Action.values();
        if (ordinal < 0 || ordinal >= actions.length) {
            throw new DecoderException("geste inconnu : " + ordinal);
        }
        String arg = buf.readUtf(MAX_ARG);
        long value = buf.readLong();
        return new OperatorActionPacket(session, actions[ordinal], arg, value);
    }

    /** Sur le fil du serveur (consommateur du fil principal). */
    public static void handle(OperatorActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            OperatorActions.apply(sender, packet);
        }
    }
}
