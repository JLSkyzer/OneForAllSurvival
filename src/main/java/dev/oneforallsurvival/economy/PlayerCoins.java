package dev.oneforallsurvival.economy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Les pièces que porte un joueur, et la seule porte par où elles y entrent et en sortent (commandes, ramassage au
 * sol, somme de départ). Aujourd'hui l'inventaire seulement ; la bourse (tâche 5) s'ajoute ici, dans
 * {@link #held}, {@link #receive} et {@link #remove}, sans toucher aux appelants.
 *
 * <p>La pile tenue au curseur dans un écran ouvert n'est pas comptée : elle n'est pas dans l'inventaire.
 */
public final class PlayerCoins {

    private PlayerCoins() {}

    /** Le nombre de pièces de chaque coupure dans l'inventaire (armure et main gauche comprises). */
    public static EnumMap<Coin, Long> held(ServerPlayer player) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            Optional<Coin> coin = stack.isEmpty() ? Optional.empty() : Coin.fromItem(stack.getItem());
            coin.ifPresent(c -> out.merge(c, (long) stack.getCount(), Long::sum));
        }
        return out;
    }

    /**
     * Fait entrer jusqu'à {@code count} pièces chez le joueur.
     *
     * @return combien sont entrées (le reste n'a pas trouvé de place et n'est pas touché)
     */
    public static long receive(ServerPlayer player, Coin coin, long count) {
        long left = count;
        int max = new ItemStack(coin.item()).getMaxStackSize();
        while (left > 0L) {
            int size = (int) Math.min(left, max);
            ItemStack stack = new ItemStack(coin.item(), size);
            player.getInventory().add(stack);
            left -= size - stack.getCount();
            if (!stack.isEmpty()) {
                break;
            }
        }
        return count - left;
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
     * Retire exactement ces pièces de l'inventaire. L'appelant a vérifié qu'elles y sont ({@link #held}) ; si ce
     * n'est pas le cas, ce qui manque est ignoré.
     */
    public static void remove(ServerPlayer player, Map<Coin, Long> coins) {
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
     * bouge. La monnaie est rendue par {@link CoinMath#makeChange} ; ce qui ne rentre pas dans l'inventaire
     * tombe aux pieds du joueur.
     *
     * @return vrai si la somme a été retirée
     */
    public static boolean take(ServerPlayer player, long amount) {
        Optional<CoinMath.Payment> payment = CoinMath.makeChange(held(player), amount);
        if (payment.isEmpty()) {
            return false;
        }
        remove(player, payment.get().taken());
        giveOrDrop(player, payment.get().change());
        return true;
    }
}
