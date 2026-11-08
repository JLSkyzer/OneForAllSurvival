package dev.oneforallsurvival.earlywindow.scene.effects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Trajectoire 3D de la chute de l'avion : port de la classe {@code Crash} de compose.py, lue dans
 * {@code fx/crash/track.json} (poses image par image, réacteur, point d'arrachement de l'aile, crête, contact) et
 * {@code fx/crash/sprites.json} (coin de chaque image de l'avion, en pixels de la plaque). Les points du monde se
 * projettent avec la caméra de la scène ({@code Camera} de common.py, plaque entière). Logique pure, sans GL.
 */
public final class CrashTrack {
    public static final String TRACK = "fx/crash/track.json";
    public static final String SPRITES = "fx/crash/sprites.json";

    // Caméra de common.py : position, tangage -4,6°, roulis -9°, champ horizontal de 75° sur l'image finale.
    private static final double[] CAM_POS = {250.0, 93.5, 0.0};
    private static final double[] FWD;
    private static final double[] RIGHT;
    private static final double[] UP;

    static {
        double pitch = Math.toRadians(-4.6);
        double roll = Math.toRadians(-9.0);
        double[] f = {0, Math.sin(pitch), Math.cos(pitch)};
        double[] r0 = {1, 0, 0};
        double[] u0 = {0, Math.cos(pitch), -Math.sin(pitch)}; // cross(forward, right)
        double[] r = new double[3];
        double[] u = new double[3];
        for (int i = 0; i < 3; i++) {
            r[i] = r0[i] * Math.cos(roll) + u0[i] * Math.sin(roll);
            u[i] = u0[i] * Math.cos(roll) - r0[i] * Math.sin(roll);
        }
        FWD = f;
        RIGHT = r;
        UP = u;
    }

    private final double start;
    private final double fps;
    private final double crestT;
    private final double touchT;
    private final double[] crest;
    private final double[][] pos;
    private final double[][] forward;
    private final double[][] side;
    private final double[][] engine;
    private final double[][] tear;
    private final Map<Integer, int[]> offsets;

    CrashTrack(double start, double fps, double crestT, double touchT, double[] crest, double[][] pos,
               double[][] forward, double[][] side, double[][] engine, double[][] tear, Map<Integer, int[]> offsets) {
        this.start = start;
        this.fps = fps;
        this.crestT = crestT;
        this.touchT = touchT;
        this.crest = crest;
        this.pos = pos;
        this.forward = forward;
        this.side = side;
        this.engine = engine;
        this.tear = tear;
        this.offsets = offsets;
    }

    /**
     * Lit la trajectoire et les coins des images.
     *
     * @param opener ouvre un fichier sous {@code lostisland/loading/} ; {@code null} s'il manque
     * @throws IllegalArgumentException si un fichier manque ou ne se lit pas ; le message dit lequel
     */
    public static CrashTrack load(Function<String, InputStream> opener) {
        JsonElement trackJson = json(opener, TRACK);
        JsonElement spritesJson = json(opener, SPRITES);
        try {
            JsonObject track = trackJson.getAsJsonObject();
            JsonObject sprites = spritesJson.getAsJsonObject();
            JsonArray frames = track.getAsJsonArray("frames");
            int n = frames.size();
            if (n == 0) {
                throw new IllegalArgumentException(TRACK + " : aucune image");
            }
            double[][] pos = new double[n][];
            double[][] fwd = new double[n][];
            double[][] side = new double[n][];
            double[][] engine = new double[n][];
            double[][] tear = new double[n][];
            for (int i = 0; i < n; i++) {
                JsonObject f = frames.get(i).getAsJsonObject();
                pos[i] = vec(f, "pos");
                fwd[i] = vec(f, "f");
                side[i] = vec(f, "s");
                engine[i] = vec(f, "engine");
                tear[i] = vec(f, "break");
            }
            Map<Integer, int[]> offsets = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : sprites.entrySet()) {
                JsonArray xy = e.getValue().getAsJsonArray();
                offsets.put(Integer.parseInt(e.getKey()), new int[] {xy.get(0).getAsInt(), xy.get(1).getAsInt()});
            }
            return new CrashTrack(track.get("start").getAsDouble(), track.get("fps").getAsDouble(),
                    track.get("crest_t").getAsDouble(), track.get("touch_t").getAsDouble(), vec(track, "crest"),
                    pos, fwd, side, engine, tear, offsets);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(TRACK + " : contenu inattendu (" + e + ")", e);
        }
    }

    /** Chemins des images de l'avion ({@code fx/crash/plane_NNN.png}), d'après {@code sprites.json} ; vide s'il manque. */
    public static List<String> spritePaths(Function<String, InputStream> opener) {
        List<String> paths = new ArrayList<>();
        try {
            JsonObject sprites = json(opener, SPRITES).getAsJsonObject();
            TreeMap<Integer, String> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : sprites.entrySet()) {
                int frame = Integer.parseInt(e.getKey());
                sorted.put(frame, spritePath(frame));
            }
            paths.addAll(sorted.values());
        } catch (RuntimeException e) {
            // Pas de liste : pas d'images d'avion, l'effet du crash se retirera seul faute de texture.
        }
        return paths;
    }

    static String spritePath(int frame) {
        return String.format("fx/crash/plane_%03d.png", frame);
    }

    private static JsonElement json(Function<String, InputStream> opener, String path) {
        try (InputStream in = opener.apply(path)) {
            if (in == null) {
                throw new IllegalArgumentException(path + " absent");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(path + " illisible", e);
        } catch (com.google.gson.JsonParseException e) {
            throw new IllegalArgumentException(path + " illisible : " + e.getMessage(), e);
        }
    }

    private static double[] vec(JsonObject o, String key) {
        JsonArray a = o.getAsJsonArray(key);
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    /** Image de la trajectoire à l'instant t : {@code int(clip(round((t - start) * fps), 0, n - 1))}. */
    public int index(double t) {
        double i = Math.rint((t - start) * fps); // np.round arrondit au pair, comme rint
        return (int) Math.min(pos.length - 1, Math.max(0, i));
    }

    /** Instant de l'image {@code frame}. */
    public double born(int frame) {
        return start + frame / fps;
    }

    public double start() {
        return start;
    }

    public double crestT() {
        return crestT;
    }

    public double touchT() {
        return touchT;
    }

    public double[] crest() {
        return crest;
    }

    public int frames() {
        return pos.length;
    }

    public double[] pos(int i) {
        return pos[i];
    }

    public double[] forward(int i) {
        return forward[i];
    }

    public double[] side(int i) {
        return side[i];
    }

    public double[] engine(int i) {
        return engine[i];
    }

    public double[] tear(int i) {
        return tear[i];
    }

    /** Coin haut-gauche de l'image {@code frame} de l'avion, en pixels de la plaque, ou {@code null}. */
    public int[] offset(int frame) {
        return offsets.get(frame);
    }

    /**
     * {@code Camera.project} de common.py (plaque entière) : point du monde vers {x, y, profondeur} en pixels de
     * la plaque.
     */
    public static double[] project(double[] point, int plateW, int plateH, int outW) {
        double focal = (outW / 2.0) / Math.tan(Math.toRadians(37.5));
        double vx = point[0] - CAM_POS[0];
        double vy = point[1] - CAM_POS[1];
        double vz = point[2] - CAM_POS[2];
        double depth = vx * FWD[0] + vy * FWD[1] + vz * FWD[2];
        double r = vx * RIGHT[0] + vy * RIGHT[1] + vz * RIGHT[2];
        double u = vx * UP[0] + vy * UP[1] + vz * UP[2];
        return new double[] {plateW / 2.0 + r / depth * focal, plateH / 2.0 - u / depth * focal, depth};
    }

    /** Focale de la caméra de la plaque entière, en pixels. */
    public static double focal(int outW) {
        return (outW / 2.0) / Math.tan(Math.toRadians(37.5));
    }
}
