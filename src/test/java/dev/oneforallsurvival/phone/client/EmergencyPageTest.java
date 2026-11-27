package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Les urgences dans la page (feature 95) : le bouton « Urgence » de l'écran verrouillé, ses deux numéros, les titres
 * de l'appel, en français et égaux à ceux des messages du serveur ; le détail passé à la page.
 */
class EmergencyPageTest {

    private static Path ressources() {
        String racine = System.getProperty("oneforallsurvival.mainResources");
        assertNotNull(racine, "propriété oneforallsurvival.mainResources absente");
        return Paths.get(racine).resolve("assets/oneforallsurvival");
    }

    private static String lire(String chemin) throws IOException {
        return Files.readString(ressources().resolve(chemin), StandardCharsets.UTF_8);
    }

    @Test
    void lEcranVerrouilleALeBoutonEtSesDeuxNumeros() throws IOException {
        String html = lire("html/phone/index.html");
        int lock = html.indexOf("<section id=\"lock\"");
        int fin = html.indexOf("</section>", lock);
        assertTrue(lock >= 0 && fin > lock, "écran verrouillé absent");
        String ecran = html.substring(lock, fin);
        assertTrue(ecran.contains("id=\"lock-sos\">Urgence<"), "bouton « Urgence »");
        assertTrue(ecran.contains("id=\"sos-17\">Police 17<"), "« Police 17 »");
        assertTrue(ecran.contains("id=\"sos-15\">Secours 15<"), "« Secours 15 »");
        String js = lire("html/phone/phone.js");
        for (String id : new String[] {"lock-sos", "sos-17", "sos-15", "sos-cancel"}) {
            assertTrue(js.contains("add('" + id + "')"), id + " : zone de toucher déclarée");
        }
        assertTrue(js.contains("dial(t.closest('#sos-17') ? '17' : '15')"), "les deux boutons composent");
    }

    @Test
    void lesTitresDeLaPageSontCeuxDuServeur() throws IOException {
        String js = lire("html/phone/phone.js");
        JsonObject fr = JsonParser.parseString(lire("lang/fr_fr.json")).getAsJsonObject();
        String[][] services = {{"17", "Police 17", "police"}, {"15", "Secours 15", "medical"}};
        for (String[] s : services) {
            Matcher m = Pattern.compile("'" + s[0] + "': \\{ title: '([^']*)', incoming: '([^']*)' \\}").matcher(js);
            assertTrue(m.find(), "titres du " + s[0] + " absents de phone.js");
            assertEquals(s[1], m.group(1));
            assertEquals(fr.get("message.oneforallsurvival.call.emergency." + s[2]).getAsString(), m.group(2),
                    "le titre côté secours est le texte du serveur");
        }
        JsonObject en = JsonParser.parseString(lire("lang/en_us.json")).getAsJsonObject();
        for (String cle : fr.keySet()) {
            if (cle.contains("emergency") || cle.contains("urgences") || cle.endsWith("hang_up_first")) {
                assertTrue(en.has(cle), "clé absente de en_us : " + cle);
            }
        }
    }

    @Test
    void leDetailPasseALaPageEtSAnnuleAilleurs() {
        PhoneCallStatePacket ici = new PhoneCallStatePacket(Phase.INCOMING, 1_000_000_000, "17", "Bob", -1L, false,
                false, true, null, null, "Bob · 1 2 3 · overworld");
        assertEquals("Bob · 1 2 3 · overworld", PhoneCallJson.call(ici, 0L, null).get("detail").getAsString());
        PhoneCallStatePacket ailleurs = new PhoneCallStatePacket(Phase.INCOMING, 1_000_000_000, "17", "Bob", -1L, false,
                false, false, null, null, "Bob · 1 2 3 · overworld");
        assertEquals("", PhoneCallJson.call(ailleurs, 0L, null).get("detail").getAsString(),
                "un appel porté ailleurs ne montre rien, position comprise");
    }
}
