package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmoothLinesTest {

    private static final double IMAGE = 1.0 / 60.0;

    @Test
    void lePremierReleveEstTelQuel() {
        String ligne = "XYZ: 12.345 / 64.00000 / -5.678";
        assertEquals(ligne, new SmoothLines().apply("xyz", ligne, IMAGE));
    }

    @Test
    void leChiffreAvanceEnDouceurEtGardeSesDecimales() {
        SmoothLines lines = new SmoothLines();
        lines.apply("xyz", "XYZ: 100.000 / 64.00000 / 10.000", IMAGE);
        String ensuite = lines.apply("xyz", "XYZ: 101.000 / 64.00000 / 10.000", IMAGE);
        assertNotEquals("XYZ: 101.000 / 64.00000 / 10.000", ensuite);
        assertTrue(ensuite.matches("XYZ: 100\\.\\d{3} / 64\\.00000 / 10\\.000"), ensuite);
    }

    @Test
    void uneTeleportationSauteDirectement() {
        SmoothLines lines = new SmoothLines();
        lines.apply("xyz", "XYZ: 100.000 / 64.00000 / 10.000", IMAGE);
        assertEquals("XYZ: 9000.000 / 64.00000 / 10.000", lines.apply("xyz", "XYZ: 9000.000 / 64.00000 / 10.000", IMAGE));
    }

    @Test
    void seulLePremierNombreDesFpsEstLisse() {
        SmoothLines lines = new SmoothLines();
        lines.apply("fps", "100 fps T: 120 vsync B: 2", IMAGE);
        String ensuite = lines.apply("fps", "200 fps T: 240 vsync B: 2", IMAGE);
        assertTrue(ensuite.matches("1\\d\\d fps T: 240 vsync B: 2"), ensuite);
        assertNotEquals("200 fps T: 240 vsync B: 2", ensuite);
    }

    @Test
    void lesAutresLignesRestentExactes() {
        SmoothLines lines = new SmoothLines();
        lines.apply("block", "Block: 1 2 3", IMAGE);
        assertEquals("Block: 9 9 9", lines.apply("block", "Block: 9 9 9", IMAGE));
        lines.apply("mod.create", "Create: 1", IMAGE);
        assertEquals("Create: 5", lines.apply("mod.create", "Create: 5", IMAGE));
    }

    @Test
    void lesIdentifiantsNeSontPasDesNombres() {
        SmoothLines lines = new SmoothLines();
        lines.apply("mem", "Mem: 10% 100/4096MB r2d2", IMAGE);
        assertTrue(lines.apply("mem", "Mem: 20% 200/4096MB r2d2", IMAGE).endsWith("/4096MB r2d2"));
    }

    @Test
    void unChangementDeFormatRepartDeLaValeurReelle() {
        SmoothLines lines = new SmoothLines();
        lines.apply("server", "Integrated server @ 1 ms ticks, 0 tx, 0 rx", IMAGE);
        assertEquals("\"forge\" server, 500 tx, 700 rx", lines.apply("server", "\"forge\" server, 500 tx, 700 rx", IMAGE));
    }

    @Test
    void pasDeMoinsZero() {
        assertEquals("0.0", SmoothLines.format(-0.04, 1));
        assertEquals("-0.1", SmoothLines.format(-0.14, 1));
    }

    @Test
    void oublierRepartDeZero() {
        SmoothLines lines = new SmoothLines();
        lines.apply("fps", "100 fps", IMAGE);
        lines.clear();
        assertEquals("200 fps", lines.apply("fps", "200 fps", IMAGE));
    }
}
