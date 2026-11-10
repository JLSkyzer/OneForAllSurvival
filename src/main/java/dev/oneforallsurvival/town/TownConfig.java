package dev.oneforallsurvival.town;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de la vie de la ville. Fichier SERVER ({@code oneforallsurvival-town.toml}) : le serveur en décide et
 * Forge le synchronise. Les lecteurs retombent sur les valeurs par défaut tant que le fichier n'est pas chargé
 * (aucun monde, ou JUnit).
 */
public final class TownConfig {

    private static final int DEFAULT_HEATER_MB_PER_TICK = 5;
    private static final int DEFAULT_HEATER_ENERGY = 40;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue HEATER_MB_PER_TICK;
    private static final ForgeConfigSpec.IntValue HEATER_ENERGY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        HEATER_MB_PER_TICK = builder
                .comment("Eau potable (mB) que le chauffe-eau change en eau chaude à chaque tick de travail.")
                .defineInRange("heaterMbPerTick", DEFAULT_HEATER_MB_PER_TICK, 1, 100);
        HEATER_ENERGY = builder
                .comment("Énergie (FE) que le chauffe-eau consomme à chaque tick de travail.",
                        "Bornée à ce que sa réserve reçoit par tick (200) : au-delà il ne pourrait plus tourner.")
                .defineInRange("heaterEnergy", DEFAULT_HEATER_ENERGY, 1, WaterHeaterBlockEntity.MAX_RECEIVE);
        SPEC = builder.build();
    }

    private TownConfig() {}

    public static int heaterMbPerTick() {
        return read(HEATER_MB_PER_TICK, DEFAULT_HEATER_MB_PER_TICK);
    }

    public static int heaterEnergy() {
        return read(HEATER_ENERGY, DEFAULT_HEATER_ENERGY);
    }

    /** Lit une valeur ; repli sur le défaut tant que le fichier n'est pas chargé. */
    private static int read(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
