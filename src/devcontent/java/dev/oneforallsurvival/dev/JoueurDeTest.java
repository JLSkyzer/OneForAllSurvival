package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.QueuePacket;
import dev.oneforallsurvival.bench.RecipeListPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        JoueurDeTest simule = new JoueurDeTest(canal, joueur);
        simule.lire();
        // L'entrée en jeu a envoyé son lot de paquets : on ne garde que ce qui suit.
        simule.listes.clear();
        simule.files.clear();
        return simule;
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
        } else if (message instanceof ClientboundCustomPayloadPacket charge
                && BenchNetwork.CHANNEL_ID.equals(charge.getIdentifier())) {
            FriendlyByteBuf donnees = charge.getData();
            try {
                int discriminant = donnees.readUnsignedByte();
                if (discriminant == BenchNetwork.ID_RECIPE_LIST) {
                    listes.add(RecipeListPacket.decode(donnees));
                } else if (discriminant == BenchNetwork.ID_QUEUE) {
                    files.add(QueuePacket.decode(donnees));
                }
            } finally {
                donnees.release();
            }
        }
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
