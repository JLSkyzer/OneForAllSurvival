package dev.oneforallsurvival.food;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Le dessalinisateur. Deux cuves de 8 000 mB (l'eau à traiter, l'eau potable) et trois cases : le sel en sortie, la
 * bouteille vide en entrée, la bouteille pleine en sortie.
 *
 * <p>Un cycle ({@link FoodConfig#desalinatorCycle()} ticks de travail, à {@link FoodConfig#desalinatorEnergy()} FE
 * par tick) consomme {@link FoodConfig#waterPerCycle()} mB d'eau et rend {@link FoodConfig#potablePerCycle()} mB
 * d'eau potable ; tous les {@link FoodConfig#cyclesPerSalt()} cycles, il tombe aussi un sel. La machine ne
 * travaille que si le cycle en cours peut s'achever : assez d'eau, place pour l'eau potable et, au cycle du sel,
 * place pour le sel. Sinon elle s'arrête, sans rien perdre : la progression reste, comme sans courant.
 *
 * <p>Hors panne, trois choses se font sans courant : en milieu côtier (relu toutes les 200 ticks), la cuve d'eau se
 * remplit seule de 100 mB par seconde ; une bouteille vide se remplit de {@link #BOTTLE_MB} mB d'eau potable.
 *
 * <p>Vers l'extérieur, une seule capability de fluides, sur toutes les faces : elle ne remplit que de l'eau (tout
 * ce que porte {@link FluidTags#WATER}, déposé comme de l'eau) et ne vide que l'eau potable. Casser la machine rend
 * les objets des cases et perd les réservoirs.
 */
public final class DesalinatorBlockEntity extends ElectricMachineBlockEntity {

    /** Sel en sortie, bouteille vide en entrée, bouteille pleine en sortie. */
    public static final int SLOT_SALT = 0;
    public static final int SLOT_EMPTY = 1;
    public static final int SLOT_FILLED = 2;
    public static final int SLOTS = 3;
    public static final int TANK_CAPACITY = 8000;
    /** Le prix d'un cycle, aux réglages d'origine (40 FE pendant 100 ticks). */
    public static final int CAPACITY = 4000;
    public static final int MAX_RECEIVE = 100;
    /** Eau potable que prend une bouteille. */
    public static final int BOTTLE_MB = 250;
    /** 100 mB par seconde, à 20 ticks par seconde. */
    private static final int COAST_MB_PER_TICK = 5;

    private static final String TAG_ITEMS = "items";
    private static final String TAG_WATER = "water";
    private static final String TAG_POTABLE = "potable";
    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_CYCLES = "cycles";

    /** Nombres lus par le menu : énergie, capacité, état, eau, eau potable, progression, durée du cycle. */
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_WATER = 3;
    public static final int DATA_POTABLE = 4;
    public static final int DATA_PROGRESS = 5;
    public static final int DATA_CYCLE = 6;
    public static final int DATA_COUNT = 7;

    /** Seule la bouteille de verre entre par les trémies, et seulement dans sa case ; les sorties ne reçoivent rien. */
    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == SLOT_EMPTY && stack.is(Items.GLASS_BOTTLE);
        }
    };

    private final FluidTank water = new FluidTank(TANK_CAPACITY, stack -> stack.getFluid().is(FluidTags.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final FluidTank potable = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid().isSame(FoodRegistry.POTABLE_WATER.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** Ce que voient les tuyaux : de l'eau entre, de l'eau potable sort, rien d'autre. */
    private final IFluidHandler pipes = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return (tank == 0 ? water : potable).getFluid().copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 && water.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!water.isFluidValid(resource)) {
                return 0;
            }
            // Eau courante ou source, la cuve ne garde qu'une sorte d'eau : elles se mêlent.
            return water.fill(new FluidStack(Fluids.WATER, resource.getAmount()), action);
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!potable.isFluidValid(resource)) {
                return FluidStack.EMPTY;
            }
            return potable.drain(resource, action);
        }

        @NotNull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return potable.drain(maxDrain, action);
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy().getEnergyStored();
                case DATA_CAPACITY -> energy().getMaxEnergyStored();
                case DATA_STATE -> state().ordinal();
                case DATA_WATER -> water.getFluidAmount();
                case DATA_POTABLE -> potable.getFluidAmount();
                case DATA_PROGRESS -> progress;
                case DATA_CYCLE -> FoodConfig.desalinatorCycle();
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

    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> items);
    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> pipes);

    /** Ticks de travail du cycle en cours. */
    private int progress;
    /** Cycles achevés : c'est lui qui dit quand tombe le sel. */
    private int cycles;

    public DesalinatorBlockEntity(BlockPos pos, BlockState state) {
        super(FoodRegistry.DESALINATOR_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    /** Les trois cases, pour le menu, les tests et le bloc. */
    public IItemHandlerModifiable inventory() {
        return items;
    }

    /** La cuve d'eau à traiter, côté machine (le tuyau passe par la capability). */
    public FluidTank waterTank() {
        return water;
    }

    /** La cuve d'eau potable, côté machine. */
    public FluidTank potableTank() {
        return potable;
    }

    public ContainerData data() {
        return data;
    }

    public int progress() {
        return progress;
    }

    public int cycles() {
        return cycles;
    }

    /** Pour les tests : place le compteur de cycles, pour atteindre un cycle de sel sans en jouer trois. */
    public void debugSetCycles(int value) {
        cycles = Math.max(0, value);
        setChanged();
    }

    // ---------------------------------------------------------------------------------------------
    // Travail
    // ---------------------------------------------------------------------------------------------

    @Override
    protected boolean wantsToWork() {
        if (water.getFluidAmount() < FoodConfig.waterPerCycle()) {
            return false;
        }
        if (TANK_CAPACITY - potable.getFluidAmount() < FoodConfig.potablePerCycle()) {
            return false;
        }
        return !isSaltCycle() || canAdd(SLOT_SALT, FoodRegistry.SALT.get());
    }

    @Override
    protected int energyPerTick() {
        return FoodConfig.desalinatorEnergy();
    }

    @Override
    protected void workTick() {
        progress++;
        if (progress >= FoodConfig.desalinatorCycle()) {
            finishCycle();
        }
    }

    /** Le cycle qui s'achève sera-t-il celui du sel ? */
    private boolean isSaltCycle() {
        return (cycles + 1) % FoodConfig.cyclesPerSalt() == 0;
    }

    private void finishCycle() {
        water.drain(FoodConfig.waterPerCycle(), IFluidHandler.FluidAction.EXECUTE);
        potable.fill(new FluidStack(FoodRegistry.POTABLE_WATER.get(), FoodConfig.potablePerCycle()),
                IFluidHandler.FluidAction.EXECUTE);
        progress = 0;
        cycles++;
        if (cycles % FoodConfig.cyclesPerSalt() == 0) {
            add(SLOT_SALT, FoodRegistry.SALT.get());
        }
        setChanged();
    }

    @Override
    protected void afterTick(Level world, MachineState tickState) {
        // En panne, la machine ne fait plus rien, pas même ce qui est gratuit.
        if (tickState == MachineState.FAULT) {
            return;
        }
        if (isCoastal()) {
            water.fill(new FluidStack(Fluids.WATER, COAST_MB_PER_TICK), IFluidHandler.FluidAction.EXECUTE);
        }
        fillBottle();
    }

    /** Une bouteille vide devient une bouteille d'eau potable, tant que la cuve et la case de sortie suivent. */
    private void fillBottle() {
        if (!items.getStackInSlot(SLOT_EMPTY).is(Items.GLASS_BOTTLE) || potable.getFluidAmount() < BOTTLE_MB
                || !canAdd(SLOT_FILLED, FoodRegistry.POTABLE_WATER_BOTTLE.get())) {
            return;
        }
        potable.drain(BOTTLE_MB, IFluidHandler.FluidAction.EXECUTE);
        items.extractItem(SLOT_EMPTY, 1, false);
        add(SLOT_FILLED, FoodRegistry.POTABLE_WATER_BOTTLE.get());
    }

    /** Reste-t-il de la place pour un objet de plus dans cette case de sortie ? */
    private boolean canAdd(int slot, Item item) {
        ItemStack stack = items.getStackInSlot(slot);
        return stack.isEmpty() || (stack.is(item) && stack.getCount() < stack.getMaxStackSize());
    }

    /** Ajoute un objet dans une case de sortie ; l'appelant a vérifié la place par {@link #canAdd}. */
    private void add(int slot, Item item) {
        ItemStack stack = items.getStackInSlot(slot);
        items.setStackInSlot(slot, stack.isEmpty() ? new ItemStack(item) : stack.copyWithCount(stack.getCount() + 1));
    }

    /** Jette les objets au sol et vide les cases : appelé quand le bloc disparaît. Les réservoirs, eux, se perdent. */
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

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
        fluidCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCap = LazyOptional.of(() -> items);
        fluidCap = LazyOptional.of(() -> pipes);
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ITEMS, items.serializeNBT());
        tag.put(TAG_WATER, water.writeToNBT(new CompoundTag()));
        tag.put(TAG_POTABLE, potable.writeToNBT(new CompoundTag()));
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_CYCLES, cycles);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        water.readFromNBT(tag.getCompound(TAG_WATER));
        potable.readFromNBT(tag.getCompound(TAG_POTABLE));
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        cycles = Math.max(0, tag.getInt(TAG_CYCLES));
    }
}
