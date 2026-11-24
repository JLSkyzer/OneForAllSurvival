package dev.oneforallsurvival.phone.net.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.phone.PhoneRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Branchements côté client du panneau de l'opérateur : l'écran de la caisse, enregistré au démarrage du client. */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class OperatorClient {

    private OperatorClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(PhoneRegistry.OPERATOR_TILL_MENU.get(), OperatorTillScreen::new));
    }
}
