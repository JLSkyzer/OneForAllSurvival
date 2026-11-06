package dev.oneforallsurvival.bench;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;

/**
 * Catalogue de l'établi : les recettes des mods d'ingénierie, sorties du RecipeManager. Table,
 * assembleurs mécaniques, livre de recettes et JEI ne les voient donc plus, sans mixin.
 *
 * <p>Mécanisme : on garde le RecipeManager de chaque rechargement (AddReloadListenerEvent), puis on
 * le purge au TagsUpdatedEvent de cause SERVER_DATA_LOAD, c'est-à-dire une fois toutes les recettes
 * chargées (datapacks, CraftTweaker compris) et avant que le serveur les envoie aux clients.
 */
public final class BenchCatalog {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Une recette qu'on ne peut pas payer d'avance, restée sur la table. */
    public record Special(ResourceLocation id, String recipeClass, String reason) {}

    private static volatile BenchCatalog current = new BenchCatalog(List.of(), List.of());

    /** Le RecipeManager du rechargement en cours, et le dernier déjà traité. */
    private static RecipeManager pending;
    private static RecipeManager processed;

    private final Map<ResourceLocation, BenchRecipe> byId = new LinkedHashMap<>();
    private final List<Special> specials;

    private BenchCatalog(List<BenchRecipe> recipes, List<Special> specials) {
        for (BenchRecipe r : recipes) {
            byId.put(r.id(), r);
        }
        this.specials = List.copyOf(specials);
    }

    public static BenchCatalog current() {
        return current;
    }

    public Collection<BenchRecipe> recipes() {
        return byId.values();
    }

    public Optional<BenchRecipe> byId(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Recettes de table, de tailleur ou de forgeron des mods gérés, laissées sur la table. */
    public List<Special> specials() {
        return specials;
    }

    // --- Événements -------------------------------------------------------------------------

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        pending = event.getServerResources().getRecipeManager();
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        RecipeManager manager = pending;
        if (manager == null || manager == processed) {
            return;
        }
        processed = manager;
        current = extract(manager, event.getRegistryAccess(), BenchConfig.managedMods(),
                BenchConfig.tableAllowedItems());
        LOGGER.info("Etabli : {} recettes sorties du RecipeManager, {} recettes speciales laissees sur la table",
                current.byId.size(), current.specials.size());
        Map<String, Integer> parMod = new TreeMap<>();
        for (BenchRecipe r : current.byId.values()) {
            parMod.merge(BuiltInRegistries.ITEM.getKey(r.result().getItem()).getNamespace(), 1, Integer::sum);
        }
        LOGGER.info("Etabli : recettes par mod du resultat {}", parMod);
    }

    // --- Règles -----------------------------------------------------------------------------

    /** Table, tailleur de pierre ou forgeron : les seuls types qu'on déplace. */
    private static boolean isConcerned(Recipe<?> recipe) {
        RecipeType<?> type = recipe.getType();
        return type == RecipeType.CRAFTING || type == RecipeType.STONECUTTING || type == RecipeType.SMITHING;
    }

    /**
     * Les classes de CraftTweaker pour la table : sous-classes de ShapedRecipe et ShapelessRecipe, à
     * ingrédients et résultat statiques. Mesuré : sans elles, toute recette de table ajoutée par un
     * script au résultat d'un mod géré resterait sur la table.
     */
    private static final Set<String> CRAFTTWEAKER_CLASSES = Set.of(
            "com.blamejared.crafttweaker.api.recipe.type.CTShapedRecipe",
            "com.blamejared.crafttweaker.api.recipe.type.CTShapelessRecipe");

    /**
     * Les classes dont ingrédients et résultat sont statiques. Classe exacte, sauf CraftTweaker : une
     * autre sous-classe peut fabriquer son résultat dynamiquement, on ne la fige pas en liste fixe.
     */
    private static boolean hasStandardClass(Recipe<?> recipe) {
        Class<?> c = recipe.getClass();
        return c == ShapedRecipe.class || c == ShapelessRecipe.class || c == StonecutterRecipe.class
                || c == SmithingTransformRecipe.class || CRAFTTWEAKER_CLASSES.contains(c.getName());
    }

    private static ItemStack resultOf(Recipe<?> recipe, RegistryAccess access) {
        try {
            return recipe.getResultItem(access);
        } catch (RuntimeException e) {
            LOGGER.warn("Etabli : resultat illisible pour {}", recipe.getId(), e);
            return ItemStack.EMPTY;
        }
    }

    public static boolean isManaged(Recipe<?> recipe, RegistryAccess access, Set<String> mods) {
        return isManaged(recipe, access, mods, BenchConfig.tableAllowedItems());
    }

    /**
     * Une recette est gérée si son résultat appartient à un mod géré, quel que soit le mod qui la
     * déclare : une recette vanilla au résultat géré l'est, une recette d'un mod géré au résultat
     * d'un autre mod ne l'est pas. Les recettes spéciales et les objets de {@code tableAllowed}
     * restent sur la table.
     */
    public static boolean isManaged(Recipe<?> recipe, RegistryAccess access, Set<String> mods,
            Set<ResourceLocation> tableAllowed) {
        if (!isConcerned(recipe) || !hasStandardClass(recipe)) {
            return false;
        }
        ItemStack result = resultOf(recipe, access);
        if (result.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(result.getItem());
        return !tableAllowed.contains(key) && mods.contains(key.getNamespace());
    }

    /** Recette concernant un mod géré (par son identifiant ou son résultat) mais non déplaçable. */
    private static Optional<Special> specialOf(Recipe<?> recipe, RegistryAccess access, Set<String> mods) {
        if (!isConcerned(recipe)) {
            return Optional.empty();
        }
        ItemStack result = resultOf(recipe, access);
        boolean standard = hasStandardClass(recipe);
        if (standard && !result.isEmpty()) {
            return Optional.empty();
        }
        boolean touchesMod = mods.contains(recipe.getId().getNamespace())
                || (!result.isEmpty() && mods.contains(BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace()));
        if (!touchesMod) {
            return Optional.empty();
        }
        String reason = !standard ? "classe non standard" : "resultat vide";
        return Optional.of(new Special(recipe.getId(), recipe.getClass().getName(), reason));
    }

    // --- Construction -----------------------------------------------------------------------

    /** Sort du manager les recettes gérées et renvoie le catalogue correspondant. */
    static BenchCatalog extract(RecipeManager manager, RegistryAccess access, Set<String> mods,
            Set<ResourceLocation> tableAllowed) {
        List<Recipe<?>> keep = new ArrayList<>();
        List<BenchRecipe> moved = new ArrayList<>();
        List<Special> specials = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (isManaged(recipe, access, mods, tableAllowed)) {
                moved.add(toBench(recipe, access));
                continue;
            }
            keep.add(recipe);
            specialOf(recipe, access, mods).ifPresent(specials::add);
        }
        manager.replaceRecipes(keep);
        moved.sort(Comparator.comparing(r -> r.id().toString()));
        specials.sort(Comparator.comparing(s -> s.id().toString()));
        return new BenchCatalog(moved, specials);
    }

    /** Durée fixe à 10 s pour l'instant : la formule de temps vient avec la tâche suivante. */
    private static BenchRecipe toBench(Recipe<?> recipe, RegistryAccess access) {
        ItemStack result = resultOf(recipe, access).copy();
        if (recipe instanceof SmithingTransformRecipe smithing) {
            return new BenchRecipe(recipe.getId(), result, smithingIngredients(smithing), "smithing", 10);
        }
        List<Ingredient> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                ingredients.add(ingredient);
            }
        }
        String category = recipe.getType() == RecipeType.STONECUTTING ? "stonecutting" : "crafting";
        return new BenchRecipe(recipe.getId(), result, ingredients, category, 10);
    }

    /**
     * La forge de 1.20.1 garde modèle, base et ajout dans des champs privés. Le sérialiseur réseau
     * les écrit dans cet ordre, puis le résultat : on relit les trois premiers sans accès réfléchi.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Ingredient> smithingIngredients(SmithingTransformRecipe recipe) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ((RecipeSerializer) recipe.getSerializer()).toNetwork(buf, recipe);
            List<Ingredient> out = new ArrayList<>(3);
            for (int i = 0; i < 3; i++) {
                Ingredient ingredient = Ingredient.fromNetwork(buf);
                if (!ingredient.isEmpty()) {
                    out.add(ingredient);
                }
            }
            return out;
        } finally {
            buf.release();
        }
    }
}
