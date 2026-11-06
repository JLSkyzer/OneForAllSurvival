package dev.oneforallsurvival.bench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/** La file de fabrication et le stock d'un joueur. Seule la tête de file avance. */
public final class PlayerWorkshop {

    /** Au plus une sauvegarde par seconde de progression. */
    private static final int DIRTY_EVERY_TICKS = 20;

    private final WorkshopSavedData owner;
    private final List<WorkshopEntry> queue = new ArrayList<>();
    private final List<ItemStack> stock = new ArrayList<>();
    private int ticksSinceDirty;
    /** L'établi de la tête de file telle que {@link WorkshopSavedData} l'a comptée ; null si la file est vide. */
    private WorkshopSavedData.BenchSite headSite;

    PlayerWorkshop(WorkshopSavedData owner) {
        this.owner = owner;
    }

    /** Vue en lecture seule : toute modification passe par les méthodes d'ici, qui marquent la sauvegarde. */
    public List<WorkshopEntry> queue() {
        return Collections.unmodifiableList(queue);
    }

    public List<ItemStack> stock() {
        return Collections.unmodifiableList(stock);
    }

    public void add(WorkshopEntry entry) {
        queue.add(entry);
        changed();
    }

    /** Retire l'entrée et la rend (avec ses piles payées) ; vide si l'identifiant est inconnu. */
    public Optional<WorkshopEntry> cancel(UUID id) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).id().equals(id)) {
                WorkshopEntry removed = queue.remove(i);
                changed();
                return Optional.of(removed);
            }
        }
        return Optional.empty();
    }

    /** Fait avancer la tête de file ; à la fin, résultat et restes rejoignent le stock. */
    public void advance(double ticks) {
        if (queue.isEmpty() || ticks <= 0) {
            return;
        }
        WorkshopEntry head = queue.get(0);
        head.setDoneTicks(head.doneTicks() + ticks);
        if (head.isDone()) {
            // Le surplus de temps est abandonné : l'entrée suivante repart de zéro.
            queue.remove(0);
            addToStock(head.result());
            for (ItemStack remainder : head.remainders()) {
                addToStock(remainder);
            }
            changed();
        } else if (++ticksSinceDirty >= DIRTY_EVERY_TICKS) {
            ticksSinceDirty = 0;
            owner.setDirty();
        }
    }

    /** Fusionne avec une pile identique jusqu'à sa taille maximale ; le reste ouvre de nouvelles piles. */
    public void addToStock(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack left = stack.copy();
        for (ItemStack existing : stock) {
            if (left.isEmpty()) {
                break;
            }
            if (existing.getCount() < existing.getMaxStackSize() && ItemStack.isSameItemSameTags(existing, left)) {
                int moved = Math.min(left.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                left.shrink(moved);
            }
        }
        while (!left.isEmpty()) {
            int size = Math.min(left.getCount(), left.getMaxStackSize());
            stock.add(left.split(size));
        }
        owner.setDirty();
    }

    /**
     * Retire jusqu'à {@code count} objets de la pile {@code index} et les rend, détachés du stock. La pile
     * entière est retirée de la liste : la suite remonte, sans trou. Hors limites ou quantité nulle : vide,
     * sans rien marquer. Seule voie de retrait pour les menus, car {@link #stock()} expose des piles vivantes.
     */
    public ItemStack takeFromStock(int index, int count) {
        if (index < 0 || index >= stock.size() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack live = stock.get(index);
        ItemStack taken = count >= live.getCount() ? stock.remove(index) : live.split(count);
        owner.setDirty();
        return taken;
    }

    /** Chargement uniquement : ajoute sans notifier. */
    void restoreEntry(WorkshopEntry entry) {
        queue.add(entry);
    }

    void restoreStock(ItemStack stack) {
        stock.add(stack);
    }

    WorkshopSavedData.BenchSite headSite() {
        return headSite;
    }

    void setHeadSite(WorkshopSavedData.BenchSite headSite) {
        this.headSite = headSite;
    }

    boolean isActive() {
        return !queue.isEmpty();
    }

    boolean isEmptyWorkshop() {
        return queue.isEmpty() && stock.isEmpty();
    }

    private void changed() {
        ticksSinceDirty = 0;
        owner.queueChanged(this);
        owner.setDirty();
    }
}
