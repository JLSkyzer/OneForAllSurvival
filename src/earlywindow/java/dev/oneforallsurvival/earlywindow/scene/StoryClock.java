package dev.oneforallsurvival.earlywindow.scene;

/**
 * Horloge du récit de 36 s (chute, crash, feux, lever du jour). Logique pure, sans GL.
 *
 * <p>Le temps du récit suit le temps réel, puis se fige sur {@link #HOLD} (dernière image de
 * l'aube avant le voile blanc de {@code compose.py}). Il ne boucle jamais. Si le chargement
 * finit avant la fin du récit, le temps accélère de {@link #ACCELERATION} fois, sans saut,
 * jusqu'à {@link #HOLD}.
 *
 * <p>L'objet garde l'instant où il a vu le chargement fini : appeler avec un temps réel
 * croissant.
 */
public final class StoryClock {
    public static final float DURATION = 36f;
    public static final float IMPACT = 4.4f;
    /** Arrivée de la barre dans compose.py ; ici, seul le logo s'y règle (la barre est là dès l'ouverture). */
    public static final float BAR_IN = 5.2f;
    /** Image tenue après le récit (FADE_OUT de compose.py). */
    public static final float HOLD = 33.6f;
    public static final float ACCELERATION = 4.0f;

    // Constantes de compose.py pour la courbe d'ambiance.
    private static final double LOAD_START = 5.8;
    private static final double LOAD_END = 29.0;

    private double finiReel = Double.NaN;
    private double recitAuFini;
    private float dernier;

    /** Temps du récit pour un temps réel donné (secondes depuis l'ouverture de la fenêtre). */
    public float storyTime(double realSeconds, boolean loadingDone) {
        double reel = Math.max(0.0, realSeconds);
        if (loadingDone && Double.isNaN(finiReel)) {
            finiReel = reel;
            recitAuFini = Math.min(reel, HOLD);
        }
        double t;
        if (Double.isNaN(finiReel) || reel < finiReel) {
            t = reel;
        } else {
            t = recitAuFini + ACCELERATION * (reel - finiReel);
        }
        dernier = Math.max(dernier, (float) Math.min(t, HOLD));
        return dernier;
    }

    /** Vrai quand le récit a atteint l'image tenue. */
    public boolean storyFinished(double realSeconds, boolean loadingDone) {
        return storyTime(realSeconds, loadingDone) >= HOLD;
    }

    /**
     * Progression d'ambiance du récit (nuit, orage, aube), port exact de {@code progress_at} de
     * compose.py. Elle pilote la scène ; la barre affichée suit le vrai chargement.
     */
    public static float storyProgress(float storyTime) {
        if (storyTime <= LOAD_START) {
            return 0f;
        }
        if (storyTime >= LOAD_END) {
            return 1f;
        }
        double x = (storyTime - LOAD_START) / (LOAD_END - LOAD_START);
        double brut = x + 0.035 * Math.sin(x * 9.0) + 0.018 * Math.sin(x * 23.0);
        double p = brut * (0.5 + 0.5 * x) + x * (0.5 - 0.5 * x);
        return (float) Math.min(1.0, Math.max(0.0, p));
    }
}
