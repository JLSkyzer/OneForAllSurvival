package dev.oneforallsurvival.grid;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Le menu du tableau de délestage : aucune case, seulement les nombres que le serveur tient à jour (rang, état et FE
 * de chaque sortie, état du tableau). Les boutons « monter » et « descendre » passent par le clic de bouton de menu
 * de vanilla ({@link #clickMenuButton}) : aucun paquet propre, le serveur vérifie tout.
 */
public final class LoadShedderMenu extends AbstractContainerMenu {

    /** Distance maximale du joueur au tableau, au carré. */
    private static final double MAX_DISTANCE_SQR = 64;
    private static final int BUTTONS = LoadShedderBlockEntity.OUTPUTS * 2;

    private final BlockPos pos;
    private final ContainerData data;
    /** Le vrai tableau côté serveur, {@code null} côté client. */
    @Nullable
    private final LoadShedderBlockEntity entity;

    /** Côté serveur : les nombres du vrai tableau. */
    public LoadShedderMenu(int containerId, Inventory inventory, LoadShedderBlockEntity be, BlockPos pos) {
        this(containerId, be.data(), pos, be);
    }

    /** Côté client : des nombres que le serveur tient à jour. */
    public static LoadShedderMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new LoadShedderMenu(containerId, new SimpleContainerData(LoadShedderBlockEntity.DATA_COUNT),
                buf.readBlockPos(), null);
    }

    private LoadShedderMenu(int containerId, ContainerData data, BlockPos pos, @Nullable LoadShedderBlockEntity entity) {
        super(GridRegistry.LOAD_SHEDDER_MENU.get(), containerId);
        this.pos = pos.immutable();
        this.data = data;
        this.entity = entity;
        addDataSlots(data);
    }

    /** Le numéro du bouton qui monte ({@code up}) ou descend une sortie d'un rang. */
    public static int buttonId(int output, boolean up) {
        return output * 2 + (up ? 0 : 1);
    }

    /** Rang de la sortie, de 0 (servie la première) à 3. */
    public int rank(int output) {
        return data.get(LoadShedderBlockEntity.DATA_RANK + output);
    }

    public OutputStatus status(int output) {
        return OutputStatus.byOrdinal(data.get(LoadShedderBlockEntity.DATA_STATUS + output));
    }

    /** FE livrés à la sortie au dernier tick achevé, recomposés de leurs deux moitiés. */
    public int delivered(int output) {
        int low = data.get(LoadShedderBlockEntity.DATA_FE_LOW + output) & 0xFFFF;
        int high = data.get(LoadShedderBlockEntity.DATA_FE_HIGH + output) & 0xFFFF;
        return (high << 16) | low;
    }

    /** Position de l'état dans {@link dev.oneforallsurvival.machine.MachineState#values()}. */
    public int stateOrdinal() {
        return data.get(LoadShedderBlockEntity.DATA_STATE);
    }

    /** Côté serveur : monte ou descend la sortie d'un rang. Faux si le bouton n'existe pas ou ne peut rien faire. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null || id < 0 || id >= BUTTONS) {
            return false;
        }
        int output = id / 2;
        int target = entity.rank(output) + (id % 2 == 0 ? -1 : 1);
        if (target < 0 || target >= LoadShedderBlockEntity.OUTPUTS) {
            return false;
        }
        entity.setPriority(output, target);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // Un tableau cassé puis reposé au même endroit passe le contrôle du bloc : seule l'instance trahit le remplacement.
        if (entity != null && entity.isRemoved()) {
            return false;
        }
        return player.level().getBlockState(pos).is(GridRegistry.LOAD_SHEDDER.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }
}
