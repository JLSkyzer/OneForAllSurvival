package dev.oneforallsurvival.earlywindow.scene;

/**
 * Barre de chargement : une part fixe par phase, jamais décroissante, lissée. Logique pure.
 */
public final class ProgressModel {
    public enum Phase { FORGE_START, MOD_DISCOVERY, MOD_LOADING, REGISTRIES, RESOURCES, DONE }

    /**
     * Durée attendue de chaque phase (s, ordre de l'enum sans DONE), moyenne de deux démarrages réels avec le pack
     * et la scène, sans film, poste de Killian (RTX 4060), lue dans {@code run/logs/lostisland-earlywindow.log}
     * (changements de phase ; RESOURCES jusqu'à « rechargement fini ») :
     * <pre>
     *                  13:21 (tâche 8)  13:35 (tâche 9)  moyenne
     *   FORGE_START         0,58             0,59          0,59
     *   MOD_DISCOVERY      11,16            11,37         11,27
     *   MOD_LOADING         2,70             2,62          2,66
     *   REGISTRIES          2,01             2,11          2,06
     *   RESOURCES           7,53             7,60          7,56
     * </pre>
     * Total ≈ 24,1 s : bien moins que le relevé de la tâche 0 (≈ 44 s, un seul lancement avec l'écran de Forge,
     * échantillonné à 1 Hz). Voir docs/verifications/chantier-1-chargement.md (« Étalonnage »). Elles donnent les
     * parts de la barre ({@link #WEIGHTS}) et l'avancement estimé au temps des phases sans étapes
     * ({@code ProgressTracker}) et des ressources ({@link #resourcesFraction}).
     */
    static final double[] EXPECTED_SECONDS = {0.59, 11.27, 2.66, 2.06, 7.56};

    /** Part de la barre par phase (ordre de l'enum sans DONE) : durées attendues rapportées à leur somme. */
    static final float[] WEIGHTS = normalise(EXPECTED_SECONDS);

    /**
     * Plafond de l'avancement de la phase des ressources tant que le rechargement n'est pas fini : la barre
     * n'annonce jamais la fin avant qu'elle n'arrive.
     */
    public static final float RESOURCES_CAP = 0.98f;

    /** Vitesse maximale de rattrapage de la cible, en fraction de barre par seconde. */
    public static final float CATCH_UP = 3.0f;

    private float cible;
    private float affichee;

    private static float[] normalise(double[] seconds) {
        double total = 0.0;
        for (double s : seconds) {
            total += s;
        }
        float[] parts = new float[seconds.length];
        for (int i = 0; i < seconds.length; i++) {
            parts[i] = (float) (seconds[i] / total);
        }
        return parts;
    }

    /** Durée attendue de la phase, en secondes (0 pour DONE). */
    public static double expectedSeconds(Phase phase) {
        return phase.ordinal() < EXPECTED_SECONDS.length ? EXPECTED_SECONDS[phase.ordinal()] : 0.0;
    }

    /**
     * Avancement à montrer dans la phase des ressources (ruling 26). {@code reload.getActualProgress()} de
     * Minecraft est très en avance sur le temps : relevé en tâche 8, 0,70 à la première image, 0,95 une seconde
     * plus tard, 1,00 à 5 s, pour un rechargement fini à 7,6 s ; seul, il met la barre à ≈ 97 % presque tout de
     * suite. L'avancement montré est donc le plus petit de l'avancement réel et du temps écoulé rapporté à la durée
     * attendue : il croît avec le temps, ne devance jamais le rechargement, et reste sous {@link #RESOURCES_CAP}
     * jusqu'à la fin réelle (l'appelant passe alors à DONE). Les deux termes ne décroissent pas : le résultat non
     * plus.
     *
     * @param actual  avancement réel du rechargement (0 à 1)
     * @param elapsed secondes depuis le début de la phase des ressources
     */
    public static float resourcesFraction(float actual, double elapsed) {
        float real = Math.max(0f, Math.min(1f, actual));
        float time = (float) Math.max(0.0, elapsed / expectedSeconds(Phase.RESOURCES));
        return Math.min(RESOURCES_CAP, Math.min(real, time));
    }

    private static float debut(Phase phase) {
        float somme = 0f;
        for (int i = 0; i < phase.ordinal() && i < WEIGHTS.length; i++) {
            somme += WEIGHTS[i];
        }
        return somme;
    }

    /** Signale l'avancement dans une phase. Un rapport qui ferait reculer la cible est ignoré. */
    public void report(Phase phase, float fractionInPhase) {
        float nouvelle;
        if (phase == Phase.DONE) {
            nouvelle = 1f;
        } else {
            float f = Math.min(1f, Math.max(0f, fractionInPhase));
            nouvelle = debut(phase) + f * WEIGHTS[phase.ordinal()];
        }
        cible = Math.max(cible, Math.min(1f, nouvelle));
    }

    /** Valeur à dessiner après {@code dtSeconds} : rattrape la cible à {@link #CATCH_UP} au plus. */
    public float displayed(double dtSeconds) {
        float pas = (float) (Math.max(0.0, dtSeconds) * CATCH_UP);
        if (affichee < cible) {
            affichee = Math.min(cible, affichee + pas);
        }
        return affichee;
    }
}
