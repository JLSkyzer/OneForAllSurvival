package dev.oneforallsurvival.phone.call;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.call.CallBook.Call;
import dev.oneforallsurvival.phone.call.CallBook.End;
import dev.oneforallsurvival.phone.call.CallBook.Party;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.RelayFailure;
import dev.oneforallsurvival.phone.net.RelayPlanner;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimNumbers;
import dev.oneforallsurvival.phone.net.TrafficHandle;
import dev.oneforallsurvival.phone.net.TrafficKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;

/**
 * Les appels côté serveur (feature 93, lot T3, tâche 3), sans voix ni écran : composer, décrocher, refuser, raccrocher,
 * et tout ce qui coupe un appel (départ, mort, dimension, téléphone ou carte perdu, signal perdu, sonnerie sans
 * réponse, arrêt du serveur). La page, le repli et la voix écoutent par {@link #addListener}.
 *
 * <p>Un appel vit tant que ses deux bouts portent leur téléphone, carte active logée : fermer l'écran ne raccroche pas
 * (choix de l'auteur). Le trafic s'ouvre sur les antennes du trajet dès la sonnerie ; toutes les
 * {@value #CHECK_PERIOD} tics le trajet est refait ({@link PhoneNetwork#route}) et la poignée rouverte si ses antennes
 * changent. Chaque poignée se ferme en un seul point ({@link #setLine}) ; toute fin passe par {@link #finish}, qui la
 * ferme, écrit les récents des deux cartes et prévient les écouteurs.
 *
 * <p>Rien n'est sauvegardé : à l'arrêt du serveur tous les appels finissent ({@link End#SERVER_STOPPING}) et le
 * carnet des appels est vidé. Fil du serveur seulement ; ni numéro, ni carte, ni jeton dans un journal.
 */
public final class CallService {

    public static final String MESSAGE = "message.oneforallsurvival.call.";

    /** Période des relevés des appels en cours (tics). */
    public static final int CHECK_PERIOD = 20;

    /** Fenêtre du frein des appels composés : une minute de tics serveur. */
    private static final int DIAL_WINDOW_TICKS = 1_200;

    /** Les numéros d'urgence, refusés jusqu'à la tâche 8. */
    private static final Set<String> EMERGENCY = Set.of("17", "15");

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Outcome { RINGING, BAD_NUMBER, SELF, BUSY, UNREACHABLE, FAILED, TOO_FAST }

    /**
     * L'issue d'un appel composé.
     *
     * @param message ce que l'appelant lit ; nul quand ça sonne
     * @param callId le numéro de l'appel s'il sonne, sinon 0
     */
    public record Dial(Outcome outcome, @Nullable Component message, int callId) {}

    /** La poignée de trafic d'un appel et les antennes du trajet qui l'a ouverte. */
    private record Line(TrafficHandle traffic, UUID callerAntenna, UUID calleeAntenna) {

        boolean sameAntennas(RelayPlanner.Route route) {
            return callerAntenna.equals(route.caller().id()) && calleeAntenna.equals(route.callee().id());
        }
    }

    private static final CallBook BOOK = new CallBook();
    private static final Map<Integer, Line> LINES = new HashMap<>();
    /** Les appels composés de la dernière minute, par joueur ; gardés à la déconnexion (revue de la tâche 3, I1). */
    private static final DialThrottle DIALS = new DialThrottle(DIAL_WINDOW_TICKS);
    private static final List<CallListener> LISTENERS = new CopyOnWriteArrayList<>();
    /**
     * Vrai pendant {@link #stopAll} : un écouteur qui composerait depuis une fin d'appel ferait naître un appel que
     * l'arrêt effacerait sans fin ni récents (revue de la tâche 3, m1) ; il est refusé.
     */
    private static boolean stopping;

    private CallService() {}

    private static int now(MinecraftServer server) {
        return server.getTickCount();
    }

    // ---------------------------------------------------------------------------------------------
    // Gestes
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code caller} compose {@code rawNumber} (avec ou sans espaces) sur {@code phone}, qu'il tient.
     *
     * <p>Dans cet ordre : frein ({@code callDialsPerMinute} par minute, tout appel composé compte) ; déjà en appel ;
     * numéro (17 et 15 refusés jusqu'à la tâche 8) ; batterie vide ; son propre numéro ; puis le trajet
     * ({@link PhoneNetwork#route}, qui juge l'appelant avant de rien dire de l'appelé). L'appelé est injoignable s'il
     * n'a aucun porteur connecté, ou si son téléphone est en mode avion ou à plat ; occupé s'il est déjà en appel.
     */
    public static Dial dial(ServerPlayer caller, ItemStack phone, boolean airplane, String rawNumber) {
        if (caller == null || caller instanceof FakePlayer || !caller.isAlive() || stopping) {
            return new Dial(Outcome.FAILED, null, 0);
        }
        MinecraftServer server = caller.server;
        if (!DIALS.allow(caller.getUUID(), now(server), PhoneConfig.callDialsPerMinute())) {
            return refused(Outcome.TOO_FAST, "too_fast");
        }
        if (BOOK.of(caller.getUUID()).isPresent()) {
            return refused(Outcome.FAILED, "in_call");
        }
        String digits = rawNumber == null ? "" : rawNumber.replace(" ", "");
        Optional<String> number = SimNumbers.parse(rawNumber);
        if (EMERGENCY.contains(digits) || number.isEmpty()) {
            return refused(Outcome.BAD_NUMBER, "bad_number");
        }
        if (phone != null && phone.getItem() instanceof PhoneItem && PhoneItem.stored(phone) <= 0) {
            return new Dial(Outcome.FAILED, Component.translatable(PhoneService.MESSAGE + "battery_empty"), 0);
        }
        Optional<SimCard> mine = phone == null ? Optional.empty() : PhoneSims.line(server, phone);
        if (mine.isPresent() && mine.get().number().equals(number.get())) {
            return refused(Outcome.SELF, "self");
        }
        Optional<SimCard> target = PhoneNetwork.lineOf(server, number.get());
        Optional<PhoneCarriers.Carrier> carrier = target.map(SimCard::phoneId)
                .flatMap(id -> PhoneCarriers.find(server, id))
                .filter(c -> c.player().isAlive() && reachable(server, c.phone()));
        if (carrier.isPresent() && carrier.get().player() == caller) {
            return refused(Outcome.SELF, "self");
        }
        Either<RelayPlanner.Route, RelayFailure> route = PhoneNetwork.route(server, caller, phone, airplane,
                number.get(), carrier.map(c -> where(c.player())).orElse(null));
        if (route.right().isPresent()) {
            RelayFailure failure = route.right().get();
            if (failure.reason() == RelayFailure.Reason.CALLEE_NO_COVERAGE && carrier.isEmpty()) {
                return refused(Outcome.UNREACHABLE, "unreachable");
            }
            return new Dial(Outcome.FAILED, failure.describe(), 0);
        }
        Optional<UUID> phoneId = PhoneItem.phoneId(phone);
        if (mine.isEmpty() || phoneId.isEmpty() || target.isEmpty() || carrier.isEmpty()
                || PhoneCarriers.heldBy(caller, phoneId.get()).isEmpty()) {
            // Le trajet est passé sans ligne de part et d'autre, ou le téléphone n'est pas à l'appelant.
            return new Dial(Outcome.FAILED,
                    new RelayFailure(RelayFailure.Reason.CALLER_SIM_INACTIVE, "").describe(), 0);
        }
        Party from = new Party(caller.getUUID(), phoneId.get(), mine.get().simId(), mine.get().number());
        Party to = new Party(carrier.get().player().getUUID(), target.get().phoneId(), target.get().simId(),
                target.get().number());
        Optional<Call> call = BOOK.start(from, to, now(server));
        if (call.isEmpty()) {
            return refused(Outcome.BUSY, "busy");
        }
        RelayPlanner.Route r = route.left().get();
        setLine(call.get().id(), new Line(PhoneNetwork.openTraffic(server, r, TrafficKind.CALL), r.caller().id(),
                r.callee().id()));
        changed(call.get());
        return new Dial(Outcome.RINGING, null, call.get().id());
    }

    private static Dial refused(Outcome outcome, String key) {
        return new Dial(outcome, Component.translatable(MESSAGE + key), 0);
    }

    /**
     * L'appelé décroche. Faux si l'appel ne sonne pas, n'est pas le sien, s'il n'est plus en vie, ne porte plus son
     * téléphone ou l'a mis en mode avion (l'appel reste alors manqué : le relevé le finit).
     */
    public static boolean answer(ServerPlayer callee, int callId) {
        Optional<Call> call = BOOK.get(callId);
        if (callee == null || !callee.isAlive() || call.isEmpty() || call.get().state() != CallBook.State.RINGING
                || !call.get().callee().player().equals(callee.getUUID())
                || PhoneData.get(callee.server).record(call.get().callee().phoneId()).airplane()
                || present(callee.server, callee, call.get().callee()).isEmpty()) {
            return false;
        }
        Optional<Call> answered = BOOK.answer(callId, callee.getUUID(), now(callee.server));
        answered.ifPresent(CallService::changed);
        return answered.isPresent();
    }

    /** L'appelé refuse pendant la sonnerie ({@link End#DECLINED}). Faux sinon. Jamais freiné. */
    public static boolean decline(ServerPlayer callee, int callId) {
        Optional<Call> call = BOOK.get(callId);
        if (callee == null || call.isEmpty() || call.get().state() != CallBook.State.RINGING
                || !call.get().callee().player().equals(callee.getUUID())) {
            return false;
        }
        finish(callee.server, call.get(), End.DECLINED);
        return true;
    }

    /**
     * Raccroche ({@link End#HUNG_UP}) : l'appelant à tout moment, l'appelé une fois décroché. Faux pour l'appelé
     * pendant la sonnerie (c'est {@link #decline}) ou pour qui n'est pas de l'appel. Jamais freiné.
     */
    public static boolean hangUp(ServerPlayer player, int callId) {
        Optional<Call> call = BOOK.get(callId);
        if (player == null || call.isEmpty() || !call.get().involves(player.getUUID())) {
            return false;
        }
        if (call.get().state() == CallBook.State.RINGING && call.get().callee().player().equals(player.getUUID())) {
            return false;
        }
        finish(player.server, call.get(), End.HUNG_UP);
        return true;
    }

    /** Tous les appels en cours, par ordre de début (copie). */
    public static List<Call> calls() {
        return BOOK.all();
    }

    /** L'appel de ce joueur, qu'il sonne ou soit en ligne. */
    public static Optional<Call> callOf(ServerPlayer player) {
        return player == null ? Optional.empty() : BOOK.of(player.getUUID());
    }

    /** Coupe ou rend le micro de ce joueur (écouteurs prévenus). Vide hors appel. */
    public static Optional<Call> setMuted(ServerPlayer player, boolean muted) {
        Optional<Call> call = BOOK.setMuted(player.getUUID(), muted);
        call.ifPresent(CallService::changed);
        return call;
    }

    /** Met ou retire le haut-parleur de ce joueur (écouteurs prévenus). Vide hors appel. */
    public static Optional<Call> setSpeaker(ServerPlayer player, boolean speaker) {
        Optional<Call> call = BOOK.setSpeaker(player.getUUID(), speaker);
        call.ifPresent(CallService::changed);
        return call;
    }

    public static void addListener(CallListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(CallListener listener) {
        LISTENERS.remove(listener);
    }

    // ---------------------------------------------------------------------------------------------
    // Relevé et fin
    // ---------------------------------------------------------------------------------------------

    private static GlobalPos where(ServerPlayer player) {
        return GlobalPos.of(player.level().dimension(), player.blockPosition());
    }

    /** Un téléphone qui reçoit : ni en mode avion, ni à plat. */
    private static boolean reachable(MinecraftServer server, ItemStack phone) {
        Optional<UUID> id = PhoneItem.phoneId(phone);
        return id.isPresent() && !PhoneData.get(server).record(id.get()).airplane() && PhoneItem.stored(phone) > 0;
    }

    /**
     * Le téléphone de ce bout, s'il le porte encore : même téléphone, carte active, la même carte qu'au début de l'appel,
     * batterie non vide.
     */
    private static Optional<ItemStack> present(MinecraftServer server, ServerPlayer player, Party party) {
        return PhoneCarriers.heldBy(player, party.phoneId())
                .filter(stack -> PhoneItem.stored(stack) > 0)
                .filter(stack -> PhoneSims.line(server, stack).map(SimCard::simId).filter(party.simId()::equals)
                        .isPresent());
    }

    /**
     * Ce qui coupe cet appel à ce relevé, s'il y a quelque chose ; rouvre la poignée si le trajet a changé. La sonnerie
     * expirée n'est pas jugée ici : {@link #checkAll} la lit dans {@link CallBook#expired}, seule règle.
     */
    private static Optional<End> verdict(MinecraftServer server, Call call) {
        ServerPlayer caller = server.getPlayerList().getPlayer(call.caller().player());
        ServerPlayer callee = server.getPlayerList().getPlayer(call.callee().player());
        if (caller == null || callee == null) {
            return Optional.of(End.PARTY_LEFT);
        }
        if (!caller.isAlive() || !callee.isAlive()) {
            return Optional.of(End.DIED);
        }
        Optional<ItemStack> callerPhone = present(server, caller, call.caller());
        Optional<ItemStack> calleePhone = present(server, callee, call.callee());
        if (callerPhone.isEmpty() || calleePhone.isEmpty()) {
            return Optional.of(End.PHONE_GONE);
        }
        PhoneData data = PhoneData.get(server);
        if (data.record(call.callee().phoneId()).airplane()) {
            return Optional.of(End.NO_SIGNAL);
        }
        Either<RelayPlanner.Route, RelayFailure> route = PhoneNetwork.route(server, caller, callerPhone.get(),
                data.record(call.caller().phoneId()).airplane(), call.callee().number(), where(callee));
        if (route.left().isEmpty()) {
            return Optional.of(End.NO_SIGNAL);
        }
        Line line = LINES.get(call.id());
        if (line == null || !line.sameAntennas(route.left().get())) {
            RelayPlanner.Route r = route.left().get();
            setLine(call.id(), new Line(PhoneNetwork.openTraffic(server, r, TrafficKind.CALL), r.caller().id(),
                    r.callee().id()));
        }
        return Optional.empty();
    }

    /**
     * Relève tous les appels en cours (le tic le fait toutes les {@value #CHECK_PERIOD} tics) : d'abord ce qui les
     * coupe ({@link #verdict}), puis la sonnerie expirée, par la seule règle de {@link CallBook#expired} (un appel
     * décroché n'expire jamais).
     */
    static void checkAll(MinecraftServer server) {
        int tick = now(server);
        Set<Integer> expired = new HashSet<>();
        for (Call call : BOOK.expired(tick, PhoneConfig.callRingSeconds() * 20)) {
            expired.add(call.id());
        }
        for (Call call : BOOK.all()) {
            if (BOOK.get(call.id()).isEmpty()) {
                continue;
            }
            Optional<End> end = verdict(server, call);
            if (end.isEmpty() && expired.contains(call.id())) {
                end = Optional.of(End.NO_ANSWER);
            }
            end.ifPresent(reason -> finish(server, BOOK.get(call.id()).orElse(call), reason));
        }
    }

    /**
     * Le seul endroit où une poignée de trafic se ferme : remplace la ligne de l'appel {@code id} par {@code line}
     * (nulle : la retire) et ferme l'ancienne.
     */
    private static void setLine(int id, @Nullable Line line) {
        Line old = line == null ? LINES.remove(id) : LINES.put(id, line);
        if (old != null) {
            old.traffic().close();
        }
    }

    /**
     * Toute fin d'appel passe ici, une fois : la poignée est fermée, les récents des deux cartes écrits (appelant :
     * passé ; appelé : reçu s'il a décroché, sinon manqué ; durée depuis le décroché) et les écouteurs prévenus. Un
     * appel déjà fini ne fait rien.
     */
    private static void finish(MinecraftServer server, Call call, End reason) {
        Optional<Call> ended = BOOK.end(call.id());
        if (ended.isEmpty()) {
            return;
        }
        Call last = ended.get();
        try {
            setLine(last.id(), null);
        } finally {
            try {
                writeRecents(server, last);
            } catch (RuntimeException e) {
                LOGGER.error("Récents de l'appel {} non écrits", last.id(), e);
            }
            for (CallListener listener : LISTENERS) {
                try {
                    listener.ended(last, reason);
                } catch (RuntimeException e) {
                    LOGGER.error("Un écouteur des appels a échoué à la fin de l'appel {}", last.id(), e);
                }
            }
        }
    }

    private static void writeRecents(MinecraftServer server, Call call) {
        boolean answered = call.state() == CallBook.State.ACTIVE;
        int seconds = answered ? (int) Math.max(0L, (now(server) - call.answeredTick()) / 20L) : 0;
        long at = EconomyClock.now();
        PhoneDirectory directory = PhoneDirectory.get(server);
        directory.log(call.caller().simId(), new Recent(call.callee().number(), Recent.Kind.OUTGOING, at, seconds));
        directory.log(call.callee().simId(), new Recent(call.caller().number(),
                answered ? Recent.Kind.INCOMING : Recent.Kind.MISSED, at, seconds));
    }

    /**
     * Prévient les écouteurs d'un changement. Un écouteur qui finit ou change l'appel (réentrance) arrête la diffusion :
     * les suivants ne reçoivent pas un état qui n'est plus celui du carnet (revue de la tâche 3, m1).
     */
    private static void changed(Call call) {
        for (CallListener listener : LISTENERS) {
            if (!BOOK.get(call.id()).filter(call::equals).isPresent()) {
                return;
            }
            try {
                listener.changed(call);
            } catch (RuntimeException e) {
                LOGGER.error("Un écouteur des appels a échoué sur l'appel {}", call.id(), e);
            }
        }
    }

    private static void endFor(MinecraftServer server, UUID player, End reason) {
        BOOK.of(player).ifPresent(call -> finish(server, call, reason));
    }

    /** Arrêt du serveur : tous les appels finissent, puis tout est oublié (aucune poignée, aucun frein). */
    public static void stopAll(MinecraftServer server) {
        stopping = true;
        try {
            for (Call call : BOOK.all()) {
                finish(server, call, End.SERVER_STOPPING);
            }
            BOOK.clear();
            for (Integer id : new ArrayList<>(LINES.keySet())) {
                setLine(id, null);
            }
            DIALS.clear();
        } finally {
            // Un serveur intégré relancé dans la même JVM recompose normalement.
            stopping = false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bus de Forge
    // ---------------------------------------------------------------------------------------------

    /**
     * Inscrit les écouteurs des appels sur {@code bus} : le mod le fait sur le bus de Forge, les essais sur un bus à
     * part pour prouver l'inscription (l'arrêt surtout) sans réveiller les autres écouteurs du jeu. La mort passe en
     * priorité la plus haute : l'appel finit avant les lâchers.
     */
    public static void register(IEventBus bus) {
        bus.addListener(CallService::onServerTick);
        bus.addListener(CallService::onLoggedOut);
        bus.addListener(EventPriority.HIGHEST, CallService::onDeath);
        bus.addListener(CallService::onChangedDimension);
        bus.addListener(CallService::onServerStopping);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % CHECK_PERIOD == 0) {
            checkAll(event.getServer());
            DIALS.purge(now(event.getServer()));
        }
    }

    /** Le départ finit l'appel ; le frein reste (quitter puis revenir ne le remet pas à zéro). */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endFor(player.server, player.getUUID(), End.PARTY_LEFT);
        }
    }

    /** Branché en priorité la plus haute : l'appel finit avant les lâchers (le téléphone tombe ensuite). */
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endFor(player.server, player.getUUID(), End.DIED);
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endFor(player.server, player.getUUID(), End.CHANGED_DIMENSION);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        stopAll(event.getServer());
    }
}
