package dev.oneforallsurvival.bench;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
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

    /** Plafond dur d'une durée imposée : une durée en ticks (x20) reste dans un int. */
    private static final int MAX_OVERRIDE_SECONDS = 86400;

    /**
     * L'établi lui-même reste toujours prenable à la main, quelle que soit la configuration : vider
     * {@code tableAllowedItems} ne doit pas verrouiller sa propre recette.
     */
    public static final ResourceLocation ALWAYS_ON_TABLE = new ResourceLocation("oneforallsurvival", "engineer_bench");

    /** Une recette qu'on ne peut pas payer d'avance, restée sur la table. */
    public record Special(ResourceLocation id, String recipeClass, String reason) {}

    private static volatile BenchCatalog current = new BenchCatalog(List.of(), List.of());

    /**
     * Compteur de catalogues installés. Il monte à chaque installation (donc à chaque /reload) : un
     * client qui a reçu le catalogue d'une autre génération doit le recevoir de nouveau.
     */
    private static volatile int generation = 1;

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

    /** Génération du catalogue courant : change à chaque {@link #install}. */
    public static int generation() {
        return generation;
    }

    /** Un catalogue sans recette spéciale, sorti d'une liste de recettes (essais). */
    public static BenchCatalog of(List<BenchRecipe> recipes) {
        return new BenchCatalog(recipes, List.of());
    }

    /**
     * Installe un catalogue et passe à une nouvelle génération.
     *
     * @return le catalogue remplacé
     */
    public static synchronized BenchCatalog install(BenchCatalog catalog) {
        BenchCatalog previous = current;
        current = catalog;
        generation++;
        return previous;
    }

    public Collection<BenchRecipe> recipes() {
        return Collections.unmodifiableCollection(byId.values());
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
        install(extract(manager, event.getRegistryAccess(), BenchConfig.managedMods(),
                BenchConfig.tableAllowedItems(), BenchConfig.excludedItems(), BenchOverrides.current(),
                BenchConfig.timeParams()));
        LOGGER.info("Etabli : {} recettes visibles au catalogue, {} recettes speciales laissees sur la table",
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
        return !stays(key, tableAllowed) && mods.contains(key.getNamespace());
    }

    /** Vrai si l'objet reste sur la table : la liste de la configuration, plus l'exemption de l'établi. */
    private static boolean stays(ResourceLocation key, Set<ResourceLocation> tableAllowed) {
        return key.equals(ALWAYS_ON_TABLE) || tableAllowed.contains(key);
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

    /**
     * Sort du manager les recettes gérées et renvoie le catalogue correspondant.
     *
     * @param excluded  objets que l'établi refuse de fabriquer : leur recette quitte la table, sans
     *                  entrer au catalogue visible (comme {@code hidden} d'une surcharge)
     * @param overrides surcharges de données, par résultat
     */
    public static BenchCatalog extract(RecipeManager manager, RegistryAccess access, Set<String> mods,
            Set<ResourceLocation> tableAllowed, Set<ResourceLocation> excluded,
            Map<ResourceLocation, List<BenchOverrides.Surcharge>> overrides, BenchTimes.Params params) {
        List<Recipe<?>> keep = new ArrayList<>();
        List<BenchRecipe> moved = new ArrayList<>();
        List<Special> specials = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (isManaged(recipe, access, mods, tableAllowed)) {
                moved.add(toBench(recipe, access));
                continue;
            }
            keep.add(recipe);
            Optional<Special> special = specialOf(recipe, access, mods);
            special.ifPresent(specials::add);
            // Une recette de mod géré laissée sur la table doit se voir : un futur mod peut en ajouter.
            if (special.isPresent() && hasManagedResult(recipe, access, mods, tableAllowed)) {
                LOGGER.warn("Etabli : recette speciale laissee sur la table, {} (classe {}) : {}",
                        recipe.getId(), recipe.getClass().getName(), special.get().reason());
            }
        }
        manager.replaceRecipes(keep);
        moved.sort(Comparator.comparing(r -> r.id().toString()));
        specials.sort(Comparator.comparing(s -> s.id().toString()));
        return new BenchCatalog(finish(moved, excluded, overrides, params), specials);
    }

    private static boolean hasManagedResult(Recipe<?> recipe, RegistryAccess access, Set<String> mods,
            Set<ResourceLocation> tableAllowed) {
        ItemStack result = resultOf(recipe, access);
        if (result.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(result.getItem());
        return !stays(key, tableAllowed) && mods.contains(key.getNamespace());
    }

    private static ResourceLocation resultKey(BenchRecipe recipe) {
        return BuiltInRegistries.ITEM.getKey(recipe.result().getItem());
    }

    /** Applique surcharges et exclusions, calcule les durées, ne garde que les recettes visibles. */
    private static List<BenchRecipe> finish(List<BenchRecipe> moved, Set<ResourceLocation> excluded,
            Map<ResourceLocation, List<BenchOverrides.Surcharge>> overrides, BenchTimes.Params params) {
        Map<String, Integer> fixed = new HashMap<>();
        Set<ResourceLocation> hidden = new HashSet<>();
        Set<ResourceLocation> seenResults = new HashSet<>();
        List<BenchRecipe> recipes = new ArrayList<>(moved.size());
        for (BenchRecipe recipe : moved) {
            ResourceLocation result = resultKey(recipe);
            seenResults.add(result);
            if (excluded.contains(result)) {
                hidden.add(recipe.id());
            }
            for (BenchOverrides.Surcharge o : overrides.getOrDefault(result, List.of())) {
                if (o.ingredients() != null) {
                    recipe = new BenchRecipe(recipe.id(), recipe.result(), o.ingredients(), recipe.category(), 0);
                }
                if (o.seconds() != null) {
                    fixed.put(recipe.id().toString(), Math.min(o.seconds(), MAX_OVERRIDE_SECONDS));
                }
                if (o.hidden() != null) {
                    if (o.hidden()) {
                        hidden.add(recipe.id());
                    } else {
                        hidden.remove(recipe.id());
                    }
                }
            }
            recipes.add(recipe);
        }
        for (Map.Entry<ResourceLocation, List<BenchOverrides.Surcharge>> entry : overrides.entrySet()) {
            if (!seenResults.contains(entry.getKey())) {
                for (BenchOverrides.Surcharge o : entry.getValue()) {
                    LOGGER.warn("Etabli : surcharge {} ignoree, aucune recette de l'etabli ne fabrique {}",
                            o.file(), entry.getKey());
                }
            }
        }
        Map<String, Integer> times = BenchTimes.compute(slotsOf(recipes), params, fixed);
        List<BenchRecipe> visible = new ArrayList<>();
        for (BenchRecipe recipe : recipes) {
            if (!hidden.contains(recipe.id())) {
                visible.add(new BenchRecipe(recipe.id(), recipe.result(), recipe.ingredients(), recipe.category(),
                        times.get(recipe.id().toString())));
            }
        }
        if (!hidden.isEmpty()) {
            LOGGER.info("Etabli : {} recettes cachees (exclusions et surcharges)", hidden.size());
        }
        return visible;
    }

    /** Poids de rareté : COMMON 0, UNCOMMON 2, RARE 5, EPIC 10. */
    static int rarityWeight(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0;
            case UNCOMMON -> 2;
            case RARE -> 5;
            case EPIC -> 10;
        };
    }

    /**
     * Les ingrédients de chaque recette, vus par le calcul de durée. Pour un ingrédient : la rareté la
     * plus basse parmi ses objets, et toutes les recettes du catalogue qui fabriquent l'un d'eux. Aucun
     * choix d'ordre : le calcul prend le moins cher, le résultat ne dépend donc que des données.
     */
    private static Map<String, List<BenchTimes.Slot>> slotsOf(List<BenchRecipe> recipes) {
        Map<Item, List<String>> makers = new HashMap<>();
        for (BenchRecipe recipe : recipes) {
            makers.computeIfAbsent(recipe.result().getItem(), k -> new ArrayList<>()).add(recipe.id().toString());
        }
        Map<String, List<BenchTimes.Slot>> slots = new LinkedHashMap<>();
        for (BenchRecipe recipe : recipes) {
            List<BenchTimes.Slot> list = new ArrayList<>(recipe.ingredients().size());
            for (Ingredient ingredient : recipe.ingredients()) {
                list.add(slotOf(ingredient, makers));
            }
            slots.put(recipe.id().toString(), list);
        }
        return slots;
    }

    private static BenchTimes.Slot slotOf(Ingredient ingredient, Map<Item, List<String>> makers) {
        int weight = Integer.MAX_VALUE;
        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            Item item = stack.getItem();
            weight = Math.min(weight, rarityWeight(item.getRarity(new ItemStack(item))));
            ids.addAll(makers.getOrDefault(item, List.of()));
        }
        return new BenchTimes.Slot(weight == Integer.MAX_VALUE ? 0 : weight, List.copyOf(ids));
    }

    /** Recette brute : la durée est calculée ensuite, une fois toutes les recettes connues. */
    private static BenchRecipe toBench(Recipe<?> recipe, RegistryAccess access) {
        ItemStack result = resultOf(recipe, access).copy();
        if (recipe instanceof SmithingTransformRecipe smithing) {
            return new BenchRecipe(recipe.getId(), result, smithingIngredients(smithing), "smithing", 0);
        }
        List<Ingredient> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                ingredients.add(ingredient);
            }
        }
        String category = recipe.getType() == RecipeType.STONECUTTING ? "stonecutting" : "crafting";
        return new BenchRecipe(recipe.getId(), result, ingredients, category, 0);
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
