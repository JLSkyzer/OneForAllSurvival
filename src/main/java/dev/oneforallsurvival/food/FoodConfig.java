package dev.oneforallsurvival.food;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de la péremption. Fichier SERVER : le serveur en décide et Forge le synchronise vers les clients,
 * pour que l'infobulle suive les durées du serveur. Les lecteurs retombent sur les valeurs par défaut tant que
 * le fichier n'est pas chargé (aucun monde, ou JUnit).
 */
public final class FoodConfig {

    /**
     * Ticks par heure de jeu : 20 ticks par seconde. Le temps est compté sur l'horloge du monde
     * ({@code getGameTime}), qui ne court que serveur allumé.
     */
    private static final double TICKS_PER_HOUR = 72000.0;

    private static final double DEFAULT_FRESH_HOURS = 6.0;
    private static final double DEFAULT_STALE_HOURS = 6.0;
    private static final double DEFAULT_SLOW_FACTOR = 8.0;
    private static final double DEFAULT_FRIDGE_FACTOR = 4.0;
    private static final int DEFAULT_DESALINATOR_ENERGY = 40;
    private static final int DEFAULT_DESALINATOR_CYCLE = 100;
    private static final int DEFAULT_WATER_PER_CYCLE = 250;
    private static final int DEFAULT_POTABLE_PER_CYCLE = 200;
    private static final int DEFAULT_CYCLES_PER_SALT = 4;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue FRESH_HOURS;
    private static final ForgeConfigSpec.DoubleValue STALE_HOURS;
    private static final ForgeConfigSpec.DoubleValue SLOW_FACTOR;
    private static final ForgeConfigSpec.DoubleValue FRIDGE_FACTOR;
    private static final ForgeConfigSpec.IntValue DESALINATOR_ENERGY;
    private static final ForgeConfigSpec.IntValue DESALINATOR_CYCLE;
    private static final ForgeConfigSpec.IntValue WATER_PER_CYCLE;
    private static final ForgeConfigSpec.IntValue POTABLE_PER_CYCLE;
    private static final ForgeConfigSpec.IntValue CYCLES_PER_SALT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        FRESH_HOURS = builder
                .comment("Heures de jeu (serveur allumé) pendant lesquelles un aliment reste frais.",
                        "Le temps est compté sur l'horloge du monde : un aliment ne vieillit pas quand le serveur est éteint.")
                .defineInRange("freshHours", DEFAULT_FRESH_HOURS, 0.01, 10000.0);
        STALE_HOURS = builder
                .comment("Heures de jeu (serveur allumé) pendant lesquelles un aliment reste rassis, avant d'être avarié.",
                        "Le temps est compté sur l'horloge du monde : un aliment ne vieillit pas quand le serveur est éteint.")
                .defineInRange("staleHours", DEFAULT_STALE_HOURS, 0.01, 10000.0);
        SLOW_FACTOR = builder
                .comment("Multiplicateur des durées pour un aliment du tag slow_perishable (viandes séchées).")
                .defineInRange("slowFactor", DEFAULT_SLOW_FACTOR, 1.0, 10000.0);
        FRIDGE_FACTOR = builder
                .comment("Combien de fois plus lentement un aliment vieillit dans une armoire alimentée.")
                .defineInRange("fridgeFactor", DEFAULT_FRIDGE_FACTOR, 1.0, 10000.0);
        DESALINATOR_ENERGY = builder
                .comment("Énergie (FE) que le dessalinisateur consomme à chaque tick de travail.",
                        "Bornée à ce que sa réserve reçoit par tick (100) : au-delà il ne pourrait plus tourner.")
                .defineInRange("desalinatorEnergy", DEFAULT_DESALINATOR_ENERGY, 1, DesalinatorBlockEntity.MAX_RECEIVE);
        DESALINATOR_CYCLE = builder
                .comment("Ticks de travail d'un cycle du dessalinisateur.")
                .defineInRange("desalinatorCycle", DEFAULT_DESALINATOR_CYCLE, 1, 10000);
        WATER_PER_CYCLE = builder
                .comment("Eau (mB) que consomme un cycle du dessalinisateur.")
                .defineInRange("waterPerCycle", DEFAULT_WATER_PER_CYCLE, 1, 8000);
        POTABLE_PER_CYCLE = builder
                .comment("Eau potable (mB) que produit un cycle du dessalinisateur.")
                .defineInRange("potablePerCycle", DEFAULT_POTABLE_PER_CYCLE, 1, 8000);
        CYCLES_PER_SALT = builder
                .comment("Nombre de cycles du dessalinisateur pour produire une unité de sel.")
                .defineInRange("cyclesPerSalt", DEFAULT_CYCLES_PER_SALT, 1, 10000);
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

    public static int desalinatorEnergy() {
        return read(DESALINATOR_ENERGY, DEFAULT_DESALINATOR_ENERGY);
    }

    public static int desalinatorCycle() {
        return read(DESALINATOR_CYCLE, DEFAULT_DESALINATOR_CYCLE);
    }

    public static int waterPerCycle() {
        return read(WATER_PER_CYCLE, DEFAULT_WATER_PER_CYCLE);
    }

    public static int potablePerCycle() {
        return read(POTABLE_PER_CYCLE, DEFAULT_POTABLE_PER_CYCLE);
    }

    public static int cyclesPerSalt() {
        return read(CYCLES_PER_SALT, DEFAULT_CYCLES_PER_SALT);
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

    private static int read(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
