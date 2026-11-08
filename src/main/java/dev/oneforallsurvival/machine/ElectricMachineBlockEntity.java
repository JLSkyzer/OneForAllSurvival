package dev.oneforallsurvival.machine;

import dev.oneforallsurvival.coast.CoastDetector;
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
    private static final int COAST_PERIOD = 200;

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
    /** Dernière lecture du milieu côtier et tick de jeu de la suivante ; non sauvegardés. */
    private boolean coastal;
    private long nextCoastCheck;

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

    /** Sortie d'usine : état plein, aucune panne, non traitée. Seule une telle machine laisse un objet nu. */
    public boolean isFactoryFresh() {
        return condition >= MachineWear.FULL && fault == null && !isProtected;
    }

    /**
     * L'usure que l'objet lâché emporte, au format de {@link #saveAdditional} : état, panne, prochaine
     * panne, protection. Ni l'énergie ni le reste, qui repartent à zéro à la pose.
     */
    public CompoundTag wearTag() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(TAG_CONDITION, condition);
        if (fault != null) {
            tag.putString(TAG_FAULT_TYPE, fault.name());
        }
        tag.putString(TAG_NEXT_FAULT, nextFault.name());
        tag.putBoolean(TAG_PROTECTED, isProtected);
        return tag;
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
     * Retire des points d'état sans passer par le travail (un coup de foudre, par exemple). À 0, la machine tombe en
     * panne de la pièce annoncée, comme à l'usure. Une machine déjà en panne n'est pas touchée.
     */
    public void takeDamage(double points) {
        if (fault != null || points <= 0) {
            return;
        }
        loseCondition(points);
        setChanged();
    }

    /** Retire des points d'état ; à 0, panne de la pièce annoncée. Renvoie vrai si la machine vient de tomber en panne. */
    private boolean loseCondition(double points) {
        condition = Math.max(0, condition - points);
        if (condition <= 0) {
            breakDown(nextFault);
            return true;
        }
        return false;
    }

    /**
     * Ce que donnerait {@link #repair} avec cette pièce, sans rien changer. La pièce doit être celle
     * qui a lâché, ou à défaut celle qui lâchera.
     */
    public RepairOutcome previewRepair(FaultType part, boolean treated) {
        if (part != expectedPart()) {
            return RepairOutcome.WRONG_PART;
        }
        if (fault == null && condition >= MachineWear.FULL && (!treated || isProtected)) {
            return RepairOutcome.NOTHING_TO_DO;
        }
        return RepairOutcome.REPAIRED;
    }

    /** La pièce qu'il faut à cette machine : celle qui a lâché, ou à défaut celle qui lâchera. */
    public FaultType expectedPart() {
        return fault != null ? fault : nextFault;
    }

    /**
     * Répare avec cette pièce. {@code treated} : la pièce posée est traitée, ce qui protège la machine.
     * Si la pièce ne convient pas ou qu'il n'y a rien à faire, rien ne change.
     */
    public RepairOutcome repair(FaultType part, boolean treated) {
        RepairOutcome outcome = previewRepair(part, treated);
        if (outcome != RepairOutcome.REPAIRED) {
            return outcome;
        }
        condition = MachineWear.FULL;
        fault = null;
        isProtected = treated;
        nextFault = drawFault();
        flameTicksLeft = 0;
        setChanged();
        return RepairOutcome.REPAIRED;
    }

    /**
     * Prélève jusqu'à {@code amount} FE de la réserve, pour recharger un outil, et renvoie ce qui a
     * réellement été retiré. La capability, elle, ne laisse rien sortir.
     */
    public int drainEnergy(int amount) {
        int taken = storage.consume(amount);
        if (taken > 0) {
            setChanged();
        }
        return taken;
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

    /**
     * Le milieu est-il côtier ? Relu au plus tous les {@value #COAST_PERIOD} ticks : le détecteur garde
     * son propre cache, mais on évite de l'interroger à chaque tick de travail.
     */
    protected boolean isCoastal() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        long now = serverLevel.getGameTime();
        if (now >= nextCoastCheck) {
            coastal = CoastDetector.isCoastal(serverLevel, worldPosition);
            nextCoastCheck = now + COAST_PERIOD;
        }
        return coastal;
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
        afterTick(level, next);
    }

    /**
     * Fin de chaque tick serveur, quel que soit l'état (panne, arrêt, manque de courant, marche) : pour une
     * machine qui doit suivre le temps même quand elle ne travaille pas. Ne fait rien par défaut.
     */
    protected void afterTick(Level level, MachineState tickState) {}

    /** Un tick d'usure ; à 0 la machine tombe en panne de la pièce annoncée, et peut s'enflammer. */
    private void wear() {
        if (loseCondition(MachineWear.wearPerTick(MachineConfig.lifetimeTicks(),
                !isProtected && isCoastal(), isProtected, MachineConfig.coastalMultiplier()))
                && MachineConfig.flamesEnabled() && random.nextDouble() < MachineConfig.flameChance()) {
            igniteFlames();
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
        condition = tag.contains(TAG_CONDITION)
                ? Math.max(0, Math.min(MachineWear.FULL, tag.getDouble(TAG_CONDITION))) : MachineWear.FULL;
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
