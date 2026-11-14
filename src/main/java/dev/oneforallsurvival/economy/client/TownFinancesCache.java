package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.economy.town.TownFinances.Snapshot;
import javax.annotation.Nullable;

/** Côté client : les derniers comptes publics de la ville reçus, lus par le rendu du tableau de la mairie. */
public final class TownFinancesCache {

    @Nullable
    private static volatile Snapshot last;

    private TownFinancesCache() {}

    public static void set(Snapshot snapshot) {
        last = snapshot;
    }

    /** Les derniers comptes, ou nul tant que rien n'est reçu (le tableau affiche alors « … »). */
    @Nullable
    public static Snapshot get() {
        return last;
    }

    public static void clear() {
        last = null;
    }
}
