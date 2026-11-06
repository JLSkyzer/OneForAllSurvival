package dev.oneforallsurvival.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.oneforallsurvival.bench.WorkshopTicker;
import org.junit.jupiter.api.Test;

class MachineWearTest {

    @Test
    void wearPerTickSpreadsTheLifetimeOver100Points() {
        assertEquals(0.1, MachineWear.wearPerTick(1000, false, false, 2), 1e-12);
    }

    @Test
    void coastalDoublesTheWearUnlessProtected() {
        assertEquals(0.2, MachineWear.wearPerTick(1000, true, false, 2), 1e-12);
        assertEquals(0.1, MachineWear.wearPerTick(1000, true, true, 2), 1e-12);
    }

    @Test
    void efficiencyDropsStrictlyBelowFifty() {
        assertEquals(1.0, MachineWear.efficiency(50, .75), 0);
        assertEquals(.75, MachineWear.efficiency(49.9, .75), 0);
    }

    @Test
    void combinedSpeedKeepsTheBestOfHandAndPower() {
        assertEquals(3.0, WorkshopTicker.combinedSpeed(1, 4, .75), 1e-12);
        assertEquals(1.0, WorkshopTicker.combinedSpeed(1, 4, .1), 1e-12);
    }

    @Test
    void faultTypesHaveKeysAndLookupByName() {
        assertEquals("fault.oneforallsurvival.fouling", FaultType.FOULING.translationKey());
        assertEquals(FaultType.BEARING, FaultType.byName("bearing"));
        assertNull(FaultType.byName("nope"));
        assertNull(FaultType.byName(null));
    }
}
