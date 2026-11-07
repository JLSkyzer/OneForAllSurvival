package dev.oneforallsurvival.client;

import io.netty.channel.Channel;

/**
 * Relance la lecture d'une connexion qui attend le serveur. Une course entre le fil de connexion et
 * {@code channelActive} peut laisser le canal sans lecture, avec {@code autoRead} vrai : plus rien ne la
 * remet, et le client tombe en « Timed out » après 30 s. Voir {@code docs/verifications/lot-2.md}.
 */
final class ReadRescue {

    /** Une connexion saine reçoit son premier message en moins de 6 s : à 3 s, une relance reste sans effet. */
    static final int FIRST_TICKS = 60;
    static final int EVERY_TICKS = 100;

    private Object watched;
    private int ticks;

    /** À appeler une fois par tick tant que la connexion attend. Une autre connexion repart de zéro. */
    boolean isDue(Object connection) {
        if (connection != watched) {
            watched = connection;
            ticks = 0;
        }
        ticks++;
        return ticks >= FIRST_TICKS && (ticks - FIRST_TICKS) % EVERY_TICKS == 0;
    }

    /**
     * Demande une lecture, sur la boucle du canal. Jamais avec {@code autoRead} faux : pendant un changement
     * de protocole, un paquet passerait par l'ancien décodeur.
     */
    static void rescue(Channel channel) {
        channel.eventLoop().execute(() -> {
            if (channel.isActive() && channel.config().isAutoRead()) {
                channel.read();
            }
        });
    }
}
