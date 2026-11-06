package dev.oneforallsurvival.bench;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Calcul du temps de fabrication à l'établi. Logique pure, sans Minecraft : elle se teste seule.
 *
 * <p>Temps d'une recette = base + par ingrédient (1 + poids de rareté + moitié du temps de la recette
 * qui fabrique cet ingrédient), plafonné. La complexité se transmet donc le long de la chaîne de
 * fabrication, amortie de moitié à chaque maillon.
 */
public final class BenchTimes {

    /** Réglages : base, coût par ingrédient, plafond, en secondes. */
    public record Params(int baseSeconds, int perIngredient, int maxSeconds) {}

    /**
     * Un ingrédient.
     *
     * @param rarityWeight poids de rareté de l'objet (0 commun, 2 peu commun, 5 rare, 10 épique)
     * @param madeBy       identifiant de la recette qui fabrique l'ingrédient, ou null
     */
    public record Slot(int rarityWeight, String madeBy) {}

    private BenchTimes() {}

    public static Map<String, Integer> compute(Map<String, List<Slot>> recipes, Params params) {
        return compute(recipes, params, Map.of());
    }

    /**
     * @param fixed durées imposées (surcharges) : elles valent telles quelles, y compris pour les
     *              recettes qui en consomment le résultat
     */
    public static Map<String, Integer> compute(Map<String, List<Slot>> recipes, Params params,
            Map<String, Integer> fixed) {
        Map<String, Integer> done = new HashMap<>();
        Set<String> inProgress = new HashSet<>();
        // Ordre trié : un cycle est coupé au même endroit à chaque chargement.
        Map<String, Integer> out = new TreeMap<>();
        for (String id : new TreeMap<>(recipes).keySet()) {
            out.put(id, time(id, recipes, params, fixed, done, inProgress));
        }
        return out;
    }

    private static int time(String id, Map<String, List<Slot>> recipes, Params params, Map<String, Integer> fixed,
            Map<String, Integer> done, Set<String> inProgress) {
        Integer imposed = fixed.get(id);
        if (imposed != null) {
            return imposed;
        }
        Integer known = done.get(id);
        if (known != null) {
            return known;
        }
        List<Slot> slots = recipes.get(id);
        // Recette inconnue, ou déjà en cours de calcul (cycle) : elle compte 0.
        if (slots == null || !inProgress.add(id)) {
            return 0;
        }
        long total = params.baseSeconds();
        for (Slot slot : slots) {
            total += params.perIngredient() + slot.rarityWeight();
            if (slot.madeBy() != null) {
                total += time(slot.madeBy(), recipes, params, fixed, done, inProgress) / 2;
            }
        }
        inProgress.remove(id);
        int result = (int) Math.min(total, params.maxSeconds());
        done.put(id, result);
        return result;
    }
}
