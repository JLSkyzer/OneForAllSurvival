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
    /** {@code etal} : ne jouer que les écrans de l'étal (contrôle rapide d'une tâche), sans les autres captures. */
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
        mc.getToasts().clear();
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
        if ("etal".equals(SEUL)) {
            etal(mc);
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
        etal(mc);
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
        etape("etal vitrine defile", () -> clicEcran(mc, -1, 0, 0, -3.0), () -> true, 800, "etal_vitrine_ventes_defilee.png");
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
        be.addCash(Coin.GOLD, 1);
        long plusLong = 99_999_999L;
        be.setOffer(0, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.BREAD), 4, 30L));
        be.setOffer(1, new dev.oneforallsurvival.economy.stall.StallOffer(epee, 1, plusLong));
        be.setOffer(2, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.COBBLESTONE), 64, 100_000_000L));
        be.setOffer(3, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.WHEAT), 16, 1L));
        be.setOffer(4, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.IRON_INGOT), 8, 12_345L));
        be.setOffer(6, new dev.oneforallsurvival.economy.stall.StallOffer(new ItemStack(Items.EMERALD), 2, 777L));
        be.setBuyback(0, new dev.oneforallsurvival.economy.stall.StallBuyback(new ItemStack(Items.WHEAT), 8, 16L, 9_999));
        be.setBuyback(1, new dev.oneforallsurvival.economy.stall.StallBuyback(epee, 1, plusLong, 3));
        be.setBuyback(2, new dev.oneforallsurvival.economy.stall.StallBuyback(new ItemStack(Items.CARROT), 64, 64L, 1));
        // Le registre : chaque genre, des noms de 16 lettres, les montants les plus longs.
        long t = System.currentTimeMillis();
        java.util.UUID qui = java.util.UUID.nameUUIDFromBytes("Abcdefghijklmnop".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var K = dev.oneforallsurvival.economy.stall.StallLedger.Kind.values();
        for (int i = 0; i < 26; i++) {
            var genre = K[i % K.length];
            boolean objet = genre != dev.oneforallsurvival.economy.stall.StallLedger.Kind.CASH_IN
                    && genre != dev.oneforallsurvival.economy.stall.StallLedger.Kind.CASH_OUT;
            long montant = genre == dev.oneforallsurvival.economy.stall.StallLedger.Kind.SALE
                    || genre == dev.oneforallsurvival.economy.stall.StallLedger.Kind.BUYBACK
                    || !objet ? (i % 2 == 0 ? plusLong : 30L) : 0L;
            be.ledger().add(new dev.oneforallsurvival.economy.stall.StallLedger.Line(t - (26 - i) * 60_000L, qui,
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

    private static void souris(Minecraft mc) {
        try {
            for (String nom : new String[] {"xpos", "ypos"}) {
                Field f = net.minecraft.client.MouseHandler.class.getDeclaredField(nom);
                f.setAccessible(true);
                f.setDouble(mc.mouseHandler, 1);
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
}
