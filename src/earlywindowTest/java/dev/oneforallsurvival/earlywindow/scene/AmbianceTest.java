package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** Valeurs de référence calculées avec {@code state(t)} et la caméra de compose.py. */
class AmbianceTest {
    @Test
    void auMilieuDuRecitLesValeursSontCellesDeCompose() {
        Ambiance a = Ambiance.at(22f, 22.0);
        assertEquals(0.6930392f, a.progress(), 1e-5f);
        assertEquals(1.0264933f, a.day(), 1e-5f);
        assertEquals(0f, a.storm(), 1e-6f);
        assertEquals(0.1395077f, a.stars(), 1e-5f);
        assertEquals(0.6147252f, a.moon(), 1e-5f);
        assertEquals(1.0278848f, a.zoom(), 1e-6f);
        assertEquals(0.6069084f, a.offX(), 1e-4f);
        assertEquals(-1.6899818f, a.offY(), 1e-4f);
    }

    @Test
    void pendantLOrageLaLuneSeLeve() {
        Ambiance a = Ambiance.at(12f, 12.0);
        assertEquals(0.6452009f, a.storm(), 1e-5f);
        assertEquals(0.4359478f, a.moon(), 1e-5f);
    }

    @Test
    void lAubeTenueGardeUneCameraVivante() {
        // Le récit est figé, mais la dérive suit le temps réel : deux temps réels, deux cadrages.
        Ambiance a = Ambiance.at(StoryClock.HOLD, 40.0);
        Ambiance b = Ambiance.at(StoryClock.HOLD, 45.0);
        assertEquals(2f, a.day(), 0f);
        assertEquals(a.zoom(), b.zoom(), 0f);
        assertEquals(a.day(), b.day(), 0f);
        assertNotEquals(a.offX(), b.offX());
    }

    @Test
    void lerp3PasseParLesTroisAmbiances() {
        float[][] p = {{0f}, {1f}, {3f}};
        assertArrayEquals(new float[] {0f}, Ambiance.lerp3(p, -1), 0f);
        assertArrayEquals(new float[] {0.5f}, Ambiance.lerp3(p, 0.5), 1e-6f);
        assertArrayEquals(new float[] {2f}, Ambiance.lerp3(p, 1.5), 1e-6f);
        assertArrayEquals(new float[] {3f}, Ambiance.lerp3(p, 2), 0f);
        assertArrayEquals(new float[] {3f}, Ambiance.lerp3(p, 5), 0f);
    }

    @Test
    void lePointDUneVueSuitCompose() {
        SceneManifest m = new SceneManifest(1920, 1080, 2112, 1188, null, null, null, null, null, null, null, null,
                null);
        float[] p = new Ambiance.View(2f, 10f, -5f).point(m, 1056 + 10, 594 - 5);
        assertArrayEquals(new float[] {960f, 540f}, p, 1e-4f);
    }
}
