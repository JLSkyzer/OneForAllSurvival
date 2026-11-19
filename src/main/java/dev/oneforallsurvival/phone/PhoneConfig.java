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

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue CAPACITY;
    private static final ForgeConfigSpec.IntValue DRAIN_PER_TICK;
    private static final ForgeConfigSpec.IntValue SOCKET_CHARGE_PER_TICK;
    private static final ForgeConfigSpec.IntValue MAX_ATTEMPTS;
    private static final ForgeConfigSpec.IntValue LOCKOUT_SECONDS;

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
}
