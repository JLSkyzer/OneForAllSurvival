package dev.oneforallsurvival.economy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * La seule porte par où le commerce rend des objets et de l'argent à quelqu'un : dans son inventaire s'il y a de
 * la place, sinon en attente chez le serveur ({@link HeldGoods}), jamais au sol et jamais au néant. Une pièce
 * passe par {@link PlayerCoins#receive}, un objet par {@code Inventory.add} lu avant et après.
 *
 * <p>Rien n'est écrit au registre ici : l'appelant dit de quel transfert il s'agit.
 */
public final class CommerceDelivery {

    /** Préfixe des clés de langue des messages de livraison. */
    public static final String MESSAGE = "message.oneforallsurvival.commerce.";

    /** Période de la livraison des attentes : 30 secondes. */
    public static final int PERIOD_TICKS = 600;

    /** Où est arrivé un paiement ; l'ordre va du plus proche du joueur au plus lointain. */
    public enum Destination { INVENTORY, SAFE, PENDING }

    private CommerceDelivery() {}

    // ---------------------------------------------------------------------------------------------
    // Rendre des objets
    // ---------------------------------------------------------------------------------------------

    /**
     * Rend ces piles à leur propriétaire connecté. Les pièces entrent par {@link PlayerCoins#receive}, les objets
     * par {@code Inventory.add} (ce qui est entré se lit dans l'inventaire, jamais dans la pile rendue : en
     * créatif {@code Inventory.add} vide la pile sans rien placer, voir {@link PlayerCoins#receiveLoose}). Ce qui
     * n'entre pas va en attente, avec un message.
     *
     * @return le nombre de piles mises en attente
     */
    public static int giveBack(ServerPlayer owner, List<ItemStack> stacks) {
        Rest rest = Rest.of(stacks, Map.of());
        try {
            // Un mort (ou un mourant) ne reçoit rien : son inventaire va être lâché ou perdu à la réapparition.
            if (owner.isAlive()) {
                deliver(owner, rest);
            }
        } finally {
            // Même si une livraison lève une exception, ce qui n'est pas encore entré n'est pas perdu.
            if (!rest.isEmpty()) {
                HeldGoods.get(owner.server).addPending(owner.getUUID(), rest.items, rest.coins);
                owner.sendSystemMessage(Component.translatable(MESSAGE + "pending"));
            }
        }
        return rest.items.size() + rest.coins.size();
    }

    /** Comme {@link #giveBack(ServerPlayer, List)} ; joueur absent : tout va en attente. */
    public static int giveBack(MinecraftServer server, UUID owner, List<ItemStack> stacks) {
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) {
            return giveBack(online, stacks);
        }
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                kept.add(stack.copy());
            }
        }
        HeldGoods.get(server).addPending(owner, kept, Map.of());
        return kept.size();
    }

    /**
     * Ce qui reste à livrer, tenu à jour pendant la livraison : chaque pièce ou objet entré en est retiré aussitôt.
     * Si la livraison s'interrompt (exception), le reste est exactement ce qui n'est pas entré. Les piles sont des
     * copies, jamais celles de l'appelant.
     */
    private static final class Rest {
        final List<ItemStack> items = new ArrayList<>();
        final EnumMap<Coin, Long> coins = new EnumMap<>(Coin.class);

        static Rest of(List<ItemStack> stacks, Map<Coin, Long> coins) {
            Rest rest = new Rest();
            coins.forEach((coin, count) -> {
                if (count > 0L) {
                    rest.coins.merge(coin, count, Long::sum);
                }
            });
            for (ItemStack stack : stacks) {
                if (stack.isEmpty()) {
                    continue;
                }
                Optional<Coin> coin = Coin.fromItem(stack.getItem());
                if (coin.isPresent()) {
                    rest.coins.merge(coin.get(), (long) stack.getCount(), Long::sum);
                } else {
                    rest.items.add(stack.copy());
                }
            }
            return rest;
        }

        boolean isEmpty() {
            return items.isEmpty() && coins.isEmpty();
        }

        long itemCount() {
            return countStacks(items);
        }
    }

    /** Fait entrer ce qu'on peut ; {@code rest} garde, à chaque instant, ce qui n'est pas encore entré. */
    private static void deliver(ServerPlayer player, Rest rest) {
        for (Coin coin : new ArrayList<>(rest.coins.keySet())) {
            long want = rest.coins.get(coin);
            long entered = PlayerCoins.receive(player, coin, want);
            if (entered >= want) {
                rest.coins.remove(coin);
            } else {
                rest.coins.put(coin, want - entered);
            }
        }
        java.util.Iterator<ItemStack> it = rest.items.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            int max = Math.max(1, stack.getMaxStackSize());
            while (!stack.isEmpty()) {
                int size = Math.min(stack.getCount(), max);
                long before = countOf(player.getInventory(), stack);
                player.getInventory().add(stack.copyWithCount(size));
                long gained = Math.max(0L, Math.min(size, countOf(player.getInventory(), stack) - before));
                stack.shrink((int) gained);
                if (gained < size) {
                    break;
                }
            }
            if (stack.isEmpty()) {
                it.remove();
            }
        }
    }

    /** Le nombre d'objets identiques (même objet, mêmes étiquettes) dans tout l'inventaire. */
    private static long countOf(Inventory inventory, ItemStack model) {
        long total = 0L;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, model)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------------
    // Payer quelqu'un
    // ---------------------------------------------------------------------------------------------

    /**
     * Verse {@code amount} cuivres à ce joueur, pièces comprises dans {@link CoinMath#decompose}. Connecté : les
     * pièces entrent chez lui ({@link PlayerCoins#receive}). Ce qui n'est pas entré va dans son coffre-fort s'il en
     * a un (le compte marche hors ligne), sinon en attente. Rien n'est écrit au registre.
     *
     * @return la destination de la dernière part, la plus lointaine du joueur ({@link Destination#INVENTORY} si
     *         rien n'était à verser)
     */
    public static Destination payTo(MinecraftServer server, UUID creditor, long amount) {
        Destination worst = Destination.INVENTORY;
        if (amount <= 0L) {
            return worst;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(creditor);
        SafeAccounts safes = SafeAccounts.get(server);
        boolean hasSafe = safes.safePos(creditor).isPresent();
        EnumMap<Coin, Long> pending = new EnumMap<>(Coin.class);
        for (Map.Entry<Coin, Long> part : CoinMath.decompose(amount).entrySet()) {
            long left = part.getValue();
            if (online != null && online.isAlive()) {
                left -= PlayerCoins.receive(online, part.getKey(), left);
            }
            if (left > 0L && hasSafe) {
                long fits = Math.min(left, safes.room(creditor, part.getKey()));
                if (fits > 0L) {
                    safes.deposit(creditor, part.getKey(), fits);
                    left -= fits;
                    worst = later(worst, Destination.SAFE);
                }
            }
            if (left > 0L) {
                pending.merge(part.getKey(), left, Long::sum);
                worst = later(worst, Destination.PENDING);
            }
        }
        if (!pending.isEmpty()) {
            HeldGoods.get(server).addPending(creditor, List.of(), pending);
            if (online != null) {
                online.sendSystemMessage(Component.translatable(MESSAGE + "pending"));
            }
        }
        return worst;
    }

    private static Destination later(Destination a, Destination b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    // ---------------------------------------------------------------------------------------------
    // Livrer ce qui attend
    // ---------------------------------------------------------------------------------------------

    /**
     * Livre à ce joueur ce que le serveur gardait pour lui ; ce qui ne tient pas reste en attente.
     *
     * @return vrai s'il reste quelque chose en attente
     */
    public static boolean deliverPending(ServerPlayer player) {
        return deliverPending(player, true);
    }

    private static boolean deliverPending(ServerPlayer player, boolean announceLeftovers) {
        HeldGoods held = HeldGoods.get(player.server);
        if (!held.hasPending(player.getUUID())) {
            return false;
        }
        // Un mort ne reçoit rien : sans keepInventory, ServerPlayer.restoreFrom ne recopie pas l'inventaire à la
        // réapparition, la livraison serait perdue. Elle attend la réapparition (onRespawn).
        if (!player.isAlive()) {
            return true;
        }
        HeldGoods.Pending taken = held.takePending(player.getUUID());
        Rest rest = Rest.of(taken.items(), taken.coins());
        EnumMap<Coin, Long> coinsBefore = new EnumMap<>(rest.coins);
        long itemsBefore = rest.itemCount();
        try {
            deliver(player, rest);
        } finally {
            // Retiré de l'attente avant la livraison : ce qui n'est pas entré y revient, exception ou non.
            if (!rest.isEmpty()) {
                held.addPending(player.getUUID(), rest.items, rest.coins);
            }
        }
        boolean progress = !rest.coins.equals(coinsBefore) || rest.itemCount() != itemsBefore;
        if (progress) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "delivered"));
        }
        if (!rest.isEmpty() && (announceLeftovers || progress)) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "pending"));
        }
        return !rest.isEmpty();
    }

    private static long countStacks(List<ItemStack> stacks) {
        long total = 0L;
        for (ItemStack stack : stacks) {
            total += stack.getCount();
        }
        return total;
    }

    /** Branché sur la connexion : ce qui attend le joueur lui est livré. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deliverPending(player, true);
        }
    }

    /** Branché sur la réapparition : ce qui attendait pendant la mort est livré au joueur vivant. */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deliverPending(player, true);
        }
    }

    /** Branché sur le tick du serveur : toutes les 30 secondes, une nouvelle tentative pour qui attend encore. */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % PERIOD_TICKS != 0) {
            return;
        }
        HeldGoods held = HeldGoods.get(event.getServer());
        for (UUID owner : held.pendingOwners()) {
            ServerPlayer online = event.getServer().getPlayerList().getPlayer(owner);
            if (online != null) {
                deliverPending(online, false);
            }
        }
    }

    /**
     * Branché sur le démarrage : charge la garde du serveur, donc convertit en attentes les gardes qu'un arrêt
     * brutal a laissées, et note le résultat à sauvegarder.
     */
    public static void onServerStarted(ServerStartedEvent event) {
        HeldGoods.get(event.getServer());
    }
}
