package dev.oneforallsurvival.client.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WebFailureTest {

    @Test
    void cleDeTraduction() {
        assertEquals("message.oneforallsurvival.web.fallback.no_ready", WebFailure.NO_READY.translationKey());
        assertEquals("message.oneforallsurvival.web.fallback.absent", WebFailure.ABSENT.translationKey());
        assertEquals(5, WebFailure.values().length);
    }
}
