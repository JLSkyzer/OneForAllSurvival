package dev.oneforallsurvival.phone.net;

import com.mojang.datafixers.util.Either;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * La façade du réseau téléphonique (lot T2, tâche 6), ce que le lot T3 (appels, messages, urgences) consomme : le
 * statut d'un téléphone, le trajet d'un appel d'un opérateur à l'autre, l'antenne d'urgence, le trafic qui fait
 * consommer les antennes et l'état du relais pour le panneau.
 *
 * <p>Tout se lit dans le registre ({@link PhoneOperators}) : une antenne est « en service » d'après ce qu'elle y a noté
 * ({@link AntennaBlockEntity#inService}), jamais en allant voir son bloc. Rien ici ne charge un chunk : seul
 * {@link #messageSent} touche un bloc, et seulement si son chunk est déjà chargé. Aucun état statique : le trafic vit
 * dans le registre du monde ({@link PhoneOperators#traffic}), transitoire, et disparaît avec lui.
 *
 * <p>Fil du serveur seulement. Ni jeton, ni identifiant de carte, ni numéro n'est écrit dans un journal ou une
 * exception.
 */
public final class PhoneNetwork {

    /** Ce que le panneau montre d'un opérateur : ses antennes, et combien sont en service. */
    public record RelayState(String operatorId, String name, int antennas, int inService) {}

    private PhoneNetwork() {}

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /**
     * Le réseau à cet instant : toutes les antennes inscrites (en service d'après {@link AntennaBlockEntity#inService}),
     * les opérateurs qui existent et la portée de la configuration. Les antennes d'un opérateur supprimé y sont, mais
     * le plan les ignore (et, n'ayant plus d'opérateur, elles ne tournent plus).
     */
    public static RelayPlanner.Snapshot snapshot(MinecraftServer server, long gameTime) {
        PhoneOperators registry = PhoneOperators.get(server);
        List<RelayPlanner.AntennaPoint> points = new ArrayList<>();
        for (AntennaRecord r : registry.antennas()) {
            points.add(new RelayPlanner.AntennaPoint(r.id(), r.operatorId(), r.dimension(), r.pos(),
                    AntennaBlockEntity.inService(r, gameTime)));
        }
        Map<String, String> names = new HashMap<>();
        for (PhoneOperator op : registry.all()) {
            names.put(op.id(), op.name());
        }
        return new RelayPlanner.Snapshot(points, names, PhoneConfig.antennaRange());
    }

    /**
     * La carte authentique logée dans ce téléphone, quel que soit son état (vide sans carte, ou si elle est morte :
     * inconnue, ancien jeton, liée à un autre téléphone).
     */
    private static Optional<SimCard> lodged(MinecraftServer server, PhoneOperators registry, ItemStack phone) {
        if (phone == null) {
            return Optional.empty();
        }
        PhoneSims.Validity validity = PhoneSims.check(server, phone);
        if (validity == PhoneSims.Validity.NONE || validity == PhoneSims.Validity.UNKNOWN) {
            return Optional.empty();
        }
        return SimCardItem.simId(PhoneItem.sim(phone)).flatMap(registry::card);
    }

    // ---------------------------------------------------------------------------------------------
    // Statut
    // ---------------------------------------------------------------------------------------------

    /**
     * Ce que le téléphone {@code phone}, porté par {@code holder}, montre du réseau. Numéro, titulaire et opérateur ne
     * sont remplis que s'il a une ligne (carte {@code ACTIVE} ou {@code SUSPENDED}, {@link PhoneSims#line}). Les barres
     * sont celles de l'antenne en service de son opérateur la plus proche ; 0 en mode avion, sans carte {@code ACTIVE}
     * (une suspendue garde son opérateur affiché, sans barre) ou sans antenne en service à portée.
     */
    public static NetworkStatus status(ServerPlayer holder, ItemStack phone, boolean airplane) {
        MinecraftServer server = holder.server;
        PhoneSims.Validity sim = phone == null ? PhoneSims.Validity.NONE : PhoneSims.check(server, phone);
        Optional<SimCard> line = phone == null ? Optional.empty() : PhoneSims.line(server, phone);
        if (line.isEmpty()) {
            return new NetworkStatus(sim, "", "", "", 0, airplane);
        }
        SimCard card = line.get();
        PhoneOperators registry = PhoneOperators.get(server);
        String operatorName = registry.byId(card.operatorId()).map(PhoneOperator::name).orElse("");
        int bars = 0;
        if (!airplane && sim == PhoneSims.Validity.ACTIVE) {
            RelayPlanner.Endpoint here = new RelayPlanner.Endpoint(card.operatorId(), holder.level().dimension(),
                    holder.blockPosition());
            RelayPlanner.Snapshot net = snapshot(server, now(server));
            bars = RelayPlanner.best(here, net)
                    .map(a -> SignalMath.bars(SignalMath.distance(a.pos(), here.pos()), net.range()))
                    .orElse(0);
        }
        return new NetworkStatus(sim, card.number(), card.holderName(), operatorName, bars, airplane);
    }

    // ---------------------------------------------------------------------------------------------
    // Appels
    // ---------------------------------------------------------------------------------------------

    /**
     * La ligne de ce numéro (avec ou sans espaces) : sa carte, si elle existe, n'est pas désactivée et que son opérateur
     * existe ; {@link SimCard#phoneId()} dit dans quel téléphone elle est (le dernier où elle a été glissée avec le bon
     * jeton ; nul si elle n'est dans aucun). Une carte suspendue a une ligne : elle n'appelle plus (sauf les urgences,
     * côté T3) et ne reçoit plus.
     *
     * <p>La carte rendue est celle du registre, <b>jeton compris</b> (masqué par {@link SimCard#toString()} seulement)
     * et UUID du titulaire : le lot T3 n'en sérialise rien directement (paquet, JSON de page, journal) ; il passe par
     * un enregistrement réduit ({@code phoneId}, {@code holderName}, état) et n'expose jamais {@code lineOf} à un client
     * avant {@link #route}.
     */
    public static Optional<SimCard> lineOf(MinecraftServer server, String number) {
        if (number == null) {
            return Optional.empty();
        }
        PhoneOperators registry = PhoneOperators.get(server);
        return registry.cardByNumber(number)
                .filter(card -> card.state() != SimCard.State.DEACTIVATED)
                .filter(card -> registry.byId(card.operatorId()).isPresent());
    }

    /**
     * Le trajet d'un appel de {@code caller} (son téléphone {@code callerPhone}) vers le numéro {@code calleeNumber},
     * dont le téléphone est en {@code calleePos} (retrouver l'appelé est le travail du lot T3).
     *
     * <p>Dans cet ordre : mode avion ; carte de l'appelant (opérateur supprimé, puis carte qui n'est pas
     * {@code ACTIVE}) ; signal de l'appelant (avant de rien dire de l'appelé : sans réseau, on n'apprend rien d'un
     * numéro) ; carte de l'appelé (numéro inconnu, opérateur supprimé, carte désactivée = numéro inconnu, carte
     * suspendue) ; puis {@link RelayPlanner#plan} sur l'instantané du réseau.
     *
     * <p>{@code calleePos} nul (le lot T3 n'a pas retrouvé le téléphone de l'appelé : porteur hors ligne, téléphone dans
     * un chunk déchargé) donne {@code CALLEE_NO_COVERAGE}, jamais une exception. Le trajet rendu est figé : le lot T3
     * refait {@code route} à chaque relevé et rouvre sa poignée de trafic si les antennes changent (changement de
     * dimension, déplacement).
     */
    public static Either<RelayPlanner.Route, RelayFailure> route(MinecraftServer server, ServerPlayer caller,
            ItemStack callerPhone, boolean airplane, String calleeNumber, @Nullable GlobalPos calleePos) {
        if (airplane) {
            return fail(RelayFailure.Reason.CALLER_AIRPLANE);
        }
        PhoneOperators registry = PhoneOperators.get(server);
        Optional<SimCard> mine = lodged(server, registry, callerPhone);
        if (mine.isPresent() && registry.byId(mine.get().operatorId()).isEmpty()) {
            return fail(RelayFailure.Reason.CALLER_OPERATOR_REMOVED);
        }
        if (mine.isEmpty() || mine.get().state() != SimCard.State.ACTIVE) {
            return fail(RelayFailure.Reason.CALLER_SIM_INACTIVE);
        }
        RelayPlanner.Snapshot net = snapshot(server, now(server));
        RelayPlanner.Endpoint from = new RelayPlanner.Endpoint(mine.get().operatorId(), caller.level().dimension(),
                caller.blockPosition());
        if (RelayPlanner.best(from, net).isEmpty()) {
            return fail(RelayFailure.Reason.CALLER_NO_SIGNAL);
        }
        Optional<SimCard> callee = calleeNumber == null ? Optional.empty() : registry.cardByNumber(calleeNumber);
        if (callee.isEmpty()) {
            return fail(RelayFailure.Reason.CALLEE_UNKNOWN);
        }
        if (registry.byId(callee.get().operatorId()).isEmpty()) {
            return fail(RelayFailure.Reason.CALLEE_OPERATOR_REMOVED);
        }
        switch (callee.get().state()) {
            case DEACTIVATED -> {
                return fail(RelayFailure.Reason.CALLEE_UNKNOWN);
            }
            case SUSPENDED -> {
                return fail(RelayFailure.Reason.CALLEE_SIM_INACTIVE);
            }
            case ACTIVE -> { }
        }
        if (calleePos == null) {
            // Le téléphone de l'appelé n'est nulle part dans le monde (porteur hors ligne, chunk déchargé) : hors réseau,
            // sans rien dire de plus.
            return fail(RelayFailure.Reason.CALLEE_NO_COVERAGE);
        }
        RelayPlanner.Endpoint to = new RelayPlanner.Endpoint(callee.get().operatorId(), calleePos.dimension(),
                calleePos.pos());
        RelayPlanner.Result result = RelayPlanner.plan(from, to, net);
        if (result instanceof RelayPlanner.Route r) {
            return Either.left(r);
        }
        return Either.right(RelayFailure.of((RelayPlanner.Failed) result));
    }

    private static Either<RelayPlanner.Route, RelayFailure> fail(RelayFailure.Reason reason) {
        return Either.right(new RelayFailure(reason, ""));
    }

    /**
     * Pour les urgences : la meilleure antenne en service, de n'importe quel opérateur existant, à portée de
     * {@code pos} ; aucune carte n'est demandée. L'antenne d'un opérateur supprimé ne compte pas : elle ne consomme
     * plus, donc ne tourne plus, donc n'est jamais en service ({@link RelayPlanner#anyInService}).
     */
    public static Optional<RelayPlanner.AntennaPoint> emergencyAntenna(MinecraftServer server, GlobalPos pos) {
        return RelayPlanner.anyInService(pos.dimension(), pos.pos(), snapshot(server, now(server)));
    }

    /**
     * Les barres d'un téléphone en appel d'urgence : celles de l'antenne d'urgence la plus proche ({@link
     * #emergencyAntenna}), 0 en mode avion ou sans antenne à portée.
     */
    public static int emergencyBars(MinecraftServer server, GlobalPos pos, boolean airplane) {
        if (airplane) {
            return 0;
        }
        RelayPlanner.Snapshot net = snapshot(server, now(server));
        return RelayPlanner.anyInService(pos.dimension(), pos.pos(), net)
                .map(a -> SignalMath.bars(SignalMath.distance(a.pos(), pos.pos()), net.range()))
                .orElse(0);
    }

    // ---------------------------------------------------------------------------------------------
    // Trafic
    // ---------------------------------------------------------------------------------------------

    /**
     * Ouvre ce trafic sur les antennes du trajet : chacune le compte dans sa consommation ({@code antennaCallFe} ou
     * {@code antennaFacetimeFe} par tic) tant que la poignée est ouverte. Une antenne qui relaie les deux bouts ne le
     * compte qu'une fois. Transitoire : rien sur le disque.
     *
     * <p><b>Contrat pour le lot T3.</b> La session d'appel possède sa poignée et la ferme en un seul point (le
     * {@code finally} de la fin d'appel), atteint dans tous ces cas : raccrocher, refus, zéro barre, déconnexion de
     * l'un ou l'autre ({@code PlayerLoggedOutEvent}), mort, changement de dimension, téléphone ou carte retiré,
     * arrêt du serveur ({@code ServerStoppingEvent}). Ouverture et fermeture sur le fil du serveur seulement. T2
     * n'a aucun garde-fou (une durée maximale couperait les appels longs, une purge sur l'état de l'antenne
     * désynchroniserait les compteurs) : une poignée oubliée fait payer chaque antenne du trajet 9 FE par tic de plus
     * (24 pour une visio) jusqu'au rechargement du monde, soit environ 15,5 millions de FE par jour réel pour un appel
     * (9 × 20 × 86 400 : calcul, pas une mesure).
     */
    public static TrafficHandle openTraffic(MinecraftServer server, RelayPlanner.Route route, TrafficKind kind) {
        return openTraffic(PhoneOperators.get(server), route, kind);
    }

    static TrafficHandle openTraffic(PhoneOperators registry, RelayPlanner.Route route, TrafficKind kind) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(kind, "kind");
        return new TrafficHandle(registry, antennasOf(route), kind);
    }

    /** Les antennes d'un trajet, chacune une fois. */
    private static List<UUID> antennasOf(RelayPlanner.Route route) {
        UUID caller = route.caller().id();
        UUID callee = route.callee().id();
        return caller.equals(callee) ? List.of(caller) : List.of(caller, callee);
    }

    /**
     * Un message est parti par ce trajet : {@code antennaMessageFe} est retiré une fois de la réserve de chaque antenne
     * du trajet (une antenne qui relaie les deux bouts ne paie qu'une fois). Une antenne dont le chunk n'est pas chargé
     * n'est pas touchée (aucun chargement forcé) ; une réserve trop maigre est vidée sans plus.
     */
    public static void messageSent(MinecraftServer server, RelayPlanner.Route route) {
        PhoneOperators registry = PhoneOperators.get(server);
        int cost = PhoneConfig.antennaMessageFe();
        for (UUID id : antennasOf(route)) {
            Optional<AntennaRecord> record = registry.antenna(id);
            if (record.isEmpty()) {
                continue;
            }
            ServerLevel level = server.getLevel(record.get().dimension());
            BlockPos pos = record.get().pos();
            LevelChunk chunk = level == null ? null
                    : level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(pos) instanceof AntennaBlockEntity be
                    && id.equals(be.antennaId())) {
                be.drainEnergy(cost);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Panneau
    // ---------------------------------------------------------------------------------------------

    /** Pour chaque opérateur existant : ses antennes inscrites, et combien sont en service. */
    public static List<RelayState> relayStates(MinecraftServer server) {
        PhoneOperators registry = PhoneOperators.get(server);
        long gameTime = now(server);
        List<RelayState> out = new ArrayList<>();
        for (PhoneOperator op : registry.all()) {
            List<AntennaRecord> antennas = registry.antennasOf(op.id());
            int inService = 0;
            for (AntennaRecord a : antennas) {
                if (AntennaBlockEntity.inService(a, gameTime)) {
                    inService++;
                }
            }
            out.add(new RelayState(op.id(), op.name(), antennas.size(), inService));
        }
        return out;
    }
}
