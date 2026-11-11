package dev.oneforallsurvival.town;

import dev.oneforallsurvival.OneForAllSurvival;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** L'octroi du Bien-être (douche) et son effet sur la vitesse de minage. */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID)
public final class WellBeing {

    private WellBeing() {}

    /** La durée après un octroi : on garde la plus longue, jamais la somme. */
    public static int newDuration(int remaining, int granted) {
        return Math.max(0, Math.max(remaining, granted));
    }

    /** Donne le Bien-être (toujours niveau 0). S'il est déjà là, la durée devient la plus longue des deux. */
    public static void grant(LivingEntity entity, int ticks) {
        MobEffectInstance current = entity.getEffect(TownRegistry.WELL_BEING.get());
        int duration = newDuration(current == null ? 0 : current.getDuration(), ticks);
        if (duration <= 0) {
            return;
        }
        entity.addEffect(new MobEffectInstance(TownRegistry.WELL_BEING.get(), duration, 0, false, true, true));
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity().hasEffect(TownRegistry.WELL_BEING.get())) {
            event.setNewSpeed(event.getNewSpeed() * (1.0F + (float) TownConfig.wellBeingMiningBonus()));
        }
    }
}
