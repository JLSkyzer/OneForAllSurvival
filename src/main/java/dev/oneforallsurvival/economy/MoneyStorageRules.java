package dev.oneforallsurvival.economy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Où une pièce peut se ranger. Partout ailleurs que dans l'inventaire du joueur et dans les écrans permis, elle
 * est refusée : les mixins {@code SlotMixin} et {@code SlotItemHandlerMixin} interrogent {@link #mayHold}, et
 * l'entonnoir interroge {@link #isMoney}.
 *
 * <p>Écrans permis : l'inventaire du joueur (sa grille 2x2 comprise) et la table de fabrication, car le change se
 * fait dans une grille de fabrication ; plus les types d'écran déclarés par {@link #register(MenuType)} (coffre-fort,
 * bourse...) et par la configuration ({@link EconomyConfig#extraAllowedMenus()}). Un type de la liste interdite de
 * la configuration l'emporte sur tout, sauf sur les cases de l'inventaire du joueur lui-même.
 *
 * <p>La case résultat d'une fabrication ne reçoit jamais d'objet du joueur ({@code mayPlace} y est faux), et les
 * pièces qui en sortent passent par {@link CoinRecipeGuard}.
 */
public final class MoneyStorageRules {

    private static final Set<MenuType<?>> ALLOWED = ConcurrentHashMap.newKeySet();
    private static final List<Predicate<ItemStack>> MONEY = new CopyOnWriteArrayList<>();

    private MoneyStorageRules() {}

    /**
     * Vrai pour une pièce. Les lots suivants ajoutent par {@link #registerMoneyPredicate} ce qui compte aussi
     * comme de l'argent (la bourse qui contient au moins une pièce).
     */
    public static boolean isMoney(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (Coin.fromItem(stack.getItem()).isPresent()) {
            return true;
        }
        for (Predicate<ItemStack> predicate : MONEY) {
            if (predicate.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Point d'extension : tout objet que ce prédicat accepte est traité comme de l'argent partout. */
    public static void registerMoneyPredicate(Predicate<ItemStack> predicate) {
        MONEY.add(predicate);
    }

    /** Déclare un type d'écran comme rangement permis (coffre-fort, bourse...). À appeler une fois le type enregistré. */
    public static void register(MenuType<?> type) {
        ALLOWED.add(type);
    }

    /** Retire une déclaration (essais seulement : en jeu, un rangement déclaré le reste). */
    public static void unregister(MenuType<?> type) {
        ALLOWED.remove(type);
    }

    /** Vrai si l'écran est un rangement permis : le sien, la table, un type déclaré ou ajouté par la configuration. */
    public static boolean isAllowedMenu(AbstractContainerMenu menu) {
        ResourceLocation id = typeId(menu);
        if (id != null && EconomyConfig.extraForbiddenMenus().contains(id)) {
            return false;
        }
        if (menu instanceof InventoryMenu || menu instanceof CraftingMenu) {
            return true;
        }
        if (id == null) {
            return false;
        }
        MenuType<?> type = ForgeRegistries.MENU_TYPES.getValue(id);
        return (type != null && ALLOWED.contains(type)) || EconomyConfig.extraAllowedMenus().contains(id);
    }

    /**
     * Cette case peut-elle recevoir cet objet ? Toujours oui hors argent. Pour de l'argent : oui dans l'inventaire
     * du joueur, oui dans un écran permis, non partout ailleurs (y compris une case dont on ne connaît pas l'écran).
     */
    public static boolean mayHold(Slot slot, ItemStack stack) {
        return !isMoney(stack) || isMoneySlot(slot);
    }

    /** La case est-elle un endroit où l'argent a le droit de se trouver ? */
    public static boolean isMoneySlot(Slot slot) {
        if (slot.container instanceof Inventory) {
            return true;
        }
        AbstractContainerMenu menu = slot instanceof SlotOwner owner ? owner.oneforall$menu() : null;
        return menu != null && isAllowedMenu(menu);
    }

    /** L'identifiant du type d'écran, ou null pour l'inventaire du joueur (sans type) ou un type non enregistré. */
    public static ResourceLocation typeId(AbstractContainerMenu menu) {
        try {
            return ForgeRegistries.MENU_TYPES.getKey(menu.getType());
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }
}
