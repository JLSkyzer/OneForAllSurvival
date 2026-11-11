package dev.oneforallsurvival.explore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ReceiverIndexTest {

    private static final BlockPos CENTRE = new BlockPos(100, 64, -200);

    @Test
    void laDistanceEstHorizontale() {
        ReceiverIndex index = new ReceiverIndex();
        index.add(new BlockPos(100, 300, -200));
        index.add(new BlockPos(110, 0, -200));
        assertEquals(1, index.within(CENTRE, 5).size());
        assertEquals(2, index.within(CENTRE, 10).size());
    }

    @Test
    void laBorneEstIncluse() {
        ReceiverIndex index = new ReceiverIndex();
        index.add(new BlockPos(103, 64, -196));
        assertEquals(1, index.within(CENTRE, 5).size());
        assertEquals(0, index.within(CENTRE, 4).size());
    }

    @Test
    void duPlusProcheAuPlusLointain() {
        ReceiverIndex index = new ReceiverIndex();
        BlockPos loin = new BlockPos(150, 64, -200);
        BlockPos pres = new BlockPos(101, 64, -200);
        index.add(loin);
        index.add(pres);
        assertEquals(List.of(pres, loin), index.within(CENTRE, 100));
    }

    @Test
    void ajouterEtRetirer() {
        ReceiverIndex index = new ReceiverIndex();
        assertTrue(index.add(CENTRE));
        assertFalse(index.add(CENTRE));
        assertTrue(index.contains(CENTRE));
        assertTrue(index.remove(CENTRE));
        assertFalse(index.remove(CENTRE));
        assertEquals(0, index.size());
    }

    @Test
    void lesGrandsRayonsNeDebordentPas() {
        ReceiverIndex index = new ReceiverIndex();
        index.add(new BlockPos(29_000_000, 64, 29_000_000));
        assertEquals(0, index.within(new BlockPos(-29_000_000, 64, -29_000_000), 30000).size());
    }

    @Test
    void sauvegardeEtRelecture() {
        ReceiverIndex index = new ReceiverIndex();
        index.add(CENTRE);
        index.add(new BlockPos(-5, 70, 12));
        CompoundTag tag = index.save(new CompoundTag());
        ReceiverIndex relu = new ReceiverIndex();
        for (long packed : tag.getLongArray("Positions")) {
            relu.add(BlockPos.of(packed));
        }
        assertEquals(2, relu.size());
        assertTrue(relu.contains(new BlockPos(-5, 70, 12)));
    }
}
