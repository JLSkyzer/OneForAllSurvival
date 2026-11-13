package dev.oneforallsurvival;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchConfig;
import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.BenchOverrides;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.WorkshopTicker;
import dev.oneforallsurvival.coast.CoastCommands;
import dev.oneforallsurvival.debug.IslandDebugRequest;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.EconomyCommands;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyGuard;
import dev.oneforallsurvival.economy.SafeBlock;
import dev.oneforallsurvival.economy.StartingMoney;
import dev.oneforallsurvival.explore.ExploreCommands;
import dev.oneforallsurvival.explore.ExploreConfig;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.fence.FenceConfig;
import dev.oneforallsurvival.fence.FenceRegistry;
import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodEvents;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.grid.GridConfig;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.LightningEvents;
import dev.oneforallsurvival.light.LightConfig;
import dev.oneforallsurvival.light.LightRegistry;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.role.RoleCommands;
import dev.oneforallsurvival.role.RoleEvents;
import dev.oneforallsurvival.salvage.SalvageRegistry;
import dev.oneforallsurvival.town.TownConfig;
import dev.oneforallsurvival.town.TownRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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
        modEventBus.addListener(FoodRegistry::onCommonSetup);
        BenchRegistry.register(modEventBus);
        RepairRegistry.register(modEventBus);
        SalvageRegistry.register(modEventBus);
        FoodRegistry.register(modEventBus);
        GridRegistry.register(modEventBus);
        FenceRegistry.register(modEventBus);
        TownRegistry.register(modEventBus);
        LightRegistry.register(modEventBus);
        ExploreRegistry.register(modEventBus);
        EconomyRegistry.register(modEventBus);
        BenchNetwork.register();
        MinecraftForge.EVENT_BUS.register(new RoleEvents());
        MinecraftForge.EVENT_BUS.register(new RoleCommands());
        MinecraftForge.EVENT_BUS.register(new CoastCommands());
        MinecraftForge.EVENT_BUS.register(new ExploreCommands());
        MinecraftForge.EVENT_BUS.register(new EconomyCommands());
        MinecraftForge.EVENT_BUS.register(new MoneyGuard());
        MinecraftForge.EVENT_BUS.register(new WorkshopTicker());
        MinecraftForge.EVENT_BUS.register(new FoodEvents());
        MinecraftForge.EVENT_BUS.register(new LightningEvents());
        context.registerConfig(ModConfig.Type.COMMON, BenchConfig.SPEC);
        context.registerConfig(ModConfig.Type.COMMON, MachineConfig.SPEC, "oneforallsurvival-machines.toml");
        // SERVER : Forge synchronise ce fichier vers les clients, pour que l'infobulle suive le serveur.
        context.registerConfig(ModConfig.Type.SERVER, FoodConfig.SPEC, "oneforallsurvival-food.toml");
        context.registerConfig(ModConfig.Type.SERVER, GridConfig.SPEC, "oneforallsurvival-grid.toml");
        context.registerConfig(ModConfig.Type.SERVER, FenceConfig.SPEC, "oneforallsurvival-fence.toml");
        context.registerConfig(ModConfig.Type.SERVER, TownConfig.SPEC, "oneforallsurvival-town.toml");
        context.registerConfig(ModConfig.Type.SERVER, LightConfig.SPEC, "oneforallsurvival-light.toml");
        context.registerConfig(ModConfig.Type.SERVER, ExploreConfig.SPEC, "oneforallsurvival-explore.toml");
        context.registerConfig(ModConfig.Type.SERVER, EconomyConfig.SPEC, "oneforallsurvival-economy.toml");
        modEventBus.addListener(ExploreConfig::onConfigLoad);
        modEventBus.addListener(EconomyConfig::onConfigEvent);
        MinecraftForge.EVENT_BUS.addListener(StartingMoney::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(CommerceDelivery::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(CommerceDelivery::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(CommerceDelivery::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(SafeBlock::onBreak);
        MinecraftForge.EVENT_BUS.addListener(BenchCatalog::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(BenchOverrides::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(BenchCatalog::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(BenchNetwork::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(BenchNetwork::onLogout);
        MinecraftForge.EVENT_BUS.addListener(IslandDebugRequest::onLogout);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("One For All Survival loaded");
    }
}
