package dev.oneforallsurvival.earlywindow.scene;

import static org.lwjgl.opengl.GL32C.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Chargeur réel : sources GLSL 150 lues dans les ressources {@code lostisland/shaders/}. */
public final class GlShaderLoader implements ShaderLoader {
    private static final String ROOT = "lostisland/shaders/";

    private final ClassLoader resources;

    public GlShaderLoader() {
        this(GlShaderLoader.class.getClassLoader());
    }

    public GlShaderLoader(ClassLoader resources) {
        this.resources = resources;
    }

    @Override
    public int load(String vertex, String fragment) {
        int vs = compile(GL_VERTEX_SHADER, vertex + ".vert");
        int fs;
        try {
            fs = compile(GL_FRAGMENT_SHADER, fragment + ".frag");
        } catch (RuntimeException e) {
            glDeleteShader(vs);
            throw e;
        }
        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        // GLSL 150 n'a pas de layout(location) : les emplacements sont fixés avant l'édition des liens.
        glBindAttribLocation(program, 0, "aPos");
        glBindFragDataLocation(program, 0, "fragColor");
        glLinkProgram(program);
        glDetachShader(program, vs);
        glDetachShader(program, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new ShaderException("édition des liens de " + vertex + "/" + fragment + " refusée : " + log);
        }
        return program;
    }

    private int compile(int type, String file) {
        String source = read(file);
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new ShaderException("compilation de " + file + " refusée : " + log);
        }
        return shader;
    }

    private String read(String file) {
        try (InputStream in = resources.getResourceAsStream(ROOT + file)) {
            if (in == null) {
                throw new ShaderException("source introuvable : " + ROOT + file);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ShaderException("source illisible : " + ROOT + file + " (" + e.getMessage() + ")");
        }
    }
}
