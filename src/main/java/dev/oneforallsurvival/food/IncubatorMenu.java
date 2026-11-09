package dev.oneforallsurvival.food;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Le menu de la couveuse : les quatre places, l'inventaire du joueur et dix nombres suivis par le serveur (quatre
 * progressions, durée, énergie, capacité, état, coupure, durée de coupure), que l'écran lit côté client.
 */
public final class IncubatorMenu extends AbstractContainerMenu {

    /** Distance maximale du joueur à la couveuse, au carré. */
    private static final double MAX_DISTANCE_SQR = 64;
    private static final int INVENTORY_SLOTS = 36;

    // Positions des places (pixels du menu), partagées avec l'écran.
    public static final int SLOT_X = 17;
    public static final int SLOT_STEP = 36;
    public static final int SLOT_Y = 20;

    private final BlockPos pos;
    private final ContainerData data;
    /** La vraie couveuse côté serveur, {@code null} côté client : sert à voir si elle a été retirée du monde. */
    @Nullable
    private final IncubatorBlockEntity entity;

    /** Côté serveur : les places et les nombres de la vraie couveuse. */
    public IncubatorMenu(int containerId, Inventory inventory, IncubatorBlockEntity be, BlockPos pos) {
        this(containerId, inventory, be.inventory(), be.data(), pos, be::setChanged, be);
    }

    /** Côté client : des places vides que le serveur remplit, et des nombres qu'il tient à jour. */
    public static IncubatorMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new IncubatorMenu(containerId, inventory, IncubatorBlockEntity.newEggHandler(() -> {}),
                new SimpleContainerData(IncubatorBlockEntity.DATA_COUNT), buf.readBlockPos(), () -> {}, null);
    }

    private IncubatorMenu(int containerId, Inventory inventory, IItemHandler eggs, ContainerData data, BlockPos pos,
            Runnable onChanged, @Nullable IncubatorBlockEntity entity) {
        super(FoodRegistry.INCUBATOR_MENU.get(), containerId);
        this.entity = entity;
        this.pos = pos.immutable();
        this.data = data;
        for (int i = 0; i < IncubatorBlockEntity.SLOTS; i++) {
            addSlot(new EggSlot(eggs, i, SLOT_X + i * SLOT_STEP, SLOT_Y, onChanged));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 98 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 156));
        }
        addDataSlots(data);
    }

    /**
     * Une place de la couveuse. {@code moveItemStackTo} (clic majuscule) modifie la pile en place puis appelle
     * {@link #setChanged()} : sans ce rappel, le block entity ne saurait pas qu'il doit se sauvegarder.
     */
    private static final class EggSlot extends SlotItemHandler {
        private final Runnable onChanged;

        EggSlot(IItemHandler handler, int index, int x, int y, Runnable onChanged) {
            super(handler, index, x, y);
            this.onChanged = onChanged;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            onChanged.run();
        }
    }

    /** Progression de l'œuf de cette place, en ticks de travail. */
    public int progress(int slot) {
        return data.get(IncubatorBlockEntity.DATA_PROGRESS + slot);
    }

    public int total() {
        return data.get(IncubatorBlockEntity.DATA_TOTAL);
    }

    public int energy() {
        return data.get(IncubatorBlockEntity.DATA_ENERGY);
    }

    public int capacity() {
        return data.get(IncubatorBlockEntity.DATA_CAPACITY);
    }

    /** Position de l'état dans {@link dev.oneforallsurvival.machine.MachineState#values()}. */
    public int stateOrdinal() {
        return data.get(IncubatorBlockEntity.DATA_STATE);
    }

    /** Ticks passés sans courant. */
    public int outage() {
        return data.get(IncubatorBlockEntity.DATA_OUTAGE);
    }

    /** Ticks sans courant au bout desquels la couvée est perdue. */
    public int outageTotal() {
        return data.get(IncubatorBlockEntity.DATA_OUTAGE_TOTAL);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean moved;
        if (index < IncubatorBlockEntity.SLOTS) {
            moved = moveItemStackTo(stack, IncubatorBlockEntity.SLOTS, IncubatorBlockEntity.SLOTS + INVENTORY_SLOTS, true);
        } else {
            // Les places refusent d'elles-mêmes ce qui n'est pas un œuf de la liste (mayPlace).
            // Une seule place est garnie par appel (un œuf par place) : on répète tant qu'il reste de la place.
            moved = false;
            while (!stack.isEmpty() && moveItemStackTo(stack, 0, IncubatorBlockEntity.SLOTS, false)) {
                moved = true;
            }
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        // Une couveuse cassée puis reposée au même endroit passe le contrôle du bloc : seule l'instance trahit le remplacement.
        if (entity != null && entity.isRemoved()) {
            return false;
        }
        return player.level().getBlockState(pos).is(FoodRegistry.INCUBATOR.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }
}
