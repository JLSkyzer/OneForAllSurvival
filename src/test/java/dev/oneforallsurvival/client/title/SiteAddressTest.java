package dev.oneforallsurvival.client.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SiteAddressTest {
    @Test
    void realReplyIsParsed() {
        var r = SiteAddress.parse("{\"adresse\":\"play.lostisland.fr\",\"maintenance\":false,\"message\":null}");
        assertEquals("play.lostisland.fr", r.address());
        assertFalse(r.maintenance());
        assertNull(r.message());
    }

    @Test
    void maintenanceKeepsPlainTextMessage() {
        var r = SiteAddress.parse("{\"adresse\":\"1.2.3.4:25566\",\"maintenance\":true,\"message\":\"  Retour\\n\\u00a7cdemain <b>  \"}");
        assertTrue(r.maintenance());
        assertEquals("Retour §cdemain <b>", r.message()); // pas d'interprétation, seulement nettoyé
    }

    @Test
    void longMessageIsTruncated() {
        var r = SiteAddress.parse("{\"adresse\":\"a.fr\",\"maintenance\":true,\"message\":\"" + "x".repeat(500) + "\"}");
        assertEquals(SiteAddress.MAX_MESSAGE, r.message().length());
        assertTrue(r.message().endsWith("…"));
    }

    @Test
    void garbageIsIgnored() {
        assertNull(SiteAddress.parse(null));
        assertNull(SiteAddress.parse(""));
        assertNull(SiteAddress.parse("<html>"));
        assertNull(SiteAddress.parse("[1,2]"));
        assertNull(SiteAddress.parse("{\"adresse\":\"a.fr\",\"pad\":\"" + "x".repeat(5000) + "\"}"));
        assertNull(SiteAddress.parse("{\"adresse\":42}").address());
        assertFalse(SiteAddress.parse("{\"adresse\":\"a.fr\",\"maintenance\":\"true\"}").maintenance());
    }

    @Test
    void hostValidation() {
        for (String ok : new String[] {"play.lostisland.fr", "localhost", "play.fr:25565", "10.0.0.1", "10.0.0.1:1",
                "a-b.example.com:65535", " play.fr "}) {
            assertEquals(ok.trim(), SiteAddress.valid(ok), ok);
        }
        for (String bad : new String[] {"", "  ", "a b.fr", "play.fr:0", "play.fr:65536", "play.fr:", "play.fr:12a",
                "play.fr:123456", ":25565", "-a.fr", "a-.fr", "a..fr", "999.1.1.1", "1.2.3", "[::1]:25565",
                "http://play.fr", "play.fr/x", "pléay.fr", "a.fr:1:2", "x".repeat(300)}) {
            assertNull(SiteAddress.valid(bad), bad);
        }
        assertNull(SiteAddress.valid(null));
    }

    @Test
    void invalidAddressKeepsMaintenanceButNoAddress() {
        var r = SiteAddress.parse("{\"adresse\":\"pas valide\",\"maintenance\":true,\"message\":\"x\"}");
        assertNull(r.address());
        assertTrue(r.maintenance());
    }

    @Test
    void directThenCacheThenFallback() {
        var live = new SiteAddress.Reply("live.fr", false, null);
        assertEquals("live.fr", SiteAddress.choose(live, "cache.fr", "repli.fr").address());
        var noAddr = new SiteAddress.Reply(null, false, null);
        assertEquals("cache.fr", SiteAddress.choose(noAddr, "cache.fr", "repli.fr").address());
        assertEquals("cache.fr", SiteAddress.choose(null, "cache.fr", "repli.fr").address());
        assertEquals("repli.fr", SiteAddress.choose(null, null, "repli.fr").address());
        assertEquals("repli.fr", SiteAddress.choose(null, "invalide !", "repli.fr").address());
        var none = SiteAddress.choose(null, null, "");
        assertNull(none.address());
        assertFalse(none.joinable());
    }

    @Test
    void maintenanceIsLiveOnly() {
        var live = new SiteAddress.Reply("live.fr", true, "Retour à 20 h");
        var s = SiteAddress.choose(live, "cache.fr", "");
        assertTrue(s.maintenance());
        assertFalse(s.joinable());
        assertEquals("Retour à 20 h", s.message());
        assertEquals("live.fr", s.address());
        var offline = SiteAddress.choose(null, "cache.fr", "");
        assertFalse(offline.maintenance());
        assertTrue(offline.joinable());
        assertNull(offline.message());
    }
}
