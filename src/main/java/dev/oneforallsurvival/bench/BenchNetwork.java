package dev.oneforallsurvival.bench;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.bench.client.BenchClientState;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Le canal réseau de l'établi, protocole « 1 » : client et serveur doivent concorder. Chaque paquet du
 * client est traité sur le thread du serveur, et revalide tout : rôle, distance, menu ouvert. Un client
 * n'est jamais cru sur parole.
 */
public final class BenchNetwork {

    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(OneForAllSurvival.MODID, "bench");
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(CHANNEL_ID, () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    /** Numéros des messages sur le canal (premier octet de chaque paquet). */
    public static final int ID_ORDER = 0;
    public static final int ID_CANCEL = 1;
    public static final int ID_OPEN_STOCK = 2;
    public static final int ID_RECIPE_LIST = 3;
    public static final int ID_QUEUE = 4;

    /** Clé de traduction d'un refus : {@code message.oneforallsurvival.bench.refused.<résultat>}. */
    public static final String REFUSED_KEY = "message.oneforallsurvival.bench.refused.";

    /** Dernière génération du catalogue envoyée à chaque joueur connecté. */
    private static final Map<UUID, Integer> CATALOG_SENT = new ConcurrentHashMap<>();

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
        CHANNEL.messageBuilder(RecipeListPacket.class, ID_RECIPE_LIST, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RecipeListPacket::encode).decoder(RecipeListPacket::decode)
                .consumerMainThread(BenchNetwork::onRecipeList).add();
        CHANNEL.messageBuilder(QueuePacket.class, ID_QUEUE, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QueuePacket::encode).decoder(QueuePacket::decode)
                .consumerMainThread(BenchNetwork::onQueue).add();
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
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), QueuePacket.of(workshop));
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
        if (sender == null) {
            return;
        }
        EngineerBenchMenu menu = openMenu(sender);
        if (menu == null || !menu.pos().equals(packet.bench())) {
            return;
        }
        OrderResult result = BenchOrders.order(sender, packet.recipe(), packet.count(), packet.bench());
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
        // Le menu de stock arrive avec la Task 6 ; la demande est déjà revalidée.
    }

    // --- Serveur vers client : réception, côté client seulement ------------------------------

    private static void onRecipeList(RecipeListPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BenchClientState.acceptFragment(packet));
    }

    private static void onQueue(QueuePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BenchClientState.acceptQueue(packet));
    }
}
