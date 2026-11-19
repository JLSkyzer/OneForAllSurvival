package dev.oneforallsurvival.client.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Garde des pages locales : minuscules, CSP, aucune adresse externe. Tolère l'absence du dossier. */
class WebResourcesTest {

    private static final String CSP =
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src mod: 'unsafe-inline'; img-src mod: data:; connect-src 'none'\">";

    private static Path dossier() {
        String racine = System.getProperty("oneforallsurvival.mainResources");
        assertNotNull(racine, "propriété oneforallsurvival.mainResources absente");
        return Paths.get(racine).resolve("assets/oneforallsurvival/html");
    }

    private static List<Path> fichiers() throws IOException {
        Path d = dossier();
        if (!Files.isDirectory(d)) {
            return List.of(); // dossier absent toléré : rien à lire tant que html/ n'existe pas
        }
        try (Stream<Path> s = Files.walk(d)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void cheminsEnMinuscules() throws IOException {
        Path d = dossier();
        for (Path f : fichiers()) {
            String rel = d.relativize(f).toString().replace('\\', '/');
            assertTrue(rel.equals(rel.toLowerCase(Locale.ROOT)), "chemin non minuscule : " + rel);
        }
    }

    @Test
    void chaquePageHtmlPorteLaCsp() throws IOException {
        for (Path f : fichiers()) {
            if (f.toString().endsWith(".html")) {
                String texte = Files.readString(f, StandardCharsets.UTF_8);
                assertTrue(texte.contains(CSP), "CSP absente ou altérée : " + f);
            }
        }
    }

    @Test
    void aucuneAdresseExterne() throws IOException {
        for (Path f : fichiers()) {
            String texte = Files.readString(f, StandardCharsets.UTF_8);
            assertFalse(texte.contains("http://") || texte.contains("https://"), "adresse externe : " + f);
        }
    }
}
