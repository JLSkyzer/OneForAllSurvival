package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;

/**
 * Éclairs de l'orage : port de {@code draw_bolt} (l'arc, dans le ciel derrière l'île, passe {@link Pass#SKY}) et
 * du flash de {@code flash_at} (toute l'image s'éclaircit puis retombe, passe {@link Pass#WEATHER}). Instants
 * fixes du récit ({@link EffectMath#FLASHES}).
 */
public final class BoltEffect implements SceneEffect {
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] FLASH = Ambiance.rgb("#9db8ff");
    /** Abscisse de l'arc de chaque éclair (compose.py), en pixels de l'image de référence. */
    private static final int[] BOLT_X = {520, 1230, 760, 900, 1050, 300};
    private static final double BOLT_LIFE = 0.16;

    private final SceneManifest manifest;
    private TextureStore textures;
    private EffectProgram halo;
    private EffectProgram flash;

    public BoltEffect(SceneManifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public String name() {
        return "bolts";
    }

    @Override
    public void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable {
        this.textures = textures;
        halo = EffectProgram.load(shaders, name(), "halo");
        flash = EffectProgram.load(shaders, name(), "flash");
    }

    @Override
    public void render(SceneState state, View view) {
        double t = state.storyTime();
        if (view.pass() == Pass.SKY) {
            drawBolts(view, t);
        } else if (view.pass() == Pass.WEATHER) {
            drawFlash(view, EffectMath.flash(t, view.ambiance().storm()));
        }
    }

    /** {@code blit_add} de l'arc, blanc, pendant 0,16 s après chaque éclair assez fort. */
    private void drawBolts(View view, double t) {
        for (int index = 0; index < EffectMath.FLASHES.length; index++) {
            double dt = t - EffectMath.FLASHES[index][0];
            double power = EffectMath.FLASHES[index][1];
            if (dt < 0 || dt >= BOLT_LIFE || power <= 0.6) {
                continue;
            }
            int tex = EffectProgram.texture(textures, "fx/bolt_" + (index % 3) + ".png");
            halo.use(view, manifest);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, tex);
            int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
            int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
            glUniform1i(halo.loc("uTex"), 0);
            halo.rect(BOLT_X[index], -20, w, h);
            glUniform3fv(halo.loc("uColor"), WHITE);
            glUniform1f(halo.loc("uAlpha"), (float) (power * (1 - dt / BOLT_LIFE)));
            glUniform1i(halo.loc("uSolid"), 0);
            glUniform1f(halo.loc("uAlphaScale"), 0f);
            view.quad().draw();
        }
    }

    /** {@code frame = frame * (1 + 2.2 * flash) + #9db8ff * 0.18 * flash}. */
    private void drawFlash(View view, double value) {
        if (value < 1e-4) {
            return; // éclat imperceptible (moins d'un dix-millième) : une passe plein écran épargnée
        }
        flash.use(view, manifest);
        flash.rect(0, 0, manifest.outW(), manifest.outH());
        float k = (float) (0.18 * value);
        glUniform3f(flash.loc("uAdd"), FLASH[0] * k, FLASH[1] * k, FLASH[2] * k);
        glUniform1f(flash.loc("uGain"), (float) (1 + 2.2 * value));
        // Tampon flottant : le facteur SRC_ALPHA n'est pas écrêté à 1. L'alpha de la scène reste intact.
        glBlendFuncSeparate(GL_ONE, GL_SRC_ALPHA, GL_ZERO, GL_ONE);
        try {
            view.quad().draw();
        } finally {
            glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    @Override
    public void close() {
        if (halo != null) {
            halo.close();
            halo = null;
        }
        if (flash != null) {
            flash.close();
            flash = null;
        }
    }
}
