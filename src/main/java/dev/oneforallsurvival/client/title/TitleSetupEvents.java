package dev.oneforallsurvival.client.title;

import dev.oneforallsurvival.OneForAllSurvival;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Lance la lecture de l'adresse du serveur au démarrage du client (jamais chargé côté serveur). */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TitleSetupEvents {
    private TitleSetupEvents() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SiteAddressClient.start();
    }
}
