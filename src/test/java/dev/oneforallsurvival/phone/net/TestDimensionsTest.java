package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class TestDimensionsTest {

    @Test
    void deuxAppelsRendentLaMemeInstance() {
        assertSame(TestDimensions.key("overworld"), TestDimensions.key("overworld"));
        assertSame(TestDimensions.OVERWORLD, TestDimensions.key("overworld"));
        assertSame(TestDimensions.NETHER, TestDimensions.key("the_nether"));
    }

    @Test
    void deuxDimensionsSontDeuxClesDistinctes() {
        assertNotEquals(TestDimensions.OVERWORLD, TestDimensions.NETHER);
        assertEquals("overworld", TestDimensions.OVERWORLD.location().getPath());
        assertEquals("minecraft:dimension", TestDimensions.OVERWORLD.registry().toString());
    }
}
