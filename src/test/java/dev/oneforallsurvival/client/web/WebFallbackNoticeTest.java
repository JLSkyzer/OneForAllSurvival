package dev.oneforallsurvival.client.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WebFallbackNoticeTest {

    @AfterEach
    void remettre() {
        WebFallbackNotice.resetForTest();
    }

    @Test
    void uneSeuleFois() {
        WebFallbackNotice.resetForTest();
        assertTrue(WebFallbackNotice.shouldWarn(), "premier avis de la session");
        WebFallbackNotice.markWarned();
        for (WebFailure autre : WebFailure.values()) {
            assertFalse(WebFallbackNotice.shouldWarn(), "second avis pour " + autre);
        }
    }
}
