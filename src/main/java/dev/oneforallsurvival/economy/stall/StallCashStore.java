package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinStore;

/**
 * La caisse d'un étal vue comme un {@link CoinStore} : {@code CoinActions} dépose, retire et regroupe dessus comme
 * sur un coffre-fort. Elle prend aussi les pièces des bourses portées, comme lui. Même plafond par coupure.
 */
public record StallCashStore(MarketStallBlockEntity stall) implements CoinStore {

    @Override
    public long count(Coin coin) {
        return stall.cashCount(coin);
    }

    @Override
    public long room(Coin coin) {
        return stall.cashRoom(coin);
    }

    @Override
    public void add(Coin coin, long count) {
        stall.addCash(coin, count);
    }

    @Override
    public boolean remove(Coin coin, long count) {
        return stall.removeCash(coin, count);
    }

    /** Le regroupement commun ({@link CoinStore#groupByTens}). */
    @Override
    public boolean groupAll() {
        return groupByTens();
    }

    @Override
    public boolean usesPurses() {
        return true;
    }
}
