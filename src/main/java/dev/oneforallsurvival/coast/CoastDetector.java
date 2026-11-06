package dev.oneforallsurvival.coast;

import dev.oneforallsurvival.machine.MachineConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Dit si une position est en milieu côtier. Si la dimension a des zones déclarées, seules elles
 * comptent. Sinon, il faut une source d'eau au niveau de la mer dans un disque autour de la position.
 *
 * <p>Ne charge jamais de chunk : une colonne non chargée est ignorée, et le résultat, alors incertain,
 * est gardé moins longtemps. Appelé depuis le fil du serveur seulement.
 */
public final class CoastDetector {

    /** Durée de validité d'un résultat sûr, en ticks. */
    static final long TTL = 6000;
    /** Durée de validité d'un résultat qui a ignoré une colonne, en ticks. */
    static final long TTL_UNCERTAIN = 200;
    private static final int MAX_ENTRIES = 2048;

    private record Key(ResourceLocation dimension, int x, int z) {}

    private record Entry(boolean coastal, long expires) {}

    private static final Map<Key, Entry> CACHE = new HashMap<>();
    private static CoastZones cachedZones;
    private static int cachedVersion;

    private CoastDetector() {}

    /** Vide le cache ; pour les tests, et quand le monde change sans que les zones le disent. */
    public static void clearCache() {
        CACHE.clear();
        cachedZones = null;
    }

    public static boolean isCoastal(ServerLevel level, BlockPos pos) {
        CoastZones zones = CoastSavedData.get(level.getServer()).zones();
        ResourceLocation dimension = level.dimension().location();
        if (zones.hasZones(dimension)) {
            return zones.contains(dimension, pos);
        }
        if (cachedZones != zones || cachedVersion != zones.version()) {
            CACHE.clear();
            cachedZones = zones;
            cachedVersion = zones.version();
        }
        long now = level.getGameTime();
        Key key = new Key(dimension, pos.getX(), pos.getZ());
        Entry known = CACHE.get(key);
        if (known != null && now < known.expires()) {
            return known.coastal();
        }
        boolean[] skipped = new boolean[1];
        boolean coastal = scan(level, pos, skipped);
        if (CACHE.size() >= MAX_ENTRIES) {
            CACHE.values().removeIf(e -> now >= e.expires());
            if (CACHE.size() >= MAX_ENTRIES) {
                CACHE.clear();
            }
        }
        CACHE.put(key, new Entry(coastal, now + (skipped[0] ? TTL_UNCERTAIN : TTL)));
        return coastal;
    }

    /** Cherche une source d'eau au niveau de la mer dans le disque, sur les seuls chunks déjà chargés. */
    private static boolean scan(ServerLevel level, BlockPos pos, boolean[] skipped) {
        int y = MachineConfig.seaLevel();
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return false;
        }
        int radius = MachineConfig.coastRadius();
        long radiusSq = (long) radius * radius;
        int minCx = (pos.getX() - radius) >> 4;
        int maxCx = (pos.getX() + radius) >> 4;
        int minCz = (pos.getZ() - radius) >> 4;
        int maxCz = (pos.getZ() + radius) >> 4;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    skipped[0] = true;
                    continue;
                }
                int x0 = Math.max(pos.getX() - radius, (cx << 4));
                int x1 = Math.min(pos.getX() + radius, ((cx << 4) + 15));
                int z0 = Math.max(pos.getZ() - radius, (cz << 4));
                int z1 = Math.min(pos.getZ() + radius, ((cz << 4) + 15));
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        long dx = x - pos.getX();
                        long dz = z - pos.getZ();
                        if (dx * dx + dz * dz > radiusSq) {
                            continue;
                        }
                        var fluid = chunk.getFluidState(cursor.set(x, y, z));
                        if (fluid.isSource() && fluid.is(FluidTags.WATER)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
