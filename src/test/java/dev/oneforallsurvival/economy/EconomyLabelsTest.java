package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/**
 * {@code /eco loot} et {@code /eco quests} nomment paliers et modèles en clair : chaque palier de
 * {@code mob_coins.json} et chaque modèle de {@code quests.json} livrés ont un libellé dans les deux langues, et un
 * palier ou un modèle ajouté par un datapack sans libellé retombe sur son identifiant.
 */
class EconomyLabelsTest {

    private static final Path DATA = Path.of("src/main/resources/data/oneforallsurvival/economy");

    private static JsonObject lang(String code) {
        try (InputStream in = EconomyLabelsTest.class
                .getResourceAsStream("/assets/oneforallsurvival/lang/" + code + ".json")) {
            assertNotNull(in, "fichier de langue " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static JsonElement data(String file) throws IOException {
        return JsonParser.parseString(Files.readString(DATA.resolve(file), StandardCharsets.UTF_8));
    }

    private static List<String> paliers() throws IOException {
        return new ArrayList<>(data("mob_coins.json").getAsJsonObject().getAsJsonObject("tiers").keySet());
    }

    private static List<String> modeles() throws IOException {
        JsonElement racine = data("quests.json");
        List<String> ids = new ArrayList<>();
        for (JsonElement t : racine.getAsJsonObject().getAsJsonArray("templates")) {
            ids.add(t.getAsJsonObject().get("id").getAsString());
        }
        return ids;
    }

    private static TranslatableContents traduisible(Component c) {
        assertTrue(c.getContents() instanceof TranslatableContents, "libellé traduisible attendu : " + c);
        return (TranslatableContents) c.getContents();
    }

    @Test
    void chaquePalierLivreASonLibelleDansLesDeuxLangues() throws IOException {
        List<String> paliers = paliers();
        assertFalse(paliers.isEmpty(), "paliers livrés");
        JsonObject fr = lang("fr_fr");
        JsonObject en = lang("en_us");
        for (String palier : paliers) {
            TranslatableContents t = traduisible(EconomyCommands.tierLabel(palier));
            assertTrue(fr.has(t.getKey()), "fr_fr : " + t.getKey());
            assertTrue(en.has(t.getKey()), "en_us : " + t.getKey());
            assertFalse(fr.get(t.getKey()).getAsString().equals(palier), "libellé français, pas l'identifiant : " + palier);
            assertEquals(palier, t.getFallback(), "repli sur l'identifiant");
        }
    }

    @Test
    void chaqueModeleLivreASonLibelleDansLesDeuxLangues() throws IOException {
        List<String> modeles = modeles();
        assertEquals(22, modeles.size(), "modèles livrés");
        JsonObject fr = lang("fr_fr");
        JsonObject en = lang("en_us");
        for (String modele : modeles) {
            TranslatableContents t = traduisible(EconomyCommands.templateLabel(modele));
            assertTrue(fr.has(t.getKey()), "fr_fr : " + t.getKey());
            assertTrue(en.has(t.getKey()), "en_us : " + t.getKey());
            assertFalse(fr.get(t.getKey()).getAsString().contains("%"), "libellé sans quantité : " + t.getKey());
            assertEquals(modele, t.getFallback(), "repli sur l'identifiant");
        }
    }

    @Test
    void unPalierOuUnModeleSansLibelleRetombeSurSonIdentifiant() {
        JsonObject fr = lang("fr_fr");
        for (Component c : List.of(EconomyCommands.tierLabel("palier_d_un_datapack"),
                EconomyCommands.templateLabel("modele_d_un_datapack"), EconomyCommands.templateLabel("event:fete"))) {
            TranslatableContents t = traduisible(c);
            assertFalse(fr.has(t.getKey()), "aucune clé pour " + t.getKey());
            assertTrue(t.getKey().endsWith(t.getFallback()), "repli sur l'identifiant : " + t.getFallback());
        }
    }
}
