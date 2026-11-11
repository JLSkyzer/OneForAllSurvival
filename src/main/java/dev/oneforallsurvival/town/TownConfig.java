package dev.oneforallsurvival.town;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de la vie de la ville. Fichier SERVER ({@code oneforallsurvival-town.toml}) : le serveur en décide et
 * Forge le synchronise. Les lecteurs retombent sur les valeurs par défaut tant que le fichier n'est pas chargé
 * (aucun monde, ou JUnit).
 */
public final class TownConfig {

    private static final int DEFAULT_HEATER_MB_PER_TICK = 5;
    private static final int DEFAULT_HEATER_ENERGY = 40;

    private static final double DEFAULT_WELL_BEING_SPEED = 0.05;
    private static final double DEFAULT_WELL_BEING_MINING = 0.10;
    private static final int DEFAULT_SHOWER_SECONDS = 5;
    private static final int DEFAULT_SHOWER_MB_PER_TICK = 2;
    private static final int DEFAULT_SHOWER_WELL_BEING_MINUTES = 6;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue HEATER_MB_PER_TICK;
    private static final ForgeConfigSpec.IntValue HEATER_ENERGY;
    private static final ForgeConfigSpec.DoubleValue WELL_BEING_SPEED;
    private static final ForgeConfigSpec.DoubleValue WELL_BEING_MINING;
    private static final ForgeConfigSpec.IntValue SHOWER_SECONDS;
    private static final ForgeConfigSpec.IntValue SHOWER_MB_PER_TICK;
    private static final ForgeConfigSpec.IntValue SHOWER_WELL_BEING_MINUTES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        HEATER_MB_PER_TICK = builder
                .comment("Eau potable (mB) que le chauffe-eau change en eau chaude à chaque tick de travail.")
                .defineInRange("heaterMbPerTick", DEFAULT_HEATER_MB_PER_TICK, 1, 100);
        HEATER_ENERGY = builder
                .comment("Énergie (FE) que le chauffe-eau consomme à chaque tick de travail.",
                        "Bornée à ce que sa réserve reçoit par tick (200) : au-delà il ne pourrait plus tourner.")
                .defineInRange("heaterEnergy", DEFAULT_HEATER_ENERGY, 1, ElectricMachineBlockEntity.TOWN_MAX_RECEIVE);
        WELL_BEING_SPEED = builder
                .comment("Bonus de vitesse de marche du Bien-être (0,05 = +5 %).")
                .defineInRange("wellBeingSpeedBonus", DEFAULT_WELL_BEING_SPEED, 0.0, 1.0);
        WELL_BEING_MINING = builder
                .comment("Bonus de vitesse de minage du Bien-être (0,10 = +10 %).")
                .defineInRange("wellBeingMiningBonus", DEFAULT_WELL_BEING_MINING, 0.0, 1.0);
        SHOWER_SECONDS = builder
                .comment("Secondes continues d'eau chaude sous la pomme avant d'obtenir le Bien-être.")
                .defineInRange("showerSeconds", DEFAULT_SHOWER_SECONDS, 1, 60);
        SHOWER_MB_PER_TICK = builder
                .comment("Eau chaude (mB) que la pomme de douche consomme par tick d'écoulement (×20 par seconde).")
                .defineInRange("showerMbPerTick", DEFAULT_SHOWER_MB_PER_TICK, 1, 50);
        SHOWER_WELL_BEING_MINUTES = builder
                .comment("Durée (minutes) du Bien-être donné par une douche. Une douche de plus ne la cumule pas.")
                .defineInRange("showerWellBeingMinutes", DEFAULT_SHOWER_WELL_BEING_MINUTES, 1, 60);
        SPEC = builder.build();
    }

    private TownConfig() {}

    public static int heaterMbPerTick() {
        return read(HEATER_MB_PER_TICK, DEFAULT_HEATER_MB_PER_TICK);
    }

    public static int heaterEnergy() {
        return read(HEATER_ENERGY, DEFAULT_HEATER_ENERGY);
    }

    public static double wellBeingSpeedBonus() {
        return read(WELL_BEING_SPEED, DEFAULT_WELL_BEING_SPEED);
    }

    public static double wellBeingMiningBonus() {
        return read(WELL_BEING_MINING, DEFAULT_WELL_BEING_MINING);
    }

    public static int showerSeconds() {
        return read(SHOWER_SECONDS, DEFAULT_SHOWER_SECONDS);
    }

    public static int showerMbPerTick() {
        return read(SHOWER_MB_PER_TICK, DEFAULT_SHOWER_MB_PER_TICK);
    }

    public static int showerWellBeingMinutes() {
        return read(SHOWER_WELL_BEING_MINUTES, DEFAULT_SHOWER_WELL_BEING_MINUTES);
    }

    /** Lit une valeur ; repli sur le défaut tant que le fichier n'est pas chargé. */
    private static int read(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private static double read(ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
