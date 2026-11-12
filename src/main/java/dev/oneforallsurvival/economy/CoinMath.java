package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
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

    /**
     * Regroupe chaque tranche de 10 pièces d'une coupure en 1 pièce de la coupure au-dessus, jusqu'au rubis,
     * à valeur égale. Le rapport est celui du change (10 pour 1), pas celui de la configuration.
     */
    public static EnumMap<Coin, Long> group(EnumMap<Coin, Long> coins) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        Coin[] all = Coin.values();
        long carry = 0L;
        for (int i = 0; i < all.length; i++) {
            long n = coins.getOrDefault(all[i], 0L) + carry;
            if (i < all.length - 1) {
                carry = n / 10;
                n = n % 10;
            }
            if (n > 0) {
                out.put(all[i], n);
            }
        }
        return out;
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
            out.append(count + " ").append(Component.translatable(coins[i].unitKey()));
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
