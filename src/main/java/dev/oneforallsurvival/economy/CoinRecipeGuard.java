package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.OneForAllSurvival;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Aucune recette ne produit une pièce, sauf les quatre recettes de change vers le bas : 1 pièce donne 10 de la
 * coupure inférieure (le regroupement 10 pour 1 se fait hors de la table). Toute autre recette qui en fabriquerait
 * une, de n'importe quel type et de n'importe quel mod ou script, quitte le jeu et est signalée au journal.
 *
 * <p>Limite au chargement : une recette dynamique (résultat vide, comme une {@code CustomRecipe}, ou résultat
 * illisible) ne dit pas ce qu'elle fabriquera ; elle est gardée et comptée dans une seule ligne INFO du journal
 * (les identifiants au niveau DEBUG). Le filet d'exécution
 * ({@link #craftAllowed}), appelé par {@link CoinItem#onCraftedBy}, couvre les pièces qui sortiraient quand même
 * d'une grille de fabrication.
 */
public final class CoinRecipeGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Le nombre de pièces rendues par une recette de change. */
    private static final int CHANGE_COUNT = 10;

    /** Recette de change : une pièce {@code from} donne dix pièces {@code to}, la coupure inférieure. */
    private record Change(Coin from, Coin to) {}

    private static final Map<ResourceLocation, Change> CHANGES = new LinkedHashMap<>();

    static {
        addChange("silver_to_copper", Coin.SILVER, Coin.COPPER);
        addChange("gold_to_silver", Coin.GOLD, Coin.SILVER);
        addChange("emerald_to_gold", Coin.EMERALD, Coin.GOLD);
        addChange("ruby_to_emerald", Coin.RUBY, Coin.EMERALD);
    }

    /** Les quatre recettes de change, dans {@code data/oneforallsurvival/recipes/coin_change/}. */
    public static final Set<ResourceLocation> CHANGE_RECIPES = Set.copyOf(CHANGES.keySet());

    private CoinRecipeGuard() {}

    private static void addChange(String name, Coin from, Coin to) {
        CHANGES.put(new ResourceLocation(OneForAllSurvival.MODID, "coin_change/" + name), new Change(from, to));
    }

    /**
     * Retire du manager toute recette dont le résultat est une pièce, hors change. Une recette de change n'est
     * épargnée que sous son identifiant exact, en classe {@code ShapelessRecipe}, avec un seul ingrédient (la
     * pièce juste au-dessus) et pour résultat exactement 10 pièces de la coupure juste en dessous.
     *
     * @return les identifiants retirés
     */
    public static List<ResourceLocation> filter(RecipeManager manager, RegistryAccess access) {
        return filter(manager, access, Coin::value);
    }

    /**
     * Comme {@link #filter(RecipeManager, RegistryAccess)} avec des valeurs de pièces données (essais). Un change
     * n'est permis qu'à valeur égale : 10 pièces du dessous valent la pièce du dessus. Si la configuration donne un
     * autre rapport, les recettes de change livrées sont retirées (message unique au journal).
     */
    public static List<ResourceLocation> filter(RecipeManager manager, RegistryAccess access,
            ToLongFunction<Coin> value) {
        List<Recipe<?>> keep = new ArrayList<>();
        List<ResourceLocation> removed = new ArrayList<>();
        List<ResourceLocation> dynamic = new ArrayList<>();
        boolean badRatio = false;
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (!producesCoin(recipe, access, dynamic)) {
                keep.add(recipe);
                continue;
            }
            Change change = CHANGES.get(recipe.getId());
            if (isValidChange(recipe, change, access)) {
                if (value.applyAsLong(change.to()) * CHANGE_COUNT == value.applyAsLong(change.from())) {
                    keep.add(recipe);
                    continue;
                }
                badRatio = true;
                LOGGER.debug("Economie : change {} retire, le rapport de la config n'est pas 10", recipe.getId());
            } else {
                LOGGER.warn("Economie : recette retiree, elle fabrique une piece : {} (classe {})",
                        recipe.getId(), recipe.getClass().getName());
            }
            removed.add(recipe.getId());
        }
        if (badRatio) {
            LOGGER.warn("Economie : change a la table desactive : le rapport de la config n'est pas 10");
        }
        if (!dynamic.isEmpty()) {
            LOGGER.info("Economie : {} recettes au resultat dynamique : non verifiables au chargement, "
                    + "couvertes par le filet onCraftedBy", dynamic.size());
            LOGGER.debug("Economie : recettes au resultat dynamique {}", dynamic);
        }
        if (!removed.isEmpty()) {
            manager.replaceRecipes(keep);
        }
        return removed;
    }

    private static boolean isValidChange(Recipe<?> recipe, Change change, RegistryAccess access) {
        if (change == null || recipe.getClass() != ShapelessRecipe.class || recipe.getIngredients().size() != 1) {
            return false;
        }
        Ingredient ingredient = recipe.getIngredients().get(0);
        ItemStack[] choices = ingredient.getItems();
        ItemStack result = recipe.getResultItem(access);
        return choices.length == 1 && choices[0].is(change.from().item())
                && result.is(change.to().item()) && result.getCount() == CHANGE_COUNT;
    }

    /** Vrai si le résultat est une pièce ; une recette au résultat vide ou illisible est ajoutée à {@code dynamic}. */
    private static boolean producesCoin(Recipe<?> recipe, RegistryAccess access, List<ResourceLocation> dynamic) {
        ItemStack result;
        try {
            result = recipe.getResultItem(access);
        } catch (RuntimeException e) {
            dynamic.add(recipe.getId());
            return false;
        }
        if (result.isEmpty()) {
            dynamic.add(recipe.getId());
            return false;
        }
        return Coin.fromItem(result.getItem()).isPresent();
    }

    /**
     * Filet d'exécution : une pièce qui sort d'un écran de fabrication est-elle permise ? Oui seulement pour une
     * grille de fabrication (table, grille 2x2) dont toutes les recettes qui y donnent une pièce sont des
     * recettes de change. Une recette dynamique qui fabriquerait une pièce est ainsi refusée.
     * Tout autre écran (tailleur de pierre, forge, mod) est refusé : une pièce n'y a aucune origine permise.
     */
    @SuppressWarnings("unchecked")
    public static boolean craftAllowed(AbstractContainerMenu menu, Level level, RecipeManager recipes) {
        if (!(menu instanceof CraftingMenu || menu instanceof InventoryMenu)) {
            return false;
        }
        RecipeBookMenu<net.minecraft.world.inventory.CraftingContainer> grid =
                (RecipeBookMenu<net.minecraft.world.inventory.CraftingContainer>) menu;
        TransientCraftingContainer container =
                new TransientCraftingContainer(menu, grid.getGridWidth(), grid.getGridHeight());
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, menu.getSlot(i + 1).getItem().copy());
        }
        boolean change = false;
        for (Recipe<net.minecraft.world.inventory.CraftingContainer> recipe : recipes.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!recipe.matches(container, level)) {
                continue;
            }
            ItemStack result;
            try {
                result = recipe.assemble(container, level.registryAccess());
            } catch (RuntimeException e) {
                continue;
            }
            if (result.isEmpty() || Coin.fromItem(result.getItem()).isEmpty()) {
                continue;
            }
            if (!CHANGE_RECIPES.contains(recipe.getId())) {
                return false;
            }
            change = true;
        }
        return change;
    }
}
