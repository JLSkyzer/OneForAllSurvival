package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.CoinActions.Result;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

/**
 * Client vers serveur : une opération ({@link CoinRequest}) sur le compte du coffre-fort en {@code pos}. Le serveur
 * revalide tout, comme pour les autres paquets du mod : joueur toujours en ligne, demande conforme à un bouton de
 * l'écran, chunk chargé, distance, bloc toujours là, droit du joueur sur ce compte, écran de ce coffre-fort ouvert.
 * L'opération elle-même est {@link CoinActions#apply}, commune avec la bourse.
 *
 * <p>Deux clics rapides font deux paquets traités l'un après l'autre, le second voit le compte déjà changé ; un
 * joueur qui part entre l'envoi et le traitement ne déclenche rien ({@link Result#GONE}).
 */
public record SafeActionPacket(BlockPos pos, CoinRequest request) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static SafeActionPacket depositAll(BlockPos pos) {
        return new SafeActionPacket(pos, CoinRequest.depositAll());
    }

    /** Déposer jusqu'à {@code amount} pièces de cette coupure (clic gauche sur un bouton de ligne). */
    public static SafeActionPacket deposit(BlockPos pos, Coin coin, int amount) {
        return new SafeActionPacket(pos, CoinRequest.deposit(coin, amount));
    }

    public static SafeActionPacket withdraw(BlockPos pos, Coin coin, int amount) {
        return new SafeActionPacket(pos, CoinRequest.withdraw(coin, amount));
    }

    public static SafeActionPacket groupAll(BlockPos pos) {
        return new SafeActionPacket(pos, CoinRequest.groupAll());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        request.encode(buf);
    }

    public static SafeActionPacket decode(FriendlyByteBuf buf) {
        return new SafeActionPacket(buf.readBlockPos(), CoinRequest.decode(buf));
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(SafeActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        Result result = apply(sender, packet);
        if (CoinActions.isTold(result)) {
            sender.displayClientMessage(
                    Component.translatable(SafeBlock.MESSAGE + result.name().toLowerCase(Locale.ROOT)), true);
        }
        // L'écran et l'inventaire du client suivent tout de suite, sans attendre le tick.
        if (sender.containerMenu instanceof CoinAccountMenu menu) {
            menu.broadcastChanges();
        }
    }

    /** Le contrôle et l'opération, sans le réseau. */
    public static Result apply(ServerPlayer player, SafeActionPacket packet) {
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
        ServerLevel level = player.serverLevel();
        BlockPos pos = packet.pos;
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > SafeBlock.MAX_DISTANCE_SQR) {
            return Result.TOO_FAR;
        }
        if (!level.getBlockState(pos).is(EconomyRegistry.SAFE.get())
                || !(level.getBlockEntity(pos) instanceof SafeBlockEntity be) || be.owner() == null) {
            return Result.NO_SAFE;
        }
        UUID owner = be.owner();
        if (!SafeBlock.mayAccess(player, owner)) {
            return Result.NOT_ALLOWED;
        }
        if (!(player.containerMenu instanceof SafeMenu menu) || !menu.pos().equals(pos) || !owner.equals(menu.owner())
                || !menu.stillValid(player)) {
            return Result.NOT_OPEN;
        }
        Result result = CoinActions.apply(player, new SafeStore(SafeAccounts.get(server), owner), request.action(),
                coin.get(), request.amount(), request.all());
        if (result == Result.DONE && !owner.equals(player.getUUID())) {
            LOGGER.info("Economie : {} a agi sur le coffre-fort de {} ({})",
                    player.getGameProfile().getName(), owner, request);
        }
        return result;
    }
}
