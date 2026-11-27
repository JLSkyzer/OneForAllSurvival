package dev.oneforallsurvival.compat;

import com.mojang.logging.LogUtils;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import org.slf4j.Logger;

/**
 * Charge processeur du panneau de Reggadium ({@code SystemPerformanceTracker}), mesurée hors du fil de rendu
 * ({@link CpuLoadSampler}) : le mixin de compatibilité remplace ses deux appels à
 * {@code OperatingSystemMXBean} par ces lectures. Le fil de mesure ne démarre qu'à la première, donc seulement si le
 * panneau s'affiche (Reggadium n'appelle sa mesure que là).
 */
public final class ReggadiumCpu {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Période de Reggadium 1.3-beta (lue au javap : une mesure au plus toutes les 500 ms). */
    static final long PERIOD_MS = 500L;
    /** Panneau fermé (ou quitté) depuis ce temps : le fil s'endort. */
    static final long IDLE_MS = 5_000L;

    private static volatile CpuLoadSampler sampler;

    private ReggadiumCpu() {}

    /** Dernière charge du processus mesurée (0 à 1), -1 si indisponible. */
    public static double processLoad() {
        return sampler().processLoad();
    }

    /** Dernière charge du système mesurée (0 à 1), -1 si indisponible. */
    public static double systemLoad() {
        return sampler().systemLoad();
    }

    /** L'échantillonneur, s'il a été créé (essais). */
    public static CpuLoadSampler current() {
        return sampler;
    }

    private static CpuLoadSampler sampler() {
        CpuLoadSampler s = sampler;
        if (s == null) {
            synchronized (ReggadiumCpu.class) {
                s = sampler;
                if (s == null) {
                    java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
                    OperatingSystemMXBean os = bean instanceof OperatingSystemMXBean sun ? sun : null;
                    s = new CpuLoadSampler(os == null ? () -> -1.0 : os::getProcessCpuLoad,
                            os == null ? () -> -1.0 : os::getCpuLoad, PERIOD_MS, IDLE_MS,
                            "OneForAllSurvival - charge processeur (Reggadium)");
                    sampler = s;
                    LOGGER.info("Compatibilité Reggadium : charge processeur du panneau mesurée hors du fil de rendu");
                }
            }
        }
        return s;
    }
}
