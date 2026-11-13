package dev.oneforallsurvival.economy.stall;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinActions;
import dev.oneforallsurvival.economy.CoinActions.Result;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.stall.StallLedger.Kind;
import dev.oneforallsurvival.economy.stall.StallLedger.Line;
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
 * Client vers serveur : une opération ({@link CoinRequest}) sur la caisse de l'étal en {@code pos}. Le serveur
 * revalide tout, comme pour le coffre-fort : joueur toujours en ligne, demande conforme à un bouton de l'écran,
 * chunk chargé, distance, bloc toujours là, droit de gérer l'étal, écran de cette caisse ouvert. L'opération elle-même
 * est {@link CoinActions#apply}, commune avec le coffre-fort et la bourse.
 *
 * <p>Quand la valeur de la caisse change, l'opération est notée au registre de l'étal : {@code CASH_IN} et
 * {@code CASH_OUT} par le propriétaire ; {@code STAFF} (montant signé) par l'équipe sur l'étal d'un autre, dont un
 * retrait est aussi un transfert du registre global ({@link MoneyTransfer#STAFF_WITHDRAW}). Casser ou regrouper ne
 * change pas la valeur : rien n'est noté.
 */
public record StallCashPacket(BlockPos pos, CoinRequest request) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static StallCashPacket depositAll(BlockPos pos) {
        return new StallCashPacket(pos, CoinRequest.depositAll());
    }

    public static StallCashPacket deposit(BlockPos pos, Coin coin, int amount) {
        return new StallCashPacket(pos, CoinRequest.deposit(coin, amount));
    }

    public static StallCashPacket withdraw(BlockPos pos, Coin coin, int amount) {
        return new StallCashPacket(pos, CoinRequest.withdraw(coin, amount));
    }

    public static StallCashPacket groupAll(BlockPos pos) {
        return new StallCashPacket(pos, CoinRequest.groupAll());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        request.encode(buf);
    }

    public static StallCashPacket decode(FriendlyByteBuf buf) {
        return new StallCashPacket(buf.readBlockPos(), CoinRequest.decode(buf));
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(StallCashPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        Result result = apply(sender, packet);
        if (CoinActions.isTold(result)) {
            sender.displayClientMessage(
                    Component.translatable(MarketStallBlock.MESSAGE + result.name().toLowerCase(Locale.ROOT)), true);
        }
        // L'écran et l'inventaire du client suivent tout de suite, sans attendre le tick.
        if (sender.containerMenu instanceof CoinAccountMenu menu) {
            menu.broadcastChanges();
        }
    }

    /** Le contrôle et l'opération, sans le réseau. {@link Result#NO_SAFE} dit « pas d'étal ici ». */
    public static Result apply(ServerPlayer player, StallCashPacket packet) {
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
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > MarketStallBlock.MAX_DISTANCE_SQR) {
            return Result.TOO_FAR;
        }
        if (!level.getBlockState(pos).is(EconomyRegistry.MARKET_STALL.get())
                || !(level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall)) {
            return Result.NO_SAFE;
        }
        if (!MarketStallBlock.mayManage(player, stall.owner())) {
            return Result.NOT_ALLOWED;
        }
        if (!(player.containerMenu instanceof StallCashMenu menu) || !menu.pos().equals(pos) || menu.stall() != stall
                || !menu.stillValid(player)) {
            return Result.NOT_OPEN;
        }
        StallCashStore store = new StallCashStore(stall);
        long before = stall.cashValue();
        Result result = CoinActions.apply(player, store, request.action(), coin.get(), request.amount(), request.all());
        if (result == Result.DONE) {
            note(player, stall, stall.cashValue() - before);
        }
        return result;
    }

    /** Note au registre de l'étal ce qui a changé la valeur de la caisse ({@code delta} en cuivres, signé). */
    private static void note(ServerPlayer player, MarketStallBlockEntity stall, long delta) {
        if (delta == 0L) {
            return;
        }
        UUID owner = stall.owner();
        boolean ownStall = player.getUUID().equals(owner);
        String name = player.getGameProfile().getName();
        long now = System.currentTimeMillis();
        if (ownStall) {
            stall.ledger().add(new Line(now, player.getUUID(), name, delta > 0 ? Kind.CASH_IN : Kind.CASH_OUT, "", 0,
                    Math.abs(delta)));
            return;
        }
        stall.ledger().add(new Line(now, player.getUUID(), name, Kind.STAFF, "", 0, delta));
        LOGGER.info("Economie : {} a agi sur la caisse de l'etal de {} en {} ({} cuivres)", name, owner, stall.getBlockPos(),
                delta);
        if (delta < 0L) {
            EconomyLedger.get(player.server).transferred(MoneyTransfer.STAFF_WITHDRAW, -delta, player.getUUID(),
                    "retrait de l'equipe dans la caisse de l'etal de " + (stall.ownerName().isEmpty() ? owner : stall.ownerName())
                            + " en " + stall.getBlockPos().toShortString());
        }
        stall.setChanged();
    }
}
