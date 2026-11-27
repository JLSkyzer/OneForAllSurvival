package dev.oneforallsurvival.phone.call;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Le frein des appels composés (règle pure, sans monde) : {@code limit} appels au plus par fenêtre glissante de
 * {@code window} tics, par joueur. Chaque appel compté, même raté. Une déconnexion n'oublie rien (revue de la tâche 3,
 * I1 : quitter puis revenir ne remet pas le compte à zéro) ; seules les entrées sorties de la fenêtre s'oublient
 * ({@link #purge}). Une horloge qui recule (nouveau monde) oublie tout. Fil du serveur seulement.
 */
public final class DialThrottle {

    private final int window;
    private final Map<UUID, Deque<Integer>> dials = new HashMap<>();

    public DialThrottle(int window) {
        this.window = Math.max(1, window);
    }

    /** Vrai, et l'appel est compté, s'il reste moins de {@code limit} appels dans la fenêtre qui finit à {@code tick}. */
    public boolean allow(UUID player, int tick, int limit) {
        Deque<Integer> recent = dials.computeIfAbsent(player, id -> new ArrayDeque<>());
        drop(recent, tick);
        if (recent.size() >= limit) {
            return false;
        }
        recent.addLast(tick);
        return true;
    }

    /** Oublie ce qui est sorti de la fenêtre à {@code tick}, et les joueurs qui n'ont plus rien dedans. */
    public void purge(int tick) {
        for (Iterator<Deque<Integer>> it = dials.values().iterator(); it.hasNext();) {
            Deque<Integer> recent = it.next();
            drop(recent, tick);
            if (recent.isEmpty()) {
                it.remove();
            }
        }
    }

    /** Les joueurs gardés (essais). */
    public int tracked() {
        return dials.size();
    }

    public void clear() {
        dials.clear();
    }

    private void drop(Deque<Integer> recent, int tick) {
        while (!recent.isEmpty() && (tick - recent.peekFirst() >= window || tick < recent.peekFirst())) {
            recent.pollFirst();
        }
    }
}
