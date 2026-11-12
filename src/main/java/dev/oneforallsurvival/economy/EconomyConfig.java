package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;

/**
 * Réglages de l'économie. Fichier SERVER ({@code oneforallsurvival-economy.toml}) : le serveur en décide et Forge
 * le synchronise, pour que l'infobulle suive. Les lecteurs retombent sur les défauts tant que le fichier n'est pas
 * chargé (aucun monde, ou JUnit).
 */
public final class EconomyConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cuivre, argent, or, émeraude, rubis. */
    static final long[] DEFAULTS = {1L, 10L, 100L, 1_000L, 10_000L};

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.LongValue[] VALUES = new ForgeConfigSpec.LongValue[Coin.values().length];

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Valeur de chaque pièce, en cuivres.",
                "Les valeurs doivent croître strictement du cuivre au rubis, sinon les valeurs par défaut",
                "(1, 10, 100, 1000, 10000) sont utilisées et un avertissement est écrit au journal.",
                "Les recettes de change (10 pour 1) ne suivent pas ces valeurs : elles sont dans les données.");
        for (Coin coin : Coin.values()) {
            VALUES[coin.ordinal()] = builder.defineInRange(coin.key() + "CoinValue", DEFAULTS[coin.ordinal()],
                    1L, 1_000_000_000L);
        }
        SPEC = builder.build();
    }

    private static volatile boolean warned;

    private EconomyConfig() {}

    /** Valeur de la pièce en cuivres : la configuration si elle est cohérente, sinon la valeur par défaut. */
    public static long coinValue(Coin coin) {
        return validated(read())[coin.ordinal()] ;
    }

    /**
     * Les valeurs si elles croissent strictement, sinon les valeurs par défaut (règle pure, essayée en JUnit).
     */
    static long[] validated(long[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] <= values[i - 1]) {
                if (!warned) {
                    warned = true;
                    LOGGER.warn("Economie : les valeurs des pieces ne croissent pas strictement {}, "
                            + "valeurs par defaut utilisees", java.util.Arrays.toString(values));
                }
                return DEFAULTS.clone();
            }
        }
        return values;
    }

    private static long[] read() {
        long[] values = new long[VALUES.length];
        for (int i = 0; i < values.length; i++) {
            try {
                values[i] = VALUES[i].get();
            } catch (IllegalStateException e) {
                values[i] = DEFAULTS[i];
            }
        }
        return values;
    }

    /** Pour les essais : les valeurs par défaut par coupure. */
    static Map<Coin, Long> defaults() {
        Map<Coin, Long> out = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            out.put(coin, DEFAULTS[coin.ordinal()]);
        }
        return out;
    }
}
