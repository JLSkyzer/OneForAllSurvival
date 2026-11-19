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
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Garde des pages locales : minuscules, CSP hors commentaire, aucune adresse externe, pages du socle présentes. */
class WebResourcesTest {

    static final String CSP =
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src mod: 'unsafe-inline'; img-src mod: data:; connect-src 'none'\">";

    /** Seules ces extensions sont lues comme du texte ; les autres fichiers (images…) ne le sont pas. */
    private static final Set<String> TEXTE = Set.of("html", "js", "css", "json", "txt");

    private static Path dossier() {
        String racine = System.getProperty("oneforallsurvival.mainResources");
        assertNotNull(racine, "propriété oneforallsurvival.mainResources absente");
        return Paths.get(racine).resolve("assets/oneforallsurvival/html");
    }

    private static List<Path> fichiers() throws IOException {
        Path d = dossier();
        assertTrue(Files.isDirectory(d), "dossier des pages absent : " + d);
        try (Stream<Path> s = Files.walk(d)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    private static String extension(Path f) {
        String nom = f.getFileName().toString();
        int point = nom.lastIndexOf('.');
        return point < 0 ? "" : nom.substring(point + 1).toLowerCase(Locale.ROOT);
    }

    private static List<Path> fichiersTexte() throws IOException {
        return fichiers().stream().filter(f -> TEXTE.contains(extension(f))).toList();
    }

    /** Le texte sans ses commentaires HTML ({@code <!-- … -->}, un commentaire non fermé court jusqu'à la fin). */
    static String horsCommentaires(String html) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < html.length()) {
            int debut = html.indexOf("<!--", i);
            if (debut < 0) {
                sb.append(html, i, html.length());
                break;
            }
            sb.append(html, i, debut);
            int fin = html.indexOf("-->", debut + 4);
            i = fin < 0 ? html.length() : fin + 3;
        }
        return sb.toString();
    }

    @Test
    void lesPagesDuSocleExistent() {
        Path d = dossier();
        for (String page : new String[] {"web/bridge.js", "web/selftest.html"}) {
            assertTrue(Files.isRegularFile(d.resolve(page)), "page du socle absente : " + page);
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
        for (Path f : fichiersTexte()) {
            if (extension(f).equals("html")) {
                String texte = horsCommentaires(Files.readString(f, StandardCharsets.UTF_8));
                assertTrue(texte.contains(CSP), "CSP absente, altérée ou en commentaire : " + f);
            }
        }
    }

    @Test
    void uneCspEnCommentaireNeComptePas() {
        assertFalse(horsCommentaires("<head><!-- " + CSP + " --></head>").contains(CSP));
        assertFalse(horsCommentaires("<head><!-- " + CSP).contains(CSP));
        assertTrue(horsCommentaires("<!-- x -->" + CSP + "<!-- y -->").contains(CSP));
    }

    @Test
    void aucuneAdresseExterne() throws IOException {
        for (Path f : fichiersTexte()) {
            String texte = Files.readString(f, StandardCharsets.UTF_8);
            assertFalse(texte.contains("http://") || texte.contains("https://"), "adresse externe : " + f);
        }
    }
}
