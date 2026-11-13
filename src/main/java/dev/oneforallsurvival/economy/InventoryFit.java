package dev.oneforallsurvival.economy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * « Est-ce que ça tient ? » avant de rien bouger : une simulation sur une copie des 36 cases principales, avec
 * l'algorithme d'{@code Inventory.add} (les piles existantes se complètent, puis les cases vides se remplissent).
 * La main gauche et l'armure ne sont pas comptées : la réponse peut donc être prudente (faux alors que la main
 * gauche aurait pris une pile), jamais trop large.
 */
public final class InventoryFit {

    private static final int MAIN_SLOTS = 36;

    private InventoryFit() {}

    /**
     * Les objets entrants tiennent-ils dans l'inventaire du joueur ? Les pièces entrent d'abord dans les bourses,
     * jusqu'à leur plafond ({@link Purse#MAX_PER_COIN}), comme {@link PlayerCoins#receive} ; le surplus prend des
     * cases comme n'importe quel objet.
     */
    public static boolean fits(Player player, List<ItemStack> incoming) {
        List<ItemStack> slots = new ArrayList<>(MAIN_SLOTS);
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            slots.add(player.getInventory().getItem(slot).copy());
        }
        // La place qui reste dans les bourses, coupure par coupure (les bourses se remplissent l'une après l'autre).
        EnumMap<Coin, Long> purseRoom = new EnumMap<>(Coin.class);
        for (ItemStack purse : Purse.purses(player)) {
            for (Coin coin : Coin.values()) {
                purseRoom.merge(coin, Purse.room(purse, coin), (a, b) -> a + b < a ? Long.MAX_VALUE : a + b);
            }
        }
        for (ItemStack stack : incoming) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = stack.copy();
            Optional<Coin> coin = Coin.fromItem(stack.getItem());
            if (coin.isPresent()) {
                long room = purseRoom.getOrDefault(coin.get(), 0L);
                int intoPurses = (int) Math.min(room, rest.getCount());
                purseRoom.put(coin.get(), room - intoPurses);
                rest.shrink(intoPurses);
                if (rest.isEmpty()) {
                    continue;
                }
            }
            if (!place(slots, rest)) {
                return false;
            }
        }
        return true;
    }

    /** Place la pile dans la copie ; faux si elle n'y tient pas tout entière. */
    private static boolean place(List<ItemStack> slots, ItemStack stack) {
        int max = stack.getMaxStackSize();
        // Une pile abîmée ne rejoint aucune autre : Inventory.add lui cherche une case vide.
        if (!stack.isDamaged()) {
            for (ItemStack slot : slots) {
                int room = Math.min(max, slot.getMaxStackSize()) - slot.getCount();
                if (!slot.isEmpty() && room > 0 && ItemStack.isSameItemSameTags(slot, stack)) {
                    int moved = Math.min(stack.getCount(), room);
                    slot.grow(moved);
                    stack.shrink(moved);
                    if (stack.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) {
                int moved = Math.min(stack.getCount(), max);
                slots.set(i, stack.copyWithCount(moved));
                stack.shrink(moved);
            }
        }
        return stack.isEmpty();
    }
}
