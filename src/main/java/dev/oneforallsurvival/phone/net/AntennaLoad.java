package dev.oneforallsurvival.phone.net;

/** La consommation d'une antenne par tic, détaillée. Règle pure : les tarifs arrivent en paramètres. */
public final class AntennaLoad {

    private AntennaLoad() {}

    /** @param standby veille ; @param clients cartes actives ; @param traffic appels et visios ouverts, en FE par tic */
    public record Breakdown(int standby, int clients, int traffic) {

        /** La somme des trois postes, saturée à {@link Integer#MAX_VALUE}. */
        public int total() {
            return saturate((long) standby + clients + traffic);
        }
    }

    /**
     * Veille, plus {@code perSimFe} par carte active, plus {@code callFe} par appel ouvert et {@code facetimeFe} par
     * visio ouverte. Chaque poste est saturé à {@link Integer#MAX_VALUE} ; une valeur négative compte pour zéro (la
     * consommation ne baisse jamais).
     */
    public static Breakdown of(int standbyFe, int perSimFe, int activeCards, int openCalls, int callFe,
            int openFacetimes, int facetimeFe) {
        int standby = Math.max(0, standbyFe);
        int clients = saturate(product(perSimFe, activeCards));
        int traffic = saturate(product(callFe, openCalls) + product(facetimeFe, openFacetimes));
        return new Breakdown(standby, clients, traffic);
    }

    /** Produit de deux valeurs d'un {@code int} sans débordement ; négatif ramené à zéro. */
    private static long product(int a, int b) {
        return (long) Math.max(0, a) * Math.max(0, b);
    }

    private static int saturate(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
