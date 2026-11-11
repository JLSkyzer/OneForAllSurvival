package dev.oneforallsurvival.explore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class SignalMessageTest {

    @Test
    void lesCodesDeFormatSontRetires() {
        assertFalse(new SignalMessage(0, 1, "\u00a74Rouge \u00a7lgras\u00a7r").text().contains("\u00a7"));
        assertEquals("4Rouge lgrasr", new SignalMessage(0, 1, "\u00a74Rouge \u00a7lgras\u00a7r").text());
    }

    @Test
    void lesRetoursALaLigneDeviennentDesEspaces() {
        assertEquals("a b c", new SignalMessage(0, 1, "a\nb\tc").text());
    }

    @Test
    void coupeA256Caracteres() {
        assertEquals(256, new SignalMessage(0, 1, "x".repeat(1000)).text().length());
        assertEquals(256, SignalMessage.MAX_LENGTH);
    }

    @Test
    void unTexteCourtResteIntact() {
        assertEquals("Bonjour à tous", new SignalMessage(5, 9, "Bonjour à tous").text());
    }

    @Test
    void lesSectionsNeComptentPasDansLaLimite() {
        String brut = "\u00a7a".repeat(200) + "y".repeat(300);
        assertEquals(256, new SignalMessage(0, 1, brut).text().length());
    }
}
