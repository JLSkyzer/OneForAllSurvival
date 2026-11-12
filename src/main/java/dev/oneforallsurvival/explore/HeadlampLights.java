package dev.oneforallsurvival.explore;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.machine.ItemBattery;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * La lumière des lampes frontales : un bloc {@code minecraft:light} invisible, posé côté serveur dans le bloc des yeux
 * du porteur et déplacé avec lui. Elle ne pose que dans l'air ou dans l'eau source (bloc imbibé), n'écrase rien, ne
 * retire que les blocs que {@link HeadlampLightData} lui attribue et qui ont encore le niveau de la lampe, et n'en
 * laisse aucun derrière elle : déconnexion, mort, changement de dimension, lampe retirée ou vide, arrêt du serveur.
 * Les notes sont écrites dans les données de chaque chunk à sa sauvegarde ; après un plantage, le chargement du chunk
 * relève les blocs notés, et la fin du tick de serveur suivant retire ceux que plus aucune lampe active ne tient (le
 * chunk n'est pas encore inscrit au monde pendant {@code ChunkDataEvent.Load}).
 *
 * <p>L'énergie se dépense par blocs de {@value ItemBattery#PERIOD} ticks ({@link ItemBattery.Prepaid}), pour que la
 * pile ne change qu'une fois par seconde (chaque changement est envoyé à tous ceux qui voient le porteur).
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID)
public final class HeadlampLights {

    /** Combien de ticks de serveur une position à nettoyer attend son chunk avant qu'on y renonce (la note reste). */
    private static final int SWEEP_PATIENCE = 200;

    /** Ce qu'une lampe allumée tient : la dimension et le bloc des yeux, si un bloc de lumière y est posé, les ticks déjà payés. */
    private static final class Active {
        private volatile ResourceKey<Level> dimension;
        private volatile BlockPos pos;
        private volatile boolean holding;
        private final ItemBattery.Prepaid prepaid;

        private Active(ResourceKey<Level> dimension, BlockPos pos, ItemBattery.Prepaid prepaid) {
            this.dimension = dimension;
            this.pos = pos;
            this.prepaid = prepaid;
        }
    }

    /** Les positions à nettoyer, par dimension, avec l'âge en ticks de serveur. Remplie par le chargement des chunks. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> TO_SWEEP = new ConcurrentHashMap<>();

    /** Les lampes allumées, par joueur. */
    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private HeadlampLights() {}

    /**
     * Un tick de lampe pour ce joueur, à la fin de son tick serveur. Allumée si portée, chargée, joueur vivant et pas
     * spectateur. Quand les ticks payés sont écoulés, elle paie le bloc suivant ; si la batterie ne le couvre pas,
     * elle s'éteint et tout ce qu'elle tenait est rendu.
     */
    public static void update(ServerPlayer player) {
        ItemStack lamp = HeadlampItem.worn(player);
        if (lamp.isEmpty() || player.isSpectator() || !player.isAlive()) {
            clear(player);
            return;
        }
        Active active = ACTIVE.get(player.getUUID());
        ItemBattery.Prepaid prepaid = active == null ? new ItemBattery.Prepaid() : active.prepaid;
        if (!prepaid.pay(lamp, ExploreConfig.headlampEnergy(), ExploreConfig.headlampCapacity())) {
            clear(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        if (active == null) {
            active = new Active(level.dimension(), eye, prepaid);
            ACTIVE.put(player.getUUID(), active);
        } else if (!active.dimension.equals(level.dimension()) || !active.pos.equals(eye)) {
            release(level.getServer(), active);
            active.dimension = level.dimension();
            active.pos = eye;
            active.holding = false;
        }
        if (active.holding && !isLamp(level.getBlockState(active.pos))) {
            // Le bloc a été remplacé (par autre chose, ou par la lumière d'un autre niveau, celle de l'équipe) :
            // plus rien à retirer, la note s'efface.
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

    /** Un bloc de lumière du niveau que pose la lampe. */
    private static boolean isLamp(BlockState state) {
        return state.is(Blocks.LIGHT) && state.getValue(LightBlock.LEVEL) == ExploreConfig.headlampLight();
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
            // Le moteur de lumière suit le changement de bloc quel que soit l'indicateur (LevelChunk.setBlockState).
            // Une pose refusée n'est pas notée : la note ne dit que ce qui est vraiment là.
            if (!level.setBlock(pos, Blocks.LIGHT.defaultBlockState()
                    .setValue(LightBlock.LEVEL, ExploreConfig.headlampLight())
                    .setValue(LightBlock.WATERLOGGED, water), Block.UPDATE_CLIENTS)) {
                return false;
            }
        } else if (!(isLamp(state) && data.isOurs(pos))) {
            return false;
        }
        data.placed(pos);
        return true;
    }

    /**
     * Une lampe quitte son bloc. Le bloc de lumière n'est retiré que si c'était la dernière lampe et s'il est
     * toujours un bloc de lumière de notre niveau. Un chunk déchargé n'est pas touché : la note reste, le balayage au
     * chargement s'en charge.
     */
    private static void release(MinecraftServer server, Active active) {
        if (!active.holding) {
            return;
        }
        ServerLevel level = server.getLevel(active.dimension);
        BlockPos pos = active.pos;
        if (level == null || !level.hasChunkAt(pos)) {
            return;
        }
        boolean last = HeadlampLightData.get(level).released(pos);
        BlockState state = level.getBlockState(pos);
        if (last && isLamp(state)) {
            // L'eau rendue garde la mise à jour complète : le fluide doit se remettre à couler.
            level.setBlock(pos, restored(state),
                    state.getValue(LightBlock.WATERLOGGED) ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
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
     * Relève les blocs de lumière à nettoyer dans un chunk qui vient de charger : ceux notés en mémoire et ceux notés
     * dans les données du chunk qu'on vient de lire ({@code fromDisk}). Rien n'est touché ici : le chunk n'est pas
     * encore inscrit au monde, {@link #sweepPending} agit à la fin d'un tick de serveur.
     */
    public static void sweep(ServerLevel level, ChunkAccess chunk, List<HeadlampLightData.Note> fromDisk) {
        HeadlampLightData data = HeadlampLightData.get(level);
        Set<BlockPos> noted = new LinkedHashSet<>();
        data.notedIn(chunk.getPos()).forEach(n -> noted.add(n.pos()));
        for (HeadlampLightData.Note note : fromDisk) {
            BlockPos pos = note.pos();
            if ((pos.getX() >> 4) == chunk.getPos().x && (pos.getZ() >> 4) == chunk.getPos().z) {
                noted.add(pos);
            }
        }
        if (noted.isEmpty()) {
            return;
        }
        Map<BlockPos, Integer> pending = TO_SWEEP.computeIfAbsent(level.dimension(), d -> new ConcurrentHashMap<>());
        for (BlockPos pos : noted) {
            pending.put(pos.immutable(), 0);
        }
    }

    /**
     * Nettoie, à la fin d'un tick de serveur, les positions relevées au chargement : un bloc de lumière de notre niveau
     * que plus aucune lampe active ne tient est rendu (air, ou eau source s'il était imbibé), mise à jour complète. Une
     * position dont le chunk n'est pas encore au monde attend ; passé {@value #SWEEP_PATIENCE} ticks, on y renonce et
     * la note reste, pour le prochain chargement.
     */
    public static void sweepPending(ServerLevel level) {
        Map<BlockPos, Integer> pending = TO_SWEEP.get(level.dimension());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        HeadlampLightData data = HeadlampLightData.get(level);
        for (Map.Entry<BlockPos, Integer> entry : new ArrayList<>(pending.entrySet())) {
            BlockPos pos = entry.getKey();
            if (!level.hasChunkAt(pos)) {
                if (entry.getValue() >= SWEEP_PATIENCE) {
                    pending.remove(pos);
                } else {
                    pending.put(pos, entry.getValue() + 1);
                }
                continue;
            }
            pending.remove(pos);
            if (held(level.dimension(), pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (isLamp(state)) {
                level.setBlock(pos, restored(state), Block.UPDATE_ALL);
            }
            data.forget(pos);
        }
    }

    /** Rend tout, pour tous les joueurs puis pour toute lampe restante. */
    public static void releaseAll(MinecraftServer server) {
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            clear(player);
        }
        for (Active active : new ArrayList<>(ACTIVE.values())) {
            release(server, active);
        }
        ACTIVE.clear();
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

    /** Les notes du chunk partent avec lui : enregistrées dans la même écriture que le bloc. */
    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            HeadlampLightData.writeNotes(HeadlampLightData.get(level).notedIn(event.getChunk().getPos()), event.getData());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkDataEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            sweep(level, event.getChunk(), HeadlampLightData.readNotes(event.getData()));
        }
    }

    /** La fin du tick de chaque monde de serveur : l'heure sûre pour toucher aux chunks qui viennent de charger. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer() && event.level instanceof ServerLevel level) {
            sweepPending(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        releaseAll(event.getServer());
    }
}
