package dev.oneforallsurvival.dev.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.oneforallsurvival.dev.FenetreSecondEcran;
import dev.oneforallsurvival.dev.SourisLibre;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La fenêtre d'un client qui se pilote seul ne prend jamais le premier plan : sans cela, elle passe devant le jeu de
 * Killian à son ouverture et lui retire souris et clavier (2026-11-27). Elle est créée cachée et sans focus, placée sur
 * le second écran, puis montrée sans l'activer. La fenêtre anticipée de Forge, créée avant tout mixin, est coupée dans
 * ces lancements (build.gradle) ; la nôtre s'ouvre déjà sans focus.
 */
@Mixin(value = Window.class, remap = false)
public abstract class FenetreSansFocusMixin {

    @Shadow
    @Final
    private long window;

    @Redirect(method = "<init>", remap = false,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V", remap = false))
    private void oneforallsurvival$sansFocus() {
        GLFW.glfwDefaultWindowHints();
        if (SourisLibre.actif()) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void oneforallsurvival$montrer(CallbackInfo ci) {
        if (SourisLibre.actif()) {
            FenetreSecondEcran.montrer(window);
        }
    }
}
