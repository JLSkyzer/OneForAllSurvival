package dev.oneforallsurvival.earlywindow.scene.effects;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.GlQuad;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;

/**
 * Effet facultatif de la scène (feux, fumées, braises, halos, orage, crash). Un effet qui échoue, à
 * l'initialisation ou en cours de rendu, est retiré seul par {@link EffectChain} : le reste de la scène continue
 * de s'afficher.
 *
 * <p>Comme dans compose.py, un effet se déduit entièrement du temps (aucun état, aucun générateur aléatoire
 * global). Son <em>intensité</em> suit le récit ({@code storyTime}, {@link Ambiance}) ; son <em>mouvement</em>
 * (scintillement, fumée qui monte, braises) suit {@link SceneState#realTime()}, pour rester vivant sur l'aube
 * tenue, quand {@code storyTime} est figé.
 */
public interface SceneEffect {
    /** Nom court, stable, utilisé par le journal et par {@link EffectChain#active()}. */
    String name();

    /**
     * Prépare l'effet (programmes GLSL par {@code shaders}). Aucun appel GL direct ici : tout passe par le
     * {@link ShaderLoader}, ce qui permet de simuler un pilote qui refuse un shader. Les textures ne sont
     * peut-être pas encore envoyées : l'effet garde {@code textures} et les lit au rendu.
     *
     * @throws EffectUnavailable si l'effet ne peut pas fonctionner (shader refusé…)
     */
    void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable;

    /**
     * Dessine la part de l'effet qui appartient à {@link View#pass()} dans le tampon de la scène (fondu
     * prémultiplié déjà en place). Thread GL. Une exception retire l'effet.
     */
    void render(SceneState state, View view);

    /** Libère ce que {@link #init} a créé ; doit tolérer une initialisation partielle. Thread GL. */
    void close();

    /** Moment du rendu où la chaîne d'effets est appelée : les emplacements exacts de {@code render(t)}. */
    enum Pass {
        /** Dans le ciel, après les bancs d'orage et avant les rayons de lune : éclairs ({@code draw_bolt}). */
        SKY,
        /** Après l'arrière-plan, avant le calque du milieu : lueur du temple. */
        BEHIND_MID,
        /** Juste après le calque du milieu : lueur des feux sur le décor ({@code A.fire["mid"]}). */
        MID_GLOW,
        /** Halos, fumées, flammes, braises (dans cet ordre, celui de compose.py). */
        MID,
        /** Après l'assombrissement de l'orage, avant le premier plan : l'avion qui tombe ({@code draw_falling_plane}). */
        PLANE,
        /** Après le premier plan : lueur des feux du premier plan ({@code A.fire["fg"]}). */
        FRONT,
        /** Après {@link #FRONT} : pluie, puis flash des éclairs et de l'impact (fin de l'image avant le bloom). */
        WEATHER,
        /** Après le bloom et l'étalonnage, avant l'ouverture depuis le noir : logo (il suit le récit). */
        UI,
        /**
         * Après l'ouverture depuis le noir, dans la fenêtre même : barre, pourcentage, texte d'état et repères,
         * visibles dès les premières images, noir d'ouverture compris (demande de Killian, 2026-11-08).
         */
        HUD
    }

    /**
     * Ce qu'un effet reçoit pour une passe : l'ambiance et les caméras de l'image, plus de quoi dessiner dans
     * l'image de référence (le carré partagé et le recadrage sur la fenêtre).
     *
     * @param refToNdc {@code 2 * échelle / taille de la fenêtre}, sur x et y (uniforme {@code uRefToNdc})
     */
    record View(Pass pass, Ambiance ambiance, float[] refToNdc, GlQuad quad) {
    }

    /** L'effet ne peut pas fonctionner sur cette machine ; le message dit pourquoi. */
    final class EffectUnavailable extends Exception {
        public EffectUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
