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
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Le menu du dessalinisateur : ses trois cases, l'inventaire du joueur et sept nombres suivis par le serveur
 * (énergie, capacité, état, les deux cuves, la progression, la durée du cycle). Seule la case d'entrée reçoit un
 * objet, et seulement une bouteille de verre ; les deux cases de sortie se vident à la main ou au clic majuscule.
 */
public final class DesalinatorMenu extends AbstractContainerMenu {

    /** Distance maximale du joueur à la machine, au carré. */
    private static final double MAX_DISTANCE_SQR = 64;
    private static final int INVENTORY_SLOTS = 36;
    private static final int COLUMNS = 9;

    // Positions (pixels du menu), partagées avec l'écran.
    public static final int EMPTY_X = 36;
    public static final int EMPTY_Y = 26;
    public static final int FILLED_X = 104;
    public static final int FILLED_Y = 26;
    public static final int SALT_X = 126;
    public static final int SALT_Y = 50;
    public static final int INVENTORY_Y = 100;
    public static final int HOTBAR_Y = 158;

    private final BlockPos pos;
    private final ContainerData data;
    /** La vraie machine côté serveur, {@code null} côté client : sert à voir si elle a été retirée du monde. */
    @Nullable
    private final DesalinatorBlockEntity entity;

    /** Côté serveur : les cases et les nombres de la vraie machine. */
    public DesalinatorMenu(int containerId, Inventory inventory, DesalinatorBlockEntity be, BlockPos pos) {
        this(containerId, inventory, be.inventory(), be.data(), pos, be::setChanged, be);
    }

    /** Côté client : des cases vides que le serveur remplit, et des nombres qu'il tient à jour. */
    public static DesalinatorMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new DesalinatorMenu(containerId, inventory, new ItemStackHandler(DesalinatorBlockEntity.SLOTS),
                new SimpleContainerData(DesalinatorBlockEntity.DATA_COUNT), buf.readBlockPos(), () -> {}, null);
    }

    private DesalinatorMenu(int containerId, Inventory inventory, IItemHandlerModifiable machine, ContainerData data,
            BlockPos pos, Runnable onChanged, @Nullable DesalinatorBlockEntity entity) {
        super(FoodRegistry.DESALINATOR_MENU.get(), containerId);
        this.entity = entity;
        this.pos = pos.immutable();
        this.data = data;
        addSlot(new MachineSlot(machine, DesalinatorBlockEntity.SLOT_SALT, SALT_X, SALT_Y, onChanged, false));
        addSlot(new MachineSlot(machine, DesalinatorBlockEntity.SLOT_EMPTY, EMPTY_X, EMPTY_Y, onChanged, true));
        addSlot(new MachineSlot(machine, DesalinatorBlockEntity.SLOT_FILLED, FILLED_X, FILLED_Y, onChanged, false));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new Slot(inventory, 9 + row * COLUMNS + col, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < COLUMNS; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    /**
     * Une case de la machine. {@code moveItemStackTo} change les piles en place puis appelle {@link #setChanged} :
     * sans ce crochet, la machine ne saurait pas qu'elle doit se sauvegarder.
     */
    private static final class MachineSlot extends SlotItemHandler {

        private final Runnable onChanged;
        private final boolean acceptsBottle;

        MachineSlot(IItemHandlerModifiable handler, int index, int x, int y, Runnable onChanged, boolean acceptsBottle) {
            super(handler, index, x, y);
            this.onChanged = onChanged;
            this.acceptsBottle = acceptsBottle;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return acceptsBottle && stack.is(Items.GLASS_BOTTLE);
        }

        @Override
        public void setChanged() {
            onChanged.run();
        }
    }

    public int energy() {
        return data.get(DesalinatorBlockEntity.DATA_ENERGY);
    }

    public int capacity() {
        return data.get(DesalinatorBlockEntity.DATA_CAPACITY);
    }

    /** Position de l'état dans {@link dev.oneforallsurvival.machine.MachineState#values()}. */
    public int stateOrdinal() {
        return data.get(DesalinatorBlockEntity.DATA_STATE);
    }

    public int waterAmount() {
        return data.get(DesalinatorBlockEntity.DATA_WATER);
    }

    public int potableAmount() {
        return data.get(DesalinatorBlockEntity.DATA_POTABLE);
    }

    public int progress() {
        return data.get(DesalinatorBlockEntity.DATA_PROGRESS);
    }

    public int cycleLength() {
        return data.get(DesalinatorBlockEntity.DATA_CYCLE);
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
        if (index < DesalinatorBlockEntity.SLOTS) {
            moved = moveItemStackTo(stack, DesalinatorBlockEntity.SLOTS,
                    DesalinatorBlockEntity.SLOTS + INVENTORY_SLOTS, true);
        } else {
            // Seule la case d'entrée reçoit quelque chose, et seulement une bouteille de verre.
            moved = moveItemStackTo(stack, DesalinatorBlockEntity.SLOT_EMPTY, DesalinatorBlockEntity.SLOT_EMPTY + 1, false);
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
        // Une machine cassée puis reposée au même endroit passe le contrôle du bloc : seule l'instance trahit le remplacement.
        if (entity != null && entity.isRemoved()) {
            return false;
        }
        return player.level().getBlockState(pos).is(FoodRegistry.DESALINATOR.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }
}
