package dev.oneforallsurvival.client.title;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.oneforallsurvival.earlywindow.EarlyScene;
import dev.oneforallsurvival.earlywindow.GlStateGuard;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL32C;

/**
 * Tout ce qui touche la scène du jar de service ({@link EarlyScene}) côté écran titre : la seule classe du titre
 * qui la référence en dehors des signatures de {@link TitleScene}. Elle n'est chargée qu'une fois la scène
 * confirmée (overlay du mod paru, donc jar de service présent) : sans le jar, rien n'y mène.
 *
 * <p>Thread du jeu seulement, contexte du jeu courant. Chaque appel GL est entouré d'une photographie de l'état
 * ({@link GlStateGuard}), comme l'overlay du chargement : {@code GlStateManager} retrouve ce qu'il croit lié.
 */
final class SceneBackend {
    private final EarlyScene scene;
    /** Signal du pont de la fenêtre anticipée ({@code awaitBridgeStopped}) ; toujours vrai pour une scène recréée. */
    private final BooleanSupplier bridgeStopped;
    private final boolean recreated;

    private SceneBackend(EarlyScene scene, BooleanSupplier bridgeStopped, boolean recreated) {
        this.scene = scene;
        this.bridgeStopped = bridgeStopped;
        this.recreated = recreated;
    }

    /** La scène de l'overlay du chargement, confiée sans être libérée ; son film s'arrête (il couvre le chargement). */
    static SceneBackend adopt(EarlyScene scene, BooleanSupplier bridgeStopped) {
        scene.stopRecording();
        return new SceneBackend(scene, bridgeStopped, false);
    }

    /**
     * Une scène neuve dans le contexte du jeu, sur l'aube tenue ({@link EarlyScene#createForGame}) : shaders compilés
     * ici, textures décodées à part.
     *
     * @throws RuntimeException si elle ne peut pas être créée (shader refusé, catalogue absent)
     */
    static SceneBackend create(Consumer<String> log) {
        Minecraft mc = Minecraft.getInstance();
        GlStateGuard state = GlStateGuard.save();
        try {
            EarlyScene scene = EarlyScene.createForGame(mc.gameDirectory.toPath(),
                    SharedConstants.getCurrentVersion().getName(), log);
            return new SceneBackend(scene, () -> true, true);
        } finally {
            state.restore();
        }
    }

    EarlyScene scene() {
        return scene;
    }

    boolean recreated() {
        return recreated;
    }

    /** Vrai si la scène peut être dessinée : ses textures de base sont prêtes. Sinon, envoie ce qui est décodé. */
    boolean prepare() {
        if (scene.readyToShow()) {
            return true;
        }
        GlStateGuard state = GlStateGuard.save();
        try {
            scene.uploadPending();
        } finally {
            state.restore();
        }
        return scene.readyToShow();
    }

    /**
     * Une image de la scène dans la cible principale du jeu, plein cadre. {@code false} si le pont tient encore la
     * scène (rien n'est touché).
     *
     * @throws RuntimeException si l'image échoue (l'appelant libère la scène et passe au repli)
     */
    boolean draw() {
        if (!bridgeStopped.getAsBoolean()) {
            return false;
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        target.bindWrite(true);
        GlStateGuard state = GlStateGuard.save();
        try {
            GL32C.glViewport(0, 0, target.width, target.height);
            scene.renderTitle(target.width, target.height);
            return true;
        } finally {
            state.restore();
        }
    }

    /**
     * Libère textures, programmes et tampons de la scène. {@code -1} si le pont ne s'est pas arrêté (scène gardée,
     * fuite assumée comme au chantier 1), sinon la mémoire vidéo estimée des textures libérées, en octets.
     */
    long close() {
        if (!bridgeStopped.getAsBoolean()) {
            return -1L;
        }
        long bytes = scene.textureBytes();
        GlStateGuard state = GlStateGuard.save();
        try {
            scene.close();
        } finally {
            state.restore();
        }
        return bytes;
    }

    void log(String line) {
        scene.log(line);
    }
}
