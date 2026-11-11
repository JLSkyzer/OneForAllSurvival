package dev.oneforallsurvival.bench;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.bench.client.BenchClientState;
import dev.oneforallsurvival.debug.IslandDebugRequest;
import dev.oneforallsurvival.debug.IslandDebugResponse;
import dev.oneforallsurvival.explore.ReceiverFrequencyPacket;
import dev.oneforallsurvival.grid.SubscriberNamePacket;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

/**
 * Le canal réseau du mod (né avec l'établi), protocole « 3 » : client et serveur doivent concorder. Chaque paquet du
 * client est traité sur le thread du serveur, et revalide tout : rôle, distance, menu ouvert. Un client
 * n'est jamais cru sur parole.
 */
public final class BenchNetwork {

    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(OneForAllSurvival.MODID, "bench");
    /** « 3 » depuis les données Lost Island du F3 : un serveur plus ancien ne connaît pas la requête. */
    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(CHANNEL_ID, () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    /** Numéros des messages sur le canal (premier octet de chaque paquet). */
    public static final int ID_ORDER = 0;
    public static final int ID_CANCEL = 1;
    public static final int ID_OPEN_STOCK = 2;
    public static final int ID_RECIPE_LIST = 3;
    public static final int ID_QUEUE = 4;
    public static final int ID_OPEN_BENCH = 5;
    /** Gestion du réseau : inscrire l'abonné d'un compteur ou d'un disjoncteur, et en ouvrir l'écran. */
    public static final int ID_SUBSCRIBER_NAME = 6;
    public static final int ID_SUBSCRIBER_SCREEN = 7;
    /** Le F3 : demande des données Lost Island, et réponse filtrée selon le rôle. */
    public static final int ID_ISLAND_DEBUG_REQUEST = 8;
    public static final int ID_ISLAND_DEBUG_RESPONSE = 9;
    /** Le récepteur de signaux : régler sa fréquence. */
    public static final int ID_RECEIVER_FREQUENCY = 10;

    /** Clé de traduction d'un refus : {@code message.oneforallsurvival.bench.refused.<résultat>}. */
    public static final String REFUSED_KEY = "message.oneforallsurvival.bench.refused.";

    /** Dernière génération du catalogue envoyée à chaque joueur connecté. */
    private static final Map<UUID, Integer> CATALOG_SENT = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LogUtils.getLogger();

    private BenchNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(OrderPacket.class, ID_ORDER, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OrderPacket::encode).decoder(OrderPacket::decode)
                .consumerMainThread(BenchNetwork::onOrder).add();
        CHANNEL.messageBuilder(CancelPacket.class, ID_CANCEL, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CancelPacket::encode).decoder(CancelPacket::decode)
                .consumerMainThread(BenchNetwork::onCancel).add();
        CHANNEL.messageBuilder(OpenStockPacket.class, ID_OPEN_STOCK, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenStockPacket::encode).decoder(OpenStockPacket::decode)
                .consumerMainThread(BenchNetwork::onOpenStock).add();
        CHANNEL.messageBuilder(OpenBenchPacket.class, ID_OPEN_BENCH, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenBenchPacket::encode).decoder(OpenBenchPacket::decode)
                .consumerMainThread(BenchNetwork::onOpenBench).add();
        CHANNEL.messageBuilder(RecipeListPacket.class, ID_RECIPE_LIST, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RecipeListPacket::encode).decoder(RecipeListPacket::decode)
                .consumerMainThread(BenchNetwork::onRecipeList).add();
        CHANNEL.messageBuilder(QueuePacket.class, ID_QUEUE, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QueuePacket::encode).decoder(QueuePacket::decode)
                .consumerMainThread(BenchNetwork::onQueue).add();
        CHANNEL.messageBuilder(SubscriberNamePacket.class, ID_SUBSCRIBER_NAME, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubscriberNamePacket::encode).decoder(SubscriberNamePacket::decode)
                .consumerMainThread(SubscriberNamePacket::handle).add();
        CHANNEL.messageBuilder(SubscriberNamePacket.Open.class, ID_SUBSCRIBER_SCREEN, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SubscriberNamePacket.Open::encode).decoder(SubscriberNamePacket.Open::decode)
                .consumerMainThread(SubscriberNamePacket.Open::handle).add();
        CHANNEL.messageBuilder(IslandDebugRequest.class, ID_ISLAND_DEBUG_REQUEST, NetworkDirection.PLAY_TO_SERVER)
                .encoder(IslandDebugRequest::encode).decoder(IslandDebugRequest::decode)
                .consumerMainThread(IslandDebugRequest::handle).add();
        CHANNEL.messageBuilder(IslandDebugResponse.class, ID_ISLAND_DEBUG_RESPONSE, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(IslandDebugResponse::encode).decoder(IslandDebugResponse::decode)
                .consumerMainThread(IslandDebugResponse::handle).add();
        CHANNEL.messageBuilder(ReceiverFrequencyPacket.class, ID_RECEIVER_FREQUENCY, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ReceiverFrequencyPacket::encode).decoder(ReceiverFrequencyPacket::decode)
                .consumerMainThread(ReceiverFrequencyPacket::handle).add();
    }

    // --- Serveur vers client -----------------------------------------------------------------

    /**
     * Envoie le catalogue (en fragments) si le joueur n'a pas celui de la génération courante. Le serveur
     * retient ce qu'il a envoyé ; un client qui se reconnecte le reçoit de nouveau.
     */
    public static void sendCatalogIfNeeded(ServerPlayer player) {
        int generation = BenchCatalog.generation();
        Integer sent = CATALOG_SENT.get(player.getUUID());
        if (sent != null && sent == generation) {
            return;
        }
        for (RecipeListPacket fragment : RecipeListPacket.fragments(generation, BenchCatalog.current().recipes())) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), fragment);
        }
        CATALOG_SENT.put(player.getUUID(), generation);
    }

    public static void sendQueue(ServerPlayer player) {
        PlayerWorkshop workshop = WorkshopSavedData.get(player.server).of(player.getUUID());
        LOGGER.debug("File envoyee a {} : {} entree(s), {} objet(s) en stock", player.getGameProfile().getName(),
                workshop.queue().size(), workshop.stock().size());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), QueuePacket.of(workshop));
    }

    /**
     * Synchronisation des données : le catalogue part tout de suite, sans attendre la première ouverture d'un
     * établi (les compléments client, JEI en tête, en ont besoin avant que le joueur n'approche d'un établi).
     * Forge publie cet événement à l'entrée d'un joueur ({@code getPlayer()} le désigne) et à chaque
     * {@code /reload} ({@code getPlayer()} est nul : tous les joueurs connectés). Lu dans le bytecode : au
     * rechargement, {@code updateRegistryTags} (qui installe le nouveau catalogue) précède
     * {@code PlayerList.reloadResources}, qui publie l'événement avant d'envoyer recettes et étiquettes.
     */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer target = event.getPlayer();
        if (target != null) {
            sendCatalogIfNeeded(target);
            return;
        }
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            sendCatalogIfNeeded(player);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CATALOG_SENT.remove(event.getEntity().getUUID());
    }

    // --- Client vers serveur : tout est revalidé ---------------------------------------------

    /** Le menu de l'établi que le joueur a ouvert ; sans lui, un paquet du client est ignoré. */
    private static EngineerBenchMenu openMenu(ServerPlayer player) {
        return player.containerMenu instanceof EngineerBenchMenu menu && menu.stillValid(player) ? menu : null;
    }

    private static void onOrder(OrderPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        LOGGER.debug("Commande recue de {} : {} x{} a {}", sender == null ? "?" : sender.getGameProfile().getName(),
                packet.recipe(), packet.count(), packet.bench());
        if (sender == null) {
            return;
        }
        EngineerBenchMenu menu = openMenu(sender);
        if (menu == null || !menu.pos().equals(packet.bench())) {
            LOGGER.debug("Commande de {} ignoree : menu ouvert {}, etabli demande {}", sender.getGameProfile().getName(),
                    sender.containerMenu.getClass().getSimpleName(), packet.bench());
            return;
        }
        OrderResult result = BenchOrders.order(sender, packet.recipe(), packet.count(), packet.bench());
        LOGGER.debug("Commande de {} : {} x{} -> {}", sender.getGameProfile().getName(), packet.recipe(), packet.count(),
                result);
        if (result != OrderResult.OK) {
            sender.sendSystemMessage(Component.translatable(REFUSED_KEY + result.name().toLowerCase(Locale.ROOT)));
        }
    }

    private static void onCancel(CancelPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null || openMenu(sender) == null) {
            return;
        }
        BenchOrders.cancel(sender, packet.entry());
    }

    private static void onOpenStock(OpenStockPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        EngineerBenchMenu menu = openMenu(sender);
        if (menu == null || !menu.pos().equals(packet.bench())) {
            return;
        }
        BenchStockMenu.open(sender, menu.pos());
    }

    /** Retour du stock à l'établi : seulement depuis un stock ouvert, valide, ouvert depuis cet établi. */
    private static void onOpenBench(OpenBenchPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null || !(sender.containerMenu instanceof BenchStockMenu menu) || !menu.stillValid(sender)
                || !menu.pos().equals(packet.bench())) {
            return;
        }
        EngineerBenchBlock.open(sender, menu.pos());
    }

    // --- Serveur vers client : réception, côté client seulement ------------------------------

    private static void onRecipeList(RecipeListPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BenchClientState.acceptFragment(packet));
    }

    private static void onQueue(QueuePacket packet, Supplier<NetworkEvent.Context> context) {
        LOGGER.debug("File recue : {} entree(s)", packet.rows().size());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BenchClientState.acceptQueue(packet));
    }
}
