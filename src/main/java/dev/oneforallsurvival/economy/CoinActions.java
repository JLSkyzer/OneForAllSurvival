package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

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
    public enum Result { DONE, GONE, BAD_REQUEST, TOO_FAR, NO_SAFE, NOT_ALLOWED, NOT_OPEN, INSUFFICIENT, FULL, NOTHING }

    private CoinActions() {}

    /** Les refus qu'un joueur honnête peut rencontrer, et qu'on lui dit. */
    public static boolean isTold(Result result) {
        return result == Result.INSUFFICIENT || result == Result.FULL || result == Result.NOTHING;
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
     * Retire {@code amount} pièces, toutes ou aucune si le contenant n'en a pas assez. Le contenant est débité
     * d'abord ; ce qui ne trouve pas de place chez le joueur y est remis.
     */
    private static Result withdraw(ServerPlayer player, CoinStore store, Coin coin, int amount) {
        if (!store.remove(coin, amount)) {
            return Result.INSUFFICIENT;
        }
        long entered = store.usesPurses() ? PlayerCoins.receive(player, coin, amount)
                : PlayerCoins.receiveLoose(player, coin, amount);
        if (entered < amount) {
            store.add(coin, amount - entered);
        }
        return entered > 0L ? Result.DONE : Result.FULL;
    }
}
