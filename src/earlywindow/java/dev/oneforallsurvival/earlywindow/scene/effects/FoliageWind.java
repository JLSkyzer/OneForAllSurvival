package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.GlQuad;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;
import java.util.Map;

/**
 * Feuillage au vent : dessine une plaque du décor (far, mid, fg) en décalant de quelques pixels la lecture là où
 * le masque {@code plates/foliage_<plan>.png} marque des feuilles, selon un bruit lent du temps réel (le vent ne
 * s'arrête pas sur l'aube tenue). Pas d'équivalent dans compose.py : c'est l'ajout de la spec (« le feuillage
 * ondule »).
 *
 * <p>Facultatif à deux niveaux : sans masque pour un plan, {@link #draw} renvoie {@code false} et la plaque est
 * dessinée immobile par le rendu de base ; si le shader est refusé ou si le tracé lève, l'appelant retire le vent
 * (une ligne de journal) et le décor reste, immobile.
 */
public final class FoliageWind {
    /** Déplacement maximal par plan, en pixels de la plaque : plus fort au premier plan. */
    private static final Map<String, Float> AMPLITUDE = Map.of("far", 1.2f, "mid", 2.2f, "fg", 3.0f);

    private final SceneManifest manifest;
    private TextureStore textures;
    private EffectProgram program;

    public FoliageWind(SceneManifest manifest) {
        this.manifest = manifest;
    }

    public String name() {
        return "wind";
    }

    /** @throws SceneEffect.EffectUnavailable si le shader du vent est refusé */
    public void init(TextureStore textures, ShaderLoader shaders) throws SceneEffect.EffectUnavailable {
        this.textures = textures;
        program = EffectProgram.load(shaders, name(), "wind");
    }

    /** Masque des feuilles d'un plan, ou 0 s'il manque. */
    public int mask(String layer) {
        return textures == null ? 0 : textures.texture("plates/foliage_" + layer + ".png");
    }

    /**
     * Dessine la plaque {@code layer} (textures prémultipliées {@code texA}/{@code texB} mêlées par {@code mix})
     * avec le vent. Renvoie {@code false}, sans rien dessiner, si ce plan n'a pas de masque.
     */
    public boolean draw(String layer, int texA, int texB, float mix, Ambiance.View view, float gain, double real,
                        float[] refToNdc, GlQuad quad) {
        int mask = mask(layer);
        if (mask == 0 || program == null) {
            return false;
        }
        program.use(new SceneEffect.View(SceneEffect.Pass.MID, null, refToNdc, quad), manifest);
        program.rect(0, 0, manifest.outW(), manifest.outH());
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texA);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, texB == 0 ? texA : texB);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, mask);
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(program.loc("uTexA"), 0);
        glUniform1i(program.loc("uTexB"), 1);
        glUniform1i(program.loc("uMask"), 2);
        glUniform1f(program.loc("uMix"), texB == 0 ? 0f : mix);
        glUniform3f(program.loc("uView"), view.zoom(), view.offX(), view.offY());
        glUniform3f(program.loc("uTint"), gain, gain, gain);
        // Temps réel ramené sur une période longue : la précision des flottants du shader reste bonne.
        glUniform1f(program.loc("uTime"), (float) (real % 3600.0));
        glUniform1f(program.loc("uAmplitude"), AMPLITUDE.getOrDefault(layer, 2f));
        quad.draw();
        return true;
    }

    public void close() {
        if (program != null) {
            program.close();
            program = null;
        }
    }
}
