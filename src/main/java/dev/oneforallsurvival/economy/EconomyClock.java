package dev.oneforallsurvival.economy;

import java.util.function.LongSupplier;

/**
 * L'heure réelle lue par l'économie (paie, factures quotidiennes), en millisecondes depuis l'époque. Injectable pour
 * les essais, qui n'attendent jamais un vrai minuit : {@link #setClock} puis {@link #reset}. Même forme que
 * {@code TradeService.setClock}.
 */
public final class EconomyClock {

    private static volatile LongSupplier clock = System::currentTimeMillis;

    private EconomyClock() {}

    /** L'instant présent, en millisecondes. */
    public static long now() {
        return clock.getAsLong();
    }

    /** Remplace l'horloge (essais seulement). */
    public static void setClock(LongSupplier newClock) {
        clock = newClock;
    }

    /** Rend l'horloge du système. */
    public static void reset() {
        clock = System::currentTimeMillis;
    }
}
