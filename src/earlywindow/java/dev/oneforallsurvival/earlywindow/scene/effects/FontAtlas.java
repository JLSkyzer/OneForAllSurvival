package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.BitmapFont;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/**
 * Glyphes d'une {@link BitmapFont} rangés dans une texture R8, et leur tracé : port de {@code Font.draw} de
 * compose.py (ombre décalée d'un pixel agrandi, couleur × 0,25, alpha × 0,9, puis le texte, en
 * {@code blit_mask}). Chaque glyphe est un carré lu au texel près ({@code texelFetch}) : l'agrandissement entier
 * de {@code np.kron} est exact. Thread GL.
 */
final class FontAtlas {
    private static final int WIDTH = 1024;

    private final BitmapFont font;
    private final Map<BitmapFont.Glyph, int[]> slots = new IdentityHashMap<>();
    private int texture;

    FontAtlas(BitmapFont font) {
        this.font = font;
    }

    BitmapFont font() {
        return font;
    }

    /** Crée la texture au premier tracé (le constructeur ne fait aucun appel GL). */
    private void ensure() {
        if (texture != 0) {
            return;
        }
        int x = 0;
        int y = 0;
        int row = 0;
        for (BitmapFont.Glyph g : font.glyphs().values()) {
            if (x + g.width() > WIDTH) {
                x = 0;
                y += row + 1;
                row = 0;
            }
            slots.put(g, new int[] {x, y});
            x += g.width() + 1;
            row = Math.max(row, g.height());
        }
        int height = y + row + 1;
        ByteBuffer pixels = MemoryUtil.memCalloc(WIDTH * height);
        try {
            for (Map.Entry<BitmapFont.Glyph, int[]> e : slots.entrySet()) {
                BitmapFont.Glyph g = e.getKey();
                int[] at = e.getValue();
                for (int gy = 0; gy < g.height(); gy++) {
                    for (int gx = 0; gx < g.width(); gx++) {
                        pixels.put((at[1] + gy) * WIDTH + at[0] + gx, g.alpha()[gy * g.width() + gx]);
                    }
                }
            }
            int previous = glGetInteger(GL_UNPACK_ALIGNMENT);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, WIDTH, height, 0, GL_RED, GL_UNSIGNED_BYTE, pixels);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glPixelStorei(GL_UNPACK_ALIGNMENT, previous);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    /**
     * {@code font.draw(frame, text, x, y, scale, color, alpha, shadow)} ; renvoie la largeur dessinée. Le
     * programme {@code ui} doit être actif (fondu prémultiplié en place).
     */
    int draw(EffectProgram ui, SceneEffect.View view, String text, float x, float y, int scale, float[] color,
             double alpha, boolean shadow) {
        if (alpha <= 0) {
            return font.width(text, scale);
        }
        ensure();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(ui.loc("uTex"), 0);
        glUniform1i(ui.loc("uMode"), UiMode.GLYPH);
        glUniform1f(ui.loc("uAlphaScale"), 1f);
        if (shadow) {
            float[] dark = {color[0] * 0.25f, color[1] * 0.25f, color[2] * 0.25f};
            glyphs(ui, view, text, x + scale, y + scale, scale, dark, alpha * 0.9);
        }
        glyphs(ui, view, text, x, y, scale, color, alpha);
        return font.width(text, scale);
    }

    private void glyphs(EffectProgram ui, SceneEffect.View view, String text, float x, float y, int scale,
                        float[] color, double alpha) {
        // blit_mask place la ligne à int(x), int(y).
        int ox = (int) x;
        int oy = (int) y;
        glUniform3fv(ui.loc("uColor"), color);
        glUniform1f(ui.loc("uAlpha"), (float) alpha);
        for (BitmapFont.Placed p : font.layout(text)) {
            BitmapFont.Glyph g = p.glyph();
            int rows = Math.min(g.height(), BitmapFont.LINE - g.top());
            if (rows <= 0) {
                continue;
            }
            int[] at = slots.get(g);
            ui.rect(ox + p.x() * scale, oy + g.top() * scale, g.width() * scale, rows * scale);
            glUniform4f(ui.loc("uCell"), at[0], at[1], g.width(), rows);
            view.quad().draw();
        }
    }

    void close() {
        if (texture != 0) {
            glDeleteTextures(texture);
            texture = 0;
        }
    }

    /** Modes du programme {@code ui.frag}. */
    static final class UiMode {
        static final int SPRITE = 0;
        static final int RECT = 1;
        static final int GLYPH = 2;
        static final int SHEEN = 3;

        private UiMode() {
        }
    }
}
