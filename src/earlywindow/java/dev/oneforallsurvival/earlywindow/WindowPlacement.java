package dev.oneforallsurvival.earlywindow;

/**
 * Où ouvrir la fenêtre anticipée. Logique pure, sans GLFW.
 *
 * <p>Par défaut, centrée sur l'écran principal (comportement des joueurs). En développement seulement, avec la propriété
 * système {@value #PROPERTY} à {@code true}, les essais automatiques l'ouvrent sur un autre écran que le principal, à
 * {@value #MARGIN} pixels du coin de sa zone de travail (même règle que {@code dev/FenetreSecondEcran} pour la fenêtre du
 * jeu) ; s'il n'y a qu'un écran, rien ne change.
 */
public final class WindowPlacement {
    public static final String PROPERTY = "oneforallsurvival.fenetre.secondEcran";
    /** Marge depuis le coin de la zone de travail, pour garder la barre de titre visible. */
    static final int MARGIN = 40;

    private WindowPlacement() {}

    /** Vrai si la propriété de développement demande l'autre écran. */
    public static boolean secondScreenRequested() {
        return Boolean.getBoolean(PROPERTY);
    }

    /** Le premier écran de la liste qui n'est pas le principal ; {@code 0} s'il n'y en a pas. */
    public static long otherMonitor(long[] monitors, long primary) {
        if (monitors == null) {
            return 0L;
        }
        for (long m : monitors) {
            if (m != 0L && m != primary) {
                return m;
            }
        }
        return 0L;
    }

    /** Fenêtre centrée sur l'écran principal : position {x, y}. */
    public static int[] centered(int monitorX, int monitorY, int modeWidth, int modeHeight, int winWidth, int winHeight) {
        return new int[] {(modeWidth - winWidth) / 2 + monitorX, (modeHeight - winHeight) / 2 + monitorY};
    }

    /** Fenêtre sur l'autre écran, au coin de sa zone de travail : position {x, y}. */
    public static int[] onWorkArea(int workX, int workY) {
        return new int[] {workX + MARGIN, workY + MARGIN};
    }
}
