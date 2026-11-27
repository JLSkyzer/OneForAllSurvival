package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.machine.ItemBattery;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneSession;
import dev.oneforallsurvival.phone.call.CallBook.Call;
import dev.oneforallsurvival.phone.call.CallBook.End;
import dev.oneforallsurvival.phone.call.CallBook.Party;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Book;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Ce que les porteurs reçoivent des appels (lot T3, tâche 4a), écran fermé compris : l'état d'appel
 * ({@link PhoneCallStatePacket}) à chaque changement et à chaque fin, aux deux bouts ; la sonnerie et la tonalité ; le
 * message de fin en barre d'action quand aucun écran n'est ouvert sur ce téléphone ; la batterie qui se vide pendant
 * l'appel.
 *
 * <p>Sonnerie : au premier tic où l'appel sonne, puis toutes les {@value #RING_PERIOD} tics, un son de cloche joué à la
 * position de l'appelé, envoyé à tous les joueurs à {@value #RING_RANGE} blocs au plus (les voisins l'entendent, décision
 * du 27/11) ; l'appelé sans écran ouvert sur le téléphone appelé lit « Appel entrant » en barre d'action ; l'appelant
 * entend un bip, pour lui seul.
 *
 * <p>Batterie : pendant un appel (sonnerie comprise), le téléphone de chaque bout paie
 * {@link PhoneConfig#drainPerTick()} par tic, par blocs de vingt tics, comme l'écran ouvert ; un bout dont l'écran est
 * ouvert sur ce même téléphone ne paie que par l'écran (aucun double compte). À plat, la batterie passe à 0 et le
 * relevé finit l'appel ({@link End#PHONE_GONE}). Fil du serveur seulement ; ni numéro ni carte dans un journal.
 */
public final class CallUpdates {

    /** Tics entre deux sonneries (et deux bips de tonalité). */
    public static final int RING_PERIOD = 40;
    /** Distance (blocs) à laquelle la sonnerie s'entend autour de l'appelé. */
    public static final double RING_RANGE = 8.0;
    public static final Holder<SoundEvent> RING_SOUND = SoundEvents.NOTE_BLOCK_BELL;
    public static final Holder<SoundEvent> TONE_SOUND = SoundEvents.NOTE_BLOCK_BIT;

    /** Clé du message d'appel entrant (barre d'action, écran fermé). */
    public static final String INCOMING = CallService.MESSAGE + "incoming";
    /** Préfixe des messages de fin : {@code hung_up}, {@code declined}, {@code no_answer}, {@code missed}… */
    public static final String ENDED = CallService.MESSAGE + "ended.";

    /** L'instant du décroché de chaque appel en ligne ({@code EconomyClock.now()}). */
    private static final Map<Integer, Long> ANSWERED_AT = new HashMap<>();
    /** Quand chaque appel sonne : au premier tic vu, puis toutes les {@value #RING_PERIOD} tics. */
    private static final RingSchedule RINGS = new RingSchedule(RING_PERIOD);
    /** Les tics de batterie déjà payés par le téléphone de chaque bout en appel. */
    private static final Map<UUID, ItemBattery.Prepaid> DRAIN = new HashMap<>();

    private static final CallListener LISTENER = new CallListener() {
        @Override
        public void changed(Call call) {
            onChanged(call);
        }

        @Override
        public void ended(Call call, End reason) {
            onEnded(call, reason);
        }
    };

    private static boolean installed;

    private CallUpdates() {}

    /** Écoute les appels ({@link CallService#addListener}) ; une fois. Le tic s'inscrit à part ({@link #onServerTick}). */
    public static void install() {
        if (!installed) {
            installed = true;
            CallService.addListener(LISTENER);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Vues
    // ---------------------------------------------------------------------------------------------

    /** L'état d'appel de ce joueur, vu de son bout ; {@link Phase#NONE} hors appel. */
    static PhoneCallStatePacket view(ServerPlayer player, @Nullable Call call, @Nullable Component message,
            @Nullable Book book) {
        if (call == null || !call.involves(player.getUUID())) {
            // Hors appel, l'état répond à la session ouverte (ouverture, geste) : il la concerne.
            return new PhoneCallStatePacket(Phase.NONE, 0, "", "", -1L, false, false, true, message, book);
        }
        boolean caller = call.caller().player().equals(player.getUUID());
        Party me = caller ? call.caller() : call.callee();
        Party other = caller ? call.callee() : call.caller();
        Phase phase = call.state() == CallBook.State.ACTIVE ? Phase.ACTIVE : caller ? Phase.OUTGOING : Phase.INCOMING;
        long answered = call.state() == CallBook.State.ACTIVE
                ? ANSWERED_AT.getOrDefault(call.id(), EconomyClock.now()) : -1L;
        Optional<CallService.EmergencyCall> emergency = CallService.emergency(call.id());
        if (emergency.isPresent()) {
            // Une urgence : l'appelant voit le service ; le secours voit le service, l'appelant et sa position.
            CallService.EmergencyCall e = emergency.get();
            return new PhoneCallStatePacket(phase, call.id(), e.service().number(), caller ? "" : e.callerName(),
                    answered, caller ? call.callerMuted() : call.calleeMuted(),
                    caller ? call.callerSpeaker() : call.calleeSpeaker(), openOn(player, me.phoneId()), message, book,
                    caller ? "" : e.detail());
        }
        return new PhoneCallStatePacket(phase, call.id(), other.number(), nameOf(player.server, me, other), answered,
                caller ? call.callerMuted() : call.calleeMuted(), caller ? call.callerSpeaker() : call.calleeSpeaker(),
                openOn(player, me.phoneId()), message, book);
    }

    /** L'état d'appel actuel de ce joueur (son appel en cours, son urgence qui sonne, ou aucun). */
    static PhoneCallStatePacket current(ServerPlayer player, @Nullable Component message, @Nullable Book book) {
        Optional<Call> call = CallService.callOf(player);
        if (call.isEmpty()) {
            Optional<PhoneCallStatePacket> pending = EmergencyService.view(player);
            if (pending.isPresent()) {
                PhoneCallStatePacket p = pending.get();
                return new PhoneCallStatePacket(p.phase(), p.callId(), p.number(), p.name(), p.answeredMillis(),
                        p.muted(), p.speaker(), p.here(), message, book, p.detail());
            }
        }
        return view(player, call.orElse(null), message, book);
    }

    /** Le carnet de cette carte, tel que la page le montre. */
    static Book book(MinecraftServer server, UUID simId) {
        PhoneDirectory directory = PhoneDirectory.get(server);
        return new Book(directory.contacts(simId), directory.recents(simId));
    }

    private static String nameOf(MinecraftServer server, Party me, Party other) {
        return PhoneDirectory.get(server).nameOf(me.simId(), other.number()).orElse("");
    }

    static void send(ServerPlayer player, PhoneCallStatePacket packet) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Ce joueur a-t-il un écran de téléphone ouvert sur ce téléphone-là ? Un écran ouvert sur un autre téléphone ne
     * compte pas (revue de la tâche 4a, I3) : il ne montre pas l'appel, la barre d'action continue.
     */
    static boolean openOn(ServerPlayer player, UUID phoneId) {
        return PhoneService.session(player.getUUID()).filter(s -> s.phoneId().equals(phoneId)).isPresent();
    }

    /**
     * Une session de téléphone s'ouvre : elle reçoit toujours un état, même {@link Phase#NONE} (revue de la tâche 4a,
     * I3 : le client oublie ainsi le message, le numéro et le carnet d'un état précédent) ; un appel en cours lui est
     * montré tout de suite, marqué « ailleurs » s'il est porté par un autre téléphone.
     */
    public static void sessionOpened(ServerPlayer player) {
        send(player, current(player, null, null));
    }

    // ---------------------------------------------------------------------------------------------
    // Écoute des appels
    // ---------------------------------------------------------------------------------------------

    private static void onChanged(Call call) {
        if (call.state() == CallBook.State.ACTIVE) {
            ANSWERED_AT.putIfAbsent(call.id(), EconomyClock.now());
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (Party party : new Party[] {call.caller(), call.callee()}) {
            ServerPlayer player = server.getPlayerList().getPlayer(party.player());
            if (player != null) {
                send(player, view(player, call, null, null));
            }
        }
    }

    /**
     * Fin d'un appel : chaque bout reçoit {@link Phase#NONE} avec le numéro de l'appel fini et son message ; le carnet
     * (récents changés) seulement s'il a un écran déverrouillé sur ce téléphone ; écran fermé, le message passe en barre
     * d'action.
     */
    private static void onEnded(Call call, End reason) {
        Long answered = ANSWERED_AT.remove(call.id());
        RINGS.forget(call.id());
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        for (Party party : new Party[] {call.caller(), call.callee()}) {
            DRAIN.remove(party.player());
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(party.player());
            if (player == null) {
                continue;
            }
            boolean caller = party == call.caller();
            Party other = caller ? call.callee() : call.caller();
            Component message = Component.translatable(ENDED + endKey(reason, caller));
            Book book = PhoneService.unlockedOn(player, party.phoneId()) ? book(server, party.simId()) : null;
            Optional<CallService.EmergencyCall> emergency = CallService.emergency(call.id());
            String number = emergency.map(e -> e.service().number()).orElse(other.number());
            String name = emergency.isPresent() ? "" : nameOf(server, party, other);
            send(player, new PhoneCallStatePacket(Phase.NONE, call.id(), number, name,
                    answered == null ? -1L : answered, false, false, openOn(player, party.phoneId()), message, book));
            if (!openOn(player, party.phoneId())) {
                player.displayClientMessage(message, true);
            }
        }
    }

    /** Le suffixe du message de fin, vu de ce bout. */
    static String endKey(End reason, boolean caller) {
        return switch (reason) {
            case HUNG_UP -> "hung_up";
            case DECLINED -> "declined";
            case NO_ANSWER -> caller ? "no_answer" : "missed";
            case NO_SIGNAL, CHANGED_DIMENSION -> "no_signal";
            case PARTY_LEFT -> "party_left";
            case PHONE_GONE, DIED, SERVER_STOPPING -> "interrupted";
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Tic : sonnerie et batterie
    // ---------------------------------------------------------------------------------------------

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        for (Call call : CallService.calls()) {
            if (RINGS.due(call, tick)) {
                ring(server, call);
            }
            drain(server, call.caller());
            drain(server, call.callee());
        }
    }

    private static void ring(MinecraftServer server, Call call) {
        ServerPlayer callee = server.getPlayerList().getPlayer(call.callee().player());
        if (callee != null && callee.isAlive()) {
            ClientboundSoundPacket bell = new ClientboundSoundPacket(RING_SOUND, SoundSource.PLAYERS, callee.getX(),
                    callee.getY(), callee.getZ(), 1.0f, 1.0f, callee.getRandom().nextLong());
            for (ServerPlayer near : callee.serverLevel().players()) {
                if (near.connection != null && near.distanceToSqr(callee) <= RING_RANGE * RING_RANGE) {
                    near.connection.send(bell);
                }
            }
            if (!openOn(callee, call.callee().phoneId())) {
                callee.displayClientMessage(Component.translatable(INCOMING), true);
            }
        }
        ServerPlayer caller = server.getPlayerList().getPlayer(call.caller().player());
        if (caller != null && caller.connection != null) {
            caller.connection.send(new ClientboundSoundPacket(TONE_SOUND, SoundSource.PLAYERS, caller.getX(),
                    caller.getY(), caller.getZ(), 0.4f, 0.7f, caller.getRandom().nextLong()));
        }
    }

    /** Le téléphone de ce bout paie un tic d'appel, sauf si l'écran ouvert sur lui le paie déjà. */
    private static void drain(MinecraftServer server, Party party) {
        ServerPlayer player = server.getPlayerList().getPlayer(party.player());
        if (player == null) {
            return;
        }
        Optional<PhoneSession> session = PhoneService.session(player.getUUID());
        if (session.isPresent() && session.get().phoneId().equals(party.phoneId())) {
            return;
        }
        // Sans exiger de carte : une urgence se paie aussi (le relevé coupe un appel ordinaire sans carte active).
        Optional<ItemStack> phone = PhoneCarriers.holding(player, party.phoneId());
        if (phone.isEmpty() || PhoneItem.stored(phone.get()) <= 0) {
            return;
        }
        ItemBattery.Prepaid prepaid = DRAIN.computeIfAbsent(player.getUUID(), id -> new ItemBattery.Prepaid());
        if (!prepaid.pay(phone.get(), PhoneConfig.drainPerTick(), PhoneConfig.capacity())) {
            PhoneItem.store(phone.get(), 0);
        }
    }
}
