package dev.oneforallsurvival.light.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.light.LightRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Branchements côté client seulement de l'éclairage de la ville : le rendu des faisceaux du phare. */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LightClient {

    private LightClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LightRegistry.LIGHTHOUSE_BE.get(), LighthouseRenderer::new);
    }
}
