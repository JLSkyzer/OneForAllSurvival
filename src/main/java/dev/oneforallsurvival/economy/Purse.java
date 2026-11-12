package dev.oneforallsurvival.economy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Le contenu d'une bourse et le paiement qui la vide. La bourse est un objet qui garde un nombre de pièces par
 * coupure dans son NBT ({@value #TAG}, un {@code long} par coupure) ; ce nombre est la seule vérité, l'objet ne
 * range aucune pile. Chaque écriture passe par ici : le plafond par coupure est celui du coffre-fort.
 *
 * <p>Ce qu'une bourse contient est de l'argent au même titre qu'une pièce ({@link MoneyStorageRules#isMoney}) : une
 * bourse qui contient au moins une pièce ne se range pas là où une pièce est refusée.
 */
public final class Purse {

    /** Plafond de pièces par coupure et par bourse (celui du coffre-fort). */
    public static final long MAX_PER_COIN = SafeAccounts.MAX_PER_COIN;

    /** Clé du NBT de la bourse. */
    public static final String TAG = "Coins";

    private Purse() {}

    public static boolean isPurse(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PurseItem;
    }

    /** Le nombre de pièces de chaque coupure ; seules les coupures non nulles y figurent. */
    public static EnumMap<Coin, Long> contents(ItemStack stack) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        CompoundTag tag = stack.getTag();
        if (!isPurse(stack) || tag == null || !tag.contains(TAG, 10)) {
            return out;
        }
        CompoundTag coins = tag.getCompound(TAG);
        for (Coin coin : Coin.values()) {
            long count = Math.max(0L, Math.min(MAX_PER_COIN, coins.getLong(coin.key())));
            if (count > 0L) {
                out.put(coin, count);
            }
        }
        return out;
    }

    public static long count(ItemStack stack, Coin coin) {
        return contents(stack).getOrDefault(coin, 0L);
    }

    /** Valeur de la bourse, en cuivres (jamais d'exception : un débordement donne {@link Long#MAX_VALUE}). */
    public static long total(ItemStack stack) {
        return SafeAccounts.saturatedValue(contents(stack));
    }

    /** Combien de pièces de cette coupure la bourse peut encore recevoir. */
    public static long room(ItemStack stack, Coin coin) {
        return Math.max(0L, MAX_PER_COIN - count(stack, coin));
    }

    /**
     * Ajoute des pièces.
     *
     * @throws IllegalArgumentException si le nombre est négatif ou dépasse la place ({@link #room}) : l'appelant l'a vérifiée
     */
    public static void add(ItemStack stack, Coin coin, long count) {
        if (count < 0L || count > room(stack, coin)) {
            throw new IllegalArgumentException("ajout de " + count + " " + coin + " : place " + room(stack, coin));
        }
        if (count > 0L) {
            EnumMap<Coin, Long> now = contents(stack);
            now.merge(coin, count, Long::sum);
            write(stack, now);
        }
    }

    /** Retire des pièces, toutes ou aucune. @return faux (rien ne bouge) si la bourse n'en a pas assez */
    public static boolean remove(ItemStack stack, Coin coin, long count) {
        if (count <= 0L || count(stack, coin) < count) {
            return false;
        }
        EnumMap<Coin, Long> now = contents(stack);
        now.merge(coin, -count, Long::sum);
        write(stack, now);
        return true;
    }

    /**
     * Regroupe la bourse selon les valeurs vivantes : 10 pièces d'une coupure deviennent 1 pièce de celle du dessus,
     * à valeur égale, pour les seules paires dont le rapport est de 10 ({@link CoinMath#group(EnumMap,
     * java.util.function.ToLongFunction)}). Rien ne bouge si le résultat dépasserait le plafond.
     *
     * @return vrai si la bourse a changé
     */
    public static boolean group(ItemStack stack) {
        EnumMap<Coin, Long> now = contents(stack);
        EnumMap<Coin, Long> grouped = CoinMath.group(now, Coin::value);
        if (grouped.equals(now)) {
            return false;
        }
        for (long count : grouped.values()) {
            if (count > MAX_PER_COIN) {
                return false;
            }
        }
        write(stack, grouped);
        return true;
    }

    private static void write(ItemStack stack, Map<Coin, Long> coins) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            if (entry.getValue() > 0L) {
                tag.putLong(entry.getKey().key(), entry.getValue());
            }
        }
        if (tag.isEmpty()) {
            CompoundTag root = stack.getTag();
            if (root != null) {
                root.remove(TAG);
                if (root.isEmpty()) {
                    stack.setTag(null);
                }
            }
        } else {
            stack.getOrCreateTag().put(TAG, tag);
        }
    }

    /** Les bourses de l'inventaire (armure et main gauche comprises), dans l'ordre des cases : la barre d'abord. */
    public static List<ItemStack> purses(Player player) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isPurse(stack)) {
                out.add(stack);
            }
        }
        return out;
    }

    /** La première bourse de l'inventaire : celle où vont les pièces ramassées et la monnaie rendue. */
    public static Optional<ItemStack> firstPurse(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isPurse(stack)) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    /**
     * Paie {@code price} cuivres. Le paiement prend d'abord dans les bourses ; si elles ne suffisent pas, dans tout
     * ce que le joueur porte (bourses d'abord, pièces seules ensuite). La monnaie est calculée par
     * {@link CoinMath#makeChange} et rendue dans la première bourse, sinon dans l'inventaire, sinon au sol. Jamais
     * en partie : si le joueur ne porte pas assez, rien ne bouge.
     *
     * <p>Rien n'est écrit au registre : l'appelant dit où va la somme (lot E2).
     *
     * @return la somme payée, ou 0 si rien n'a été payé (prix nul ou négatif, ou pas assez)
     */
    public static long pay(ServerPlayer player, long price) {
        return price > 0L && PlayerCoins.take(player, price) ? price : 0L;
    }
}
