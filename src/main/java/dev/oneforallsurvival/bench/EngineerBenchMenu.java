package dev.oneforallsurvival.bench;

import dev.oneforallsurvival.role.RoleGate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Le menu de l'établi : aucune case, seulement l'établi d'origine, que les paquets de commande doivent
 * citer. Il ne reste ouvert que si le bloc est là, à portée, et si le joueur a encore le rôle.
 */
public final class EngineerBenchMenu extends AbstractContainerMenu {

    /** Une seconde de ticks entre deux envois de la file au joueur. */
    private static final int QUEUE_SYNC_TICKS = 20;

    private final BlockPos pos;
    private final Player player;
    private int ticksSinceSync;

    public EngineerBenchMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(BenchRegistry.ENGINEER_BENCH_MENU.get(), containerId);
        this.pos = pos.immutable();
        this.player = inventory.player;
    }

    public static EngineerBenchMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new EngineerBenchMenu(containerId, inventory, buf.readBlockPos());
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(pos).is(BenchRegistry.ENGINEER_BENCH.get())) {
            return false;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > BenchOrders.MAX_DISTANCE_SQR) {
            return false;
        }
        return !(player instanceof ServerPlayer serverPlayer) || RoleGate.canDoEngineerWork(serverPlayer);
    }

    /** Côté serveur, renvoie la file au joueur une fois par seconde tant que le menu est ouvert. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && ++ticksSinceSync >= QUEUE_SYNC_TICKS) {
            ticksSinceSync = 0;
            BenchNetwork.sendQueue(serverPlayer);
        }
    }
}
