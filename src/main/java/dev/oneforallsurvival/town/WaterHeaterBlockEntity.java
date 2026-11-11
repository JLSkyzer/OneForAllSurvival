package dev.oneforallsurvival.town;

import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Le chauffe-eau. Deux cuves de {@value #TANK_CAPACITY} mB : l'eau potable qui entre, l'eau chaude qui sort.
 *
 * <p>Il ne travaille que s'il a au moins 1 mB d'eau potable et 1 mB de place en eau chaude. Chaque tick de travail
 * change {@link TownConfig#heaterMbPerTick()} mB d'eau potable en eau chaude (moins si la cuve source ou la place
 * manque : le dernier pas peut être partiel), à {@link TownConfig#heaterEnergy()} FE par tick. L'usure n'agit qu'une
 * fois : la classe de base espace déjà les ticks de travail selon le rendement. Sans courant, à l'arrêt ou en panne,
 * il ne touche à rien : rien ne se perd.
 *
 * <p>Vers l'extérieur, une seule capability de fluides, sur toutes les faces : elle ne remplit que de l'eau potable
 * du mod (ni eau vanilla, ni lave) et ne vide que l'eau chaude. Casser la machine perd les cuves.
 */
public final class WaterHeaterBlockEntity extends ElectricMachineBlockEntity {

    /** Quelques secondes de marche : la réserve reçoit jusqu'à 200 FE par tick. */
    public static final int CAPACITY = 8000;
    public static final int MAX_RECEIVE = TOWN_MAX_RECEIVE;
    public static final int TANK_CAPACITY = 4000;

    private static final String TAG_POTABLE = "potable";
    private static final String TAG_HOT = "hot";

    /** Nombres lus par le menu : énergie, capacité, état, eau potable, eau chaude. */
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_POTABLE = 3;
    public static final int DATA_HOT = 4;
    public static final int DATA_COUNT = 5;

    private final FluidTank potable = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid().isSame(FoodRegistry.POTABLE_WATER.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final FluidTank hot = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid().isSame(TownRegistry.HOT_WATER.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** Ce que voient les tuyaux : de l'eau potable entre, de l'eau chaude sort, rien d'autre. */
    private final IFluidHandler pipes = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return (tank == 0 ? potable : hot).getFluid().copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 && potable.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return potable.isFluidValid(resource) ? potable.fill(resource, action) : 0;
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return hot.isFluidValid(resource) ? hot.drain(resource, action) : FluidStack.EMPTY;
        }

        @NotNull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return hot.drain(maxDrain, action);
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy().getEnergyStored();
                case DATA_CAPACITY -> energy().getMaxEnergyStored();
                case DATA_STATE -> state().ordinal();
                case DATA_POTABLE -> potable.getFluidAmount();
                case DATA_HOT -> hot.getFluidAmount();
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

    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> pipes);

    public WaterHeaterBlockEntity(BlockPos pos, BlockState state) {
        super(TownRegistry.WATER_HEATER_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    /** La cuve d'eau potable, côté machine (le tuyau passe par {@link #fluids()}). */
    public FluidTank potable() {
        return potable;
    }

    /** La cuve d'eau chaude, côté machine. */
    public FluidTank hot() {
        return hot;
    }

    /** Ce que voient les tuyaux sur toutes les faces. */
    public IFluidHandler fluids() {
        return pipes;
    }

    public ContainerData data() {
        return data;
    }

    // ---------------------------------------------------------------------------------------------
    // Travail
    // ---------------------------------------------------------------------------------------------

    @Override
    protected boolean wantsToWork() {
        return potable.getFluidAmount() > 0 && hot.getFluidAmount() < TANK_CAPACITY;
    }

    @Override
    protected int energyPerTick() {
        return TownConfig.heaterEnergy();
    }

    @Override
    protected void workTick() {
        int moved = Math.min(TownConfig.heaterMbPerTick(),
                Math.min(potable.getFluidAmount(), TANK_CAPACITY - hot.getFluidAmount()));
        if (moved <= 0) {
            return;
        }
        potable.drain(moved, IFluidHandler.FluidAction.EXECUTE);
        hot.fill(new FluidStack(TownRegistry.HOT_WATER.get(), moved), IFluidHandler.FluidAction.EXECUTE);
    }

    // ---------------------------------------------------------------------------------------------
    // Faces
    // ---------------------------------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluidCap = LazyOptional.of(() -> pipes);
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_POTABLE, potable.writeToNBT(new CompoundTag()));
        tag.put(TAG_HOT, hot.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        potable.readFromNBT(tag.getCompound(TAG_POTABLE));
        hot.readFromNBT(tag.getCompound(TAG_HOT));
    }
}
