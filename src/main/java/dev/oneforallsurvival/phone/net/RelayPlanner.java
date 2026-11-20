package dev.oneforallsurvival.phone.net;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Qui relaie un appel : choisit l'antenne de chaque bout, ou dit pourquoi l'appel ne passe pas. Règle pure, sur un
 * instantané du réseau (aucune lecture du monde).
 */
public final class RelayPlanner {

    private RelayPlanner() {}

    /** Une antenne du réseau, telle que le serveur la voit à l'instant de l'instantané. */
    public record AntennaPoint(UUID id, String operatorId, ResourceKey<Level> dimension, BlockPos pos,
            boolean inService) {

        public AntennaPoint {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(operatorId, "operatorId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
        }
    }

    /** Un bout de l'appel : l'opérateur de la carte et l'endroit où se tient le téléphone. */
    public record Endpoint(String operatorId, ResourceKey<Level> dimension, BlockPos pos) {

        public Endpoint {
            Objects.requireNonNull(operatorId, "operatorId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
        }
    }

    /**
     * Le réseau : toutes les antennes, les noms des opérateurs qui existent et la portée. Un opérateur absent de
     * {@code operatorNames} est supprimé.
     */
    public record Snapshot(List<AntennaPoint> antennas, Map<String, String> operatorNames, int range) {

        public Snapshot {
            antennas = List.copyOf(antennas);
            operatorNames = Map.copyOf(operatorNames);
        }
    }

    public sealed interface Result permits Route, Failed {}

    /** L'appel passe : l'antenne de l'appelant et celle de l'appelé (la même si les deux sont sous la même). */
    public record Route(AntennaPoint caller, AntennaPoint callee) implements Result {

        public Route {
            Objects.requireNonNull(caller, "caller");
            Objects.requireNonNull(callee, "callee");
        }
    }

    /**
     * L'appel ne passe pas. {@code operatorName} n'est renseigné que pour {@code CALLEE_ANTENNA_DOWN} (nom de
     * l'opérateur de l'appelé, pour le message) ; vide sinon.
     */
    public record Failed(RelayFailure.Reason reason, String operatorName) implements Result {

        public Failed {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(operatorName, "operatorName");
        }
    }

    /**
     * Le plan d'un appel, dans cet ordre : opérateur de l'appelant supprimé, appelant sans antenne en service à
     * portée, opérateur de l'appelé supprimé, appelé à portée d'antennes dont aucune n'est en service, appelé sans
     * aucune antenne à portée ; sinon la route.
     */
    public static Result plan(Endpoint caller, Endpoint callee, Snapshot net) {
        if (!net.operatorNames().containsKey(caller.operatorId())) {
            return new Failed(RelayFailure.Reason.CALLER_OPERATOR_REMOVED, "");
        }
        Optional<AntennaPoint> callerAntenna = best(caller, net);
        if (callerAntenna.isEmpty()) {
            return new Failed(RelayFailure.Reason.CALLER_NO_SIGNAL, "");
        }
        String calleeOperator = net.operatorNames().get(callee.operatorId());
        if (calleeOperator == null) {
            return new Failed(RelayFailure.Reason.CALLEE_OPERATOR_REMOVED, "");
        }
        Optional<AntennaPoint> calleeAntenna = best(callee, net);
        if (calleeAntenna.isPresent()) {
            return new Route(callerAntenna.get(), calleeAntenna.get());
        }
        boolean someInRange = net.antennas().stream()
                .anyMatch(a -> a.operatorId().equals(callee.operatorId()) && inRange(a, callee.dimension(), callee.pos(), net));
        return new Failed(someInRange ? RelayFailure.Reason.CALLEE_ANTENNA_DOWN : RelayFailure.Reason.CALLEE_NO_COVERAGE,
                someInRange ? calleeOperator : "");
    }

    /**
     * La meilleure antenne pour ce bout : en service, de son opérateur, dans sa dimension et à portée, la plus
     * proche ; à distance égale, l'identifiant le plus petit ({@link UUID#compareTo}). Vide si l'opérateur est
     * supprimé ou si aucune ne convient.
     */
    public static Optional<AntennaPoint> best(Endpoint at, Snapshot net) {
        if (!net.operatorNames().containsKey(at.operatorId())) {
            return Optional.empty();
        }
        return nearest(at.dimension(), at.pos(), net, a -> a.operatorId().equals(at.operatorId()));
    }

    /**
     * Pour les urgences : la meilleure antenne en service de n'importe quel opérateur existant (celles d'un
     * opérateur supprimé n'existent plus pour le réseau).
     */
    public static Optional<AntennaPoint> anyInService(ResourceKey<Level> dimension, BlockPos pos, Snapshot net) {
        return nearest(dimension, pos, net, a -> net.operatorNames().containsKey(a.operatorId()));
    }

    private static Optional<AntennaPoint> nearest(ResourceKey<Level> dimension, BlockPos pos, Snapshot net,
            Predicate<AntennaPoint> wanted) {
        return net.antennas().stream()
                .filter(AntennaPoint::inService)
                .filter(wanted)
                .filter(a -> inRange(a, dimension, pos, net))
                .min(Comparator.<AntennaPoint>comparingDouble(a -> SignalMath.distance(a.pos(), pos))
                        .thenComparing(AntennaPoint::id));
    }

    private static boolean inRange(AntennaPoint a, ResourceKey<Level> dimension, BlockPos pos, Snapshot net) {
        return a.dimension().equals(dimension) && SignalMath.bars(SignalMath.distance(a.pos(), pos), net.range()) > 0;
    }
}
