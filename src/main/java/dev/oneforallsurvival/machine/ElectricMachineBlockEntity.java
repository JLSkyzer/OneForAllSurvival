package dev.oneforallsurvival.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

/**
 * Socle de toutes les machines électriques : une réserve de Forge Energy exposée par
 * {@link ForgeCapabilities#ENERGY}, un état calculé à chaque tick et une panne mémorisée.
 *
 * <p>Règle du tick : en panne, rien. Sinon, si la machine n'a rien à faire, elle est à l'arrêt ;
 * si la réserve couvre le besoin du tick, elle consomme, travaille et tourne ; sinon elle manque
 * de courant, sans toucher à sa progression.
 */
public abstract class ElectricMachineBlockEntity extends BlockEntity {

    private static final String TAG_ENERGY = "energy";
    private static final String TAG_FAULT = "fault";

    private final MachineEnergyStorage storage;
    private LazyOptional<IEnergyStorage> energyCap;
    private boolean fault;
    private MachineState state = MachineState.STOPPED;

    protected ElectricMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
            int capacity, int maxReceive) {
        super(type, pos, blockState);
        this.storage = new MachineEnergyStorage(capacity, maxReceive) {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                int received = super.receiveEnergy(maxReceive, simulate);
                if (received > 0 && !simulate) {
                    setChanged();
                }
                return received;
            }
        };
        this.energyCap = LazyOptional.of(() -> storage);
    }

    /** La machine a du travail à faire. */
    protected abstract boolean wantsToWork();

    /** Énergie consommée à chaque tick de travail. */
    protected abstract int energyPerTick();

    /** Un tick de travail, appelé seulement quand l'énergie a été payée. */
    protected abstract void workTick();

    public MachineState state() {
        return state;
    }

    public IEnergyStorage energy() {
        return storage;
    }

    protected void setFault(boolean fault) {
        if (this.fault != fault) {
            this.fault = fault;
            setChanged();
        }
    }

    /** Appelé par le bloc, côté serveur seulement. */
    void serverTick(Level level, BlockPos pos, BlockState blockState) {
        MachineState next;
        if (fault) {
            next = MachineState.FAULT;
        } else if (!wantsToWork()) {
            next = MachineState.STOPPED;
        } else if (storage.getEnergyStored() >= energyPerTick()) {
            storage.consume(energyPerTick());
            setChanged();
            workTick();
            next = MachineState.RUNNING;
        } else {
            next = MachineState.NO_POWER;
        }
        state = next;
        if (blockState.getValue(ElectricMachineBlock.STATE) != next) {
            level.setBlock(pos, blockState.setValue(ElectricMachineBlock.STATE, next), Block.UPDATE_CLIENTS);
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> storage);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_ENERGY, storage.getEnergyStored());
        tag.putBoolean(TAG_FAULT, fault);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        storage.setStored(tag.getInt(TAG_ENERGY));
        fault = tag.getBoolean(TAG_FAULT);
        // L'état n'est pas sauvegardé : le BlockState le porte, le prochain tick le recalcule.
        state = getBlockState().getValue(ElectricMachineBlock.STATE);
    }
}
