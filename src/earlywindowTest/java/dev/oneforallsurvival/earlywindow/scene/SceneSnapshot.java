package dev.oneforallsurvival.earlywindow.scene;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32C.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryUtil;

/**
 * Outil de développement (pas un test JUnit) : rend des images fixes de la scène hors du jeu, dans une fenêtre
 * GLFW cachée, et les écrit en PNG pour les comparer aux images de la vidéo de référence.
 *
 * <pre>./gradlew sceneSnapshot --args="--t 3,12,22,33 --p 0.69 --size 1920x1080 --out build/scene-snapshots"</pre>
 *
 * <p>{@code --t} donne le <em>temps réel</em> de chaque image. Le temps du récit s'en déduit par {@link StoryClock},
 * chargement non terminé : il suit le temps réel puis se fige à {@link StoryClock#HOLD} (33,6 s). Jusqu'à 33,6 s,
 * les deux horloges sont donc égales, comme dans la vidéo de compose.py ; au-delà ({@code --t 60,60.1,120}),
 * l'aube est tenue et seuls les effets du temps réel (mer, nuages, feux, fumée, braises) bougent. Toutes les
 * textures sont attendues avant la première image. Les effets retirés (shader refusé) sont listés au début.
 */
public final class SceneSnapshot {
    private SceneSnapshot() {
    }

    public static void main(String[] args) throws Exception {
        List<Float> times = new ArrayList<>(List.of(3f, 12f, 22f, 33f));
        float progress = 0.69f;
        int width = 1920;
        int height = 1080;
        Path out = Path.of("build", "scene-snapshots");
        for (int i = 0; i + 1 < args.length; i += 2) {
            String v = args[i + 1];
            switch (args[i]) {
                case "--t" -> {
                    times.clear();
                    for (String s : v.split(",")) {
                        times.add(Float.parseFloat(s.trim()));
                    }
                }
                case "--p" -> progress = Float.parseFloat(v);
                case "--size" -> {
                    String[] wh = v.toLowerCase(Locale.ROOT).split("x");
                    width = Integer.parseInt(wh[0]);
                    height = Integer.parseInt(wh[1]);
                }
                case "--out" -> out = Path.of(v);
                default -> throw new IllegalArgumentException("option inconnue : " + args[i]);
            }
        }
        Files.createDirectories(out);

        if (!glfwInit()) {
            throw new IllegalStateException("GLFW ne s'initialise pas");
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        long window = glfwCreateWindow(64, 64, "lostisland-snapshot", 0L, 0L);
        if (window == 0L) {
            glfwTerminate();
            throw new IllegalStateException("contexte OpenGL 3.2 core introuvable");
        }
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GL : " + glGetString(GL_VERSION) + " / " + glGetString(GL_RENDERER));

        ClassLoader loader = SceneSnapshot.class.getClassLoader();
        SceneManifest manifest;
        try (InputStream in = loader.getResourceAsStream("lostisland/loading/manifest.json")) {
            if (in == null) {
                throw new IllegalStateException("lostisland/loading/manifest.json absent du classpath");
            }
            manifest = SceneManifest.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        TextureStore textures = new TextureStore(manifest, TextureStore.classpath(loader));
        long begin = System.nanoTime();
        textures.start();
        while (!textures.allReady()) {
            textures.upload();
            if (textures.failure() != null) {
                throw new IllegalStateException("texture illisible", textures.failure());
            }
            if (System.nanoTime() - begin > 180_000_000_000L) {
                throw new IllegalStateException("textures toujours pas prêtes après 180 s");
            }
            Thread.sleep(1);
        }
        System.out.printf(Locale.ROOT, "textures prêtes en %.1f s (nuages vanilla : %s)%n",
                (System.nanoTime() - begin) / 1e9, textures.texture(TextureStore.CLOUDS) != 0 ? "oui" : "absents");

        SceneRenderer renderer = new SceneRenderer(manifest, textures);
        System.out.println("effets actifs : " + renderer.activeEffects());
        int fbo = glGenFramebuffers();
        int color = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, color);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("FBO de capture incomplet");
        }
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 3);
        STBImageWrite.stbi_flip_vertically_on_write(true);
        StatusMessages.Status status = new StatusMessages.Status("Exploration du littoral…", "textures");
        try {
            for (float t : times) {
                float story = new StoryClock().storyTime(t, false);
                glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                renderer.render(width, height, new SceneState(story, progress, status, 1f, t));
                glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                glPixelStorei(GL_PACK_ALIGNMENT, 1);
                glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixels);
                int error = glGetError();
                if (error != GL_NO_ERROR) {
                    throw new IllegalStateException("erreur GL 0x" + Integer.toHexString(error) + " à t=" + t);
                }
                Path file = out.resolve(String.format(Locale.ROOT, "scene_%04.1f_%dx%d.png", t, width, height));
                if (!STBImageWrite.stbi_write_png(file.toString(), width, height, 3, pixels, width * 3)) {
                    throw new IllegalStateException("écriture impossible : " + file);
                }
                System.out.printf(Locale.ROOT, "écrit %s (réel %.2f s, récit %.2f s)%n", file, t, story);
            }
        } finally {
            MemoryUtil.memFree(pixels);
            renderer.close();
            textures.close();
            glDeleteFramebuffers(fbo);
            glDeleteTextures(color);
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
