package dev.oneforallsurvival.client.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebMessageTest {

    @Test
    void messageValide() {
        var m = WebMessage.parse("{\"a\":\"ready\"}");
        assertTrue(m.isPresent());
        assertEquals("ready", m.get().get("a").getAsString());
    }

    @Test
    void messagesRefuses() {
        String[] mauvais = {
            null,
            "",
            "[]",
            "{",
            "null",
            "\"a\"",
            "{\"a\":5}",
            "{\"b\":\"x\"}",
            "{\"a\":\"\"}",
            "{\"a\":null}",
            "{\"a\":{}}",
            "{\"a\":\"" + "x".repeat(33) + "\"}",
            "x".repeat(100_000),
            "{\"a\":\"ready\",\"p\":\"" + "x".repeat(WebMessage.MAX_LENGTH) + "\"}"
        };
        for (String raw : mauvais) {
            assertTrue(
                    WebMessage.parse(raw).isEmpty(),
                    raw == null ? "null" : raw.substring(0, Math.min(40, raw.length())));
        }
    }

    @Test
    void bornes() {
        assertTrue(WebMessage.parse("{\"a\":\"" + "x".repeat(32) + "\"}").isPresent());
        assertTrue(WebMessage.parse("{\"a\":\"x\"}").isPresent());
        String base = "{\"a\":\"x\",\"p\":\"";
        String pile = base + "y".repeat(WebMessage.MAX_LENGTH - base.length() - 2) + "\"}";
        assertEquals(WebMessage.MAX_LENGTH, pile.length());
        assertTrue(WebMessage.parse(pile).isPresent());
        assertTrue(WebMessage.parse(pile + " ").isEmpty());
    }
}
