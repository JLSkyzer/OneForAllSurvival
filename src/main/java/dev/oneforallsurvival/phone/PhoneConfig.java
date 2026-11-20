package dev.oneforallsurvival.phone;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages du téléphone. Fichier SERVER ({@code oneforallsurvival-phone.toml}) : le serveur en décide et Forge le
 * synchronise. Les lecteurs retombent sur les valeurs par défaut tant que le fichier n'est pas chargé.
 */
public final class PhoneConfig {

    private static final int DEFAULT_CAPACITY = 72_000;
    private static final int DEFAULT_DRAIN_PER_TICK = 2;
    private static final int DEFAULT_SOCKET_CHARGE_PER_TICK = 40;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_LOCKOUT_SECONDS = 60;
    private static final int DEFAULT_ANTENNA_RANGE = 270;
    private static final int DEFAULT_ANTENNA_STANDBY_FE = 40;
    private static final int DEFAULT_ANTENNA_PER_SIM_FE = 6;
    private static final int DEFAULT_ANTENNA_CALL_FE = 9;
    private static final int DEFAULT_ANTENNA_FACETIME_FE = 24;
    private static final int DEFAULT_ANTENNA_MESSAGE_FE = 100;
    private static final int DEFAULT_ANTENNA_CAPACITY = 40_000;
    private static final int DEFAULT_ANTENNA_MAX_RECEIVE = 4_000;
    private static final boolean DEFAULT_ANTENNA_KEEPS_CHUNK_LOADED = true;
    private static final int DEFAULT_ANTENNA_STALE_TICKS = 40;
    private static final int DEFAULT_SIM_REQUESTS_MAX = 32;
    private static final int DEFAULT_TARIFF_MAX = 1_000;
    private static final int DEFAULT_TARIFF_CAP = 20;
    private static final int DEFAULT_SIGNAL_REFRESH_TICKS = 20;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue CAPACITY;
    private static final ForgeConfigSpec.IntValue DRAIN_PER_TICK;
    private static final ForgeConfigSpec.IntValue SOCKET_CHARGE_PER_TICK;
    private static final ForgeConfigSpec.IntValue MAX_ATTEMPTS;
    private static final ForgeConfigSpec.IntValue LOCKOUT_SECONDS;
    private static final ForgeConfigSpec.IntValue ANTENNA_RANGE;
    private static final ForgeConfigSpec.IntValue ANTENNA_STANDBY_FE;
    private static final ForgeConfigSpec.IntValue ANTENNA_PER_SIM_FE;
    private static final ForgeConfigSpec.IntValue ANTENNA_CALL_FE;
    private static final ForgeConfigSpec.IntValue ANTENNA_FACETIME_FE;
    private static final ForgeConfigSpec.IntValue ANTENNA_MESSAGE_FE;
    private static final ForgeConfigSpec.IntValue ANTENNA_CAPACITY;
    private static final ForgeConfigSpec.IntValue ANTENNA_MAX_RECEIVE;
    private static final ForgeConfigSpec.BooleanValue ANTENNA_KEEPS_CHUNK_LOADED;
    private static final ForgeConfigSpec.IntValue ANTENNA_STALE_TICKS;
    private static final ForgeConfigSpec.IntValue SIM_REQUESTS_MAX;
    private static final ForgeConfigSpec.IntValue TARIFF_MAX;
    private static final ForgeConfigSpec.IntValue TARIFF_CAP;
    private static final ForgeConfigSpec.IntValue SIGNAL_REFRESH_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CAPACITY = builder
                .comment("Capacité de la batterie du téléphone (FE).")
                .defineInRange("capacity", DEFAULT_CAPACITY, 1, 10_000_000);
        DRAIN_PER_TICK = builder
                .comment("FE dépensés à chaque tick d'écran allumé (2 FE/t = 30 minutes d'écran sur 72 000 FE).")
                .defineInRange("drainPerTick", DEFAULT_DRAIN_PER_TICK, 0, 1_000);
        SOCKET_CHARGE_PER_TICK = builder
                .comment("FE reçus à chaque tick sur une prise (40 FE/t = charge complète en 90 secondes).")
                .defineInRange("socketChargePerTick", DEFAULT_SOCKET_CHARGE_PER_TICK, 1, 200);
        MAX_ATTEMPTS = builder
                .comment("Essais de code ratés avant le blocage.")
                .defineInRange("maxAttempts", DEFAULT_MAX_ATTEMPTS, 1, 100);
        LOCKOUT_SECONDS = builder
                .comment("Durée du blocage après les essais ratés (secondes).")
                .defineInRange("lockoutSeconds", DEFAULT_LOCKOUT_SECONDS, 0, 86_400);
        ANTENNA_RANGE = builder
                .comment("Portée d'une antenne (blocs, en ligne droite dans une même dimension).")
                .defineInRange("antennaRange", DEFAULT_ANTENNA_RANGE, 16, 2_048);
        ANTENNA_STANDBY_FE = builder
                .comment("FE par tick qu'une antenne consomme rien que pour rester allumée.")
                .defineInRange("antennaStandbyFe", DEFAULT_ANTENNA_STANDBY_FE, 0, 100_000);
        ANTENNA_PER_SIM_FE = builder
                .comment("FE par tick en plus, pour chaque carte SIM active de l'opérateur.")
                .defineInRange("antennaPerSimFe", DEFAULT_ANTENNA_PER_SIM_FE, 0, 100_000);
        ANTENNA_CALL_FE = builder
                .comment("FE par tick en plus, pour chaque appel ouvert par l'antenne.")
                .defineInRange("antennaCallFe", DEFAULT_ANTENNA_CALL_FE, 0, 100_000);
        ANTENNA_FACETIME_FE = builder
                .comment("FE par tick en plus, pour chaque visio ouverte par l'antenne.")
                .defineInRange("antennaFacetimeFe", DEFAULT_ANTENNA_FACETIME_FE, 0, 100_000);
        ANTENNA_MESSAGE_FE = builder
                .comment("FE d'un coup pour chaque message relayé par l'antenne.")
                .defineInRange("antennaMessageFe", DEFAULT_ANTENNA_MESSAGE_FE, 0, 100_000);
        ANTENNA_CAPACITY = builder
                .comment("Capacité de la réserve d'énergie d'une antenne (FE).")
                .defineInRange("antennaCapacity", DEFAULT_ANTENNA_CAPACITY, 1, 100_000_000);
        ANTENNA_MAX_RECEIVE = builder
                .comment("FE par tick qu'une antenne accepte de recevoir.")
                .defineInRange("antennaMaxReceive", DEFAULT_ANTENNA_MAX_RECEIVE, 1, 10_000_000);
        ANTENNA_KEEPS_CHUNK_LOADED = builder
                .comment("Une antenne en service garde son chunk chargé.")
                .define("antennaKeepsChunkLoaded", DEFAULT_ANTENNA_KEEPS_CHUNK_LOADED);
        ANTENNA_STALE_TICKS = builder
                .comment("Ticks sans nouvelle d'une antenne avant qu'elle ne soit tenue pour éteinte.")
                .defineInRange("antennaStaleTicks", DEFAULT_ANTENNA_STALE_TICKS, 2, 1_200);
        SIM_REQUESTS_MAX = builder
                .comment("Demandes de carte SIM en attente, au plus, chez un même opérateur.")
                .defineInRange("simRequestsMax", DEFAULT_SIM_REQUESTS_MAX, 1, 10_000);
        TARIFF_MAX = builder
                .comment("Tarif le plus haut qu'un opérateur puisse demander (cuivres).")
                .defineInRange("phoneTariffMax", DEFAULT_TARIFF_MAX, 0, 1_000_000);
        TARIFF_CAP = builder
                .comment("Plafond des tarifs tant que le maire n'en a pas fixé un (cuivres).")
                .defineInRange("phoneDefaultTariffCap", DEFAULT_TARIFF_CAP, 0, 1_000_000);
        SIGNAL_REFRESH_TICKS = builder
                .comment("Ticks entre deux relevés du signal d'un téléphone.")
                .defineInRange("signalRefreshTicks", DEFAULT_SIGNAL_REFRESH_TICKS, 1, 1_200);
        SPEC = builder.build();
    }

    private PhoneConfig() {}

    public static int capacity() {
        try {
            return CAPACITY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_CAPACITY;
        }
    }

    public static int drainPerTick() {
        try {
            return DRAIN_PER_TICK.get();
        } catch (IllegalStateException e) {
            return DEFAULT_DRAIN_PER_TICK;
        }
    }

    public static int socketChargePerTick() {
        try {
            return SOCKET_CHARGE_PER_TICK.get();
        } catch (IllegalStateException e) {
            return DEFAULT_SOCKET_CHARGE_PER_TICK;
        }
    }

    public static int maxAttempts() {
        try {
            return MAX_ATTEMPTS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_MAX_ATTEMPTS;
        }
    }

    public static int lockoutSeconds() {
        try {
            return LOCKOUT_SECONDS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_LOCKOUT_SECONDS;
        }
    }

    public static int antennaRange() {
        try {
            return ANTENNA_RANGE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_RANGE;
        }
    }

    public static int antennaStandbyFe() {
        try {
            return ANTENNA_STANDBY_FE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_STANDBY_FE;
        }
    }

    public static int antennaPerSimFe() {
        try {
            return ANTENNA_PER_SIM_FE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_PER_SIM_FE;
        }
    }

    public static int antennaCallFe() {
        try {
            return ANTENNA_CALL_FE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_CALL_FE;
        }
    }

    public static int antennaFacetimeFe() {
        try {
            return ANTENNA_FACETIME_FE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_FACETIME_FE;
        }
    }

    public static int antennaMessageFe() {
        try {
            return ANTENNA_MESSAGE_FE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_MESSAGE_FE;
        }
    }

    public static int antennaCapacity() {
        try {
            return ANTENNA_CAPACITY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_CAPACITY;
        }
    }

    public static int antennaMaxReceive() {
        try {
            return ANTENNA_MAX_RECEIVE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_MAX_RECEIVE;
        }
    }

    public static boolean antennaKeepsChunkLoaded() {
        try {
            return ANTENNA_KEEPS_CHUNK_LOADED.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_KEEPS_CHUNK_LOADED;
        }
    }

    public static int antennaStaleTicks() {
        try {
            return ANTENNA_STALE_TICKS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ANTENNA_STALE_TICKS;
        }
    }

    public static int simRequestsMax() {
        try {
            return SIM_REQUESTS_MAX.get();
        } catch (IllegalStateException e) {
            return DEFAULT_SIM_REQUESTS_MAX;
        }
    }

    public static int phoneTariffMax() {
        try {
            return TARIFF_MAX.get();
        } catch (IllegalStateException e) {
            return DEFAULT_TARIFF_MAX;
        }
    }

    public static int phoneDefaultTariffCap() {
        try {
            return TARIFF_CAP.get();
        } catch (IllegalStateException e) {
            return DEFAULT_TARIFF_CAP;
        }
    }

    public static int signalRefreshTicks() {
        try {
            return SIGNAL_REFRESH_TICKS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_SIGNAL_REFRESH_TICKS;
        }
    }
}
