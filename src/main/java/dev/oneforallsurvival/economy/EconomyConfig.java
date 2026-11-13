package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

/**
 * Réglages de l'économie. Fichier SERVER ({@code oneforallsurvival-economy.toml}) : le serveur en décide et
 * Forge le synchronise, pour que l'infobulle suive. Les valeurs validées sont mises en cache au chargement et au
 * rechargement du fichier ; tant qu'il n'est pas chargé (aucun monde, ou JUnit), ce sont les valeurs par défaut.
 *
 * <p>Le cuivre vaut toujours 1 : il n'est pas réglable. Chaque autre pièce est un multiple de celle du dessous.
 */
public final class EconomyConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cuivre, argent, or, émeraude, rubis. */
    static final long[] DEFAULTS = {1L, 10L, 100L, 1_000L, 10_000L};

    public static final ForgeConfigSpec SPEC;

    /** Indexé comme {@link Coin#ordinal()} ; l'entrée du cuivre reste nulle (non réglable). */
    private static final ForgeConfigSpec.LongValue[] VALUES = new ForgeConfigSpec.LongValue[Coin.values().length];

    static final int DEFAULT_LEDGER_MAX_LINES = 10_000;
    static final int DEFAULT_STARTING_MONEY = 500;

    private static final ForgeConfigSpec.IntValue LEDGER_MAX_LINES;
    private static final ForgeConfigSpec.IntValue STARTING_MONEY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_ALLOWED_MENUS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_FORBIDDEN_MENUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Valeur de chaque pièce, en cuivres. Le cuivre vaut toujours 1.",
                "Chaque valeur doit être un multiple strict de celle de la pièce du dessous, sinon les valeurs",
                "par défaut (1, 10, 100, 1000, 10000) sont utilisées et un avertissement est écrit au journal.",
                "Le change à la table (1 pièce pour 10 de celle du dessous) n'est actif que si chaque pièce vaut 10 fois",
                "celle du dessous ; sinon ses recettes sont retirées.");
        for (Coin coin : Coin.values()) {
            if (coin != Coin.COPPER) {
                VALUES[coin.ordinal()] = builder.defineInRange(coin.key() + "CoinValue", DEFAULTS[coin.ordinal()],
                        2L, 1_000_000_000L);
            }
        }
        LEDGER_MAX_LINES = builder
                .comment("Lignes du registre gardées ; les plus anciennes sont effacées d'abord, mais les totaux",
                        "par source et par cause ne sont jamais effacés.")
                .defineInRange("ledgerMaxLines", DEFAULT_LEDGER_MAX_LINES, 100, 100_000);
        STARTING_MONEY = builder
                .comment("Somme de départ, en cuivres, donnée à la première connexion de chaque joueur (500 = 5 or).")
                .defineInRange("startingMoney", DEFAULT_STARTING_MONEY, 0, 1_000_000);
        EXTRA_ALLOWED_MENUS = builder
                .comment("Types d'écran (identifiants, ex. \"mod:coffre\") où une pièce a le droit d'être rangée, en plus",
                        "de l'inventaire du joueur, de la table de fabrication et des rangements de ce mod.")
                .defineListAllowEmpty("extraAllowedMenus", List.of(), EconomyConfig::isResourceLocation);
        EXTRA_FORBIDDEN_MENUS = builder
                .comment("Types d'écran (identifiants) où une pièce est refusée même si l'écran serait permis.",
                        "Les cases de l'inventaire du joueur restent toujours permises.")
                .defineListAllowEmpty("extraForbiddenMenus", List.of(), EconomyConfig::isResourceLocation);
        SPEC = builder.build();
    }

    private static volatile long[] cache = DEFAULTS.clone();
    private static volatile Set<ResourceLocation> allowedCache = Set.of();
    private static volatile Set<ResourceLocation> forbiddenCache = Set.of();

    private EconomyConfig() {}

    /** Valeur de la pièce en cuivres : la configuration si elle est cohérente, sinon la valeur par défaut. */
    public static long coinValue(Coin coin) {
        return cache[coin.ordinal()];
    }

    /** Nombre de lignes du registre gardées (10000 par défaut). */
    public static int ledgerMaxLines() {
        try {
            return LEDGER_MAX_LINES.get();
        } catch (IllegalStateException e) {
            return DEFAULT_LEDGER_MAX_LINES;
        }
    }

    /** Somme de départ, en cuivres (500 par défaut). */
    public static long startingMoney() {
        try {
            return STARTING_MONEY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_STARTING_MONEY;
        }
    }

    /** Types d'écran ajoutés par la configuration aux rangements permis (lus au chargement du fichier, vides avant). */
    public static Set<ResourceLocation> extraAllowedMenus() {
        return allowedCache;
    }

    /** Types d'écran que la configuration interdit à l'argent (lus au chargement du fichier, vides avant). */
    public static Set<ResourceLocation> extraForbiddenMenus() {
        return forbiddenCache;
    }

    private static Set<ResourceLocation> ids(ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        try {
            Set<ResourceLocation> out = new HashSet<>();
            for (String text : value.get()) {
                ResourceLocation id = ResourceLocation.tryParse(text);
                if (id != null) {
                    out.add(id);
                }
            }
            return out;
        } catch (IllegalStateException e) {
            return Set.of();
        }
    }

    private static boolean isResourceLocation(Object text) {
        return text instanceof String string && ResourceLocation.tryParse(string) != null;
    }

    /** Relit le fichier à son chargement et à son rechargement. */
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            cache = validated(read());
            allowedCache = Set.copyOf(ids(EXTRA_ALLOWED_MENUS));
            forbiddenCache = Set.copyOf(ids(EXTRA_FORBIDDEN_MENUS));
            CoinRecipeGuard.reapplyOnLiveServer();
        }
    }

    /**
     * Les valeurs si le cuivre vaut 1 et si chacune est un multiple strict de la précédente, sinon les valeurs
     * par défaut avec un avertissement (règle pure, essayée en JUnit).
     */
    static long[] validated(long[] values) {
        boolean ok = values.length == DEFAULTS.length && values[0] == 1L;
        for (int i = 1; ok && i < values.length; i++) {
            ok = values[i] > values[i - 1] && values[i] % values[i - 1] == 0;
        }
        if (ok) {
            return values;
        }
        LOGGER.warn("Economie : valeurs des pieces {} incoherentes (cuivre = 1, chaque piece multiple strict de "
                + "celle du dessous), valeurs par defaut utilisees", Arrays.toString(values));
        return DEFAULTS.clone();
    }

    private static long[] read() {
        long[] values = DEFAULTS.clone();
        for (int i = 1; i < values.length; i++) {
            try {
                values[i] = VALUES[i].get();
            } catch (IllegalStateException e) {
                values[i] = DEFAULTS[i];
            }
        }
        return values;
    }
}
