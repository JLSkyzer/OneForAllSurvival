package dev.oneforallsurvival.light;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de l'éclairage de la ville. Fichier SERVER ({@code oneforallsurvival-light.toml}) : le serveur en décide et
 * Forge le synchronise. Les lecteurs retombent sur les valeurs par défaut tant que le fichier n'est pas chargé.
 */
public final class LightConfig {

    /** Ce que la réserve d'une machine du paquet reçoit par tick : la consommation ne peut pas la dépasser. */
    public static final int MAX_MACHINE_RECEIVE = 200;
    private static final int DEFAULT_LIGHTHOUSE_ENERGY = 32;
    private static final int DEFAULT_LIGHTHOUSE_MIN_ABOVE_SEA = 8;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue LIGHTHOUSE_ENERGY;
    private static final ForgeConfigSpec.IntValue LIGHTHOUSE_MIN_ABOVE_SEA;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        LIGHTHOUSE_ENERGY = builder
                .comment("Énergie (FE) que le phare consomme à chaque tick de travail.",
                        "Bornée à ce que sa réserve reçoit par tick (200) : au-delà il ne pourrait plus tourner.")
                .defineInRange("lighthouseEnergy", DEFAULT_LIGHTHOUSE_ENERGY, 1, MAX_MACHINE_RECEIVE);
        LIGHTHOUSE_MIN_ABOVE_SEA = builder
                .comment("Hauteur minimale (blocs) du phare au-dessus du niveau de la mer",
                        "(oneforallsurvival-machines.toml, seaLevel) pour qu'il marche ; il doit aussi voir le ciel.")
                .defineInRange("lighthouseMinAboveSea", DEFAULT_LIGHTHOUSE_MIN_ABOVE_SEA, 0, 384);
        SPEC = builder.build();
    }

    private LightConfig() {}

    public static int lighthouseEnergy() {
        try {
            return LIGHTHOUSE_ENERGY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_LIGHTHOUSE_ENERGY;
        }
    }

    public static int lighthouseMinAboveSea() {
        try {
            return LIGHTHOUSE_MIN_ABOVE_SEA.get();
        } catch (IllegalStateException e) {
            return DEFAULT_LIGHTHOUSE_MIN_ABOVE_SEA;
        }
    }
}
