package dev.oneforallsurvival.earlywindow;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.SceneRenderer;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWNativeWGL;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.WGL;
import org.lwjgl.system.JNI;
import org.lwjgl.system.windows.GDI32;
import org.lwjgl.system.windows.User32;

/**
 * Le pont : un fil de dessin allégé qui tient l'image quand le thread du jeu ne dessine plus, après le passage de la
 * fenêtre à Minecraft (surtout le trou de ≈ 1,6 s entre la fin du chargement des mods et la première image de
 * l'overlay des ressources), avec un second contexte OpenGL partagé avec celui du jeu.
 *
 * <p><b>Pourquoi ce montage.</b> Pendant le trou, le thread du jeu garde son contexte courant et s'en sert
 * (textures, cibles, shaders du constructeur de {@code Minecraft}) : on ne peut ni le lui prendre ni dessiner avec.
 * Une fenêtre GLFW n'a qu'un contexte ; le pont crée donc une fenêtre cachée dont le contexte partage les objets de
 * celui du jeu, puis rend ce contexte courant, sur son thread, sur la <i>fenêtre du jeu</i>, par WGL, avec son
 * propre DC : documentation de {@code wglMakeCurrent} (Microsoft), « It need not be the same hdc that was passed to
 * wglCreateContext […] but it must be on the same device and have the same pixel format », et « An application can
 * perform multithread drawing by making different rendering contexts current to different threads, supplying each
 * thread with its own rendering context and device context ». Le DC du pont vient de {@code GetDCEx(DCX_CACHE)}
 * (un DC du cache, distinct du DC privé CS_OWNDC que tient le contexte du jeu), obtenu et rendu
 * ({@code ReleaseDC}) sur le thread du pont, comme l'exige {@code GetDCEx}. Windows seulement
 * ({@link BridgePolicy}).
 *
 * <p><b>Règles de GLFW suivies</b> (référence GLFW 3.3, « Thread safety ») :
 * <ul>
 *   <li>{@code glfwCreateWindow} et {@code glfwDestroyWindow} : « must only be called from the main thread » ; la
 *       fenêtre cachée est créée par {@link #create} et détruite par {@link #destroyWindow}, appelés par la fenêtre
 *       anticipée sur le thread principal (« Render thread », qui est celui de {@code glfwInit}) ; et
 *       {@code glfwDestroyWindow} : « The context of the specified window must not be current on any other thread »,
 *       d'où la destruction seulement après la fin du thread du pont ({@link #finished()}) ;</li>
 *   <li>{@code glfwGetWin32Window}, {@code glfwGetWGLContext} : « may be called from any thread » ; appelés quand
 *       même sur le thread principal, dans {@link #create} ;</li>
 *   <li><b>aucun appel GLFW sur le thread du pont</b>, même ceux permis ailleurs ({@code glfwMakeContextCurrent} :
 *       « may be called from any thread ») : le rappel d'erreur de GLFW est global, et celui que pose Minecraft
 *       ({@code Window.defaultErrorCallback}) exige le thread du jeu ; une erreur GLFW levée sur le pont lève donc
 *       sur le pont (vu au premier essai : {@code glfwMakeContextCurrent(NULL)} en fin de pont, « Rendersystem called
 *       from wrong thread », thread mort sans rendre la main). Le contexte du pont est donc rendu courant et rendu
 *       par {@code wglMakeCurrent} seulement, sur un seul thread à la fois (« A rendering context can be current to
 *       only one thread at a time », wglMakeCurrent) ;</li>
 *   <li>{@code glfwGetFramebufferSize} : thread principal seulement ; le pont lit donc la taille de la zone client
 *       par {@code GetClientRect} de Win32.</li>
 * </ul>
 *
 * <p><b>Pourquoi il ne dessine jamais en même temps que le jeu.</b>
 * <ul>
 *   <li>Chaque image (rendu, échange, et {@code glFinish} pour le pont) se fait sous {@link #frameLock}, pris par le
 *       pont et par {@code periodicTick} : jamais deux images à la fois. Le pont ne dessine que si aucune image
 *       n'est venue depuis {@value #TAKEOVER_MILLIS} ms, et le jeu reprend la main dès son image suivante.</li>
 *   <li>Ordre entre contextes : après chaque image du jeu, une barrière ({@code glFenceSync} puis {@code glFlush})
 *       que le pont attend avant de dessiner ; chaque image du pont finit par {@code glFinish}. Barrière pas franchie
 *       en 50 ms : image du pont sautée, puis pause hors du verrou ({@link #pauseNanos}) ; attente en échec
 *       ({@code GL_WAIT_FAILED}) : arrêt du pont ({@link #fenceOutcome}).</li>
 *   <li>Fin : {@code loadingOverlay} (thread du jeu, avant que la boucle du jeu ne vide le tampon de la fenêtre
 *       puis ne dessine l'overlay) demande l'arrêt et attend, jusqu'à 1 s, que le pont ne puisse plus dessiner
 *       ({@link #stopAndQuiesce} : verrou des images pris après la demande). Tant que ce n'est pas le cas, rien ne
 *       touche la scène (ni phase, ni image, ni libération des textures) : l'overlay affiche du noir et redemande à
 *       chaque image. Limite : si le pont reste bloqué plus d'une seconde au milieu d'une image (pilote figé), la
 *       boucle du jeu présente quand même sa propre image ; seule la fenêtre peut alors montrer un mélange, aucun
 *       état ni objet partagé n'est touché par le jeu.</li>
 *   <li>Objets : le pont a son propre rendu ({@link EarlyScene#createLightRenderer()}) : tampons (FBO), VAO,
 *       programmes et atlas de police créés dans son contexte et détruits par lui avant de s'arrêter. Il lit les
 *       textures de la scène, partagées, et n'en détruit aucune.</li>
 * </ul>
 *
 * <p><b>Repli.</b> Toute erreur (création, partage, format de pixel, rendu, échange, attente de la barrière) arrête le pont, qui libère ses
 * objets et rend son contexte : l'image se fige alors comme avant, sans plantage.
 */
final class GapBridge {
    static final long TAKEOVER_MILLIS = 150;
    private static final long TAKEOVER_NANOS = TAKEOVER_MILLIS * 1_000_000L;
    private static final long FRAME_NANOS = 1_000_000_000L / 30; // version allégée : 30 images par seconde
    private static final long MAX_HOLD_NANOS = 60_000_000_000L;
    private static final long FENCE_WAIT_NANOS = 50_000_000L;
    private static final int DCX_CACHE = 0x2;

    private final long hidden;
    private final long hwnd;       // fenêtre du jeu (Win32)
    private final long hiddenHwnd; // fenêtre cachée (Win32)
    private final long context;    // contexte du pont (WGL)
    private final EarlyScene scene;
    private final Consumer<String> log;
    private final long startNanos;
    private final ReentrantLock frameLock = new ReentrantLock();
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean stop;
    private volatile boolean bridgeOwns;
    private volatile long lastFrameNanos;
    private volatile Throwable failure;
    private boolean destroyed;
    private volatile Thread thread;
    private volatile boolean quiesced;
    // Essai du pont en retard (-Dlostisland.bridgeThread.slowStop=<ms>) : à la demande d'arrêt, le pont garde le
    // verrou des images ce temps-là, comme un pilote bloqué au milieu d'une image.
    private final long slowStopMillis = Long.getLong("lostisland.bridgeThread.slowStop", 0L);
    private boolean fenceTimeoutLogged;
    // Sous frameLock :
    private long gameFence;
    private boolean exited;
    private long ownsSince;
    private int frames;
    private int holdFrames;
    private int takeovers;

    private GapBridge(long hidden, long hwnd, long hiddenHwnd, long context, EarlyScene scene, Consumer<String> log) {
        this.hidden = hidden;
        this.hwnd = hwnd;
        this.hiddenHwnd = hiddenHwnd;
        this.context = context;
        this.scene = scene;
        this.log = log;
        this.startNanos = System.nanoTime();
        this.lastFrameNanos = startNanos;
    }

    /**
     * Crée la fenêtre cachée et son contexte, partagé avec celui de {@code visible}. Thread principal, aucun des deux
     * contextes courant nulle part (le thread de rendu d'avant le passage l'a rendu, le jeu ne l'a pas encore pris).
     *
     * <p>Le rappel d'erreur de GLFW en place à ce moment est celui du démarrage de Minecraft
     * ({@code Window.bootCrash} : boîte « mettez à jour vos pilotes » puis arrêt du jeu). Tous les appels GLFW de la
     * création passent donc sous un rappel local qui ne fait que noter l'erreur, et l'ancien est remis ensuite : un
     * échec retombe en silence sur la saccade d'avant.
     *
     * @param fail essai du repli ({@code -Dlostisland.bridgeThread.fail=true}) : version de contexte impossible
     *             (99), pour que la création échoue par une vraie erreur de GLFW
     * @throws IllegalStateException si le second contexte ne se crée pas ; rien ne reste alloué
     */
    static GapBridge create(long visible, String glVersion, EarlyScene scene, Consumer<String> log, boolean fail) {
        String[] v = glVersion.split("\\.");
        int major = fail ? 99 : Integer.parseInt(v[0].trim());
        int minor = Integer.parseInt(v[1].trim());
        String[] error = {null};
        GLFWErrorCallback local = GLFWErrorCallback.create((code, description) -> {
            if (error[0] == null) {
                error[0] = "0x" + Integer.toHexString(code) + " " + GLFWErrorCallback.getDescription(description);
            }
        });
        GLFWErrorCallback previous = glfwSetErrorCallback(local);
        long hidden = 0L;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
            glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, major);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, minor);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            // Partage : « share: The window whose context to share resources with » (glfwCreateWindow).
            hidden = glfwCreateWindow(16, 16, "lostisland-pont", 0L, visible);
            if (hidden == 0L) {
                throw new IllegalStateException("second contexte non créé (glfwCreateWindow, erreur GLFW : "
                        + error[0] + (fail ? ", essai demandé par -Dlostisland.bridgeThread.fail=true" : "") + ")");
            }
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(visible);
            long hiddenHwnd = GLFWNativeWin32.glfwGetWin32Window(hidden);
            long context = GLFWNativeWGL.glfwGetWGLContext(hidden);
            if (hwnd == 0L || hiddenHwnd == 0L || context == 0L) {
                throw new IllegalStateException("poignées Win32/WGL introuvables (erreur GLFW : " + error[0] + ")");
            }
            GapBridge bridge = new GapBridge(hidden, hwnd, hiddenHwnd, context, scene, log);
            hidden = 0L; // gardée par le pont
            return bridge;
        } finally {
            if (hidden != 0L) {
                glfwDestroyWindow(hidden);
            }
            glfwDefaultWindowHints(); // aussi sur exception : aucun indice global laissé modifié
            glfwSetErrorCallback(previous);
            local.free();
        }
    }

    void start() {
        Thread t = new Thread(this::run, "lostisland-pont");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1); // le jeu d'abord
        thread = t;
        t.start();
    }

    private void run() {
        long dc = 0L;
        long hiddenDc = 0L;
        boolean current = false;
        SceneRenderer light = null;
        try {
            // DC privé (CS_OWNDC) de la fenêtre cachée, utilisé par ce seul thread ; et un DC du cache pour la
            // fenêtre du jeu, distinct du DC privé que tient le contexte du jeu.
            hiddenDc = User32.GetDC(hiddenHwnd);
            long getDcEx = User32.getLibrary().getFunctionAddress("GetDCEx");
            dc = JNI.callPPP(hwnd, 0L, DCX_CACHE, getDcEx);
            if (dc == 0L || hiddenDc == 0L) {
                throw new IllegalStateException("GetDCEx(DCX_CACHE) ou GetDC a échoué");
            }
            int gameFormat = GDI32.GetPixelFormat(dc);
            int ownFormat = GDI32.GetPixelFormat(hiddenDc);
            if (gameFormat == 0 || gameFormat != ownFormat) {
                throw new IllegalStateException("formats de pixel différents (jeu " + gameFormat + ", pont " + ownFormat
                        + ")");
            }
            // Le contexte du pont, courant sur ce thread et dessinant dans la fenêtre du jeu par son propre DC.
            if (!WGL.wglMakeCurrent(dc, context)) {
                throw new IllegalStateException("wglMakeCurrent sur la fenêtre du jeu a échoué");
            }
            current = true;
            GL.createCapabilities();
            long compile = System.nanoTime();
            light = scene.createLightRenderer();
            log.accept(String.format(Locale.ROOT, "pont : prêt (format de pixel %d, rendu allégé %s compilé en %.2f s, "
                    + "30 i/s, prise de l'écran après %d ms sans image)", gameFormat, light.activeEffects(),
                    (System.nanoTime() - compile) / 1e9, TAKEOVER_MILLIS));
            loop(light, dc);
        } catch (Throwable t) {
            failure = t;
            log.accept("pont : arrêté sur erreur, l'image se figera au passage comme avant : " + t);
        } finally {
            frameLock.lock();
            try {
                exited = true;
                bridgeOwns = false;
                if (current) {
                    if (light != null) {
                        light.close(); // nos objets seulement : aucune texture de la scène
                    }
                    if (gameFence != 0L) {
                        glDeleteSync(gameFence);
                        gameFence = 0L;
                    }
                    glFinish();
                }
            } catch (Throwable t) {
                log.accept("pont : libération incomplète : " + t);
            } finally {
                frameLock.unlock();
            }
            try {
                if (current) {
                    WGL.wglMakeCurrent(0L, 0L);
                }
                GL.setCapabilities(null);
                if (dc != 0L) {
                    User32.ReleaseDC(hwnd, dc);
                }
                if (hiddenDc != 0L) {
                    User32.ReleaseDC(hiddenHwnd, hiddenDc);
                }
            } catch (Throwable t) {
                log.accept("pont : contexte mal rendu : " + t);
            }
            done.countDown();
            log.accept(String.format(Locale.ROOT, "pont : thread terminé (%d images dessinées, %d prises d'écran)", frames,
                    takeovers));
        }
    }

    private void loop(SceneRenderer light, long dc) {
        long getClientRect = User32.getLibrary().getFunctionAddress("GetClientRect");
        int[] rect = new int[4];
        while (!stop) {
            long need = bridgeOwns ? FRAME_NANOS : TAKEOVER_NANOS;
            long wait = lastFrameNanos + need - System.nanoTime();
            if (wait > 0) {
                LockSupport.parkNanos(Math.min(wait, 5_000_000L));
                continue;
            }
            if (!frameLock.tryLock()) {
                LockSupport.parkNanos(1_000_000L);
                continue;
            }
            boolean skipped = false;
            try {
                if (stop) {
                    return;
                }
                long now = System.nanoTime();
                // Sinon : le jeu vient de dessiner, rien à faire avant la pause.
                if (now - lastFrameNanos >= (bridgeOwns ? FRAME_NANOS : TAKEOVER_NANOS)) {
                    if (!bridgeOwns) {
                        bridgeOwns = true;
                        ownsSince = now;
                        holdFrames = 0;
                        takeovers++;
                        log.accept(String.format(Locale.ROOT, "pont : prend l'écran (aucune image depuis %d ms)",
                                (now - lastFrameNanos) / 1_000_000L));
                    } else if (now - ownsSince > MAX_HOLD_NANOS) {
                        log.accept("pont : 60 s sans image du jeu, le pont s'arrête");
                        return;
                    }
                    if (gameFence != 0L) {
                        int waited = glClientWaitSync(gameFence, 0, FENCE_WAIT_NANOS);
                        switch (fenceOutcome(waited)) {
                            case DRAW -> {
                                glDeleteSync(gameFence);
                                gameFence = 0L;
                            }
                            case SKIP -> {
                                // Image du jeu pas encore finie côté GPU : on ne dessine pas par-dessus, on réessaie
                                // après la pause, hors du verrou, pour que le jeu puisse dessiner entre-temps.
                                if (!fenceTimeoutLogged) {
                                    fenceTimeoutLogged = true;
                                    log.accept("pont : image du jeu pas finie après 50 ms (0x"
                                            + Integer.toHexString(waited) + "), image du pont sautée");
                                }
                                skipped = true;
                            }
                            case STOP -> {
                                // Attente en échec (GL_WAIT_FAILED, ou retour inconnu) : elle échouerait à chaque
                                // essai. Le pont s'arrête proprement, l'image se fige au passage comme avant.
                                log.accept("pont : attente de la barrière du jeu en échec (0x"
                                        + Integer.toHexString(waited) + "), le pont s'arrête");
                                return;
                            }
                        }
                    }
                    if (!skipped) {
                        drawFrame(light, dc, getClientRect, rect);
                    }
                }
            } finally {
                frameLock.unlock();
            }
            // Pause hors du verrou sur tous les chemins (image dessinée, sautée, ou jeu qui vient de dessiner).
            LockSupport.parkNanos(pauseNanos(skipped, lastFrameNanos, System.nanoTime()));
        }
    }

    /** Une image du pont, sous {@link #frameLock}, barrière du jeu franchie. */
    private void drawFrame(SceneRenderer light, long dc, long getClientRect, int[] rect) {
        if (JNI.callPPI(hwnd, rect, getClientRect) == 0) {
            throw new IllegalStateException("GetClientRect a échoué");
        }
        int w = rect[2] - rect[0];
        int h = rect[3] - rect[1];
        if (w > 0 && h > 0) { // 0 : fenêtre réduite, rien à dessiner
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, w, h);
            scene.renderWith(light, w, h);
            if (!GDI32.SwapBuffers(dc)) {
                throw new IllegalStateException("SwapBuffers a échoué");
            }
            glFinish();
            frames++;
            holdFrames++;
        }
        lastFrameNanos = System.nanoTime();
        if (slowStopMillis > 0L) {
            // Essai seulement : attendre la prochaine image en gardant le verrou, et à la demande d'arrêt le
            // garder encore slowStopMillis, comme un pilote bloqué.
            while (!stop && System.nanoTime() - lastFrameNanos < FRAME_NANOS) {
                LockSupport.parkNanos(1_000_000L);
            }
            if (stop) {
                log.accept("pont : essai du pont en retard, verrou gardé " + slowStopMillis + " ms");
                LockSupport.parkNanos(slowStopMillis * 1_000_000L);
            }
        }
    }

    /** Suite à donner au retour de {@code glClientWaitSync} sur la barrière du jeu. */
    enum FenceOutcome { DRAW, SKIP, STOP }

    /**
     * {@code GL_ALREADY_SIGNALED}/{@code GL_CONDITION_SATISFIED} : dessiner ; {@code GL_TIMEOUT_EXPIRED} : sauter
     * l'image et réessayer ; {@code GL_WAIT_FAILED} ou tout autre retour : arrêter le pont.
     */
    static FenceOutcome fenceOutcome(int waited) {
        return switch (waited) {
            case GL_ALREADY_SIGNALED, GL_CONDITION_SATISFIED -> FenceOutcome.DRAW;
            case GL_TIMEOUT_EXPIRED -> FenceOutcome.SKIP;
            default -> FenceOutcome.STOP;
        };
    }

    /**
     * Pause du pont après avoir rendu le verrou : jusqu'à l'image suivante ({@code lastFrame + FRAME_NANOS}), et au
     * moins une image entière après une image sautée, où {@code lastFrame} n'a pas avancé : sans quoi le pont
     * reprendrait le verrou aussitôt et le jeu ne pourrait plus dessiner.
     */
    static long pauseNanos(boolean skipped, long lastFrame, long now) {
        long until = skipped ? now + FRAME_NANOS : lastFrame + FRAME_NANOS;
        return Math.max(0L, until - now);
    }

    // --- Thread du jeu -------------------------------------------------------------------------------------

    /** Avant une image du jeu ({@code periodicTick}) : {@code false} si le pont dessine en ce moment (image sautée). */
    boolean lockForGame() {
        return frameLock.tryLock();
    }

    /** Après l'échange de l'image du jeu : barrière pour le pont, puis le verrou est rendu. */
    void unlockAfterGame() {
        try {
            if (gameFence != 0L) {
                glDeleteSync(gameFence); // objet partagé : supprimable depuis le contexte du jeu (même si le pont
                gameFence = 0L;          // s'est arrêté sans contexte courant)
            }
            if (!exited) {
                gameFence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
                glFlush(); // sans quoi l'attente du pont, dans l'autre contexte, pourrait ne jamais finir
            }
            if (bridgeOwns) {
                bridgeOwns = false;
                log.accept("pont : le jeu reprend l'écran après " + holdFrames + " images du pont");
            }
        } finally {
            lastFrameNanos = System.nanoTime();
            frameLock.unlock();
        }
    }

    /**
     * Demande l'arrêt et attend que le pont ne puisse plus dessiner : soit son thread est fini, soit le verrou des
     * images a été pris <i>après</i> la demande (le pont relit l'arrêt sous ce verrou avant chaque image, et chaque
     * image du pont finit par {@code glFinish} sous ce verrou). Une fois vrai, le pont ne lit plus rien de la scène
     * ni ne dessine ; il ne lui reste qu'à libérer ses propres objets. Thread du jeu.
     *
     * @return vrai si le pont ne dessinera plus
     */
    boolean stopAndQuiesce(long timeoutMillis) {
        stop = true;
        if (quiesced) {
            return true;
        }
        if (finished() || dead()) {
            quiesced = true;
            return true;
        }
        try {
            if (frameLock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) {
                frameLock.unlock();
                quiesced = true;
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (finished() || dead()) {
            quiesced = true;
            return true;
        }
        return false;
    }

    /** Fin propre : contexte rendu, objets du pont libérés. */
    boolean finished() {
        return done.getCount() == 0;
    }

    /**
     * Thread mort sans fin propre (erreur hors de toute prise) : il ne dessine plus, mais son contexte n'a peut-être
     * pas été rendu ; sa fenêtre cachée n'est alors jamais détruite (fuite assumée plutôt que règle de GLFW violée).
     */
    boolean dead() {
        Thread t = thread;
        return t != null && !t.isAlive() && !finished();
    }

    Throwable failure() {
        return failure;
    }

    int frames() {
        return frames; // lu après stopAndQuiesce (verrou pris après l'arrêt, ou thread fini)
    }

    /**
     * Détruit la fenêtre cachée. Thread principal, seulement une fois le thread du pont fini (contexte rendu). Sous
     * un rappel d'erreur local : selon le moment, celui de Minecraft arrêterait le démarrage sur une erreur GLFW.
     */
    void destroyWindow() {
        if (destroyed || !finished()) {
            return;
        }
        destroyed = true;
        GLFWErrorCallback local = GLFWErrorCallback.create((code, description) -> log.accept(
                "pont : erreur GLFW à la destruction de la fenêtre cachée : 0x" + Integer.toHexString(code)));
        GLFWErrorCallback previous = glfwSetErrorCallback(local);
        try {
            glfwDestroyWindow(hidden);
        } finally {
            glfwSetErrorCallback(previous);
            local.free();
        }
    }

    /** Secondes depuis la création du pont. */
    double age() {
        return (System.nanoTime() - startNanos) / 1e9;
    }
}
