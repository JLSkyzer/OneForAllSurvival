package dev.oneforallsurvival.earlywindow;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import net.minecraftforge.fml.earlydisplay.ColourScheme;
import net.minecraftforge.fml.earlydisplay.DisplayWindow;
import net.minecraftforge.fml.earlydisplay.RenderElement;

/**
 * Ce que l'overlay de Forge ({@code ForgeLoadingOverlay}) prend pour un {@link DisplayWindow}, quand le mod
 * n'apporte pas son propre overlay : la phase des ressources de Minecraft garde alors notre scène, dessinée et
 * fondue par la mécanique de Forge.
 *
 * <p>L'overlay de Forge, sur le thread du jeu et dans son contexte, appelle à chaque image {@link #render(int)},
 * puis colle la texture {@link #getFramebufferTextureId()} sur un rectangle calculé depuis {@link #context()}
 * (largeur, hauteur, échelle), avec des bandes de la couleur du fond autour ; au bout du rechargement, il fond le
 * tout vers l'écran titre en 1 s puis appelle {@link #close()}. Ce pont :
 * <ul>
 *   <li>annonce un contexte de la taille exacte de la fenêtre et d'échelle 2 : le rectangle couvre alors toute la
 *       fenêtre, sans bandes ;</li>
 *   <li>dessine la scène dans un tampon à la taille de la fenêtre, puis la recopie retournée de haut en bas dans
 *       la texture rendue (l'overlay lit la ligne 0 en haut, comme le tampon de Forge) ;</li>
 *   <li>sauvegarde et rend l'état GL de Minecraft autour du rendu ({@link GlStateGuard}) ;</li>
 *   <li>ignore le logo de Mojang ({@link #addMojangTexture(int)}) ;</li>
 *   <li>libère la scène à la fermeture.</li>
 * </ul>
 * Un rendu qui lève n'arrête pas le jeu : la texture reste noire jusqu'au fondu, une ligne au journal.
 *
 * <p>Seules les méthodes publiques de {@link DisplayWindow} que l'overlay appelle sont redéfinies ; ce
 * {@code DisplayWindow} n'a jamais été initialisé et ne doit rien recevoir d'autre.
 */
final class ForgeOverlayBridge extends DisplayWindow {
    private final EarlyScene scene;
    private final long window;
    private final Consumer<String> log;
    private final int[] w = new int[1];
    private final int[] h = new int[1];
    private int sceneFbo;
    private int sceneTex;
    private int flipFbo;
    private int flipTex;
    private int targetW;
    private int targetH;
    private boolean failed;
    private boolean closed;
    private boolean firstFrame = true;

    ForgeOverlayBridge(EarlyScene scene, long window, Consumer<String> log) {
        this.scene = scene;
        this.window = window;
        this.log = log;
    }

    @Override
    public RenderElement.DisplayContext context() {
        size();
        // Échelle 2 : l'overlay en déduit un rectangle de 2 * min(fb / taille) / 2 = 1 fois la fenêtre.
        return new RenderElement.DisplayContext(w[0], h[0], 2, null, ColourScheme.BLACK, null);
    }

    @Override
    public void render(int alpha) {
        if (closed) {
            return;
        }
        GlStateGuard state = GlStateGuard.save();
        try {
            size();
            ensureTargets(w[0], h[0]);
            if (!failed) {
                try {
                    if (scene == null || scene.closed()) {
                        throw new IllegalStateException("scène indisponible");
                    }
                    if (firstFrame) {
                        firstFrame = false;
                        scene.enter(Phase.RESOURCES);
                        log.accept("overlay de Forge : première image de la scène dans le contexte du jeu");
                    }
                    glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
                    glViewport(0, 0, targetW, targetH);
                    scene.render(targetW, targetH);
                } catch (Throwable t) {
                    failed = true;
                    log.accept("overlay de Forge : rendu de la scène en échec, image noire jusqu'au menu : " + t);
                    glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
                    glViewport(0, 0, targetW, targetH);
                    glClearColor(0f, 0f, 0f, 1f);
                    glClear(GL_COLOR_BUFFER_BIT);
                }
            }
            // Recopie retournée : la ligne du haut de la scène devient la ligne 0 de la texture.
            glBindFramebuffer(GL_READ_FRAMEBUFFER, sceneFbo);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, flipFbo);
            glDisable(GL_SCISSOR_TEST);
            glBlitFramebuffer(0, 0, targetW, targetH, 0, targetH, targetW, 0, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        } catch (Throwable t) {
            failed = true;
            log.accept("overlay de Forge : tampon de la scène indisponible : " + t);
        } finally {
            state.restore();
        }
    }

    @Override
    public int getFramebufferTextureId() {
        return flipTex;
    }

    @Override
    public void addMojangTexture(int textureId) {
        // Pas de logo de Mojang sur la scène.
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        deleteTargets();
        if (scene != null) {
            scene.close();
        }
        log.accept("overlay de Forge : fin du fondu, scène libérée");
    }

    @Override
    public void periodicTick() {
        // Jamais appelé sur ce pont : la fenêtre est celle de LostIslandWindow.
    }

    private void size() {
        glfwGetFramebufferSize(window, w, h);
        w[0] = Math.max(1, w[0]);
        h[0] = Math.max(1, h[0]);
    }

    private void ensureTargets(int width, int height) {
        if (sceneFbo != 0 && width == targetW && height == targetH) {
            return;
        }
        deleteTargets();
        sceneTex = texture(width, height);
        sceneFbo = framebuffer(sceneTex);
        flipTex = texture(width, height);
        flipFbo = framebuffer(flipTex);
        targetW = width;
        targetH = height;
    }

    private static int texture(int width, int height) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return tex;
    }

    private static int framebuffer(int tex) {
        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("tampon de l'overlay incomplet : 0x" + Integer.toHexString(status));
        }
        return fbo;
    }

    private void deleteTargets() {
        if (sceneFbo != 0) {
            glDeleteFramebuffers(new int[] {sceneFbo, flipFbo});
            glDeleteTextures(new int[] {sceneTex, flipTex});
            sceneFbo = 0;
            flipFbo = 0;
            sceneTex = 0;
            flipTex = 0;
        }
    }
}
