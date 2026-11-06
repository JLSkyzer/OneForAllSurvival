package dev.oneforallsurvival.bench;

import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/**
 * Le stock d'un ingénieur : une fenêtre de 6 lignes de 9 cases sur une liste de piles qui n'a pas de fin,
 * l'inventaire du joueur en dessous. La fenêtre glisse par lignes ({@link #clickMenuButton}) ; la case
 * {@code i} montre la pile {@code décalage * 9 + i} du stock. Rien ne s'y dépose : on y prend seulement, et
 * la pile retirée laisse la suivante remonter, sans trou.
 *
 * <p>Côté serveur la fenêtre est un conteneur virtuel qui lit et retire dans {@link PlayerWorkshop} ; côté
 * client c'est un conteneur ordinaire que la synchronisation vanille remplit. Les cases du serveur rendent
 * des copies : seul {@link PlayerWorkshop#takeFromStock} retire, jamais un {@code shrink} sur une pile vive.
 */
public final class BenchStockMenu extends AbstractContainerMenu {

    public static final String TITLE_KEY = "gui.oneforallsurvival.bench.stock.title";

    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    /** Les cases de stock sont les premières du menu ; l'inventaire suit. */
    public static final int WINDOW = COLUMNS * ROWS;

    /** Boutons de menu : 0 monte d'une ligne, 1 descend, {@code BUTTON_OFFSET + n} va à la ligne n. */
    public static final int BUTTON_UP = 0;
    public static final int BUTTON_DOWN = 1;
    public static final int BUTTON_OFFSET = 2;

    // Mise en page, partagée avec l'écran : coin de la première case, pas, ligne de l'inventaire et de la barre.
    public static final int SLOT_X = 8;
    public static final int GRID_Y = 23;
    public static final int SLOT_PITCH = 18;
    public static final int INVENTORY_Y = 147;
    public static final int HOTBAR_Y = 205;

    /** Une donnée de menu voyage en entier court signé : 15 bits par moitié, soit 2^30 piles au plus. */
    private static final int HALF_BITS = 15;
    private static final int HALF_MASK = (1 << HALF_BITS) - 1;

    private final BlockPos pos;
    /** Le stock du joueur ; null côté client, où le serveur décide de tout. */
    private final PlayerWorkshop workshop;
    /** Ligne affichée en haut de la fenêtre. Serveur seulement. */
    private int offset;
    private int clientLow;
    private int clientHigh;

    private BenchStockMenu(int containerId, Inventory inventory, BlockPos pos, PlayerWorkshop workshop) {
        super(BenchRegistry.BENCH_STOCK_MENU.get(), containerId);
        this.pos = pos.immutable();
        this.workshop = workshop;

        Container window = workshop == null ? new SimpleContainer(WINDOW) : new Window(workshop, this::firstIndex);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new StockSlot(window, row * COLUMNS + col, SLOT_X + col * SLOT_PITCH,
                        GRID_Y + row * SLOT_PITCH));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new Slot(inventory, 9 + row * COLUMNS + col, SLOT_X + col * SLOT_PITCH,
                        INVENTORY_Y + row * SLOT_PITCH));
            }
        }
        for (int col = 0; col < COLUMNS; col++) {
            addSlot(new Slot(inventory, col, SLOT_X + col * SLOT_PITCH, HOTBAR_Y));
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return BenchStockMenu.this.workshop != null ? stacks() & HALF_MASK : clientLow;
            }

            @Override
            public void set(int value) {
                clientLow = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return BenchStockMenu.this.workshop != null ? (stacks() >>> HALF_BITS) & HALF_MASK : clientHigh;
            }

            @Override
            public void set(int value) {
                clientHigh = value;
            }
        });
    }

    /** Le menu côté serveur, branché sur le stock du joueur. */
    public static BenchStockMenu forServer(int containerId, Inventory inventory, BlockPos pos) {
        if (!(inventory.player instanceof ServerPlayer player)) {
            throw new IllegalArgumentException("le menu serveur exige un joueur serveur");
        }
        return new BenchStockMenu(containerId, inventory, pos,
                WorkshopSavedData.get(player.server).of(player.getUUID()));
    }

    /** Le menu côté client : une fenêtre vide que le serveur remplit. */
    public static BenchStockMenu client(int containerId, Inventory inventory, BlockPos pos) {
        return new BenchStockMenu(containerId, inventory, pos, null);
    }

    public static BenchStockMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return client(containerId, inventory, buf.readBlockPos());
    }

    /** Ouvre le stock au joueur, depuis l'établi {@code pos} ; rôle et portée sont déjà vérifiés par l'appelant. */
    public static void open(ServerPlayer player, BlockPos pos) {
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, p) -> forServer(id, inventory, pos),
                        Component.translatable(TITLE_KEY)),
                buf -> buf.writeBlockPos(pos));
    }

    /** L'établi d'origine : le retour y ramène. */
    public BlockPos pos() {
        return pos;
    }

    /** Le bouton de menu qui amène la fenêtre à la ligne {@code lines}. */
    public static int buttonForOffset(int lines) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, lines) + BUTTON_OFFSET);
    }

    // --- Taille et décalage -------------------------------------------------------------------

    private int stacks() {
        return workshop.stock().size();
    }

    /** Le nombre de piles du stock : exact côté serveur, reconstitué des deux moitiés reçues côté client. */
    public int totalStacks() {
        return workshop != null ? stacks() : (clientHigh << HALF_BITS) | clientLow;
    }

    public int totalLines() {
        return (int) ((totalStacks() + (long) COLUMNS - 1) / COLUMNS);
    }

    /** Le plus grand décalage : la dernière fenêtre pleine. Un stock qui tient dans la fenêtre ne défile pas. */
    public int maxOffset() {
        return Math.max(0, totalLines() - ROWS);
    }

    /** La ligne affichée en haut ; connue du serveur seulement (le client suit sa propre barre). */
    public int offsetLines() {
        return offset;
    }

    private int firstIndex() {
        return Math.min(offset, maxOffset()) * COLUMNS;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (workshop == null || id < 0 || !stillValid(player)) {
            return false;
        }
        long wanted = switch (id) {
            case BUTTON_UP -> offset - 1L;
            case BUTTON_DOWN -> offset + 1L;
            default -> id - (long) BUTTON_OFFSET;
        };
        offset = (int) Math.max(0, Math.min(wanted, maxOffset()));
        return true;
    }

    /** Un stock qui rétrécit resserre le décalage avant la synchronisation, pour ne pas montrer le vide. */
    @Override
    public void broadcastChanges() {
        if (workshop != null) {
            offset = Math.min(offset, maxOffset());
        }
        super.broadcastChanges();
    }

    // --- Clics -----------------------------------------------------------------------------------

    /**
     * Maj-clic : une pile du stock passe dans l'inventaire, ce qui n'y tient pas reste au stock. Depuis
     * l'inventaire il ne se passe rien, car le stock ne reçoit pas de dépôt. Rend toujours vide : le
     * Maj-clic vanille se répète tant que la case contient le même objet, et comme la suite remonte dans
     * la case, il viderait toute une série de piles identiques d'un seul geste.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= WINDOW) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        ItemStack rest = slot.getItem().copy();
        if (rest.isEmpty() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        int before = rest.getCount();
        moveItemStackTo(rest, WINDOW, slots.size(), true);
        int moved = before - rest.getCount();
        if (moved > 0) {
            slot.remove(moved);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return EngineerBenchMenu.usable(player, pos);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof StockSlot);
    }

    // --- Cases -----------------------------------------------------------------------------------

    /** Une case du stock : on y prend, on n'y dépose pas. */
    private static final class StockSlot extends Slot {

        StockSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        /**
         * Sans cela, une case qui n'accepte pas de dépôt refuserait aussi de céder une partie de sa pile
         * (le double-clic de ramassage ne prend que ce qui manque au curseur).
         */
        @Override
        public boolean allowModification(Player player) {
            return mayPickup(player);
        }
    }

    /**
     * La fenêtre côté serveur : la case {@code i} est la pile {@code première + i} du stock. Elle rend des
     * copies et retire par {@link PlayerWorkshop#takeFromStock}, si bien qu'aucune pile vive du stock ne
     * peut être modifiée, ni dupliquée, par un clic.
     */
    private static final class Window implements Container {

        private final PlayerWorkshop workshop;
        private final IntSupplier first;

        Window(PlayerWorkshop workshop, IntSupplier first) {
            this.workshop = workshop;
            this.first = first;
        }

        @Override
        public int getContainerSize() {
            return WINDOW;
        }

        @Override
        public boolean isEmpty() {
            return workshop.stock().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            List<ItemStack> stock = workshop.stock();
            long index = (long) first.getAsInt() + slot;
            return slot >= 0 && slot < WINDOW && index < stock.size() ? stock.get((int) index).copy() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return slot >= 0 && slot < WINDOW ? workshop.takeFromStock(first.getAsInt() + slot, amount)
                    : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, Integer.MAX_VALUE);
        }

        /**
         * Vider une case retire sa pile. Y poser quelque chose est ignoré : aucune case ne l'accepte, et
         * recopier ici une pile rendue par {@link #getItem} la dupliquerait.
         */
        @Override
        public void setItem(int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                removeItemNoUpdate(slot);
            }
        }

        @Override
        public void setChanged() {
            // Le stock marque lui-même la sauvegarde.
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public void clearContent() {
            // Jamais vidé en bloc : le stock appartient au joueur, pas à la fenêtre.
        }
    }
}
