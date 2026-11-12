package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Les pièces que porte un joueur, et la seule porte par où elles y entrent et en sortent (commandes, ramassage au
 * sol, somme de départ). Ce sont les pièces de l'inventaire et le contenu de ses bourses ({@link Purse}) : elles
 * entrent d'abord dans la première bourse, puis dans l'inventaire ; elles sortent des bourses d'abord.
 * L'écran de la bourse, qui déplace des pièces entre la bourse et l'inventaire, passe par les variantes « seules »
 * ({@link #heldLoose}, {@link #receiveLoose}, {@link #removeLoose}).
 *
 * <p>La pile tenue au curseur dans un écran ouvert n'est pas comptée : elle n'est pas dans l'inventaire.
 */
public final class PlayerCoins {

    private PlayerCoins() {}

    /** Le nombre de pièces de chaque coupure que porte le joueur : l'inventaire et le contenu de ses bourses. */
    public static EnumMap<Coin, Long> held(ServerPlayer player) {
        EnumMap<Coin, Long> out = heldLoose(player);
        heldInPurses(player).forEach((coin, count) -> out.merge(coin, count, Long::sum));
        return out;
    }

    /** Le contenu des bourses de l'inventaire, coupure par coupure. */
    public static EnumMap<Coin, Long> heldInPurses(ServerPlayer player) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (ItemStack purse : Purse.purses(player)) {
            Purse.contents(purse).forEach((coin, count) -> out.merge(coin, count, Long::sum));
        }
        return out;
    }

    /** Le nombre de pièces de chaque coupure dans l'inventaire (armure et main gauche comprises), bourses exclues. */
    public static EnumMap<Coin, Long> heldLoose(ServerPlayer player) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            Optional<Coin> coin = stack.isEmpty() ? Optional.empty() : Coin.fromItem(stack.getItem());
            coin.ifPresent(c -> out.merge(c, (long) stack.getCount(), Long::sum));
        }
        return out;
    }

    /** Le nombre de pièces de cette coupure dans l'inventaire (armure et main gauche comprises). */
    private static long countOf(Inventory inventory, Coin coin) {
        long total = 0L;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == coin.item()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Fait entrer jusqu'à {@code count} pièces chez le joueur : dans la première bourse qui a de la place (dans
     * l'ordre des cases), puis dans l'inventaire.
     *
     * @return combien sont entrées (le reste n'a pas trouvé de place et n'est pas touché)
     */
    public static long receive(ServerPlayer player, Coin coin, long count) {
        long entered = 0L;
        for (ItemStack purse : Purse.purses(player)) {
            long fits = Math.min(count - entered, Purse.room(purse, coin));
            if (fits > 0L) {
                Purse.add(purse, coin, fits);
                entered += fits;
            }
            if (entered >= count) {
                return entered;
            }
        }
        return entered + receiveLoose(player, coin, count - entered);
    }

    /**
     * Comme {@link #receive}, mais dans l'inventaire seulement, jamais dans une bourse.
     *
     * <p>Ce qui est entré se lit dans l'inventaire (avant et après), jamais dans la pile passée à
     * {@code Inventory.add} : pour un joueur en créatif (capacité {@code instabuild}), le jeu vide la pile et
     * répond vrai même quand rien n'a trouvé de place (vérifié dans le bytecode de {@code Inventory.add} de la
     * 1.20.1). Se fier à la pile ferait croire à une entrée qui n'a pas eu lieu, et l'argent disparaîtrait.
     *
     * @return combien sont entrées (le reste n'a pas trouvé de place et n'est pas touché)
     */
    public static long receiveLoose(ServerPlayer player, Coin coin, long count) {
        long entered = 0L;
        int max = new ItemStack(coin.item()).getMaxStackSize();
        Inventory inventory = player.getInventory();
        while (entered < count) {
            int size = (int) Math.min(count - entered, max);
            long before = countOf(inventory, coin);
            inventory.add(new ItemStack(coin.item(), size));
            long gained = countOf(inventory, coin) - before;
            entered += gained;
            if (gained < size) {
                break;
            }
        }
        return entered;
    }

    /**
     * Donne les pièces : dans l'inventaire tant qu'il y a de la place, le reste tombe aux pieds du joueur en une
     * pièce au sol par coupure.
     */
    public static void giveOrDrop(ServerPlayer player, Map<Coin, Long> coins) {
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            long left = entry.getValue() - receive(player, entry.getKey(), entry.getValue());
            while (left > 0L) {
                int size = (int) Math.min(left, CoinDropEntity.MAX_COUNT);
                player.level().addFreshEntity(CoinDropEntity.at(player.level(), player.getX(), player.getY() + 0.3D,
                        player.getZ(), entry.getKey(), size));
                left -= size;
            }
        }
    }

    /**
     * Retire exactement ces pièces, des bourses d'abord, puis de l'inventaire. L'appelant a vérifié qu'elles y
     * sont ({@link #held}) ; si ce n'est pas le cas, ce qui manque est ignoré.
     */
    public static void remove(ServerPlayer player, Map<Coin, Long> coins) {
        EnumMap<Coin, Long> rest = new EnumMap<>(Coin.class);
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            long left = entry.getValue();
            for (ItemStack purse : Purse.purses(player)) {
                long moved = Math.min(left, Purse.count(purse, entry.getKey()));
                if (moved > 0L) {
                    Purse.remove(purse, entry.getKey(), moved);
                    left -= moved;
                }
            }
            if (left > 0L) {
                rest.put(entry.getKey(), left);
            }
        }
        removeLoose(player, rest);
    }

    /** Comme {@link #remove}, mais dans les pièces de l'inventaire seulement, jamais dans une bourse. */
    public static void removeLoose(ServerPlayer player, Map<Coin, Long> coins) {
        Inventory inventory = player.getInventory();
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            long left = entry.getValue();
            for (int slot = 0; slot < inventory.getContainerSize() && left > 0L; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.getItem() == entry.getKey().item()) {
                    int moved = (int) Math.min(left, stack.getCount());
                    stack.shrink(moved);
                    left -= moved;
                }
            }
        }
    }

    /**
     * Retire {@code amount} cuivres, jamais en partie : si les pièces portées ne couvrent pas la somme, rien ne
     * bouge. Les bourses paient seules quand elles suffisent ; sinon on prend dans tout ce qui est porté (bourses
     * d'abord). La monnaie est rendue par {@link CoinMath#makeChange}, dans la première bourse, puis
     * l'inventaire ; ce qui ne rentre nulle part tombe aux pieds du joueur.
     *
     * @return vrai si la somme a été retirée
     */
    public static boolean take(ServerPlayer player, long amount) {
        Optional<CoinMath.Payment> payment = CoinMath.makeChange(heldInPurses(player), amount);
        if (payment.isEmpty()) {
            payment = CoinMath.makeChange(held(player), amount);
        }
        if (payment.isEmpty()) {
            return false;
        }
        remove(player, payment.get().taken());
        giveOrDrop(player, payment.get().change());
        return true;
    }
}
