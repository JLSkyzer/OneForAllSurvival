package dev.oneforallsurvival.phone.voice.svc;

import dev.oneforallsurvival.phone.voice.Spot;
import dev.oneforallsurvival.phone.voice.VoiceCodec;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Flow;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.FlowKey;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Kind;
import dev.oneforallsurvival.phone.voice.VoiceService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;

/**
 * Le puits réel : un canal de Simple Voice Chat par clé de flux. Privé : un {@link StaticAudioChannel} vers l'auditeur
 * seul (qui passe l'isolement d'un groupe : un appel n'est pas de la proximité). Haut-parleur : un
 * {@link LocationalAudioChannel} posé sur le téléphone, de portée {@code hearBlocks}, filtré par l'instantané (exclus,
 * distance). Les canaux vivent sous le verrou du routeur ; les mondes sont lus une fois, au démarrage, sur le fil du
 * serveur.
 */
final class SvcSink implements dev.oneforallsurvival.phone.voice.VoiceSink {

    private final VoicechatServerApi api;
    /** Mondes de Simple Voice Chat par nom de dimension, lus au démarrage du serveur vocal. */
    private final Map<String, ServerLevel> levels = new ConcurrentHashMap<>();
    /** Sous le verrou du routeur. */
    private final Map<FlowKey, AudioChannel> channels = new HashMap<>();

    SvcSink(VoicechatServerApi api, @Nullable MinecraftServer server) {
        this.api = api;
        if (server != null) {
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                levels.put(level.dimension().location().toString(), api.fromServerLevel(level));
            }
        }
    }

    @Override
    public void send(Flow flow, byte[] opus) {
        AudioChannel channel = channels.get(flow.key());
        if (channel == null) {
            channel = open(flow);
            if (channel == null) {
                return;
            }
            channels.put(flow.key(), channel);
        }
        if (channel instanceof LocationalAudioChannel located && flow.at() != null) {
            Spot at = flow.at();
            located.updateLocation(api.createPosition(at.x(), at.y(), at.z()));
        }
        channel.send(opus);
    }

    /** Le canal du flux, ou rien si l'auditeur n'a pas Simple Voice Chat ou si le monde est inconnu. */
    private @Nullable AudioChannel open(Flow flow) {
        FlowKey key = flow.key();
        if (key.kind() == Kind.PRIVATE) {
            VoicechatConnection to = api.getConnectionOf(key.listener());
            if (to == null) {
                return null;
            }
            StaticAudioChannel channel = api.createStaticAudioChannel(UUID.randomUUID());
            if (channel == null) {
                return null;
            }
            channel.setBypassGroupIsolation(true);
            channel.addTarget(to);
            return channel;
        }
        Spot at = flow.at();
        ServerLevel level = at == null ? null : levels.get(at.dimension());
        if (level == null) {
            return null;
        }
        LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), level,
                api.createPosition(at.x(), at.y(), at.z()));
        if (channel == null) {
            return null;
        }
        channel.setDistance(flow.hearBlocks());
        channel.setFilter(player -> VoiceService.hears(key, player.getUuid()));
        return channel;
    }

    @Override
    public void flush(Flow flow) {
        AudioChannel channel = channels.get(flow.key());
        if (channel != null) {
            channel.flush();
        }
    }

    @Override
    public void close(FlowKey key) {
        AudioChannel channel = channels.remove(key);
        if (channel == null) {
            return;
        }
        channel.flush();
        if (channel instanceof StaticAudioChannel s) {
            s.clearTargets();
        }
    }

    @Override
    public VoiceCodec codec() {
        OpusDecoder decoder = api.createDecoder();
        OpusEncoder encoder = api.createEncoder();
        return new VoiceCodec() {
            @Override
            public short[] decode(byte[] opus) {
                return decoder.decode(opus);
            }

            @Override
            public byte[] encode(short[] pcm) {
                return encoder.encode(pcm);
            }

            @Override
            public void reset() {
                decoder.resetState();
                encoder.resetState();
            }

            @Override
            public void close() {
                try {
                    decoder.close();
                } finally {
                    encoder.close();
                }
            }
        };
    }

    /** Fil du serveur : un groupe qui retire de la proximité (tout sauf ouvert). */
    @Override
    public boolean inClosedGroup(UUID player) {
        VoicechatConnection connection = api.getConnectionOf(player);
        if (connection == null || !connection.isInGroup()) {
            return false;
        }
        Group group = connection.getGroup();
        return group != null && group.getType() != Group.Type.OPEN;
    }
}
