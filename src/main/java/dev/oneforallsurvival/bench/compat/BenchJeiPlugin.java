package dev.oneforallsurvival.bench.compat;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.client.BenchClientState;
import dev.oneforallsurvival.bench.client.BenchFormat;
import dev.oneforallsurvival.repair.GalvanicRecipe;
import dev.oneforallsurvival.repair.RepairRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JEI : une catégorie « Établi d'ingénieur » bâtie sur le catalogue que le client a reçu, l'établi comme
 * catalyseur, et une catégorie « Bain galvanique » bâtie sur les recettes du bain (le bain comme catalyseur). Les recettes d'un objet géré ont quitté la table : JEI n'y montre donc plus de recette de
 * table pour lui, seulement celle de l'établi.
 *
 * <p>Toute référence à JEI reste dans cette classe : le mod se charge sans JEI, qui seul instancie le
 * plugin. Le catalogue arrive par paquets, parfois après le démarrage de JEI (serveur dédié, rechargement) :
 * un abonné de {@link BenchClientState} rafraîchit alors les recettes de la catégorie.
 */
@JeiPlugin
public final class BenchJeiPlugin implements IModPlugin {

    public static final RecipeType<BenchRecipe> TYPE =
            RecipeType.create(OneForAllSurvival.MODID, "engineer_bench", BenchRecipe.class);

    public static final RecipeType<GalvanicRecipe> GALVANIC_TYPE =
            RecipeType.create(OneForAllSurvival.MODID, "galvanic_bath", GalvanicRecipe.class);

    private static final Logger LOGGER = LoggerFactory.getLogger("oneforallsurvival-jei");
    private static final ResourceLocation UID = new ResourceLocation(OneForAllSurvival.MODID, "jei");
    /** Propriété de développement : journalise ce que JEI montre pour un objet géré. */
    private static final boolean CHECK = System.getProperty("oneforallsurvival.jeicheck") != null;

    private static final int COLUMNS = 4;
    private static final int ROWS = 3;
    private static final int SLOT = 18;
    private static final int WIDTH = COLUMNS * SLOT + 8 + 26 + 4;
    private static final int HEIGHT = ROWS * SLOT + 12;

    /** Ce que JEI montre actuellement dans la catégorie ; remplacé quand un autre catalogue arrive. */
    private static volatile List<BenchRecipe> shown = List.of();
    private static volatile IJeiRuntime runtime;
    /** Recettes déjà signalées comme tronquées : un seul avertissement chacune. */
    private static final java.util.Set<ResourceLocation> TRUNCATED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Runnable listener = this::onCatalogChanged;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()),
                new GalvanicCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<BenchRecipe> catalog = BenchClientState.catalog();
        shown = catalog;
        registration.addRecipes(TYPE, catalog);
        // Les recettes du bain sont des données ordinaires, déjà synchronisées avec le client.
        var level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(GALVANIC_TYPE,
                    level.getRecipeManager().getAllRecipesFor(RepairRegistry.GALVANIC_TYPE.get()));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(BenchRegistry.ENGINEER_BENCH_ITEM.get()), TYPE);
        registration.addRecipeCatalyst(new ItemStack(RepairRegistry.GALVANIC_BATH_ITEM.get()), GALVANIC_TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        BenchClientState.addCatalogListener(listener);
        // Le catalogue a pu arriver entre l'enregistrement des recettes et maintenant.
        if (BenchClientState.catalog() != shown) {
            onCatalogChanged();
        } else {
            check("démarrage de JEI");
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        BenchClientState.removeCatalogListener(listener);
        runtime = null;
    }

    /** Un autre catalogue est arrivé : on retire les anciennes recettes de JEI et on ajoute les nouvelles. */
    private void onCatalogChanged() {
        Minecraft.getInstance().execute(() -> {
            IJeiRuntime jei = runtime;
            if (jei == null) {
                return;
            }
            List<BenchRecipe> next = BenchClientState.catalog();
            List<BenchRecipe> old = shown;
            if (next == old) {
                return;
            }
            var manager = jei.getRecipeManager();
            if (!old.isEmpty()) {
                manager.hideRecipes(TYPE, old);
            }
            manager.addRecipes(TYPE, next);
            shown = next;
            LOGGER.info("JEI: catalogue de l'etabli rafraichi, {} recettes (generation {})", next.size(),
                    BenchClientState.generation());
            check("nouveau catalogue");
        });
    }

    /**
     * Dev : pour chaque objet fabriqué par une recette de l'établi, combien de recettes de table et d'établi
     * JEI montre-t-il ? Une violation est un objet géré pour lequel JEI montre encore une recette de table.
     */
    private static void check(String moment) {
        IJeiRuntime jei = runtime;
        if (!CHECK || jei == null || shown.isEmpty()) {
            return;
        }
        java.util.Set<Item> seen = new java.util.LinkedHashSet<>();
        int violations = 0;
        int missing = 0;
        List<String> examples = new ArrayList<>();
        for (BenchRecipe recipe : shown) {
            ItemStack result = recipe.result();
            if (!seen.add(result.getItem())) {
                continue;
            }
            IFocus<ItemStack> focus = jei.getJeiHelpers().getFocusFactory()
                    .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, result);
            long table = jei.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).limitFocus(List.of(focus))
                    .get().count();
            long bench = jei.getRecipeManager().createRecipeLookup(TYPE).limitFocus(List.of(focus)).get().count();
            if (table > 0) {
                violations++;
                if (examples.size() < 5) {
                    examples.add(result.getItem() + " (" + table + " table)");
                }
            }
            if (bench == 0) {
                missing++;
            }
        }
        LOGGER.info("JEI-CHECK ({}) {} objets distincts sur {} recettes d'etabli : {} avec une recette de table"
                + " (violations), {} sans recette d'etabli {}", moment, seen.size(), shown.size(), violations, missing,
                examples);
    }

    /** Les ingrédients d'une recette regroupés par ensemble d'objets acceptés, avec leur nombre. */
    private static Map<List<Item>, List<ItemStack>> grouped(BenchRecipe recipe) {
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
        Map<List<Item>, List<ItemStack>> out = new LinkedHashMap<>();
        for (Map.Entry<List<Item>, int[]> e : counts.entrySet()) {
            List<ItemStack> choices = new ArrayList<>();
            for (ItemStack s : first.get(e.getKey()).getItems()) {
                choices.add(s.copyWithCount(e.getValue()[0]));
            }
            out.put(e.getKey(), choices);
        }
        return out;
    }

    private static final class Category implements IRecipeCategory<BenchRecipe> {
        private final IDrawable icon;

        Category(IGuiHelper gui) {
            this.icon = gui.createDrawableItemStack(new ItemStack(BenchRegistry.ENGINEER_BENCH_ITEM.get()));
        }

        @Override
        public RecipeType<BenchRecipe> getRecipeType() {
            return TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable(BenchRegistry.ENGINEER_BENCH.get().getDescriptionId());
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BenchRecipe recipe, IFocusGroup focuses) {
            int i = 0;
            Map<List<Item>, List<ItemStack>> groups = grouped(recipe);
            if (groups.size() > COLUMNS * ROWS && TRUNCATED.add(recipe.id())) {
                LOGGER.warn("JEI: la recette {} a {} groupes d'ingredients, seuls {} sont montres", recipe.id(),
                        groups.size(), COLUMNS * ROWS);
            }
            for (List<ItemStack> choices : groups.values()) {
                if (i >= COLUMNS * ROWS) {
                    break;
                }
                builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % COLUMNS) * SLOT, 1 + (i / COLUMNS) * SLOT)
                        .addItemStacks(choices);
                i++;
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, COLUMNS * SLOT + 12, (ROWS * SLOT - SLOT) / 2 + 1)
                    .addItemStack(recipe.result());
        }

        @Override
        public void draw(BenchRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                double mouseY) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, Component.translatable("gui.oneforallsurvival.bench.time",
                    BenchFormat.duration(recipe.seconds())), 1, ROWS * SLOT + 3, 0xFF555555, false);
        }
    }

    /** La catégorie du bain : la pièce et le zinc à gauche, la pièce traitée à droite, durée et courant dessous. */
    private static final class GalvanicCategory implements IRecipeCategory<GalvanicRecipe> {
        private static final int G_WIDTH = 110;
        private static final int G_HEIGHT = 46;
        private final IDrawable icon;

        GalvanicCategory(IGuiHelper gui) {
            this.icon = gui.createDrawableItemStack(new ItemStack(RepairRegistry.GALVANIC_BATH_ITEM.get()));
        }

        @Override
        public RecipeType<GalvanicRecipe> getRecipeType() {
            return GALVANIC_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable(RepairRegistry.GALVANIC_BATH.get().getDescriptionId());
        }

        @Override
        public int getWidth() {
            return G_WIDTH;
        }

        @Override
        public int getHeight() {
            return G_HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, GalvanicRecipe recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).addIngredients(recipe.ingredient());
            List<ItemStack> zinc = new ArrayList<>();
            for (ItemStack stack : recipe.zinc().getItems()) {
                zinc.add(stack.copyWithCount(recipe.zincCount()));
            }
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 19).addItemStacks(zinc);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 80, 10).addItemStack(recipe.result());
        }

        @Override
        public void draw(GalvanicRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                double mouseY) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, Component.translatable("gui.oneforallsurvival.galvanic.jei.time",
                    BenchFormat.duration(Math.max(1, recipe.ticks() / 20))), 1, G_HEIGHT - 9, 0xFF555555, false);
            graphics.drawString(font, Component.translatable("gui.oneforallsurvival.galvanic.jei.energy",
                    recipe.energyPerTick()), 26, 14, 0xFF555555, false);
        }
    }
}
