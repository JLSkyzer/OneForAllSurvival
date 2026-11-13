package dev.oneforallsurvival.economy.trade;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.InventoryFit;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.Receipt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;

/**
 * L'échange de main à main (feature 70) : les demandes, les sessions ouvertes, leur exécution et leur annulation.
 * Fil du serveur seulement ; l'état vit en mémoire (une demande ou un échange ne survit pas à un redémarrage, les
 * gardes, elles, si : voir {@link HeldGoods}).
 *
 * <p>Annulation (fenêtre fermée, éloignement, changement de dimension, déconnexion, mort, arrêt du serveur) :
 * chaque garde est fermée et rendue à son propriétaire par {@link CommerceDelivery#giveBack} (inventaire, sinon
 * attente ; jamais au sol). La déconnexion est traitée sur {@code PlayerLoggedOutEvent}, que Forge publie avant la
 * sauvegarde du joueur ; la mort sur {@code LivingDeathEvent} en priorité la plus haute, avant les lâchers.
 */
public final class TradeService {

    public static final String MESSAGE = "message.oneforallsurvival.trade.";

    /** Période du contrôle des distances : 10 ticks. */
    public static final int CHECK_PERIOD = 10;

    public enum RequestResult { SENT, ACCEPTED, SELF, TOO_FAR, BUSY, NO_REQUEST, EXPIRED, DENIED }

    public enum CancelReason { CLOSED, LOGGED_OUT, TOO_FAR, DIED, OTHER_LEFT, SERVER_STOP }

    /** Les demandes en cours : destinataire, puis demandeur et heure de la demande (horloge du service). */
    private static final Map<UUID, Map<UUID, Long>> REQUESTS = new HashMap<>();
    private static final Map<UUID, TradeSession> SESSIONS = new HashMap<>();
    private static LongSupplier clock = System::currentTimeMillis;

    private TradeService() {}

    /** Essais seulement : l'horloge des demandes (millisecondes). */
    public static void setClock(LongSupplier newClock) {
        clock = newClock;
    }

    public static void resetClock() {
        clock = System::currentTimeMillis;
    }

    @Nullable
    public static TradeSession sessionOf(UUID player) {
        return SESSIONS.get(player);
    }

    // ---------------------------------------------------------------------------------------------
    // Demandes
    // ---------------------------------------------------------------------------------------------

    /** Vrai si les deux sont connectés, dans la même dimension, vivants et à portée l'un de l'autre. */
    static boolean inReach(ServerPlayer a, ServerPlayer b) {
        if (a.isRemoved() || b.isRemoved() || !a.isAlive() || !b.isAlive() || a.level() != b.level()) {
            return false;
        }
        double max = EconomyConfig.tradeMaxDistance();
        return a.distanceToSqr(b) <= max * max;
    }

    private static boolean online(ServerPlayer player) {
        return player.server.getPlayerList().getPlayer(player.getUUID()) == player && !player.hasDisconnected();
    }

    private static boolean expired(long requestedAt) {
        return clock.getAsLong() - requestedAt > EconomyConfig.tradeRequestSeconds() * 1000L;
    }

    /**
     * {@code from} propose un échange à {@code to}. Si {@code to} avait déjà proposé à {@code from} (demande encore
     * valable), c'est une acceptation : l'échange s'ouvre.
     */
    public static RequestResult request(ServerPlayer from, ServerPlayer to) {
        if (from.getUUID().equals(to.getUUID())) {
            return RequestResult.SELF;
        }
        if (SESSIONS.containsKey(from.getUUID()) || SESSIONS.containsKey(to.getUUID())) {
            return RequestResult.BUSY;
        }
        if (!online(from) || !online(to) || !inReach(from, to)) {
            return RequestResult.TOO_FAR;
        }
        Map<UUID, Long> toMe = REQUESTS.get(from.getUUID());
        Long reverse = toMe == null ? null : toMe.get(to.getUUID());
        if (reverse != null && !expired(reverse)) {
            return accept(from, to);
        }
        REQUESTS.computeIfAbsent(to.getUUID(), k -> new HashMap<>()).put(from.getUUID(), clock.getAsLong());
        String fromName = from.getGameProfile().getName();
        MutableComponent accept = Component.translatable(MESSAGE + "accept_link").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept " + fromName))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(MESSAGE + "accept_hover", fromName))));
        MutableComponent deny = Component.translatable(MESSAGE + "deny_link").withStyle(Style.EMPTY
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny " + fromName))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(MESSAGE + "deny_hover", fromName))));
        to.sendSystemMessage(Component.translatable(MESSAGE + "request_received", fromName,
                EconomyConfig.tradeRequestSeconds()).append(" ").append(accept).append(" ").append(deny));
        from.sendSystemMessage(Component.translatable(MESSAGE + "request_sent", to.getGameProfile().getName()));
        return RequestResult.SENT;
    }

    /** {@code to} accepte la demande de {@code from} : la fenêtre s'ouvre chez les deux. */
    public static RequestResult accept(ServerPlayer to, ServerPlayer from) {
        Map<UUID, Long> mine = REQUESTS.get(to.getUUID());
        Long at = mine == null ? null : mine.get(from.getUUID());
        if (at == null) {
            return RequestResult.NO_REQUEST;
        }
        if (expired(at)) {
            forget(to.getUUID(), from.getUUID());
            return RequestResult.EXPIRED;
        }
        if (SESSIONS.containsKey(from.getUUID()) || SESSIONS.containsKey(to.getUUID())) {
            return RequestResult.BUSY;
        }
        if (!online(from) || !online(to) || !inReach(from, to)) {
            return RequestResult.TOO_FAR;
        }
        forget(to.getUUID(), from.getUUID());
        forget(from.getUUID(), to.getUUID());
        open(from, to);
        return RequestResult.ACCEPTED;
    }

    /** {@code to} refuse la demande de {@code from}. */
    public static RequestResult deny(ServerPlayer to, ServerPlayer from) {
        Map<UUID, Long> mine = REQUESTS.get(to.getUUID());
        if (mine == null || mine.get(from.getUUID()) == null) {
            return RequestResult.NO_REQUEST;
        }
        forget(to.getUUID(), from.getUUID());
        from.sendSystemMessage(Component.translatable(MESSAGE + "denied", to.getGameProfile().getName()));
        return RequestResult.DENIED;
    }

    private static void forget(UUID to, UUID from) {
        Map<UUID, Long> mine = REQUESTS.get(to);
        if (mine != null) {
            mine.remove(from);
            if (mine.isEmpty()) {
                REQUESTS.remove(to);
            }
        }
    }

    private static void open(ServerPlayer first, ServerPlayer second) {
        HeldGoods held = HeldGoods.get(first.server);
        SimpleContainer firstOffer = held.openEscrow(first.getUUID(), TradeMenu.OFFER_SIZE);
        SimpleContainer secondOffer = held.openEscrow(second.getUUID(), TradeMenu.OFFER_SIZE);
        TradeSession session = new TradeSession(first.server, first, second, firstOffer, secondOffer);
        SESSIONS.put(first.getUUID(), session);
        SESSIONS.put(second.getUUID(), session);
        for (ServerPlayer player : List.of(first, second)) {
            ServerPlayer other = player == first ? second : first;
            String otherName = other.getGameProfile().getName();
            NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> {
                TradeMenu menu = new TradeMenu(id, inventory, session);
                session.attach(p.getUUID(), menu);
                return menu;
            }, Component.translatable("gui.oneforallsurvival.trade.title", otherName)), buf -> buf.writeUtf(otherName, 64));
            if (session.ended()) {
                // Ouvrir la seconde fenêtre a fermé autre chose qui a tout annulé : ne rien ouvrir de plus.
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Exécution
    // ---------------------------------------------------------------------------------------------

    /** Les objets d'une garde, copiés, dans l'ordre des cases. */
    private static List<ItemStack> contents(SimpleContainer offer) {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < offer.getContainerSize(); slot++) {
            if (!offer.getItem(slot).isEmpty()) {
                out.add(offer.getItem(slot).copy());
            }
        }
        return out;
    }

    /** La valeur en cuivres de l'argent d'une liste : pièces et contenu des bourses. */
    static long moneyValue(List<ItemStack> stacks) {
        long total = 0L;
        for (ItemStack stack : stacks) {
            long value = Purse.isPurse(stack) ? Purse.total(stack)
                    : Coin.fromItem(stack.getItem()).map(c -> c.value() * stack.getCount()).orElse(0L);
            total = total + value < total ? Long.MAX_VALUE : total + value;
        }
        return total;
    }

    @Nullable
    private static ServerPlayer present(TradeSession session, UUID player) {
        ServerPlayer online = session.server().getPlayerList().getPlayer(player);
        if (online == null || online.hasDisconnected() || online.containerMenu != session.menuOf(player)) {
            return null;
        }
        return online;
    }

    /**
     * Les deux ont confirmé la même version. Vérifier, puis engager : présence, portée et fenêtre ouverte des deux,
     * place de chacun pour ce qu'il reçoit (et son reçu). Après la vérification rien ne peut plus échouer ; ce que
     * l'imprévu empêcherait d'entrer va en attente.
     */
    static TradeSession.ConfirmResult execute(TradeSession session) {
        ServerPlayer first = present(session, session.first());
        ServerPlayer second = present(session, session.second());
        if (first == null || second == null || !inReach(first, second)) {
            cancel(session, CancelReason.OTHER_LEFT);
            return TradeSession.ConfirmResult.CHANGED_HANDS;
        }
        List<ItemStack> fromFirst = contents(session.offerOf(session.first()));
        List<ItemStack> fromSecond = contents(session.offerOf(session.second()));
        long when = System.currentTimeMillis();
        ItemStack firstReceipt = session.receipt() ? Receipt.forTrade(first, second, fromFirst, fromSecond, when) : ItemStack.EMPTY;
        ItemStack secondReceipt = session.receipt() ? Receipt.forTrade(second, first, fromSecond, fromFirst, when) : ItemStack.EMPTY;
        List<ItemStack> toFirst = incoming(fromSecond, firstReceipt, session.menuOf(session.first()));
        List<ItemStack> toSecond = incoming(fromFirst, secondReceipt, session.menuOf(session.second()));
        boolean firstFits = InventoryFit.fits(first, toFirst);
        boolean secondFits = InventoryFit.fits(second, toSecond);
        if (!firstFits || !secondFits) {
            session.dropConfirmations();
            for (ServerPlayer player : List.of(first, second)) {
                if (!firstFits) {
                    player.sendSystemMessage(Component.translatable(MESSAGE + "no_room", first.getGameProfile().getName()));
                }
                if (!secondFits) {
                    player.sendSystemMessage(Component.translatable(MESSAGE + "no_room", second.getGameProfile().getName()));
                }
            }
            broadcast(session);
            return TradeSession.ConfirmResult.NO_ROOM;
        }

        // Engager : tout dans ce tick, rien ne peut plus être refusé.
        session.end();
        SESSIONS.remove(session.first());
        SESSIONS.remove(session.second());
        HeldGoods held = HeldGoods.get(session.server());
        held.closeEscrow(session.first());
        held.closeEscrow(session.second());
        takeCarried(session.menuOf(session.first()));
        takeCarried(session.menuOf(session.second()));
        CommerceDelivery.giveBack(first, toFirst);
        CommerceDelivery.giveBack(second, toSecond);
        EconomyLedger ledger = EconomyLedger.get(session.server());
        String firstName = first.getGameProfile().getName();
        String secondName = second.getGameProfile().getName();
        ledger.transferred(MoneyTransfer.TRADE, moneyValue(fromFirst), first.getUUID(), firstName + " -> " + secondName);
        ledger.transferred(MoneyTransfer.TRADE, moneyValue(fromSecond), second.getUUID(), secondName + " -> " + firstName);
        for (ServerPlayer player : List.of(first, second)) {
            player.closeContainer();
            player.sendSystemMessage(Component.translatable(MESSAGE + "done",
                    session.nameOf(session.other(player.getUUID()))));
        }
        return TradeSession.ConfirmResult.DONE;
    }

    /** Ce qu'un joueur recevra : l'offre de l'autre, son reçu, et ce qu'il tient au curseur (qui revient chez lui). */
    private static List<ItemStack> incoming(List<ItemStack> offer, ItemStack receipt, @Nullable TradeMenu menu) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : offer) {
            out.add(stack.copy());
        }
        if (!receipt.isEmpty()) {
            out.add(receipt);
        }
        if (menu != null && !menu.getCarried().isEmpty()) {
            out.add(menu.getCarried().copy());
        }
        return out;
    }

    /** Vide le curseur de la fenêtre : l'appelant a déjà compté ce qu'il tenait. */
    private static ItemStack takeCarried(@Nullable TradeMenu menu) {
        if (menu == null || menu.getCarried().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack carried = menu.getCarried().copy();
        menu.setCarried(ItemStack.EMPTY);
        return carried;
    }

    static void broadcast(TradeSession session) {
        if (session.ended()) {
            return;
        }
        for (UUID player : List.of(session.first(), session.second())) {
            TradeMenu menu = session.menuOf(player);
            if (menu != null) {
                menu.broadcastChanges();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Annulation
    // ---------------------------------------------------------------------------------------------

    /**
     * Annule l'échange : chaque garde est fermée et rendue à son propriétaire (avec ce qu'il tenait au curseur),
     * les fenêtres encore ouvertes se ferment, chacun est prévenu. Sans effet sur une session déjà finie.
     */
    public static void cancel(TradeSession session, CancelReason reason) {
        if (session.ended()) {
            return;
        }
        session.end();
        SESSIONS.remove(session.first());
        SESSIONS.remove(session.second());
        MinecraftServer server = session.server();
        HeldGoods held = HeldGoods.get(server);
        for (UUID owner : List.of(session.first(), session.second())) {
            List<ItemStack> back = held.closeEscrow(owner);
            ItemStack carried = takeCarried(session.menuOf(owner));
            if (!carried.isEmpty()) {
                back.add(carried);
            }
            if (!back.isEmpty()) {
                CommerceDelivery.giveBack(server, owner, back);
            }
        }
        for (UUID player : List.of(session.first(), session.second())) {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online == null || online.hasDisconnected()) {
                continue;
            }
            TradeMenu menu = session.menuOf(player);
            // La fenêtre qui est en train de se fermer (celle qui a causé l'annulation) ne se referme pas.
            if (menu != null && online.containerMenu == menu && !menu.closing()) {
                online.closeContainer();
            }
            online.sendSystemMessage(Component.translatable(MESSAGE + "cancelled."
                    + reason.name().toLowerCase(java.util.Locale.ROOT), session.nameOf(session.other(player))));
        }
    }

    /** Annule la session de ce joueur s'il en a une. */
    private static void cancelFor(UUID player, CancelReason reason) {
        TradeSession session = SESSIONS.get(player);
        if (session != null) {
            cancel(session, reason);
        }
    }

    /** Toutes les 10 ticks : un échange dont les deux ne sont plus à portée s'annule. */
    public static void checkSessions(MinecraftServer server) {
        for (TradeSession session : new ArrayList<>(new java.util.LinkedHashSet<>(SESSIONS.values()))) {
            ServerPlayer first = server.getPlayerList().getPlayer(session.first());
            ServerPlayer second = server.getPlayerList().getPlayer(session.second());
            if (first == null || second == null) {
                cancel(session, CancelReason.OTHER_LEFT);
            } else if (!inReach(first, second)) {
                cancel(session, CancelReason.TOO_FAR);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Branchements (bus de Forge)
    // ---------------------------------------------------------------------------------------------

    /** Branché sur le bus de Forge : gestes, départs, contrôle périodique. */
    public static final class Events {

        /** Accroupi, main principale vide, clic droit sur un joueur : propose (ou accepte) un échange. */
        @SubscribeEvent
        public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
            if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND
                    || !(event.getEntity() instanceof ServerPlayer from) || !(event.getTarget() instanceof ServerPlayer to)
                    || !from.isShiftKeyDown() || !from.getMainHandItem().isEmpty()) {
                return;
            }
            RequestResult result = request(from, to);
            if (result != RequestResult.SENT && result != RequestResult.ACCEPTED) {
                from.displayClientMessage(Component.translatable(MESSAGE + "refused."
                        + result.name().toLowerCase(java.util.Locale.ROOT), to.getGameProfile().getName()), true);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }

        /** Avant la sauvegarde du joueur : tout ce qu'il avait posé rentre dans son inventaire, puis il est sauvé. */
        @SubscribeEvent
        public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            UUID player = event.getEntity().getUUID();
            cancelFor(player, CancelReason.LOGGED_OUT);
            REQUESTS.remove(player);
            for (Map<UUID, Long> mine : REQUESTS.values()) {
                mine.remove(player);
            }
        }

        /** Avant les lâchers : la garde rentre dans l'inventaire du mort et suit son sort. */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                cancelFor(player.getUUID(), CancelReason.DIED);
            }
        }

        @SubscribeEvent
        public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            cancelFor(event.getEntity().getUUID(), CancelReason.TOO_FAR);
        }

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % CHECK_PERIOD == 0) {
                checkSessions(event.getServer());
            }
        }

        /** Les joueurs sont encore là : tout leur est rendu avant la sauvegarde finale. */
        @SubscribeEvent
        public void onServerStopping(ServerStoppingEvent event) {
            for (TradeSession session : new ArrayList<>(new java.util.LinkedHashSet<>(SESSIONS.values()))) {
                cancel(session, CancelReason.SERVER_STOP);
            }
            SESSIONS.clear();
            REQUESTS.clear();
        }
    }
}
