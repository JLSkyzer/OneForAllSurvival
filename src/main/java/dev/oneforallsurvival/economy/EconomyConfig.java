package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
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

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Valeur de chaque pièce, en cuivres. Le cuivre vaut toujours 1.",
                "Chaque valeur doit être un multiple strict de celle de la pièce du dessous, sinon les valeurs",
                "par défaut (1, 10, 100, 1000, 10000) sont utilisées et un avertissement est écrit au journal.",
                "Les recettes de change (1 pour 10) ne suivent pas ces valeurs : elles sont dans les données.");
        for (Coin coin : Coin.values()) {
            if (coin != Coin.COPPER) {
                VALUES[coin.ordinal()] = builder.defineInRange(coin.key() + "CoinValue", DEFAULTS[coin.ordinal()],
                        2L, 1_000_000_000L);
            }
        }
        SPEC = builder.build();
    }

    private static volatile long[] cache = DEFAULTS.clone();

    private EconomyConfig() {}

    /** Valeur de la pièce en cuivres : la configuration si elle est cohérente, sinon la valeur par défaut. */
    public static long coinValue(Coin coin) {
        return cache[coin.ordinal()];
    }

    /** Relit le fichier à son chargement et à son rechargement. */
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            cache = validated(read());
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
