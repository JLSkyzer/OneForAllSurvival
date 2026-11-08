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
 * Braises qui montent de l'épave : port de {@code draw_embers}, 170 carrés de 2 à 4 px ajoutés à l'image.
 *
 * <p>Le cycle de chaque braise suit le temps réel (elles continuent de monter sur l'aube tenue) ; l'intensité
 * suit le feu du récit. Une braise n'apparaît qu'une fois son premier cycle commencé depuis l'impact, mesuré en
 * temps de récit, comme dans compose.py.
 */
public final class EmberEffect implements SceneEffect {
    private static final float[] COLOR = Ambiance.rgb("#ff8a2c");
    private static final int COUNT = 170;

    /** Une braise : coin haut-gauche (pixels entiers de référence), côté, intensité ajoutée. */
    public record Spark(int x, int y, int size, float alpha) {
    }

    private final SceneManifest manifest;
    private EffectProgram program;

    public EmberEffect(SceneManifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public String name() {
        return "embers";
    }

    @Override
    public void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable {
        program = EffectProgram.load(shaders, name(), "ember");
    }

    /** Les braises visibles d'une image. */
    public static List<Spark> sparks(SceneManifest m, Ambiance a, float storyTime, double realTime) {
        List<Spark> out = new ArrayList<>();
        if (storyTime < StoryClock.IMPACT) {
            return out;
        }
        double sinceImpact = storyTime - StoryClock.IMPACT;
        double cycle = realTime - StoryClock.IMPACT;
        double level = EffectMath.fireLevel(storyTime);
        double wx = m.wreck()[0];
        double wy = m.wreck()[1];
        Ambiance.View view = a.mid();
        for (int i = 0; i < COUNT; i++) {
            double life = 2.5 + EffectMath.mod(i * 0.37, 3.0);
            double offset = EffectMath.mod(i * 0.7713, life);
            double age = EffectMath.mod(cycle + offset, life);
            if (sinceImpact < age && sinceImpact < offset) {
                continue;
            }
            double h = EffectMath.mod(i * 0.61803, 1.0);
            double spread = (h - 0.5) * 460;
            double rise = age * (42 + h * 90);
            double side = Math.pow(age, 1.3) * 16 + Math.sin(age * 2.5 + i) * 13;
            float[] p = view.point(m, wx + spread + side, wy + 18 - rise);
            int size = 2 + (i % 3);
            double alpha = (1 - age / life) * level;
            int x = (int) p[0];
            int y = (int) p[1];
            if (0 <= x && x < m.outW() - size && 0 <= y && y < m.outH() - size && alpha > 0.02) {
                out.add(new Spark(x, y, size, (float) (alpha * 1.2)));
            }
        }
        return out;
    }

    @Override
    public void render(SceneState state, View view) {
        if (view.pass() != Pass.MID) {
            return;
        }
        List<Spark> sparks = sparks(manifest, view.ambiance(), state.storyTime(), state.realTime());
        if (sparks.isEmpty()) {
            return;
        }
        program.use(view, manifest);
        glUniform3fv(program.loc("uColor"), COLOR);
        for (Spark s : sparks) {
            program.rect(s.x(), s.y(), s.size(), s.size());
            glUniform1f(program.loc("uAlpha"), s.alpha());
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
