package dev.oneforallsurvival.grid.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.grid.GridRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Branchements côté client seulement de la gestion du réseau : les écrans, enregistrés au démarrage du client. */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GridClient {

    private GridClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(GridRegistry.LOAD_SHEDDER_MENU.get(), LoadShedderScreen::new));
    }
}
