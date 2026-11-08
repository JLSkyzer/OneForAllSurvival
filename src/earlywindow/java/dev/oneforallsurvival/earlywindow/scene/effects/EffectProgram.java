package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;
import java.util.HashMap;
import java.util.Map;

/** Programme GLSL d'un effet (sommets {@code quad.vert}), ses uniformes mis en cache et le placement du carré. */
final class EffectProgram {
    final int id;
    private final Map<String, Integer> locations = new HashMap<>();

    private EffectProgram(int id) {
        this.id = id;
    }

    /** Charge {@code quad.vert} + {@code <fragment>.frag} ; un refus du pilote rend l'effet indisponible. */
    static EffectProgram load(ShaderLoader shaders, String effect, String fragment)
            throws SceneEffect.EffectUnavailable {
        try {
            return new EffectProgram(shaders.load("quad", fragment));
        } catch (RuntimeException e) {
            throw new SceneEffect.EffectUnavailable(
                    "shader « " + fragment + " » de l'effet « " + effect + " » refusé : " + e.getMessage(), e);
        }
    }

    /** Active le programme et règle le recadrage de l'image de référence sur la fenêtre. */
    void use(SceneEffect.View view, SceneManifest m) {
        glUseProgram(id);
        glUniform2f(loc("uRefCenter"), m.outW() / 2f, m.outH() / 2f);
        glUniform2f(loc("uRefToNdc"), view.refToNdc()[0], view.refToNdc()[1]);
        glUniform2f(loc("uPlate"), m.plateW(), m.plateH());
        glUniform2f(loc("uOut"), m.outW(), m.outH());
    }

    /** Place le carré à un rectangle de l'image de référence (pixels, y vers le bas). */
    void rect(float x, float y, float w, float h) {
        glUniform4f(loc("uRect"), x, y, w, h);
    }

    int loc(String name) {
        return locations.computeIfAbsent(name, n -> glGetUniformLocation(id, n));
    }

    void close() {
        glDeleteProgram(id);
    }

    /** Largeur et hauteur d'une texture (taille du sprite de compose.py). Laisse la texture liée. */
    static int[] size(int texture) {
        glBindTexture(GL_TEXTURE_2D, texture);
        return new int[] {glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH),
                glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT)};
    }

    /** Texture d'effet envoyée, ou une erreur qui retire l'effet (image facultative absente ou illisible). */
    static int texture(TextureStore textures, String path) {
        int id = textures.texture(path);
        if (id == 0) {
            throw new IllegalStateException("texture absente : " + path);
        }
        return id;
    }
}
