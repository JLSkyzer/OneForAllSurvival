package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.Locale;
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
 * Client vers serveur : une opération sur le compte du coffre-fort en {@code pos}. Le serveur revalide tout, comme
 * pour les autres paquets du mod : joueur toujours en ligne, action et coupure connues, montant parmi ceux de
 * l'écran, chunk chargé, distance, bloc toujours là, droit du joueur sur ce compte, écran de ce coffre-fort ouvert.
 *
 * <p>Chaque opération vérifie d'abord, puis valide d'un seul bloc sur le fil du serveur : deux clics rapides font
 * deux paquets traités l'un après l'autre, le second voit le compte déjà changé ; un joueur qui part entre
 * l'envoi et le traitement ne déclenche rien ({@link Result#GONE}). Les dépôts et retraits ne sont pas écrits au
 * registre : ils ne créent ni ne détruisent de pièces.
 */
public record SafeActionPacket(BlockPos pos, int action, int coin, int amount) {

    public static final int DEPOSIT_ALL = 0;
    public static final int WITHDRAW = 1;
    public static final int GROUP = 2;

    /** Les montants du retrait, ceux des boutons de l'écran : tout autre est refusé. */
    public static final int[] WITHDRAW_AMOUNTS = {1, 10, 64};

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ce qu'a donné le traitement d'un paquet. Seuls les trois derniers refus sont dits au joueur. */
    public enum Result { DONE, GONE, BAD_REQUEST, TOO_FAR, NO_SAFE, NOT_ALLOWED, NOT_OPEN, INSUFFICIENT, FULL, NOTHING }

    public static SafeActionPacket deposit(BlockPos pos) {
        return new SafeActionPacket(pos, DEPOSIT_ALL, 0, 0);
    }

    public static SafeActionPacket withdraw(BlockPos pos, Coin coin, int amount) {
        return new SafeActionPacket(pos, WITHDRAW, coin.ordinal(), amount);
    }

    public static SafeActionPacket group(BlockPos pos) {
        return new SafeActionPacket(pos, GROUP, 0, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
        buf.writeVarInt(coin);
        buf.writeVarInt(amount);
    }

    public static SafeActionPacket decode(FriendlyByteBuf buf) {
        return new SafeActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(SafeActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        Result result = apply(sender, packet);
        if (result == Result.INSUFFICIENT || result == Result.FULL || result == Result.NOTHING) {
            sender.displayClientMessage(
                    Component.translatable(SafeBlock.MESSAGE + result.name().toLowerCase(Locale.ROOT)), true);
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
        Coin coin = null;
        if (packet.action == WITHDRAW) {
            if (packet.coin < 0 || packet.coin >= Coin.values().length || !isWithdrawAmount(packet.amount)) {
                return Result.BAD_REQUEST;
            }
            coin = Coin.values()[packet.coin];
        } else if (packet.action != DEPOSIT_ALL && packet.action != GROUP) {
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
        SafeAccounts accounts = SafeAccounts.get(server);
        Result result = switch (packet.action) {
            case DEPOSIT_ALL -> depositAll(player, accounts, owner);
            case WITHDRAW -> withdraw(player, accounts, owner, coin, packet.amount);
            default -> accounts.group(owner) ? Result.DONE : Result.NOTHING;
        };
        if (result == Result.DONE && !owner.equals(player.getUUID())) {
            LOGGER.info("Economie : {} a agi sur le coffre-fort de {} (action {}, coupure {}, montant {})",
                    player.getGameProfile().getName(), owner, packet.action, packet.coin, packet.amount);
        }
        return result;
    }

    private static boolean isWithdrawAmount(int amount) {
        for (int allowed : WITHDRAW_AMOUNTS) {
            if (allowed == amount) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dépose toutes les pièces portées, dans la limite de la place du compte. Ce qui est déposé est ce qui a
     * réellement quitté le joueur (relevé avant et après), jamais ce qu'on espérait retirer : le compte ne gagne
     * rien que le joueur n'ait perdu.
     */
    private static Result depositAll(ServerPlayer player, SafeAccounts accounts, UUID owner) {
        EnumMap<Coin, Long> before = PlayerCoins.held(player);
        EnumMap<Coin, Long> wanted = new EnumMap<>(Coin.class);
        before.forEach((coin, count) -> {
            long fits = Math.min(count, accounts.room(owner, coin));
            if (fits > 0L) {
                wanted.put(coin, fits);
            }
        });
        if (wanted.isEmpty()) {
            return Result.NOTHING;
        }
        PlayerCoins.remove(player, wanted);
        EnumMap<Coin, Long> after = PlayerCoins.held(player);
        boolean any = false;
        for (Coin coin : wanted.keySet()) {
            long gone = Math.min(wanted.get(coin), before.get(coin) - after.getOrDefault(coin, 0L));
            if (gone > 0L) {
                accounts.deposit(owner, coin, gone);
                any = true;
            }
        }
        return any ? Result.DONE : Result.NOTHING;
    }

    /**
     * Retire {@code amount} pièces, toutes ou aucune si le compte n'en a pas assez. Le compte est débité d'abord ;
     * ce qui ne rentre pas dans l'inventaire n'est pas retiré (remis au compte). Rien n'est lâché au sol.
     */
    private static Result withdraw(ServerPlayer player, SafeAccounts accounts, UUID owner, Coin coin, int amount) {
        if (!accounts.withdraw(owner, coin, amount)) {
            return Result.INSUFFICIENT;
        }
        long entered = PlayerCoins.receive(player, coin, amount);
        if (entered < amount) {
            accounts.deposit(owner, coin, amount - entered);
        }
        return entered > 0L ? Result.DONE : Result.FULL;
    }
}
