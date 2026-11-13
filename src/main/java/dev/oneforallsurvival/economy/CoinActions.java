package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * La seule implémentation des opérations des écrans du coffre-fort et de la bourse, sur un {@link CoinStore}.
 * Les paquets ({@link SafeActionPacket}, {@link PurseActionPacket}) font leurs propres contrôles de cible, puis
 * appellent {@link #apply}.
 *
 * <p>Chaque opération vérifie d'abord, puis engage, d'un seul bloc sur le fil du serveur. Elle ne crée ni ne perd
 * jamais rien : ce qui entre dans le contenant est ce qui a réellement quitté le joueur (relevé avant et après), et
 * ce qui ne trouve pas de place chez le joueur reste dans le contenant. Rien ne tombe au sol, rien n'est écrit au
 * registre (aucune pièce n'est créée ni détruite).
 */
public final class CoinActions {

    /** Ce qu'a donné le traitement d'un paquet. Seuls les refus de {@link #isTold} sont dits au joueur. */
    public enum Result {
        DONE, GONE, BAD_REQUEST, TOO_FAR, NO_SAFE, NOT_ALLOWED, NOT_OPEN,
        /** Le contenant n'a pas assez de pièces (retrait, change). */
        INSUFFICIENT,
        /** L'inventaire du joueur n'a plus de place. */
        FULL,
        /** Le contenant n'a plus de place pour cette coupure (plafond). */
        STORE_FULL,
        /** La paire n'est pas au rapport de 10 dans la config vivante : le change ne serait pas à valeur égale. */
        NO_RATE,
        NOTHING
    }

    private CoinActions() {}

    /** Les refus qu'un joueur honnête peut rencontrer, et qu'on lui dit. */
    public static boolean isTold(Result result) {
        return result == Result.INSUFFICIENT || result == Result.FULL || result == Result.STORE_FULL
                || result == Result.NO_RATE || result == Result.NOTHING;
    }

    /**
     * Applique une demande déjà validée ({@link CoinRequest#coinIfValid}) sur {@code store}.
     *
     * @param coin la coupure visée (ignorée par les actions sans coupure)
     * @param amount le nombre de pièces des actions à montant
     * @param all pour le change d'une paire : autant que possible
     */
    public static Result apply(ServerPlayer player, CoinStore store, int action, Coin coin, int amount, boolean all) {
        return switch (action) {
            case CoinRequest.DEPOSIT_ALL -> depositAll(player, store);
            case CoinRequest.WITHDRAW -> withdraw(player, store, coin, amount);
            case CoinRequest.GROUP_ALL -> store.groupAll() ? Result.DONE : Result.NOTHING;
            case CoinRequest.DEPOSIT -> deposit(player, store, coin, amount);
            case CoinRequest.BREAK -> breakCoin(store, coin, all);
            case CoinRequest.GROUP_PAIR -> groupPair(store, coin, all);
            default -> Result.BAD_REQUEST;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Les pièces que le joueur porte, selon le contenant : en vrac seulement, ou bourses comprises
    // ---------------------------------------------------------------------------------------------

    private static EnumMap<Coin, Long> carried(ServerPlayer player, CoinStore store) {
        return store.usesPurses() ? PlayerCoins.held(player) : PlayerCoins.heldLoose(player);
    }

    private static void takeCarried(ServerPlayer player, CoinStore store, Map<Coin, Long> coins) {
        if (store.usesPurses()) {
            PlayerCoins.remove(player, coins);
        } else {
            PlayerCoins.removeLoose(player, coins);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Opérations
    // ---------------------------------------------------------------------------------------------

    /**
     * Dépose toutes les pièces portées, dans la limite de la place. Ce qui est déposé est ce qui a réellement
     * quitté le joueur (relevé avant et après), jamais ce qu'on espérait retirer.
     */
    private static Result depositAll(ServerPlayer player, CoinStore store) {
        EnumMap<Coin, Long> before = carried(player, store);
        EnumMap<Coin, Long> wanted = new EnumMap<>(Coin.class);
        before.forEach((coin, count) -> {
            long fits = Math.min(count, store.room(coin));
            if (fits > 0L) {
                wanted.put(coin, fits);
            }
        });
        if (wanted.isEmpty()) {
            return Result.NOTHING;
        }
        takeCarried(player, store, wanted);
        EnumMap<Coin, Long> after = carried(player, store);
        boolean any = false;
        for (Coin coin : wanted.keySet()) {
            long gone = Math.min(wanted.get(coin), before.get(coin) - after.getOrDefault(coin, 0L));
            if (gone > 0L) {
                store.add(coin, gone);
                any = true;
            }
        }
        return any ? Result.DONE : Result.NOTHING;
    }

    /**
     * Dépose jusqu'à {@code amount} pièces de cette coupure, selon ce que le joueur porte et la place du contenant :
     * d'abord les pièces en vrac de l'inventaire, puis (coffre-fort seulement) celles des bourses portées. Ce qui est
     * déposé est ce qui a réellement quitté le joueur.
     */
    private static Result deposit(ServerPlayer player, CoinStore store, Coin coin, int amount) {
        long have = carried(player, store).getOrDefault(coin, 0L);
        if (have <= 0L) {
            return Result.NOTHING;
        }
        long room = store.room(coin);
        if (room <= 0L) {
            return Result.STORE_FULL;
        }
        long want = Math.min(amount, Math.min(have, room));
        long looseBefore = PlayerCoins.heldLoose(player).getOrDefault(coin, 0L);
        long fromLoose = Math.min(want, looseBefore);
        long moved = 0L;
        if (fromLoose > 0L) {
            PlayerCoins.removeLoose(player, Map.of(coin, fromLoose));
            long looseAfter = PlayerCoins.heldLoose(player).getOrDefault(coin, 0L);
            moved = Math.max(0L, Math.min(fromLoose, looseBefore - looseAfter));
        }
        if (store.usesPurses()) {
            for (ItemStack purse : Purse.purses(player)) {
                long take = Math.min(want - moved, Purse.count(purse, coin));
                if (take > 0L && Purse.remove(purse, coin, take)) {
                    moved += take;
                }
                if (moved >= want) {
                    break;
                }
            }
        }
        if (moved <= 0L) {
            return Result.NOTHING;
        }
        store.add(coin, moved);
        return Result.DONE;
    }

    /**
     * Retire {@code amount} pièces, toutes ou aucune si le contenant n'en a pas assez. Le contenant est débité
     * d'abord ; les pièces vont en vrac dans l'inventaire, puis (coffre-fort seulement) dans les bourses portées, la
     * première d'abord. Ce qui ne trouve de place nulle part est remis au contenant : rien ne tombe au sol.
     */
    private static Result withdraw(ServerPlayer player, CoinStore store, Coin coin, int amount) {
        if (!store.remove(coin, amount)) {
            return Result.INSUFFICIENT;
        }
        long entered = PlayerCoins.receiveLoose(player, coin, amount);
        if (store.usesPurses()) {
            for (ItemStack purse : Purse.purses(player)) {
                long fits = Math.min(amount - entered, Purse.room(purse, coin));
                if (fits > 0L) {
                    Purse.add(purse, coin, fits);
                    entered += fits;
                }
                if (entered >= amount) {
                    break;
                }
            }
        }
        if (entered < amount) {
            store.add(coin, amount - entered);
        }
        return entered > 0L ? Result.DONE : Result.FULL;
    }

    /** Casse une pièce {@code upper} (ou autant que possible) en 10 de la coupure du dessous, dans le contenant. */
    private static Result breakCoin(CoinStore store, Coin upper, boolean all) {
        if (!CoinMath.atPar(upper, Coin::value)) {
            return Result.NO_RATE;
        }
        if (store.breakOne(upper, all) > 0L) {
            return Result.DONE;
        }
        return store.count(upper) <= 0L ? Result.INSUFFICIENT : Result.STORE_FULL;
    }

    /** Regroupe 10 pièces du dessous en une pièce {@code upper} (ou autant que possible), dans le contenant. */
    private static Result groupPair(CoinStore store, Coin upper, boolean all) {
        if (!CoinMath.atPar(upper, Coin::value)) {
            return Result.NO_RATE;
        }
        if (store.groupPair(upper, all) > 0L) {
            return Result.DONE;
        }
        Coin lower = Coin.values()[upper.ordinal() - 1];
        return store.count(lower) < 10L ? Result.INSUFFICIENT : Result.STORE_FULL;
    }
}
