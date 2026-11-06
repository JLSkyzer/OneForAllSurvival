package dev.oneforallsurvival.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class BenchTimesTest {

    private static final BenchTimes.Params DEFAUT = new BenchTimes.Params(4, 1, 600);

    private static BenchTimes.Slot brut(int rarete) {
        return new BenchTimes.Slot(rarete, List.of());
    }

    private static BenchTimes.Slot fabrique(String... fabricants) {
        return new BenchTimes.Slot(0, List.of(fabricants));
    }

    private static List<BenchTimes.Slot> neuf(BenchTimes.Slot slot) {
        return Collections.nCopies(9, slot);
    }

    @Test
    void sansIngredient() {
        assertEquals(4, BenchTimes.compute(Map.of("a", List.of()), DEFAUT).get("a"));
    }

    @Test
    void lesIngredientsEtLaRaretePesent() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of("a", List.of(brut(0), brut(5)));
        assertEquals(4 + 1 + 1 + 5, BenchTimes.compute(recettes, DEFAUT).get("a"));
    }

    @Test
    void laComplexiteSeTransmet() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of(
                "b", List.of(brut(0), brut(5)),
                "a", List.of(fabrique("b")));
        Map<String, Integer> temps = BenchTimes.compute(recettes, DEFAUT);
        assertEquals(11, temps.get("b"));
        // 4 de base, 1 pour l'ingrédient, la moitié entière de 11 pour sa fabrication.
        assertEquals(4 + 1 + 5, temps.get("a"));
    }

    @Test
    void leTempsEstPlafonne() {
        assertEquals(600, BenchTimes.compute(Map.of("a", List.of(brut(10_000))), DEFAUT).get("a"));
    }

    @Test
    void uneDureeImposeeSeTransmetAuxRecettesQuiLaConsomment() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of(
                "b", List.of(brut(0)),
                "a", List.of(fabrique("b")));
        Map<String, Integer> temps = BenchTimes.compute(recettes, DEFAUT, Map.of("b", 120));
        assertEquals(120, temps.get("b"));
        assertEquals(4 + 1 + 60, temps.get("a"));
    }

    @Test
    void leMoinsCherGagne() {
        for (List<String> fabricants : List.of(List.of("m10", "m50"), List.of("m50", "m10"))) {
            Map<String, List<BenchTimes.Slot>> recettes = Map.of(
                    "m10", List.of(), "m50", List.of(),
                    "a", List.of(new BenchTimes.Slot(0, fabricants)));
            Map<String, Integer> temps = BenchTimes.compute(recettes, DEFAUT, Map.of("m10", 10, "m50", 50));
            assertEquals(4 + 1 + 5, temps.get("a"));
        }
    }

    /** Boucle lingot, bloc, pépite avec une entrée : la pépite peut aussi sortir d'un minerai. */
    private static Map<String, List<BenchTimes.Slot>> boucle(boolean avecEntree, UnaryOperator<String> nom,
            Random hasard) {
        Map<String, List<BenchTimes.Slot>> brut = new LinkedHashMap<>();
        String bloc = nom.apply("bloc");
        String lingotDuBloc = nom.apply("lingot_du_bloc");
        String lingotDesPepites = nom.apply("lingot_des_pepites");
        String pepiteDuLingot = nom.apply("pepite_du_lingot");
        String pepiteDuMinerai = nom.apply("pepite_du_minerai");
        brut.put(bloc, neuf(fabrique(lingotDuBloc, lingotDesPepites)));
        brut.put(lingotDuBloc, List.of(fabrique(bloc)));
        brut.put(lingotDesPepites, neuf(avecEntree ? fabrique(pepiteDuLingot, pepiteDuMinerai) : fabrique(pepiteDuLingot)));
        brut.put(pepiteDuLingot, List.of(fabrique(lingotDuBloc, lingotDesPepites)));
        if (avecEntree) {
            brut.put(pepiteDuMinerai, List.of(brut(0)));
        }
        List<String> cles = new ArrayList<>(brut.keySet());
        if (hasard != null) {
            Collections.shuffle(cles, hasard);
        }
        Map<String, List<BenchTimes.Slot>> melange = new LinkedHashMap<>();
        for (String cle : cles) {
            melange.put(cle, brut.get(cle));
        }
        return melange;
    }

    @Test
    void uneBoucleLingotBlocPepite() {
        Map<String, Integer> temps = BenchTimes.compute(boucle(false, UnaryOperator.identity(), null), DEFAUT);
        // Aucune entrée : les fabricants de la boucle comptent comme matière première.
        assertEquals(4 + 9, temps.get("bloc"));
        assertEquals(4 + 1, temps.get("lingot_du_bloc"));
        assertEquals(4 + 9, temps.get("lingot_des_pepites"));
        assertEquals(4 + 1, temps.get("pepite_du_lingot"));
    }

    @Test
    void uneBoucleAvecUneEntree() {
        Map<String, Integer> temps = BenchTimes.compute(boucle(true, UnaryOperator.identity(), null), DEFAUT);
        assertEquals(5, temps.get("pepite_du_minerai"));
        // Lingot de 9 pépites de minerai : 4 + 9 x (1 + 5/2).
        assertEquals(31, temps.get("lingot_des_pepites"));
        // Le bloc prend le lingot le moins cher : 4 + 9 x (1 + 31/2).
        assertEquals(148, temps.get("bloc"));
        assertEquals(4 + 1 + 148 / 2, temps.get("lingot_du_bloc"));
        // La pépite du lingot prend le lingot le moins cher (31) : 4 + 1 + 15.
        assertEquals(20, temps.get("pepite_du_lingot"));
    }

    @Test
    void leResultatNeDependPasDeLOrdre() {
        for (boolean entree : new boolean[] {false, true}) {
            Map<String, Integer> reference = BenchTimes.compute(boucle(entree, UnaryOperator.identity(), null), DEFAUT);
            for (long graine = 0; graine < 20; graine++) {
                assertEquals(reference,
                        BenchTimes.compute(boucle(entree, UnaryOperator.identity(), new Random(graine)), DEFAUT));
            }
            // Identifiants renommés pour inverser l'ordre alphabétique.
            Map<String, Integer> renomme = BenchTimes.compute(
                    boucle(entree, n -> "z" + (char) ('z' - n.charAt(0)) + n, new Random(7)), DEFAUT);
            Map<String, Integer> attendu = new LinkedHashMap<>();
            reference.forEach((n, v) -> attendu.put("z" + (char) ('z' - n.charAt(0)) + n, v));
            assertEquals(new java.util.TreeMap<>(attendu), new java.util.TreeMap<>(renomme));
        }
    }

    @Test
    void unCycleNeBouclePas() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of(
                "a", List.of(fabrique("b")),
                "b", List.of(fabrique("a")));
        Map<String, Integer> temps = BenchTimes.compute(recettes, DEFAUT);
        assertTrue(temps.get("a") <= 600 && temps.get("b") <= 600);
        assertEquals(5, temps.get("a"));
        assertEquals(5, temps.get("b"));
    }

    @Test
    void auMoinsUneSeconde() {
        assertEquals(1, BenchTimes.compute(Map.of("a", List.of()), new BenchTimes.Params(0, 0, 600)).get("a"));
    }
}
