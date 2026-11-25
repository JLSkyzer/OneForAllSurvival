package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.SafeBlock;
import dev.oneforallsurvival.economy.SafeBlockEntity;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import dev.oneforallsurvival.economy.client.PurseScreen;
import dev.oneforallsurvival.economy.client.SafeScreen;
import dev.oneforallsurvival.food.DesalinatorBlockEntity;
import dev.oneforallsurvival.explore.DetectorItem;
import dev.oneforallsurvival.explore.ExploreConfig;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.explore.HeadlampItem;
import dev.oneforallsurvival.explore.MechanismBlock;
import dev.oneforallsurvival.explore.MechanismBlockEntity;
import dev.oneforallsurvival.explore.ReceiverBlockEntity;
import dev.oneforallsurvival.explore.SignalMessage;
import dev.oneforallsurvival.explore.client.ReceiverScreen;
import dev.oneforallsurvival.fence.FenceRegistry;
import dev.oneforallsurvival.food.CanteenPotBlockEntity;
import dev.oneforallsurvival.food.DryerBlockEntity;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FridgeBlockEntity;
import dev.oneforallsurvival.food.IncubatorBlockEntity;
import dev.oneforallsurvival.food.client.CanteenPotScreen;
import dev.oneforallsurvival.food.client.DesalinatorScreen;
import dev.oneforallsurvival.food.client.DryerScreen;
import dev.oneforallsurvival.food.client.FridgeScreen;
import dev.oneforallsurvival.food.client.IncubatorScreen;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.LoadShedderBlockEntity;
import dev.oneforallsurvival.grid.WeatherForecast;
import dev.oneforallsurvival.grid.WeatherStationBlock;
import dev.oneforallsurvival.grid.client.LoadShedderScreen;
import dev.oneforallsurvival.grid.client.SubscriberNameScreen;
import dev.oneforallsurvival.light.LightConfig;
import dev.oneforallsurvival.light.LightRegistry;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.repair.GalvanicBathBlockEntity;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.repair.client.GalvanicBathScreen;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import dev.oneforallsurvival.salvage.SalvageRegistry;
import dev.oneforallsurvival.town.ShowerHeadBlock;
import dev.oneforallsurvival.town.ShowerHeadBlockEntity;
import dev.oneforallsurvival.town.TownRegistry;
import dev.oneforallsurvival.town.WaterHeaterBlockEntity;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Capture de contrôle des visuels des lots 2 à 5 (développement seulement, jamais empaqueté). Un client qui se
 * pilote seul : il monte le bain, l'armoire, le dessalinisateur et le séchoir dans leurs quatre états réels, les
 * cinq débris, un mur de cadres avec tous les objets, une grille des objets du lot 3 à côté d'un steak, le matériel
 * du réseau (délesteurs, disjoncteurs, compteur, parafoudres, stations sous trois temps), le matériel du lot 5
 * (couveuse, marmite de cantine, électrificateur et plaque dans leurs quatre états, une ligne de clôture), puis ouvre
 * les écrans des machines, du délesteur et de l'abonné, l'établi et les catégories JEI, et écrit une image par vue dans
 * {@code visuels-captures}. Activé par {@code -Doneforallsurvival.demo.visuels}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DemoVisuels {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-demo-visuels");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.visuels") != null;
    /**
     * {@code etal} ou {@code commerce} : ne jouer que les captures du commerce ; {@code tresor} : que celles du trésor et du
     * tableau de la mairie ; {@code quetes} : que celles de l'écran des quêtes (contrôle rapide d'une tâche) ;
     * {@code electricite} : que celles du panneau du poste, de la facture d'électricité et du poste posé (lot E6) ;
     * {@code telephone} : que celles du téléphone, de la prise murale et de l'écran de repli (lot T1) ;
     * {@code telephone_reseau} : que celles de l'antenne, de la carte SIM, de l'outil de maintenance, du panneau de
     * l'opérateur et du repli avec une ligne (lot T2) ; sans les autres.
     */
    private static final String SEUL = System.getProperty("oneforallsurvival.demo.visuels.seul", "");
    private static final String MONDE = "demo_visuels";
    private static final String[] ETATS = {"arret", "marche", "sans_courant", "panne"};
    private static final String[] OBJETS = {"tester", "welder", "fuse", "treated_fuse", "coil", "treated_coil", "bearing",
        "treated_bearing", "cleaning_kit", "treated_cleaning_kit", "scrap_metal", "aviation_wire", "instrument_core",
        "incomplete_instrument_core", "battery_cell", "incomplete_battery_cell", "debris_fuselage", "debris_wiring",
        "debris_instruments", "debris_battery", "debris_cabin", "galvanic_bath", "engineer_bench"};

    /** Les objets du lot 3, rangés avec leur équivalent de Minecraft, pour juger la lisibilité. */
    private static final String[] VIVRES = {"minecraft:cooked_beef", "dried_meat", "salted_meat", "minecraft:cooked_cod",
        "dried_fish", "salted_fish", "minecraft:sweet_berries", "dried_fruit", "salted_fruit",
        "minecraft:glass_bottle", "potable_water_bottle", "salt", "spoiled_food", "fridge", "desalinator", "dryer"};
    private static final String[] MACHINES = {"armoire", "dessalinisateur", "sechoir"};

    /** Les objets du lot 7, à côté de ce qui leur ressemble dans Minecraft : boussole, casque de fer, tonneau. */
    private static final String[] LOT7 = {"metal_detector", "minecraft:compass", "headlamp", "minecraft:iron_helmet",
        "buried_cache", "signal_receiver", "ancient_mechanism", "minecraft:barrel", "minecraft:note_block"};

    /** Le matériel du lot 4, à côté de ce qui lui ressemble dans Minecraft, pour juger la lisibilité. */
    private static final String[] RESEAU = {"load_shedder", "meter", "breaker", "seal", "meter_reading", "surge_arrester",
        "weather_station", "minecraft:lever", "minecraft:paper", "minecraft:lightning_rod", "minecraft:compass",
        "minecraft:iron_nugget"};
    /** Les délesteurs dans l'ordre : à l'arrêt, en marche (courant poussé dans l'entrée), en panne. */
    private static final BlockPos[] DELESTEURS = new BlockPos[3];
    private static final String[] ETATS_DELESTEUR = {"arret", "marche", "panne"};
    /** Les disjoncteurs : enclenché, coupé, coupé et scellé, enclenché et scellé (cet état ne se joue pas). */
    private static final BlockPos[] DISJONCTEURS = new BlockPos[4];
    private static final String[] ETATS_DISJONCTEUR = {"enclenche", "coupe", "coupe_scelle", "enclenche_scelle"};
    private static BlockPos compteur;
    private static BlockPos parafoudre;
    private static BlockPos parafoudrePanne;
    private static BlockPos station;
    private static BlockPos stationEteinte;

    /** Le matériel du lot 5, à côté de ce qui lui ressemble dans Minecraft, pour juger la lisibilité. */
    private static final String[] LOT5 = {"incubator", "hot_plate", "canteen_pot", "electrifier", "electric_fence",
        "minecraft:egg", "minecraft:furnace", "farmersdelight:cooking_pot", "minecraft:iron_bars", "minecraft:oak_fence",
        "minecraft:cauldron", "minecraft:redstone_lamp"};
    /** Une rangée par machine du lot 5 (couveuse, marmite, électrificateur, plaque), un bloc par état. */
    private static final BlockPos[][] LOT5_BLOCS = new BlockPos[4][4];
    private static BlockPos origine;
    private static BlockPos cloture;
    private static volatile boolean lot5Pose;
    /** Le phare du lot 6, posé en tour libre au moment de ses vues ; la tour est de pierre. */
    private static BlockPos phare;
    private static volatile boolean pharePose;
    /** Le matériel de la ville (lot 6) : chauffe-eau dans leurs quatre états, douches, et chaque bloc contre un mur. */
    private static final BlockPos[] CHAUFFE_EAU = new BlockPos[4];
    private static BlockPos chauffeEauMur;
    private static final BlockPos[] DOUCHES = new BlockPos[2];
    private static BlockPos doucheMur;
    private static BlockPos phareMur;
    private static volatile boolean villePose;
    /** Le matériel du lot 7, loin à l'ouest (au-delà des 6 chunks affichés) : cache, récepteurs, mécanismes, grotte. */
    private static BlockPos exploration;
    private static BlockPos cacheMur;
    private static BlockPos recepteurMur;
    private static BlockPos mecanismeMur;
    private static final BlockPos[] RECEPTEURS = new BlockPos[4];
    private static final BlockPos[] MECANISMES = new BlockPos[5];
    private static BlockPos grotte;
    private static final List<BlockPos> EXPLORATION_ALIMENTEE = new ArrayList<>();
    /** Vrai une fois les étages réglés : le bloc de l'étage 3 est alors tenu à cet étage, inactif. */
    private static volatile boolean etage3Force;
    private static volatile boolean explorationPosee;

    /**
     * Les objets de la monnaie (lot E1), les pièces au premier rang, puis ce qui leur ressemble dans Minecraft : lingots,
     * émeraude, poudre rouge, cuir, coffres, bloc de fer, tonneau.
     */
    private static final String[] MONNAIE = {"copper_coin", "silver_coin", "gold_coin", "emerald_coin", "ruby_coin",
        "purse", "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:emerald",
        "minecraft:redstone", "minecraft:leather", "safe", "minecraft:chest", "minecraft:iron_block", "minecraft:barrel",
        "minecraft:ender_chest"};
    /** La monnaie, loin au sud (au-delà des 6 chunks affichés) : aucune capture d'un lot précédent ne la voit. */
    private static BlockPos monnaie;
    private static BlockPos coffreSeul;
    private static BlockPos coffreMur;
    private static volatile boolean monnaiePosee;
    private static final Coin[] PIECES = Coin.values();

    private enum Phase { TITRE, ATTENTE_MONDE, PREPARATION, SCENARIO, FIN }

    private record Etape(String nom, Runnable action, BooleanSupplier pret, long reposMs, String image) {}

    private static Phase phase = Phase.TITRE;
    private static boolean dansUneEtape;
    private static long debutPhaseNs;
    private static final List<Etape> ETAPES = new ArrayList<>();
    private static int index;
    private static boolean actionFaite;
    private static long debutAction;
    private static long debutRepos;
    private static Path dossier;
    private static volatile boolean preparationFaite;
    private static int images;
    private static String echec;

    /** Les bains d'exposition, dans l'ordre de {@link #ETATS}, le bain entouré de voisins, le mur et l'établi. */
    private static final BlockPos[] BAINS = new BlockPos[4];
    private static BlockPos entoure;
    private static BlockPos debris;
    private static BlockPos mur;
    private static BlockPos etabli;
    /** Les machines du lot 3 : une rangée par machine, un bloc par état dans l'ordre de {@link #ETATS}. */
    private static final BlockPos[][] VIVRES_BLOCS = new BlockPos[3][4];
    private static final List<BlockPos> ALIMENTES = new ArrayList<>();

    private DemoVisuels() {}

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || phase == Phase.FIN || event.phase != TickEvent.Phase.END || dansUneEtape) {
            return;
        }
        dansUneEtape = true;
        Minecraft mc = Minecraft.getInstance();
        if (!garderToasts) {
            mc.getToasts().clear();
        }
        try {
            avancer(mc);
        } catch (RuntimeException | IOException e) {
            LOG.error("[visuels] échec en phase {}", phase, e);
            echec = e.toString();
            terminer(mc);
        } finally {
            dansUneEtape = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (ACTIF && event.phase == TickEvent.Phase.END && phase != Phase.FIN) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.screen instanceof PauseScreen) {
                mc.setScreen(null);
            }
        }
    }

    /** Le bain « en marche » reçoit du courant pour de bon ; celui « sans courant » n'en reçoit jamais. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (ACTIF && event.phase == TickEvent.Phase.END && !PRISES_EN_CHARGE.isEmpty()) {
            tenirLesPrises(event.getServer());
        }
        if (!ACTIF || event.phase != TickEvent.Phase.END || BAINS[1] == null) {
            return;
        }
        if (event.getServer().overworld().getBlockEntity(BAINS[1]) instanceof GalvanicBathBlockEntity be) {
            be.energy().receiveEnergy(200, false);
        }
        for (BlockPos p : ALIMENTES) {
            if (event.getServer().overworld().getBlockEntity(p) instanceof ElectricMachineBlockEntity machine) {
                machine.energy().receiveEnergy(100, false);
            }
        }
        // Lot 7 : récepteurs sous tension et mécanismes tenus à leur étage par 1 FE par tick (seuil réglé à 1).
        for (BlockPos p : EXPLORATION_ALIMENTEE) {
            var monde = event.getServer().overworld();
            if (monde.isLoaded(p) && monde.getBlockEntity(p) != null) {
                boolean recepteur = monde.getBlockEntity(p) instanceof ReceiverBlockEntity;
                monde.getBlockEntity(p).getCapability(ForgeCapabilities.ENERGY, null)
                        .ifPresent(e -> e.receiveEnergy(recepteur ? 200 : 1, false));
            }
        }
        if (etage3Force && MECANISMES[3] != null) {
            var monde = event.getServer().overworld();
            var etat = monde.getBlockState(MECANISMES[3]);
            if (etat.is(ExploreRegistry.MECHANISM.get()) && (etat.getValue(MechanismBlock.STAGE) != 3
                    || etat.getValue(MechanismBlock.ACTIVE))) {
                monde.setBlock(MECANISMES[3], etat.setValue(MechanismBlock.STAGE, 3).setValue(MechanismBlock.ACTIVE, false), 3);
            }
        }
        // La douche « en marche » n'a personne dessous : son état actif est reposé à chaque tic pour la capture.
        if (DOUCHES[1] != null) {
            var monde = event.getServer().overworld();
            if (monde.getBlockEntity(DOUCHES[1]) instanceof ShowerHeadBlockEntity douche) {
                douche.tank().fill(new FluidStack(TownRegistry.HOT_WATER.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
                var etat = monde.getBlockState(DOUCHES[1]);
                if (etat.is(TownRegistry.SHOWER_HEAD.get()) && !etat.getValue(ShowerHeadBlock.ACTIVE)) {
                    monde.setBlock(DOUCHES[1], etat.setValue(ShowerHeadBlock.ACTIVE, true), 3);
                }
            }
        }
        // 15 FE par tick dans l'entrée (au nord) du délesteur en marche : devant rien, gauche servie, droite en
        // partie, dessus coupé. Chaque machine d'essai prend 10 FE par tick une fois pleine.
        if (DELESTEURS[1] != null && event.getServer().overworld().getBlockEntity(DELESTEURS[1]) instanceof
                LoadShedderBlockEntity tableau) {
            tableau.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).ifPresent(e -> e.receiveEnergy(15, false));
        }
    }

    private static void avancer(Minecraft mc) throws IOException {
        long maintenant = System.nanoTime();
        switch (phase) {
            case TITRE -> {
                if (mc.getOverlay() == null && mc.level == null && mc.screen != null) {
                    mc.options.pauseOnLostFocus = false;
                    mc.options.onboardAccessibility = false;
                    mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                    mc.options.renderDistance().set(6);
                    mc.options.simulationDistance().set(6);
                    mc.options.guiScale().set(3);
                    mc.options.enableVsync().set(false);
                    mc.options.framerateLimit().set(60);
                    mc.mouseHandler.releaseMouse();
                    phase = Phase.ATTENTE_MONDE;
                    creerLeMonde(mc);
                    debutPhaseNs = maintenant;
                }
            }
            case ATTENTE_MONDE -> {
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null
                        && mc.getSingleplayerServer().isReady() && maintenant - debutPhaseNs > 6_000_000_000L) {
                    mc.setScreen(null);
                    phase = Phase.PREPARATION;
                    MinecraftServer serveur = mc.getSingleplayerServer();
                    serveur.execute(() -> preparer(serveur));
                }
            }
            case PREPARATION -> {
                if (preparationFaite) {
                    construireScenario(mc);
                    dossier = mc.gameDirectory.toPath().resolve("visuels-captures");
                    Files.createDirectories(dossier);
                    phase = Phase.SCENARIO;
                }
            }
            case SCENARIO -> jouer(mc, maintenant);
            default -> {
            }
        }
    }

    private static void creerLeMonde(Minecraft mc) {
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // La scène, côté serveur
    // ================================================================================================

    private static Item objet(String nom) {
        return ForgeRegistries.ITEMS.getValue(nom.contains(":") ? new ResourceLocation(nom)
                : new ResourceLocation("oneforallsurvival", nom));
    }

    /** Une machine du lot 3 face au sud, dans l'état voulu par les vrais chemins : vide, alimentée, sans courant, en panne. */
    private static void poserMachine(ServerLevel monde, BlockPos p, int machine, int etat) {
        Block bloc = List.of(FoodRegistry.FRIDGE.get(), FoodRegistry.DESALINATOR.get(), FoodRegistry.DRYER.get()).get(machine);
        monde.setBlock(p, bloc.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
        var be = (ElectricMachineBlockEntity) monde.getBlockEntity(p);
        if (etat != 0) {
            garnirMachine(be);
        }
        if (etat == 1) {
            ALIMENTES.add(p);
        } else if (etat == 3) {
            be.breakDown(FaultType.FUSE);
        }
    }

    private static void garnirMachine(ElectricMachineBlockEntity be) {
        if (be instanceof FridgeBlockEntity fridge) {
            fridge.inventory().setStackInSlot(0, new ItemStack(Items.COOKED_BEEF, 8));
            fridge.inventory().setStackInSlot(1, new ItemStack(Items.COOKED_COD, 5));
            fridge.inventory().setStackInSlot(9, new ItemStack(Items.SWEET_BERRIES, 12));
        } else if (be instanceof DesalinatorBlockEntity desal) {
            desal.waterTank().fill(new FluidStack(Fluids.WATER, 6000), IFluidHandler.FluidAction.EXECUTE);
            desal.potableTank().fill(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 3000), IFluidHandler.FluidAction.EXECUTE);
            desal.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY, new ItemStack(Items.GLASS_BOTTLE, 16));
            desal.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_FILLED,
                    new ItemStack(FoodRegistry.POTABLE_WATER_BOTTLE.get(), 3));
            desal.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 7));
        } else if (be instanceof DryerBlockEntity dryer) {
            dryer.inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT, new ItemStack(Items.BEEF, 8));
            dryer.inventory().setStackInSlot(DryerBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 4));
            dryer.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(FoodRegistry.SALTED_MEAT.get(), 3));
        } else if (be instanceof WaterHeaterBlockEntity chauffeEau) {
            chauffeEau.potable().fill(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 3000), IFluidHandler.FluidAction.EXECUTE);
            chauffeEau.hot().fill(new FluidStack(TownRegistry.HOT_WATER.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        } else if (be instanceof IncubatorBlockEntity couveuse) {
            for (int i = 0; i < IncubatorBlockEntity.SLOTS; i++) {
                couveuse.inventory().setStackInSlot(i, new ItemStack(Items.EGG));
            }
        } else if (be instanceof CanteenPotBlockEntity marmite) {
            marmite.inventory().setStackInSlot(0, new ItemStack(Items.BEEF, 2));
            marmite.inventory().setStackInSlot(1, new ItemStack(Items.CARROT, 2));
            marmite.inventory().setStackInSlot(2, new ItemStack(Items.POTATO, 2));
            marmite.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_CONTAINER, new ItemStack(Items.BOWL, 6));
        }
    }

    /**
     * Le lot 5, posé au moment où ses vues commencent (et non avec le reste) : une couveuse sans courant perd sa
     * couvée au bout d'une minute, et son état changerait avant la capture. Une rangée par machine, quatre états :
     * vide, alimentée, sans courant, en panne. La plaque a son ustensile de Farmer's Delight dessus, l'électrificateur
     * sa clôture, sans quoi aucun des deux ne travaille.
     */
    private static void preparerLot5(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        Block marmite = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("farmersdelight", "cooking_pot"));
        List<Block> blocs = List.of(FoodRegistry.INCUBATOR.get(), FoodRegistry.CANTEEN_POT.get(),
                FenceRegistry.ELECTRIFIER.get(), FoodRegistry.HOT_PLATE.get());
        for (int m = 0; m < 4; m++) {
            for (int e = 0; e < 4; e++) {
                BlockPos p = LOT5_BLOCS[m][e];
                var etat = blocs.get(m).defaultBlockState();
                if (m < 2) {
                    etat = etat.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
                }
                monde.setBlock(p, etat, 3);
                var be = (ElectricMachineBlockEntity) monde.getBlockEntity(p);
                if (e != 0) {
                    garnirMachine(be);
                    if (m == 2) {
                        monde.setBlock(p.east(), FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState(), 3);
                        monde.setBlock(p.east(2), FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState(), 3);
                    } else if (m == 3) {
                        monde.setBlock(p.above(), marmite.defaultBlockState(), 3);
                    }
                }
                if (e == 1) {
                    ALIMENTES.add(p);
                } else if (e == 3) {
                    be.breakDown(FaultType.FUSE);
                }
            }
        }
        // Une ligne de clôture : l'électrificateur, six clôtures, un coude, et une clôture en bois au bout (elle ne se
        // raccorde pas : ce n'est pas la même clôture).
        BlockPos debut = cloture;
        monde.setBlock(debut, FenceRegistry.ELECTRIFIER.get().defaultBlockState(), 3);
        for (int i = 1; i <= 6; i++) {
            monde.setBlock(debut.offset(i, 0, 0), FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState(), 3);
        }
        for (int i = 1; i <= 3; i++) {
            monde.setBlock(debut.offset(6, 0, -i), FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState(), 3);
        }
        monde.setBlock(debut.offset(7, 0, 0), Blocks.OAK_FENCE.defaultBlockState(), 3);
        ALIMENTES.add(debut);
        // Contre un mur : son modèle n'est plein que sur 11/16, les faces voisines doivent rester dessinées.
        BlockPos contreMur = LOT5_BLOCS[2][0].offset(-5, 0, 0);
        monde.setBlock(contreMur, FenceRegistry.ELECTRIFIER.get().defaultBlockState(), 3);
        for (BlockPos mur : List.of(contreMur.north(), contreMur.west())) {
            monde.setBlock(mur, Blocks.STONE.defaultBlockState(), 3);
            monde.setBlock(mur.above(), Blocks.STONE.defaultBlockState(), 3);
        }
        lot5Pose = true;
    }

    private static GalvanicBathBlockEntity poserBain(ServerLevel monde, BlockPos p) {
        monde.setBlock(p, RepairRegistry.GALVANIC_BATH.get().defaultBlockState(), 3);
        return (GalvanicBathBlockEntity) monde.getBlockEntity(p);
    }

    private static void garnir(GalvanicBathBlockEntity be) {
        be.inventory().setStackInSlot(GalvanicBathBlockEntity.SLOT_PIECE, new ItemStack(RepairRegistry.FUSE.get(), 64));
        be.inventory().setStackInSlot(GalvanicBathBlockEntity.SLOT_ZINC, new ItemStack(ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("create", "zinc_ingot")), 64));
    }

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        RoleSavedData.get(serveur).book().assign(joueur.getUUID(), RoleBook.ENGINEER_ROLE_ID);
        BlockPos ici = joueur.blockPosition();
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, ici.getX(), ici.getZ());
        BlockPos o = new BlockPos(ici.getX(), y, ici.getZ());
        origine = o;
        // Les positions du lot 5 sont connues d'avance (les vues les lisent à la construction du scénario) ; les blocs,
        // eux, ne sont posés qu'à l'étape « pose du lot 5 ».
        for (int m = 0; m < 4; m++) {
            for (int e = 0; e < 4; e++) {
                LOT5_BLOCS[m][e] = o.offset(4 * e, 0, -46 - 6 * m);
            }
        }
        cloture = o.offset(0, 0, -76);

        // Les quatre états, par les vrais chemins du bloc : vide, alimenté, sans courant, en panne.
        for (int i = 0; i < 4; i++) {
            BAINS[i] = o.offset(4 * i, 0, 0);
            GalvanicBathBlockEntity be = poserBain(monde, BAINS[i]);
            if (i == 1 || i == 2) {
                garnir(be);
            } else if (i == 3) {
                ((ElectricMachineBlockEntity) be).breakDown(FaultType.FUSE);
            }
        }
        // Un bain entouré de pierre : ses voisins ne doivent ni disparaître ni laisser voir à travers.
        entoure = o.offset(0, 0, 6);
        poserBain(monde, entoure);
        for (Direction d : new Direction[] {Direction.WEST, Direction.EAST, Direction.NORTH}) {
            monde.setBlock(entoure.relative(d), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
        etabli = o.offset(6, 0, 6);
        monde.setBlock(etabli, BenchRegistry.ENGINEER_BENCH.get().defaultBlockState(), 3);

        // Les trois machines du lot 3, les quatre états de chacune.
        for (int m = 0; m < 3; m++) {
            for (int e = 0; e < 4; e++) {
                VIVRES_BLOCS[m][e] = o.offset(4 * e, 0, -6 * (m + 1));
                poserMachine(monde, VIVRES_BLOCS[m][e], m, e);
            }
        }

        // Les cinq débris, chacun en carré de deux sur deux pour voir le raccord.
        debris = o.offset(0, 0, 12);
        List<Block> blocs = List.of(SalvageRegistry.DEBRIS_FUSELAGE.get(), SalvageRegistry.DEBRIS_WIRING.get(),
                SalvageRegistry.DEBRIS_INSTRUMENTS.get(), SalvageRegistry.DEBRIS_BATTERY.get(),
                SalvageRegistry.DEBRIS_CABIN.get());
        for (int i = 0; i < blocs.size(); i++) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    monde.setBlock(debris.offset(3 * i + dx, dy, 0), blocs.get(i).defaultBlockState(), 3);
                }
            }
        }

        // Le mur de cadres : six colonnes, quatre rangées, tous les objets.
        mur = o.offset(0, 0, 26);
        for (int x = 0; x < 6; x++) {
            for (int h = 0; h < 4; h++) {
                monde.setBlock(mur.offset(x, h, 0), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        for (int i = 0; i < OBJETS.length; i++) {
            BlockPos p = mur.offset(i % 6, 3 - i / 6, 1);
            ItemFrame cadre = new ItemFrame(monde, p, Direction.SOUTH);
            cadre.setItem(new ItemStack(objet(OBJETS[i])), false);
            monde.addFreshEntity(cadre);
        }

        preparerReseau(monde, o);

        // De quoi remplir l'inventaire pour la vue en cases, le matériel du lot 4 à la suite.
        joueur.getInventory().clearContent();
        for (int i = 0; i < OBJETS.length && i < 36; i++) {
            joueur.getInventory().setItem(i, new ItemStack(objet(OBJETS[i])));
        }
        for (int i = 0; i < 7; i++) {
            joueur.getInventory().setItem(OBJETS.length + i, new ItemStack(objet(RESEAU[i])));
        }
        joueur.setGameMode(GameType.CREATIVE);
        joueur.getAbilities().flying = true;
        joueur.onUpdateAbilities();
        preparationFaite = true;
    }

    /** Une machine d'essai pleine : elle ne demande plus que ses 10 FE par tick. */
    private static void consommateur(ServerLevel monde, BlockPos p, boolean plein) {
        monde.setBlock(p, DevBlocks.testMachine().defaultBlockState(), 3);
        if (plein && monde.getBlockEntity(p) instanceof ElectricMachineBlockEntity m) {
            for (int k = 0; k < 20; k++) { // la réserve n'accepte qu'une part par appel
                m.energy().receiveEnergy(1000, false);
            }
        }
    }

    /** Le lot 4, au nord des machines du lot 3 : délesteurs, disjoncteurs et compteur, parafoudres, stations. */
    private static void preparerReseau(ServerLevel monde, BlockPos o) {
        for (int i = 0; i < 3; i++) {
            DELESTEURS[i] = o.offset(5 * i, 0, -28);
            monde.setBlock(DELESTEURS[i], GridRegistry.LOAD_SHEDDER.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
        }
        // En marche : rien devant, pour qu'on voie la face ; des machines à gauche (ouest), à droite (est) et dessus.
        BlockPos marche = DELESTEURS[1];
        consommateur(monde, marche.west(), true);
        consommateur(monde, marche.east(), true);
        consommateur(monde, marche.above(), true);
        // En panne : une machine branchée dessus, que l'écran montre coupée.
        ((ElectricMachineBlockEntity) monde.getBlockEntity(DELESTEURS[2])).breakDown(FaultType.FUSE);
        consommateur(monde, DELESTEURS[2].above(), false);

        // Les disjoncteurs et le compteur, blocs en ligne : FACING est le sens du courant, la face regarde l'arrière.
        // Courant vers le nord, face au sud.
        boolean[][] etats = {{true, false}, {false, false}, {false, true}, {true, true}};
        for (int i = 0; i < 4; i++) {
            DISJONCTEURS[i] = o.offset(3 * i, 0, -34);
            monde.setBlock(DISJONCTEURS[i], GridRegistry.BREAKER.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                    .setValue(BreakerBlock.CLOSED, etats[i][0]).setValue(BreakerBlock.SEALED, etats[i][1]), 3);
        }
        compteur = o.offset(12, 0, -34);
        monde.setBlock(compteur, GridRegistry.METER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);

        parafoudre = o.offset(0, 0, -40);
        parafoudrePanne = o.offset(3, 0, -40);
        monde.setBlock(parafoudre, GridRegistry.SURGE_ARRESTER.get().defaultBlockState(), 3);
        monde.setBlock(parafoudrePanne, GridRegistry.SURGE_ARRESTER.get().defaultBlockState(), 3);
        ((ElectricMachineBlockEntity) monde.getBlockEntity(parafoudrePanne)).breakDown(FaultType.FUSE);

        // Deux stations : l'une alimentée, l'autre jamais (face éteinte).
        station = o.offset(7, 0, -40);
        stationEteinte = o.offset(10, 0, -40);
        for (BlockPos p : new BlockPos[] {station, stationEteinte}) {
            monde.setBlock(p, GridRegistry.WEATHER_STATION.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
        }
        ALIMENTES.add(station);
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    /** Tous les objets en grand, leur nom dessous : la vérité du modèle de chaque objet, et de sa clé de langue. */
    private static final class Grille extends Screen {
        private final String[] noms;
        private final int rangees;

        Grille(String[] noms, int rangees) {
            super(Component.literal("grille"));
            this.noms = noms;
            this.rangees = rangees;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(0, 0, width, height, 0xFF8B8B8B);
            int colonnes = 6;
            int cw = width / colonnes;
            int ch = height / rangees;
            for (int i = 0; i < noms.length; i++) {
                int x = (i % colonnes) * cw;
                int y = (i / colonnes) * ch;
                ItemStack pile = new ItemStack(objet(noms[i]));
                if (pile.getItem() instanceof DetectorItem) {
                    DetectorItem.store(pile, ExploreConfig.detectorCapacity() * 3 / 4);
                } else if (pile.getItem() instanceof HeadlampItem) {
                    HeadlampItem.store(pile, ExploreConfig.headlampCapacity() / 2);
                }
                g.pose().pushPose();
                g.pose().translate(x + cw / 2F - 16, y + 2, 0);
                g.pose().scale(2, 2, 1);
                g.renderItem(pile, 0, 0);
                g.pose().popPose();
                int l = 0;
                for (var ligne : font.split(pile.getHoverName(), cw - 2)) {
                    g.drawString(font, ligne, x + 1, y + 35 + 9 * l++, 0xFFFFFFFF, true);
                }
            }
        }
    }

    /** Un objet en grand et son infobulle réelle, à côté : la pile de pièces et la bourse avec leur total. */
    private static final class Infobulle extends Screen {
        private final ItemStack pile;

        Infobulle(ItemStack pile) {
            super(Component.literal("infobulle"));
            this.pile = pile;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(0, 0, width, height, 0xFF8B8B8B);
            g.pose().pushPose();
            g.pose().translate(width / 2F - 150, height / 2F - 40, 0);
            g.pose().scale(5, 5, 1);
            g.renderItem(pile, 0, 0);
            g.renderItemDecorations(font, pile, 0, 0);
            g.pose().popPose();
            g.renderTooltip(font, pile, width / 2 - 40, height / 2 - 10);
        }
    }

    private static void regarder(Minecraft mc, BlockPos b, Vec3 d, boolean nuit) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        Vec3 cible = new Vec3(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
        Vec3 oeil = cible.add(d);
        double dx = -d.x;
        double dy = -d.y;
        double dz = -d.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.setScreen(null);
        mc.options.hideGui = true;
        mc.gameRenderer.setRenderHand(false);
        serveur.execute(() -> {
            ServerLevel monde = serveur.overworld();
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            monde.setDayTime(nuit ? 18000 : 6000);
            joueur.setNoGravity(true);
            joueur.teleportTo(monde, oeil.x, oeil.y - 1.62, oeil.z, yaw, pitch);
        });
    }

    /**
     * Montre la bulle d'un bouton de l'écran du coffre-fort ou de la bourse, comme si la souris était dessus (la
     * souris de la machine reste libre), et note au journal si le cadre tient dans l'interface.
     */
    private static void bulle(Minecraft mc, String element, String image) {
        etape("bulle " + element, () -> {
            if (mc.screen instanceof CoinAccountScreen<?> ecran) {
                ecran.pinHover(element);
                LOG.info("[visuels] ecran {} : cadre {}x{} en ({}, {}) dans une interface de {}x{}", ecran.getClass().getSimpleName(),
                        ecran.getXSize(), ecran.getYSize(), ecran.getGuiLeft(), ecran.getGuiTop(),
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }
        }, () -> mc.screen instanceof CoinAccountScreen<?>, 800, image);
    }

    private static void etape(String nom, Runnable action, BooleanSupplier pret, long reposMs, String image) {
        ETAPES.add(new Etape(nom, action, pret, reposMs, image));
    }

    private static void vue(Minecraft mc, String fichier, BlockPos b, Vec3 d, boolean nuit) {
        etape("vue " + fichier, () -> regarder(mc, b, d, nuit), () -> true, 1500, fichier);
    }

    /** Change le temps du monde, devant la station alimentée, et attend que sa face suive. */
    private static void meteo(Minecraft mc, String fichier, boolean pluie, boolean orage, WeatherForecast.Trend attendue) {
        etape("meteo " + attendue, () -> {
            regarder(mc, station, new Vec3(1.35, 1.0, 1.75), false);
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> serveur.overworld().setWeatherParameters(pluie ? 0 : 6000, pluie ? 6000 : 0, pluie,
                    orage));
        }, () -> mc.level.getBlockState(station).is(GridRegistry.WEATHER_STATION.get())
                && mc.level.getBlockState(station).getValue(WeatherStationBlock.TREND) == attendue, 2500, fichier);
    }

    private static void ouvrir(Minecraft mc, BlockPos p) {
        mc.options.hideGui = false;
        MinecraftServer serveur = mc.getSingleplayerServer();
        serveur.execute(() -> {
            ServerLevel monde = serveur.overworld();
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            monde.setDayTime(6000);
            // À portée du bloc : le menu se referme tout seul au-delà de huit blocs.
            joueur.teleportTo(monde, p.getX() + 2.5, p.getY(), p.getZ() + 2.5, 135F, 20F);
            monde.getBlockState(p).use(monde, joueur, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
        });
    }

    private static void fermer(Minecraft mc) {
        if (mc.player != null) {
            mc.player.closeContainer();
        }
        mc.setScreen(null);
    }

    /** JEI n'est pas une dépendance de compilation de {@code devcontent} : tout passe par réflexion. */
    private static void montrerDansJei(Minecraft mc, String champ) {
        mc.options.hideGui = false;
        try {
            Class<?> plugin = Class.forName("dev.oneforallsurvival.bench.compat.BenchJeiPlugin");
            Field runtime = plugin.getDeclaredField("runtime");
            runtime.setAccessible(true);
            Object jei = runtime.get(null);
            if (jei == null) {
                throw new IllegalStateException("JEI n'a pas démarré");
            }
            Object recettes = Class.forName("mezz.jei.api.runtime.IJeiRuntime").getMethod("getRecipesGui").invoke(jei);
            Class.forName("mezz.jei.api.runtime.IRecipesGui").getMethod("showTypes", List.class)
                    .invoke(recettes, List.of(plugin.getField(champ).get(null)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean ecranJei(Minecraft mc) {
        return mc.screen != null && mc.screen.getClass().getName().contains("jei");
    }

    private static void construireScenario(Minecraft mc) {
        if ("etal".equals(SEUL) || "commerce".equals(SEUL)) {
            commerce(mc);
            return;
        }
        if ("tresor".equals(SEUL)) {
            construireTresor(mc);
            return;
        }
        if ("quetes".equals(SEUL)) {
            construireQuetes(mc);
            return;
        }
        if ("electricite".equals(SEUL)) {
            construireElectricite(mc);
            return;
        }
        if ("telephone".equals(SEUL)) {
            construireTelephone(mc);
            return;
        }
        if ("telephone_reseau".equals(SEUL)) {
            construireTelephoneReseau(mc);
            return;
        }
        Vec3 sudEst = new Vec3(1.35, 1.0, 1.75);
        etape("vue calage", () -> regarder(mc, BAINS[0], sudEst, false), () -> true, 3000, null);
        for (int i = 0; i < 4; i++) {
            vue(mc, "bain_" + ETATS[i] + "_jour.png", BAINS[i], sudEst, false);
        }
        for (int i = 0; i < 4; i++) {
            vue(mc, "bain_" + ETATS[i] + "_nuit.png", BAINS[i], sudEst, true);
        }
        vue(mc, "bain_marche_dessus.png", BAINS[1], new Vec3(0.4, 1.9, 0.7), false);
        vue(mc, "bain_arret_nord_ouest.png", BAINS[0], new Vec3(-1.35, 0.9, -1.75), false);
        vue(mc, "bain_arret_bas.png", BAINS[0], new Vec3(1.5, -0.1, 1.5), false);
        vue(mc, "bains_les_quatre.png", BAINS[1].offset(2, 0, 0), new Vec3(1.0, 3.0, 7.0), false);
        vue(mc, "bain_voisins.png", entoure, new Vec3(1.2, 1.3, 2.2), false);
        vue(mc, "bain_arret_face.png", BAINS[0], new Vec3(0, 0.3, 2.3), false);
        vue(mc, "bain_et_etabli.png", entoure.offset(3, 0, 0), new Vec3(0.5, 2.5, 5.5), false);
        vue(mc, "debris_tous.png", debris.offset(7, 1, 0), new Vec3(0, 1.5, 9.5), false);
        String[] noms = {"fuselage", "cablage", "instruments", "batterie", "cabine"};
        for (int i = 0; i < noms.length; i++) {
            vue(mc, "debris_" + noms[i] + ".png", debris.offset(3 * i, 0, 0), new Vec3(1.8, 1.6, 2.6), false);
        }
        vue(mc, "objets_cadres.png", mur.offset(2, 1, 0), new Vec3(0.5, 0.5, 5.0), false);

        // Le lot 3 : chaque machine dans ses quatre états, de jour, face au sud (la face avant se lit du sud-est).
        for (int m = 0; m < 3; m++) {
            for (int e = 0; e < 4; e++) {
                vue(mc, MACHINES[m] + "_" + ETATS[e] + "_jour.png", VIVRES_BLOCS[m][e], sudEst, false);
            }
            vue(mc, MACHINES[m] + "_les_quatre.png", VIVRES_BLOCS[m][1].offset(2, 0, 0), new Vec3(0.0, 2.2, 5.5), false);
            vue(mc, MACHINES[m] + "_arriere_dessus.png", VIVRES_BLOCS[m][1], new Vec3(-1.5, 1.6, -1.7), false);
        }
        vue(mc, "armoire_marche_nuit.png", VIVRES_BLOCS[0][1], sudEst, true);

        // Le lot 4 : chaque bloc dans ses états réels, de jour, face au sud.
        Vec3 face = new Vec3(0.0, 0.5, 2.3);
        Vec3 arriereDessus = new Vec3(-1.5, 1.6, -1.7);
        vue(mc, "delesteurs_les_trois.png", DELESTEURS[1], new Vec3(0.0, 2.6, 7.5), false);
        for (int i = 0; i < 3; i++) {
            vue(mc, "delesteur_" + ETATS_DELESTEUR[i] + "_face.png", DELESTEURS[i], face, false);
        }
        vue(mc, "delesteur_arret_jour.png", DELESTEURS[0], sudEst, false);
        vue(mc, "delesteur_arret_arriere_dessus.png", DELESTEURS[0], arriereDessus, false);
        vue(mc, "delesteur_arret_dessous.png", DELESTEURS[0], new Vec3(1.5, -0.1, 1.5), false);
        vue(mc, "delesteur_marche_nuit.png", DELESTEURS[1], face, true);
        vue(mc, "disjoncteurs_les_quatre.png", DISJONCTEURS[1].offset(1, 0, 0), new Vec3(0.5, 1.6, 4.2), false);
        for (int i = 0; i < 4; i++) {
            vue(mc, "disjoncteur_" + ETATS_DISJONCTEUR[i] + ".png", DISJONCTEURS[i], sudEst, false);
        }
        vue(mc, "disjoncteur_arriere_dessus.png", DISJONCTEURS[0], arriereDessus, false);
        vue(mc, "compteur_jour.png", compteur, sudEst, false);
        vue(mc, "compteur_face.png", compteur, face, false);
        vue(mc, "compteur_arriere_dessus.png", compteur, arriereDessus, false);
        vue(mc, "parafoudre_jour.png", parafoudre, sudEst, false);
        vue(mc, "parafoudre_panne_jour.png", parafoudrePanne, sudEst, false);
        vue(mc, "parafoudre_dessus.png", parafoudre, new Vec3(0.4, 1.9, 0.7), false);
        vue(mc, "station_eteinte_jour.png", stationEteinte, sudEst, false);
        vue(mc, "station_stable_jour.png", station, sudEst, false);
        vue(mc, "station_arriere_dessus.png", station, arriereDessus, false);
        // La météo du monde est figée (doWeatherCycle à faux) : la station lit le temps présent.
        meteo(mc, "station_pluie.png", true, false, WeatherForecast.Trend.RAIN_SOON);
        meteo(mc, "station_orage.png", true, true, WeatherForecast.Trend.STORM_SOON);
        meteo(mc, null, false, false, WeatherForecast.Trend.STABLE);
        vue(mc, "parafoudres_stations.png", parafoudre.offset(5, 0, 0), new Vec3(0.0, 2.2, 6.5), false);

        construirePhare(mc);
        construireVille(mc, sudEst);
        construireExploration(mc, sudEst);
        construireLot5(mc, sudEst);

        etape("grille", () -> {
            mc.options.hideGui = false;
            mc.setScreen(new Grille(OBJETS, 4));
        }, () -> true, 800, "objets_grille.png");
        etape("grille lot 7", () -> mc.setScreen(new Grille(LOT7, 2)), () -> true, 800, "lot7_grille.png");
        etape("grille vivres", () -> mc.setScreen(new Grille(VIVRES, 3)), () -> true, 800, "vivres_grille.png");
        etape("grille reseau", () -> mc.setScreen(new Grille(RESEAU, 2)), () -> true, 800, "reseau_grille.png");
        etape("grille lot 5", () -> mc.setScreen(new Grille(LOT5, 2)), () -> true, 800, "lot5_grille.png");
        // En survie : en créatif, ce même écran s'ouvre sur les onglets et cache l'inventaire.
        etape("inventaire", () -> mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList()
                .getPlayers().get(0).setGameMode(GameType.SURVIVAL)), () -> !mc.player.getAbilities().instabuild, 500, null);
        etape("inventaire ouvert", () -> mc.setScreen(new InventoryScreen(mc.player)), () -> true, 800,
                "objets_inventaire.png");
        etape("en main", () -> {
            mc.setScreen(null);
            mc.gameRenderer.setRenderHand(true);
            mc.player.getInventory().selected = 1;
        }, () -> true, 800, "objets_en_main.png");

        for (int i = 0; i < 4; i++) {
            int n = i;
            etape("ecran " + ETATS[n], () -> {
                fermer(mc);
                ouvrir(mc, BAINS[n]);
            }, () -> mc.screen instanceof GalvanicBathScreen, 1200, "ecran_bain_" + ETATS[n] + ".png");
        }
        etape("etabli", () -> {
            fermer(mc);
            ouvrir(mc, etabli);
        }, () -> mc.screen instanceof EngineerBenchScreen, 1500, "etabli_liste.png");
        etape("etabli bain", () -> ((EngineerBenchScreen) mc.screen).selectById(
                new ResourceLocation("oneforallsurvival", "galvanic_bath")), () -> true, 800, "etabli_bain.png");
        etape("etabli testeur", () -> ((EngineerBenchScreen) mc.screen).selectById(
                new ResourceLocation("oneforallsurvival", "tester")), () -> true, 800, "etabli_testeur.png");
        etape("etabli delesteur", () -> ((EngineerBenchScreen) mc.screen).selectById(
                new ResourceLocation("oneforallsurvival", "load_shedder")), () -> true, 800, "etabli_delesteur.png");
        etape("etabli scelle", () -> ((EngineerBenchScreen) mc.screen).selectById(
                new ResourceLocation("oneforallsurvival", "seal")), () -> true, 800, "etabli_scelle.png");
        etape("etabli couveuse", () -> ((EngineerBenchScreen) mc.screen).selectById(
                new ResourceLocation("oneforallsurvival", "incubator")), () -> true, 800, "etabli_couveuse.png");
        etape("etabli cloture", () -> ((EngineerBenchScreen) mc.screen).selectById(
                new ResourceLocation("oneforallsurvival", "electric_fence")), () -> true, 800, "etabli_cloture.png");
        Class<?>[] ecrans = {FridgeScreen.class, DesalinatorScreen.class, DryerScreen.class};
        for (int m = 0; m < 3; m++) {
            int n = m;
            for (int e : new int[] {1, 2, 3}) {
                int k = e;
                etape("ecran " + MACHINES[n] + " " + ETATS[k], () -> {
                    fermer(mc);
                    ouvrir(mc, VIVRES_BLOCS[n][k]);
                }, () -> ecrans[n].isInstance(mc.screen), 1500, "ecran_" + MACHINES[n] + "_" + ETATS[k] + ".png");
            }
        }
        for (int i : new int[] {1, 2}) {
            etape("ecran delesteur " + ETATS_DELESTEUR[i], () -> {
                fermer(mc);
                ouvrir(mc, DELESTEURS[i]);
            }, () -> mc.screen instanceof LoadShedderScreen, 1500, "ecran_delesteur_" + ETATS_DELESTEUR[i] + ".png");
        }
        etape("ecran abonne", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.setScreen(new SubscriberNameScreen(compteur, "Marin Lefevre"));
        }, () -> mc.screen instanceof SubscriberNameScreen, 800, "ecran_abonne.png");
        etape("ecran abonne vide", () -> mc.setScreen(new SubscriberNameScreen(compteur, "")),
                () -> mc.screen instanceof SubscriberNameScreen, 800, "ecran_abonne_vide.png");
        etape("jei sechoir", () -> {
            fermer(mc);
            montrerDansJei(mc, "DRYING_TYPE");
        }, () -> ecranJei(mc), 1500, "jei_sechoir.png");
        etape("jei bain", () -> {
            fermer(mc);
            montrerDansJei(mc, "GALVANIC_TYPE");
        }, () -> ecranJei(mc), 1500, "jei_bain.png");
        construireMonnaie(mc, sudEst);
    }

    /**
     * Le lot 5 : chaque machine dans ses quatre états, de jour, face au sud, la couveuse d'abord (ses écrans suivent
     * tout de suite, avant que la couvée sans courant ne soit perdue), puis la marmite, la plaque, l'électrificateur
     * et la ligne de clôture.
     */
    private static void construireLot5(Minecraft mc, Vec3 sudEst) {
        etape("pose du lot 5", () -> {
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> preparerLot5(serveur));
        }, () -> lot5Pose, 2500, null);
        Vec3 arriereDessus = new Vec3(-1.5, 1.6, -1.7);
        for (int e = 0; e < 4; e++) {
            int k = e;
            etape("vue couveuse " + ETATS[k], () -> regarder(mc, LOT5_BLOCS[0][k], sudEst, false), () -> true, 1500,
                    "couveuse_" + ETATS[k] + "_jour.png");
        }
        for (int e : new int[] {1, 2, 3}) {
            int k = e;
            etape("ecran couveuse " + ETATS[k], () -> {
                fermer(mc);
                ouvrir(mc, LOT5_BLOCS[0][k]);
            }, () -> mc.screen instanceof IncubatorScreen, 1500, "ecran_couveuse_" + ETATS[k] + ".png");
        }
        vue(mc, "couveuse_les_quatre.png", LOT5_BLOCS[0][1].offset(2, 0, 0), new Vec3(0.0, 2.2, 5.5), false);
        vue(mc, "couveuse_arriere_dessus.png", LOT5_BLOCS[0][1], arriereDessus, false);
        vue(mc, "couveuse_marche_nuit.png", LOT5_BLOCS[0][1], sudEst, true);
        for (int e = 0; e < 4; e++) {
            vue(mc, "marmite_" + ETATS[e] + "_jour.png", LOT5_BLOCS[1][e], sudEst, false);
        }
        vue(mc, "marmite_les_quatre.png", LOT5_BLOCS[1][1].offset(2, 0, 0), new Vec3(0.0, 2.2, 5.5), false);
        vue(mc, "marmite_arriere_dessus.png", LOT5_BLOCS[1][1], arriereDessus, false);
        for (int e = 0; e < 4; e++) {
            vue(mc, "plaque_" + ETATS[e] + "_jour.png", LOT5_BLOCS[3][e], sudEst.add(0, 0.3, 0), false);
        }
        vue(mc, "plaque_les_quatre.png", LOT5_BLOCS[3][1].offset(2, 0, 0), new Vec3(0.0, 2.6, 5.5), false);
        vue(mc, "plaque_allumee_nuit.png", LOT5_BLOCS[3][1], sudEst.add(0, 0.3, 0), true);
        vue(mc, "plaque_allumee_dessus.png", LOT5_BLOCS[3][1].above(), new Vec3(0.4, 2.0, 0.9), true);
        for (int e = 0; e < 4; e++) {
            vue(mc, "electrificateur_" + ETATS[e] + "_jour.png", LOT5_BLOCS[2][e], sudEst, false);
        }
        vue(mc, "electrificateur_les_quatre.png", LOT5_BLOCS[2][1].offset(2, 0, 0), new Vec3(0.0, 2.2, 6.5), false);
        vue(mc, "electrificateur_dessus.png", LOT5_BLOCS[2][0], new Vec3(0.4, 1.9, 0.7), false);
        vue(mc, "electrificateur_contre_un_mur.png", LOT5_BLOCS[2][0].offset(-5, 0, 0), new Vec3(1.1, 1.3, 1.6), false);
        vue(mc, "cloture_ligne.png", cloture.offset(3, 0, -1), new Vec3(3.0, 3.0, 8.0), false);
        vue(mc, "cloture_poteau.png", cloture.offset(3, 0, 0), new Vec3(1.2, 0.9, 2.0), false);
        vue(mc, "cloture_coude_dessus.png", cloture.offset(6, 0, -1), new Vec3(1.0, 4.5, 3.0), false);
        vue(mc, "cloture_et_bois.png", cloture.offset(7, 0, 0), new Vec3(1.6, 1.2, 2.6), false);
        for (int e : new int[] {1, 2, 3}) {
            int k = e;
            etape("ecran marmite " + ETATS[k], () -> {
                fermer(mc);
                ouvrir(mc, LOT5_BLOCS[1][k]);
            }, () -> mc.screen instanceof CanteenPotScreen, 1500, "ecran_marmite_" + ETATS[k] + ".png");
        }
        etape("fin du lot 5", () -> fermer(mc), () -> true, 300, null);
    }

    /**
     * La monnaie (lot E1), loin au sud : les cinq pièces en main et au sol, une pile et son infobulle, la bourse et son
     * écran avec un total, le coffre-fort seul puis contre un mur et son écran avec un total, la grille des objets à côté de
     * leurs équivalents de Minecraft. Jouée à la fin, le joueur en survie : la main ne se montre que dans cet état.
     */
    private static void construireMonnaie(Minecraft mc, Vec3 sudEst) {
        etape("pose de la monnaie", () -> {
            fermer(mc);
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> preparerMonnaie(serveur));
        }, () -> monnaiePosee, 2500, null);
        // Au sol : les cinq pièces en rang, puis la pile et la bourse pleine de près.
        etape("vue pieces au sol", () -> regarder(mc, monnaie.offset(-12, 0, 0), new Vec3(0.0, 2.2, 4.6), false),
                () -> true, 2500, "monnaie_pieces_au_sol.png");
        etape("vue pieces au sol nuit", () -> regarder(mc, monnaie.offset(-12, 0, 0), new Vec3(0.0, 2.2, 4.6), true),
                () -> true, 1500, "monnaie_pieces_au_sol_nuit.png");
        // La bourse tourne sur elle-même comme les pièces, et de chant elle disparaît : plusieurs prises, de plus près et
        // de plus haut, espacées de façon que l'une au moins la montre de face.
        etape("vue pile et bourse au sol", () -> regarder(mc, monnaie.offset(-3, 0, 0), new Vec3(0.0, 1.3, 2.2), false),
                () -> true, 1500, "monnaie_pile_et_bourse_au_sol.png");
        for (int i = 2; i <= 4; i++) {
            etape("vue pile et bourse au sol " + i, () -> {}, () -> true, 450 + 170 * i,
                    "monnaie_pile_et_bourse_au_sol_" + i + ".png");
        }
        // En main : les cases 0 à 4 sont les pièces, la 5 la bourse, la 6 le coffre-fort.
        String[] noms = {"cuivre", "argent", "or", "emeraude", "rubis", "bourse", "coffre_fort"};
        for (int i = 0; i < noms.length; i++) {
            int k = i;
            etape("en main " + noms[k], () -> {
                mc.options.hideGui = false;
                mc.gameRenderer.setRenderHand(true);
                mc.player.getInventory().selected = k;
                MinecraftServer serveur = mc.getSingleplayerServer();
                BlockPos c = monnaie.offset(-12, 0, 6);
                serveur.execute(() -> {
                    ServerLevel monde = serveur.overworld();
                    monde.setDayTime(6000);
                    serveur.getPlayerList().getPlayers().get(0).teleportTo(monde, c.getX() + 0.5, c.getY(),
                            c.getZ() + 0.5, 180F, 35F);
                });
            }, () -> true, 1800, "monnaie_en_main_" + noms[k] + ".png");
        }
        etape("infobulle pile", () -> {
            mc.options.hideGui = false;
            mc.setScreen(new Infobulle(new ItemStack(Coin.GOLD.item(), 23)));
        }, () -> true, 800, "monnaie_pile_infobulle.png");
        etape("infobulle bourse", () -> {
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            remplirBourse(bourse);
            mc.setScreen(new Infobulle(bourse));
        }, () -> true, 800, "monnaie_bourse_infobulle.png");
        etape("ecran bourse", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> {
                ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                joueur.getInventory().selected = 5;
                joueur.getInventory().getItem(5).use(serveur.overworld(), joueur, InteractionHand.MAIN_HAND);
            });
        }, () -> mc.screen instanceof PurseScreen, 1500, "monnaie_ecran_bourse.png");
        bulle(mc, "row.gold.10", "monnaie_ecran_bourse_bulle_ligne.png");
        bulle(mc, "break.emerald", "monnaie_ecran_bourse_bulle_change.png");
        // Le coffre-fort : seul, puis contre un mur (nord et ouest), de jour.
        etape("vue coffre seul", () -> {
            fermer(mc);
            regarder(mc, coffreSeul, sudEst, false);
        }, () -> true, 1500, "monnaie_coffre_seul.png");
        etape("vue coffre face", () -> regarder(mc, coffreSeul, new Vec3(0.0, 0.5, 2.3), false), () -> true, 1500,
                "monnaie_coffre_face.png");
        etape("vue coffre arriere dessus", () -> regarder(mc, coffreSeul, new Vec3(-1.5, 1.6, -1.7), false),
                () -> true, 1500, "monnaie_coffre_arriere_dessus.png");
        etape("vue coffre contre un mur", () -> regarder(mc, coffreMur, new Vec3(1.1, 1.3, 1.6), false), () -> true,
                1500, "monnaie_coffre_contre_un_mur.png");
        etape("ecran coffre", () -> {
            fermer(mc);
            ouvrir(mc, coffreSeul);
        }, () -> mc.screen instanceof SafeScreen, 1500, "monnaie_ecran_coffre.png");
        bulle(mc, "row.silver.10", "monnaie_ecran_coffre_bulle_ligne.png");
        bulle(mc, "row.ruby.64", "monnaie_ecran_coffre_bulle_retrait_impossible.png");
        bulle(mc, "group.ruby", "monnaie_ecran_coffre_bulle_change_grise.png");
        bulle(mc, "break.gold", "monnaie_ecran_coffre_bulle_change.png");
        etape("ecran coffre sans bulle", () -> {
            if (mc.screen instanceof CoinAccountScreen<?> ecran) {
                ecran.pinHover(null);
            }
        }, () -> true, 200, null);
        etape("grille monnaie", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.setScreen(new Grille(MONNAIE, 3));
        }, () -> true, 800, "monnaie_grille.png");
        etape("fin de la monnaie", () -> fermer(mc), () -> true, 300, null);
    }

    /** Une bourse pleine : un peu de chaque coupure, soit 13 957 cuivres (1 rubis, 3 émeraudes, 8 or, 12 argent, 37 cuivre). */
    private static void remplirBourse(ItemStack bourse) {
        long[] nombres = {37, 12, 8, 3, 1};
        for (Coin c : PIECES) {
            Purse.add(bourse, c, nombres[c.ordinal()]);
        }
    }

    private static void preparerMonnaie(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        // Loin au sud, au-delà des 6 chunks affichés autour de tout ce que posent les lots précédents (le lot 6 est à
        // l'est, le lot 7 à l'ouest, le lot 5 au nord) : aucune capture d'un lot précédent ne voit celle-ci, ni l'inverse.
        BlockPos m = origine.offset(0, 0, 130);
        monnaie = m;
        coffreSeul = m.offset(4, 0, 0);
        coffreMur = m.offset(10, 0, 0);
        var coffre = EconomyRegistry.SAFE.get().defaultBlockState().setValue(SafeBlock.FACING, Direction.SOUTH);
        for (BlockPos p : new BlockPos[] {coffreSeul, coffreMur}) {
            monde.setBlock(p, coffre, 3);
            if (monde.getBlockEntity(p) instanceof SafeBlockEntity be) {
                be.setOwner(joueur.getUUID());
            }
        }
        entourer(monde, coffreMur);
        SafeAccounts comptes = SafeAccounts.get(serveur);
        long[] enCoffre = {4, 25, 12, 2, 3};
        for (Coin c : PIECES) {
            comptes.deposit(joueur.getUUID(), c, enCoffre[c.ordinal()] - comptes.count(joueur.getUUID(), c));
        }
        // Les cinq pièces en rang, une pile de 48 pièces d'argent, une bourse pleine : tout au sol, ramassage interdit.
        for (Coin c : PIECES) {
            sol(monde, CoinDropEntity.at(monde, m.getX() - 16 + 2 * c.ordinal() + 0.5, m.getY() + 0.1, m.getZ() + 0.5, c, 1));
        }
        sol(monde, CoinDropEntity.at(monde, m.getX() - 5 + 0.5, m.getY() + 0.1, m.getZ() + 0.5, Coin.SILVER, 48));
        ItemStack bourseSol = new ItemStack(EconomyRegistry.PURSE.get());
        remplirBourse(bourseSol);
        ItemEntity porte = new ItemEntity(monde, m.getX() - 1 + 0.5, m.getY() + 0.1, m.getZ() + 0.5, bourseSol);
        porte.setPickUpDelay(30000); // pas 32767 : ce délai-là marque un objet factice, qui deviendrait une entité inerte
        porte.setDeltaMovement(Vec3.ZERO);
        monde.addFreshEntity(porte);
        // L'inventaire : les cinq pièces (cases 0 à 4), la bourse pleine (5), le coffre-fort (6).
        joueur.getInventory().clearContent();
        int[] piles = {37, 12, 8, 3, 1};
        for (Coin c : PIECES) {
            joueur.getInventory().setItem(c.ordinal(), new ItemStack(c.item(), piles[c.ordinal()]));
        }
        ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
        remplirBourse(bourse);
        joueur.getInventory().setItem(5, bourse);
        joueur.getInventory().setItem(6, new ItemStack(EconomyRegistry.SAFE_ITEM.get()));
        monnaiePosee = true;
    }

    private static void sol(ServerLevel monde, CoinDropEntity piece) {
        piece.setDeltaMovement(Vec3.ZERO);
        piece.setPickUpDelay(CoinDropEntity.NEVER_PICKED_UP);
        monde.addFreshEntity(piece);
    }

    /**
     * Le phare, de nuit : posé au sommet d'une tour de pierre, assez haut pour être en tour libre (au moins 8 blocs
     * au-dessus de la mer), alimenté par le tick serveur. Les faisceaux tournent : trois vues à des instants différents,
     * puis le phare de près.
     */
    private static void construirePhare(Minecraft mc) {
        etape("pose du phare", () -> {
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> {
                ServerLevel monde = serveur.overworld();
                int sommet = Math.max(origine.getY() + 8, MachineConfig.seaLevel() + LightConfig.lighthouseMinAboveSea() + 2);
                phare = origine.offset(12, 0, 24).atY(sommet);
                for (int y = origine.getY(); y < sommet; y++) {
                    monde.setBlock(phare.atY(y), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
                monde.setBlock(phare, LightRegistry.LIGHTHOUSE.get().defaultBlockState(), 3);
                garnirMachine((ElectricMachineBlockEntity) monde.getBlockEntity(phare));
                ALIMENTES.add(phare);
                pharePose = true;
            });
        }, () -> pharePose, 2500, null);
        vuePhare(mc, "phare_nuit_1.png", new Vec3(16.0, -1.0, 16.0));
        etape("attente faisceau", () -> {}, () -> true, 1300, null);
        vuePhare(mc, "phare_nuit_2.png", new Vec3(16.0, -1.0, 16.0));
        etape("attente faisceau 2", () -> {}, () -> true, 1700, null);
        vuePhare(mc, "phare_nuit_3.png", new Vec3(-10.0, -3.0, 22.0));
        vuePhare(mc, "phare_de_pres_nuit.png", new Vec3(2.2, 1.0, 3.0));
    }

    /** Une vue du phare : sa position n'est connue qu'après la pose, elle est donc lue au moment de la vue. */
    private static void vuePhare(Minecraft mc, String fichier, Vec3 d) {
        vuePhare(mc, fichier, d, true);
    }

    private static void vuePhare(Minecraft mc, String fichier, Vec3 d, boolean nuit) {
        etape("vue " + fichier, () -> regarder(mc, phare, d, nuit), () -> true, 1500, fichier);
    }

    /**
     * Le lot 6 : le chauffe-eau dans ses quatre états, la douche à sec puis en marche, et chaque bloc contre un mur
     * (leurs modèles ne sont pas des cubes pleins : les faces voisines doivent rester dessinées). Le phare de jour,
     * éteint en haut de sa tour (il ne marche que la nuit), puis contre un mur.
     */
    private static void construireVille(Minecraft mc, Vec3 sudEst) {
        etape("pose de la ville", () -> {
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> preparerVille(serveur));
        }, () -> villePose, 2500, null);
        for (int e = 0; e < 4; e++) {
            int k = e;
            etape("vue chauffe-eau " + ETATS[k], () -> regarder(mc, CHAUFFE_EAU[k], sudEst, false), () -> true, 1500,
                    "chauffe_eau_" + ETATS[k] + "_jour.png");
        }
        etape("vue chauffe-eau les quatre", () -> regarder(mc, CHAUFFE_EAU[1].offset(2, 0, 0), new Vec3(0.0, 2.2, 5.5),
                false), () -> true, 1500, "chauffe_eau_les_quatre.png");
        etape("vue chauffe-eau nuit", () -> regarder(mc, CHAUFFE_EAU[1], sudEst, true), () -> true, 1500,
                "chauffe_eau_marche_nuit.png");
        etape("vue chauffe-eau arriere", () -> regarder(mc, CHAUFFE_EAU[1], new Vec3(-1.5, 1.6, -1.7), false),
                () -> true, 1500, "chauffe_eau_arriere_dessus.png");
        etape("vue chauffe-eau mur", () -> regarder(mc, chauffeEauMur, new Vec3(1.1, 1.3, 1.6), false), () -> true,
                1500, "chauffe_eau_contre_un_mur.png");
        etape("vue douche a sec", () -> regarder(mc, DOUCHES[0], new Vec3(1.0, 0.5, 1.6), false), () -> true, 1500,
                "douche_a_sec_jour.png");
        etape("vue douche en marche", () -> regarder(mc, DOUCHES[1], new Vec3(1.0, 0.3, 1.6), false), () -> true, 1500,
                "douche_en_marche_jour.png");
        etape("vue douche en marche nuit", () -> regarder(mc, DOUCHES[1], new Vec3(1.0, 0.3, 1.6), true), () -> true,
                1500, "douche_en_marche_nuit.png");
        etape("vue douche dessous", () -> regarder(mc, DOUCHES[0], new Vec3(0.6, -1.3, 0.8), false), () -> true, 1500,
                "douche_dessous.png");
        etape("vue douche mur", () -> regarder(mc, doucheMur, new Vec3(1.1, 0.5, 1.6), false), () -> true, 1500,
                "douche_contre_un_mur.png");
        etape("vue douche mur dessus", () -> regarder(mc, doucheMur, new Vec3(1.1, 1.4, 1.4), false), () -> true, 1500,
                "douche_contre_un_mur_dessus.png");
        vuePhare(mc, "phare_eteint_jour.png", new Vec3(5.0, -1.0, 6.0), false);
        vuePhare(mc, "phare_de_pres_eteint_jour.png", new Vec3(2.2, 1.0, 3.0), false);
        etape("vue phare mur", () -> regarder(mc, phareMur, new Vec3(1.1, 1.1, 1.8), false), () -> true, 1500,
                "phare_contre_un_mur.png");
    }

    private static void preparerVille(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        // Loin à l'est (au-delà de la distance d'affichage de 6 chunks) : les vues regardent vers le nord-ouest, et le bloc
        // d'essai magenta et noir du lot 4 ne doit pas apparaître derrière.
        BlockPos v = origine.offset(110, 0, -10);
        for (int e = 0; e < 4; e++) {
            BlockPos p = v.offset(4 * e, 0, 0);
            CHAUFFE_EAU[e] = p;
            monde.setBlock(p, TownRegistry.WATER_HEATER.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
            var be = (ElectricMachineBlockEntity) monde.getBlockEntity(p);
            if (e != 0) {
                garnirMachine(be);
            }
            if (e == 1) {
                ALIMENTES.add(p);
            } else if (e == 3) {
                be.breakDown(FaultType.FUSE);
            }
        }
        chauffeEauMur = v.offset(0, 0, -6);
        monde.setBlock(chauffeEauMur, TownRegistry.WATER_HEATER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
        entourer(monde, chauffeEauMur);
        // Une pomme pend sous un plafond : un bloc de pierre au-dessus de chacune.
        for (int i = 0; i < 2; i++) {
            BlockPos p = v.offset(4 * i, 0, -12);
            DOUCHES[i] = p;
            monde.setBlock(p.above(), Blocks.STONE.defaultBlockState(), 3);
            monde.setBlock(p, TownRegistry.SHOWER_HEAD.get().defaultBlockState(), 3);
        }
        doucheMur = v.offset(8, 0, -12);
        monde.setBlock(doucheMur.above(), Blocks.STONE.defaultBlockState(), 3);
        monde.setBlock(doucheMur, TownRegistry.SHOWER_HEAD.get().defaultBlockState(), 3);
        entourer(monde, doucheMur);
        phareMur = v.offset(12, 0, -12);
        monde.setBlock(phareMur, LightRegistry.LIGHTHOUSE.get().defaultBlockState(), 3);
        entourer(monde, phareMur);
        villePose = true;
    }

    /**
     * Le lot 7, loin à l'ouest : la cache, les récepteurs (sans courant, à l'écoute, message non lu, celui de l'écran),
     * les mécanismes (étages 0 à 3, puis actif avec une lampe de redstone à côté), chacun contre un mur, l'écran du
     * récepteur à deux messages, puis la lampe frontale portée de nuit dans une grotte close.
     */
    private static void construireExploration(Minecraft mc, Vec3 sudEst) {
        etape("pose de l'exploration", () -> {
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> preparerExploration(serveur));
        }, () -> explorationPosee, 2500, null);
        // Les étages se règlent une fois le joueur sur place et la zone en marche ; le courant d'un FE par tick vient de
        // la liste EXPLORATION_ALIMENTEE.
        etape("approche des mecanismes", () -> regarder(mc, MECANISMES[2], new Vec3(0.0, 2.6, 8.5), false), () -> true,
                2500, null);
        etape("reglage des etages", () -> {
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> reglerLesEtages(serveur.overworld()));
        }, () -> true, 1500, null);
        etape("vue cache", () -> regarder(mc, exploration, sudEst, false), () -> true, 1500, "cache_seule.png");
        etape("vue cache dessus", () -> regarder(mc, exploration, new Vec3(0.4, 1.9, 0.7), false), () -> true, 1500,
                "cache_dessus.png");
        String[] etatsRecepteur = {"sans_courant", "ecoute", "message"};
        for (int i = 0; i < 3; i++) {
            int k = i;
            etape("vue recepteur " + etatsRecepteur[k], () -> regarder(mc, RECEPTEURS[k], sudEst, false), () -> true,
                    1500, "recepteur_" + etatsRecepteur[k] + ".png");
        }
        etape("vue recepteurs les trois", () -> regarder(mc, RECEPTEURS[1], new Vec3(0.0, 2.2, 5.5), false),
                () -> true, 1500, "recepteurs_les_trois.png");
        for (int i = 0; i < 4; i++) {
            int k = i;
            etape("vue mecanisme " + k, () -> regarder(mc, MECANISMES[k], sudEst, false), () -> true, 1500,
                    "mecanisme_etage_" + k + ".png");
        }
        etape("vue mecanisme actif", () -> regarder(mc, MECANISMES[4], new Vec3(2.4, 1.3, 3.0), false), () -> true,
                2500, "mecanisme_actif_et_lampe.png");
        etape("vue mecanisme actif nuit", () -> regarder(mc, MECANISMES[4], new Vec3(2.4, 1.3, 3.0), true),
                () -> true, 2500, "mecanisme_actif_et_lampe_nuit.png");
        etape("vue mecanismes les cinq", () -> regarder(mc, MECANISMES[2], new Vec3(0.0, 2.6, 8.5), false),
                () -> true, 1500, "mecanismes_les_cinq.png");
        etape("vue mecanismes les cinq nuit", () -> regarder(mc, MECANISMES[2], new Vec3(0.0, 2.6, 8.5), true),
                () -> true, 1500, "mecanismes_les_cinq_nuit.png");
        etape("vue cache mur", () -> regarder(mc, cacheMur, new Vec3(1.1, 1.3, 1.6), false), () -> true, 1500,
                "cache_contre_un_mur.png");
        etape("vue recepteur mur", () -> regarder(mc, recepteurMur, new Vec3(1.1, 1.3, 1.6), false), () -> true,
                1500, "recepteur_contre_un_mur.png");
        etape("vue mecanisme mur", () -> regarder(mc, mecanismeMur, new Vec3(1.1, 1.3, 1.6), false), () -> true,
                1500, "mecanisme_contre_un_mur.png");
        etape("ecran recepteur", () -> {
            fermer(mc);
            ouvrir(mc, RECEPTEURS[3]);
        }, () -> mc.screen instanceof ReceiverScreen, 1500, "ecran_recepteur.png");
        etape("fin de l'ecran", () -> fermer(mc), () -> true, 300, null);
        // La grotte : sans lampe, puis avec, de face, de dos et à la première personne.
        etape("grotte sans lampe", () -> regarderGrotte(mc, false, CameraType.THIRD_PERSON_FRONT), () -> true, 2500,
                "grotte_sans_lampe_face.png");
        etape("grotte lampe face", () -> regarderGrotte(mc, true, CameraType.THIRD_PERSON_FRONT), () -> true, 3000,
                "grotte_lampe_portee_face.png");
        etape("grotte lampe dos", () -> regarderGrotte(mc, true, CameraType.THIRD_PERSON_BACK), () -> true, 3000,
                "grotte_lampe_portee_dos.png");
        etape("grotte lampe premiere personne", () -> regarderGrotte(mc, true, CameraType.FIRST_PERSON), () -> true,
                3000, "grotte_lampe_premiere_personne.png");
        etape("fin de la grotte", () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.HEAD,
                    ItemStack.EMPTY));
        }, () -> true, 800, null);
        commerce(mc);
        construireTresor(mc);
        construireQuetes(mc);
    }

    // ================================================================================================
    // Le commerce (lot E2) : l'étal vu du dehors, l'échange, la facture, la grille des objets
    // ================================================================================================

    /** Les objets du lot E2 à côté de leurs équivalents de Minecraft : établi, livre, papier, livre et plume, coffre, tonneau. */
    private static final String[] COMMERCE = {"market_stall", "minecraft:crafting_table", "invoice_book", "minecraft:book",
        "invoice", "minecraft:paper", "receipt", "minecraft:writable_book", "safe", "minecraft:chest", "purse",
        "minecraft:barrel"};

    /** L'angle des étals vus du dehors, et celui de l'échange : loin des zones des lots précédents. */
    private static BlockPos zoneCommerce;
    private static boolean commercePose;
    private static boolean echangePret;
    private static JoueurDeTest autreJoueur;
    private static final java.util.UUID EMETTEUR = java.util.UUID.nameUUIDFromBytes("Abcdefghijklmnop".getBytes(
            java.nio.charset.StandardCharsets.UTF_8));
    private static ItemStack factureDue = ItemStack.EMPTY;
    private static ItemStack factureAutre = ItemStack.EMPTY;
    private static ItemStack recuEchange = ItemStack.EMPTY;
    private static ItemStack recuFacture = ItemStack.EMPTY;
    private static ItemStack recuMain = ItemStack.EMPTY;
    private static ItemStack recuFerme = ItemStack.EMPTY;

    private static void commerce(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        etape("commerce pose", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            zoneCommerce = origine.offset(24, 0, 230);
            // Le bavardage du serveur (arrivée du second joueur, progrès) n'a rien à faire sur une capture.
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            serveur.execute(() -> preparerCommerce(serveur));
        }, () -> commercePose, 3000, null);
        // L'étal contre un mur (pierre au nord, à l'est, à l'ouest et au-dessus), de jour, de nuit, de face.
        etape("etal mur jour", () -> regarder(mc, zoneCommerce, new Vec3(0.7, 0.55, 1.25), false), () -> true, 3000,
                "etal_contre_un_mur_jour.png");
        etape("etal mur nuit", () -> regarder(mc, zoneCommerce, new Vec3(0.7, 0.55, 1.25), true), () -> true, 1500,
                "etal_contre_un_mur_nuit.png");
        etape("etal mur face jour", () -> regarder(mc, zoneCommerce, new Vec3(0.0, 0.5, 1.15), false), () -> true, 1500,
                "etal_contre_un_mur_face.png");
        etape("etal mur gauche", () -> regarder(mc, zoneCommerce, new Vec3(-0.75, 0.5, 1.2), false), () -> true, 1500,
                "etal_contre_un_mur_gauche.png");
        // Le comptoir de près, du dessus : les trois offres posées, la quatrième ne se montre pas.
        etape("etal comptoir", () -> regarder(mc, zoneCommerce.offset(8, 0, 0), new Vec3(0.0, 0.45, 0.95), false),
                () -> true, 1500, "etal_comptoir_trois_offres.png");
        // Seul : trois quarts, de l'arrière, du dessus, de nuit.
        etape("etal seul", () -> regarder(mc, zoneCommerce.offset(8, 0, 0), new Vec3(1.3, 1.0, 1.9), false), () -> true, 2000, "etal_seul_jour.png");
        etape("etal seul nuit", () -> regarder(mc, zoneCommerce.offset(8, 0, 0), new Vec3(1.3, 1.0, 1.9), true), () -> true, 1500, "etal_seul_nuit.png");
        etape("etal seul arriere", () -> regarder(mc, zoneCommerce.offset(8, 0, 0), new Vec3(1.2, 1.0, -2.0), false), () -> true, 1500,
                "etal_seul_arriere.png");
        etape("etal seul dessus", () -> regarder(mc, zoneCommerce.offset(8, 0, 0), new Vec3(0.2, 2.8, 0.9), false), () -> true, 1500,
                "etal_seul_dessus.png");
        etape("etal seul dessous", () -> regarder(mc, zoneCommerce.offset(8, 0, 0), new Vec3(0.3, -0.35, 1.2), false), () -> true, 1500,
                "etal_seul_dessous.png");
        // Les quatre orientations : sud, est, nord, ouest, côte à côte.
        etape("etal quatre orientations", () -> regarder(mc, zoneCommerce.offset(18, 0, 0), new Vec3(0.0, 2.6, 6.4), false),
                () -> true, 2000, "etal_quatre_orientations.png");
        etape("etal quatre orientations nuit", () -> regarder(mc, zoneCommerce.offset(18, 0, 0), new Vec3(0.0, 2.6, 6.4),
                true), () -> true, 1500, "etal_quatre_orientations_nuit.png");
        etal(mc);
        echange(mc);
        facture(mc);
        etape("grille du commerce", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.setScreen(new Grille(COMMERCE, 2));
        }, () -> true, 800, "commerce_grille.png");
        etape("fin du commerce", () -> {
            fermer(mc);
            serveur.execute(() -> {
                if (autreJoueur != null && autreJoueur.connecte()) {
                    autreJoueur.quitter();
                }
            });
        }, () -> true, 500, null);
    }

    /** Pose un étal du joueur, garni de quatre offres (seules les trois premières se voient sur le comptoir). */
    private static void poserEtal(ServerLevel monde, ServerPlayer joueur, BlockPos p, Direction face) {
        monde.setBlock(p, EconomyRegistry.MARKET_STALL.get().defaultBlockState()
                .setValue(dev.oneforallsurvival.economy.stall.MarketStallBlock.FACING, face), 3);
        if (!(monde.getBlockEntity(p) instanceof dev.oneforallsurvival.economy.stall.MarketStallBlockEntity be)) {
            throw new IllegalStateException("pas d'étal en " + p);
        }
        be.setOwner(joueur.getUUID(), joueur.getGameProfile().getName());
        be.setOffer(0, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.BREAD), 4, 30L));
        be.setOffer(1, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.DIAMOND_SWORD), 1, 1_200L));
        be.setOffer(2, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.COBBLESTONE), 64, 90L));
        be.setOffer(3, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.EMERALD), 2, 777L));
    }

    private static void preparerCommerce(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos z = zoneCommerce;
        // Le sol : de l'herbe plane partout, rien à déblayer. Les étals, de l'ouest à l'est.
        poserEtal(monde, joueur, z, Direction.SOUTH);
        for (BlockPos mur : List.of(z.north(), z.east(), z.west())) {
            monde.setBlock(mur, Blocks.STONE.defaultBlockState(), 3);
            monde.setBlock(mur.above(), Blocks.STONE.defaultBlockState(), 3);
        }
        monde.setBlock(z.above(), Blocks.STONE.defaultBlockState(), 3);
        poserEtal(monde, joueur, z.offset(8, 0, 0), Direction.SOUTH);
        // Une lanterne de chaque côté du premier et du second : la nuit, les offres se voient sans être au grand jour.
        for (BlockPos l : List.of(z.offset(-2, 0, 2), z.offset(2, 0, 2), z.offset(6, 0, 2), z.offset(10, 0, 2),
                z.offset(18, 0, 3))) {
            monde.setBlock(l, Blocks.LANTERN.defaultBlockState(), 3);
        }
        Direction[] faces = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
        for (int i = 0; i < faces.length; i++) {
            poserEtal(monde, joueur, z.offset(12 + 4 * i, 0, 0), faces[i]);
        }
        commercePose = true;
    }

    // ---- L'échange -----------------------------------------------------------------------------

    private static void echange(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        etape("echange preparer", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            serveur.execute(() -> preparerEchange(serveur));
        }, () -> echangePret, 2500, null);
        // L'autre a proposé, le joueur accepte : il est le second côté. L'autre a posé ses objets, ses pièces et sa
        // bourse, coché le reçu et confirmé.
        etape("echange ouvrir", () -> serveur.execute(() -> ouvrirEchange(serveur, false)),
                () -> mc.screen instanceof dev.oneforallsurvival.economy.client.TradeScreen, 2000, null);
        etape("echange rempli", () -> serveur.execute(() -> remplirEchange(serveur)), () -> true, 1800,
                "echange_l_autre_a_confirme.png");
        etape("echange confirme", () -> serveur.execute(() -> confirmerEchange(serveur)), () -> true, 1500,
                "echange_vous_avez_confirme.png");
        etape("echange ferme", () -> serveur.execute(() -> fermerEchange(serveur)), () -> mc.screen == null, 1200, null);
        // Les rôles échangés : le joueur propose, l'autre accepte ; douze cases de chaque côté, noms longs.
        etape("echange inverse ouvrir", () -> serveur.execute(() -> ouvrirEchange(serveur, true)),
                () -> mc.screen instanceof dev.oneforallsurvival.economy.client.TradeScreen, 2000, null);
        etape("echange inverse rempli", () -> serveur.execute(() -> remplirEchangePlein(serveur)), () -> true, 1800,
                "echange_douze_cases.png");
        etape("echange inverse ferme", () -> serveur.execute(() -> fermerEchange(serveur)), () -> mc.screen == null, 1200,
                null);
    }

    private static ItemStack nomme(ItemStack pile, String nom) {
        pile.setHoverName(Component.literal(nom));
        return pile;
    }

    private static final int[] EN_DOUZE = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    private static void preparerEchange(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer a = serveur.getPlayerList().getPlayers().get(0);
        BlockPos t = origine.offset(24, 0, 260);
        a.setNoGravity(true);
        a.teleportTo(monde, t.getX() + 0.5, t.getY(), t.getZ() + 0.5, 0F, 0F);
        if (autreJoueur == null) {
            autreJoueur = JoueurDeTest.rejoindre(monde, "Abcdefghijklmnop", new Vec3(t.getX() + 1.5, t.getY(), t.getZ() + 0.5));
        }
        ServerPlayer b = autreJoueur.joueur();
        b.setNoGravity(true);
        b.teleportTo(monde, t.getX() + 1.5, t.getY(), t.getZ() + 0.5, 0F, 0F);
        a.getInventory().clearContent();
        b.getInventory().clearContent();
        // A : une épée au nom long, des pains, une bourse pleine, de l'or, des émeraudes.
        a.getInventory().items.set(0, nomme(new ItemStack(Items.DIAMOND_SWORD), "Excalibur, lame du roi d'Avalon"));
        a.getInventory().items.set(1, new ItemStack(Items.BREAD, 64));
        ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
        remplirBourse(bourse);
        a.getInventory().items.set(2, bourse);
        a.getInventory().items.set(3, new ItemStack(Coin.GOLD.item(), 20));
        a.getInventory().items.set(4, new ItemStack(Coin.EMERALD.item(), 9));
        a.getInventory().items.set(5, nomme(new ItemStack(Items.WRITTEN_BOOK), "Le grand livre des comptes du port"));
        b.getInventory().items.set(0, new ItemStack(Coin.RUBY.item(), 3));
        ItemStack bourseB = new ItemStack(EconomyRegistry.PURSE.get());
        remplirBourse(bourseB);
        b.getInventory().items.set(1, bourseB);
        b.getInventory().items.set(2, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3));
        b.getInventory().items.set(3, nomme(new ItemStack(Items.IRON_INGOT, 64), "Lingots de la forge de Saint-Aubin"));
        b.getInventory().items.set(4, new ItemStack(Coin.SILVER.item(), 33));
        // Les infobulles : une facture et ses trois reçus.
        long maintenant = System.currentTimeMillis();
        long plafond = dev.oneforallsurvival.economy.EconomyConfig.invoiceMaxAmount();
        String motif = "Réparation complète de la toiture de la grande grange du nord, bois et clous fournis par l'atelier";
        motif = motif.substring(0, Math.min(motif.length(), dev.oneforallsurvival.economy.EconomyConfig.invoiceReasonMax()));
        var due = new dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice(java.util.UUID.fromString(
                "a1b2c3d4-0000-4000-8000-000000000001"), EMETTEUR, "Abcdefghijklmnop", a.getUUID(),
                a.getGameProfile().getName(), plafond, motif, maintenant - 3_600_000L,
                dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status.DUE, 0L);
        factureDue = dev.oneforallsurvival.economy.invoice.InvoiceItem.of(due);
        factureAutre = dev.oneforallsurvival.economy.invoice.InvoiceItem.of(new dev.oneforallsurvival.economy.invoice.InvoiceRegistry
                .Invoice(java.util.UUID.fromString("a1b2c3d4-0000-4000-8000-000000000002"), a.getUUID(),
                a.getGameProfile().getName(), EMETTEUR, "Abcdefghijklmnop", 12_345L, "Trois sacs de blé", maintenant - 600_000L,
                dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status.DUE, 0L));
        var payee = new dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice(due.id(), due.issuer(), due.issuerName(),
                due.debtor(), due.debtorName(), due.amount(), due.reason(), due.issuedAt(),
                dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status.PAID, maintenant);
        recuFacture = dev.oneforallsurvival.economy.Receipt.forInvoice(payee,
                dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status.PAID);
        recuMain = dev.oneforallsurvival.economy.Receipt.forInvoice(payee,
                dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status.SETTLED_BY_HAND);
        recuFerme = dev.oneforallsurvival.economy.Receipt.forClosedInvoice(factureDue);
        // Un échange plein : douze piles de chaque côté, dont deux bourses pleines.
        List<ItemStack> donne = new ArrayList<>();
        List<ItemStack> recu = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            donne.add(i == 2 || i == 9 ? bourse.copy() : nomme(new ItemStack(Items.DIAMOND_SWORD), "Excalibur " + i));
            recu.add(i == 4 || i == 7 ? bourseB.copy() : new ItemStack(Coin.GOLD.item(), 20 + i));
        }
        recuEchange = dev.oneforallsurvival.economy.Receipt.forTrade(a, b, donne, recu, maintenant);
        echangePret = true;
    }

    /** Ouvre la fenêtre : {@code leJoueurPropose} faux, c'est l'autre qui propose (le joueur est le second côté). */
    private static void ouvrirEchange(MinecraftServer serveur, boolean leJoueurPropose) {
        ServerPlayer a = serveur.getPlayerList().getPlayers().get(0);
        ServerPlayer b = autreJoueur.joueur();
        ServerPlayer de = leJoueurPropose ? a : b;
        ServerPlayer vers = leJoueurPropose ? b : a;
        var r1 = dev.oneforallsurvival.economy.trade.TradeService.request(de, vers);
        var r2 = dev.oneforallsurvival.economy.trade.TradeService.accept(vers, de);
        LOG.info("[visuels] échange : demande {}, acceptation {}", r1, r2);
    }

    private static void remplirEchange(MinecraftServer serveur) {
        ServerPlayer a = serveur.getPlayerList().getPlayers().get(0);
        ServerPlayer b = autreJoueur.joueur();
        var session = dev.oneforallsurvival.economy.trade.TradeService.sessionOf(a.getUUID());
        if (session == null) {
            throw new IllegalStateException("aucun échange ouvert");
        }
        for (int inv : new int[] {0, 1, 2, 5}) {
            ((dev.oneforallsurvival.economy.trade.TradeMenu) a.containerMenu).clicked(
                    dev.oneforallsurvival.economy.trade.TradeMenu.slotOfInventory(inv), 0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE, a);
        }
        for (int inv : new int[] {0, 1, 2, 3, 4}) {
            ((dev.oneforallsurvival.economy.trade.TradeMenu) b.containerMenu).clicked(
                    dev.oneforallsurvival.economy.trade.TradeMenu.slotOfInventory(inv), 0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE, b);
        }
        // Les pièces de A passent par les boutons +1 / +10 : de la bourse d'abord, puis de l'inventaire.
        LOG.info("[visuels] échange : A verse {} {}", session.addCoins(a, Coin.GOLD, 10), session.addCoins(a, Coin.EMERALD, 4));
        session.toggleReceipt(b, session.version());
        LOG.info("[visuels] échange : B confirme -> {}", session.confirm(b, session.version()));
    }

    private static void confirmerEchange(MinecraftServer serveur) {
        ServerPlayer a = serveur.getPlayerList().getPlayers().get(0);
        var session = dev.oneforallsurvival.economy.trade.TradeService.sessionOf(a.getUUID());
        // Un changement du joueur efface la confirmation de l'autre ; il confirme ensuite, l'autre attend.
        boolean verse = session.addCoins(a, Coin.GOLD, 1);
        if (!verse || session.confirmed(autreJoueur.uuid())) {
            throw new IllegalStateException("le versement devait effacer la confirmation de l'autre : " + verse);
        }
        LOG.info("[visuels] échange : A confirme -> {}", session.confirm(a, session.version()));
    }

    private static void fermerEchange(MinecraftServer serveur) {
        serveur.getPlayerList().getPlayers().get(0).closeContainer();
    }

    /** Douze cases de chaque côté, aux noms longs, le reçu coché, l'autre confirmé. */
    private static void remplirEchangePlein(MinecraftServer serveur) {
        ServerPlayer a = serveur.getPlayerList().getPlayers().get(0);
        ServerPlayer b = autreJoueur.joueur();
        var session = dev.oneforallsurvival.economy.trade.TradeService.sessionOf(a.getUUID());
        if (session == null) {
            throw new IllegalStateException("aucun échange ouvert");
        }
        Item[] objets = {Items.DIAMOND_SWORD, Items.GOLDEN_APPLE, Items.IRON_INGOT, Items.WHEAT, Items.NETHERITE_INGOT,
            Items.ENCHANTED_GOLDEN_APPLE, Items.EMERALD, Items.COOKED_BEEF, Items.SPYGLASS, Items.SADDLE, Items.NAME_TAG,
            Items.MAP};
        for (int i : EN_DOUZE) {
            ItemStack pile = nomme(new ItemStack(objets[i], i % 3 == 0 ? 1 : 16), "Marchandise rare de la maison Abcdefghijklmnop n° " + i);
            session.offer(a.getUUID()).setItem(i, pile.copy());
            session.offer(b.getUUID()).setItem(i, i % 4 == 0 ? new ItemStack(Coin.GOLD.item(), 64) : pile.copy());
        }
        ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
        remplirBourse(bourse);
        session.offer(a.getUUID()).setItem(11, bourse);
        session.offer(b.getUUID()).setItem(11, bourse.copy());
        session.toggleReceipt(a, session.version());
        LOG.info("[visuels] échange plein : B confirme -> {}", session.confirm(b, session.version()));
    }

    // ---- La facture ----------------------------------------------------------------------------

    private static void facture(Minecraft mc) {
        etape("facture formulaire", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            var ecran = new dev.oneforallsurvival.economy.client.InvoiceFormScreen(EMETTEUR, "Abcdefghijklmnop");
            mc.setScreen(ecran);
            List<net.minecraft.client.gui.components.EditBox> champs = new ArrayList<>();
            for (var enfant : ecran.children()) {
                if (enfant instanceof net.minecraft.client.gui.components.EditBox champ) {
                    champs.add(champ);
                }
            }
            if (champs.size() != 2) {
                throw new IllegalStateException("deux champs attendus, vu " + champs.size());
            }
            champs.get(0).setValue(Long.toString(dev.oneforallsurvival.economy.EconomyConfig.invoiceMaxAmount()));
            String motif = "Réparation complète de la toiture de la grande grange du nord, bois et clous fournis par l'atelier";
            champs.get(1).setValue(motif.substring(0, Math.min(motif.length(),
                    dev.oneforallsurvival.economy.EconomyConfig.invoiceReasonMax())));
            champs.get(1).moveCursorToStart();
        }, () -> mc.screen instanceof dev.oneforallsurvival.economy.client.InvoiceFormScreen, 1200, "facture_formulaire_rempli.png");
        etape("facture formulaire court", () -> {
            var ecran = new dev.oneforallsurvival.economy.client.InvoiceFormScreen(EMETTEUR, "Abcdefghijklmnop");
            mc.setScreen(ecran);
            int i = 0;
            for (var enfant : ecran.children()) {
                if (enfant instanceof net.minecraft.client.gui.components.EditBox champ) {
                    champ.setValue(i++ == 0 ? "1275" : "Trois sacs de blé");
                }
            }
        }, () -> mc.screen instanceof dev.oneforallsurvival.economy.client.InvoiceFormScreen, 1000,
                "facture_formulaire_court.png");
        etape("facture ecran debiteur", () -> dev.oneforallsurvival.economy.client.InvoiceScreen.open(factureDue, 0),
                () -> mc.screen instanceof dev.oneforallsurvival.economy.client.InvoiceScreen, 1200, "facture_ecran_debiteur.png");
        etape("facture ecran autre", () -> dev.oneforallsurvival.economy.client.InvoiceScreen.open(factureAutre, 0),
                () -> mc.screen instanceof dev.oneforallsurvival.economy.client.InvoiceScreen, 1200,
                "facture_ecran_emetteur.png");
        etape("infobulle facture", () -> mc.setScreen(new Infobulle(factureDue)), () -> true, 800, "facture_infobulle.png");
        etape("infobulle recu echange", () -> mc.setScreen(new Infobulle(recuEchange)), () -> true, 800,
                "recu_echange_infobulle.png");
        etape("infobulle recu facture", () -> mc.setScreen(new Infobulle(recuFacture)), () -> true, 800,
                "recu_facture_infobulle.png");
        etape("infobulle recu main", () -> mc.setScreen(new Infobulle(recuMain)), () -> true, 800,
                "recu_facture_main_infobulle.png");
        etape("infobulle recu ferme", () -> mc.setScreen(new Infobulle(recuFerme)), () -> true, 800,
                "recu_facture_close_infobulle.png");
        etape("fin de la facture", () -> fermer(mc), () -> true, 300, null);
    }

    // ================================================================================================
    // L'étal (lot E2) : la vitrine et la gestion, avec les prix et les noms les plus longs permis
    // ================================================================================================

    private static BlockPos etal;

    /**
     * Un étal du joueur, garni pour le pire cas d'affichage : le prix le plus long sous le plafond des factures
     * (99 999 999 cuivres = 9 999 rubis 9 émeraudes 9 or 9 argent 9 cuivres), le plafond lui-même (10 000 rubis), un
     * nom d'objet long, six offres (la liste défile), des rachats dans chaque état, un registre aux noms de 16 lettres.
     * Les vues de la gestion se choisissent par de vrais clics sur l'écran ({@code mouseClicked}).
     */
    private static void etal(Minecraft mc) {
        etape("etal aller", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            etal = origine.offset(24, 0, 170);
            regarder(mc, etal, new Vec3(0.0, 0.6, 2.4), false);
        }, () -> true, 2500, null);
        etape("etal poser et vitrine", () -> {
            mc.options.hideGui = false;
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> {
                ServerLevel monde = serveur.overworld();
                ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                garnirEtal(monde, joueur);
                dev.oneforallsurvival.economy.stall.MarketStallBlock.openShop(joueur, etal);
            });
        }, () -> mc.screen instanceof dev.oneforallsurvival.economy.client.StallShopScreen, 1500, "etal_vitrine_ventes.png");
        etape("etal vitrine defile", () -> clicEcran(mc, -1, 0, 0, -9.0), () -> true, 800, "etal_vitrine_ventes_defilee.png");
        etape("etal vitrine rachats", () -> clicEcran(mc, 8 + 79 + 2 + 10, 16 + 5, 0, 0),
                () -> true, 800, "etal_vitrine_rachats.png");
        etape("etal gestion", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> dev.oneforallsurvival.economy.stall.MarketStallBlock.openManage(
                    serveur.getPlayerList().getPlayers().get(0), etal));
        }, () -> mc.screen instanceof dev.oneforallsurvival.economy.client.StallOwnerScreen, 1200, "etal_gestion_stock.png");
        // Onglets : x = 8 + i * (38 + 2), y = 18 ; cases d'offre : x = 7 + col * 18, y = 50.
        etape("etal gestion offres", () -> clicEcran(mc, 8 + 40 + 10, 18 + 5, 0, 0), () -> true, 600, null);
        etape("etal offre longue", () -> clicEcran(mc, 7 + 18 + 9, 50 + 9, 0, 0), () -> true, 800, "etal_gestion_offres.png");
        etape("etal rachats", () -> clicEcran(mc, 8 + 64 + 10, 36 + 5, 0, 0), () -> true, 300, null);
        etape("etal rachat long", () -> clicEcran(mc, 7 + 18 + 9, 50 + 9, 0, 0), () -> true, 800,
                "etal_gestion_rachats.png");
        etape("etal registre", () -> clicEcran(mc, 8 + 3 * 40 + 10, 18 + 5, 0, 0), () -> true, 1500,
                "etal_gestion_registre.png");
        etape("etal registre defile", () -> clicEcran(mc, -1, 0, 0, -3.0), () -> true, 800, "etal_gestion_registre_defile.png");
        // La caisse : l'écran commun du coffre-fort, ouvert par le vrai clic sur l'onglet depuis la gestion.
        etape("etal gestion pour la caisse", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> dev.oneforallsurvival.economy.stall.MarketStallBlock.openManage(
                    serveur.getPlayerList().getPlayers().get(0), etal));
        }, () -> mc.screen instanceof dev.oneforallsurvival.economy.client.StallOwnerScreen, 1000, null);
        etape("etal caisse", () -> clicEcran(mc, 8 + 2 * 40 + 10, 18 + 5, 0, 0),
                () -> mc.screen instanceof dev.oneforallsurvival.economy.client.StallCashScreen, 1500,
                "etal_gestion_caisse.png");
        etape("fin de l'etal", () -> fermer(mc), () -> true, 300, null);
    }

    /**
     * Un clic gauche (ou, {@code molette} non nulle, un tour de molette) sur l'écran ouvert, en coordonnées du cadre ;
     * {@code x} négatif : au milieu de la liste. La souris de la machine n'est pas touchée.
     */
    private static void clicEcran(Minecraft mc, int x, int y, int bouton, double molette) {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> ecran)) {
            throw new IllegalStateException("aucun écran ouvert pour le clic : " + mc.screen);
        }
        double ax = ecran.getGuiLeft() + (x < 0 ? 80 : x);
        double ay = ecran.getGuiTop() + (x < 0 ? 70 : y);
        LOG.info("[visuels] ecran {} : cadre {}x{} en ({}, {}) dans une interface de {}x{}", ecran.getClass().getSimpleName(),
                ecran.getXSize(), ecran.getYSize(), ecran.getGuiLeft(), ecran.getGuiTop(),
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        if (molette != 0) {
            ecran.mouseScrolled(ax, ay, molette);
        } else {
            ecran.mouseClicked(ax, ay, bouton);
        }
    }

    private static void garnirEtal(ServerLevel monde, ServerPlayer joueur) {
        var etat = EconomyRegistry.MARKET_STALL.get().defaultBlockState()
                .setValue(dev.oneforallsurvival.economy.stall.MarketStallBlock.FACING, Direction.SOUTH);
        monde.setBlock(etal, etat, 3);
        if (!(monde.getBlockEntity(etal) instanceof dev.oneforallsurvival.economy.stall.MarketStallBlockEntity be)) {
            throw new IllegalStateException("pas d'étal en " + etal);
        }
        be.setOwner(joueur.getUUID(), joueur.getGameProfile().getName());
        ItemStack epee = new ItemStack(Items.DIAMOND_SWORD);
        epee.setHoverName(net.minecraft.network.chat.Component.literal("Excalibur, lame du roi d'Avalon"));
        be.stock().setStackInSlot(0, new ItemStack(Items.BREAD, 64));
        be.stock().setStackInSlot(1, epee.copy());
        be.stock().setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 64));
        be.stock().setStackInSlot(3, new ItemStack(Items.WHEAT, 64));
        for (Coin c : PIECES) {
            be.addCash(c, new long[] {37, 12, 8, 3, 9_999}[c.ordinal()]);
        }
        long plusLong = 99_999_999L;
        be.setOffer(0, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.BREAD), 4, 30L));
        be.setOffer(1, new dev.oneforallsurvival.economy.stall.StallOffer(epee, 1, plusLong));
        be.setOffer(2, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.COBBLESTONE), 64, 100_000_000L));
        be.setOffer(3, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.WHEAT), 16, 1L));
        be.setOffer(4, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.IRON_INGOT), 8, 12_345L));
        be.setOffer(5, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 1, plusLong));
        be.setOffer(6, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.EMERALD), 2, 777L));
        be.setOffer(7, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.NETHERITE_INGOT), 9, plusLong));
        ItemStack baton = new ItemStack(Items.STICK);
        baton.setHoverName(net.minecraft.network.chat.Component.literal("Bâton de marche du vieux berger de la vallée"));
        be.setOffer(8, new dev.oneforallsurvival.economy.stall.StallOffer(baton, 16, 100_000_000L));
        be.stock().setStackInSlot(4, new ItemStack(Items.IRON_INGOT, 64));
        be.stock().setStackInSlot(5, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1));
        be.stock().setStackInSlot(6, new ItemStack(Items.NETHERITE_INGOT, 64));
        be.setBuyback(0, new dev.oneforallsurvival.economy.stall.StallBuyback(new ItemStack(Items.WHEAT), 8, 16L, 9_999));
        be.setBuyback(1, new dev.oneforallsurvival.economy.stall.StallBuyback(epee, 1, plusLong, 3));
        be.setBuyback(2, new dev.oneforallsurvival.economy.stall.StallBuyback(new ItemStack(Items.CARROT), 64, 64L, 1));
        // Le registre : chaque genre, des noms de 16 lettres, les montants les plus longs.
        long t = System.currentTimeMillis();
        java.util.UUID qui = java.util.UUID.nameUUIDFromBytes("Abcdefghijklmnop".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var K = dev.oneforallsurvival.economy.stall.StallLedger.Kind.values();
        for (int i = 0; i < 200; i++) {
            var genre = K[i % K.length];
            boolean objet = genre != dev.oneforallsurvival.economy.stall.StallLedger.Kind.CASH_IN
                    && genre != dev.oneforallsurvival.economy.stall.StallLedger.Kind.CASH_OUT;
            long montant = genre == dev.oneforallsurvival.economy.stall.StallLedger.Kind.SALE
                    || genre == dev.oneforallsurvival.economy.stall.StallLedger.Kind.BUYBACK
                    || !objet ? (i % 2 == 0 ? plusLong : 30L) : 0L;
            be.ledger().add(new dev.oneforallsurvival.economy.stall.StallLedger.Line(t - (200 - i) * 60_000L, qui,
                    i % 3 == 0 ? "Abcdefghijklmnop" : joueur.getGameProfile().getName(), genre,
                    objet ? (i % 2 == 0 ? "minecraft:diamond_sword" : "minecraft:bread") : "", objet ? 64 : 0, montant));
        }
    }

    /** Le joueur au centre de la grotte, de nuit, la lampe chargée sur la tête ou non, vu par la caméra demandée. */
    private static void regarderGrotte(Minecraft mc, boolean lampe, CameraType camera) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        mc.setScreen(null);
        mc.options.hideGui = true;
        mc.gameRenderer.setRenderHand(false);
        mc.options.setCameraType(camera);
        float yaw = camera == CameraType.THIRD_PERSON_FRONT ? 0F : 180F;
        BlockPos c = grotte;
        serveur.execute(() -> {
            ServerLevel monde = serveur.overworld();
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            monde.setDayTime(18000);
            ItemStack casque = ItemStack.EMPTY;
            if (lampe) {
                casque = new ItemStack(ExploreRegistry.HEADLAMP.get());
                HeadlampItem.store(casque, ExploreConfig.headlampCapacity());
            }
            joueur.setItemSlot(EquipmentSlot.HEAD, casque);
            joueur.setNoGravity(true);
            joueur.teleportTo(monde, c.getX() + 4.5, c.getY(), c.getZ() + 4.5, yaw, 0F);
        });
    }

    /**
     * Étages 1 et 2 : une progression à 40 % et à 70 % de la durée, donc strictement en dessous (étage = progression x 3 /
     * durée, arrondi vers le bas : 1 puis 2). L'étage 3 n'existe qu'à la progression complète, au moment où le mécanisme
     * devient actif : il n'a donc aucune durée stable. Pour pouvoir le regarder à côté de l'état actif, son bloc n'est pas
     * alimenté et {@link #onServerTick} le force à l'étage 3, inactif, à chaque tic (affichage de contrôle des textures).
     */
    private static void reglerLesEtages(ServerLevel monde) {
        int[] parMille = {0, 400, 700, 0};
        for (int i = 1; i < 4; i++) {
            if (monde.getBlockEntity(MECANISMES[i]) instanceof MechanismBlockEntity be) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Threshold", 1);
                tag.putInt("Duration", 100_000);
                tag.putInt("Progress", (int) ((long) 100_000 * parMille[i] / 1000));
                tag.putBoolean("Active", false);
                be.load(tag);
            }
        }
        etage3Force = true;
    }

    private static void preparerExploration(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        // Loin à l'ouest, au-delà des 6 chunks affichés autour de tout ce que posent les lots 2 à 6 (le lot 6 est à
        // l'est) : aucune capture d'un lot précédent ne voit celui-ci, ni l'inverse.
        BlockPos e = origine.offset(-110, 0, -10);
        exploration = e;
        monde.setBlock(e, ExploreRegistry.CACHE.get().defaultBlockState(), 3);
        for (int i = 0; i < 4; i++) {
            BlockPos p = e.offset(4 * i, 0, -6);
            RECEPTEURS[i] = p;
            monde.setBlock(p, ExploreRegistry.RECEIVER.get().defaultBlockState(), 3);
            if (i != 0 && monde.getBlockEntity(p) instanceof ReceiverBlockEntity be) {
                be.energy().receiveEnergy(200, false);
                EXPLORATION_ALIMENTEE.add(p);
                if (i >= 2) {
                    be.deliver(new SignalMessage(1000L, 1, "Essai de reception, un deux, un deux."));
                    be.deliver(new SignalMessage(7300L, 1,
                            "Second essai, plus long : la ligne doit passer a la suivante sans deborder du cadre."));
                }
                if (i == 1) {
                    be.markRead();
                }
            }
        }
        for (int i = 0; i < 5; i++) {
            BlockPos p = e.offset(4 * i, 0, -12);
            MECANISMES[i] = p;
            monde.setBlock(p, ExploreRegistry.MECHANISM.get().defaultBlockState(), 3);
            if (monde.getBlockEntity(p) instanceof MechanismBlockEntity be) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Threshold", 1);
                tag.putInt("Duration", 100_000);
                tag.putBoolean("Active", i == 4);
                be.load(tag);
                be.setChanged();
                if (i >= 1 && i < 3) {
                    EXPLORATION_ALIMENTEE.add(p);
                }
            }
        }
        monde.setBlock(MECANISMES[4].east(), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
        cacheMur = e.offset(0, 0, -18);
        monde.setBlock(cacheMur, ExploreRegistry.CACHE.get().defaultBlockState(), 3);
        entourer(monde, cacheMur);
        recepteurMur = e.offset(4, 0, -18);
        monde.setBlock(recepteurMur, ExploreRegistry.RECEIVER.get().defaultBlockState(), 3);
        entourer(monde, recepteurMur);
        mecanismeMur = e.offset(8, 0, -18);
        monde.setBlock(mecanismeMur, ExploreRegistry.MECHANISM.get().defaultBlockState(), 3);
        entourer(monde, mecanismeMur);
        // La grotte : neuf blocs de côté, close de pierre de toutes parts, rien qui éclaire.
        grotte = e.offset(0, 0, 14);
        for (int x = -1; x <= 9; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = -1; z <= 9; z++) {
                    boolean interieur = x >= 0 && x <= 8 && y >= 0 && y <= 4 && z >= 0 && z <= 8;
                    monde.setBlock(grotte.offset(x, y, z),
                            interieur ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        explorationPosee = true;
    }

    /** Deux murs de pierre de deux blocs, au nord et à l'ouest du bloc, qui le touchent. */
    private static void entourer(ServerLevel monde, BlockPos p) {
        for (BlockPos mur : List.of(p.north(), p.west())) {
            monde.setBlock(mur, Blocks.STONE.defaultBlockState(), 3);
            monde.setBlock(mur.above(), Blocks.STONE.defaultBlockState(), 3);
        }
    }

    // ================================================================================================
    // Le trésor et le tableau de la mairie (lot E3)
    // ================================================================================================

    /** Les objets du lot E3 à côté de leurs équivalents : coffres et coffre-fort, panneau de chêne. */
    private static final String[] TRESOR = {"treasury", "minecraft:chest", "safe", "minecraft:ender_chest", "town_board",
        "minecraft:oak_sign"};

    /** Loin au sud (au-delà des 6 chunks affichés de tout ce qui précède). */
    private static BlockPos zoneTresor;
    private static volatile boolean tresorPose;
    /** L'écran du trésor ouvert, d'où partent les fenêtres du motif. */
    private static dev.oneforallsurvival.economy.client.TreasuryScreen ecranTresor;

    /** Les coupures du trésor de la démonstration : cuivre, argent, or, émeraude, rubis (le compte le plus long, 9 999). */
    private static final long[] TRESOR_PIECES = {37, 12, 8, 3, 9_999};

    /** Un motif de 80 caractères, le plus long permis (le plafond de {@code treasuryReasonMax}). */
    private static final String MOTIF_LONG = "Remboursement des materiaux du pont ouest et des planches de la forge du village";

    private static void construireTresor(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        etape("tresor pose", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            zoneTresor = origine.offset(24, 0, 320);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            serveur.execute(() -> preparerTresor(serveur));
        }, () -> tresorPose, 4000, null);
        // Le trésor seul : de face et de trois quarts, de jour et de nuit, puis dessus et derrière.
        vueTresor(mc, "tresor_seul_face_jour.png", 0, new Vec3(0.0, 0.4, 3.0), false, 3000);
        vueTresor(mc, "tresor_seul_trois_quarts_jour.png", 0, new Vec3(2.2, 1.3, 2.6), false, 1500);
        vueTresor(mc, "tresor_seul_face_nuit.png", 0, new Vec3(0.0, 0.4, 3.0), true, 1500);
        vueTresor(mc, "tresor_seul_trois_quarts_nuit.png", 0, new Vec3(2.2, 1.3, 2.6), true, 1500);
        vueTresor(mc, "tresor_seul_dessus.png", 0, new Vec3(0.3, 2.8, 1.2), false, 1500);
        vueTresor(mc, "tresor_seul_arriere.png", 0, new Vec3(1.6, 1.0, -2.4), false, 1500);
        // Contre un mur : pierre au nord, à l'est, à l'ouest et au-dessus.
        vueTresor(mc, "tresor_contre_un_mur_jour.png", 8, new Vec3(1.4, 1.1, 2.1), false, 1500);
        vueTresor(mc, "tresor_contre_un_mur_face.png", 8, new Vec3(0.0, 0.5, 1.6), false, 1500);

        // Le tableau (3 x 2) collé à un mur, de près et à quatre blocs du parchemin, avec les comptes les plus longs,
        // moyens, sans dette, vides. Le centre du panneau est 0,56 bloc au-dessus du centre de la maîtresse, la face
        // 0,125 bloc devant lui : « à 4 blocs » met l'œil à 4 blocs de la face.
        comptes(mc, "les plus longs", new dev.oneforallsurvival.economy.town.TownFinances.Snapshot(99_999_999L, 99_999_999L, 99));
        vueTresor(mc, "tableau_pire_pres.png", 16, new Vec3(0.0, 0.56, 2.2), false, 1800);
        vueTresor(mc, "tableau_pire_4_blocs.png", 16, new Vec3(0.0, 0.56, 4.125), false, 1500);
        vueTresor(mc, "tableau_pire_trois_quarts.png", 16, new Vec3(2.4, 1.0, 3.0), false, 1500);
        vueTresor(mc, "tableau_pire_nuit_4_blocs.png", 16, new Vec3(0.0, 0.56, 4.125), true, 1500);
        vueTresor(mc, "tableau_pire_6_blocs.png", 16, new Vec3(0.0, 0.8, 6.125), false, 1500);
        comptes(mc, "moyens", new dev.oneforallsurvival.economy.town.TownFinances.Snapshot(13_957L, 600L, 2));
        vueTresor(mc, "tableau_moyen_pres.png", 16, new Vec3(0.0, 0.56, 2.2), false, 1200);
        vueTresor(mc, "tableau_moyen_4_blocs.png", 16, new Vec3(0.0, 0.56, 4.125), false, 1500);
        comptes(mc, "sans dette", new dev.oneforallsurvival.economy.town.TownFinances.Snapshot(99_999_999L, 0L, 0));
        vueTresor(mc, "tableau_sans_dette_pres.png", 16, new Vec3(0.0, 0.56, 2.2), false, 1200);
        vueTresor(mc, "tableau_sans_dette_4_blocs.png", 16, new Vec3(0.0, 0.56, 4.125), false, 1500);
        comptes(mc, "vide", new dev.oneforallsurvival.economy.town.TownFinances.Snapshot(0L, 0L, 0));
        vueTresor(mc, "tableau_vide_pres.png", 16, new Vec3(0.0, 0.56, 2.2), false, 1200);
        vueTresor(mc, "tableau_vide_4_blocs.png", 16, new Vec3(0.0, 0.56, 4.125), false, 1500);
        comptes(mc, "rien recu", null);
        vueTresor(mc, "tableau_rien_recu_pres.png", 16, new Vec3(0.0, 0.56, 2.2), false, 1200);
        comptes(mc, "les plus longs, encore", new dev.oneforallsurvival.economy.town.TownFinances.Snapshot(99_999_999L, 99_999_999L, 99));
        // Les quatre orientations (sud, est, nord, ouest) vues d'en haut, puis chacune de face, puis un tableau seul.
        vueTresor(mc, "tableau_quatre_orientations.png", 33, new Vec3(0.0, 6.0, 8.0), false, 1800);
        vueTresor(mc, "tableau_face_sud.png", 24, new Vec3(0.0, 0.56, 2.2), false, 1500);
        vueTresor(mc, "tableau_face_est.png", 30, new Vec3(2.2, 0.56, 0.0), false, 1500);
        vueTresor(mc, "tableau_face_nord.png", 36, new Vec3(0.0, 0.56, -2.2), false, 1500);
        vueTresor(mc, "tableau_face_ouest.png", 42, new Vec3(-2.2, 0.56, 0.0), false, 1500);
        vueTresor(mc, "tableau_seul_trois_quarts.png", 50, new Vec3(2.6, 1.3, 3.2), false, 1500);
        vueTresor(mc, "tableau_seul_arriere.png", 50, new Vec3(-1.8, 1.2, -3.2), false, 1500);

        // L'écran du trésor (cadre, panneau Change, inventaire), la bulle d'un retrait, la fenêtre du motif.
        etape("tresor ecran", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            serveur.execute(() -> {
                ServerLevel monde = serveur.overworld();
                ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                monde.setDayTime(6000);
                BlockPos t = zoneTresor;
                joueur.teleportTo(monde, t.getX() + 0.5, t.getY(), t.getZ() + 3.5, 0F, 20F);
                dev.oneforallsurvival.economy.town.TreasuryBlock.open(joueur, t);
            });
        }, () -> mc.screen instanceof dev.oneforallsurvival.economy.client.TreasuryScreen, 1500, "tresor_ecran.png");
        bulle(mc, "row.gold.10", "tresor_ecran_bulle_retrait.png");
        etape("tresor motif plein", () -> {
            ecranTresor = (dev.oneforallsurvival.economy.client.TreasuryScreen) mc.screen;
            mc.setScreen(new dev.oneforallsurvival.economy.client.TreasuryReasonScreen(ecranTresor,
                    new dev.oneforallsurvival.economy.CoinRequest(dev.oneforallsurvival.economy.CoinRequest.WITHDRAW,
                            Coin.RUBY.ordinal(), 64, false), MOTIF_LONG));
        }, () -> mc.screen instanceof dev.oneforallsurvival.economy.client.TreasuryReasonScreen, 1000,
                "tresor_motif_plein.png");
        etape("tresor motif vide", () -> mc.setScreen(new dev.oneforallsurvival.economy.client.TreasuryReasonScreen(
                        ecranTresor, new dev.oneforallsurvival.economy.CoinRequest(
                                dev.oneforallsurvival.economy.CoinRequest.WITHDRAW, Coin.EMERALD.ordinal(), 1, false), "")),
                () -> mc.screen instanceof dev.oneforallsurvival.economy.client.TreasuryReasonScreen, 1000,
                "tresor_motif_vide.png");
        etape("grille du tresor", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.setScreen(new Grille(TRESOR, 2));
        }, () -> true, 800, "tresor_grille.png");
        etape("fin du tresor", () -> fermer(mc), () -> true, 300, null);
    }

    /** Une vue d'un bloc de la zone du trésor, à {@code dx} blocs à l'est de son origine (relue à l'exécution). */
    private static void vueTresor(Minecraft mc, String fichier, int dx, Vec3 d, boolean nuit, long reposMs) {
        etape("vue " + fichier, () -> regarder(mc, zoneTresor.offset(dx, 0, 0), d, nuit), () -> true, reposMs, fichier);
    }

    /**
     * Les comptes du tableau, posés à la main chez le client : le serveur n'envoie rien tant que ses comptes ne changent
     * pas, et ceux d'ici ne sont pas ceux de la ville de démonstration.
     */
    private static void comptes(Minecraft mc, String nom, dev.oneforallsurvival.economy.town.TownFinances.Snapshot comptes) {
        etape("comptes " + nom, () -> {
            if (comptes == null) {
                dev.oneforallsurvival.economy.client.TownFinancesCache.clear();
            } else {
                dev.oneforallsurvival.economy.client.TownFinancesCache.set(comptes);
            }
        }, () -> true, 200, null);
    }

    private static void preparerTresor(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos z = zoneTresor;
        BlockState tresor = EconomyRegistry.TREASURY.get().defaultBlockState()
                .setValue(dev.oneforallsurvival.economy.town.TreasuryBlock.FACING, Direction.SOUTH);
        // Le trésor de la ville : seul, noté à son emplacement (l'écran s'ouvre) ; un second contre un mur (non noté).
        monde.setBlock(z, tresor, 3);
        var compte = dev.oneforallsurvival.economy.TreasuryAccount.get(serveur);
        compte.setPosition(net.minecraft.core.GlobalPos.of(monde.dimension(), z));
        for (Coin c : PIECES) {
            compte.add(c, Math.min(TRESOR_PIECES[c.ordinal()], compte.room(c)));
        }
        BlockPos mur = z.offset(8, 0, 0);
        monde.setBlock(mur, tresor, 3);
        pierreAutour(monde, mur);
        // Le tableau (3 x 2) contre un mur, les quatre orientations, un tableau seul.
        dev.oneforallsurvival.economy.town.TownBoardBlock tableau =
                (dev.oneforallsurvival.economy.town.TownBoardBlock) EconomyRegistry.TOWN_BOARD.get();
        BlockPos contre = z.offset(16, 0, 0);
        tableau.placeWhole(monde, contre, Direction.SOUTH);
        murAutourDuTableau(monde, contre, Direction.SOUTH);
        Direction[] faces = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
        for (int i = 0; i < faces.length; i++) {
            tableau.placeWhole(monde, z.offset(24 + 6 * i, 0, 0), faces[i]);
        }
        tableau.placeWhole(monde, z.offset(50, 0, 0), Direction.SOUTH);
        // Des lanternes : la nuit, les faces se voient sans être au grand jour.
        for (BlockPos l : List.of(z.offset(-2, 0, 2), z.offset(2, 0, 2), z.offset(6, 0, 3), z.offset(10, 0, 3),
                z.offset(13, 0, 3), z.offset(19, 0, 3), z.offset(27, 0, 3), z.offset(39, 0, 3), z.offset(47, 0, 3),
                z.offset(53, 0, 3))) {
            monde.setBlock(l, Blocks.LANTERN.defaultBlockState(), 3);
        }
        // L'inventaire : les cinq pièces et une bourse, comme à la monnaie.
        joueur.getInventory().clearContent();
        int[] piles = {37, 12, 8, 3, 1};
        for (Coin c : PIECES) {
            joueur.getInventory().setItem(c.ordinal(), new ItemStack(c.item(), piles[c.ordinal()]));
        }
        ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
        remplirBourse(bourse);
        joueur.getInventory().setItem(5, bourse);
        tresorPose = true;
    }

    /** De la pierre derrière, sur les côtés et au-dessus du tableau de 3 x 2 (la face avant et le sol restent libres). */
    private static void murAutourDuTableau(ServerLevel monde, BlockPos maitresse, Direction face) {
        for (dev.oneforallsurvival.economy.town.TownBoardPart partie : dev.oneforallsurvival.economy.town.TownBoardPart.values()) {
            BlockPos p = partie.from(maitresse, face);
            monde.setBlock(p.relative(face.getOpposite()), Blocks.STONE.defaultBlockState(), 3);
            if (partie.column() != 0) {
                monde.setBlock(p.relative(dev.oneforallsurvival.economy.town.TownBoardPart.right(face), partie.column()),
                        Blocks.STONE.defaultBlockState(), 3);
            }
            if (partie.row() == 1) {
                monde.setBlock(p.above(), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    /** De la pierre au nord, à l'est, à l'ouest et au-dessus du bloc (la face avant et le sol restent libres). */
    private static void pierreAutour(ServerLevel monde, BlockPos p) {
        for (BlockPos pierre : List.of(p.north(), p.east(), p.west(), p.above())) {
            monde.setBlock(pierre, Blocks.STONE.defaultBlockState(), 3);
        }
    }

    private static void souris(Minecraft mc) {
        try {
            double[] gui = sourisGui;
            double echelle = mc.getWindow().getGuiScale();
            double[] xy = gui == null ? new double[] {1, 1} : new double[] {gui[0] * echelle, gui[1] * echelle};
            String[] noms = {"xpos", "ypos"};
            for (int i = 0; i < 2; i++) {
                Field f = net.minecraft.client.MouseHandler.class.getDeclaredField(noms[i]);
                f.setAccessible(true);
                f.setDouble(mc.mouseHandler, xy[i]);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void jouer(Minecraft mc, long maintenant) throws IOException {
        souris(mc); // le curseur réel traînerait une infobulle dans l'image
        Etape e = ETAPES.get(index);
        if (!actionFaite) {
            LOG.info("[visuels] étape : {}", e.nom());
            e.action().run();
            actionFaite = true;
            debutAction = maintenant;
            debutRepos = 0;
            return;
        }
        if (!e.pret().getAsBoolean()) {
            if (maintenant - debutAction > 30_000_000_000L) {
                throw new IllegalStateException("étape sans issue : " + e.nom());
            }
            return;
        }
        if (debutRepos == 0) {
            debutRepos = maintenant;
        }
        if ((maintenant - debutRepos) / 1_000_000L < e.reposMs()) {
            return;
        }
        if (e.image() != null) {
            capturer(mc, e.image());
        }
        index++;
        actionFaite = false;
        if (index >= ETAPES.size()) {
            terminer(mc);
        }
    }

    private static void capturer(Minecraft mc, String fichier) throws IOException {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int l = image.getWidth();
            int h = image.getHeight();
            BufferedImage img = new BufferedImage(l, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < l; x++) {
                    int p = image.getPixelRGBA(x, y);
                    img.setRGB(x, y, ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF));
                }
            }
            ImageIO.write(img, "png", dossier.resolve(fichier).toFile());
            images++;
        }
    }

    private static void terminer(Minecraft mc) {
        phase = Phase.FIN;
        LOG.info("[visuels] terminé : {} images, {}", images, echec == null ? "sans erreur" : echec);
        mc.stop();
    }

    // ================================================================================================
    // Lot E5 : l'écran des quêtes
    // ================================================================================================

    /** Vrai pendant la capture du toast : les notifications ne sont pas effacées à chaque image. */
    private static volatile boolean garderToasts;
    private static final java.util.Set<String> QUETES_JOURNAL = new java.util.HashSet<>();
    private static final java.time.LocalDate JOUR_DEMO = java.time.LocalDate.of(2026, 11, 18);
    private static final String TITRE_64 = "Rassembler les vivres du grand banquet de la fête du solstice !!";
    /** 1 rubis 3 émeraudes 9 or 5 argent 7 cuivres : une récompense à cinq coupures. */
    private static final long CINQ_COUPURES = 13_957L;

    /**
     * L'écran des quêtes avec un état imposé chez le client (le serveur n'envoie rien tant que le carnet ne change pas) :
     * le contenu le plus long dans l'interface de 320 × 240 (onglet des quêtes, des événements, bulles du titre coupé,
     * de la récompense coupée et de l'attente), le plus court, le plafond atteint et réduit, le toast, puis les mêmes
     * dans une grande interface (640 × 360), et l'écran Contrôles sur la touche des quêtes (le pack est chargé).
     */
    private static void construireQuetes(Minecraft mc) {
        etape("quetes debut", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            journaliserTouche(mc);
        }, () -> true, 500, null);
        taille(mc, 960, 720, 3, 320, 240);
        ecranQuetes(mc, "320 long", etatLePlusLong(), 0, null, "quetes_320_long.png");
        ecranQuetes(mc, "320 evenements", etatLePlusLong(), 1, null, "quetes_320_evenements.png");
        ecranQuetes(mc, "320 bulle titre", etatLePlusLong(), 0, "title:0:0", "quetes_320_bulle_titre.png");
        ecranQuetes(mc, "320 bulle recompense", etatLePlusLong(), 0, "reward:0:1", "quetes_320_bulle_recompense.png");
        ecranQuetes(mc, "320 bulle attente", etatLePlusLong(), 0, "pending", "quetes_320_bulle_attente.png");
        ecranQuetes(mc, "320 bulle evenement", etatLePlusLong(), 1, "title:0:7", "quetes_320_bulle_evenement.png");
        ecranQuetes(mc, "320 court", etatLePlusCourt(), 0, null, "quetes_320_court.png");
        ecranQuetes(mc, "320 plafond", etatPlafond(1_000L), 0, "button:0:0", "quetes_320_plafond.png");
        ecranQuetes(mc, "320 plafond partiel", etatPlafond(980L), 0, "button:0:0", "quetes_320_plafond_partiel.png");
        toastQuete(mc, "quetes_320_toast.png");
        taille(mc, 1280, 720, 2, 640, 360);
        ecranQuetes(mc, "grand long", etatLePlusLong(), 0, null, "quetes_grand_long.png");
        ecranQuetes(mc, "grand evenements", etatLePlusLong(), 1, null, "quetes_grand_evenements.png");
        ecranQuetes(mc, "grand court", etatLePlusCourt(), 0, null, "quetes_grand_court.png");
        ecranQuetes(mc, "grand plafond", etatPlafond(1_000L), 0, "button:0:0", "quetes_grand_plafond.png");
        toastQuete(mc, "quetes_grand_toast.png");
        etape("controles ouverture", () -> {
            fermer(mc);
            mc.setScreen(new net.minecraft.client.gui.screens.controls.KeyBindsScreen(null, mc.options));
        }, () -> mc.screen instanceof net.minecraft.client.gui.screens.controls.KeyBindsScreen, 800, null);
        etape("controles touche des quetes", () -> defilerVersLaTouche(mc), () -> true, 800,
                "quetes_controles_touche.png");
        etape("fin des quetes", () -> {
            fermer(mc);
            dev.oneforallsurvival.economy.quest.client.QuestClientCache.clear();
        }, () -> true, 300, null);
    }

    /** Fenêtre de {@code l} × {@code h} pixels à l'échelle {@code echelle}, attendue à {@code guiL} × {@code guiH}. */
    private static void taille(Minecraft mc, int l, int h, int echelle, int guiL, int guiH) {
        etape("taille " + guiL + "x" + guiH, () -> {
            fermer(mc);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), l, h);
            mc.options.guiScale().set(echelle);
            mc.resizeDisplay();
        }, () -> {
            if (mc.getWindow().getGuiScaledWidth() == guiL && mc.getWindow().getGuiScaledHeight() == guiH) {
                return true;
            }
            mc.resizeDisplay();
            return false;
        }, 800, null);
    }

    private static void ecranQuetes(Minecraft mc, String nom, dev.oneforallsurvival.economy.quest.QuestSyncPacket etat,
            int onglet, String bulle, String image) {
        etape("quetes " + nom, () -> {
            fermer(mc);
            dev.oneforallsurvival.economy.quest.client.QuestClientCache.clear();
            dev.oneforallsurvival.economy.quest.client.QuestClientCache.accept(etat);
            var ecran = new dev.oneforallsurvival.economy.quest.client.QuestScreen();
            mc.setScreen(ecran);
            ecran.showTab(onglet);
            ecran.pinHover(bulle);
        }, () -> {
            if (!(mc.screen instanceof dev.oneforallsurvival.economy.quest.client.QuestScreen ecran)
                    || ecran.layout() == null) {
                return false;
            }
            if (QUETES_JOURNAL.add(nom)) {
                var l = ecran.layout();
                LOG.info("[visuels] quetes {} : cadre {}x{} en ({}, {}) dans une interface de {}x{}, defilement {}",
                        nom, l.frame().w(), l.frame().h(), l.frame().x(), l.frame().y(),
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), l.scrollMax());
            }
            return true;
        }, 1000, image);
    }

    /** Deux états successifs : la quête passe de « en cours » à « finie », le cache ajoute le toast. */
    private static void toastQuete(Minecraft mc, String image) {
        etape("toast " + image, () -> {
            fermer(mc);
            garderToasts = true;
            mc.getToasts().clear();
            dev.oneforallsurvival.economy.quest.client.QuestClientCache.clear();
            dev.oneforallsurvival.economy.quest.client.QuestClientCache.accept(etatToast(false));
            dev.oneforallsurvival.economy.quest.client.QuestClientCache.accept(etatToast(true));
        }, () -> true, 1500, image);
        etape("fin du toast " + image, () -> {
            garderToasts = false;
            mc.getToasts().clear();
        }, () -> true, 200, null);
    }

    /** Note au journal la touche des quêtes et toute touche qui la partage (ce que Contrôles montre en rouge). */
    private static void journaliserTouche(Minecraft mc) {
        net.minecraft.client.KeyMapping quetes = dev.oneforallsurvival.economy.quest.client.QuestKeys.OPEN;
        List<String> conflits = new ArrayList<>();
        for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
            if (k != quetes && (k.same(quetes) || quetes.same(k))) {
                conflits.add(k.getName() + " (" + k.getCategory() + ")");
            }
        }
        LOG.info("[visuels] touche des quetes : {} parmi {} touches, conflits : {}", quetes.getKey().getName(),
                mc.options.keyMappings.length, conflits.isEmpty() ? "aucun" : conflits);
    }

    /** Fait défiler la liste de Contrôles jusqu'à la touche des quêtes (champ privé lu par son type). */
    private static void defilerVersLaTouche(Minecraft mc) {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.controls.KeyBindsScreen ecran)) {
            throw new IllegalStateException("Contrôles n'est pas ouvert : " + mc.screen);
        }
        try {
            for (Field champ : net.minecraft.client.gui.screens.controls.KeyBindsScreen.class.getDeclaredFields()) {
                if (champ.getType() != net.minecraft.client.gui.screens.controls.KeyBindsList.class) {
                    continue;
                }
                champ.setAccessible(true);
                var liste = (net.minecraft.client.gui.screens.controls.KeyBindsList) champ.get(ecran);
                var lignes = liste.children();
                for (int i = 0; i < lignes.size(); i++) {
                    for (Field f : lignes.get(i).getClass().getDeclaredFields()) {
                        if (f.getType() == net.minecraft.client.KeyMapping.class) {
                            f.setAccessible(true);
                            if (f.get(lignes.get(i)) == dev.oneforallsurvival.economy.quest.client.QuestKeys.OPEN) {
                                liste.setScrollAmount(Math.max(0, i * 20 - 60));
                                LOG.info("[visuels] Contrôles : touche des quetes à la ligne {} sur {}", i, lignes.size());
                                return;
                            }
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("touche des quêtes absente de Contrôles");
    }

    private static dev.oneforallsurvival.economy.quest.QuestSyncPacket.Entry quete(
            dev.oneforallsurvival.economy.quest.QuestRef ref, Component titre, net.minecraft.world.item.Item icone,
            int progres, int but, long recompense, long payable,
            dev.oneforallsurvival.economy.quest.QuestSyncPacket.State etat) {
        return new dev.oneforallsurvival.economy.quest.QuestSyncPacket.Entry(ref, titre, new ItemStack(icone), progres, but,
                recompense, payable, etat);
    }

    private static dev.oneforallsurvival.economy.quest.QuestRef ref(dev.oneforallsurvival.economy.quest.QuestRef.Kind kind,
            int index) {
        return new dev.oneforallsurvival.economy.quest.QuestRef(kind, index, "demo_" + kind + "_" + index, JOUR_DEMO);
    }

    private static Component modele(String id, int but) {
        return Component.translatable("quest.oneforallsurvival.template." + id, but);
    }

    /**
     * Le contenu le plus long : titre de 64 caractères, titres livrés les plus longs, récompenses à cinq coupures,
     * progressions à quatre chiffres, 3 + 5 quêtes, attente pleine (8), huit événements, plafond à neuf chiffres,
     * cinq relances, les deux renouvellements à 23 h 59.
     */
    private static dev.oneforallsurvival.economy.quest.QuestSyncPacket etatLePlusLong() {
        var D = dev.oneforallsurvival.economy.quest.QuestRef.Kind.DAILY;
        var W = dev.oneforallsurvival.economy.quest.QuestRef.Kind.WEEKLY;
        var FINIE = dev.oneforallsurvival.economy.quest.QuestSyncPacket.State.COMPLETE;
        var EN_COURS = dev.oneforallsurvival.economy.quest.QuestSyncPacket.State.IN_PROGRESS;
        var RECLAMEE = dev.oneforallsurvival.economy.quest.QuestSyncPacket.State.CLAIMED;
        long r = CINQ_COUPURES;
        List<dev.oneforallsurvival.economy.quest.QuestSyncPacket.Entry> q = new ArrayList<>();
        q.add(quete(ref(D, 0), Component.literal(TITRE_64), Items.CAKE, 9999, 9999, r, r, FINIE));
        q.add(quete(ref(D, 1), modele("harvest_potatoes", 1280), Items.POTATO, 1279, 1280, r, 0, EN_COURS));
        q.add(quete(ref(D, 2), modele("breed_animals", 1280), Items.EGG, 999, 1280, r, 0, EN_COURS));
        q.add(quete(ref(W, 0), modele("harvest_potatoes", 9999), Items.POTATO, 9999, 9999, r, r, FINIE));
        q.add(quete(ref(W, 1), modele("cook_meals", 9999), Items.BOWL, 4321, 9999, r, 0, EN_COURS));
        q.add(quete(ref(W, 2), modele("mine_copper", 9999), Items.COPPER_ORE, 9999, 9999, r, 0, RECLAMEE));
        q.add(quete(ref(W, 3), modele("kill_skeletons", 9999), Items.SKELETON_SPAWN_EGG, 1, 9999, r, 0, EN_COURS));
        q.add(quete(ref(W, 4), modele("place_bricks", 9999), Items.BRICKS, 0, 9999, r, 0, EN_COURS));
        for (int i = 0; i < 8; i++) {
            q.add(quete(dev.oneforallsurvival.economy.quest.QuestRef.event("evenement_" + i, 1_000L + i),
                    Component.literal(TITRE_64), Items.FIREWORK_ROCKET, i == 0 ? 9999 : 1234, 9999, r, i == 0 ? r : 0,
                    i == 0 ? FINIE : EN_COURS));
        }
        long minuit = (23L * 60L + 59L) * 60_000L;
        return new dev.oneforallsurvival.economy.quest.QuestSyncPacket(999_999_999L - r, 999_999_999L, 5, minuit, minuit, 8,
                q);
    }

    /** Le contenu le plus court : une quête, aucune attente, aucun événement, pas de relance, renouvellement proche. */
    private static dev.oneforallsurvival.economy.quest.QuestSyncPacket etatLePlusCourt() {
        var q = List.of(quete(ref(dev.oneforallsurvival.economy.quest.QuestRef.Kind.DAILY, 0), modele("fish", 6),
                Items.COD, 0, 6, 15L, 0L, dev.oneforallsurvival.economy.quest.QuestSyncPacket.State.IN_PROGRESS));
        return new dev.oneforallsurvival.economy.quest.QuestSyncPacket(0L, 1_000L, 0, 3L * 60_000L, 4L * 24L * 3_600_000L, 0,
                q);
    }

    /** Le plafond payé à {@code paye} sur 1 000 : deux journalières finies, la seconde partiellement payable ou pas. */
    private static dev.oneforallsurvival.economy.quest.QuestSyncPacket etatPlafond(long paye) {
        var D = dev.oneforallsurvival.economy.quest.QuestRef.Kind.DAILY;
        var W = dev.oneforallsurvival.economy.quest.QuestRef.Kind.WEEKLY;
        var FINIE = dev.oneforallsurvival.economy.quest.QuestSyncPacket.State.COMPLETE;
        var EN_COURS = dev.oneforallsurvival.economy.quest.QuestSyncPacket.State.IN_PROGRESS;
        long reste = Math.max(0L, 1_000L - paye);
        List<dev.oneforallsurvival.economy.quest.QuestSyncPacket.Entry> q = new ArrayList<>();
        q.add(quete(ref(D, 0), modele("mine_iron", 6), Items.IRON_ORE, 6, 6, 50L, Math.min(50L, reste), FINIE));
        q.add(quete(ref(D, 1), modele("break_logs", 40), Items.OAK_LOG, 40, 40, 15L, Math.min(15L, reste), FINIE));
        q.add(quete(ref(D, 2), modele("fish", 8), Items.COD, 2, 8, 20L, 0L, EN_COURS));
        q.add(quete(ref(W, 0), modele("break_stone", 400), Items.STONE, 400, 400, 62L, Math.min(62L, reste), FINIE));
        q.add(quete(ref(W, 1), modele("kill_monsters", 60), Items.ZOMBIE_SPAWN_EGG, 31, 60, 90L, 0L, EN_COURS));
        return new dev.oneforallsurvival.economy.quest.QuestSyncPacket(paye, 1_000L, 1, 5L * 3_600_000L + 12L * 60_000L,
                3L * 24L * 3_600_000L, 0, q);
    }

    /** L'état d'avant et d'après pour le toast : la même quête, en cours puis finie. */
    private static dev.oneforallsurvival.economy.quest.QuestSyncPacket etatToast(boolean finie) {
        var q = List.of(quete(ref(dev.oneforallsurvival.economy.quest.QuestRef.Kind.DAILY, 0),
                modele("harvest_potatoes", 128), Items.POTATO, finie ? 128 : 127, 128, 25L, finie ? 25L : 0L,
                finie ? dev.oneforallsurvival.economy.quest.QuestSyncPacket.State.COMPLETE
                        : dev.oneforallsurvival.economy.quest.QuestSyncPacket.State.IN_PROGRESS));
        return new dev.oneforallsurvival.economy.quest.QuestSyncPacket(0L, 1_000L, 1, 3_600_000L, 3_600_000L, 0, q);
    }

    // ================================================================================================
    // Lot E6 : le panneau du poste de fournisseur, la facture d'électricité, le poste posé
    // ================================================================================================

    private static BlockPos zoneElectricite;
    private static volatile boolean electricitePosee;
    private static final java.util.Set<String> PANNEAU_JOURNAL = new java.util.HashSet<>();
    private static final String NOM_24 = "Compagnie des Eaux Vives";
    private static final String NOM_16 = "Abcdefghijklmnop";

    /**
     * Le panneau du poste avec un état imposé chez le client (le contenu le plus long : nom de 24 caractères, 70 clients
     * dont 64 montrés aux noms de 16 caractères, montants à cinq coupures, écart en alerte ; le plus court, avec un client
     * coupé et des impayés ; un poste neuf, vide puis nommé), la bulle d'un nom coupé et celle des impayés, la fin de la
     * liste, dans l'interface de 320 × 240 puis dans une grande (640 × 360) ; l'écran d'une facture d'électricité (motif
     * traduit) ; puis le poste posé seul et collé à un mur, sous deux angles, de jour.
     */
    private static void construireElectricite(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        etape("electricite pose", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            zoneElectricite = origine.offset(-24, 0, 360);
            serveur.execute(() -> preparerElectricite(serveur));
        }, () -> electricitePosee, 4000, null);
        // Le joueur se tient devant le poste : le panneau se ferme au-delà de huit blocs.
        etape("electricite devant le poste", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            serveur.execute(() -> {
                ServerLevel monde = serveur.overworld();
                ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                monde.setDayTime(6000);
                BlockPos z = zoneElectricite;
                joueur.teleportTo(monde, z.getX() + 0.5, z.getY(), z.getZ() + 3.5, 180F, 20F);
            });
        }, () -> mc.player != null && mc.player.blockPosition().distSqr(zoneElectricite) < 30, 1500, null);
        taille(mc, 960, 720, 3, 320, 240);
        panneau(mc, "320 long", () -> panneauLePlusLong(), null, 0, "panneau_320_long.png");
        panneau(mc, "320 bulle nom", () -> panneauLePlusLong(), "name:0", 0, "panneau_320_bulle_nom.png");
        panneau(mc, "320 bulle impayes", () -> panneauLePlusLong(), "unpaid:1", 0, "panneau_320_bulle_impayes.png");
        panneau(mc, "320 bulle ecart", () -> panneauLePlusLong(), "gap", 0, "panneau_320_bulle_ecart.png");
        panneau(mc, "320 fin de liste", () -> panneauLePlusLong(), null, 99_999, "panneau_320_fin_de_liste.png");
        panneau(mc, "320 court", () -> panneauLePlusCourt(), null, 0, "panneau_320_court_coupe_impayes.png");
        panneau(mc, "320 bulle sans disjoncteur", () -> panneauLePlusCourt(), "button:2:0", 0,
                "panneau_320_bulle_sans_disjoncteur.png");
        panneau(mc, "320 tarif ferme", () -> panneauCourt(false), "price", 0, "panneau_320_tarif_ferme.png");
        panneau(mc, "320 neuf", () -> panneauNeuf(), null, 0, "panneau_320_neuf.png");
        panneauNomme(mc, "panneau_320_neuf_nom_saisi.png");
        factureElectricite(mc, "facture_electricite_320.png");
        taille(mc, 1280, 720, 2, 640, 360);
        panneau(mc, "grand long", () -> panneauLePlusLong(), null, 0, "panneau_grand_long.png");
        panneau(mc, "grand bulle nom", () -> panneauLePlusLong(), "name:0", 0, "panneau_grand_bulle_nom.png");
        panneau(mc, "grand court", () -> panneauLePlusCourt(), null, 0, "panneau_grand_court_coupe_impayes.png");
        panneau(mc, "grand neuf", () -> panneauNeuf(), null, 0, "panneau_grand_neuf.png");
        factureElectricite(mc, "facture_electricite_grand.png");
        // La fenêtre de l'essai de bout en bout (1280 × 656, échelle automatique : 640 × 328) : les tarifs du lendemain aux
        // valeurs courantes les plus longues (abonnement 49 → 48, prix et plafond 19 → 18, taxe 20 → 19), puis ceux de
        // l'essai (prix 0 → 5, abonnement 0 → 10, plafond 2 argent → 3) ; rien ne doit être coupé (revue de la tâche 7).
        taille(mc, 1280, 656, 0, 640, 328);
        panneau(mc, "1280x656 tarifs courants", () -> panneauTarifs(19L, 18L, 49L, 48L, 19L, 18L, 20, 19), null, 0,
                "panneau_1280x656_tarifs_courants.png");
        panneau(mc, "1280x656 tarifs de l'essai", () -> panneauTarifs(0L, 5L, 0L, 10L, 20L, 3L, 0, 0), null, 0,
                "panneau_1280x656_tarifs_essai.png");
        // Le poste posé : seul, puis collé à un mur (pierre derrière, à l'est, à l'ouest et au-dessus).
        etape("electricite vues", () -> fermer(mc), () -> true, 300, null);
        etape("vue poste_seul_trois_quarts.png", () -> regarder(mc, zoneElectricite, new Vec3(2.2, 1.3, 2.6), false),
                () -> true, 2500, "poste_seul_trois_quarts.png");
        etape("vue poste_seul_arriere.png", () -> regarder(mc, zoneElectricite, new Vec3(-1.8, 1.2, -2.6), false),
                () -> true, 1500, "poste_seul_arriere.png");
        etape("vue poste_contre_un_mur_trois_quarts.png", () -> regarder(mc, zoneElectricite.offset(8, 0, 0),
                new Vec3(1.6, 1.1, 2.2), false), () -> true, 1500, "poste_contre_un_mur_trois_quarts.png");
        etape("vue poste_contre_un_mur_autre_cote.png", () -> regarder(mc, zoneElectricite.offset(8, 0, 0),
                new Vec3(-1.6, 0.6, 2.0), false), () -> true, 1500, "poste_contre_un_mur_autre_cote.png");
        etape("vue poste_contre_un_mur_face.png", () -> regarder(mc, zoneElectricite.offset(8, 0, 0),
                new Vec3(0.0, 0.5, 1.8), false), () -> true, 1500, "poste_contre_un_mur_face.png");
        etape("fin de l'electricite", () -> fermer(mc), () -> true, 300, null);
    }

    private static void preparerElectricite(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        BlockPos z = zoneElectricite;
        BlockState poste = GridRegistry.POWER_STATION.get().defaultBlockState()
                .setValue(dev.oneforallsurvival.machine.InlineEnergyBlock.FACING, Direction.SOUTH);
        for (int dx = -3; dx <= 11; dx++) {
            for (int dz = -3; dz <= 4; dz++) {
                monde.setBlock(z.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int dy = 0; dy <= 3; dy++) {
                    monde.setBlock(z.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        monde.setBlock(z, poste, 3);
        BlockPos mur = z.offset(8, 0, 0);
        monde.setBlock(mur, poste, 3);
        pierreAutour(monde, mur);
        electricitePosee = true;
    }

    /** Un état imposé au panneau, ouvert sur le poste devant lequel se tient le joueur. */
    private static void panneau(Minecraft mc, String nom,
            java.util.function.Supplier<dev.oneforallsurvival.grid.supplier.PowerPanelPacket> etat, String bulle,
            int defilement, String image) {
        etape("panneau " + nom, () -> {
            fermer(mc);
            mc.options.hideGui = false;
            dev.oneforallsurvival.grid.supplier.client.PowerPanelScreen.accept(etat.get());
            if (mc.screen instanceof dev.oneforallsurvival.grid.supplier.client.PowerPanelScreen ecran) {
                ecran.pinHover(bulle);
                ecran.scrollTo(defilement);
            }
        }, () -> {
            if (!(mc.screen instanceof dev.oneforallsurvival.grid.supplier.client.PowerPanelScreen ecran)
                    || ecran.layout() == null) {
                return false;
            }
            if (PANNEAU_JOURNAL.add(nom)) {
                var l = ecran.layout();
                List<String> coupes = new ArrayList<>();
                if (l.price() != null) {
                    for (var t : List.of(l.price().label(), l.price().next(), l.subscription().label(),
                            l.subscription().next(), l.cap(), l.capNext())) {
                        if (t.cut()) {
                            coupes.add("« " + t.full() + " »");
                        }
                    }
                }
                LOG.info("[visuels] panneau {} : cadre {}x{} en ({}, {}) dans une interface de {}x{}, defilement {}, "
                        + "{} clients visibles ; tarifs coupes : {}", nom, l.frame().w(), l.frame().h(), l.frame().x(),
                        l.frame().y(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                        l.scrollMax(), l.list() == null ? 0 : l.list().h() / 28,
                        coupes.isEmpty() ? "aucun" : String.join(", ", coupes));
            }
            return true;
        }, 1000, image);
    }

    /** Le poste neuf, son nom de 24 caractères saisi. */
    private static void panneauNomme(Minecraft mc, String image) {
        etape("panneau neuf nomme", () -> {
            if (mc.screen instanceof dev.oneforallsurvival.grid.supplier.client.PowerPanelScreen ecran) {
                ecran.type("name", NOM_24);
            }
        }, () -> mc.screen instanceof dev.oneforallsurvival.grid.supplier.client.PowerPanelScreen, 800, image);
    }

    private static BlockPos posteDemo() {
        return zoneElectricite;
    }

    private static dev.oneforallsurvival.grid.supplier.PowerPanelPacket panneauLePlusLong() {
        List<dev.oneforallsurvival.grid.supplier.PowerPanelPacket.Client> clients = new ArrayList<>();
        var etats = dev.oneforallsurvival.grid.supplier.PowerClient.State.values();
        for (int i = 0; i < 64; i++) {
            var etat = etats[i % etats.length];
            clients.add(new dev.oneforallsurvival.grid.supplier.PowerPanelPacket.Client(
                    new java.util.UUID(0x0E6L, i), NOM_16.substring(0, 12) + String.format("%04d", i),
                    "Zyxwvutsrqponmlk", etat, 99_999_999_999L, 99_999_999_999L, CINQ_COUPURES, i % 5 != 2,
                    etat.passes() && i % 3 == 0));
        }
        return new dev.oneforallsurvival.grid.supplier.PowerPanelPacket(posteDemo(), true, NOM_24, CINQ_COUPURES, 50L,
                CINQ_COUPURES, CINQ_COUPURES, CINQ_COUPURES, CINQ_COUPURES, 20, 20, 100_000L, 50L, 10,
                99_999_999_999L, 12_345_678_901L, 4_000_000L, 99_999_999_999L, 999_999_999L, true, true, 70, clients);
    }

    private static dev.oneforallsurvival.grid.supplier.PowerPanelPacket panneauLePlusCourt() {
        return panneauCourt(true);
    }

    /** Le contenu le plus court ; {@code mayTariffDemo} faux : vu par un ayant droit client du fournisseur. */
    private static dev.oneforallsurvival.grid.supplier.PowerPanelPacket panneauCourt(boolean mayTariffDemo) {
        var actif = dev.oneforallsurvival.grid.supplier.PowerClient.State.ACTIVE;
        var attente = dev.oneforallsurvival.grid.supplier.PowerClient.State.AWAITING_ACCEPT;
        List<dev.oneforallsurvival.grid.supplier.PowerPanelPacket.Client> clients = List.of(
                new dev.oneforallsurvival.grid.supplier.PowerPanelPacket.Client(new java.util.UUID(0x0E6L, 100), "Bob",
                        "Bob", attente, 0L, 0L, 0L, false, false),
                new dev.oneforallsurvival.grid.supplier.PowerPanelPacket.Client(new java.util.UUID(0x0E6L, 101), "Alice",
                        "Alice", actif, 0L, 1_240_000L, 125L, true, true),
                new dev.oneforallsurvival.grid.supplier.PowerPanelPacket.Client(new java.util.UUID(0x0E6L, 102), "Atelier",
                        "Eve", actif, 1_234_000L, 980_000L, 0L, false, false),
                new dev.oneforallsurvival.grid.supplier.PowerPanelPacket.Client(new java.util.UUID(0x0E6L, 103), "Dan",
                        "Dan", dev.oneforallsurvival.grid.supplier.PowerClient.State.RESIGNING, 52_000L, 61_000L, 20L,
                        true, false));
        return new dev.oneforallsurvival.grid.supplier.PowerPanelPacket(posteDemo(), true, "Ab", 1L, 0L, 1L, 0L, 20L, 20L,
                0, 0, 100_000L, 50L, 10, 0L, 1_286_000L, 1_300_000L, 1_255_000L, 0L, false, mayTariffDemo, 4, clients);
    }

    /** Le contenu court, avec ces tarifs du jour et du lendemain (cuivres) et ces taxes (%). */
    private static dev.oneforallsurvival.grid.supplier.PowerPanelPacket panneauTarifs(long prix, long prixDemain,
            long abonnement, long abonnementDemain, long plafond, long plafondDemain, int taxe, int taxeDemain) {
        var c = panneauCourt(true);
        return new dev.oneforallsurvival.grid.supplier.PowerPanelPacket(posteDemo(), true, "Lumière du Port", prix,
                abonnement, prixDemain, abonnementDemain, plafond, plafondDemain, taxe, taxeDemain, 100_000L, 50L, 10,
                74_560L, 74_560L, 1_300_000L, 1_255_000L, 22L, true, true, c.clientCount(), c.clients());
    }

    private static dev.oneforallsurvival.grid.supplier.PowerPanelPacket panneauNeuf() {
        return dev.oneforallsurvival.grid.supplier.PowerPanelPacket.unnamed(posteDemo(), true);
    }

    /** L'écran d'une facture d'électricité dont le joueur est le débiteur : le motif est traduit chez le client. */
    private static void factureElectricite(Minecraft mc, String image) {
        etape("facture electricite " + image, () -> {
            fermer(mc);
            mc.options.hideGui = false;
            var moi = mc.player.getUUID();
            long jour = java.time.LocalDate.of(2026, 11, 17).toEpochDay();
            String ref = String.join("/", "1", "e6demo000001", new java.util.UUID(0x0E6L, 7).toString(), "10",
                    Long.toString(jour), "5800000", "1", "100000", "2", NOM_24);
            var facture = new dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice(
                    java.util.UUID.fromString("e6e6e6e6-0000-4000-8000-000000000001"), moi, "Fondateur", moi,
                    mc.player.getGameProfile().getName(), 60L, NOM_24 + " 17/11/2026", System.currentTimeMillis(),
                    dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status.DUE, 0L,
                    dev.oneforallsurvival.grid.supplier.PowerBilling.KIND + ":" + ref);
            dev.oneforallsurvival.economy.client.InvoiceScreen.open(
                    dev.oneforallsurvival.economy.invoice.InvoiceItem.of(facture), 0);
        }, () -> mc.screen instanceof dev.oneforallsurvival.economy.client.InvoiceScreen, 1200, image);
    }

    // ================================================================================================
    // Lot T1 : le téléphone et la prise murale
    // ================================================================================================

    private static BlockPos zoneTelephone;
    private static volatile boolean telephonePose;
    /** Les prises des quatre états contre le mur de pierre : vide, branchée à l'arrêt, en charge, sans courant. */
    private static final BlockPos[] PRISES_ETATS = new BlockPos[4];
    private static final String[] NOMS_ETATS_PRISE = {"vide", "branchee_arret", "en_charge", "sans_courant"};
    private static final dev.oneforallsurvival.machine.MachineState[] ETATS_PRISE = {
        dev.oneforallsurvival.machine.MachineState.STOPPED, dev.oneforallsurvival.machine.MachineState.STOPPED,
        dev.oneforallsurvival.machine.MachineState.RUNNING, dev.oneforallsurvival.machine.MachineState.NO_POWER};
    /** Les prises en charge autour du pilier de pierre, une par orientation. */
    private static final Direction[] ORIENTATIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final String[] NOMS_ORIENTATIONS = {"nord", "est", "sud", "ouest"};
    private static final BlockPos[] PRISES_PILIER = new BlockPos[4];
    private static BlockPos pilier;
    /** Les prises tenues en charge : du courant à chaque tic, et un téléphone qui ne se remplit jamais. */
    private static final List<BlockPos> PRISES_EN_CHARGE = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.Set<String> TELEPHONE_JOURNAL = new java.util.HashSet<>();

    /**
     * Le téléphone et sa prise : la prise collée à un mur de pierre dans ses quatre états (vide, branchée à l'arrêt, en
     * charge avec le voyant allumé, sans courant), de jour et de nuit, avec des voisins de pierre contre elle, de profil,
     * puis en charge sur les quatre faces d'un pilier (quatre orientations) ; l'objet en main (première et troisième
     * personne), dans la barre et l'inventaire avec sa charge pleine, à moitié et vide ; enfin l'écran de repli
     * (verrouillé, accueil, Réglages remplis, effacement armé) dans l'interface de 320 × 240, à l'échelle 4 sur une
     * grande fenêtre (480 × 260), et dans une grande interface (640 × 360). Activé par {@code -PvisuelsSeul=telephone}.
     */
    private static void construireTelephone(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        zoneTelephone = origine.offset(24, 0, 400);
        etape("telephone pose", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            serveur.execute(() -> preparerTelephone(serveur));
        }, () -> telephonePose, 3000, null);

        // La prise contre le mur, ses quatre états ; l'image n'est prise qu'une fois chaque état relu chez le client.
        etape("vue telephone_prises_etats_jour.png", () -> regarder(mc, zoneTelephone.offset(4, 1, 1),
                new Vec3(0.0, 0.4, 4.2), false), () -> prisesPretes(mc), 2500, "telephone_prises_etats_jour.png");
        vue(mc, "telephone_prises_etats_nuit.png", zoneTelephone.offset(4, 1, 1), new Vec3(0.0, 0.4, 4.2), true);
        // Les vues de près lisent les positions à l'exécution : elles ne sont connues qu'une fois la zone posée.
        for (int i = 0; i < 4; i++) {
            int n = i;
            etape("vue telephone_prise_" + NOMS_ETATS_PRISE[n], () -> regarder(mc, PRISES_ETATS[n],
                    new Vec3(0.9, 0.35, 1.5), false), () -> true, 1500, "telephone_prise_" + NOMS_ETATS_PRISE[n] + ".png");
        }
        // Des voisins de pierre contre la prise vide (à gauche et au-dessus) : aucune face ne doit manquer.
        etape("vue telephone_prise_voisins_droite", () -> regarder(mc, PRISES_ETATS[0], new Vec3(1.3, 0.6, 1.3), false),
                () -> true, 1500, "telephone_prise_voisins_droite.png");
        etape("vue telephone_prise_voisins_dessous", () -> regarder(mc, PRISES_ETATS[0], new Vec3(0.5, -0.7, 1.4), false),
                () -> true, 1500, "telephone_prise_voisins_dessous.png");
        etape("vue telephone_prise_profil", () -> regarder(mc, PRISES_ETATS[2], new Vec3(1.5, 0.15, 0.1), false),
                () -> true, 1500, "telephone_prise_profil.png");
        etape("vue telephone_prise_profil_branchee", () -> regarder(mc, PRISES_ETATS[1], new Vec3(-1.4, 0.3, 0.35), false),
                () -> true, 1500, "telephone_prise_profil_branchee.png");
        // Les quatre orientations, en charge, autour du pilier.
        for (int k = 0; k < 4; k++) {
            int n = k;
            etape("vue telephone_prise_orientation_" + NOMS_ORIENTATIONS[n], () -> {
                Direction d = ORIENTATIONS[n];
                Vec3 v = new Vec3(d.getStepX() * 1.6 - d.getStepZ() * 0.6, 0.35, d.getStepZ() * 1.6 + d.getStepX() * 0.6);
                regarder(mc, PRISES_PILIER[n], v, false);
            }, () -> true, 1500, "telephone_prise_orientation_" + NOMS_ORIENTATIONS[n] + ".png");
        }
        etape("vue telephone_pilier_sud_est", () -> regarder(mc, pilier.above(), new Vec3(2.3, 1.0, 2.3), false),
                () -> true, 1500, "telephone_pilier_sud_est.png");
        etape("vue telephone_pilier_nord_ouest", () -> regarder(mc, pilier.above(), new Vec3(-2.3, 1.0, -2.3), false),
                () -> true, 1500, "telephone_pilier_nord_ouest.png");

        // L'objet : charge pleine, à moitié et vide dans la barre, à côté de la prise, d'une horloge et d'une boussole.
        etape("telephone objets", () -> serveur.execute(() -> {
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            joueur.getInventory().clearContent();
            int cap = dev.oneforallsurvival.phone.PhoneConfig.capacity();
            int[] charges = {cap, cap / 2, 0};
            for (int i = 0; i < 3; i++) {
                joueur.getInventory().setItem(i, telephone(charges[i]));
            }
            joueur.getInventory().setItem(3, new ItemStack(dev.oneforallsurvival.phone.PhoneRegistry.WALL_SOCKET_ITEM.get()));
            joueur.getInventory().setItem(4, new ItemStack(Items.CLOCK));
            joueur.getInventory().setItem(5, new ItemStack(Items.COMPASS));
            joueur.getInventory().setItem(9, telephone(cap / 4));
            joueur.getInventory().setItem(10, new ItemStack(dev.oneforallsurvival.phone.PhoneRegistry.WALL_SOCKET_ITEM.get()));
        }), () -> mc.player.getInventory().getItem(2).is(dev.oneforallsurvival.phone.PhoneRegistry.PHONE.get()), 500, null);
        etape("telephone en main premiere personne", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.gameRenderer.setRenderHand(true);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.getInventory().selected = 1;
            BlockPos z = zoneTelephone;
            serveur.execute(() -> {
                ServerLevel monde = serveur.overworld();
                ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                monde.setDayTime(6000);
                joueur.setNoGravity(true);
                joueur.teleportTo(monde, z.getX() + 4.5, z.getY(), z.getZ() + 4.5, 180F, 10F);
            });
        }, () -> true, 2000, "telephone_main_premiere_personne_et_barre.png");
        etape("telephone en main troisieme personne", () -> {
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            BlockPos z = zoneTelephone;
            serveur.execute(() -> {
                ServerLevel monde = serveur.overworld();
                ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                joueur.teleportTo(monde, z.getX() + 4.5, z.getY(), z.getZ() + 3.5, 0F, 15F);
            });
        }, () -> true, 2000, "telephone_main_troisieme_personne.png");
        etape("telephone infobulle", () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
            mc.setScreen(new Infobulle(telephone(dev.oneforallsurvival.phone.PhoneConfig.capacity() / 2)));
        }, () -> true, 800, "telephone_objet_infobulle.png");
        // En survie : en créatif, l'inventaire s'ouvre sur les onglets.
        etape("telephone survie", () -> {
            fermer(mc);
            serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0).setGameMode(GameType.SURVIVAL));
        }, () -> !mc.player.getAbilities().instabuild, 500, null);
        etape("telephone inventaire", () -> mc.setScreen(new InventoryScreen(mc.player)), () -> true, 800,
                "telephone_inventaire.png");
        etape("telephone barre survie", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.player.getInventory().selected = 0;
        }, () -> true, 800, "telephone_barre_survie.png");
        etape("telephone retour creatif", () -> serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0)
                .setGameMode(GameType.CREATIVE)), () -> mc.player.getAbilities().instabuild, 300, null);

        // L'écran de repli, état imposé chez le client (aucun geste n'est envoyé : la session 4242 n'existe pas).
        taille(mc, 960, 720, 3, 320, 240);
        repli(mc, "320 verrouille", PhoneStatePacket.Lock.LOCKED, null, "telephone_repli_320_verrouille.png");
        repli(mc, "320 accueil", PhoneStatePacket.Lock.UNLOCKED, null, "telephone_repli_320_accueil.png");
        repli(mc, "320 reglages vides", PhoneStatePacket.Lock.UNLOCKED, "vides", "telephone_repli_320_reglages_vides.png");
        repli(mc, "320 reglages", PhoneStatePacket.Lock.UNLOCKED, "remplis", "telephone_repli_320_reglages.png");
        repli(mc, "320 effacement", PhoneStatePacket.Lock.UNLOCKED, "efface", "telephone_repli_320_effacement.png");
        taille(mc, 1920, 1040, 4, 480, 260);
        repli(mc, "480 verrouille", PhoneStatePacket.Lock.LOCKED, null, "telephone_repli_480x260_verrouille.png");
        repli(mc, "480 reglages", PhoneStatePacket.Lock.UNLOCKED, "remplis", "telephone_repli_480x260_reglages.png");
        repli(mc, "480 effacement", PhoneStatePacket.Lock.UNLOCKED, "efface", "telephone_repli_480x260_effacement.png");
        taille(mc, 1280, 720, 2, 640, 360);
        repli(mc, "640 verrouille", PhoneStatePacket.Lock.LOCKED, null, "telephone_repli_640_verrouille.png");
        repli(mc, "640 accueil", PhoneStatePacket.Lock.UNLOCKED, null, "telephone_repli_640_accueil.png");
        repli(mc, "640 reglages", PhoneStatePacket.Lock.UNLOCKED, "remplis", "telephone_repli_640_reglages.png");
        etape("fin du telephone", () -> fermer(mc), () -> true, 300, null);
    }

    private static ItemStack telephone(int fe) {
        ItemStack pile = new ItemStack(dev.oneforallsurvival.phone.PhoneRegistry.PHONE.get());
        dev.oneforallsurvival.phone.PhoneItem.store(pile, fe);
        return pile;
    }

    /** Vrai quand les quatre prises du mur sont, chez le client, dans l'état attendu ; le note au journal une fois. */
    private static boolean prisesPretes(Minecraft mc) {
        for (int i = 0; i < 4; i++) {
            BlockState s = mc.level.getBlockState(PRISES_ETATS[i]);
            if (!s.is(dev.oneforallsurvival.phone.PhoneRegistry.WALL_SOCKET.get())
                    || s.getValue(dev.oneforallsurvival.machine.ElectricMachineBlock.STATE) != ETATS_PRISE[i]
                    || s.getValue(dev.oneforallsurvival.phone.WallSocketBlock.PLUGGED) != (i != 0)) {
                return false;
            }
        }
        if (TELEPHONE_JOURNAL.add("prises")) {
            for (int i = 0; i < 4; i++) {
                LOG.info("[visuels] prise {} : {}", NOMS_ETATS_PRISE[i], mc.level.getBlockState(PRISES_ETATS[i]));
            }
            for (int k = 0; k < 4; k++) {
                LOG.info("[visuels] prise du pilier {} : {}", NOMS_ORIENTATIONS[k], mc.level.getBlockState(PRISES_PILIER[k]));
            }
        }
        return true;
    }

    private static void preparerTelephone(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        BlockPos z = zoneTelephone;
        for (int dx = -4; dx <= 20; dx++) {
            for (int dz = -6; dz <= 8; dz++) {
                monde.setBlock(z.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int dy = 0; dy <= 4; dy++) {
                    monde.setBlock(z.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // Le mur de pierre (9 x 3), les quatre prises sur sa face sud, à hauteur d'œil.
        for (int x = 0; x <= 8; x++) {
            for (int y = 0; y <= 2; y++) {
                monde.setBlock(z.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int i = 0; i < 4; i++) {
            PRISES_ETATS[i] = z.offset(1 + 2 * i, 1, 1);
            poserPrise(monde, PRISES_ETATS[i], Direction.SOUTH, i);
        }
        // Des voisins pleins contre la prise vide : à gauche et au-dessus.
        monde.setBlock(PRISES_ETATS[0].west(), Blocks.STONE.defaultBlockState(), 3);
        monde.setBlock(PRISES_ETATS[0].above(), Blocks.STONE.defaultBlockState(), 3);
        // Le pilier, une prise en charge sur chacune de ses quatre faces.
        pilier = z.offset(14, 0, 0);
        for (int y = 0; y <= 2; y++) {
            monde.setBlock(pilier.above(y), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int k = 0; k < 4; k++) {
            PRISES_PILIER[k] = pilier.above().relative(ORIENTATIONS[k]);
            poserPrise(monde, PRISES_PILIER[k], ORIENTATIONS[k], 2);
        }
        telephonePose = true;
    }

    /** Une prise tournée vers {@code face} : 0 vide, 1 téléphone plein (arrêt), 2 en charge, 3 téléphone vide sans courant. */
    private static void poserPrise(ServerLevel monde, BlockPos p, Direction face, int etat) {
        monde.setBlock(p, dev.oneforallsurvival.phone.PhoneRegistry.WALL_SOCKET.get().defaultBlockState()
                .setValue(dev.oneforallsurvival.phone.WallSocketBlock.FACING, face)
                .setValue(dev.oneforallsurvival.phone.WallSocketBlock.PLUGGED, etat != 0), 3);
        if (etat == 0) {
            return;
        }
        int cap = dev.oneforallsurvival.phone.PhoneConfig.capacity();
        var prise = (dev.oneforallsurvival.phone.WallSocketBlockEntity) monde.getBlockEntity(p);
        prise.setPhone(telephone(etat == 1 ? cap : etat == 2 ? cap / 4 : 0));
        if (etat == 2) {
            PRISES_EN_CHARGE.add(p);
        }
    }

    /** Les prises en charge le restent : du courant à chaque tic, un téléphone ramené au quart quand il passe la moitié. */
    private static void tenirLesPrises(MinecraftServer serveur) {
        int cap = dev.oneforallsurvival.phone.PhoneConfig.capacity();
        for (BlockPos p : PRISES_EN_CHARGE) {
            if (serveur.overworld().getBlockEntity(p) instanceof dev.oneforallsurvival.phone.WallSocketBlockEntity prise) {
                prise.energy().receiveEnergy(400, false);
                if (dev.oneforallsurvival.phone.PhoneItem.stored(prise.phone()) > cap / 2) {
                    dev.oneforallsurvival.phone.PhoneItem.store(prise.phone(), cap / 4);
                }
            }
        }
    }

    private static PhoneStatePacket etatTelephone(PhoneStatePacket.Lock lock) {
        boolean ouvert = lock == PhoneStatePacket.Lock.UNLOCKED;
        return new PhoneStatePacket(4242, PhoneStatePacket.Kind.OPEN, lock, 64, 1_763_640_000_000L, 3600, "jungle", false,
                true, ouvert ? NOM_16 : "", 5, 0, PhoneStatePacket.Result.NONE, PhoneStatePacket.CloseReason.NONE,
                dev.oneforallsurvival.phone.PhoneNetView.NONE);
    }

    /**
     * L'écran de repli sur un état imposé : verrouillé avec deux chiffres saisis ; ouvert sur l'accueil ; ou, selon
     * {@code reglages}, les Réglages vides, remplis (code actuel, nouveau, confirmation), ou remplis avec l'effacement armé.
     */
    private static void repli(Minecraft mc, String nom, PhoneStatePacket.Lock lock, String reglages, String image) {
        etape("repli " + nom, () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            var ecran = new dev.oneforallsurvival.phone.client.PhoneFallbackScreen(etatTelephone(lock));
            mc.setScreen(ecran);
            if (lock == PhoneStatePacket.Lock.LOCKED) {
                ecran.charTyped('4', 0);
                ecran.charTyped('8', 0);
            }
            if (reglages != null) {
                ((net.minecraft.client.gui.components.Button) ecran.widgets().get("settings")).onPress();
                if (!"vides".equals(reglages)) {
                    ((net.minecraft.client.gui.components.EditBox) ecran.widgets().get("old")).setValue("1234");
                    ((net.minecraft.client.gui.components.EditBox) ecran.widgets().get("new")).setValue("5678");
                    ((net.minecraft.client.gui.components.EditBox) ecran.widgets().get("confirm")).setValue("5678");
                }
                if ("efface".equals(reglages)) {
                    ((net.minecraft.client.gui.components.Button) ecran.widgets().get("erase")).onPress();
                }
            }
        }, () -> {
            if (!(mc.screen instanceof dev.oneforallsurvival.phone.client.PhoneFallbackScreen ecran)) {
                return false;
            }
            if (TELEPHONE_JOURNAL.add(nom)) {
                StringBuilder b = new StringBuilder();
                ecran.widgets().forEach((k, w) -> b.append(' ').append(k).append('=').append(w.getX()).append(',')
                        .append(w.getY()).append(',').append(w.getWidth()).append('x').append(w.getHeight()));
                LOG.info("[visuels] repli {} : page {}, cadre {} dans une interface de {}x{} ;{}", nom, ecran.page(),
                        ecran.frameRect(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), b);
            }
            return true;
        }, 1000, image);
    }

    // ================================================================================================
    // Lot T2 : l'antenne, la carte SIM, l'outil de maintenance, le panneau de l'opérateur
    // ================================================================================================

    private static final String RES = "telephone_reseau-";
    private static BlockPos zoneReseau;
    private static volatile boolean reseauPose;
    private static volatile String echecReseau;
    /**
     * Les antennes posées par les vrais chemins (objet posé par un clic, fondation, réserve) : contre le mur en service,
     * sans courant, en panne ; la quatrième en service sous un bloc de pierre, entre deux pierres, contre le mur.
     */
    private static final BlockPos[] ANTENNES = new BlockPos[4];
    private static final dev.oneforallsurvival.machine.MachineState[] ETATS_ANTENNE = {
        dev.oneforallsurvival.machine.MachineState.RUNNING, dev.oneforallsurvival.machine.MachineState.NO_POWER,
        dev.oneforallsurvival.machine.MachineState.FAULT, dev.oneforallsurvival.machine.MachineState.RUNNING};
    private static final String[] NOMS_ANTENNE = {"en_service", "sans_courant", "en_panne", "sous_un_bloc"};
    private static final String OPERATEUR_DEMO = "Radio du Lagon";
    /** La souris tenue à ce point de l'interface (une bulle), ou nul : le coin (1, 1) de la fenêtre. */
    private static volatile double[] sourisGui;
    private static final java.util.Set<String> RESEAU_JOURNAL = new java.util.HashSet<>();

    /**
     * L'antenne posée contre un mur de pierre en service, sans courant et en panne (de jour, de nuit, de près, de
     * profil, de dessus), puis sous un bloc ; la carte SIM et l'outil de maintenance en main, dans la barre (à côté du
     * testeur et du soudeur) et l'inventaire, leurs infobulles (carte nommée, sans nom, vierge) ; le panneau de
     * l'opérateur, contenu le plus long et le plus court, ses trois onglets et une bulle de texte coupé, dans
     * l'interface de 320 × 240 et dans une grande (640 × 360) ; l'écran de repli (barre d'état, feuille « Carte SIM »,
     * panneau ouvert depuis l'appli « Antenne »). Activé par {@code -PvisuelsSeul=telephone_reseau}.
     */
    private static void construireTelephoneReseau(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        zoneReseau = origine.offset(-24, 0, 440);
        for (int i = 0; i < 3; i++) {
            ANTENNES[i] = zoneReseau.offset(2 + 3 * i, 0, 1);
        }
        ANTENNES[3] = zoneReseau.offset(13, 0, 1);
        etape("reseau pose", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            serveur.execute(() -> preparerTelephoneReseau(serveur));
        }, () -> reseauPose || echecReseau != null, 1000, null);
        etape("reseau etats", () -> {
            if (echecReseau != null) {
                throw new IllegalStateException("pose du réseau : " + echecReseau);
            }
            regarder(mc, ANTENNES[1], new Vec3(0.0, 1.0, 6.0), false);
        }, () -> antennesPretes(mc), 2500, null);

        // Les trois états contre le mur, de jour puis de nuit (le voyant doit se lire).
        vue(mc, RES + "antennes_jour.png", ANTENNES[1], new Vec3(0.0, 1.0, 6.0), false);
        vue(mc, RES + "antennes_nuit.png", ANTENNES[1], new Vec3(0.0, 1.0, 6.0), true);
        for (int i = 0; i < 4; i++) {
            int n = i;
            etape("vue antenne " + NOMS_ANTENNE[n], () -> regarder(mc, ANTENNES[n], new Vec3(1.0, 0.45, 1.8), false),
                    () -> true, 1500, RES + "antenne_" + NOMS_ANTENNE[n] + ".png");
        }
        etape("vue antenne nuit de pres", () -> regarder(mc, ANTENNES[0], new Vec3(0.9, 0.4, 1.6), true), () -> true,
                1500, RES + "antenne_en_service_nuit.png");
        // De profil (les haubans), de dessus (semelle et ancrages), sous le bloc vu d'en bas, et de dos (le mur ne doit
        // pas laisser voir à travers).
        etape("vue antenne profil", () -> regarder(mc, ANTENNES[0], new Vec3(2.0, 0.3, 0.05), false), () -> true, 1500,
                RES + "antenne_profil_haubans.png");
        etape("vue antenne dessus", () -> regarder(mc, ANTENNES[0], new Vec3(0.3, 2.3, 1.0), false), () -> true, 1500,
                RES + "antenne_dessus.png");
        etape("vue antenne sous le bloc d'en bas", () -> regarder(mc, ANTENNES[3], new Vec3(-0.6, -0.15, 1.6), false),
                () -> true, 1500, RES + "antenne_sous_un_bloc_bas.png");
        etape("vue antennes de haut", () -> regarder(mc, ANTENNES[1], new Vec3(1.5, 3.2, 4.0), false), () -> true, 1500,
                RES + "antennes_de_haut.png");

        // Les objets : la barre, l'outil à côté du testeur et du soudeur, les cartes.
        etape("reseau objets", () -> serveur.execute(() -> {
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            joueur.getInventory().clearContent();
            ItemStack[] barre = {new ItemStack(RepairRegistry.MAINTENANCE_TOOL.get()),
                new ItemStack(RepairRegistry.TESTER.get()), new ItemStack(RepairRegistry.WELDER.get()),
                carteSim("0612345678", OPERATEUR_DEMO, NOM_16), carteSim("0698765432", "Mairie", ""), carteSim(null, null, null),
                new ItemStack(dev.oneforallsurvival.phone.PhoneRegistry.ANTENNA_ITEM.get()), telephone(
                        dev.oneforallsurvival.phone.PhoneConfig.capacity())};
            for (int i = 0; i < barre.length; i++) {
                joueur.getInventory().setItem(i, barre[i]);
            }
            joueur.getInventory().setItem(9, carteSim("0611111111", NOM_24, "Alice"));
            joueur.getInventory().setItem(10, new ItemStack(Items.SHEARS));
            joueur.getInventory().setItem(11, new ItemStack(Items.PAPER));
            joueur.getInventory().setItem(12, new ItemStack(Items.LIGHTNING_ROD));
        }), () -> mc.player.getInventory().getItem(7).is(dev.oneforallsurvival.phone.PhoneRegistry.PHONE.get()), 500,
                null);
        etape("outil en main premiere personne", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.gameRenderer.setRenderHand(true);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.getInventory().selected = 0;
            BlockPos z = zoneReseau;
            serveur.execute(() -> {
                ServerLevel monde = serveur.overworld();
                ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                monde.setDayTime(6000);
                joueur.setNoGravity(true);
                joueur.teleportTo(monde, z.getX() + 5.5, z.getY(), z.getZ() + 5.5, 180F, 15F);
            });
        }, () -> true, 2000, RES + "outil_main_premiere_personne_et_barre.png");
        etape("carte en main premiere personne", () -> mc.player.getInventory().selected = 3, () -> true, 1200,
                RES + "carte_main_premiere_personne.png");
        etape("outil en main troisieme personne", () -> {
            mc.player.getInventory().selected = 0;
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            BlockPos z = zoneReseau;
            serveur.execute(() -> {
                ServerLevel monde = serveur.overworld();
                ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                joueur.teleportTo(monde, z.getX() + 5.5, z.getY(), z.getZ() + 4.5, 0F, 15F);
            });
        }, () -> true, 2000, RES + "outil_main_troisieme_personne.png");
        etape("infobulle outil", () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
            mc.setScreen(new Infobulle(new ItemStack(RepairRegistry.MAINTENANCE_TOOL.get())));
        }, () -> true, 800, RES + "outil_infobulle.png");
        etape("infobulle carte nommee", () -> mc.setScreen(new Infobulle(carteSim("0612345678", OPERATEUR_DEMO, NOM_16))),
                () -> true, 800, RES + "carte_infobulle_nommee.png");
        etape("infobulle carte sans nom", () -> mc.setScreen(new Infobulle(carteSim("0698765432", "Mairie", ""))),
                () -> true, 800, RES + "carte_infobulle_sans_nom.png");
        etape("infobulle carte vierge", () -> mc.setScreen(new Infobulle(carteSim(null, null, null))), () -> true, 800,
                RES + "carte_infobulle_vierge.png");
        etape("infobulle antenne objet", () -> mc.setScreen(new Infobulle(
                new ItemStack(dev.oneforallsurvival.phone.PhoneRegistry.ANTENNA_ITEM.get()))), () -> true, 800,
                RES + "antenne_objet_infobulle.png");
        etape("reseau survie", () -> {
            fermer(mc);
            serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0).setGameMode(GameType.SURVIVAL));
        }, () -> !mc.player.getAbilities().instabuild, 500, null);
        etape("reseau inventaire", () -> mc.setScreen(new InventoryScreen(mc.player)), () -> true, 800,
                RES + "inventaire.png");
        etape("reseau barre survie", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            mc.player.getInventory().selected = 0;
        }, () -> true, 800, RES + "barre_survie.png");
        etape("reseau retour creatif", () -> serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0)
                .setGameMode(GameType.CREATIVE)), () -> mc.player.getAbilities().instabuild, 300, null);

        // Le panneau du bloc : le joueur se tient près de l'antenne (le panneau se ferme au-delà de huit blocs).
        etape("reseau devant l'antenne", () -> {
            fermer(mc);
            mc.options.hideGui = false;
            BlockPos a = ANTENNES[0];
            serveur.execute(() -> {
                ServerLevel monde = serveur.overworld();
                ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                monde.setDayTime(6000);
                joueur.teleportTo(monde, a.getX() + 0.5, a.getY(), a.getZ() + 3.5, 180F, 20F);
            });
        }, () -> mc.player != null && mc.player.blockPosition().distSqr(ANTENNES[0]) < 30, 1000, null);
        var reseau = dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Tab.NETWORK;
        var cartes = dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Tab.CARDS;
        var tarif = dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Tab.TARIFF;
        taille(mc, 960, 720, 3, 320, 240);
        panneauOperateur(mc, "320 long reseau", true, reseau, false, RES + "panneau_320_long_reseau.png");
        panneauOperateur(mc, "320 long cartes", true, cartes, false, RES + "panneau_320_long_cartes.png");
        panneauOperateur(mc, "320 long tarif", true, tarif, false, RES + "panneau_320_long_tarif.png");
        panneauOperateur(mc, "320 bulle reseau", true, reseau, true, RES + "panneau_320_bulle_reseau.png");
        panneauOperateur(mc, "320 bulle cartes", true, cartes, true, RES + "panneau_320_bulle_cartes.png");
        panneauOperateur(mc, "320 court reseau", false, reseau, false, RES + "panneau_320_court_reseau.png");
        panneauOperateur(mc, "320 court cartes", false, cartes, false, RES + "panneau_320_court_cartes.png");
        panneauOperateur(mc, "320 court tarif", false, tarif, false, RES + "panneau_320_court_tarif.png");
        repliReseau(mc, "320 accueil", "accueil", RES + "repli_320_accueil.png");
        repliReseau(mc, "320 carte sim", "sim", RES + "repli_320_feuille_carte_sim.png");
        repliReseau(mc, "320 carte sim bulle", "bulle", RES + "repli_320_feuille_carte_sim_bulle.png");
        repliReseau(mc, "320 carte sim demandes", "demandes", RES + "repli_320_feuille_carte_sim_demandes.png");
        repliReseau(mc, "320 appli antenne", "appli", RES + "repli_320_appli_antenne.png");
        taille(mc, 1280, 720, 2, 640, 360);
        panneauOperateur(mc, "640 long reseau", true, reseau, false, RES + "panneau_640_long_reseau.png");
        panneauOperateur(mc, "640 long cartes", true, cartes, false, RES + "panneau_640_long_cartes.png");
        panneauOperateur(mc, "640 long tarif", true, tarif, false, RES + "panneau_640_long_tarif.png");
        panneauOperateur(mc, "640 bulle reseau", true, reseau, true, RES + "panneau_640_bulle_reseau.png");
        panneauOperateur(mc, "640 court reseau", false, reseau, false, RES + "panneau_640_court_reseau.png");
        repliReseau(mc, "640 accueil", "accueil", RES + "repli_640_accueil.png");
        repliReseau(mc, "640 carte sim", "sim", RES + "repli_640_feuille_carte_sim.png");
        repliReseau(mc, "640 appli antenne", "appli", RES + "repli_640_appli_antenne.png");
        etape("fin du reseau", () -> {
            sourisGui = null;
            fermer(mc);
        }, () -> true, 300, null);
    }

    /** Une carte SIM d'affichage (numéro, opérateur, titulaire écrits sur la pile, sans jeton) ; numéro nul : vierge. */
    private static ItemStack carteSim(String numero, String operateur, String titulaire) {
        ItemStack pile = new ItemStack(dev.oneforallsurvival.phone.PhoneRegistry.SIM_CARD.get());
        if (numero != null) {
            CompoundTag t = pile.getOrCreateTag();
            t.putString(dev.oneforallsurvival.phone.net.SimCardItem.TAG_NUMBER, numero);
            t.putString(dev.oneforallsurvival.phone.net.SimCardItem.TAG_OPERATOR, operateur);
            t.putString(dev.oneforallsurvival.phone.net.SimCardItem.TAG_HOLDER, titulaire);
        }
        return pile;
    }

    /** Vrai quand les quatre antennes sont, chez le client, dans l'état attendu ; le note au journal une fois. */
    private static boolean antennesPretes(Minecraft mc) {
        if (echecReseau != null) {
            throw new IllegalStateException("pose du réseau : " + echecReseau);
        }
        for (int i = 0; i < 4; i++) {
            BlockState s = mc.level.getBlockState(ANTENNES[i]);
            if (!s.is(dev.oneforallsurvival.phone.PhoneRegistry.ANTENNA.get())
                    || s.getValue(dev.oneforallsurvival.machine.ElectricMachineBlock.STATE) != ETATS_ANTENNE[i]) {
                return false;
            }
        }
        if (RESEAU_JOURNAL.add("antennes")) {
            for (int i = 0; i < 4; i++) {
                LOG.info("[visuels] antenne {} : {}", NOMS_ANTENNE[i], mc.level.getBlockState(ANTENNES[i]));
            }
        }
        return true;
    }

    private static void preparerTelephoneReseau(MinecraftServer serveur) {
        try {
            ServerLevel monde = serveur.overworld();
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            BlockPos z = zoneReseau;
            for (int dx = -4; dx <= 18; dx++) {
                for (int dz = -4; dz <= 9; dz++) {
                    monde.setBlock(z.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    for (int dy = 0; dy <= 5; dy++) {
                        monde.setBlock(z.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            // Le mur de pierre (16 x 3) au nord ; les antennes contre sa face sud.
            for (int x = 0; x <= 15; x++) {
                for (int y = 0; y <= 2; y++) {
                    monde.setBlock(z.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            OperateurDEssai.oublier(serveur, joueur.getUUID());
            poserAntenne(monde, joueur, ANTENNES[0]);
            var fonde = dev.oneforallsurvival.phone.net.AntennaBlock.found(joueur, ANTENNES[0], OPERATEUR_DEMO);
            if (fonde.isEmpty() || !fonde.get().founded()) {
                throw new IllegalStateException("fondation : " + fonde);
            }
            for (int i = 1; i < 4; i++) {
                poserAntenne(monde, joueur, ANTENNES[i]);
            }
            // La quatrième sous un bloc, entre deux pierres : aucune face voisine ne doit manquer.
            for (BlockPos p : new BlockPos[] {ANTENNES[3].above(), ANTENNES[3].west(), ANTENNES[3].east()}) {
                monde.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
            }
            ALIMENTES.add(ANTENNES[0]);
            ALIMENTES.add(ANTENNES[3]);
            ((ElectricMachineBlockEntity) monde.getBlockEntity(ANTENNES[2])).breakDown(FaultType.FUSE);
            reseauPose = true;
        } catch (RuntimeException e) {
            LOG.error("[visuels] pose du réseau", e);
            echecReseau = e.toString();
        }
    }

    /** Pose une antenne neuve en {@code p} par un clic du joueur sur le bloc du dessous (le vrai chemin de l'objet). */
    private static void poserAntenne(ServerLevel monde, ServerPlayer joueur, BlockPos p) {
        ItemStack avant = joueur.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack pile = new ItemStack(dev.oneforallsurvival.phone.PhoneRegistry.ANTENNA_ITEM.get());
        joueur.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = p.below();
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
        var r = pile.useOn(new net.minecraft.world.item.context.UseOnContext(monde, joueur, InteractionHand.MAIN_HAND,
                pile, tir));
        joueur.setItemInHand(InteractionHand.MAIN_HAND, avant);
        if (!r.consumesAction() || !(monde.getBlockEntity(p) instanceof dev.oneforallsurvival.phone.net.AntennaBlockEntity)) {
            throw new IllegalStateException("antenne non posée en " + p + " : " + r);
        }
    }

    /** Les textes du jeu pour mesurer le panneau comme l'écran le fait (langue du joueur, montants en coupures). */
    private static final dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Texts TEXTES_PANNEAU =
            new dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Texts() {
                @Override
                public String t(String key, Object... args) {
                    return net.minecraft.client.resources.language.I18n.get(key, args);
                }

                @Override
                public String coins(long amount) {
                    return dev.oneforallsurvival.economy.CoinMath.format(amount).getString();
                }

                @Override
                public String number(long value) {
                    String digits = Long.toString(Math.abs(value));
                    StringBuilder out = new StringBuilder();
                    for (int i = 0; i < digits.length(); i++) {
                        if (i > 0 && (digits.length() - i) % 3 == 0) {
                            out.append(' ');
                        }
                        out.append(digits.charAt(i));
                    }
                    return (value < 0 ? "-" : "") + out;
                }
            };

    /**
     * Le panneau du bloc sur une vue imposée chez le client (aucun geste n'est joué : le numéro 4343 n'existe pas au
     * serveur), sur l'onglet {@code onglet} ; avec {@code bulle}, la souris est posée sur le premier texte coupé de
     * l'onglet (ou du titre) pour montrer sa bulle ; le journal dit ce qui est coupé.
     */
    private static void panneauOperateur(Minecraft mc, String nom, boolean long_,
            dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.Tab onglet, boolean bulle, String image) {
        etape("panneau operateur " + nom, () -> {
            sourisGui = null;
            fermer(mc);
            mc.options.hideGui = false;
            var vue = long_ ? vueLaPlusLongue(dev.oneforallsurvival.phone.net.OperatorView.Source.BLOCK, ANTENNES[0])
                    : vueLaPlusCourte(dev.oneforallsurvival.phone.net.OperatorView.Source.BLOCK, ANTENNES[0]);
            var ecran = new dev.oneforallsurvival.phone.net.client.OperatorPanelScreen(vue);
            mc.setScreen(ecran);
            double[] c = ecran.centreOf(onglet, null, "");
            if (c != null) {
                ecran.mouseClicked(c[0], c[1], 0);
            }
            var l = dev.oneforallsurvival.phone.net.client.OperatorPanelLayout.of(ecran.width, ecran.height,
                    mc.font::width, vue, TEXTES_PANNEAU, "");
            List<String> coupes = new ArrayList<>();
            double[] cible = null;
            if (l.title().cut()) {
                coupes.add("titre « " + l.title().full() + " »");
            }
            if (!l.panes().isEmpty()) {
                var pane = l.panes().get(onglet.ordinal());
                for (var row : pane.rows()) {
                    for (var t : row.texts()) {
                        if (t.cut()) {
                            coupes.add("« " + t.full() + " » → « " + t.shown() + " »");
                            boolean visible = pane.list().contains(t.area());
                            if (visible && cible == null) {
                                cible = new double[] {t.x() + Math.min(t.room(), 20) / 2.0, t.y() + 4};
                            }
                        }
                    }
                }
            }
            if (cible == null && l.title().cut()) {
                cible = new double[] {l.title().x() + 4, l.title().y() + 4};
            }
            LOG.info("[visuels] panneau operateur {} : cadre {} dans une interface de {}x{}, onglet {} ; textes coupes : {}",
                    nom, l.frame(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), ecran.tab(),
                    coupes.isEmpty() ? "aucun" : String.join(", ", coupes));
            if (bulle) {
                if (cible == null) {
                    LOG.info("[visuels] panneau operateur {} : aucun texte coupe, pas de bulle", nom);
                } else {
                    sourisGui = cible;
                }
            }
        }, () -> mc.screen instanceof dev.oneforallsurvival.phone.net.client.OperatorPanelScreen, 1200, image);
    }

    /** Le contenu le plus long : nom de 24, 32 antennes, 32 cartes aux titulaires de 16, 32 demandes, 32 opérateurs. */
    private static dev.oneforallsurvival.phone.net.OperatorView vueLaPlusLongue(
            dev.oneforallsurvival.phone.net.OperatorView.Source source, BlockPos pos) {
        var max = dev.oneforallsurvival.phone.net.OperatorView.MAX_LIST;
        List<dev.oneforallsurvival.phone.net.OperatorView.AntennaLine> antennes = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            antennes.add(new dev.oneforallsurvival.phone.net.OperatorView.AntennaLine(new java.util.UUID(0x7A, i),
                    "minecraft:the_nether", new BlockPos(-29_999_999, -64, 29_999_999),
                    dev.oneforallsurvival.machine.MachineState.values()[i % 4],
                    new dev.oneforallsurvival.phone.net.AntennaLoad.Breakdown(9_999, 99_999, 99_999), 6_912_000_000L,
                    6_912_000_000L, i == 0, i % 2 == 1));
        }
        List<dev.oneforallsurvival.phone.net.OperatorView.CardLine> cartes = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            cartes.add(new dev.oneforallsurvival.phone.net.OperatorView.CardLine(new java.util.UUID(0x7B, i),
                    "0612345678", i % 5 == 0 ? "" : NOM_16, dev.oneforallsurvival.phone.net.SimCard.State.values()[i % 3]));
        }
        List<dev.oneforallsurvival.phone.net.OperatorView.RequestLine> demandes = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            demandes.add(new dev.oneforallsurvival.phone.net.OperatorView.RequestLine(new java.util.UUID(0x7C, i), NOM_16,
                    1_789_000_000_000L));
        }
        List<dev.oneforallsurvival.phone.net.PhoneNetwork.RelayState> relais = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            relais.add(new dev.oneforallsurvival.phone.net.PhoneNetwork.RelayState(String.format("%012x", i), NOM_24, 32,
                    i % 2 == 0 ? 0 : 16));
        }
        return new dev.oneforallsurvival.phone.net.OperatorView(source, pos, 4343, "0123456789ab", NOM_24,
                new dev.oneforallsurvival.phone.net.OperatorView.Rights(true, true, true, false), CINQ_COUPURES,
                CINQ_COUPURES - 1, CINQ_COUPURES, 99_999_999_999L, antennes, 99, 100, 3_200, cartes, demandes, relais);
    }

    /** Le contenu le plus court : nom de deux lettres, une antenne, aucune carte ni demande, un opérateur au relais. */
    private static dev.oneforallsurvival.phone.net.OperatorView vueLaPlusCourte(
            dev.oneforallsurvival.phone.net.OperatorView.Source source, BlockPos pos) {
        var antenne = new dev.oneforallsurvival.phone.net.OperatorView.AntennaLine(new java.util.UUID(0x7D, 0),
                "minecraft:overworld", new BlockPos(1, 64, 1), dev.oneforallsurvival.machine.MachineState.RUNNING,
                new dev.oneforallsurvival.phone.net.AntennaLoad.Breakdown(40, 0, 0), 0L, 0L, true, false);
        return new dev.oneforallsurvival.phone.net.OperatorView(source, pos, 4343, "0123456789ac", "Ab",
                new dev.oneforallsurvival.phone.net.OperatorView.Rights(true, true, false, false), 0L, null, 1L, 0L,
                List.of(antenne), 0, 1, 0, List.of(), List.of(), List.of(
                        new dev.oneforallsurvival.phone.net.PhoneNetwork.RelayState("0123456789ac", "Ab", 1, 1)));
    }

    /** Le téléphone ouvert, avec une ligne de l'opérateur de démonstration et des cartes dans l'inventaire. */
    private static PhoneStatePacket etatTelephoneReseau(boolean ligne) {
        List<dev.oneforallsurvival.phone.net.PhoneSims.SimChoice> choix = List.of(
                new dev.oneforallsurvival.phone.net.PhoneSims.SimChoice(3, "06 12 34 56 78", NOM_24,
                        dev.oneforallsurvival.phone.net.PhoneSims.Validity.ACTIVE),
                new dev.oneforallsurvival.phone.net.PhoneSims.SimChoice(4, "06 98 76 54 32", "Mairie",
                        dev.oneforallsurvival.phone.net.PhoneSims.Validity.SUSPENDED),
                new dev.oneforallsurvival.phone.net.PhoneSims.SimChoice(5, "06 11 22 33 44", "Radio Île",
                        dev.oneforallsurvival.phone.net.PhoneSims.Validity.UNKNOWN),
                new dev.oneforallsurvival.phone.net.PhoneSims.SimChoice(9, "06 55 44 33 22", "Radio Île",
                        dev.oneforallsurvival.phone.net.PhoneSims.Validity.DEACTIVATED));
        List<dev.oneforallsurvival.phone.PhoneNetView.OperatorChoice> demandables = List.of(
                new dev.oneforallsurvival.phone.PhoneNetView.OperatorChoice("0123456789ab", NOM_24, 4),
                new dev.oneforallsurvival.phone.PhoneNetView.OperatorChoice("ba9876543210", "Mairie", 2),
                new dev.oneforallsurvival.phone.PhoneNetView.OperatorChoice("aaaaaaaaaaaa", "Radio Île", 0));
        // Sans ligne (aucune carte logée), le téléphone propose de demander une carte : la liste des opérateurs.
        var net = ligne ? new dev.oneforallsurvival.phone.PhoneNetView(
                dev.oneforallsurvival.phone.net.PhoneSims.Validity.ACTIVE, NOM_24, 3, "06 12 34 56 78", NOM_16, true, true,
                choix, demandables)
                : new dev.oneforallsurvival.phone.PhoneNetView(dev.oneforallsurvival.phone.net.PhoneSims.Validity.NONE, "",
                        0, "", "", true, false, choix, demandables);
        return new PhoneStatePacket(4242, PhoneStatePacket.Kind.OPEN, PhoneStatePacket.Lock.UNLOCKED, 64,
                1_763_640_000_000L, 3600, "jungle", false, true, NOM_16, 5, 0, PhoneStatePacket.Result.NONE,
                PhoneStatePacket.CloseReason.NONE, net);
    }

    /**
     * L'écran de repli avec une ligne : {@code accueil} (barre d'état, bouton de l'appli « Antenne ») ; {@code sim} (la
     * feuille « Carte SIM ») ; {@code bulle} (la même, souris sur une carte au libellé coupé) ; {@code demandes} (la
     * même sans carte logée, demandes ouvertes) ; {@code appli} (le panneau de l'opérateur
     * ouvert par-dessus, source {@code PHONE}, contenu le plus long).
     */
    private static void repliReseau(Minecraft mc, String nom, String page, String image) {
        etape("repli reseau " + nom, () -> {
            sourisGui = null;
            fermer(mc);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            var ecran = new dev.oneforallsurvival.phone.client.PhoneFallbackScreen(
                    etatTelephoneReseau(!"demandes".equals(page)));
            if ("appli".equals(page)) {
                mc.setScreen(new dev.oneforallsurvival.phone.net.client.OperatorPanelScreen(
                        vueLaPlusLongue(dev.oneforallsurvival.phone.net.OperatorView.Source.PHONE, BlockPos.ZERO), ecran));
                return;
            }
            mc.setScreen(ecran);
            if (!"accueil".equals(page)) {
                ((net.minecraft.client.gui.components.Button) ecran.widgets().get("settings")).onPress();
                ((net.minecraft.client.gui.components.Button) ecran.widgets().get("sim")).onPress();
                if ("demandes".equals(page)) {
                    ((net.minecraft.client.gui.components.Button) ecran.widgets().get("sim-request")).onPress();
                }
                if ("bulle".equals(page)) {
                    // La souris sur la première carte de l'inventaire, dont le libellé est coupé : sa bulle.
                    var w = ecran.widgets().get("sim-choice-3");
                    sourisGui = new double[] {w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0};
                }
            }
        }, () -> {
            if (mc.screen instanceof dev.oneforallsurvival.phone.net.client.OperatorPanelScreen) {
                return true;
            }
            if (!(mc.screen instanceof dev.oneforallsurvival.phone.client.PhoneFallbackScreen ecran)) {
                return false;
            }
            if (RESEAU_JOURNAL.add(nom)) {
                StringBuilder b = new StringBuilder();
                ecran.widgets().forEach((k, w) -> b.append(' ').append(k).append('=').append(w.getX()).append(',')
                        .append(w.getY()).append(',').append(w.getWidth()).append('x').append(w.getHeight()));
                LOG.info("[visuels] repli reseau {} : page {}, cadre {} dans une interface de {}x{} ;{}", nom,
                        ecran.page(), ecran.frameRect(), mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight(), b);
            }
            return true;
        }, 1000, image);
    }
}
