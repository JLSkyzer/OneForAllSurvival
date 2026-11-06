package dev.oneforallsurvival.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Les ressources de l'établi sont complètes : un modèle pour chaque combinaison d'état, des textures qui existent. */
class BenchAssetsTest {

    private static final String BASE = "/assets/oneforallsurvival/";

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = BenchAssetsTest.class.getResourceAsStream(BASE + path)) {
            assertNotNull(in, "ressource absente : " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static BufferedImage png(String path) throws IOException {
        try (InputStream in = BenchAssetsTest.class.getResourceAsStream(BASE + path)) {
            assertNotNull(in, "ressource absente : " + path);
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image, "PNG illisible : " + path);
            return image;
        }
    }

    @Test
    void leBlockstateCouvreChaqueOrientationEtChaqueEtat() throws IOException {
        JsonObject variants = json("blockstates/engineer_bench.json").getAsJsonObject("variants");
        assertEquals(16, variants.size());
        int quart = 0;
        for (String facing : List.of("north", "east", "south", "west")) {
            for (String state : List.of("stopped", "running", "no_power", "fault")) {
                JsonObject v = variants.getAsJsonObject("facing=" + facing + ",state=" + state);
                assertNotNull(v, facing + " " + state);
                assertEquals("oneforallsurvival:block/engineer_bench_" + state, v.get("model").getAsString());
                assertEquals(90 * quart, v.has("y") ? v.get("y").getAsInt() : 0);
            }
            quart++;
        }
    }

    @Test
    void chaqueModeleEtChaqueTextureExistent() throws IOException {
        for (String state : List.of("stopped", "running", "no_power", "fault")) {
            JsonObject model = json("models/block/engineer_bench_" + state + ".json");
            assertTrue(model.getAsJsonArray("elements").size() > 10, "un vrai modèle à plusieurs éléments");
            JsonObject textures = model.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
                String id = e.getValue().getAsString().substring("oneforallsurvival:".length());
                BufferedImage image = png("textures/" + id + ".png");
                assertEquals(16, image.getWidth(), id);
                assertEquals(16, image.getHeight(), id);
            }
            for (JsonElement el : model.getAsJsonArray("elements")) {
                for (Map.Entry<String, JsonElement> f : el.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    String key = f.getValue().getAsJsonObject().get("texture").getAsString().substring(1);
                    assertTrue(textures.has(key), "texture non déclarée : " + key);
                }
            }
        }
        assertEquals("oneforallsurvival:block/engineer_bench_stopped",
                json("models/item/engineer_bench.json").get("parent").getAsString());
    }

    @Test
    void lesTexturesDeInterfaceSontPetites() throws IOException {
        for (String name : List.of("bench_metal", "bench_well", "bench_brass", "bench_frame")) {
            BufferedImage image = png("textures/gui/" + name + ".png");
            assertEquals(16, image.getWidth(), name);
            assertEquals(16, image.getHeight(), name);
        }
        assertEquals(18, png("textures/gui/bench_slot.png").getWidth());
        assertEquals(60, png("textures/gui/bench_button.png").getHeight());
        assertEquals(4, png("textures/gui/bench_rivet.png").getWidth());
    }
}
