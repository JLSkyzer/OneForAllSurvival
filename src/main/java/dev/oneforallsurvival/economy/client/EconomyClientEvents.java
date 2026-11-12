package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.economy.EconomyRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Branchements côté client seulement : le rendu de la pièce au sol et l'écran du coffre-fort. */
public final class EconomyClientEvents {

    private EconomyClientEvents() {}

    @Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EconomyRegistry.COIN_DROP.get(), CoinDropRenderer::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(EconomyRegistry.SAFE_MENU.get(), SafeScreen::new));
        }
    }
}
