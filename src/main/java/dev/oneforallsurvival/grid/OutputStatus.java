package dev.oneforallsurvival.grid;

import java.util.Locale;

/** Ce que le tableau de délestage affiche pour une sortie, au dernier tick achevé. */
public enum OutputStatus {
    /** Rien de branché sur cette face. */
    NONE,
    /** Servie de tout ce qu'elle a pris, ou n'avait besoin de rien. */
    FED,
    /** Servie en partie : l'offre s'est épuisée sur elle. */
    PARTIAL,
    /** Rien reçu alors qu'elle en voulait. */
    CUT;

    /** Le nom dans les clés de traduction. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** L'état d'après sa position, pour ce que le menu synchronise ; {@link #NONE} hors limites. */
    public static OutputStatus byOrdinal(int ordinal) {
        OutputStatus[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NONE;
    }
}
