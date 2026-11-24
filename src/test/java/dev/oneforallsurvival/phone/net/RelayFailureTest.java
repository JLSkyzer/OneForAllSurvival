package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/**
 * Pourquoi un appel ne passe pas, et ce que l'appelant en lit : une clé par raison, dans les deux langues ; seul
 * « l'antenne de l'appelé est hors tension » nomme quelque chose, et c'est l'opérateur (jamais le fondateur ni la
 * position).
 */
class RelayFailureTest {

    private static JsonObject lang(String code) {
        try (InputStream in = RelayFailureTest.class
                .getResourceAsStream("/assets/oneforallsurvival/lang/" + code + ".json")) {
            assertNotNull(in, "fichier de langue " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Le texte tel qu'un joueur en français le lit : le modèle de {@code fr_fr.json}, arguments posés. */
    private static String enFrancais(Component c) {
        TranslatableContents t = (TranslatableContents) c.getContents();
        String modele = lang("fr_fr").get(t.getKey()).getAsString();
        Object[] args = new Object[t.getArgs().length];
        for (int i = 0; i < args.length; i++) {
            args[i] = t.getArgs()[i] instanceof Component part ? part.getString() : String.valueOf(t.getArgs()[i]);
        }
        return String.format(Locale.ROOT, modele, args);
    }

    @Test
    void chaqueRaisonASonTexteDansLesDeuxLangues() {
        JsonObject fr = lang("fr_fr");
        JsonObject en = lang("en_us");
        for (RelayFailure.Reason reason : RelayFailure.Reason.values()) {
            String key = "message.oneforallsurvival.relay." + reason.name().toLowerCase(Locale.ROOT);
            assertTrue(fr.has(key), "fr_fr : " + key);
            assertTrue(en.has(key), "en_us : " + key);
            Component c = new RelayFailure(reason, "Lagon").describe();
            assertEquals(key, ((TranslatableContents) c.getContents()).getKey());
        }
    }

    @Test
    void lesRaisonsDesCartesExistent() {
        // Les noms sont ceux que le lot T3 consommera : ne pas les renommer.
        for (String name : new String[] {"CALLER_NO_SIGNAL", "CALLER_OPERATOR_REMOVED", "CALLEE_OPERATOR_REMOVED",
                "CALLEE_ANTENNA_DOWN", "CALLEE_NO_COVERAGE", "CALLER_AIRPLANE", "CALLER_SIM_INACTIVE",
                "CALLEE_SIM_INACTIVE", "CALLEE_UNKNOWN"}) {
            assertNotNull(RelayFailure.Reason.valueOf(name));
        }
    }

    @Test
    void lAntenneHorsTensionNommeLOperateurDeLAppele() {
        Component c = new RelayFailure(RelayFailure.Reason.CALLEE_ANTENNA_DOWN, "Lagon").describe();
        TranslatableContents t = (TranslatableContents) c.getContents();
        assertEquals(1, t.getArgs().length, "un seul argument : le nom de l'opérateur");
        assertEquals("Injoignable · Antenne de Lagon hors tension", enFrancais(c));
    }

    @Test
    void lesTextesFrancaisSontCeuxDuBrief() {
        Map<RelayFailure.Reason, String> attendus = new EnumMap<>(RelayFailure.Reason.class);
        attendus.put(RelayFailure.Reason.CALLER_AIRPLANE, "Mode avion");
        attendus.put(RelayFailure.Reason.CALLER_NO_SIGNAL, "Pas de réseau");
        attendus.put(RelayFailure.Reason.CALLEE_NO_COVERAGE, "Injoignable · Hors réseau");
        attendus.put(RelayFailure.Reason.CALLEE_SIM_INACTIVE, "Injoignable · Carte suspendue");
        attendus.put(RelayFailure.Reason.CALLEE_UNKNOWN, "Numéro inconnu");
        attendus.forEach((reason, texte) ->
                assertEquals(texte, enFrancais(new RelayFailure(reason, "").describe()), reason.name()));
    }

    @Test
    void seuleLAntenneHorsTensionPorteUnNom() {
        for (RelayFailure.Reason reason : RelayFailure.Reason.values()) {
            if (reason == RelayFailure.Reason.CALLEE_ANTENNA_DOWN) {
                continue;
            }
            Component c = new RelayFailure(reason, "Fondateur secret").describe();
            assertEquals(0, ((TranslatableContents) c.getContents()).getArgs().length, reason.name());
            assertTrue(!enFrancais(c).contains("Fondateur secret"), reason.name());
        }
    }

    @Test
    void unChampNulEstRefuse() {
        assertThrows(NullPointerException.class, () -> new RelayFailure(null, ""));
        assertThrows(NullPointerException.class, () -> new RelayFailure(RelayFailure.Reason.CALLEE_UNKNOWN, null));
    }

    @Test
    void lEchecDuPlanDevientUnEchecLisible() {
        RelayFailure f = RelayFailure.of(new RelayPlanner.Failed(RelayFailure.Reason.CALLEE_ANTENNA_DOWN, "Mairie"));
        assertEquals(new RelayFailure(RelayFailure.Reason.CALLEE_ANTENNA_DOWN, "Mairie"), f);
    }
}
