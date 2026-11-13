package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.stall.CommerceMath;
import java.util.EnumMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommerceMathTest {

    private static EnumMap<Coin, Long> coins(Object... pairs) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((Coin) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return out;
    }

    @Test
    void taxeZeroParDefaut() {
        assertEquals(0L, CommerceMath.salesTax(30L, 0));
        assertEquals(0L, CommerceMath.salesTax(100_000_000L, 0));
        assertEquals(0, EconomyConfig.DEFAULT_SALES_TAX_PERCENT, "la taxe par défaut est nulle");
    }

    @Test
    void taxeArrondieVersLeBas() {
        assertEquals(99L, CommerceMath.salesTax(999L, 10));
        assertEquals(3L, CommerceMath.salesTax(30L, 10));
        assertEquals(0L, CommerceMath.salesTax(9L, 10));
        assertEquals(0L, CommerceMath.salesTax(1L, 99));
        // Aucun débordement au prix le plus haut permis.
        assertEquals(99_000_000L, CommerceMath.salesTax(100_000_000L, 99));
    }

    @Test
    void taxeCentPourCent() {
        assertEquals(999L, CommerceMath.salesTax(999L, 100));
        // Hors bornes : ramené à 0..100, jamais négatif ni au-delà du prix.
        assertEquals(999L, CommerceMath.salesTax(999L, 250));
        assertEquals(0L, CommerceMath.salesTax(999L, -5));
        assertEquals(0L, CommerceMath.salesTax(0L, 50));
    }

    @Test
    void laCaissePaieEnCassantSesPieces() {
        Optional<EnumMap<Coin, Long>> after = CommerceMath.payFromBox(coins(Coin.GOLD, 1), 25L, Coin::value);
        assertTrue(after.isPresent());
        assertEquals(coins(Coin.SILVER, 7, Coin.COPPER, 5), after.get());
        // Le compte exact : rien à casser.
        assertEquals(coins(Coin.SILVER, 1), CommerceMath.payFromBox(coins(Coin.SILVER, 2, Coin.COPPER, 6), 16L, Coin::value)
                .orElseThrow());
        // 2 argent pour 16 : on casse le second, 4 cuivres reviennent.
        assertEquals(coins(Coin.COPPER, 4), CommerceMath.payFromBox(coins(Coin.SILVER, 2), 16L, Coin::value).orElseThrow());
    }

    @Test
    void laCaisseNePaiePasPlusQuElleNA() {
        assertTrue(CommerceMath.payFromBox(coins(Coin.SILVER, 1), 11L, Coin::value).isEmpty());
        assertTrue(CommerceMath.payFromBox(new EnumMap<>(Coin.class), 1L, Coin::value).isEmpty());
        // Un prix nul ou négatif ne se paie pas.
        assertTrue(CommerceMath.payFromBox(coins(Coin.GOLD, 1), 0L, Coin::value).isEmpty());
        assertTrue(CommerceMath.payFromBox(coins(Coin.GOLD, 1), -5L, Coin::value).isEmpty());
        // La caisse donnée n'est jamais modifiée.
        EnumMap<Coin, Long> box = coins(Coin.GOLD, 1);
        CommerceMath.payFromBox(box, 25L, Coin::value);
        assertEquals(coins(Coin.GOLD, 1), box);
    }

    @Test
    void valeurConservee() {
        EnumMap<Coin, Long> box = coins(Coin.RUBY, 1, Coin.EMERALD, 3, Coin.GOLD, 2, Coin.SILVER, 9, Coin.COPPER, 4);
        long before = CoinMath.valueOf(box);
        for (long price : new long[] {1L, 7L, 25L, 99L, 101L, 999L, 3_333L, 13_294L}) {
            EnumMap<Coin, Long> after = CommerceMath.payFromBox(box, price, Coin::value).orElseThrow();
            assertEquals(before, CoinMath.valueOf(after) + price, "prix " + price);
            after.values().forEach(n -> assertTrue(n > 0L, "aucune coupure nulle ni négative : " + after));
        }
        assertTrue(CommerceMath.payFromBox(box, before + 1, Coin::value).isEmpty());
        assertTrue(CommerceMath.payFromBox(box, before, Coin::value).orElseThrow().isEmpty());
    }
}
