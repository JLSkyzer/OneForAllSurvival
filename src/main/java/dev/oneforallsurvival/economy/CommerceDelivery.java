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

/**
 * La seule porte par où le commerce rend des objets et de l'argent à quelqu'un : dans son inventaire s'il y a de
 * la place ; sinon les <b>pièces</b>, et le contenu des <b>bourses</b>, vont dans son coffre-fort s'il en a un
 * ({@link SafeAccounts}, le compte marche hors ligne, dans la limite du plafond), et seulement ce qui n'a nulle part où
 * aller attend chez le serveur ({@link HeldGoods}) : objets non monétaires, bourses (vidées de ce qui est entré au
 * coffre-fort), pièces sans coffre-fort ou au-delà du plafond ; jamais au sol et jamais au néant. Ainsi l'attente n'est
 * pas une cachette pour l'argent de qui a un coffre-fort. Une pièce passe par
 * {@link PlayerCoins#receive}, un objet par {@code Inventory.add} lu avant et après.
 *
 * <p>Rien n'est écrit au registre ici : l'appelant dit de quel transfert il s'agit (verser dans son propre
 * coffre-fort n'en est pas un).
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
     * créatif {@code Inventory.add} vide la pile sans rien placer, voir {@link PlayerCoins#receiveLoose}). Les pièces
     * qui n'entrent pas vont dans son coffre-fort s'il en a un ; le reste va en attente. Un message dit où.
     *
     * @return le nombre de lignes mises en attente (une par objet, une par coupure)
     */
    public static int giveBack(ServerPlayer owner, List<ItemStack> stacks) {
        Rest rest = Rest.of(stacks, Map.of());
        Parked parked = Parked.NONE;
        try {
            // Un mort (ou un mourant) ne reçoit rien : son inventaire va être lâché ou perdu à la réapparition.
            if (owner.isAlive()) {
                deliver(owner, rest);
            }
        } finally {
            // Même si une livraison lève une exception, ce qui n'est pas encore entré n'est pas perdu.
            if (!rest.isEmpty()) {
                parked = park(owner.server, owner.getUUID(), rest);
            }
        }
        if (parked.toSafe()) {
            owner.sendSystemMessage(Component.translatable(MESSAGE + "safe"));
        }
        if (parked.pendingLines() > 0) {
            owner.sendSystemMessage(Component.translatable(MESSAGE + "pending"));
        }
        return parked.pendingLines();
    }

    /**
     * Comme {@link #giveBack(ServerPlayer, List)} ; joueur absent : les pièces vont dans son coffre-fort s'il en a un,
     * tout le reste en attente.
     */
    public static int giveBack(MinecraftServer server, UUID owner, List<ItemStack> stacks) {
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) {
            return giveBack(online, stacks);
        }
        Rest rest = Rest.of(stacks, Map.of());
        return rest.isEmpty() ? 0 : park(server, owner, rest).pendingLines();
    }

    /** Ce que {@link #park} a fait du reste : des pièces au coffre-fort, et combien de lignes en attente. */
    private record Parked(boolean toSafe, int pendingLines) {
        static final Parked NONE = new Parked(false, 0);
    }

    /**
     * Range ce qui n'a pas pu entrer chez le joueur : ses pièces et le contenu de ses bourses dans son coffre-fort tant
     * qu'il y en a un et qu'il a la place, puis tout ce qui reste (les bourses comprises) en attente. {@code rest} est
     * vidé.
     */
    private static Parked park(MinecraftServer server, UUID owner, Rest rest) {
        boolean toSafe = intoSafe(server, owner, rest.coins);
        toSafe |= pursesIntoSafe(server, owner, rest.items);
        int lines = rest.items.size() + rest.coins.size();
        if (lines > 0) {
            HeldGoods.get(server).addPending(owner, rest.items, rest.coins);
        }
        rest.items.clear();
        rest.coins.clear();
        return new Parked(toSafe, lines);
    }

    /**
     * Verse dans le coffre-fort de ce joueur, s'il en a un, ce qui y tient de ces pièces, et le retire de
     * {@code coins}. Le compte du coffre-fort marche hors ligne et chunk déchargé.
     *
     * @return vrai si au moins une pièce y est entrée
     */
    private static boolean intoSafe(MinecraftServer server, UUID owner, Map<Coin, Long> coins) {
        if (coins.isEmpty()) {
            return false;
        }
        SafeAccounts safes = SafeAccounts.get(server);
        if (safes.safePos(owner).isEmpty()) {
            return false;
        }
        boolean any = false;
        for (Coin coin : new ArrayList<>(coins.keySet())) {
            long left = coins.get(coin);
            long fits = Math.min(left, safes.room(owner, coin));
            if (fits > 0L) {
                safes.deposit(owner, coin, fits);
                left -= fits;
                any = true;
            }
            if (left > 0L) {
                coins.put(coin, left);
            } else {
                coins.remove(coin);
            }
        }
        return any;
    }

    /**
     * Vide dans le coffre-fort de ce joueur, s'il en a un, le contenu de ces bourses, dans la limite de sa place ; une
     * bourse garde ce qui n'a pas tenu. Les piles sont celles de {@link Rest} (des copies).
     *
     * @return vrai si au moins une pièce y est entrée
     */
    private static boolean pursesIntoSafe(MinecraftServer server, UUID owner, List<ItemStack> items) {
        SafeAccounts safes = SafeAccounts.get(server);
        if (safes.safePos(owner).isEmpty()) {
            return false;
        }
        boolean any = false;
        for (ItemStack stack : items) {
            if (!Purse.isPurse(stack)) {
                continue;
            }
            for (Map.Entry<Coin, Long> part : Purse.contents(stack).entrySet()) {
                long fits = Math.min(part.getValue(), safes.room(owner, part.getKey()));
                if (fits > 0L && Purse.remove(stack, part.getKey(), fits)) {
                    safes.deposit(owner, part.getKey(), fits);
                    any = true;
                }
            }
        }
        return any;
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
        EnumMap<Coin, Long> left = new EnumMap<>(Coin.class);
        for (Map.Entry<Coin, Long> part : CoinMath.decompose(amount).entrySet()) {
            long count = part.getValue();
            if (online != null && online.isAlive()) {
                count -= PlayerCoins.receive(online, part.getKey(), count);
            }
            if (count > 0L) {
                left.put(part.getKey(), count);
            }
        }
        if (intoSafe(server, creditor, left)) {
            worst = Destination.SAFE;
        }
        if (!left.isEmpty()) {
            HeldGoods.get(server).addPending(creditor, List.of(), left);
            worst = Destination.PENDING;
            if (online != null) {
                online.sendSystemMessage(Component.translatable(MESSAGE + "pending"));
            }
        }
        return worst;
    }

    /**
     * Verse {@code amount} cuivres à ce joueur (pièces de {@link CoinMath#decompose}) dans l'ordre d'un salaire :
     * son coffre-fort s'il en a un, dans la limite du plafond (le compte marche hors ligne) ; puis son inventaire s'il
     * est connecté et vivant ({@link PlayerCoins#receive}) ; le reste en attente. Distinct de {@link #payTo} (factures :
     * l'inventaire d'abord). Rien n'est écrit au registre et aucun message n'est envoyé : l'appelant le fait.
     *
     * @return la destination la plus lointaine du joueur parmi celles qui ont reçu une part ({@link Destination#SAFE}
     *         si rien n'était à verser)
     */
    public static Destination payToSafeFirst(MinecraftServer server, UUID payee, long amount) {
        if (amount <= 0L) {
            return Destination.SAFE;
        }
        EnumMap<Coin, Long> left = new EnumMap<>(CoinMath.decompose(amount));
        left.values().removeIf(count -> count <= 0L);
        Destination worst = null;
        if (intoSafe(server, payee, left)) {
            worst = Destination.SAFE;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(payee);
        if (!left.isEmpty() && online != null && online.isAlive()) {
            boolean any = false;
            for (Coin coin : new ArrayList<>(left.keySet())) {
                long count = left.get(coin);
                long entered = PlayerCoins.receive(online, coin, count);
                any |= entered > 0L;
                if (entered >= count) {
                    left.remove(coin);
                } else {
                    left.put(coin, count - entered);
                }
            }
            if (any) {
                // Une part au moins est dans l'inventaire : l'avis le dit, même si le reste est au coffre-fort.
                worst = Destination.INVENTORY;
            }
        }
        if (!left.isEmpty()) {
            HeldGoods.get(server).addPending(payee, List.of(), left);
            worst = Destination.PENDING;
        }
        return worst == null ? Destination.SAFE : worst;
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
        boolean delivered = false;
        Parked parked = Parked.NONE;
        try {
            deliver(player, rest);
            delivered = !rest.coins.equals(coinsBefore) || rest.itemCount() != itemsBefore;
        } finally {
            // Retiré de l'attente avant la livraison : ce qui n'est pas entré va au coffre-fort (pièces) ou revient en
            // attente, exception ou non.
            if (!rest.isEmpty()) {
                parked = park(player.server, player.getUUID(), rest);
            }
        }
        if (delivered) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "delivered"));
        }
        if (parked.toSafe()) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "safe"));
        }
        if (parked.pendingLines() > 0 && (announceLeftovers || delivered || parked.toSafe())) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "pending"));
        }
        return parked.pendingLines() > 0;
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

}
