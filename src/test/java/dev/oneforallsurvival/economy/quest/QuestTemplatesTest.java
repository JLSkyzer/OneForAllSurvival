package dev.oneforallsurvival.economy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuestTemplatesTest {

    private static final Path FILE = Path.of("src/main/resources/data/oneforallsurvival/economy/quests.json");
    private static List<QuestTemplate> shipped;

    /** Les identifiants que connaît le « registre » des essais : ceux du fichier livré. */
    /** Les mods « chargés » des essais. */
    static final java.util.function.Predicate<String> MODS = "farmersdelight"::equals;

    static final QuestTemplates.RegistryCheck REGISTRY = (kind, id) -> java.util.Set.of(
            "BLOCK:minecraft:bricks", "BLOCK:minecraft:wheat", "BLOCK:minecraft:carrots", "BLOCK:minecraft:potatoes",
            "ENTITY:minecraft:zombie", "ENTITY:minecraft:skeleton", "ENTITY:minecraft:spider")
            .contains(kind + ":" + id);

    @BeforeAll
    static void lire() throws IOException {
        shipped = parse(Files.readString(FILE, StandardCharsets.UTF_8));
    }

    private static List<QuestTemplate> parse(String json) {
        return QuestTemplates.parse(JsonParser.parseString(json).getAsJsonObject(), REGISTRY, MODS);
    }

    private static QuestTemplate shipped(String id) {
        return shipped.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void leFichierLivreSeLit() {
        assertEquals(22, shipped.size());
    }

    @Test
    void chaqueCategorieComptableALeSien() {
        Set<QuestCategory> seen = EnumSet.noneOf(QuestCategory.class);
        shipped.forEach(t -> seen.add(t.category()));
        for (QuestCategory category : QuestCategory.values()) {
            if (category.counted()) {
                assertTrue(seen.contains(category), "aucun modèle pour " + category);
            }
        }
    }

    @Test
    void uneLivraisonEstRefusee() {
        JsonParseException e = assertThrows(JsonParseException.class, () -> parse(
                "{\"templates\":[{\"id\":\"livre\",\"category\":\"deliver\",\"target\":\"minecraft:wheat\","
                        + "\"min\":1,\"max\":2,\"reward\":5}]}"));
        assertTrue(e.getMessage().contains("category"), e.getMessage());
        assertTrue(e.getMessage().contains("livre"), e.getMessage());
    }

    @Test
    void uneActionInconnueEstRefusee() {
        JsonParseException e = assertThrows(JsonParseException.class, () -> parse(
                "{\"templates\":[{\"id\":\"geste\",\"category\":\"job\",\"target\":\"oneforallsurvival:inconnue\","
                        + "\"min\":1,\"max\":2,\"reward\":5}]}"));
        assertTrue(e.getMessage().contains("target"), e.getMessage());
    }

    @Test
    void unMetierSansCibleEstRefuse() {
        JsonParseException e = assertThrows(JsonParseException.class, () -> parse(
                "{\"templates\":[{\"id\":\"geste\",\"category\":\"job\",\"min\":1,\"max\":2,\"reward\":5}]}"));
        assertTrue(e.getMessage().contains("target"), e.getMessage());
    }

    @Test
    void uneCibleAbsenteDuRegistreIgnoreSeulementCeModele() {
        List<QuestTemplate> out = parse("{\"templates\":["
                + "{\"id\":\"x\",\"category\":\"break\",\"target\":\"minecraft:pas_un_bloc\",\"min\":1,\"max\":2,"
                + "\"reward\":5},"
                + "{\"id\":\"y\",\"category\":\"break\",\"target\":\"minecraft:wheat\",\"min\":1,\"max\":2,"
                + "\"reward\":5}]}");
        assertEquals(List.of("y"), out.stream().map(QuestTemplate::id).toList());
    }

    @Test
    void unModeleIgnoreNeReservePasSonIdentifiant() {
        List<QuestTemplate> out = parse("{\"templates\":["
                + "{\"id\":\"x\",\"category\":\"break\",\"target\":\"minecraft:pas_un_bloc\",\"min\":1,\"max\":2,"
                + "\"reward\":5},"
                + "{\"id\":\"x\",\"category\":\"break\",\"min\":1,\"max\":2,\"reward\":5}]}");
        assertEquals(1, out.size());
    }

    @Test
    void unModeleDUnModAbsentEstIgnoreSansFaute() {
        List<QuestTemplate> out = parse("{\"templates\":["
                + "{\"id\":\"a\",\"category\":\"break\",\"requires\":\"create\",\"target\":\"create:cogwheel\","
                + "\"min\":1,\"max\":2,\"reward\":5},"
                + "{\"id\":\"b\",\"category\":\"break\",\"requires\":\"farmersdelight\",\"min\":1,\"max\":2,"
                + "\"reward\":5}]}");
        assertEquals(List.of("b"), out.stream().map(QuestTemplate::id).toList());
    }

    @Test
    void uneFauteDeStructureRefuseToujoursLeFichier() {
        // un modèle de mod absent n'excuse pas un min > max
        assertThrows(JsonParseException.class, () -> parse("{\"templates\":["
                + "{\"id\":\"a\",\"category\":\"break\",\"requires\":\"create\",\"min\":3,\"max\":2,\"reward\":5}]}"));
    }

    @Test
    void unTagInconnuOuVideRendLeModeleIntirable() {
        QuestTemplate meals = shipped("cook_meals");
        QuestTemplate stone = shipped("break_stone");
        java.util.Set<String> dead = QuestTemplates.findDeadTags(List.of(meals, stone),
                (kind, id) -> !id.toString().equals("farmersdelight:meals"));
        assertEquals(java.util.Set.of("ITEM:farmersdelight:meals"), dead);
        assertEquals(List.of(stone), QuestTemplates.usable(List.of(meals, stone), dead));
        assertEquals(List.of(meals, stone), QuestTemplates.usable(List.of(meals, stone), java.util.Set.of()));
    }

    @Test
    void cookMealsExigeFarmersDelight() {
        assertTrue(shipped.stream().anyMatch(t -> t.id().equals("cook_meals")));
        String json;
        try {
            json = Files.readString(FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        List<QuestTemplate> sans = QuestTemplates.parse(JsonParser.parseString(json).getAsJsonObject(), REGISTRY,
                mod -> false);
        assertEquals(21, sans.size());
        assertTrue(sans.stream().noneMatch(t -> t.id().equals("cook_meals")));
    }

    private static String one(String fields) {
        return "{\"templates\":[{\"id\":\"a\",\"category\":\"break\",\"min\":2,\"max\":4,\"reward\":5" + fields
                + "}]}";
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"templates\":[|templates",
            "{\"templates\":[{\"id\":\"a\",\"category\":\"voler\",\"min\":1,\"max\":2,\"reward\":5}]}|category",
            "{\"templates\":[{\"id\":\"a\",\"category\":\"break\",\"min\":3,\"max\":2,\"reward\":5}]}|max",
            "{\"templates\":[{\"id\":\"a\",\"category\":\"break\",\"min\":0,\"max\":2,\"reward\":5}]}|min",
            "{\"templates\":[{\"id\":\"a\",\"category\":\"break\",\"min\":1,\"max\":2,\"reward\":0}]}|reward",
            "{\"templates\":[{\"id\":\"a\",\"category\":\"break\",\"min\":1,\"max\":2,\"reward\":5,\"weight\":0}]}|weight",
            "{\"templates\":[{\"id\":\"a\",\"category\":\"break\",\"min\":1,\"max\":2,\"reward\":5},"
                    + "{\"id\":\"a\",\"category\":\"break\",\"min\":1,\"max\":2,\"reward\":5}]}|en double",
            "{\"templates\":[{\"id\":\"Mauvais Id\",\"category\":\"break\",\"min\":1,\"max\":2,\"reward\":5}]}|[a-z0-9_]",
            "{\"templates\":[{\"id\":\"a\",\"category\":\"break\",\"min\":1,\"max\":2,\"reward\":5,"
                    + "\"weekly_factor\":30}]}|weekly_factor",
            "{\"templates\":[{\"id\":\"a\",\"category\":\"break\",\"min\":1,\"max\":2,\"reward\":5,"
                    + "\"role_weights\":{\"medecin\":-1}}]}|role_weights",
            "{\"templates\":[{\"id\":\"a\",\"category\":\"break\",\"min\":1,\"max\":2,\"reward\":5,"
                    + "\"target\":\"#\"}]}|target"
    })
    void unFichierInvalideEstRefuse(String json, String field) {
        JsonParseException e = assertThrows(JsonParseException.class, () -> parse(json));
        if (!field.equals("templates")) {
            assertTrue(e.getMessage().contains(field), e.getMessage());
        }
    }

    @Test
    void unChampValideEstAccepte() {
        QuestTemplate t = parse(one(",\"weight\":2.5,\"weekly_factor\":3,\"role_weights\":{\"agriculteur\":3},"
                + "\"title\":\"quest.x\"")).get(0);
        assertEquals(2.5D, t.weight());
        assertEquals(3, t.weeklyFactor().getAsInt());
        assertEquals(7.5D, t.weightFor("agriculteur"));
        assertEquals("quest.x", t.titleKey());
    }

    @Test
    void laRecompenseSuitLaQuantite() {
        QuestTemplate stone = shipped("break_stone");
        assertEquals(10L, stone.rewardFor(64));
        assertEquals(15L, stone.rewardFor(96));
        assertEquals(20L, stone.rewardFor(128));
        assertEquals(1L, new QuestTemplate("t", QuestCategory.BREAK, null, 100, 200, 1L, 1.0D, java.util.Map.of(),
                java.util.OptionalInt.empty(), null).rewardFor(100));
        // hebdomadaire : 96 × 5 de quantité, 15 × 5 de récompense
        assertEquals(75L, stone.rewardFor(96) * 5);
    }
}
