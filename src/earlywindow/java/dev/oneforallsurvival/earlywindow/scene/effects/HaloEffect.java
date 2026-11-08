package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;

/**
 * Lueurs : port de {@code draw_halos} (halo des panaches de l'épave, feux du camp qui s'allument au fil du
 * récit) et de la lueur du temple de {@code render(t)}. Intensités sur le récit (feu, progression, jour),
 * vacillements sur le temps réel.
 */
public final class HaloEffect implements SceneEffect {
    private static final float[] PLUME = Ambiance.rgb("#ff7a2a");
    private static final float[] CAMP = Ambiance.rgb("#ffb35c");
    private static final float[] CAMP_CORE = Ambiance.rgb("#fff0c0");
    private static final float[] TEMPLE = Ambiance.rgb("#6fe6d8");

    private final SceneManifest manifest;
    private TextureStore textures;
    private EffectProgram program;

    public HaloEffect(SceneManifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public String name() {
        return "halo";
    }

    @Override
    public void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable {
        this.textures = textures;
        program = EffectProgram.load(shaders, name(), "halo");
    }

    @Override
    public void render(SceneState state, View view) {
        if (view.pass() != Pass.BEHIND_MID && view.pass() != Pass.MID) {
            return;
        }
        Ambiance a = view.ambiance();
        double real = state.realTime();
        int halo = EffectProgram.texture(textures, "fx/halo.png");
        program.use(view, manifest);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, halo);
        glUniform1i(program.loc("uTex"), 0);
        Ambiance.View mid = a.mid();
        if (view.pass() == Pass.BEHIND_MID) {
            // Lueur du temple, signe discret qu'il reste quelque chose de vivant sur l'îlot.
            double glimmer = Ambiance.sstep(0.74, 0.96, a.progress()) * (0.55 + 0.45 * Math.sin(real * 1.7));
            if (glimmer > 0.01) {
                float[] p = mid.point(manifest, manifest.temple()[0], manifest.temple()[1]);
                draw(view, trunc(p[0] - 75), trunc(p[1] - 58), 150, TEMPLE, 0.48 * glimmer, false, 0f);
            }
            return;
        }
        double level = EffectMath.fireLevel(state.storyTime());
        if (level > 0.01) {
            double flicker = EffectMath.flicker(real);
            for (SceneManifest.Plume plume : manifest.plumes()) {
                float[] p = mid.point(manifest, plume.x(), plume.y());
                double size = plume.scale() * 34 * (0.7 + plume.power() * 0.09);
                int side = Math.max(2, (int) size);
                draw(view, trunc(p[0] - size / 2), trunc(p[1] - size / 2), side, PLUME, 0.42 * level * flicker,
                        false, 0f);
            }
        }
        double night = 1 - Ambiance.sstep(1.1, 1.9, a.day());
        for (int i = 0; i < manifest.camp().size(); i++) {
            SceneManifest.Camp camp = manifest.camp().get(i);
            double on = Ambiance.sstep(camp.at(), camp.at() + 0.04, a.progress()) * (0.25 + 0.75 * night);
            if (on <= 0.01) {
                continue;
            }
            double wobble = 0.86 + 0.14 * Math.sin(real * (9 + i) + i * 2.1);
            float[] p = mid.point(manifest, camp.x(), camp.y());
            draw(view, trunc(p[0] - 32), trunc(p[1] - 32), 64, CAMP, 0.5 * on * wobble, false, 0f);
            // Le foyer lui-même : un carré de 4 px qui recouvre le décor.
            draw(view, (int) p[0] - 2, (int) p[1] - 2, 4, CAMP_CORE, on, true, 1f);
        }
    }

    /** {@code blit_add} du halo (alphaScale 0) ou carré plein recouvrant (solid, alphaScale 1). */
    private void draw(View view, float x, float y, float side, float[] color, double alpha, boolean solid,
                      float alphaScale) {
        program.rect(x, y, side, side);
        glUniform3fv(program.loc("uColor"), color);
        glUniform1f(program.loc("uAlpha"), (float) alpha);
        glUniform1i(program.loc("uSolid"), solid ? 1 : 0);
        glUniform1f(program.loc("uAlphaScale"), alphaScale);
        view.quad().draw();
    }

    /** {@code int()} de Python, appliqué par {@code clip_rect} aux positions des sprites. */
    static float trunc(double v) {
        return (int) v;
    }

    @Override
    public void close() {
        if (program != null) {
            program.close();
            program = null;
        }
    }
}
