package dev.oneforallsurvival.phone.net;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * L'empreinte publique d'un jeton de carte SIM, et la mémoire des jetons vus chez chaque joueur (retours d'essai du
 * 27/11). Le client reçoit l'empreinte ({@link #mark}) à la place du jeton ; pour chaque joueur, la mémoire retrouve le
 * jeton d'une empreinte parmi ceux que le serveur a vus chez lui. Au plus {@code perPlayer} jetons par joueur, les moins
 * récemment vus oubliés d'abord ; un joueur s'oublie entier à sa déconnexion. Règle pure, sans Minecraft.
 *
 * <p>L'empreinte désigne un jeton précis, jamais seulement une carte : deux piles de la même carte aux jetons différents
 * (copie puis insertion, carte remplacée) gardent chacune le sien, quel que soit l'ordre des paquets. Ce n'est qu'une
 * mémoire de ce que le serveur a vu : elle ne lit jamais le registre et ne rend un jeton qu'au joueur chez qui il a été
 * vu.
 */
public final class CreativeSimTokens {

    private final int perPlayer;
    /** Par joueur : empreinte vers jeton, dans l'ordre du dernier usage. */
    private final Map<UUID, LinkedHashMap<UUID, UUID>> seen = new HashMap<>();

    public CreativeSimTokens(int perPlayer) {
        if (perPlayer < 1) {
            throw new IllegalArgumentException("au moins un jeton par joueur : " + perPlayer);
        }
        this.perPlayer = perPlayer;
    }

    /**
     * L'empreinte publique d'un jeton : les 128 premiers bits du SHA-256 de ses 16 octets (poids fort en tête). Elle ne
     * permet pas de retrouver le jeton, tiré par {@code UUID.randomUUID()}, et ne vaut rien au registre.
     */
    public static UUID mark(UUID token) {
        byte[] bytes = ByteBuffer.allocate(16).putLong(token.getMostSignificantBits())
                .putLong(token.getLeastSignificantBits()).array();
        try {
            ByteBuffer digest = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new UUID(digest.getLong(), digest.getLong());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 manque à cette machine Java", e);
        }
    }

    /** Le serveur a vu ce jeton sur une pile chez {@code player}. */
    public void note(UUID player, UUID token) {
        LinkedHashMap<UUID, UUID> tokens = seen.computeIfAbsent(player, p -> new LinkedHashMap<>(16, 0.75f, true));
        tokens.put(mark(token), token);
        if (tokens.size() > perPlayer) {
            UUID oldest = tokens.keySet().iterator().next();
            tokens.remove(oldest);
        }
    }

    /** Le jeton de cette empreinte, s'il a été vu chez ce joueur ; sinon rien. */
    public Optional<UUID> token(UUID player, UUID mark) {
        LinkedHashMap<UUID, UUID> tokens = seen.get(player);
        return tokens == null ? Optional.empty() : Optional.ofNullable(tokens.get(mark));
    }

    /** Oublie tout ce qui a été vu chez ce joueur (déconnexion). */
    public void forget(UUID player) {
        seen.remove(player);
    }

    /** Oublie tout (arrêt du serveur). */
    public void clear() {
        seen.clear();
    }

    /** Le nombre de jetons retenus pour ce joueur. */
    public int size(UUID player) {
        LinkedHashMap<UUID, UUID> tokens = seen.get(player);
        return tokens == null ? 0 : tokens.size();
    }

    /** Le nombre de joueurs dont quelque chose est retenu. */
    public int players() {
        return seen.size();
    }
}
