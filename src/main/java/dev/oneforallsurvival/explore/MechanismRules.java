package dev.oneforallsurvival.explore;

/** Les règles pures du mécanisme ancien : la progression d'un tick à l'autre et son étage visuel. */
public final class MechanismRules {

    /** La propriété {@code stage} va de 0 à ce nombre. */
    public static final int MAX_STAGE = 3;

    private MechanismRules() {}

    /**
     * La progression au tick suivant. Elle repart à 0 si le mécanisme a reçu moins que {@code threshold} FE pendant ce
     * tick ; sinon elle avance de 1, sans dépasser {@code durationTicks}.
     */
    public static int next(int progress, int receivedThisTick, int threshold, int durationTicks) {
        if (receivedThisTick < threshold) {
            return 0;
        }
        return Math.min(progress + 1, durationTicks);
    }

    /** Vrai quand la progression a atteint la durée réglée. */
    public static boolean isComplete(int progress, int durationTicks) {
        return progress >= durationTicks;
    }

    /** L'étage visuel, de 0 (au repos) à {@link #MAX_STAGE} (progression complète), proportionnel à la progression. */
    public static int stage(int progress, int durationTicks) {
        if (progress <= 0) {
            return 0;
        }
        if (durationTicks <= 0) {
            return MAX_STAGE;
        }
        return Math.min(MAX_STAGE, progress * MAX_STAGE / durationTicks);
    }
}
