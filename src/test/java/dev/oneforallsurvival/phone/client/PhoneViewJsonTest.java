package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.client.web.WebScript;
import dev.oneforallsurvival.phone.PhoneNetView;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import dev.oneforallsurvival.phone.net.PhoneSims;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** La vue envoyée à {@code window.phone.render} : clés du contrat de la page, énumérations en noms, rien de trop. */
class PhoneViewJsonTest {

    private static PhoneStatePacket etat(Lock lock, String ownerName, Result last) {
        return etat(lock, ownerName, last, PhoneNetView.NONE);
    }

    private static PhoneStatePacket etat(Lock lock, String ownerName, Result last, PhoneNetView net) {
        return new PhoneStatePacket(9, Kind.UPDATE, lock, 76, 1_763_600_000_000L, 3600, "nuit", true, true, ownerName, 4,
                0, last, CloseReason.NONE, net);
    }

    /** Une vue réseau complète : carte suspendue, numéro, titulaire, appli, deux cartes et deux opérateurs. */
    private static PhoneNetView pleine(String operateur) {
        return new PhoneNetView(PhoneSims.Validity.SUSPENDED, operateur, 3, "06 12 34 56 78", "Alex", true, true,
                List.of(new PhoneSims.SimChoice(5, "07 11 22 33 44", "Lagon", PhoneSims.Validity.ACTIVE),
                        new PhoneSims.SimChoice(40, "06 98 76 54 32", "Mairie", PhoneSims.Validity.UNKNOWN)),
                List.of(new PhoneNetView.OperatorChoice("3f2a9c1b0d4e", "Lagon", 2),
                        new PhoneNetView.OperatorChoice("0a1b2c3d4e5f", "Mairie", 4)));
    }

    @Test
    void lesClesSontCellesDuContrat() {
        JsonObject v = PhoneViewJson.toJson(etat(Lock.UNLOCKED, "Alex", Result.NONE));
        assertEquals(Set.of("session", "lock", "battery", "serverMillis", "zoneOffsetSeconds", "wallpaper", "airplane",
                "faceId", "owner", "ownerName", "attemptsLeft", "lockoutSeconds", "last", "net"), v.keySet());
        assertEquals(9, v.get("session").getAsInt());
        assertEquals(76, v.get("battery").getAsInt());
        assertEquals(1_763_600_000_000L, v.get("serverMillis").getAsLong());
        assertEquals(3600, v.get("zoneOffsetSeconds").getAsInt());
        assertEquals("nuit", v.get("wallpaper").getAsString());
        assertTrue(v.get("airplane").getAsBoolean());
        assertTrue(v.get("owner").getAsBoolean());
        assertEquals("Alex", v.get("ownerName").getAsString());
        assertEquals(4, v.get("attemptsLeft").getAsInt());
        assertEquals(0, v.get("lockoutSeconds").getAsInt());
    }

    /** Le contrat de la tâche 8 : {@code net} et ses clés, cartes et opérateurs aux clés exactes. */
    @Test
    void laVueReseauSuitLeContrat() {
        JsonObject net = PhoneViewJson.toJson(etat(Lock.UNLOCKED, "Alex", Result.NONE, pleine("Mairie")))
                .getAsJsonObject("net");
        assertEquals(Set.of("sim", "operator", "bars", "number", "holder", "founderApp", "founderAppAvailable",
                "choices", "requestable"), net.keySet());
        assertEquals("SUSPENDED", net.get("sim").getAsString());
        assertEquals("Mairie", net.get("operator").getAsString());
        assertEquals(3, net.get("bars").getAsInt());
        assertEquals("06 12 34 56 78", net.get("number").getAsString());
        assertEquals("Alex", net.get("holder").getAsString());
        assertTrue(net.get("founderApp").getAsBoolean());
        assertTrue(net.get("founderAppAvailable").getAsBoolean());
        JsonArray choices = net.getAsJsonArray("choices");
        assertEquals(2, choices.size());
        JsonObject c = choices.get(0).getAsJsonObject();
        assertEquals(Set.of("slot", "number", "operator", "validity"), c.keySet());
        assertEquals(5, c.get("slot").getAsInt());
        assertEquals("07 11 22 33 44", c.get("number").getAsString());
        assertEquals("Lagon", c.get("operator").getAsString());
        assertEquals("ACTIVE", c.get("validity").getAsString());
        assertEquals("UNKNOWN", choices.get(1).getAsJsonObject().get("validity").getAsString());
        JsonArray ops = net.getAsJsonArray("requestable");
        assertEquals(2, ops.size());
        JsonObject o = ops.get(0).getAsJsonObject();
        assertEquals(Set.of("id", "name", "bars"), o.keySet());
        assertEquals("3f2a9c1b0d4e", o.get("id").getAsString());
        assertEquals("Lagon", o.get("name").getAsString());
        assertEquals(2, o.get("bars").getAsInt());
    }

    @Test
    void sansCarteLaVueReseauEstVide() {
        JsonObject net = PhoneViewJson.toJson(etat(Lock.UNLOCKED, "Alex", Result.NONE)).getAsJsonObject("net");
        assertEquals("NONE", net.get("sim").getAsString());
        assertEquals("", net.get("operator").getAsString());
        assertEquals(0, net.get("bars").getAsInt());
        assertFalse(net.get("founderApp").getAsBoolean());
        assertFalse(net.get("founderAppAvailable").getAsBoolean());
        assertEquals(0, net.getAsJsonArray("choices").size());
        assertEquals(0, net.getAsJsonArray("requestable").size());
    }

    /** L'icône « Antenne » montrée mais grisée (tâche 13b) : les deux champs partent chacun tel quel. */
    @Test
    void uneAppliGriseeResteMontree() {
        PhoneNetView grisee = new PhoneNetView(PhoneSims.Validity.NONE, "", 0, "", "", true, false, List.of(),
                List.of());
        JsonObject net = PhoneViewJson.toJson(etat(Lock.UNLOCKED, "Alex", Result.NONE, grisee))
                .getAsJsonObject("net");
        assertTrue(net.get("founderApp").getAsBoolean());
        assertFalse(net.get("founderAppAvailable").getAsBoolean());
    }

    /**
     * La page lit chaque clé de la vue réseau envoyée ({@code cleanNet} de {@code phone.js}), grise l'icône d'après
     * {@code founderAppAvailable} seul (ni barres, ni carte, ni mode avion relus pour elle) et la laisse touchable.
     */
    @Test
    void laPageLitChaqueCleEtGriseDApresLeServeur() throws IOException {
        Path html = Path.of("src/main/resources/assets/oneforallsurvival/html/phone");
        String js = Files.readString(html.resolve("phone.js"));
        int debut = js.indexOf("function cleanNet(");
        assertTrue(debut >= 0, "cleanNet introuvable");
        String corps = js.substring(debut, js.indexOf("function clean(v)", debut));
        JsonObject net = PhoneViewJson.toJson(etat(Lock.UNLOCKED, "Alex", Result.NONE, pleine("Mairie")))
                .getAsJsonObject("net");
        for (String cle : net.keySet()) {
            // La clé entière : « n.founderApp » est aussi le début de « n.founderAppAvailable » (revue de la tâche 13b).
            java.util.regex.Pattern lue = java.util.regex.Pattern.compile(
                    "(?<![A-Za-z0-9_$])" + java.util.regex.Pattern.quote("n." + cle) + "(?![A-Za-z0-9_$])");
            assertTrue(lue.matcher(corps).find(), "la page ne lit pas net." + cle);
        }
        assertTrue(js.contains("$('app-antenna').classList.toggle('off', !n.founderAppAvailable);"),
                "l'icône n'est pas grisée d'après founderAppAvailable seul");
        String css = Files.readString(html.resolve("phone.css"));
        assertTrue(css.contains(".app.off .app-icon"), "aucune règle pour l'icône grisée");
        for (String regle : css.split("}")) {
            if (regle.contains(".app.off")) {
                assertFalse(regle.contains("pointer-events") || regle.contains("display: none"),
                        "l'icône grisée doit rester montrée et touchable : " + regle.strip());
            }
        }
    }

    /**
     * Tâche 15 (« message seul ») : toucher l'icône « Antenne » grisée n'anime rien ; le geste part quand même (le
     * serveur répond « Pas de réseau. »), et la page ne relit rien d'autre que la classe que la vue du serveur a posée.
     */
    @Test
    void toucherLIconeGriseeNAnimeRien() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/assets/oneforallsurvival/html/phone/phone.js"));
        int toucher = js.indexOf("if (t.closest('#app-antenna')) {");
        assertTrue(toucher >= 0, "toucher de l'icône introuvable");
        String bloc = js.substring(toucher, js.indexOf("return;", toucher));
        assertTrue(bloc.contains("openAntennaApp($('app-antenna').classList.contains('off'));"),
                "le toucher doit dire à l'appli si l'icône est grisée : " + bloc);
        int debut = js.indexOf("function openAntennaApp(quiet) {");
        assertTrue(debut >= 0, "openAntennaApp(quiet) introuvable");
        String corps = js.substring(debut, js.indexOf("function closeAntenna(", debut));
        int calme = corps.indexOf("if (quiet) {");
        assertTrue(calme >= 0, "aucune branche pour l'icône grisée");
        String branche = corps.substring(calme, corps.indexOf("return;", calme));
        assertTrue(branche.contains("send({ a: 'antennaApp' });"), "le geste doit partir quand même : " + branche);
        assertFalse(branche.contains("ant-open") || branche.contains("transformOrigin") || branche.contains("paintChrome"),
                "l'icône grisée ne doit rien animer : " + branche);
    }

    @Test
    void lesEnumerationsSontEnNoms() {
        JsonObject v = PhoneViewJson.toJson(etat(Lock.LOCKED, "", Result.WRONG_CODE));
        assertTrue(v.get("lock").getAsJsonPrimitive().isString());
        assertEquals("LOCKED", v.get("lock").getAsString());
        assertEquals("WRONG_CODE", v.get("last").getAsString());
        assertEquals("SETUP", PhoneViewJson.toJson(etat(Lock.SETUP, "", Result.NONE)).get("lock").getAsString());
        assertEquals("UNLOCKED", PhoneViewJson.toJson(etat(Lock.UNLOCKED, "", Result.CODE_CHANGED)).get("lock")
                .getAsString());
        assertEquals("REQUESTS_FULL", PhoneViewJson.toJson(etat(Lock.UNLOCKED, "", Result.REQUESTS_FULL)).get("last")
                .getAsString());
    }

    @Test
    void uneVueVerrouilleeNeDitRienDuProprietaire() {
        for (Lock lock : new Lock[] {Lock.LOCKED, Lock.SETUP}) {
            JsonObject v = PhoneViewJson.toJson(etat(lock, "Alex", Result.NONE));
            assertEquals("", v.get("ownerName").getAsString(), lock.name());
            assertFalse(v.get("owner").getAsBoolean(), lock.name());
            assertFalse(v.get("airplane").getAsBoolean(), lock.name());
            assertEquals("nuit", v.get("wallpaper").getAsString(), "le fond reste visible");
        }
    }

    /**
     * Verrouillé, même si le paquet portait une vue pleine (le serveur l'a déjà réduite, la page n'a pas à le croire) :
     * ni {@code number} ni {@code holder}, aucune carte, aucun opérateur, pas d'appli, et l'état de la carte grossier
     * (une suspendue se lit « présente », {@code ACTIVE}). L'opérateur et les barres restent.
     */
    @Test
    void uneVueVerrouilleeNePorteNiNumeroNiTitulaire() {
        for (Lock lock : new Lock[] {Lock.LOCKED, Lock.SETUP}) {
            JsonObject net = PhoneViewJson.toJson(etat(lock, "Alex", Result.NONE, pleine("Mairie")))
                    .getAsJsonObject("net");
            assertFalse(net.has("number"), lock + " : numéro dans la vue " + net);
            assertFalse(net.has("holder"), lock + " : titulaire dans la vue " + net);
            assertEquals("ACTIVE", net.get("sim").getAsString(), lock + " : état de la carte exact");
            assertEquals("Mairie", net.get("operator").getAsString());
            assertEquals(3, net.get("bars").getAsInt());
            assertFalse(net.get("founderApp").getAsBoolean(), lock + " : appli Antenne");
            assertFalse(net.get("founderAppAvailable").getAsBoolean(), lock + " : appli Antenne ouvrable");
            assertEquals(0, net.getAsJsonArray("choices").size(), lock + " : cartes de l'inventaire");
            assertEquals(0, net.getAsJsonArray("requestable").size(), lock + " : opérateurs demandables");
            String texte = net.toString();
            assertFalse(texte.contains("06 12 34 56 78") || texte.contains("Alex") || texte.contains("Lagon"), texte);
        }
    }

    @Test
    void unNomPiegeRevientALIdentiqueApresWebScript() {
        String piege = "</script>\u2028";
        String script = WebScript.call("phone.render", PhoneViewJson.toJson(etat(Lock.UNLOCKED, piege, Result.NONE)));
        assertTrue(script.startsWith("phone.render(") && script.endsWith(")"), script);
        assertFalse(script.contains("</script>"), script);
        assertFalse(script.contains("\u2028"), "U+2028 brut dans le script");
        String argument = script.substring("phone.render(".length(), script.length() - 1);
        assertEquals(piege, JsonParser.parseString(argument).getAsJsonObject().get("ownerName").getAsString());
    }

    /** Un nom d'opérateur piégé (dans la ligne et dans une carte) revient à l'identique après {@code WebScript.call}. */
    @Test
    void unOperateurPiegeRevientALIdentiqueApresWebScript() {
        String piege = "</script>\u2028";
        String script = WebScript.call("phone.render", PhoneViewJson.toJson(etat(Lock.UNLOCKED, "Alex", Result.NONE,
                pleine(piege))));
        assertFalse(script.contains("</script>"), script);
        assertFalse(script.contains("\u2028"), "U+2028 brut dans le script");
        String argument = script.substring("phone.render(".length(), script.length() - 1);
        JsonObject net = JsonParser.parseString(argument).getAsJsonObject().getAsJsonObject("net");
        assertEquals(piege, net.get("operator").getAsString());
    }

    /** Face ID (retours d'essai du 27/11) : montré déverrouillé au propriétaire seulement, même si le paquet le porte. */
    @Test
    void faceIdNeSeMontreQueDeverrouilleAuProprietaire() {
        for (Lock lock : Lock.values()) {
            for (boolean owner : new boolean[] {true, false}) {
                PhoneStatePacket p = new PhoneStatePacket(9, Kind.UPDATE, lock, 76, 0L, 0, "nuit", false, owner, "Alex",
                        4, 0, Result.NONE, CloseReason.NONE, PhoneNetView.NONE, true);
                assertEquals(lock == Lock.UNLOCKED && owner, PhoneViewJson.toJson(p).get("faceId").getAsBoolean(),
                        lock + " propriétaire=" + owner);
            }
        }
    }
}
