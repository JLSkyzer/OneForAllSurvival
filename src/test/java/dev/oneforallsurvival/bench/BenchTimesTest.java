package dev.oneforallsurvival.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BenchTimesTest {

    private static final BenchTimes.Params DEFAUT = new BenchTimes.Params(4, 1, 600);

    @Test
    void sansIngredient() {
        assertEquals(4, BenchTimes.compute(Map.of("a", List.of()), DEFAUT).get("a"));
    }

    @Test
    void lesIngredientsEtLaRaretePesent() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of(
                "a", List.of(new BenchTimes.Slot(0, null), new BenchTimes.Slot(5, null)));
        assertEquals(4 + 1 + 1 + 5, BenchTimes.compute(recettes, DEFAUT).get("a"));
    }

    @Test
    void laComplexiteSeTransmet() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of(
                "b", List.of(new BenchTimes.Slot(0, null), new BenchTimes.Slot(5, null)),
                "a", List.of(new BenchTimes.Slot(0, "b")));
        Map<String, Integer> temps = BenchTimes.compute(recettes, DEFAUT);
        assertEquals(11, temps.get("b"));
        // 4 de base, 1 pour l'ingrédient, la moitié entière de 11 pour sa fabrication.
        assertEquals(4 + 1 + 5, temps.get("a"));
    }

    @Test
    void leTempsEstPlafonne() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of("a", List.of(new BenchTimes.Slot(10_000, null)));
        assertEquals(600, BenchTimes.compute(recettes, DEFAUT).get("a"));
    }

    @Test
    void unCycleNeBouclePas() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of(
                "a", List.of(new BenchTimes.Slot(0, "b")),
                "b", List.of(new BenchTimes.Slot(0, "a")));
        Map<String, Integer> temps = BenchTimes.compute(recettes, DEFAUT);
        assertTrue(temps.get("a") <= 600 && temps.get("b") <= 600);
    }

    @Test
    void uneFabricationInconnueNeCompteRien() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of("a", List.of(new BenchTimes.Slot(0, "absente")));
        assertEquals(4 + 1, BenchTimes.compute(recettes, DEFAUT).get("a"));
    }

    @Test
    void uneDureeImposeeSeTransmetAuxRecettesQuiLaConsomment() {
        Map<String, List<BenchTimes.Slot>> recettes = Map.of(
                "b", List.of(new BenchTimes.Slot(0, null)),
                "a", List.of(new BenchTimes.Slot(0, "b")));
        Map<String, Integer> temps = BenchTimes.compute(recettes, DEFAUT, Map.of("b", 120));
        assertEquals(120, temps.get("b"));
        assertEquals(4 + 1 + 60, temps.get("a"));
    }
}
