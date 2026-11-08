package dev.oneforallsurvival.earlywindow.scene;

/**
 * Ambiance et caméra d'une image : port de {@code state(t)} et de l'en-tête de {@code render(t)} de
 * {@code art/loading-screen/tools/compose.py}. Logique pure, sans GL.
 *
 * <p>{@code day} va de 0 (nuit) à 1 (pré-aube) puis 2 (lever du soleil) ; c'est l'indice de {@link #lerp3}.
 *
 * @param progress progression scénarisée du récit ({@code progress_at}), pas la vraie progression du chargement
 * @param zoom     zoom de la caméra (calque du milieu)
 * @param offX     décalage horizontal de la caméra, en pixels de la plaque
 * @param offY     décalage vertical de la caméra, en pixels de la plaque
 */
public record Ambiance(float progress, float day, float storm, float stars, float moon,
                       float zoom, float offX, float offY) {

    /**
     * Ambiance d'une image.
     *
     * @param storyTime temps du récit : ambiance (par {@link StoryClock#storyProgress}), avancée du zoom
     * @param realTime  temps réel : dérive sinusoïdale de la caméra, qui continue après le récit
     */
    public static Ambiance at(float storyTime, double realTime) {
        double t = storyTime;
        double p = StoryClock.storyProgress(storyTime);
        double day = Math.min(2.0, sstep(0.28, 0.66, p) + sstep(0.66, 1.0, p));
        double storm = 1 - sstep(0.16, 0.46, p);
        double stars = sstep(0.22, 0.42, p) * (1 - sstep(0.54, 0.74, p));
        double moon = sstep(0.18, 0.40, p) * (1 - sstep(0.6, 0.82, p));
        // Caméra : lente avancée sur la durée du récit, plus une dérive qui ne s'arrête jamais.
        // Les secousses des chocs de l'avion (crête, sol, impact) viendront avec le crash (tâche 5).
        double advance = sstep(0, StoryClock.DURATION, t);
        double zoom = 1.0 + 0.042 * advance;
        double ox = -14 + 28 * advance + Math.sin(realTime * 0.21) * 4;
        double oy = Math.sin(realTime * 0.17) * 3;
        return new Ambiance((float) p, (float) day, (float) storm, (float) stars, (float) moon,
                (float) zoom, (float) ox, (float) oy);
    }

    /** {@code sstep} de compose.py. */
    public static double sstep(double a, double b, double x) {
        double t = Math.min(1.0, Math.max(0.0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** {@code lerp3} : interpolation entre nuit (k=0), pré-aube (k=1) et lever (k=2), sur des couleurs RVB. */
    public static float[] lerp3(float[][] values, double k) {
        k = Math.min(2.0, Math.max(0.0, k));
        int i = Math.min(1, (int) k);
        double f = k - i;
        float[] a = values[i];
        float[] b = values[i + 1];
        float[] out = new float[a.length];
        for (int c = 0; c < a.length; c++) {
            out[c] = (float) (a[c] * (1 - f) + b[c] * f);
        }
        return out;
    }

    /** Couleur {@code #rrggbb} en RVB 0..1 ({@code hex_rgb}). */
    public static float[] rgb(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return new float[] {((v >> 16) & 0xff) / 255f, ((v >> 8) & 0xff) / 255f, (v & 0xff) / 255f};
    }

    /** Palette nuit, pré-aube, lever ({@code rgb_palette}). */
    public static float[][] palette(String night, String pre, String sun) {
        return new float[][] {rgb(night), rgb(pre), rgb(sun)};
    }

    /**
     * Transformation d'un calque ({@code View} de compose.py) : zoom autour du centre et décalage, en pixels
     * de la plaque.
     */
    public record View(float zoom, float offX, float offY) {
        /** Pixel de la plaque vers pixel de l'image de référence ({@code View.point}). */
        public float[] point(SceneManifest m, double x, double y) {
            return new float[] {
                    (float) ((x - m.plateW() / 2.0 - offX) * zoom + m.outW() / 2.0),
                    (float) ((y - m.plateH() / 2.0 - offY) * zoom + m.outH() / 2.0)};
        }
    }

    /** Calque du milieu (décor, épave) : la caméra elle-même. */
    public View mid() {
        return new View(zoom, offX, offY);
    }

    /** Arrière-plan : parallaxe plus lente. */
    public View far() {
        return new View(zoom, offX * 0.82f, offY * 0.82f);
    }

    /** Premier plan : parallaxe amplifiée. */
    public View fg() {
        return new View(1 + (zoom - 1) * 1.8f, offX * 1.9f, offY * 1.9f);
    }

    /** Ciel, nuages, rayons de lune (plaques à demi-résolution). */
    public View sky() {
        return new View(1 + (zoom - 1) * 0.3f, offX * 0.25f, offY * 0.25f);
    }

    /** Étoiles. */
    public View starView() {
        return new View(1 + (zoom - 1) * 0.15f, offX * 0.12f, offY * 0.12f);
    }

    /** Lune et soleil. */
    public View astre() {
        return new View(1 + (zoom - 1) * 0.15f, offX * 0.2f, offY * 0.2f);
    }

    /** Mer : même caméra que le calque du milieu, plaque à demi-résolution. */
    public View sea() {
        return new View(zoom, offX, offY);
    }
}
