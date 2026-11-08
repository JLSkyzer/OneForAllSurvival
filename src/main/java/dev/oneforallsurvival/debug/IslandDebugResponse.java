package dev.oneforallsurvival.debug;

import dev.oneforallsurvival.client.debug.IslandDebugCache;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Serveur vers client : la réponse à {@link IslandDebugRequest}.
 *
 * @param target la cible retenue, {@code null} si aucune ou si elle a été ignorée (hors de portée, chunk non chargé)
 * @param info   les données, déjà filtrées selon le rôle du joueur
 */
public record IslandDebugResponse(@Nullable BlockPos target, IslandDebugInfo info) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeNullable(target, FriendlyByteBuf::writeBlockPos);
        info.encode(buf);
    }

    public static IslandDebugResponse decode(FriendlyByteBuf buf) {
        return new IslandDebugResponse(buf.readNullable(FriendlyByteBuf::readBlockPos), IslandDebugInfo.decode(buf));
    }

    public static void handle(IslandDebugResponse packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> IslandDebugCache.accept(packet));
    }
}
