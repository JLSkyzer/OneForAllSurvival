package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.economy.CoinActions;
import dev.oneforallsurvival.economy.CoinActions.Result;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.SafeBlock;
import dev.oneforallsurvival.economy.TreasuryAccount;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : une opération ({@link CoinRequest}) sur le trésor en {@code pos}, avec le motif d'une dépense.
 * Le serveur revalide tout, comme pour le coffre-fort : joueur en ligne, demande conforme à un bouton de l'écran,
 * motif valable pour un retrait, chunk chargé, distance, bloc du trésor à l'emplacement noté, droit d'ouvrir, écran
 * de ce trésor ouvert. L'opération elle-même est {@link CoinActions#apply}.
 *
 * <p>Ce qui est noté se lit dans la variation de la valeur du trésor, avant et après (comme la caisse de l'étal) :
 * une baisse est une dépense publique (ligne « Dépense publique » avec le motif, transfert
 * {@link MoneyTransfer#TREASURY_SPEND}), une hausse un dépôt (ligne « Dépôt », transfert
 * {@link MoneyTransfer#TREASURY_DEPOSIT}, arrivée de fonds signalée à la paie). Le change ne change pas la valeur : rien
 * n'est noté.
 */
public record TreasuryActionPacket(BlockPos pos, CoinRequest request, String reason) {

    /** Borne du motif sur le réseau : au-delà, le paquet ne se lit pas (la config, elle, plafonne à 200). */
    public static final int MAX_WIRE_REASON = 256;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        request.encode(buf);
        buf.writeUtf(reason, MAX_WIRE_REASON);
    }

    public static TreasuryActionPacket decode(FriendlyByteBuf buf) {
        return new TreasuryActionPacket(buf.readBlockPos(), CoinRequest.decode(buf), buf.readUtf(MAX_WIRE_REASON));
    }

    /**
     * Le motif d'une dépense, prêt à noter, ou vide s'il n'est pas valable : vide ou blanc, porteur d'un saut de
     * ligne (ou de tout caractère de contrôle, ou du signe de mise en forme), ou plus long que
     * {@link EconomyConfig#treasuryReasonMax()} une fois les blancs du bord ôtés.
     */
    public static Optional<String> validReason(@Nullable String reason) {
        if (reason == null) {
            return Optional.empty();
        }
        String text = reason.strip();
        if (text.isEmpty() || text.length() > EconomyConfig.treasuryReasonMax()
                || reason.chars().anyMatch(c -> Character.isISOControl(c) || c == '§')) {
            return Optional.empty();
        }
        return Optional.of(text);
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(TreasuryActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        Result result = apply(sender, packet);
        if (CoinActions.isTold(result)) {
            // Les refus communs se disent comme au coffre-fort ; « trésor plein » et « pas assez » ont leur texte.
            String key = result == Result.INSUFFICIENT || result == Result.STORE_FULL
                    ? TreasuryBlock.MESSAGE + result.name().toLowerCase(Locale.ROOT)
                    : SafeBlock.MESSAGE + result.name().toLowerCase(Locale.ROOT);
            sender.displayClientMessage(Component.translatable(key), true);
        }
        if (sender.containerMenu instanceof CoinAccountMenu menu) {
            menu.broadcastChanges();
        }
    }

    /** Le contrôle, l'opération et les notes, sans le réseau. */
    public static Result apply(ServerPlayer player, TreasuryActionPacket packet) {
        MinecraftServer server = player.server;
        if (player.connection == null || !player.connection.isAcceptingMessages()
                || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return Result.GONE;
        }
        CoinRequest request = packet.request;
        Optional<Coin> coin = request == null ? Optional.empty() : request.coinIfValid();
        if (coin.isEmpty()) {
            return Result.BAD_REQUEST;
        }
        boolean withdraw = request.action() == CoinRequest.WITHDRAW;
        Optional<String> reason = validReason(packet.reason);
        if (withdraw && reason.isEmpty()) {
            return Result.BAD_REQUEST;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = packet.pos;
        if (pos == null || !level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > TreasuryBlock.MAX_DISTANCE_SQR) {
            return Result.TOO_FAR;
        }
        if (!level.getBlockState(pos).is(EconomyRegistry.TREASURY.get()) || !TreasuryBlock.isNoted(level, pos)) {
            return Result.NO_SAFE;
        }
        if (!TownBlockRules.mayOpenTreasury(player)) {
            return Result.NOT_ALLOWED;
        }
        if (!(player.containerMenu instanceof TreasuryMenu menu) || !menu.pos().equals(pos) || !menu.stillValid(player)) {
            return Result.NOT_OPEN;
        }
        TreasuryAccount account = TreasuryAccount.get(server);
        long before = account.total();
        Result result = CoinActions.apply(player, new TreasuryStore(account), request.action(), coin.get(),
                request.amount(), request.all());
        if (result == Result.DONE) {
            note(player, account, account.total() - before, reason.orElse(""));
        }
        return result;
    }

    /** Note ce qui a changé la valeur du trésor ({@code delta} en cuivres, signé) ; rien pour le change. */
    private static void note(ServerPlayer player, TreasuryAccount account, long delta, String reason) {
        if (delta == 0L) {
            return;
        }
        String name = player.getGameProfile().getName();
        long now = System.currentTimeMillis();
        EconomyLedger global = EconomyLedger.get(player.server);
        if (delta < 0L) {
            account.note(new TreasuryLedger.Line(now, player.getUUID(), name, TreasuryLedger.Kind.SPEND, -delta, reason));
            global.transferred(MoneyTransfer.TREASURY_SPEND, -delta, player.getUUID(), name + " : " + reason);
        } else {
            account.note(new TreasuryLedger.Line(now, player.getUUID(), name, TreasuryLedger.Kind.DEPOSIT, delta, ""));
            global.transferred(MoneyTransfer.TREASURY_DEPOSIT, delta, player.getUUID(), name + " : depot au tresor");
            account.markFundsArrived();
        }
    }
}
