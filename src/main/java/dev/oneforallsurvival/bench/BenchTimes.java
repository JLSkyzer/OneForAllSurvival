package dev.oneforallsurvival.bench;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calcul du temps de fabrication à l'établi. Logique pure, sans Minecraft : elle se teste seule.
 *
 * <p>Temps d'une recette : {@code t(r) = base + somme sur ses ingrédients de (par ingrédient + rareté +
 * moitié entière du moins cher de ses fabricants)}, plafonné, au moins 1 s. Un ingrédient sans
 * fabricant est une matière première (0).
 *
 * <p>Les recettes forment des boucles (lingot, bloc, pépite). On prend donc le plus petit point fixe :
 * tout part de l'infini, on recalcule jusqu'à stabilité (à chaque passe, depuis les valeurs de la
 * passe précédente). Le résultat ne dépend ni de l'ordre des recettes ni de leurs identifiants. Une
 * boucle sans entrée reste infinie : dans une seconde phase, ses fabricants sont traités comme des
 * matières premières.
 */
public final class BenchTimes {

    /** Réglages : base, coût par ingrédient, plafond, en secondes. */
    public record Params(int baseSeconds, int perIngredient, int maxSeconds) {}

    /**
     * Un ingrédient.
     *
     * @param rarityWeight poids de rareté (0 commun, 2 peu commun, 5 rare, 10 épique) ; le plus bas parmi
     *                     les objets possibles
     * @param makers       identifiants des recettes du catalogue qui produisent l'un des objets possibles ;
     *                     vide pour une matière première
     */
    public record Slot(int rarityWeight, List<String> makers) {}

    private static final long INFINITE = Long.MAX_VALUE;
    private static final int MAX_PASSES = 100_000;

    private BenchTimes() {}

    public static Map<String, Integer> compute(Map<String, List<Slot>> recipes, Params params) {
        return compute(recipes, params, Map.of());
    }

    /**
     * @param fixed durées imposées (surcharges) : des constantes, y compris pour les recettes qui en
     *              consomment le résultat
     */
    public static Map<String, Integer> compute(Map<String, List<Slot>> recipes, Params params,
            Map<String, Integer> fixed) {
        List<String> ids = new ArrayList<>(new TreeMap<>(recipes).keySet());
        Map<String, Long> t = new HashMap<>();
        for (String id : ids) {
            Integer imposed = fixed.get(id);
            t.put(id, imposed == null ? INFINITE : (long) imposed);
        }
        // Phase 1 : plus petit point fixe, à partir de l'infini.
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            Map<String, Long> next = new HashMap<>(t);
            boolean changed = false;
            for (String id : ids) {
                if (fixed.containsKey(id)) {
                    continue;
                }
                long v = evaluate(recipes.get(id), params, t, false);
                if (v != t.get(id)) {
                    next.put(id, v);
                    changed = true;
                }
            }
            t = next;
            if (!changed) {
                break;
            }
        }
        // Phase 2 : les recettes restées infinies ne vivent que dans des boucles sans entrée.
        Map<String, Long> settled = new HashMap<>(t);
        for (String id : ids) {
            if (t.get(id) == INFINITE) {
                settled.put(id, evaluate(recipes.get(id), params, t, true));
            }
        }
        Map<String, Integer> out = new TreeMap<>();
        for (String id : ids) {
            out.put(id, (int) (long) settled.get(id));
        }
        return out;
    }

    /**
     * @param loopsAreRaw vrai en phase 2 : un ingrédient dont tous les fabricants sont encore infinis
     *                    compte comme matière première ; sinon la recette reste infinie
     */
    private static long evaluate(List<Slot> slots, Params params, Map<String, Long> t, boolean loopsAreRaw) {
        long total = params.baseSeconds();
        for (Slot slot : slots) {
            total += params.perIngredient() + slot.rarityWeight();
            long cheapest = INFINITE;
            boolean known = false;
            for (String maker : slot.makers()) {
                Long value = t.get(maker);
                if (value != null) {
                    known = true;
                    cheapest = Math.min(cheapest, value);
                }
            }
            if (!known) {
                continue;
            }
            if (cheapest == INFINITE) {
                if (!loopsAreRaw) {
                    return INFINITE;
                }
                continue;
            }
            total += cheapest / 2;
        }
        return Math.max(1, Math.min(total, params.maxSeconds()));
    }
}
