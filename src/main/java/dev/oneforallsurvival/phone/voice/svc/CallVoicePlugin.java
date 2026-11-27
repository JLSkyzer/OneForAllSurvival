package dev.oneforallsurvival.phone.voice.svc;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.phone.voice.VoiceService;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Le greffon de Simple Voice Chat (trouvé par son annotation ; aucune classe du mod ne le nomme). Il branche le puits
 * réel quand le serveur vocal démarre, le retire à son arrêt, et passe chaque trame du micro au routeur des appels.
 * Le paquet du micro n'est jamais annulé : la proximité normale reste celle de Simple Voice Chat.
 */
@ForgeVoicechatPlugin
public final class CallVoicePlugin implements VoicechatPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getPluginId() {
        return "oneforallsurvival_telephone";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::started);
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> VoiceService.detach());
        registration.registerEvent(MicrophonePacketEvent.class, this::microphone);
    }

    private void started(VoicechatServerStartedEvent event) {
        VoiceService.attach(new SvcSink(event.getVoicechat(), ServerLifecycleHooks.getCurrentServer()));
        LOGGER.info("Voix des appels du téléphone branchée sur Simple Voice Chat");
    }

    /** Fil vocal : ne bloque pas, ne lit pas le monde, n'annule rien. */
    private void microphone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) {
            return;
        }
        VoiceService.microphone(sender.getPlayer().getUuid(), event.getPacket().getOpusEncodedData());
    }
}
