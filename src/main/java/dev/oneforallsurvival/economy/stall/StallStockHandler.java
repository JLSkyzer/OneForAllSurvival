package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.MoneyStorageRules;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Le stock d'un étal : 27 cases qui refusent l'argent ({@link #isItemValid}), pour toute voie qui passe par le
 * handler (clic sur une case, maj-clic, entonnoir, mod).
 *
 * <p>Ce qui est exposé aux automatismes n'est pas ce handler mais {@link #insertOnly()} : une vue qui reçoit et ne
 * rend jamais rien. Une trémie, un entonnoir de Create ou un tuyau garnissent le stock, aucun ne le vide.
 */
public final class StallStockHandler extends ItemStackHandler {

    public static final int SIZE = 27;

    private final IItemHandler insertOnly = new InsertOnly();
    private Runnable onChange = () -> {};

    public StallStockHandler() {
        super(SIZE);
    }

    /** Appelé à chaque changement de contenu (le bloc se marque à sauvegarder). */
    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChange.run();
    }

    /** Jamais d'argent dans un stock : ni une pièce, ni une bourse qui en contient. */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !MoneyStorageRules.isMoney(stack);
    }

    public boolean isEmpty() {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** La vue exposée en capacité d'objets, sur toutes les faces : elle reçoit, elle ne rend rien. */
    public IItemHandler insertOnly() {
        return insertOnly;
    }

    /** Le stock sous une forme qui ne laisse que déposer : {@code extractItem} rend toujours une pile vide. */
    private final class InsertOnly implements IItemHandler {
        @Override
        public int getSlots() {
            return StallStockHandler.this.getSlots();
        }

        /** Une copie : lire le stock ne donne pas de quoi le modifier. */
        @Override
        public ItemStack getStackInSlot(int slot) {
            return StallStockHandler.this.getStackInSlot(slot).copy();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return StallStockHandler.this.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return StallStockHandler.this.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return StallStockHandler.this.isItemValid(slot, stack);
        }
    }
}
