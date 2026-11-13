package dev.oneforallsurvival.economy;

import net.minecraft.world.item.ItemStack;

/** Une bourse (la pile elle-même, dont le NBT fait foi), vue comme un {@link CoinStore}. */
public record PurseStore(ItemStack stack) implements CoinStore {

    @Override
    public long count(Coin coin) {
        return Purse.count(stack, coin);
    }

    @Override
    public long room(Coin coin) {
        return Purse.room(stack, coin);
    }

    @Override
    public void add(Coin coin, long count) {
        Purse.add(stack, coin, count);
    }

    @Override
    public boolean remove(Coin coin, long count) {
        return Purse.remove(stack, coin, count);
    }

    @Override
    public boolean groupAll() {
        return Purse.group(stack);
    }

    @Override
    public boolean usesPurses() {
        return false;
    }
}
