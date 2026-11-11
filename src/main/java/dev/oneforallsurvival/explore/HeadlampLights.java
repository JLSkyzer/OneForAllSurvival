package dev.oneforallsurvival.explore;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * La lumière des lampes frontales : un bloc {@code minecraft:light} invisible, posé côté serveur dans le bloc des yeux
 * du porteur et déplacé avec lui. Elle ne pose que dans l'air ou dans l'eau source (bloc imbibé), n'écrase rien, ne
 * retire que les blocs que {@link HeadlampLightData} lui attribue, et n'en laisse aucun derrière elle : déconnexion,
 * mort, changement de dimension, lampe retirée ou vide, arrêt du serveur. Après un plantage, le chargement d'un chunk
 * retire les blocs notés que plus aucune lampe active ne tient.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID)
public final class HeadlampLights {

    /** Ce qu'une lampe allumée tient : la dimension et le bloc des yeux, et si un bloc de lumière y est posé. */
    private static final class Active {
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;
        private boolean holding;

        private Active(ResourceKey<Level> dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }
    }

    /** Les lampes allumées, par joueur. Thread du serveur seulement. */
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private HeadlampLights() {}

    /**
     * Un tick de lampe pour ce joueur, à la fin de son tick serveur. Allumée si portée, chargée, joueur vivant et pas
     * spectateur ; elle dépense alors {@code headlampEnergy}. Sinon tout ce qu'elle tenait est rendu.
     */
    public static void update(ServerPlayer player) {
        ItemStack lamp = HeadlampItem.worn(player);
        if (lamp.isEmpty() || player.isSpectator() || !player.isAlive()
                || !HeadlampItem.spend(lamp, ExploreConfig.headlampEnergy())) {
            clear(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        Active active = ACTIVE.get(player.getUUID());
        if (active != null && (!active.dimension.equals(level.dimension()) || !active.pos.equals(eye))) {
            release(level.getServer(), active);
            active = null;
        }
        if (active == null) {
            active = new Active(level.dimension(), eye);
            ACTIVE.put(player.getUUID(), active);
        }
        if (active.holding && !level.getBlockState(active.pos).is(Blocks.LIGHT)) {
            // Le bloc a été remplacé par autre chose : plus rien à retirer, la note s'efface.
            HeadlampLightData.get(level).released(active.pos);
            active.holding = false;
        }
        if (!active.holding) {
            active.holding = acquire(level, active.pos);
        }
    }

    /** Rend tout ce que la lampe de ce joueur tenait. Sans effet si elle ne tenait rien. */
    public static void clear(ServerPlayer player) {
        Active active = ACTIVE.remove(player.getUUID());
        if (active != null) {
            release(player.getServer(), active);
        }
    }

    /** Pose, ou partage, le bloc de lumière en {@code pos} ; faux si le bloc n'est ni de l'air, ni de l'eau source, ni le nôtre. */
    private static boolean acquire(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        HeadlampLightData data = HeadlampLightData.get(level);
        boolean water = state.is(Blocks.WATER) && state.getValue(LiquidBlock.LEVEL) == 0;
        if (state.isAir() || water) {
            level.setBlock(pos, Blocks.LIGHT.defaultBlockState()
                    .setValue(LightBlock.LEVEL, ExploreConfig.headlampLight())
                    .setValue(LightBlock.WATERLOGGED, water), Block.UPDATE_ALL);
        } else if (!(state.is(Blocks.LIGHT) && data.isOurs(pos))) {
            return false;
        }
        data.placed(pos);
        return true;
    }

    /**
     * Une lampe quitte son bloc. Le bloc de lumière n'est retiré que si c'était la dernière lampe et s'il est
     * toujours un bloc de lumière. Un chunk déchargé n'est pas touché : la note reste, le balayage au chargement s'en
     * charge.
     */
    private static void release(MinecraftServer server, Active active) {
        if (!active.holding) {
            return;
        }
        ServerLevel level = server.getLevel(active.dimension);
        if (level == null || !level.hasChunkAt(active.pos)) {
            return;
        }
        boolean last = HeadlampLightData.get(level).released(active.pos);
        BlockState state = level.getBlockState(active.pos);
        if (last && state.is(Blocks.LIGHT)) {
            level.setBlock(active.pos, restored(state), Block.UPDATE_ALL);
        }
    }

    private static BlockState restored(BlockState light) {
        return light.getValue(LightBlock.WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /** Une lampe allumée tient ce bloc, dans cette dimension. */
    private static boolean held(ResourceKey<Level> dimension, BlockPos pos) {
        for (Active active : ACTIVE.values()) {
            if (active.holding && active.dimension.equals(dimension) && active.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Balaie un chunk qui vient de charger : tout bloc de lumière noté ici que plus aucune lampe active ne tient est
     * un reste de plantage. Il est retiré s'il est encore un bloc de lumière, et sa note est effacée.
     */
    public static void sweep(ServerLevel level, ChunkAccess chunk) {
        HeadlampLightData data = HeadlampLightData.get(level);
        if (data.size() == 0) {
            return;
        }
        for (BlockPos pos : data.notedIn(chunk.getPos())) {
            if (held(level.dimension(), pos)) {
                continue;
            }
            BlockState state = chunk.getBlockState(pos);
            if (state.is(Blocks.LIGHT)) {
                chunk.setBlockState(pos, restored(state), false);
            }
            data.forget(pos);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer() && event.player instanceof ServerPlayer player) {
            update(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            sweep(level, event.getChunk());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            clear(player);
        }
        for (Active active : new ArrayList<>(ACTIVE.values())) {
            release(server, active);
        }
        ACTIVE.clear();
    }
}
