package dev.oneforallsurvival.earlywindow.scene.effects;

import static org.lwjgl.opengl.GL32C.*;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneState;
import dev.oneforallsurvival.earlywindow.scene.ShaderLoader;
import dev.oneforallsurvival.earlywindow.scene.StoryClock;
import dev.oneforallsurvival.earlywindow.scene.TextureStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La chute de l'avion : port de {@code draw_falling_plane} (avec {@code draw_crash_trail},
 * {@code draw_crash_particles}, {@code draw_puff} et {@code draw_glow}) et du flash de l'impact de
 * {@code render(t)}. Les positions viennent de la trajectoire 3D ({@link CrashTrack}), projetées avec la caméra
 * du décor ; les images de l'avion ({@code fx/crash/plane_NNN.png}) ont été rendues sous le même angle.
 *
 * <p>Tout suit le récit ({@code storyTime}) : l'avion n'existe qu'entre le début de la trajectoire et l'impact ;
 * après, seule reste la traînée de fumée (jusqu'à 4 s après l'impact) et le flash de l'impact qui retombe, comme
 * dans compose.py. Rien n'est tiré au hasard : chaque bouffée et chaque éclat se déduisent de leur numéro.
 */
public final class CrashEffect implements SceneEffect {
    private static final float[] ORANGE = Ambiance.rgb("#ff8d32");
    private static final float[] RED = Ambiance.rgb("#d8401a");
    private static final float[] CORE = Ambiance.rgb("#ffe2a0");
    private static final float[] TRAIL_DARK = Ambiance.rgb("#5a5c6e");
    private static final float[] TRAIL_HOT = Ambiance.rgb("#c9662a");
    private static final float[] CREST_SMOKE = Ambiance.rgb("#2c2a2e");
    private static final float[] DUST_DARK = Ambiance.rgb("#3e3b42");
    private static final float[] DUST_LIT = Ambiance.rgb("#8a6548");
    private static final float[][] LEAVES = {Ambiance.rgb("#2f6b2b"), Ambiance.rgb("#3f8234"),
            Ambiance.rgb("#6c4a26"), Ambiance.rgb("#d7dbe2"), Ambiance.rgb("#ffb050")};
    private static final float[][] SPARKS = {Ambiance.rgb("#ffd27a"), Ambiance.rgb("#ff9a3a"),
            Ambiance.rgb("#fff1c6")};
    private static final float[] IMPACT_HALO = Ambiance.rgb("#ffcf8a");
    private static final float[] IMPACT_WASH = Ambiance.rgb("#ffe6c2");
    private static final float[] WHITE = {1f, 1f, 1f};

    /** Ce que dessine l'avion : lueur ajoutée, bouffée de fumée, image de l'avion, éclat carré recouvrant. */
    public enum Kind { GLOW, PUFF, PLANE, SQUARE }

    /**
     * Un tracé, dans l'ordre de compose.py. {@code x, y} : coin haut-gauche tronqué comme {@code clip_rect}, en
     * pixels de l'image de référence ; {@code size} : côté (lueur, bouffée, éclat), 0 pour l'avion (taille de son
     * image) ; {@code sprite} : image de fumée (0..5) ou image de l'avion.
     */
    public record Draw(Kind kind, int sprite, float x, float y, int size, float[] color, float alpha) {
    }

    private final SceneManifest manifest;
    private final CrashTrack track;
    private TextureStore textures;
    private EffectProgram halo;
    private EffectProgram smoke;
    private final Map<Integer, int[]> planeSizes = new HashMap<>();

    /** @param track trajectoire lue, ou {@code null} si elle manque (l'effet se retire à l'initialisation) */
    public CrashEffect(SceneManifest manifest, CrashTrack track) {
        this.manifest = manifest;
        this.track = track;
    }

    @Override
    public String name() {
        return "crash";
    }

    @Override
    public void init(TextureStore textures, ShaderLoader shaders) throws EffectUnavailable {
        if (track == null) {
            throw new EffectUnavailable("trajectoire " + CrashTrack.TRACK + " absente ou illisible", null);
        }
        this.textures = textures;
        halo = EffectProgram.load(shaders, name(), "halo");
        smoke = EffectProgram.load(shaders, name(), "smoke");
    }

    // --- Port pur de compose.py -----------------------------------------------------------------------

    /** Les tracés de {@code draw_falling_plane} à l'instant {@code t} du récit, caméra {@code a.mid()}. */
    public static List<Draw> draws(SceneManifest m, CrashTrack c, Ambiance a, double t) {
        List<Draw> out = new ArrayList<>();
        Ambiance.View view = a.mid();
        trail(out, m, c, view, t);
        if (!(c.start() <= t && t < StoryClock.IMPACT + 0.02)) {
            return out;
        }
        int i = c.index(t);
        // Flammes derrière le réacteur en feu, puis derrière l'aile arrachée.
        int torn = c.index(c.crestT());
        double flicker = 0.8 + 0.2 * Math.sin(t * 47) * Math.sin(t * 31);
        for (int source = 0; source < 2; source++) {
            int first = source == 0 ? 0 : torn;
            if (i < first) {
                continue;
            }
            for (int k = 0; k < 16; k++) {
                int j = i - k;
                if (j < first) {
                    break;
                }
                double[] s = screen(m, view, source == 0 ? c.engine(j) : c.tear(j));
                double fall = 1 - k / 16.0;
                glow(out, (5 + 7 * fall) * s[2], s[0], s[1], mix(ORANGE, RED, 1 - fall), 0.5 * fall * flicker);
            }
        }

        // Crête : les arbres fauchés, des éclats, et une lueur qui reste accrochée au relief.
        double age = t - c.crestT();
        if (age >= 0) {
            double[] s = screen(m, view, c.crest());
            glow(out, (44 * Math.exp(-age * 3.5) + 24) * s[2], s[0], s[1], ORANGE, 0.55 * Math.exp(-age * 3.5) + 0.34);
            double[] velocity = scale(c.forward(c.index(c.crestT())), 110);
            particles(out, m, c, view, t, c.crest(), c.crestT(), 90, 1.0, velocity, 95, 34, 1.5, LEAVES, 0.7);
            for (int k = 0; k < 14; k++) {
                double local = age - k * 0.012;
                if (local >= 0 && local < 1.1) {
                    double h = EffectMath.hash01(k, 5.0);
                    puff(out, k, (10 + local * 34) * s[2], s[0] + (h - 0.5) * 22 * s[2], s[1] - local * 12 * s[2],
                            0.48 * (1 - local / 1.1), CREST_SMOKE);
                }
            }
        }

        // Glissade : poussière soulevée, gerbes d'étincelles sous le ventre, lueur au sol.
        int touch = c.index(c.touchT());
        if (i >= touch) {
            double[] s = screen(m, view, add(c.pos(i), scale(c.forward(i), 26), new double[] {0, -6, 0}));
            glow(out, 62 * s[2], s[0], s[1], ORANGE, 0.5);
            float[] dust = mix(DUST_DARK, DUST_LIT, 0.55);
            for (int j = touch; j <= i; j += 2) {
                double local = t - c.born(j);
                double h1 = EffectMath.hash01(j, 8.0);
                double h2 = EffectMath.hash01(j, 9.0);
                for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
                    double[] point = add(c.pos(j), scale(c.forward(j), -(6 + 40 * h1)),
                            scale(c.side(j), sideSign * (4 + 7 * h2)), new double[] {0, -3 + local * 7, 0});
                    double[] p = screen(m, view, point);
                    puff(out, j + sideSign, (9 + local * 30) * p[2], p[0], p[1], 0.72 * (1 - local * 0.45), dust);
                }
            }
        }

        int[] offset = c.offset(i);
        if (offset != null) {
            float[] p = view.point(m, offset[0], offset[1]);
            // L'air de la tempête voile l'avion tant qu'il est loin.
            out.add(new Draw(Kind.PLANE, i, HaloEffect.trunc(p[0]), HaloEffect.trunc(p[1]), 0, WHITE,
                    (float) (0.62 + 0.38 * Ambiance.sstep(0.7, 1.9, t))));
        }

        // Coeur des flammes, devant la carlingue : le réacteur reste la source la plus vive.
        double[] e = screen(m, view, c.engine(i));
        glow(out, 13 * e[2], e[0], e[1], ORANGE, 0.8 * flicker);
        glow(out, 5 * e[2], e[0], e[1], CORE, 0.95 * flicker);
        if (i >= torn) {
            double[] r = screen(m, view, c.tear(i));
            glow(out, 10 * r[2], r[0], r[1], ORANGE, 0.7 * flicker);
        }

        if (i >= touch) {
            int n = 0;
            for (int base = touch; base <= i; base += 4, n++) {
                double[] origin = add(c.pos(base), scale(c.forward(base), 20 - n * 2), new double[] {0, -4.5, 0});
                double[] speed = scale(c.forward(base), -70);
                particles(out, m, c, view, t, origin, c.born(base), 18, 20.0 + n, speed, 90, 22, 0.45, SPARKS, 0.28);
            }
        }
        return out;
    }

    /** {@code draw_crash_trail} : fumée émise le long de la trajectoire, qui reste où l'avion est passé. */
    private static void trail(List<Draw> out, SceneManifest m, CrashTrack c, Ambiance.View view, double t) {
        if (t < c.start()) {
            return;
        }
        int now = c.index(t);
        double life = 4.2;
        double level = 1 - Ambiance.sstep(StoryClock.IMPACT + 1.0, StoryClock.IMPACT + 4.0, t);
        if (level <= 0) {
            return;
        }
        int tornFrom = c.index(c.crestT());
        for (int j = 0; j <= now; j += 2) {
            double age = t - c.born(j);
            if (!(age >= 0 && age <= life)) {
                continue;
            }
            double u = age / life;
            for (int source = 0; source < 2; source++) {
                if (source == 1 && j < tornFrom) {
                    continue;
                }
                double h1 = EffectMath.hash01(j, source);
                double h2 = EffectMath.hash01(j + 7.1, source + 3.0);
                double spread = 0.9 + age * 2.6;
                double[] point = add(source == 0 ? c.engine(j) : c.tear(j), new double[] {
                        (h1 - 0.5) * spread + age * 5.5 + Math.pow(age, 1.5) * 1.5,
                        (h2 - 0.5) * spread + age * 3.0,
                        (EffectMath.hash01(j + 3.3, source) - 0.5) * spread});
                double[] s = screen(m, view, point);
                double size = (4.5 + age * 7.5) * s[2] * (0.8 + 0.4 * h1);
                double alpha = Ambiance.sstep(0, 0.03, u) * Math.pow(1 - u, 1.25) * 0.8 * level
                        * (source == 1 ? 0.8 : 1.0);
                double heat = 1 - Ambiance.sstep(0.04, 0.42, age);
                puff(out, j, size, s[0], s[1], alpha, mix(TRAIL_DARK, TRAIL_HOT, heat));
            }
        }
    }

    /** {@code draw_crash_particles} : éclats projetés au moment d'un choc, balistique simple. */
    private static void particles(List<Draw> out, SceneManifest m, CrashTrack c, Ambiance.View view, double t,
                                  double[] origin, double born, int count, double seed, double[] speed,
                                  double spread, double gravity, double life, float[][] palette, double sizeBlocks) {
        double age = t - born;
        if (age < 0) {
            return;
        }
        for (int k = 0; k < count; k++) {
            double[] h = new double[6];
            for (int n = 0; n < 6; n++) {
                h[n] = EffectMath.hash01(k + 0.13 * n, seed);
            }
            double local = age * (0.6 + 0.8 * h[4]);
            if (local > life) {
                continue;
            }
            double drag = (1 - Math.exp(-2.2 * local)) / 2.2;
            double[] point = {
                    origin[0] + (speed[0] + (h[0] - 0.5) * spread) * drag,
                    origin[1] + (speed[1] + h[1] * 0.8 * spread) * drag - gravity * local * local,
                    origin[2] + (speed[2] + (h[2] - 0.5) * spread) * drag};
            double[] s = screen(m, view, point);
            int side = Math.max(2, (int) (sizeBlocks * s[2] * (0.5 + h[3])));
            int ix = (int) s[0];
            int iy = (int) s[1];
            if (ix < 0 || iy < 0 || ix + side >= m.outW() || iy + side >= m.outH()) {
                continue;
            }
            out.add(new Draw(Kind.SQUARE, 0, ix, iy, side, palette[k % palette.length], (float) (1 - local / life)));
        }
    }

    /** {@code draw_puff} : bouffée de {@code max(8, min(900, round(size / 6) * 6))} px, centrée. */
    private static void puff(List<Draw> out, int index, double size, double x, double y, double alpha, float[] tint) {
        int side = (int) Math.max(8, Math.min(900, Math.rint(size / 6) * 6));
        out.add(new Draw(Kind.PUFF, Math.floorMod(index, 6), HaloEffect.trunc(x - side / 2.0),
                HaloEffect.trunc(y - side / 2.0), side, tint, (float) alpha));
    }

    /** {@code draw_glow} : halo de {@code max(8, min(1400, round(size / 4) * 4))} px ajouté, centré. */
    private static void glow(List<Draw> out, double size, double x, double y, float[] color, double alpha) {
        int side = (int) Math.max(8, Math.min(1400, Math.rint(size / 4) * 4));
        out.add(new Draw(Kind.GLOW, 0, HaloEffect.trunc(x - side / 2.0), HaloEffect.trunc(y - side / 2.0), side,
                color, (float) alpha));
    }

    /** {@code Crash.to_screen} : {x, y, pixels par bloc} dans l'image de référence. */
    static double[] screen(SceneManifest m, Ambiance.View view, double[] point) {
        double[] p = CrashTrack.project(point, m.plateW(), m.plateH(), m.outW());
        double depth = Math.max(p[2], 3.0);
        float[] s = view.point(m, p[0], p[1]);
        return new double[] {s[0], s[1], CrashTrack.focal(m.outW()) / depth};
    }

    private static double[] add(double[]... vs) {
        double[] r = new double[3];
        for (double[] v : vs) {
            for (int i = 0; i < 3; i++) {
                r[i] += v[i];
            }
        }
        return r;
    }

    private static double[] scale(double[] v, double k) {
        return new double[] {v[0] * k, v[1] * k, v[2] * k};
    }

    /** {@code a * (1 - k) + b * k}. */
    private static float[] mix(float[] a, float[] b, double k) {
        float[] r = new float[3];
        for (int i = 0; i < 3; i++) {
            r[i] = (float) (a[i] * (1 - k) + b[i] * k);
        }
        return r;
    }

    // --- Rendu ----------------------------------------------------------------------------------------

    @Override
    public void render(SceneState state, View view) {
        double t = state.storyTime();
        if (view.pass() == Pass.PLANE) {
            List<Draw> draws = draws(manifest, track, view.ambiance(), t);
            if (!draws.isEmpty()) {
                drawAll(view, draws);
            }
        } else if (view.pass() == Pass.WEATHER) {
            drawImpact(view, EffectMath.impactFlash(t));
        }
    }

    private void drawAll(View view, List<Draw> draws) {
        int haloTex = EffectProgram.texture(textures, "fx/halo.png");
        int[] smokeTex = new int[6];
        for (int i = 0; i < 6; i++) {
            smokeTex[i] = EffectProgram.texture(textures, "fx/smoke_" + i + ".png");
        }
        glActiveTexture(GL_TEXTURE0);
        EffectProgram current = null;
        for (Draw d : draws) {
            EffectProgram wanted = d.kind() == Kind.GLOW || d.kind() == Kind.SQUARE ? halo : smoke;
            if (wanted != current) {
                current = wanted;
                current.use(view, manifest);
                glUniform1i(current.loc("uTex"), 0);
            }
            switch (d.kind()) {
                case GLOW, SQUARE -> {
                    boolean square = d.kind() == Kind.SQUARE;
                    glBindTexture(GL_TEXTURE_2D, haloTex);
                    halo.rect(d.x(), d.y(), d.size(), d.size());
                    glUniform3fv(halo.loc("uColor"), d.color());
                    glUniform1f(halo.loc("uAlpha"), d.alpha());
                    // Lueur : blit_add du halo. Éclat : carré plein recouvrant, frame * (1 - fade) + couleur * fade.
                    glUniform1i(halo.loc("uSolid"), square ? 1 : 0);
                    glUniform1f(halo.loc("uAlphaScale"), square ? 1f : 0f);
                }
                case PUFF -> {
                    glBindTexture(GL_TEXTURE_2D, smokeTex[d.sprite()]);
                    smoke.rect(d.x(), d.y(), d.size(), d.size());
                    glUniform3fv(smoke.loc("uTint"), d.color());
                    glUniform1f(smoke.loc("uAlpha"), d.alpha());
                }
                case PLANE -> {
                    int tex = EffectProgram.texture(textures, CrashTrack.spritePath(d.sprite()));
                    glBindTexture(GL_TEXTURE_2D, tex);
                    int[] wh = planeSizes.computeIfAbsent(tex, id -> new int[] {
                            glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH),
                            glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT)});
                    // blit_over à la taille native de l'image, non teintée.
                    smoke.rect(d.x(), d.y(), wh[0], wh[1]);
                    glUniform3fv(smoke.loc("uTint"), WHITE);
                    glUniform1f(smoke.loc("uAlpha"), d.alpha());
                }
            }
            view.quad().draw();
        }
    }

    /** Impact : grand halo chaud sur l'épave, puis voile clair sur toute l'image, qui retombent ensemble. */
    private void drawImpact(View view, double impact) {
        if (impact < 1e-4) {
            return; // éclat éteint : deux passes épargnées
        }
        float[] wreck = view.ambiance().mid().point(manifest, manifest.wreck()[0], manifest.wreck()[1]);
        int size = 1800;
        halo.use(view, manifest);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, EffectProgram.texture(textures, "fx/halo.png"));
        glUniform1i(halo.loc("uTex"), 0);
        halo.rect(HaloEffect.trunc(wreck[0] - size / 2.0), HaloEffect.trunc(wreck[1] - size / 2.0), size, size);
        glUniform3fv(halo.loc("uColor"), IMPACT_HALO);
        glUniform1f(halo.loc("uAlpha"), (float) (2.4 * impact));
        glUniform1i(halo.loc("uSolid"), 0);
        glUniform1f(halo.loc("uAlphaScale"), 0f);
        view.quad().draw();
        // frame += #ffe6c2 * impact² * 0.9 : carré plein ajouté sur toute l'image.
        halo.rect(0, 0, manifest.outW(), manifest.outH());
        glUniform3fv(halo.loc("uColor"), IMPACT_WASH);
        glUniform1f(halo.loc("uAlpha"), (float) (impact * impact * 0.9));
        glUniform1i(halo.loc("uSolid"), 1);
        view.quad().draw();
    }

    @Override
    public void close() {
        if (halo != null) {
            halo.close();
            halo = null;
        }
        if (smoke != null) {
            smoke.close();
            smoke = null;
        }
        planeSizes.clear();
    }
}
