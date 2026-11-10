package dev.oneforallsurvival.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WellBeingTest {

    @Test
    void garderLaPlusLongue() {
        assertEquals(200, WellBeing.newDuration(100, 200));
        assertEquals(300, WellBeing.newDuration(300, 200));
    }

    @Test
    void zeroResteZero() {
        assertEquals(0, WellBeing.newDuration(0, 0));
    }
}
