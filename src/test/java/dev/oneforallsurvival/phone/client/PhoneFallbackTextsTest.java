package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.client.web.WebScreen.Rect;
import dev.oneforallsurvival.phone.client.PhoneStatusText.Shown;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Chaque texte fixe français de l'écran de repli (lot T2, et les Réglages qu'il partage avec le lot T1) se lit sans
 * effort de 320 × 240 à 1920 × 1080, sans lancer le jeu : un bouton tient sur sa ligne au plancher 0,75 sans « … », un
 * message tient sur deux lignes au plus sans « … », un texte d'état de la barre d'état tient sans « … » (l'heure et des
 * barres vides pouvant céder leur place). Largeurs : {@link FontEstimate}, borne haute de la police du jeu (l'essai
 * réel compare chacune à la largeur réelle).
 */
class PhoneFallbackTextsTest {

    private static final int LH = 9;

    private static JsonObject fr() throws IOException {
        String racine = System.getProperty("oneforallsurvival.mainResources");
        assertNotNull(racine, "propriété oneforallsurvival.mainResources absente");
        return JsonParser.parseString(Files.readString(Paths.get(racine).resolve(
                "assets/oneforallsurvival/lang/fr_fr.json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String t(JsonObject fr, String cle, Object... args) {
        assertTrue(fr.has(cle), "clé absente : " + cle);
        return String.format(fr.get(cle).getAsString(), args);
    }

    private static String k(String nom) {
        return "screen.oneforallsurvival.phone." + nom;
    }

    /** Les libellés de boutons : Réglages, accueil et feuille « Carte SIM ». */
    static List<String> boutons(JsonObject fr) {
        List<String> l = new ArrayList<>();
        for (String nom : List.of("settings", "antenna_app", "sim_remove", "sim_request", "no_choices",
                "no_requestable", "back", "change_code", "lock", "erase", "erase_confirm")) {
            l.add(t(fr, k(nom)));
        }
        for (String oui : List.of("on", "off")) {
            l.add(t(fr, k("airplane"), t(fr, k(oui))));
        }
        for (String fond : List.of("aube", "lagon", "jungle", "nuit")) {
            l.add(t(fr, k("wallpaper"), t(fr, k("wallpaper." + fond))));
        }
        // La ligne « Carte SIM » des Réglages : le numéro, ou l'état de la carte.
        l.add(t(fr, k("sim"), "06 12 34 56 78"));
        for (Shown s : List.of(Shown.NO_CARD, Shown.UNRECOGNIZED, Shown.INACTIVE)) {
            l.add(t(fr, s.key()));
        }
        return l;
    }

    /** Les messages de la feuille « Carte SIM » et de l'accueil (issues des gestes), et l'abonnement. */
    static List<String> messages(JsonObject fr) {
        List<String> l = new ArrayList<>();
        for (String nom : List.of("sim_inserted", "sim_removed", "sim_unknown", "inventory_full", "sim_requested",
                "already_requested", "no_network", "requests_full", "not_owner", "plan", "face_unlocked",
                "face_not_recognized", "face_id_on", "face_id_off")) {
            l.add(t(fr, k(nom)));
        }
        return l;
    }

    private static Rect zone(int w, int h) {
        return PhoneFallbackLayout.inner(PhoneFrameLayout.fit(w, h), LH);
    }

    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void chaqueBoutonSeLitSansCoupe(int w, int h) throws IOException {
        Rect z = zone(w, h);
        for (String b : boutons(fr())) {
            PhoneFallbackLayout.StatusLine l = PhoneFallbackLayout.button(b, z.width(), FontEstimate::width);
            assertFalse(l.cut(), "« " + b + " » coupé à " + w + " x " + h + " (" + FontEstimate.width(b) + " px pour "
                    + PhoneFallbackLayout.textRoom(z.width()) + ")");
            assertTrue(l.scale() >= PhoneFallbackLayout.MIN_TEXT_SCALE, b);
        }
    }

    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void chaqueMessageTientSurDeuxLignes(int w, int h) throws IOException {
        Rect z = zone(w, h);
        for (String m : messages(fr())) {
            PhoneFallbackLayout.Wrapped r = PhoneFallbackLayout.wrap(m, z.width(), FontEstimate::width,
                    PhoneFallbackLayout.MAX_TEXT_LINES);
            assertFalse(r.cut(), "« " + m + " » coupé à " + w + " x " + h + " : " + r);
            assertTrue(r.lines().size() <= PhoneFallbackLayout.MAX_TEXT_LINES, m);
            assertTrue(r.scale() >= PhoneFallbackLayout.MIN_TEXT_SCALE, m);
            for (String line : r.lines()) {
                assertTrue(FontEstimate.width(line) * r.scale() <= z.width() + 0.01, line);
            }
        }
    }

    /**
     * La barre d'état : chaque texte d'état tient sans « … » avec la batterie la plus large (« 100% », « ✈ 100% » en
     * mode avion) ; avec des barres pour une carte active, vides et donc facultatives à « Pas de service ».
     */
    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void chaqueTexteDEtatTientDansLaBarre(int w, int h) throws IOException {
        Rect z = zone(w, h);
        JsonObject fr = fr();
        for (Shown s : Shown.values()) {
            if (s.key() == null || s == Shown.NO_CARD) {
                continue;
            }
            String batterie = s == Shown.AIRPLANE ? "✈ 100%" : "100%";
            int place = PhoneFallbackLayout.statusRoom(z.width(), FontEstimate.width(batterie));
            int barres = s == Shown.NO_SERVICE ? PhoneFallbackLayout.BARS_WIDTH : 0;
            PhoneFallbackLayout.Status st = PhoneFallbackLayout.status("21:57  ", t(fr, s.key()), barres,
                    s == Shown.NO_SERVICE, place, FontEstimate::width);
            assertFalse(st.line().cut(), s + " coupé à " + w + " x " + h + " : " + st);
        }
    }

    /**
     * Lot T3, tâche 7 : chaque libellé des appels tient sans « … » dans son bouton (demi-largeur, touche du clavier,
     * pleine largeur) ; la ligne d'état « Appel entrant » ou « Appel… »
     * tient sur une ligne.
     */
    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void lesLibellesDesAppelsTiennent(int w, int h) throws IOException {
        Rect z = zone(w, h);
        JsonObject fr = fr();
        PhoneFallbackLayout.CallScreen c = PhoneFallbackLayout.callScreen(z, LH);
        PhoneFallbackLayout.Dial d = PhoneFallbackLayout.dial(z, LH);
        String[][] boutons = {
            {"decline", String.valueOf(c.decline().width())}, {"answer", String.valueOf(c.answer().width())},
            {"hangup", String.valueOf(c.hangup().width())},
            {"dial", String.valueOf(d.call().width())}, {"sos_police", String.valueOf(d.police().width())},
            {"sos_medical", String.valueOf(d.medical().width())}, {"app", String.valueOf(z.width())},
            {"sos", String.valueOf(PhoneFallbackLayout.lockSos(z, LH).width())},
            {"cancel", String.valueOf(z.width())}};
        List<String[]> tous = new ArrayList<>(List.of(boutons));
        tous.add(new String[] {"mute", String.valueOf(c.mute().width())});
        tous.add(new String[] {"speaker", String.valueOf(c.speaker().width())});
        tous.add(new String[] {"back", String.valueOf(d.back().width())});
        for (String[] b : tous) {
            String texte = t(fr, b[0].equals("back") ? k("back") : PhoneFallbackCall.KEY + b[0]);
            PhoneFallbackLayout.StatusLine l = PhoneFallbackLayout.button(texte, Integer.parseInt(b[1]),
                    FontEstimate::width);
            assertFalse(l.cut(), "« " + texte + " » coupé à " + w + " x " + h);
        }
        for (String s : List.of("incoming", "outgoing")) {
            PhoneFallbackLayout.Wrapped r = PhoneFallbackLayout.wrap(t(fr, PhoneFallbackCall.KEY + s), z.width(),
                    FontEstimate::width, 1);
            assertFalse(r.cut(), s);
        }
        for (String fin : List.of("message.oneforallsurvival.call.ended.party_left",
                "message.oneforallsurvival.call.emergency.no_answer", "message.oneforallsurvival.call.hang_up_first")) {
            PhoneFallbackLayout.Wrapped r = PhoneFallbackLayout.wrap(t(fr, fin), z.width(), FontEstimate::width,
                    PhoneFallbackLayout.MAX_TEXT_LINES);
            assertFalse(r.cut(), fin + " à " + w + " x " + h);
        }
    }

    /** Les mots des appels du repli sont ceux de la page (index.html et phone.js), en français et en anglais. */
    @org.junit.jupiter.api.Test
    void lesTextesDesAppelsSontCeuxDeLaPage() throws IOException {
        JsonObject fr = fr();
        String racine = System.getProperty("oneforallsurvival.mainResources");
        String html = Files.readString(Paths.get(racine).resolve("assets/oneforallsurvival/html/phone/index.html"),
                StandardCharsets.UTF_8);
        String js = Files.readString(Paths.get(racine).resolve("assets/oneforallsurvival/html/phone/phone.js"),
                StandardCharsets.UTF_8);
        for (String nom : List.of("answer", "decline", "mute", "speaker", "sos", "sos_police", "sos_medical", "cancel",
                "app")) {
            String mot = t(fr, PhoneFallbackCall.KEY + nom);
            assertTrue(html.contains(">" + mot + "<"), "« " + mot + " » n'est pas un mot de la page");
        }
        assertTrue(js.contains("INCOMING: '" + t(fr, PhoneFallbackCall.KEY + "incoming") + "'"), "Appel entrant");
        assertTrue(js.contains("OUTGOING: '" + t(fr, PhoneFallbackCall.KEY + "outgoing") + "'"), "Appel…");
        JsonObject en = JsonParser.parseString(Files.readString(Paths.get(racine).resolve(
                "assets/oneforallsurvival/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (String cle : fr.keySet()) {
            if (cle.startsWith(PhoneFallbackCall.KEY)) {
                assertTrue(en.has(cle), "clé absente de en_us : " + cle);
            }
        }
    }
}
