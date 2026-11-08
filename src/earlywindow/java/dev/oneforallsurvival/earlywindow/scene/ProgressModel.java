package dev.oneforallsurvival.earlywindow.scene;

/**
 * Barre de chargement : une part fixe par phase, jamais décroissante, lissée. Logique pure.
 */
public final class ProgressModel {
    public enum Phase { FORGE_START, MOD_DISCOVERY, MOD_LOADING, REGISTRIES, RESOURCES, DONE }

    /**
     * Part de la barre par phase (ordre de l'enum sans DONE), proportionnelle aux durées du
     * relevé de la tâche 0 (docs/verifications/chantier-1-chargement.md, un seul lancement,
     * poste de Killian) :
     * fenêtre 0,7 s ; découverte + préparation du jeu 5,7 + 14,1 = 19,8 s ;
     * mods 20,5 à 28,9 s = 8,4 s (« Mod Gather » 18/18 à 28,9 s) ; registres 28,9 à 29,9 s
     * = 1,0 s ; ressources de Minecraft ≈ 14 s. Total 43,9 s, donc en dixièmes de seconde
     * 7 / 198 / 84 / 10 / 140 sur 439.
     */
    static final float[] WEIGHTS = {7f / 439f, 198f / 439f, 84f / 439f, 10f / 439f, 140f / 439f};

    /** Vitesse maximale de rattrapage de la cible, en fraction de barre par seconde. */
    public static final float CATCH_UP = 3.0f;

    private float cible;
    private float affichee;

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
