package dev.oneforallsurvival.explore;

import java.util.ArrayList;
import java.util.List;
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
 * Le menu du récepteur : aucune case. Trois nombres suivis par le serveur (réserve, fréquence, écoute) et l'archive,
 * envoyée une fois à l'ouverture (un message qui arrive écran ouvert se lit à la prochaine ouverture).
 */
public final class ReceiverMenu extends AbstractContainerMenu {

    /** Distance maximale du joueur au récepteur, au carré (8 blocs) : celle des paquets de fréquence aussi. */
    public static final double MAX_DISTANCE_SQR = 64;

    private final BlockPos pos;
    private final ContainerData data;
    /** Le vrai récepteur côté serveur, {@code null} côté client. */
    @Nullable
    private final ReceiverBlockEntity entity;
    /** Côté client : l'archive reçue à l'ouverture. */
    private final List<SignalMessage> snapshot;

    /** Côté serveur. */
    public ReceiverMenu(int containerId, Inventory inventory, ReceiverBlockEntity be, BlockPos pos) {
        this(containerId, be.data(), pos, be, List.of());
    }

    /** Côté client : ce que {@link #writeOpenData} a écrit. */
    public static ReceiverMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = Math.min(buf.readVarInt(), ReceiverBlockEntity.MAX_ARCHIVE);
        List<SignalMessage> archive = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            archive.add(SignalMessage.decode(buf));
        }
        return new ReceiverMenu(containerId, new SimpleContainerData(ReceiverBlockEntity.DATA_COUNT), pos, null, archive);
    }

    /** Les données d'ouverture : la position puis l'archive, le message le plus récent en premier. */
    public static void writeOpenData(FriendlyByteBuf buf, BlockPos pos, ReceiverBlockEntity be) {
        buf.writeBlockPos(pos);
        List<SignalMessage> archive = be.archive();
        buf.writeVarInt(archive.size());
        archive.forEach(message -> message.encode(buf));
    }

    private ReceiverMenu(int containerId, ContainerData data, BlockPos pos, @Nullable ReceiverBlockEntity entity,
            List<SignalMessage> snapshot) {
        super(ExploreRegistry.RECEIVER_MENU.get(), containerId);
        this.pos = pos.immutable();
        this.data = data;
        this.entity = entity;
        this.snapshot = snapshot;
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int energy() {
        return data.get(ReceiverBlockEntity.DATA_ENERGY);
    }

    public int frequency() {
        return data.get(ReceiverBlockEntity.DATA_FREQUENCY);
    }

    public boolean listening() {
        return data.get(ReceiverBlockEntity.DATA_LISTENING) != 0;
    }

    public List<SignalMessage> archive() {
        return entity != null ? entity.archive() : snapshot;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (entity != null && entity.isRemoved()) {
            return false;
        }
        return player.level().getBlockState(pos).is(ExploreRegistry.RECEIVER.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }
}
