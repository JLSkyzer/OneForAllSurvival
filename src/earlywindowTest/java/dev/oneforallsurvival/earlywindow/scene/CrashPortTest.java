package dev.oneforallsurvival.earlywindow.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.earlywindow.scene.effects.CrashEffect;
import dev.oneforallsurvival.earlywindow.scene.effects.CrashTrack;
import dev.oneforallsurvival.earlywindow.scene.effects.EffectMath;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Orage et crash contre compose.py. Valeurs attendues relevées en exécutant le vrai {@code draw_falling_plane}
 * (draw_glow, draw_puff et blit_over remplacés par des enregistreurs, image remplacée par un enregistreur
 * d'affectations pour les éclats ; caméra secouée calculée comme {@code render(t)}), et le vrai {@code flash_at},
 * avec les vrais manifeste et trajectoire (script dans le scratchpad de la tâche 5).
 */
class CrashPortTest {
    private static final Path ASSETS = Path.of("art/loading-screen/assets");

    private static SceneManifest manifest() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(ASSETS.resolve(CrashTrack.TRACK)), "trajectoire absente : test ignore");
        try (Reader r = Files.newBufferedReader(ASSETS.resolve("manifest.json"), StandardCharsets.UTF_8)) {
            return SceneManifest.parse(r);
        }
    }

    private static CrashTrack track() {
        return CrashTrack.load(path -> {
            try {
                Path p = ASSETS.resolve(path);
                return Files.isRegularFile(p) ? Files.newInputStream(p) : null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static List<CrashEffect.Draw> draws(SceneManifest m, CrashTrack c, float t) {
        return CrashEffect.draws(m, c, Ambiance.at(t, t, c.crestT(), c.touchT()), t);
    }

    private static Map<CrashEffect.Kind, Integer> kinds(List<CrashEffect.Draw> draws) {
        Map<CrashEffect.Kind, Integer> n = new TreeMap<>();
        for (CrashEffect.Draw d : draws) {
            n.merge(d.kind(), 1, Integer::sum);
        }
        return n;
    }

    /** Un tracé : position à 1 px près (troncature de flottants voisins), taille exacte, couleur et alpha. */
    private static void check(CrashEffect.Draw d, CrashEffect.Kind kind, int sprite, int x, int y, int size,
                              float[] color, double alpha) {
        assertEquals(kind, d.kind());
        assertEquals(sprite, d.sprite());
        assertEquals(x, d.x(), 1f);
        assertEquals(y, d.y(), 1f);
        assertEquals(size, d.size());
        assertArrayEquals(color, d.color(), 1e-4f);
        assertEquals(alpha, d.alpha(), 1e-4);
    }

    @Test
    void laChuteEstCelleDeCompose() throws IOException {
        SceneManifest m = manifest();
        CrashTrack c = track();
        float[] trail = {0.3529411852359772f, 0.3607843220233917f, 0.4313725531101227f};

        List<CrashEffect.Draw> d = draws(m, c, 2f);
        assertEquals(62, d.size());
        assertEquals(Map.of(CrashEffect.Kind.PUFF, 43, CrashEffect.Kind.GLOW, 18, CrashEffect.Kind.PLANE, 1), kinds(d));
        check(d.get(0), CrashEffect.Kind.PUFF, 0, 1299, 11, 12, trail, 0.4819210685919173);
        check(d.get(31), CrashEffect.Kind.PUFF, 2, 1202, 86, 12,
                new float[] {0.3762580454349518f, 0.3628849387168884f, 0.4170883595943451f}, 0.7136727360785418);
        check(d.get(59), CrashEffect.Kind.PLANE, c.index(2f), 1129, 94, 0, new float[] {1, 1, 1}, 1.0);
        check(d.get(61), CrashEffect.Kind.GLOW, 0, 1172, 125, 8,
                new float[] {1.0f, 0.886274516582489f, 0.6274510025978088f}, 0.7944442513597914);

        d = draws(m, c, 3.5f);
        assertEquals(231, d.size());
        assertEquals(Map.of(CrashEffect.Kind.PUFF, 108, CrashEffect.Kind.GLOW, 32, CrashEffect.Kind.SQUARE, 90,
                CrashEffect.Kind.PLANE, 1), kinds(d));
        check(d.get(77), CrashEffect.Kind.PUFF, 4, 1194, 298, 18,
                new float[] {0.4105399250984192f, 0.3659733831882477f, 0.39608684182167053f}, 0.7214384620277396);
        check(d.get(115), CrashEffect.Kind.GLOW, 0, 1210, 371, 32,
                new float[] {0.9522058963775635f, 0.4585784375667572f, 0.1666666716337204f}, 0.33697493662431327);
        check(d.get(227), CrashEffect.Kind.PLANE, c.index(3.5f), 1015, 242, 0, new float[] {1, 1, 1}, 1.0);
        check(d.get(230), CrashEffect.Kind.GLOW, 0, 1197, 397, 36,
                new float[] {1.0f, 0.5529412031173706f, 0.19607843458652496f}, 0.6862035073076924);

        // Glissade : poussière, étincelles, éclats de la crête encore en vol.
        d = draws(m, c, 4.3f);
        assertEquals(411, d.size());
        assertEquals(Map.of(CrashEffect.Kind.PUFF, 180, CrashEffect.Kind.GLOW, 37, CrashEffect.Kind.SQUARE, 193,
                CrashEffect.Kind.PLANE, 1), kinds(d));
        check(d.get(137), CrashEffect.Kind.PUFF, 2, 1040, 708, 36,
                new float[] {0.7821052074432373f, 0.3994477391242981f, 0.16846124827861786f}, 0.34101583887632214);
        check(d.get(304), CrashEffect.Kind.PLANE, c.index(4.3f), 733, 550, 0, new float[] {1, 1, 1}, 1.0);
        // Éclat : compose.py n'écrit que couleur * fondu (image noire), on compare ce produit.
        CrashEffect.Draw spark = d.get(410);
        assertEquals(CrashEffect.Kind.SQUARE, spark.kind());
        assertEquals(928, spark.x(), 1f);
        assertEquals(776, spark.y(), 1f);
        assertEquals(2, spark.size());
        float[] seen = {0.9174821972846985f, 0.8671106100082397f, 0.7123979330062866f};
        for (int i = 0; i < 3; i++) {
            assertEquals(seen[i], spark.color()[i] * spark.alpha(), 1e-4);
        }
    }

    @Test
    void apresLImpactNeResteQueLaTraineeQuiSEteint() throws IOException {
        SceneManifest m = manifest();
        CrashTrack c = track();
        List<CrashEffect.Draw> d = draws(m, c, 6f);
        assertEquals(Map.of(CrashEffect.Kind.PUFF, 112), kinds(d));
        check(d.get(111), CrashEffect.Kind.PUFF, 0, 1044, 657, 114,
                new float[] {0.3529411852359772f, 0.3607843220233917f, 0.4313725531101227f}, 0.31487853460077964);
        assertTrue(draws(m, c, 0.5f).isEmpty(), "rien avant le début de la trajectoire");
        assertTrue(draws(m, c, 8.5f).isEmpty(), "traînée éteinte 4 s après l'impact");
        assertTrue(draws(m, c, StoryClock.HOLD).isEmpty(), "rien sur l'aube tenue");
    }

    @Test
    void lesEclairsEtLImpactSontCeuxDeFlashAt() {
        assertEquals(0.29082356038363444, EffectMath.flash(1.6, 1.0), 1e-9);
        assertEquals(0.4154622291194765, EffectMath.flash(2.7, 1.0), 1e-9);
        assertEquals(0.3323697832955833, EffectMath.flash(7.35, 1.0), 1e-9);
        assertEquals(1.0000765784310071e-05, EffectMath.flash(12.0, 0.6452009346092134), 1e-12);
        assertEquals(0.0, EffectMath.impactFlash(4.3), 0.0);
        assertEquals(0.7261490370736917, EffectMath.impactFlash(4.5), 1e-6);
        assertEquals(0.0, EffectMath.impactFlash(StoryClock.HOLD), 1e-30);
    }

    @Test
    void lesChocsSecouentLaCameraCommeCompose() throws IOException {
        manifest();
        CrashTrack c = track();
        // ox, oy de render(t) relevés dans compose.py, chocs compris.
        Ambiance a = Ambiance.at(4.3f, 4.3, c.crestT(), c.touchT());
        assertEquals(-10.963660477291299, a.offX(), 1e-3);
        assertEquals(2.616228728509329, a.offY(), 1e-3);
        Ambiance b = Ambiance.at(3.5f, 3.5, c.crestT(), c.touchT());
        assertEquals(-10.748934938948565, b.offX(), 1e-3);
        assertEquals(2.2522550674894077, b.offY(), 1e-3);
        // Plus aucune secousse sur l'aube tenue : seule la dérive du temps réel bouge la caméra.
        Ambiance hold = Ambiance.at(StoryClock.HOLD, 60.0, c.crestT(), c.touchT());
        Ambiance plain = Ambiance.at(StoryClock.HOLD, 60.0);
        assertEquals(plain.offX(), hold.offX(), 1e-6);
        assertEquals(plain.offY(), hold.offY(), 1e-6);
    }

    @Test
    void lesImagesDeLAvionSontToutesListees() throws IOException {
        manifest();
        List<String> paths = CrashTrack.spritePaths(path -> {
            try {
                Path p = ASSETS.resolve(path);
                return Files.isRegularFile(p) ? (InputStream) Files.newInputStream(p) : null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        assertEquals(track().frames(), paths.size());
        assertEquals("fx/crash/plane_000.png", paths.get(0));
        for (String p : paths) {
            assertTrue(Files.isRegularFile(ASSETS.resolve(p)), p);
        }
    }
}
