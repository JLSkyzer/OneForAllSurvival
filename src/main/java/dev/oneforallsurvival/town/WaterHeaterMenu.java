package dev.oneforallsurvival.town;

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

/**
 * Le menu du chauffe-eau : aucune case, l'inventaire du joueur et cinq nombres suivis par le serveur (énergie,
 * capacité, état, eau potable, eau chaude).
 */
public final class WaterHeaterMenu extends AbstractContainerMenu {

    /** Distance maximale du joueur à la machine, au carré. */
    private static final double MAX_DISTANCE_SQR = 64;
    private static final int COLUMNS = 9;

    // Positions (pixels du menu), partagées avec l'écran.
    public static final int INVENTORY_Y = 100;
    public static final int HOTBAR_Y = 158;

    private final BlockPos pos;
    private final ContainerData data;
    /** La vraie machine côté serveur, {@code null} côté client : sert à voir si elle a été retirée du monde. */
    @Nullable
    private final WaterHeaterBlockEntity entity;

    /** Côté serveur : les nombres de la vraie machine. */
    public WaterHeaterMenu(int containerId, Inventory inventory, WaterHeaterBlockEntity be, BlockPos pos) {
        this(containerId, inventory, be.data(), pos, be);
    }

    /** Côté client : des nombres que le serveur tient à jour. */
    public static WaterHeaterMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new WaterHeaterMenu(containerId, inventory, new SimpleContainerData(WaterHeaterBlockEntity.DATA_COUNT),
                buf.readBlockPos(), null);
    }

    private WaterHeaterMenu(int containerId, Inventory inventory, ContainerData data, BlockPos pos,
            @Nullable WaterHeaterBlockEntity entity) {
        super(TownRegistry.WATER_HEATER_MENU.get(), containerId);
        this.entity = entity;
        this.pos = pos.immutable();
        this.data = data;
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

    public int energy() {
        return data.get(WaterHeaterBlockEntity.DATA_ENERGY);
    }

    public int capacity() {
        return data.get(WaterHeaterBlockEntity.DATA_CAPACITY);
    }

    /** Position de l'état dans {@link dev.oneforallsurvival.machine.MachineState#values()}. */
    public int stateOrdinal() {
        return data.get(WaterHeaterBlockEntity.DATA_STATE);
    }

    public int potableAmount() {
        return data.get(WaterHeaterBlockEntity.DATA_POTABLE);
    }

    public int hotAmount() {
        return data.get(WaterHeaterBlockEntity.DATA_HOT);
    }

    /** Rien à déplacer : la machine n'a pas de case. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // Une machine cassée puis reposée au même endroit passe le contrôle du bloc : seule l'instance trahit le remplacement.
        if (entity != null && entity.isRemoved()) {
            return false;
        }
        return player.level().getBlockState(pos).is(TownRegistry.WATER_HEATER.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }
}
