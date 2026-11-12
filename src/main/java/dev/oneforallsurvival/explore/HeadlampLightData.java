package dev.oneforallsurvival.explore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Les blocs de lumière que les lampes frontales ont posés dans une dimension, avec le nombre de lampes qui s'y
 * appuient. La table vit en mémoire, par dimension. Sa copie durable n'est pas un fichier à part : à chaque sauvegarde
 * d'un chunk, les notes de ce chunk sont écrites dans les données du chunk lui-même ({@link #writeNotes}), donc
 * enregistrées atomiquement avec le bloc. Après un plantage, le chargement du chunk relit ces notes
 * ({@link #readNotes}) et le balayage retire les blocs que plus aucune lampe ne tient. Un bloc de lumière qui n'est
 * pas noté n'est jamais touché. Les méthodes sont synchronisées : Forge publie la sauvegarde d'un chunk depuis
 * {@code ChunkMap.save}, et la table peut être lue de n'importe quel fil sans dommage.
 */
public final class HeadlampLightData {

    /** La clé des notes dans les données d'un chunk. */
    public static final String CHUNK_KEY = "oneforallsurvival:headlamp_lights";
    private static final String TAG_POSITIONS = "Positions";
    private static final String TAG_COUNTS = "Counts";

    /** Une position notée et le nombre de lampes qui s'y appuient (au moins 1). */
    public record Note(BlockPos pos, int count) {}

    private static final Map<ServerLevel, HeadlampLightData> BY_LEVEL =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Position empaquetée vers le nombre de lampes qui s'y appuient (toujours au moins 1). */
    private final Map<Long, Integer> counts = new LinkedHashMap<>();

    public static HeadlampLightData get(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, l -> new HeadlampLightData());
    }

    /** Écrit ces notes dans les données d'un chunk. Sans note, ne touche à rien. */
    public static void writeNotes(Collection<Note> notes, CompoundTag chunkData) {
        if (notes.isEmpty()) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putLongArray(TAG_POSITIONS, notes.stream().mapToLong(n -> n.pos().asLong()).toArray());
        tag.putIntArray(TAG_COUNTS, notes.stream().mapToInt(Note::count).toArray());
        chunkData.put(CHUNK_KEY, tag);
    }

    /** Relit les notes écrites par {@link #writeNotes} ; une donnée absente ou bancale ne donne rien. */
    public static List<Note> readNotes(CompoundTag chunkData) {
        List<Note> out = new ArrayList<>();
        if (!chunkData.contains(CHUNK_KEY, 10)) {
            return out;
        }
        CompoundTag tag = chunkData.getCompound(CHUNK_KEY);
        long[] positions = tag.getLongArray(TAG_POSITIONS);
        int[] saved = tag.getIntArray(TAG_COUNTS);
        for (int i = 0; i < positions.length && i < saved.length; i++) {
            if (saved[i] > 0) {
                out.add(new Note(BlockPos.of(positions[i]), saved[i]));
            }
        }
        return out;
    }

    /** Une lampe de plus s'appuie sur ce bloc de lumière. */
    public synchronized void placed(BlockPos pos) {
        counts.merge(pos.asLong(), 1, Integer::sum);
    }

    /** Une lampe de moins ; vrai si c'était la dernière (le bloc est alors à retirer). Faux si la position n'est pas notée. */
    public synchronized boolean released(BlockPos pos) {
        Integer have = counts.get(pos.asLong());
        if (have == null) {
            return false;
        }
        if (have > 1) {
            counts.put(pos.asLong(), have - 1);
            return false;
        }
        counts.remove(pos.asLong());
        return true;
    }

    /** Ce bloc de lumière a été posé par une lampe frontale. */
    public synchronized boolean isOurs(BlockPos pos) {
        return counts.containsKey(pos.asLong());
    }

    /** Efface la note, quel que soit le nombre de lampes (balayage d'un chunk). */
    public synchronized void forget(BlockPos pos) {
        counts.remove(pos.asLong());
    }

    /** Les notes de ce chunk. */
    public synchronized List<Note> notedIn(ChunkPos chunk) {
        List<Note> out = new ArrayList<>();
        counts.forEach((packed, count) -> {
            BlockPos pos = BlockPos.of(packed);
            if ((pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z) {
                out.add(new Note(pos, count));
            }
        });
        return out;
    }

    public synchronized int size() {
        return counts.size();
    }
}
