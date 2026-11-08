package dev.oneforallsurvival.client.debug;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applique {@link SmoothNumber} aux chiffres d'une ligne du F3, sans toucher au reste du texte ni au nombre
 * de décimales. Seules les lignes dont les chiffres sont des mesures sont concernées : un numéro de bloc ou
 * une ligne d'un autre mod reste exact. Logique pure.
 */
public final class SmoothLines {

    /** Un nombre isolé, pas un morceau d'identifiant ou de version. */
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?");

    /**
     * @param count nombre de nombres lissés en tête de ligne
     * @param jump  les grands écarts sautent directement (téléportation)
     */
    private record Spec(int count, boolean jump) {}

    private static final int ALL = Integer.MAX_VALUE;
    private static final Map<String, Spec> SPECS = Map.of(
            "xyz", new Spec(3, true),
            // Après le premier nombre viennent la limite d'images et les réglages : rien à lisser.
            "fps", new Spec(1, false),
            "mem", new Spec(ALL, false),
            "alloc", new Spec(ALL, false),
            "alloc.rate", new Spec(ALL, false),
            "server", new Spec(ALL, false));

    private final Map<String, SmoothNumber[]> numbers = new HashMap<>();

    /** Oublie tout : le prochain relevé s'affiche tel quel. */
    public void clear() {
        numbers.clear();
    }

    /** Le texte à afficher pour cette image. Une ligne dont le nombre de chiffres change repart de ses valeurs réelles. */
    public String apply(String key, String text, double dt) {
        Spec spec = SPECS.get(key);
        if (spec == null) {
            return text;
        }
        Matcher m = NUMBER.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        int smoothed = Math.min(count, spec.count());
        SmoothNumber[] state = numbers.get(key);
        if (state == null || state.length != smoothed) {
            state = new SmoothNumber[smoothed];
            for (int i = 0; i < smoothed; i++) {
                state[i] = new SmoothNumber(spec.jump());
            }
            numbers.put(key, state);
        }
        m.reset();
        StringBuilder out = new StringBuilder(text.length() + 4);
        int last = 0;
        for (int i = 0; i < smoothed && m.find(); i++) {
            out.append(text, last, m.start());
            String token = m.group();
            out.append(format(state[i].update(Double.parseDouble(token), dt), decimals(token)));
            last = m.end();
        }
        return out.append(text, last, text.length()).toString();
    }

    private static int decimals(String token) {
        int dot = token.indexOf('.');
        return dot < 0 ? 0 : token.length() - dot - 1;
    }

    /** Toujours avec un point, comme vanilla pour les coordonnées ; jamais de « -0 ». */
    static String format(double value, int decimals) {
        String text = String.format(Locale.ROOT, "%." + decimals + "f", value);
        if (text.startsWith("-") && text.chars().noneMatch(c -> c >= '1' && c <= '9')) {
            return text.substring(1);
        }
        return text;
    }
}
