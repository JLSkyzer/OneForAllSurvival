package dev.oneforallsurvival.economy.payroll;

/**
 * Calcul des salaires, pur. Chaque seconde passée dans un rôle payé ajoute son taux horaire en « cuivre-secondes » ;
 * le salaire du jour vaut ces cuivre-secondes divisées par 3 600, arrondies vers le bas.
 */
public final class SalaryMath {

    public static final long SECONDS_PER_HOUR = 3_600L;

    private SalaryMath() {}

    /** Le salaire, en cuivres, de ces cuivre-secondes (÷ 3 600, vers le bas ; jamais négatif). */
    public static long amount(long rateSeconds) {
        return rateSeconds <= 0L ? 0L : rateSeconds / SECONDS_PER_HOUR;
    }

    /**
     * Découpe l'intervalle {@code [from, to)} à la frontière d'un jour de paie. Renvoie
     * {@code {secondes avant, secondes après, reste en millisecondes}} : le total des secondes entières de
     * l'intervalle, dont la part avant la frontière (secondes entières écoulées avant elle) ; le reste, moins d'une
     * seconde, est à reporter sur l'échantillon suivant. Un intervalle vide ou à rebours donne trois zéros.
     */
    public static long[] split(long fromMillis, long toMillis, long boundaryMillis) {
        if (toMillis <= fromMillis) {
            return new long[] {0L, 0L, 0L};
        }
        long span = toMillis - fromMillis;
        long total = span / 1_000L;
        long rest = span % 1_000L;
        long before;
        if (boundaryMillis <= fromMillis) {
            before = 0L;
        } else if (boundaryMillis >= toMillis) {
            before = total;
        } else {
            before = Math.min(total, (boundaryMillis - fromMillis) / 1_000L);
        }
        return new long[] {before, total - before, rest};
    }

    /** Ce qui peut encore être compté : {@code add} au plus, sans dépasser {@code maxSeconds} au total. */
    public static long creditable(long alreadySeconds, long addSeconds, long maxSeconds) {
        if (addSeconds <= 0L) {
            return 0L;
        }
        return Math.max(0L, Math.min(addSeconds, maxSeconds - alreadySeconds));
    }
}
