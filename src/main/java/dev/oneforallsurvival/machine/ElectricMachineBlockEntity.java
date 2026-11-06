package dev.oneforallsurvival.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import org.jetbrains.annotations.Nullable;

/**
 * Socle de toutes les machines électriques : une réserve de Forge Energy exposée par
 * {@link ForgeCapabilities#ENERGY}, un état calculé à chaque tick, un état d'usure et une panne
 * mémorisée.
 *
 * <p>Règle du tick : en panne, rien. Sinon, si la machine n'a rien à faire, elle est à l'arrêt ;
 * si la réserve couvre le besoin du tick, elle consomme, travaille (au rendement de son état) et
 * tourne, puis s'use ; sinon elle manque de courant, sans toucher à sa progression ni à son état.
 *
 * <p>L'état (de 0 à 100) ne baisse qu'en marche. Sous {@link MachineWear#REDUCED_BELOW} le rendement
 * baisse, sous {@link MachineWear#SMOKE_BELOW} la machine fume, à 0 elle tombe en panne : la pièce
 * qui lâche est tirée au sort à l'avance ({@link #nextFault()}), pour que le diagnostic soit possible.
 */
public abstract class ElectricMachineBlockEntity extends BlockEntity {

    private static final String TAG_ENERGY = "energy";
    private static final String TAG_CONDITION = "condition";
    private static final String TAG_FAULT_TYPE = "fault_type";
    private static final String TAG_NEXT_FAULT = "next_fault";
    private static final String TAG_PROTECTED = "protected";
    private static final int SMOKE_PERIOD = 20;
    private static final int FLAME_PERIOD = 5;

    private final MachineEnergyStorage storage;
    private final RandomSource random = RandomSource.create();
    private LazyOptional<IEnergyStorage> energyCap;
    private double condition = MachineWear.FULL;
    @Nullable
    private FaultType fault;
    private FaultType nextFault = drawFault();
    private boolean isProtected;
    /** Rendement pas encore converti en tick de travail ; non sauvegardé. */
    private double workCredit;
    /** Ticks de flammes restants ; les flammes ne se sauvegardent pas. */
    private int flameTicksLeft;
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

    /** Un tick de travail, appelé seulement quand l'énergie a été payée (et que le rendement l'accorde). */
    protected abstract void workTick();

    /**
     * État de la machine tel que le serveur l'a calculé. Il fait autorité côté serveur seulement :
     * il peut retarder sur {@link #breakDown} jusqu'au prochain tick, et il n'est pas mis à jour côté
     * client. Le code client lit {@code getBlockState().getValue(ElectricMachineBlock.STATE)}.
     */
    public MachineState state() {
        return state;
    }

    public IEnergyStorage energy() {
        return storage;
    }

    /** État d'usure, de 0 (hors d'usage) à 100 (neuf). */
    public double condition() {
        return condition;
    }

    /** La panne en cours, ou {@code null} si la machine n'est pas en panne. */
    @Nullable
    public FaultType fault() {
        return fault;
    }

    /** La pièce qui lâchera à la prochaine panne. */
    public FaultType nextFault() {
        return nextFault;
    }

    /** Machine traitée : le milieu côtier ne l'use pas plus vite. */
    public boolean isProtected() {
        return isProtected;
    }

    /** Part du travail réellement fournie à cet état (1.0 au plein rendement). */
    public double efficiency() {
        return MachineWear.efficiency(condition, MachineConfig.reducedEfficiency());
    }

    public int flameTicksLeft() {
        return flameTicksLeft;
    }

    /** Met la machine en panne de cette pièce, hors d'usage (état à 0). */
    public void breakDown(FaultType type) {
        fault = type;
        condition = 0;
        setChanged();
    }

    /**
     * Répare avec cette pièce. {@code treated} : la pièce posée est traitée, ce qui protège la machine.
     * La pièce doit être celle qui a lâché, ou à défaut celle qui lâchera ; sinon rien ne change.
     */
    public RepairOutcome repair(FaultType part, boolean treated) {
        FaultType expected = fault != null ? fault : nextFault;
        if (part != expected) {
            return RepairOutcome.WRONG_PART;
        }
        if (fault == null && condition >= MachineWear.FULL && (!treated || isProtected)) {
            return RepairOutcome.NOTHING_TO_DO;
        }
        condition = MachineWear.FULL;
        fault = null;
        isProtected = treated;
        nextFault = drawFault();
        setChanged();
        return RepairOutcome.REPAIRED;
    }

    protected void setCondition(double value) {
        condition = Math.max(0, Math.min(MachineWear.FULL, value));
        setChanged();
    }

    /** Lève la panne sans toucher à l'état. */
    protected void clearFault() {
        if (fault != null) {
            fault = null;
            setChanged();
        }
    }

    /** Allume les flammes d'une panne : visuelles seulement, elles ne posent aucun bloc. */
    protected void igniteFlames() {
        flameTicksLeft = MachineConfig.flameTicks();
    }

    /** Le milieu est-il côtier ? Pas encore détecté : toujours faux. */
    protected boolean isCoastal() {
        return false;
    }

    private FaultType drawFault() {
        FaultType[] all = FaultType.values();
        return all[random.nextInt(all.length)];
    }

    /** Appelé par le bloc, côté serveur seulement. */
    void serverTick(Level level, BlockPos pos, BlockState blockState) {
        if (flameTicksLeft > 0) {
            flameTicksLeft--;
            if (flameTicksLeft % FLAME_PERIOD == 0) {
                spawnParticles(level, pos, false);
            }
        }
        MachineState next;
        int cost = energyPerTick(); // lu une seule fois par tick
        if (fault != null) {
            next = MachineState.FAULT;
        } else if (!wantsToWork()) {
            next = MachineState.STOPPED;
        } else if (storage.getEnergyStored() >= cost) {
            storage.consume(cost);
            setChanged();
            workCredit += efficiency();
            if (workCredit >= 1) {
                workCredit -= 1;
                workTick();
            }
            wear();
            next = fault != null ? MachineState.FAULT : MachineState.RUNNING;
        } else {
            next = MachineState.NO_POWER;
        }
        state = next;
        if (condition < MachineWear.SMOKE_BELOW && (next == MachineState.RUNNING || next == MachineState.FAULT)
                && level.getGameTime() % SMOKE_PERIOD == 0) {
            spawnParticles(level, pos, true);
        }
        if (blockState.getValue(ElectricMachineBlock.STATE) != next) {
            level.setBlock(pos, blockState.setValue(ElectricMachineBlock.STATE, next), Block.UPDATE_CLIENTS);
        }
    }

    /** Un tick d'usure ; à 0 la machine tombe en panne de la pièce annoncée, et peut s'enflammer. */
    private void wear() {
        condition = Math.max(0, condition - MachineWear.wearPerTick(MachineConfig.lifetimeTicks(), isCoastal(),
                isProtected, MachineConfig.coastalMultiplier()));
        if (condition <= 0) {
            breakDown(nextFault);
            if (MachineConfig.flamesEnabled() && random.nextDouble() < MachineConfig.flameChance()) {
                igniteFlames();
            }
        }
    }

    private static void spawnParticles(Level level, BlockPos pos, boolean smoke) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        if (smoke) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.2, 0.1, 0.2, 0.01);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, 0.25, 0.1, 0.25, 0.05);
        } else {
            serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 3, 0.2, 0.1, 0.2, 0.02);
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
        tag.putDouble(TAG_CONDITION, condition);
        if (fault != null) {
            tag.putString(TAG_FAULT_TYPE, fault.name());
        }
        tag.putString(TAG_NEXT_FAULT, nextFault.name());
        tag.putBoolean(TAG_PROTECTED, isProtected);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        storage.setStored(tag.getInt(TAG_ENERGY));
        condition = tag.contains(TAG_CONDITION) ? tag.getDouble(TAG_CONDITION) : MachineWear.FULL;
        fault = FaultType.byName(tag.getString(TAG_FAULT_TYPE));
        FaultType saved = FaultType.byName(tag.getString(TAG_NEXT_FAULT));
        if (saved != null) {
            nextFault = saved;
        }
        isProtected = tag.getBoolean(TAG_PROTECTED);
        // L'état n'est pas sauvegardé : le BlockState le porte, le prochain tick le recalcule.
        state = getBlockState().getValue(ElectricMachineBlock.STATE);
    }
}
