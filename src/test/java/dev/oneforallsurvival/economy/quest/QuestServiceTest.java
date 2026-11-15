package dev.oneforallsurvival.economy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestServiceTest {

    private static final UUID JOUEUR = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
    private static final LocalDate JOUR = LocalDate.of(2026, 11, 18);

    private static List<QuestTemplate> shipped() throws IOException {
        return QuestTemplates.parse(JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/data/oneforallsurvival/economy/quests.json"), StandardCharsets.UTF_8))
                .getAsJsonObject(), QuestTemplatesTest.REGISTRY, QuestTemplatesTest.MODS);
    }

    @Test
    void uneReserveVideDonneUneSerieVideSansException() {
        assertTrue(QuestService.drawSeries(List.of(), "citoyen", Series.DAILY, 3, 5, 1L).isEmpty());
        assertTrue(QuestService.drawSeries(List.of(), "citoyen", Series.WEEKLY, 5, 5, 1L).isEmpty());
    }

    @Test
    void uneReserveTropPetiteDonneUneSeriePlusCourte() throws IOException {
        List<QuestTemplate> two = shipped().subList(0, 2);
        assertEquals(2, QuestService.drawSeries(two, "citoyen", Series.WEEKLY, 5, 5, 1L).size());
        assertEquals(0, QuestService.drawSeries(two, "citoyen", Series.DAILY, 0, 5, 1L).size());
    }

    @Test
    void leMemeTirageRedonneLesMemesQuetes() throws IOException {
        List<QuestTemplate> pool = shipped();
        long seed = QuestService.seed(JOUEUR, Series.DAILY, JOUR, 0);
        assertEquals(QuestService.drawSeries(pool, "citoyen", Series.DAILY, 3, 5, seed),
                QuestService.drawSeries(pool, "citoyen", Series.DAILY, 3, 5, seed));
        assertNotEquals(seed, QuestService.seed(JOUEUR, Series.DAILY, JOUR.plusDays(1), 0));
        assertNotEquals(seed, QuestService.seed(JOUEUR, Series.WEEKLY, JOUR, 0));
        assertNotEquals(seed, QuestService.seed(JOUEUR, Series.DAILY, JOUR, 1));
    }
}
