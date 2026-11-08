package dev.oneforallsurvival.client.debug;

/**
 * Chiffre affiché qui suit sa valeur réelle sans clignoter : approche exponentielle, indépendante du
 * nombre d'images par seconde. Logique pure.
 *
 * <p>Avec {@code jumpOnBigChange} (coordonnées), un écart supérieur à 50 % de la valeur affichée est une
 * téléportation ou un changement de dimension : le chiffre saute directement, au lieu de défiler.
 */
public final class SmoothNumber {

    /** Constante de temps de l'approche, en secondes (63 % de l'écart résorbé en 0,15 s). */
    public static final double TIME_CONSTANT = 0.15;
    /** Écart relatif au-delà duquel le chiffre saute directement. */
    public static final double JUMP_RATIO = 0.5;

    private final boolean jumpOnBigChange;
    private double value;
    private boolean initialized;

    public SmoothNumber(boolean jumpOnBigChange) {
        this.jumpOnBigChange = jumpOnBigChange;
    }

    /**
     * Avance d'une image et rend la valeur à afficher. Le premier appel adopte la cible telle quelle.
     *
     * @param target valeur réelle
     * @param dt     secondes écoulées depuis l'appel précédent (négatif ou nul : valeur inchangée)
     */
    public double update(double target, double dt) {
        if (!initialized) {
            value = target;
            initialized = true;
            return value;
        }
        double gap = Math.abs(target - value);
        if (jumpOnBigChange && gap > JUMP_RATIO * Math.abs(value)) {
            value = target;
        } else if (dt > 0) {
            value += (target - value) * (1.0 - Math.exp(-dt / TIME_CONSTANT));
        }
        return value;
    }

    /** Dernière valeur rendue (0 avant le premier appel). */
    public double value() {
        return value;
    }
}
