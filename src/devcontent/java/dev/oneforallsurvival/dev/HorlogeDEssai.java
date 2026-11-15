package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.EconomyClock;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * L'horloge décalée de l'essai de bout en bout du trésor et des salaires (développement seulement, jamais empaquetée) :
 * l'essai n'attend jamais un vrai minuit. Au démarrage du serveur ({@link ServerAboutToStartEvent}, avant le rattrapage
 * des jours manqués de {@code PayrollService.onServerStarted}), la propriété {@code oneforallsurvival.e2e.horloge} (en
 * minutes, passée par {@code runServer -Pe2eHorloge=<minutes>}) avance {@link EconomyClock} d'autant ; sans propriété,
 * rien n'est touché. En cours de partie, {@code e2etresor horloge} ({@link E2eTresorServeur}) règle le décalage.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HorlogeDEssai {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String PROPRIETE = "oneforallsurvival.e2e.horloge";

    /** Le décalage en place, en millisecondes (0 : l'horloge du système). */
    private static volatile long decalageMs;

    private HorlogeDEssai() {}

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        String valeur = System.getProperty(PROPRIETE);
        if (valeur == null || valeur.isBlank()) {
            return;
        }
        long minutes;
        try {
            minutes = Long.parseLong(valeur.trim());
        } catch (NumberFormatException e) {
            LOG.error("[horloge] {}={} illisible : horloge du systeme gardee", PROPRIETE, valeur);
            return;
        }
        regler(minutes * 60_000L);
        LOG.info("[horloge] decalage de {} minutes ({} ms) au demarrage : l'economie lit {}", minutes, decalageMs,
                java.time.Instant.ofEpochMilli(EconomyClock.now()));
    }

    /** Avance (ou recule) l'horloge de l'économie de {@code ms} par rapport à l'heure du système. */
    static void regler(long ms) {
        decalageMs = ms;
        if (ms == 0L) {
            EconomyClock.reset();
        } else {
            EconomyClock.setClock(() -> System.currentTimeMillis() + decalageMs);
        }
    }

    /** Le décalage en place, en millisecondes. */
    static long decalage() {
        return decalageMs;
    }
}
