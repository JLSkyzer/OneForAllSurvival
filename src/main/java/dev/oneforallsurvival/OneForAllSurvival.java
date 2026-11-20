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
import dev.oneforallsurvival.economy.trade.TradeCommands;
import dev.oneforallsurvival.economy.trade.TradeService;
import dev.oneforallsurvival.economy.EconomyCommands;
import dev.oneforallsurvival.economy.town.TreasuryCommands;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyGuard;
import dev.oneforallsurvival.economy.SafeBlock;
import dev.oneforallsurvival.economy.stall.MarketStallBlock;
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
        dev.oneforallsurvival.phone.PhoneRegistry.register(modEventBus);
        BenchNetwork.register();
        MinecraftForge.EVENT_BUS.register(new RoleEvents());
        MinecraftForge.EVENT_BUS.register(new RoleCommands());
        MinecraftForge.EVENT_BUS.register(new CoastCommands());
        MinecraftForge.EVENT_BUS.register(new ExploreCommands());
        MinecraftForge.EVENT_BUS.register(new EconomyCommands());
        MinecraftForge.EVENT_BUS.register(new dev.oneforallsurvival.phone.PhoneCommands());
        MinecraftForge.EVENT_BUS.register(new TreasuryCommands());
        MinecraftForge.EVENT_BUS.register(new MoneyGuard());
        MinecraftForge.EVENT_BUS.register(new TradeService.Events());
        MinecraftForge.EVENT_BUS.register(new TradeCommands());
        MinecraftForge.EVENT_BUS.register(new dev.oneforallsurvival.economy.invoice.InvoiceCommands());
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
        context.registerConfig(ModConfig.Type.SERVER, dev.oneforallsurvival.phone.PhoneConfig.SPEC, "oneforallsurvival-phone.toml");
        modEventBus.addListener(ExploreConfig::onConfigLoad);
        modEventBus.addListener(EconomyConfig::onConfigEvent);
        MinecraftForge.EVENT_BUS.addListener(StartingMoney::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(CommerceDelivery::onLoggedIn);
        // Après la livraison des attentes : l'avis d'un paiement reçu peut dire « en attente ».
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.invoice.InvoiceService::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.invoice.InvoiceService::onLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(CommerceDelivery::onRespawn);
        MinecraftForge.EVENT_BUS.addListener(CommerceDelivery::onServerTick);
        // Temps passé dans un rôle payé : départ à la connexion, crédit à la déconnexion ; l'échantillon de chaque
        // seconde et la paie de minuit passent par le tick de PayrollService (échantillon, clôture, dettes).
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.payroll.PayrollService::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.payroll.PayrollService::onServerStarted);
        // Prélèvement de minuit (E6) : inscrit juste après la paie (même priorité, donc appelé après elle) ; il lit la
        // pause posée par la vérification d'horloge de la paie au même tic, et rattrape au démarrage après elle.
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.debit.DailyDebit::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.debit.DailyDebit::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.debit.DirectDebit::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.grid.supplier.PowerCommands::onServerStopped);
        // Après la livraison des attentes (CommerceDelivery::onLoggedIn, plus haut) : salaires versés pendant
        // l'absence, dettes dues au joueur.
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.payroll.PayrollService::onLoggedIn);
        // Prélèvements refusés non lus, et prélevés pendant l'absence.
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.debit.DirectDebit::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.payroll.RoleTimeTracker::onLoggedInEvent);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.payroll.RoleTimeTracker::onLoggedOutEvent);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.payroll.RoleTimeTracker::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(SafeBlock::onBreak);
        MinecraftForge.EVENT_BUS.addListener(MarketStallBlock::onBreak);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.town.TreasuryBlock::onBreak);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.town.TownBoardBlock::onBreak);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.grid.supplier.PowerStationBlock::onBreak);
        // Lot E6, tâche 3 : compteurs et disjoncteurs jumelés à un fournisseur, accord des clients.
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.grid.supplier.PowerPairing::onBreak);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.grid.supplier.PowerPairing::onLoggedIn);
        MinecraftForge.EVENT_BUS.register(new dev.oneforallsurvival.grid.supplier.PowerCommands());
        // Lot E6, tâche 4 : la facture quotidienne d'électricité, créancier et émetteur du prélèvement de minuit ; les
        // impayés des clients rappelés au fondateur à sa connexion.
        dev.oneforallsurvival.grid.supplier.PowerBilling.install(dev.oneforallsurvival.grid.supplier.PowerBilling.SERVER);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.grid.supplier.PowerBilling::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.grid.supplier.PowerActionPacket::onLoggedOut);
        // Téléphone : sessions ouvertes (tic serveur) et départ du joueur (session et frein oubliés).
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.phone.PhoneService::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.phone.PhoneService::onLoggedOut);
        // Le tableau de la mairie : les comptes publics partent à la connexion puis quand ils changent (1 fois par seconde).
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.town.TownFinances::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.town.TownFinances::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.town.TownFinances::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(BenchCatalog::onAddReloadListener);
        // Le butin des mobs : table de données, rechargée avec /reload.
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.loot.MobCoinTables::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.loot.MobCoinTables::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.quest.QuestTemplates::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.quest.QuestTemplates::onTagsUpdated);
        // Quêtes du joueur : séries renouvelées à la connexion et par un tic de contrôle (toutes les 20 ticks).
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.quest.QuestService::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.quest.QuestService::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.quest.QuestService::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.quest.QuestService::onLoggedOut);
        // Progression des quêtes par les gestes : les derniers à parler, jamais sur un geste annulé (le combat
        // excepté : un mod qui annule les objets lâchés n'annule pas la mort). Marque « posé » dans chaque chunk.
        modEventBus.addListener(dev.oneforallsurvival.economy.quest.PlacedBlocks::onRegisterCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.level.chunk.LevelChunk.class,
                dev.oneforallsurvival.economy.quest.PlacedBlocks::onAttachChunk);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.quest.QuestProgress::onBreak);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.quest.QuestProgress::onPlace);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.quest.QuestProgress::onFluidPlace);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.quest.QuestProgress::onPiston);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, true,
                dev.oneforallsurvival.economy.quest.QuestProgress::onLivingDrops);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.quest.QuestProgress::onFished);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.quest.QuestProgress::onBabySpawn);
        MinecraftForge.EVENT_BUS.addListener(dev.oneforallsurvival.economy.quest.QuestProgress::onServerStopped);
        // Origine des mobs : la dernière à parler, et même sur un événement annulé (le générateur fait quand même
        // apparaître son mob, avec son NBT, qu'il faut effacer).
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, true,
                dev.oneforallsurvival.economy.loot.MobOrigin::onFinalizeSpawn);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.loot.MobOrigin::onConversion);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.loot.MobOrigin::onBabySpawn);
        // Raid d'un pillard recruté : noté au coup, avant que la mort ne le retire de son raid.
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.loot.MobOrigin::onLivingDamage);
        // Butin des mobs : le dernier à parler, jamais sur des objets déjà annulés (rien n'est alors lâché ni compté).
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                dev.oneforallsurvival.economy.loot.MobLoot::onLivingDrops);
        MinecraftForge.EVENT_BUS.addListener(BenchOverrides::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(BenchCatalog::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(BenchNetwork::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(BenchNetwork::onLogout);
        MinecraftForge.EVENT_BUS.addListener(IslandDebugRequest::onLogout);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("One For All Survival loaded");
        // Tickets de chunk des antennes (lot T2) : revus au chargement de chaque monde, avant d'être remis ; Forge
        // demande que le rappel s'inscrive ici, sur le fil principal.
        event.enqueueWork(() -> net.minecraftforge.common.world.ForgeChunkManager.setForcedChunkLoadingCallback(MODID,
                dev.oneforallsurvival.phone.net.AntennaChunks::validate));
    }
}
