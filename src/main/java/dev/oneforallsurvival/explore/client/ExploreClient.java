package dev.oneforallsurvival.explore.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.explore.ExploreRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Branchements côté client seulement de l'exploration : les écrans, enregistrés au démarrage du client. */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExploreClient {

    private ExploreClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ExploreRegistry.RECEIVER_MENU.get(), ReceiverScreen::new));
    }
}
