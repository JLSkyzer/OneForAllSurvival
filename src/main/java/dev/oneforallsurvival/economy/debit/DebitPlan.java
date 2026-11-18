package dev.oneforallsurvival.economy.debit;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.SafeAccounts;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Ce qu'un prélèvement prend et rend, calculé en entier avant de bouger une seule pièce (règle pure, sans monde). Les
 * trois sources sont, dans l'ordre : ce que le joueur <b>porte</b> (inventaire et bourses, joueur connecté et vivant),
 * ce qui l'<b>attend</b> chez le serveur (pièces en vrac et contenu des bourses en attente) et son <b>coffre-fort</b>.
 *
 * <p>Règle « Sources mêlées » : la première source qui suffit seule paie, la monnaie lui revient ({@link #change()} va
 * à {@link #changeTo()}, la même source ; pour le coffre-fort, en attente si une coupure dépasserait le plafond). Si
 * aucune ne suffit seule mais que leur total suffit, tout ce qui est porté et tout ce qui attend est pris, le reste vient
 * du coffre-fort et la monnaie va au coffre-fort (au plafond : en attente). Total insuffisant : pas de plan, rien ne
 * bouge. Toujours : valeur prise moins valeur rendue = montant.
 *
 * @param single la source qui paie ({@link Source#MIXED} si plusieurs)
 * @param fromHeld pièces prises sur le joueur
 * @param fromPending pièces prises dans son attente
 * @param fromSafe pièces prises dans son coffre-fort
 * @param change monnaie rendue (décomposition de {@link CoinMath#decompose})
 * @param changeTo où va la monnaie : {@link Source#HELD}, {@link Source#PENDING} ou {@link Source#SAFE}
 */
public record DebitPlan(Source single, EnumMap<Coin, Long> fromHeld, EnumMap<Coin, Long> fromPending,
        EnumMap<Coin, Long> fromSafe, EnumMap<Coin, Long> change, Source changeTo) {

    /** D'où vient l'argent d'un prélèvement. */
    public enum Source { HELD, PENDING, SAFE, MIXED }

    /**
     * Le plan qui paie {@code amount} cuivres, ou vide si les trois sources réunies ne suffisent pas (ou si le montant
     * n'est pas strictement positif).
     *
     * @param held ce que porte le joueur ; {@code null} : absent ou mort, l'inventaire ne compte pas
     * @param pending pièces en attente (bourses en attente comprises)
     * @param safe contenu du coffre-fort
     */
    public static Optional<DebitPlan> plan(@Nullable EnumMap<Coin, Long> held, EnumMap<Coin, Long> pending,
            EnumMap<Coin, Long> safe, long amount) {
        if (amount <= 0L) {
            return Optional.empty();
        }
        EnumMap<Coin, Long> h = clean(held);
        EnumMap<Coin, Long> p = clean(pending);
        EnumMap<Coin, Long> s = clean(safe);

        if (held != null) {
            Optional<CoinMath.Payment> pay = CoinMath.makeChange(h, amount);
            if (pay.isPresent()) {
                return Optional.of(new DebitPlan(Source.HELD, pay.get().taken(), empty(), empty(), pay.get().change(),
                        Source.HELD));
            }
        }
        Optional<CoinMath.Payment> fromPending = CoinMath.makeChange(p, amount);
        if (fromPending.isPresent()) {
            return Optional.of(new DebitPlan(Source.PENDING, empty(), fromPending.get().taken(), empty(),
                    fromPending.get().change(), Source.PENDING));
        }
        Optional<CoinMath.Payment> fromSafe = CoinMath.makeChange(s, amount);
        if (fromSafe.isPresent()) {
            EnumMap<Coin, Long> change = fromSafe.get().change();
            Source to = fitsSafe(s, fromSafe.get().taken(), change) ? Source.SAFE : Source.PENDING;
            return Optional.of(new DebitPlan(Source.SAFE, empty(), empty(), fromSafe.get().taken(), change, to));
        }

        // Aucune source seule : tout ce qui est porté et tout ce qui attend, le reste au coffre-fort.
        long covered = saturatedValue(h) + saturatedValue(p);
        EnumMap<Coin, Long> takenSafe;
        EnumMap<Coin, Long> change;
        if (covered >= amount) {
            takenSafe = empty();
            change = CoinMath.decompose(covered - amount);
        } else {
            Optional<CoinMath.Payment> rest = CoinMath.makeChange(s, amount - covered);
            if (rest.isEmpty()) {
                return Optional.empty();
            }
            takenSafe = rest.get().taken();
            change = rest.get().change();
        }
        Source to = fitsSafe(s, takenSafe, change) ? Source.SAFE : Source.PENDING;
        return Optional.of(new DebitPlan(Source.MIXED, new EnumMap<>(h), new EnumMap<>(p), takenSafe, change, to));
    }

    /** Le coffre-fort, après ce qu'on y prend, peut-il recevoir toute la monnaie sans dépasser le plafond ? */
    private static boolean fitsSafe(EnumMap<Coin, Long> safe, Map<Coin, Long> taken, Map<Coin, Long> change) {
        for (Map.Entry<Coin, Long> part : change.entrySet()) {
            long after = safe.getOrDefault(part.getKey(), 0L) - taken.getOrDefault(part.getKey(), 0L);
            if (part.getValue() > SafeAccounts.MAX_PER_COIN - after) {
                return false;
            }
        }
        return true;
    }

    private static EnumMap<Coin, Long> clean(@Nullable Map<Coin, Long> coins) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        if (coins != null) {
            coins.forEach((coin, count) -> {
                if (count != null && count > 0L) {
                    out.put(coin, count);
                }
            });
        }
        return out;
    }

    private static EnumMap<Coin, Long> empty() {
        return new EnumMap<>(Coin.class);
    }

    private static long saturatedValue(Map<Coin, Long> coins) {
        return SafeAccounts.saturatedValue(coins);
    }
}
