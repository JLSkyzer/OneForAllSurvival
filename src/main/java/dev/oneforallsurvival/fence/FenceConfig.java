package dev.oneforallsurvival.fence;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de la clôture électrique. Fichier SERVER ({@code oneforallsurvival-fence.toml}) : le serveur en décide et
 * Forge le synchronise. Les lecteurs retombent sur les valeurs par défaut tant que le fichier n'est pas chargé.
 */
public final class FenceConfig {

    /** Plafond de la ligne : au plus 64 clôtures. */
    public static final int MAX_LENGTH_LIMIT = 64;
    private static final int DEFAULT_MAX_LENGTH = 64;
    private static final double DEFAULT_BASE_ENERGY = 2.0;
    private static final double DEFAULT_PER_FENCE_ENERGY = 0.25;
    private static final double DEFAULT_PER_VEGETATION_ENERGY = 1.0;
    private static final double DEFAULT_STORM_MULTIPLIER = 2.0;
    private static final double DEFAULT_MOB_DAMAGE = 2.0;
    private static final double DEFAULT_PLAYER_DAMAGE = 1.0;
    private static final double DEFAULT_SAFE_HEALTH = 4.0;
    private static final boolean DEFAULT_LETHAL_FOR_PLAYERS = false;
    private static final double DEFAULT_KNOCKBACK = 0.6;
    private static final double MAX_ENERGY_FACTOR = 1000.0;
    /** Au moins 1 FE par tick de base : à 0 (avec le reste à 0) la clôture tournerait sans courant. */
    static final double MIN_BASE_ENERGY = 1.0;
    /** Au moins un demi-point : à 0, le choc « non mortel » deviendrait mortel (il laisserait 0 point de vie). */
    static final double MIN_SAFE_HEALTH = 0.5;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue MAX_LENGTH;
    private static final ForgeConfigSpec.DoubleValue BASE_ENERGY;
    private static final ForgeConfigSpec.DoubleValue PER_FENCE_ENERGY;
    private static final ForgeConfigSpec.DoubleValue PER_VEGETATION_ENERGY;
    private static final ForgeConfigSpec.DoubleValue STORM_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue MOB_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue PLAYER_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue SAFE_HEALTH;
    private static final ForgeConfigSpec.BooleanValue LETHAL_FOR_PLAYERS;
    private static final ForgeConfigSpec.DoubleValue KNOCKBACK;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        MAX_LENGTH = builder
                .comment("Nombre maximal de clôtures qu'un électrificateur alimente : les plus proches de lui.")
                .defineInRange("maxLength", DEFAULT_MAX_LENGTH, 1, MAX_LENGTH_LIMIT);
        BASE_ENERGY = builder
                .comment("Énergie (FE) par tick que coûte un électrificateur dont la ligne n'est pas vide (au moins 1).")
                .defineInRange("baseEnergy", DEFAULT_BASE_ENERGY, MIN_BASE_ENERGY, MAX_ENERGY_FACTOR);
        PER_FENCE_ENERGY = builder
                .comment("Énergie (FE) par tick que coûte chaque clôture de la ligne.")
                .defineInRange("perFenceEnergy", DEFAULT_PER_FENCE_ENERGY, 0.0, MAX_ENERGY_FACTOR);
        PER_VEGETATION_ENERGY = builder
                .comment("Énergie (FE) par tick que coûte chaque bloc de végétation qui touche la ligne",
                        "(tag oneforallsurvival:fence_vegetation) : l'herbe et les feuilles fuient.")
                .defineInRange("perVegetationEnergy", DEFAULT_PER_VEGETATION_ENERGY, 0.0, MAX_ENERGY_FACTOR);
        STORM_MULTIPLIER = builder
                .comment("Multiplicateur de la consommation pendant un orage (l'humidité fuit).")
                .defineInRange("stormMultiplier", DEFAULT_STORM_MULTIPLIER, 1.0, MAX_ENERGY_FACTOR);
        MOB_DAMAGE = builder
                .comment("Points de vie (un cœur = 2) qu'un choc retire à un monstre ; aux autres vivants",
                        "sans les tuer (voir safeHealth).")
                .defineInRange("mobDamage", DEFAULT_MOB_DAMAGE, 0.0, 1000.0);
        PLAYER_DAMAGE = builder
                .comment("Points de vie qu'un choc retire à un joueur.")
                .defineInRange("playerDamage", DEFAULT_PLAYER_DAMAGE, 0.0, 1000.0);
        SAFE_HEALTH = builder
                .comment("Points de vie sous lesquels un choc n'achève pas ce qui ne doit pas mourir de la clôture",
                        "(joueurs si lethalForPlayers est faux, animaux et autres vivants non hostiles).")
                .defineInRange("safeHealth", DEFAULT_SAFE_HEALTH, MIN_SAFE_HEALTH, 1000.0);
        LETHAL_FOR_PLAYERS = builder
                .comment("Vrai : un joueur peut mourir de la clôture.")
                .define("lethalForPlayers", DEFAULT_LETHAL_FOR_PLAYERS);
        KNOCKBACK = builder
                .comment("Force du repoussement à chaque contact, avec ou sans dégât.")
                .defineInRange("knockback", DEFAULT_KNOCKBACK, 0.0, 10.0);
        SPEC = builder.build();
    }

    private FenceConfig() {}

    public static int maxLength() {
        return read(MAX_LENGTH, DEFAULT_MAX_LENGTH);
    }

    public static double baseEnergy() {
        return read(BASE_ENERGY, DEFAULT_BASE_ENERGY);
    }

    public static double perFenceEnergy() {
        return read(PER_FENCE_ENERGY, DEFAULT_PER_FENCE_ENERGY);
    }

    public static double perVegetationEnergy() {
        return read(PER_VEGETATION_ENERGY, DEFAULT_PER_VEGETATION_ENERGY);
    }

    public static double stormMultiplier() {
        return read(STORM_MULTIPLIER, DEFAULT_STORM_MULTIPLIER);
    }

    public static double mobDamage() {
        return read(MOB_DAMAGE, DEFAULT_MOB_DAMAGE);
    }

    public static double playerDamage() {
        return read(PLAYER_DAMAGE, DEFAULT_PLAYER_DAMAGE);
    }

    public static double safeHealth() {
        return read(SAFE_HEALTH, DEFAULT_SAFE_HEALTH);
    }

    public static boolean lethalForPlayers() {
        try {
            return LETHAL_FOR_PLAYERS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_LETHAL_FOR_PLAYERS;
        }
    }

    public static double knockback() {
        return read(KNOCKBACK, DEFAULT_KNOCKBACK);
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
