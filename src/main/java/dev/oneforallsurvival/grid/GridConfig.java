package dev.oneforallsurvival.grid;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de la gestion du réseau. Fichier SERVER ({@code oneforallsurvival-grid.toml}) : le serveur en décide et Forge
 * le synchronise vers les clients. Les lecteurs retombent sur les valeurs par défaut tant que le fichier n'est pas
 * chargé (aucun monde, ou JUnit).
 */
public final class GridConfig {

    private static final int DEFAULT_SHEDDER_MAX_PER_TICK = 4096;
    private static final int DEFAULT_METER_MAX_PER_TICK = 4096;
    private static final int DEFAULT_BREAKER_MAX_PER_TICK = 4096;
    /** Borne haute des débits : la somme d'un tick tient dans un {@code int}. */
    private static final int MAX_FLOW = 1_000_000_000;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue SHEDDER_MAX_PER_TICK;
    private static final ForgeConfigSpec.IntValue METER_MAX_PER_TICK;
    private static final ForgeConfigSpec.IntValue BREAKER_MAX_PER_TICK;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SHEDDER_MAX_PER_TICK = builder
                .comment("Énergie (FE) qu'un tableau de délestage neuf laisse passer au plus par tick, toutes sorties confondues.",
                        "Un tableau usé sous 50 % n'en laisse passer que la part de son rendement.")
                .defineInRange("shedderMaxPerTick", DEFAULT_SHEDDER_MAX_PER_TICK, 1, MAX_FLOW);
        METER_MAX_PER_TICK = builder
                .comment("Énergie (FE) qu'un compteur laisse passer au plus par tick.")
                .defineInRange("meterMaxPerTick", DEFAULT_METER_MAX_PER_TICK, 1, MAX_FLOW);
        BREAKER_MAX_PER_TICK = builder
                .comment("Énergie (FE) qu'un disjoncteur fermé laisse passer au plus par tick.")
                .defineInRange("breakerMaxPerTick", DEFAULT_BREAKER_MAX_PER_TICK, 1, MAX_FLOW);
        SPEC = builder.build();
    }

    private GridConfig() {}

    public static int shedderMaxPerTick() {
        return read(SHEDDER_MAX_PER_TICK, DEFAULT_SHEDDER_MAX_PER_TICK);
    }

    public static int meterMaxPerTick() {
        return read(METER_MAX_PER_TICK, DEFAULT_METER_MAX_PER_TICK);
    }

    public static int breakerMaxPerTick() {
        return read(BREAKER_MAX_PER_TICK, DEFAULT_BREAKER_MAX_PER_TICK);
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
