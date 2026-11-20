package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SignalMathTest {

    private static final int RANGE = 270;

    @Test
    void quatreBarresJusqu67() {
        assertEquals(4, SignalMath.bars(0, RANGE));
        assertEquals(4, SignalMath.bars(67, RANGE));
    }

    @Test
    void troisBarresDe68a134() {
        assertEquals(3, SignalMath.bars(68, RANGE));
        assertEquals(3, SignalMath.bars(134, RANGE));
    }

    @Test
    void deuxBarresDe135a202() {
        assertEquals(2, SignalMath.bars(135, RANGE));
        assertEquals(2, SignalMath.bars(202, RANGE));
    }

    @Test
    void uneBarreDe203aLaPortee() {
        assertEquals(1, SignalMath.bars(203, RANGE));
        assertEquals(1, SignalMath.bars(270, RANGE));
    }

    @Test
    void auDelaDeLaPorteeAucuneBarre() {
        assertEquals(0, SignalMath.bars(270.01, RANGE));
        assertEquals(0, SignalMath.bars(10_000, RANGE));
        assertEquals(0, SignalMath.bars(Double.NaN, RANGE));
    }

    @Test
    void uneAutrePorteeSuitLesQuarts() {
        // 100 blocs : 25, 50 et 75 appartiennent à la tranche du dessous, la portée à la dernière.
        assertEquals(4, SignalMath.bars(24.9, 100));
        assertEquals(3, SignalMath.bars(25, 100));
        assertEquals(3, SignalMath.bars(49.9, 100));
        assertEquals(2, SignalMath.bars(50, 100));
        assertEquals(2, SignalMath.bars(74.9, 100));
        assertEquals(1, SignalMath.bars(75, 100));
        assertEquals(1, SignalMath.bars(100, 100));
        assertEquals(0, SignalMath.bars(100.01, 100));
    }

    @Test
    void sansPorteePasDeSignal() {
        assertEquals(0, SignalMath.bars(5, 0));
        assertEquals(0, SignalMath.bars(0, 0));
        assertEquals(0, SignalMath.bars(5, -10));
    }

    @Test
    void distanceDeTroisQuatreCinq() {
        assertEquals(5.0, SignalMath.distance(new BlockPos(0, 0, 0), new BlockPos(3, 4, 0)));
    }

    @Test
    void distanceEnTroisDimensionsEtSymetrique() {
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(3, 4, 4);
        assertEquals(3.0, SignalMath.distance(a, b));
        assertEquals(3.0, SignalMath.distance(b, a));
        assertEquals(0.0, SignalMath.distance(a, a));
    }

    @Test
    void distanceSansDebordementAuxBordsDuMonde() {
        double d = SignalMath.distance(new BlockPos(-29_000_000, 0, 0), new BlockPos(29_000_000, 0, 0));
        assertEquals(58_000_000.0, d);
    }
}
