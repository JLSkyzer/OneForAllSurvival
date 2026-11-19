package dev.oneforallsurvival.client.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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
        assertFalse(WebFallbackNotice.shouldWarn(), "second avis");
    }

    @Test
    void claimNeVautQuUneFois() {
        WebFallbackNotice.resetForTest();
        assertTrue(WebFallbackNotice.claim(), "premier appel");
        assertFalse(WebFallbackNotice.claim(), "second appel");
        assertFalse(WebFallbackNotice.claim(), "troisième appel");
    }

    @Test
    void warnOnceNEnvoieQuUnAvisParSession() {
        WebFallbackNotice.resetForTest();
        List<WebFailure> envoyes = new ArrayList<>();
        WebFallbackNotice.warnOnce(WebFailure.ABSENT, envoyes::add);
        WebFallbackNotice.warnOnce(WebFailure.ABSENT, envoyes::add);
        for (WebFailure autre : WebFailure.values()) {
            WebFallbackNotice.warnOnce(autre, envoyes::add);
        }
        assertEquals(List.of(WebFailure.ABSENT), envoyes, "un seul avis, le premier");
    }

    @Test
    void sansRaisonRienNestEnvoyeNiConsomme() {
        WebFallbackNotice.resetForTest();
        List<WebFailure> envoyes = new ArrayList<>();
        WebFallbackNotice.warnOnce(null, envoyes::add);
        assertTrue(envoyes.isEmpty());
        WebFallbackNotice.warnOnce(WebFailure.NO_READY, envoyes::add);
        assertEquals(List.of(WebFailure.NO_READY), envoyes, "un appel sans raison ne prend pas l'avis");
    }
}
