package dev.oneforallsurvival.food;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Le menu du séchoir : les trois cases, l'inventaire du joueur et cinq nombres suivis par le serveur
 * (progression, durée, énergie, capacité, état), que l'écran lit côté client.
 */
public final class DryerMenu extends AbstractContainerMenu {

    /** Distance maximale du joueur au séchoir, au carré. */
    private static final double MAX_DISTANCE_SQR = 64;
    private static final int DRYER_SLOTS = 3;
    private static final int INVENTORY_SLOTS = 36;

    private final BlockPos pos;
    private final IItemHandler dryer;
    private final ContainerData data;
    /** Le vrai séchoir côté serveur, {@code null} côté client : sert à voir si le séchoir a été retiré du monde. */
    @Nullable
    private final DryerBlockEntity entity;

    /** Côté serveur : les cases et les nombres du vrai séchoir. */
    public DryerMenu(int containerId, Inventory inventory, DryerBlockEntity be, BlockPos pos) {
        this(containerId, inventory, be.inventory(), be.data(), pos, be::setChanged, be);
    }

    /** Côté client : des cases vides que le serveur remplit, et des nombres qu'il tient à jour. */
    public static DryerMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new DryerMenu(containerId, inventory, new ItemStackHandler(DRYER_SLOTS),
                new SimpleContainerData(DryerBlockEntity.DATA_COUNT), buf.readBlockPos(), () -> {}, null);
    }

    private DryerMenu(int containerId, Inventory inventory, IItemHandler dryer, ContainerData data, BlockPos pos,
            Runnable onChanged, @Nullable DryerBlockEntity entity) {
        super(FoodRegistry.DRYER_MENU.get(), containerId);
        this.entity = entity;
        this.pos = pos.immutable();
        this.dryer = dryer;
        this.data = data;
        addSlot(new DryerSlot(dryer, DryerBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y, true, onChanged));
        addSlot(new DryerSlot(dryer, DryerBlockEntity.SLOT_SALT, SALT_X, SALT_Y, true, onChanged));
        // La sortie ne reçoit rien du joueur, côté client comme côté serveur.
        addSlot(new DryerSlot(dryer, DryerBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y, false, onChanged));
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
     * Une case du séchoir. {@code moveItemStackTo} (clic majuscule) modifie la pile en place puis appelle
     * {@link #setChanged()} : sans ce rappel, le block entity ne saurait pas qu'il doit se sauvegarder.
     */
    private static final class DryerSlot extends SlotItemHandler {
        private final boolean acceptsFromPlayer;
        private final Runnable onChanged;

        DryerSlot(IItemHandler handler, int index, int x, int y, boolean acceptsFromPlayer, Runnable onChanged) {
            super(handler, index, x, y);
            this.acceptsFromPlayer = acceptsFromPlayer;
            this.onChanged = onChanged;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return acceptsFromPlayer && super.mayPlace(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            onChanged.run();
        }
    }

    // Positions des cases (pixels du menu), partagées avec l'écran.
    public static final int INPUT_X = 36;
    public static final int INPUT_Y = 22;
    public static final int SALT_X = 36;
    public static final int SALT_Y = 48;
    public static final int OUTPUT_X = 134;
    public static final int OUTPUT_Y = 35;

    public int progress() {
        return data.get(DryerBlockEntity.DATA_PROGRESS);
    }

    public int total() {
        return data.get(DryerBlockEntity.DATA_TOTAL);
    }

    public int energy() {
        return data.get(DryerBlockEntity.DATA_ENERGY);
    }

    public int capacity() {
        return data.get(DryerBlockEntity.DATA_CAPACITY);
    }

    /** Position de l'état dans {@link dev.oneforallsurvival.machine.MachineState#values()}. */
    public int stateOrdinal() {
        return data.get(DryerBlockEntity.DATA_STATE);
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
        if (index < DRYER_SLOTS) {
            moved = moveItemStackTo(stack, DRYER_SLOTS, DRYER_SLOTS + INVENTORY_SLOTS, true);
        } else {
            // Les cases du séchoir refusent d'elles-mêmes ce qui ne leur convient pas (mayPlace).
            moved = moveItemStackTo(stack, DryerBlockEntity.SLOT_INPUT, DryerBlockEntity.SLOT_OUTPUT, false);
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
        // Un séchoir cassé puis reposé au même endroit passe le contrôle du bloc : seul l'instance trahit le remplacement.
        if (entity != null && entity.isRemoved()) {
            return false;
        }
        return player.level().getBlockState(pos).is(FoodRegistry.DRYER.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }
}
