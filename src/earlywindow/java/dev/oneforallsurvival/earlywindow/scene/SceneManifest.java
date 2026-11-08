package dev.oneforallsurvival.earlywindow.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue des calques de la scène : le contenu de {@code manifest.json}, avec chaque champ que
 * {@code art/loading-screen/tools/compose.py} lit. Aucune classe de Minecraft : ce code tourne avant le jeu.
 *
 * <p>Les noms Java sont ceux du JSON en camelCase ({@code horizon_row} devient {@code horizonRow}) ; {@code output}
 * et {@code plate} sont aplatis en {@code outW/outH} et {@code plateW/plateH}. Les coordonnées sont celles du
 * plan de référence, en pixels de la plaque ({@code plate}), sauf {@code sunUv} et {@code moonUv} (0 à 1).
 */
public record SceneManifest(
        int outW, int outH,
        int plateW, int plateH,
        Sea sea,
        float[] sunUv, float[] moonUv,
        List<Fire> fires,
        List<Plume> plumes,
        List<Camp> camp,
        float[] temple,
        float[] wreck,
        Logo logo) {

    /** Mer : {@code frames} images en boucle, {@code scale} de l'agrandissement, ligne d'horizon de la plaque de mer. */
    public record Sea(int frames, int scale, int horizonRow) {}

    /** Foyer de l'épave : position, taille, déphasage dans la boucle et bande de flammes (0 ou 1). */
    public record Fire(float x, float y, float size, int phase, int strip) {}

    /** Panache de fumée : position, échelle et puissance. */
    public record Plume(float x, float y, float scale, int power) {}

    /** Feu de camp : position et instant du récit (0 à 1) où il s'allume. */
    public record Camp(float x, float y, float at) {}

    /** Lettre du logo : fichier de l'image et décalage dans le logo. */
    public record Letter(String file, int x, int y) {}

    /** Logo : lettres, dimensions de l'ensemble et marge de la lueur. */
    public record Logo(List<Letter> letters, int width, int height, int glowPad) {}

    /**
     * Lit un manifeste.
     *
     * @throws IllegalArgumentException si le JSON est invalide ou si un champ manque ou a le mauvais type ;
     *                                  le message nomme le champ en cause
     */
    public static SceneManifest parse(Reader json) {
        JsonObject root;
        try {
            JsonElement e = JsonParser.parseReader(json);
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException("manifest.json : un objet JSON est attendu à la racine");
            }
            root = e.getAsJsonObject();
        } catch (com.google.gson.JsonParseException e) {
            throw new IllegalArgumentException("manifest.json illisible : " + e.getMessage(), e);
        }
        try {
            int[] out = ints(root, "output", 2);
            int[] plate = ints(root, "plate", 2);
            JsonObject sea = object(root, "sea");
            JsonObject logo = object(root, "logo");
            List<Letter> letters = new ArrayList<>();
            for (JsonObject l : objects(logo, "letters")) {
                letters.add(new Letter(string(l, "file"), integer(l, "x"), integer(l, "y")));
            }
            List<Fire> fires = new ArrayList<>();
            for (JsonObject f : objects(root, "fires")) {
                fires.add(new Fire(number(f, "x"), number(f, "y"), number(f, "size"),
                        integer(f, "phase"), integer(f, "strip")));
            }
            List<Plume> plumes = new ArrayList<>();
            for (JsonObject p : objects(root, "plumes")) {
                plumes.add(new Plume(number(p, "x"), number(p, "y"), number(p, "scale"), integer(p, "power")));
            }
            List<Camp> camp = new ArrayList<>();
            for (JsonObject c : objects(root, "camp")) {
                camp.add(new Camp(number(c, "x"), number(c, "y"), number(c, "at")));
            }
            return new SceneManifest(
                    out[0], out[1], plate[0], plate[1],
                    new Sea(integer(sea, "frames"), integer(sea, "scale"), integer(sea, "horizon_row")),
                    floats(root, "sun_uv"), floats(root, "moon_uv"),
                    List.copyOf(fires), List.copyOf(plumes), List.copyOf(camp),
                    floats(root, "temple"), floats(root, "wreck"),
                    new Logo(List.copyOf(letters), integer(logo, "width"), integer(logo, "height"),
                            integer(logo, "glow_pad")));
        } catch (ClassCastException | IllegalStateException | NumberFormatException e) {
            throw new IllegalArgumentException("manifest.json : champ de type inattendu (" + e.getMessage() + ")", e);
        }
    }

    private static JsonElement required(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            throw new IllegalArgumentException("manifest.json : champ « " + key + " » manquant");
        }
        return e;
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = required(o, key);
        if (!e.isJsonObject()) {
            throw new IllegalArgumentException("manifest.json : « " + key + " » doit être un objet");
        }
        return e.getAsJsonObject();
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = required(o, key);
        if (!e.isJsonArray()) {
            throw new IllegalArgumentException("manifest.json : « " + key + " » doit être une liste");
        }
        return e.getAsJsonArray();
    }

    private static List<JsonObject> objects(JsonObject o, String key) {
        List<JsonObject> res = new ArrayList<>();
        for (JsonElement e : array(o, key)) {
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException("manifest.json : « " + key + " » ne doit contenir que des objets");
            }
            res.add(e.getAsJsonObject());
        }
        return res;
    }

    private static int[] ints(JsonObject o, String key, int n) {
        JsonArray a = array(o, key);
        if (a.size() != n) {
            throw new IllegalArgumentException("manifest.json : « " + key + " » doit avoir " + n + " valeurs");
        }
        int[] res = new int[n];
        for (int i = 0; i < n; i++) {
            res[i] = a.get(i).getAsInt();
        }
        return res;
    }

    private static float[] floats(JsonObject o, String key) {
        JsonArray a = array(o, key);
        if (a.size() != 2) {
            throw new IllegalArgumentException("manifest.json : « " + key + " » doit avoir 2 valeurs");
        }
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    private static int integer(JsonObject o, String key) {
        return required(o, key).getAsInt();
    }

    private static float number(JsonObject o, String key) {
        return required(o, key).getAsFloat();
    }

    private static String string(JsonObject o, String key) {
        return required(o, key).getAsString();
    }
}
