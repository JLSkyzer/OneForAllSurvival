package dev.oneforallsurvival.earlywindow.scene;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.effects.CrashTrack;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Textures de la scène. Les PNG sont décodés (STB) dans un thread à part, puis envoyés à la carte graphique par
 * {@link #upload()} sur le thread GL. Chaque groupe ({@link #SKY}, {@link #PLATES}, {@link #SEA}, {@link #FX},
 * {@link #UI}) devient {@link #ready prêt} quand toutes ses images sont envoyées : la scène s'affiche par
 * morceaux, le ciel d'abord.
 *
 * <p>Les calques sont livrés tels quels (aucune recompression) ; les plaques et les étoiles sont seulement
 * prémultipliées au décodage, comme {@code npy_plate} et {@code premult} de compose.py.
 */
public final class TextureStore implements AutoCloseable {
    public static final String SKY = "sky";
    public static final String PLATES = "plates";
    public static final String SEA = "sea";
    public static final String FX = "fx";
    public static final String UI = "ui";
    /** Les images de la mer, en une texture tableau (une couche par image). */
    public static final String SEA_FRAMES = "sea/frames";
    /** Texture vanilla des nuages : facultative (sans elle, pas de nuages voxel). */
    public static final String CLOUDS = "minecraft:textures/environment/clouds.png";
    /**
     * Flammes vanilla (bandes 16x512, 32 images 16x16 empilées) : lues dans le jar du client comme les nuages,
     * jamais copiées dans le nôtre (licence). Facultatives : sans jar du client, pas de flammes.
     */
    public static final List<String> FIRE_STRIPS = List.of(
            "minecraft:textures/block/fire_0.png", "minecraft:textures/block/fire_1.png");

    private static final long UPLOAD_BUDGET_NANOS = 6_000_000L;

    /** Une image à charger : groupe, chemin, canaux voulus, prémultiplication, couche de la mer (ou -1). */
    private record Entry(String group, String path, int channels, boolean premultiply, boolean optional,
                         int layer, boolean mipmap) {
    }

    /** Image décodée en attente d'envoi ; {@code pixels} vient de STB et se libère par stbi_image_free. */
    private record Decoded(Entry entry, int width, int height, ByteBuffer pixels, Throwable error) {
    }

    private final Function<String, InputStream> opener;
    private final int seaFrames;
    private final List<Entry> entries = new ArrayList<>();
    // Borné : le décodage n'avance pas plus vite que l'envoi, la mémoire reste contenue.
    private final BlockingQueue<Decoded> queue = new ArrayBlockingQueue<>(6);
    private final Map<String, Integer> textures = new HashMap<>();
    private final Map<String, Integer> remaining = new LinkedHashMap<>();
    private Thread decoder;
    private volatile boolean closed;
    private Throwable failure;
    private long bytes; // mémoire vidéo estimée des textures envoyées (largeur x hauteur x canaux, mipmaps compris)

    /**
     * @param opener ouvre une image par son chemin sous {@code lostisland/loading/} (« sky/moon.png »), ou
     *               {@link #CLOUDS} ; renvoie {@code null} si elle n'existe pas
     */
    public TextureStore(SceneManifest manifest, Function<String, InputStream> opener) {
        this.opener = opener;
        this.seaFrames = manifest.sea().frames();
        for (String g : List.of(SKY, PLATES, SEA, FX, UI)) {
            remaining.put(g, 0);
        }
        // Ordre de décodage : ciel, interface (barre et texte, légers, visibles tôt), décor, effets, mer.
        // Le récit attend ciel, décor et effets ; la mer, la plus lourde (48 images), arrive en dernier.
        for (String k : List.of("night", "pre", "sun")) {
            add(SKY, "sky/sky_" + k + ".png", 3, false);
        }
        add(SKY, "sky/stars_a.png", 4, true);
        add(SKY, "sky/stars_b.png", 4, true);
        add(SKY, "sky/moon.png", 4, false);
        add(SKY, "sky/sun.png", 4, false);
        add(SKY, "sky/cloudbank_0.png", 4, false);
        add(SKY, "sky/cloudbank_1.png", 4, false);
        entries.add(new Entry(SKY, CLOUDS, 4, false, true, -1, false));
        remaining.merge(SKY, 1, Integer::sum);
        // Logo, sa lueur, son masque de reflet, icône de l'avion : facultatifs (un manque retire l'effet seul).
        for (SceneManifest.Letter letter : manifest.logo().letters()) {
            addOptional(UI, letter.file(), 4, false);
        }
        addOptional(UI, "ui/logo/glow.png", 4, false);
        addOptional(UI, "ui/logo/face_mask.png", 4, false);
        addOptional(UI, "ui/plane_icon.png", 4, false);
        for (String layer : List.of("far", "mid", "fg")) {
            for (String k : List.of("night", "pre", "sun")) {
                add(PLATES, "plates/" + layer + "_" + k + ".png", 4, true);
            }
        }
        // Avant l'impact, compose.py ne prend que la variante de nuit (il fait encore nuit).
        add(PLATES, "plates/precrash_mid_night.png", 4, true);
        // Masques du feuillage (vent) : facultatifs ; sans eux, le décor reste immobile.
        for (String layer : List.of("far", "mid", "fg")) {
            addOptional(PLATES, "plates/foliage_" + layer + ".png", 1, false);
        }
        // Halo réduit jusqu'à 64 px (feux de camp) : mipmaps, comme le filtre de réduction de resized().
        entries.add(new Entry(FX, "fx/halo.png", 4, false, false, -1, true));
        remaining.merge(FX, 1, Integer::sum);
        add(FX, "fx/moon_rays.png", 4, false);
        // Images des effets (tâche 4) : facultatives. Si l'une manque, seul l'effet qui la lit se retire.
        for (String strip : FIRE_STRIPS) {
            addOptional(FX, strip, 4, false);
        }
        for (int i = 0; i < 6; i++) {
            addOptional(FX, "fx/smoke_" + i + ".png", 4, true);
        }
        // Lueur des feux sur le décor : couleurs seules, l'alpha vient de la plaque de nuit (fire_plate).
        addOptional(FX, "plates/mid_fire.png", 3, false);
        addOptional(FX, "plates/fg_fire.png", 3, false);
        // Orage et crash (tâche 5), facultatifs eux aussi. La pluie est lue au texel près, l'avion à sa taille.
        addOptional(FX, "fx/rain.png", 4, false);
        for (int i = 0; i < 3; i++) {
            addOptional(FX, "fx/bolt_" + i + ".png", 4, false);
        }
        for (String plane : CrashTrack.spritePaths(opener)) {
            addOptional(FX, plane, 4, false);
        }
        for (String name : List.of("depth", "shallow", "mask", "glint_moon", "glint_sun")) {
            add(SEA, "sea/" + name + ".png", 1, false);
        }
        for (int i = 0; i < seaFrames; i++) {
            entries.add(new Entry(SEA, String.format("sea/sea_%02d.png", i), 3, false, false, i, false));
            remaining.merge(SEA, 1, Integer::sum);
        }
    }

    /** Ressources du jar de service ({@code lostisland/loading/}) et texture vanilla des nuages du jeu. */
    public static Function<String, InputStream> classpath(ClassLoader loader) {
        return path -> path.startsWith("minecraft:")
                ? loader.getResourceAsStream("assets/minecraft/" + path.substring("minecraft:".length()))
                : loader.getResourceAsStream("lostisland/loading/" + path);
    }

    /**
     * Ouvre un fichier de données de la scène (JSON) par le même chemin que les images ; {@code null} s'il
     * n'existe pas. L'appelant ferme le flux.
     */
    public InputStream open(String path) {
        return opener.apply(path);
    }

    private void add(String group, String path, int channels, boolean premultiply) {
        entries.add(new Entry(group, path, channels, premultiply, false, -1, false));
        remaining.merge(group, 1, Integer::sum);
    }

    private void addOptional(String group, String path, int channels, boolean mipmap) {
        entries.add(new Entry(group, path, channels, false, true, -1, mipmap));
        remaining.merge(group, 1, Integer::sum);
    }

    /** Lance le décodage en arrière-plan. */
    public synchronized void start() {
        if (decoder != null) {
            return;
        }
        decoder = new Thread(this::decodeAll, "lostisland-textures");
        decoder.setDaemon(true);
        decoder.start();
    }

    private void decodeAll() {
        for (Entry e : entries) {
            if (closed) {
                return;
            }
            Decoded d;
            try {
                d = decode(e);
            } catch (Throwable t) {
                d = new Decoded(e, 0, 0, null, t);
            }
            try {
                queue.put(d);
            } catch (InterruptedException ie) {
                free(d);
                return;
            }
            if (closed) {
                // close() a pu vider la file avant ce dépôt : on ne laisse rien derrière.
                drainQueue();
                return;
            }
        }
    }

    private Decoded decode(Entry e) throws IOException {
        byte[] bytes;
        try (InputStream in = opener.apply(e.path())) {
            if (in == null) {
                throw new IOException("image introuvable : " + e.path());
            }
            bytes = in.readAllBytes();
        }
        ByteBuffer file = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            file.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(file, w, h, c, e.channels());
            if (pixels == null) {
                throw new IOException("décodage impossible de " + e.path() + " : " + STBImage.stbi_failure_reason());
            }
            if (e.premultiply()) {
                premultiply(pixels);
            }
            return new Decoded(e, w.get(0), h.get(0), pixels, null);
        } finally {
            MemoryUtil.memFree(file);
        }
    }

    /** Couleurs multipliées par l'alpha, arrondies comme {@code (out * 255 + 0.5).astype(uint8)}. */
    private static void premultiply(ByteBuffer rgba) {
        for (int i = 0, n = rgba.remaining(); i < n; i += 4) {
            int a = rgba.get(i + 3) & 0xff;
            if (a == 255) {
                continue;
            }
            for (int c = 0; c < 3; c++) {
                int v = rgba.get(i + c) & 0xff;
                rgba.put(i + c, (byte) ((v * a * 2 + 255) / 510));
            }
        }
    }

    /**
     * Envoie à la carte graphique les images décodées depuis l'appel précédent, dans un budget de quelques
     * millisecondes. À appeler sur le thread GL, une fois par image.
     */
    public void upload() {
        long start = System.nanoTime();
        // Dans le contexte de Minecraft (tâche 8), l'état de lecture des pixels peut être réglé autrement : on
        // le remet aux valeurs par défaut le temps de l'envoi, puis on le rend tel quel.
        PixelUnpack unpack = PixelUnpack.neutral();
        int previous2d = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousArray = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
        try {
            Decoded d;
            while (!closed && (d = queue.poll()) != null) {
                try {
                    place(d);
                } finally {
                    free(d);
                }
                if (System.nanoTime() - start > UPLOAD_BUDGET_NANOS) {
                    break;
                }
            }
        } finally {
            unpack.restore();
            glBindTexture(GL_TEXTURE_2D, previous2d);
            glBindTexture(GL_TEXTURE_2D_ARRAY, previousArray);
        }
    }

    private void place(Decoded d) {
        Entry e = d.entry();
        if (d.error() != null) {
            if (!e.optional()) {
                if (failure == null) {
                    failure = d.error();
                }
                return;
            }
            // Image facultative absente : le groupe reste complet, l'effet ne sera pas dessiné.
            done(e.group());
            return;
        }
        int format = switch (e.channels()) {
            case 1 -> GL_RED;
            case 3 -> GL_RGB;
            default -> GL_RGBA;
        };
        int internal = switch (e.channels()) {
            case 1 -> GL_R8;
            case 3 -> GL_RGB8;
            default -> GL_RGBA8;
        };
        if (e.layer() >= 0) {
            Integer array = textures.get(SEA_FRAMES);
            if (array == null) {
                array = glGenTextures();
                glBindTexture(GL_TEXTURE_2D_ARRAY, array);
                glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, internal, d.width(), d.height(), seaFrames, 0, format,
                        GL_UNSIGNED_BYTE, (ByteBuffer) null);
                parameters(GL_TEXTURE_2D_ARRAY, GL_LINEAR);
                textures.put(SEA_FRAMES, array);
                bytes += (long) d.width() * d.height() * e.channels() * seaFrames;
            }
            glBindTexture(GL_TEXTURE_2D_ARRAY, array);
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, e.layer(), d.width(), d.height(), 1, format,
                    GL_UNSIGNED_BYTE, d.pixels());
        } else {
            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexImage2D(GL_TEXTURE_2D, 0, internal, d.width(), d.height(), 0, format, GL_UNSIGNED_BYTE, d.pixels());
            // Nuages et bancs sont lus au texel près (texelFetch) ; le reste est filtré comme View.sample.
            parameters(GL_TEXTURE_2D, GL_LINEAR);
            if (e.mipmap()) {
                // Sprites très réduits (fumée, halo des feux de camp) : sans mipmaps, ils scintilleraient.
                glGenerateMipmap(GL_TEXTURE_2D);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            }
            textures.put(e.path(), id);
            long size = (long) d.width() * d.height() * e.channels();
            bytes += e.mipmap() ? size * 4 / 3 : size;
        }
        done(e.group());
    }

    private static void parameters(int target, int filter) {
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private void done(String group) {
        remaining.merge(group, -1, Integer::sum);
    }

    /** Vrai quand toutes les images du groupe sont sur la carte graphique. Thread GL. */
    public boolean ready(String group) {
        Integer left = remaining.get(group);
        return left != null && left == 0;
    }

    /** Vrai quand tous les groupes sont prêts. */
    public boolean allReady() {
        for (int left : remaining.values()) {
            if (left != 0) {
                return false;
            }
        }
        return true;
    }

    /** Première image obligatoire qui n'a pas pu être lue ou décodée, sinon {@code null}. */
    public Throwable failure() {
        return failure;
    }

    /**
     * Mémoire vidéo estimée des textures envoyées, en octets : largeur x hauteur x canaux de chaque image (un tiers
     * de plus pour les mipmaps). Estimation, pas une mesure : le pilote peut aligner ou compléter les formats.
     */
    public long bytes() {
        return bytes;
    }

    /** Identifiant GL d'une texture envoyée (par son chemin, ou {@link #SEA_FRAMES}), sinon 0. */
    public int texture(String path) {
        Integer id = textures.get(path);
        return id == null ? 0 : id;
    }

    private static void free(Decoded d) {
        if (d != null && d.pixels() != null) {
            STBImage.stbi_image_free(d.pixels());
        }
    }

    private void drainQueue() {
        Decoded d;
        while ((d = queue.poll()) != null) {
            free(d);
        }
    }

    /** Arrête le décodage et libère textures et images en attente. Thread GL. */
    @Override
    public void close() {
        closed = true;
        Thread t = decoder;
        if (t != null) {
            t.interrupt();
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        drainQueue();
        for (int id : textures.values()) {
            glDeleteTextures(id);
        }
        textures.clear();
        bytes = 0L;
    }
}
