package dev.oneforallsurvival.food;

import java.util.List;
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
    private static final int DEFAULT_DRYER_ENERGY = 20;
    private static final List<String> DEFAULT_INCUBATOR_EGGS =
            List.of("minecraft:egg=minecraft:chicken", "minecraft:turtle_egg=minecraft:turtle");
    private static final int DEFAULT_INCUBATOR_HATCH_MINUTES = 20;
    private static final int DEFAULT_INCUBATOR_OUTAGE_SECONDS = 60;
    private static final int DEFAULT_INCUBATOR_ENERGY = 8;

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
    private static final ForgeConfigSpec.IntValue DRYER_ENERGY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> INCUBATOR_EGGS;
    private static final ForgeConfigSpec.IntValue INCUBATOR_HATCH_MINUTES;
    private static final ForgeConfigSpec.IntValue INCUBATOR_OUTAGE_SECONDS;
    private static final ForgeConfigSpec.IntValue INCUBATOR_ENERGY;

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
        DRYER_ENERGY = builder
                .comment("Énergie (FE) que le séchoir consomme à chaque tick de travail.",
                        "Bornée à ce que sa réserve reçoit par tick (100) : au-delà il ne pourrait plus tourner.")
                .defineInRange("dryerEnergy", DEFAULT_DRYER_ENERGY, 1, DryerBlockEntity.MAX_RECEIVE);
        INCUBATOR_EGGS = builder
                .comment("Les œufs que la couveuse fait éclore, une entrée « objet=entité » chacune.",
                        "L'animal naît petit. Une entrée mal formée ou inconnue est ignorée (et notée dans le journal).")
                .defineList("incubatorEggs", DEFAULT_INCUBATOR_EGGS, entry -> entry instanceof String);
        INCUBATOR_HATCH_MINUTES = builder
                .comment("Minutes de marche (couveuse alimentée) qu'il faut à chaque œuf pour éclore.",
                        "Bornée à 27 minutes : la durée voyage au menu dans un entier de 16 bits.")
                .defineInRange("incubatorHatchMinutes", DEFAULT_INCUBATOR_HATCH_MINUTES, 1, 27);
        INCUBATOR_OUTAGE_SECONDS = builder
                .comment("Secondes sans courant (réserve de la couveuse vide) après lesquelles la couvée est perdue.",
                        "La couveuse est la seule machine qu'une coupure prive de son contenu.")
                .defineInRange("incubatorOutageSeconds", DEFAULT_INCUBATOR_OUTAGE_SECONDS, 1, 600);
        INCUBATOR_ENERGY = builder
                .comment("Énergie (FE) que la couveuse consomme à chaque tick de travail, quel que soit le nombre d'œufs.",
                        "Bornée à ce que sa réserve reçoit par tick (40) : au-delà elle ne pourrait plus tourner.")
                .defineInRange("incubatorEnergy", DEFAULT_INCUBATOR_ENERGY, 1, IncubatorBlockEntity.MAX_RECEIVE);
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

    public static int dryerEnergy() {
        return read(DRYER_ENERGY, DEFAULT_DRYER_ENERGY);
    }

    /** La liste de config telle quelle : {@link IncubatorEggs} s'appuie sur son identité pour son cache. */
    public static List<? extends String> incubatorEggs() {
        try {
            return INCUBATOR_EGGS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_INCUBATOR_EGGS;
        }
    }

    public static int incubatorHatchTicks() {
        return read(INCUBATOR_HATCH_MINUTES, DEFAULT_INCUBATOR_HATCH_MINUTES) * 60 * 20;
    }

    public static int incubatorOutageTicks() {
        return read(INCUBATOR_OUTAGE_SECONDS, DEFAULT_INCUBATOR_OUTAGE_SECONDS) * 20;
    }

    public static int incubatorEnergy() {
        return read(INCUBATOR_ENERGY, DEFAULT_INCUBATOR_ENERGY);
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
