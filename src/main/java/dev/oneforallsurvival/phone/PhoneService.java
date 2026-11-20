package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneData.PhoneRecord;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
        return Outcome.OPENED;
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
        if (throttled(player)) {
            return Outcome.THROTTLED;
        }
        PhoneData data = PhoneData.get(player.server);
        PhoneRecord record = data.record(session.phoneId());
        syncLock(session, record);
        Result[] last = {Result.NONE};
        Outcome outcome = switch (packet.action()) {
            case CLOSE -> {
                SESSIONS.remove(player.getUUID(), session);
                yield Outcome.DONE;
            }
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
                PhoneCode.hash(salt, code), 0, record.lockedUntil(), chosen, record.airplane()));
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
                now.failures(), now.lockedUntil(), now.wallpaper(), now.airplane()));
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

    /**
     * Un essai de code, par {@link PhoneLock#attempt} : compte l'échec, pose ou garde le blocage, et sauvegarde la
     * valeur rendue (plafonnée). Rend {@link Result#NONE} si le code est accepté.
     */
    private static Result check(PhoneData data, UUID phoneId, PhoneRecord record, String code) {
        long now = EconomyClock.now();
        boolean correct = PhoneCode.matches(record.salt(), record.codeHash(), code);
        PhoneLock.Outcome o = PhoneLock.attempt(correct, record.failures(), record.lockedUntil(), now,
                PhoneConfig.maxAttempts(), lockoutMillis());
        if (o.failures() != record.failures() || o.lockedUntil() != record.lockedUntil()) {
            data.put(phoneId, new PhoneRecord(record.owner(), record.ownerName(), record.salt(), record.codeHash(),
                    o.failures(), o.lockedUntil(), record.wallpaper(), record.airplane()));
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
        PhoneRecord record = PhoneData.get(viewer.server).record(session.phoneId());
        syncLock(session, record);
        long now = EconomyClock.now();
        long until = PhoneLock.capped(record.lockedUntil(), now, lockoutMillis());
        long remaining = until - now;
        int lockoutSeconds = remaining > 0 ? (int) ((remaining + 999L) / 1000L) : 0;
        int attemptsLeft = lockoutSeconds > 0 ? 0 : Math.max(0, PhoneConfig.maxAttempts() - record.failures());
        boolean unlocked = session.lock() == Lock.UNLOCKED;
        ItemStack stack = viewer.getItemInHand(session.hand());
        int battery = percent(stack);
        session.showPercent(battery);
        int offset = EconomyCalendar.configuredZone().getRules().getOffset(Instant.ofEpochMilli(now))
                .getTotalSeconds();
        return new PhoneStatePacket(session.id(), kind, session.lock(), battery, now, offset, record.wallpaper(),
                unlocked && record.airplane(), unlocked && viewer.getUUID().equals(record.owner()),
                unlocked ? record.ownerName() : "", attemptsLeft, lockoutSeconds, last, reason);
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
                wallpaper, airplane);
    }

    private static long lockoutMillis() {
        return PhoneConfig.lockoutSeconds() * 1000L;
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
        }
    }

    /** La session et le frein d'un joueur parti. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        SESSIONS.remove(player);
        clearThrottle(player);
    }
}
