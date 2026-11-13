package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.stall.StallLedger.Kind;
import dev.oneforallsurvival.economy.stall.StallLedger.Line;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * La gestion d'un étal, onglet Stock : les cases 0 à 26 sont le stock, 27 à 53 les trois rangées de l'inventaire du
 * joueur, 54 à 62 sa barre d'action. Les 36 cases du joueur sont là pour que le jeu synchronise l'inventaire du
 * client comme pour tout écran de stockage.
 *
 * <p>Ce menu n'est <b>pas</b> déclaré comme rangement permis pour l'argent : les cases du stock refusent les pièces
 * et les bourses pleines par {@link StockSlot#mayPlace}, par le mixin des cases et par le stockage lui-même. Chaque
 * objet que la gestion pose ou retire est noté au registre de l'étal ({@link #clicked}) : par le propriétaire,
 * {@code GOODS_IN} et {@code GOODS_OUT} ; par l'équipe sur l'étal d'un autre, {@code STAFF} avec des quantités
 * signées.
 *
 * <p>Les autres onglets sont des boutons de menu (le mécanisme du jeu, sans paquet à nous) : {@link #BUTTON_CASH} ouvre
 * la caisse. Offres et Registre sont des vues de ce même menu, côté client ({@link #setView}) : la gestion garde ses
 * cases, l'écran cache celles qui n'ont rien à faire dans la vue ({@link Slot#isActive}). Les offres s'écrivent par
 * {@link StallActionPacket} (modèle = la pile du curseur de ce menu, côté serveur) ; le registre se demande par le
 * même paquet et n'est envoyé qu'au joueur qui a ce menu ouvert. {@link #BUTTON_OFFERS} et {@link #BUTTON_LEDGER}
 * répondent vrai sans rien ouvrir.
 */
public final class StallOwnerMenu extends AbstractContainerMenu {

    public static final int STOCK_START = 0;
    public static final int STOCK_SIZE = StallStockHandler.SIZE;
    public static final int INVENTORY_START = STOCK_START + STOCK_SIZE;
    public static final int HOTBAR_START = INVENTORY_START + 27;
    public static final int SLOT_COUNT = HOTBAR_START + 9;

    /** Boutons de menu : les onglets de la gestion. */
    public static final int BUTTON_CASH = 0;
    public static final int BUTTON_OFFERS = 1;
    public static final int BUTTON_LEDGER = 2;

    /** Coin haut-gauche (objet) des cases dans le cadre de l'écran. */
    public static final int STOCK_X = 8;
    public static final int STOCK_Y = 38;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 108;
    public static final int HOTBAR_Y = INVENTORY_Y + 3 * 18 + 4;

    /** Vues de l'écran (côté client) : quelles cases sont montrées. */
    public static final int VIEW_STOCK = 0;
    public static final int VIEW_OFFERS = 1;
    public static final int VIEW_LEDGER = 2;

    private final BlockPos pos;
    /** Côté serveur : l'étal ; nul côté client. */
    @Nullable
    private final MarketStallBlockEntity stall;
    /** Côté client : la vue de l'écran. Côté serveur, toujours le stock (toutes les cases actives). */
    private int view = VIEW_STOCK;

    /** Côté serveur. */
    public StallOwnerMenu(int containerId, Inventory inventory, BlockPos pos, MarketStallBlockEntity stall) {
        this(containerId, inventory, pos, stall, stall.stock());
    }

    /** Côté client : ce que {@code MarketStallBlock#openManage} a écrit. */
    public static StallOwnerMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new StallOwnerMenu(containerId, inventory, buf.readBlockPos(), null, new StallStockHandler());
    }

    private StallOwnerMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable MarketStallBlockEntity stall,
            StallStockHandler stock) {
        super(EconomyRegistry.STALL_OWNER_MENU.get(), containerId);
        this.pos = pos.immutable();
        this.stall = stall;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new StockSlot(this, stock, row * 9 + col, STOCK_X + col * 18, STOCK_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(this, inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new PlayerSlot(this, inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    /** L'indice, dans ce menu, de la case {@code inventorySlot} (0 à 35) de l'inventaire du joueur. */
    public static int slotOfInventory(int inventorySlot) {
        return inventorySlot < 9 ? HOTBAR_START + inventorySlot : INVENTORY_START + inventorySlot - 9;
    }

    public BlockPos pos() {
        return pos;
    }

    /** Côté serveur : l'étal de cette gestion ; nul côté client. */
    @Nullable
    public MarketStallBlockEntity stall() {
        return stall;
    }

    public int view() {
        return view;
    }

    /** Côté client : change la vue ; le stock n'est montré que dans sa vue, l'inventaire partout sauf au registre. */
    public void setView(int view) {
        this.view = view;
    }

    /** Une case du stock : elle refuse l'argent d'elle-même, quel que soit l'écran qui la porte. */
    private static final class StockSlot extends SlotItemHandler {
        private final StallOwnerMenu menu;

        StockSlot(StallOwnerMenu menu, StallStockHandler stock, int index, int x, int y) {
            super(stock, index, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !MoneyStorageRules.isMoney(stack) && super.mayPlace(stack);
        }

        @Override
        public boolean isActive() {
            return menu.view == VIEW_STOCK;
        }
    }

    /** Une case de l'inventaire du joueur : cachée dans la vue du registre. */
    private static final class PlayerSlot extends Slot {
        private final StallOwnerMenu menu;

        PlayerSlot(StallOwnerMenu menu, Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
            this.menu = menu;
        }

        @Override
        public boolean isActive() {
            return menu.view != VIEW_LEDGER;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Registre
    // ---------------------------------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (stall == null || !(player instanceof ServerPlayer actor)) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        Map<String, Integer> before = tally(stall.stock());
        super.clicked(slotId, button, clickType, player);
        note(actor, stall, before, tally(stall.stock()));
    }

    /** Le stock, objet par objet (identifiant) : pour savoir ce qu'un geste a posé ou retiré. */
    private static Map<String, Integer> tally(StallStockHandler stock) {
        Map<String, Integer> totals = new TreeMap<>();
        for (int i = 0; i < stock.getSlots(); i++) {
            ItemStack stack = stock.getStackInSlot(i);
            if (!stack.isEmpty()) {
                totals.merge(keyOf(stack), stack.getCount(), Integer::sum);
            }
        }
        return totals;
    }

    /** Identifiant et NBT : deux piles de même identifiant mais de contenu différent ne se confondent pas. */
    private static String keyOf(ItemStack stack) {
        return ForgeRegistries.ITEMS.getKey(stack.getItem()) + (stack.getTag() == null ? "" : TAG_SEPARATOR + stack.getTag());
    }

    private static final String TAG_SEPARATOR = "|";

    /** L'identifiant seul d'une clé de {@link #keyOf}. */
    private static String idOfKey(String key) {
        int cut = key.indexOf(TAG_SEPARATOR);
        return cut < 0 ? key : key.substring(0, cut);
    }

    /** Une ligne du registre de l'étal par objet dont la quantité a changé. */
    private static void note(ServerPlayer player, MarketStallBlockEntity stall, Map<String, Integer> before,
            Map<String, Integer> after) {
        Map<String, Integer> diff = new HashMap<>();
        before.forEach((id, n) -> diff.merge(id, -n, Integer::sum));
        after.forEach((id, n) -> diff.merge(id, n, Integer::sum));
        boolean ownStall = player.getUUID().equals(stall.owner());
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(diff).entrySet()) {
            int delta = entry.getValue();
            if (delta == 0) {
                continue;
            }
            Kind kind = !ownStall ? Kind.STAFF : delta > 0 ? Kind.GOODS_IN : Kind.GOODS_OUT;
            int count = ownStall ? Math.abs(delta) : delta;
            stall.ledger().add(new Line(now, player.getUUID(), player.getGameProfile().getName(), kind, idOfKey(entry.getKey()), count, 0L));
        }
        if (!diff.isEmpty()) {
            stall.setChanged();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Onglets, validité
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (stall == null || !(player instanceof ServerPlayer serverPlayer) || !stillValid(player)) {
            return false;
        }
        return switch (id) {
            case BUTTON_CASH -> MarketStallBlock.openCash(serverPlayer, pos);
            // Vues du client : rien à ouvrir, le menu reste celui-ci.
            case BUTTON_OFFERS, BUTTON_LEDGER -> true;
            default -> false;
        };
    }

    /**
     * L'étal est toujours là, le joueur à portée et toujours ayant droit : l'équipe qui perd son niveau, ou un
     * propriétaire dont l'étal a été remplacé, voit l'écran se fermer. Côté client, seul le bloc compte.
     */
    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(pos).is(EconomyRegistry.MARKET_STALL.get())) {
            return false;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MarketStallBlock.MAX_DISTANCE_SQR) {
            return false;
        }
        return stall == null || (player.level().getBlockEntity(pos) == stall && MarketStallBlock.mayManage(player, stall.owner()));
    }

    /** Maj-clic : du stock vers l'inventaire, de l'inventaire vers le stock (jamais d'argent : les cases le refusent). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < INVENTORY_START
                ? moveItemStackTo(stack, INVENTORY_START, SLOT_COUNT, true)
                : moveItemStackTo(stack, STOCK_START, INVENTORY_START, false);
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
