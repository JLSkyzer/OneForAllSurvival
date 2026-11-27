package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.phone.call.CallBook.Call;
import java.util.HashMap;
import java.util.Map;

/**
 * Quand chaque appel sonne (lot T3, tâche 4a), en tics du serveur : au premier tic où il est vu sonnant, puis toutes les
 * {@code period} tics. Jamais calculé depuis {@code startedTick} (revue de la tâche 4a, mineur) : un vrai paquet
 * {@code DIAL} est joué entre deux tics, après la fin du tic de {@code startedTick}, qu'un tel calcul ne faisait sonner
 * qu'une période plus tard. Un appel qui ne sonne plus est oublié. Règle pure, sans rien du jeu.
 */
final class RingSchedule {

    private final int period;
    /** Le tic de la prochaine sonnerie de chaque appel qui sonne. */
    private final Map<Integer, Integer> next = new HashMap<>();

    RingSchedule(int period) {
        this.period = period;
    }

    /** Cet appel sonne-t-il à ce tic ? Si oui, sa sonnerie suivante est notée. */
    boolean due(Call call, int tick) {
        if (call.state() != CallBook.State.RINGING) {
            next.remove(call.id());
            return false;
        }
        if (tick < next.getOrDefault(call.id(), tick)) {
            return false;
        }
        next.put(call.id(), tick + period);
        return true;
    }

    /** Un appel fini. */
    void forget(int callId) {
        next.remove(callId);
    }
}
