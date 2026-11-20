package dev.oneforallsurvival.phone.net;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;

/** Les numéros des cartes SIM : « 06 » suivi de huit chiffres, comme un mobile français. Règle pure. */
public final class SimNumbers {

    /** Nombre maximal de tirages avant d'abandonner (le plan de numérotation compte 100 millions de numéros). */
    private static final int MAX_DRAWS = 1000;

    private static final String PREFIX = "06";
    private static final int DIGITS = 10;

    private SimNumbers() {}

    /**
     * Tire un numéro que {@code taken} ne retient pas. Au plus {@value #MAX_DRAWS} tirages ; si tous sont pris, le
     * plan de numérotation est saturé (ou {@code taken} est faux) : {@link IllegalStateException}. Les chiffres sont
     * toujours ASCII, quelle que soit la langue par défaut de la JVM ({@link Locale#ROOT}) : {@link #parse} ne lit
     * que de l'ASCII.
     */
    public static String draw(RandomSource random, Predicate<String> taken) {
        for (int i = 0; i < MAX_DRAWS; i++) {
            String number = PREFIX + String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000));
            if (!taken.test(number)) {
                return number;
            }
        }
        throw new IllegalStateException("aucun numéro libre après " + MAX_DRAWS + " tirages");
    }

    /**
     * « 0612345678 » devient « 06 12 34 56 78 ». Un texte qui n'est pas un numéro de carte est rendu tel quel : c'est
     * un affichage, il ne doit jamais lever ({@code format(null)} rend donc {@code null}).
     */
    public static String format(String number) {
        Optional<String> clean = parse(number);
        if (clean.isEmpty()) {
            return number;
        }
        String n = clean.get();
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < DIGITS; i += 2) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(n, i, i + 2);
        }
        return sb.toString();
    }

    /**
     * Lit un numéro saisi, avec ou sans espaces : exactement dix chiffres ASCII, le premier couple valant « 06 ».
     * Rend les dix chiffres sans espaces, ou rien. Tout autre caractère (lettre, tabulation, chiffre d'une autre
     * écriture, pleine chasse comprise) refuse le texte.
     */
    public static Optional<String> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        StringBuilder digits = new StringBuilder(DIGITS);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                continue;
            }
            if (c < '0' || c > '9' || digits.length() >= DIGITS) {
                return Optional.empty();
            }
            digits.append(c);
        }
        if (digits.length() != DIGITS || !digits.substring(0, 2).equals(PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(digits.toString());
    }
}
