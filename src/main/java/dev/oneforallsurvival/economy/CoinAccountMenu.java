package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Le menu commun du coffre-fort ({@link SafeMenu}) et de la bourse ({@link PurseMenu}).
 *
 * <p>Le contenu voyage vers le client par des champs de données : le jeu n'en envoie que 16 bits à la fois, chaque
 * coupure en prend donc quatre. Côté serveur le contenu est lu une seule fois par diffusion
 * ({@link #broadcastChanges}), puis découpé ; côté client les morceaux reçus sont remontés par {@link #count}. Le
 * client n'a que la copie de ce que le serveur lui a dit ; il n'a jamais autorité.
 *
 * <p>Le menu porte aussi les 36 cases de l'inventaire du joueur : le jeu les synchronise comme pour tout écran, et
 * l'inventaire du client suit donc les dépôts et retraits pendant que l'écran est ouvert. Aucune pièce n'entre ni
 * ne sort par le curseur vers le contenant (il n'a pas de case) ; le maj-clic ne fait rien.
 */
public abstract class CoinAccountMenu extends AbstractContainerMenu {

    /** Coin haut-gauche (objet) de la première case de l'inventaire, dans le cadre de l'écran. */
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 151;
    public static final int HOTBAR_Y = INVENTORY_Y + 3 * 18 + 4;

    private static final int PARTS = 4;
    private static final int PART_BITS = 16;
    private static final long PART_MASK = (1L << PART_BITS) - 1L;

    /** Côté serveur : le dernier relevé découpé ; côté client : les morceaux reçus. Quatre par coupure. */
    private final int[] parts = new int[Coin.values().length * PARTS];
    private final Inventory playerInventory;
    private final int lockedSlot;

    /**
     * @param lockedSlot une case de l'inventaire qu'on ne peut ni prendre ni remplir pendant l'écran (la bourse
     *        ouverte), ou -1
     */
    protected CoinAccountMenu(MenuType<?> type, int containerId, Inventory playerInventory, int lockedSlot) {
        super(type, containerId);
        this.playerInventory = playerInventory;
        this.lockedSlot = lockedSlot;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = 9 + row * 9 + col;
                addSlot(slot(playerInventory, index, INVENTORY_X + col * 18, INVENTORY_Y + row * 18, lockedSlot));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y, lockedSlot));
        }
        for (int i = 0; i < parts.length; i++) {
            addDataSlot(new Part(i));
        }
    }

    private static Slot slot(Inventory inventory, int index, int x, int y, int lockedSlot) {
        if (index != lockedSlot) {
            return new Slot(inventory, index, x, y);
        }
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    /** Un morceau de 16 bits du nombre de pièces d'une coupure. */
    private final class Part extends DataSlot {
        private final int index;

        Part(int index) {
            this.index = index;
        }

        @Override
        public int get() {
            return parts[index];
        }

        @Override
        public void set(int value) {
            parts[index] = value & (int) PART_MASK;
        }
    }

    /** Vrai côté serveur, où le contenant réel est connu. */
    protected abstract boolean isServerSide();

    /** Côté serveur : le contenu réel du contenant, coupures nulles omises. */
    protected abstract EnumMap<Coin, Long> liveContents();

    /**
     * Vrai pour le coffre-fort : il prend aussi les pièces des bourses portées (pour la bourse : les pièces en vrac
     * seulement).
     */
    public abstract boolean usesPurses();

    /** Relève le contenu une fois et le découpe en morceaux de 16 bits. */
    private void snapshot() {
        if (!isServerSide()) {
            return;
        }
        EnumMap<Coin, Long> contents = liveContents();
        for (Coin coin : Coin.values()) {
            long n = contents.getOrDefault(coin, 0L);
            for (int part = 0; part < PARTS; part++) {
                parts[coin.ordinal() * PARTS + part] = (int) ((n >>> (part * PART_BITS)) & PART_MASK);
            }
        }
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

    /** Le nombre de pièces de cette coupure (côté client : ce que le serveur a envoyé). */
    public long count(Coin coin) {
        if (isServerSide()) {
            return liveContents().getOrDefault(coin, 0L);
        }
        long total = 0L;
        for (int part = 0; part < PARTS; part++) {
            total |= (parts[coin.ordinal() * PARTS + part] & PART_MASK) << (part * PART_BITS);
        }
        return total;
    }

    /** La valeur du contenu, en cuivres (jamais d'exception : {@link Long#MAX_VALUE} au-delà). */
    public long total() {
        EnumMap<Coin, Long> counts = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            counts.put(coin, count(coin));
        }
        return SafeAccounts.saturatedValue(counts);
    }

    /**
     * Les pièces de cette coupure que le joueur peut déposer ici : en vrac dans l'inventaire, plus le contenu de
     * ses bourses pour le coffre-fort. Côté client, l'inventaire est celui que les cases synchronisent.
     */
    public long carried(Coin coin) {
        long total = 0L;
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == coin.item()) {
                total += stack.getCount();
            } else if (usesPurses() && Purse.isPurse(stack)) {
                total += Purse.count(stack, coin);
            }
        }
        return total;
    }

    /** La case verrouillée ne s'échange pas non plus par une touche numérique (ni par la touche de la main gauche). */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.SWAP && lockedSlot >= 0 && button == lockedSlot) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
