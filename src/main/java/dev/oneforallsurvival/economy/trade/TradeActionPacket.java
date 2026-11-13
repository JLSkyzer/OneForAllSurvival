package dev.oneforallsurvival.economy.trade;

import dev.oneforallsurvival.economy.Coin;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : un geste dans la fenêtre d'échange. Le serveur revalide tout : joueur toujours en ligne,
 * fenêtre d'échange ouverte sur sa session en cours, version vue (confirmer, cocher le reçu), coupure et nombre
 * conformes à un bouton de l'écran (+1 ou +10).
 */
public record TradeActionPacket(int action, int seenVersion, int coin, int count) {

    public static final int CONFIRM = 0;
    public static final int TOGGLE_RECEIPT = 1;
    public static final int ADD_COINS = 2;
    public static final int CANCEL = 3;

    /** Les nombres qu'un bouton de pièces peut demander. */
    public static final int[] COIN_STEPS = {1, 10};

    public static TradeActionPacket confirm(int seenVersion) {
        return new TradeActionPacket(CONFIRM, seenVersion, 0, 0);
    }

    public static TradeActionPacket toggleReceipt(int seenVersion) {
        return new TradeActionPacket(TOGGLE_RECEIPT, seenVersion, 0, 0);
    }

    public static TradeActionPacket addCoins(Coin coin, int count) {
        return new TradeActionPacket(ADD_COINS, 0, coin.ordinal(), count);
    }

    public static TradeActionPacket cancel() {
        return new TradeActionPacket(CANCEL, 0, 0, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(seenVersion);
        buf.writeVarInt(coin);
        buf.writeVarInt(count);
    }

    public static TradeActionPacket decode(FriendlyByteBuf buf) {
        return new TradeActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(TradeActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender != null) {
            apply(sender, packet);
        }
    }

    /** Le contrôle et le geste, sans le réseau. */
    public static void apply(ServerPlayer player, TradeActionPacket packet) {
        if (player.connection == null || !player.connection.isAcceptingMessages() || player.hasDisconnected()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return;
        }
        TradeSession session = TradeService.sessionOf(player.getUUID());
        if (session == null || !(player.containerMenu instanceof TradeMenu menu) || menu.session() != session
                || !menu.stillValid(player)) {
            return;
        }
        switch (packet.action) {
            case CONFIRM -> {
                TradeSession.ConfirmResult result = session.confirm(player, packet.seenVersion);
                if (result == TradeSession.ConfirmResult.STALE) {
                    player.displayClientMessage(Component.translatable(TradeService.MESSAGE + "stale"), true);
                }
            }
            case TOGGLE_RECEIPT -> session.toggleReceipt(player, packet.seenVersion);
            case ADD_COINS -> {
                if (packet.coin < 0 || packet.coin >= Coin.values().length || !validStep(packet.count)) {
                    return;
                }
                if (!session.addCoins(player, Coin.values()[packet.coin], packet.count)) {
                    player.displayClientMessage(Component.translatable(TradeService.MESSAGE + "coins_refused"), true);
                }
            }
            case CANCEL -> TradeService.cancel(session, TradeService.CancelReason.CLOSED);
            default -> {
                return;
            }
        }
        // Les deux fenêtres suivent tout de suite, sans attendre le tick.
        TradeService.broadcast(session);
    }

    private static boolean validStep(int count) {
        for (int step : COIN_STEPS) {
            if (step == count) {
                return true;
            }
        }
        return false;
    }
}
