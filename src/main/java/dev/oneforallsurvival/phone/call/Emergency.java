package dev.oneforallsurvival.phone.call;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Les règles pures des appels d'urgence (feature 95, lot T3, tâche 8) : les deux services, le délai entre deux appels
 * d'un même joueur, et l'appel qui sonne chez plusieurs secours à la fois, dont le premier qui répond prend l'appel.
 * Aucun monde ici : {@link EmergencyService} s'en sert sur le fil du serveur.
 */
public final class Emergency {

    private Emergency() {}

    /** Les numéros d'urgence et le rôle qui y répond. */
    public enum Service {
        POLICE("17", "policier", "police"),
        MEDICAL("15", "medecin", "medical");

        private final String number;
        private final String role;
        private final String key;

        Service(String number, String role, String key) {
            this.number = number;
            this.role = role;
            this.key = key;
        }

        /** Le numéro composé : {@code 17} ou {@code 15}. */
        public String number() {
            return number;
        }

        /** L'identifiant du rôle des secours ({@code RoleBook}). */
        public String role() {
            return role;
        }

        /** Le suffixe des clés de langue : {@code police} ou {@code medical}. */
        public String key() {
            return key;
        }

        /** Le service de ce numéro (espaces ignorés), s'il en est un. Les chiffres non ASCII ne comptent pas. */
        public static Optional<Service> of(@Nullable String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String digits = raw.replace(" ", "").toLowerCase(Locale.ROOT);
            for (Service s : values()) {
                if (s.number.equals(digits)) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Un appel d'urgence par joueur toutes les {@code cooldownTicks} tics. Le délai court depuis le dernier appel
     * accepté ; une horloge qui recule (serveur relancé) le remet à zéro. Gardé à la déconnexion.
     */
    public static final class Cooldown {

        private final Map<UUID, Long> last = new HashMap<>();

        /** Accepte (et retient) un appel de ce joueur à {@code tick}, ou le refuse sans rien changer. */
        public boolean allow(UUID player, long tick, long cooldownTicks) {
            Long before = last.get(player);
            if (before != null && tick >= before && tick - before < cooldownTicks) {
                return false;
            }
            last.put(player, tick);
            return true;
        }

        /** Oublie les entrées sorties du délai (ou d'une horloge qui a reculé). */
        public void purge(long tick, long cooldownTicks) {
            last.values().removeIf(t -> tick < t || tick - t >= cooldownTicks);
        }

        public int tracked() {
            return last.size();
        }

        public void clear() {
            last.clear();
        }
    }

    /**
     * Un appel d'urgence qui sonne : l'appelant, le service, et les secours dont le téléphone sonne. Le premier d'entre
     * eux qui répond le prend ({@link #take}) ; personne d'autre ne peut plus le prendre.
     */
    public static final class Dispatch {

        private final int id;
        private final UUID caller;
        private final Service service;
        private final long startedTick;
        private final Set<UUID> ringing = new LinkedHashSet<>();
        private @Nullable UUID taker;

        public Dispatch(int id, UUID caller, Service service, long startedTick, List<UUID> responders) {
            this.id = id;
            this.caller = Objects.requireNonNull(caller, "caller");
            this.service = Objects.requireNonNull(service, "service");
            this.startedTick = startedTick;
            for (UUID r : responders) {
                if (!r.equals(caller)) {
                    ringing.add(r);
                }
            }
        }

        public int id() {
            return id;
        }

        public UUID caller() {
            return caller;
        }

        public Service service() {
            return service;
        }

        public long startedTick() {
            return startedTick;
        }

        /** Les secours dont le téléphone sonne encore (copie). */
        public Set<UUID> ringing() {
            return Set.copyOf(ringing);
        }

        public boolean ringsFor(UUID responder) {
            return taker == null && ringing.contains(responder);
        }

        /** Ce secours ne sonne plus (refus, téléphone perdu). Faux s'il ne sonnait pas. */
        public boolean drop(UUID responder) {
            return ringing.remove(responder);
        }

        /**
         * Ce secours répond. Vrai s'il est le premier : il prend l'appel, et plus rien ne sonne chez les autres. Faux
         * s'il ne sonnait pas ou si quelqu'un l'a déjà pris.
         */
        public boolean take(UUID responder) {
            if (taker != null || !ringing.contains(responder)) {
                return false;
            }
            taker = responder;
            ringing.remove(responder);
            return true;
        }

        public Optional<UUID> taker() {
            return Optional.ofNullable(taker);
        }

        /** La sonnerie a-t-elle duré {@code ringTicks} tics à {@code tick} (borne comprise) ? */
        public boolean expired(long tick, int ringTicks) {
            return tick - startedTick >= ringTicks;
        }

        public boolean involves(UUID player) {
            return caller.equals(player) || ringing.contains(player);
        }

        @Override
        public String toString() {
            return "Dispatch[id=" + id + ", service=" + service + ", ringing=" + ringing.size() + "]";
        }
    }
}
