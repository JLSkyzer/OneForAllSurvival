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
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Le menu de la marmite de cantine : les six cases d'ingrédients, le récipient, la sortie, l'inventaire du joueur et
 * sept nombres suivis par le serveur (progression, durée, énergie, capacité, état, récipients exigés et manquants),
 * que l'écran lit côté client.
 */
public final class CanteenPotMenu extends AbstractContainerMenu {

    /** Distance maximale du joueur à la marmite, au carré. */
    private static final double MAX_DISTANCE_SQR = 64;
    private static final int INVENTORY_SLOTS = 36;
    /** Les cases qui reçoivent un clic majuscule : les ingrédients puis le récipient. */
    private static final int LOADABLE = CanteenPotBlockEntity.SLOT_OUTPUT;

    private final BlockPos pos;
    private final ContainerData data;
    /** La vraie marmite côté serveur, {@code null} côté client : sert à voir si elle a été retirée du monde. */
    @Nullable
    private final CanteenPotBlockEntity entity;

    /** Côté serveur : les cases et les nombres de la vraie marmite. */
    public CanteenPotMenu(int containerId, Inventory inventory, CanteenPotBlockEntity be, BlockPos pos) {
        this(containerId, inventory, be.inventory(), be.data(), pos, be::setChanged, be);
    }

    /** Côté client : des cases vides que le serveur remplit, et des nombres qu'il tient à jour. */
    public static CanteenPotMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new CanteenPotMenu(containerId, inventory, new ItemStackHandler(CanteenPotBlockEntity.SLOTS),
                new SimpleContainerData(CanteenPotBlockEntity.DATA_COUNT), buf.readBlockPos(), () -> {}, null);
    }

    private CanteenPotMenu(int containerId, Inventory inventory, IItemHandler pot, ContainerData data, BlockPos pos,
            Runnable onChanged, @Nullable CanteenPotBlockEntity entity) {
        super(FoodRegistry.CANTEEN_POT_MENU.get(), containerId);
        this.entity = entity;
        this.pos = pos.immutable();
        this.data = data;
        for (int i = 0; i < CanteenPotBlockEntity.INPUTS; i++) {
            addSlot(new PotSlot(pot, CanteenPotBlockEntity.SLOT_INPUT_FIRST + i, INPUT_X + (i % 3) * 18,
                    INPUT_Y + (i / 3) * 18, true, onChanged));
        }
        addSlot(new PotSlot(pot, CanteenPotBlockEntity.SLOT_CONTAINER, CONTAINER_X, CONTAINER_Y, true, onChanged));
        // La sortie ne reçoit rien du joueur, côté client comme côté serveur.
        addSlot(new PotSlot(pot, CanteenPotBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y, false, onChanged));
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
     * Une case de la marmite. {@code moveItemStackTo} (clic majuscule) modifie la pile en place puis appelle
     * {@link #setChanged()} : sans ce rappel, le block entity ne saurait pas qu'il doit se sauvegarder.
     */
    private static final class PotSlot extends SlotItemHandler {
        private final boolean acceptsFromPlayer;
        private final Runnable onChanged;

        PotSlot(IItemHandler handler, int index, int x, int y, boolean acceptsFromPlayer, Runnable onChanged) {
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
    public static final int INPUT_X = 26;
    public static final int INPUT_Y = 18;
    public static final int CONTAINER_X = 26;
    public static final int CONTAINER_Y = 60;
    public static final int OUTPUT_X = 134;
    public static final int OUTPUT_Y = 27;

    public int progress() {
        return data.get(CanteenPotBlockEntity.DATA_PROGRESS);
    }

    public int total() {
        return data.get(CanteenPotBlockEntity.DATA_TOTAL);
    }

    public int energy() {
        return data.get(CanteenPotBlockEntity.DATA_ENERGY);
    }

    public int capacity() {
        return data.get(CanteenPotBlockEntity.DATA_CAPACITY);
    }

    /** Position de l'état dans {@link dev.oneforallsurvival.machine.MachineState#values()}. */
    public int stateOrdinal() {
        return data.get(CanteenPotBlockEntity.DATA_STATE);
    }

    /** Combien de récipients la fournée exige (0 sans recette, ou si la recette n'en veut pas). */
    public int containersNeeded() {
        return data.get(CanteenPotBlockEntity.DATA_CONTAINERS_NEEDED);
    }

    /** Combien de récipients manquent encore pour lancer la fournée. */
    public int containersMissing() {
        return data.get(CanteenPotBlockEntity.DATA_CONTAINERS_MISSING);
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
        if (index < CanteenPotBlockEntity.SLOTS) {
            moved = moveItemStackTo(stack, CanteenPotBlockEntity.SLOTS, CanteenPotBlockEntity.SLOTS + INVENTORY_SLOTS, true);
        } else {
            // Les cases refusent d'elles-mêmes ce qui ne leur convient pas (mayPlace). Une pile n'emplit qu'une case vide
            // par appel : on répète tant qu'il en reste et qu'une case la reçoit.
            moved = false;
            while (!stack.isEmpty() && moveItemStackTo(stack, CanteenPotBlockEntity.SLOT_INPUT_FIRST, LOADABLE, false)) {
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
        // Une marmite cassée puis reposée au même endroit passe le contrôle du bloc : seule l'instance trahit le remplacement.
        if (entity != null && entity.isRemoved()) {
            return false;
        }
        return player.level().getBlockState(pos).is(FoodRegistry.CANTEEN_POT.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }
}
