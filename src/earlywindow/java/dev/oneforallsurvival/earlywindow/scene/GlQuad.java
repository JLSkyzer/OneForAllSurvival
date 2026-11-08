package dev.oneforallsurvival.earlywindow.scene;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Le seul maillage de la scène : un carré unité (0..1), en bande de deux triangles. Le shader de sommets
 * {@code quad.vert} le place dans l'image de référence avec l'uniforme {@code uRect}.
 */
public final class GlQuad implements AutoCloseable {
    private final int vao;
    private final int vbo;

    public GlQuad() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousVbo = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, new float[] {0, 0, 1, 0, 0, 1, 1, 1}, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);
        glBindVertexArray(previousVao);
        glBindBuffer(GL_ARRAY_BUFFER, previousVbo);
    }

    /** Dessine le carré avec le programme et les uniformes déjà en place. */
    public void draw() {
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }

    @Override
    public void close() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
