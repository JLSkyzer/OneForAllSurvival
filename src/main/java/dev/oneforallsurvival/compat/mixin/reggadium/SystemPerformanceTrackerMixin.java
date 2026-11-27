package dev.oneforallsurvival.compat.mixin.reggadium;

import com.sun.management.OperatingSystemMXBean;
import dev.oneforallsurvival.compat.ReggadiumCpu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Le panneau de Reggadium ({@code ReggadiumExtraHud.render}, fil de rendu) appelle
 * {@code SystemPerformanceTracker.update()}, qui toutes les 500 ms demande {@code getProcessCpuLoad()} (et
 * {@code getCpuLoad()} en repli) : sous Windows, environ 0,1 s par appel, une saccade deux fois par seconde. Bytecode lu
 * sur 1.3-beta. Les deux appels sont remplacés par la dernière valeur d'une mesure faite sur un fil de fond
 * ({@link ReggadiumCpu}) ; le reste de la méthode (cadence, arrondi, GPU) est celui de Reggadium.
 *
 * <p>Redirections non requises ({@code require = 0}) et classe {@code @Pseudo} : une autre version dont la méthode a
 * changé garde sa mesure d'origine, sans plantage.
 */
@Pseudo
@Mixin(targets = "com.reggarf.mods.reggadium.extra.SystemPerformanceTracker", remap = false)
public abstract class SystemPerformanceTrackerMixin {

    @Redirect(method = "update", require = 0, remap = false,
            at = @At(value = "INVOKE", target = "Lcom/sun/management/OperatingSystemMXBean;getProcessCpuLoad()D",
                    remap = false))
    private static double oneforallsurvival$processLoad(OperatingSystemMXBean bean) {
        return ReggadiumCpu.processLoad();
    }

    @Redirect(method = "update", require = 0, remap = false,
            at = @At(value = "INVOKE", target = "Lcom/sun/management/OperatingSystemMXBean;getCpuLoad()D",
                    remap = false))
    private static double oneforallsurvival$systemLoad(OperatingSystemMXBean bean) {
        return ReggadiumCpu.systemLoad();
    }
}
