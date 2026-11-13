package dev.oneforallsurvival.economy;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToLongFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/** Calculs purs sur les montants, en cuivres. Aucun état, aucun monde. */
public final class CoinMath {

    /**
     * Ce qu'un paiement retire et rend : {@code taken} sort des pièces disponibles, {@code change} y entre.
     * Les maps ne contiennent que des quantités strictement positives.
     */
    public record Payment(EnumMap<Coin, Long> taken, EnumMap<Coin, Long> change) {}

    private CoinMath() {}

    /** Valeur d'une pile : 0 pour un objet qui n'est pas une pièce. */
    public static long valueOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }
        return Coin.fromItem(stack.getItem()).map(coin -> valueOf(coin, stack.getCount())).orElse(0L);
    }

    public static long valueOf(Coin coin, long count) {
        return Math.multiplyExact(coin.value(), count);
    }

    /** Valeur d'un ensemble de pièces. */
    public static long valueOf(Map<Coin, Long> coins) {
        long total = 0L;
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            total = Math.addExact(total, valueOf(entry.getKey(), entry.getValue()));
        }
        return total;
    }

    /** Décomposition gloutonne, de la plus grande coupure à la plus petite. Les coupures à zéro sont omises. */
    public static EnumMap<Coin, Long> decompose(long amount) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        long left = Math.max(0L, amount);
        Coin[] coins = Coin.values();
        for (int i = coins.length - 1; i >= 0 && left > 0; i--) {
            long count = left / coins[i].value();
            if (count > 0) {
                out.put(coins[i], count);
                left -= count * coins[i].value();
            }
        }
        return out;
    }

    /** Combien de pièces d'une coupure font une pièce de celle du dessus, au change. */
    private static final long GROUP_SIZE = 10L;

    /**
     * Regroupe chaque tranche de 10 pièces d'une coupure en 1 pièce de la coupure au-dessus, jusqu'au rubis,
     * à valeur égale. Le rapport est celui du change (10 pour 1), pas celui de la configuration : voir
     * {@link #group(EnumMap, ToLongFunction)} pour tenir compte des valeurs vivantes.
     */
    public static EnumMap<Coin, Long> group(EnumMap<Coin, Long> coins) {
        boolean[] pairs = new boolean[Coin.values().length - 1];
        Arrays.fill(pairs, true);
        return group(coins, pairs);
    }

    /**
     * Comme {@link #group(EnumMap)}, mais une paire (une coupure et celle du dessus) n'est regroupée que si la
     * pièce du dessus vaut exactement 10 fois l'autre selon {@code value} : sans cela le regroupement ne serait
     * pas à valeur égale. Pour une paire qui ne l'est pas, les pièces restent telles quelles.
     */
    public static EnumMap<Coin, Long> group(EnumMap<Coin, Long> coins, ToLongFunction<Coin> value) {
        Coin[] all = Coin.values();
        boolean[] pairs = new boolean[all.length - 1];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = value.applyAsLong(all[i + 1]) == value.applyAsLong(all[i]) * GROUP_SIZE;
        }
        return group(coins, pairs);
    }

    private static EnumMap<Coin, Long> group(EnumMap<Coin, Long> coins, boolean[] pairs) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        Coin[] all = Coin.values();
        long carry = 0L;
        for (int i = 0; i < all.length; i++) {
            long n = coins.getOrDefault(all[i], 0L) + carry;
            carry = 0L;
            if (i < pairs.length && pairs[i]) {
                carry = n / GROUP_SIZE;
                n = n % GROUP_SIZE;
            }
            if (n > 0) {
                out.put(all[i], n);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // Change d'une paire : casser une pièce, regrouper dix pièces (panneau « Change » des écrans)
    // ---------------------------------------------------------------------------------------------

    /**
     * La paire nommée par sa pièce du dessus ({@code upper}) et la coupure juste en dessous est-elle au rapport de
     * 10 selon {@code value} ? Sans cela, ni casser ni regrouper ne serait à valeur égale. Faux pour le cuivre, qui
     * n'a rien en dessous.
     */
    public static boolean atPar(Coin upper, ToLongFunction<Coin> value) {
        if (upper.ordinal() == 0) {
            return false;
        }
        Coin lower = Coin.values()[upper.ordinal() - 1];
        return value.applyAsLong(upper) == value.applyAsLong(lower) * GROUP_SIZE;
    }

    /**
     * Combien de pièces {@code upper} on peut casser, chacune en 10 de la coupure du dessous, sans que celle-ci
     * dépasse {@code max} : 0 si la paire n'est pas au rapport de 10.
     */
    public static long maxBreaks(Map<Coin, Long> coins, Coin upper, ToLongFunction<Coin> value, long max) {
        if (!atPar(upper, value)) {
            return 0L;
        }
        Coin lower = Coin.values()[upper.ordinal() - 1];
        long room = Math.max(0L, max - coins.getOrDefault(lower, 0L));
        return Math.max(0L, Math.min(coins.getOrDefault(upper, 0L), room / GROUP_SIZE));
    }

    /**
     * Combien de fois on peut regrouper 10 pièces de la coupure du dessous en 1 pièce {@code upper}, sans que
     * celle-ci dépasse {@code max} : 0 si la paire n'est pas au rapport de 10.
     */
    public static long maxGroups(Map<Coin, Long> coins, Coin upper, ToLongFunction<Coin> value, long max) {
        if (!atPar(upper, value)) {
            return 0L;
        }
        Coin lower = Coin.values()[upper.ordinal() - 1];
        long room = Math.max(0L, max - coins.getOrDefault(upper, 0L));
        return Math.max(0L, Math.min(coins.getOrDefault(lower, 0L) / GROUP_SIZE, room));
    }

    /**
     * Casse {@code times} pièces {@code upper}, chacune en 10 pièces de la coupure du dessous, à valeur égale.
     *
     * @return le nouveau contenu (coupures nulles omises), ou vide si ce n'est pas possible : rapport différent
     *         de 10, pas assez de pièces, plus de place sous {@code max}, ou {@code times} pas strictement positif
     */
    public static Optional<EnumMap<Coin, Long>> breakOne(EnumMap<Coin, Long> coins, Coin upper, long times,
            ToLongFunction<Coin> value, long max) {
        if (times <= 0L || times > maxBreaks(coins, upper, value, max)) {
            return Optional.empty();
        }
        Coin lower = Coin.values()[upper.ordinal() - 1];
        EnumMap<Coin, Long> out = new EnumMap<>(coins);
        out.merge(upper, -times, Long::sum);
        out.merge(lower, times * GROUP_SIZE, Long::sum);
        out.values().removeIf(n -> n == 0L);
        return Optional.of(out);
    }

    /**
     * Regroupe {@code times} fois 10 pièces de la coupure du dessous en 1 pièce {@code upper}, à valeur égale.
     *
     * @return le nouveau contenu (coupures nulles omises), ou vide si ce n'est pas possible : rapport différent
     *         de 10, moins de 10 pièces du dessous par regroupement, plus de place sous {@code max}, ou
     *         {@code times} pas strictement positif
     */
    public static Optional<EnumMap<Coin, Long>> groupPair(EnumMap<Coin, Long> coins, Coin upper, long times,
            ToLongFunction<Coin> value, long max) {
        if (times <= 0L || times > maxGroups(coins, upper, value, max)) {
            return Optional.empty();
        }
        Coin lower = Coin.values()[upper.ordinal() - 1];
        EnumMap<Coin, Long> out = new EnumMap<>(coins);
        out.merge(lower, -times * GROUP_SIZE, Long::sum);
        out.merge(upper, times, Long::sum);
        out.values().removeIf(n -> n == 0L);
        return Optional.of(out);
    }

    /** Une pièce {@code upper} cassée, avec les valeurs vivantes et le plafond du coffre-fort et de la bourse. */
    public static Optional<EnumMap<Coin, Long>> breakOne(EnumMap<Coin, Long> coins, Coin upper) {
        return breakOne(coins, upper, 1L, Coin::value, SafeAccounts.MAX_PER_COIN);
    }

    /** Dix pièces regroupées en une {@code upper}, avec les valeurs vivantes et le plafond. */
    public static Optional<EnumMap<Coin, Long>> groupPair(EnumMap<Coin, Long> coins, Coin upper) {
        return groupPair(coins, upper, 1L, Coin::value, SafeAccounts.MAX_PER_COIN);
    }

    /** « 2 or 3 argent 5 cuivre », par clés de langue ; « 0 cuivre » pour un montant nul. */
    public static Component format(long amount) {
        EnumMap<Coin, Long> parts = decompose(amount);
        if (parts.isEmpty()) {
            return Component.literal("0 ").append(Component.translatable(Coin.COPPER.unitKey()));
        }
        MutableComponent out = Component.empty();
        Coin[] coins = Coin.values();
        boolean first = true;
        for (int i = coins.length - 1; i >= 0; i--) {
            Long count = parts.get(coins[i]);
            if (count == null) {
                continue;
            }
            if (!first) {
                out.append(" ");
            }
            first = false;
            out.append(count + " ").append(Component.translatable(coins[i].unitKey(count)));
        }
        return out;
    }

    /**
     * Paye {@code price} avec les pièces disponibles en prenant le moins de pièces possible : d'abord les
     * coupures exactes, de la plus grande à la plus petite ; s'il reste un reste dû, on casse la plus petite
     * pièce restante qui le couvre, et la monnaie rendue est décomposée gloutonnement.
     *
     * <p>{@code taken} et {@code change} sont des bilans nets : une coupure peut figurer dans les deux (on casse
     * une pièce dont une partie revient en monnaie de même coupure) ; l'appelant retire {@code taken} puis ajoute
     * {@code change}, ce qui donne le même résultat que de les compenser.
     *
     * @return vide si les pièces disponibles ne suffisent pas
     */
    public static Optional<Payment> makeChange(EnumMap<Coin, Long> available, long price) {
        EnumMap<Coin, Long> taken = new EnumMap<>(Coin.class);
        EnumMap<Coin, Long> change = new EnumMap<>(Coin.class);
        if (price <= 0L) {
            return Optional.of(new Payment(taken, change));
        }
        if (valueOf(available) < price) {
            return Optional.empty();
        }
        Coin[] coins = Coin.values();
        long due = price;
        for (int i = coins.length - 1; i >= 0 && due > 0; i--) {
            long have = available.getOrDefault(coins[i], 0L);
            long use = Math.min(have, due / coins[i].value());
            if (use > 0) {
                taken.put(coins[i], use);
                due -= use * coins[i].value();
            }
        }
        if (due > 0) {
            for (Coin coin : coins) {
                long left = available.getOrDefault(coin, 0L) - taken.getOrDefault(coin, 0L);
                if (left > 0 && coin.value() >= due) {
                    taken.merge(coin, 1L, Long::sum);
                    change = decompose(coin.value() - due);
                    due = 0;
                    break;
                }
            }
        }
        if (due > 0) {
            return Optional.empty();
        }
        return Optional.of(new Payment(taken, change));
    }
}
