package dev.oneforallsurvival.grid;

import java.util.List;

/**
 * La répartition par priorité, en logique pure : une offre d'énergie sert les sorties dans l'ordre, chacune prend ce
 * qu'elle accepte, la suivante reçoit le reste. Une sortie atteinte une fois l'offre épuisée, et qui acceptait encore,
 * est « affamée » : c'est elle que le tableau affiche partielle ou coupée.
 */
public final class PriorityRelay {

    /** Une sortie : comme {@code IEnergyStorage.receiveEnergy}, renvoie ce qu'elle prend (ou prendrait). */
    public interface Sink {
        int accept(int amount, boolean simulate);
    }

    private PriorityRelay() {}

    /**
     * Sert {@code ordered} dans l'ordre avec {@code offer} FE et renvoie le total accepté. Hors simulation, ajoute à
     * {@code delivered[i]} ce que la sortie i a pris et passe {@code starved[i]} à vrai pour chaque sortie atteinte quand
     * l'offre était épuisée et qui acceptait encore (sondée par une simulation d'1 FE). En simulation, ni
     * {@code delivered} ni {@code starved} ne changent et aucune sortie n'est sondée.
     */
    public static int distribute(int offer, List<Sink> ordered, boolean simulate, int[] delivered, boolean[] starved) {
        int remaining = Math.max(0, offer);
        for (int i = 0; i < ordered.size(); i++) {
            Sink sink = ordered.get(i);
            if (remaining > 0) {
                int taken = Math.max(0, Math.min(remaining, sink.accept(remaining, simulate)));
                remaining -= taken;
                if (!simulate) {
                    delivered[i] += taken;
                }
            }
            if (remaining == 0 && !simulate && sink.accept(1, true) > 0) {
                starved[i] = true;
            }
        }
        return Math.max(0, offer) - remaining;
    }

    /** L'état affiché d'une sortie : rien de branché, alimentée, partielle ou coupée. */
    public static OutputStatus status(boolean present, int delivered, boolean starved) {
        if (!present) {
            return OutputStatus.NONE;
        }
        if (!starved) {
            return OutputStatus.FED;
        }
        return delivered > 0 ? OutputStatus.PARTIAL : OutputStatus.CUT;
    }
}
