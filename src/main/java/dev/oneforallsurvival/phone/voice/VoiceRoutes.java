package dev.oneforallsurvival.phone.voice;

import dev.oneforallsurvival.phone.call.CallBook;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Instantané immuable des routes de la voix : pour chaque parleur, les flux par lesquels sa voix part. Calculé sur le
 * fil du serveur ({@link #compute}, pur), lu par le fil vocal ({@link VoiceRouter}) sans rien toucher du monde.
 *
 * <p>Pour un appel décroché entre A et B (B symétrique) :
 * <ul>
 *   <li>la voix de A part à B, sauf si A est en silence ; en privé, ou par le haut-parleur de B s'il l'a mis ;
 *   <li>haut-parleur de A : la voix de B sort du téléphone de A (canal posé sur A, entendu à {@code hear} blocs par
 *       tous sauf B), et A ne la reçoit plus en privé ; si A n'est pas en silence, la voix de chaque joueur C
 *       (ni A ni B), même dimension, à {@code capture} blocs de A au plus, hors groupe vocal fermé, part aussi à B ;
 *   <li>une barre d'un côté (ou moins) : tous les flux de l'appel grésillent.
 * </ul>
 * La proximité de Simple Voice Chat n'est jamais touchée : ces flux s'y ajoutent.
 */
public final class VoiceRoutes {

    public static final VoiceRoutes EMPTY = new VoiceRoutes(Map.of(), Map.of());

    /** Privé : à l'auditeur seul. Haut-parleur : posé sur le téléphone de l'auditeur, audible autour de lui. */
    public enum Kind { PRIVATE, SPEAKER }

    /**
     * Un flux : la voix de {@code speaker} vers {@code listener} dans l'appel {@code callId}. Pour un haut-parleur,
     * {@code listener} est le porteur du téléphone d'où sort la voix. Une clé = un canal de Simple Voice Chat.
     */
    public record FlowKey(int callId, UUID speaker, UUID listener, Kind kind) {

        public FlowKey {
            Objects.requireNonNull(speaker, "speaker");
            Objects.requireNonNull(listener, "listener");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * Un flux et ce qu'il faut pour le jouer. {@code at}, {@code excluded} et {@code hearBlocks} ne servent qu'au
     * haut-parleur ({@code at} nul et {@code excluded} vide en privé).
     */
    public record Flow(FlowKey key, @Nullable Spot at, Set<UUID> excluded, int hearBlocks, boolean crackle) {

        public Flow {
            Objects.requireNonNull(key, "key");
            excluded = Set.copyOf(excluded);
        }
    }

    /** Un bout d'un appel décroché, tel que le serveur le voit à la publication. */
    public record Side(UUID player, Spot spot, int bars, boolean muted, boolean speaker) {

        public Side {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(spot, "spot");
        }
    }

    /** Où est un bout et combien de barres il a (lu sur le fil du serveur). */
    public record Presence(Spot spot, int bars) {

        public Presence {
            Objects.requireNonNull(spot, "spot");
        }
    }

    /** Un joueur connecté qui peut être capté par un haut-parleur ou l'entendre. */
    public record Bystander(UUID player, Spot spot, boolean closedGroup) {

        public Bystander {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(spot, "spot");
        }
    }

    /** Un appel décroché et ses deux bouts. */
    public record CallSides(int id, Side caller, Side callee) {

        public CallSides {
            Objects.requireNonNull(caller, "caller");
            Objects.requireNonNull(callee, "callee");
        }

        /**
         * Les bouts d'un appel du carnet : vide s'il sonne encore, ou si l'un des bouts n'a pas de présence (absent,
         * téléphone introuvable). Silence et haut-parleur viennent du carnet.
         */
        public static Optional<CallSides> of(CallBook.Call call, Function<CallBook.Party, Optional<Presence>> where) {
            if (call.state() != CallBook.State.ACTIVE) {
                return Optional.empty();
            }
            Optional<Presence> caller = where.apply(call.caller());
            Optional<Presence> callee = where.apply(call.callee());
            if (caller.isEmpty() || callee.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CallSides(call.id(),
                    new Side(call.caller().player(), caller.get().spot(), caller.get().bars(), call.callerMuted(),
                            call.callerSpeaker()),
                    new Side(call.callee().player(), callee.get().spot(), callee.get().bars(), call.calleeMuted(),
                            call.calleeSpeaker())));
        }
    }

    private final Map<UUID, List<Flow>> bySpeaker;
    /** Positions des joueurs à la publication : le filtre du haut-parleur les lit, jamais le monde. */
    private final Map<UUID, Spot> spots;
    private final Set<FlowKey> keys;

    private VoiceRoutes(Map<UUID, List<Flow>> bySpeaker, Map<UUID, Spot> spots) {
        Map<UUID, List<Flow>> copy = new HashMap<>();
        Set<FlowKey> all = new LinkedHashSet<>();
        bySpeaker.forEach((speaker, flows) -> {
            copy.put(speaker, List.copyOf(flows));
            flows.forEach(f -> all.add(f.key()));
        });
        this.bySpeaker = Map.copyOf(copy);
        this.spots = Map.copyOf(spots);
        this.keys = Set.copyOf(all);
    }

    /**
     * Calcule les routes. {@code around} : les joueurs connectés (les bouts peuvent y être, ils ne sont jamais captés
     * comme voisins de leur propre appel) ; {@code hear} et {@code capture} : distances du haut-parleur, en blocs.
     */
    public static VoiceRoutes compute(Collection<CallSides> calls, Collection<Bystander> around, int hear,
            int capture) {
        Map<UUID, List<Flow>> bySpeaker = new HashMap<>();
        Map<UUID, Spot> spots = new HashMap<>();
        for (Bystander b : around) {
            spots.put(b.player(), b.spot());
        }
        for (CallSides call : calls) {
            spots.put(call.caller().player(), call.caller().spot());
            spots.put(call.callee().player(), call.callee().spot());
        }
        for (CallSides call : calls) {
            boolean crackle = Math.min(call.caller().bars(), call.callee().bars()) <= 1;
            direction(call, call.caller(), call.callee(), around, hear, capture, crackle, bySpeaker);
            direction(call, call.callee(), call.caller(), around, hear, capture, crackle, bySpeaker);
        }
        return new VoiceRoutes(bySpeaker, spots);
    }

    /** Ce qui part du côté de {@code from} vers {@code to} : sa voix, et celles que son haut-parleur capte. */
    private static void direction(CallSides call, Side from, Side to, Collection<Bystander> around, int hear,
            int capture, boolean crackle, Map<UUID, List<Flow>> bySpeaker) {
        if (from.muted()) {
            return;
        }
        deliver(call.id(), from.player(), from, to, hear, crackle, bySpeaker);
        if (!from.speaker()) {
            return;
        }
        for (Bystander c : around) {
            UUID id = c.player();
            if (id.equals(from.player()) || id.equals(to.player()) || c.closedGroup()
                    || !c.spot().within(from.spot(), capture)) {
                continue;
            }
            deliver(call.id(), id, from, to, hear, crackle, bySpeaker);
        }
    }

    /** La voix de {@code speaker}, venue du côté {@code from}, arrive à {@code to} : en privé ou par son haut-parleur. */
    private static void deliver(int callId, UUID speaker, Side from, Side to, int hear, boolean crackle,
            Map<UUID, List<Flow>> bySpeaker) {
        Flow flow;
        if (to.speaker()) {
            Set<UUID> excluded = new LinkedHashSet<>();
            excluded.add(from.player());
            excluded.add(speaker);
            flow = new Flow(new FlowKey(callId, speaker, to.player(), Kind.SPEAKER), to.spot(), excluded, hear,
                    crackle);
        } else {
            flow = new Flow(new FlowKey(callId, speaker, to.player(), Kind.PRIVATE), null, Set.of(), 0, crackle);
        }
        bySpeaker.computeIfAbsent(speaker, k -> new ArrayList<>()).add(flow);
    }

    /** Les flux de la voix de {@code speaker} (liste immuable, vide s'il n'en a aucun). */
    public List<Flow> from(UUID speaker) {
        return bySpeaker.getOrDefault(speaker, List.of());
    }

    /** Toutes les clés de l'instantané. */
    public Set<FlowKey> keys() {
        return keys;
    }

    /**
     * Ce joueur entend-il ce flux de haut-parleur ? Hors des exclus, connu de l'instantané, même dimension et à
     * {@code hearBlocks} au plus du téléphone (borne comprise). Un flux privé ne s'entend que par son auditeur.
     */
    public boolean hears(Flow flow, UUID listener) {
        if (flow.key().kind() == Kind.PRIVATE) {
            return flow.key().listener().equals(listener);
        }
        if (flow.excluded().contains(listener) || flow.at() == null) {
            return false;
        }
        Spot spot = spots.get(listener);
        return spot != null && spot.within(flow.at(), flow.hearBlocks());
    }

    /** {@link #hears(Flow, UUID)} pour le flux de cette clé ; faux si l'instantané ne l'a pas (appel fini). */
    public boolean hears(FlowKey key, UUID listener) {
        for (Flow flow : from(key.speaker())) {
            if (flow.key().equals(key)) {
                return hears(flow, listener);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "VoiceRoutes[" + keys.size() + " flux]";
    }
}
