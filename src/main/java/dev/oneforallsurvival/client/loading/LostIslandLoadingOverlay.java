package dev.oneforallsurvival.client.loading;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.oneforallsurvival.earlywindow.EarlyScene;
import dev.oneforallsurvival.earlywindow.GlStateGuard;
import dev.oneforallsurvival.earlywindow.LostIslandWindow;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.lwjgl.opengl.GL32C;

/**
 * Overlay du premier chargement des ressources de Minecraft : la scène Lost Island de la fenêtre anticipée continue,
 * plein écran, dans le contexte du jeu, jusqu'à l'écran titre. Désigné par {@code LostIslandWindow.loadingOverlay}
 * (jar de service, couche des services), qui appelle {@link #newInstance} par réflexion ; aucune classe du mod ne
 * le référence, et seul un client le charge.
 *
 * <p>Structure de {@code ForgeLoadingOverlay} (rechargement, {@code onFinish}, erreurs, fin), sans ses bandes de
 * couleur ni le logo de Mojang :
 * <ul>
 *   <li>à chaque image, la scène est dessinée dans la cible principale de Minecraft (celle que le jeu présente
 *       ensuite à l'écran, dans le même sens que la fenêtre), l'état GL du jeu photographié et rendu autour
 *       ({@link GlStateGuard}, ruling 11) ; la barre suit {@code reload.getActualProgress()} ;</li>
 *   <li>rechargement fini : erreurs relevées et remises à {@code onFinish}, écran courant réinitialisé, puis la
 *       barre et le texte s'effacent en {@value #UI_FADE_MS} ms (le logo reste) ;</li>
 *   <li>au bout du fondu : l'overlay se retire ({@code setOverlay(null)}), l'écran titre s'affiche, et la scène est
 *       libérée (chantier 1 seul : voir {@link #release}).</li>
 * </ul>
 * Repli : si la première image échoue, l'overlay de Forge, qui dessine la même scène par le pont de la fenêtre
 * anticipée, prend sa place ; un échec plus tard laisse une image noire jusqu'à l'écran titre. Jamais de plantage.
 */
public final class LostIslandLoadingOverlay extends LoadingOverlay {
    /** Durée du fondu de la barre et du texte, rechargement fini (ruling 20). */
    static final long UI_FADE_MS = 1000L;
    /**
     * Essai du repli (ruling 28) : {@code -Dlostisland.overlay.fail=first} fait échouer la première image, l'overlay
     * de Forge (avec le pont de la fenêtre anticipée) prend alors la main. Développement seulement
     * ({@code -PoverlayFail=first} au lancement de Gradle).
     */
    private static final String FAIL_MODE = System.getProperty("lostisland.overlay.fail");
    private static final String FAIL_FIRST = "first";
    /** Relevé de l'avancement une fois par seconde au journal : mise au point seulement. */
    private static final boolean TRACE = Boolean.getBoolean("lostisland.progress.trace");

    private final Minecraft minecraft;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final Supplier<Minecraft> minecraftSupplier;
    private final Supplier<ReloadInstance> reloadSupplier;
    private final LostIslandWindow window;
    private final EarlyScene scene;
    private final long createdMillis = Util.getMillis();
    private long fadeOutStart = -1L;
    private long lastProgressLog = Long.MIN_VALUE / 2;
    private boolean firstFrame = true;
    private boolean failed;
    private boolean released;

    private LostIslandLoadingOverlay(Supplier<Minecraft> mc, Supplier<ReloadInstance> ri,
                                     Consumer<Optional<Throwable>> onFinish, LostIslandWindow window,
                                     EarlyScene scene) {
        super(mc.get(), ri.get(), onFinish, false);
        this.minecraft = mc.get();
        this.reload = ri.get();
        this.onFinish = onFinish;
        this.minecraftSupplier = mc;
        this.reloadSupplier = ri;
        this.window = window;
        this.scene = scene;
    }

    /**
     * Même forme que {@code ForgeLoadingOverlay.newInstance}, appelée par la fenêtre anticipée avec elle-même en
     * dernier argument. Tout ce qui peut échouer (classes du jar de service, scène absente) échoue ici, dans
     * l'appel réflexif que la fenêtre entoure : elle prend alors l'overlay de Forge. Le fournisseur rendu ne fait
     * plus que construire l'overlay.
     */
    public static Supplier<LoadingOverlay> newInstance(Supplier<Minecraft> mc, Supplier<ReloadInstance> ri,
                                                       Consumer<Optional<Throwable>> handler, Object window) {
        LostIslandWindow lostIsland = (LostIslandWindow) window;
        EarlyScene scene = LostIslandWindow.sceneState();
        if (scene == null) {
            throw new IllegalStateException("scène de la fenêtre anticipée indisponible (repli ou déjà libérée)");
        }
        Module own = LostIslandLoadingOverlay.class.getModule();
        Module service = LostIslandWindow.class.getModule();
        scene.log("overlay du mod : module " + own.getName() + " (" + layerName(own) + ") lit " + service.getName()
                + " (" + layerName(service) + ") : " + own.canRead(service));
        return () -> new LostIslandLoadingOverlay(mc, ri, handler, lostIsland, scene);
    }

    private static String layerName(Module module) {
        ModuleLayer layer = module.getLayer();
        if (layer == null) {
            return "module sans couche";
        }
        return layer == ModuleLayer.boot() ? "couche de démarrage" : "couche de " + layer.modules().size() + " modules";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float fade = fadeOutStart < 0L ? 0f : Math.min(1f, (now - fadeOutStart) / (float) UI_FADE_MS);
        if (!failed && !drawScene(fadeOutStart >= 0L, 1f - fade)) {
            return; // l'overlay de Forge a pris la place de celui-ci
        }
        if (failed) {
            RenderSystem.clearColor(0f, 0f, 0f, 1f);
            RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        }

        if (fadeOutStart >= 0L && fade >= 1f) {
            // Garde défensive : un overlay remplacé n'est plus rendu, cette branche ne s'atteint donc qu'avec
            // getOverlay() == this ; la vérification évite seulement de retirer un overlay qui ne serait pas le nôtre.
            if (minecraft.getOverlay() == this) {
                minecraft.setOverlay(null);
            }
            scene.log("overlay du mod : retiré, écran titre");
            release();
            return;
        }

        if (fadeOutStart < 0L && reload.isDone()) {
            fadeOutStart = now;
            scene.log(String.format(Locale.ROOT,
                    "overlay du mod : rechargement fini en %.2f s, fondu de la barre et du texte (%d ms)",
                    (now - createdMillis) / 1000.0, UI_FADE_MS));
            try {
                reload.checkExceptions();
                onFinish.accept(Optional.empty());
            } catch (Throwable throwable) {
                onFinish.accept(Optional.of(throwable));
            }
            if (minecraft.screen != null) {
                minecraft.screen.init(minecraft, minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight());
            }
            if (minecraft.getOverlay() != this) {
                // onFinish a posé un autre overlay (retour aux packs par défaut après une erreur) : plus d'image ici.
                scene.log("overlay du mod : remplacé par " + describe(minecraft.getOverlay()) + ", scène libérée");
                release();
            }
        }
    }

    /**
     * Une image de la scène dans la cible principale du jeu. {@code false} si l'overlay de Forge vient de prendre
     * la place de celui-ci (échec de la première image).
     */
    private boolean drawScene(boolean done, float uiAlpha) {
        RenderTarget target = minecraft.getMainRenderTarget();
        target.bindWrite(true);
        GlStateGuard state = GlStateGuard.save();
        try {
            if (firstFrame && FAIL_FIRST.equals(FAIL_MODE)) {
                throw new IllegalStateException("échec demandé par -Dlostisland.overlay.fail=" + FAIL_FIRST);
            }
            GL32C.glViewport(0, 0, target.width, target.height);
            float actual = reload.getActualProgress();
            scene.renderResources(target.width, target.height, actual, done, uiAlpha);
            long now = Util.getMillis();
            if (TRACE && !done && now - lastProgressLog >= 1000L) {
                // Relevé de mise au point (-Dlostisland.progress.trace=true) : avancement réel et barre montrée.
                lastProgressLog = now;
                scene.log(String.format(Locale.ROOT, "overlay du mod : rechargement à %.3f, barre à %.3f", actual,
                        scene.lastState() == null ? 0f : scene.lastState().progress()));
            }
            if (firstFrame) {
                firstFrame = false;
                scene.log("overlay du mod : première image de la scène dans le contexte du jeu, "
                        + target.width + "x" + target.height);
            }
            return true;
        } catch (Throwable t) {
            failed = true;
            if (firstFrame) {
                firstFrame = false;
                scene.log("overlay du mod : première image en échec (" + t + "), l'overlay de Forge prend la main");
                state.restore();
                state = null;
                return !handOverToForge();
            }
            scene.log("overlay du mod : rendu de la scène en échec, image noire jusqu'à l'écran titre : " + t);
            return true;
        } finally {
            if (state != null) {
                state.restore();
            }
        }
    }

    /** Remplace cet overlay par celui de Forge (même rechargement, même {@code onFinish}). {@code true} si fait. */
    private boolean handOverToForge() {
        try {
            Supplier<Overlay> forge = window.forgeOverlay(minecraftSupplier, reloadSupplier, onFinish);
            minecraft.setOverlay(forge.get());
            return true;
        } catch (Throwable t) {
            scene.log("overlay du mod : overlay de Forge indisponible (" + t + "), image noire jusqu'à l'écran titre");
            return false;
        }
    }

    /**
     * Libère la scène (textures, shaders, film) : le chantier 1 n'en a plus l'usage après l'écran titre.
     *
     * <p>Chantier 2 (écran titre animé) : c'est ici qu'il faudra la garder au lieu de la libérer, et la remettre à
     * l'écran titre, qui la libérera à son tour ; {@code LostIslandWindow.sceneState()} reste non nul tant qu'elle
     * n'est pas libérée.
     */
    private void release() {
        if (released) {
            return;
        }
        released = true;
        try {
            scene.close();
            scene.log("overlay du mod : scène libérée");
        } catch (Throwable t) {
            scene.log("overlay du mod : libération de la scène incomplète : " + t);
        }
    }

    private static String describe(Overlay overlay) {
        return overlay == null ? "aucun" : overlay.getClass().getName();
    }
}
