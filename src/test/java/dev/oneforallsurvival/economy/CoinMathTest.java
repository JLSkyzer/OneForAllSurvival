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
    void pluriel() {
        for (Coin c : Coin.values()) {
            assertEquals(c.unitKey(), c.unitKey(0));
            assertEquals(c.unitKey(), c.unitKey(1));
            assertEquals(c.unitKey() + ".plural", c.unitKey(2));
            assertEquals(c.unitKey() + ".plural", c.unitKey(1_000_000));
        }
        String texte = CoinMath.format(2_203).getString();
        assertTrue(texte.contains("2 " + Coin.EMERALD.unitKey() + ".plural"), texte);
        assertTrue(texte.contains("2 " + Coin.GOLD.unitKey() + ".plural"), texte);
        assertTrue(texte.contains("3 " + Coin.COPPER.unitKey() + ".plural"), texte);
        String un = CoinMath.format(1_001).getString();
        assertTrue(un.contains("1 " + Coin.EMERALD.unitKey()) && !un.contains(".plural"), un);
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
    void regrouperSuitLeRapportVivant() {
        // Rapport de 10 partout : comme le regroupement fixe.
        assertEquals(CoinMath.group(coins(Coin.COPPER, 123, Coin.SILVER, 47)),
                CoinMath.group(coins(Coin.COPPER, 123, Coin.SILVER, 47), Coin::value));
        // L'argent vaut 20 cuivres : le cuivre ne se regroupe pas, mais l'argent monte en or (or = 10 argents).
        java.util.function.ToLongFunction<Coin> exotique = coin -> new long[] {1, 20, 200, 2000, 20000}[coin.ordinal()];
        assertEquals(coins(Coin.COPPER, 25, Coin.GOLD, 1, Coin.SILVER, 3),
                CoinMath.group(coins(Coin.COPPER, 25, Coin.SILVER, 13), exotique));
        // L'or vaut 1000 cuivres (100 fois l'argent) : le cuivre monte en argent, mais l'argent ne monte pas en or.
        java.util.function.ToLongFunction<Coin> trop = coin -> new long[] {1, 10, 1000, 10000, 100000}[coin.ordinal()];
        assertEquals(coins(Coin.COPPER, 5, Coin.SILVER, 12, Coin.EMERALD, 1, Coin.GOLD, 5),
                CoinMath.group(coins(Coin.COPPER, 15, Coin.SILVER, 11, Coin.GOLD, 15), trop));
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

    private static final java.util.function.ToLongFunction<Coin> DIX = coin -> new long[] {1, 10, 100, 1000, 10000}[coin.ordinal()];

    @Test
    void casserUnePieceAValeurEgale() {
        // 1 or -> 10 argent, une fois ; la valeur ne change pas.
        EnumMap<Coin, Long> avant = coins(Coin.GOLD, 3, Coin.SILVER, 2);
        EnumMap<Coin, Long> apres = CoinMath.breakOne(avant, Coin.GOLD, 1, DIX, 1000).orElseThrow();
        assertEquals(coins(Coin.GOLD, 2, Coin.SILVER, 12), apres);
        assertEquals(CoinMath.valueOf(avant), CoinMath.valueOf(apres));
        assertEquals(coins(Coin.GOLD, 3, Coin.SILVER, 2), avant, "l'entrée n'est pas modifiée");
        // La dernière pièce cassée disparaît de la map.
        assertEquals(coins(Coin.COPPER, 10), CoinMath.breakOne(coins(Coin.SILVER, 1), Coin.SILVER, 1, DIX, 1000).orElseThrow());
        // Pas de pièce à casser, rien sous le cuivre, nombre de fois nul ou négatif.
        assertTrue(CoinMath.breakOne(coins(Coin.SILVER, 5), Coin.GOLD, 1, DIX, 1000).isEmpty());
        assertTrue(CoinMath.breakOne(coins(Coin.COPPER, 5), Coin.COPPER, 1, DIX, 1000).isEmpty());
        assertTrue(CoinMath.breakOne(coins(Coin.GOLD, 5), Coin.GOLD, 0, DIX, 1000).isEmpty());
        assertTrue(CoinMath.breakOne(coins(Coin.GOLD, 5), Coin.GOLD, -1, DIX, 1000).isEmpty());
    }

    @Test
    void casserRespecteLePlafondEtLeRapport() {
        // 995 argent, plafond 1000 : il n'y a pas la place de 10 de plus.
        assertEquals(0L, CoinMath.maxBreaks(coins(Coin.GOLD, 4, Coin.SILVER, 995), Coin.GOLD, DIX, 1000));
        assertTrue(CoinMath.breakOne(coins(Coin.GOLD, 4, Coin.SILVER, 995), Coin.GOLD, 1, DIX, 1000).isEmpty());
        // 975 argent : place pour 2 pièces cassées, même si on a 4 or.
        assertEquals(2L, CoinMath.maxBreaks(coins(Coin.GOLD, 4, Coin.SILVER, 975), Coin.GOLD, DIX, 1000));
        assertTrue(CoinMath.breakOne(coins(Coin.GOLD, 4, Coin.SILVER, 975), Coin.GOLD, 3, DIX, 1000).isEmpty());
        assertEquals(coins(Coin.GOLD, 2, Coin.SILVER, 995),
                CoinMath.breakOne(coins(Coin.GOLD, 4, Coin.SILVER, 975), Coin.GOLD, 2, DIX, 1000).orElseThrow());
        // Autant que possible : limité par les pièces.
        assertEquals(4L, CoinMath.maxBreaks(coins(Coin.GOLD, 4), Coin.GOLD, DIX, 1000));
        // Rapport de la config différent de 10 : jamais.
        java.util.function.ToLongFunction<Coin> exotique = coin -> new long[] {1, 20, 200, 2000, 20000}[coin.ordinal()];
        assertFalse(CoinMath.atPar(Coin.SILVER, exotique));
        assertTrue(CoinMath.atPar(Coin.GOLD, exotique));
        assertEquals(0L, CoinMath.maxBreaks(coins(Coin.SILVER, 4), Coin.SILVER, exotique, 1000));
        assertTrue(CoinMath.breakOne(coins(Coin.SILVER, 4), Coin.SILVER, 1, exotique, 1000).isEmpty());
        assertFalse(CoinMath.atPar(Coin.COPPER, DIX));
    }

    @Test
    void regrouperUnePaireAValeurEgale() {
        EnumMap<Coin, Long> avant = coins(Coin.SILVER, 25, Coin.GOLD, 1);
        EnumMap<Coin, Long> apres = CoinMath.groupPair(avant, Coin.GOLD, 1, DIX, 1000).orElseThrow();
        assertEquals(coins(Coin.SILVER, 15, Coin.GOLD, 2), apres);
        assertEquals(CoinMath.valueOf(avant), CoinMath.valueOf(apres));
        // Autant que possible : 2 fois (25 argent), puis plus rien.
        assertEquals(2L, CoinMath.maxGroups(avant, Coin.GOLD, DIX, 1000));
        assertEquals(coins(Coin.SILVER, 5, Coin.GOLD, 3), CoinMath.groupPair(avant, Coin.GOLD, 2, DIX, 1000).orElseThrow());
        assertTrue(CoinMath.groupPair(avant, Coin.GOLD, 3, DIX, 1000).isEmpty());
        // Moins de 10 : impossible ; plus de place au-dessus : impossible.
        assertTrue(CoinMath.groupPair(coins(Coin.SILVER, 9), Coin.GOLD, 1, DIX, 1000).isEmpty());
        assertEquals(0L, CoinMath.maxGroups(coins(Coin.SILVER, 50, Coin.GOLD, 1000), Coin.GOLD, DIX, 1000));
        assertEquals(1L, CoinMath.maxGroups(coins(Coin.SILVER, 50, Coin.GOLD, 999), Coin.GOLD, DIX, 1000));
        // Rapport différent de 10 : jamais ; le cuivre n'a rien en dessous.
        java.util.function.ToLongFunction<Coin> trop = coin -> new long[] {1, 10, 1000, 10000, 100000}[coin.ordinal()];
        assertTrue(CoinMath.groupPair(coins(Coin.SILVER, 50), Coin.GOLD, 1, trop, 1000).isEmpty());
        assertTrue(CoinMath.groupPair(coins(Coin.COPPER, 50), Coin.COPPER, 1, DIX, 1000).isEmpty());
        // Des montants qui frôlent le plafond réel ne débordent pas.
        long max = SafeAccounts.MAX_PER_COIN;
        assertEquals(max / 10, CoinMath.maxGroups(coins(Coin.COPPER, max), Coin.SILVER, DIX, max));
        assertEquals(max / 10, CoinMath.maxBreaks(coins(Coin.SILVER, max), Coin.SILVER, DIX, max));
    }

    @Test
    void lesDemandesHorsDesBoutonsSontRefusees() {
        assertTrue(CoinRequest.depositAll().coinIfValid().isPresent());
        assertTrue(CoinRequest.groupAll().coinIfValid().isPresent());
        for (Coin c : Coin.values()) {
            for (int n : CoinRequest.amounts()) {
                assertEquals(Optional.of(c), CoinRequest.deposit(c, n).coinIfValid());
                assertEquals(Optional.of(c), CoinRequest.withdraw(c, n).coinIfValid());
            }
        }
        for (int n : new int[] {0, -1, 2, 63, 65, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertTrue(new CoinRequest(CoinRequest.DEPOSIT, 0, n, false).coinIfValid().isEmpty(), "montant " + n);
            assertTrue(new CoinRequest(CoinRequest.WITHDRAW, 0, n, false).coinIfValid().isEmpty(), "montant " + n);
        }
        // Le change se nomme par la pièce du dessus : pas le cuivre ; pas de montant.
        assertTrue(CoinRequest.breakCoin(Coin.COPPER, false).coinIfValid().isEmpty());
        assertTrue(CoinRequest.groupPair(Coin.COPPER, true).coinIfValid().isEmpty());
        assertEquals(Optional.of(Coin.RUBY), CoinRequest.breakCoin(Coin.RUBY, true).coinIfValid());
        assertTrue(new CoinRequest(CoinRequest.BREAK, 2, 10, false).coinIfValid().isEmpty());
        // « Autant que possible » n'existe que pour le change.
        assertTrue(new CoinRequest(CoinRequest.WITHDRAW, 0, 1, true).coinIfValid().isEmpty());
        assertTrue(new CoinRequest(CoinRequest.DEPOSIT_ALL, 0, 0, true).coinIfValid().isEmpty());
        for (int coupure : new int[] {-1, 5, 99, Integer.MIN_VALUE}) {
            assertTrue(new CoinRequest(CoinRequest.WITHDRAW, coupure, 1, false).coinIfValid().isEmpty());
            assertTrue(new CoinRequest(CoinRequest.BREAK, coupure, 0, false).coinIfValid().isEmpty());
        }
        for (int action : new int[] {-1, 6, 99, Integer.MIN_VALUE}) {
            assertTrue(new CoinRequest(action, 1, 1, false).coinIfValid().isEmpty(), "action " + action);
        }
    }
}
