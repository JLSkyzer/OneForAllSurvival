package dev.oneforallsurvival.earlywindow.scene;

/**
 * Projection de l'image de référence (1920x1080 de compose.py, y vers le bas) sur la fenêtre : recadrage centré.
 * Logique pure, sans GL ; partagée par {@link SceneRenderer} et par l'écran titre du mod, pour que les boutons
 * restent calés sur le logo de la scène.
 */
public final class SceneProjection {
    /** Position du logo dans l'image de référence ({@code OX}, {@code OY} de compose.py). */
    public static final int LOGO_X = 72;
    public static final int LOGO_Y = 58;

    private SceneProjection() {}

    /** Recadrage centré : la plus petite échelle (pixels fenêtre par pixel de référence) qui couvre la fenêtre. */
    public static float coverScale(int fbWidth, int fbHeight, int outW, int outH) {
        return Math.max((float) fbWidth / outW, (float) fbHeight / outH);
    }

    /**
     * Rectangle de l'image de référence en pixels de fenêtre : {@code {x, y, w, h}}. Les bords sont arrondis vers
     * l'extérieur (le rectangle retourné couvre le rectangle exact).
     */
    public static int[] toWindow(int fbWidth, int fbHeight, int outW, int outH, int x, int y, int w, int h) {
        float s = coverScale(fbWidth, fbHeight, outW, outH);
        double x0 = (x - outW / 2.0) * s + fbWidth / 2.0;
        double y0 = (y - outH / 2.0) * s + fbHeight / 2.0;
        double x1 = (x + w - outW / 2.0) * s + fbWidth / 2.0;
        double y1 = (y + h - outH / 2.0) * s + fbHeight / 2.0;
        int fx = (int) Math.floor(x0);
        int fy = (int) Math.floor(y0);
        return new int[] {fx, fy, (int) Math.ceil(x1) - fx, (int) Math.ceil(y1) - fy};
    }
}
