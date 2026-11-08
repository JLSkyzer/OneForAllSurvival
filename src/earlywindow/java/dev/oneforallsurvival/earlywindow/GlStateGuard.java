package dev.oneforallsurvival.earlywindow;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Photographie de l'état OpenGL touché par la scène, rendue telle quelle après le dessin.
 *
 * <p>Après le passage de la fenêtre à Minecraft, la scène dessine dans le contexte du jeu, dont
 * {@code GlStateManager} garde en cache ce qu'il croit lié (programme, textures par unité, fondu, tests de
 * profondeur, de faces et de découpe…). {@code SceneRenderer.render} ne restaure rien (ruling 11) : sans cette
 * remise en état, l'interface de Minecraft se dessinerait avec nos textures ou sans test de profondeur.
 * Lecture par {@code glGet*}, donc coûteuse : une fois par image, autour du rendu de la scène seulement.
 */
public final class GlStateGuard {
    private static final int UNITS = 12;

    private final int program;
    private final int vao;
    private final int arrayBuffer;
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int[] viewport = new int[4];
    private final int activeTexture;
    private final int[] textures2d;
    private final int[] texturesArray;
    private final boolean blend;
    private final boolean depthTest;
    private final boolean cullFace;
    private final boolean scissorTest;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final int blendEquationRgb;
    private final int blendEquationAlpha;
    private final float[] clearColor = new float[4];
    private final int unpackAlignment;
    private final int packAlignment;

    private GlStateGuard() {
        program = glGetInteger(GL_CURRENT_PROGRAM);
        vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glGetIntegerv(GL_VIEWPORT, viewport);
        activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        int units = Math.min(UNITS, glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));
        textures2d = new int[units];
        texturesArray = new int[units];
        for (int i = 0; i < units; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            textures2d[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            texturesArray[i] = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
        }
        glActiveTexture(activeTexture);
        blend = glIsEnabled(GL_BLEND);
        depthTest = glIsEnabled(GL_DEPTH_TEST);
        cullFace = glIsEnabled(GL_CULL_FACE);
        scissorTest = glIsEnabled(GL_SCISSOR_TEST);
        blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
        unpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        packAlignment = glGetInteger(GL_PACK_ALIGNMENT);
    }

    /** Photographie l'état courant. Thread GL. */
    public static GlStateGuard save() {
        return new GlStateGuard();
    }

    /** Remet l'état photographié. Thread GL. */
    public void restore() {
        for (int i = 0; i < textures2d.length; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, textures2d[i]);
            glBindTexture(GL_TEXTURE_2D_ARRAY, texturesArray[i]);
        }
        glActiveTexture(activeTexture);
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        enable(GL_BLEND, blend);
        enable(GL_DEPTH_TEST, depthTest);
        enable(GL_CULL_FACE, cullFace);
        enable(GL_SCISSOR_TEST, scissorTest);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment);
        glPixelStorei(GL_PACK_ALIGNMENT, packAlignment);
    }

    private static void enable(int cap, boolean on) {
        if (on) {
            glEnable(cap);
        } else {
            glDisable(cap);
        }
    }
}
