package dev.oneforallsurvival.phone.voice;

import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.call.CallBook;
import dev.oneforallsurvival.phone.call.CallListener;
import dev.oneforallsurvival.phone.call.CallService;
import dev.oneforallsurvival.phone.call.PhoneCarriers;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Bystander;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.CallSides;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Presence;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * La voix des appels côté serveur, sans Simple Voice Chat : le greffon ({@code phone/voice/svc/}) branche son puits
 * ({@link #attach}) quand le serveur vocal démarre et le retire ({@link #detach}) à son arrêt. Le fil du serveur calcule
 * les routes ({@link #compute}) toutes les {@value #PUBLISH_PERIOD} tics, et dès le tic suivant un changement d'appel
 * (décroché, silence, haut-parleur, fin), puis les publie au routeur ; le fil vocal n'appelle que {@link #microphone}.
 */
public final class VoiceService {

    public static final int PUBLISH_PERIOD = 5;

    private static volatile @Nullable VoiceRouter router;
    /** Un appel a changé depuis la dernière publication ; fil du serveur. */
    private static boolean dirty;
    private static boolean installed;

    private static final CallListener LISTENER = new CallListener() {
        @Override
        public void changed(CallBook.Call call) {
            dirty = true;
        }

        @Override
        public void ended(CallBook.Call call, CallBook.End reason) {
            dirty = true;
        }
    };

    private VoiceService() {}

    /** Inscrit le tic sur {@code bus} et l'écoute des appels (une fois). */
    public static void register(IEventBus bus) {
        bus.addListener(VoiceService::onServerTick);
        if (!installed) {
            installed = true;
            CallService.addListener(LISTENER);
        }
    }

    /** Branche un puits : un routeur neuf remplace l'ancien, qui est arrêté (ses canaux fermés). */
    public static synchronized VoiceRouter attach(VoiceSink sink) {
        VoiceRouter next = new VoiceRouter(sink, new SecureRandom().nextLong());
        VoiceRouter old = router;
        router = next;
        if (old != null) {
            old.stop();
        }
        dirty = true;
        return next;
    }

    /** Retire le puits : tout est fermé et oublié. */
    public static synchronized void detach() {
        VoiceRouter old = router;
        router = null;
        if (old != null) {
            old.stop();
        }
    }

    public static Optional<VoiceRouter> router() {
        return Optional.ofNullable(router);
    }

    /** Fil vocal : une trame du micro de {@code speaker} (vide = fin de parole). Ne lit que l'instantané publié. */
    public static void microphone(UUID speaker, byte[] opus) {
        VoiceRouter r = router;
        if (r != null) {
            r.microphone(speaker, opus);
        }
    }

    /** Fil vocal : ce joueur entend-il ce flux, d'après l'instantané publié ? (filtre du haut-parleur) */
    public static boolean hears(VoiceRoutes.FlowKey key, UUID listener) {
        VoiceRouter r = router;
        return r != null && r.current().hears(key, listener);
    }

    /** Fil du serveur : calcule et publie les routes, s'il y a un puits. */
    public static void publish(MinecraftServer server) {
        VoiceRouter r = router;
        dirty = false;
        if (r != null) {
            r.publish(compute(server, r.sink()::inClosedGroup));
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && (dirty || event.getServer().getTickCount() % PUBLISH_PERIOD == 0)) {
            publish(event.getServer());
        }
    }

    /**
     * Les routes du moment, fil du serveur : appels décrochés dont les deux bouts portent encore leur téléphone, joueurs
     * connectés autour, {@code closedGroup} pour ceux qu'un groupe vocal fermé retire de la proximité.
     */
    public static VoiceRoutes compute(MinecraftServer server, Predicate<UUID> closedGroup) {
        List<CallSides> calls = new ArrayList<>();
        for (CallBook.Call call : CallService.calls()) {
            boolean emergency = CallService.emergency(call.id()).isPresent();
            CallSides.of(call, party -> presence(server, party, emergency)).ifPresent(calls::add);
        }
        if (calls.isEmpty()) {
            return VoiceRoutes.EMPTY;
        }
        List<Bystander> around = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) {
                // Un spectateur n'est pas dans le monde : le haut-parleur ne le capte pas (relecture de la tâche 5).
                continue;
            }
            around.add(new Bystander(p.getUUID(), spot(p), closedGroup.test(p.getUUID())));
        }
        return VoiceRoutes.compute(calls, around, PhoneConfig.speakerHearBlocks(), PhoneConfig.speakerCaptureBlocks());
    }

    /**
     * Un bout présent : il porte son téléphone (une urgence n'exige aucune carte, feature 95). Les barres sont celles de
     * sa ligne, ou pour une urgence celles de l'antenne d'urgence la plus proche.
     */
    private static Optional<Presence> presence(MinecraftServer server, CallBook.Party party, boolean emergency) {
        ServerPlayer player = server.getPlayerList().getPlayer(party.player());
        if (player == null) {
            return Optional.empty();
        }
        Optional<ItemStack> phone = emergency ? PhoneCarriers.holding(player, party.phoneId())
                : PhoneCarriers.heldBy(player, party.phoneId());
        if (phone.isEmpty()) {
            return Optional.empty();
        }
        boolean airplane = PhoneData.get(server).record(party.phoneId()).airplane();
        int bars = emergency ? PhoneNetwork.emergencyBars(server, GlobalPos.of(player.level().dimension(),
                player.blockPosition()), airplane) : PhoneNetwork.status(player, phone.get(), airplane).bars();
        return Optional.of(new Presence(spot(player), bars));
    }

    static Spot spot(ServerPlayer player) {
        return new Spot(player.level().dimension().location().toString(), player.getX(), player.getEyeY(),
                player.getZ());
    }
}
