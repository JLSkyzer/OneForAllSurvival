package dev.oneforallsurvival.client.debug;

/** Couleurs de la direction A (verre ambré) et petits calculs de dessin. Logique pure. */
public final class DebugTheme {

    /** Fond des cartes : rgba(16, 14, 12, 0,62). */
    public static final int GLASS = 0x9E100E0C;
    /** Bord ambré : rgba(255, 190, 90, 0,25). */
    public static final int BORDER = 0x40FFBE5A;
    /** Bord bleu du panneau Lost Island : #78b8ff à 35 %. */
    public static final int BORDER_ISLAND = 0x5978B8FF;
    public static final int AMBER = 0xFFFFB544;
    public static final int ORANGE = 0xFFFF7A2E;
    public static final int AXIS_X = 0xFFFF7B6B;
    public static final int AXIS_Y = 0xFF7FE08A;
    public static final int AXIS_Z = 0xFF78B8FF;
    public static final int ISLAND = 0xFF78B8FF;
    public static final int VALUE = 0xFFFFFFFF;
    public static final int LABEL = 0xFFB9AD99;
    public static final int TRACK = 0x1FFFFFFF;
    public static final int GUIDE = 0x66FFFFFF;
    public static final int GOOD = 0xFF7FE08A;
    public static final int BAD = 0xFFFF7B6B;

    /** Durée d'une image à 30 FPS, en nanosecondes : le haut du petit graphe. */
    private static final double SPARK_FULL_NANOS = 1_000_000_000.0 / 30.0;
    /** Échelle de vanilla pour le graphe des FPS : 30 px pour 1/60 s. */
    private static final double FRAME_SCALE = 30.0 * 60.0 / 1_000_000_000.0;
    /** Borne de l'échelle de couleur de vanilla pour le graphe des FPS. */
    public static final int FRAME_COLOR_MAX = 100;

    private DebugTheme() {}

    /** La couleur {@code argb}, son alpha multiplié par {@code alpha} (0..1). */
    public static int fade(int argb, double alpha) {
        int a = (int) Math.round((argb >>> 24) * Math.max(0.0, Math.min(1.0, alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Mélange linéaire de deux couleurs ({@code t} borné à 0..1). */
    public static int mix(int from, int to, double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int a = (from >>> shift) & 0xFF;
            int b = (to >>> shift) & 0xFF;
            out |= ((int) Math.round(a + (b - a) * k)) << shift;
        }
        return out;
    }

    /** Couleur d'une barre de graphe : vert à 0, ambre au milieu, rouge à {@code max}, le dégradé de vanilla aux couleurs du F3. */
    public static int sampleColor(int value, int max) {
        int mid = max / 2;
        if (value <= mid) {
            return mix(GOOD, AMBER, mid == 0 ? 1.0 : (double) value / mid);
        }
        return mix(AMBER, BAD, (double) (value - mid) / Math.max(1, max - mid));
    }

    /** Valeur 0..{@link #FRAME_COLOR_MAX} d'une durée d'image, à l'échelle de vanilla. */
    public static int frameValue(long nanos) {
        return (int) Math.max(0, Math.min(FRAME_COLOR_MAX, nanos * FRAME_SCALE));
    }

    /** Hauteur d'une barre du petit graphe : pleine à 30 FPS ou moins, au moins 1 pixel. */
    public static int sparkHeight(long nanos, int maxHeight) {
        int h = (int) Math.round(nanos / SPARK_FULL_NANOS * maxHeight);
        return Math.max(1, Math.min(maxHeight, h));
    }

    /**
     * Échelle des titres en petites capitales : proche de 3/4, mais calée sur un nombre entier de pixels
     * d'écran par pixel de police, sinon la police bitmap déforme ses traits. Échelle d'interface 1 ou 2 : 1.
     */
    public static double titleScale(int guiScale) {
        if (guiScale <= 2) {
            return 1.0;
        }
        return Math.round(guiScale * 0.75) / (double) guiScale;
    }
}
