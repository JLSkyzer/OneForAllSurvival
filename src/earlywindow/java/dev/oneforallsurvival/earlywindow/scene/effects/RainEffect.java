package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;

/**
 * Pluie de l'orage : port de {@code draw_rain}. Deux couches de {@code fx/rain.png}, fixes à l'écran, qui
 * défilent vite et en biais, plus claires pendant un éclair. Tout suit le récit : la pluie cesse avec l'orage,
 * bien avant que le récit se fige.
 */
public final class RainEffect implements SceneEffect {
    private static final float[] COLOR = Ambiance.rgb("#a9bce0");
    // (vitesse, dérive, alpha) des deux couches de compose.py.
    private static final double[][] LAYERS = {{2400, -520, 0.55}, {1500, -340, 0.32}};

    private final SceneManifest manifest;
    private TextureStore textures;
    private EffectProgram program;

    public RainEffect(SceneManifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public String name() {
        return "rain";
    }

    @Override
    public void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable {
        this.textures = textures;
        program = EffectProgram.load(shaders, name(), "rain");
    }

    /** Décalage {@code (ox, oy)} d'une couche : {@code int(t * -drift) % 1024}, {@code int(t * speed) % 1024}. */
    static int[] shift(int layer, double t, int size) {
        double[] l = LAYERS[layer];
        return new int[] {(int) Math.floorMod((long) (t * -l[1]), (long) size),
                (int) Math.floorMod((long) (t * l[0]), (long) size)};
    }

    @Override
    public void render(SceneState state, View view) {
        if (view.pass() != Pass.WEATHER) {
            return;
        }
        float storm = view.ambiance().storm();
        if (storm < 0.01f) {
            return;
        }
        double t = state.storyTime();
        double flash = EffectMath.flash(t, storm);
        int tex = EffectProgram.texture(textures, "fx/rain.png");
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
        int size = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        program.use(view, manifest);
        program.rect(0, 0, manifest.outW(), manifest.outH());
        glUniform1i(program.loc("uTex"), 0);
        int[] s0 = shift(0, t, size);
        int[] s1 = shift(1, t, size);
        glUniform2i(program.loc("uShift0"), s0[0], s0[1]);
        glUniform2i(program.loc("uShift1"), s1[0], s1[1]);
        double gain = storm * (0.75 + flash);
        glUniform1f(program.loc("uAlpha0"), (float) (LAYERS[0][2] * gain));
        glUniform1f(program.loc("uAlpha1"), (float) (LAYERS[1][2] * gain));
        glUniform3f(program.loc("uColor"), COLOR[0] * 0.5f, COLOR[1] * 0.5f, COLOR[2] * 0.5f);
        view.quad().draw();
    }

    @Override
    public void close() {
        if (program != null) {
            program.close();
            program = null;
        }
    }
}
