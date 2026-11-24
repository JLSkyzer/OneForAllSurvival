package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.phone.client.PhoneStatusText.Shown;
import dev.oneforallsurvival.phone.net.PhoneSims.Validity;
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

/**
 * Le lien entre l'écran de repli et la page : pour chaque état de carte, le même texte. La règle est ici
 * ({@link PhoneStatusText}) ; les textes viennent de {@code fr_fr.json} pour le repli et de la table
 * {@code STATUS_TEXTS} de {@code phone.js} pour la page. Changer un texte d'un seul côté fait rougir
 * {@link #lesTextesDeLaPageSontCeuxDuJeu}.
 */
class PhoneStatusTextTest {

    /** La table de référence : état montré, et son texte en français. */
    private static final Map<Shown, String> FRANCAIS = new LinkedHashMap<>();

    static {
        FRANCAIS.put(Shown.NO_SIM, "Pas de SIM");
        FRANCAIS.put(Shown.UNRECOGNIZED, "SIM non reconnue");
        FRANCAIS.put(Shown.SUSPENDED, "SIM suspendue");
        FRANCAIS.put(Shown.INACTIVE, "Carte SIM inactive");
        FRANCAIS.put(Shown.NO_SERVICE, "Pas de service");
        FRANCAIS.put(Shown.AIRPLANE, "Mode avion");
        FRANCAIS.put(Shown.NO_CARD, "Aucune carte SIM");
    }

    private static Path ressources() {
        String racine = System.getProperty("oneforallsurvival.mainResources");
        assertNotNull(racine, "propriété oneforallsurvival.mainResources absente");
        return Paths.get(racine).resolve("assets/oneforallsurvival");
    }

    private static JsonObject langue(String code) throws IOException {
        return JsonParser.parseString(Files.readString(ressources().resolve("lang/" + code + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** La table {@code var STATUS_TEXTS = { NOM: 'texte', … };} de la page, relue telle qu'elle est écrite. */
    private static Map<String, String> tableDeLaPage() throws IOException {
        String js = Files.readString(ressources().resolve("html/phone/phone.js"), StandardCharsets.UTF_8);
        Matcher bloc = Pattern.compile("var STATUS_TEXTS = \\{(.*?)\\};", Pattern.DOTALL).matcher(js);
        assertTrue(bloc.find(), "table STATUS_TEXTS absente de phone.js");
        Map<String, String> table = new LinkedHashMap<>();
        Matcher e = Pattern.compile("([A-Z_]+): '([^']*)'").matcher(bloc.group(1));
        while (e.find()) {
            table.put(e.group(1), e.group(2));
        }
        return table;
    }

    @Test
    void lesTextesDuJeuSontCeuxDeLaTable() throws IOException {
        JsonObject fr = langue("fr_fr");
        JsonObject en = langue("en_us");
        for (Map.Entry<Shown, String> e : FRANCAIS.entrySet()) {
            String cle = e.getKey().key();
            assertNotNull(cle, e.getKey().name());
            assertTrue(fr.has(cle) && en.has(cle), "clé absente : " + cle);
            assertEquals(e.getValue(), fr.get(cle).getAsString(), e.getKey().name());
        }
    }

    @Test
    void lesTextesDeLaPageSontCeuxDuJeu() throws IOException {
        Map<String, String> page = tableDeLaPage();
        assertEquals(FRANCAIS.size(), page.size(), "STATUS_TEXTS : " + page);
        for (Map.Entry<Shown, String> e : FRANCAIS.entrySet()) {
            assertEquals(e.getValue(), page.get(e.getKey().name()), "page : " + e.getKey().name());
        }
    }

    /**
     * La page suit la même règle de carte : une désactivée y est « Carte SIM inactive » (INACTIVE), jamais « non
     * reconnue » ; la ligne de {@code cardKey} est relue telle qu'elle est écrite.
     */
    @Test
    void laPageNeConfondPasDesactiveeEtNonReconnue() throws IOException {
        String js = Files.readString(ressources().resolve("html/phone/phone.js"), StandardCharsets.UTF_8);
        assertTrue(js.contains("sim === 'DEACTIVATED' ? 'INACTIVE'"), "cardKey : DEACTIVATED doit donner INACTIVE");
        assertTrue(js.contains("sim === 'UNKNOWN' ? 'UNRECOGNIZED'"), "cardKey : UNKNOWN doit donner UNRECOGNIZED");
        assertFalse(js.contains("'Carte non reconnue'"), "ancien texte de la page, déduit d'un numéro vide");
    }

    /** Pour chaque état de carte : ce que la barre d'état montre, déverrouillé puis verrouillé. */
    @Test
    void chaqueEtatDeCarteASonTexte() {
        // Aucune carte.
        assertEquals(Shown.NO_SIM, PhoneStatusText.carrier(Validity.NONE, true, false, 0, ""));
        assertEquals(Shown.NO_SIM, PhoneStatusText.carrier(Validity.NONE, false, false, 0, ""));
        // Non reconnue.
        assertEquals(Shown.UNRECOGNIZED, PhoneStatusText.carrier(Validity.UNKNOWN, true, false, 0, ""));
        assertEquals(Shown.UNRECOGNIZED, PhoneStatusText.carrier(Validity.UNKNOWN, false, false, 0, ""));
        // Active : l'opérateur avec des barres, « Pas de service » sans.
        assertEquals(Shown.OPERATOR, PhoneStatusText.carrier(Validity.ACTIVE, true, false, 3, "Mairie"));
        assertEquals(Shown.NO_SERVICE, PhoneStatusText.carrier(Validity.ACTIVE, true, false, 0, "Mairie"));
        // Présente (verrouillé, ACTIVE grossier) : pareil, rien de plus.
        assertEquals(Shown.OPERATOR, PhoneStatusText.carrier(Validity.ACTIVE, false, false, 4, "Mairie"));
        assertEquals(Shown.NO_SERVICE, PhoneStatusText.carrier(Validity.ACTIVE, false, false, 0, "Mairie"));
        // Suspendue et désactivée : leur texte déverrouillé seulement.
        assertEquals(Shown.SUSPENDED, PhoneStatusText.carrier(Validity.SUSPENDED, true, false, 0, "Mairie"));
        assertEquals(Shown.INACTIVE, PhoneStatusText.carrier(Validity.DEACTIVATED, true, false, 0, ""));
        assertEquals(Shown.NO_SERVICE, PhoneStatusText.carrier(Validity.SUSPENDED, false, false, 0, "Mairie"));
        assertEquals(Shown.NO_SERVICE, PhoneStatusText.carrier(Validity.DEACTIVATED, false, false, 0, ""));
        // Mode avion : déverrouillé seulement (verrouillé, le drapeau n'est pas envoyé).
        assertEquals(Shown.AIRPLANE, PhoneStatusText.carrier(Validity.ACTIVE, true, true, 0, "Mairie"));
    }

    /** Une carte désactivée n'a pas de numéro (pas de ligne) mais n'est pas « non reconnue » (revue, I1). */
    @Test
    void uneCarteDesactiveeNEstPasNonReconnue() {
        assertEquals(Shown.INACTIVE, PhoneStatusText.card(Validity.DEACTIVATED));
        assertEquals(Shown.UNRECOGNIZED, PhoneStatusText.card(Validity.UNKNOWN));
        assertEquals(Shown.NO_CARD, PhoneStatusText.card(Validity.NONE));
        assertEquals(Shown.NUMBER, PhoneStatusText.card(Validity.ACTIVE));
        assertEquals(Shown.NUMBER, PhoneStatusText.card(Validity.SUSPENDED));
    }

    @Test
    void laDemandeDeCarteNEstProposeeQueSansCarteActiveNiModeAvion() {
        assertTrue(PhoneStatusText.requestOffered(Validity.NONE, false));
        assertTrue(PhoneStatusText.requestOffered(Validity.SUSPENDED, false));
        assertTrue(PhoneStatusText.requestOffered(Validity.DEACTIVATED, false));
        assertFalse(PhoneStatusText.requestOffered(Validity.ACTIVE, false));
        assertFalse(PhoneStatusText.requestOffered(Validity.NONE, true));
    }

    @Test
    void lesBarresNeSeDessinentQuAvecUneCarteActiveHorsModeAvion() {
        assertTrue(PhoneStatusText.barsShown(Validity.ACTIVE, true, false));
        assertTrue(PhoneStatusText.barsShown(Validity.ACTIVE, false, true), "verrouillé, l'avion n'est pas connu");
        assertFalse(PhoneStatusText.barsShown(Validity.ACTIVE, true, true));
        assertFalse(PhoneStatusText.barsShown(Validity.DEACTIVATED, true, false));
        assertFalse(PhoneStatusText.barsShown(Validity.NONE, true, false));
    }
}
