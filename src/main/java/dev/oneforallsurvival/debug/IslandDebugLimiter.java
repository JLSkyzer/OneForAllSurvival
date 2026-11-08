package dev.oneforallsurvival.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Limite les requêtes du F3 par joueur : au plus {@value #MAX_PER_WINDOW} sur toute fenêtre glissante de
 * {@value #WINDOW_TICKS} ticks de serveur (une seconde). Le tick, et non l'horloge : un serveur en retard répond
 * moins, jamais plus. Logique pure, sur le thread du serveur seulement.
 */
public final class IslandDebugLimiter {

    public static final int MAX_PER_WINDOW = 4;
    public static final int WINDOW_TICKS = 20;

    private final Map<UUID, Deque<Long>> accepted = new HashMap<>();

    /** Vrai si la requête de {@code player} au tick {@code tick} passe ; elle est alors comptée, sinon oubliée. */
    public boolean allow(UUID player, long tick) {
        Deque<Long> ticks = accepted.computeIfAbsent(player, id -> new ArrayDeque<>(MAX_PER_WINDOW));
        // Un tick antérieur au dernier compté : le compteur du serveur est reparti de zéro, l'ancien compte est caduc.
        while (!ticks.isEmpty() && (tick - ticks.peekFirst() >= WINDOW_TICKS || tick < ticks.peekLast())) {
            ticks.pollFirst();
        }
        if (ticks.size() >= MAX_PER_WINDOW) {
            return false;
        }
        ticks.addLast(tick);
        return true;
    }

    /** Oublie le joueur (à sa déconnexion). */
    public void forget(UUID player) {
        accepted.remove(player);
    }
}
