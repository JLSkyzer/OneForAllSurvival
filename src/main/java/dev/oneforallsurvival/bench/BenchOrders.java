package dev.oneforallsurvival.bench;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.role.RoleGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Les commandes à l'établi : payer d'avance, mettre en file, annuler. Tout ou rien : une commande qui
 * ne peut pas être payée en entier ne prélève rien.
 */
public final class BenchOrders {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Distance maximale (au carré) entre le joueur et l'établi, en blocs. */
    public static final double MAX_DISTANCE_SQR = 8.0 * 8.0;
    public static final int MAX_COUNT = 64;

    private BenchOrders() {}

    /**
     * Commande {@code count} exemplaires d'une recette de l'établi. Chaque exemplaire devient une entrée
     * de la file, payée par des piles prélevées dans l'inventaire du joueur.
     */
    public static OrderResult order(ServerPlayer player, ResourceLocation recipeId, int count, BlockPos bench) {
        if (!RoleGate.canDoEngineerWork(player)) {
            return OrderResult.NOT_ENGINEER;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(bench)) > MAX_DISTANCE_SQR) {
            return OrderResult.TOO_FAR;
        }
        if (count < 1 || count > MAX_COUNT) {
            return OrderResult.BAD_COUNT;
        }
        Optional<BenchRecipe> found = BenchCatalog.current().byId(recipeId);
        if (found.isEmpty()) {
            return OrderResult.UNKNOWN_RECIPE;
        }
        BenchRecipe recipe = found.get();
        PlayerWorkshop workshop = WorkshopSavedData.get(player.server).of(player.getUUID());
        if ((long) workshop.queue().size() + count > BenchConfig.maxQueue()) {
            return OrderResult.QUEUE_FULL;
        }

        // Plan unité par unité sur une copie de l'inventaire : rien n'est touché tant que tout n'est pas payable.
        Inventory inventory = player.getInventory();
        List<ItemStack> simulated = new ArrayList<>(inventory.items.size());
        for (ItemStack stack : inventory.items) {
            simulated.add(stack.copy());
        }
        int[] taken = new int[simulated.size()];
        List<WorkshopEntry> entries = new ArrayList<>(count);
        long totalTicks = (long) recipe.seconds() * 20L;
        for (int unit = 0; unit < count; unit++) {
            Optional<int[]> plan = Withdrawal.plan(recipe.ingredients(), simulated);
            if (plan.isEmpty()) {
                return OrderResult.MISSING_INGREDIENTS;
            }
            List<ItemStack> paid = new ArrayList<>();
            List<ItemStack> remainders = new ArrayList<>();
            int[] slots = plan.get();
            for (int i = 0; i < slots.length; i++) {
                Ingredient ingredient = recipe.ingredients().get(i);
                if (slots[i] < 0 || ingredient == Ingredient.EMPTY) {
                    continue; // un ingrédient vide ne coûte rien
                }
                ItemStack source = simulated.get(slots[i]);
                ItemStack one = source.copyWithCount(1);
                paid.add(one);
                ItemStack remainder = one.getCraftingRemainingItem();
                if (!remainder.isEmpty()) {
                    remainders.add(remainder);
                }
                source.shrink(1);
                taken[slots[i]]++;
            }
            entries.add(new WorkshopEntry(UUID.randomUUID(), recipe.id(), recipe.result(), paid, remainders,
                    (int) Math.min(totalTicks, Integer.MAX_VALUE), 0.0, player.level().dimension(), bench, false));
        }

        // Tout est payable : on prélève, puis on met en file.
        for (int s = 0; s < taken.length; s++) {
            if (taken[s] > 0) {
                inventory.items.get(s).shrink(taken[s]);
            }
        }
        inventory.setChanged();
        for (WorkshopEntry entry : entries) {
            workshop.add(entry);
        }
        player.inventoryMenu.broadcastChanges();
        BenchNetwork.sendQueue(player);
        return OrderResult.OK;
    }

    /**
     * Annule une fabrication du joueur : les piles payées reviennent dans son inventaire, et ce qui n'y
     * entre pas va au stock, au même tick. Aucune pile n'est jamais jetée. Pas de contrôle de distance,
     * mais le rôle est requis, et un joueur déconnecté ne peut pas annuler.
     *
     * @return vrai si l'entrée existait et a été annulée
     */
    public static boolean cancel(ServerPlayer player, UUID entryId) {
        if (player.connection == null || !player.connection.connection.isConnected()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return false;
        }
        if (!RoleGate.canDoEngineerWork(player)) {
            return false;
        }
        PlayerWorkshop workshop = WorkshopSavedData.get(player.server).of(player.getUUID());
        Optional<WorkshopEntry> removed = workshop.cancel(entryId);
        if (removed.isEmpty()) {
            return false;
        }
        for (ItemStack paid : removed.get().paid()) {
            deliver(player, workshop, paid);
        }
        player.inventoryMenu.broadcastChanges();
        BenchNetwork.sendQueue(player);
        return true;
    }

    /** Inventaire d'abord, le reste au stock ; une erreur imprévue envoie tout au stock plutôt que de perdre. */
    private static void deliver(ServerPlayer player, PlayerWorkshop workshop, ItemStack paid) {
        ItemStack left = paid.copy();
        try {
            player.getInventory().add(left);
        } catch (RuntimeException e) {
            LOGGER.error("Etabli : rendu d'une pile annulee impossible, elle va au stock", e);
            left = paid.copy();
        }
        if (!left.isEmpty()) {
            workshop.addToStock(left);
        }
    }
}
