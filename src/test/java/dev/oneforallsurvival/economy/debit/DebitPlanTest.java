package dev.oneforallsurvival.economy.debit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.debit.DebitPlan.Source;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * La règle du prélèvement (« Sources mêlées ») : une seule source qui suffit seule paie, dans l'ordre inventaire →
 * attente → coffre-fort, la monnaie revenant à cette source ; sinon, si le total suffit, tout ce qui est porté et tout ce
 * qui attend est pris, le reste vient du coffre-fort et la monnaie va au coffre-fort (au plafond : en attente) ; sinon
 * rien. Jamais partiel.
 */
class DebitPlanTest {

    private static EnumMap<Coin, Long> coins(Object... pairs) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((Coin) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return out;
    }

    private static EnumMap<Coin, Long> none() {
        return new EnumMap<>(Coin.class);
    }

    private static long v(Map<Coin, Long> coins) {
        return CoinMath.valueOf(coins);
    }

    /** La somme prise moins la monnaie rendue : ce que le créancier reçoit. */
    private static long net(DebitPlan plan) {
        return v(plan.fromHeld()) + v(plan.fromPending()) + v(plan.fromSafe()) - v(plan.change());
    }

    /** Chaque source ne donne jamais plus que ce qu'elle a, coupure par coupure. */
    private static void withinSources(DebitPlan plan, EnumMap<Coin, Long> held, EnumMap<Coin, Long> pending,
            EnumMap<Coin, Long> safe) {
        for (Coin coin : Coin.values()) {
            long h = held == null ? 0L : held.getOrDefault(coin, 0L);
            assertTrue(plan.fromHeld().getOrDefault(coin, 0L) <= h, "inventaire " + coin + " : " + plan);
            assertTrue(plan.fromPending().getOrDefault(coin, 0L) <= pending.getOrDefault(coin, 0L),
                    "attente " + coin + " : " + plan);
            assertTrue(plan.fromSafe().getOrDefault(coin, 0L) <= safe.getOrDefault(coin, 0L),
                    "coffre-fort " + coin + " : " + plan);
        }
    }

    @Test
    void linventaireSeulPaie() {
        EnumMap<Coin, Long> held = coins(Coin.GOLD, 3, Coin.COPPER, 5);
        EnumMap<Coin, Long> pending = coins(Coin.GOLD, 9);
        EnumMap<Coin, Long> safe = coins(Coin.EMERALD, 4);
        DebitPlan plan = DebitPlan.plan(held, pending, safe, 205L).orElseThrow();
        assertEquals(Source.HELD, plan.single());
        assertEquals(Source.HELD, plan.changeTo());
        assertEquals(coins(Coin.GOLD, 2, Coin.COPPER, 5), plan.fromHeld());
        assertTrue(plan.fromPending().isEmpty() && plan.fromSafe().isEmpty() && plan.change().isEmpty(), plan.toString());
        assertEquals(205L, net(plan));
    }

    @Test
    void sansInventaireLAttentePaie() {
        EnumMap<Coin, Long> held = coins(Coin.COPPER, 4);
        EnumMap<Coin, Long> pending = coins(Coin.GOLD, 1);
        EnumMap<Coin, Long> safe = coins(Coin.EMERALD, 4);
        DebitPlan plan = DebitPlan.plan(held, pending, safe, 30L).orElseThrow();
        assertEquals(Source.PENDING, plan.single());
        assertEquals(Source.PENDING, plan.changeTo());
        assertEquals(coins(Coin.GOLD, 1), plan.fromPending());
        assertEquals(CoinMath.decompose(70L), plan.change());
        assertTrue(plan.fromHeld().isEmpty() && plan.fromSafe().isEmpty(), plan.toString());
        assertEquals(30L, net(plan));
    }

    @Test
    void sansAttenteLeCoffreFortPaie() {
        EnumMap<Coin, Long> held = coins(Coin.COPPER, 4);
        EnumMap<Coin, Long> pending = coins(Coin.SILVER, 2);
        EnumMap<Coin, Long> safe = coins(Coin.GOLD, 5);
        DebitPlan plan = DebitPlan.plan(held, pending, safe, 250L).orElseThrow();
        assertEquals(Source.SAFE, plan.single());
        assertEquals(Source.SAFE, plan.changeTo());
        assertEquals(coins(Coin.GOLD, 3), plan.fromSafe());
        assertEquals(CoinMath.decompose(50L), plan.change());
        assertTrue(plan.fromHeld().isEmpty() && plan.fromPending().isEmpty(), plan.toString());
        assertEquals(250L, net(plan));
    }

    @Test
    void aucuneSourceSeuleMaisLeTotalSuffit() {
        EnumMap<Coin, Long> held = coins(Coin.GOLD, 1, Coin.COPPER, 3);
        EnumMap<Coin, Long> pending = coins(Coin.SILVER, 5);
        EnumMap<Coin, Long> safe = coins(Coin.GOLD, 2);
        // 103 porté + 50 en attente + 200 au coffre-fort ; aucune ne paie 250 seule.
        DebitPlan plan = DebitPlan.plan(held, pending, safe, 250L).orElseThrow();
        assertEquals(Source.MIXED, plan.single());
        assertEquals(Source.SAFE, plan.changeTo());
        assertEquals(held, plan.fromHeld(), "tout ce qui est porté");
        assertEquals(pending, plan.fromPending(), "tout ce qui attend");
        assertEquals(coins(Coin.GOLD, 1), plan.fromSafe(), "le reste (97) vient du coffre-fort");
        assertEquals(CoinMath.decompose(3L), plan.change(), "monnaie de 3 au coffre-fort");
        assertEquals(250L, net(plan));

        // Porté et attente couvrent à eux deux : le coffre-fort ne donne rien, il reçoit la monnaie.
        DebitPlan sansCoffre = DebitPlan.plan(coins(Coin.SILVER, 6), coins(Coin.SILVER, 6), none(), 100L).orElseThrow();
        assertEquals(Source.MIXED, sansCoffre.single());
        assertTrue(sansCoffre.fromSafe().isEmpty(), sansCoffre.toString());
        assertEquals(CoinMath.decompose(20L), sansCoffre.change());
        assertEquals(100L, net(sansCoffre));

        // Coffre-fort au plafond d'une coupure : il paie alors seul (une coupure au plafond vaut plus que toute
        // facture), et la monnaie ne le fait jamais déborder (règle vérifiée sur 1 000 tirages dans jamaisPartiel).
        EnumMap<Coin, Long> plein = coins(Coin.GOLD, 2, Coin.SILVER, SafeAccounts.MAX_PER_COIN);
        DebitPlan auPlafond = DebitPlan.plan(held, pending, plein, 255L).orElseThrow();
        assertEquals(Source.SAFE, auPlafond.single());
        noOverflow(auPlafond, plein);
        assertEquals(255L, net(auPlafond));
    }

    /** Monnaie rendue au coffre-fort : aucune coupure n'y dépasse le plafond. */
    private static void noOverflow(DebitPlan plan, EnumMap<Coin, Long> safe) {
        if (plan.changeTo() != Source.SAFE) {
            return;
        }
        for (Coin coin : Coin.values()) {
            long after = safe.getOrDefault(coin, 0L) - plan.fromSafe().getOrDefault(coin, 0L)
                    + plan.change().getOrDefault(coin, 0L);
            assertTrue(after <= SafeAccounts.MAX_PER_COIN, coin + " : " + after);
        }
    }

    @Test
    void unRubisPourTroisCuivresRendLaMonnaie() {
        for (long amount : new long[] {3L, 1L}) {
            DebitPlan plan = DebitPlan.plan(coins(Coin.RUBY, 1), none(), none(), amount).orElseThrow();
            assertEquals(Source.HELD, plan.single());
            assertEquals(coins(Coin.RUBY, 1), plan.fromHeld());
            assertEquals(CoinMath.decompose(10_000L - amount), plan.change(), "monnaie exacte pour " + amount);
            assertEquals(amount, net(plan));
        }
        // Au coffre-fort : même monnaie, rendue au coffre-fort.
        DebitPlan auCoffre = DebitPlan.plan(null, none(), coins(Coin.RUBY, 1), 3L).orElseThrow();
        assertEquals(Source.SAFE, auCoffre.single());
        assertEquals(Source.SAFE, auCoffre.changeTo());
        assertEquals(CoinMath.decompose(9_997L), auCoffre.change());
    }

    @Test
    void absentLInventaireNeCompteJamais() {
        EnumMap<Coin, Long> pending = coins(Coin.SILVER, 2);
        EnumMap<Coin, Long> safe = coins(Coin.GOLD, 1);
        DebitPlan plan = DebitPlan.plan(null, pending, safe, 50L).orElseThrow();
        assertEquals(Source.SAFE, plan.single());
        assertTrue(plan.fromHeld().isEmpty(), plan.toString());
        // Absent : ce qu'il aurait sur lui ne compte pas, même s'il suffirait.
        assertTrue(DebitPlan.plan(null, none(), none(), 1L).isEmpty());
        DebitPlan mixte = DebitPlan.plan(null, pending, safe, 110L).orElseThrow();
        assertEquals(Source.MIXED, mixte.single());
        assertTrue(mixte.fromHeld().isEmpty(), mixte.toString());
        assertEquals(110L, net(mixte));
    }

    @Test
    void totalInsuffisantRefuse() {
        assertTrue(DebitPlan.plan(coins(Coin.SILVER, 2), coins(Coin.SILVER, 2), coins(Coin.SILVER, 2), 61L).isEmpty());
        assertTrue(DebitPlan.plan(none(), none(), none(), 1L).isEmpty());
        assertEquals(Optional.empty(), DebitPlan.plan(coins(Coin.RUBY, 1), none(), none(), 10_001L));
        // La borne elle-même passe.
        assertTrue(DebitPlan.plan(coins(Coin.SILVER, 2), coins(Coin.SILVER, 2), coins(Coin.SILVER, 2), 60L).isPresent());
    }

    @Test
    void jamaisPartiel() {
        Random random = new Random(86L);
        for (int i = 0; i < 1_000; i++) {
            EnumMap<Coin, Long> held = random.nextInt(4) == 0 ? null : randomCoins(random);
            EnumMap<Coin, Long> pending = randomCoins(random);
            EnumMap<Coin, Long> safe = randomCoins(random);
            long amount = 1L + random.nextInt(40_000);
            long total = (held == null ? 0L : v(held)) + v(pending) + v(safe);
            Optional<DebitPlan> plan = DebitPlan.plan(held, pending, safe, amount);
            assertEquals(total >= amount, plan.isPresent(), "total " + total + " pour " + amount);
            if (plan.isPresent()) {
                assertEquals(amount, net(plan.get()), "montant " + amount + " : " + plan.get());
                withinSources(plan.get(), held, pending, safe);
                noOverflow(plan.get(), safe);
                if (held == null) {
                    assertTrue(plan.get().fromHeld().isEmpty(), "absent : " + plan.get());
                }
            }
        }
    }

    private static EnumMap<Coin, Long> randomCoins(Random random) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            if (random.nextInt(3) == 0) {
                out.put(coin, (long) random.nextInt(5));
            }
        }
        out.values().removeIf(n -> n == 0L);
        return out;
    }
}
