package dev.oneforallsurvival.economy;

import java.util.UUID;

/** Le compte d'un coffre-fort, vu comme un {@link CoinStore}. */
public record SafeStore(SafeAccounts accounts, UUID owner) implements CoinStore {

    @Override
    public long count(Coin coin) {
        return accounts.count(owner, coin);
    }

    @Override
    public long room(Coin coin) {
        return accounts.room(owner, coin);
    }

    @Override
    public void add(Coin coin, long count) {
        accounts.deposit(owner, coin, count);
    }

    @Override
    public boolean remove(Coin coin, long count) {
        return accounts.withdraw(owner, coin, count);
    }

    @Override
    public boolean groupAll() {
        return accounts.group(owner);
    }

    @Override
    public boolean usesPurses() {
        return true;
    }
}
