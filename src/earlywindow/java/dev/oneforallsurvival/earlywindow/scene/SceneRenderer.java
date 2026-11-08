package dev.oneforallsurvival.earlywindow.scene;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.effects.EffectChain;
import dev.oneforallsurvival.earlywindow.scene.effects.EmberEffect;
import dev.oneforallsurvival.earlywindow.scene.effects.FireEffect;
import dev.oneforallsurvival.earlywindow.scene.effects.HaloEffect;
import dev.oneforallsurvival.earlywindow.scene.effects.SceneEffect;
import dev.oneforallsurvival.earlywindow.scene.effects.SmokeEffect;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Rendu OpenGL 3.2 (GLSL 150) de la scène de chargement, sans aucune classe de Minecraft : port de
 * {@code render(t)} de {@code art/loading-screen/tools/compose.py}, couche par couche, dans le même ordre.
 *
 * <p>Tout est calculé dans l'image de référence de compose.py (1920x1080, y vers le bas), puis recadré au centre
 * pour couvrir la fenêtre sans bandes, quel que soit son ratio. La scène est composée dans un tampon flottant
 * (RGBA16F) : comme les tableaux de numpy, les valeurs peuvent dépasser 1 entre deux couches et ne sont écrêtées
 * qu'à la dernière passe.
 *
 * <p>Horloges ({@link SceneState}) : {@code storyTime} pilote l'ambiance (par {@link StoryClock#storyProgress},
 * jamais la vraie progression), le zoom et l'ouverture depuis le noir ; {@code realTime} anime la mer, les
 * nuages, les bancs, la dérive de la caméra et le scintillement, pour que l'aube tenue reste vivante.
 *
 * <p>Dessine ce qui est prêt dans le {@link TextureStore} : rien (noir) avant le ciel, puis le décor, la mer et
 * les effets à mesure qu'ils arrivent.
 *
 * <p>Feux, fumées, braises et halos sont des {@link SceneEffect} facultatifs ({@link EffectChain}) : un effet dont
 * le shader est refusé ou dont le rendu lève est retiré seul, journalisé une fois, et la scène continue.
 */
public final class SceneRenderer implements AutoCloseable {
    // Palettes de compose.py : nuit, pré-aube, lever.
    private static final float[][] SKY_BANK = Ambiance.palette("#39436a", "#9c6682", "#ffc8a4");
    private static final float[][] STORM_BANK_1 = Ambiance.palette("#0e1224", "#3d3a58", "#7d6a78");
    private static final float[][] STORM_BANK_0 = Ambiance.palette("#090c1a", "#2c2a44", "#5f5462");
    private static final float[][] CLOUD_UNDER = Ambiance.palette("#10162b", "#7a5678", "#f2b48e");
    private static final float[][] CLOUD_WALL = Ambiance.palette("#242d4c", "#dc8d86", "#fff0d6");
    private static final float[][] SEA_FAR = Ambiance.palette("#0d1a38", "#a26677", "#f8c186");
    private static final float[][] SEA_NEAR = Ambiance.palette("#040a1c", "#2a3760", "#2e6d94");
    private static final float[][] SEA_SHALLOW = Ambiance.palette("#0d3748", "#4d7f8e", "#49c0bb");
    private static final float[][] SEA_FOAM = Ambiance.palette("#7a90b8", "#f0c2bd", "#fff3e0");
    private static final float[] SUN_HALO = Ambiance.rgb("#ffb04f");
    private static final float[] SUN_DISC = Ambiance.rgb("#fff4cf");
    private static final float[] MOON_HALO = Ambiance.rgb("#a9c2f2");
    private static final float[] MOON_RAYS = Ambiance.rgb("#9db6e6");
    private static final float[] WHITE = {1f, 1f, 1f};

    // Caméra des nuages : Camera(PLATE_W // 2, PLATE_H // 2) de common.py (seules les directions comptent).
    private static final double PITCH = Math.toRadians(-4.6);
    private static final double ROLL = Math.toRadians(-9.0);
    private static final double HALF_FOV = Math.toRadians(37.5);

    private final SceneManifest manifest;
    private final TextureStore textures;
    private final EffectChain effects;
    // Créé au premier rendu : le constructeur ne fait aucun appel GL direct (testable sans contexte).
    private GlQuad quad;
    private final Program layer;
    private final Program sprite;
    private final Program bank;
    private final Program clouds;
    private final Program sea;
    private final Program multiply;
    private final Program present;

    private final float[] camFwd;
    private final float[] camRight;
    private final float[] camUp;
    private final float camFocal;
    private final int cloudRows;

    private int sceneFbo;
    private int sceneTex;
    private int sceneW;
    private int sceneH;
    private long frames;

    public SceneRenderer(SceneManifest manifest, TextureStore textures) {
        this(manifest, textures, new GlShaderLoader());
    }

    /** @throws ShaderLoader.ShaderException si un shader de base est refusé (l'appelant retombe sur Forge) */
    public SceneRenderer(SceneManifest manifest, TextureStore textures, ShaderLoader shaders) {
        this(manifest, textures, shaders, line -> System.err.println("[lostisland] " + line));
    }

    /**
     * @param log reçoit une ligne par effet retiré (shader refusé, rendu en échec)
     * @throws ShaderLoader.ShaderException si un shader de base est refusé (l'appelant retombe sur Forge) ; un
     *                                      shader d'effet refusé ne retire que cet effet
     */
    public SceneRenderer(SceneManifest manifest, TextureStore textures, ShaderLoader shaders, Consumer<String> log) {
        this.manifest = manifest;
        this.textures = textures;
        Map<String, Integer> loaded = new HashMap<>();
        try {
            for (String f : new String[] {"layer", "sprite", "bank", "clouds", "sea", "multiply", "present"}) {
                loaded.put(f, shaders.load("quad", f));
            }
        } catch (RuntimeException e) {
            loaded.values().forEach(id -> glDeleteProgram(id));
            throw e;
        }
        layer = new Program(loaded.get("layer"));
        sprite = new Program(loaded.get("sprite"));
        bank = new Program(loaded.get("bank"));
        clouds = new Program(loaded.get("clouds"));
        sea = new Program(loaded.get("sea"));
        multiply = new Program(loaded.get("multiply"));
        present = new Program(loaded.get("present"));
        // Ordre de dessin de compose.py dans la passe MID : halos, fumées, flammes, braises.
        effects = new EffectChain(List.of(new HaloEffect(manifest), new SmokeEffect(manifest),
                new FireEffect(manifest), new EmberEffect(manifest)), log);
        effects.init(textures, shaders);

        // Caméra relevée de la bannière : tangage -4,6°, roulis -9°, lacet 0.
        double[] fwd = {0, Math.sin(PITCH), Math.cos(PITCH)};
        double[] right0 = {1, 0, 0};
        double[] up0 = {0, Math.cos(PITCH), -Math.sin(PITCH)}; // cross(forward, right0)
        double[] right = new double[3];
        double[] up = new double[3];
        for (int i = 0; i < 3; i++) {
            right[i] = right0[i] * Math.cos(ROLL) + up0[i] * Math.sin(ROLL);
            up[i] = up0[i] * Math.cos(ROLL) - right0[i] * Math.sin(ROLL);
        }
        int camW = manifest.plateW() / 2;
        int camH = manifest.plateH() / 2;
        double focal = (manifest.outW() / 2.0) / Math.tan(HALF_FOV) * ((double) camW / manifest.plateW());
        camFwd = floats(fwd);
        camRight = floats(right);
        camUp = floats(up);
        camFocal = (float) focal;
        cloudRows = cloudRows(fwd, right, up, focal, camW, camH);
    }

    /** {@code A.cloud_rows} : dernière ligne de la demi-plaque où un rayon monte au-dessus de 0,01, plus un. */
    private static int cloudRows(double[] f, double[] r, double[] u, double focal, int w, int h) {
        int last = -1;
        for (int y = 0; y < h; y++) {
            double dy = (h / 2.0 - (y + 0.5)) / focal;
            for (int x = 0; x < w; x++) {
                double dx = (x + 0.5 - w / 2.0) / focal;
                double vx = f[0] + dx * r[0] + dy * u[0];
                double vy = f[1] + dx * r[1] + dy * u[1];
                double vz = f[2] + dx * r[2] + dy * u[2];
                if (vy / Math.sqrt(vx * vx + vy * vy + vz * vz) > 0.01) {
                    last = y;
                    break;
                }
            }
        }
        return last + 1;
    }

    /** Noms des effets encore actifs (ceux qui ont échoué en sont retirés), dans l'ordre de dessin. */
    public List<String> activeEffects() {
        return effects.active();
    }

    /**
     * Dessine une image de la scène dans le framebuffer lié au moment de l'appel (fenêtre ou FBO de capture),
     * sur toute sa surface {@code fbWidth} x {@code fbHeight}. Thread GL.
     */
    public void render(int fbWidth, int fbHeight, SceneState state) {
        if (fbWidth <= 0 || fbHeight <= 0) {
            return;
        }
        if (quad == null) {
            quad = new GlQuad();
        }
        int target = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        ensureSceneTarget(fbWidth, fbHeight);
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
        glViewport(0, 0, fbWidth, fbHeight);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND);
        blendOver();

        // Recadrage centré : la plus petite échelle qui couvre la fenêtre.
        float scale = Math.max((float) fbWidth / manifest.outW(), (float) fbHeight / manifest.outH());
        float[] refToNdc = {2f * scale / fbWidth, 2f * scale / fbHeight};
        for (Program p : new Program[] {layer, sprite, bank, clouds, sea, multiply, present}) {
            glUseProgram(p.id);
            glUniform2f(p.loc("uRefCenter"), manifest.outW() / 2f, manifest.outH() / 2f);
            glUniform2f(p.loc("uRefToNdc"), refToNdc[0], refToNdc[1]);
            glUniform2f(p.loc("uPlate"), manifest.plateW(), manifest.plateH());
            glUniform2f(p.loc("uOut"), manifest.outW(), manifest.outH());
        }

        float t = state.storyTime();
        double real = state.realTime();
        Ambiance a = Ambiance.at(t, real);
        boolean sky = textures.ready(TextureStore.SKY);
        boolean fx = textures.ready(TextureStore.FX);
        // Effets : seulement une fois leurs images arrivées (groupe fx), aux emplacements de compose.py.
        boolean withEffects = fx && textures.ready(TextureStore.PLATES);

        if (sky) {
            drawSky(a);
            drawStars(a, real);
            if (fx) {
                drawSun(a);
            }
            // Bancs doux éclairés par la lune : le gros des nuages de la bannière.
            drawBank(0, real, 14, 0.62 - 0.36 * Ambiance.sstep(0.7, 1.7, a.day()), SKY_BANK, a.day());
            drawClouds(a, real);
            // La lune passe devant les nuages voxel.
            drawMoon(a, fx);
            if (a.storm() > 0.01f) {
                drawBank(1, real, 44, 0.97 * a.storm(), STORM_BANK_1, a.day());
                drawBank(0, real, 80, 0.8 * a.storm(), STORM_BANK_0, a.day());
            }
            // Tâche 5 : éclairs (draw_bolt) ici.
            if (fx && a.moon() > 0.01f) {
                drawMoonRays(a, real);
            }
        }
        if (textures.ready(TextureStore.SEA)) {
            drawSea(a, real);
        }
        if (textures.ready(TextureStore.PLATES)) {
            drawPlate("far", a.far(), a.day(), 1f);
            if (withEffects) {
                drawEffects(SceneEffect.Pass.BEHIND_MID, state, a, refToNdc);
            }
            // Avant l'impact, le décor n'a pas encore d'épave.
            if (t < StoryClock.IMPACT) {
                drawLayer(textures.texture("plates/precrash_mid_night.png"), 0, 0f, a.mid(), full(), WHITE, false, 1f);
            } else {
                drawPlate("mid", a.mid(), a.day(), 1f);
            }
            if (withEffects) {
                drawEffects(SceneEffect.Pass.MID_GLOW, state, a, refToNdc);
                drawEffects(SceneEffect.Pass.MID, state, a, refToNdc);
            }
        }
        // Pendant la tempête, le monde est plus sombre.
        drawMultiply(1 - 0.34f * a.storm(), 0f, 0f);
        // Tâche 5 : l'avion qui tombe (draw_falling_plane) ici.
        if (textures.ready(TextureStore.PLATES)) {
            drawPlate("fg", a.fg(), a.day(), 1 - 0.28f * a.storm());
            if (withEffects) {
                drawEffects(SceneEffect.Pass.FRONT, state, a, refToNdc);
            }
        }
        // Tâche 5 : pluie, flash des éclairs et de l'impact ici. Tâche 9 : bloom (non porté).

        // Étalonnage : vignette, puis assombrissement du bas pour le texte une fois la barre arrivée.
        float bottom = t >= StoryClock.BAR_IN
                ? (float) Ambiance.sstep(StoryClock.BAR_IN, StoryClock.BAR_IN + 0.7, t) : 0f;
        drawMultiply(1f, 1f, bottom);
        // Tâche 6 : logo, barre et texte ici (après l'étalonnage, avant l'ouverture depuis le noir).

        // Vers la fenêtre : ouverture depuis le noir, grain, écrêtage.
        glBindFramebuffer(GL_FRAMEBUFFER, target);
        glViewport(0, 0, fbWidth, fbHeight);
        glDisable(GL_BLEND);
        glUseProgram(present.id);
        rect(present, 0, 0, manifest.outW(), manifest.outH());
        bindTexture(0, GL_TEXTURE_2D, sceneTex);
        glUniform1i(present.loc("uScene"), 0);
        glUniform1f(present.loc("uFade"), (float) Ambiance.sstep(0.0, 1.2, t));
        glUniform1f(present.loc("uSeed"), (frames++ % 512) * 7.31f);
        quad.draw();
        glBindVertexArray(0);
        glUseProgram(0);
    }

    // --- Couches ---------------------------------------------------------------------------------------

    private void drawEffects(SceneEffect.Pass pass, SceneState state, Ambiance a, float[] refToNdc) {
        effects.render(state, new SceneEffect.View(pass, a, refToNdc, quad));
        blendOver(); // un effet retiré en cours de tracé ne laisse pas un autre fondu derrière lui
    }

    private void drawSky(Ambiance a) {
        // lerp3(A.sky, day) puis view_sky.sample : les deux opérations sont linéaires, on mêle après lecture.
        String[] names = {"sky/sky_night.png", "sky/sky_pre.png", "sky/sky_sun.png"};
        int[] pair = pair(a.day());
        float f = fraction(a.day());
        glDisable(GL_BLEND);
        drawLayer(textures.texture(names[pair[0]]), textures.texture(names[pair[1]]), f, a.sky(), half(),
                WHITE, false, 1f);
        glEnable(GL_BLEND);
    }

    private void drawStars(Ambiance a, double real) {
        if (a.stars() <= 0.01f) {
            return;
        }
        double twinkle = 0.5 + 0.5 * Math.sin(real * 2.3);
        float g0 = (float) (a.stars() * twinkle);
        float g1 = (float) (a.stars() * (1 - twinkle * 0.6));
        drawLayer(textures.texture("sky/stars_a.png"), 0, 0f, a.starView(), full(), new float[] {g0, g0, g0}, false, 0f);
        drawLayer(textures.texture("sky/stars_b.png"), 0, 0f, a.starView(), full(), new float[] {g1, g1, g1}, false, 0f);
    }

    private void drawSun(Ambiance a) {
        double sunUp = Ambiance.sstep(0.8, 2.0, a.day());
        if (sunUp <= 0.01) {
            return;
        }
        double sx = (manifest.sunUv()[0] - 0.5) * manifest.outW() + manifest.plateW() / 2.0;
        double targetY = (manifest.sunUv()[1] - 0.5) * manifest.outH() + manifest.plateH() / 2.0 + 44;
        double sy = targetY + (1 - sunUp) * 150;
        float[] p = a.astre().point(manifest, sx, sy);
        drawSprite(textures.texture("fx/halo.png"), p[0] - 390, p[1] - 390, 780, 780, SUN_HALO,
                (float) (0.72 * sunUp), true);
        drawSprite(textures.texture("sky/sun.png"), p[0] - 96, p[1] - 96, 192, 192, SUN_DISC,
                (float) (1.3 * sunUp), true);
    }

    private void drawMoon(Ambiance a, boolean fx) {
        if (a.moon() <= 0.01f) {
            return;
        }
        float[] p = a.astre().point(manifest,
                (manifest.moonUv()[0] - 0.5) * manifest.outW() + manifest.plateW() / 2.0,
                (manifest.moonUv()[1] - 0.5) * manifest.outH() + manifest.plateH() / 2.0);
        if (fx) {
            drawSprite(textures.texture("fx/halo.png"), p[0] - 380, p[1] - 380, 760, 760, MOON_HALO,
                    0.62f * a.moon(), true);
        }
        drawSprite(textures.texture("sky/moon.png"), p[0] - 108, p[1] - 108, 216, 216, WHITE, a.moon(), false);
    }

    private void drawMoonRays(Ambiance a, double real) {
        float k = (float) (0.36 * a.moon() * (0.8 + 0.2 * Math.sin(real * 0.7)));
        drawLayer(textures.texture("fx/moon_rays.png"), 0, 0f, a.sky(), half(),
                new float[] {MOON_RAYS[0] * k, MOON_RAYS[1] * k, MOON_RAYS[2] * k}, true, 0f);
    }

    private void drawBank(int index, double real, double speed, double opacity, float[][] palette, double day) {
        int tex = textures.texture("sky/cloudbank_" + index + ".png");
        int width = textureWidth(tex);
        int rows = Math.min(manifest.outH(), (int) (manifest.outH() * 0.56));
        glUseProgram(bank.id);
        rect(bank, 0, 0, manifest.outW(), rows);
        bindTexture(0, GL_TEXTURE_2D, tex);
        glUniform1i(bank.loc("uTex"), 0);
        glUniform1i(bank.loc("uShift"), (int) Math.floorMod((long) (real * speed), (long) width));
        glUniform1i(bank.loc("uRows"), rows);
        glUniform3fv(bank.loc("uColor"), Ambiance.lerp3(palette, day));
        glUniform1f(bank.loc("uOpacity"), (float) opacity);
        quad.draw();
    }

    private void drawClouds(Ambiance a, double real) {
        int tex = textures.texture(TextureStore.CLOUDS);
        if (tex == 0) {
            return; // texture vanilla introuvable : pas de nuages voxel, le reste du ciel tient
        }
        Ambiance.View v = a.sky();
        glUseProgram(clouds.id);
        rect(clouds, 0, 0, manifest.outW(), manifest.outH());
        bindTexture(0, GL_TEXTURE_2D, tex);
        glUniform1i(clouds.loc("uClouds"), 0);
        glUniform3fv(clouds.loc("uFwd"), camFwd);
        glUniform3fv(clouds.loc("uRight"), camRight);
        glUniform3fv(clouds.loc("uUp"), camUp);
        glUniform1f(clouds.loc("uFocal"), camFocal);
        glUniform2f(clouds.loc("uCam"), manifest.plateW() / 2f, manifest.plateH() / 2f);
        glUniform1i(clouds.loc("uRows"), cloudRows);
        glUniform1f(clouds.loc("uDrift"), (float) (real * 1.3));
        glUniform3fv(clouds.loc("uUnder"), Ambiance.lerp3(CLOUD_UNDER, a.day()));
        glUniform3fv(clouds.loc("uWall"), Ambiance.lerp3(CLOUD_WALL, a.day()));
        double nightOpacity = 0.22 + 0.63 * Ambiance.sstep(0.7, 1.7, a.day());
        glUniform1f(clouds.loc("uOpacity"), (float) (nightOpacity + 0.3 * a.storm()));
        glUniform3f(clouds.loc("uView"), v.zoom(), v.offX(), v.offY());
        quad.draw();
    }

    private void drawSea(Ambiance a, double real) {
        SceneManifest.Sea m = manifest.sea();
        double pos = real * 16.0;
        long whole = (long) Math.floor(pos);
        int i0 = (int) Math.floorMod(whole, (long) m.frames());
        int i1 = (i0 + 1) % m.frames();
        double day = a.day();
        Ambiance.View v = a.sea();
        glUseProgram(sea.id);
        rect(sea, 0, 0, manifest.outW(), manifest.outH());
        bindTexture(0, GL_TEXTURE_2D_ARRAY, textures.texture(TextureStore.SEA_FRAMES));
        glUniform1i(sea.loc("uFrames"), 0);
        String[] maps = {"depth", "shallow", "mask", "glint_moon", "glint_sun"};
        String[] uniforms = {"uDepth", "uShallow", "uMask", "uGlintMoon", "uGlintSun"};
        for (int i = 0; i < maps.length; i++) {
            bindTexture(1 + i, GL_TEXTURE_2D, textures.texture("sea/" + maps[i] + ".png"));
            glUniform1i(sea.loc(uniforms[i]), 1 + i);
        }
        glUniform1f(sea.loc("uLayer0"), i0);
        glUniform1f(sea.loc("uLayer1"), i1);
        glUniform1f(sea.loc("uFrameMix"), (float) (pos - whole));
        glUniform3fv(sea.loc("uFar"), Ambiance.lerp3(SEA_FAR, day));
        glUniform3fv(sea.loc("uNear"), Ambiance.lerp3(SEA_NEAR, day));
        glUniform3fv(sea.loc("uShallowColor"), Ambiance.lerp3(SEA_SHALLOW, day));
        glUniform3fv(sea.loc("uFoam"), Ambiance.lerp3(SEA_FOAM, day));
        glUniform1f(sea.loc("uMoonPower"), a.moon() * 0.75f);
        glUniform1f(sea.loc("uSunPower"),
                (float) (Ambiance.sstep(1.0, 2.0, day) * 1.2 + Ambiance.sstep(0.6, 1.2, day) * 0.25));
        glUniform1f(sea.loc("uHorizon"), m.horizonRow());
        glUniform2f(sea.loc("uSize"), manifest.plateW() / 2f, manifest.plateH() / 2f);
        glUniform3f(sea.loc("uView"), v.zoom(), v.offX(), v.offY());
        quad.draw();
        glActiveTexture(GL_TEXTURE0);
    }

    /** {@code layer_rgba(layer, day)} : plaque prémultipliée, mêlée entre deux ambiances ; {@code gain} du fg. */
    private void drawPlate(String name, Ambiance.View view, float day, float gain) {
        String[] k = {"night", "pre", "sun"};
        int[] pair = pair(day);
        float f = fraction(day);
        int a = textures.texture("plates/" + name + "_" + k[pair[0]] + ".png");
        int b = f < 1e-3f ? 0 : textures.texture("plates/" + name + "_" + k[pair[1]] + ".png");
        drawLayer(a, b, b == 0 ? 0f : f, view, full(), new float[] {gain, gain, gain}, false, 1f);
    }

    private void drawMultiply(float factor, float vignette, float bottom) {
        if (factor >= 1f && vignette <= 0f && bottom <= 0f) {
            return;
        }
        glBlendFuncSeparate(GL_ZERO, GL_SRC_COLOR, GL_ZERO, GL_ONE);
        glUseProgram(multiply.id);
        rect(multiply, 0, 0, manifest.outW(), manifest.outH());
        glUniform1f(multiply.loc("uFactor"), factor);
        glUniform1f(multiply.loc("uVignette"), vignette);
        glUniform1f(multiply.loc("uBottom"), bottom);
        quad.draw();
        blendOver();
    }

    // --- Outils ----------------------------------------------------------------------------------------

    /** Taille de la texture lue par une vue : {largeur, hauteur, échelle}. */
    private float[] full() {
        return new float[] {manifest.plateW(), manifest.plateH(), 1f};
    }

    private float[] half() {
        return new float[] {manifest.plateW() / 2f, manifest.plateH() / 2f, 2f};
    }

    private void drawLayer(int texA, int texB, float mix, Ambiance.View view, float[] src, float[] tint,
                           boolean fromAlpha, float alphaScale) {
        glUseProgram(layer.id);
        rect(layer, 0, 0, manifest.outW(), manifest.outH());
        bindTexture(0, GL_TEXTURE_2D, texA);
        bindTexture(1, GL_TEXTURE_2D, texB == 0 ? texA : texB);
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(layer.loc("uTexA"), 0);
        glUniform1i(layer.loc("uTexB"), 1);
        glUniform1f(layer.loc("uMix"), texB == 0 ? 0f : mix);
        glUniform3f(layer.loc("uView"), view.zoom(), view.offX(), view.offY());
        glUniform3f(layer.loc("uSrc"), src[0], src[1], src[2]);
        glUniform3fv(layer.loc("uTint"), tint);
        glUniform1i(layer.loc("uFromAlpha"), fromAlpha ? 1 : 0);
        glUniform1f(layer.loc("uAlphaScale"), alphaScale);
        quad.draw();
    }

    private void drawSprite(int tex, float x, float y, float w, float h, float[] tint, float alpha, boolean add) {
        glUseProgram(sprite.id);
        rect(sprite, x, y, w, h);
        bindTexture(0, GL_TEXTURE_2D, tex);
        glUniform1i(sprite.loc("uTex"), 0);
        glUniform3fv(sprite.loc("uTint"), tint);
        glUniform1f(sprite.loc("uAlpha"), alpha);
        glUniform1i(sprite.loc("uFromAlpha"), add ? 1 : 0);
        glUniform1f(sprite.loc("uAlphaScale"), add ? 0f : 1f);
        quad.draw();
    }

    private static void rect(Program p, float x, float y, float w, float h) {
        glUniform4f(p.loc("uRect"), x, y, w, h);
    }

    private static void bindTexture(int unit, int target, int tex) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(target, tex);
    }

    private static void blendOver() {
        // Tout est prémultiplié : recouvrir et ajouter (alpha 0) partagent le même fondu.
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Indices des deux ambiances encadrant {@code day}, comme lerp3. */
    private static int[] pair(float day) {
        float k = Math.min(2f, Math.max(0f, day));
        int i = Math.min(1, (int) k);
        return new int[] {i, i + 1};
    }

    private static float fraction(float day) {
        float k = Math.min(2f, Math.max(0f, day));
        return k - Math.min(1, (int) k);
    }

    private int textureWidth(int tex) {
        glBindTexture(GL_TEXTURE_2D, tex);
        return glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
    }

    private void ensureSceneTarget(int w, int h) {
        if (sceneFbo != 0 && w == sceneW && h == sceneH) {
            return;
        }
        deleteSceneTarget();
        sceneTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, sceneTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_HALF_FLOAT, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        sceneFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneTex, 0);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("tampon de la scène incomplet : 0x" + Integer.toHexString(status));
        }
        sceneW = w;
        sceneH = h;
    }

    private void deleteSceneTarget() {
        if (sceneFbo != 0) {
            glDeleteFramebuffers(sceneFbo);
            glDeleteTextures(sceneTex);
            sceneFbo = 0;
            sceneTex = 0;
        }
    }

    private static float[] floats(double[] v) {
        return new float[] {(float) v[0], (float) v[1], (float) v[2]};
    }

    @Override
    public void close() {
        deleteSceneTarget();
        effects.close();
        if (quad != null) {
            quad.close();
            quad = null;
        }
        for (Program p : new Program[] {layer, sprite, bank, clouds, sea, multiply, present}) {
            glDeleteProgram(p.id);
        }
    }

    /** Programme GL et ses emplacements d'uniformes, mis en cache. */
    private static final class Program {
        final int id;
        private final Map<String, Integer> locations = new HashMap<>();

        Program(int id) {
            this.id = id;
        }

        int loc(String name) {
            return locations.computeIfAbsent(name, n -> glGetUniformLocation(id, n));
        }
    }
}
