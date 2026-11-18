package dev.oneforallsurvival.grid.supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * Le calcul de la facture d'électricité : consommation fois prix (en cuivres par tranche de FE), arrondie au cuivre
 * inférieur, plus l'abonnement du jour ; taxe arrondie au cuivre inférieur. Jamais de dépassement : un montant qui ne
 * tient pas dans un {@code long} reste au plus grand.
 */
class PowerBillMathTest {

    private static final long TRANCHE = 100_000L;

    @Test
    void consommationFoisPrixPlusAbonnement() {
        // 2 300 000 FE à 5 cuivres les 100 000 FE : 115 ; plus 10 d'abonnement.
        assertEquals(115L, PowerBillMath.energyCost(2_300_000L, 5L, TRANCHE));
        assertEquals(125L, PowerBillMath.amount(2_300_000L, 5L, TRANCHE, 10L));
        assertEquals(10L, PowerBillMath.amount(0L, 5L, TRANCHE, 10L), "sans consommation : l'abonnement seul");
    }

    @Test
    void arrondiAuCuivreInferieur() {
        assertEquals(0L, PowerBillMath.energyCost(99_999L, 1L, TRANCHE));
        assertEquals(1L, PowerBillMath.energyCost(199_999L, 1L, TRANCHE));
        assertEquals(4L, PowerBillMath.energyCost(150_000L, 3L, TRANCHE), "4,5 cuivres : 4");
        assertEquals(4L, PowerBillMath.amount(150_000L, 3L, TRANCHE, 0L));
    }

    @Test
    void grandsNombresSansDepassement() {
        long fe = Long.MAX_VALUE / 2;
        long attendu = BigInteger.valueOf(fe).multiply(BigInteger.valueOf(20L)).divide(BigInteger.valueOf(TRANCHE))
                .longValueExact();
        assertEquals(attendu, PowerBillMath.energyCost(fe, 20L, TRANCHE));
        assertEquals(attendu + 50L, PowerBillMath.amount(fe, 20L, TRANCHE, 50L));
        // Un produit qui ne tient pas, même divisé : au plus grand, jamais négatif.
        assertEquals(Long.MAX_VALUE, PowerBillMath.energyCost(fe, Long.MAX_VALUE, 1L));
        assertEquals(Long.MAX_VALUE, PowerBillMath.amount(fe, Long.MAX_VALUE, 1L, 50L));
        assertEquals(Long.MAX_VALUE, PowerBillMath.amount(Long.MAX_VALUE, 1L, 1L, Long.MAX_VALUE));
    }

    @Test
    void taxeArrondieEnDessous() {
        assertEquals(12L, PowerBillMath.tax(125L, 10));
        assertEquals(0L, PowerBillMath.tax(9L, 10));
        assertEquals(0L, PowerBillMath.tax(125L, 0));
        assertEquals(125L, PowerBillMath.tax(125L, 100));
        long attendu = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(20L))
                .divide(BigInteger.valueOf(100L)).longValueExact();
        assertEquals(attendu, PowerBillMath.tax(Long.MAX_VALUE, 20), "sans dépassement");
    }

    @Test
    void prixNulEtAbonnementNulDonnentZero() {
        assertEquals(0L, PowerBillMath.amount(1_000_000L, 0L, TRANCHE, 0L));
        assertEquals(0L, PowerBillMath.amount(0L, 5L, TRANCHE, 0L));
        assertEquals(0L, PowerBillMath.energyCost(-5L, 5L, TRANCHE), "jamais négatif");
    }
}
