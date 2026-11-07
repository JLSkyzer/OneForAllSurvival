package dev.oneforallsurvival.food;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Le séchoir électrique : un aliment, du sel si l'on veut, du courant, et la ration ressort. Trois cases : 0 l'aliment,
 * 1 le sel, 2 la sortie.
 *
 * <p>Sans sel, l'aliment devient une ration séchée (elle périme lentement) ; avec du sel dans la case, une ration salée
 * (elle ne périme jamais) et un sel est consommé. L'aliment et le sel ne se paient qu'à la fin du séchage, et le résultat
 * est un objet neuf, sans date. La progression est gardée sans courant, en panne et sortie pleine ; elle ne repart de
 * zéro que si l'aliment change réellement, ce que la machine voit à chaque tick. La recette en cours est mise en cache,
 * jusqu'à ce que la case de l'aliment change ou que le gestionnaire de recettes ne soit plus le même (rechargement
 * des données).
 *
 * <p>Un aliment avarié n'entre pas, et la machine ne sèche pas celui qui l'est devenu en route : sécher effacerait
 * sa date et le ferait passer pour frais. Les faces ne donnent accès qu'à leur case : le dessous extrait la sortie, le
 * dessus insère l'aliment, les côtés insèrent le sel. Le contenu est jeté quand le bloc disparaît.
 */
public final class DryerBlockEntity extends ElectricMachineBlockEntity {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_SALT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOTS = 3;
    public static final int CAPACITY = 3000;
    public static final int MAX_RECEIVE = 100;

    private static final String TAG_ITEMS = "items";
    private static final String TAG_PROGRESS = "progress";

    /** Cases lues par le menu : progression, durée, énergie, capacité, état. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_CAPACITY = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_COUNT = 5;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return switch (slot) {
                case SLOT_INPUT -> !isSpoiled(stack) && anyRecipe(r -> r.acceptsInput(stack));
                case SLOT_SALT -> stack.is(FoodTags.SALT);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == SLOT_INPUT) {
                // Le cache tombe, mais la progression, non : un clic de souris vide la case un instant
                // (SlotItemHandler.getMaxStackSize) sans que l'aliment ait vraiment changé.
                cacheValid = false;
            }
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> {
                    DryingRecipe recipe = currentRecipe();
                    yield recipe == null ? 0 : recipe.ticks();
                }
                case DATA_ENERGY -> energy().getEnergyStored();
                case DATA_CAPACITY -> energy().getMaxEnergyStored();
                case DATA_STATE -> state().ordinal();
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
    private LazyOptional<IItemHandler> topCap = LazyOptional.of(() -> new Face(items, SLOT_INPUT, true, false));
    private LazyOptional<IItemHandler> sideCap = LazyOptional.of(() -> new Face(items, SLOT_SALT, true, false));
    private LazyOptional<IItemHandler> bottomCap = LazyOptional.of(() -> new Face(items, SLOT_OUTPUT, false, true));

    /** Ticks de travail du séchage en cours. */
    private int progress;
    /** L'objet dans la case de l'aliment à la dernière lecture : sert à voir si l'aliment a changé. */
    @Nullable
    private Item trackedInput;
    /** Recette de l'aliment posé ; non sauvegardée. {@code cacheValid} : le cache dit vrai pour l'aliment et les données. */
    @Nullable
    private DryingRecipe cachedRecipe;
    private boolean cacheValid;
    @Nullable
    private RecipeManager cachedManager;

    public DryerBlockEntity(BlockPos pos, BlockState state) {
        super(FoodRegistry.DRYER_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    /** Les trois cases, pour le menu, les tests et le bloc. */
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

    /** Un déchet, ou un aliment dont la date est passée : ce n'est plus une entrée. */
    private boolean isSpoiled(ItemStack stack) {
        if (stack.is(FoodRegistry.SPOILED_FOOD.get())) {
            return true;
        }
        return level != null && FoodStamp.state(stack, FoodStamp.now(level)) == DecayState.SPOILED;
    }

    private boolean anyRecipe(Predicate<DryingRecipe> test) {
        if (level == null) {
            return true;
        }
        for (DryingRecipe recipe : level.getRecipeManager().getAllRecipesFor(FoodRegistry.DRYING_TYPE.get())) {
            if (test.test(recipe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * La recette de l'aliment posé. Le cache vaut tant que l'aliment et le gestionnaire de recettes sont les mêmes :
     * un rechargement des données installe un autre gestionnaire. C'est aussi ici, une fois par tick (via
     * {@link #energyPerTick()}), que la progression repart de zéro si l'objet de la case a changé.
     */
    @Nullable
    private DryingRecipe currentRecipe() {
        if (level == null) {
            return null;
        }
        ItemStack input = items.getStackInSlot(SLOT_INPUT);
        Item now = input.isEmpty() ? null : input.getItem();
        if (now != trackedInput) {
            trackedInput = now;
            progress = 0;
            cacheValid = false;
        }
        RecipeManager manager = level.getRecipeManager();
        if (!cacheValid || manager != cachedManager) {
            cachedRecipe = lookup(input);
            cachedManager = manager;
            cacheValid = true;
        }
        return cachedRecipe;
    }

    @Nullable
    private DryingRecipe lookup(ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        for (DryingRecipe recipe : level.getRecipeManager().getAllRecipesFor(FoodRegistry.DRYING_TYPE.get())) {
            if (recipe.acceptsInput(input)) {
                return recipe;
            }
        }
        return null;
    }

    /** Du sel est dans sa case : la prochaine ration sera salée. */
    private boolean salted() {
        ItemStack salt = items.getStackInSlot(SLOT_SALT);
        return !salt.isEmpty() && salt.is(FoodTags.SALT);
    }

    private boolean outputFits(ItemStack result) {
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        return out.isEmpty() || (ItemStack.isSameItemSameTags(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize());
    }

    // ---------------------------------------------------------------------------------------------
    // Travail
    // ---------------------------------------------------------------------------------------------

    @Override
    protected boolean wantsToWork() {
        DryingRecipe recipe = currentRecipe();
        if (recipe == null || isSpoiled(items.getStackInSlot(SLOT_INPUT))) {
            return false;
        }
        return outputFits(recipe.resultFor(salted()));
    }

    @Override
    protected int energyPerTick() {
        return currentRecipe() == null ? 0 : FoodConfig.dryerEnergy();
    }

    @Override
    protected void workTick() {
        DryingRecipe recipe = currentRecipe();
        if (recipe == null) {
            return;
        }
        progress++;
        setChanged();
        if (progress >= recipe.ticks()) {
            finish(recipe);
        }
    }

    /** Le séchage est fini : l'aliment (et le sel, s'il y en a) est payé, la ration neuve arrive en sortie. */
    private void finish(DryingRecipe recipe) {
        boolean salted = salted();
        ItemStack result = recipe.resultFor(salted);
        items.extractItem(SLOT_INPUT, 1, false);
        if (salted) {
            items.extractItem(SLOT_SALT, 1, false);
        }
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            items.setStackInSlot(SLOT_OUTPUT, result);
        } else {
            ItemStack grown = out.copy();
            grown.grow(result.getCount());
            items.setStackInSlot(SLOT_OUTPUT, grown);
        }
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

    /** Une face ne voit qu'une case : elle y insère, en extrait, ou les deux selon son rôle. */
    private static final class Face extends RangedWrapper {
        private final boolean canInsert;
        private final boolean canExtract;

        Face(IItemHandlerModifiable handler, int slot, boolean canInsert, boolean canExtract) {
            super(handler, slot, slot + 1);
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return canInsert ? super.insertItem(slot, stack, simulate) : stack;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return canExtract ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return canInsert && super.isItemValid(slot, stack);
        }
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
        topCap = LazyOptional.of(() -> new Face(items, SLOT_INPUT, true, false));
        sideCap = LazyOptional.of(() -> new Face(items, SLOT_SALT, true, false));
        bottomCap = LazyOptional.of(() -> new Face(items, SLOT_OUTPUT, false, true));
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ITEMS, items.serializeNBT());
        tag.putInt(TAG_PROGRESS, progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        progress = tag.getInt(TAG_PROGRESS);
        ItemStack input = items.getStackInSlot(SLOT_INPUT);
        trackedInput = input.isEmpty() ? null : input.getItem();
        cacheValid = false;
    }
}
