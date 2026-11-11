package dev.oneforallsurvival.explore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Les positions des récepteurs de signaux d'une dimension, sauvegardées avec elle : {@code /signal send} les trouve sans
 * parcourir ni charger de chunk. Un récepteur posé s'y ajoute, un récepteur retiré en sort.
 */
public final class ReceiverIndex extends SavedData {

    public static final String KEY = "oneforallsurvival_receivers";
    private static final String TAG_POSITIONS = "Positions";

    private final Set<Long> positions = new LinkedHashSet<>();

    public static ReceiverIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ReceiverIndex::load, ReceiverIndex::new, KEY);
    }

    private static ReceiverIndex load(CompoundTag tag) {
        ReceiverIndex index = new ReceiverIndex();
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

    private static long horizontalDistSqr(BlockPos p, BlockPos centre) {
        long dx = (long) p.getX() - centre.getX();
        long dz = (long) p.getZ() - centre.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Les récepteurs à {@code radius} blocs au plus de {@code centre}, en distance horizontale (la hauteur ne compte
     * pas), du plus proche au plus lointain.
     */
    public List<BlockPos> within(BlockPos centre, int radius) {
        long limit = (long) radius * radius;
        List<BlockPos> found = new ArrayList<>();
        for (long packed : positions) {
            BlockPos p = BlockPos.of(packed);
            if (horizontalDistSqr(p, centre) <= limit) {
                found.add(p);
            }
        }
        found.sort(Comparator.comparingLong(p -> horizontalDistSqr(p, centre)));
        return found;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray(TAG_POSITIONS, positions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
