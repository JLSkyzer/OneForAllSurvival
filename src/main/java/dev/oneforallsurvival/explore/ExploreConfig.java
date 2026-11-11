package dev.oneforallsurvival.explore;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de l'exploration. Fichier SERVER ({@code oneforallsurvival-explore.toml}) : le serveur en décide et Forge
 * le synchronise. Les lecteurs retombent sur les valeurs par défaut tant que le fichier n'est pas chargé.
 */
public final class ExploreConfig {

    private static final int DEFAULT_CACHE_LIST_MAX = 50;

    public static final ForgeConfigSpec SPEC;

    private static final int DEFAULT_DETECTOR_CAPACITY = 24000;
    private static final int DEFAULT_DETECTOR_ENERGY = 2;
    private static final int DEFAULT_DETECTOR_RANGE = 24;
    private static final int DEFAULT_HEADLAMP_CAPACITY = 36000;
    private static final int DEFAULT_HEADLAMP_ENERGY = 2;
    private static final int DEFAULT_HEADLAMP_LIGHT = 14;
    private static final int DEFAULT_RECEIVER_ENERGY = 4;

    private static final ForgeConfigSpec.IntValue CACHE_LIST_MAX;
    private static final ForgeConfigSpec.IntValue DETECTOR_CAPACITY;
    private static final ForgeConfigSpec.IntValue DETECTOR_ENERGY;
    private static final ForgeConfigSpec.IntValue DETECTOR_RANGE;
    private static final ForgeConfigSpec.IntValue HEADLAMP_CAPACITY;
    private static final ForgeConfigSpec.IntValue HEADLAMP_ENERGY;
    private static final ForgeConfigSpec.IntValue HEADLAMP_LIGHT;
    private static final ForgeConfigSpec.IntValue RECEIVER_ENERGY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CACHE_LIST_MAX = builder
                .comment("Nombre maximal de positions que /cache list affiche (les plus proches de la source d'abord).")
                .defineInRange("cacheListMax", DEFAULT_CACHE_LIST_MAX, 1, 1000);
        DETECTOR_CAPACITY = builder
                .comment("Capacité de la batterie du détecteur de métaux, en FE.")
                .defineInRange("detectorCapacity", DEFAULT_DETECTOR_CAPACITY, 1, 10000000);
        DETECTOR_ENERGY = builder
                .comment("FE que le détecteur consomme à chaque tick où il est tenu en main.")
                .defineInRange("detectorEnergy", DEFAULT_DETECTOR_ENERGY, 1, 100000);
        DETECTOR_RANGE = builder
                .comment("Portée du détecteur, en blocs (distance réelle).")
                .defineInRange("detectorRange", DEFAULT_DETECTOR_RANGE, 1, 256);
        HEADLAMP_CAPACITY = builder
                .comment("Capacité de la batterie de la lampe frontale, en FE.")
                .defineInRange("headlampCapacity", DEFAULT_HEADLAMP_CAPACITY, 1, 10000000);
        HEADLAMP_ENERGY = builder
                .comment("FE que la lampe frontale consomme à chaque tick où elle est portée et allumée.")
                .defineInRange("headlampEnergy", DEFAULT_HEADLAMP_ENERGY, 1, 100000);
        HEADLAMP_LIGHT = builder
                .comment("Niveau de lumière du bloc invisible qui suit la tête du porteur (1 à 15).")
                .defineInRange("headlampLight", DEFAULT_HEADLAMP_LIGHT, 1, 15);
        RECEIVER_ENERGY = builder
                .comment("FE que le récepteur de signaux consomme à chaque tick pour écouter (sa réserve en tient 2000).")
                .defineInRange("receiverEnergy", DEFAULT_RECEIVER_ENERGY, 1, 2000);
        SPEC = builder.build();
    }

    private ExploreConfig() {}

    public static int cacheListMax() {
        try {
            return CACHE_LIST_MAX.get();
        } catch (IllegalStateException e) {
            return DEFAULT_CACHE_LIST_MAX;
        }
    }

    public static int detectorCapacity() {
        try {
            return DETECTOR_CAPACITY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_DETECTOR_CAPACITY;
        }
    }

    public static int detectorEnergy() {
        try {
            return DETECTOR_ENERGY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_DETECTOR_ENERGY;
        }
    }

    public static int detectorRange() {
        try {
            return DETECTOR_RANGE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_DETECTOR_RANGE;
        }
    }

    public static int headlampCapacity() {
        try {
            return HEADLAMP_CAPACITY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_HEADLAMP_CAPACITY;
        }
    }

    public static int headlampEnergy() {
        try {
            return HEADLAMP_ENERGY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_HEADLAMP_ENERGY;
        }
    }

    public static int headlampLight() {
        try {
            return HEADLAMP_LIGHT.get();
        } catch (IllegalStateException e) {
            return DEFAULT_HEADLAMP_LIGHT;
        }
    }

    public static int receiverEnergy() {
        try {
            return RECEIVER_ENERGY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_RECEIVER_ENERGY;
        }
    }
}
