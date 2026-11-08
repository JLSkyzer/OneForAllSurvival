package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DebugThemeTest {

    @Test
    void leFonduNeTouchePasLaCouleur() {
        assertEquals(0x4FFFB544, DebugTheme.fade(0x9EFFB544, 0.5));
        assertEquals(0x00FFB544, DebugTheme.fade(0xFFFFB544, 0));
        assertEquals(0xFFFFB544, DebugTheme.fade(0xFFFFB544, 2));
    }

    @Test
    void lesBarresVontDuVertAuRougeEnPassantParLAmbre() {
        assertEquals(DebugTheme.GOOD, DebugTheme.sampleColor(0, 100));
        assertEquals(DebugTheme.AMBER, DebugTheme.sampleColor(50, 100));
        assertEquals(DebugTheme.BAD, DebugTheme.sampleColor(100, 100));
    }

    @Test
    void lEchelleDesImagesEstCelleDeVanilla() {
        assertEquals(30, DebugTheme.frameValue(1_000_000_000L / 60 + 1_000));
        assertEquals(DebugTheme.FRAME_COLOR_MAX, DebugTheme.frameValue(1_000_000_000L));
    }

    @Test
    void lePetitGrapheEstBorne() {
        assertEquals(1, DebugTheme.sparkHeight(0, 14));
        assertEquals(7, DebugTheme.sparkHeight(1_000_000_000L / 60, 14));
        assertEquals(14, DebugTheme.sparkHeight(1_000_000_000L, 14));
    }

    @Test
    void lesTitresTombentSurDesPixelsEntiers() {
        assertEquals(1.0, DebugTheme.titleScale(1));
        assertEquals(1.0, DebugTheme.titleScale(2));
        assertEquals(2.0 / 3.0, DebugTheme.titleScale(3), 1e-9);
        assertEquals(0.75, DebugTheme.titleScale(4), 1e-9);
    }
}
