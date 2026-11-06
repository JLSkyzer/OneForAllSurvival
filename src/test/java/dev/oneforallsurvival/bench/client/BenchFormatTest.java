package dev.oneforallsurvival.bench.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class BenchFormatTest {

    @Test
    void duree() {
        assertEquals("0 s", BenchFormat.duration(0));
        assertEquals("8 s", BenchFormat.duration(8));
        assertEquals("59 s", BenchFormat.duration(59));
        assertEquals("1 min", BenchFormat.duration(60));
        assertEquals("1 min 26 s", BenchFormat.duration(86));
        assertEquals("10 min", BenchFormat.duration(600));
        assertEquals("1 h", BenchFormat.duration(3600));
        assertEquals("1 h 5 min", BenchFormat.duration(3900));
        assertEquals("0 s", BenchFormat.duration(-4));
    }

    @Test
    void tempsRestantArrondiAuDessus() {
        assertEquals("1 s", BenchFormat.remaining(1));
        assertEquals("1 s", BenchFormat.remaining(20));
        assertEquals("2 s", BenchFormat.remaining(21));
        assertEquals("0 s", BenchFormat.remaining(0));
    }

    @Test
    void vitesse() {
        assertEquals("4", BenchFormat.speed(4.0, Locale.FRANCE));
        assertEquals("2,5", BenchFormat.speed(2.5, Locale.FRANCE));
        assertEquals("2.5", BenchFormat.speed(2.5, Locale.US));
        assertEquals("0.25", BenchFormat.speed(0.25, Locale.US));
    }

    @Test
    void defilementBorneLesLignes() {
        ScrollModel m = new ScrollModel();
        m.setSize(100, 7);
        assertEquals(0, m.first());
        m.scrollBy(-5);
        assertEquals(0, m.first());
        m.scrollBy(500);
        assertEquals(93, m.first());
        m.reveal(3);
        assertEquals(3, m.first());
        m.reveal(20);
        assertEquals(14, m.first());
        m.setSize(5, 7);
        assertEquals(0, m.first());
        assertFalse(m.scrollable());
    }

    @Test
    void poigneeProportionnelleEtGlissante() {
        ScrollModel m = new ScrollModel();
        m.setSize(100, 10);
        assertEquals(12, m.thumbHeight(120));
        assertEquals(0, m.thumbTop(120));
        m.dragThumbTo(1000, 120);
        assertEquals(90, m.first());
        assertEquals(120 - 12, m.thumbTop(120));
        m.setSize(100000, 10);
        assertTrue(m.thumbHeight(120) >= 8);
    }
}
