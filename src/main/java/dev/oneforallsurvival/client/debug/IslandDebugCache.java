package dev.oneforallsurvival.client.debug;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.debug.IslandDebugInfo;
import dev.oneforallsurvival.debug.IslandDebugRequest;
import dev.oneforallsurvival.debug.IslandDebugResponse;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Côté client : demande les données Lost Island tant que le F3 est ouvert ({@link DebugRequestPacing}) et garde la
 * dernière réponse avec son âge. Rien de tout cela n'existe sur un serveur dédié. En mode réduit, la réponse gardée est
 * oubliée : elle situerait le joueur.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class IslandDebugCache {

    private static final DebugRequestPacing PACING = new DebugRequestPacing();

    @Nullable
    private static IslandDebugResponse last;
    private static long receivedAt;

    private IslandDebugCache() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        boolean reduced = minecraft.showOnlyReducedInfo();
        if (reduced) {
            last = null;
        }
        BlockPos target = minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? hit.getBlockPos() : null;
        if (PACING.due(minecraft.options.renderDebug, reduced, Util.getMillis(), target)) {
            BenchNetwork.CHANNEL.sendToServer(new IslandDebugRequest(target));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        last = null;
    }

    /** À la réception d'une réponse, sur le thread du client. */
    public static void accept(IslandDebugResponse response) {
        last = response;
        receivedAt = Util.getMillis();
    }

    /** Les dernières données reçues, ou {@code null} s'il n'y en a pas. */
    @Nullable
    public static IslandDebugInfo info() {
        return last == null ? null : last.info();
    }

    /** La cible à laquelle ces données répondent ; à comparer au bloc regardé, la réponse pouvant avoir du retard. */
    @Nullable
    public static BlockPos target() {
        return last == null ? null : last.target();
    }

    /** Les TPS du serveur à la dernière réponse, ou {@code null} s'il n'y en a pas. */
    @Nullable
    public static Float serverTps() {
        return last == null ? null : last.serverTps();
    }

    /** L'âge des dernières données en millisecondes, {@link Long#MAX_VALUE} s'il n'y en a pas. */
    public static long ageMillis() {
        return last == null ? Long.MAX_VALUE : Util.getMillis() - receivedAt;
    }
}
