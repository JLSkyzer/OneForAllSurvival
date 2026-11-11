package dev.oneforallsurvival.explore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Les blocs de lumière que les lampes frontales ont posés dans une dimension, avec le nombre de lampes qui s'y
 * appuient. La note est sauvegardée avec le monde : après un plantage, le chargement du chunk retrouve les blocs que
 * personne ne tient plus. Un bloc de lumière qui n'est pas noté ici n'est jamais touché.
 */
public final class HeadlampLightData extends SavedData {

    public static final String KEY = "oneforallsurvival_headlamp_lights";
    private static final String TAG_POSITIONS = "Positions";
    private static final String TAG_COUNTS = "Counts";

    /** Position empaquetée vers le nombre de lampes qui s'y appuient (toujours au moins 1). */
    private final Map<Long, Integer> counts = new LinkedHashMap<>();

    public static HeadlampLightData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HeadlampLightData::load, HeadlampLightData::new, KEY);
    }

    private static HeadlampLightData load(CompoundTag tag) {
        HeadlampLightData data = new HeadlampLightData();
        long[] positions = tag.getLongArray(TAG_POSITIONS);
        int[] saved = tag.getIntArray(TAG_COUNTS);
        for (int i = 0; i < positions.length && i < saved.length; i++) {
            if (saved[i] > 0) {
                data.counts.put(positions[i], saved[i]);
            }
        }
        return data;
    }

    /** Une lampe de plus s'appuie sur ce bloc de lumière. */
    public void placed(BlockPos pos) {
        counts.merge(pos.asLong(), 1, Integer::sum);
        setDirty();
    }

    /** Une lampe de moins ; vrai si c'était la dernière (le bloc est alors à retirer). Faux si la position n'est pas notée. */
    public boolean released(BlockPos pos) {
        Integer have = counts.get(pos.asLong());
        if (have == null) {
            return false;
        }
        if (have > 1) {
            counts.put(pos.asLong(), have - 1);
            setDirty();
            return false;
        }
        counts.remove(pos.asLong());
        setDirty();
        return true;
    }

    /** Ce bloc de lumière a été posé par une lampe frontale. */
    public boolean isOurs(BlockPos pos) {
        return counts.containsKey(pos.asLong());
    }

    /** Efface la note, quel que soit le nombre de lampes (balayage d'un chunk). */
    public void forget(BlockPos pos) {
        if (counts.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    /** Les positions notées dans ce chunk. */
    public List<BlockPos> notedIn(ChunkPos chunk) {
        List<BlockPos> out = new ArrayList<>();
        for (long packed : counts.keySet()) {
            BlockPos pos = BlockPos.of(packed);
            if ((pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z) {
                out.add(pos);
            }
        }
        return out;
    }

    public int size() {
        return counts.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray(TAG_POSITIONS, counts.keySet().stream().mapToLong(Long::longValue).toArray());
        tag.putIntArray(TAG_COUNTS, counts.values().stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }
}
