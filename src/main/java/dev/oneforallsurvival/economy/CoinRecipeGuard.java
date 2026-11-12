package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.OneForAllSurvival;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.slf4j.Logger;

/**
 * Aucune recette ne produit une pièce, sauf les quatre recettes de change vers le bas, à valeur égale (1 pièce donne 10 de la coupure inférieure ; le regroupement 10 pour 1 se fait hors de la table). Toute autre recette
 * qui en fabriquerait une, de n'importe quel type et de n'importe quel mod ou script, quitte le jeu et est
 * signalée au journal.
 */
public final class CoinRecipeGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Les quatre recettes de change vers le bas, dans {@code data/oneforallsurvival/recipes/coin_change/}. */
    public static final Set<ResourceLocation> CHANGE_RECIPES = Set.of(
            change("silver_to_copper"), change("gold_to_silver"),
            change("emerald_to_gold"), change("ruby_to_emerald"));

    private CoinRecipeGuard() {}

    private static ResourceLocation change(String name) {
        return new ResourceLocation(OneForAllSurvival.MODID, "coin_change/" + name);
    }

    /**
     * Retire du manager toute recette dont le résultat est une pièce, hors change.
     *
     * <p>Une recette de change n'est épargnée que sous son identifiant exact et en classe {@code
     * ShapelessRecipe} : un script qui la remplacerait par une recette dynamique est retiré.
     *
     * @return les identifiants retirés
     */
    public static List<ResourceLocation> filter(RecipeManager manager, RegistryAccess access) {
        List<Recipe<?>> keep = new ArrayList<>();
        List<ResourceLocation> removed = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (!producesCoin(recipe, access) || isChange(recipe)) {
                keep.add(recipe);
                continue;
            }
            removed.add(recipe.getId());
            LOGGER.warn("Economie : recette retiree, elle fabrique une piece : {} (classe {})",
                    recipe.getId(), recipe.getClass().getName());
        }
        if (!removed.isEmpty()) {
            manager.replaceRecipes(keep);
        }
        return removed;
    }

    private static boolean isChange(Recipe<?> recipe) {
        return CHANGE_RECIPES.contains(recipe.getId()) && recipe.getClass() == ShapelessRecipe.class;
    }

    private static boolean producesCoin(Recipe<?> recipe, RegistryAccess access) {
        ItemStack result;
        try {
            result = recipe.getResultItem(access);
        } catch (RuntimeException e) {
            LOGGER.warn("Economie : resultat illisible pour {}", recipe.getId(), e);
            return false;
        }
        return !result.isEmpty() && Coin.fromItem(result.getItem()).isPresent();
    }
}
