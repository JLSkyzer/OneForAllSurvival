package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.BitmapFont;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.StatusMessages;
import dev.oneforallsurvival.earlywindow.scene.Texts;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;

/**
 * Barre, texte d'état et repères : port de {@code draw_ui} de compose.py. Rail pointillé vers l'île inconnue,
 * traînée dorée (la barre), icône de l'avion à sa tête et son halo, « LOST ISLAND RP » et « ÎLE INCONNUE »,
 * pourcentage.
 *
 * <p>La barre, le pourcentage et le texte suivent le <em>vrai</em> chargement ({@link SceneState#progress()},
 * {@link SceneState#status()}), jamais le récit ; le texte est {@link StatusMessages.Status#line()}. L'arrivée
 * (fondu et glissement, {@link #intro}) suit le temps réel depuis l'arrivée de ses images, pas le récit : la
 * barre est là dès les premières images, noir d'ouverture compris (passe {@link Pass#HUD}, dessinée après
 * l'ouverture depuis le noir). Puis {@link SceneState#uiAlpha()} estompe le tout. Le balancement de l'avion suit
 * le temps réel.
 */
public final class ProgressUi implements SceneEffect {
    private static final int X0 = 96;
    private static final int X1 = 1240;
    private static final int Y = 1006;
    private static final float[] RAIL = {1f, 1f, 1f};
    private static final float[] TIP = Ambiance.rgb("#ffe07a");
    private static final float[] TAIL = Ambiance.rgb("#c8561a");
    private static final float[] HALO = Ambiance.rgb("#ffb24d");
    private static final float[] WHITE = Ambiance.rgb("#f4f1e8");
    private static final float[] GOLD = Ambiance.rgb("#ffd76a");
    private static final float[] GREY = Ambiance.rgb("#c9c6bc");
    private static final float[] ONE = {1f, 1f, 1f};

    private final SceneManifest manifest;
    private final FontAtlas font; // null : pas de police lisible, texte omis
    private TextureStore textures;
    private EffectProgram program;

    public ProgressUi(SceneManifest manifest, BitmapFont font) {
        this.manifest = manifest;
        this.font = font == null ? null : new FontAtlas(font);
    }

    @Override
    public String name() {
        return "ui";
    }

    @Override
    public void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable {
        this.textures = textures;
        program = EffectProgram.load(shaders, name(), "ui");
    }

    /** Durée du fondu d'arrivée de la barre, en secondes réelles depuis l'arrivée de l'interface. */
    public static final double INTRO_SECONDS = 0.5;

    /**
     * Arrivée de la barre et de l'assombrissement du bas (0 à 1) : fondu de {@value #INTRO_SECONDS} s de temps réel
     * depuis l'arrivée de l'interface ({@link SceneState#uiTime()}, quelques dixièmes de seconde après l'ouverture
     * de la fenêtre), indépendant du récit ; 0 avant ({@code NaN}). L'origine est tenue par la scène, partagée par
     * tous les rendus : le rendu allégé du pont ne refait pas l'arrivée.
     */
    public static double intro(double uiTime) {
        return Double.isNaN(uiTime) ? 0.0 : Ambiance.sstep(0.0, INTRO_SECONDS, uiTime);
    }

    /** {@code f"{int(p * 100 + 1e-4)} %"}. */
    static String percent(float p) {
        return (int) (p * 100 + 1e-4) + " %";
    }

    /** Texte d'état : la ligne du message réel, le message de fin (« Bienvenue sur Lost Island. ») barre pleine. */
    static String statusLine(StatusMessages.Status status, float p) {
        if (p >= 1f) {
            return Texts.current().get(Texts.STATUS_DONE);
        }
        return status == null ? "" : status.line();
    }

    @Override
    public void render(SceneState state, View view) {
        if (view.pass() != Pass.HUD) {
            return;
        }
        double intro = intro(state.uiTime());
        double alpha = intro * Math.max(0f, Math.min(1f, state.uiAlpha()));
        if (alpha <= 0) {
            return;
        }
        float p = Math.max(0f, Math.min(1f, state.progress()));
        int width = X1 - X0;
        int y = Y + (int) ((1 - intro) * 26);
        program.use(view, manifest);
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(program.loc("uTex"), 0);

        // Rail : traits pointillés de la route vers l'île inconnue.
        glUniform1i(program.loc("uMode"), FontAtlas.UiMode.RECT);
        for (int x = X0; x < X1; x += 18) {
            rect(view, x, y, 10, 4, RAIL, RAIL, 0.42 * alpha, 1, 1, x, 1, 1f);
        }
        // Traînée dorée : c'est elle, la barre de progression.
        int head = X0 + (int) (width * p);
        if (head > X0) {
            int n = head - X0;
            rect(view, X0, y - 2, n, 8, TAIL, TIP, alpha, 0.65, 1.0, X0, n, 1f);
            // Liseré lumineux en haut (np.minimum(1.2, …) : sans effet, l'image est écrêtée à 1 ensuite).
            rect(view, X0, y - 2, n, 2, ONE, ONE, alpha, 0, 0.18, X0, n, 0f);
        }
        glUniform1i(program.loc("uMode"), FontAtlas.UiMode.SPRITE);
        int plane = EffectProgram.texture(textures, "ui/plane_icon.png");
        int[] size = EffectProgram.size(plane);
        double bob = Math.sin(state.realTime() * 3.1) * 3;
        sprite(view, plane, head - size[0] + 8, (int) (y - size[1] / 2.0 + 2 + bob), size[0], size[1], ONE, alpha,
                false);
        int halo = textures.texture("fx/halo.png");
        if (p > 0 && halo != 0) {
            sprite(view, halo, head - 60, y - 58, 120, 120, HALO, 0.34 * alpha, true);
        }

        // Repères de départ et d'arrivée, et état du chargement.
        if (font != null) {
            BitmapFont f = font.font();
            Texts texts = Texts.current();
            font.draw(program, view, texts.get(Texts.UI_START), X0, y + 16, 2, GREY, alpha, true);
            String end = texts.get(Texts.UI_END);
            font.draw(program, view, end, X1 - f.width(end, 2), y + 16, 2, p > 0.98f ? GOLD : GREY, alpha, true);
            font.draw(program, view, statusLine(state.status(), p), X0, y - 62, 3, WHITE, alpha, true);
            String percent = percent(p);
            font.draw(program, view, percent, X1 - f.width(percent, 4), y - 72, 4, GOLD, alpha, true);
        }
    }

    private void rect(View view, float x, float y, float w, float h, float[] from, float[] to, double alpha,
                      double alphaFrom, double alphaTo, int gradX0, int gradN, float alphaScale) {
        program.rect(x, y, w, h);
        glUniform3fv(program.loc("uColor"), from);
        glUniform3fv(program.loc("uColor2"), to);
        glUniform2f(program.loc("uAlphaGrad"), (float) alphaFrom, (float) alphaTo);
        glUniform2f(program.loc("uGrad"), gradX0, gradN);
        glUniform1f(program.loc("uAlpha"), (float) alpha);
        glUniform1f(program.loc("uAlphaScale"), alphaScale);
        view.quad().draw();
    }

    private void sprite(View view, int tex, float x, float y, float w, float h, float[] color, double alpha,
                        boolean add) {
        glBindTexture(GL_TEXTURE_2D, tex);
        program.rect((int) x, (int) y, w, h);
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
