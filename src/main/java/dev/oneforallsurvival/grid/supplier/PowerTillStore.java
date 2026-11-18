package dev.oneforallsurvival.grid.supplier;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinStore;

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

    /** Le regroupement commun ({@link CoinStore#groupByTens}). */
    @Override
    public boolean groupAll() {
        return groupByTens();
    }

    /** Un retrait qui ne tient plus en vrac entre dans la première bourse portée, comme au coffre-fort. */
    @Override
    public boolean usesPurses() {
        return true;
    }
}
