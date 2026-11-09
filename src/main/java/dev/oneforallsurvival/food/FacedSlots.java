package dev.oneforallsurvival.food;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.NotNull;

/** Ce qu'une face d'une machine voit de son inventaire : des cases de {@code from} à {@code to} (exclu), où l'on insère, d'où l'on extrait, ou les deux. */
final class FacedSlots extends RangedWrapper {

    private final boolean canInsert;
    private final boolean canExtract;

    FacedSlots(IItemHandlerModifiable handler, int from, int to, boolean canInsert, boolean canExtract) {
        super(handler, from, to);
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return canInsert ? super.insertItem(slot, stack, simulate) : stack;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return canExtract ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return canInsert && super.isItemValid(slot, stack);
    }
}
