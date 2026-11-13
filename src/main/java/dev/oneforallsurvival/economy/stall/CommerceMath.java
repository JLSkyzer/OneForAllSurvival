package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinStore;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToLongFunction;

/** Calculs purs du commerce à l'étal : la taxe, et le paiement par la caisse. Aucun état, aucun monde. */
public final class CommerceMath {

    private CommerceMath() {}

    /**
     * La taxe sur une vente : {@code price * percent / 100}, arrondie vers le bas (en faveur du vendeur, aucun
     * cuivre fractionnaire). Le pourcentage est ramené entre 0 et 100 ; un prix nul ou négatif ne paie rien.
     */
    public static long salesTax(long price, int percent) {
        if (price <= 0L) {
            return 0L;
        }
        int clamped = Math.max(0, Math.min(100, percent));
        // price < 2^63 / 100 en pratique (prix bornés) ; on reste exact au-delà en divisant d'abord.
        return price / 100L * clamped + price % 100L * clamped / 100L;
    }

    /**
     * La caisse {@code box} paie exactement {@code price} : on retire les pièces que choisit
     * {@link CoinMath#makeChange} et on y remet la monnaie (elle casse ses propres pièces au besoin).
     *
     * @param value la valeur de chaque coupure, en cuivres (celle de la configuration)
     * @return la nouvelle caisse (coupures nulles omises), ou vide si elle n'a pas la valeur, si le prix n'est pas
     *     positif, ou si une coupure dépasserait le plafond ; {@code box} n'est jamais modifiée
     */
    public static Optional<EnumMap<Coin, Long>> payFromBox(Map<Coin, Long> box, long price, ToLongFunction<Coin> value) {
        if (price <= 0L) {
            return Optional.empty();
        }
        EnumMap<Coin, Long> copy = new EnumMap<>(Coin.class);
        box.forEach((coin, n) -> {
            if (n != null && n > 0L) {
                copy.put(coin, n);
            }
        });
        Optional<CoinMath.Payment> payment = CoinMath.makeChange(copy, price);
        if (payment.isEmpty()) {
            return Optional.empty();
        }
        EnumMap<Coin, Long> after = new EnumMap<>(copy);
        payment.get().taken().forEach((coin, n) -> after.merge(coin, -n, Long::sum));
        payment.get().change().forEach((coin, n) -> after.merge(coin, n, Long::sum));
        after.values().removeIf(n -> n == 0L);
        for (long n : after.values()) {
            if (n < 0L || n > CoinStore.MAX_PER_COIN) {
                return Optional.empty();
            }
        }
        // Garde-fou : la valeur avant vaut la valeur après plus le prix, avec les valeurs données.
        if (valueOf(copy, value) != valueOf(after, value) + price) {
            return Optional.empty();
        }
        return Optional.of(after);
    }

    private static long valueOf(Map<Coin, Long> coins, ToLongFunction<Coin> value) {
        long total = 0L;
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            total = Math.addExact(total, Math.multiplyExact(value.applyAsLong(entry.getKey()), entry.getValue()));
        }
        return total;
    }
}
