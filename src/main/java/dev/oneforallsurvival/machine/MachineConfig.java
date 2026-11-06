package dev.oneforallsurvival.machine;

import net.minecraftforge.common.ForgeConfigSpec;

/** Réglages de l'usure et des pannes des machines. Fichier COMMON, comme {@code BenchConfig}. */
public final class MachineConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue LIFETIME_HOURS;
    private static final ForgeConfigSpec.DoubleValue REDUCED_EFFICIENCY;
    private static final ForgeConfigSpec.DoubleValue COASTAL_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue FLAMES_ENABLED;
    private static final ForgeConfigSpec.DoubleValue FLAME_CHANCE;
    private static final ForgeConfigSpec.IntValue FLAME_TICKS;
    private static final ForgeConfigSpec.IntValue WELDER_CAPACITY;
    private static final ForgeConfigSpec.IntValue REPAIR_ENERGY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        LIFETIME_HOURS = builder
                .comment("Heures de fonctionnement d'une machine neuve avant la panne.")
                .defineInRange("lifetimeHours", 12.0, 0.01, 100000.0);
        REDUCED_EFFICIENCY = builder
                .comment("Rendement d'une machine dont l'état est tombé sous 50 % (1.0 = plein rendement).")
                .defineInRange("reducedEfficiency", 0.75, 0.01, 1.0);
        COASTAL_MULTIPLIER = builder
                .comment("Multiplicateur d'usure en milieu côtier, pour une machine non protégée.")
                .defineInRange("coastalMultiplier", 2.0, 1.0, 100.0);
        FLAMES_ENABLED = builder
                .comment("Une machine qui tombe en panne peut-elle cracher des flammes (visuelles, sans dégât) ?")
                .define("flamesEnabled", true);
        FLAME_CHANCE = builder
                .comment("Probabilité de flammes à la panne.")
                .defineInRange("flameChance", 0.5, 0.0, 1.0);
        FLAME_TICKS = builder
                .comment("Durée des flammes, en ticks.")
                .defineInRange("flameTicks", 200, 1, 72000);
        WELDER_CAPACITY = builder
                .comment("Capacité de la batterie du poste à souder, en FE.")
                .defineInRange("welderCapacity", 2000, 1, 1000000);
        REPAIR_ENERGY = builder
                .comment("Énergie que coûte une réparation au poste à souder, en FE.")
                .defineInRange("repairEnergy", 400, 1, 1000000);
        SPEC = builder.build();
    }

    private MachineConfig() {}

    /** Durée de vie d'une machine neuve, en ticks de travail. */
    public static double lifetimeTicks() {
        try {
            return LIFETIME_HOURS.get() * 3600.0 * 20.0;
        } catch (IllegalStateException e) {
            return 12.0 * 3600.0 * 20.0;
        }
    }

    public static double reducedEfficiency() {
        try {
            return REDUCED_EFFICIENCY.get();
        } catch (IllegalStateException e) {
            return 0.75;
        }
    }

    public static double coastalMultiplier() {
        try {
            return COASTAL_MULTIPLIER.get();
        } catch (IllegalStateException e) {
            return 2.0;
        }
    }

    public static boolean flamesEnabled() {
        try {
            return FLAMES_ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static double flameChance() {
        try {
            return FLAME_CHANCE.get();
        } catch (IllegalStateException e) {
            return 0.5;
        }
    }

    public static int flameTicks() {
        try {
            return FLAME_TICKS.get();
        } catch (IllegalStateException e) {
            return 200;
        }
    }

    public static int welderCapacity() {
        try {
            return WELDER_CAPACITY.get();
        } catch (IllegalStateException e) {
            return 2000;
        }
    }

    public static int repairEnergy() {
        try {
            return REPAIR_ENERGY.get();
        } catch (IllegalStateException e) {
            return 400;
        }
    }
}
