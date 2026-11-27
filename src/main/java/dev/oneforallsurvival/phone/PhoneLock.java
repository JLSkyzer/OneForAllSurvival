package dev.oneforallsurvival.phone;

/**
 * Le blocage après essais ratés, sans état : l'appelant garde {@code failures}, {@code lockedUntil} et {@code level},
 * le nombre de séries ratées depuis le dernier bon code. Chaque série double la durée du blocage : {@code baseMillis}
 * à la première, puis le double à chaque série suivante, jusqu'au plafond {@code maxMillis} (un plafond sous la base
 * vaut la base ; une base nulle : aucun blocage). Seul un bon code remet le niveau à zéro : ni le temps, ni rien
 * d'autre ne le baisse ici.
 */
public final class PhoneLock {

    /** @param accepted le code est bon et le téléphone n'était pas bloqué */
    public record Outcome(boolean accepted, int failures, long lockedUntil, int level) {}

    private PhoneLock() {}

    /**
     * Pendant un blocage ({@code now < lockedUntil}) tout est refusé, même le bon code, et rien n'est compté. Un bon
     * code remet les échecs et le niveau à zéro. Un faux code compte ; au {@code maxAttempts}-ième, le blocage démarre,
     * pour la durée de la série en cours ({@code level}), le niveau monte d'un cran et le compte repart de zéro. Un
     * niveau négatif (corrompu) vaut 0.
     */
    public static Outcome attempt(boolean correct, int failures, long lockedUntil, int level, long now,
            int maxAttempts, long baseMillis, long maxMillis) {
        level = Math.max(0, level);
        lockedUntil = capped(lockedUntil, now, level, baseMillis, maxMillis);
        if (now < lockedUntil) {
            return new Outcome(false, failures, lockedUntil, level);
        }
        if (correct) {
            return new Outcome(true, 0, lockedUntil, 0);
        }
        int next = failures + 1;
        if (next >= maxAttempts) {
            return new Outcome(false, 0, after(now, duration(level, baseMillis, maxMillis)),
                    level == Integer.MAX_VALUE ? level : level + 1);
        }
        return new Outcome(false, next, lockedUntil, level);
    }

    /**
     * La durée du blocage posé par la série {@code series}, comptée depuis le dernier bon code (0 : la première, ou une
     * valeur négative) : {@code baseMillis} doublée {@code series} fois, sans jamais passer
     * {@code max(baseMillis, maxMillis)} ni dépasser un long ; 0 si la base est nulle.
     */
    public static long duration(int series, long baseMillis, long maxMillis) {
        if (baseMillis <= 0) {
            return 0L;
        }
        long ceiling = Math.max(baseMillis, maxMillis);
        long duration = baseMillis;
        for (int i = 0; i < series && duration < ceiling; i++) {
            duration = duration > ceiling - duration ? ceiling : duration * 2;
        }
        return duration;
    }

    /**
     * La fin de blocage ramenée à {@code now} plus la durée de la série qui l'a posée, au plus : c'est la série
     * {@code level - 1}, et la base au niveau 0 (sauvegarde d'avant le niveau). Une horloge qui a reculé, une sauvegarde
     * restaurée ou une valeur corrompue ne bloquent donc pas le téléphone plus longtemps que cette durée (sans
     * dépassement de long).
     */
    public static long capped(long lockedUntil, long now, int level, long baseMillis, long maxMillis) {
        // « level <= 0 » d'abord : Integer.MIN_VALUE - 1 vaudrait le plus grand des niveaux.
        int posedBy = level <= 0 ? 0 : level - 1;
        return Math.min(lockedUntil, after(now, duration(posedBy, baseMillis, maxMillis)));
    }

    /** {@code now + millis} ({@code millis} positif ou nul), arrêté à {@link Long#MAX_VALUE}. */
    private static long after(long now, long millis) {
        return now > Long.MAX_VALUE - millis ? Long.MAX_VALUE : now + millis;
    }
}
