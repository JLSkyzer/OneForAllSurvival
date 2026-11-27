package dev.oneforallsurvival.phone.call;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Les demandes de partage de numéro en attente (« AirDrop », retours d'essai du 27/11), machine à états pure, sans monde
 * ni réseau : {@link ShareService} la pilote. Rien n'est sauvegardé : un redémarrage n'a aucune demande. Fil du serveur
 * seulement.
 *
 * <ul>
 *   <li>une seule demande en attente d'un même envoyeur vers un même destinataire ;
 *   <li>au plus une demande nouvelle toutes les {@link #GAP_MILLIS} ms par envoyeur (seules les demandes posées
 *       comptent ; une horloge qui recule ne bloque pas) ;
 *   <li>une demande vit {@link #TTL_MILLIS} ms ; au plus {@link #MAX_PENDING} en attente sur le serveur ;
 *   <li>une réponse ne vaut que pour le destinataire de la demande.
 * </ul>
 */
public final class ShareBook {

    /** Durée de vie d'une demande sans réponse. */
    public static final long TTL_MILLIS = 60_000L;
    /** Écart minimal entre deux demandes posées par un même envoyeur. */
    public static final long GAP_MILLIS = 3_000L;
    /** Demandes en attente sur tout le serveur. */
    public static final int MAX_PENDING = 1024;

    /**
     * Une demande : l'envoyeur, sa carte ({@code senderSim}) et le numéro qui part, la fiche proposée ({@code name}, le
     * nom du propriétaire du téléphone), le destinataire, et les noms des deux joueurs pour leurs messages.
     * {@link #toString()} ne montre ni numéro ni carte.
     */
    public record Request(int id, UUID sender, String senderName, UUID senderSim, String number, String name,
            UUID receiver, String receiverName, long createdMillis) {

        public Request {
            Objects.requireNonNull(sender, "sender");
            Objects.requireNonNull(senderSim, "senderSim");
            Objects.requireNonNull(receiver, "receiver");
            senderName = senderName == null ? "" : senderName;
            number = number == null ? "" : number;
            name = name == null ? "" : name;
            receiverName = receiverName == null ? "" : receiverName;
        }

        @Override
        public String toString() {
            return "Request[id=" + id + ", sender=" + sender + ", receiver=" + receiver + ", number=***, senderSim=***]";
        }
    }

    public enum Send { SENT, PENDING, THROTTLED, SELF, FULL }

    /** Issue d'une demande : la demande posée seulement pour {@link Send#SENT}. */
    public record SendResult(Send outcome, Optional<Request> request) {}

    private final Map<Integer, Request> pending = new LinkedHashMap<>();
    private final Map<UUID, Long> lastSent = new HashMap<>();
    private int nextId = 1;

    /**
     * Pose une demande de {@code sender} vers {@code receiver} à l'instant {@code now}. Refus, sans rien changer :
     * {@link Send#SELF} (même joueur), {@link Send#PENDING} (une demande de lui vers lui attend déjà),
     * {@link Send#THROTTLED} (moins de {@link #GAP_MILLIS} ms depuis sa dernière demande posée), {@link Send#FULL}
     * ({@link #MAX_PENDING} demandes en attente).
     */
    public SendResult send(UUID sender, String senderName, UUID senderSim, String number, String name, UUID receiver,
            String receiverName, long now) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(receiver, "receiver");
        if (sender.equals(receiver)) {
            return new SendResult(Send.SELF, Optional.empty());
        }
        for (Request r : pending.values()) {
            if (r.sender().equals(sender) && r.receiver().equals(receiver)) {
                return new SendResult(Send.PENDING, Optional.empty());
            }
        }
        Long last = lastSent.get(sender);
        if (last != null && now >= last && now - last < GAP_MILLIS) {
            return new SendResult(Send.THROTTLED, Optional.empty());
        }
        if (pending.size() >= MAX_PENDING) {
            return new SendResult(Send.FULL, Optional.empty());
        }
        Request request = new Request(takeId(), sender, senderName, senderSim, number, name, receiver, receiverName,
                now);
        pending.put(request.id(), request);
        lastSent.put(sender, now);
        return new SendResult(Send.SENT, Optional.of(request));
    }

    /** Un numéro libre, jamais 0 ni négatif. */
    private int takeId() {
        while (pending.containsKey(nextId)) {
            nextId = nextId == Integer.MAX_VALUE ? 1 : nextId + 1;
        }
        int id = nextId;
        nextId = nextId == Integer.MAX_VALUE ? 1 : nextId + 1;
        return id;
    }

    /** La demande {@code id}, si {@code responder} en est le destinataire (rien ne change). */
    public Optional<Request> forReceiver(int id, UUID responder) {
        Request r = pending.get(id);
        return r != null && r.receiver().equals(responder) ? Optional.of(r) : Optional.empty();
    }

    /** Retire la demande {@code id} et la rend ; vide si elle n'attend plus. */
    public Optional<Request> remove(int id) {
        return Optional.ofNullable(pending.remove(id));
    }

    /** Retire et rend les demandes posées depuis {@link #TTL_MILLIS} ms ou plus à {@code now}, par ordre de pose. */
    public List<Request> expire(long now) {
        return removeIf(r -> now - r.createdMillis() >= TTL_MILLIS || now < r.createdMillis() - TTL_MILLIS);
    }

    /** Retire et rend les demandes que ce joueur envoie ou reçoit ; oublie son frein. */
    public List<Request> dropPlayer(UUID player) {
        lastSent.remove(player);
        return removeIf(r -> r.sender().equals(player) || r.receiver().equals(player));
    }

    /** Retire et rend les demandes qui passent {@code test}, par ordre de pose. */
    public List<Request> removeIf(Predicate<Request> test) {
        List<Request> out = new ArrayList<>();
        pending.values().removeIf(r -> {
            if (test.test(r)) {
                out.add(r);
                return true;
            }
            return false;
        });
        return List.copyOf(out);
    }

    /** Toutes les demandes en attente, par ordre de pose. Copie non modifiable. */
    public List<Request> all() {
        return List.copyOf(pending.values());
    }

    /** Oublie tout (arrêt du serveur). */
    public void clear() {
        pending.clear();
        lastSent.clear();
    }
}
