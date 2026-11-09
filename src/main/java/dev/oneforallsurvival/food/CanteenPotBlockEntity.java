package dev.oneforallsurvival.food;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

/**
 * La marmite de cantine : le Cooking Pot de Farmer's Delight, mais électrique et en plus grand. Huit cases : 0 à 5 les
 * ingrédients, 6 le récipient (le bol d'un ragoût), 7 la sortie. Elle cuit les recettes du Cooking Pot, sans le feu
 * dessous, et une fournée donne plusieurs fois le résultat ({@link FoodConfig#canteenPotPortions()}) pour un seul jeu
 * d'ingrédients.
 *
 * <p>Chaque portion exige son récipient, présent d'avance : la fournée n'est lancée que si tous y sont, et ils se paient
 * en fin de cuisson avec les ingrédients (un de chaque). Les restes d'ingrédients (le seau d'un lait, une bouteille)
 * sont jetés au-dessus du bloc, comme le fait le Cooking Pot. Aucune expérience n'est donnée.
 *
 * <p>La progression est gardée sans courant, en panne, faute de récipients et sortie pleine ; elle ne repart de zéro que
 * si la recette change, ce que la machine voit à chaque tick. La recette est mise en cache jusqu'à ce qu'une case
 * d'ingrédient change ou que le gestionnaire de recettes ne soit plus le même (rechargement des données). Un ingrédient
 * avarié n'entre pas, et la machine ne cuit pas celui qui l'est devenu en route. Les faces : le dessus insère les
 * ingrédients, les côtés le récipient, le dessous extrait la sortie. Le contenu est jeté quand le bloc disparaît.
 */
public final class CanteenPotBlockEntity extends ElectricMachineBlockEntity {

    public static final int SLOT_INPUT_FIRST = 0;
    /** Nombre de cases d'ingrédients, celui du Cooking Pot. */
    public static final int INPUTS = CookingPotRecipe.INPUT_SLOTS;
    public static final int SLOT_CONTAINER = 6;
    public static final int SLOT_OUTPUT = 7;
    public static final int SLOTS = 8;
    public static final int CAPACITY = 6000;
    public static final int MAX_RECEIVE = 200;

    private static final String TAG_ITEMS = "items";
    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_RECIPE = "recipe";

    /** Cases lues par le menu : progression, durée, énergie, capacité, état, récipients exigés, récipients manquants. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_CAPACITY = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_CONTAINERS_NEEDED = 5;
    public static final int DATA_CONTAINERS_MISSING = 6;
    public static final int DATA_COUNT = 7;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot < INPUTS) {
                return !FoodOutput.isSpoiled(level, stack) && anyRecipe(r -> usesIngredient(r, stack));
            }
            return slot == SLOT_CONTAINER && anyRecipe(r -> ItemStack.isSameItem(containerOf(r), stack));
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot < INPUTS) {
                // Le cache tombe, mais la progression, non : un clic de souris vide la case un instant
                // (SlotItemHandler.getMaxStackSize) sans que la recette ait vraiment changé.
                cacheValid = false;
            }
        }
    };

    /** Les six cases d'ingrédients vues comme un conteneur, pour chercher la recette. */
    private final RecipeWrapper ingredients = new RecipeWrapper(new RangedWrapper(items, SLOT_INPUT_FIRST, INPUTS));

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> {
                    CookingPotRecipe recipe = currentRecipe();
                    yield recipe == null ? 0 : recipe.getCookTime();
                }
                case DATA_ENERGY -> energy().getEnergyStored();
                case DATA_CAPACITY -> energy().getMaxEnergyStored();
                case DATA_STATE -> state().ordinal();
                case DATA_CONTAINERS_NEEDED -> containersNeeded();
                case DATA_CONTAINERS_MISSING -> containersMissing();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private LazyOptional<IItemHandler> allCap = LazyOptional.of(() -> items);
    private LazyOptional<IItemHandler> topCap = LazyOptional.of(this::topFace);
    private LazyOptional<IItemHandler> sideCap = LazyOptional.of(this::sideFace);
    private LazyOptional<IItemHandler> bottomCap = LazyOptional.of(this::bottomFace);

    /** Ticks de travail de la cuisson en cours. */
    private int progress;
    /** L'identifiant de la recette à la dernière lecture : sert à voir si la recette a changé. */
    @Nullable
    private ResourceLocation trackedRecipe;
    /** Recette des ingrédients posés ; non sauvegardée. {@code cacheValid} : le cache dit vrai pour les cases et les données. */
    @Nullable
    private CookingPotRecipe cachedRecipe;
    private boolean cacheValid;
    @Nullable
    private RecipeManager cachedManager;

    public CanteenPotBlockEntity(BlockPos pos, BlockState state) {
        super(FoodRegistry.CANTEEN_POT_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    /** Les huit cases, pour le menu, les tests et le bloc. */
    public IItemHandlerModifiable inventory() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    public int progress() {
        return progress;
    }

    // ---------------------------------------------------------------------------------------------
    // Recette
    // ---------------------------------------------------------------------------------------------

    private boolean anyRecipe(Predicate<CookingPotRecipe> test) {
        if (level == null) {
            return true;
        }
        for (CookingPotRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
            if (test.test(recipe)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesIngredient(CookingPotRecipe recipe, ItemStack stack) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * La recette des ingrédients posés. Le cache vaut tant que les cases et le gestionnaire de recettes sont les mêmes :
     * un rechargement des données installe un autre gestionnaire. C'est aussi ici, une fois par tick (via
     * {@link #energyPerTick()}), que la progression repart de zéro si la recette n'est plus la même.
     */
    @Nullable
    private CookingPotRecipe currentRecipe() {
        if (level == null) {
            return null;
        }
        RecipeManager manager = level.getRecipeManager();
        if (!cacheValid || manager != cachedManager) {
            cachedRecipe = lookup();
            cachedManager = manager;
            cacheValid = true;
        }
        ResourceLocation id = cachedRecipe == null ? null : cachedRecipe.getId();
        if (!Objects.equals(id, trackedRecipe)) {
            trackedRecipe = id;
            progress = 0;
        }
        return cachedRecipe;
    }

    @Nullable
    private CookingPotRecipe lookup() {
        boolean empty = true;
        for (int slot = 0; slot < INPUTS; slot++) {
            empty &= items.getStackInSlot(slot).isEmpty();
        }
        if (empty) {
            return null;
        }
        return level.getRecipeManager().getRecipeFor(ModRecipeTypes.COOKING.get(), ingredients, level).orElse(null);
    }

    /** Ce que la fournée produit : le résultat de la recette, autant de fois que de portions. */
    private ItemStack batchResult(CookingPotRecipe recipe) {
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        result.setCount(result.getCount() * FoodConfig.canteenPotPortions());
        return result;
    }

    /** Le récipient de la recette : celui qu'elle déclare, sinon le reste de son résultat (le bol d'un ragoût). */
    private ItemStack containerOf(CookingPotRecipe recipe) {
        ItemStack container = recipe.getOutputContainer();
        if (container.isEmpty() && level != null) {
            container = recipe.getResultItem(level.registryAccess()).getCraftingRemainingItem();
        }
        return container;
    }

    /** Combien de récipients la fournée en cours exige : autant que de portions, aucun si la recette n'en veut pas. */
    public int containersNeeded() {
        CookingPotRecipe recipe = currentRecipe();
        if (recipe == null || containerOf(recipe).isEmpty()) {
            return 0;
        }
        return batchResult(recipe).getCount();
    }

    private int containersMissing() {
        CookingPotRecipe recipe = currentRecipe();
        int needed = containersNeeded();
        if (recipe == null || needed == 0) {
            return 0;
        }
        ItemStack held = items.getStackInSlot(SLOT_CONTAINER);
        int have = ItemStack.isSameItem(held, containerOf(recipe)) ? held.getCount() : 0;
        return Math.max(0, needed - have);
    }

    private boolean anyIngredientSpoiled() {
        for (int slot = 0; slot < INPUTS; slot++) {
            if (FoodOutput.isSpoiled(level, items.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Travail
    // ---------------------------------------------------------------------------------------------

    @Override
    protected boolean wantsToWork() {
        CookingPotRecipe recipe = currentRecipe();
        if (recipe == null || anyIngredientSpoiled() || containersMissing() > 0) {
            return false;
        }
        return FoodOutput.fits(items.getStackInSlot(SLOT_OUTPUT), batchResult(recipe));
    }

    @Override
    protected int energyPerTick() {
        return currentRecipe() == null ? 0 : FoodConfig.canteenPotEnergy();
    }

    @Override
    protected void workTick() {
        CookingPotRecipe recipe = currentRecipe();
        if (recipe == null) {
            return;
        }
        progress++;
        setChanged();
        if (progress >= recipe.getCookTime()) {
            finish(recipe);
        }
    }

    /**
     * La cuisson est finie : un de chaque ingrédient est payé, les récipients aussi, la fournée arrive en sortie et les
     * restes d'ingrédients tombent au-dessus du bloc.
     */
    private void finish(CookingPotRecipe recipe) {
        ItemStack result = batchResult(recipe);
        int containers = containersNeeded();
        for (int slot = 0; slot < INPUTS; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = stack.getCraftingRemainingItem();
            items.extractItem(slot, 1, false);
            if (!remainder.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                        worldPosition.getZ() + 0.5, remainder);
            }
        }
        if (containers > 0) {
            items.extractItem(SLOT_CONTAINER, containers, false);
        }
        FoodOutput.place(items, SLOT_OUTPUT, result, level);
        progress = 0;
    }

    /** Jette tout le contenu au sol et vide les cases : appelé quand le bloc disparaît. */
    void dropContents(Level world, BlockPos pos) {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                items.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Faces
    // ---------------------------------------------------------------------------------------------

    private IItemHandler topFace() {
        return new FacedSlots(items, SLOT_INPUT_FIRST, INPUTS, true, false);
    }

    private IItemHandler sideFace() {
        return new FacedSlots(items, SLOT_CONTAINER, SLOT_CONTAINER + 1, true, false);
    }

    private IItemHandler bottomFace() {
        return new FacedSlots(items, SLOT_OUTPUT, SLOT_OUTPUT + 1, false, true);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) {
                return allCap.cast();
            }
            return switch (side) {
                case DOWN -> bottomCap.cast();
                case UP -> topCap.cast();
                default -> sideCap.cast();
            };
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        allCap.invalidate();
        topCap.invalidate();
        sideCap.invalidate();
        bottomCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        allCap = LazyOptional.of(() -> items);
        topCap = LazyOptional.of(this::topFace);
        sideCap = LazyOptional.of(this::sideFace);
        bottomCap = LazyOptional.of(this::bottomFace);
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ITEMS, items.serializeNBT());
        tag.putInt(TAG_PROGRESS, progress);
        // La recette suivie accompagne la progression : sans elle, le premier tick après un rechargement la croirait changée.
        if (trackedRecipe != null) {
            tag.putString(TAG_RECIPE, trackedRecipe.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        progress = tag.getInt(TAG_PROGRESS);
        trackedRecipe = tag.contains(TAG_RECIPE) ? ResourceLocation.tryParse(tag.getString(TAG_RECIPE)) : null;
        cacheValid = false;
    }
}
