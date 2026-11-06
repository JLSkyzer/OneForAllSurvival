package dev.oneforallsurvival.repair.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.repair.RepairRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Branchements côté client seulement du métier de réparation : l'écran du bain galvanique. */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RepairClient {

    private RepairClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(RepairRegistry.GALVANIC_BATH_MENU.get(), GalvanicBathScreen::new));
    }
}
