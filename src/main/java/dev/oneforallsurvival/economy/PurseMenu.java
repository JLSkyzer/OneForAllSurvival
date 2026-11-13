package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Le menu de la bourse : la bourse n'a aucune case, rien ne passe par le curseur. Les pièces entrent et sortent par
 * {@link PurseActionPacket}. La bourse est retrouvée par sa case dans l'inventaire du joueur : côté serveur, c'est
 * elle qui fait foi, et son contenu voyage vers le client par les champs de données de {@link CoinAccountMenu}.
 * Sa case est verrouillée pendant l'écran (on ne la prend pas, on n'y pose rien).
 */
public final class PurseMenu extends CoinAccountMenu {

    private final int slot;
    /** Côté serveur : l'inventaire où se trouve la bourse ; nul côté client. */
    @Nullable
    private final Inventory inventory;

    /** Côté serveur. */
    public PurseMenu(int containerId, Inventory inventory, int slot) {
        this(containerId, inventory, slot, inventory);
    }

    /** Côté client : ce que {@code PurseItem#use} a écrit. */
    public static PurseMenu fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        return new PurseMenu(containerId, inventory, buf.readVarInt(), null);
    }

    private PurseMenu(int containerId, Inventory playerInventory, int slot, @Nullable Inventory inventory) {
        super(EconomyRegistry.PURSE_MENU.get(), containerId, playerInventory, slot);
        this.slot = slot;
        this.inventory = inventory;
    }

    @Override
    protected boolean isServerSide() {
        return inventory != null;
    }

    @Override
    protected EnumMap<Coin, Long> liveContents() {
        return Purse.contents(purse());
    }

    @Override
    public boolean usesPurses() {
        return false;
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

    /**
     * Valable tant que la bourse est dans sa case : jetée, rangée ailleurs ou volée, elle ferme l'écran. Côté
     * client, qui ne la lit pas, l'écran reste ouvert jusqu'à ce que le serveur le ferme.
     */
    @Override
    public boolean stillValid(Player player) {
        return inventory == null || (player.getInventory() == inventory && !purse().isEmpty());
    }
}
