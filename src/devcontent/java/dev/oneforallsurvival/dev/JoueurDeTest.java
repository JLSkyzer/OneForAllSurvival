package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.QueuePacket;
import dev.oneforallsurvival.bench.RecipeListPacket;
import dev.oneforallsurvival.debug.IslandDebugResponse;
import dev.oneforallsurvival.grid.SubscriberNamePacket;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;

/**
 * Un vrai {@code ServerPlayer} sur une connexion Netty embarquée : le serveur le traite en joueur distant.
 * Ce qu'il envoie ressort de l'{@link EmbeddedChannel} en objets {@code Packet} ; ce que le client enverrait
 * rentre par {@link #envoyer}. Sur le thread du serveur seulement. Contenu de développement, jamais empaqueté.
 */
public final class JoueurDeTest {

    /** La marque de Forge qu'un client moddé porte ; certains contrôles de Forge la lisent sur le canal. */
    private static final AttributeKey<String> VERSION_FML = AttributeKey.valueOf("fml:netversion");

    private final EmbeddedChannel canal;
    private final ServerPlayer joueur;
    private final List<RecipeListPacket> listes = new ArrayList<>();
    private final List<QueuePacket> files = new ArrayList<>();
    /** Les ouvertures de l'écran d'inscription d'un abonné reçues. */
    private final List<SubscriberNamePacket.Open> ecransAbonne = new ArrayList<>();
    /** Les réponses du F3 reçues, décodées depuis les octets réellement envoyés. */
    private final List<IslandDebugResponse> reponsesDebug = new ArrayList<>();
    /** Les clés des messages (chat ou barre d'action) reçus, dans l'ordre ; un texte brut est gardé tel quel. */
    private final List<String> messages = new ArrayList<>();
    /** Les hauteurs des sons joués au joueur (paquets de son), dans l'ordre. */
    private final List<Float> sons = new ArrayList<>();
    /** Les paquets de synchronisation de menu (contenu, case, donnée), dans l'ordre d'envoi. */
    private final List<Packet<?>> menus = new ArrayList<>();
    /** Les fragments du catalogue que l'entrée en jeu elle-même a envoyés, avant qu'on ne les oublie. */
    private final List<RecipeListPacket> listesALaConnexion = new ArrayList<>();

    private JoueurDeTest(EmbeddedChannel canal, ServerPlayer joueur) {
        this.canal = canal;
        this.joueur = joueur;
    }

    /** Fait entrer le joueur ; son identifiant découle du nom, comme sur un serveur hors ligne. */
    public static JoueurDeTest rejoindre(ServerLevel level, String nom, Vec3 position) {
        Connection connexion = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel canal = new EmbeddedChannel(connexion);
        canal.attr(VERSION_FML).set("FML3");
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nom).getBytes(StandardCharsets.UTF_8));
        ServerPlayer joueur = new ServerPlayer(level.getServer(), level, new GameProfile(uuid, nom));
        joueur.moveTo(position.x, position.y, position.z, 0f, 0f);
        level.getServer().getPlayerList().placeNewPlayer(connexion, joueur);
        // L'UUID découle du nom : placeNewPlayer relit le playerdata d'un joueur d'essai du même nom. On repart à vide.
        joueur.getInventory().clearContent();
        JoueurDeTest simule = new JoueurDeTest(canal, joueur);
        simule.lire();
        // L'entrée en jeu a envoyé son lot de paquets : on ne garde que ce qui suit.
        simule.listesALaConnexion.addAll(simule.listes);
        simule.listes.clear();
        simule.files.clear();
        simule.ecransAbonne.clear();
        simule.menus.clear();
        simule.messages.clear();
        return simule;
    }

    /** Les fragments du catalogue reçus pendant l'entrée en jeu (la connexion réelle, pas un rejeu). */
    public List<RecipeListPacket> listesALaConnexion() {
        return List.copyOf(listesALaConnexion);
    }

    public ServerPlayer joueur() {
        return joueur;
    }

    public UUID uuid() {
        return joueur.getUUID();
    }

    /** Lit ce que le serveur a envoyé depuis le dernier appel, comme le client le lirait. */
    public void lire() {
        Object message;
        while ((message = canal.readOutbound()) != null) {
            lire(message);
        }
    }

    private void lire(Object message) {
        if (message instanceof BundlePacket<?> groupe) {
            for (Packet<?> paquet : groupe.subPackets()) {
                lire(paquet);
            }
        } else if (message instanceof ClientboundSystemChatPacket chat) {
            messages.add(chat.content().getContents() instanceof TranslatableContents t ? t.getKey()
                    : chat.content().getString());
        } else if (message instanceof ClientboundSoundPacket son) {
            sons.add(son.getPitch());
        } else if (message instanceof ClientboundContainerSetContentPacket
                || message instanceof ClientboundContainerSetSlotPacket
                || message instanceof ClientboundContainerSetDataPacket) {
            menus.add((Packet<?>) message);
        } else if (message instanceof ClientboundCustomPayloadPacket charge
                && BenchNetwork.CHANNEL_ID.equals(charge.getIdentifier())) {
            FriendlyByteBuf donnees = charge.getData();
            try {
                int discriminant = donnees.readUnsignedByte();
                if (discriminant == BenchNetwork.ID_RECIPE_LIST) {
                    listes.add(RecipeListPacket.decode(donnees));
                } else if (discriminant == BenchNetwork.ID_QUEUE) {
                    files.add(QueuePacket.decode(donnees));
                } else if (discriminant == BenchNetwork.ID_SUBSCRIBER_SCREEN) {
                    ecransAbonne.add(SubscriberNamePacket.Open.decode(donnees));
                } else if (discriminant == BenchNetwork.ID_ISLAND_DEBUG_RESPONSE) {
                    reponsesDebug.add(IslandDebugResponse.decode(donnees));
                }
            } finally {
                donnees.release();
            }
        }
    }

    /** Les hauteurs des sons reçus depuis le dernier appel, retirées. */
    public List<Float> sonsRecus() {
        lire();
        List<Float> out = new ArrayList<>(sons);
        sons.clear();
        return out;
    }

    /** Les clés des messages reçus depuis le dernier appel, retirées. */
    public List<String> messagesRecus() {
        lire();
        List<String> out = new ArrayList<>(messages);
        messages.clear();
        return out;
    }

    /** Les fragments du catalogue reçus depuis le dernier appel, retirés. */
    public List<RecipeListPacket> listesRecues() {
        lire();
        List<RecipeListPacket> out = new ArrayList<>(listes);
        listes.clear();
        return out;
    }

    /** Les files reçues depuis le dernier appel, retirées. */
    public List<QueuePacket> filesRecues() {
        lire();
        List<QueuePacket> out = new ArrayList<>(files);
        files.clear();
        return out;
    }

    /** Les ouvertures de l'écran d'inscription d'un abonné reçues depuis le dernier appel, retirées. */
    public List<SubscriberNamePacket.Open> ecransAbonneRecus() {
        lire();
        List<SubscriberNamePacket.Open> out = new ArrayList<>(ecransAbonne);
        ecransAbonne.clear();
        return out;
    }

    /** Les réponses du F3 reçues depuis le dernier appel, retirées. */
    public List<IslandDebugResponse> reponsesDebugRecues() {
        lire();
        List<IslandDebugResponse> out = new ArrayList<>(reponsesDebug);
        reponsesDebug.clear();
        return out;
    }

    /**
     * Rejoue sur {@code client} (un menu du même type, d'un autre inventaire) ce que le serveur a envoyé pour
     * lui depuis le dernier appel : c'est ce que fait le client vanille à la réception.
     */
    public void synchroniser(AbstractContainerMenu client) {
        lire();
        for (Packet<?> paquet : menus) {
            if (paquet instanceof ClientboundContainerSetContentPacket p && p.getContainerId() == client.containerId) {
                client.initializeContents(p.getStateId(), p.getItems(), p.getCarriedItem());
            } else if (paquet instanceof ClientboundContainerSetSlotPacket p
                    && p.getContainerId() == client.containerId) {
                client.setItem(p.getSlot(), p.getStateId(), p.getItem());
            } else if (paquet instanceof ClientboundContainerSetDataPacket p
                    && p.getContainerId() == client.containerId) {
                client.setData(p.getId(), p.getValue());
            }
        }
        menus.clear();
    }

    /** Les paquets de menu (contenu, case, donnée) reçus depuis le dernier appel, retirés. */
    public List<Packet<?>> menusRecus() {
        lire();
        List<Packet<?>> out = new ArrayList<>(menus);
        menus.clear();
        return out;
    }

    /** Envoie au serveur un paquet du mod, comme le ferait le client. */
    public void envoyer(Object message) {
        canal.writeInbound(BenchNetwork.CHANNEL.toVanillaPacket(message, NetworkDirection.PLAY_TO_SERVER));
    }

    public boolean connecte() {
        return canal.isOpen();
    }

    /** Quitte comme un client qui se déconnecte : Minecraft retire le joueur, Forge publie sa sortie. */
    public void quitter() {
        joueur.connection.disconnect(Component.literal("fin du joueur d'essai"));
        if (joueur.getServer().getPlayerList().getPlayer(joueur.getUUID()) == joueur) {
            joueur.getServer().getPlayerList().remove(joueur);
        }
        canal.finishAndReleaseAll();
    }
}
