package dev.oneforallsurvival.phone.voice;

/**
 * Codec d'essai : une trame « Opus » est la suite des échantillons en octets (poids fort d'abord). Compte ses appels.
 */
final class FakeCodec implements VoiceCodec {

    int decoded;
    int encoded;
    int resets;
    boolean closed;

    static byte[] bytes(short[] pcm) {
        byte[] out = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            out[2 * i] = (byte) (pcm[i] >> 8);
            out[2 * i + 1] = (byte) pcm[i];
        }
        return out;
    }

    static short[] shorts(byte[] data) {
        short[] pcm = new short[data.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) ((data[2 * i] << 8) | (data[2 * i + 1] & 0xFF));
        }
        return pcm;
    }

    @Override
    public short[] decode(byte[] opus) {
        decoded++;
        return shorts(opus);
    }

    @Override
    public byte[] encode(short[] pcm) {
        encoded++;
        return bytes(pcm);
    }

    @Override
    public void reset() {
        resets++;
    }

    @Override
    public void close() {
        closed = true;
    }
}
