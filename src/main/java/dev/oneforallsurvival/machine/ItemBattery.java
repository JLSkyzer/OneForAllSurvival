package dev.oneforallsurvival.machine;

import java.util.function.IntSupplier;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * La batterie d'un objet, adossée au NBT de sa pile ({@code energy}). Partagée par le poste à souder, le détecteur et
 * la lampe frontale : la capacité est passée à chaque appel, l'objet la tire de sa config. On peut la charger, jamais
 * la vider de l'extérieur : seul l'objet lui-même dépense, par {@link #spend} ou par {@link Prepaid}. Les gestes
 * communs (recharge accroupi sur une source, largeur de barre, animation de la main) sont ici aussi.
 */
public final class ItemBattery {

    private static final String TAG_ENERGY = "energy";
    /** Ticks payés d'un coup par un objet qui consomme en continu. */
    public static final int PERIOD = 20;
    private static final int BAR_WIDTH = 13;

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

    /** Le coût d'une période de {@link #PERIOD} ticks, borné à la capacité (sinon l'objet ne démarrerait jamais). */
    public static int periodCost(int perTick, int capacity) {
        return (int) Math.min((long) perTick * PERIOD, capacity);
    }

    /** Largeur de la barre d'objet (0 à 13) pour cette charge. */
    public static int barWidth(ItemStack stack, int capacity) {
        return Math.round(BAR_WIDTH * (float) stored(stack, capacity) / capacity);
    }

    /** La charge change à chaque dépense : ce n'est pas un nouvel objet, la main ne doit pas rebondir. */
    public static boolean reequip(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    /**
     * Le geste de recharge d'un objet à batterie : accroupi, clic droit sur une source. Debout, ou sur un bloc qui n'a
     * pas d'énergie à rendre (ni machine du mod, ni capability qui laisse sortir), le clic passe : il ouvre l'écran
     * du bloc ou fait son usage normal, il ne le vide pas.
     *
     * @param messagePrefix clé de langue sans suffixe : {@code <prefix>.charged} et {@code <prefix>.not_charged}
     */
    public static InteractionResult useOnSource(ItemStack stack, UseOnContext context, int capacity, String messagePrefix) {
        if (context.getPlayer() == null || !context.getPlayer().isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockEntity target = level.getBlockEntity(context.getClickedPos());
        if (target == null) {
            return InteractionResult.PASS;
        }
        boolean machine = target instanceof ElectricMachineBlockEntity;
        if (!machine) {
            IEnergyStorage supplier = target.getCapability(ForgeCapabilities.ENERGY, context.getClickedFace()).orElse(null);
            if (supplier == null || !supplier.canExtract()) {
                return InteractionResult.PASS;
            }
        }
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            int received = chargeFrom(stack, target, context.getClickedFace(), capacity);
            player.displayClientMessage(Component.translatable(
                    messagePrefix + (received > 0 ? ".charged" : ".not_charged"), received), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Les ticks déjà payés d'un objet qui consomme en continu : la dépense part par blocs de {@link #PERIOD} ticks,
     * pour que la pile (donc son NBT, envoyé à tous ceux qui voient le porteur) ne change qu'une fois par seconde.
     * Transitoire : vit côté serveur, jamais dans la pile.
     */
    public static final class Prepaid {

        private int remaining;

        /**
         * Couvre un tick de plus. Quand les ticks payés sont écoulés, paie le bloc suivant ({@code perTick} x
         * {@link #PERIOD}, borné à la capacité) ; renvoie faux, sans rien dépenser, si la batterie ne le couvre pas.
         */
        public boolean pay(ItemStack stack, int perTick, int capacity) {
            if (remaining <= 0) {
                if (!spend(stack, periodCost(perTick, capacity), capacity)) {
                    return false;
                }
                remaining = PERIOD;
            }
            remaining--;
            return true;
        }
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
