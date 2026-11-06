package dev.oneforallsurvival.repair;

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
 * Le menu du bain : les trois cases, l'inventaire du joueur et cinq nombres suivis par le serveur
 * (progression, durée, énergie, capacité, état), que l'écran lit côté client.
 */
public final class GalvanicBathMenu extends AbstractContainerMenu {

    /** Distance maximale du joueur au bain, au carré. */
    private static final double MAX_DISTANCE_SQR = 64;
    private static final int BATH_SLOTS = 3;
    private static final int INVENTORY_SLOTS = 36;

    private final BlockPos pos;
    private final IItemHandler bath;
    private final ContainerData data;

    /** Côté serveur : les cases et les nombres du vrai bain. */
    public GalvanicBathMenu(int containerId, Inventory inventory, GalvanicBathBlockEntity be, BlockPos pos) {
        this(containerId, inventory, be.inventory(), be.data(), pos);
    }

    /** Côté client : des cases vides que le serveur remplit, et des nombres qu'il tient à jour. */
    public static GalvanicBathMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new GalvanicBathMenu(containerId, inventory, new ItemStackHandler(BATH_SLOTS),
                new SimpleContainerData(GalvanicBathBlockEntity.DATA_COUNT), buf.readBlockPos());
    }

    private GalvanicBathMenu(int containerId, Inventory inventory, IItemHandler bath, ContainerData data, BlockPos pos) {
        super(RepairRegistry.GALVANIC_BATH_MENU.get(), containerId);
        this.pos = pos.immutable();
        this.bath = bath;
        this.data = data;
        addSlot(new SlotItemHandler(bath, GalvanicBathBlockEntity.SLOT_PIECE, PIECE_X, PIECE_Y));
        addSlot(new SlotItemHandler(bath, GalvanicBathBlockEntity.SLOT_ZINC, ZINC_X, ZINC_Y));
        // La sortie ne reçoit rien du joueur, côté client comme côté serveur.
        addSlot(new SlotItemHandler(bath, GalvanicBathBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
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

    // Positions des cases (pixels du menu), partagées avec l'écran.
    public static final int PIECE_X = 36;
    public static final int PIECE_Y = 22;
    public static final int ZINC_X = 36;
    public static final int ZINC_Y = 48;
    public static final int OUTPUT_X = 134;
    public static final int OUTPUT_Y = 35;

    public int progress() {
        return data.get(GalvanicBathBlockEntity.DATA_PROGRESS);
    }

    public int total() {
        return data.get(GalvanicBathBlockEntity.DATA_TOTAL);
    }

    public int energy() {
        return data.get(GalvanicBathBlockEntity.DATA_ENERGY);
    }

    public int capacity() {
        return data.get(GalvanicBathBlockEntity.DATA_CAPACITY);
    }

    /** Position de l'état dans {@link dev.oneforallsurvival.machine.MachineState#values()}. */
    public int stateOrdinal() {
        return data.get(GalvanicBathBlockEntity.DATA_STATE);
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
        if (index < BATH_SLOTS) {
            moved = moveItemStackTo(stack, BATH_SLOTS, BATH_SLOTS + INVENTORY_SLOTS, true);
        } else {
            // Les cases du bain refusent d'elles-mêmes ce qui ne leur convient pas (mayPlace).
            moved = moveItemStackTo(stack, GalvanicBathBlockEntity.SLOT_PIECE, GalvanicBathBlockEntity.SLOT_OUTPUT, false);
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
        return player.level().getBlockState(pos).is(RepairRegistry.GALVANIC_BATH.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }
}
