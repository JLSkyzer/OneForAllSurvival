package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.Optional;

/**
 * Un contenant de pièces côté serveur, compté par coupure : le compte d'un coffre-fort ({@link SafeStore}) ou une
 * bourse ({@link PurseStore}). {@link CoinActions} ne connaît que lui : déposer, retirer, regrouper et changer se
 * font de la même façon pour les deux.
 */
public interface CoinStore {

    /** Plafond de pièces par coupure, le même pour le coffre-fort et la bourse. */
    long MAX_PER_COIN = SafeAccounts.MAX_PER_COIN;

    long count(Coin coin);

    /** Combien de pièces de cette coupure le contenant peut encore recevoir. */
    long room(Coin coin);

    /** Ajoute des pièces ; l'appelant a vérifié {@link #room} (sinon une exception, rien n'est écrit). */
    void add(Coin coin, long count);

    /** Retire exactement {@code count} pièces, ou rien. @return faux si le contenant en a moins */
    boolean remove(Coin coin, long count);

    /** Regroupe tout le contenu (10 pour 1, valeurs vivantes). @return vrai s'il a changé */
    boolean groupAll();

    /**
     * Vrai pour le coffre-fort : on y dépose aussi les pièces des bourses portées, et un retrait qui ne tient plus en
     * vrac entre dans la première bourse. Faux pour la bourse, qui n'échange qu'avec les pièces en vrac.
     */
    boolean usesPurses();

    /** Le contenu, coupures nulles omises. */
    default EnumMap<Coin, Long> contents() {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            long n = count(coin);
            if (n > 0L) {
                out.put(coin, n);
            }
        }
        return out;
    }

    /**
     * Casse des pièces {@code upper} en dix de la coupure du dessous : une seule, ou autant que possible si
     * {@code all}. Calculé par {@link CoinMath#breakOne(EnumMap, Coin, long, java.util.function.ToLongFunction, long)},
     * puis écrit : la valeur du contenu ne change pas.
     *
     * @return le nombre de pièces cassées (0 : rien n'a bougé)
     */
    default long breakOne(Coin upper, boolean all) {
        EnumMap<Coin, Long> before = contents();
        long times = all ? CoinMath.maxBreaks(before, upper, Coin::value, MAX_PER_COIN) : 1L;
        Optional<EnumMap<Coin, Long>> after = CoinMath.breakOne(before, upper, times, Coin::value, MAX_PER_COIN);
        if (after.isEmpty()) {
            return 0L;
        }
        replace(before, after.get());
        return times;
    }

    /**
     * Regroupe dix pièces de la coupure du dessous en une pièce {@code upper} : une fois, ou autant que possible si
     * {@code all}. La valeur du contenu ne change pas.
     *
     * @return le nombre de pièces {@code upper} obtenues (0 : rien n'a bougé)
     */
    default long groupPair(Coin upper, boolean all) {
        EnumMap<Coin, Long> before = contents();
        long times = all ? CoinMath.maxGroups(before, upper, Coin::value, MAX_PER_COIN) : 1L;
        Optional<EnumMap<Coin, Long>> after = CoinMath.groupPair(before, upper, times, Coin::value, MAX_PER_COIN);
        if (after.isEmpty()) {
            return 0L;
        }
        replace(before, after.get());
        return times;
    }

    /** Passe de {@code before} (le contenu actuel) à {@code after} : les retraits d'abord, puis les ajouts. */
    private void replace(EnumMap<Coin, Long> before, EnumMap<Coin, Long> after) {
        for (Coin coin : Coin.values()) {
            long diff = after.getOrDefault(coin, 0L) - before.getOrDefault(coin, 0L);
            if (diff < 0L && !remove(coin, -diff)) {
                throw new IllegalStateException("change : " + coin + " manquant");
            }
        }
        for (Coin coin : Coin.values()) {
            long diff = after.getOrDefault(coin, 0L) - before.getOrDefault(coin, 0L);
            if (diff > 0L) {
                add(coin, diff);
            }
        }
    }
}
