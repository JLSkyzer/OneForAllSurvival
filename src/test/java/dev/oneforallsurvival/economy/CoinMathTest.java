package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class CoinMathTest {

    private static EnumMap<Coin, Long> coins(Object... pairs) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((Coin) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return out;
    }

    @Test
    void decomposeGlouton() {
        // 235 cuivres : 2 or (200), 3 argent (30), 5 cuivre.
        assertEquals(coins(Coin.GOLD, 2, Coin.SILVER, 3, Coin.COPPER, 5), CoinMath.decompose(235));
        // Une coupure à zéro est omise : 2 305 = 2 émeraude, 3 or, 5 cuivre.
        assertEquals(coins(Coin.EMERALD, 2, Coin.GOLD, 3, Coin.COPPER, 5), CoinMath.decompose(2305));
        assertTrue(CoinMath.decompose(0).isEmpty());
        assertEquals(coins(Coin.RUBY, 12), CoinMath.decompose(120_000));
    }

    @Test
    void valeursParDefautEtOrdre() {
        assertEquals(1L, Coin.COPPER.value());
        assertEquals(10L, Coin.SILVER.value());
        assertEquals(100L, Coin.GOLD.value());
        assertEquals(1_000L, Coin.EMERALD.value());
        assertEquals(10_000L, Coin.RUBY.value());
    }

    @Test
    void formatPlusGrandeCoupure() {
        String text = CoinMath.format(235).getString();
        int gold = text.indexOf("2 " + Coin.GOLD.unitKey());
        int silver = text.indexOf("3 " + Coin.SILVER.unitKey());
        int copper = text.indexOf("5 " + Coin.COPPER.unitKey());
        assertTrue(gold >= 0 && silver > gold && copper > silver, text);
        assertFalse(text.contains(Coin.EMERALD.unitKey()), text);
        Component zero = CoinMath.format(0);
        assertTrue(zero.getString().startsWith("0 "), zero.getString());
    }

    @Test
    void monnaieExacte() {
        // 12 cuivres pour un prix de 7 : on prend exactement 7 cuivres, rien à rendre.
        CoinMath.Payment p = CoinMath.makeChange(coins(Coin.COPPER, 12), 7).orElseThrow();
        assertEquals(coins(Coin.COPPER, 7), p.taken());
        assertTrue(p.change().isEmpty());
    }

    @Test
    void onCasseLaPlusPetitePieceSuffisante() {
        // 1 or et 3 argent pour 25 : 2 argent exacts, puis on casse le 3e argent (rend 5 cuivres).
        CoinMath.Payment p = CoinMath.makeChange(coins(Coin.GOLD, 1, Coin.SILVER, 3), 25).orElseThrow();
        assertEquals(coins(Coin.SILVER, 3), p.taken());
        assertEquals(coins(Coin.COPPER, 5), p.change());
        // L'or seul pour 25 : on casse l'or, on rend 7 argent et 5 cuivres.
        CoinMath.Payment q = CoinMath.makeChange(coins(Coin.GOLD, 1), 25).orElseThrow();
        assertEquals(coins(Coin.GOLD, 1), q.taken());
        assertEquals(coins(Coin.SILVER, 7, Coin.COPPER, 5), q.change());
    }

    @Test
    void laValeurPrisePlusLaMonnaieEgaleLePrix() {
        EnumMap<Coin, Long> available = coins(Coin.RUBY, 1, Coin.GOLD, 2, Coin.SILVER, 1, Coin.COPPER, 4);
        for (long price : new long[] {1, 4, 9, 14, 50, 214, 215, 9_999, 10_000}) {
            Optional<CoinMath.Payment> p = CoinMath.makeChange(available, price);
            assertTrue(p.isPresent(), "prix " + price);
            assertEquals(price, CoinMath.valueOf(p.get().taken()) - CoinMath.valueOf(p.get().change()), "prix " + price);
            p.get().taken().forEach((coin, n) -> assertTrue(n <= available.get(coin), "prix " + price));
        }
    }

    @Test
    void insuffisantVide() {
        assertTrue(CoinMath.makeChange(coins(Coin.GOLD, 1, Coin.COPPER, 4), 105).isEmpty());
        assertTrue(CoinMath.makeChange(new EnumMap<>(Coin.class), 1).isEmpty());
    }

    @Test
    void valeurDUnePile() {
        assertEquals(300L, CoinMath.valueOf(Coin.GOLD, 3));
        assertEquals(0L, CoinMath.valueOf(Coin.RUBY, 0));
        assertEquals(10_000L + 3 * 10, CoinMath.valueOf(coins(Coin.RUBY, 1, Coin.SILVER, 3)));
    }

    @Test
    void regrouperDixPourUn() {
        assertEquals(coins(Coin.SILVER, 1, Coin.COPPER, 5), CoinMath.group(coins(Coin.COPPER, 15)));
        // Report en cascade : 99 cuivres + 1 = 100 cuivres = 1 or.
        assertEquals(coins(Coin.GOLD, 1), CoinMath.group(coins(Coin.COPPER, 100)));
        // Le rubis est la dernière coupure : on n'en sort pas.
        assertEquals(coins(Coin.RUBY, 25), CoinMath.group(coins(Coin.RUBY, 25)));
        assertEquals(coins(Coin.RUBY, 1, Coin.EMERALD, 9), CoinMath.group(coins(Coin.EMERALD, 19)));
        EnumMap<Coin, Long> mixte = coins(Coin.COPPER, 123, Coin.SILVER, 47, Coin.GOLD, 8, Coin.EMERALD, 12);
        assertEquals(CoinMath.valueOf(mixte), CoinMath.valueOf(CoinMath.group(mixte)));
        CoinMath.group(mixte).forEach((coin, n) -> assertTrue(coin == Coin.RUBY || n < 10, coin + " " + n));
        assertTrue(CoinMath.group(new EnumMap<>(Coin.class)).isEmpty());
    }

    @Test
    void leCuivreVautToujoursUnEtChaquePieceEstUnMultiple() {
        String defauts = java.util.Arrays.toString(EconomyConfig.DEFAULTS);
        // Cuivre different de 1, pieces non multiples, egales ou decroissantes : valeurs par defaut.
        for (long[] bad : new long[][] {{2, 20, 200, 2000, 20000}, {1, 10, 105, 1000, 10000},
                {1, 10, 100, 100, 10000}, {1, 10, 100, 1000, 500}, {1, 10, 100}}) {
            assertEquals(defauts, java.util.Arrays.toString(EconomyConfig.validated(bad)), java.util.Arrays.toString(bad));
        }
        long[] good = {1, 5, 50, 500, 5000};
        assertEquals(java.util.Arrays.toString(good), java.util.Arrays.toString(EconomyConfig.validated(good)));
    }
}
