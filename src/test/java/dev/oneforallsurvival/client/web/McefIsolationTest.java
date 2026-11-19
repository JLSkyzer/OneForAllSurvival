package dev.oneforallsurvival.client.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Garde d'isolement de MCEF : lit le pool de constantes de chaque classe de {@code main}, sans la
 * charger (MCEF est facultatif et absent du classpath de test).
 *
 * <ul>
 *   <li>{@code com/cinemamod/} et {@code org/cef/} ne sont nommés que dans {@code client/web/mcef/} ;
 *   <li>{@code client/web/mcef/} n'est nommé que par des classes d'un paquet {@code .../client/...}.
 * </ul>
 */
class McefIsolationTest {

    private static final String PAQUET_MCEF = "dev/oneforallsurvival/client/web/mcef/";
    private static final String[] BIBLIOTHEQUES = {"com/cinemamod/", "org/cef/"};

    /** Classe lue : son chemin relatif (avec des barres obliques) et les textes de son pool. */
    private record Classe(String chemin, List<String> textes) {}

    @Test
    void seulLePaquetMcefNommeMcef() throws IOException {
        List<String> fautives = new ArrayList<>();
        for (Classe c : lire()) {
            if (c.chemin().startsWith(PAQUET_MCEF)) {
                continue;
            }
            for (String t : c.textes()) {
                if (nomme(t, BIBLIOTHEQUES)) {
                    fautives.add(c.chemin() + " nomme " + t);
                    break;
                }
            }
        }
        assertTrue(fautives.isEmpty(), "classes hors de client/web/mcef qui nomment MCEF ou CEF : " + fautives);
    }

    @Test
    void rienHorsDuClientNeNommeLePaquetMcef() throws IOException {
        List<String> fautives = new ArrayList<>();
        for (Classe c : lire()) {
            if (c.chemin().startsWith(PAQUET_MCEF) || ("/" + c.chemin()).contains("/client/")) {
                continue;
            }
            for (String t : c.textes()) {
                if (nomme(t, PAQUET_MCEF)) {
                    fautives.add(c.chemin() + " nomme " + t);
                    break;
                }
            }
        }
        assertTrue(fautives.isEmpty(), "classes hors d'un paquet client qui nomment client/web/mcef : " + fautives);
    }

    @Test
    void leDossierEstLu() throws IOException {
        int n = lire().size();
        assertTrue(n >= 500, "au moins 500 classes lues, trouvé " + n);
    }

    /** Un texte du pool nomme le paquet s'il le contient (nom interne, descripteur {@code L...;} ou signature). */
    private static boolean nomme(String texte, String... paquets) {
        for (String p : paquets) {
            if (texte.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static List<Classe> lire() throws IOException {
        String dossiers = System.getProperty("oneforallsurvival.mainClasses");
        assertTrue(dossiers != null && !dossiers.isBlank(), "propriété oneforallsurvival.mainClasses absente");
        List<Classe> classes = new ArrayList<>();
        for (String d : dossiers.split(java.io.File.pathSeparator)) {
            Path racine = Paths.get(d);
            if (!Files.isDirectory(racine)) {
                continue;
            }
            List<Path> fichiers;
            try (Stream<Path> s = Files.walk(racine)) {
                fichiers = s.filter(p -> p.toString().endsWith(".class")).toList();
            }
            for (Path f : fichiers) {
                String chemin = racine.relativize(f).toString().replace(java.io.File.separatorChar, '/');
                classes.add(new Classe(chemin, textesDuPool(Files.readAllBytes(f))));
            }
        }
        return classes;
    }

    /** Lit le pool de constantes (JVMS 4.4) et rend le texte de chaque entrée Utf8. */
    private static List<String> textesDuPool(byte[] octets) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(octets));
        in.readInt(); // magic
        in.readUnsignedShort();
        in.readUnsignedShort();
        int n = in.readUnsignedShort();
        List<String> textes = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> {
                    int len = in.readUnsignedShort();
                    byte[] b = new byte[len];
                    in.readFully(b);
                    textes.add(new String(b, StandardCharsets.UTF_8));
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                case 5, 6 -> {
                    in.skipBytes(8);
                    i++; // Long et Double prennent deux entrées
                }
                case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                case 15 -> in.skipBytes(3);
                default -> throw new IOException("étiquette de pool inconnue : " + tag);
            }
        }
        return textes;
    }
}
