package dev.oneforallsurvival.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Les ressources de la station météo sont complètes : une variante pour chaque orientation, chaque état et chaque
 * tendance, et un modèle qui existe derrière chacune. Le serveur de GameTests ne charge aucun modèle : c'est ce test qui
 * garde le client à l'abri d'un modèle manquant.
 */
class WeatherStationAssetsTest {

    private static final String BASE = "/assets/oneforallsurvival/";

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = WeatherStationAssetsTest.class.getResourceAsStream(BASE + path)) {
            assertNotNull(in, "ressource absente : " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void chaqueCombinaisonALeSienne() throws IOException {
        JsonObject variants = json("blockstates/weather_station.json").getAsJsonObject("variants");
        assertEquals(4 * 4 * 3, variants.size());
        int quart = 0;
        for (String facing : List.of("north", "east", "south", "west")) {
            for (String state : List.of("stopped", "running", "no_power", "fault")) {
                for (String trend : List.of("stable", "rain_soon", "storm_soon")) {
                    String key = "facing=" + facing + ",state=" + state + ",trend=" + trend;
                    JsonObject v = variants.getAsJsonObject(key);
                    assertNotNull(v, key);
                    String model = v.get("model").getAsString();
                    // Sans marche, la face avant est éteinte, quelle que soit la dernière tendance lue.
                    String attendu = "oneforallsurvival:block/weather_station_"
                            + (state.equals("running") ? trend : "off");
                    assertEquals(attendu, model, key);
                    assertEquals(90 * quart, v.has("y") ? v.get("y").getAsInt() : 0, key);
                    json("models/block/" + model.substring("oneforallsurvival:block/".length()) + ".json");
                }
            }
            quart++;
        }
        assertEquals("oneforallsurvival:block/weather_station_off",
                json("models/item/weather_station.json").get("parent").getAsString());
    }
}
