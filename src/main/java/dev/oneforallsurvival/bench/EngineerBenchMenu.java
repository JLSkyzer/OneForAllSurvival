package dev.oneforallsurvival.bench;

import dev.oneforallsurvival.role.RoleGate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
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
    /** Ce que le client connaît de l'inventaire du joueur (côté serveur seulement) ; null : rien d'envoyé. */
    private final ItemStack[] sentInventory = new ItemStack[36];

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
        return usable(player, pos);
    }

    /**
     * Le joueur peut-il se servir de cet établi : le bloc est là, à portée, et il a le rôle. Les menus de
     * l'établi (commande, stock) posent tous la même condition.
     */
    static boolean usable(Player player, BlockPos pos) {
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
        if (player instanceof ServerPlayer serverPlayer) {
            syncInventory(serverPlayer);
            if (++ticksSinceSync >= QUEUE_SYNC_TICKS) {
                ticksSinceSync = 0;
                BenchNetwork.sendQueue(serverPlayer);
            }
        }
    }

    /**
     * Ce menu n'a aucune case : le serveur ne synchronise donc pas l'inventaire du joueur à travers lui, et
     * le client n'accepte, d'un menu ouvert, que les mises à jour de la barre d'action. Sans cet envoi, les
     * ingrédients prélevés par une commande (ou rendus par une annulation) resteraient affichés à leur
     * ancien nombre dans le reste de l'inventaire jusqu'à la fermeture. Le paquet « -2 » écrit directement
     * dans l'inventaire du client, case par case, sans passer par un menu. Le mécanisme vanille
     * ({@code inventoryMenu.broadcastChanges()}) ne suffit pas : le client n'applique un paquet d'identifiant 0
     * qu'aux cases de la barre d'action tant qu'un autre menu est ouvert (lu dans {@code handleContainerSetSlot}).
     */
    private void syncInventory(ServerPlayer serverPlayer) {
        var items = serverPlayer.getInventory().items;
        for (int i = 0; i < sentInventory.length && i < items.size(); i++) {
            ItemStack current = items.get(i);
            if (sentInventory[i] == null || !ItemStack.matches(sentInventory[i], current)) {
                serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, i, current));
                sentInventory[i] = current.copy();
            }
        }
    }
}
