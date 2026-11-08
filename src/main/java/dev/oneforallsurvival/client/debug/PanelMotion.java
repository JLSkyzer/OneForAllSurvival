package dev.oneforallsurvival.client.debug;

/**
 * Animation d'ouverture des panneaux : glissement de 6 px et fondu sur 0,25 s, panneau après panneau
 * (0,04 s de décalage par rang). La fermeture est immédiate : l'appelant cesse de dessiner et remet son
 * horloge à zéro. Logique pure.
 */
public final class PanelMotion {

    /** Distance de glissement en pixels d'interface. */
    public static final double SLIDE_PX = 6.0;
    /** Durée de l'apparition d'un panneau, en secondes. */
    public static final double DURATION = 0.25;
    /** Décalage entre deux panneaux successifs, en secondes. */
    public static final double STAGGER = 0.04;

    private PanelMotion() {}

    /** Avancement 0..1 (adouci en sortie) du panneau de rang {@code index}, {@code sinceOpen} secondes après l'ouverture. */
    public static double progress(double sinceOpen, int index) {
        double t = (sinceOpen - index * STAGGER) / DURATION;
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        double inv = 1 - t;
        return 1 - inv * inv * inv;
    }

    /** Opacité 0..1. */
    public static double alpha(double sinceOpen, int index) {
        return progress(sinceOpen, index);
    }

    /** Décalage restant en pixels (6 au début, 0 à l'arrivée) ; l'appelant choisit le sens (vers le bord de l'écran). */
    public static double offset(double sinceOpen, int index) {
        return SLIDE_PX * (1 - progress(sinceOpen, index));
    }

    /** Vrai quand les {@code count} panneaux sont arrivés (plus rien à animer). */
    public static boolean settled(double sinceOpen, int count) {
        return count <= 0 || sinceOpen >= (count - 1) * STAGGER + DURATION;
    }
}
