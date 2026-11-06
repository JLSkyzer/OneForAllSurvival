package dev.oneforallsurvival.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

/**
 * Socle des éléments en ligne (compteur, délesteur...). Aucune réserve : l'énergie reçue sur la
 * face d'entrée est aussitôt proposée au voisin de la face de sortie, et seul ce qu'il accepte est
 * rendu et compté. Un plafond par tick de jeu borne le débit ; une simulation ne touche à rien
 * mais respecte ce plafond dans la valeur qu'elle renvoie. Le voisin n'est pas mis en cache.
 *
 * <p>Pas de récursion possible : la sortie d'un bloc touche un voisin par la face qu'il lui
 * présente ; deux blocs face à face s'offrent mutuellement leur sortie, qui n'expose rien.
 */
public abstract class InlineEnergyBlockEntity extends BlockEntity {

    private static final String TAG_TOTAL = "total_transferred";

    private final int maxPerTick;
    private final IEnergyStorage input = new InputStorage();
    private LazyOptional<IEnergyStorage> inputCap = LazyOptional.of(() -> input);
    private long totalTransferred;
    private long budgetTick = Long.MIN_VALUE;
    private int usedThisTick;

    protected InlineEnergyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int maxPerTick) {
        super(type, pos, state);
        this.maxPerTick = maxPerTick;
    }

    /** Vrai tant que le bloc laisse passer le courant ; une sous-classe peut le couper. */
    protected boolean isOpen() {
        return true;
    }

    /** Total d'énergie réellement transmis depuis la pose, en FE. */
    public long totalTransferred() {
        return totalTransferred;
    }

    private Direction outputSide() {
        return getBlockState().getValue(InlineEnergyBlock.FACING);
    }

    private IEnergyStorage neighbor() {
        if (level == null) {
            return null;
        }
        Direction out = outputSide();
        BlockEntity target = level.getBlockEntity(worldPosition.relative(out));
        if (target == null) {
            return null;
        }
        return target.getCapability(ForgeCapabilities.ENERGY, out.getOpposite()).resolve().orElse(null);
    }

    private int pass(int amount, boolean simulate) {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide || amount <= 0 || !isOpen()) {
            return 0;
        }
        IEnergyStorage next = neighbor();
        if (next == null) {
            return 0;
        }
        long now = lvl.getGameTime();
        int used = now == budgetTick ? usedThisTick : 0;
        int offer = Math.min(amount, maxPerTick - used);
        if (offer <= 0) {
            return 0;
        }
        int accepted = next.receiveEnergy(offer, simulate);
        if (!simulate && accepted > 0) {
            budgetTick = now;
            usedThisTick = used + accepted;
            totalTransferred += accepted;
            setChanged();
        }
        return accepted;
    }

    /** Face d'entrée : se contente de relayer ce que le voisin de sortie rapporte. */
    private final class InputStorage implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return pass(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            IEnergyStorage next = neighbor();
            return next == null ? 0 : next.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            IEnergyStorage next = neighbor();
            return next == null ? 0 : next.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return isOpen() && neighbor() != null;
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ENERGY && side != null && side == outputSide().getOpposite()) {
            return inputCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inputCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inputCap = LazyOptional.of(() -> input);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong(TAG_TOTAL, totalTransferred);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        totalTransferred = tag.getLong(TAG_TOTAL);
    }
}
