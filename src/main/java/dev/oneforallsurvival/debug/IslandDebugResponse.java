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
 * @param serverTps les TPS du serveur ; en multijoueur, le client n'a aucun autre moyen de les connaître
 */
public record IslandDebugResponse(@Nullable BlockPos target, IslandDebugInfo info, float serverTps) {

    private static final float TICK_MILLIS = 1000f / 20f;

    /** Les TPS d'après le temps de tick moyen du serveur, plafonnés à 20 comme le rythme du jeu. */
    public static float tps(float averageTickMillis) {
        return 1000f / Math.max(TICK_MILLIS, averageTickMillis);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNullable(target, FriendlyByteBuf::writeBlockPos);
        info.encode(buf);
        buf.writeFloat(serverTps);
    }

    public static IslandDebugResponse decode(FriendlyByteBuf buf) {
        return new IslandDebugResponse(buf.readNullable(FriendlyByteBuf::readBlockPos), IslandDebugInfo.decode(buf), buf.readFloat());
    }

    public static void handle(IslandDebugResponse packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> IslandDebugCache.accept(packet));
    }
}
