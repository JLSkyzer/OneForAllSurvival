package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.bench.BenchRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Branchements côté client seulement : l'écran du menu, et l'oubli de l'état à la déconnexion. */
public final class BenchClientEvents {

    private BenchClientEvents() {}

    @Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(BenchRegistry.ENGINEER_BENCH_MENU.get(),
                    EngineerBenchScreen::new));
        }
    }

    @Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            BenchClientState.reset();
        }
    }
}
