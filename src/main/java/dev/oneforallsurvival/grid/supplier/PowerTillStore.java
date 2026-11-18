package dev.oneforallsurvival.grid.supplier;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinStore;
import java.util.EnumMap;

/**
 * La caisse d'un fournisseur vue comme un {@link CoinStore} : l'écran commun ({@code CoinActions}) y retire et y fait
 * le change comme au coffre-fort. Les dépôts du joueur n'y passent pas ({@link PowerTillMenu} les refuse) : la caisse
 * ne reçoit que des pièces prélevées. Même plafond par coupure.
 */
public record PowerTillStore(PowerSuppliers registry, String supplierId) implements CoinStore {

    @Override
    public long count(Coin coin) {
        return registry.till(supplierId).getOrDefault(coin, 0L);
    }

    @Override
    public long room(Coin coin) {
        return registry.tillRoom(supplierId, coin);
    }

    @Override
    public void add(Coin coin, long count) {
        registry.deposit(supplierId, coin, count);
    }

    @Override
    public boolean remove(Coin coin, long count) {
        return registry.withdraw(supplierId, coin, count);
    }

    /**
     * Regroupe dix pièces d'une coupure en une du dessus, à valeur égale, pour les seules paires au rapport de 10
     * selon la configuration vivante. Rien ne bouge si un résultat dépasserait le plafond.
     */
    @Override
    public boolean groupAll() {
        EnumMap<Coin, Long> now = contents();
        EnumMap<Coin, Long> grouped = CoinMath.group(now, Coin::value);
        if (grouped.equals(now)) {
            return false;
        }
        for (long count : grouped.values()) {
            if (count > MAX_PER_COIN) {
                return false;
            }
        }
        for (Coin coin : Coin.values()) {
            long diff = grouped.getOrDefault(coin, 0L) - now.getOrDefault(coin, 0L);
            if (diff < 0L && !remove(coin, -diff)) {
                throw new IllegalStateException("regroupement : " + coin + " manquant");
            }
        }
        for (Coin coin : Coin.values()) {
            long diff = grouped.getOrDefault(coin, 0L) - now.getOrDefault(coin, 0L);
            if (diff > 0L) {
                add(coin, diff);
            }
        }
        return true;
    }

    /** Un retrait qui ne tient plus en vrac entre dans la première bourse portée, comme au coffre-fort. */
    @Override
    public boolean usesPurses() {
        return true;
    }
}
