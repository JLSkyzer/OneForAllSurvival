package dev.oneforallsurvival.explore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Les positions des caches d'une dimension, sauvegardées avec elle : le détecteur les trouve sans parcourir les
 * chunks et sans les charger. Une cache posée s'y ajoute, une cache retirée en sort.
 */
public final class CacheIndex extends SavedData {

    public static final String KEY = "oneforallsurvival_caches";
    private static final String TAG_POSITIONS = "Positions";

    private final Set<Long> positions = new LinkedHashSet<>();

    public static CacheIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CacheIndex::load, CacheIndex::new, KEY);
    }

    private static CacheIndex load(CompoundTag tag) {
        CacheIndex index = new CacheIndex();
        for (long packed : tag.getLongArray(TAG_POSITIONS)) {
            index.positions.add(packed);
        }
        return index;
    }

    public boolean add(BlockPos pos) {
        boolean added = positions.add(pos.asLong());
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean remove(BlockPos pos) {
        boolean removed = positions.remove(pos.asLong());
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    public int size() {
        return positions.size();
    }

    /** Les positions de la dimension, de la plus proche de {@code from} à la plus lointaine. */
    public List<BlockPos> sortedFrom(BlockPos from) {
        List<BlockPos> all = new ArrayList<>(positions.size());
        positions.forEach(p -> all.add(BlockPos.of(p)));
        all.sort(Comparator.comparingDouble(p -> p.distSqr(from)));
        return all;
    }

    /** La cache la plus proche, à {@code range} blocs au plus (distance réelle depuis {@code from}). */
    public Optional<BlockPos> nearest(BlockPos from, double range) {
        double limit = range * range;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (long packed : positions) {
            BlockPos p = BlockPos.of(packed);
            double d = p.distSqr(from);
            if (d <= limit && d < bestDist) {
                best = p;
                bestDist = d;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray(TAG_POSITIONS, positions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
