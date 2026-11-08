package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.grid.client.GridClient;
import dev.oneforallsurvival.role.RoleGate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Client vers serveur : inscrire {@code name} comme abonné du bloc en {@code pos} (tout {@link SubscriberHolder} :
 * compteur, disjoncteur). Le serveur revalide tout : rôle, distance, bloc, texte. {@link Open} fait le chemin
 * inverse et ouvre l'écran chez l'ingénieur.
 */
public record SubscriberNamePacket(BlockPos pos, String name) {

    /** Borne de lecture du texte : bien au-delà d'un nom valide, mais un paquet géant est rejeté au décodage. */
    private static final int MAX_WIRE_LENGTH = 64;
    /** Distance au centre du bloc, au carré, comme un menu ordinaire (8 blocs). */
    private static final double MAX_DISTANCE_SQR = 64.0;

    public static final String MESSAGE = "message.oneforallsurvival.subscriber.";

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(name, MAX_WIRE_LENGTH);
    }

    public static SubscriberNamePacket decode(FriendlyByteBuf buf) {
        return new SubscriberNamePacket(buf.readBlockPos(), buf.readUtf(MAX_WIRE_LENGTH));
    }

    /** Ouvre l'écran d'inscription chez {@code player}, prérempli du nom actuel. Au rôle de l'appelant de décider. */
    public static void openScreen(ServerPlayer player, BlockPos pos, SubscriberHolder holder) {
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Open(pos, holder.subscriber()));
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(SubscriberNamePacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null || !RoleGate.requireEngineer(sender)) {
            return;
        }
        ServerLevel level = sender.serverLevel();
        BlockPos pos = packet.pos();
        if (!level.isLoaded(pos) || sender.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR
                || !(level.getBlockEntity(pos) instanceof SubscriberHolder holder)) {
            return;
        }
        if (!MeterLedger.isValidName(packet.name())) {
            sender.sendSystemMessage(Component.translatable(MESSAGE + "invalid", MeterLedger.MAX_NAME_LENGTH));
            return;
        }
        holder.setSubscriber(packet.name().trim());
        sender.sendSystemMessage(Component.translatable(MESSAGE + "set", holder.subscriber()));
    }

    /** Serveur vers client : ouvrir l'écran d'inscription du bloc en {@code pos}, prérempli de {@code current}. */
    public record Open(BlockPos pos, String current) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(current, MAX_WIRE_LENGTH);
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open(buf.readBlockPos(), buf.readUtf(MAX_WIRE_LENGTH));
        }

        public static void handle(Open packet, Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> GridClient.openSubscriberScreen(packet.pos(), packet.current()));
        }
    }
}
