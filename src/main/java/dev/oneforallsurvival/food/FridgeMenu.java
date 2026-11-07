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
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

/**
 * Le menu de l'armoire : ses 27 cases, l'inventaire du joueur et trois nombres suivis par le serveur (énergie,
 * capacité, état). Les cases sont des {@link Slot} simples posées sur un {@code Container} qui enveloppe le
 * gestionnaire d'objets : c'est ce qui permet à la péremption de les traiter comme celles d'un coffre (déchet à
 * l'ouverture, regroupement à la fermeture), alors que des {@code SlotItemHandler} en seraient exclues.
 */
public final class FridgeMenu extends AbstractContainerMenu {

    /** Distance maximale du joueur à l'armoire, au carré. */
    private static final double MAX_DISTANCE_SQR = 64;
    private static final int INVENTORY_SLOTS = 36;
    private static final int COLUMNS = 9;

    private final BlockPos pos;
    private final ContainerData data;
    /** La vraie armoire côté serveur, {@code null} côté client : sert à voir si elle a été retirée du monde. */
    @Nullable
    private final FridgeBlockEntity entity;

    /** Côté serveur : les cases et les nombres de la vraie armoire. */
    public FridgeMenu(int containerId, Inventory inventory, FridgeBlockEntity be, BlockPos pos) {
        this(containerId, inventory, be.inventory(), be.data(), pos, be::setChanged, be);
    }

    /** Côté client : des cases vides que le serveur remplit, et des nombres qu'il tient à jour. */
    public static FridgeMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new FridgeMenu(containerId, inventory, new ItemStackHandler(FridgeBlockEntity.SLOTS),
                new SimpleContainerData(FridgeBlockEntity.DATA_COUNT), buf.readBlockPos(), () -> {}, null);
    }

    private FridgeMenu(int containerId, Inventory inventory, IItemHandlerModifiable cabinet, ContainerData data,
            BlockPos pos, Runnable onChanged, @Nullable FridgeBlockEntity entity) {
        super(FoodRegistry.FRIDGE_MENU.get(), containerId);
        this.entity = entity;
        this.pos = pos.immutable();
        this.data = data;
        // setChanged est appelé par moveItemStackTo après une modification en place : sans lui, l'armoire ne
        // saurait pas qu'elle doit se sauvegarder.
        RecipeWrapper container = new RecipeWrapper(cabinet) {
            @Override
            public void setChanged() {
                onChanged.run();
            }

            /** RecipeWrapper renvoie 0 : une case posée dessus ne recevrait jamais rien. */
            @Override
            public int getMaxStackSize() {
                return 64;
            }
        };
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new Slot(container, row * COLUMNS + col, 8 + col * 18, CABINET_Y + row * 18));
            }
        }
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

    // Positions (pixels du menu), partagées avec l'écran.
    public static final int CABINET_Y = 30;
    public static final int INVENTORY_Y = 100;
    public static final int HOTBAR_Y = 158;

    public int energy() {
        return data.get(FridgeBlockEntity.DATA_ENERGY);
    }

    public int capacity() {
        return data.get(FridgeBlockEntity.DATA_CAPACITY);
    }

    /** Position de l'état dans {@link dev.oneforallsurvival.machine.MachineState#values()}. */
    public int stateOrdinal() {
        return data.get(FridgeBlockEntity.DATA_STATE);
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
        if (index < FridgeBlockEntity.SLOTS) {
            moved = moveItemStackTo(stack, FridgeBlockEntity.SLOTS, FridgeBlockEntity.SLOTS + INVENTORY_SLOTS, true);
        } else {
            moved = moveItemStackTo(stack, 0, FridgeBlockEntity.SLOTS, false);
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
        // Une armoire cassée puis reposée au même endroit passe le contrôle du bloc : seule l'instance trahit le remplacement.
        if (entity != null && entity.isRemoved()) {
            return false;
        }
        return player.level().getBlockState(pos).is(FoodRegistry.FRIDGE.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }
}
