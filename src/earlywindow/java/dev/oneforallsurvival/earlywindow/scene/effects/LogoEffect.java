package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.BitmapFont;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneProjection;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.StoryClock;
import dev.oneforallsurvival.earlywindow.scene.Texts;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Logo Lost Island : port de {@code draw_logo} de compose.py. Lettres qui tombent une à une (ease_out_back),
 * lueur additive ({@code glow.png}), respiration, reflet qui balaie la face ({@code face_mask.png}), devise.
 *
 * <p>Horloges : l'arrivée des lettres, la lueur, les deux reflets de la vidéo et la devise suivent le récit
 * ({@code storyTime} et sa progression d'ambiance, comme compose.py). La respiration suit le temps réel ; une
 * fois le récit tenu sur l'aube, un reflet repasse toutes les {@link #HELD_SWEEP_PERIOD} s de temps réel, pour
 * que le logo reste vivant.
 */
public final class LogoEffect implements SceneEffect {
    // Constantes de compose.py.
    private static final double LOAD_END = 29.0;
    private static final int OX = SceneProjection.LOGO_X;
    private static final int OY = SceneProjection.LOGO_Y;
    private static final float[] GLOW = Ambiance.rgb("#ffb040");
    private static final float[] SHEEN = Ambiance.rgb("#fff4d0");
    private static final float[] MOTTO = Ambiance.rgb("#3a1a08");
    private static final float[] WHITE = {1f, 1f, 1f};
    /** Reflet de l'aube tenue : une période, en secondes de temps réel. */
    static final double HELD_SWEEP_PERIOD = 12.0;
    private static final double SWEEP = 1.1;

    private final SceneManifest manifest;
    private final FontAtlas font; // null : pas de police lisible, devise omise
    private TextureStore textures;
    private EffectProgram program;

    public LogoEffect(SceneManifest manifest, BitmapFont font) {
        this.manifest = manifest;
        this.font = font == null ? null : new FontAtlas(font);
    }

    @Override
    public String name() {
        return "logo";
    }

    @Override
    public void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable {
        this.textures = textures;
        program = EffectProgram.load(shaders, name(), "ui");
    }

    /**
     * Reflets en cours : pour chacun, l'avancée {@code u} (0..1) et son intensité. Ceux de la vidéo
     * ({@code start + 1.4} et {@code LOAD_END + 0.25} du récit), puis, récit tenu, un reflet périodique en temps
     * réel dont l'intensité monte et descend aux bords (il peut commencer en cours de passage).
     */
    static List<double[]> sweeps(double story, double real) {
        List<double[]> out = new ArrayList<>();
        double start = StoryClock.BAR_IN + 0.1;
        for (double at : new double[] {start + 1.4, LOAD_END + 0.25}) {
            double u = (story - at) / SWEEP;
            if (u >= 0 && u <= 1) {
                out.add(new double[] {u, 1.0});
            }
        }
        if (story >= StoryClock.HOLD) {
            double u = Math.floorMod((long) Math.floor(real * 1000), (long) (HELD_SWEEP_PERIOD * 1000)) / 1000.0
                    / SWEEP;
            if (u <= 1) {
                out.add(new double[] {u, Ambiance.sstep(0, 0.15, u) * (1 - Ambiance.sstep(0.85, 1, u))});
            }
        }
        return out;
    }

    /** {@code ease_out_back} de compose.py. */
    static double easeOutBack(double t) {
        t = Math.min(1, Math.max(0, t));
        double c = 1.70158;
        return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2);
    }

    @Override
    public void render(SceneState state, View view) {
        if (view.pass() != Pass.UI) {
            return;
        }
        double t = state.storyTime();
        double start = StoryClock.BAR_IN + 0.1;
        if (t < start) {
            return;
        }
        SceneManifest.Logo m = manifest.logo();
        // Légère respiration, pour que le logo reste vivant pendant le chargement (temps réel).
        double bob = Math.sin(state.realTime() * 0.8) * 3;
        program.use(view, manifest);
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(program.loc("uTex"), 0);
        glUniform1i(program.loc("uMode"), FontAtlas.UiMode.SPRITE);

        double glowAlpha = Ambiance.sstep(start + 0.6, start + 1.6, t)
                * (0.25 + 0.35 * Ambiance.sstep(0.9, 1.0, StoryClock.storyProgress((float) t)));
        if (glowAlpha > 0) {
            int glow = EffectProgram.texture(textures, "ui/logo/glow.png");
            int[] size = EffectProgram.size(glow);
            int pad = m.glowPad();
            sprite(view, glow, OX - pad, (int) (OY - pad + bob), size, GLOW, glowAlpha * 0.55, true);
        }
        for (int i = 0; i < m.letters().size(); i++) {
            double local = (t - start - i * 0.085) / 0.62;
            if (local <= 0) {
                continue;
            }
            SceneManifest.Letter letter = m.letters().get(i);
            double yOffset = (1 - easeOutBack(local)) * -230;
            double alpha = Ambiance.sstep(0, 0.22, local);
            int tex = EffectProgram.texture(textures, letter.file());
            sprite(view, tex, OX + letter.x(), (int) (OY + letter.y() + yOffset + bob), EffectProgram.size(tex),
                    WHITE, alpha, false);
        }
        // Reflet qui balaie le logo : à l'arrivée, une fois chargé, puis régulièrement sur l'aube tenue.
        List<double[]> sweeps = sweeps(t, state.realTime());
        if (!sweeps.isEmpty()) {
            int face = EffectProgram.texture(textures, "ui/logo/face_mask.png");
            int[] size = EffectProgram.size(face);
            glUniform1i(program.loc("uMode"), FontAtlas.UiMode.SHEEN);
            glUniform3fv(program.loc("uColor"), SHEEN);
            glUniform4f(program.loc("uCell"), 0, 0, size[0], size[1]);
            program.rect(OX, (int) (OY + bob), size[0], size[1]);
            for (double[] s : sweeps) {
                glUniform1f(program.loc("uSheen"), (float) (-200 + (size[0] + 400) * s[0]));
                glUniform1f(program.loc("uAlpha"), (float) (0.75 * s[1]));
                view.quad().draw();
            }
        }
        // Devise du projet, une fois le chargement du récit terminé.
        double tag = Ambiance.sstep(LOAD_END + 0.6, LOAD_END + 1.5, t);
        if (tag > 0 && font != null) {
            font.draw(program, view, Texts.current().get(Texts.UI_MOTTO), OX + 12, OY + m.height() - 6, 3, MOTTO, tag, false);
        }
    }

    /** {@code blit_over} (add faux) ou {@code blit_add} (add vrai) d'une image à sa taille, en position entière. */
    private void sprite(View view, int tex, int x, int y, int[] size, float[] color, double alpha, boolean add) {
        glBindTexture(GL_TEXTURE_2D, tex);
        program.rect(x, y, size[0], size[1]);
        glUniform3fv(program.loc("uColor"), color);
        glUniform1f(program.loc("uAlpha"), (float) alpha);
        glUniform1i(program.loc("uFromAlpha"), add ? 1 : 0);
        glUniform1f(program.loc("uAlphaScale"), add ? 0f : 1f);
        view.quad().draw();
    }

    @Override
    public void close() {
        if (font != null) {
            font.close();
        }
        if (program != null) {
            program.close();
            program = null;
        }
    }
}
