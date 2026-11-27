package dev.oneforallsurvival.phone.voice;

/**
 * Un décodeur et un encodeur Opus pour un flux (le greffon les prend à Simple Voice Chat ; les essais, un codec factice).
 * Créé, utilisé et fermé sur le fil vocal, ou sous le verrou du {@link VoiceRouter}.
 */
public interface VoiceCodec {

    short[] decode(byte[] opus);

    byte[] encode(short[] pcm);

    /** Fin de parole : l'état des deux codecs repart à zéro. */
    void reset();

    void close();
}
