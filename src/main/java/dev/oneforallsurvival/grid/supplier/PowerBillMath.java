package dev.oneforallsurvival.grid.supplier;

import java.math.BigInteger;

/**
 * Le calcul pur de la facture quotidienne d'électricité (feature 83) : consommation fois prix, arrondie au cuivre
 * inférieur, plus l'abonnement ; taxe arrondie au cuivre inférieur. Jamais de dépassement ni de montant négatif : un
 * résultat qui ne tient pas dans un {@code long} reste à {@link Long#MAX_VALUE} (aucune facture réaliste n'y arrive,
 * mais une consommation saturée ne doit pas devenir une facture négative).
 */
public final class PowerBillMath {

    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger HUNDRED = BigInteger.valueOf(100L);

    private PowerBillMath() {}

    /**
     * Ce que coûtent {@code fe} FE au prix de {@code price} cuivres par tranche de {@code unitFe} FE, arrondi au cuivre
     * inférieur. Zéro pour une consommation ou un prix nul ou négatif ; une tranche nulle ou négative compte pour 1 FE.
     */
    public static long energyCost(long fe, long price, long unitFe) {
        if (fe <= 0L || price <= 0L) {
            return 0L;
        }
        long unit = Math.max(1L, unitFe);
        if (Math.multiplyHigh(fe, price) == 0L && fe * price >= 0L) {
            return fe * price / unit; // le produit tient dans un long (positif)
        }
        return clamp(BigInteger.valueOf(fe).multiply(BigInteger.valueOf(price)).divide(BigInteger.valueOf(unit)));
    }

    /** Le montant de la facture du jour : {@link #energyCost} plus l'abonnement (négatif : 0), sans dépassement. */
    public static long amount(long fe, long price, long unitFe, long subscription) {
        long energy = energyCost(fe, price, unitFe);
        long sub = Math.max(0L, subscription);
        long sum = energy + sub;
        return sum < energy ? Long.MAX_VALUE : sum;
    }

    /** La taxe retenue sur {@code amount} cuivres à {@code percent} % (borné à 0..100), arrondie au cuivre inférieur. */
    public static long tax(long amount, int percent) {
        if (amount <= 0L || percent <= 0) {
            return 0L;
        }
        int rate = Math.min(100, percent);
        return clamp(BigInteger.valueOf(amount).multiply(BigInteger.valueOf(rate)).divide(HUNDRED));
    }

    private static long clamp(BigInteger value) {
        return value.compareTo(MAX) > 0 ? Long.MAX_VALUE : Math.max(0L, value.longValue());
    }
}
