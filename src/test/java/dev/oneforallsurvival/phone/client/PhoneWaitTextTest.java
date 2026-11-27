package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.client.web.WebScreen.Rect;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.client.PhoneWaitText.Form;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * La durée d'un blocage à l'écran : la règle de {@link PhoneWaitText}, la même dans la page ({@code phone.js}), et un
 * décompte qui se lit dans l'écran de repli jusqu'à la plus petite interface.
 */
class PhoneWaitTextTest {

    private static final int LH = 9;

    private static Path ressources() {
        String racine = System.getProperty("oneforallsurvival.mainResources");
        assertNotNull(racine, "propriété oneforallsurvival.mainResources absente");
        return Paths.get(racine).resolve("assets/oneforallsurvival");
    }

    private static JsonObject langue(String code) throws IOException {
        return JsonParser.parseString(Files.readString(ressources().resolve("lang/" + code + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String page() throws IOException {
        return Files.readString(ressources().resolve("html/phone/phone.js"), StandardCharsets.UTF_8);
    }

    private static String duree(JsonObject lang, long secondes) {
        PhoneWaitText.Wait wait = PhoneWaitText.of(secondes);
        return String.format(lang.get(wait.form().key()).getAsString(), wait.arguments());
    }

    @ParameterizedTest(name = "{0} s : {1}")
    @CsvSource({"1, 1 s", "59, 59 s", "60, 60 s", "119, 119 s", "120, 2 min", "121, 2 min 1 s", "180, 3 min",
            "1920, 32 min", "3599, 59 min 59 s", "3600, 60 min", "86400, 1440 min", "604799, 10079 min 59 s",
            "604800, 10080 min"})
    void enSecondesPuisEnMinutes(long secondes, String attendu) throws IOException {
        assertEquals(attendu, duree(langue("fr_fr"), secondes));
        assertEquals(attendu, duree(langue("en_us"), secondes), "mêmes unités en anglais");
    }

    @Test
    void lesFormesOntLeursCles() {
        assertEquals(new PhoneWaitText.Wait(Form.SECONDS, 119, 0), PhoneWaitText.of(119));
        assertEquals(new PhoneWaitText.Wait(Form.MINUTES, 2, 0), PhoneWaitText.of(120));
        assertEquals(new PhoneWaitText.Wait(Form.MINUTES_SECONDS, 2, 1), PhoneWaitText.of(121));
        assertEquals("screen.oneforallsurvival.phone.wait_s", Form.SECONDS.key());
        assertEquals("screen.oneforallsurvival.phone.wait_min", Form.MINUTES.key());
        assertEquals("screen.oneforallsurvival.phone.wait_min_s", Form.MINUTES_SECONDS.key());
    }

    /** La page écrit les mêmes textes que le jeu, au même seuil : ses lignes sont relues telles qu'elles sont écrites. */
    @Test
    void laPageSuitLaMemeRegle() throws IOException {
        String js = page();
        assertTrue(js.contains("var WAIT_MINUTES_FROM = 120;"), "seuil de la page");
        Matcher bloc = Pattern.compile("var WAIT_TEXTS = \\{(.*?)\\n  \\};", Pattern.DOTALL).matcher(js);
        assertTrue(bloc.find(), "table WAIT_TEXTS absente de phone.js");
        Map<String, String> table = new LinkedHashMap<>();
        Matcher e = Pattern.compile("([A-Z_]+): '([^']*)'").matcher(bloc.group(1));
        while (e.find()) {
            table.put(e.group(1), e.group(2));
        }
        JsonObject fr = langue("fr_fr");
        assertEquals(3, table.size(), "WAIT_TEXTS : " + table);
        assertEquals(fr.get(Form.SECONDS.key()).getAsString(), table.get("SECONDS").replace("{s}", "%s"));
        assertEquals(fr.get(Form.MINUTES.key()).getAsString(), table.get("MINUTES").replace("{m}", "%s"));
        assertEquals(fr.get(Form.MINUTES_SECONDS.key()).getAsString(),
                table.get("MINUTES_SECONDS").replace("{m}", "%s").replace("{s}", "%s"));
        assertEquals("Réessayez dans %s", fr.get("screen.oneforallsurvival.phone.retry_in").getAsString());
        assertTrue(js.contains("'Réessayez dans ' + waitText(s)"), "le décompte de la page passe par waitText");
    }

    /** La page ne rabote pas un blocage long : sa borne est le plus haut réglage possible du serveur (sept jours). */
    @Test
    void laPageAccepteLePlusLongBlocage() throws IOException {
        assertEquals(604_800, PhoneConfig.LOCKOUT_MAX_LIMIT);
        assertTrue(page().contains("lockoutSeconds: num(v.lockoutSeconds, 0, 604800, 0)"),
                "borne de lockoutSeconds dans phone.js");
    }

    /**
     * L'écran de repli : le décompte tient sur deux lignes au plus, sans « … », jamais sous le plancher, la durée
     * entière sur une même ligne, et le texte du blocage s'arrête avant le clavier.
     */
    @ParameterizedTest(name = "interface {0} x {1}")
    @CsvSource({"320, 240", "427, 240", "480, 270", "640, 328", "640, 360", "960, 540", "1920, 1080", "320, 480"})
    void leDecompteSeLitDansLeRepli(int w, int h) throws IOException {
        Rect zone = PhoneFallbackLayout.inner(PhoneFrameLayout.fit(w, h), LH);
        for (String code : new String[] {"fr_fr", "en_us"}) {
            JsonObject lang = langue(code);
            for (long secondes : new long[] {60, 119, 120, 1920, 3599, 3600, 604799}) {
                String duree = duree(lang, secondes);
                String phrase = String.format(lang.get("screen.oneforallsurvival.phone.retry_in").getAsString(), duree);
                PhoneFallbackLayout.Wrapped r = PhoneFallbackLayout.retryLines(phrase, duree, zone.width(),
                        FontEstimate::width);
                String quoi = "« " + phrase + " » à " + w + " x " + h + " : " + r;
                assertFalse(r.cut(), quoi);
                assertTrue(r.lines().size() <= PhoneFallbackLayout.MAX_TEXT_LINES, quoi);
                assertTrue(r.scale() >= PhoneFallbackLayout.MIN_TEXT_SCALE, quoi);
                assertEquals(phrase, String.join(" ", r.lines()), quoi);
                assertTrue(r.lines().stream().anyMatch(l -> l.contains(duree)), "durée coupée : " + quoi);
                for (String line : r.lines()) {
                    assertTrue(FontEstimate.width(line) * r.scale() <= zone.width() + 0.01, quoi);
                }
            }
        }
        PhoneFallbackLayout.CodeText texte = PhoneFallbackLayout.codeText(zone, LH, PhoneFallbackLayout.LOCKOUT_LINES);
        assertTrue(PhoneFallbackLayout.keypad(zone).get(0).y() >= texte.bottom(), "clavier sur le texte du blocage");
    }

    /** Une phrase d'une ligne le reste ; trop large, elle coupe devant la durée, jamais dedans. */
    @Test
    void laDureeNEstPasCoupee() {
        PhoneFallbackLayout.Wrapped large = PhoneFallbackLayout.retryLines("Réessayez dans 59 min 59 s",
                "59 min 59 s", 200, FontEstimate::width);
        assertEquals(java.util.List.of("Réessayez dans 59 min 59 s"), large.lines());
        // 135 px : sans colle, « Réessayez dans 59 min 59 » puis « s » seul.
        PhoneFallbackLayout.Wrapped etroit = PhoneFallbackLayout.retryLines("Réessayez dans 59 min 59 s",
                "59 min 59 s", 135, FontEstimate::width);
        assertEquals(java.util.List.of("Réessayez dans", "59 min 59 s"), etroit.lines());
        assertEquals(1f, etroit.scale());
    }
}
