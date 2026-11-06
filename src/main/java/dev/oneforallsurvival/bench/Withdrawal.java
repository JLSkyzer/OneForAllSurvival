package dev.oneforallsurvival.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Calcul de prélèvement : à quelle case de l'inventaire chaque ingrédient est servi.
 * Ne modifie rien ; c'est à l'appelant d'appliquer le plan, d'un seul geste.
 */
public final class Withdrawal {

    private Withdrawal() {}

    /**
     * Cherche, pour chaque ingrédient, une case qui le satisfait, une unité par ingrédient.
     * Une case ne sert pas plus de fois que son nombre d'objets. Un ingrédient vide ne consomme rien
     * (index -1). L'affectation est un couplage maximum (chemins augmentants), donc un ingrédient
     * large ne prend jamais l'unique objet dont un ingrédient précis a besoin.
     *
     * @return un index de case par ingrédient, ou vide s'il en manque un
     */
    public static Optional<int[]> plan(List<Ingredient> needed, List<ItemStack> inventory) {
        int n = needed.size();
        int[] slotOf = new int[n];
        List<List<Integer>> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            slotOf[i] = -1;
            List<Integer> slots = new ArrayList<>();
            Ingredient ingredient = needed.get(i);
            if (!ingredient.isEmpty()) {
                for (int s = 0; s < inventory.size(); s++) {
                    ItemStack stack = inventory.get(s);
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        slots.add(s);
                    }
                }
            }
            candidates.add(slots);
        }

        List<List<Integer>> users = new ArrayList<>(inventory.size());
        for (int s = 0; s < inventory.size(); s++) {
            users.add(new ArrayList<>());
        }
        // Les plus contraints d'abord : l'ordre n'affecte pas l'issue, seulement la rapidité.
        List<Integer> order = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (!needed.get(i).isEmpty()) {
                order.add(i);
            }
        }
        order.sort((a, b) -> Integer.compare(candidates.get(a).size(), candidates.get(b).size()));

        for (int i : order) {
            if (!assign(i, candidates, users, inventory, slotOf, new boolean[inventory.size()])) {
                return Optional.empty();
            }
        }
        return Optional.of(slotOf);
    }

    private static boolean assign(int ingredient, List<List<Integer>> candidates, List<List<Integer>> users,
            List<ItemStack> inventory, int[] slotOf, boolean[] visited) {
        for (int s : candidates.get(ingredient)) {
            if (visited[s]) {
                continue;
            }
            visited[s] = true;
            List<Integer> onSlot = users.get(s);
            if (onSlot.size() < inventory.get(s).getCount()) {
                onSlot.add(ingredient);
                slotOf[ingredient] = s;
                return true;
            }
            // Case pleine : un de ses occupants peut-il aller ailleurs ?
            for (int k = 0; k < onSlot.size(); k++) {
                int other = onSlot.get(k);
                if (assign(other, candidates, users, inventory, slotOf, visited)) {
                    onSlot.set(k, ingredient);
                    slotOf[ingredient] = s;
                    return true;
                }
            }
        }
        return false;
    }
}
