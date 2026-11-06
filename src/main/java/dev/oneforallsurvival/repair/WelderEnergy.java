package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.machine.MachineConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * La batterie du poste à souder, adossée au NBT de sa pile ({@code energy}). On peut la charger,
 * jamais la vider de l'extérieur : seul le poste lui-même dépense, par {@link #spend}.
 */
public final class WelderEnergy implements IEnergyStorage, ICapabilityProvider {

    private static final String TAG_ENERGY = "energy";

    private final ItemStack stack;
    private final LazyOptional<IEnergyStorage> self = LazyOptional.of(() -> this);

    public WelderEnergy(ItemStack stack) {
        this.stack = stack;
    }

    /** FE contenus dans la batterie de cette pile. */
    public static int stored(ItemStack stack) {
        if (!stack.hasTag()) {
            return 0;
        }
        return Math.max(0, Math.min(stack.getTag().getInt(TAG_ENERGY), MachineConfig.welderCapacity()));
    }

    /** Fixe la charge, bornée à la capacité. */
    public static void store(ItemStack stack, int fe) {
        int bounded = Math.max(0, Math.min(fe, MachineConfig.welderCapacity()));
        if (bounded == 0) {
            if (stack.hasTag()) {
                stack.getTag().remove(TAG_ENERGY);
            }
        } else {
            stack.getOrCreateTag().putInt(TAG_ENERGY, bounded);
        }
    }

    /** Dépense {@code fe} si la batterie les a ; sinon ne touche à rien et renvoie faux. */
    public static boolean spend(ItemStack stack, int fe) {
        int have = stored(stack);
        if (have < fe) {
            return false;
        }
        store(stack, have - fe);
        return true;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int accepted = Math.max(0, Math.min(maxReceive, MachineConfig.welderCapacity() - stored(stack)));
        if (accepted > 0 && !simulate) {
            store(stack, stored(stack) + accepted);
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return stored(stack);
    }

    @Override
    public int getMaxEnergyStored() {
        return MachineConfig.welderCapacity();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY ? self.cast() : LazyOptional.empty();
    }
}
