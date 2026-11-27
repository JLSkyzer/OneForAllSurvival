package dev.oneforallsurvival.phone.voice;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Flow;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.FlowKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * Entre le fil du serveur et le fil vocal. Le serveur publie un instantané immuable ({@link #publish}, champ
 * {@code volatile}) ; le fil vocal ({@link #microphone}) n'y lit que les flux du parleur et parle au {@link VoiceSink},
 * sans toucher au monde. Tout ce qui ouvre, envoie ou ferme un canal ou un codec passe sous un même verrou, tenu le
 * temps d'un envoi : un flux disparu de l'instantané est fermé (avec son codec) à la publication suivante.
 */
public final class VoiceRouter {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final VoiceSink sink;
    private final long seed;
    private final Object lock = new Object();
    private volatile VoiceRoutes current = VoiceRoutes.EMPTY;

    /** Flux qui ont reçu de la voix, et leur grésillement s'ils en ont un ; sous {@link #lock}. */
    private final Set<FlowKey> open = new LinkedHashSet<>();
    private final Map<FlowKey, Crackle> crackles = new HashMap<>();
    private boolean stopped;
    /** Flux dont un échec d'envoi est déjà journalisé ; oubliés à leur fermeture. Sous {@link #lock}. */
    private final Set<FlowKey> warned = new LinkedHashSet<>();
    private int warnings;

    public VoiceRouter(VoiceSink sink, long seed) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.seed = seed;
    }

    public VoiceSink sink() {
        return sink;
    }

    /** L'instantané en cours (celui que le fil vocal lit). */
    public VoiceRoutes current() {
        return current;
    }

    /** Fil du serveur : remplace l'instantané et ferme les flux qui n'y sont plus. */
    public void publish(VoiceRoutes routes) {
        Objects.requireNonNull(routes, "routes");
        synchronized (lock) {
            if (stopped) {
                return;
            }
            current = routes;
            Set<FlowKey> known = new LinkedHashSet<>(open);
            known.addAll(crackles.keySet());
            warned.removeIf(key -> !routes.keys().contains(key));
            List<FlowKey> gone = new ArrayList<>();
            for (FlowKey key : known) {
                if (!routes.keys().contains(key)) {
                    gone.add(key);
                }
            }
            for (FlowKey key : gone) {
                closeFlow(key);
            }
        }
    }

    /**
     * Fil vocal : une trame du micro de {@code speaker}. Des données vides marquent la fin de sa parole : chaque flux
     * ouvert est vidé et ses codecs repartent à zéro. Ne lève jamais : une erreur du puits est journalisée.
     */
    public void microphone(UUID speaker, byte[] opus) {
        if (current.from(speaker).isEmpty()) {
            return;
        }
        synchronized (lock) {
            if (stopped) {
                return;
            }
            for (Flow flow : current.from(speaker)) {
                try {
                    if (opus.length == 0) {
                        endOfSpeech(flow);
                    } else {
                        sink.send(flow, flow.crackle() ? crackle(flow.key()).apply(opus) : opus);
                        open.add(flow.key());
                    }
                } catch (RuntimeException e) {
                    // Une fois par flux : un puits cassé ne remplit pas le journal à 50 trames par seconde.
                    if (warned.add(flow.key())) {
                        warnings++;
                        LOGGER.warn("Voix d'un appel non relayée (avertissement unique pour ce flux)", e);
                    }
                }
            }
        }
    }

    /** Combien d'avertissements d'envoi ont été journalisés (essais). */
    int warnings() {
        synchronized (lock) {
            return warnings;
        }
    }

    /** Arrêt du serveur vocal : tout est fermé et oublié ; ce routeur ne sert plus. */
    public void stop() {
        synchronized (lock) {
            stopped = true;
            current = VoiceRoutes.EMPTY;
            for (FlowKey key : new ArrayList<>(open)) {
                closeFlow(key);
            }
            for (Crackle c : crackles.values()) {
                c.close();
            }
            crackles.clear();
        }
    }

    private void endOfSpeech(Flow flow) {
        if (open.contains(flow.key())) {
            sink.flush(flow);
        }
        Crackle c = crackles.get(flow.key());
        if (c != null) {
            c.reset();
        }
    }

    private Crackle crackle(FlowKey key) {
        return crackles.computeIfAbsent(key, k -> new Crackle(sink.codec(), seed ^ k.hashCode()));
    }

    /** Sous le verrou : ferme le canal et le codec d'un flux, même si l'un des deux échoue. */
    private void closeFlow(FlowKey key) {
        open.remove(key);
        warned.remove(key);
        Crackle c = crackles.remove(key);
        try {
            sink.close(key);
        } catch (RuntimeException e) {
            LOGGER.warn("Canal d'un appel mal fermé", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }
}
