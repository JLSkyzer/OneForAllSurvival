package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneData.PhoneRecord;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import dev.oneforallsurvival.phone.net.NetworkStatus;
import dev.oneforallsurvival.phone.net.OperatorPanels;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.RelayPlanner;
import dev.oneforallsurvival.phone.net.SignalMath;
import dev.oneforallsurvival.phone.net.SimNumbers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Le serveur du téléphone : seul juge du code, de la session, du propriétaire et des réglages. Le client ne fait que
 * montrer ce qu'il reçoit ({@link PhoneStatePacket}) et proposer des gestes ({@link PhoneActionPacket}), tous revérifiés
 * ici : session ouverte et du même numéro, vrai joueur en ligne, même téléphone dans la même main, état du verrou,
 * propriétaire, format du code, blocage. Un geste refusé de cette façon ne reçoit rien.
 *
 * <p>Base de temps : {@link EconomyClock#now()} (millisecondes) pour le blocage, ce qui en est sauvegardé et l'heure
 * montrée. Le code n'est ni journalisé, ni renvoyé, ni écrit ailleurs que sous forme d'empreinte salée.
 *
 * <p>Deux piles de même {@code phone_id} (copie créative) partagent leurs données, par choix ; chaque geste relit
 * {@link PhoneData} et le propriétaire est toujours comparé au joueur qui agit.
 *
 * <p>Lot T2 : chaque état porte la vue réseau ({@link PhoneNetView}), calculée à l'ouverture, après chaque geste et à
 * chaque relevé du signal (toutes les {@link PhoneConfig#signalRefreshTicks()} tics, envoyée seulement si elle a
 * changé). Les gestes de carte ({@code INSERT_SIM}, {@code REMOVE_SIM}, {@code REQUEST_SIM}) et l'appli « Antenne »
 * ne passent que déverrouillé, et par le seul propriétaire : un autre qui connaît le code voit la ligne, sans geste
 * de carte (choix de la tâche 7, le plan étant muet). Le jeton d'une carte ne part jamais au client.
 */
public final class PhoneService {

    public enum Outcome { OPENED, EMPTY_BATTERY, DONE, REFUSED, NO_SESSION, THROTTLED }

    public static final String MESSAGE = "message.oneforallsurvival.phone.";
    /** Frein : un geste tous les {@value #MIN_TICKS_BETWEEN} tics par joueur au plus ; au-delà, ignoré sans réponse. */
    public static final int MIN_TICKS_BETWEEN = 2;

    private static final Map<UUID, PhoneSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_GESTURE = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_SESSION = new AtomicInteger();
    private static final RandomSource SALT_SOURCE = RandomSource.create();
    /** Une case de l'inventaire d'un joueur, écrite sans zéro en tête : {@code "0"} à {@code "40"}. */
    private static final Pattern SLOT = Pattern.compile("0|[1-9][0-9]?");
    /** Un identifiant d'opérateur : 12 caractères hexadécimaux en minuscules. */
    private static final Pattern OPERATOR_ID = Pattern.compile("[0-9a-f]{12}");

    private PhoneService() {}

    // ---------------------------------------------------------------------------------------------
    // Ouverture et fermeture
    // ---------------------------------------------------------------------------------------------

    /**
     * Ouvre le téléphone tenu dans {@code hand} : lui pose un identifiant s'il n'en a pas, refuse une batterie vide
     * (message), remplace la session déjà ouverte de ce joueur par une nouvelle (numéro croissant) et envoie
     * {@link Kind#OPEN}.
     */
    public static Outcome open(ServerPlayer player, InteractionHand hand) {
        if (gone(player)) {
            return Outcome.NO_SESSION;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PhoneItem)) {
            return Outcome.REFUSED;
        }
        UUID phoneId = PhoneItem.ensurePhoneId(stack);
        if (PhoneItem.stored(stack) <= 0) {
            // Comme une ouverture réussie, un essai d'ouverture remplace la session précédente : elle est fermée.
            close(player, CloseReason.CLOSED);
            player.displayClientMessage(Component.translatable(MESSAGE + "empty"), true);
            return Outcome.EMPTY_BATTERY;
        }
        PhoneRecord record = PhoneData.get(player.server).record(phoneId);
        PhoneSession session = new PhoneSession(NEXT_SESSION.incrementAndGet(), phoneId, hand,
                hasCode(record) ? Lock.LOCKED : Lock.SETUP);
        SESSIONS.put(player.getUUID(), session);
        send(player, view(player, session, Kind.OPEN, Result.NONE, CloseReason.NONE));
        // Lot T3 : un appel en cours (qui sonne, ou décroché écran fermé) est montré dès l'ouverture.
        dev.oneforallsurvival.phone.call.CallUpdates.sessionOpened(player);
        return Outcome.OPENED;
    }

    /**
     * Ce qu'un geste d'appel (lot T3, tâche 4a) trouve après les mêmes contrôles que {@link #act} : la session ouverte
     * sous ce numéro, le téléphone de la session dans sa main, et son enregistrement, verrou remis d'accord.
     */
    public record Held(PhoneSession session, ItemStack phone, PhoneRecord record) {

        public boolean unlocked() {
            return session.lock() == Lock.UNLOCKED;
        }
    }

    /**
     * Les contrôles de {@link #act} sans le frein : vrai joueur en ligne, session ouverte de ce numéro, même téléphone
     * dans la même main. Vide sinon (rien n'est envoyé). Le verrou de la session est relu ({@link #syncLock}).
     */
    public static Optional<Held> held(ServerPlayer player, int sessionId) {
        if (gone(player)) {
            return Optional.empty();
        }
        PhoneSession session = SESSIONS.get(player.getUUID());
        if (session == null || session.id() != sessionId) {
            return Optional.empty();
        }
        ItemStack stack = player.getItemInHand(session.hand());
        if (!(stack.getItem() instanceof PhoneItem)
                || !PhoneItem.phoneId(stack).equals(Optional.of(session.phoneId()))) {
            return Optional.empty();
        }
        PhoneRecord record = PhoneData.get(player.server).record(session.phoneId());
        syncLock(session, record);
        return Optional.of(new Held(session, stack, record));
    }

    /**
     * Le frein des gestes de l'écran, partagé avec {@link #act} : vrai (geste ignoré) si ce joueur a déjà fait un geste
     * il y a moins de {@link #MIN_TICKS_BETWEEN} tics ; sinon ce tic est noté.
     */
    public static boolean throttle(ServerPlayer player) {
        return throttled(player);
    }

    /**
     * Ce joueur a-t-il une session ouverte et déverrouillée sur ce téléphone, verrou relu, ce téléphone toujours dans la
     * main de la session (revue de la tâche 4a, mineur) ? Pour envoyer un carnet sans qu'il l'ait demandé (fin
     * d'appel) : jamais à un écran verrouillé, ni à une session dont le téléphone a quitté la main.
     */
    public static boolean unlockedOn(ServerPlayer player, UUID phoneId) {
        PhoneSession session = SESSIONS.get(player.getUUID());
        if (session == null || !session.phoneId().equals(phoneId)) {
            return false;
        }
        ItemStack stack = player.getItemInHand(session.hand());
        if (!(stack.getItem() instanceof PhoneItem) || !PhoneItem.phoneId(stack).equals(Optional.of(phoneId))) {
            return false;
        }
        syncLock(session, PhoneData.get(player.server).record(phoneId));
        return session.lock() == Lock.UNLOCKED;
    }

    /** La session ouverte de ce joueur. */
    public static Optional<PhoneSession> session(UUID player) {
        return Optional.ofNullable(SESSIONS.get(player));
    }

    /** Ferme la session de ce joueur, s'il en a une, et lui envoie {@link Kind#CLOSE} avec la raison. */
    public static void close(ServerPlayer player, CloseReason reason) {
        PhoneSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            Result last = reason == CloseReason.ERASED ? Result.ERASED : Result.NONE;
            send(player, view(player, session, Kind.CLOSE, last, reason));
        }
    }

    /**
     * Ferme toutes les sessions ouvertes sur ce téléphone, quel que soit le joueur (copies comprises), avec la raison
     * donnée. Pour {@code /phone reset} : après l'effacement, aucun écran ne reste ouvert sur l'ancien code.
     */
    public static void closeAll(MinecraftServer server, UUID phoneId, CloseReason reason) {
        for (SessionOf s : othersOn(server, phoneId, null)) {
            close(s.player(), reason);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Les gestes
    // ---------------------------------------------------------------------------------------------

    /**
     * Un geste de l'écran. Session absente ou d'un autre numéro, faux joueur, téléphone qui n'est plus dans la main de
     * la session : {@link Outcome#NO_SESSION}, rien n'est envoyé. Trop tôt après le geste précédent :
     * {@link Outcome#THROTTLED}, rien non plus. Sinon le geste est joué et un {@link Kind#UPDATE} répond (sauf
     * {@link PhoneActionPacket.Action#CLOSE} et un effacement, qui ferment).
     */
    public static Outcome act(ServerPlayer player, PhoneActionPacket packet) {
        if (gone(player)) {
            return Outcome.NO_SESSION;
        }
        PhoneSession session = SESSIONS.get(player.getUUID());
        if (session == null || session.id() != packet.session()) {
            return Outcome.NO_SESSION;
        }
        ItemStack stack = player.getItemInHand(session.hand());
        if (!(stack.getItem() instanceof PhoneItem)
                || !PhoneItem.phoneId(stack).equals(Optional.of(session.phoneId()))) {
            return Outcome.NO_SESSION;
        }
        if (packet.action() == PhoneActionPacket.Action.CLOSE) {
            // Fermer ne passe jamais par le frein (revue de la tâche 4a, mineur ; contrainte du lot T3).
            SESSIONS.remove(player.getUUID(), session);
            return Outcome.DONE;
        }
        if (throttled(player)) {
            return Outcome.THROTTLED;
        }
        PhoneData data = PhoneData.get(player.server);
        PhoneRecord record = data.record(session.phoneId());
        syncLock(session, record);
        Result[] last = {Result.NONE};
        Outcome outcome = switch (packet.action()) {
            case CLOSE -> Outcome.DONE; // déjà joué plus haut, avant le frein
            case SETUP -> setup(player, session, data, record, packet.first(), packet.second(), last);
            case UNLOCK -> unlock(session, data, record, packet.first(), last);
            case LOCK -> {
                if (session.lock() != Lock.UNLOCKED) {
                    yield Outcome.REFUSED;
                }
                session.setLock(Lock.LOCKED);
                yield Outcome.DONE;
            }
            case WALLPAPER -> {
                if (session.lock() != Lock.UNLOCKED || !PhoneWallpapers.isValid(packet.first())) {
                    yield Outcome.REFUSED;
                }
                data.put(session.phoneId(), withSettings(record, packet.first(), record.airplane()));
                yield Outcome.DONE;
            }
            case AIRPLANE -> {
                if (session.lock() != Lock.UNLOCKED) {
                    yield Outcome.REFUSED;
                }
                data.put(session.phoneId(), withSettings(record, record.wallpaper(), packet.flag()));
                yield Outcome.DONE;
            }
            case CHANGE_CODE -> changeCode(player, session, data, record, packet.first(), packet.second(), last);
            case ERASE -> erase(player, session, data, record, packet.first(), last);
            case INSERT_SIM -> insertSim(player, session, record, packet.first(), last);
            case REMOVE_SIM -> removeSim(player, session, record, last);
            case REQUEST_SIM -> requestSim(player, session, record, packet.first(), last);
            case OPEN_OPERATOR_APP -> openOperatorApp(player, session, record, last);
        };
        if (SESSIONS.get(player.getUUID()) == session) {
            send(player, view(player, session, Kind.UPDATE, last[0], CloseReason.NONE));
        }
        return outcome;
    }

    /**
     * Le choix du code, seul geste qui configure un téléphone neuf : l'assistant de la page peut y joindre le fond
     * choisi ({@code wallpaper}, vide sans assistant). Code validé d'abord ; le fond n'est posé qu'avec lui, dans le
     * même enregistrement, et seulement s'il est dans {@link PhoneWallpapers#IDS} (sinon le fond en place reste).
     */
    private static Outcome setup(ServerPlayer player, PhoneSession session, PhoneData data, PhoneRecord record,
            String code, String wallpaper, Result[] last) {
        if (session.lock() != Lock.SETUP) {
            return Outcome.REFUSED;
        }
        if (!PhoneCode.isValid(code)) {
            last[0] = Result.INVALID_CODE;
            return Outcome.REFUSED;
        }
        String salt = PhoneCode.newSalt(SALT_SOURCE);
        String chosen = PhoneWallpapers.isValid(wallpaper) ? wallpaper : record.wallpaper();
        data.put(session.phoneId(), new PhoneRecord(player.getUUID(), player.getGameProfile().getName(), salt,
                PhoneCode.hash(salt, code), 0, record.lockedUntil(), 0, chosen, record.airplane()));
        session.unlock(salt);
        relockOthers(player, session.phoneId());
        return Outcome.DONE;
    }

    /**
     * Les autres sessions ouvertes sur ce téléphone (copies) dont le verrou ne vaut plus après un nouveau code (choix du
     * code en cours, ou déverrouillées par l'ancien) : reverrouillées et prévenues tout de suite par un
     * {@link Kind#UPDATE}, sans attendre leur prochain geste ni le tic.
     */
    private static void relockOthers(ServerPlayer player, UUID phoneId) {
        PhoneRecord record = PhoneData.get(player.server).record(phoneId);
        for (SessionOf other : othersOn(player.server, phoneId, player)) {
            Lock before = other.session().lock();
            syncLock(other.session(), record);
            if (other.session().lock() != before) {
                send(other.player(), view(other.player(), other.session(), Kind.UPDATE, Result.NONE,
                        CloseReason.NONE));
            }
        }
    }

    private static Outcome unlock(PhoneSession session, PhoneData data, PhoneRecord record, String code,
            Result[] last) {
        if (session.lock() != Lock.LOCKED) {
            return Outcome.REFUSED;
        }
        Result verdict = check(data, session.phoneId(), record, code);
        if (verdict != Result.NONE) {
            last[0] = verdict;
            return Outcome.REFUSED;
        }
        session.unlock(record.salt());
        return Outcome.DONE;
    }

    private static Outcome changeCode(ServerPlayer player, PhoneSession session, PhoneData data, PhoneRecord record,
            String oldCode, String newCode, Result[] last) {
        if (session.lock() != Lock.UNLOCKED) {
            return Outcome.REFUSED;
        }
        if (!player.getUUID().equals(record.owner())) {
            last[0] = Result.NOT_OWNER;
            return Outcome.REFUSED;
        }
        if (!PhoneCode.isValid(newCode)) {
            last[0] = Result.INVALID_CODE;
            return Outcome.REFUSED;
        }
        Result verdict = check(data, session.phoneId(), record, oldCode);
        if (verdict != Result.NONE) {
            last[0] = verdict;
            if (verdict == Result.LOCKED_OUT) {
                session.setLock(Lock.LOCKED);
            }
            return Outcome.REFUSED;
        }
        PhoneRecord now = data.record(session.phoneId());
        String salt = PhoneCode.newSalt(SALT_SOURCE);
        data.put(session.phoneId(), new PhoneRecord(now.owner(), now.ownerName(), salt, PhoneCode.hash(salt, newCode),
                now.failures(), now.lockedUntil(), now.lockoutLevel(), now.wallpaper(), now.airplane()));
        session.unlock(salt);
        last[0] = Result.CODE_CHANGED;
        // Les autres sessions déverrouillées par l'ancien code : leur sel ne vaut plus, reverrouillées tout de suite.
        relockOthers(player, session.phoneId());
        return Outcome.DONE;
    }

    private static Outcome erase(ServerPlayer player, PhoneSession session, PhoneData data, PhoneRecord record,
            String code, Result[] last) {
        if (session.lock() != Lock.UNLOCKED) {
            return Outcome.REFUSED;
        }
        if (!player.getUUID().equals(record.owner())) {
            last[0] = Result.NOT_OWNER;
            return Outcome.REFUSED;
        }
        Result verdict = check(data, session.phoneId(), record, code);
        if (verdict != Result.NONE) {
            last[0] = verdict;
            if (verdict == Result.LOCKED_OUT) {
                session.setLock(Lock.LOCKED);
            }
            return Outcome.REFUSED;
        }
        data.erase(session.phoneId());
        for (SessionOf other : othersOn(player.server, session.phoneId(), player)) {
            close(other.player(), CloseReason.ERASED);
        }
        close(player, CloseReason.ERASED);
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------------------------------------
    // La carte SIM et le réseau (lot T2)
    // ---------------------------------------------------------------------------------------------

    /**
     * Un geste de carte n'est permis que déverrouillé, et au seul propriétaire ({@link Result#NOT_OWNER} sinon).
     * Rend {@code null} s'il est permis, sinon l'issue du refus.
     */
    @Nullable
    private static Outcome cardGestureRefused(ServerPlayer player, PhoneSession session, PhoneRecord record,
            Result[] last) {
        if (session.lock() != Lock.UNLOCKED) {
            return Outcome.REFUSED;
        }
        if (!player.getUUID().equals(record.owner())) {
            last[0] = Result.NOT_OWNER;
            return Outcome.REFUSED;
        }
        return null;
    }

    /** Glisse la carte de la case {@code slotText} ; la case est relue par {@link PhoneSims#insert}. */
    private static Outcome insertSim(ServerPlayer player, PhoneSession session, PhoneRecord record, String slotText,
            Result[] last) {
        Outcome refused = cardGestureRefused(player, session, record, last);
        if (refused != null) {
            return refused;
        }
        if (!SLOT.matcher(slotText).matches() || Integer.parseInt(slotText) >= PhoneNetView.MAX_CHOICES) {
            return Outcome.REFUSED;
        }
        PhoneSims.Insert r = PhoneSims.insert(player, player.getItemInHand(session.hand()), Integer.parseInt(slotText));
        switch (r) {
            case DONE -> last[0] = Result.SIM_INSERTED;
            case NOT_A_SIM, UNKNOWN_CARD -> last[0] = Result.SIM_UNKNOWN;
            case NO_PHONE, REFUSED -> { }
        }
        return r == PhoneSims.Insert.DONE ? Outcome.DONE : Outcome.REFUSED;
    }

    private static Outcome removeSim(ServerPlayer player, PhoneSession session, PhoneRecord record, Result[] last) {
        Outcome refused = cardGestureRefused(player, session, record, last);
        if (refused != null) {
            return refused;
        }
        PhoneSims.Remove r = PhoneSims.remove(player, player.getItemInHand(session.hand()));
        switch (r) {
            case DONE -> last[0] = Result.SIM_REMOVED;
            case INVENTORY_FULL -> last[0] = Result.INVENTORY_FULL;
            case NO_SIM, REFUSED -> { }
        }
        return r == PhoneSims.Remove.DONE ? Outcome.DONE : Outcome.REFUSED;
    }

    /**
     * Demande une carte à l'opérateur {@code operatorId}, s'il est parmi ceux que la vue propose maintenant (une antenne
     * en service couvre le joueur, téléphone sans carte active, hors mode avion) ; sinon {@link Result#NO_NETWORK}. Le
     * fondateur est prévenu tout de suite s'il est en ligne, sinon à sa connexion (avis gardé au registre).
     */
    private static Outcome requestSim(ServerPlayer player, PhoneSession session, PhoneRecord record,
            String operatorId, Result[] last) {
        Outcome refused = cardGestureRefused(player, session, record, last);
        if (refused != null) {
            return refused;
        }
        if (!OPERATOR_ID.matcher(operatorId).matches()) {
            return Outcome.REFUSED;
        }
        if (record.airplane()) {
            last[0] = Result.NO_NETWORK;
            return Outcome.REFUSED;
        }
        ItemStack phone = player.getItemInHand(session.hand());
        if (PhoneSims.check(player.server, phone) == PhoneSims.Validity.ACTIVE) {
            return Outcome.REFUSED;
        }
        boolean covered = requestable(player).stream().anyMatch(o -> o.id().equals(operatorId));
        PhoneOperators registry = PhoneOperators.get(player.server);
        Optional<PhoneOperator> operator = registry.byId(operatorId);
        if (!covered || operator.isEmpty()) {
            last[0] = Result.NO_NETWORK;
            return Outcome.REFUSED;
        }
        boolean already = registry.requestsOf(operatorId).stream().anyMatch(r -> r.player().equals(player.getUUID()));
        if (already) {
            last[0] = Result.ALREADY_REQUESTED;
            return Outcome.REFUSED;
        }
        String name = player.getGameProfile().getName();
        if (registry.request(operatorId, player.getUUID(), name, EconomyClock.now()).isEmpty()) {
            // Ni opérateur absent ni demande déjà faite (vus plus haut) : la file de l'opérateur est pleine.
            last[0] = Result.REQUESTS_FULL;
            return Outcome.REFUSED;
        }
        Component notice = Component.translatable(MESSAGE + "sim_request.received", name, operator.get().name());
        ServerPlayer founder = player.server.getPlayerList().getPlayer(operator.get().founder());
        if (founder != null) {
            founder.sendSystemMessage(notice);
        } else {
            registry.addNotice(operator.get().founder(), notice);
        }
        last[0] = Result.SIM_REQUESTED;
        return Outcome.DONE;
    }

    /**
     * L'appli « Antenne » : le panneau de l'opérateur du fondateur, ouvert depuis ce téléphone
     * ({@link OperatorPanels#openFromPhone} : propriétaire, déverrouillé, carte {@code ACTIVE} de son opérateur, une barre
     * au moins, hors mode avion). Dans tous les autres cas, refus sans réseau ({@link Result#NO_NETWORK}) ; la vue grise
     * alors l'icône par la même condition ({@link OperatorPanels#canOpenFromPhone}), et le toucher reste permis.
     */
    private static Outcome openOperatorApp(ServerPlayer player, PhoneSession session, PhoneRecord record,
            Result[] last) {
        Outcome refused = cardGestureRefused(player, session, record, last);
        if (refused != null) {
            return refused;
        }
        if (!OperatorPanels.openFromPhone(player, session)) {
            last[0] = Result.NO_NETWORK;
            return Outcome.REFUSED;
        }
        return Outcome.DONE;
    }

    /**
     * Les opérateurs dont une antenne en service couvre ce joueur, avec leurs barres ici, du meilleur signal au moins
     * bon puis par nom ; {@value PhoneNetView#MAX_REQUESTABLE} au plus.
     */
    private static List<PhoneNetView.OperatorChoice> requestable(ServerPlayer player) {
        PhoneOperators registry = PhoneOperators.get(player.server);
        RelayPlanner.Snapshot net = PhoneNetwork.snapshot(player.server, player.server.overworld().getGameTime());
        List<PhoneNetView.OperatorChoice> out = new ArrayList<>();
        for (PhoneOperator op : registry.all()) {
            RelayPlanner.Endpoint here = new RelayPlanner.Endpoint(op.id(), player.level().dimension(),
                    player.blockPosition());
            RelayPlanner.best(here, net).ifPresent(a -> out.add(new PhoneNetView.OperatorChoice(op.id(), op.name(),
                    SignalMath.bars(SignalMath.distance(a.pos(), here.pos()), net.range()))));
        }
        out.sort(Comparator.comparingInt(PhoneNetView.OperatorChoice::bars).reversed()
                .thenComparing(PhoneNetView.OperatorChoice::name));
        return out.size() > PhoneNetView.MAX_REQUESTABLE ? out.subList(0, PhoneNetView.MAX_REQUESTABLE) : out;
    }

    /**
     * Ce que {@code viewer} voit du réseau sur le téléphone {@code stack} de sa session. Hors {@link Lock#UNLOCKED} : la
     * carte, l'opérateur et les barres seulement. Déverrouillé par un autre que le propriétaire : la ligne, sans cartes
     * de l'inventaire, sans opérateurs demandables, sans appli. Mode avion : 0 barre (par {@link PhoneNetwork#status}),
     * aucun opérateur demandable. L'appli « Antenne » se montre au propriétaire qui fonde un opérateur, ouvrable ou non
     * ({@link OperatorPanels#canOpenFromPhone}). Le jeton d'une carte n'y entre jamais : seuls son numéro formaté, son
     * titulaire et le nom de son opérateur.
     */
    private static PhoneNetView netView(ServerPlayer viewer, PhoneSession session, ItemStack stack,
            PhoneRecord record) {
        if (!(stack.getItem() instanceof PhoneItem)) {
            return PhoneNetView.NONE;
        }
        boolean airplane = record.airplane();
        NetworkStatus status = PhoneNetwork.status(viewer, stack, airplane);
        PhoneNetView full = new PhoneNetView(status.sim(), status.operatorName(), status.bars(),
                status.number().isEmpty() ? "" : SimNumbers.format(status.number()), status.holderName(), false,
                false, List.of(), List.of());
        if (session.lock() != Lock.UNLOCKED) {
            return full.locked();
        }
        if (!viewer.getUUID().equals(record.owner())) {
            return full;
        }
        boolean founder = PhoneOperators.get(viewer.server).byFounder(viewer.getUUID()).isPresent();
        List<PhoneNetView.OperatorChoice> requestable = airplane || status.sim() == PhoneSims.Validity.ACTIVE
                ? List.of() : requestable(viewer);
        return new PhoneNetView(full.sim(), full.operator(), full.bars(), full.number(), full.holder(),
                founder, OperatorPanels.canOpenFromPhone(viewer, session), PhoneSims.choices(viewer), requestable);
    }

    /**
     * Un essai de code, par {@link PhoneLock#attempt} : compte l'échec, pose ou garde le blocage (de plus en plus long,
     * selon le niveau du téléphone), et sauvegarde ce qui a changé : échecs, fin de blocage (plafonnée), niveau. Rend
     * {@link Result#NONE} si le code est accepté.
     */
    private static Result check(PhoneData data, UUID phoneId, PhoneRecord record, String code) {
        long now = EconomyClock.now();
        boolean correct = PhoneCode.matches(record.salt(), record.codeHash(), code);
        PhoneLock.Outcome o = PhoneLock.attempt(correct, record.failures(), record.lockedUntil(),
                record.lockoutLevel(), now, PhoneConfig.maxAttempts(), lockoutBaseMillis(), lockoutMaxMillis());
        // Le niveau compte à lui seul : un bon code après un blocage expiré ne change souvent que lui.
        if (o.failures() != record.failures() || o.lockedUntil() != record.lockedUntil()
                || o.level() != record.lockoutLevel()) {
            data.put(phoneId, new PhoneRecord(record.owner(), record.ownerName(), record.salt(), record.codeHash(),
                    o.failures(), o.lockedUntil(), o.level(), record.wallpaper(), record.airplane()));
        }
        if (o.accepted()) {
            return Result.NONE;
        }
        return now < o.lockedUntil() ? Result.LOCKED_OUT : Result.WRONG_CODE;
    }

    // ---------------------------------------------------------------------------------------------
    // La vue
    // ---------------------------------------------------------------------------------------------

    /**
     * Met le verrou de la session d'accord avec ce que le téléphone sait. Il a pu changer sous la session (copie
     * effacée, {@code /phone reset}, code posé ou changé ailleurs) : on suit. Une session déverrouillée ne l'est que par
     * le code en cours, reconnu à son sel.
     */
    private static void syncLock(PhoneSession session, PhoneRecord record) {
        if (!hasCode(record)) {
            session.setLock(Lock.SETUP);
        } else if (session.lock() == Lock.SETUP) {
            session.setLock(Lock.LOCKED);
        } else if (session.lock() == Lock.UNLOCKED && !record.salt().equals(session.unlockedSalt())) {
            session.setLock(Lock.LOCKED);
        }
    }

    /**
     * Ce que {@code viewer} voit de sa session : hors {@link Lock#UNLOCKED}, rien du propriétaire ni des réglages. La
     * vue relit le sel : une session dont le sel n'est plus celui du téléphone (effacé, nouveau code) est reverrouillée
     * ici même, pour que les {@code UPDATE} poussés au tic ne montrent jamais ses anciens réglages ni ceux du nouveau
     * propriétaire. Note le pourcentage envoyé : le client le connaît désormais.
     */
    static PhoneStatePacket view(ServerPlayer viewer, PhoneSession session, Kind kind, Result last,
            CloseReason reason) {
        return view(viewer, session, kind, last, reason, null);
    }

    /**
     * Comme {@link #view(ServerPlayer, PhoneSession, Kind, Result, CloseReason)}, avec la vue réseau déjà calculée
     * dans le même tic après {@link #syncLock} (relevé du signal) : elle n'est pas recalculée.
     */
    private static PhoneStatePacket view(ServerPlayer viewer, PhoneSession session, Kind kind, Result last,
            CloseReason reason, @Nullable PhoneNetView computed) {
        PhoneRecord record = PhoneData.get(viewer.server).record(session.phoneId());
        syncLock(session, record);
        long now = EconomyClock.now();
        long until = PhoneLock.capped(record.lockedUntil(), now, record.lockoutLevel(), lockoutBaseMillis(),
                lockoutMaxMillis());
        long remaining = until - now;
        int lockoutSeconds = remaining > 0 ? (int) ((remaining + 999L) / 1000L) : 0;
        int attemptsLeft = lockoutSeconds > 0 ? 0 : Math.max(0, PhoneConfig.maxAttempts() - record.failures());
        boolean unlocked = session.lock() == Lock.UNLOCKED;
        ItemStack stack = viewer.getItemInHand(session.hand());
        // La pile tenue n'est peut-être plus le téléphone de la session (fermeture PHONE_GONE, ouverture d'un autre
        // téléphone vide, /phone reset après un changement de case) : rien d'elle ne part, ni sa ligne ni sa batterie
        // (revue finale du lot T2, I1).
        boolean same = stack.getItem() instanceof PhoneItem
                && PhoneItem.phoneId(stack).equals(Optional.of(session.phoneId()));
        int battery = same ? percent(stack) : 0;
        session.showPercent(battery);
        int offset = EconomyCalendar.configuredZone().getRules().getOffset(Instant.ofEpochMilli(now))
                .getTotalSeconds();
        PhoneNetView net = !same ? PhoneNetView.NONE
                : computed != null ? computed : netView(viewer, session, stack, record);
        session.showNet(net);
        return new PhoneStatePacket(session.id(), kind, session.lock(), battery, now, offset, record.wallpaper(),
                unlocked && record.airplane(), unlocked && viewer.getUUID().equals(record.owner()),
                unlocked ? record.ownerName() : "", attemptsLeft, lockoutSeconds, last, reason, net);
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** La batterie du téléphone en pour cent, arrondi vers le bas ; 0 si la pile n'est pas un téléphone. */
    private static int percent(ItemStack stack) {
        return stack.getItem() instanceof PhoneItem
                ? (int) (PhoneItem.stored(stack) * 100L / Math.max(1, PhoneConfig.capacity()))
                : 0;
    }

    private static boolean hasCode(PhoneRecord record) {
        return record.salt() != null && record.codeHash() != null;
    }

    private static PhoneRecord withSettings(PhoneRecord r, String wallpaper, boolean airplane) {
        return new PhoneRecord(r.owner(), r.ownerName(), r.salt(), r.codeHash(), r.failures(), r.lockedUntil(),
                r.lockoutLevel(), wallpaper, airplane);
    }

    private static long lockoutBaseMillis() {
        return PhoneConfig.lockoutSeconds() * 1000L;
    }

    private static long lockoutMaxMillis() {
        return PhoneConfig.lockoutMaxSeconds() * 1000L;
    }

    private record SessionOf(ServerPlayer player, PhoneSession session) {}

    /** Les sessions des joueurs en ligne ouvertes sur ce téléphone, sauf celle de {@code except} (nul : toutes). */
    private static List<SessionOf> othersOn(MinecraftServer server, UUID phoneId, @Nullable ServerPlayer except) {
        List<SessionOf> out = new ArrayList<>();
        for (Map.Entry<UUID, PhoneSession> e : SESSIONS.entrySet()) {
            if ((except != null && e.getKey().equals(except.getUUID())) || !e.getValue().phoneId().equals(phoneId)) {
                continue;
            }
            ServerPlayer other = server.getPlayerList().getPlayer(e.getKey());
            if (other != null) {
                out.add(new SessionOf(other, e.getValue()));
            }
        }
        return out;
    }

    /** Un client parti entre l'envoi et le traitement, ou un automate. */
    private static boolean gone(@Nullable ServerPlayer player) {
        return player == null || player instanceof FakePlayer || player.connection == null
                || !player.connection.isAcceptingMessages()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player;
    }

    private static void send(ServerPlayer player, PhoneStatePacket packet) {
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Vrai si ce joueur a déjà fait un geste il y a moins de {@link #MIN_TICKS_BETWEEN} tics ; sinon note ce tic. */
    private static boolean throttled(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer previous = LAST_GESTURE.get(player.getUUID());
        if (previous != null && now - previous >= 0 && now - previous < MIN_TICKS_BETWEEN) {
            return true;
        }
        LAST_GESTURE.put(player.getUUID(), now);
        return false;
    }

    /**
     * Oublie le frein de ce joueur (déconnexion). Visibilité de paquet : aucun autre code du mod ne lève le frein ; les
     * essais qui enchaînent des gestes dans le même tic l'appellent par réflexion.
     */
    static void clearThrottle(UUID player) {
        LAST_GESTURE.remove(player);
    }

    // ---------------------------------------------------------------------------------------------
    // Bus de Forge
    // ---------------------------------------------------------------------------------------------

    /**
     * À chaque tic, pour chaque session : joueur parti, mort, ou téléphone plus dans la main de la session (autre
     * objet ou autre {@code phone_id}) : fermée ({@link CloseReason#PHONE_GONE}). Sinon l'écran ouvert coûte
     * {@link PhoneConfig#drainPerTick()} par tic, payé par blocs de vingt tics ({@code ItemBattery.Prepaid}) :
     * un bloc que la batterie ne couvre plus la met à zéro et ferme ({@link CloseReason#BATTERY_EMPTY}). Un
     * pourcentage qui change est poussé en {@link Kind#UPDATE}. Écran fermé, il n'y a plus de session : aucune dépense.
     * Toutes les {@link PhoneConfig#signalRefreshTicks()} tics, la vue réseau est relevée (position, dimension,
     * antennes) et poussée en {@link Kind#UPDATE} seulement si elle a changé : jamais deux fois la même vue.
     */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, PhoneSession> entry : new ArrayList<>(SESSIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            PhoneSession session = entry.getValue();
            if (player == null) {
                SESSIONS.remove(entry.getKey(), session);
            } else if (SESSIONS.get(entry.getKey()) == session) {
                tick(player, session);
            }
        }
    }

    private static void tick(ServerPlayer player, PhoneSession session) {
        ItemStack stack = player.getItemInHand(session.hand());
        if (!player.isAlive() || !(stack.getItem() instanceof PhoneItem)
                || !PhoneItem.phoneId(stack).equals(Optional.of(session.phoneId()))) {
            close(player, CloseReason.PHONE_GONE);
            return;
        }
        if (!session.prepaid().pay(stack, PhoneConfig.drainPerTick(), PhoneConfig.capacity())) {
            PhoneItem.store(stack, 0);
            close(player, CloseReason.BATTERY_EMPTY);
            player.displayClientMessage(Component.translatable(MESSAGE + "battery_empty"), true);
            return;
        }
        if (percent(stack) != session.shownPercent()) {
            send(player, view(player, session, Kind.UPDATE, Result.NONE, CloseReason.NONE));
        } else if (session.refreshDue(PhoneConfig.signalRefreshTicks())) {
            PhoneRecord record = PhoneData.get(player.server).record(session.phoneId());
            syncLock(session, record);
            PhoneNetView net = netView(player, session, stack, record);
            if (!net.equals(session.shownNet())) {
                // Calculée une fois : view relit le même enregistrement dans le même tic, sans le recalculer.
                send(player, view(player, session, Kind.UPDATE, Result.NONE, CloseReason.NONE, net));
            }
        }
    }

    /** La session et le frein d'un joueur parti. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        SESSIONS.remove(player);
        clearThrottle(player);
    }
}
