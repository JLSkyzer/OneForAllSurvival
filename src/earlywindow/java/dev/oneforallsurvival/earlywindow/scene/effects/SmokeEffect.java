package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.StoryClock;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Panaches de fumée de l'épave : port de {@code draw_smoke}. Chaque bouffée se déduit du temps (émission
 * déterministe, hachage de son numéro), sans état.
 *
 * <p>Horloges : l'émission et l'âge des bouffées suivent le temps réel, pour que la fumée continue de monter sur
 * l'aube tenue ; la couleur, l'intensité et le départ à l'impact suivent le récit. Une bouffée n'existe que si
 * elle est plus jeune que le temps de récit écoulé depuis l'impact (équivalent de {@code serial >= 0}). Tant que
 * les deux horloges avancent ensemble (pendant le récit), le résultat est celui de compose.py à l'identique.
 */
public final class SmokeEffect implements SceneEffect {
    private static final float[][] DARK = Ambiance.palette("#2a2c38", "#6b5766", "#b49a8d");
    private static final float[] LIT = Ambiance.rgb("#ff8a3c");
    private static final double LIFE = 7.5;

    /** Une bouffée : image de fumée (0..5), coin haut-gauche et côté en pixels de référence, alpha et teinte. */
    public record Puff(int sprite, float x, float y, int size, float alpha, float[] tint) {
    }

    private final SceneManifest manifest;
    private TextureStore textures;
    private EffectProgram program;

    public SmokeEffect(SceneManifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public String name() {
        return "smoke";
    }

    @Override
    public void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable {
        this.textures = textures;
        program = EffectProgram.load(shaders, name(), "smoke");
    }

    /** Les bouffées d'une image, dans l'ordre de dessin de compose.py (de la plus jeune à la plus vieille). */
    public static List<Puff> puffs(SceneManifest m, Ambiance a, float storyTime, double realTime) {
        List<Puff> out = new ArrayList<>();
        if (storyTime < StoryClock.IMPACT) {
            return out;
        }
        double sinceImpact = storyTime - StoryClock.IMPACT;
        double fire = EffectMath.fireLevel(storyTime);
        float[] dark = Ambiance.lerp3(DARK, a.day());
        Ambiance.View view = a.mid();
        for (int pi = 0; pi < m.plumes().size(); pi++) {
            SceneManifest.Plume plume = m.plumes().get(pi);
            double rate = 2.4 + plume.power() * 0.45;
            int count = (int) (rate * LIFE);
            double scale = plume.scale();
            long newest = (long) Math.floor((realTime - StoryClock.IMPACT) * rate);
            for (int k = 0; k < count; k++) {
                long serial = newest - k;
                double born = StoryClock.IMPACT + serial / rate;
                double age = realTime - born;
                if (age < 0 || age > LIFE || age > sinceImpact) {
                    continue;
                }
                double h1 = EffectMath.hash01(serial, pi);
                double h2 = EffectMath.frac(Math.sin(serial * 39.346 + pi * 11.135) * 24634.6345);
                double u = age / LIFE;
                double rise = Math.pow(age, 0.86) * scale * 3.4;
                double wind = Math.pow(age, 1.35) * scale * 1.15;
                double wob = Math.sin(age * 1.3 + h1 * 6.28) * scale * (0.5 + age * 0.55);
                double size = scale * (5 + age * 3.4) * (0.8 + h2 * 0.5);
                float[] p = view.point(m, plume.x() + wind + wob, plume.y() - rise);
                double level = 0.5 + 0.5 * fire;
                double alpha = Ambiance.sstep(0, 0.08, u) * Math.pow(1 - u, 1.25) * 0.3 * level;
                // resized(..., max(8, round(size / 6) * 6)) : round de Python arrondit au pair, comme rint.
                int side = (int) Math.max(8, Math.rint(size / 6) * 6);
                double heat = (1 - Ambiance.sstep(0.02, 0.22, u)) * fire;
                float[] tint = new float[3];
                for (int c = 0; c < 3; c++) {
                    tint[c] = (float) (dark[c] * (1 - heat) + LIT[c] * heat);
                }
                int sprite = (int) (h1 * 6) % 6;
                out.add(new Puff(sprite, HaloEffect.trunc(p[0] - side / 2.0), HaloEffect.trunc(p[1] - side / 2.0),
                        side, (float) alpha, tint));
            }
        }
        return out;
    }

    @Override
    public void render(SceneState state, View view) {
        if (view.pass() != Pass.MID) {
            return;
        }
        List<Puff> puffs = puffs(manifest, view.ambiance(), state.storyTime(), state.realTime());
        if (puffs.isEmpty()) {
            return;
        }
        int[] sprites = new int[6];
        for (int i = 0; i < 6; i++) {
            sprites[i] = EffectProgram.texture(textures, "fx/smoke_" + i + ".png");
        }
        program.use(view, manifest);
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(program.loc("uTex"), 0);
        int bound = -1;
        for (Puff puff : puffs) {
            if (puff.sprite() != bound) {
                bound = puff.sprite();
                glBindTexture(GL_TEXTURE_2D, sprites[bound]);
            }
            program.rect(puff.x(), puff.y(), puff.size(), puff.size());
            glUniform3fv(program.loc("uTint"), puff.tint());
            glUniform1f(program.loc("uAlpha"), puff.alpha());
            view.quad().draw();
        }
    }

    @Override
    public void close() {
        if (program != null) {
            program.close();
            program = null;
        }
    }
}
