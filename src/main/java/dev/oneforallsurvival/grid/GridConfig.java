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
    private static final int DEFAULT_LIGHTNING_RADIUS = 16;
    private static final double DEFAULT_LIGHTNING_DAMAGE = 25.0;
    private static final double DEFAULT_LIGHTNING_FUSE_CHANCE = 0.25;
    private static final int DEFAULT_ARRESTER_RADIUS = 24;
    private static final double DEFAULT_ARRESTER_WEAR_PER_STRIKE = 10.0;
    /** Borne haute des rayons de la foudre : la recherche reste de quelques chunks. */
    private static final int MAX_RADIUS = 64;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue SHEDDER_MAX_PER_TICK;
    private static final ForgeConfigSpec.IntValue METER_MAX_PER_TICK;
    private static final ForgeConfigSpec.IntValue BREAKER_MAX_PER_TICK;
    private static final ForgeConfigSpec.IntValue LIGHTNING_RADIUS;
    private static final ForgeConfigSpec.DoubleValue LIGHTNING_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue LIGHTNING_FUSE_CHANCE;
    private static final ForgeConfigSpec.IntValue ARRESTER_RADIUS;
    private static final ForgeConfigSpec.DoubleValue ARRESTER_WEAR_PER_STRIKE;

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
        LIGHTNING_RADIUS = builder
                .comment("Distance (blocs) jusqu'à laquelle un éclair abîme les machines du mod.")
                .defineInRange("lightningRadius", DEFAULT_LIGHTNING_RADIUS, 0, MAX_RADIUS);
        LIGHTNING_DAMAGE = builder
                .comment("Points d'état (sur 100) qu'un éclair retire à une machine non protégée.")
                .defineInRange("lightningDamage", DEFAULT_LIGHTNING_DAMAGE, 0.0, 100.0);
        LIGHTNING_FUSE_CHANCE = builder
                .comment("Risque (0 à 1) qu'un éclair grille le fusible d'une machine non protégée : panne immédiate.")
                .defineInRange("lightningFuseChance", DEFAULT_LIGHTNING_FUSE_CHANCE, 0.0, 1.0);
        ARRESTER_RADIUS = builder
                .comment("Distance (blocs) jusqu'à laquelle un parafoudre en état protège une machine.")
                .defineInRange("arresterRadius", DEFAULT_ARRESTER_RADIUS, 0, MAX_RADIUS);
        ARRESTER_WEAR_PER_STRIKE = builder
                .comment("Points d'état (sur 100) qu'un parafoudre perd à chaque éclair encaissé, une seule fois par éclair.")
                .defineInRange("arresterWearPerStrike", DEFAULT_ARRESTER_WEAR_PER_STRIKE, 0.0, 100.0);
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

    public static int lightningRadius() {
        return read(LIGHTNING_RADIUS, DEFAULT_LIGHTNING_RADIUS);
    }

    public static double lightningDamage() {
        return read(LIGHTNING_DAMAGE, DEFAULT_LIGHTNING_DAMAGE);
    }

    public static double lightningFuseChance() {
        return read(LIGHTNING_FUSE_CHANCE, DEFAULT_LIGHTNING_FUSE_CHANCE);
    }

    /**
     * Change le risque de fusible en mémoire, sans écrire le fichier : pour les essais, qui remettent l'ancienne
     * valeur ensuite. Exige le fichier chargé.
     */
    public static void setLightningFuseChance(double chance) {
        LIGHTNING_FUSE_CHANCE.set(chance);
    }

    public static int arresterRadius() {
        return read(ARRESTER_RADIUS, DEFAULT_ARRESTER_RADIUS);
    }

    public static double arresterWearPerStrike() {
        return read(ARRESTER_WEAR_PER_STRIKE, DEFAULT_ARRESTER_WEAR_PER_STRIKE);
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
