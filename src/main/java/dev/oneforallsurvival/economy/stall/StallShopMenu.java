package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.EconomyRegistry;
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
 * La vitrine d'un étal, ouverte par tous : les offres de vente et de rachat, avec leur disponibilité. Elle ne porte
 * que les 36 cases du joueur (un achat ou un rachat fait bouger son inventaire : le jeu le synchronise ainsi). Aucune
 * case de l'étal n'y est : les modèles et les prix viennent de l'entité du bloc, que le client reçoit déjà (offres
 * publiques) ; la disponibilité, de données de menu calculées par le serveur à chaque tick ({@link #broadcastChanges}).
 *
 * <p>Les données : pour chaque case d'offre de vente, le nombre de lots en stock ({@link #saleAvailable}) ; pour
 * chaque case de rachat, un état ({@link #buybackState}). Elles passent par le réseau en entiers courts : bornées à
 * {@link Short#MAX_VALUE}.
 */
public final class StallShopMenu extends AbstractContainerMenu {

    public static final int INVENTORY_START = 0;
    public static final int HOTBAR_START = INVENTORY_START + 27;
    public static final int SLOT_COUNT = HOTBAR_START + 9;

    /** Rangées d'offres visibles à la fois, et leur hauteur. */
    public static final int ROWS = 5;
    public static final int ROW_H = 20;
    public static final int LIST_Y = 30;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = LIST_Y + ROWS * ROW_H + 14;
    public static final int HOTBAR_Y = INVENTORY_Y + 3 * 18 + 4;

    /** État d'une offre de rachat, pour la vitrine. */
    public static final int BUYBACK_NONE = -1;
    public static final int BUYBACK_OPEN = 0;
    public static final int BUYBACK_EXHAUSTED = 1;
    public static final int BUYBACK_CASH_SHORT = 2;
    public static final int BUYBACK_STOCK_FULL = 3;

    private static final int SALE_BASE = 0;
    private static final int BUYBACK_BASE = MarketStallBlockEntity.MAX_OFFERS;

    private final BlockPos pos;
    /** Côté serveur : l'étal ; nul côté client. */
    @Nullable
    private final MarketStallBlockEntity stall;
    private final ContainerData data;

    /** Côté serveur. */
    public StallShopMenu(int containerId, Inventory inventory, BlockPos pos, MarketStallBlockEntity stall) {
        this(containerId, inventory, pos, stall, new SimpleContainerData(2 * MarketStallBlockEntity.MAX_OFFERS));
        refresh();
    }

    /** Côté client : ce que {@code MarketStallBlock#openShop} a écrit. */
    public static StallShopMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new StallShopMenu(containerId, inventory, buf.readBlockPos(), null,
                new SimpleContainerData(2 * MarketStallBlockEntity.MAX_OFFERS));
    }

    private StallShopMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable MarketStallBlockEntity stall,
            ContainerData data) {
        super(EconomyRegistry.STALL_SHOP_MENU.get(), containerId);
        this.pos = pos.immutable();
        this.stall = stall;
        this.data = data;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    /** Côté serveur : l'étal de cette vitrine ; nul côté client. */
    @Nullable
    public MarketStallBlockEntity stall() {
        return stall;
    }

    /** Lots en stock de l'offre de vente {@code index}, tels que le serveur les a envoyés. */
    public int saleAvailable(int index) {
        return index < 0 || index >= MarketStallBlockEntity.MAX_OFFERS ? 0 : data.get(SALE_BASE + index);
    }

    /** L'état de l'offre de rachat {@code index} ({@link #BUYBACK_OPEN}...), tel que le serveur l'a envoyé. */
    public int buybackState(int index) {
        return index < 0 || index >= MarketStallBlockEntity.MAX_OFFERS ? BUYBACK_NONE : data.get(BUYBACK_BASE + index);
    }

    /** Recalcule les données de la vitrine (serveur). */
    private void refresh() {
        if (stall == null) {
            return;
        }
        int slots = MarketStallBlockEntity.offerSlots();
        for (int i = 0; i < MarketStallBlockEntity.MAX_OFFERS; i++) {
            data.set(SALE_BASE + i, i < slots ? Math.min(Short.MAX_VALUE, stall.available(i)) : 0);
            data.set(BUYBACK_BASE + i, i < slots ? stateOf(stall, stall.buybacks().get(i)) : BUYBACK_NONE);
        }
    }

    private static int stateOf(MarketStallBlockEntity stall, @Nullable StallBuyback buyback) {
        if (buyback == null) {
            return BUYBACK_NONE;
        }
        if (buyback.lotsLeft() <= 0) {
            return BUYBACK_EXHAUSTED;
        }
        if (CommerceMath.payFromBox(stall.cash(), buyback.price(), Coin::value).isEmpty()) {
            return BUYBACK_CASH_SHORT;
        }
        if (!StallTrade.stockHasRoom(stall, buyback.template(), buyback.lot())) {
            return BUYBACK_STOCK_FULL;
        }
        return BUYBACK_OPEN;
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    /** L'étal est toujours là et le joueur à portée ; côté serveur, c'est toujours la même entité. */
    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(pos).is(EconomyRegistry.MARKET_STALL.get())) {
            return false;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MarketStallBlock.MAX_DISTANCE_SQR) {
            return false;
        }
        return stall == null || player.level().getBlockEntity(pos) == stall;
    }

    /** Maj-clic : de l'inventaire vers la barre d'action, et retour. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < HOTBAR_START
                ? moveItemStackTo(stack, HOTBAR_START, SLOT_COUNT, false)
                : moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
