package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinStore;
import dev.oneforallsurvival.economy.TreasuryAccount;

/**
 * Le compte du trésor, vu comme un {@link CoinStore} : l'écran du trésor dépose, retire et change par
 * {@code CoinActions}, comme au coffre-fort (bourses comprises). Rien n'est noté ici : le paquet note la variation de
 * valeur ({@link TreasuryActionPacket}).
 */
public record TreasuryStore(TreasuryAccount account) implements CoinStore {

    @Override
    public long count(Coin coin) {
        return account.count(coin);
    }

    @Override
    public long room(Coin coin) {
        return account.room(coin);
    }

    @Override
    public void add(Coin coin, long count) {
        account.add(coin, count);
    }

    @Override
    public boolean remove(Coin coin, long count) {
        return account.remove(coin, count);
    }

    @Override
    public boolean groupAll() {
        return account.group();
    }

    @Override
    public boolean usesPurses() {
        return true;
    }
}
