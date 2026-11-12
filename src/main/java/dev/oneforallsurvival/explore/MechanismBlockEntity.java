package dev.oneforallsurvival.explore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

/**
 * Le mécanisme ancien : il demande {@code threshold} FE à chaque tick pendant {@code durationTicks} ticks de suite, puis
 * s'active et le reste jusqu'à {@link #reset()}. Une coupure d'un tick remet la progression à zéro. Il ne stocke aucune
 * énergie, ne s'use pas et ne tombe pas en panne (ce n'est donc pas une machine électrique du mod). Dans un chunk
 * déchargé il ne tique pas : rien n'avance, rien ne se perd, et le premier tick après un rechargement ne juge pas
 * (voir {@link #graceTick}). Il ne juge pas non plus un tick sans courant quand l'un de ses voisins est dans un
 * chunk qui ne tique pas : la source figée là-bas n'a pas pu verser.
 *
 * <p>Ordre des ticks. Un câble peut verser du courant avant ou après le tick de ce bloc dans le même tick de jeu. La règle
 * est donc : {@link #serverTick} juge ce qui est arrivé depuis son tick précédent, puis remet le compte à zéro. Une
 * source qui verse à chaque tick est comptée une fois par tick quel que soit son ordre (seul le tout premier tick peut
 * compter un versement de trop ou de moins), et le plafond de {@code 2 × threshold} vaut pour cette même fenêtre.
 * Verrouillé, il refuse tout courant : il n'a plus rien à consommer.
 */
public final class MechanismBlockEntity extends BlockEntity {

    public static final int DEFAULT_THRESHOLD = 100;
    public static final int DEFAULT_DURATION_TICKS = 200;

    private static final String TAG_THRESHOLD = "Threshold";
    private static final String TAG_DURATION = "Duration";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_ACTIVE = "Active";

    /** Reçoit sans rien garder : compte ce qui arrive entre deux ticks du bloc, plafonné à 2 × le seuil. */
    private final class Receiver implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (active || maxReceive <= 0) {
                return 0;
            }
            int accepted = Math.min(maxReceive, Math.max(0, 2 * threshold - receivedSinceTick));
            if (!simulate) {
                receivedSinceTick += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 2 * threshold;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return !active;
        }
    }

    private final Receiver receiver = new Receiver();
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> receiver);
    private int threshold = DEFAULT_THRESHOLD;
    private int durationTicks = DEFAULT_DURATION_TICKS;
    private int progress;
    private boolean active;
    /** Ce qui est arrivé depuis le dernier tick du bloc ; jamais sauvegardé. */
    private int receivedSinceTick;
    /**
     * Vrai de {@link #load} jusqu'au premier tick qui suit : ce tick ne juge pas (ni progression ni remise à zéro). Au
     * rechargement, rien n'a pu arriver depuis un tick précédent qui n'existe pas encore ; juger tout de suite ferait
     * perdre la progression à chaque rechargement de chunk ou redémarrage. Un seul tick suffit : dès le suivant, la
     * fenêtre « depuis le dernier tick » couvre un tick complet de la source, avant ou après le bloc dans l'ordre du tick.
     * Une vraie coupure, hors rechargement, remet toujours à zéro. Jamais sauvegardé.
     */
    private boolean graceTick;

    public MechanismBlockEntity(BlockPos pos, BlockState state) {
        super(ExploreRegistry.MECHANISM_BE.get(), pos, state);
    }

    public int threshold() {
        return threshold;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public int progress() {
        return progress;
    }

    public boolean active() {
        return active;
    }

    /** Règle le seuil (FE par tick) et la durée (ticks), au moins 1 chacun ; la progression reste bornée à la durée. */
    public void setSettings(int newThreshold, int newDurationTicks) {
        threshold = Math.max(1, newThreshold);
        durationTicks = Math.max(1, newDurationTicks);
        progress = Math.min(progress, durationTicks);
        setChanged();
    }

    /** Remet le mécanisme à zéro : progression nulle, signal coupé. */
    public void reset() {
        progress = 0;
        active = false;
        receivedSinceTick = 0;
        setChanged();
        if (level instanceof ServerLevel server) {
            syncState(server, worldPosition, getBlockState());
        }
    }

    public void serverTick(ServerLevel server, BlockPos pos, BlockState state) {
        int received = receivedSinceTick;
        receivedSinceTick = 0;
        if (graceTick) {
            graceTick = false;
            syncState(server, pos, state);
            return;
        }
        if (!active && received == 0 && hasUntickingNeighbor(server, pos)) {
            // Un voisin dans un chunk qui ne tique pas n'a peut-être pas pu verser son courant : pas de jugement.
            syncState(server, pos, state);
            return;
        }
        if (!active) {
            int before = progress;
            progress = MechanismRules.next(progress, received, threshold, durationTicks);
            if (MechanismRules.isComplete(progress, durationTicks)) {
                active = true;
                setChanged();
            } else if (progress != before) {
                setChanged();
            }
        }
        syncState(server, pos, state);
    }

    /** Un des 6 voisins est dans un chunk dont les blocs ne tiquent pas (la source qui l'alimente est figée). */
    private static boolean hasUntickingNeighbor(ServerLevel server, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!server.shouldTickBlocksAt(pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    /** Aligne l'état du bloc sur la progression et, quand le signal change, prévient les voisins des voisins. */
    private void syncState(ServerLevel server, BlockPos pos, BlockState state) {
        int stage = MechanismRules.stage(progress, durationTicks);
        if (state.getValue(MechanismBlock.STAGE) == stage && state.getValue(MechanismBlock.ACTIVE) == active) {
            return;
        }
        boolean signalChanged = state.getValue(MechanismBlock.ACTIVE) != active;
        server.setBlock(pos, state.setValue(MechanismBlock.STAGE, stage).setValue(MechanismBlock.ACTIVE, active), 3);
        if (signalChanged) {
            // Source forte (comme le levier) : les voisins des voisins, pour la poudre posée sur un bloc adjacent.
            for (Direction direction : Direction.values()) {
                server.updateNeighborsAt(pos.relative(direction), state.getBlock());
            }
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
        energyCap = LazyOptional.of(() -> receiver);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_THRESHOLD, threshold);
        tag.putInt(TAG_DURATION, durationTicks);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putBoolean(TAG_ACTIVE, active);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        threshold = tag.contains(TAG_THRESHOLD) ? Math.max(1, tag.getInt(TAG_THRESHOLD)) : DEFAULT_THRESHOLD;
        durationTicks = tag.contains(TAG_DURATION) ? Math.max(1, tag.getInt(TAG_DURATION)) : DEFAULT_DURATION_TICKS;
        progress = Math.max(0, Math.min(tag.getInt(TAG_PROGRESS), durationTicks));
        active = tag.getBoolean(TAG_ACTIVE);
        receivedSinceTick = 0;
        graceTick = true;
    }
}
