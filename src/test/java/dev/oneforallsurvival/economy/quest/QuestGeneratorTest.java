package dev.oneforallsurvival.economy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QuestGeneratorTest {

    private static final Path FILE = Path.of("src/main/resources/data/oneforallsurvival/economy/quests.json");
    private static List<QuestTemplate> pool;

    @BeforeAll
    static void lire() throws IOException {
        pool = QuestTemplates.parse(
                JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject(),
                QuestTemplatesTest.REGISTRY, QuestTemplatesTest.MODS);
    }

    private static List<Quest> draw(String role, Series series, int count, Set<String> excluded, RandomSource r) {
        return QuestGenerator.draw(pool, role, series, count, excluded, 5, r);
    }

    @Test
    void troisJournalieresDifferentes() {
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < 1000; i++) {
            List<Quest> quests = draw("citoyen", Series.DAILY, 3, Set.of(), random);
            assertEquals(3, quests.size());
            assertEquals(3, quests.stream().map(Quest::templateId).distinct().count());
        }
    }

    @Test
    void cinqHebdomadairesDifferentes() {
        RandomSource random = RandomSource.create(43L);
        for (int i = 0; i < 1000; i++) {
            List<Quest> quests = draw("agriculteur", Series.WEEKLY, 5, Set.of(), random);
            assertEquals(5, quests.size());
            assertEquals(5, quests.stream().map(Quest::templateId).distinct().count());
        }
    }

    @Test
    void uneRelanceEnDonneUneAutre() {
        RandomSource random = RandomSource.create(44L);
        for (int i = 0; i < 1000; i++) {
            List<Quest> series = draw("citoyen", Series.DAILY, 3, Set.of(), random);
            Set<String> excluded = series.stream().map(Quest::templateId).collect(Collectors.toSet());
            String replaced = series.get(0).templateId();
            excluded.add(replaced);
            List<Quest> again = draw("citoyen", Series.DAILY, 1, excluded, random);
            assertEquals(1, again.size());
            assertFalse(excluded.contains(again.get(0).templateId()));
        }
    }

    @Test
    void uneQueteDeMetierResteAuRole() {
        RandomSource random = RandomSource.create(45L);
        for (String role : List.of("citoyen", "agriculteur", "medecin")) {
            for (int i = 0; i < 10_000; i++) {
                for (Quest q : draw(role, Series.DAILY, 3, Set.of(), random)) {
                    assertFalse(q.templateId().equals("repair_machines"), role);
                }
            }
        }
        boolean seen = false;
        for (int i = 0; i < 10_000 && !seen; i++) {
            seen = draw("ingenieur", Series.DAILY, 3, Set.of(), random).stream()
                    .anyMatch(q -> q.templateId().equals("repair_machines"));
        }
        assertTrue(seen);
    }

    private static long harvests(List<Quest> quests) {
        return quests.stream().filter(q -> q.category() == QuestCategory.HARVEST).count();
    }

    @Test
    void lAgriculteurRecoitPlusDeRecoltes() {
        RandomSource random = RandomSource.create(46L);
        long farmer = 0;
        long citizen = 0;
        Set<QuestCategory> farmerCategories = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            List<Quest> f = draw("agriculteur", Series.DAILY, 3, Set.of(), random);
            farmer += harvests(f);
            f.forEach(q -> farmerCategories.add(q.category()));
            citizen += harvests(draw("citoyen", Series.DAILY, 3, Set.of(), random));
        }
        double ratio = (double) farmer / citizen;
        assertTrue(ratio >= 2.0D && ratio <= 4.0D, "rapport " + ratio);
        assertTrue(farmerCategories.size() > 4, "catégories : " + farmerCategories);
    }

    @Test
    void uneReserveTropPetiteDonneCeQuElleA() {
        List<QuestTemplate> two = pool.subList(0, 2);
        assertEquals(2, QuestGenerator.draw(two, "citoyen", Series.DAILY, 3, Set.of(), 5, RandomSource.create(1L))
                .size());
    }

    @Test
    void lHebdomadaireVautCinqJournalieres() {
        QuestTemplate stone = pool.stream().filter(t -> t.id().equals("break_stone")).findFirst().orElseThrow();
        List<QuestTemplate> only = List.of(stone);
        RandomSource random = RandomSource.create(47L);
        for (int i = 0; i < 200; i++) {
            Quest daily = QuestGenerator.draw(only, "citoyen", Series.DAILY, 1, Set.of(), 5, random).get(0);
            Quest weekly = QuestGenerator.draw(only, "citoyen", Series.WEEKLY, 1, Set.of(), 5, random).get(0);
            assertTrue(daily.goal() >= 64 && daily.goal() <= 128);
            assertEquals(stone.rewardFor(daily.goal()), daily.reward());
            assertEquals(0, weekly.goal() % 5);
            assertEquals(stone.rewardFor(weekly.goal() / 5) * 5, weekly.reward());
            assertEquals(0, weekly.progress());
        }
    }

    @Test
    void leFacteurDuModeleLaisseLeFacteurGeneral() {
        QuestTemplate repair = pool.stream().filter(t -> t.id().equals("repair_machines")).findFirst().orElseThrow();
        Quest weekly = QuestGenerator.draw(List.of(repair), "ingenieur", Series.WEEKLY, 1, Set.of(), 5,
                RandomSource.create(5L)).get(0);
        assertEquals(0, weekly.goal() % 3);
        assertEquals(repair.rewardFor(weekly.goal() / 3) * 3, weekly.reward());
    }

    @Test
    void memeGraineMemeTirage() {
        List<Quest> a = draw("agriculteur", Series.DAILY, 3, Set.of(), RandomSource.create(77L));
        List<Quest> b = draw("agriculteur", Series.DAILY, 3, Set.of(), RandomSource.create(77L));
        assertEquals(a, b);
        List<Quest> w1 = draw("citoyen", Series.WEEKLY, 5, Set.of(), RandomSource.create(78L));
        assertEquals(w1, draw("citoyen", Series.WEEKLY, 5, Set.of(), RandomSource.create(78L)));
    }

    @Test
    void uneCategorieDisparueEcarteLaQuete() {
        net.minecraft.nbt.CompoundTag tag = draw("citoyen", Series.DAILY, 1, Set.of(), RandomSource.create(9L))
                .get(0).save();
        tag.putString("category", "voler");
        assertTrue(Quest.load(tag).isEmpty());
    }

    @Test
    void uneQueteSeSauveEtSeRelit() {
        Quest q = draw("citoyen", Series.DAILY, 1, Set.of(), RandomSource.create(9L)).get(0).withProgress(3);
        Quest back = Quest.load(q.save()).orElseThrow();
        assertEquals(q, back);
        assertEquals(q.goal(), q.withProgress(10_000).progress());
        assertTrue(q.withProgress(10_000).complete());
        assertTrue(q.claimedNow().claimed());
    }
}
