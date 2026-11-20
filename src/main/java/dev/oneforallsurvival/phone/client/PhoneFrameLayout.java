package dev.oneforallsurvival.phone.client;

import dev.oneforallsurvival.client.web.WebScreen.Rect;

/**
 * Le cadre de l'écran du téléphone, en coordonnées d'interface : le rapport de la page (390 : 844 px CSS), 92 % de la
 * hauteur de l'interface au plus, 90 % de sa largeur au plus, centré. La page se dessine toujours sur 390 px CSS de
 * large : son agrandissement est la largeur réelle du cadre divisée par 390.
 */
public final class PhoneFrameLayout {

    public static final int PAGE_WIDTH = 390;
    public static final int PAGE_HEIGHT = 844;
    private static final double MAX_HEIGHT = 0.92;
    private static final double MAX_WIDTH = 0.90;

    private PhoneFrameLayout() {}

    public static Rect fit(int guiWidth, int guiHeight) {
        int maxW = Math.max(1, (int) Math.floor(guiWidth * MAX_WIDTH));
        int maxH = Math.max(1, (int) Math.floor(guiHeight * MAX_HEIGHT));
        int h = maxH;
        int w = (int) Math.round(h * (double) PAGE_WIDTH / PAGE_HEIGHT);
        if (w > maxW) {
            w = maxW;
            h = Math.min(maxH, (int) Math.round(w * (double) PAGE_HEIGHT / PAGE_WIDTH));
        }
        w = Math.max(1, w);
        h = Math.max(1, h);
        return new Rect((guiWidth - w) / 2, (guiHeight - h) / 2, w, h);
    }

    /** Agrandissement de la page pour un cadre de {@code pixelWidth} pixels réels de large. */
    public static double scale(int pixelWidth) {
        return pixelWidth / (double) PAGE_WIDTH;
    }
}
