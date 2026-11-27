package dev.oneforallsurvival.phone.voice;

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
 * Garde d'isolement de Simple Voice Chat : lit le pool de constantes de chaque classe de {@code main}, sans la charger.
 *
 * <ul>
 *   <li>{@code de/maxhenkel/} n'est nommé que dans le paquet du greffon, {@code phone/voice/svc/} ;
 *   <li>ce paquet n'est nommé par aucune autre classe (Simple Voice Chat trouve le greffon par son annotation).
 * </ul>
 */
class VoiceIsolationTest {

    private static final String PAQUET = "dev/oneforallsurvival/phone/voice/svc/";
    private static final String PAQUET_POINTE = "dev.oneforallsurvival.phone.voice.svc.";
    private static final String[] API = {"de/maxhenkel/", "de.maxhenkel."};

    private record Classe(String chemin, List<String> textes) {}

    @Test
    void seulLeGreffonNommeSimpleVoiceChat() throws IOException {
        List<String> fautives = new ArrayList<>();
        for (Classe c : lire()) {
            if (!c.chemin().startsWith(PAQUET) && c.textes().stream().anyMatch(t -> nomme(t, API))) {
                fautives.add(c.chemin());
            }
        }
        assertTrue(fautives.isEmpty(), "classes hors de phone/voice/svc qui nomment Simple Voice Chat : " + fautives);
    }

    @Test
    void personneNeNommeLeGreffon() throws IOException {
        List<String> fautives = new ArrayList<>();
        for (Classe c : lire()) {
            if (!c.chemin().startsWith(PAQUET)
                    && c.textes().stream().anyMatch(t -> nomme(t, PAQUET, PAQUET_POINTE))) {
                fautives.add(c.chemin());
            }
        }
        assertTrue(fautives.isEmpty(), "classes hors du greffon qui le nomment : " + fautives);
    }

    @Test
    void leGreffonEstLuEtNommeSimpleVoiceChat() throws IOException {
        List<Classe> greffon = lire().stream().filter(c -> c.chemin().startsWith(PAQUET)).toList();
        assertTrue(!greffon.isEmpty(), "aucune classe de phone/voice/svc lue");
        assertTrue(greffon.stream().anyMatch(c -> c.textes().stream().anyMatch(t -> nomme(t, API))),
                "le greffon ne nomme pas Simple Voice Chat : la lecture du pool est cassée");
    }

    /**
     * Le vrai puits garde ce qui fait la voix d'un appel (relecture de la tâche 5) : le greffon n'annule jamais une
     * trame du micro (la proximité reste), le canal privé vise son auditeur et passe l'isolement d'un groupe, le
     * haut-parleur est filtré par l'instantané.
     */
    @Test
    void leVraiPuitsGardeSesAppels() throws IOException {
        List<Classe> greffon = lire().stream().filter(c -> c.chemin().startsWith(PAQUET)).toList();
        List<String> plugin = textesDe(greffon, "CallVoicePlugin");
        List<String> puits = textesDe(greffon, "SvcSink");
        assertTrue(plugin.stream().noneMatch(t -> t.contains("cancel")), "le greffon nomme cancel");
        for (String m : new String[] {"setFilter", "addTarget", "setBypassGroupIsolation"}) {
            assertTrue(puits.contains(m), "SvcSink ne nomme plus " + m);
        }
    }

    /** Les textes du pool de la classe {@code nom} et de ses classes internes. */
    private static List<String> textesDe(List<Classe> classes, String nom) {
        List<String> textes = new ArrayList<>();
        for (Classe c : classes) {
            String simple = c.chemin().substring(PAQUET.length());
            if (simple.equals(nom + ".class") || simple.startsWith(nom + "$")) {
                textes.addAll(c.textes());
            }
        }
        assertTrue(!textes.isEmpty(), "classe " + nom + " non lue");
        return textes;
    }
    private static boolean nomme(String texte, String... motifs) {
        for (String m : motifs) {
            if (texte.contains(m)) {
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
        assertTrue(classes.size() >= 500, "au moins 500 classes lues, trouvé " + classes.size());
        return classes;
    }

    /** Lit le pool de constantes (JVMS 4.4) et rend le texte de chaque entrée Utf8. */
    private static List<String> textesDuPool(byte[] octets) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(octets));
        in.readInt();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int n = in.readUnsignedShort();
        List<String> textes = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> {
                    byte[] b = new byte[in.readUnsignedShort()];
                    in.readFully(b);
                    textes.add(new String(b, StandardCharsets.UTF_8));
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                case 5, 6 -> {
                    in.skipBytes(8);
                    i++;
                }
                case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                case 15 -> in.skipBytes(3);
                default -> throw new IOException("étiquette de pool inconnue : " + tag);
            }
        }
        return textes;
    }
}
