package dev.oneforallsurvival.phone.net;

import java.util.List;
import java.util.UUID;

/**
 * Un appel ou une visio ouvert sur les antennes d'un trajet ({@link PhoneNetwork#openTraffic}) : tant que la poignée est
 * ouverte, chaque antenne du trajet compte ce trafic dans sa consommation. {@link #close()} le rend, une seule fois
 * (idempotent).
 *
 * <p>La poignée tient le registre où elle a été ouverte : fermée après un changement de monde, elle ne touche pas au
 * registre du monde suivant. Rien de tout cela n'est sauvegardé. Fil du serveur seulement.
 *
 * <p>Qui ferme, et quand : la session d'appel du lot T3, en un seul point (fin d'appel), sur raccrocher, refus, zéro
 * barre, déconnexion, mort, changement de dimension, téléphone ou carte retiré, arrêt du serveur. Une poignée oubliée
 * coûte 9 FE par tic (24 pour une visio) à chaque antenne du trajet jusqu'au rechargement du monde (calcul, non mesuré) :
 * voir {@link PhoneNetwork#openTraffic}.
 */
public final class TrafficHandle implements AutoCloseable {

    private final PhoneOperators registry;
    private final List<UUID> antennas;
    private final TrafficKind kind;
    private boolean closed;

    TrafficHandle(PhoneOperators registry, List<UUID> antennas, TrafficKind kind) {
        this.registry = registry;
        this.antennas = List.copyOf(antennas);
        this.kind = kind;
        for (UUID antenna : this.antennas) {
            registry.addTraffic(antenna, kind, 1);
        }
    }

    public TrafficKind kind() {
        return kind;
    }

    public boolean isOpen() {
        return !closed;
    }

    /** Rend le trafic aux antennes du trajet ; les appels suivants ne font rien. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (UUID antenna : antennas) {
            registry.addTraffic(antenna, kind, -1);
        }
    }
}
