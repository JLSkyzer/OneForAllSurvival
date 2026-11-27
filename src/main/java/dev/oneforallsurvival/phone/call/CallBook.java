package dev.oneforallsurvival.phone.call;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Les appels en cours : qui appelle qui, sonnerie ou décroché, silence et haut-parleur. Machine à états pure, sans
 * monde ni réseau : {@link CallService} la pilote, et seul lui ferme les poignées de trafic et écrit les récents.
 *
 * <p>Un joueur, un téléphone ({@code phoneId}, copies comprises) et une carte ({@code simId}) ne sont jamais dans deux
 * appels à la fois. Rien n'est sauvegardé : un redémarrage n'a aucun appel. Fil du serveur seulement.
 */
public final class CallBook {

    /**
     * Un bout de l'appel : le joueur qui porte le téléphone, ce téléphone, la carte qui y est logée et son numéro (dix
     * chiffres sans espaces). {@link #toString()} ne montre que le joueur : ni numéro ni carte dans un journal.
     */
    public record Party(UUID player, UUID phoneId, UUID simId, String number) {

        public Party {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(phoneId, "phoneId");
            Objects.requireNonNull(simId, "simId");
            Objects.requireNonNull(number, "number");
        }

        /** Partage-t-il avec {@code other} le joueur, le téléphone ou la carte ? */
        boolean overlaps(Party other) {
            return player.equals(other.player) || phoneId.equals(other.phoneId) || simId.equals(other.simId);
        }

        @Override
        public String toString() {
            return "Party[player=" + player + ", phoneId=***, simId=***, number=***]";
        }
    }

    public enum State { RINGING, ACTIVE }

    /** Pourquoi un appel a pris fin. */
    public enum End {
        /** L'un des deux a raccroché (l'appelant pendant la sonnerie, ou l'un ou l'autre en ligne). */
        HUNG_UP,
        /** L'appelé a refusé pendant la sonnerie. */
        DECLINED,
        /** La sonnerie a duré {@code callRingSeconds} sans réponse. */
        NO_ANSWER,
        /** Plus de trajet : zéro barre d'un côté ou de l'autre, mode avion, antenne tombée. */
        NO_SIGNAL,
        /** L'un des deux s'est déconnecté. */
        PARTY_LEFT,
        /** Téléphone jeté ou rangé ailleurs, carte retirée ou plus active, batterie vide. */
        PHONE_GONE,
        DIED,
        CHANGED_DIMENSION,
        SERVER_STOPPING
    }

    /**
     * Un appel. {@code answeredTick} vaut -1 tant que l'appelé n'a pas décroché. Les tics sont ceux du serveur
     * ({@code MinecraftServer.getTickCount}).
     */
    public record Call(int id, Party caller, Party callee, State state, long startedTick, long answeredTick,
            boolean callerMuted, boolean calleeMuted, boolean callerSpeaker, boolean calleeSpeaker) {

        public Call {
            Objects.requireNonNull(caller, "caller");
            Objects.requireNonNull(callee, "callee");
            Objects.requireNonNull(state, "state");
        }

        /** Ce joueur est-il l'un des deux bouts ? */
        public boolean involves(UUID player) {
            return caller.player().equals(player) || callee.player().equals(player);
        }

        /** Le bout de ce joueur, s'il en est un. */
        public Optional<Party> partyOf(UUID player) {
            if (caller.player().equals(player)) {
                return Optional.of(caller);
            }
            return callee.player().equals(player) ? Optional.of(callee) : Optional.empty();
        }

        /** L'autre bout, vu de ce joueur. */
        public Optional<Party> otherOf(UUID player) {
            if (caller.player().equals(player)) {
                return Optional.of(callee);
            }
            return callee.player().equals(player) ? Optional.of(caller) : Optional.empty();
        }

        boolean overlaps(Party party) {
            return caller.overlaps(party) || callee.overlaps(party);
        }

        Call answered(long tick) {
            return new Call(id, caller, callee, State.ACTIVE, startedTick, tick, callerMuted, calleeMuted,
                    callerSpeaker, calleeSpeaker);
        }

        Call muted(UUID player, boolean muted) {
            boolean isCaller = caller.player().equals(player);
            return new Call(id, caller, callee, state, startedTick, answeredTick, isCaller ? muted : callerMuted,
                    isCaller ? calleeMuted : muted, callerSpeaker, calleeSpeaker);
        }

        Call speaker(UUID player, boolean speaker) {
            boolean isCaller = caller.player().equals(player);
            return new Call(id, caller, callee, state, startedTick, answeredTick, callerMuted, calleeMuted,
                    isCaller ? speaker : callerSpeaker, isCaller ? calleeSpeaker : speaker);
        }
    }

    private final Map<Integer, Call> calls = new LinkedHashMap<>();
    private int nextId = 1;

    /**
     * Un appel qui sonne. Vide, sans rien changer, si l'appelant et l'appelé partagent le joueur, le téléphone ou la
     * carte, ou si l'un des deux (par joueur, téléphone ou carte) est déjà dans un appel, d'un côté ou de l'autre.
     */
    public Optional<Call> start(Party caller, Party callee, long tick) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(callee, "callee");
        if (caller.overlaps(callee)) {
            return Optional.empty();
        }
        for (Call call : calls.values()) {
            if (call.overlaps(caller) || call.overlaps(callee)) {
                return Optional.empty();
            }
        }
        int id = takeId();
        Call call = new Call(id, caller, callee, State.RINGING, tick, -1L, false, false, false, false);
        calls.put(id, call);
        return Optional.of(call);
    }

    /** Un numéro libre, jamais 0 ni négatif ; les numéros rendus ne reviennent qu'après un tour complet. */
    private int takeId() {
        while (calls.containsKey(nextId)) {
            nextId = nextId == Integer.MAX_VALUE ? 1 : nextId + 1;
        }
        int id = nextId;
        nextId = nextId == Integer.MAX_VALUE ? 1 : nextId + 1;
        return id;
    }

    /** L'appelé décroche ; vide si l'appel n'existe pas, ne sonne plus, ou si {@code player} n'en est pas l'appelé. */
    public Optional<Call> answer(int id, UUID player, long tick) {
        Call call = calls.get(id);
        if (call == null || call.state() != State.RINGING || !call.callee().player().equals(player)) {
            return Optional.empty();
        }
        Call answered = call.answered(tick);
        calls.put(id, answered);
        return Optional.of(answered);
    }

    /** Retire l'appel et le rend tel qu'il était ; vide s'il n'existe pas (déjà fini). */
    public Optional<Call> end(int id) {
        return Optional.ofNullable(calls.remove(id));
    }

    public Optional<Call> get(int id) {
        return Optional.ofNullable(calls.get(id));
    }

    /** L'appel où ce joueur est appelant ou appelé. */
    public Optional<Call> of(UUID player) {
        for (Call call : calls.values()) {
            if (call.involves(player)) {
                return Optional.of(call);
            }
        }
        return Optional.empty();
    }

    /** Coupe ou rend le micro de ce joueur dans son appel ; vide s'il n'est pas en appel. */
    public Optional<Call> setMuted(UUID player, boolean muted) {
        return update(player, call -> call.muted(player, muted));
    }

    /** Met ou retire le haut-parleur de ce joueur dans son appel ; vide s'il n'est pas en appel. */
    public Optional<Call> setSpeaker(UUID player, boolean speaker) {
        return update(player, call -> call.speaker(player, speaker));
    }

    private Optional<Call> update(UUID player, UnaryOperator<Call> change) {
        Optional<Call> call = of(player);
        if (call.isEmpty()) {
            return Optional.empty();
        }
        Call changed = change.apply(call.get());
        calls.put(changed.id(), changed);
        return Optional.of(changed);
    }

    /**
     * Les appels qui sonnent depuis {@code ringTicks} tics ou plus à {@code tick} (la borne comprise), par ordre de
     * début. Rien n'est retiré.
     */
    public List<Call> expired(long tick, int ringTicks) {
        List<Call> out = new ArrayList<>();
        for (Call call : calls.values()) {
            if (call.state() == State.RINGING && tick - call.startedTick() >= ringTicks) {
                out.add(call);
            }
        }
        return List.copyOf(out);
    }

    /** Tous les appels, par ordre de début. Copie non modifiable. */
    public List<Call> all() {
        return List.copyOf(calls.values());
    }

    /** Oublie tous les appels (arrêt du serveur, après leur fin). */
    public void clear() {
        calls.clear();
    }
}
