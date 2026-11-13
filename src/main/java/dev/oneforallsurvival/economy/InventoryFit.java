package dev.oneforallsurvival.economy;

import java.util.ArrayList;
import java.util.List;
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
     * Les objets entrants tiennent-ils dans l'inventaire du joueur ? Les pièces ne comptent pas quand le joueur a
     * une bourse : elles y entrent.
     */
    public static boolean fits(Player player, List<ItemStack> incoming) {
        List<ItemStack> slots = new ArrayList<>(MAIN_SLOTS);
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            slots.add(player.getInventory().getItem(slot).copy());
        }
        boolean hasPurse = Purse.firstPurse(player).isPresent();
        for (ItemStack stack : incoming) {
            if (stack.isEmpty() || hasPurse && Coin.fromItem(stack.getItem()).isPresent()) {
                continue;
            }
            if (!place(slots, stack.copy())) {
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
