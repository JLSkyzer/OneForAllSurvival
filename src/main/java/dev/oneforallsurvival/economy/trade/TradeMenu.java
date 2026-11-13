package dev.oneforallsurvival.economy.trade;

import dev.oneforallsurvival.economy.EconomyRegistry;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * La fenêtre d'échange ({@code oneforallsurvival:trade}) : cases 0 à 11, son offre (sa garde) ; cases 12 à 23,
 * l'offre de l'autre, en lecture seule ; cases 24 à 59, l'inventaire du joueur (24 à 50 les trois rangées, 51 à 59
 * la barre). Les données synchronisées : la version (deux moitiés de 16 bits), les confirmations (bit 0 : soi,
 * bit 1 : l'autre) et le reçu coché.
 *
 * <p>Les cases de l'autre ne se prennent ni ne se remplissent par aucun clic : {@code mayPickup} et {@code mayPlace}
 * y sont faux, {@link #clicked} ignore tout clic dont l'indice y tombe (quel que soit le genre de clic),
 * {@link #canTakeItemForPickAll} et {@link #canDragTo} y sont faux. Rien ne se jette depuis une case de garde.
 */
public final class TradeMenu extends AbstractContainerMenu {

    public static final int OFFER_SIZE = 12;
    public static final int OWN_START = 0;
    public static final int OTHER_START = OWN_START + OFFER_SIZE;
    public static final int INVENTORY_START = OTHER_START + OFFER_SIZE;
    public static final int HOTBAR_START = INVENTORY_START + 27;
    public static final int SLOT_COUNT = HOTBAR_START + 9;

    /** Position (coin haut-gauche de l'objet) des grilles dans le cadre de l'écran. */
    public static final int OWN_X = 8;
    public static final int OTHER_X = 96;
    public static final int GRID_Y = 30;
    public static final int GRID_COLUMNS = 4;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 132;
    public static final int HOTBAR_Y = INVENTORY_Y + 3 * 18 + 4;

    private static final int DATA_VERSION_LOW = 0;
    private static final int DATA_VERSION_HIGH = 1;
    private static final int DATA_CONFIRMED = 2;
    private static final int DATA_RECEIPT = 3;

    private final Container own;
    private final Container other;
    /** Côté serveur : la session ; nul côté client. */
    @Nullable
    private final TradeSession session;
    @Nullable
    private final Player owner;
    private final String otherName;
    private final int[] data = new int[4];
    /** Vrai dès que le jeu ferme cette fenêtre : l'annulation qui en découle ne la referme pas une seconde fois. */
    private boolean closing;

    /** Côté serveur. */
    TradeMenu(int containerId, Inventory inventory, TradeSession session) {
        this(containerId, inventory, session, session.offer(inventory.player.getUUID()),
                session.offer(session.other(inventory.player.getUUID())),
                session.nameOf(session.other(inventory.player.getUUID())));
    }

    /** Côté client : le nom de l'autre, écrit à l'ouverture. */
    public static TradeMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new TradeMenu(containerId, inventory, null, new SimpleContainer(OFFER_SIZE), new SimpleContainer(OFFER_SIZE),
                buf.readUtf(64));
    }

    private TradeMenu(int containerId, Inventory inventory, @Nullable TradeSession session, Container own,
            Container other, String otherName) {
        super(EconomyRegistry.TRADE_MENU.get(), containerId);
        this.session = session;
        this.owner = session == null ? null : inventory.player;
        this.own = own;
        this.other = other;
        this.otherName = otherName;
        for (int i = 0; i < OFFER_SIZE; i++) {
            addSlot(new OfferSlot(own, i, OWN_X + (i % GRID_COLUMNS) * 18, GRID_Y + (i / GRID_COLUMNS) * 18));
        }
        for (int i = 0; i < OFFER_SIZE; i++) {
            addSlot(new OtherSlot(other, i, OTHER_X + (i % GRID_COLUMNS) * 18, GRID_Y + (i / GRID_COLUMNS) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
        for (int i = 0; i < data.length; i++) {
            int index = i;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return data[index];
                }

                @Override
                public void set(int value) {
                    data[index] = value & 0xFFFF;
                }
            });
        }
        snapshot();
    }

    /** L'indice, dans ce menu, de la case {@code inventorySlot} (0 à 35) de l'inventaire du joueur. */
    public static int slotOfInventory(int inventorySlot) {
        return inventorySlot < 9 ? HOTBAR_START + inventorySlot : INVENTORY_START + inventorySlot - 9;
    }

    /** Sa propre offre : une case de garde ordinaire, où l'argent est permis ({@code TRADE_MENU} est déclaré). */
    private static final class OfferSlot extends Slot {
        OfferSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
    }

    /** L'offre de l'autre : affichage seulement. */
    private static final class OtherSlot extends Slot {
        OtherSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean allowModification(Player player) {
            return false;
        }
    }

    private static boolean isOtherSlot(int slotId) {
        return slotId >= OTHER_START && slotId < OTHER_START + OFFER_SIZE;
    }

    private static boolean isOfferSlot(int slotId) {
        return slotId >= OWN_START && slotId < OTHER_START + OFFER_SIZE;
    }

    // ---------------------------------------------------------------------------------------------
    // Données
    // ---------------------------------------------------------------------------------------------

    private void snapshot() {
        if (session == null || owner == null) {
            return;
        }
        int version = session.version();
        data[DATA_VERSION_LOW] = version & 0xFFFF;
        data[DATA_VERSION_HIGH] = (version >>> 16) & 0xFFFF;
        data[DATA_CONFIRMED] = (session.confirmed(owner.getUUID()) ? 1 : 0)
                | (session.confirmed(session.other(owner.getUUID())) ? 2 : 0);
        data[DATA_RECEIPT] = session.receipt() ? 1 : 0;
    }

    @Override
    public void broadcastChanges() {
        snapshot();
        super.broadcastChanges();
    }

    @Override
    public void broadcastFullState() {
        snapshot();
        super.broadcastFullState();
    }

    @Override
    public void sendAllDataToRemote() {
        snapshot();
        super.sendAllDataToRemote();
    }

    /** La version de l'échange (côté client : celle que le serveur a envoyée). */
    public int version() {
        if (session != null) {
            return session.version();
        }
        return data[DATA_VERSION_LOW] | (data[DATA_VERSION_HIGH] << 16);
    }

    public boolean selfConfirmed() {
        return session != null && owner != null ? session.confirmed(owner.getUUID()) : (data[DATA_CONFIRMED] & 1) != 0;
    }

    public boolean otherConfirmed() {
        return session != null && owner != null ? session.confirmed(session.other(owner.getUUID()))
                : (data[DATA_CONFIRMED] & 2) != 0;
    }

    public boolean receipt() {
        return session != null ? session.receipt() : data[DATA_RECEIPT] != 0;
    }

    public String otherName() {
        return otherName;
    }

    boolean closing() {
        return closing;
    }

    /** Côté serveur : la session de cette fenêtre ; nul côté client. */
    @Nullable
    TradeSession session() {
        return session;
    }

    // ---------------------------------------------------------------------------------------------
    // Clics
    // ---------------------------------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Les cases de l'autre ne réagissent à aucun clic, quel qu'en soit le genre.
        if (isOtherSlot(slotId)) {
            return;
        }
        // Rien ne se jette depuis une garde : ce qui est posé revient à quelqu'un, jamais au sol.
        if (clickType == ClickType.THROW && isOfferSlot(slotId)) {
            return;
        }
        if (session != null && session.ended()) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
        if (session != null) {
            session.contentsMayHaveChanged();
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot slot) {
        return slot.container != other && super.canTakeItemForPickAll(carried, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.container != other && super.canDragTo(slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || isOtherSlot(index)) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved;
        if (index < OTHER_START) {
            // De sa garde vers l'inventaire : la barre en dernier, comme un coffre.
            moved = moveItemStackTo(stack, INVENTORY_START, SLOT_COUNT, true);
        } else {
            moved = moveItemStackTo(stack, OWN_START, OTHER_START, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        if (session == null) {
            return true;
        }
        return !session.ended() && session.involves(player.getUUID()) && session.menuOf(player.getUUID()) == this;
    }

    /** La fenêtre se ferme (par le joueur ou par le jeu) : l'échange s'annule et tout revient à son propriétaire. */
    @Override
    public void removed(Player player) {
        closing = true;
        if (session != null && !session.ended()) {
            TradeService.cancel(session, TradeService.CancelReason.CLOSED);
        }
        super.removed(player);
    }
}
