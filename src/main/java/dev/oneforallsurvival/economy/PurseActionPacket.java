package dev.oneforallsurvival.economy;

import dev.oneforallsurvival.economy.CoinActions.Result;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : une opération ({@link CoinRequest}) sur la bourse qui est dans la case {@code slot} de
 * l'inventaire. Le serveur revalide tout : joueur toujours en ligne, demande conforme à un bouton de l'écran, écran
 * de cette bourse ouvert et bourse toujours dans sa case. L'opération elle-même est {@link CoinActions#apply},
 * commune avec le coffre-fort ; la bourse n'échange qu'avec les pièces en vrac, jamais avec une autre bourse.
 *
 * <p>Deux clics rapides font deux paquets traités l'un après l'autre, le second voit la bourse déjà changée ; un
 * joueur qui part entre l'envoi et le traitement ne déclenche rien ({@link Result#GONE}).
 */
public record PurseActionPacket(int slot, CoinRequest request) {

    public static final String MESSAGE = "message.oneforallsurvival.purse.";

    public static PurseActionPacket depositAll(int slot) {
        return new PurseActionPacket(slot, CoinRequest.depositAll());
    }

    public static PurseActionPacket withdraw(int slot, Coin coin, int amount) {
        return new PurseActionPacket(slot, CoinRequest.withdraw(coin, amount));
    }

    public static PurseActionPacket groupAll(int slot) {
        return new PurseActionPacket(slot, CoinRequest.groupAll());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        request.encode(buf);
    }

    public static PurseActionPacket decode(FriendlyByteBuf buf) {
        return new PurseActionPacket(buf.readVarInt(), CoinRequest.decode(buf));
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(PurseActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        Result result = apply(sender, packet);
        if (CoinActions.isTold(result)) {
            sender.displayClientMessage(Component.translatable(MESSAGE + result.name().toLowerCase(Locale.ROOT)), true);
        }
        // L'écran et l'inventaire du client suivent tout de suite, sans attendre le tick.
        if (sender.containerMenu instanceof CoinAccountMenu menu) {
            menu.broadcastChanges();
        }
    }

    /** Le contrôle et l'opération, sans le réseau. */
    public static Result apply(ServerPlayer player, PurseActionPacket packet) {
        MinecraftServer server = player.server;
        // Un client déconnecté entre l'envoi et le traitement : l'inventaire ne serait plus sauvegardé.
        if (player.connection == null || !player.connection.isAcceptingMessages()
                || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return Result.GONE;
        }
        CoinRequest request = packet.request;
        Optional<Coin> coin = request == null ? Optional.empty() : request.coinIfValid();
        if (coin.isEmpty()) {
            return Result.BAD_REQUEST;
        }
        if (!(player.containerMenu instanceof PurseMenu menu) || menu.slot() != packet.slot || !menu.stillValid(player)) {
            return Result.NOT_OPEN;
        }
        return CoinActions.apply(player, new PurseStore(menu.purse()), request.action(), coin.get(), request.amount(),
                request.all());
    }
}
