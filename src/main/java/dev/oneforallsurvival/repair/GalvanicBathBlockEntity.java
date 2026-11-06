package dev.oneforallsurvival.repair;

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
 * Le bain galvanique : une pièce brute, du zinc, du courant, et la pièce ressort traitée. Trois cases :
 * 0 la pièce, 1 le zinc, 2 la sortie.
 *
 * <p>La pièce et le zinc ne se prélèvent qu'à la fin du bain : tant qu'il dure, retirer la pièce ne coûte
 * rien. La progression est gardée sans courant et en panne, et remise à zéro si la pièce change.
 * La recette en cours est mise en cache ; le cache tombe quand la case de la pièce change et quand
 * le gestionnaire de recettes n'est plus le même (rechargement des données).
 *
 * <p>Les faces ne donnent accès qu'à leur case : le dessous extrait la sortie, le dessus insère la pièce,
 * les côtés insèrent le zinc. Le contenu est jeté quand le bloc disparaît.
 */
public final class GalvanicBathBlockEntity extends ElectricMachineBlockEntity {

    public static final int SLOT_PIECE = 0;
    public static final int SLOT_ZINC = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int CAPACITY = 4000;
    public static final int MAX_RECEIVE = 200;

    private static final String TAG_ITEMS = "items";
    private static final String TAG_PROGRESS = "progress";

    /** Cases lues par le menu : progression, durée, énergie, capacité, état. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_CAPACITY = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_COUNT = 5;

    private final ItemStackHandler items = new ItemStackHandler(3) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return switch (slot) {
                case SLOT_PIECE -> anyRecipe(r -> r.acceptsPiece(stack));
                case SLOT_ZINC -> anyRecipe(r -> r.acceptsZinc(stack));
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == SLOT_PIECE) {
                // Le cache tombe, mais la progression, non : un clic de souris vide la case un instant
                // (SlotItemHandler.getMaxStackSize) sans que la pièce ait vraiment changé.
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
                    GalvanicRecipe recipe = currentRecipe();
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
    private LazyOptional<IItemHandler> topCap = LazyOptional.of(() -> new Face(items, SLOT_PIECE, true, false));
    private LazyOptional<IItemHandler> sideCap = LazyOptional.of(() -> new Face(items, SLOT_ZINC, true, false));
    private LazyOptional<IItemHandler> bottomCap = LazyOptional.of(() -> new Face(items, SLOT_OUTPUT, false, true));

    /** Ticks de travail du bain en cours. */
    private int progress;
    /** L'objet dans la case de la pièce à la dernière lecture : sert à voir si la pièce a changé. */
    @Nullable
    private Item trackedPiece;
    /** Recette de la pièce posée ; non sauvegardée. {@code cacheValid} : le cache dit vrai pour la pièce et les données. */
    @Nullable
    private GalvanicRecipe cachedRecipe;
    private boolean cacheValid;
    @Nullable
    private RecipeManager cachedManager;

    public GalvanicBathBlockEntity(BlockPos pos, BlockState state) {
        super(RepairRegistry.GALVANIC_BATH_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
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

    private boolean anyRecipe(Predicate<GalvanicRecipe> test) {
        if (level == null) {
            return true;
        }
        for (GalvanicRecipe recipe : level.getRecipeManager().getAllRecipesFor(RepairRegistry.GALVANIC_TYPE.get())) {
            if (test.test(recipe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * La recette de la pièce posée. Le cache vaut tant que la pièce et le gestionnaire de recettes sont les
     * mêmes : un rechargement des données installe un autre gestionnaire. C'est aussi ici, une fois par tick
     * (via {@link #energyPerTick()}), que la progression repart de zéro si l'objet de la case a changé.
     */
    @Nullable
    private GalvanicRecipe currentRecipe() {
        if (level == null) {
            return null;
        }
        ItemStack piece = items.getStackInSlot(SLOT_PIECE);
        Item now = piece.isEmpty() ? null : piece.getItem();
        if (now != trackedPiece) {
            trackedPiece = now;
            progress = 0;
            cacheValid = false;
        }
        RecipeManager manager = level.getRecipeManager();
        if (!cacheValid || manager != cachedManager) {
            cachedRecipe = lookup(piece);
            cachedManager = manager;
            cacheValid = true;
        }
        return cachedRecipe;
    }

    @Nullable
    private GalvanicRecipe lookup(ItemStack piece) {
        if (piece.isEmpty()) {
            return null;
        }
        for (GalvanicRecipe recipe : level.getRecipeManager().getAllRecipesFor(RepairRegistry.GALVANIC_TYPE.get())) {
            if (recipe.acceptsPiece(piece)) {
                return recipe;
            }
        }
        return null;
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
        GalvanicRecipe recipe = currentRecipe();
        if (recipe == null) {
            return false;
        }
        ItemStack zinc = items.getStackInSlot(SLOT_ZINC);
        return recipe.acceptsZinc(zinc) && zinc.getCount() >= recipe.zincCount() && outputFits(recipe.result());
    }

    @Override
    protected int energyPerTick() {
        GalvanicRecipe recipe = currentRecipe();
        return recipe == null ? 0 : recipe.energyPerTick();
    }

    @Override
    protected void workTick() {
        GalvanicRecipe recipe = currentRecipe();
        if (recipe == null) {
            return;
        }
        progress++;
        setChanged();
        if (progress >= recipe.ticks()) {
            finish(recipe);
        }
    }

    /** Le bain est fini : la pièce et le zinc sont payés, la pièce traitée arrive en sortie. */
    private void finish(GalvanicRecipe recipe) {
        items.extractItem(SLOT_PIECE, 1, false);
        items.extractItem(SLOT_ZINC, recipe.zincCount(), false);
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            items.setStackInSlot(SLOT_OUTPUT, recipe.result().copy());
        } else {
            ItemStack grown = out.copy();
            grown.grow(recipe.result().getCount());
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
        topCap = LazyOptional.of(() -> new Face(items, SLOT_PIECE, true, false));
        sideCap = LazyOptional.of(() -> new Face(items, SLOT_ZINC, true, false));
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
        ItemStack piece = items.getStackInSlot(SLOT_PIECE);
        trackedPiece = piece.isEmpty() ? null : piece.getItem();
        cacheValid = false;
    }
}
