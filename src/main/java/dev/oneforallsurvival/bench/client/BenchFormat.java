package dev.oneforallsurvival.bench.client;

import java.util.Locale;

/** Mise en forme des durées et des vitesses de l'établi. Pure : aucune classe de Minecraft. */
public final class BenchFormat {

    private BenchFormat() {}

    /** « 8 s », « 1 min 26 s », « 2 min », « 1 h 5 min » : la durée en secondes, sans zéro inutile. */
    public static String duration(long seconds) {
        long s = Math.max(0, seconds);
        if (s < 60) {
            return s + " s";
        }
        if (s < 3600) {
            long rest = s % 60;
            return rest == 0 ? (s / 60) + " min" : (s / 60) + " min " + rest + " s";
        }
        long minutes = (s % 3600) / 60;
        return minutes == 0 ? (s / 3600) + " h" : (s / 3600) + " h " + minutes + " min";
    }

    /** Durée restante en ticks, arrondie à la seconde supérieure : on ne montre jamais « 0 s » avant la fin. */
    public static String remaining(double ticks) {
        return duration((long) Math.ceil(Math.max(0, ticks) / 20.0));
    }

    /** « 4 » pour 4.0, « 2,5 » pour 2.5 (virgule décimale à la française selon la langue demandée). */
    public static String speed(double speed, Locale locale) {
        if (Math.abs(speed - Math.rint(speed)) < 1e-9) {
            return Long.toString(Math.round(speed));
        }
        String text = String.format(locale, "%.2f", speed);
        String decimal = String.valueOf(java.text.DecimalFormatSymbols.getInstance(locale).getDecimalSeparator());
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(decimal) ? text.substring(0, text.length() - 1) : text;
    }
}
