package dev.oneforallsurvival.coast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoastZonesTest {

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");

    private CoastZones zones;

    @BeforeEach
    void init() {
        zones = new CoastZones();
    }

    @Test
    void theBoxDoesNotDependOnTheOrderOfTheCorners() {
        zones.add("port", OVERWORLD, new BlockPos(10, 70, 10), new BlockPos(0, 60, -5));
        assertTrue(zones.contains(OVERWORLD, new BlockPos(5, 65, 0)));
        assertEquals(new BlockPos(0, 60, -5), zones.list().get(0).min());
        assertEquals(new BlockPos(10, 70, 10), zones.list().get(0).max());
    }

    @Test
    void boundsAreInclusive() {
        zones.add("port", OVERWORLD, new BlockPos(0, 60, 0), new BlockPos(10, 70, 10));
        assertTrue(zones.contains(OVERWORLD, new BlockPos(0, 60, 0)));
        assertTrue(zones.contains(OVERWORLD, new BlockPos(10, 70, 10)));
        assertFalse(zones.contains(OVERWORLD, new BlockPos(11, 65, 5)));
        assertFalse(zones.contains(OVERWORLD, new BlockPos(5, 59, 5)));
        assertFalse(zones.contains(OVERWORLD, new BlockPos(5, 65, -1)));
    }

    @Test
    void anotherDimensionIsExcluded() {
        zones.add("port", OVERWORLD, BlockPos.ZERO, new BlockPos(10, 10, 10));
        assertTrue(zones.hasZones(OVERWORLD));
        assertFalse(zones.hasZones(NETHER));
        assertFalse(zones.contains(NETHER, new BlockPos(5, 5, 5)));
    }

    @Test
    void aDuplicateIdIsRefused() {
        assertTrue(zones.add("port", OVERWORLD, BlockPos.ZERO, BlockPos.ZERO));
        assertFalse(zones.add("port", NETHER, new BlockPos(5, 5, 5), new BlockPos(6, 6, 6)));
        assertEquals(1, zones.list().size());
    }

    @Test
    void removeReturnsWhetherTheZoneExisted() {
        zones.add("port", OVERWORLD, BlockPos.ZERO, BlockPos.ZERO);
        assertTrue(zones.remove("port"));
        assertFalse(zones.remove("port"));
        assertFalse(zones.hasZones(OVERWORLD));
    }

    @Test
    void versionChangesOnlyWhenTheZonesDo() {
        int v0 = zones.version();
        zones.add("port", OVERWORLD, BlockPos.ZERO, BlockPos.ZERO);
        int v1 = zones.version();
        assertNotEquals(v0, v1);
        zones.add("port", OVERWORLD, BlockPos.ZERO, BlockPos.ZERO);
        assertEquals(v1, zones.version());
        zones.remove("absente");
        assertEquals(v1, zones.version());
        zones.remove("port");
        assertNotEquals(v1, zones.version());
    }

    @Test
    void nbtRoundTripKeepsTheZones() {
        zones.add("port", OVERWORLD, new BlockPos(-3, 60, 4), new BlockPos(10, 70, 12));
        zones.add("crique", NETHER, BlockPos.ZERO, new BlockPos(1, 1, 1));
        CoastZones copy = CoastZones.load(zones.save());
        assertEquals(zones.list(), copy.list());
        assertTrue(copy.contains(OVERWORLD, new BlockPos(-3, 60, 4)));
    }

    @Test
    void loadingAnEmptyTagGivesNoZones() {
        assertTrue(CoastZones.load(new CompoundTag()).list().isEmpty());
    }
}
