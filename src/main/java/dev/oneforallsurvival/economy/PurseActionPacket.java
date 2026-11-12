package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : une opération sur la bourse qui est dans la case {@code slot} de l'inventaire. Le serveur
 * revalide tout : joueur toujours en ligne, action et coupure connues, montant parmi ceux de l'écran, écran de cette
 * bourse ouvert et bourse toujours dans sa case.
 *
 * <p>Chaque opération vérifie d'abord, puis valide d'un seul bloc sur le fil du serveur : deux clics rapides font deux
 * paquets traités l'un après l'autre, le second voit la bourse déjà changée ; un joueur qui part entre l'envoi et le
 * traitement ne déclenche rien ({@link Result#GONE}). Les pièces ne font que passer de la bourse à l'inventaire ou
 * l'inverse, et le décompte se lit sur ce qui a réellement bougé : rien n'est créé ni détruit, rien n'est écrit au
 * registre.
 */
public record PurseActionPacket(int slot, int action, int coin, int amount) {

    public static final int DEPOSIT_ALL = 0;
    public static final int WITHDRAW = 1;
    public static final int GROUP = 2;

    /** Ce qu'a donné le traitement d'un paquet. Seuls les trois derniers refus sont dits au joueur. */
    public enum Result { DONE, GONE, BAD_REQUEST, NOT_OPEN, INSUFFICIENT, FULL, NOTHING }

    public static final String MESSAGE = "message.oneforallsurvival.purse.";

    public static PurseActionPacket deposit(int slot) {
        return new PurseActionPacket(slot, DEPOSIT_ALL, 0, 0);
    }

    public static PurseActionPacket withdraw(int slot, Coin coin, int amount) {
        return new PurseActionPacket(slot, WITHDRAW, coin.ordinal(), amount);
    }

    public static PurseActionPacket group(int slot) {
        return new PurseActionPacket(slot, GROUP, 0, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        buf.writeVarInt(action);
        buf.writeVarInt(coin);
        buf.writeVarInt(amount);
    }

    public static PurseActionPacket decode(FriendlyByteBuf buf) {
        return new PurseActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(PurseActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        Result result = apply(sender, packet);
        if (result == Result.INSUFFICIENT || result == Result.FULL || result == Result.NOTHING) {
            sender.displayClientMessage(Component.translatable(MESSAGE + result.name().toLowerCase(Locale.ROOT)), true);
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
        Coin coin = null;
        if (packet.action == WITHDRAW) {
            if (packet.coin < 0 || packet.coin >= Coin.values().length || !isWithdrawAmount(packet.amount)) {
                return Result.BAD_REQUEST;
            }
            coin = Coin.values()[packet.coin];
        } else if (packet.action != DEPOSIT_ALL && packet.action != GROUP) {
            return Result.BAD_REQUEST;
        }
        if (!(player.containerMenu instanceof PurseMenu menu) || menu.slot() != packet.slot || !menu.stillValid(player)) {
            return Result.NOT_OPEN;
        }
        ItemStack purse = menu.purse();
        return switch (packet.action) {
            case DEPOSIT_ALL -> depositAll(player, purse);
            case WITHDRAW -> withdraw(player, purse, coin, packet.amount);
            default -> Purse.group(purse) ? Result.DONE : Result.NOTHING;
        };
    }

    private static boolean isWithdrawAmount(int amount) {
        for (int allowed : SafeActionPacket.WITHDRAW_AMOUNTS) {
            if (allowed == amount) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dépose les pièces de l'inventaire dans la bourse, dans la limite de sa place. Ce qui est déposé est ce qui a
     * réellement quitté l'inventaire (relevé avant et après) : la bourse ne gagne rien que le joueur n'ait perdu. Les
     * pièces d'une autre bourse ne bougent pas.
     */
    private static Result depositAll(ServerPlayer player, ItemStack purse) {
        EnumMap<Coin, Long> before = PlayerCoins.heldLoose(player);
        EnumMap<Coin, Long> wanted = new EnumMap<>(Coin.class);
        before.forEach((coin, count) -> {
            long fits = Math.min(count, Purse.room(purse, coin));
            if (fits > 0L) {
                wanted.put(coin, fits);
            }
        });
        if (wanted.isEmpty()) {
            return Result.NOTHING;
        }
        PlayerCoins.removeLoose(player, wanted);
        EnumMap<Coin, Long> after = PlayerCoins.heldLoose(player);
        boolean any = false;
        for (Coin coin : wanted.keySet()) {
            long gone = Math.min(wanted.get(coin), before.get(coin) - after.getOrDefault(coin, 0L));
            if (gone > 0L) {
                Purse.add(purse, coin, gone);
                any = true;
            }
        }
        return any ? Result.DONE : Result.NOTHING;
    }

    /**
     * Retire {@code amount} pièces, toutes ou aucune si la bourse n'en a pas assez. La bourse est débitée d'abord ;
     * ce qui ne rentre pas dans l'inventaire (jamais dans une bourse, ce serait un aller-retour) est remis dans la
     * bourse. Rien n'est lâché au sol.
     */
    private static Result withdraw(ServerPlayer player, ItemStack purse, Coin coin, int amount) {
        if (!Purse.remove(purse, coin, amount)) {
            return Result.INSUFFICIENT;
        }
        long entered = PlayerCoins.receiveLoose(player, coin, amount);
        if (entered < amount) {
            Purse.add(purse, coin, amount - entered);
        }
        return entered > 0L ? Result.DONE : Result.FULL;
    }
}
