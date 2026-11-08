package dev.oneforallsurvival.earlywindow;

import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.opengl.GL32C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL32C.glBindFramebuffer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Thread de rendu de la fenêtre anticipée, avant son passage à Minecraft : il prend le contexte de la fenêtre,
 * dessine la scène image après image (60 images par seconde au plus, synchronisées à l'écran) et le rend en
 * s'arrêtant.
 *
 * <p>Il s'arrête de lui-même si une image lève (FBO incomplet, image obligatoire illisible) : l'échec est gardé
 * ({@link #failure()}) et la fenêtre retombe sur Forge au prochain appel de Forge sur le thread principal (seul
 * thread où GLFW permet de détruire une fenêtre).
 */
final class EarlyRenderLoop {
    private static final long FRAME_NANOS = 1_000_000_000L / 60;

    private final long window;
    private final GLCapabilities capabilities;
    private final EarlyScene scene;
    private final IntSupplier fbWidth;
    private final IntSupplier fbHeight;
    private final Consumer<String> log;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean stop;
    private volatile Throwable failure;
    private Thread thread;

    EarlyRenderLoop(long window, GLCapabilities capabilities, EarlyScene scene, IntSupplier fbWidth,
                    IntSupplier fbHeight, Consumer<String> log) {
        this.window = window;
        this.capabilities = capabilities;
        this.scene = scene;
        this.fbWidth = fbWidth;
        this.fbHeight = fbHeight;
        this.log = log;
    }

    /** Lance le thread ; le contexte de la fenêtre ne doit être courant sur aucun autre thread. */
    void start() {
        thread = new Thread(this::run, "lostisland-render");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        glfwMakeContextCurrent(window);
        GL.setCapabilities(capabilities);
        try {
            glfwSwapInterval(1);
            long next = System.nanoTime();
            while (!stop) {
                int w = fbWidth.getAsInt();
                int h = fbHeight.getAsInt();
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                if (w > 0 && h > 0) {
                    scene.render(w, h); // lève : repli sur Forge
                }
                glfwSwapBuffers(window);
                next += FRAME_NANOS;
                long wait = next - System.nanoTime();
                if (wait > 0) {
                    LockSupport.parkNanos(wait);
                } else {
                    next = System.nanoTime();
                }
            }
        } catch (Throwable t) {
            failure = t;
            log.accept("thread de rendu arrêté : " + t);
        } finally {
            glfwMakeContextCurrent(0L);
            GL.setCapabilities(null);
            done.countDown();
        }
    }

    /**
     * Demande l'arrêt et attend que le thread ait rendu le contexte.
     *
     * @return faux si le thread n'a pas rendu le contexte à temps
     */
    boolean stopAndJoin(long timeoutMillis) {
        stop = true;
        if (thread == null) {
            return true;
        }
        try {
            return done.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Échec d'une image (le thread s'est arrêté), sinon {@code null}. */
    Throwable failure() {
        return failure;
    }
}
