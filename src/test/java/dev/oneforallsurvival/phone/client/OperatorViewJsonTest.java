package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.client.web.WebScript;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.net.AntennaLoad;
import dev.oneforallsurvival.phone.net.OperatorView;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.SimCard;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * La vue de l'appli « Antenne » ({@code window.phone.antenna}, lot T2, tâche 10) : chaque composant de
 * {@link OperatorView} (et de ses lignes) est soit dans le JSON de la page, sous le nom du contrat, soit dans la liste
 * écrite des absents ; ni la caisse ni la position de l'antenne cliquée ; le numéro formaté ; un titulaire hostile relu
 * tel quel ; {@code nextTariff} nul quand il ne change pas.
 */
class OperatorViewJsonTest {

    /** Où chaque composant d'{@link OperatorView} va dans le JSON (chemin de la page). */
    private static final Map<String, String> VUE = new LinkedHashMap<>();
    /** Les composants laissés hors de la page, par choix. */
    private static final Set<String> VUE_ABSENTS = Set.of("source", "pos", "operatorId", "till");

    private static final Map<String, String> DROITS = Map.of("cards", "cards", "tariff", "tariff");
    private static final Set<String> DROITS_ABSENTS = Set.of("till", "name");

    private static final Map<String, String> ANTENNE = new LinkedHashMap<>();
    private static final Set<String> ANTENNE_ABSENTS = Set.of();

    private static final Map<String, String> CARTE = Map.of("simId", "id", "number", "number", "holderName", "holder",
            "state", "state");
    private static final Map<String, String> DEMANDE = Map.of("id", "id", "playerName", "name", "at", "at");
    private static final Map<String, String> RELAIS = Map.of("name", "name", "antennas", "antennas", "inService",
            "inService");
    private static final Set<String> RELAIS_ABSENTS = Set.of("operatorId");

    static {
        VUE.put("session", "session");
        VUE.put("name", "name");
        VUE.put("rights", "rights");
        VUE.put("tariff", "tariff,tariffText");
        VUE.put("nextTariff", "nextTariff,nextTariffText");
        VUE.put("cap", "cap,capText");
        VUE.put("antennas", "antennas");
        VUE.put("cardPage", "cards.page");
        VUE.put("cardPages", "cards.pages");
        VUE.put("cardTotal", "cards.total");
        VUE.put("cards", "cards.items");
        VUE.put("requests", "requests");
        VUE.put("relay", "relay");
        ANTENNE.put("id", "id");
        ANTENNE.put("dimension", "dimension");
        ANTENNE.put("pos", "x,y,z");
        ANTENNE.put("state", "state");
        ANTENNE.put("load", "standby,clients,traffic,total");
        ANTENNE.put("feToday", "feToday");
        ANTENNE.put("feYesterday", "feYesterday");
        ANTENNE.put("main", "main");
        ANTENNE.put("overReceive", "overReceive");
    }

    private static final UUID ANTENNE_ID = UUID.fromString("0b9a1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d");
    private static final UUID CARTE_ID = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final UUID DEMANDE_ID = UUID.fromString("2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a");

    static OperatorView vue(String titulaire, Long demain) {
        return new OperatorView(OperatorView.Source.BLOCK, new BlockPos(812, 70, -431), 7, "0123456789ab", "Mairie",
                new OperatorView.Rights(true, false, true, true), 12L, demain, 40L, 987_654L,
                List.of(new OperatorView.AntennaLine(ANTENNE_ID, "minecraft:overworld", new BlockPos(10, 64, -20),
                        MachineState.NO_POWER, new AntennaLoad.Breakdown(40, 6, 3), 1_234L, 5_678L, true, true)),
                1, 3, 70,
                List.of(new OperatorView.CardLine(CARTE_ID, "0612345678", titulaire, SimCard.State.SUSPENDED)),
                List.of(new OperatorView.RequestLine(DEMANDE_ID, "Alex", 1_789_000_000_000L)),
                List.of(new PhoneNetwork.RelayState("0123456789ab", "Mairie", 3, 2),
                        new PhoneNetwork.RelayState("3f2a9c1b0d4e", "Lagon", 2, 0),
                        new PhoneNetwork.RelayState("aaaaaaaaaaaa", "Vide", 0, 0)));
    }

    /** Un formateur de montants factice (le jeu passe {@code CoinMath.format}) : « <n> ». */
    private static final java.util.function.LongFunction<String> COUPURES = n -> "<" + n + ">";

    private static JsonObject json(OperatorView v) {
        return OperatorViewJson.toJson(v, COUPURES);
    }

    private static Set<String> composants(Class<?> type) {
        Set<String> out = new TreeSet<>();
        for (RecordComponent c : type.getRecordComponents()) {
            out.add(c.getName());
        }
        return out;
    }

    /** Chaque composant du type : dans le JSON (aux clés données) ou dans la liste écrite des absents, jamais les deux. */
    private static void traite(Class<?> type, Map<String, String> presents, Set<String> absents, JsonObject json,
            Set<String> clesAttendues) {
        Set<String> tous = composants(type);
        Set<String> listes = new TreeSet<>(presents.keySet());
        listes.addAll(absents);
        assertEquals(tous, listes, type.getSimpleName() + " : un composant ni dans le JSON ni dans les absents");
        for (String a : absents) {
            assertFalse(presents.containsKey(a), type.getSimpleName() + " : " + a + " présent et absent");
        }
        Set<String> cles = new TreeSet<>();
        for (String chemin : presents.values()) {
            for (String c : chemin.split(",")) {
                cles.add(c.contains(".") ? c.substring(0, c.indexOf('.')) : c);
            }
        }
        assertEquals(new TreeSet<>(clesAttendues), cles, type.getSimpleName() + " : clés du contrat");
        assertEquals(new TreeSet<>(clesAttendues), new TreeSet<>(json.keySet()),
                type.getSimpleName() + " : clés écrites par OperatorViewJson");
    }

    @Test
    void chaqueChampDeLaVueEstTraite() {
        JsonObject v = json(vue("Alex", 15L));
        traite(OperatorView.class, VUE, VUE_ABSENTS, v, Set.of("session", "name", "rights", "tariff", "tariffText",
                "nextTariff", "nextTariffText", "cap", "capText", "antennas", "cards", "requests", "relay"));
        assertEquals(Set.of("page", "pages", "total", "items"), v.getAsJsonObject("cards").keySet(), "cartes");
        traite(OperatorView.Rights.class, DROITS, DROITS_ABSENTS, v.getAsJsonObject("rights"),
                Set.of("cards", "tariff"));
        traite(OperatorView.AntennaLine.class, ANTENNE, ANTENNE_ABSENTS,
                v.getAsJsonArray("antennas").get(0).getAsJsonObject(), Set.of("id", "dimension", "x", "y", "z", "state",
                        "standby", "clients", "traffic", "total", "feToday", "feYesterday", "main", "overReceive"));
        traite(OperatorView.CardLine.class, CARTE, Set.of(),
                v.getAsJsonObject("cards").getAsJsonArray("items").get(0).getAsJsonObject(),
                Set.of("id", "number", "holder", "state"));
        traite(OperatorView.RequestLine.class, DEMANDE, Set.of(), v.getAsJsonArray("requests").get(0).getAsJsonObject(),
                Set.of("id", "name", "at"));
        // RelayState : status est calculé (joignable, hors tension, sans antenne), pas un composant.
        JsonObject relais = v.getAsJsonArray("relay").get(0).getAsJsonObject();
        Set<String> sansStatut = new TreeSet<>(relais.keySet());
        assertTrue(sansStatut.remove("status"), "le relais porte son état");
        JsonObject copie = new JsonObject();
        sansStatut.forEach(k -> copie.add(k, relais.get(k)));
        traite(PhoneNetwork.RelayState.class, RELAIS, RELAIS_ABSENTS, copie, Set.of("name", "antennas", "inService"));
    }

    @Test
    void lesValeursSuiventLaVue() {
        JsonObject v = json(vue("Alex", 15L));
        assertEquals(7, v.get("session").getAsInt());
        assertEquals("Mairie", v.get("name").getAsString());
        assertTrue(v.getAsJsonObject("rights").get("cards").getAsBoolean());
        assertFalse(v.getAsJsonObject("rights").get("tariff").getAsBoolean());
        assertEquals(12L, v.get("tariff").getAsLong());
        assertEquals(15L, v.get("nextTariff").getAsLong());
        assertEquals(40L, v.get("cap").getAsLong());
        JsonObject a = v.getAsJsonArray("antennas").get(0).getAsJsonObject();
        assertEquals(ANTENNE_ID.toString(), a.get("id").getAsString());
        assertEquals("minecraft:overworld", a.get("dimension").getAsString());
        assertEquals(10, a.get("x").getAsInt());
        assertEquals(64, a.get("y").getAsInt());
        assertEquals(-20, a.get("z").getAsInt());
        assertEquals("NO_POWER", a.get("state").getAsString());
        assertEquals(40, a.get("standby").getAsInt());
        assertEquals(6, a.get("clients").getAsInt());
        assertEquals(3, a.get("traffic").getAsInt());
        assertEquals(49, a.get("total").getAsInt());
        assertEquals(1_234L, a.get("feToday").getAsLong());
        assertEquals(5_678L, a.get("feYesterday").getAsLong());
        assertTrue(a.get("main").getAsBoolean());
        assertTrue(a.get("overReceive").getAsBoolean());
        JsonObject cards = v.getAsJsonObject("cards");
        assertEquals(1, cards.get("page").getAsInt());
        assertEquals(3, cards.get("pages").getAsInt());
        assertEquals(70, cards.get("total").getAsInt());
        JsonObject c = cards.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(CARTE_ID.toString(), c.get("id").getAsString());
        assertEquals("SUSPENDED", c.get("state").getAsString());
        JsonObject d = v.getAsJsonArray("requests").get(0).getAsJsonObject();
        assertEquals(DEMANDE_ID.toString(), d.get("id").getAsString());
        assertEquals("Alex", d.get("name").getAsString());
        assertEquals(1_789_000_000_000L, d.get("at").getAsLong());
        JsonArray relais = v.getAsJsonArray("relay");
        assertEquals(List.of("joignable", "hors tension", "sans antenne"),
                List.of(relais.get(0).getAsJsonObject().get("status").getAsString(),
                        relais.get(1).getAsJsonObject().get("status").getAsString(),
                        relais.get(2).getAsJsonObject().get("status").getAsString()));
        assertEquals(3, relais.get(0).getAsJsonObject().get("antennas").getAsInt());
        assertEquals(2, relais.get(0).getAsJsonObject().get("inService").getAsInt());
    }

    /** Ni caisse, ni position de l'antenne cliquée, ni droits de caisse et de nom, ni identifiant d'opérateur. */
    @Test
    void pasDeCaisseNiDePositionDansLaPage() {
        JsonObject v = json(vue("Alex", 15L));
        for (String interdit : List.of("till", "tillText", "pos", "source", "operatorId")) {
            assertFalse(v.has(interdit), "clé interdite dans la page : " + interdit);
        }
        assertFalse(v.getAsJsonObject("rights").has("till"), "droit de caisse dans la page");
        assertFalse(v.getAsJsonObject("rights").has("name"), "droit de nom dans la page");
        String texte = v.toString();
        assertFalse(texte.contains("987654"), "la valeur de la caisse dans la page : " + texte);
        assertFalse(texte.contains("812") || texte.contains("-431"), "la position cliquée dans la page : " + texte);
        assertFalse(texte.contains("0123456789ab"), "l'identifiant de l'opérateur dans la page : " + texte);
    }

    @Test
    void numeroFormate() {
        JsonObject c = json(vue("", 15L)).getAsJsonObject("cards").getAsJsonArray("items").get(0)
                .getAsJsonObject();
        assertEquals("06 12 34 56 78", c.get("number").getAsString());
        assertEquals("", c.get("holder").getAsString(), "une carte sans nom a un titulaire vide");
    }

    /** Un titulaire hostile passe par {@link WebScript#call}, puis relu par un analyseur JSON : identique. */
    @Test
    void unTitulaireHostileEstReluALIdentique() {
        String hostile = "</script>\u2028";
        String appel = WebScript.call("phone.antenna", json(vue(hostile, 15L)));
        assertTrue(appel.startsWith("phone.antenna(") && appel.endsWith(")"), appel);
        assertFalse(appel.contains("</script>"), "balise de fin brute dans l'appel : " + appel);
        assertFalse(appel.contains("\u2028"), "séparateur de ligne brut dans l'appel");
        String json = appel.substring("phone.antenna(".length(), appel.length() - 1);
        JsonElement relu = JsonParser.parseString(json);
        assertEquals(hostile, relu.getAsJsonObject().getAsJsonObject("cards").getAsJsonArray("items").get(0)
                .getAsJsonObject().get("holder").getAsString());
    }

    /**
     * Revue de la tâche 10 (mineur 2) : les montants sont aussi écrits comme le panneau et la ligne d'état les écrivent
     * (coupures), à côté des cuivres bruts ; {@code nextTariffText} nul s'il ne change pas.
     */
    @Test
    void lesMontantsSontEcritsCommeLePanneau() {
        JsonObject v = json(vue("Alex", 15L));
        assertEquals("<12>", v.get("tariffText").getAsString());
        assertEquals("<15>", v.get("nextTariffText").getAsString());
        assertEquals("<40>", v.get("capText").getAsString());
        JsonObject sans = json(vue("Alex", null));
        assertTrue(sans.get("nextTariffText").isJsonNull(), "nextTariffText nul : " + sans.get("nextTariffText"));
    }

    /** Revue de la tâche 10 (mineur 8) : des textes hostiles pour le nom, le titulaire et le demandeur, relus tels quels. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"\"><img src=x onerror=alert(1)>", "", "\uD83D\uDCF1 Ana \uD83C\uDF34",
        "\u202E\u0645\u0631\u062D\u0628\u0627", "</script>\u2028<!--"})
    void unTexteHostileEstReluALIdentique(String hostile) {
        verifierHostile(hostile);
    }

    @Test
    void unTexteTresLongEstReluALIdentique() {
        verifierHostile("x<".repeat(5_000));
    }

    private static void verifierHostile(String hostile) {
        OperatorView v = new OperatorView(OperatorView.Source.PHONE, BlockPos.ZERO, 2, "0123456789ab", hostile,
                new OperatorView.Rights(true, true, false, false), 1L, null, 1L, 0L, List.of(), 0, 1, 1,
                List.of(new OperatorView.CardLine(CARTE_ID, "0612345678", hostile, SimCard.State.ACTIVE)),
                List.of(new OperatorView.RequestLine(DEMANDE_ID, hostile, 1L)), List.of());
        String appel = WebScript.call("phone.antenna", json(v));
        assertFalse(appel.contains("</script>") || appel.contains("<img") || appel.contains(" "),
                "texte brut dangereux dans l'appel");
        JsonObject relu = JsonParser.parseString(appel.substring("phone.antenna(".length(), appel.length() - 1))
                .getAsJsonObject();
        assertEquals(hostile, relu.get("name").getAsString());
        assertEquals(hostile, relu.getAsJsonObject("cards").getAsJsonArray("items").get(0).getAsJsonObject()
                .get("holder").getAsString());
        assertEquals(hostile, relu.getAsJsonArray("requests").get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void nextTariffNulQuandIlNeChangePas() {
        JsonObject v = json(vue("Alex", null));
        assertTrue(v.has("nextTariff"), "la clé reste là");
        assertTrue(v.get("nextTariff").isJsonNull(), "nextTariff nul : " + v.get("nextTariff"));
    }

    /** Une vue vide (aucune antenne, carte, demande) : des listes vides, jamais de clé manquante. */
    @Test
    void uneVueVideALesMemesCles() {
        OperatorView vide = new OperatorView(OperatorView.Source.PHONE, BlockPos.ZERO, 1, "0123456789ab", "Seul",
                OperatorView.Rights.NONE, 0L, null, 0L, 0L, List.of(), 0, 1, 0, List.of(), List.of(), new ArrayList<>());
        JsonObject v = json(vide);
        assertEquals(Set.of("session", "name", "rights", "tariff", "tariffText", "nextTariff", "nextTariffText", "cap",
                "capText", "antennas", "cards", "requests", "relay"), v.keySet());
        assertEquals(0, v.getAsJsonArray("antennas").size());
        assertEquals(0, v.getAsJsonObject("cards").getAsJsonArray("items").size());
    }
}
