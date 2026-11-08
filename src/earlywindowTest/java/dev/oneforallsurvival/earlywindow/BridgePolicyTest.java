package dev.oneforallsurvival.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.earlywindow.BridgePolicy.Setting;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgePolicyTest {
    private static final String RTX = "NVIDIA GeForce RTX 4060/PCIe/SSE2";

    @Test
    void actifSurUnPosteOrdinaire() {
        assertTrue(BridgePolicy.decide(Setting.AUTO, 16, "4.6", RTX, true).enabled());
        assertTrue(BridgePolicy.decide(Setting.AUTO, 3, "3.2", RTX, true).enabled());
    }

    @Test
    void deuxCoeursOuMoinsLeCoupent() {
        BridgePolicy.Decision d = BridgePolicy.decide(Setting.AUTO, 2, "4.6", RTX, true);
        assertFalse(d.enabled());
        assertTrue(d.reason().contains("2 cœurs"), d.reason());
        assertFalse(BridgePolicy.decide(Setting.AUTO, 1, "4.6", RTX, true).enabled());
    }

    @Test
    void unGlTropAncienLeCoupe() {
        assertFalse(BridgePolicy.decide(Setting.AUTO, 8, "3.1", RTX, true).enabled());
        assertFalse(BridgePolicy.decide(Setting.AUTO, 8, "2.1", RTX, true).enabled());
        assertFalse(BridgePolicy.decide(Setting.AUTO, 8, "n/a", RTX, true).enabled());
        assertTrue(BridgePolicy.atLeast32("3.3"));
        assertTrue(BridgePolicy.atLeast32("4.0"));
    }

    @Test
    void unRenduLogicielLeCoupe() {
        assertFalse(BridgePolicy.decide(Setting.AUTO, 8, "4.5", "llvmpipe (LLVM 15.0.7, 256 bits)", true).enabled());
        assertFalse(BridgePolicy.decide(Setting.AUTO, 8, "4.5", "GDI Generic", true).enabled());
        assertFalse(BridgePolicy.decide(Setting.AUTO, 8, "4.5", "Microsoft Basic Render Driver", true).enabled());
        assertFalse(BridgePolicy.decide(Setting.AUTO, 8, "4.5", null, true).enabled());
    }

    @Test
    void leReglageCoupeOuForce() {
        assertFalse(BridgePolicy.decide(Setting.OFF, 16, "4.6", RTX, true).enabled());
        assertTrue(BridgePolicy.decide(Setting.FORCE, 2, "4.6", RTX, true).enabled());
        assertTrue(BridgePolicy.decide(Setting.FORCE, 2, "4.5", "llvmpipe", true).enabled());
    }

    @Test
    void horsDeWindowsJamais() {
        assertFalse(BridgePolicy.decide(Setting.AUTO, 16, "4.6", RTX, false).enabled());
        assertFalse(BridgePolicy.decide(Setting.FORCE, 16, "4.6", RTX, false).enabled());
    }

    @Test
    void lesValeursDuReglage() {
        assertEquals(Setting.OFF, Setting.parse("false"));
        assertEquals(Setting.OFF, Setting.parse(" FALSE "));
        assertEquals(Setting.AUTO, Setting.parse("auto"));
        assertEquals(Setting.AUTO, Setting.parse("true"));
        assertEquals(Setting.FORCE, Setting.parse("force"));
        assertEquals(null, Setting.parse("peut-être"));
    }

    @Test
    void leFichierEstCreeAvecLaValeurParDefaut(@TempDir Path dir) throws Exception {
        List<String> log = new ArrayList<>();
        Path config = dir.resolve("config");
        assertEquals(Setting.AUTO, BridgePolicy.resolve(null, config, log::add));
        Path file = config.resolve(BridgePolicy.FILE_NAME);
        assertTrue(Files.exists(file));
        byte[] bytes = Files.readAllBytes(file);
        assertTrue(bytes[0] == '#', "pas de BOM en tête");
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("bridgeThread=auto"));
        // Relu : même valeur, sans réécriture.
        assertEquals(Setting.AUTO, BridgePolicy.resolve(null, config, log::add));
    }

    @Test
    void leFichierEtLaProprieteSontLus(@TempDir Path dir) throws Exception {
        List<String> log = new ArrayList<>();
        Files.writeString(dir.resolve(BridgePolicy.FILE_NAME), "# x\nbridgeThread=false\n", StandardCharsets.UTF_8);
        assertEquals(Setting.OFF, BridgePolicy.resolve(null, dir, log::add));
        assertEquals(Setting.FORCE, BridgePolicy.resolve("force", dir, log::add), "la propriété l'emporte");
        assertEquals(Setting.OFF, BridgePolicy.resolve("n'importe quoi", dir, log::add), "propriété inconnue : fichier");
        Files.writeString(dir.resolve(BridgePolicy.FILE_NAME), "bridgeThread=bof\n", StandardCharsets.UTF_8);
        assertEquals(Setting.AUTO, BridgePolicy.resolve(null, dir, log::add));
        assertTrue(log.stream().anyMatch(l -> l.contains("non reconnu")));
    }
}
