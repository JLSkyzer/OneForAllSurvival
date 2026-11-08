package dev.oneforallsurvival.earlywindow;

import static org.lwjgl.glfw.GLFW.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import net.minecraftforge.fml.loading.ImmediateWindowProvider;
import net.minecraftforge.fml.loading.progress.Message;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Fenêtre de chargement anticipée « lostisland », choisie par {@code earlyWindowProvider} dans
 * {@code config/fml.toml}. Calquée sur le {@link DisplayWindow} de Forge pour la fenêtre, le thread de rendu et
 * le passage à Minecraft ; elle dessine la scène Lost Island ({@link EarlyScene}) au lieu de l'écran de Forge.
 *
 * <p><b>Déroulé et threads.</b>
 * <ol>
 *   <li>{@link #initialize} (thread principal de FML) : fenêtre GLFW et contexte (même suite de versions GL que
 *       Forge, 4.6 à 3.2 core), première image noire, scène préparée (shaders compilés, police, catalogue), puis
 *       thread {@code lostisland-render} ({@link EarlyRenderLoop}) qui tient le contexte et dessine à chaque image
 *       en lisant lui-même la progression de Forge (ruling 3).</li>
 *   <li>{@link #setupMinecraftWindow} (thread du jeu) : arrêt du thread de rendu, qui rend le contexte, puis le
 *       contexte et la fenêtre passent au jeu, comme chez Forge.</li>
 *   <li>{@link #periodicTick} (thread du jeu, pendant le chargement des mods seulement) : la scène est dessinée sur
 *       ce thread, l'état GL du jeu sauvegardé et rendu autour.</li>
 *   <li>Pont ({@link GapBridge}, après le passage) : quand le thread du jeu ne dessine plus depuis
 *       {@value GapBridge#TAKEOVER_MILLIS} ms (surtout entre la fin du chargement des mods et l'overlay des
 *       ressources), un fil allégé (sans bloom ni vent, 30 i/s) dessine la scène avec un second contexte partagé ;
 *       il s'arrête à {@link #loadingOverlay}. Réglage et garde-fous : {@link BridgePolicy}.</li>
 *   <li>{@link #loadingOverlay} : l'overlay du mod ({@code LostIslandLoadingOverlay}), qui dessine la scène dans
 *       le contexte du jeu ; s'il manque ou échoue, celui de Forge ({@link #forgeOverlay}), qui dessine notre scène
 *       par {@link ForgeOverlayBridge}.</li>
 * </ol>
 *
 * <p><b>Repli sur Forge.</b> Si notre initialisation échoue (fenêtre, contexte, shader de base, catalogue, ou
 * {@code -Dlostisland.earlywindow.fail=true}), tout est délégué au {@code DisplayWindow} de Forge, initialisé à ce
 * moment-là. Si une image échoue ensuite dans le thread de rendu (image obligatoire illisible, tampon incomplet),
 * le repli a lieu au prochain appel de Forge sur le thread principal avant le passage au jeu
 * ({@link #updateModuleReads} ou {@link #setupMinecraftWindow}) : notre fenêtre est détruite, celle de Forge
 * s'ouvre. Après le passage, la fenêtre appartient au jeu : un échec ne fait plus que cesser notre rendu.
 *
 * <p>Journal : {@code logs/lostisland-earlywindow.log}, avec le temps écoulé depuis {@link #initialize}.
 */
public final class LostIslandWindow implements ImmediateWindowProvider {
    private static final String NAME = "lostisland";
    private static final long LOG_INTERVAL_NANOS = 1_000_000_000L;
    private static final int KEPT_LOGS = 5;
    private static final long MAIN_FRAME_NANOS = 1_000_000_000L / 60;
    private static final int[][] GL_VERSIONS = {{4, 6}, {4, 5}, {4, 4}, {4, 3}, {4, 2}, {4, 1}, {4, 0}, {3, 3}, {3, 2}};
    private static final String MOD_MODULE = "oneforallsurvival";
    private static final String MOD_OVERLAY = "dev.oneforallsurvival.client.loading.LostIslandLoadingOverlay";

    private static volatile EarlyScene shared;

    private final DisplayWindow forge = new DisplayWindow();

    // Journal de la progression : messages déjà écrits (par identité), date du dernier relevé des barres.
    private final Set<Message> seenMessages = Collections.newSetFromMap(new IdentityHashMap<>());
    private long startNanos = System.nanoTime();
    private long lastLogNanos;
    private BufferedWriter log;

    private String[] arguments = new String[0];
    private volatile boolean delegated;
    private long window;
    private GLCapabilities capabilities;
    private String glVersion;
    private String glRenderer;
    private BridgePolicy.Setting bridgeSetting = BridgePolicy.Setting.AUTO;
    private GapBridge bridge; // null : pas de pont (décision, échec, ou déjà arrêté et détruit)
    private boolean bridgeWaited;
    private boolean bridgeLate;
    private boolean bridgeQuiet;
    private EarlyScene scene;
    private EarlyRenderLoop loop;
    private ModuleLayer gameLayer;
    private Method forgeOverlay;
    private boolean handedOff;
    private boolean overlayRequested;
    private boolean mainFailed;
    private long nextMainFrame;
    // Géométrie de la fenêtre : rappels GLFW (thread principal) lus par le thread de rendu.
    private volatile int fbWidth;
    private volatile int fbHeight;
    private volatile int winWidth;
    private volatile int winHeight;
    private volatile int winX;
    private volatile int winY;

    /**
     * La scène partagée avec l'overlay des ressources du mod (tâche 8) : textures, rendu, horloge, barre. Elle vit
     * dans le contexte de la fenêtre, devenu celui de Minecraft : à n'utiliser que sur le thread du jeu, après le
     * passage. {@code null} si la fenêtre est celle de Forge (repli) ou si la scène a été libérée.
     */
    public static EarlyScene sceneState() {
        EarlyScene s = shared;
        return s == null || s.closed() ? null : s;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Runnable initialize(String[] arguments) {
        this.arguments = arguments.clone();
        startNanos = System.nanoTime();
        lastLogNanos = startNanos - LOG_INTERVAL_NANOS;
        openLog(gameDir(arguments).resolve("logs").resolve("lostisland-earlywindow.log"));
        write("initialize : fournisseur " + NAME);
        bridgeSetting = BridgePolicy.resolve(System.getProperty(BridgePolicy.PROPERTY), gameDir(arguments).resolve("config"),
                this::write);
        // Essais du repli : true (avant notre fenêtre), late (après la fenêtre et la scène), render (dans le
        // thread de rendu, repli au prochain appel de Forge sur le thread principal).
        String fail = System.getProperty("lostisland.earlywindow.fail", "");
        try {
            if (fail.equals("true")) {
                throw new IllegalStateException("échec demandé par -Dlostisland.earlywindow.fail=true");
            }
            startOwn(fail.equals("render") ? 60 : 0);
            if (fail.equals("late")) {
                throw new IllegalStateException("échec demandé par -Dlostisland.earlywindow.fail=late");
            }
        } catch (Throwable t) {
            write("initialize : échec de notre fenêtre (" + t + "), repli sur le DisplayWindow de Forge");
            teardownOwn();
            delegateToForge();
        }
        return this::periodicTick;
    }

    // --- Notre fenêtre -------------------------------------------------------------------------------------

    private void startOwn(int failAfterFrames) {
        String mcVersion = option(arguments, "--fml.mcVersion");
        String forgeVersion = option(arguments, "--fml.forgeVersion");
        int width = intOption(arguments, "--width", FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH));
        int height = intOption(arguments, "--height",
                FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT));
        boolean maximized = Arrays.asList(arguments).contains("--earlywindow.maximized")
                || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);
        createWindow(mcVersion, width, height, maximized);

        // Le contexte, le temps de préparer la scène sur ce thread : un shader refusé lève ici, avant tout rendu.
        glfwMakeContextCurrent(window);
        capabilities = GL.createCapabilities();
        glRenderer = GL32C.glGetString(GL32C.GL_RENDERER);
        write("GL : " + GL32C.glGetString(GL32C.GL_VENDOR) + " / " + glRenderer + " / "
                + GL32C.glGetString(GL32C.GL_VERSION));
        // Quelque chose tout de suite : la fenêtre s'ouvre noire, comme la première image du récit.
        GL32C.glClearColor(0f, 0f, 0f, 1f);
        GL32C.glClear(GL32C.GL_COLOR_BUFFER_BIT);
        glfwSwapBuffers(window);
        long sceneBegin = System.nanoTime();
        scene = EarlyScene.create(startNanos, gameDir(arguments), mcVersion, this::write, this::journal);
        write(String.format(Locale.ROOT, "scène prête en %.2f s", (System.nanoTime() - sceneBegin) / 1e9));
        glfwMakeContextCurrent(0L);

        loop = new EarlyRenderLoop(window, capabilities, scene, () -> fbWidth, () -> fbHeight, this::write,
                failAfterFrames);
        loop.start();
        shared = scene;
        // Comme Forge : le message de démarrage ; il fait passer la barre à la découverte des mods.
        StartupNotificationManager.modLoaderConsumer().ifPresent(c -> c.accept("Forge loading " + forgeVersion));
        write("initialize : notre fenêtre est prête, thread de rendu lancé");
    }

    /** Fenêtre GLFW comme {@code DisplayWindow.initWindow} : mêmes réglages, même suite de versions GL. */
    private void createWindow(String mcVersion, int width, int height, boolean maximized) {
        if (!glfwInit()) {
            throw new IllegalStateException("glfwInit a échoué");
        }
        glfwGetError(null); // efface une erreur précédente, comme Forge
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        String title = "Minecraft* " + (mcVersion == null ? "" : mcVersion);
        glfwWindowHintString(GLFW_X11_CLASS_NAME, title);
        glfwWindowHintString(GLFW_X11_INSTANCE_NAME, title);
        long monitor = glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            throw new IllegalStateException("aucun écran principal (glfwGetPrimaryMonitor)");
        }
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        if (mode == null) {
            throw new IllegalStateException("résolution de l'écran inconnue (glfwGetVideoMode)");
        }
        List<String> skip = FMLConfig.getListConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_SKIP_GL_VERSIONS);
        long created = 0L;
        for (int[] v : GL_VERSIONS) {
            String version = v[0] + "." + v[1];
            if (skip.contains(version)) {
                continue;
            }
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, v[0]);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, v[1]);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            created = glfwCreateWindow(width, height, title, 0L, 0L);
            glfwGetError(null);
            if (created != 0L) {
                break;
            }
        }
        if (created == 0L) {
            throw new IllegalStateException("aucun contexte OpenGL 3.2 à 4.6 core");
        }
        window = created;
        glVersion = glfwGetWindowAttrib(window, GLFW_CONTEXT_VERSION_MAJOR) + "."
                + glfwGetWindowAttrib(window, GLFW_CONTEXT_VERSION_MINOR);
        write("fenêtre " + width + "x" + height + ", GL " + glVersion);
        int[] x = new int[1];
        int[] y = new int[1];
        glfwGetMonitorPos(monitor, x, y);
        int monitorX = x[0];
        int monitorY = y[0];
        if (maximized) {
            glfwMaximizeWindow(window);
        }
        glfwGetWindowSize(window, x, y);
        winWidth = x[0];
        winHeight = y[0];
        glfwSetWindowPos(window, (mode.width() - winWidth) / 2 + monitorX, (mode.height() - winHeight) / 2 + monitorY);
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            if (w != 0 && h != 0) {
                fbWidth = w;
                fbHeight = h;
            }
        });
        glfwSetWindowPosCallback(window, (win, px, py) -> {
            winX = px;
            winY = py;
        });
        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            if (w != 0 && h != 0) {
                winWidth = w;
                winHeight = h;
            }
        });
        glfwShowWindow(window);
        refreshGeometry();
        glfwPollEvents();
    }

    /** Relit taille, position et tampon de la fenêtre. Thread principal. */
    private void refreshGeometry() {
        int[] a = new int[1];
        int[] b = new int[1];
        glfwGetWindowPos(window, a, b);
        winX = a[0];
        winY = b[0];
        glfwGetWindowSize(window, a, b);
        if (a[0] > 0 && b[0] > 0) {
            winWidth = a[0];
            winHeight = b[0];
        }
        glfwGetFramebufferSize(window, a, b);
        if (a[0] > 0 && b[0] > 0) {
            fbWidth = a[0];
            fbHeight = b[0];
        }
    }

    /** Défait notre fenêtre (thread principal, avant le passage au jeu). */
    private void teardownOwn() {
        shared = null;
        if (loop != null) {
            if (!loop.stopAndJoin(5000)) {
                write("le thread de rendu ne rend pas le contexte : la fenêtre est abandonnée sans être libérée");
                loop = null;
                window = 0L;
                return;
            }
            loop = null;
        }
        if (window != 0L) {
            try {
                glfwMakeContextCurrent(window);
                if (capabilities != null) {
                    GL.setCapabilities(capabilities);
                }
                if (scene != null) {
                    scene.close();
                }
            } catch (Throwable t) {
                write("libération de la scène incomplète : " + t);
            } finally {
                scene = null;
                glfwMakeContextCurrent(0L);
                GL.setCapabilities(null);
                Callbacks.glfwFreeCallbacks(window);
                glfwDestroyWindow(window);
                window = 0L;
            }
        }
    }

    private void delegateToForge() {
        delegated = true;
        forge.initialize(arguments);
        write("DisplayWindow de Forge prêt");
        if (gameLayer != null) {
            forge.updateModuleReads(gameLayer);
        }
    }

    /** Repli tardif : une image a échoué dans le thread de rendu avant le passage au jeu. Thread principal. */
    private void fallBackIfRenderFailed() {
        if (delegated || handedOff || loop == null || loop.failure() == null) {
            return;
        }
        write("repli sur Forge après l'échec du thread de rendu : " + loop.failure());
        teardownOwn();
        delegateToForge();
    }

    // --- Appels de Forge et du jeu ------------------------------------------------------------------------

    @Override
    public void updateFramebufferSize(IntConsumer width, IntConsumer height) {
        if (delegated) {
            forge.updateFramebufferSize(width, height);
            return;
        }
        width.accept(fbWidth);
        height.accept(fbHeight);
    }

    @Override
    public long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title,
                                     LongSupplier monitor) {
        fallBackIfRenderFailed();
        if (delegated) {
            write("setupMinecraftWindow : la fenêtre de Forge passe à Minecraft");
            return forge.setupMinecraftWindow(width, height, title, monitor);
        }
        ImmediateWindowHandler.updateProgress("Initializing Game Graphics");
        if (!loop.stopAndJoin(30_000)) {
            throw new IllegalStateException("le thread de rendu ne rend pas le contexte de la fenêtre");
        }
        if (loop.failure() != null) {
            // Échec pendant la dernière image : il est encore temps de donner la main à Forge.
            fallBackIfRenderFailed();
            return forge.setupMinecraftWindow(width, height, title, monitor);
        }
        // Le second contexte se crée ici : thread principal (glfwCreateWindow l'exige), et aucun contexte n'est
        // courant nulle part (le thread de rendu vient de rendre celui de la fenêtre, le jeu ne l'a pas encore pris).
        createBridge();
        glfwMakeContextCurrent(window);
        glfwSetWindowTitle(window, title.get());
        glfwSwapInterval(0);
        // Le jeu pose ses propres rappels : on libère les nôtres, comme Forge.
        freeCallback(glfwSetFramebufferSizeCallback(window, null));
        freeCallback(glfwSetWindowPosCallback(window, null));
        freeCallback(glfwSetWindowSizeCallback(window, null));
        refreshGeometry();
        handedOff = true;
        write("setupMinecraftWindow : la fenêtre et son contexte passent à Minecraft");
        if (bridge != null) {
            bridge.start();
        }
        return window;
    }

    /** Décide du pont et crée son contexte. Toute erreur : pas de pont, comportement d'avant. Thread principal. */
    private void createBridge() {
        if (scene == null || scene.closed()) {
            return;
        }
        BridgePolicy.Decision decision = BridgePolicy.decide(bridgeSetting, Runtime.getRuntime().availableProcessors(),
                glVersion, glRenderer, org.lwjgl.system.Platform.get() == org.lwjgl.system.Platform.WINDOWS);
        write("pont : " + (decision.enabled() ? "actif" : "désactivé") + " (" + decision.reason()
                + ", availableProcessors=" + Runtime.getRuntime().availableProcessors() + ")");
        if (!decision.enabled()) {
            return;
        }
        try {
            bridge = GapBridge.create(window, glVersion, scene, this::write,
                    Boolean.getBoolean("lostisland.bridgeThread.fail"));
            write("pont : second contexte créé (fenêtre cachée, GL " + glVersion + ", partagé avec celui du jeu)");
        } catch (Throwable t) {
            bridge = null;
            write("pont : second contexte impossible, l'image se figera au passage comme avant : " + t);
        }
    }

    /**
     * Arrête le pont et attend qu'il ne puisse plus dessiner ({@link GapBridge#stopAndQuiesce}) ; sa fenêtre cachée
     * est détruite dès que son thread a fini. Appelé par {@link #loadingOverlay}, puis par chaque overlay avant de
     * dessiner et avant de libérer la scène (signal explicite : tant que cette méthode rend {@code false}, rien ne
     * touche la scène ni ses textures). La première attente dure au plus 1 s, les suivantes ne font que regarder.
     * Thread du jeu (thread principal de GLFW).
     *
     * @return {@code true} si aucun pont ne peut plus dessiner
     */
    public boolean awaitBridgeStopped() {
        GapBridge b = bridge;
        if (b == null) {
            return true;
        }
        if (bridgeQuiet) {
            reapBridge();
            return true;
        }
        long begin = System.nanoTime();
        boolean quiet = b.stopAndQuiesce(bridgeWaited ? 0L : 1000L);
        bridgeWaited = true;
        if (!quiet) {
            if (!bridgeLate) {
                bridgeLate = true;
                write("pont : pas encore arrêté au signal, l'overlay attend sans toucher la scène");
            }
            return false;
        }
        bridgeQuiet = true;
        write(String.format(Locale.ROOT, "pont : ne dessine plus (signal attendu %.1f ms%s), %d images dessinées en tout",
                (System.nanoTime() - begin) / 1e6, bridgeLate ? ", après un retard" : "", b.frames()));
        reapBridge();
        return true;
    }

    /** Fenêtre cachée détruite une fois le thread du pont fini (arrêt ou erreur). Thread principal. */
    private void reapBridge() {
        GapBridge b = bridge;
        if (b == null) {
            return;
        }
        if (b.finished()) {
            b.destroyWindow();
            bridge = null;
        } else if (b.dead()) {
            write("pont : thread mort sans rendre son contexte, fenêtre cachée laissée en place");
            bridge = null;
        }
    }

    private static void freeCallback(org.lwjgl.system.Callback callback) {
        if (callback != null) {
            callback.free();
        }
    }

    @Override
    public boolean positionWindow(Optional<Object> monitor, IntConsumer widthSetter, IntConsumer heightSetter,
                                  IntConsumer xSetter, IntConsumer ySetter) {
        if (delegated) {
            return forge.positionWindow(monitor, widthSetter, heightSetter, xSetter, ySetter);
        }
        widthSetter.accept(winWidth);
        heightSetter.accept(winHeight);
        xSetter.accept(winX);
        ySetter.accept(winY);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex,
                                          boolean fade) {
        if (delegated) {
            write("loadingOverlay : phase ressources de Minecraft, overlay de Forge (fenêtre de Forge)");
            return forge.loadingOverlay(mc, ri, ex, fade);
        }
        overlayRequested = true;
        // Le pont s'arrête ici, avant que la boucle du jeu ne vide le tampon de la fenêtre (RenderSystem.clear au
        // début de Minecraft.runTick, avant même l'overlay) : plus aucune image de lui ensuite.
        // Pont en retard : la phase n'est pas touchée ici (état non sûr entre threads) ; la première image de
        // l'overlay, une fois le pont arrêté, y entre de toute façon.
        if (awaitBridgeStopped() && scene != null && !scene.closed()) {
            scene.enter(dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase.RESOURCES);
        }
        try {
            Method own = modOverlay();
            Supplier<T> overlay = (Supplier<T>) own.invoke(null, mc, ri, ex, this);
            write("loadingOverlay : overlay du mod (" + MOD_OVERLAY + ")");
            return overlay;
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null
                    ? ite.getCause() : t;
            write("sansLeModLOverlayDeForgePrendLaMain : " + cause);
        }
        return forgeOverlay(mc, ri, ex);
    }

    /**
     * L'overlay des ressources de Forge, qui dessine notre scène par {@link ForgeOverlayBridge} : repli quand
     * l'overlay du mod manque, échoue à sa création, ou échoue à sa première image (il l'appelle alors lui-même,
     * sur le thread du jeu, avec les mêmes arguments, et se remplace par lui).
     */
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> forgeOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex) {
        if (forgeOverlay == null) {
            throw new IllegalStateException("ForgeLoadingOverlay introuvable : updateModuleReads n'a pas eu lieu");
        }
        try {
            if (scene == null || scene.closed() || mainFailed) {
                write("loadingOverlay : scène indisponible, l'overlay de Forge dessinera une image noire");
            }
            DisplayWindow forgeBridge = new ForgeOverlayBridge(scene, window, this::write, this::awaitBridgeStopped);
            write("loadingOverlay : overlay de Forge, qui dessine notre scène");
            return (Supplier<T>) forgeOverlay.invoke(null, mc, ri, ex, forgeBridge);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ForgeLoadingOverlay.newInstance a échoué", e);
        }
    }

    /** {@code LostIslandLoadingOverlay.newInstance} dans la couche du jeu (module {@code oneforallsurvival}). */
    private Method modOverlay() throws ReflectiveOperationException {
        if (gameLayer == null) {
            throw new IllegalStateException("couche du jeu inconnue");
        }
        Module module = gameLayer.findModule(MOD_MODULE).orElseThrow(() -> new IllegalStateException(
                "module " + MOD_MODULE + " absent de la couche du jeu (modules oneforall* : "
                        + gameLayer.modules().stream().map(Module::getName).filter(n -> n.startsWith("oneforall"))
                        .collect(Collectors.toList()) + ")"));
        Class<?> type = Class.forName(module, MOD_OVERLAY);
        if (type == null) {
            throw new ClassNotFoundException(MOD_OVERLAY + " absente du module " + MOD_MODULE);
        }
        return Arrays.stream(type.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()) && m.getName().equals("newInstance"))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(MOD_OVERLAY + ".newInstance"));
    }

    @Override
    public void updateModuleReads(ModuleLayer layer) {
        write("updateModuleReads : couche du jeu disponible");
        fallBackIfRenderFailed(); // avant de retenir la couche : le repli n'appelle pas updateModuleReads deux fois
        gameLayer = layer;
        if (delegated) {
            forge.updateModuleReads(layer);
            return;
        }
        // Comme Forge : l'overlay des ressources de Forge, pour le cas où le mod n'apporte pas le sien.
        Module forgeModule = layer.findModule("forge").orElseThrow();
        LostIslandWindow.class.getModule().addReads(forgeModule);
        try {
            Class<?> type = Class.forName(forgeModule, "net.minecraftforge.client.loading.ForgeLoadingOverlay");
            forgeOverlay = type == null ? null : Arrays.stream(type.getMethods())
                    .filter(m -> Modifier.isStatic(m.getModifiers()) && m.getName().equals("newInstance"))
                    .findFirst().orElse(null);
        } catch (RuntimeException e) {
            write("ForgeLoadingOverlay introuvable : " + e);
        }
    }

    @Override
    public void periodicTick() {
        if (delegated) {
            forge.periodicTick();
            journal();
            return;
        }
        glfwPollEvents();
        if (!handedOff) {
            fallBackIfRenderFailed();
            return;
        }
        reapBridge();
        // Après le passage, pendant le chargement des mods : la scène sur le thread du jeu, dans son contexte.
        if (overlayRequested || mainFailed || scene == null || scene.closed()) {
            return;
        }
        long now = System.nanoTime();
        if (now < nextMainFrame) {
            return;
        }
        nextMainFrame = now + MAIN_FRAME_NANOS;
        try {
            GL.getCapabilities();
        } catch (IllegalStateException e) {
            GL.setCapabilities(capabilities);
        }
        GapBridge b = bridge;
        if (b != null && !b.lockForGame()) {
            return; // le pont dessine une image en ce moment : on ne dessine jamais à deux
        }
        try {
            drawOnGameThread();
        } finally {
            if (b != null) {
                b.unlockAfterGame();
            }
        }
    }

    private void drawOnGameThread() {
        GlStateGuard state = GlStateGuard.save();
        try {
            int[] w = new int[1];
            int[] h = new int[1];
            glfwGetFramebufferSize(window, w, h);
            GL32C.glBindFramebuffer(GL32C.GL_FRAMEBUFFER, 0);
            GL32C.glViewport(0, 0, w[0], h[0]);
            if (w[0] > 0 && h[0] > 0) {
                scene.render(w[0], h[0]);
            }
        } catch (Throwable t) {
            // La fenêtre est au jeu : plus de repli possible, notre rendu s'arrête.
            mainFailed = true;
            write("rendu sur le thread du jeu en échec, la scène s'arrête : " + t);
        } finally {
            state.restore();
        }
        glfwSwapBuffers(window);
    }

    @Override
    public String getGLVersion() {
        return delegated ? forge.getGLVersion() : glVersion;
    }

    // --- Journal ---------------------------------------------------------------------------------------

    /** Relevé des barres et des nouveaux messages de Forge, une fois par seconde au plus (tous threads). */
    private synchronized void journal() {
        long now = System.nanoTime();
        if (now - lastLogNanos < LOG_INTERVAL_NANOS) {
            return;
        }
        lastLogNanos = now;
        try {
            List<StartupNotificationManager.AgeMessage> messages = StartupNotificationManager.getMessages();
            // Les messages arrivent les plus récents en tête : on les écrit dans l'ordre d'arrivée.
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i).message();
                if (seenMessages.add(message)) {
                    write("message : " + message.getText());
                }
            }
            for (ProgressMeter meter : StartupNotificationManager.getCurrentProgress()) {
                String label = meter.label() == null ? "" : meter.label().getText();
                write("barre : \"" + meter.name() + "\" " + meter.current() + "/" + meter.steps()
                        + (label.isEmpty() || label.equals(meter.name()) ? "" : " (" + label + ")"));
            }
        } catch (RuntimeException e) {
            // Le journal ne doit jamais gêner le chargement.
            write("relevé impossible : " + e);
        }
    }

    private static Path gameDir(String[] arguments) {
        String dir = option(arguments, "--gameDir");
        return dir == null ? Paths.get(".") : Paths.get(dir);
    }

    private static String option(String[] arguments, String name) {
        for (int i = 0; i + 1 < arguments.length; i++) {
            if (name.equals(arguments[i])) {
                return arguments[i + 1];
            }
        }
        return null;
    }

    private static int intOption(String[] arguments, String name, int fallback) {
        String value = option(arguments, name);
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Ouvre le journal ; ceux des lancements précédents sont gardés ({@code lostisland-earlywindow.1.log} le plus
     * récent, jusqu'à {@value #KEPT_LOGS}), pour qu'un lancement n'efface jamais le relevé du précédent.
     */
    private void openLog(Path file) {
        try {
            Files.createDirectories(file.getParent());
            String base = file.getFileName().toString().replace(".log", "");
            for (int i = KEPT_LOGS; i >= 1; i--) {
                Path from = i == 1 ? file : file.resolveSibling(base + "." + (i - 1) + ".log");
                if (Files.exists(from)) {
                    Files.move(from, file.resolveSibling(base + "." + i + ".log"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            log = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log = null;
        }
    }

    private synchronized void write(String line) {
        if (log == null) {
            return;
        }
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        try {
            log.write(String.format(Locale.ROOT, "[%8.3f s] [%s] %s", seconds, Thread.currentThread().getName(), line));
            log.newLine();
            log.flush();
        } catch (IOException e) {
            log = null;
        }
    }
}
