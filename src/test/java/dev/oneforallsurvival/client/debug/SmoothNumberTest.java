package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmoothNumberTest {

    @Test
    void leChiffreSuitSansClignoter() {
        SmoothNumber n = new SmoothNumber(false);
        n.update(60, 0.016);
        double prev = 60;
        for (int i = 0; i < 400; i++) {
            double v = n.update(100, 0.016);
            assertTrue(v >= prev && v <= 100, "monotone, sans dépassement");
            prev = v;
        }
        assertEquals(100, prev, 0.01);
        // Une constante de temps : environ 63 % de l'écart résorbé.
        SmoothNumber m = new SmoothNumber(false);
        m.update(0, 0.016);
        assertEquals(1 - Math.exp(-1), m.update(1, SmoothNumber.TIME_CONSTANT), 1e-9);
    }

    @Test
    void independantDuRythmeDesImages() {
        SmoothNumber a = new SmoothNumber(false);
        SmoothNumber b = new SmoothNumber(false);
        a.update(10, 0.01);
        b.update(10, 0.01);
        a.update(20, 0.1);
        b.update(20, 0.05);
        b.update(20, 0.05);
        assertEquals(a.value(), b.value(), 1e-9);
    }

    @Test
    void uneTeleportationSauteDirectement() {
        SmoothNumber x = new SmoothNumber(true);
        x.update(100, 0.016);
        assertEquals(5000, x.update(5000, 0.016), 0.0);
        // Un petit pas reste lissé.
        double v = x.update(5010, 0.016);
        assertTrue(v > 5000 && v < 5010);
    }
}
