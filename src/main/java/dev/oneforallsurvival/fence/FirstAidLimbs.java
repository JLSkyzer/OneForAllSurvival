package dev.oneforallsurvival.fence;

import ichttt.mods.firstaid.api.CapabilityExtendedHealthSystem;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import net.minecraft.world.entity.player.Player;

/**
 * Toute la référence à First Aid de la clôture. Cette classe n'est atteinte que derrière
 * {@code ModList.get().isLoaded("firstaid")} : sans First Aid, elle n'est jamais chargée et le mod reste chargeable.
 * First Aid tue un joueur dès que sa tête ou son torse tombe à zéro, quelle que soit sa vie totale.
 */
final class FirstAidLimbs {

    private FirstAidLimbs() {}

    /** La vie, en points de membre, du plus faible entre la tête et le torse ; {@code NaN} si le joueur n'a pas le modèle. */
    static float weakestCriticalLimb(Player player) {
        AbstractPlayerDamageModel model = player.getCapability(CapabilityExtendedHealthSystem.INSTANCE).orElse(null);
        if (model == null) {
            return Float.NaN;
        }
        return Math.min(model.HEAD.currentHealth, model.BODY.currentHealth);
    }
}
