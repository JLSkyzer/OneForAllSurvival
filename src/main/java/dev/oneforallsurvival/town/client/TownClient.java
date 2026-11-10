package dev.oneforallsurvival.town.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.town.TownRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Branchements côté client seulement de la vie de la ville : les écrans, enregistrés au démarrage du client. */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TownClient {

    private TownClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(TownRegistry.WATER_HEATER_MENU.get(), WaterHeaterScreen::new));
    }
}
