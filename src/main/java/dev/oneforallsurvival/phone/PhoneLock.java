package dev.oneforallsurvival.phone;

/** Le blocage après essais ratés, sans état : l'appelant garde {@code failures} et {@code lockedUntil}. */
public final class PhoneLock {

    /** @param accepted le code est bon et le téléphone n'était pas bloqué */
    public record Outcome(boolean accepted, int failures, long lockedUntil) {}

    private PhoneLock() {}

    /**
     * Pendant un blocage ({@code now < lockedUntil}) tout est refusé, même le bon code, et rien n'est compté. Un bon
     * code remet les échecs à zéro. Un faux code les compte ; au {@code maxAttempts}-ième, le blocage démarre et le
     * compte repart de zéro.
     */
    public static Outcome attempt(boolean correct, int failures, long lockedUntil, long now, int maxAttempts,
            long lockoutMillis) {
        if (now < lockedUntil) {
            return new Outcome(false, failures, lockedUntil);
        }
        if (correct) {
            return new Outcome(true, 0, lockedUntil);
        }
        int next = failures + 1;
        if (next >= maxAttempts) {
            return new Outcome(false, 0, now + lockoutMillis);
        }
        return new Outcome(false, next, lockedUntil);
    }
}
