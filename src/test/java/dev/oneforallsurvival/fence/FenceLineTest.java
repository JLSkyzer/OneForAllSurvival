package dev.oneforallsurvival.fence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class FenceLineTest {

    private static final BlockPos START = BlockPos.ZERO;

    private static Set<BlockPos> row(int fromX, int toX, int y, int z) {
        Set<BlockPos> set = new HashSet<>();
        for (int x = fromX; x <= toX; x++) {
            set.add(new BlockPos(x, y, z));
        }
        return set;
    }

    @Test
    void suitUneLigneDroite() {
        List<BlockPos> line = FenceLine.scan(START, row(1, 5, 0, 0)::contains, 64);
        assertEquals(List.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(3, 0, 0),
                new BlockPos(4, 0, 0), new BlockPos(5, 0, 0)), line);
    }

    @Test
    void sArreteAuTrou() {
        Set<BlockPos> fences = row(1, 2, 0, 0);
        fences.addAll(row(4, 6, 0, 0));
        List<BlockPos> line = FenceLine.scan(START, fences::contains, 64);
        assertEquals(List.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)), line);
    }

    @Test
    void neDepassePasLeMaximum() {
        List<BlockPos> line = FenceLine.scan(START, row(1, 10, 0, 0)::contains, 3);
        assertEquals(3, line.size());
        assertEquals(new BlockPos(3, 0, 0), line.get(2));
    }

    @Test
    void gardeLesPlusProches() {
        Set<BlockPos> fences = row(1, 10, 0, 0);
        fences.addAll(row(-10, -1, 0, 0));
        List<BlockPos> line = FenceLine.scan(START, fences::contains, 4);
        assertEquals(Set.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(-2, 0, 0)), new HashSet<>(line));
    }

    @Test
    void traverseUneBranche() {
        // Une barre en T : la ligne principale part vers l'est, une branche monte du bloc x = 2 vers le sud.
        Set<BlockPos> fences = row(1, 4, 0, 0);
        fences.addAll(row(2, 2, 0, 1));
        fences.addAll(row(2, 2, 0, 2));
        List<BlockPos> line = FenceLine.scan(START, fences::contains, 64);
        assertEquals(6, line.size());
        assertTrue(line.contains(new BlockPos(2, 0, 2)));
        assertTrue(line.contains(new BlockPos(4, 0, 0)));
    }

    @Test
    void neBouclePasSurUnCircuit() {
        // Un anneau de 8 clôtures autour de (2, 0, 2) ; l'électrificateur, en (0, 0, 1), touche son coin.
        Set<BlockPos> ring = new HashSet<>();
        for (int i = 1; i <= 3; i++) {
            ring.add(new BlockPos(i, 0, 1));
            ring.add(new BlockPos(i, 0, 3));
        }
        ring.add(new BlockPos(1, 0, 2));
        ring.add(new BlockPos(3, 0, 2));
        List<BlockPos> line = FenceLine.scan(new BlockPos(0, 0, 1), ring::contains, 64);
        assertEquals(8, line.size());
        assertEquals(8, new HashSet<>(line).size());
    }

    @Test
    void monteEnHauteur() {
        Set<BlockPos> fences = new HashSet<>();
        for (int y = 1; y <= 4; y++) {
            fences.add(new BlockPos(0, y, 0));
        }
        List<BlockPos> line = FenceLine.scan(START, fences::contains, 64);
        assertEquals(4, line.size());
        assertEquals(new BlockPos(0, 4, 0), line.get(3));
    }

    @Test
    void neCompteJamaisLeDepart() {
        Set<BlockPos> fences = row(0, 3, 0, 0); // le départ est lui-même une « clôture » pour le prédicat
        List<BlockPos> line = FenceLine.scan(START, fences::contains, 64);
        assertFalse(line.contains(START));
        assertEquals(3, line.size());
    }

    @Test
    void sansClotureRendVide() {
        assertTrue(FenceLine.scan(START, pos -> false, 64).isEmpty());
    }
}
