package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.call.CallBook.Call;
import dev.oneforallsurvival.phone.call.CallBook.End;
import dev.oneforallsurvival.phone.call.CallBook.Party;
import dev.oneforallsurvival.phone.call.Emergency.Dispatch;
import dev.oneforallsurvival.phone.call.Emergency.Service;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.RelayFailure;
import dev.oneforallsurvival.phone.net.RelayPlanner;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;

/**
 * Les appels d'urgence côté serveur (feature 95, lot T3, tâche 8) : {@code 17} (police, rôle {@code policier}) et
 * {@code 15} (secours, rôle {@code medecin}).
 *
 * <p>Ni carte, ni abonnement, ni opérateur : il faut un téléphone chargé, hors mode avion, et une antenne en service à
 * portée, de n'importe quel opérateur ({@link PhoneNetwork#emergencyAntenna}). Un appel par joueur toutes les
 * {@code emergencyCooldownSeconds} ; le frein des appels composés ne s'applique pas.
 *
 * <p>Chaque secours connecté, vivant, du bon rôle (autre que l'appelant) lit un message de chat avec le service, le
 * nom de l'appelant et sa position ; ceux qui portent un téléphone chargé hors mode avion, libres et à portée d'une
 * antenne, le voient sonner. Le premier qui répond prend l'appel : il devient un appel en ligne ordinaire du carnet
 * ({@link CallService#startEmergency}), voix comprise ; les autres cessent de sonner et lisent « Pris en charge par … ».
 *
 * <p>Pendant la sonnerie, le trafic est ouvert sur l'antenne de l'appelant par {@link CallService#openLine} et fermé par
 * {@link CallService#closeLine}, le même point unique que les autres appels. Les numéros des urgences qui sonnent
 * partent de {@value #ID_BASE} : jamais ceux du carnet des appels. Fil du serveur seulement ; positions seulement aux
 * secours et à l'équipe ({@code /phone urgences}).
 */
public final class EmergencyService {

    public static final String MESSAGE = CallService.MESSAGE + "emergency.";
    /** Premier numéro d'une urgence qui sonne (entier positif de dix chiffres, que la page renvoie tel quel). */
    static final int ID_BASE = 1_000_000_000;

    /** Un secours dont le téléphone sonne. {@code simId} : sa carte logée s'il en a une (pour ses récents). */
    private record Responder(UUID player, String name, UUID phoneId, @Nullable UUID simId) {}

    /** Une urgence qui sonne. */
    private static final class Pending {
        final Dispatch dispatch;
        final UUID callerPhone;
        final @Nullable SimCard callerSim;
        final String callerName;
        final String detail;
        final long logSeq;
        final Map<UUID, Responder> responders = new LinkedHashMap<>();
        RelayPlanner.AntennaPoint antenna;

        Pending(Dispatch dispatch, UUID callerPhone, @Nullable SimCard callerSim, String callerName, String detail,
                long logSeq, RelayPlanner.AntennaPoint antenna) {
            this.dispatch = dispatch;
            this.callerPhone = callerPhone;
            this.callerSim = callerSim;
            this.callerName = callerName;
            this.detail = detail;
            this.logSeq = logSeq;
            this.antenna = antenna;
        }
    }

    private static final Map<Integer, Pending> PENDING = new LinkedHashMap<>();
    private static final Emergency.Cooldown COOLDOWN = new Emergency.Cooldown();
    private static int nextId = ID_BASE;

    private EmergencyService() {}

    private static long cooldownTicks() {
        return PhoneConfig.emergencyCooldownSeconds() * 20L;
    }

    private static GlobalPos where(ServerPlayer player) {
        return GlobalPos.of(player.level().dimension(), player.blockPosition());
    }

    private static boolean airplane(MinecraftServer server, UUID phoneId) {
        return PhoneData.get(server).record(phoneId).airplane();
    }

    private static CallService.Dial failed(Component message) {
        return new CallService.Dial(CallService.Outcome.FAILED, message, 0);
    }

    // ---------------------------------------------------------------------------------------------
    // Composer
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code caller} compose le numéro de {@code service} sur {@code phone}. Dans cet ordre : déjà en appel
     * (« Raccrochez d'abord ») ; batterie vide ; mode avion ; aucune antenne en service à portée ; aucun secours
     * connecté (« Aucun service disponible ») ; délai ; puis l'alerte et la sonnerie.
     */
    static CallService.Dial dial(ServerPlayer caller, ItemStack phone, boolean airplane, Service service) {
        MinecraftServer server = caller.server;
        if (CallService.callOf(caller).isPresent() || involved(caller.getUUID())) {
            return failed(Component.translatable(CallService.MESSAGE + "hang_up_first"));
        }
        Optional<UUID> phoneId = phone == null ? Optional.empty() : PhoneItem.phoneId(phone);
        if (phoneId.isEmpty() || PhoneCarriers.holding(caller, phoneId.get()).isEmpty()) {
            return new CallService.Dial(CallService.Outcome.FAILED, null, 0);
        }
        if (PhoneItem.stored(phone) <= 0) {
            return failed(Component.translatable(PhoneService.MESSAGE + "battery_empty"));
        }
        if (airplane || airplane(server, phoneId.get())) {
            return failed(new RelayFailure(RelayFailure.Reason.CALLER_AIRPLANE, "").describe());
        }
        GlobalPos here = where(caller);
        Optional<RelayPlanner.AntennaPoint> antenna = PhoneNetwork.emergencyAntenna(server, here);
        if (antenna.isEmpty()) {
            return failed(new RelayFailure(RelayFailure.Reason.CALLER_NO_SIGNAL, "").describe());
        }
        List<ServerPlayer> responders = responders(server, caller, service);
        if (responders.isEmpty()) {
            return failed(Component.translatable(MESSAGE + "none"));
        }
        long tick = server.getTickCount();
        if (!COOLDOWN.allow(caller.getUUID(), tick, cooldownTicks())) {
            return new CallService.Dial(CallService.Outcome.TOO_FAST, Component.translatable(MESSAGE + "cooldown",
                    PhoneConfig.emergencyCooldownSeconds()), 0);
        }
        String name = caller.getGameProfile().getName();
        String dimension = here.dimension().location().toString();
        int x = here.pos().getX();
        int y = here.pos().getY();
        int z = here.pos().getZ();
        EmergencyLogData data = EmergencyLogData.get(server);
        long seq = data.log().add(EconomyClock.now(), caller.getUUID(), name, service.number(), dimension, x, y, z);
        data.setDirty();

        Component alert = Component.translatable(MESSAGE + "alert", Component.translatable(MESSAGE + service.key()),
                name, x, y, z, dimension);
        List<Responder> ringing = new ArrayList<>();
        for (ServerPlayer r : responders) {
            r.sendSystemMessage(alert);
            ringable(server, r).ifPresent(ringing::add);
        }
        int id = takeId();
        Dispatch dispatch = new Dispatch(id, caller.getUUID(), service, tick,
                ringing.stream().map(Responder::player).toList());
        String detail = name + " · " + x + " " + y + " " + z + " · " + here.dimension().location().getPath();
        Pending pending = new Pending(dispatch, phoneId.get(), PhoneSims.line(server, phone).orElse(null), name,
                detail, seq, antenna.get());
        for (Responder r : ringing) {
            pending.responders.put(r.player(), r);
        }
        PENDING.put(id, pending);
        CallService.openLine(server, id, new RelayPlanner.Route(antenna.get(), antenna.get()));
        sendCaller(server, pending, null);
        for (Responder r : ringing) {
            ServerPlayer p = server.getPlayerList().getPlayer(r.player());
            if (p != null) {
                CallUpdates.send(p, responderView(p, pending, r));
            }
        }
        return new CallService.Dial(CallService.Outcome.RINGING, null, id);
    }

    private static int takeId() {
        while (PENDING.containsKey(nextId)) {
            nextId = nextId == Integer.MAX_VALUE ? ID_BASE : nextId + 1;
        }
        int id = nextId;
        nextId = nextId == Integer.MAX_VALUE ? ID_BASE : nextId + 1;
        return id;
    }

    /** Les secours de ce service : connectés, vivants, du bon rôle, autres que l'appelant. */
    private static List<ServerPlayer> responders(MinecraftServer server, ServerPlayer caller, Service service) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != caller && !(p instanceof FakePlayer) && p.isAlive() && hasRole(server, p, service)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Ce joueur a-t-il le rôle de ce service (lu à chaque fois : un rôle perdu pendant la sonnerie compte) ? */
    private static boolean hasRole(MinecraftServer server, ServerPlayer p, Service service) {
        return service.role().equals(RoleSavedData.get(server).book().roleOf(p.getUUID()).id());
    }

    /** Le téléphone qui sonnera chez ce secours : libre, téléphone chargé hors mode avion, antenne à portée. */
    private static Optional<Responder> ringable(MinecraftServer server, ServerPlayer p) {
        if (CallService.callOf(p).isPresent() || involved(p.getUUID())
                || PhoneNetwork.emergencyAntenna(server, where(p)).isEmpty()) {
            return Optional.empty();
        }
        Optional<ItemStack> phone = PhoneCarriers.first(p, s -> PhoneItem.phoneId(s).isPresent()
                && PhoneItem.stored(s) > 0 && !airplane(server, PhoneItem.phoneId(s).get()));
        return phone.map(s -> new Responder(p.getUUID(), p.getGameProfile().getName(), PhoneItem.phoneId(s).get(),
                PhoneSims.line(server, s).map(SimCard::simId).orElse(null)));
    }

    // ---------------------------------------------------------------------------------------------
    // Vues
    // ---------------------------------------------------------------------------------------------

    private static PhoneCallStatePacket callerView(ServerPlayer caller, Pending p, @Nullable Component message) {
        return new PhoneCallStatePacket(Phase.OUTGOING, p.dispatch.id(), p.dispatch.service().number(), "", -1L,
                false, false, CallUpdates.openOn(caller, p.callerPhone), message, null);
    }

    private static PhoneCallStatePacket responderView(ServerPlayer player, Pending p, Responder r) {
        return new PhoneCallStatePacket(Phase.INCOMING, p.dispatch.id(), p.dispatch.service().number(),
                p.callerName, -1L, false, false, CallUpdates.openOn(player, r.phoneId()), null, null, p.detail);
    }

    private static void sendCaller(MinecraftServer server, Pending p, @Nullable Component message) {
        ServerPlayer caller = server.getPlayerList().getPlayer(p.dispatch.caller());
        if (caller != null) {
            CallUpdates.send(caller, callerView(caller, p, message));
        }
    }

    /** L'urgence qui sonne pour ce joueur (appelant ou secours), vue de lui ; vide sinon. */
    static Optional<PhoneCallStatePacket> view(ServerPlayer player) {
        for (Pending p : PENDING.values()) {
            if (p.dispatch.caller().equals(player.getUUID())) {
                return Optional.of(callerView(player, p, null));
            }
            Responder r = p.responders.get(player.getUUID());
            if (r != null && p.dispatch.ringsFor(player.getUUID())) {
                return Optional.of(responderView(player, p, r));
            }
        }
        return Optional.empty();
    }

    /** Ce joueur est-il dans une urgence qui sonne (appelant, ou secours chez qui elle sonne) ? */
    public static boolean involved(UUID player) {
        for (Pending p : PENDING.values()) {
            if (p.dispatch.involves(player)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Gestes
    // ---------------------------------------------------------------------------------------------

    private static Optional<Pending> pending(int id) {
        return Optional.ofNullable(PENDING.get(id));
    }

    /**
     * Ce secours répond sur {@code phoneId} : le premier prend l'appel, qui devient un appel en ligne entre lui et
     * l'appelant. Faux s'il ne sonnait pas chez lui, sur ce téléphone, ou si un autre l'a pris.
     */
    public static boolean answer(ServerPlayer responder, UUID phoneId, int id) {
        Optional<Pending> found = pending(id);
        if (responder == null || !responder.isAlive() || found.isEmpty()) {
            return false;
        }
        Pending p = found.get();
        Responder r = p.responders.get(responder.getUUID());
        MinecraftServer server = responder.server;
        if (r == null || !r.phoneId().equals(phoneId) || !p.dispatch.ringsFor(responder.getUUID())) {
            return false;
        }
        Optional<RelayPlanner.AntennaPoint> theirs = stillRinging(server, responder, r, p.dispatch.service());
        if (theirs.isEmpty() || !p.dispatch.take(responder.getUUID())) {
            return false;
        }
        PENDING.remove(id);
        CallService.closeLine(id);
        ServerPlayer caller = server.getPlayerList().getPlayer(p.dispatch.caller());
        Party from = new Party(p.dispatch.caller(), p.callerPhone,
                p.callerSim == null ? UUID.randomUUID() : p.callerSim.simId(),
                p.callerSim == null ? "" : p.callerSim.number());
        Party to = new Party(r.player(), r.phoneId(), r.simId() == null ? UUID.randomUUID() : r.simId(),
                p.dispatch.service().number());
        CallService.EmergencyCall info = new CallService.EmergencyCall(p.dispatch.service(), p.callerSim != null,
                r.simId() != null, p.callerName, p.detail);
        Optional<Call> call = caller == null ? Optional.empty()
                : CallService.startEmergency(server, from, to, info,
                        new RelayPlanner.Route(p.antenna, theirs.get()));
        EmergencyLogData data = EmergencyLogData.get(server);
        if (call.isPresent() && data.log().answered(p.logSeq, r.name())) {
            data.setDirty();
        }
        // Prise refusée par le carnet : personne n'a pris l'appel, les autres lisent l'interruption, pas « Pris en charge ».
        Component taken = call.isPresent() ? Component.translatable(MESSAGE + "taken", r.name())
                : Component.translatable(CallUpdates.ENDED + "interrupted");
        for (UUID other : p.dispatch.ringing()) {
            Responder o = p.responders.get(other);
            ServerPlayer op = server.getPlayerList().getPlayer(other);
            if (o != null && op != null) {
                stopRinging(op, p, o, taken);
            }
        }
        if (call.isEmpty()) {
            // L'appelant est parti entre-temps, ou le carnet refuse l'appel (un bout déjà en ligne) : rien à prendre,
            // et l'appelant, s'il est là, lit la fin au lieu de rester sur une sonnerie morte.
            CallUpdates.send(responder, CallUpdates.current(responder,
                    Component.translatable(CallUpdates.ENDED + "party_left"), null));
            if (caller != null) {
                sendEnd(caller, p, Component.translatable(CallUpdates.ENDED + "interrupted"));
            }
            return false;
        }
        return true;
    }

    /** Ce secours refuse : son téléphone cesse de sonner, l'urgence sonne encore chez les autres. Jamais freiné. */
    public static boolean decline(ServerPlayer responder, UUID phoneId, int id) {
        Optional<Pending> found = pending(id);
        if (responder == null || found.isEmpty()) {
            return false;
        }
        Responder r = found.get().responders.get(responder.getUUID());
        if (r == null || !r.phoneId().equals(phoneId) || !found.get().dispatch.drop(responder.getUUID())) {
            return false;
        }
        stopRinging(responder, found.get(), r, null);
        return true;
    }

    /** L'appelant raccroche pendant la sonnerie. Jamais freiné. */
    public static boolean hangUp(ServerPlayer caller, UUID phoneId, int id) {
        Optional<Pending> found = pending(id);
        if (caller == null || found.isEmpty() || !found.get().dispatch.caller().equals(caller.getUUID())
                || !found.get().callerPhone.equals(phoneId)) {
            return false;
        }
        end(caller.server, found.get(), End.HUNG_UP);
        return true;
    }

    private static void stopRinging(ServerPlayer player, Pending p, Responder r, @Nullable Component message) {
        CallUpdates.send(player, new PhoneCallStatePacket(Phase.NONE, p.dispatch.id(), p.dispatch.service().number(),
                p.callerName, -1L, false, false, CallUpdates.openOn(player, r.phoneId()), message, null));
        if (message != null && !CallUpdates.openOn(player, r.phoneId())) {
            player.displayClientMessage(message, true);
        }
    }

    /**
     * Le téléphone de ce secours sonne-t-il encore : il le porte, chargé, hors mode avion, une antenne à portée ? Rend
     * cette antenne.
     */
    private static Optional<RelayPlanner.AntennaPoint> stillRinging(MinecraftServer server, ServerPlayer player,
            Responder r, Service service) {
        if (!player.isAlive() || airplane(server, r.phoneId()) || !hasRole(server, player, service)) {
            return Optional.empty();
        }
        Optional<ItemStack> phone = PhoneCarriers.holding(player, r.phoneId());
        if (phone.isEmpty() || PhoneItem.stored(phone.get()) <= 0) {
            return Optional.empty();
        }
        return PhoneNetwork.emergencyAntenna(server, where(player));
    }

    // ---------------------------------------------------------------------------------------------
    // Fin d'une urgence qui sonne
    // ---------------------------------------------------------------------------------------------

    /**
     * Fin sans réponse (sonnerie écoulée, appelant qui raccroche ou perd son téléphone) : la poignée est fermée,
     * l'appelant lit la fin, chaque secours qui sonnait cesse de sonner (manqué) ; récents des cartes logées.
     */
    private static void end(MinecraftServer server, Pending p, End reason) {
        if (PENDING.remove(p.dispatch.id()) == null) {
            return;
        }
        try {
            CallService.closeLine(p.dispatch.id());
        } finally {
            long at = EconomyClock.now();
            String number = p.dispatch.service().number();
            PhoneDirectory directory = PhoneDirectory.get(server);
            if (p.callerSim != null) {
                directory.log(p.callerSim.simId(), new Recent(number, Recent.Kind.OUTGOING, at, 0));
            }
            Component missed = Component.translatable(CallUpdates.ENDED + "missed");
            for (UUID other : p.dispatch.ringing()) {
                Responder r = p.responders.get(other);
                if (r == null) {
                    continue;
                }
                if (r.simId() != null) {
                    directory.log(r.simId(), new Recent(number, Recent.Kind.MISSED, at, 0));
                }
                ServerPlayer op = server.getPlayerList().getPlayer(other);
                if (op != null) {
                    stopRinging(op, p, r, missed);
                }
            }
            ServerPlayer caller = server.getPlayerList().getPlayer(p.dispatch.caller());
            if (caller != null) {
                sendEnd(caller, p, Component.translatable(endKey(reason)));
            }
        }
    }

    /** La clé du message de fin chez l'appelant : « Aucune réponse » est propre aux urgences. */
    static String endKey(End reason) {
        return reason == End.NO_ANSWER ? MESSAGE + "no_answer"
                : CallUpdates.ENDED + CallUpdates.endKey(reason, true);
    }

    /** La fin d'une urgence qui sonnait, chez l'appelant (et en barre d'action sans écran ouvert sur ce téléphone). */
    private static void sendEnd(ServerPlayer caller, Pending p, Component message) {
        boolean open = CallUpdates.openOn(caller, p.callerPhone);
        CallUpdates.send(caller, new PhoneCallStatePacket(Phase.NONE, p.dispatch.id(), p.dispatch.service().number(),
                "", -1L, false, false, open, message, null));
        if (!open) {
            caller.displayClientMessage(message, true);
        }
    }

    /** Départ, mort, changement de dimension : l'urgence de ce joueur finit, ou il cesse d'y sonner. */
    static void endFor(MinecraftServer server, UUID player, End reason) {
        for (Pending p : new ArrayList<>(PENDING.values())) {
            if (p.dispatch.caller().equals(player)) {
                end(server, p, reason);
            } else if (p.dispatch.drop(player)) {
                ServerPlayer op = server.getPlayerList().getPlayer(player);
                Responder r = p.responders.get(player);
                if (op != null && r != null) {
                    stopRinging(op, p, r, null);
                }
            }
        }
    }

    /** Arrêt du serveur : chaque urgence qui sonne finit (poignées fermées), puis tout est oublié. */
    static void stopAll(MinecraftServer server) {
        for (Pending p : new ArrayList<>(PENDING.values())) {
            end(server, p, End.SERVER_STOPPING);
        }
        PENDING.clear();
        COOLDOWN.clear();
    }

    /** Les {@value EmergencyLog#SHOWN} derniers appels d'urgence du journal. */
    public static List<EmergencyLog.Entry> latest(MinecraftServer server) {
        return EmergencyLogData.get(server).log().latest(EmergencyLog.SHOWN);
    }

    /** Oublie le délai de ce joueur (essais). */
    public static void forgetCooldown() {
        COOLDOWN.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Tic : relevé, sonnerie, expiration
    // ---------------------------------------------------------------------------------------------

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % CallService.CHECK_PERIOD == 0) {
                COOLDOWN.purge(event.getServer().getTickCount(), cooldownTicks());
            }
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        boolean check = tick % CallService.CHECK_PERIOD == 0;
        for (Pending p : new ArrayList<>(PENDING.values())) {
            if (check) {
                Optional<End> verdict = callerVerdict(server, p);
                if (verdict.isPresent()) {
                    end(server, p, verdict.get());
                    continue;
                }
                for (UUID other : p.dispatch.ringing()) {
                    ServerPlayer op = server.getPlayerList().getPlayer(other);
                    Responder r = p.responders.get(other);
                    if (op == null || r == null || stillRinging(server, op, r, p.dispatch.service()).isEmpty()) {
                        p.dispatch.drop(other);
                        if (op != null && r != null) {
                            stopRinging(op, p, r, null);
                        }
                    }
                }
            }
            if (p.dispatch.expired(tick, PhoneConfig.callRingSeconds() * 20)) {
                end(server, p, End.NO_ANSWER);
                continue;
            }
            if ((tick - p.dispatch.startedTick()) % CallUpdates.RING_PERIOD == 0) {
                ring(server, p);
            }
        }
    }

    /** Ce qui coupe l'urgence côté appelant ; rouvre la poignée si son antenne a changé. */
    private static Optional<End> callerVerdict(MinecraftServer server, Pending p) {
        ServerPlayer caller = server.getPlayerList().getPlayer(p.dispatch.caller());
        if (caller == null) {
            return Optional.of(End.PARTY_LEFT);
        }
        if (!caller.isAlive()) {
            return Optional.of(End.DIED);
        }
        Optional<ItemStack> phone = PhoneCarriers.holding(caller, p.callerPhone);
        if (phone.isEmpty() || PhoneItem.stored(phone.get()) <= 0) {
            return Optional.of(End.PHONE_GONE);
        }
        if (airplane(server, p.callerPhone)) {
            return Optional.of(End.NO_SIGNAL);
        }
        Optional<RelayPlanner.AntennaPoint> antenna = PhoneNetwork.emergencyAntenna(server, where(caller));
        if (antenna.isEmpty()) {
            return Optional.of(End.NO_SIGNAL);
        }
        if (!antenna.get().id().equals(p.antenna.id())) {
            p.antenna = antenna.get();
            CallService.openLine(server, p.dispatch.id(), new RelayPlanner.Route(p.antenna, p.antenna));
        }
        return Optional.empty();
    }

    private static void ring(MinecraftServer server, Pending p) {
        for (UUID other : p.dispatch.ringing()) {
            ServerPlayer r = server.getPlayerList().getPlayer(other);
            Responder who = p.responders.get(other);
            if (r == null || who == null || !r.isAlive()) {
                continue;
            }
            ClientboundSoundPacket bell = new ClientboundSoundPacket(CallUpdates.RING_SOUND, SoundSource.PLAYERS,
                    r.getX(), r.getY(), r.getZ(), 1.0f, 1.2f, r.getRandom().nextLong());
            for (ServerPlayer near : r.serverLevel().players()) {
                if (near.connection != null
                        && near.distanceToSqr(r) <= CallUpdates.RING_RANGE * CallUpdates.RING_RANGE) {
                    near.connection.send(bell);
                }
            }
            if (!CallUpdates.openOn(r, who.phoneId())) {
                r.displayClientMessage(Component.translatable(MESSAGE + "incoming"), true);
            }
        }
        ServerPlayer caller = server.getPlayerList().getPlayer(p.dispatch.caller());
        if (caller != null && caller.connection != null) {
            caller.connection.send(new ClientboundSoundPacket(CallUpdates.TONE_SOUND, SoundSource.PLAYERS,
                    caller.getX(), caller.getY(), caller.getZ(), 0.4f, 0.7f, caller.getRandom().nextLong()));
        }
    }
}
