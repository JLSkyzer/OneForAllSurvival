package dev.oneforallsurvival.client.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebPagesTest {

    @Test
    void urlAssembleLaBaseEtLeChemin() {
        assertEquals("mod://oneforallsurvival/phone/index.html", WebPages.url("phone/index.html"));
    }

    @Test
    void urlRefuseLesCheminsDangereux() {
        for (String mauvais : new String[] {"", "Phone/x.html", "../x", "a/../x", "/x", "a\\b", "http:x", "a:b"}) {
            assertThrows(IllegalArgumentException.class, () -> WebPages.url(mauvais), mauvais);
        }
        assertThrows(IllegalArgumentException.class, () -> WebPages.url(null));
    }

    @Test
    void adressesPermises() {
        assertTrue(WebPages.isAllowed("about:blank"));
        assertTrue(WebPages.isAllowed(WebPages.url("web/selftest.html")));
    }

    @Test
    void adressesRefusees() {
        assertFalse(WebPages.isAllowed(null));
        assertFalse(WebPages.isAllowed("https://www.google.com"));
        assertFalse(WebPages.isAllowed("mod://webdisplays/x.html"));
        assertFalse(WebPages.isAllowed("mod://oneforallsurvival/../x"));
        assertFalse(WebPages.isAllowed("mod://oneforallsurvival/a/../x"));
        assertFalse(WebPages.isAllowed("file:///C:/x"));
        assertFalse(WebPages.isAllowed("about:blank#x"));
    }

    @Test
    void cheminDeRessource() {
        assertEquals("/assets/oneforallsurvival/html/phone/index.html", WebPages.resourcePath("phone/index.html"));
    }
}
