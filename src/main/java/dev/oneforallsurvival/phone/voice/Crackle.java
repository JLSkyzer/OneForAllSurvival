package dev.oneforallsurvival.phone.voice;

import java.util.Objects;
import java.util.Random;

/**
 * Le grésillement d'un appel à une barre : chaque trame est décodée, reçoit un bruit léger, ou devient muette une fois
 * sur dix environ, puis est réencodée. Sauter une trame sans la remplacer ne s'entendrait pas comme un trou (Opus la
 * masque). Un par flux ; fil vocal ou verrou du routeur.
 */
public final class Crackle {

    /** Écart maximal du bruit ajouté à chaque échantillon (sur 32 767). */
    public static final int NOISE = 600;
    /** Part des trames remplacées par du silence. */
    public static final double DROP = 0.10;

    private final VoiceCodec codec;
    private final Random random;

    public Crackle(VoiceCodec codec, long seed) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.random = new Random(seed);
    }

    public byte[] apply(byte[] opus) {
        return codec.encode(degrade(codec.decode(opus), random));
    }

    public void reset() {
        codec.reset();
    }

    public void close() {
        codec.close();
    }

    /** La trame dégradée (copie) : muette avec la probabilité {@link #DROP}, sinon bruitée d'au plus {@link #NOISE}. */
    static short[] degrade(short[] pcm, Random random) {
        short[] out = new short[pcm.length];
        if (random.nextDouble() < DROP) {
            return out;
        }
        for (int i = 0; i < pcm.length; i++) {
            int v = pcm[i] + random.nextInt(2 * NOISE + 1) - NOISE;
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
        return out;
    }
}
