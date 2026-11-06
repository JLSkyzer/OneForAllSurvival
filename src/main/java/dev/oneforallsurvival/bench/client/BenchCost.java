package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.Withdrawal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Ce qu'une recette coûte face à un inventaire, calculé comme le serveur le fait ({@link Withdrawal}) :
 * le client ne décide de rien, il prévient seulement le joueur. Le serveur reste juge.
 */
final class BenchCost {

    /** Un ingrédient (les cases identiques sont regroupées) : ce qu'il accepte, combien il en faut par exemplaire. */
    record Line(Ingredient ingredient, List<ItemStack> choices, int perUnit, int have) {

        int needed(int quantity) {
            return perUnit * quantity;
        }

        boolean missing(int quantity) {
            return have < needed(quantity);
        }

        /** L'objet montré à l'instant {@code tick} : un ingrédient à étiquette fait défiler ses objets. */
        ItemStack shown(long tick) {
            return choices.get((int) Math.floorMod(tick, (long) choices.size()));
        }
    }

    private BenchCost() {}

    /** Les ingrédients regroupés, avec ce que l'inventaire en contient (toutes les piles qui conviennent). */
    static List<Line> lines(BenchRecipe recipe, List<ItemStack> inventory) {
        Map<List<Item>, int[]> counts = new LinkedHashMap<>();
        Map<List<Item>, Ingredient> first = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.ingredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            List<Item> key = new ArrayList<>();
            for (ItemStack s : ingredient.getItems()) {
                key.add(s.getItem());
            }
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            first.putIfAbsent(key, ingredient);
        }
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<List<Item>, int[]> e : counts.entrySet()) {
            Ingredient ingredient = first.get(e.getKey());
            List<ItemStack> choices = new ArrayList<>();
            for (ItemStack s : ingredient.getItems()) {
                choices.add(s.copyWithCount(1));
            }
            if (choices.isEmpty()) {
                choices.add(new ItemStack(Items.BARRIER));
            }
            int have = 0;
            for (ItemStack stack : inventory) {
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    have += stack.getCount();
                }
            }
            lines.add(new Line(ingredient, choices, e.getValue()[0], have));
        }
        return lines;
    }

    /** Combien d'exemplaires l'inventaire paie, au plus {@code limit} : exemplaire par exemplaire, comme le serveur. */
    static int maxUnits(BenchRecipe recipe, List<ItemStack> inventory, int limit) {
        List<ItemStack> simulated = new ArrayList<>(inventory.size());
        for (ItemStack stack : inventory) {
            simulated.add(stack.copy());
        }
        int units = 0;
        while (units < limit) {
            Optional<int[]> plan = Withdrawal.plan(recipe.ingredients(), simulated);
            if (plan.isEmpty()) {
                break;
            }
            int[] slots = plan.get();
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] >= 0 && !recipe.ingredients().get(i).isEmpty()) {
                    simulated.get(slots[i]).shrink(1);
                }
            }
            units++;
        }
        return units;
    }

    /** Empreinte bon marché de l'inventaire : change dès qu'une pile change d'objet, de nombre ou de NBT. */
    static int signature(List<ItemStack> inventory) {
        int h = 1;
        for (ItemStack stack : inventory) {
            h = h * 31 + (stack.isEmpty() ? 0 : System.identityHashCode(stack.getItem()) * 31 + stack.getCount());
            if (stack.hasTag()) {
                h = h * 31 + stack.getTag().hashCode();
            }
        }
        return h;
    }
}
