package dev.oneforallsurvival.food;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/** Ce que les machines à vivres font d'un aliment qui entre ou d'un résultat qui sort : péremption, place, dépôt. */
final class FoodOutput {

    private FoodOutput() {}

    /** Un déchet, ou un aliment dont la date est passée : ce n'est plus une entrée. Sans monde, rien n'est avarié que le déchet. */
    static boolean isSpoiled(@Nullable Level level, ItemStack stack) {
        if (stack.is(FoodRegistry.SPOILED_FOOD.get())) {
            return true;
        }
        return level != null && FoodStamp.state(stack, FoodStamp.now(level)) == DecayState.SPOILED;
    }

    /** Le résultat tient-il dans la case de sortie (vide, ou même objet à la date près, avec la place) ? */
    static boolean fits(ItemStack out, ItemStack result) {
        if (out.isEmpty()) {
            return result.getCount() <= result.getMaxStackSize();
        }
        // Sans la date : ouvrir le menu date la ration séchée de la sortie, le résultat neuf n'en a pas.
        return FoodEvents.sameWithoutStamp(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    /**
     * Dépose le résultat dans la case de sortie. Sortie vide : il y prend place tel quel. Sortie datée : le résultat neuf
     * prend sa place dans la pile, à la date moyenne, comme deux piles qui se joignent.
     */
    static void place(IItemHandlerModifiable handler, int slot, ItemStack result, Level level) {
        ItemStack out = handler.getStackInSlot(slot);
        if (out.isEmpty()) {
            handler.setStackInSlot(slot, result);
            return;
        }
        ItemStack grown = out.copy();
        if (FoodStamp.isStamped(grown)) {
            FoodStamp.setStamp(result, FoodDecay.roundDown(FoodStamp.now(level)));
            FoodEvents.merge(grown, result, grown.getMaxStackSize());
        } else {
            grown.grow(result.getCount());
        }
        handler.setStackInSlot(slot, grown);
    }
}
