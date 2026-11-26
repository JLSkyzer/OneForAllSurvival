package dev.oneforallsurvival.dev.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.oneforallsurvival.dev.SourisLibre;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dans un client qui se pilote seul, le jeu ne touche jamais au curseur. {@link InputConstants#grabOrReleaseMouse} est
 * le seul chemin par lequel Minecraft le replace au centre de sa fenêtre puis le capture (ou le rend, avec le même
 * déplacement) : sous Windows, une fenêtre d'essai active tirait ainsi la souris pendant que Killian jouait
 * (2026-11-27). Le reste du jeu suit son cours ; {@link SourisLibre} baisse ensuite son drapeau de capture.
 *
 * <p>Développement seulement ({@code oneforallsurvival.devcontent.mixins.json}, noms officiels, sans refmap).
 */
@Mixin(value = InputConstants.class, remap = false)
public abstract class CurseurIntactMixin {

    @Inject(method = "grabOrReleaseMouse", at = @At("HEAD"), cancellable = true, remap = false)
    private static void oneforallsurvival$laisserLeCurseur(long window, int cursorValue, double x, double y,
            CallbackInfo ci) {
        if (SourisLibre.actif()) {
            SourisLibre.curseurLaisse();
            ci.cancel();
        }
    }
}
