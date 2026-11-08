package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.GlQuad;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;

/**
 * Bloom de compose.py, appliqué au tampon de la scène après la pluie et les flashs, avant l'étalonnage : un
 * pixel sur quatre, seuil à 0,72, flou gaussien d'écart-type 9 (en pixels réduits, donc 36 px de l'image de
 * référence), ajouté × 0,9. Deux tampons RGBA16F au quart de la fenêtre.
 *
 * <p>Facultatif : si le shader est refusé, ou si le tracé lève, l'appelant retire le bloom (une ligne de
 * journal) et l'image reste, sans halo lumineux.
 */
public final class Bloom {
    private static final float SIGMA = 9f;
    private static final float GAIN = 0.9f;

    private final SceneManifest manifest;
    private EffectProgram program;
    private final int[] fbo = new int[2];
    private final int[] tex = new int[2];
    private int smallW;
    private int smallH;

    public Bloom(SceneManifest manifest) {
        this.manifest = manifest;
    }

    /** @throws SceneEffect.EffectUnavailable si le shader du bloom est refusé */
    public void init(ShaderLoader shaders) throws SceneEffect.EffectUnavailable {
        program = EffectProgram.load(shaders, "bloom", "bloom");
    }

    /**
     * Ajoute le bloom au tampon de la scène. En sortie, {@code sceneFbo} est de nouveau lié, sur toute la
     * fenêtre ; le fondu est laissé à l'appelant.
     *
     * @param scale échelle de l'image de référence vers la fenêtre (recadrage)
     */
    public void apply(int sceneFbo, int sceneTex, int width, int height, float scale, float[] refToNdc, GlQuad quad) {
        ensureTargets((width + 3) / 4, (height + 3) / 4);
        SceneEffect.View view = new SceneEffect.View(SceneEffect.Pass.WEATHER, null, refToNdc, quad);
        program.use(view, manifest);
        program.rect(0, 0, manifest.outW(), manifest.outH());
        glUniform1i(program.loc("uSource"), 0);
        glActiveTexture(GL_TEXTURE0);
        glDisable(GL_BLEND);

        // Seuil : small = frame[::4, ::4], clip(small - 0.72, 0, 2).
        target(0);
        source(sceneTex, width, height);
        glUniform1i(program.loc("uMode"), 0);
        quad.draw();

        // Flou séparable, horizontal puis vertical.
        float sigma = SIGMA * scale;
        glUniform1i(program.loc("uMode"), 1);
        glUniform1f(program.loc("uSigma"), sigma);
        glUniform1i(program.loc("uRadius"), (int) (4 * sigma + 0.5f));
        target(1);
        source(tex[0], smallW, smallH);
        glUniform2i(program.loc("uAxis"), 1, 0);
        quad.draw();
        target(0);
        source(tex[1], smallW, smallH);
        glUniform2i(program.loc("uAxis"), 0, 1);
        quad.draw();

        // Ajout à l'image (alpha 0 : fondu prémultiplié = ajout pur).
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
        glViewport(0, 0, width, height);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        source(tex[0], smallW, smallH);
        glUniform1i(program.loc("uMode"), 2);
        glUniform1f(program.loc("uGain"), GAIN);
        quad.draw();
    }

    private void target(int i) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo[i]);
        glViewport(0, 0, smallW, smallH);
    }

    private void source(int texture, int w, int h) {
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform2i(program.loc("uSourceSize"), w, h);
    }

    private void ensureTargets(int w, int h) {
        if (fbo[0] != 0 && w == smallW && h == smallH) {
            return;
        }
        deleteTargets();
        for (int i = 0; i < 2; i++) {
            tex[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, tex[i]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_HALF_FLOAT, (java.nio.ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            fbo[i] = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo[i]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex[i], 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("tampon du bloom incomplet");
            }
        }
        smallW = w;
        smallH = h;
    }

    private void deleteTargets() {
        for (int i = 0; i < 2; i++) {
            if (fbo[i] != 0) {
                glDeleteFramebuffers(fbo[i]);
                glDeleteTextures(tex[i]);
                fbo[i] = 0;
                tex[i] = 0;
            }
        }
    }

    public void close() {
        deleteTargets();
        if (program != null) {
            program.close();
            program = null;
        }
    }
}
