package dev.oneforallsurvival.phone.net;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La mémoire des jetons de carte SIM vus chez chaque joueur (retours d'essai du 27/11) : pour chaque joueur, la carte
 * ({@code sim_id}) et le dernier jeton que le serveur a vu chez lui. Au plus {@code perPlayer} cartes par joueur, les
 * moins récemment vues oubliées d'abord ; un joueur s'oublie entier à sa déconnexion. Règle pure, sans Minecraft.
 *
 * <p>Ce n'est qu'une mémoire de ce que le serveur a vu : elle ne lit jamais le registre et ne rend un jeton qu'au joueur
 * chez qui il a été vu.
 */
public final class CreativeSimTokens {

    private final int perPlayer;
    private final Map<UUID, LinkedHashMap<UUID, UUID>> seen = new HashMap<>();

    public CreativeSimTokens(int perPlayer) {
        if (perPlayer < 1) {
            throw new IllegalArgumentException("au moins une carte par joueur : " + perPlayer);
        }
        this.perPlayer = perPlayer;
    }

    /** Le serveur a vu chez {@code player} la carte {@code simId} portant {@code token}. */
    public void note(UUID player, UUID simId, UUID token) {
        LinkedHashMap<UUID, UUID> cards = seen.computeIfAbsent(player, p -> new LinkedHashMap<>(16, 0.75f, true));
        cards.put(simId, token);
        if (cards.size() > perPlayer) {
            UUID oldest = cards.keySet().iterator().next();
            cards.remove(oldest);
        }
    }

    /** Le dernier jeton de cette carte vu chez ce joueur, ou rien. */
    public Optional<UUID> token(UUID player, UUID simId) {
        LinkedHashMap<UUID, UUID> cards = seen.get(player);
        return cards == null ? Optional.empty() : Optional.ofNullable(cards.get(simId));
    }

    /** Oublie tout ce qui a été vu chez ce joueur (déconnexion). */
    public void forget(UUID player) {
        seen.remove(player);
    }

    /** Oublie tout (arrêt du serveur). */
    public void clear() {
        seen.clear();
    }

    /** Le nombre de cartes retenues pour ce joueur. */
    public int size(UUID player) {
        LinkedHashMap<UUID, UUID> cards = seen.get(player);
        return cards == null ? 0 : cards.size();
    }

    /** Le nombre de joueurs dont quelque chose est retenu. */
    public int players() {
        return seen.size();
    }
}
