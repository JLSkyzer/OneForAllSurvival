package dev.oneforallsurvival.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.economy.quest.QuestTemplate;
import dev.oneforallsurvival.economy.quest.QuestTemplates;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Le fichier de quêtes livré, lu avec les vrais registres et les vrais tags du jeu (avec et sans le pack). */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class QuestFileGameTests {

    private QuestFileGameTests() {}

    private static JsonObject shipped() throws IOException {
        try (InputStream in = QuestTemplates.class
                .getResourceAsStream("/data/oneforallsurvival/economy/quests.json")) {
            if (in == null) {
                throw new IOException("quests.json absent du jar");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @GameTest(batch = "quetesFichier", template = "test/plancher")
    public static void leFichierLivreSeLitAvecLesVraisRegistres(GameTestHelper helper) throws IOException {
        List<QuestTemplate> parsed = QuestTemplates.parse(shipped());
        boolean meals = ModList.get().isLoaded("farmersdelight");
        helper.assertTrue(parsed.size() == (meals ? 22 : 21),
                "modèles lus : " + parsed.size() + " (Farmer's Delight " + (meals ? "présent" : "absent") + ")");
        helper.assertTrue(parsed.stream().anyMatch(t -> t.id().equals("cook_meals")) == meals,
                "cook_meals suit la présence de Farmer's Delight");
        Set<String> dead = QuestTemplates.findDeadTags(parsed, QuestTemplates::tagHasEntries);
        List<QuestTemplate> drawable = QuestTemplates.usable(parsed, dead);
        helper.assertTrue(!drawable.isEmpty(), "la réserve tirable est vide");
        helper.assertTrue(drawable.size() >= 21, "réserve tirable : " + drawable.size() + ", tags morts : " + dead);
        helper.succeed();
    }
}
