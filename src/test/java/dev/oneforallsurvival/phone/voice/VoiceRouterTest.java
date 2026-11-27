package dev.oneforallsurvival.phone.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.voice.VoiceRoutes.Bystander;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.CallSides;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Flow;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.FlowKey;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Le routeur : le fil vocal lit l'instantané publié et parle au puits ; flux finis, codecs fermés. */
class VoiceRouterTest {

    private static final String MONDE = "minecraft:overworld";
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    /** Puits d'essai : enregistre tout. */
    static final class Recorder implements VoiceSink {
        record Sent(FlowKey key, byte[] data) {}

        final List<Sent> sent = new ArrayList<>();
        final List<FlowKey> flushed = new ArrayList<>();
        final List<FlowKey> closed = new ArrayList<>();
        final List<FakeCodec> codecs = new ArrayList<>();
        boolean explode;

        @Override
        public void send(Flow flow, byte[] opus) {
            if (explode) {
                throw new IllegalStateException("puits cassé");
            }
            sent.add(new Sent(flow.key(), opus));
        }

        @Override
        public void flush(Flow flow) {
            flushed.add(flow.key());
        }

        @Override
        public void close(FlowKey key) {
            closed.add(key);
        }

        @Override
        public VoiceCodec codec() {
            FakeCodec c = new FakeCodec();
            codecs.add(c);
            return c;
        }
    }

    private static VoiceRoutes appel(int barsA, boolean speakerA) {
        Side a = new Side(A, new Spot(MONDE, 0, 64, 0), barsA, false, speakerA);
        Side b = new Side(B, new Spot(MONDE, 500, 64, 0), 4, false, false);
        return VoiceRoutes.compute(List.of(new CallSides(3, a, b)),
                List.of(new Bystander(C, new Spot(MONDE, 2, 64, 0), false)), 8, 16);
    }

    private static byte[] voix() {
        return FakeCodec.bytes(new short[] {100, 200, 300, 400});
    }

    @Test
    void unPaquetDuMicroNArriveQuAuBonAuditeur() {
        Recorder sink = new Recorder();
        VoiceRouter router = new VoiceRouter(sink, 1L);
        router.publish(appel(4, false));
        router.microphone(A, voix());
        assertEquals(1, sink.sent.size());
        assertEquals(B, sink.sent.get(0).key().listener());
        assertArrayEquals(voix(), sink.sent.get(0).data(), "quatre barres : la voix passe telle quelle");
        router.microphone(C, voix());
        assertEquals(1, sink.sent.size(), "C n'est pas en appel ni capté : rien");
        assertTrue(sink.codecs.isEmpty(), "aucun codec pour un flux net");
    }

    @Test
    void sansInstantanePublieRienNePart() {
        Recorder sink = new Recorder();
        VoiceRouter router = new VoiceRouter(sink, 1L);
        router.microphone(A, voix());
        assertTrue(sink.sent.isEmpty());
    }

    @Test
    void uneBarreGresilleParLeCodecDuFlux() {
        Recorder sink = new Recorder();
        VoiceRouter router = new VoiceRouter(sink, 1L);
        router.publish(appel(1, false));
        for (int i = 0; i < 30; i++) {
            router.microphone(A, voix());
        }
        assertEquals(30, sink.sent.size());
        assertEquals(1, sink.codecs.size(), "un codec par flux qui grésille, créé à sa première trame : seul A→B a"
                + " parlé");
        assertEquals(30, sink.codecs.get(0).decoded, "chaque trame passe par le décodage");
        router.microphone(B, voix());
        assertEquals(2, sink.codecs.size(), "B→A a son propre codec");
    }

    @Test
    void finDeParoleVideLesFluxEtRemetLesCodecsAZero() {
        Recorder sink = new Recorder();
        VoiceRouter router = new VoiceRouter(sink, 1L);
        router.publish(appel(1, false));
        router.microphone(A, voix());
        router.microphone(A, new byte[0]);
        assertEquals(1, sink.sent.size(), "les données vides ne sont pas envoyées comme une trame");
        assertEquals(List.of(sink.sent.get(0).key()), sink.flushed);
        assertEquals(1, sink.codecs.get(0).resets, "le codec du flux repart à zéro");
    }

    @Test
    void unFluxQuiDisparaitEstFermeAvecSonCodec() {
        Recorder sink = new Recorder();
        VoiceRouter router = new VoiceRouter(sink, 1L);
        router.publish(appel(1, false));
        router.microphone(A, voix());
        FlowKey cle = sink.sent.get(0).key();
        router.publish(appel(1, false));
        assertTrue(sink.closed.isEmpty(), "le même flux republié reste ouvert");
        router.publish(VoiceRoutes.EMPTY);
        assertEquals(List.of(cle), sink.closed);
        assertTrue(sink.codecs.get(0).closed);
        router.microphone(A, voix());
        assertEquals(1, sink.sent.size(), "appel fini : plus rien ne part");
    }

    @Test
    void lArretFermeTout() {
        Recorder sink = new Recorder();
        VoiceRouter router = new VoiceRouter(sink, 1L);
        router.publish(appel(1, true));
        router.microphone(A, voix());
        router.microphone(B, voix());
        router.microphone(C, voix());
        assertEquals(3, sink.sent.size(), "A→B, B par le haut-parleur de A, C capté vers B");
        router.stop();
        assertEquals(3, sink.closed.size());
        assertTrue(sink.codecs.stream().allMatch(c -> c.closed));
        router.microphone(A, voix());
        assertEquals(3, sink.sent.size(), "routeur arrêté : rien");
        router.publish(appel(1, true));
        router.microphone(A, voix());
        assertEquals(3, sink.sent.size(), "un routeur arrêté ne repart pas");
    }

    @Test
    void unPuitsQuiCasseNeRemontePasAuFilVocal() {
        Recorder sink = new Recorder();
        sink.explode = true;
        VoiceRouter router = new VoiceRouter(sink, 1L);
        router.publish(appel(4, false));
        assertDoesNotThrow(() -> router.microphone(A, voix()));
        assertFalse(router.current().keys().isEmpty());
    }
}
