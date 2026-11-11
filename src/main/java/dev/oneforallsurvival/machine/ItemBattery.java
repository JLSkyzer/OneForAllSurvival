package dev.oneforallsurvival.machine;

import java.util.function.IntSupplier;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * La batterie d'un objet, adossée au NBT de sa pile ({@code energy}). Partagée par le poste à souder et le détecteur :
 * la capacité est passée à chaque appel, l'objet la tire de sa config. On peut la charger, jamais la vider de
 * l'extérieur : seul l'objet lui-même dépense, par {@link #spend}.
 */
public final class ItemBattery {

    private static final String TAG_ENERGY = "energy";

    private ItemBattery() {}

    /** FE contenus dans la batterie de cette pile. */
    public static int stored(ItemStack stack, int capacity) {
        if (!stack.hasTag()) {
            return 0;
        }
        return Math.max(0, Math.min(stack.getTag().getInt(TAG_ENERGY), capacity));
    }

    /** Fixe la charge, bornée à la capacité. */
    public static void store(ItemStack stack, int fe, int capacity) {
        int bounded = Math.max(0, Math.min(fe, capacity));
        if (bounded == 0) {
            if (stack.hasTag()) {
                stack.getTag().remove(TAG_ENERGY);
            }
        } else {
            stack.getOrCreateTag().putInt(TAG_ENERGY, bounded);
        }
    }

    /** Dépense {@code fe} si la batterie les a ; sinon ne touche à rien et renvoie faux. */
    public static boolean spend(ItemStack stack, int fe, int capacity) {
        int have = stored(stack, capacity);
        if (have < fe) {
            return false;
        }
        store(stack, have - fe, capacity);
        return true;
    }

    /**
     * Recharge la pile depuis {@code source} et renvoie les FE reçus. Un bloc qui laisse sortir son courant est vidé
     * par sa capability ; une machine du mod, dont la capability ne laisse rien sortir, est vidée directement dans sa
     * réserve.
     */
    public static int chargeFrom(ItemStack stack, BlockEntity source, @Nullable Direction side, int capacity) {
        int room = capacity - stored(stack, capacity);
        if (room <= 0) {
            return 0;
        }
        IEnergyStorage supplier = source.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
        int received;
        if (supplier != null && supplier.canExtract()) {
            int offered = supplier.extractEnergy(room, true);
            received = supplier.extractEnergy(offered, false);
        } else if (source instanceof ElectricMachineBlockEntity machine) {
            received = machine.drainEnergy(room);
        } else {
            return 0;
        }
        store(stack, stored(stack, capacity) + received, capacity);
        return received;
    }

    /** La capability d'énergie de la pile : elle reçoit, ne rend rien. La capacité est relue à chaque appel. */
    public static final class Provider implements IEnergyStorage, ICapabilityProvider {

        private final ItemStack stack;
        private final IntSupplier capacity;
        private final LazyOptional<IEnergyStorage> self = LazyOptional.of(() -> this);

        public Provider(ItemStack stack, IntSupplier capacity) {
            this.stack = stack;
            this.capacity = capacity;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int cap = capacity.getAsInt();
            int accepted = Math.max(0, Math.min(maxReceive, cap - stored(stack, cap)));
            if (accepted > 0 && !simulate) {
                store(stack, stored(stack, cap) + accepted, cap);
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return stored(stack, capacity.getAsInt());
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity.getAsInt();
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
}
