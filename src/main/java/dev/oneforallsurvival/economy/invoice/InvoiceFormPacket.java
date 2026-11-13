package dev.oneforallsurvival.economy.invoice;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.client.InvoiceFormScreen;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Serveur vers client : ouvrir le formulaire d'une facture adressée à {@code target}. Le formulaire ne fait rien
 * par lui-même : il renvoie {@link InvoiceIssuePacket}, que le serveur revalide entièrement.
 */
public record InvoiceFormPacket(UUID target, String targetName) {

    /** Un nom de joueur fait 16 caractères au plus ; borne de lecture large. */
    private static final int MAX_NAME = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(target);
        buf.writeUtf(targetName, MAX_NAME);
    }

    public static InvoiceFormPacket decode(FriendlyByteBuf buf) {
        return new InvoiceFormPacket(buf.readUUID(), buf.readUtf(MAX_NAME));
    }

    /** Ouvre le formulaire chez {@code issuer} pour une facture à {@code debtor}. L'appelant a vérifié le geste. */
    public static void open(ServerPlayer issuer, ServerPlayer debtor) {
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> issuer),
                new InvoiceFormPacket(debtor.getUUID(), debtor.getGameProfile().getName()));
    }

    public static void handle(InvoiceFormPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> InvoiceFormScreen.open(packet.target(), packet.targetName()));
    }
}
