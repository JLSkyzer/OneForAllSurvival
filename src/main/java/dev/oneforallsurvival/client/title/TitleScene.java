package dev.oneforallsurvival.client.title;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.earlywindow.EarlyScene;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;

/**
 * Détenteur côté jeu de la scène animée de l'écran titre : celle de l'overlay du chargement, confiée sans être
 * libérée, ou une scène recréée sur l'aube au retour d'un monde. Ce que décide {@link SceneLifecycle}, cette classe
 * le fait.
 *
 * <p><b>Propriété de la scène et des ressources GL.</b>
 * <ul>
 *   <li>{@code LOADING_OVERLAY} : l'overlay du mod ({@code LostIslandLoadingOverlay}) la tient et la dessine ; rien
 *       ici ne la touche ;</li>
 *   <li>{@code TITLE} : ce détenteur la tient ; l'écran titre la fait dessiner par {@link #render} ;</li>
 *   <li>{@code RECREATING} : ce détenteur tient une scène neuve dont les textures arrivent ; {@link #render} les
 *       envoie (budget par image) et ne dessine rien, l'écran titre dessine son repli, puis la scène paraît en
 *       fondu depuis ce repli ;</li>
 *   <li>{@code IN_WORLD} et {@code UNAVAILABLE} : aucune scène, aucune ressource GL.</li>
 * </ul>
 * Tout se passe sur le thread du jeu (thread de rendu), contexte du jeu courant ; un appel venu d'un autre thread est
 * ignoré. La scène de l'overlay n'est confiée qu'une fois le pont de la fenêtre anticipée arrêté, et chaque image et
 * la libération revérifient ce signal (garantie du chantier 1 : jamais deux dessinateurs, jamais une texture
 * détruite sous le pont).
 *
 * <p>Sans jar de service, cette classe se charge sans jamais toucher {@link EarlyScene} : seule l'interface la
 * nomme ; {@link SceneBackend}, qui s'en sert, n'est chargé qu'une fois la scène confirmée. Jamais d'exception vers
 * l'écran titre.
 */
public final class TitleScene {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Fondu d'arrivée d'une scène recréée, depuis le fond de repli. */
    static final long FADE_IN_MS = 800L;
    /** Fond de repli (dégradé sombre, haut puis bas), en ARGB : celui de l'écran titre sans scène et du fondu. */
    public static final int FALLBACK_TOP = 0xFF0B1022;
    public static final int FALLBACK_BOTTOM = 0xFF02040A;

    private static final SceneLifecycle LIFECYCLE = new SceneLifecycle();
    private static SceneBackend backend; // non nul si et seulement si LIFECYCLE.holdsScene()
    private static long shownAt = -1L; // première image dessinée d'une scène recréée (Util.getMillis), -1 avant
    private static boolean unavailableLogged;

    private TitleScene() {}

    // --- Appels de l'overlay du chargement --------------------------------------------------------------

    /** L'overlay du mod est créé : il tient la scène de la fenêtre anticipée jusqu'à sa fin. */
    public static void loadingOverlayStarted() {
        if (onGameThread("loadingOverlayStarted")) {
            LIFECYCLE.overlayStarted();
        }
    }

    /**
     * Fin de l'overlay avec un écran titre derrière lui : la scène passe à ce détenteur sans être libérée. À
     * n'appeler qu'une fois le pont arrêté ({@code bridgeStopped} vrai) ; il est revérifié avant chaque image.
     *
     * @return {@code true} si la scène est prise ; {@code false} si elle est refusée (la scène reste alors à
     *         l'overlay, qui la libère comme au chantier 1)
     */
    public static boolean adoptFromLoading(EarlyScene scene, BooleanSupplier bridgeStopped) {
        if (!onGameThread("adoptFromLoading") || LIFECYCLE.state() != SceneLifecycle.State.LOADING_OVERLAY) {
            return false;
        }
        try {
            backend = SceneBackend.adopt(scene, bridgeStopped);
            LIFECYCLE.handedOver();
            backend.log("écran titre : scène reçue de l'overlay, le récit continue");
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Écran titre : scène de l'overlay refusée, fond de repli", t);
            backend = null;
            LIFECYCLE.overlayFailed();
            return false;
        }
    }

    /** L'overlay a libéré la scène lui-même (pas d'écran titre derrière lui) : un titre plus tard la recréera. */
    public static void loadingReleased() {
        if (onGameThread("loadingReleased")) {
            LIFECYCLE.overlayReleased();
        }
    }

    /** L'overlay a échoué (overlay de Forge à sa place, ou image noire) : fond de repli pour la session. */
    public static void loadingFailed() {
        if (onGameThread("loadingFailed")) {
            LIFECYCLE.overlayFailed();
        }
    }

    // --- Écran titre -----------------------------------------------------------------------------------

    /**
     * La scène de l'écran titre : celle tenue, ou une scène recréée au retour d'un monde (une seule tentative par
     * session). Vide s'il n'y en a pas : l'écran titre dessine son fond de repli. Jamais d'exception.
     */
    public static Optional<EarlyScene> acquire() {
        if (!onGameThread("acquire")) {
            return Optional.empty();
        }
        try {
            SceneLifecycle.Request request = LIFECYCLE.acquire();
            if (request == SceneLifecycle.Request.CREATE) {
                create();
            }
            if (LIFECYCLE.state() == SceneLifecycle.State.UNAVAILABLE && !unavailableLogged) {
                unavailableLogged = true;
                LOGGER.info("Écran titre : pas de scène animée pour cette session, fond de repli");
            }
            return backend == null ? Optional.empty() : Optional.of(backend.scene());
        } catch (Throwable t) {
            fail("demande de la scène", t);
            return Optional.empty();
        }
    }

    /**
     * Dessine la scène plein cadre dans la cible principale, sous l'interface (à appeler en premier dans le rendu de
     * l'écran). {@code true} si elle a été dessinée ; {@code false} sinon (pas de scène, textures en chargement,
     * pont pas arrêté, échec) : l'écran titre dessine alors son fond de repli. Une scène recréée paraît en fondu
     * depuis ce repli ({@value #FADE_IN_MS} ms). Jamais d'exception.
     */
    public static boolean render(GuiGraphics graphics, float partialTick) {
        if (!onGameThread("render") || acquire().isEmpty()) {
            return false;
        }
        SceneBackend b = backend;
        try {
            if (!b.prepare()) {
                return false;
            }
            if (LIFECYCLE.state() == SceneLifecycle.State.RECREATING) {
                LIFECYCLE.sceneReady();
                b.log("écran titre : scène recréée prête, fondu d'arrivée");
            }
            graphics.flush(); // ce qui a été mis en file avant passe sous la scène, pas dessus
            if (!b.draw()) {
                return false;
            }
            if (b.recreated()) {
                long now = Util.getMillis();
                if (shownAt < 0L) {
                    shownAt = now;
                }
                float fade = Math.min(1f, (now - shownAt) / (float) FADE_IN_MS);
                if (fade < 1f) {
                    int alpha = Math.round((1f - fade) * 255f) << 24;
                    graphics.fillGradient(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                            alpha | (FALLBACK_TOP & 0xFFFFFF), alpha | (FALLBACK_BOTTOM & 0xFFFFFF));
                }
            }
            return true;
        } catch (Throwable t) {
            fail("image de la scène", t);
            return false;
        }
    }

    // --- Jeu -------------------------------------------------------------------------------------------

    /**
     * Un monde est rejoint : la scène et toutes ses ressources GL (textures, programmes, tampons) sont libérées,
     * la mémoire vidéo estimée au journal. Rien à faire sans scène. Jamais d'exception.
     */
    public static void releaseForWorld() {
        if (!onGameThread("releaseForWorld")) {
            return;
        }
        if (LIFECYCLE.worldJoined()) {
            free("entrée dans un monde");
        }
    }

    /** État courant (journal, captures de contrôle). */
    public static SceneLifecycle.State state() {
        return LIFECYCLE.state();
    }

    // --- Interne ---------------------------------------------------------------------------------------

    private static void create() {
        long begin = System.nanoTime();
        try {
            backend = SceneBackend.create(line -> LOGGER.info("[scène du titre] {}", line));
            shownAt = -1L;
            LOGGER.info(String.format(Locale.ROOT,
                    "Écran titre : scène recréée sur l'aube en %.1f ms (textures en chargement, fond de repli)",
                    (System.nanoTime() - begin) / 1e6));
        } catch (Throwable t) {
            backend = null;
            LIFECYCLE.creationFailed();
            LOGGER.warn("Écran titre : recréation de la scène impossible, fond de repli pour la session", t);
        }
    }

    private static void fail(String what, Throwable t) {
        LOGGER.warn("Écran titre : {} en échec, scène retirée, fond de repli pour la session", what, t);
        if (LIFECYCLE.renderFailed()) {
            free("échec");
        }
        backend = null;
    }

    private static void free(String why) {
        SceneBackend b = backend;
        backend = null;
        shownAt = -1L;
        if (b == null) {
            return;
        }
        try {
            long bytes = b.close();
            if (bytes < 0L) {
                LOGGER.warn("Écran titre : pont de la fenêtre anticipée pas arrêté, scène gardée ({})", why);
                return;
            }
            String line = String.format(Locale.ROOT, "écran titre : scène libérée (%s), textures ≈ %.1f Mio", why,
                    bytes / (1024.0 * 1024.0));
            LOGGER.info(line);
            b.log(line);
        } catch (Throwable t) {
            LOGGER.warn("Écran titre : libération de la scène incomplète ({})", why, t);
        }
    }

    private static boolean onGameThread(String call) {
        if (RenderSystem.isOnRenderThread()) {
            return true;
        }
        LOGGER.warn("Écran titre : {} appelé hors du thread de rendu ({}), ignoré", call,
                Thread.currentThread().getName());
        return false;
    }
}
