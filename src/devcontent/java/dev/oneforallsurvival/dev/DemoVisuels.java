package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import dev.oneforallsurvival.food.DesalinatorBlockEntity;
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
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.repair.GalvanicBathBlockEntity;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.repair.client.GalvanicBathScreen;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import dev.oneforallsurvival.salvage.SalvageRegistry;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
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

        construireLot5(mc, sudEst);

        etape("grille", () -> {
            mc.options.hideGui = false;
            mc.setScreen(new Grille(OBJETS, 4));
        }, () -> true, 800, "objets_grille.png");
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
