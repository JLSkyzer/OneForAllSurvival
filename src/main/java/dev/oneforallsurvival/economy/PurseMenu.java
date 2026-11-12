package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Le menu de la bourse : aucune case, car rien ne passe par le curseur. Les pièces entrent et sortent par
 * {@link PurseActionPacket}. La bourse est retrouvée par sa case dans l'inventaire du joueur : côté serveur, c'est
 * elle qui fait foi, et son compte voyage vers le client par des champs de données (le jeu n'en envoie que 16
 * bits à la fois, chaque coupure en prend donc quatre). Le client n'a que la copie de ce que le serveur lui a dit.
 */
public final class PurseMenu extends AbstractContainerMenu {

    private static final int PARTS = 4;
    private static final int PART_BITS = 16;
    private static final long PART_MASK = (1L << PART_BITS) - 1L;

    private final int slot;
    /** Côté serveur : l'inventaire où se trouve la bourse ; nul côté client. */
    @Nullable
    private final Inventory inventory;
    /** Côté client : les morceaux reçus, quatre par coupure. */
    private final int[] mirror = new int[Coin.values().length * PARTS];

    /** Côté serveur. */
    public PurseMenu(int containerId, Inventory inventory, int slot) {
        this(containerId, slot, inventory);
    }

    /** Côté client : ce que {@code PurseItem#use} a écrit. */
    public static PurseMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new PurseMenu(containerId, buf.readVarInt(), null);
    }

    private PurseMenu(int containerId, int slot, @Nullable Inventory inventory) {
        super(EconomyRegistry.PURSE_MENU.get(), containerId);
        this.slot = slot;
        this.inventory = inventory;
        for (Coin coin : Coin.values()) {
            for (int part = 0; part < PARTS; part++) {
                addDataSlot(new Part(coin, part));
            }
        }
    }

    /** Un morceau de 16 bits du nombre de pièces d'une coupure. */
    private final class Part extends DataSlot {
        private final Coin coin;
        private final int part;

        Part(Coin coin, int part) {
            this.coin = coin;
            this.part = part;
        }

        @Override
        public int get() {
            if (inventory == null) {
                return mirror[coin.ordinal() * PARTS + part];
            }
            return (int) ((Purse.count(purse(), coin) >>> (part * PART_BITS)) & PART_MASK);
        }

        @Override
        public void set(int value) {
            mirror[coin.ordinal() * PARTS + part] = value & (int) PART_MASK;
        }
    }

    /** La case de la bourse dans l'inventaire du joueur (0 à 40). */
    public int slot() {
        return slot;
    }

    /** La bourse, côté serveur : ce qui est dans la case, ou la pile vide si une autre chose y est venue. */
    public ItemStack purse() {
        if (inventory == null || slot < 0 || slot >= inventory.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = inventory.getItem(slot);
        return Purse.isPurse(stack) ? stack : ItemStack.EMPTY;
    }

    /** Le nombre de pièces de cette coupure dans la bourse (côté client : ce que le serveur a envoyé). */
    public long count(Coin coin) {
        if (inventory != null) {
            return Purse.count(purse(), coin);
        }
        long total = 0L;
        for (int part = 0; part < PARTS; part++) {
            total |= (mirror[coin.ordinal() * PARTS + part] & PART_MASK) << (part * PART_BITS);
        }
        return total;
    }

    /** La valeur de la bourse, en cuivres. */
    public long total() {
        EnumMap<Coin, Long> counts = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            counts.put(coin, count(coin));
        }
        return SafeAccounts.saturatedValue(counts);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * Valable tant que la bourse est dans sa case : jetée, rangée ailleurs ou volée, elle ferme l'écran. Côté
     * client, qui ne la lit pas, l'écran reste ouvert jusqu'à ce que le serveur le ferme.
     */
    @Override
    public boolean stillValid(Player player) {
        return inventory == null || (player.getInventory() == inventory && !purse().isEmpty());
    }
}
