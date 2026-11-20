package dev.oneforallsurvival.phone.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.client.web.WebAvailability;
import dev.oneforallsurvival.client.web.WebFallbackNotice;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le téléphone côté client : reçoit les états du serveur (sur le fil du client, par {@code DistExecutor} depuis
 * {@link PhoneStatePacket#handle}) et ouvre, met à jour ou ferme l'écran de la session. Le client ne décide rien : il
 * montre ce que le serveur envoie et lui propose des gestes. Aucun code saisi n'est écrit au journal.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PhoneClient {

    /** Un écran du téléphone (web ou repli). */
    interface Shown {
        /** La session montrée. */
        int session();

        /** Un nouvel état de la même session. */
        void show(PhoneStatePacket state);

        /** Le serveur a fermé la session (ou une autre l'a remplacée) : l'écran ne lui renverra pas {@code CLOSE}. */
        void closedByServer();
    }

    @Nullable private static PhoneStatePacket last;

    private PhoneClient() {}

    /** Le dernier état reçu du serveur, quelle que soit sa sorte. */
    public static Optional<PhoneStatePacket> last() {
        return Optional.ofNullable(last);
    }

    /** La sortie du monde oublie le dernier état (il peut porter le nom du propriétaire). */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        last = null;
    }

    static void remember(PhoneStatePacket state) {
        last = state;
    }

    public static void accept(PhoneStatePacket state) {
        Minecraft mc = Minecraft.getInstance();
        remember(state);
        Shown shown = mc.screen instanceof Shown s ? s : null;
        switch (state.kind()) {
            case OPEN -> {
                if (shown != null) {
                    // Une nouvelle session remplace l'ancienne : le serveur l'a déjà oubliée.
                    shown.closedByServer();
                }
                if (WebAvailability.ready()) {
                    mc.setScreen(new PhoneWebScreen(state));
                } else {
                    WebFallbackNotice.warnOnce(WebAvailability.failure());
                    mc.setScreen(new PhoneFallbackScreen(state));
                }
            }
            case UPDATE -> {
                if (shown != null && shown.session() == state.session()) {
                    shown.show(state);
                }
            }
            case CLOSE -> {
                if (shown != null && shown.session() == state.session()) {
                    shown.closedByServer();
                    mc.setScreen(null);
                    Component why = closeMessage(state.reason());
                    if (why != null) {
                        mc.gui.setOverlayMessage(why, false);
                    }
                }
            }
        }
    }

    /** Le message en barre d'action d'une fermeture par le serveur ; rien pour une fermeture ordinaire. */
    @Nullable
    static Component closeMessage(CloseReason reason) {
        return switch (reason) {
            case BATTERY_EMPTY, PHONE_GONE, ERASED -> Component.translatable(
                    "message.oneforallsurvival.phone.closed." + reason.name().toLowerCase(Locale.ROOT));
            case NONE, CLOSED -> null;
        };
    }

    /** Envoie un geste au serveur (sans effet hors connexion). */
    static void send(PhoneActionPacket packet) {
        if (Minecraft.getInstance().getConnection() != null) {
            BenchNetwork.CHANNEL.sendToServer(packet);
        }
    }

    /** Le joueur a fermé l'écran : le serveur arrête la session (et la dépense de batterie). */
    static void sendClose(int session) {
        send(new PhoneActionPacket(session, Action.CLOSE, "", "", false));
    }
}
