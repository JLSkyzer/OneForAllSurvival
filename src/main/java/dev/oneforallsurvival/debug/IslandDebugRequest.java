package dev.oneforallsurvival.debug;

import dev.oneforallsurvival.bench.BenchNetwork;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client vers serveur : le F3 est ouvert, {@code target} est le bloc regardé ({@code null} si aucun). Le serveur
 * limite le débit, revalide la cible et répond par {@link IslandDebugResponse} ; au-delà du débit, rien, sans erreur.
 */
public record IslandDebugRequest(@Nullable BlockPos target) {

    private static final IslandDebugLimiter LIMITER = new IslandDebugLimiter();

    public void encode(FriendlyByteBuf buf) {
        buf.writeNullable(target, FriendlyByteBuf::writeBlockPos);
    }

    public static IslandDebugRequest decode(FriendlyByteBuf buf) {
        return new IslandDebugRequest(buf.readNullable(FriendlyByteBuf::readBlockPos));
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(IslandDebugRequest packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null || !LIMITER.allow(sender.getUUID(), sender.server.getTickCount())) {
            return;
        }
        BlockPos target = IslandDebugInfo.acceptedTarget(sender, packet.target());
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                new IslandDebugResponse(target, IslandDebugInfo.collect(sender, target)));
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LIMITER.forget(event.getEntity().getUUID());
    }
}
