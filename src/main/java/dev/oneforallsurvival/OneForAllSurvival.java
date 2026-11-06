package dev.oneforallsurvival;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.role.RoleCommands;
import dev.oneforallsurvival.role.RoleEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(OneForAllSurvival.MODID)
public final class OneForAllSurvival {
    public static final String MODID = "oneforallsurvival";

    private static final Logger LOGGER = LogUtils.getLogger();

    public OneForAllSurvival(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(new RoleEvents());
        MinecraftForge.EVENT_BUS.register(new RoleCommands());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("One For All Survival loaded");
    }
}
