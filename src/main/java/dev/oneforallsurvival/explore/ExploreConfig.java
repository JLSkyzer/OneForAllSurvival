package dev.oneforallsurvival.explore;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de l'exploration. Fichier SERVER ({@code oneforallsurvival-explore.toml}) : le serveur en décide et Forge
 * le synchronise. Les lecteurs retombent sur les valeurs par défaut tant que le fichier n'est pas chargé.
 */
public final class ExploreConfig {

    private static final int DEFAULT_CACHE_LIST_MAX = 50;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue CACHE_LIST_MAX;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CACHE_LIST_MAX = builder
                .comment("Nombre maximal de positions que /cache list affiche (les plus proches de la source d'abord).")
                .defineInRange("cacheListMax", DEFAULT_CACHE_LIST_MAX, 1, 1000);
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
}
