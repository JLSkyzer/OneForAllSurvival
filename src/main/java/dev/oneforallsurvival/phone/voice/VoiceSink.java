package dev.oneforallsurvival.phone.voice;

import dev.oneforallsurvival.phone.voice.VoiceRoutes.Flow;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.FlowKey;
import java.util.UUID;

/**
 * Là où le {@link VoiceRouter} envoie la voix : le greffon de Simple Voice Chat (un canal par clé de flux) ou un puits
 * d'essai qui enregistre. Toutes les méthodes d'envoi sont appelées sous le verrou du routeur, depuis le fil vocal (ou
 * le fil du serveur pour {@link #close}) : jamais deux à la fois.
 */
public interface VoiceSink {

    /** Une trame Opus pour ce flux (créer son canal à la première). */
    void send(Flow flow, byte[] opus);

    /** Fin de parole sur ce flux. */
    void flush(Flow flow);

    /** Ce flux n'existe plus : fermer son canal. */
    void close(FlowKey key);

    /** Un codec neuf pour un flux qui grésille. */
    VoiceCodec codec();

    /**
     * Ce joueur est-il dans un groupe vocal fermé (sa voix n'atteint plus la proximité) ? Lu sur le fil du serveur, à
     * la publication.
     */
    default boolean inClosedGroup(UUID player) {
        return false;
    }
}
