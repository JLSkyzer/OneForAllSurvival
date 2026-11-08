package dev.oneforallsurvival.earlywindow;

import dev.oneforallsurvival.earlywindow.scene.BitmapFont;
import dev.oneforallsurvival.earlywindow.scene.GlShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.ProgressModel;
import dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneRenderer;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.StatusMessages;
import dev.oneforallsurvival.earlywindow.scene.StoryClock;
import dev.oneforallsurvival.earlywindow.scene.Texts;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;

/**
 * La scène de chargement et tout son état, partagés de la fenêtre anticipée à la phase des ressources de
 * Minecraft : catalogue, textures, rendu, horloge du récit, barre, lecture de la progression de Forge, film.
 *
 * <p><b>Thread et contexte.</b> Tout passe par le contexte OpenGL de la fenêtre, et un seul thread le tient à la
 * fois : le thread {@code lostisland-render} de {@link EarlyRenderLoop} avant le passage de la fenêtre à
 * Minecraft, puis le thread principal du jeu (« Render thread »), qui reçoit ce même contexte : les textures restent
 * valides. Aucune méthode n'est sûre entre threads ; le passage (arrêt et attente du thread de rendu avant que le
 * thread du jeu ne prenne le contexte) fait la séparation. Après le passage, le pont ({@link GapBridge}) dessine
 * aussi la scène, dans son propre contexte partagé, avec son propre rendu ({@link #createLightRenderer}) : ses
 * images et celles du thread du jeu sont exclues l'une de l'autre par le verrou du pont. Seul le décodage des PNG tourne à part
 * ({@link TextureStore}).
 *
 * <p>Deux horloges : le temps réel part de l'ouverture de la fenêtre ; le récit ne part qu'une fois le ciel, le
 * décor et les effets prêts (ruling 14) : avant, c'est l'image t=0 (ouverture depuis le noir). La mer, décodée
 * en dernier (ruling 19), arrive en fondu.
 */
public final class EarlyScene implements AutoCloseable {
    private static final double SEA_FADE_SECONDS = 0.8;
    private static final String CLOUDS_ENTRY = "assets/minecraft/textures/environment/clouds.png";

    private final long startNanos;
    private final Consumer<String> log;
    private final Runnable journal;
    private final SceneManifest manifest;
    private final TextureStore textures;
    private final SceneRenderer renderer;
    private final BitmapFont font; // null : texte omis
    private final ZipFile clientJar; // null : nuages et flammes omis, police de repli
    private FrameRecorder recorder; // null : pas de film (ou film arrêté au passage à l'écran titre)
    private final StoryClock clock;
    private final ProgressModel progress = new ProgressModel();
    private final ProgressTracker tracker = new ProgressTracker();

    private double lastReal;
    private double storyStart = Double.NaN;
    private double seaStart = Double.NaN;
    private double uiStart = Double.NaN; // arrivée de l'interface : partagée par le rendu du jeu et celui du pont
    private double resourcesStart = Double.NaN;
    private Phase loggedPhase;
    private int frames;
    private SceneState last;
    private boolean closed;

    private EarlyScene(long startNanos, Consumer<String> log, Runnable journal, SceneManifest manifest,
                       TextureStore textures, SceneRenderer renderer, BitmapFont font, ZipFile clientJar,
                       FrameRecorder recorder, StoryClock clock) {
        this.startNanos = startNanos;
        this.clock = clock;
        this.log = log;
        this.journal = journal;
        this.manifest = manifest;
        this.textures = textures;
        this.renderer = renderer;
        this.font = font;
        this.clientJar = clientJar;
        this.recorder = recorder;
    }

    /**
     * Prépare la scène : catalogue, jar du client, police, shaders, puis lance le décodage des textures. À appeler
     * avec le contexte de la fenêtre courant.
     *
     * @param startNanos ouverture de la fenêtre ({@link System#nanoTime})
     * @param journal    relevé des barres et messages de Forge, appelé au plus une fois par seconde
     * @throws dev.oneforallsurvival.earlywindow.scene.ShaderLoader.ShaderException si un shader de base est refusé
     * @throws IllegalStateException si le catalogue manque
     */
    public static EarlyScene create(long startNanos, Path gameDir, String mcVersion, Consumer<String> log,
                                    Runnable journal) {
        return build(startNanos, gameDir, mcVersion, log, journal, new StoryClock(), true);
    }

    /**
     * Scène recréée pour l'écran titre dans le contexte du jeu, sans fenêtre anticipée (retour au titre après un
     * monde) : même catalogue, mêmes textures et même rendu, mais le récit est déjà sur l'aube tenue
     * ({@link StoryClock#atDawn}), sans film ni relevé de Forge. Les PNG se décodent dans leur thread
     * ({@link TextureStore}) et s'envoient image par image dans le budget habituel ({@link #uploadPending}) :
     * rien ne gèle le thread du jeu ; tant que {@link #readyToShow} est faux, l'appelant dessine son repli.
     * Thread du jeu, contexte du jeu courant ; l'appelant photographie et rend l'état GL autour.
     *
     * @throws RuntimeException comme {@link #create} (shader refusé, catalogue absent) : pas de scène
     */
    public static EarlyScene createForGame(Path gameDir, String mcVersion, Consumer<String> log) {
        return build(System.nanoTime(), gameDir, mcVersion, log, () -> { }, StoryClock.atDawn(), false);
    }

    private static EarlyScene build(long startNanos, Path gameDir, String mcVersion, Consumer<String> log,
                                    Runnable journal, StoryClock clock, boolean film) {
        ClassLoader loader = EarlyScene.class.getClassLoader();
        SceneManifest manifest;
        try (InputStream in = loader.getResourceAsStream("lostisland/loading/manifest.json")) {
            if (in == null) {
                throw new IllegalStateException("lostisland/loading/manifest.json absent du jar de service");
            }
            manifest = SceneManifest.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Textes de la langue du jeu (options.txt), avant toute image : messages d'état, repères, devise.
        String gameLanguage = Texts.gameLanguage(gameDir);
        Texts texts = Texts.load(gameLanguage, loader::getResourceAsStream, log);
        Texts.install(texts);
        log.accept("langue : " + texts.origin() + " (options.txt : " + (gameLanguage == null ? "aucune" : gameLanguage)
                + ")");
        ZipFile jar;
        try {
            jar = openClientJar(mcVersion, log);
        } catch (RuntimeException e) {
            // Le jar du client ne sert qu'aux nuages, aux flammes et à la police : son échec ne retire qu'eux.
            log.accept("jar du client : recherche en échec (" + e + ") : nuages et flammes omis, police de repli");
            jar = null;
        }
        TextureStore textures = null;
        try {
            Function<String, InputStream> opener = opener(loader, jar);
            textures = new TextureStore(manifest, opener);
            BitmapFont font = BitmapFont.loadOrFallback(path -> opener.apply("minecraft:" + path),
                    EarlyScene::monocraft, log);
            log.accept("police : " + (font == null ? "aucune (texte omis)" : font.origin()));
            SceneRenderer renderer = new SceneRenderer(manifest, textures, new GlShaderLoader(loader), log, font);
            renderer.setSeaOpacity(0f);
            log.accept("effets actifs : " + renderer.activeEffects());
            textures.start();
            FrameRecorder recorder = film
                    ? FrameRecorder.maybe(System.getProperty("lostisland.frames"), gameDir, log) : null;
            return new EarlyScene(startNanos, log, journal, manifest, textures, renderer, font, jar, recorder, clock);
        } catch (RuntimeException | Error e) {
            if (textures != null) {
                textures.close();
            }
            closeQuietly(jar);
            throw e;
        }
    }

    /**
     * Dessine une image dans le framebuffer lié, sur {@code fbWidth} x {@code fbHeight}, et la verse au film.
     * Envoie d'abord les textures décodées, lit la progression de Forge et avance le récit.
     *
     * @throws IllegalStateException si une image obligatoire est illisible ({@link TextureStore#failure()}) ou si le
     *                               tampon de la scène est incomplet : l'appelant retombe sur Forge
     */
    public void render(int fbWidth, int fbHeight) {
        renderFrame(renderer, fbWidth, fbHeight, null, 1f);
    }

    /**
     * Rendu allégé du pont ({@link GapBridge}) : sans vent ni bloom, à créer avec le contexte du pont courant. Ses
     * tampons, VAO et programmes sont à lui (objets non partageables, ou partageables mais propres au pont) ; il ne
     * fait que lire les textures de la scène, partagées entre les deux contextes, et ne les détruit jamais : sa
     * fermeture ne libère que ce qu'il a créé.
     */
    SceneRenderer createLightRenderer() {
        return new SceneRenderer(manifest, textures, new GlShaderLoader(EarlyScene.class.getClassLoader()), log, font,
                true);
    }

    /** Comme {@link #render(int, int)}, avec un autre rendu (celui du pont). Même état : un seul thread à la fois. */
    void renderWith(SceneRenderer other, int fbWidth, int fbHeight) {
        renderFrame(other, fbWidth, fbHeight, null, 1f);
    }

    /**
     * Image de la phase des ressources de Minecraft, dessinée par l'overlay du mod dans le framebuffer lié : la
     * barre suit le rechargement de Minecraft ({@code reloadProgress}, de 0 à 1) au lieu des barres de Forge,
     * sans jamais devancer le temps écoulé rapporté à la durée attendue ({@link ProgressModel#resourcesFraction}),
     * puis, le rechargement fini ({@code done}), passe à la fin. {@code uiAlpha} estompe la barre et le texte
     * (le logo reste). La barre ne recule pas au passage de la phase Forge à celle-ci ({@link ProgressModel}).
     *
     * @throws IllegalStateException comme {@link #render(int, int)}
     */
    public void renderResources(int fbWidth, int fbHeight, float reloadProgress, boolean done, float uiAlpha) {
        Phase phase = done ? Phase.DONE : Phase.RESOURCES;
        if (Double.isNaN(resourcesStart)) {
            resourcesStart = realTime();
        }
        // L'avancement réel de Minecraft court devant le temps : lissé par la durée attendue (ruling 26).
        float fraction = done ? 1f : ProgressModel.resourcesFraction(reloadProgress, realTime() - resourcesStart);
        renderFrame(renderer, fbWidth, fbHeight, new ProgressTracker.Reading(phase, fraction,
                StatusMessages.of(phase, "", 0, 0)), uiAlpha);
    }

    /**
     * Image de l'écran titre, dans le framebuffer lié : chargement fini (le récit accélère jusqu'à l'aube puis la
     * tient, la devise paraît avec elle), barre et texte effacés ({@code uiAlpha} 0), logo à sa place. Pas de film.
     *
     * @throws IllegalStateException comme {@link #render(int, int)}
     */
    public void renderTitle(int fbWidth, int fbHeight) {
        renderFrame(renderer, fbWidth, fbHeight, new ProgressTracker.Reading(Phase.DONE, 1f,
                StatusMessages.of(Phase.DONE, "", 0, 0)), 0f, false);
    }

    /**
     * Vrai quand ciel, décor, effets et interface (logo) sont sur la carte graphique : la scène peut paraître. La
     * mer, la plus lourde, arrive ensuite en fondu.
     */
    public boolean readyToShow() {
        return textures.ready(TextureStore.SKY) && textures.ready(TextureStore.PLATES)
                && textures.ready(TextureStore.FX) && textures.ready(TextureStore.UI);
    }

    /**
     * Envoie les images décodées, dans le budget par image de {@link TextureStore#upload}, sans dessiner : images
     * d'attente d'une scène recréée. Thread du jeu.
     *
     * @throws IllegalStateException si une image obligatoire est illisible
     */
    public void uploadPending() {
        textures.upload();
        Throwable failure = textures.failure();
        if (failure != null) {
            throw new IllegalStateException("image obligatoire illisible : " + failure.getMessage(), failure);
        }
    }

    /** Mémoire vidéo estimée des textures de la scène, en octets ({@link TextureStore#bytes}). */
    public long textureBytes() {
        return textures.bytes();
    }

    /** Arrête le film de preuve s'il tourne : il ne couvre que le chargement. Thread qui tient le contexte. */
    public void stopRecording() {
        if (recorder != null) {
            recorder.close();
            recorder = null;
        }
    }

    /** {@code given} : avancement imposé par l'appelant ; {@code null} : lu dans les barres de Forge. */
    private void renderFrame(SceneRenderer target, int fbWidth, int fbHeight, ProgressTracker.Reading given,
                             float uiAlpha) {
        renderFrame(target, fbWidth, fbHeight, given, uiAlpha, true);
    }

    private void renderFrame(SceneRenderer target, int fbWidth, int fbHeight, ProgressTracker.Reading given,
                             float uiAlpha, boolean record) {
        double real = realTime();
        double dt = Math.max(0.0, real - lastReal);
        lastReal = real;
        textures.upload();
        Throwable failure = textures.failure();
        if (failure != null) {
            throw new IllegalStateException("image obligatoire illisible : " + failure.getMessage(), failure);
        }
        ProgressTracker.Reading reading;
        if (given == null) {
            reading = readProgress(real);
        } else {
            journal.run();
            tracker.enter(given.phase(), real);
            reading = given;
            logPhase(reading);
        }
        progress.report(reading.phase(), reading.fraction());
        float shown = progress.displayed(dt);
        if (Double.isNaN(storyStart) && textures.ready(TextureStore.SKY) && textures.ready(TextureStore.PLATES)
                && textures.ready(TextureStore.FX)) {
            storyStart = real;
            log.accept(String.format(Locale.ROOT, "récit lancé (ciel, décor et effets prêts) à %.2f s", real));
        }
        if (Double.isNaN(uiStart) && textures.ready(TextureStore.UI)) {
            uiStart = real;
            log.accept(String.format(Locale.ROOT, "interface prête à %.2f s (barre en fondu)", real));
        }
        if (Double.isNaN(seaStart) && textures.ready(TextureStore.SEA)) {
            seaStart = real;
            log.accept(String.format(Locale.ROOT, "mer prête à %.2f s (fondu de %.1f s)", real, SEA_FADE_SECONDS));
        }
        // Avant le départ du récit : l'image t=0 (ouverture depuis le noir), ou l'aube pour une horloge déjà tenue.
        float story = Double.isNaN(storyStart) ? clock.storyTime(0.0, false)
                : clock.storyTime(real - storyStart, reading.phase() == Phase.DONE);
        target.setSeaOpacity(Double.isNaN(seaStart) ? 0f : (float) Math.min(1.0, (real - seaStart) / SEA_FADE_SECONDS));
        last = new SceneState(story, shown, reading.status(), Math.max(0f, Math.min(1f, uiAlpha)), real,
                real - uiStart);
        target.render(fbWidth, fbHeight, last);
        if (record && recorder != null) {
            recorder.capture(fbWidth, fbHeight);
        }
        if (frames++ == 0) {
            log.accept(String.format(Locale.ROOT, "première image %dx%d", fbWidth, fbHeight));
        }
    }

    private ProgressTracker.Reading readProgress(double real) {
        List<ProgressTracker.Meter> meters = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        try {
            for (ProgressMeter m : StartupNotificationManager.getCurrentProgress()) {
                meters.add(new ProgressTracker.Meter(m.name(), m.label() == null ? null : m.label().getText(),
                        m.current(), m.steps()));
            }
            for (StartupNotificationManager.AgeMessage m : StartupNotificationManager.getMessages()) {
                messages.add(m.message().getText());
            }
        } catch (RuntimeException e) {
            // Un relevé manqué ne doit pas arrêter l'image : la phase courante est gardée.
        }
        journal.run();
        ProgressTracker.Reading reading = tracker.update(real, meters, messages);
        logPhase(reading);
        return reading;
    }

    private void logPhase(ProgressTracker.Reading reading) {
        if (reading.phase() != loggedPhase) {
            loggedPhase = reading.phase();
            log.accept("phase " + reading.phase() + " : " + reading.status().line());
        }
    }

    /** Fait passer la barre à une phase (overlay des ressources, fin), sans jamais reculer. */
    public void enter(Phase phase) {
        tracker.enter(phase, realTime());
    }

    /** Une ligne au journal de la fenêtre anticipée ({@code logs/lostisland-earlywindow.log}). */
    public void log(String line) {
        log.accept(line);
    }

    /** Secondes depuis l'ouverture de la fenêtre. */
    public double realTime() {
        return (System.nanoTime() - startNanos) / 1e9;
    }

    public SceneManifest manifest() {
        return manifest;
    }

    public TextureStore textures() {
        return textures;
    }

    public SceneRenderer renderer() {
        return renderer;
    }

    public StoryClock clock() {
        return clock;
    }

    public ProgressModel progress() {
        return progress;
    }

    public ProgressTracker tracker() {
        return tracker;
    }

    /** Dernier état dessiné ({@code null} avant la première image). */
    public SceneState lastState() {
        return last;
    }

    public boolean closed() {
        return closed;
    }

    /** Libère shaders, textures, film et jar du client. Thread qui tient le contexte. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        renderer.close();
        textures.close();
        stopRecording();
        closeQuietly(clientJar);
    }

    // --- Fichiers ------------------------------------------------------------------------------------------

    /** Calques dans notre jar ({@code lostisland/loading/}), fichiers de Minecraft ({@code minecraft:}) dans le sien. */
    private static Function<String, InputStream> opener(ClassLoader loader, ZipFile jar) {
        return path -> {
            if (!path.startsWith("minecraft:")) {
                return loader.getResourceAsStream("lostisland/loading/" + path);
            }
            if (jar == null) {
                return null;
            }
            ZipEntry entry = jar.getEntry("assets/minecraft/" + path.substring("minecraft:".length()));
            try {
                return entry == null ? null : jar.getInputStream(entry);
            } catch (IOException e) {
                return null;
            }
        };
    }

    /** Monocraft.ttf, ressource de fmlearlydisplay : lue par son module, notre classloader ne la voit pas. */
    private static InputStream monocraft() {
        try {
            InputStream in = DisplayWindow.class.getModule().getResourceAsStream("Monocraft.ttf");
            return in != null ? in : DisplayWindow.class.getResourceAsStream("/Monocraft.ttf");
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Jar du client de Minecraft (nuages, flammes, police) : lu en place, rien n'est copié dans le nôtre. Cherché
     * dans le classpath du lancement ({@code legacyClassPath} ou {@code legacyClassPath.file} du BootstrapLauncher :
     * {@code client-extra.jar} en développement), puis sous {@code libraryDirectory}
     * ({@code net/minecraft/client/<version>-…/client-…-extra.jar} d'une installation Forge). Retenu s'il contient
     * {@code clouds.png} ; {@code null} sinon (nuages et flammes omis, police de repli). Une entrée mal formée
     * est ignorée ({@link #pathOrNull}).
     */
    static ZipFile openClientJar(String mcVersion, Consumer<String> log) {
        Set<Path> candidates = new LinkedHashSet<>();
        List<String> classpath = new ArrayList<>();
        String inline = System.getProperty("legacyClassPath");
        if (inline != null) {
            classpath.addAll(List.of(inline.split(File.pathSeparator)));
        }
        String listFile = System.getProperty("legacyClassPath.file");
        if (listFile != null) {
            try {
                classpath.addAll(Files.readAllLines(Paths.get(listFile)));
            } catch (IOException | RuntimeException e) {
                log.accept("jar du client : " + listFile + " illisible (" + e.getMessage() + ")");
            }
        }
        // Les jars du client d'abord (client-extra.jar), puis le reste de ce qui ressemble à Minecraft.
        for (boolean clientOnly : new boolean[] {true, false}) {
            for (String entry : classpath) {
                Path path = pathOrNull(entry);
                Path file = path == null ? null : path.getFileName();
                String name = file == null ? "" : file.toString().toLowerCase(Locale.ROOT);
                boolean client = name.startsWith("client") && name.endsWith(".jar");
                boolean likely = name.contains("minecraft") || name.startsWith("forge-");
                if (clientOnly ? client : likely) {
                    candidates.add(path);
                }
            }
        }
        String libraries = System.getProperty("libraryDirectory");
        Path clientDir = libraries == null ? null : pathOrNull(libraries);
        if (clientDir != null) {
            clientDir = clientDir.resolve(Paths.get("net", "minecraft", "client"));
            if (Files.isDirectory(clientDir)) {
                try (Stream<Path> files = Files.walk(clientDir, 2)) {
                    files.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith("-extra.jar") && (mcVersion == null || n.contains(mcVersion));
                    }).forEach(candidates::add);
                } catch (IOException | RuntimeException e) {
                    log.accept("jar du client : " + clientDir + " illisible (" + e.getMessage() + ")");
                }
            }
        }
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                ZipFile zip = new ZipFile(candidate.toFile());
                if (zip.getEntry(CLOUDS_ENTRY) != null) {
                    log.accept("jar du client : " + candidate);
                    return zip;
                }
                zip.close();
            } catch (IOException e) {
                // Pas un zip lisible : candidat suivant.
            }
        }
        log.accept("jar du client introuvable (" + candidates.size() + " candidats) : nuages omis, police de repli");
        return null;
    }

    /**
     * Chemin d'une entrée de classpath, ou {@code null} si elle est mal formée : une entrée illisible ne retire
     * que les fonctions du jar du client (nuages, flammes, police), jamais toute la scène.
     */
    static Path pathOrNull(String entry) {
        try {
            String trimmed = entry.trim();
            return trimmed.isEmpty() ? null : Paths.get(trimmed);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static void closeQuietly(ZipFile zip) {
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException e) {
                // rien à faire
            }
        }
    }
}
