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
