package dev.oneforallsurvival.food;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de la péremption. Fichier SERVER : le serveur en décide et Forge le synchronise vers les clients,
 * pour que l'infobulle suive les durées du serveur. Les lecteurs retombent sur les valeurs par défaut tant que
 * le fichier n'est pas chargé (aucun monde, ou JUnit).
 */
public final class FoodConfig {

    /** Ticks par heure réelle : 20 ticks par seconde. */
    private static final double TICKS_PER_HOUR = 72000.0;

    private static final double DEFAULT_FRESH_HOURS = 6.0;
    private static final double DEFAULT_STALE_HOURS = 6.0;
    private static final double DEFAULT_SLOW_FACTOR = 8.0;
    private static final double DEFAULT_FRIDGE_FACTOR = 4.0;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue FRESH_HOURS;
    private static final ForgeConfigSpec.DoubleValue STALE_HOURS;
    private static final ForgeConfigSpec.DoubleValue SLOW_FACTOR;
    private static final ForgeConfigSpec.DoubleValue FRIDGE_FACTOR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        FRESH_HOURS = builder
                .comment("Heures (réelles) pendant lesquelles un aliment reste frais.")
                .defineInRange("freshHours", DEFAULT_FRESH_HOURS, 0.01, 10000.0);
        STALE_HOURS = builder
                .comment("Heures (réelles) pendant lesquelles un aliment reste rassis, avant d'être avarié.")
                .defineInRange("staleHours", DEFAULT_STALE_HOURS, 0.01, 10000.0);
        SLOW_FACTOR = builder
                .comment("Multiplicateur des durées pour un aliment du tag slow_perishable (viandes séchées).")
                .defineInRange("slowFactor", DEFAULT_SLOW_FACTOR, 1.0, 10000.0);
        FRIDGE_FACTOR = builder
                .comment("Combien de fois plus lentement un aliment vieillit dans une armoire alimentée.")
                .defineInRange("fridgeFactor", DEFAULT_FRIDGE_FACTOR, 1.0, 10000.0);
        SPEC = builder.build();
    }

    private FoodConfig() {}

    public static long freshTicks() {
        return ticks(read(FRESH_HOURS, DEFAULT_FRESH_HOURS));
    }

    public static long staleTicks() {
        return ticks(read(STALE_HOURS, DEFAULT_STALE_HOURS));
    }

    public static double slowFactor() {
        return read(SLOW_FACTOR, DEFAULT_SLOW_FACTOR);
    }

    public static double fridgeFactor() {
        return read(FRIDGE_FACTOR, DEFAULT_FRIDGE_FACTOR);
    }

    private static long ticks(double hours) {
        return Math.round(hours * TICKS_PER_HOUR);
    }

    /** Lit une valeur ; repli sur le défaut tant que le fichier n'est pas chargé. */
    private static double read(ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
