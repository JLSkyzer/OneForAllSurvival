package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Le compte du trésor, gardé par le serveur (données du monde principal) : là où va la taxe sur les ventes. Il
 * n'a ni bloc ni écran à ce lot ; le lot E3 le rattachera au bloc du trésor, et c'est lui qui ajoutera le retrait.
 * Ne se touche que depuis le fil du serveur. Les pièces y sont des pièces qui existent : ni créées ni détruites.
 */
public final class TreasuryAccount extends SavedData {

    public static final String KEY = "oneforallsurvival_treasury";

    private final long[] coins = new long[Coin.values().length];

    TreasuryAccount() {}

    public static TreasuryAccount get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TreasuryAccount::load, TreasuryAccount::new, KEY);
    }

    /**
     * Ajoute des pièces au compte.
     *
     * @throws IllegalArgumentException si un nombre est négatif (rien n'est alors ajouté)
     */
    public void deposit(Map<Coin, Long> added) {
        for (Map.Entry<Coin, Long> entry : added.entrySet()) {
            if (entry.getValue() < 0L) {
                throw new IllegalArgumentException("depot de " + entry.getValue() + " " + entry.getKey());
            }
        }
        boolean changed = false;
        for (Map.Entry<Coin, Long> entry : added.entrySet()) {
            if (entry.getValue() > 0L) {
                coins[entry.getKey().ordinal()] = Math.addExact(coins[entry.getKey().ordinal()], entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /** Le nombre de pièces de chaque coupure (les cinq clés sont présentes). */
    public EnumMap<Coin, Long> balance() {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            out.put(coin, coins[coin.ordinal()]);
        }
        return out;
    }

    /** La valeur du compte, en cuivres (jamais d'exception : un débordement donne {@link Long#MAX_VALUE}). */
    public long total() {
        return SafeAccounts.saturatedValue(balance());
    }

    /** Les pièces sont gardées par clé de coupure : l'ordre de l'énumération peut changer. */
    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag out = new CompoundTag();
        for (Coin coin : Coin.values()) {
            out.putLong(coin.key(), coins[coin.ordinal()]);
        }
        tag.put("coins", out);
        return tag;
    }

    public static TreasuryAccount load(CompoundTag tag) {
        TreasuryAccount account = new TreasuryAccount();
        CompoundTag saved = tag.getCompound("coins");
        for (Coin coin : Coin.values()) {
            account.coins[coin.ordinal()] = Math.max(0L, saved.getLong(coin.key()));
        }
        return account;
    }
}
