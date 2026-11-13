package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.economy.stall.StallLedgerPacket;
import javax.annotation.Nullable;

/** Côté client : la dernière page du registre d'étal reçue, lue par l'onglet Registre de la gestion. */
public final class StallLedgerCache {

    @Nullable
    private static StallLedgerPacket last;

    private StallLedgerCache() {}

    public static void accept(StallLedgerPacket packet) {
        last = packet;
    }

    @Nullable
    public static StallLedgerPacket last() {
        return last;
    }

    public static void clear() {
        last = null;
    }
}
