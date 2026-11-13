package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinStore;
import java.util.EnumMap;

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

    @Override
    public boolean usesPurses() {
        return true;
    }
}
