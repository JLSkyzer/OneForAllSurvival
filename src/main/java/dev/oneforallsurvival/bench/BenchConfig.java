package dev.oneforallsurvival.bench;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de l'établi. Fichier COMMON, car le catalogue se construit au chargement des données,
 * avant que les configurations SERVER existent.
 */
public final class BenchConfig {

    private static final List<String> DEFAULT_MODS = List.of("create", "createaddition", "create_new_age",
            "light_bulb_addon_create_crafts_and__addions", "oneforallsurvival");
    private static final List<String> DEFAULT_TABLE_ALLOWED = List.of("oneforallsurvival:engineer_bench");

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MANAGED_MODS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_ITEMS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TABLE_ALLOWED_ITEMS;
    private static final ForgeConfigSpec.IntValue BASE_SECONDS;
    private static final ForgeConfigSpec.IntValue SECONDS_PER_INGREDIENT;
    private static final ForgeConfigSpec.IntValue MAX_SECONDS;
    private static final ForgeConfigSpec.DoubleValue MANUAL_SPEED;
    private static final ForgeConfigSpec.DoubleValue POWERED_SPEED;
    private static final ForgeConfigSpec.IntValue MAX_QUEUE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        MANAGED_MODS = builder
                .comment("Mods dont les recettes de table, de tailleur de pierre et de forgeron passent par l'établi.")
                .defineListAllowEmpty("managedMods", DEFAULT_MODS, o -> o instanceof String);
        EXCLUDED_ITEMS = builder
                .comment("Objets (modid:objet) que l'établi refuse de fabriquer, par exemple les réacteurs.")
                .defineListAllowEmpty("excludedItems", List.of(), o -> o instanceof String);
        TABLE_ALLOWED_ITEMS = builder
                .comment("Objets (modid:objet) dont la recette reste sur la table, malgré leur mod.")
                .defineListAllowEmpty("tableAllowedItems", DEFAULT_TABLE_ALLOWED, o -> o instanceof String);
        BASE_SECONDS = builder
                .comment("Durée de base d'une fabrication à l'établi, en secondes.")
                .defineInRange("baseSeconds", 4, 0, 3600);
        SECONDS_PER_INGREDIENT = builder
                .comment("Secondes ajoutées par ingrédient (la rareté et la complexité s'y ajoutent).")
                .defineInRange("secondsPerIngredient", 1, 0, 600);
        MAX_SECONDS = builder
                .comment("Plafond de la durée calculée, en secondes. Une surcharge de données peut le dépasser.")
                .defineInRange("maxSeconds", 600, 1, 86400);
        MANUAL_SPEED = builder
                .comment("Vitesse de fabrication d'un établi actionné à la main (1.0 = temps normal).")
                .defineInRange("manualSpeed", 1.0, 0.01, 100.0);
        POWERED_SPEED = builder
                .comment("Vitesse de fabrication d'un établi alimenté en électricité.")
                .defineInRange("poweredSpeed", 4.0, 0.01, 100.0);
        MAX_QUEUE = builder
                .comment("Nombre maximal de fabrications dans la file d'un joueur.")
                .defineInRange("maxQueue", 64, 1, 10000);
        SPEC = builder.build();
    }

    private BenchConfig() {}

    public static Set<String> managedMods() {
        return new HashSet<>(read(MANAGED_MODS, DEFAULT_MODS));
    }

    public static Set<ResourceLocation> excludedItems() {
        return ids(read(EXCLUDED_ITEMS, List.of()));
    }

    public static Set<ResourceLocation> tableAllowedItems() {
        return ids(read(TABLE_ALLOWED_ITEMS, DEFAULT_TABLE_ALLOWED));
    }

    public static BenchTimes.Params timeParams() {
        try {
            return new BenchTimes.Params(BASE_SECONDS.get(), SECONDS_PER_INGREDIENT.get(), MAX_SECONDS.get());
        } catch (IllegalStateException e) {
            return new BenchTimes.Params(4, 1, 600);
        }
    }

    public static double manualSpeed() {
        try {
            return MANUAL_SPEED.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    public static double poweredSpeed() {
        try {
            return POWERED_SPEED.get();
        } catch (IllegalStateException e) {
            return 4.0;
        }
    }

    public static int maxQueue() {
        try {
            return MAX_QUEUE.get();
        } catch (IllegalStateException e) {
            return 64;
        }
    }

    /** Lit une valeur ; repli sur le défaut tant que le fichier n'est pas chargé. */
    private static List<? extends String> read(ForgeConfigSpec.ConfigValue<List<? extends String>> value,
            List<String> fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    private static Set<ResourceLocation> ids(List<? extends String> raw) {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : raw) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }
}
