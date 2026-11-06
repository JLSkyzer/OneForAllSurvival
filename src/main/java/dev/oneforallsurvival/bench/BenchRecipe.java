package dev.oneforallsurvival.bench;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Une entrée du catalogue de l'établi : une recette sortie du RecipeManager.
 *
 * @param id          identifiant de la recette d'origine
 * @param result      objet fabriqué, avec sa quantité
 * @param ingredients ingrédients non vides, un par case occupée de la grille d'origine
 * @param category    type d'origine : « crafting », « stonecutting » ou « smithing »
 * @param seconds     durée de fabrication à la main, en secondes
 */
public record BenchRecipe(ResourceLocation id, ItemStack result, List<Ingredient> ingredients, String category,
        int seconds) {
}
