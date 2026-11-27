package dev.oneforallsurvival.dev.wiki;

import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.alimenter;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.bloc;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.constat;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.etat;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.etatServeur;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.ile;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.utiliser;

import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.EngineerBenchBlock;
import dev.oneforallsurvival.bench.client.BenchClientState;
import dev.oneforallsurvival.bench.client.BenchStockScreen;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import dev.oneforallsurvival.coast.CoastDetector;
import dev.oneforallsurvival.coast.CoastSavedData;
import dev.oneforallsurvival.dev.FilmTournage;
import dev.oneforallsurvival.dev.JoueurDeTest;
import dev.oneforallsurvival.explore.CacheBlockEntity;
import dev.oneforallsurvival.explore.DetectorItem;
import dev.oneforallsurvival.explore.ExploreConfig;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.explore.HeadlampItem;
import dev.oneforallsurvival.explore.MechanismBlockEntity;
import dev.oneforallsurvival.explore.ReceiverBlock;
import dev.oneforallsurvival.explore.ReceiverBlockEntity;
import dev.oneforallsurvival.explore.SignalMessage;
import dev.oneforallsurvival.fence.FenceRegistry;
import dev.oneforallsurvival.food.CanteenPotBlockEntity;
import dev.oneforallsurvival.food.DesalinatorBlockEntity;
import dev.oneforallsurvival.food.DryerBlockEntity;
import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import dev.oneforallsurvival.food.FridgeBlockEntity;
import dev.oneforallsurvival.food.IncubatorBlockEntity;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.LoadShedderBlockEntity;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.grid.WeatherForecast;
import dev.oneforallsurvival.grid.WeatherStationBlock;
import dev.oneforallsurvival.grid.client.LoadShedderScreen;
import dev.oneforallsurvival.grid.client.SubscriberNameScreen;
import dev.oneforallsurvival.light.LightRegistry;
import dev.oneforallsurvival.light.LighthouseBlock;
import dev.oneforallsurvival.light.LightConfig;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.repair.GalvanicBathBlockEntity;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.repair.WelderEnergy;
import dev.oneforallsurvival.role.RoleEvents;
import dev.oneforallsurvival.role.RoleSavedData;
import dev.oneforallsurvival.salvage.SalvageRegistry;
import dev.oneforallsurvival.town.ShowerHeadBlockEntity;
import dev.oneforallsurvival.town.TownRegistry;
import dev.oneforallsurvival.town.WaterHeaterBlockEntity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Le chapitre « Électricité et ingénieur » du wiki. Chaque machine est posée sur le bout d'île du chapitre Téléphone
 * ({@link ScenesTelephone#ile}) : une vue du bloc dans son décor, ses états (en marche, sans courant, en panne), son écran
 * avec ses repères, et une séquence du geste qui la fait servir. Les gestes passent par le vrai chemin du jeu : clic du
 * client ({@link Scene#clicDroit}, {@link FilmTournage#clicDroit}), clics de menu du client (Maj + clic :
 * {@code handleInventoryMouseClick} en {@link ClickType#QUICK_MOVE}), ou {@code ServerPlayerGameMode.useItemOn} pour un
 * objet tenu. Quand une durée réelle est trop longue pour une animation (séchage d'une minute, couvée de vingt), la
 * progression est posée près de la fin avant la séquence : la fin est réelle, le début ne l'est pas, et la légende du wiki
 * le dit.
 */
public final class ScenesElectricite implements Chapitre {

    private static final BlockPos A = new BlockPos(0, 0, 0);
    /** La machine « sans courant » et la machine « en panne » des trios d'états, de part et d'autre de la principale. */
    private static final BlockPos E = new BlockPos(-30, 0, 1);
    private static final BlockPos P = new BlockPos(30, 0, 1);
    private static final Vec3 VUE_ETAT = new Vec3(0.55, 0.3, 1.55);
    private static final String CREATIF = "createaddition:creative_energy";
    private static final List<JoueurDeTest> FIGURANTS = new ArrayList<>();
    private static final ItemStack[] RELEVE = new ItemStack[1];

    @Override
    public String id() {
        return "electricite";
    }

    @Override
    public void scenes(Atelier atelier) {
        couverture(atelier);
        metiers(atelier);
        etabli(atelier);
        usure(atelier);
        reparer(atelier);
        bain(atelier);
        epave(atelier);
        delestage(atelier);
        abonne(atelier);
        parafoudre(atelier);
        meteo(atelier);
        dessalinisateur(atelier);
        armoire(atelier);
        sechoir(atelier);
        couveuse(atelier);
        cantine(atelier);
        cloture(atelier);
        douche(atelier);
        phare(atelier);
        detecteur(atelier);
        lampe(atelier);
        recepteur(atelier);
        mecanismes(atelier);
    }

    // --- la couverture : une rangée de machines en marche ----------------------------------------------------------------

    private void couverture(Atelier atelier) {
        atelier.scene("couverture")
                .serveur("île et machines", lieu -> {
                    ile(lieu);
                    machine(lieu, new BlockPos(-6, 0, 0), BenchRegistry.ENGINEER_BENCH.get(), true);
                    machine(lieu, new BlockPos(-4, 0, 0), FoodRegistry.DESALINATOR.get(), true);
                    machine(lieu, new BlockPos(-2, 0, 0), FoodRegistry.DRYER.get(), true);
                    machine(lieu, new BlockPos(0, 0, 0), FoodRegistry.FRIDGE.get(), true);
                    machine(lieu, new BlockPos(2, 0, 0), TownRegistry.WATER_HEATER.get(), true);
                    machine(lieu, new BlockPos(4, 0, 0), FoodRegistry.INCUBATOR.get(), true);
                    machine(lieu, new BlockPos(6, 0, 0), GridRegistry.WEATHER_STATION.get(), true);
                })
                .attendre("machines en marche", () -> etat(new BlockPos(-2, 0, 0)) == MachineState.RUNNING
                        && etat(new BlockPos(4, 0, 0)) == MachineState.RUNNING)
                .camera(new Vec3(0.5, 1.8, 5.6), new Vec3(0.5, 0.6, 0.0))
                .prise("couverture");
    }

    // --- métiers : noms au-dessus des têtes, chat et liste des joueurs ---------------------------------------------------

    private void metiers(Atelier atelier) {
        atelier.scene("metiers")
                .serveur("île et trois joueurs", lieu -> {
                    ile(lieu);
                    quitterFigurants();
                    String[][] qui = {{"Lucas", "ingenieur"}, {"Maelle", "medecin"}, {"Noah", "policier"}, {"Ines", "citoyen"}};
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    for (int i = 0; i < qui.length; i++) {
                        Vec3 ou = lieu.v(-4.0 + 3 * i, 0, -2.5);
                        JoueurDeTest j = JoueurDeTest.rejoindre(lieu.monde(), qui[i][0], ou);
                        j.joueur().teleportTo(lieu.monde(), ou.x, ou.y, ou.z, 0F, 0F);
                        j.joueur().setYHeadRot(0F);
                        roles.book().assign(j.uuid(), qui[i][1]);
                        FIGURANTS.add(j);
                    }
                    roles.book().assign(lieu.joueur().getUUID(), "ingenieur");
                    roles.setDirty();
                    RoleEvents.refreshAll(lieu.serveur());
                })
                .camera(new Vec3(0.5, 2.0, 5.0), new Vec3(0.5, 1.8, -2.5))
                .client("noms visibles, rien d'autre", mc -> {
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements();
                    WikiStudio.reticule(false);
                    mc.gameRenderer.setRenderHand(false);
                })
                .pause(800)
                .prise("metiers")
                .client("chat visible et vidé", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    mc.options.chatVisibility().set(ChatVisiblity.FULL);
                    mc.options.broadcastOptions();
                    WikiStudio.elements(VanillaGuiOverlay.CHAT_PANEL);
                })
                .pause(600)
                .serveur("ils parlent", lieu -> {
                    String[] phrases = {"Le séchoir de la cantine fume, je passe avec le poste à souder.",
                        "Merci ! Il reste du courant au dispensaire ?", "Je surveille le disjoncteur de la mairie."};
                    for (int i = 0; i < phrases.length; i++) {
                        ServerPlayer j = FIGURANTS.get(i).joueur();
                        // Le format du chat du jeu (« <nom> message »), sans signature : aucune marque « non sûr ».
                        lieu.serveur().getPlayerList().broadcastSystemMessage(Component.translatable("chat.type.text",
                                j.getDisplayName(), Component.literal(phrases[i])), false);
                    }
                })
                .pause(1500)
                .echelle(3)
                .pause(400)
                .prise("metiers-chat")
                .client("liste des joueurs", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    WikiStudio.elements(VanillaGuiOverlay.PLAYER_LIST);
                    mc.options.keyPlayerList.setDown(true);
                })
                .echelle(2)
                .pause(800)
                .prise("metiers-tab")
                .client("rangement", mc -> {
                    mc.options.keyPlayerList.setDown(false);
                    mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                    mc.options.broadcastOptions();
                    WikiStudio.ath(mc, false);
                })
                .serveur("ils partent", lieu -> {
                    quitterFigurants();
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    roles.book().assign(lieu.joueur().getUUID(), "citoyen");
                    roles.setDirty();
                    RoleEvents.refreshAll(lieu.serveur());
                });
    }

    private static void quitterFigurants() {
        for (JoueurDeTest j : FIGURANTS) {
            if (j.connecte()) {
                j.quitter();
            }
        }
        FIGURANTS.clear();
    }

    // --- l'établi d'ingénieur ---------------------------------------------------------------------------------------------

    private static final ResourceLocation TESTEUR = new ResourceLocation("oneforallsurvival", "tester");
    private static final ResourceLocation POSTE = new ResourceLocation("oneforallsurvival", "welder");

    private void etabli(Atelier atelier) {
        boolean[] fait = new boolean[8];
        atelier.scene("etabli")
                .serveur("établi alimenté, ingrédients du testeur", lieu -> {
                    ile(lieu);
                    lieu.poser(0, 0, 0, BenchRegistry.ENGINEER_BENCH.get().defaultBlockState()
                            .setValue(EngineerBenchBlock.FACING, Direction.SOUTH));
                    lieu.poser(0, 0, -1, bloc(CREATIF).defaultBlockState());
                    ServerPlayer j = lieu.joueur();
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    roles.book().assign(j.getUUID(), "ingenieur");
                    roles.setDirty();
                    j.getInventory().add(new ItemStack(SalvageRegistry.INSTRUMENT_CORE.get(), 2));
                    j.getInventory().add(objet("create:electron_tube", 2));
                    j.getInventory().add(new ItemStack(SalvageRegistry.AVIATION_WIRE.get(), 4));
                    j.getInventory().add(objet("create:brass_sheet", 4));
                    j.getInventory().add(new ItemStack(SalvageRegistry.BATTERY_CELL.get(), 2));
                    j.getInventory().add(new ItemStack(Items.BLAZE_POWDER, 2));
                })
                .attendre("établi alimenté", () -> etat(A) == MachineState.RUNNING || etat(A) == MachineState.STOPPED)
                .camera(new Vec3(1.9, 1.5, 2.6), new Vec3(0.35, 0.45, 0.1))
                .repereBloc("plateau", A, 0.5, 1.0, 0.75)
                .repereBloc("courant", new BlockPos(0, 0, -1), 0.95, 0.75, 0.5)
                .prise("etabli-hero")
                .regarder(A, new Vec3(0.0, 0.7, 2.4))
                .ouvrirBloc(A, Direction.SOUTH, EngineerBenchScreen.class)
                .attendre("catalogue reçu", () -> !BenchClientState.catalog().isEmpty())
                .client("le testeur choisi", mc -> {
                    EngineerBenchScreen e = (EngineerBenchScreen) mc.screen;
                    for (var r : BenchClientState.catalog()) {
                        if (r.id().equals(TESTEUR)) {
                            e.setCategory(BenchClientState.modOf(r));
                        }
                    }
                    e.setSearchText("");
                    if (!e.selectById(TESTEUR)) {
                        throw new IllegalStateException("recette du testeur absente de la liste");
                    }
                })
                .pause(800)
                .repereInterface("onglets", mc -> zone(mc, "categories", 0.5, 0.85))
                .repereInterface("recherche", mc -> {
                    double[] l = zone(mc, "list", 0.80, 0.0);
                    return l == null ? null : new double[] {l[0], l[1] - 10};
                })
                .repereInterface("liste", mc -> zone(mc, "list", 0.93, 0.45))
                .repereInterface("cout", mc -> zone(mc, "detail", 0.5, 0.72))
                .repereInterface("crafter", mc -> {
                    int[] p = ((EngineerBenchScreen) mc.screen).hoverPoint("crafter");
                    return new double[] {p[0] - 34, p[1]};
                })
                .repereInterface("file", mc -> zone(mc, "queue", 0.62, 0.13))
                .repereInterface("stock", mc -> {
                    int[] p = ((EngineerBenchScreen) mc.screen).hoverPoint("stock");
                    return new double[] {p[0], p[1] + 16};
                })
                .repereInterface("vitesse", mc -> {
                    try {
                        int x = champPrive(mc.screen, "speedX");
                        int[] p = ((EngineerBenchScreen) mc.screen).hoverPoint("stock");
                        return new double[] {x - 9, p[1]};
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .prise("etabli-ecran")
                .client("recherche vidée", mc -> ((EngineerBenchScreen) mc.screen).setSearchText(""))
                .pause(500)
                .sequence("etabli-commande", 9_000, 200, null, (mc, ms) -> {
                    if (mc.screen instanceof EngineerBenchScreen e) {
                        String mot = "poste";
                        for (int k = 1; k <= mot.length(); k++) {
                            if (ms >= 500 + 250L * k && !fait[k - 1]) {
                                fait[k - 1] = true;
                                e.setSearchText(mot.substring(0, k));
                            }
                        }
                        if (ms >= 2200 && !fait[5]) {
                            fait[5] = true;
                            if (!e.selectById(POSTE)) {
                                throw new IllegalStateException("recette du poste à souder absente de la liste");
                            }
                        }
                        if (ms >= 3200 && !fait[6]) {
                            fait[6] = true;
                            int[] p = e.hoverPoint("crafter");
                            e.mouseClicked(p[0], p[1], 0);
                        }
                        if (ms >= 4200 && !fait[7] && BenchClientState.queue().isEmpty()
                                && BenchClientState.stockItems() >= 1) {
                            fait[7] = true;
                            int[] p = e.hoverPoint("stock");
                            e.mouseClicked(p[0], p[1], 0);
                        }
                    }
                })
                .attendre("stock ouvert", () -> Minecraft.getInstance().screen instanceof BenchStockScreen, 8_000)
                .pause(600)
                .prise("etabli-stock")
                .client("constat de l'établi", mc -> constat("établi : file " + BenchClientState.queue().size()
                        + ", objets en stock " + BenchClientState.stockItems()))
                .fermer();
    }

    /** Un point d'un panneau de l'établi ouvert (fractions de sa largeur et de sa hauteur), lu par réflexion. */
    @Nullable
    private static double[] zone(Minecraft mc, String panneau, double fx, double fy) {
        if (!(mc.screen instanceof EngineerBenchScreen e)) {
            return null;
        }
        try {
            Field f = EngineerBenchScreen.class.getDeclaredField(panneau);
            f.setAccessible(true);
            Object pane = f.get(e);
            int[] r = new int[4];
            String[] noms = {"x", "y", "w", "h"};
            for (int i = 0; i < 4; i++) {
                Field c = Class.forName("dev.oneforallsurvival.bench.client.BenchPane").getDeclaredField(noms[i]);
                c.setAccessible(true);
                r[i] = c.getInt(pane);
            }
            return new double[] {r[0] + r[2] * fx, r[1] + r[3] * fy};
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // --- usure et pannes ----------------------------------------------------------------------------------------------

    private static final BlockPos BON = new BlockPos(-2, 0, 0);
    private static final BlockPos FUME = new BlockPos(0, 0, 0);
    private static final BlockPos PANNE = new BlockPos(2, 0, 0);

    private void usure(Atelier atelier) {
        atelier.scene("usure")
                .serveur("trois séchoirs", lieu -> {
                    ile(lieu);
                    for (BlockPos p : new BlockPos[] {BON, FUME, PANNE}) {
                        machine(lieu, p, FoodRegistry.DRYER.get(), true);
                    }
                })
                .attendre("en marche", () -> etat(BON) == MachineState.RUNNING && etat(FUME) == MachineState.RUNNING
                        && etat(PANNE) == MachineState.RUNNING)
                .serveur("usés", lieu -> {
                    be(lieu, FUME, ElectricMachineBlockEntity.class).takeDamage(85);
                    be(lieu, PANNE, ElectricMachineBlockEntity.class).breakDown(FaultType.BEARING);
                })
                .attendre("en panne", () -> etat(PANNE) == MachineState.FAULT)
                .camera(new Vec3(0.5, 1.75, 4.4), new Vec3(0.5, 0.55, 0.0))
                .pause(2500)
                .repereBloc("bon", BON, 0.5, 1.05, 0.5)
                .repereBloc("fume", FUME, 0.5, 1.05, 0.5)
                .repereBloc("panne", PANNE, 0.5, 1.05, 0.5)
                .prise("usure-etats-a")
                .pause(330)
                .repereBloc("bon", BON, 0.5, 1.05, 0.5)
                .repereBloc("fume", FUME, 0.5, 1.05, 0.5)
                .repereBloc("panne", PANNE, 0.5, 1.05, 0.5)
                .prise("usure-etats-b")
                .pause(330)
                .repereBloc("bon", BON, 0.5, 1.05, 0.5)
                .repereBloc("fume", FUME, 0.5, 1.05, 0.5)
                .repereBloc("panne", PANNE, 0.5, 1.05, 0.5)
                .prise("usure-etats-c")
                .pause(330)
                .repereBloc("bon", BON, 0.5, 1.05, 0.5)
                .repereBloc("fume", FUME, 0.5, 1.05, 0.5)
                .repereBloc("panne", PANNE, 0.5, 1.05, 0.5)
                .prise("usure-etats-d")
                .pause(330)
                .repereBloc("bon", BON, 0.5, 1.05, 0.5)
                .repereBloc("fume", FUME, 0.5, 1.05, 0.5)
                .repereBloc("panne", PANNE, 0.5, 1.05, 0.5)
                .prise("usure-etats-e")
                .pause(330)
                .repereBloc("bon", BON, 0.5, 1.05, 0.5)
                .repereBloc("fume", FUME, 0.5, 1.05, 0.5)
                .repereBloc("panne", PANNE, 0.5, 1.05, 0.5)
                .prise("usure-etats-f")
                .client("chat visible et vidé", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    mc.options.chatVisibility().set(ChatVisiblity.FULL);
                    mc.options.broadcastOptions();
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements(VanillaGuiOverlay.CHAT_PANEL);
                    WikiStudio.reticule(false);
                    mc.gameRenderer.setRenderHand(false);
                })
                .pause(500)
                .serveur("le testeur sur le séchoir qui fume", lieu -> {
                    var r = utiliser(lieu, new ItemStack(RepairRegistry.TESTER.get()), FUME, Direction.SOUTH, false);
                    ElectricMachineBlockEntity m = be(lieu, FUME, ElectricMachineBlockEntity.class);
                    constat("testeur sur le séchoir à 15 % : " + r + ", pièce attendue " + m.expectedPart());
                })
                .pause(1200)
                .echelle(3)
                .pause(400)
                .prise("usure-testeur")
                .client("chat masqué", mc -> {
                    mc.gui.getChat().clearMessages(false);
                    mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                    mc.options.broadcastOptions();
                    WikiStudio.ath(mc, false);
                });
    }

    // --- réparer : poste à souder et pièce ---------------------------------------------------------------------------------

    private void reparer(Atelier atelier) {
        boolean[] clic = new boolean[1];
        atelier.scene("reparer")
                .serveur("séchoir en panne de fusible, poste et fusible en main", lieu -> {
                    ile(lieu);
                    machine(lieu, A, FoodRegistry.DRYER.get(), true);
                    ServerPlayer j = lieu.joueur();
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    roles.book().assign(j.getUUID(), "ingenieur");
                    roles.setDirty();
                    ItemStack poste = new ItemStack(RepairRegistry.WELDER.get());
                    WelderEnergy.store(poste, MachineConfig.welderCapacity());
                    j.getInventory().selected = 0;
                    j.getInventory().setItem(0, poste);
                    j.getInventory().setItem(1, new ItemStack(RepairRegistry.TESTER.get()));
                    j.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(RepairRegistry.FUSE.get(), 3));
                })
                .attendre("en marche", () -> etat(A) == MachineState.RUNNING)
                .serveur("panne de fusible", lieu -> be(lieu, A, ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE))
                .attendre("en panne", () -> etat(A) == MachineState.FAULT)
                .client("première case tenue", mc -> mc.player.getInventory().selected = 0)
                .attendre("poste en main", () -> {
                    var p = Minecraft.getInstance().player;
                    return p != null && p.getMainHandItem().is(RepairRegistry.WELDER.get())
                            && p.getOffhandItem().is(RepairRegistry.FUSE.get());
                })
                .camera(new Vec3(0.5, 1.55, 2.25), new Vec3(0.5, 0.45, 0.5))
                .client("main et barre visibles", mc -> {
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements(VanillaGuiOverlay.HOTBAR, VanillaGuiOverlay.RECORD_OVERLAY,
                            VanillaGuiOverlay.CROSSHAIR);
                })
                .pause(1500)
                .prise("reparer-avant")
                .sequence("reparation", 4_500, 100, null, (mc, ms) -> {
                    if (ms >= 1200 && !clic[0]) {
                        clic[0] = true;
                        FilmTournage.clicDroit(mc, WikiStudio.origineEnCours(), Direction.SOUTH);
                    }
                })
                .attendre("réparée", () -> etat(A) == MachineState.RUNNING, 5_000)
                .serveur("constat", lieu -> constat("après la réparation : " + etatServeur(lieu, A) + ", état "
                        + be(lieu, A, ElectricMachineBlockEntity.class).condition() + " %, poste "
                        + WelderEnergy.stored(lieu.joueur().getMainHandItem()) + " FE"))
                .client("interface masquée", mc -> WikiStudio.ath(mc, false));
    }

    // --- le bain galvanique ------------------------------------------------------------------------------------------------

    private static final BlockPos BAIN = new BlockPos(0, 1, 0);
    private static final BlockPos BAIN_ECRAN = new BlockPos(6, 0, 0);
    private static final BlockPos BAIN_MARCHE = new BlockPos(-45, 0, 1);

    private void bain(Atelier atelier) {
        boolean[] fait = new boolean[2];
        atelier.scene("bain")
                .serveur("bain entre ses trémies, bains d'essai", lieu -> {
                    ile(lieu);
                    ServerLevel monde = lieu.monde();
                    lieu.poser(0, 0, 0, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
                    lieu.poser(1, 0, 0, Blocks.CHEST.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                    lieu.poser(0, 1, 0, RepairRegistry.GALVANIC_BATH.get().defaultBlockState());
                    lieu.poser(0, 2, 0, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
                    lieu.poser(-1, 1, 0, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
                    lieu.poser(-1, 0, 0, Blocks.OAK_PLANKS.defaultBlockState());
                    alimenter(lieu, BAIN);
                    if (monde.getBlockEntity(lieu.p(0, 2, 0)) instanceof net.minecraft.world.Container c) {
                        c.setItem(0, new ItemStack(RepairRegistry.FUSE.get(), 8));
                    }
                    if (monde.getBlockEntity(lieu.p(-1, 1, 0)) instanceof net.minecraft.world.Container c) {
                        c.setItem(0, objet("create:zinc_ingot", 8));
                    }
                    // Le bain de l'écran : vide, alimenté ; ses pièces viennent de la barre du joueur (Maj + clic).
                    lieu.poser(6, 0, 0, RepairRegistry.GALVANIC_BATH.get().defaultBlockState());
                    alimenter(lieu, BAIN_ECRAN);
                    ServerPlayer j = lieu.joueur();
                    j.getInventory().setItem(0, new ItemStack(RepairRegistry.FUSE.get(), 4));
                    j.getInventory().setItem(1, objet("create:zinc_ingot", 4));
                    // Le trio d'états.
                    for (BlockPos p : new BlockPos[] {E, P}) {
                        lieu.poser(p.getX(), p.getY(), p.getZ(), RepairRegistry.GALVANIC_BATH.get().defaultBlockState());
                        GalvanicBathBlockEntity b = be(lieu, p, GalvanicBathBlockEntity.class);
                        b.inventory().setStackInSlot(GalvanicBathBlockEntity.SLOT_PIECE,
                                new ItemStack(RepairRegistry.COIL.get(), 16));
                        b.inventory().setStackInSlot(GalvanicBathBlockEntity.SLOT_ZINC, objet("create:zinc_ingot", 16));
                    }
                    alimenter(lieu, P);
                    machine(lieu, BAIN_MARCHE, RepairRegistry.GALVANIC_BATH.get(), true);
                })
                .attendre("bain en marche", () -> etat(BAIN) == MachineState.RUNNING, 15_000)
                .attendre("trio prêt", () -> etat(E) == MachineState.NO_POWER && etat(P) == MachineState.RUNNING
                        && etat(BAIN_MARCHE) == MachineState.RUNNING)
                .camera(new Vec3(1.9, 2.4, 3.1), new Vec3(0.3, 1.0, 0.2))
                .repereBloc("piece", new BlockPos(0, 2, 0), 0.85, 0.85, 0.85)
                .repereBloc("zinc", new BlockPos(-1, 1, 0), 0.5, 0.95, 0.9)
                .repereBloc("bain", BAIN, 0.95, 0.6, 0.95)
                .repereBloc("sortie", new BlockPos(1, 0, 0), 0.6, 0.9, 0.95)
                .prise("bain-hero")
                .serveur("trio : panne", lieu -> be(lieu, P, ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE))
                .attendre("panne vue", () -> etat(P) == MachineState.FAULT)
                .regarder(BAIN_MARCHE, VUE_ETAT)
                .prise("bain-marche")
                .regarder(E, VUE_ETAT)
                .prise("bain-sans-courant")
                .regarder(P, VUE_ETAT)
                .pause(1500)
                .prise("bain-panne")
                .regarder(BAIN_ECRAN, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(BAIN_ECRAN, Direction.SOUTH, AbstractContainerScreen.class)
                .pause(600)
                .sequence("bain-traitement", 12_500, 250, null, (mc, ms) -> {
                    if (ms >= 1000 && !fait[0]) {
                        fait[0] = true;
                        majClic(mc, 3 + 27);
                    }
                    if (ms >= 2000 && !fait[1]) {
                        fait[1] = true;
                        majClic(mc, 3 + 27 + 1);
                    }
                })
                .repereInterface("e-piece", mc -> gui(mc, 20, 30))
                .repereInterface("e-zinc", mc -> gui(mc, 20, 56))
                .repereInterface("e-sortie", mc -> gui(mc, 162, 43))
                .repereInterface("e-etat", mc -> gui(mc, 20, 77))
                .prise("bain-ecran")
                .fermer();
    }

    // --- l'épave ---------------------------------------------------------------------------------------------------------

    private static final BlockPos CABLAGE = new BlockPos(2, 0, -3);

    private void epave(Atelier atelier) {
        int[] t = {0};
        atelier.scene("epave")
                .serveur("débris de l'avion", lieu -> {
                    ile(lieu);
                    BlockState fuselage = SalvageRegistry.DEBRIS_FUSELAGE.get().defaultBlockState();
                    for (int x = -3; x <= 0; x++) {
                        lieu.poser(x, 0, -4, fuselage);
                    }
                    lieu.poser(-2, 1, -4, fuselage);
                    lieu.poser(-1, 1, -4, fuselage);
                    lieu.poser(0, 1, -4, SalvageRegistry.DEBRIS_INSTRUMENTS.get().defaultBlockState());
                    lieu.poser(2, 0, -3, SalvageRegistry.DEBRIS_WIRING.get().defaultBlockState());
                    lieu.poser(1, 0, -4, SalvageRegistry.DEBRIS_WIRING.get().defaultBlockState());
                    lieu.poser(-4, 0, -2, SalvageRegistry.DEBRIS_CABIN.get().defaultBlockState());
                    lieu.poser(-4, 0, -3, SalvageRegistry.DEBRIS_CABIN.get().defaultBlockState());
                    lieu.poser(-1, 0, -2, SalvageRegistry.DEBRIS_BATTERY.get().defaultBlockState());
                })
                .camera(new Vec3(-0.2, 2.2, 1.4), new Vec3(-0.5, 0.4, -3.6))
                .repereBloc("fuselage", new BlockPos(-2, 1, -4), 0.5, 0.95, 0.6)
                .repereBloc("instruments", new BlockPos(0, 1, -4), 0.5, 0.95, 0.6)
                .repereBloc("cablage", CABLAGE, 0.5, 0.95, 0.6)
                .repereBloc("batterie", new BlockPos(-1, 0, -2), 0.5, 0.95, 0.6)
                .repereBloc("cabine", new BlockPos(-4, 0, -2), 0.5, 0.95, 0.6)
                .prise("epave-hero")
                .serveur("survie, pioche en main", lieu -> {
                    ServerPlayer j = lieu.joueur();
                    j.getInventory().selected = 0;
                    j.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
                })
                .client("première case tenue", mc -> mc.player.getInventory().selected = 0)
                .camera(new Vec3(2.5, 1.62, -0.6), new Vec3(2.5, 0.35, -2.5))
                .serveur("mode survie", lieu -> {
                    lieu.joueur().setGameMode(GameType.SURVIVAL);
                    lieu.joueur().setNoGravity(true);
                })
                .client("main visible", mc -> {
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements(VanillaGuiOverlay.HOTBAR, VanillaGuiOverlay.CROSSHAIR);
                })
                .pause(800)
                .sequence("epave-demontage", 4_000, 100, lieu -> {
                    t[0] = 0;
                    BlockPos cible = lieu.p(CABLAGE.getX(), CABLAGE.getY(), CABLAGE.getZ());
                    lieu.tenir(monde -> {
                        int n = t[0]++;
                        if (n >= 10 && n < 40) {
                            monde.destroyBlockProgress(lieu.joueur().getId(), cible, (n - 10) / 3);
                        } else if (n == 40) {
                            monde.destroyBlockProgress(lieu.joueur().getId(), cible, -1);
                            boolean casse = lieu.joueur().gameMode.destroyBlock(cible);
                            constat("débris de câblage démonté à la pioche de fer, en survie : " + casse);
                        }
                    });
                }, (mc, ms) -> {
                    if (ms >= 500 && ms <= 2000 && (ms / 100) % 3 == 0) {
                        mc.player.swing(InteractionHand.MAIN_HAND);
                    }
                })
                .client("interface masquée", mc -> WikiStudio.ath(mc, false));
    }

    // --- le tableau de délestage -------------------------------------------------------------------------------------------

    private void delestage(Atelier atelier) {
        boolean[] clic = new boolean[1];
        atelier.scene("delestage")
                .serveur("tableau, alternateur derrière, trois séchoirs", lieu -> {
                    ile(lieu);
                    lieu.poser(0, 0, 0, GridRegistry.LOAD_SHEDDER.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                    alternateur(lieu, new BlockPos(0, 0, -1), Direction.NORTH, 32);
                    for (BlockPos p : new BlockPos[] {new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0),
                        new BlockPos(0, 1, 0)}) {
                        lieu.poser(p.getX(), p.getY(), p.getZ(), face(FoodRegistry.DRYER.get()));
                        DryerBlockEntity d = be(lieu, p, DryerBlockEntity.class);
                        d.inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT, new ItemStack(Items.BEEF, 64));
                        nbt(d, tag -> tag.putInt("energy", DryerBlockEntity.CAPACITY));
                    }
                })
                .attendre("tableau en marche", () -> etat(A) == MachineState.RUNNING, 20_000)
                .pause(3000)
                .camera(new Vec3(2.1, 1.9, 2.5), new Vec3(0.2, 0.6, 0.0))
                .repereBloc("tableau", A, 0.55, 0.45, 1.0)
                .repereBloc("entree", new BlockPos(0, 0, -1), 1.0, 0.98, 0.0)
                .repereBloc("dessus", new BlockPos(0, 1, 0), 0.9, 0.7, 0.95)
                .repereBloc("droite", new BlockPos(1, 0, 0), 0.9, 0.5, 0.95)
                .prise("delestage-hero")
                .regarder(A, new Vec3(0.0, 0.6, 2.4))
                .ouvrirBloc(A, Direction.SOUTH, LoadShedderScreen.class)
                .pause(1500)
                .repereInterface("etat", mc -> gui(mc, -9, 21))
                .repereInterface("rangs", mc -> gui(mc, -9, 73))
                .repereInterface("fe", mc -> gui(mc, 180, -9))
                .repereInterface("boutons", mc -> gui(mc, 239, 73))
                .prise("delestage-ecran")
                .sequence("delestage-priorite", 6_000, 200, null, (mc, ms) -> {
                    if (ms >= 1500 && !clic[0] && mc.screen instanceof LoadShedderScreen s) {
                        clic[0] = true;
                        // « Monter » sur la quatrième ligne (rang 4, servie en partie) : elle passe au rang 3.
                        s.mouseClicked(s.getGuiLeft() + 200 + 6, s.getGuiTop() + 46 + 3 * 18 + 6, 0);
                    }
                })
                .fermer();
    }

    /** Un alternateur de Crafts & Additions en {@code rel}, tourné vers {@code versMoteur}, un moteur créatif derrière. */
    static void alternateur(Lieu lieu, BlockPos rel, Direction versMoteur, int tours) {
        ServerLevel monde = lieu.monde();
        BlockPos alt = lieu.p(rel.getX(), rel.getY(), rel.getZ());
        BlockPos mot = alt.relative(versMoteur);
        monde.setBlock(alt, bloc("createaddition:alternator").defaultBlockState()
                .setValue(BlockStateProperties.FACING, versMoteur), 3);
        monde.setBlock(mot, bloc("create:creative_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, versMoteur.getOpposite()), 3);
        if (monde.getBlockEntity(mot) != null) {
            CompoundTag tag = monde.getBlockEntity(mot).saveWithoutMetadata();
            tag.putInt("ScrollValue", tours);
            monde.getBlockEntity(mot).load(tag);
        }
    }

    // --- compteur et disjoncteur ---------------------------------------------------------------------------------------------

    private static final BlockPos MAISON = new BlockPos(-3, 0, 0);
    private static final BlockPos COMPTEUR = new BlockPos(-2, 0, 0);
    private static final BlockPos DISJONCTEUR = new BlockPos(-1, 0, 0);

    private void abonne(Atelier atelier) {
        boolean[] clic = new boolean[2];
        atelier.scene("abonne")
                .serveur("source, disjoncteur, compteur, maison", lieu -> {
                    ile(lieu);
                    // Le courant va vers l'ouest : alternateur (0), disjoncteur (-1), compteur (-2), séchoir (-3).
                    alternateur(lieu, A, Direction.EAST, 64);
                    lieu.poser(-1, 0, 0, GridRegistry.BREAKER.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
                    lieu.poser(-2, 0, 0, GridRegistry.METER.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
                    lieu.poser(-3, 0, 0, face(FoodRegistry.DRYER.get()));
                    be(lieu, MAISON, DryerBlockEntity.class).inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT,
                            new ItemStack(Items.BEEF, 64));
                    for (int x = -5; x <= -4; x++) {
                        for (int y = 0; y <= 2; y++) {
                            lieu.poser(x, y, 0, Blocks.SPRUCE_PLANKS.defaultBlockState());
                            lieu.poser(x, y, -1, Blocks.SPRUCE_PLANKS.defaultBlockState());
                        }
                    }
                    RoleSavedData roles = RoleSavedData.get(lieu.serveur());
                    roles.book().assign(lieu.joueur().getUUID(), "ingenieur");
                    roles.setDirty();
                    // Les trois états du disjoncteur, posés à part (états du bloc).
                    boolean[][] etats = {{true, false}, {false, false}, {false, true}};
                    for (int i = 0; i < 3; i++) {
                        lieu.poser(4 + 3 * i, 0, 4, GridRegistry.BREAKER.get().defaultBlockState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                                .setValue(BreakerBlock.CLOSED, etats[i][0]).setValue(BreakerBlock.SEALED, etats[i][1]));
                    }
                })
                .attendre("compteur nu : rien ne passe", () -> etat(MAISON) == MachineState.NO_POWER, 20_000)
                .serveur("constat : compteur nu", lieu -> constat("compteur posé, non jumelé : maison "
                        + etatServeur(lieu, MAISON) + ", " + be(lieu, COMPTEUR, MeterBlockEntity.class).totalTransferred()
                        + " FE passés"))
                .regarder(COMPTEUR, new Vec3(0.3, 0.7, 2.2))
                .clicDroit(COMPTEUR, Direction.SOUTH)
                .attendre("écran du nom", () -> Minecraft.getInstance().screen instanceof SubscriberNameScreen)
                .client("nom tapé", mc -> {
                    for (char c : "InesLebrun".toCharArray()) {
                        mc.screen.charTyped(c, 0);
                    }
                })
                .pause(500)
                .repereInterface("champ", mc -> nomEcran(mc, true))
                .repereInterface("inscrire", mc -> nomEcran(mc, false))
                .prise("abonne-nom")
                .client("Inscrire", mc -> {
                    double[] b = nomEcran(mc, false);
                    mc.screen.mouseClicked(b[0], b[1] - 14, 0);
                })
                .attendre("écran fermé", () -> Minecraft.getInstance().screen == null)
                .serveur("raccordement d'essai (au lieu du jumelage au poste de fournisseur)", lieu -> {
                    MeterBlockEntity m = be(lieu, COMPTEUR, MeterBlockEntity.class);
                    constat("abonné inscrit par l'écran : « " + m.subscriber() + " »");
                    dev.oneforallsurvival.dev.FournisseurDEssai.raccorder(lieu.serveur(), m,
                            java.util.UUID.nameUUIDFromBytes("OfflinePlayer:InesLebrun".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            "InesLebrun");
                })
                .attendre("maison en marche", () -> etat(MAISON) == MachineState.RUNNING, 20_000)
                .camera(new Vec3(1.2, 1.9, 3.2), new Vec3(-1.4, 0.45, 0.2))
                .repereBloc("source", A, 0.5, 0.95, 0.9)
                .repereBloc("disjoncteur", DISJONCTEUR, 0.5, 1.0, 0.75)
                .repereBloc("compteur", COMPTEUR, 0.5, 1.0, 0.75)
                .repereBloc("maison", MAISON, 0.3, 0.9, 1.0)
                .prise("abonne-hero")
                .pause(3000)
                .serveur("relevé au testeur", lieu -> {
                    MeterBlockEntity m = be(lieu, COMPTEUR, MeterBlockEntity.class);
                    var r = utiliser(lieu, new ItemStack(RepairRegistry.TESTER.get()), COMPTEUR, Direction.SOUTH, false);
                    constat("compteur de « " + m.subscriber() + " » : " + m.totalTransferred() + " FE passés ; testeur " + r);
                    ServerPlayer j = lieu.joueur();
                    for (int i = 0; i < j.getInventory().getContainerSize(); i++) {
                        if (j.getInventory().getItem(i).is(GridRegistry.METER_READING.get())) {
                            RELEVE[0] = j.getInventory().getItem(i).copy();
                        }
                    }
                    if (RELEVE[0] == null) {
                        throw new IllegalStateException("aucun relevé reçu");
                    }
                })
                .echelle(3)
                .ouvrir("infobulle du relevé", () -> new Infobulle(RELEVE[0]))
                .pause(500)
                .prise("abonne-releve")
                .fermer()
                .echelle(2)
                // Face au levier : le disjoncteur seul, posé courant vers le nord (sa face regarde le sud).
                .regarder(new BlockPos(4, 0, 4), new Vec3(0.35, 0.45, 1.7))
                .client("barre d'action et main visibles", mc -> {
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements(VanillaGuiOverlay.RECORD_OVERLAY, VanillaGuiOverlay.CROSSHAIR);
                })
                .pause(600)
                .sequence("disjoncteur-bascule", 6_000, 150, null, (mc, ms) -> {
                    BlockPos d = WikiStudio.origineEnCours().offset(4, 0, 4);
                    if (ms >= 1000 && !clic[0]) {
                        clic[0] = true;
                        FilmTournage.tenirVide(mc);
                        FilmTournage.clicDroit(mc, d, Direction.SOUTH);
                    }
                    if (ms >= 3800 && !clic[1]) {
                        clic[1] = true;
                        FilmTournage.clicDroit(mc, d, Direction.SOUTH);
                    }
                })
                .client("interface masquée", mc -> WikiStudio.ath(mc, false))
                .regarder(new BlockPos(4, 0, 4), new Vec3(0.4, 0.3, 1.5))
                .prise("disjoncteur-enclenche")
                .regarder(new BlockPos(7, 0, 4), new Vec3(0.4, 0.3, 1.5))
                .prise("disjoncteur-coupe")
                .regarder(new BlockPos(10, 0, 4), new Vec3(0.4, 0.3, 1.5))
                .prise("disjoncteur-scelle");
    }

    /** Le champ ({@code champ}) ou le bouton « Inscrire » de l'écran du nom, en pixels d'interface, à côté du texte. */
    @Nullable
    static double[] nomEcran(Minecraft mc, boolean champ) {
        if (!(mc.screen instanceof SubscriberNameScreen s)) {
            return null;
        }
        try {
            int left = champPrive(s, "left");
            int top = champPrive(s, "top");
            if (champ) {
                return new double[] {left + 160, top + champPrive(s, "fieldY") + 8};
            }
            return new double[] {left + 10 + 43, top + champPrive(s, "buttonsY") + 16 + 6};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static int champPrive(Object o, String nom) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(nom);
        f.setAccessible(true);
        return f.getInt(o);
    }

    // --- le parafoudre --------------------------------------------------------------------------------------------------

    private void parafoudre(Atelier atelier) {
        atelier.scene("parafoudre")
                .serveur("parafoudre et deux machines", lieu -> {
                    ile(lieu);
                    lieu.poser(0, 0, 0, GridRegistry.SURGE_ARRESTER.get().defaultBlockState());
                    machine(lieu, new BlockPos(-3, 0, -1), FoodRegistry.DRYER.get(), true);
                    machine(lieu, new BlockPos(3, 0, -1), FoodRegistry.FRIDGE.get(), true);
                    lieu.poser(8, 0, 3, GridRegistry.SURGE_ARRESTER.get().defaultBlockState());
                    be(lieu, new BlockPos(8, 0, 3), ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE);
                })
                .attendre("machines en marche", () -> etat(new BlockPos(-3, 0, -1)) == MachineState.RUNNING)
                .camera(new Vec3(0.5, 1.9, 3.6), new Vec3(0.1, 0.6, -1.2))
                .repereBloc("parafoudre", A, 0.5, 1.0, 0.5)
                .repereBloc("machine", new BlockPos(-3, 0, -1), 0.5, 1.0, 0.5)
                .prise("parafoudre-hero")
                .sequence("parafoudre-eclair", 3_500, 100, lieu -> {
                    int[] t = {0};
                    lieu.tenir(monde -> {
                        if (t[0]++ == 15) {
                            LightningBolt b = EntityType.LIGHTNING_BOLT.create(monde);
                            b.moveTo(lieu.v(1.5, 0, -6.5));
                            monde.addFreshEntity(b);
                        } else if (t[0] == 40) {
                            constat("éclair à 5 blocs : séchoir " + be(lieu, new BlockPos(-3, 0, -1),
                                    ElectricMachineBlockEntity.class).condition() + " %, parafoudre "
                                    + be(lieu, A, ElectricMachineBlockEntity.class).condition() + " %");
                        }
                    });
                })
                .regarder(A, new Vec3(0.8, 0.5, 2.0))
                .prise("parafoudre-ok")
                .regarder(new BlockPos(8, 0, 3), new Vec3(0.8, 0.5, 2.0))
                .pause(1500)
                .prise("parafoudre-panne");
    }

    // --- la station météo -------------------------------------------------------------------------------------------------

    private void meteo(Atelier atelier) {
        atelier.scene("meteo")
                .serveur("station et lampe, station éteinte", lieu -> {
                    ile(lieu);
                    machine(lieu, A, GridRegistry.WEATHER_STATION.get(), true);
                    lieu.poser(1, 0, 0, Blocks.REDSTONE_LAMP.defaultBlockState());
                    lieu.poser(E.getX(), E.getY(), E.getZ(), face(GridRegistry.WEATHER_STATION.get()));
                    // Météo figée (doWeatherCycle à faux) : la station ne lit que le temps présent. Le cycle est
                    // rallumé le temps de la scène pour qu'elle annonce ce qui vient, ciel encore clair.
                    lieu.monde().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE)
                            .set(true, lieu.serveur());
                })
                .attendre("station en marche", () -> etat(A) == MachineState.RUNNING)
                .camera(new Vec3(1.4, 1.0, 2.9), new Vec3(0.9, 0.5, 0.5))
                .pause(1500)
                .prise("meteo-soleil")
                .serveur("pluie dans 2 500 tics, pas d'orage", lieu -> {
                    lieu.monde().setWeatherParameters(0, 2500, false, false);
                    ((ServerLevelData) lieu.monde().getLevelData()).setThunderTime(1_000_000);
                })
                .attendre("nuage", () -> tendance(A) == WeatherForecast.Trend.RAIN_SOON, 10_000)
                .pause(800)
                .prise("meteo-nuage")
                .serveur("orage dans 2 500 tics", lieu -> lieu.monde().setWeatherParameters(0, 2500, false, false))
                .attendre("éclair", () -> tendance(A) == WeatherForecast.Trend.STORM_SOON, 10_000)
                .pause(800)
                .prise("meteo-orage")
                .serveur("ciel dégagé, cycle coupé", lieu -> {
                    lieu.monde().setWeatherParameters(1_000_000, 0, false, false);
                    lieu.monde().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE)
                            .set(false, lieu.serveur());
                })
                .regarder(E, new Vec3(0.55, 0.3, 1.55))
                .prise("meteo-eteinte");
    }

    @Nullable
    private static WeatherForecast.Trend tendance(BlockPos rel) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos o = WikiStudio.origineEnCours();
        if (o == null || mc.level == null) {
            return null;
        }
        BlockState s = mc.level.getBlockState(o.offset(rel));
        return s.hasProperty(WeatherStationBlock.TREND) ? s.getValue(WeatherStationBlock.TREND) : null;
    }

    // --- le dessalinisateur --------------------------------------------------------------------------------------------------

    private static final BlockPos RIVE = new BlockPos(0, 0, -10);

    private void dessalinisateur(Atelier atelier) {
        boolean[] fait = new boolean[1];
        atelier.scene("dessalinisateur")
                .serveur("au bord de l'eau, zone côtière", lieu -> {
                    ile(lieu);
                    CoastSavedData.get(lieu.serveur()).remove("atelier");
                    CoastSavedData.get(lieu.serveur()).add("atelier", lieu.monde().dimension().location(),
                            lieu.p(-60, -10, -120), lieu.p(60, 30, 60));
                    CoastDetector.clearCache();
                    lieu.poser(RIVE.getX(), RIVE.getY(), RIVE.getZ(), face(FoodRegistry.DESALINATOR.get()));
                    DesalinatorBlockEntity d = be(lieu, RIVE, DesalinatorBlockEntity.class);
                    d.potableTank().fill(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 1500),
                            IFluidHandler.FluidAction.EXECUTE);
                    d.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 3));
                    alimenter(lieu, RIVE);
                    lieu.joueur().getInventory().setItem(0, new ItemStack(Items.GLASS_BOTTLE, 8));
                    for (BlockPos p : new BlockPos[] {E, P}) {
                        lieu.poser(p.getX(), p.getY(), p.getZ(), face(FoodRegistry.DESALINATOR.get()));
                        be(lieu, p, DesalinatorBlockEntity.class).waterTank()
                                .fill(new FluidStack(Fluids.WATER, 6000), IFluidHandler.FluidAction.EXECUTE);
                    }
                    alimenter(lieu, P);
                })
                .attendre("il dessale", () -> etat(RIVE) == MachineState.RUNNING, 20_000)
                .camera(new Vec3(1.7, 1.6, -7.6), new Vec3(0.3, 0.5, -10.4))
                .repereBloc("machine", RIVE, 0.9, 0.95, 0.95)
                .repereMonde("mer", new Vec3(3.0, -0.1, -15.5))
                .prise("dessal-hero")
                .regarder(RIVE, VUE_ETAT)
                .prise("dessal-marche")
                .attendre("trio prêt", () -> etat(E) == MachineState.NO_POWER && etat(P) == MachineState.RUNNING)
                .serveur("trio : panne", lieu -> be(lieu, P, ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE))
                .attendre("panne vue", () -> etat(P) == MachineState.FAULT)
                .regarder(E, VUE_ETAT)
                .prise("dessal-sans-courant")
                .regarder(P, VUE_ETAT)
                .pause(1500)
                .prise("dessal-panne")
                .regarder(RIVE, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(RIVE, Direction.SOUTH, AbstractContainerScreen.class)
                .pause(1200)
                .repereInterface("eau", mc -> gui(mc, -9, 50))
                .repereInterface("bouteille", mc -> gui(mc, 44, -9))
                .repereInterface("remplie", mc -> gui(mc, 112, -9))
                .repereInterface("potable", mc -> gui(mc, 185, 34))
                .repereInterface("sel", mc -> gui(mc, 185, 62))
                .prise("dessal-ecran")
                .sequence("dessal-bouteille", 5_000, 150, null, (mc, ms) -> {
                    if (ms >= 1000 && !fait[0]) {
                        fait[0] = true;
                        majClic(mc, 3 + 27);
                    }
                })
                .fermer()
                .serveur("zone côtière retirée", lieu -> {
                    CoastSavedData.get(lieu.serveur()).remove("atelier");
                    CoastDetector.clearCache();
                });
    }

    // --- l'armoire frigorifique ------------------------------------------------------------------------------------------

    private static final ItemStack[] INFOBULLES = new ItemStack[3];

    private void armoire(Atelier atelier) {
        atelier.scene("armoire")
                .serveur("cuisine : armoire contre un mur", lieu -> {
                    ile(lieu);
                    for (int x = -2; x <= 2; x++) {
                        for (int y = 0; y <= 2; y++) {
                            lieu.poser(x, y, -1, Blocks.BIRCH_PLANKS.defaultBlockState());
                        }
                    }
                    lieu.poser(-1, 0, 0, Blocks.BARREL.defaultBlockState());
                    lieu.poser(1, 0, 0, Blocks.SMOKER.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
                    machine(lieu, A, FoodRegistry.FRIDGE.get(), true);
                    for (BlockPos p : new BlockPos[] {E, P}) {
                        machine(lieu, p, FoodRegistry.FRIDGE.get(), p.equals(P));
                    }
                    long now = FoodStamp.now(lieu.monde());
                    long heure = 72_000L;
                    long[] ages = {heure, 8 * heure, 14 * heure};
                    for (int i = 0; i < 3; i++) {
                        ItemStack s = new ItemStack(Items.COOKED_BEEF, 6);
                        FoodStamp.setStamp(s, now - ages[i]);
                        INFOBULLES[i] = s;
                    }
                    constat("infobulles : viande datée de 1 h, 8 h et 14 h avant (frais " + FoodConfig.freshTicks()
                            + " tics, rassis " + FoodConfig.staleTicks() + " tics)");
                })
                .attendre("au froid", () -> etat(A) == MachineState.RUNNING && etat(P) == MachineState.RUNNING
                        && etat(E) == MachineState.NO_POWER)
                .camera(new Vec3(1.6, 1.7, 3.0), new Vec3(0.1, 0.6, 0.0))
                .prise("armoire-hero")
                .serveur("trio : panne", lieu -> be(lieu, P, ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE))
                .attendre("panne vue", () -> etat(P) == MachineState.FAULT)
                .regarder(A, VUE_ETAT)
                .prise("armoire-marche")
                .regarder(E, VUE_ETAT)
                .prise("armoire-sans-courant")
                .regarder(P, VUE_ETAT)
                .pause(1500)
                .prise("armoire-panne")
                .regarder(A, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(A, Direction.SOUTH, AbstractContainerScreen.class)
                .pause(800)
                .repereInterface("etat", mc -> gui(mc, -9, 21))
                .repereInterface("cases", mc -> gui(mc, -9, 57))
                .prise("armoire-ecran")
                .fermer()
                .echelle(3)
                .ouvrir("infobulle fraîche", () -> new Infobulle(INFOBULLES[0]))
                .pause(400)
                .prise("armoire-frais")
                .ouvrir("infobulle rassise", () -> new Infobulle(INFOBULLES[1]))
                .pause(400)
                .prise("armoire-rassis")
                .ouvrir("infobulle avariée", () -> new Infobulle(INFOBULLES[2]))
                .pause(400)
                .prise("armoire-avarie")
                .fermer();
    }

    // --- le séchoir ----------------------------------------------------------------------------------------------------

    private void sechoir(Atelier atelier) {
        atelier.scene("sechoir")
                .serveur("séchoir près de la plage", lieu -> {
                    ile(lieu);
                    machine(lieu, A, FoodRegistry.DRYER.get(), true);
                    lieu.poser(-1, 0, -1, Blocks.HAY_BLOCK.defaultBlockState());
                    for (BlockPos p : new BlockPos[] {E, P}) {
                        machine(lieu, p, FoodRegistry.DRYER.get(), p.equals(P));
                    }
                })
                .attendre("il sèche", () -> etat(A) == MachineState.RUNNING && etat(P) == MachineState.RUNNING
                        && etat(E) == MachineState.NO_POWER)
                .camera(new Vec3(1.7, 1.5, 2.6), new Vec3(0.2, 0.5, 0.0))
                .prise("sechoir-hero")
                .serveur("trio : panne", lieu -> be(lieu, P, ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE))
                .attendre("panne vue", () -> etat(P) == MachineState.FAULT)
                .regarder(A, VUE_ETAT)
                .prise("sechoir-marche")
                .regarder(E, VUE_ETAT)
                .prise("sechoir-sans-courant")
                .regarder(P, VUE_ETAT)
                .pause(1500)
                .prise("sechoir-panne")
                .serveur("sortie vidée, séchage aux trois quarts", lieu -> {
                    DryerBlockEntity d = be(lieu, A, DryerBlockEntity.class);
                    d.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    nbt(d, tag -> tag.putInt("progress", 900));
                })
                .regarder(A, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(A, Direction.SOUTH, AbstractContainerScreen.class)
                .pause(300)
                .repereInterface("aliment", mc -> gui(mc, 20, 30))
                .repereInterface("sel", mc -> gui(mc, 20, 56))
                .repereInterface("sortie", mc -> gui(mc, 162, 43))
                .prise("sechoir-ecran")
                .serveur("fin du séchage proche (2 s)", lieu -> nbt(be(lieu, A, DryerBlockEntity.class),
                        tag -> tag.putInt("progress", 1160)))
                .sequence("sechoir-fin", 4_500, 150)
                .fermer();
    }

    // --- la couveuse ---------------------------------------------------------------------------------------------------

    private void couveuse(Atelier atelier) {
        atelier.scene("couveuse")
                .serveur("couveuse dans la paille, coupure en cours à côté", lieu -> {
                    ile(lieu);
                    machine(lieu, A, FoodRegistry.INCUBATOR.get(), true);
                    lieu.poser(-1, 0, 0, Blocks.HAY_BLOCK.defaultBlockState());
                    lieu.poser(1, 0, -1, Blocks.HAY_BLOCK.defaultBlockState());
                    int total = FoodConfig.incubatorHatchTicks();
                    nbt(be(lieu, A, IncubatorBlockEntity.class), tag -> tag.putIntArray("progress",
                            new int[] {total / 8, total / 2, total * 3 / 4, total / 4}));
                    machine(lieu, E, FoodRegistry.INCUBATOR.get(), false);
                    nbt(be(lieu, E, IncubatorBlockEntity.class), tag -> tag.putIntArray("progress",
                            new int[] {total / 3, total / 3, total / 3, total / 3}));
                    machine(lieu, P, FoodRegistry.INCUBATOR.get(), true);
                })
                .regarder(E, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(E, Direction.SOUTH, AbstractContainerScreen.class)
                .pause(4000)
                .repereInterface("compte", mc -> gui(mc, -9, 64))
                .prise("couveuse-coupure")
                .fermer()
                .regarder(E, VUE_ETAT)
                .prise("couveuse-sans-courant")
                .attendre("elle couve", () -> etat(A) == MachineState.RUNNING && etat(P) == MachineState.RUNNING)
                .camera(new Vec3(1.6, 1.5, 2.6), new Vec3(0.2, 0.5, 0.0))
                .prise("couveuse-hero")
                .regarder(A, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(A, Direction.SOUTH, AbstractContainerScreen.class)
                .pause(600)
                .repereInterface("places", mc -> gui(mc, 185, 28))
                .repereInterface("barres", mc -> gui(mc, 185, 45))
                .repereInterface("etat", mc -> gui(mc, -9, 56))
                .prise("couveuse-ecran")
                .fermer()
                .serveur("premier oeuf à 2 s de l'éclosion", lieu -> {
                    int total = FoodConfig.incubatorHatchTicks();
                    nbt(be(lieu, A, IncubatorBlockEntity.class), tag -> {
                        int[] p = tag.getIntArray("progress");
                        p[0] = total - 50;
                        tag.putIntArray("progress", p);
                    });
                })
                .regarder(A, new Vec3(0.8, 1.1, 2.0))
                .sequence("couveuse-eclosion", 5_000, 150)
                .serveur("trio : panne", lieu -> be(lieu, P, ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE))
                .attendre("panne vue", () -> etat(P) == MachineState.FAULT)
                .regarder(A, VUE_ETAT)
                .prise("couveuse-marche")
                .regarder(P, VUE_ETAT)
                .pause(1500)
                .prise("couveuse-panne");
    }

    // --- la cuisine de cantine -------------------------------------------------------------------------------------------

    private static final BlockPos PLAQUE = new BlockPos(2, 0, 0);

    private void cantine(Atelier atelier) {
        atelier.scene("cantine")
                .serveur("marmite de cantine et plaque", lieu -> {
                    ile(lieu);
                    for (int x = -2; x <= 3; x++) {
                        for (int y = 0; y <= 2; y++) {
                            lieu.poser(x, y, -1, Blocks.STONE_BRICKS.defaultBlockState());
                        }
                    }
                    machine(lieu, A, FoodRegistry.CANTEEN_POT.get(), true);
                    lieu.poser(PLAQUE.getX(), PLAQUE.getY(), PLAQUE.getZ(), FoodRegistry.HOT_PLATE.get().defaultBlockState());
                    lieu.poser(PLAQUE.getX(), 1, PLAQUE.getZ(), bloc("farmersdelight:cooking_pot").defaultBlockState());
                    alimenter(lieu, PLAQUE);
                    for (BlockPos p : new BlockPos[] {E, P}) {
                        machine(lieu, p, FoodRegistry.CANTEEN_POT.get(), p.equals(P));
                    }
                })
                .attendre("elle cuit", () -> etat(A) == MachineState.RUNNING && etat(PLAQUE) == MachineState.RUNNING
                        && etat(P) == MachineState.RUNNING && etat(E) == MachineState.NO_POWER)
                .camera(new Vec3(2.6, 1.9, 3.0), new Vec3(0.9, 0.6, 0.0))
                .repereBloc("marmite", A, 0.5, 1.0, 0.9)
                .repereBloc("plaque", PLAQUE, 0.9, 0.3, 0.95)
                .repereBloc("ustensile", new BlockPos(2, 1, 0), 0.9, 0.5, 0.9)
                .prise("cantine-hero")
                .serveur("trio : panne", lieu -> be(lieu, P, ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE))
                .attendre("panne vue", () -> etat(P) == MachineState.FAULT)
                .regarder(A, VUE_ETAT)
                .prise("cantine-marche")
                .regarder(E, VUE_ETAT)
                .prise("cantine-sans-courant")
                .regarder(P, VUE_ETAT)
                .pause(1500)
                .prise("cantine-panne")
                .regarder(A, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(A, Direction.SOUTH, AbstractContainerScreen.class)
                .pause(100)
                .repereInterface("ingredients", mc -> gui(mc, 15, 30))
                .repereInterface("bols", mc -> gui(mc, 15, 68))
                .repereInterface("sortie", mc -> gui(mc, 162, 35))
                .prise("cantine-ecran")
                .serveur("sortie vidée, fin de cuisson proche (1 s)", lieu -> {
                    CanteenPotBlockEntity m = be(lieu, A, CanteenPotBlockEntity.class);
                    m.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
                    nbt(m, tag -> tag.putInt("progress", 175));
                })
                .sequence("cantine-fournee", 3_500, 150)
                .fermer();
    }

    // --- la clôture électrique ------------------------------------------------------------------------------------------

    private void cloture(Atelier atelier) {
        int[] t = {0};
        atelier.scene("cloture")
                .serveur("électrificateur, ligne, vache", lieu -> {
                    ile(lieu);
                    machine(lieu, new BlockPos(-5, 0, -3), FenceRegistry.ELECTRIFIER.get(), true);
                    for (int x = -4; x <= 4; x++) {
                        lieu.poser(x, 0, -3, FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState());
                    }
                    for (int z = -4; z >= -7; z--) {
                        lieu.poser(4, 0, z, FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState());
                    }
                    Cow vache = EntityType.COW.create(lieu.monde());
                    Vec3 ou = lieu.v(0.5, 0, -5.0);
                    vache.moveTo(ou.x, ou.y, ou.z, 0F, 0F);
                    vache.setPersistenceRequired();
                    vache.setCustomName(null);
                    lieu.monde().addFreshEntity(vache);
                    for (BlockPos p : new BlockPos[] {E, P}) {
                        machine(lieu, p, FenceRegistry.ELECTRIFIER.get(), p.equals(P));
                        lieu.poser(p.getX() + 1, 0, p.getZ(), FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState());
                        lieu.poser(p.getX() + 2, 0, p.getZ(), FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState());
                    }
                })
                .attendre("ligne sous tension", () -> etat(new BlockPos(-5, 0, -3)) == MachineState.RUNNING
                        && etat(P) == MachineState.RUNNING && etat(E) == MachineState.NO_POWER, 15_000)
                .camera(new Vec3(-3.4, 1.9, 0.9), new Vec3(-1.6, 0.5, -4.0))
                .repereBloc("electrificateur", new BlockPos(-5, 0, -3), 0.5, 1.0, 0.9)
                .repereBloc("cloture", new BlockPos(-2, 0, -3), 0.5, 1.3, 0.6)
                .prise("cloture-hero")
                .sequence("cloture-choc", 5_000, 100, lieu -> {
                    t[0] = 0;
                    lieu.tenir(monde -> {
                        int n = t[0]++;
                        if ((n >= 10 && n < 22) || (n >= 50 && n < 62)) {
                            for (Cow c : monde.getEntitiesOfClass(Cow.class, new net.minecraft.world.phys.AABB(
                                    lieu.p(-6, -1, -9)).inflate(12))) {
                                c.setDeltaMovement(0, c.getDeltaMovement().y, 0.22);
                            }
                        }
                    });
                })
                .serveur("trio : panne", lieu -> be(lieu, P, ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE))
                .attendre("panne vue", () -> etat(P) == MachineState.FAULT)
                .regarder(new BlockPos(-5, 0, -3), VUE_ETAT)
                .prise("cloture-marche")
                .regarder(E, VUE_ETAT)
                .prise("cloture-sans-courant")
                .regarder(P, VUE_ETAT)
                .pause(1500)
                .prise("cloture-panne");
    }

    // --- chauffe-eau et douche ------------------------------------------------------------------------------------------

    private static final BlockPos DESAL = new BlockPos(-3, 2, 0);
    private static final BlockPos CHAUFFE = new BlockPos(1, 2, 0);
    private static final BlockPos CHAUFFE_MARCHE = new BlockPos(-45, 0, 1);
    private static final BlockPos POMME = new BlockPos(6, 2, 0);
    private static final int[] POMME_MB = new int[1];

    private void douche(Atelier atelier) {
        atelier.scene("douche")
                .serveur("terrasse : puits, pompes, dessalinisateur, chauffe-eau, pomme", lieu -> {
                    ile(lieu);
                    // Tout sur une terrasse de pierre (y 0 et 1), chaîne horizontale à y = 2 : aucun tuyau vertical, aucun
                    // bout de tuyau ouvert. La pomme dépasse au bout de la terrasse, à 2 blocs au-dessus de l'herbe.
                    for (int x = -9; x <= 4; x++) {
                        for (int z = -2; z <= 2; z++) {
                            for (int y = 0; y <= 1; y++) {
                                lieu.poser(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                            }
                        }
                    }
                    Block tuyau = bloc("create:fluid_pipe");
                    int ox = -8;
                    for (int[] mur : new int[][] {{-1, 0}, {0, -1}, {0, 1}, {1, -2}, {1, 2}, {2, -1}, {2, 1}}) {
                        lieu.poser(ox + mur[0], 2, mur[1], Blocks.STONE.defaultBlockState());
                    }
                    for (int[] source : new int[][] {{0, 0}, {1, -1}, {1, 1}, {1, 0}}) {
                        lieu.poser(ox + source[0], 2, source[1], Blocks.WATER.defaultBlockState());
                    }
                    for (int x : new int[] {-6, -4, -2, 0, 2, 4, 5}) {
                        lieu.poser(x, 2, 0, tuyau.defaultBlockState());
                    }
                    for (int x : new int[] {-5, -1, 3}) {
                        pompe(lieu, x, 2, 0, Direction.EAST);
                        moteur(lieu, new BlockPos(x, 3, 0), Direction.Axis.X, new BlockPos(x - 1, 3, 0), Direction.EAST);
                    }
                    lieu.poser(DESAL.getX(), DESAL.getY(), 0, face(FoodRegistry.DESALINATOR.get()));
                    lieu.poser(DESAL.getX(), DESAL.getY(), -1, bloc(CREATIF).defaultBlockState());
                    lieu.poser(CHAUFFE.getX(), CHAUFFE.getY(), 0, face(TownRegistry.WATER_HEATER.get()));
                    lieu.poser(CHAUFFE.getX(), CHAUFFE.getY(), -1, bloc(CREATIF).defaultBlockState());
                    lieu.poser(POMME.getX(), POMME.getY(), POMME.getZ(), TownRegistry.SHOWER_HEAD.get().defaultBlockState());
                    machine(lieu, CHAUFFE_MARCHE, TownRegistry.WATER_HEATER.get(), true);
                    for (BlockPos p : new BlockPos[] {E, P}) {
                        machine(lieu, p, TownRegistry.WATER_HEATER.get(), p.equals(P));
                    }
                    constat("chaîne de la douche posée (tuyaux et pompes de Create, moteurs créatifs)");
                    int[] t = {0};
                    POMME_MB[0] = 0;
                    lieu.tenir(m -> {
                        if (t[0]++ % 20 == 0) {
                            DesalinatorBlockEntity d = be(lieu, DESAL, DesalinatorBlockEntity.class);
                            WaterHeaterBlockEntity w = be(lieu, CHAUFFE, WaterHeaterBlockEntity.class);
                            ShowerHeadBlockEntity sh = be(lieu, POMME, ShowerHeadBlockEntity.class);
                            POMME_MB[0] = sh.tank().getFluidAmount();
                            if (t[0] % 400 != 1) {
                                return;
                            }
                            constat("douche, tic " + t[0] + " : dessalinisateur eau " + d.waterTank().getFluidAmount()
                                    + " potable " + d.potableTank().getFluidAmount() + " (" + etatServeur(lieu, DESAL)
                                    + ") | chauffe-eau potable " + w.potable().getFluidAmount() + " chaude "
                                    + w.hot().getFluidAmount() + " (" + etatServeur(lieu, CHAUFFE) + ") | pomme "
                                    + sh.tank().getFluidAmount());
                        }
                    });
                })
                // Lu par le relevé du serveur : un ServerLevel ne rend pas ses entités de bloc au fil du rendu.
                // Vérification : trois minutes de transfert à partir de cuves vides (relevés au journal).
                .attendre("eau chaude dans la pomme", () -> POMME_MB[0] >= 200, 120_000)
                .pause(20_000)
                .pause(20_000)
                .pause(20_000)
                .pause(20_000)
                .pause(20_000)
                .pause(20_000)
                .pause(20_000)
                .pause(20_000)
                .pause(20_000)
                .camera(new Vec3(-1.0, 4.3, 7.4), new Vec3(-1.5, 2.3, 0.0))
                .repereBloc("puits", new BlockPos(-7, 2, 0), 0.5, 1.0, 0.5)
                .repereBloc("dessalinisateur", DESAL, 0.5, 1.0, 0.95)
                .repereBloc("chauffe-eau", CHAUFFE, 0.5, 0.95, 0.95)
                .repereBloc("pomme", POMME, 0.5, 0.1, 0.9)
                .prise("douche-chaine")
                .serveur("chauffe-eau du trio regarnis (la minute d'attente les a vidés)", lieu -> {
                    for (BlockPos p : new BlockPos[] {CHAUFFE_MARCHE, P}) {
                        WaterHeaterBlockEntity w = be(lieu, p, WaterHeaterBlockEntity.class);
                        w.hot().drain(4000, IFluidHandler.FluidAction.EXECUTE);
                        w.potable().fill(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 4000),
                                IFluidHandler.FluidAction.EXECUTE);
                    }
                })
                .attendre("chauffe-eau du trio en marche", () -> etat(CHAUFFE_MARCHE) == MachineState.RUNNING
                        && etat(P) == MachineState.RUNNING)
                .regarder(CHAUFFE_MARCHE, VUE_ETAT)
                .prise("chauffe-eau-marche")
                .attendre("trio prêt", () -> etat(E) == MachineState.NO_POWER && etat(P) == MachineState.RUNNING)
                .serveur("trio : panne", lieu -> be(lieu, P, ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE))
                .attendre("panne vue", () -> etat(P) == MachineState.FAULT)
                .regarder(E, VUE_ETAT)
                .prise("chauffe-eau-sans-courant")
                .regarder(P, VUE_ETAT)
                .pause(1500)
                .prise("chauffe-eau-panne")
                .regarder(CHAUFFE, new Vec3(0.0, 1.4, 2.3))
                .ouvrirBloc(CHAUFFE, Direction.SOUTH, AbstractContainerScreen.class)
                .pause(1000)
                .repereInterface("potable", mc -> gui(mc, -9, 50))
                .repereInterface("chaude", mc -> gui(mc, 185, 50))
                .repereInterface("etat", mc -> gui(mc, -9, 26))
                .prise("chauffe-eau-ecran")
                .fermer()
                .camera(new Vec3(6.5, 1.62, 0.5), new Vec3(6.5, 1.62, 6.0))
                .client("vue de face, icônes d'effet", mc -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements(VanillaGuiOverlay.POTION_ICONS);
                    mc.gameRenderer.setRenderHand(false);
                })
                .serveur("sous la pomme", lieu -> lieu.joueur().removeAllEffects())
                .attendre("Bien-être reçu", () -> Minecraft.getInstance().player != null
                        && Minecraft.getInstance().player.hasEffect(TownRegistry.WELL_BEING.get()), 15_000)
                .pause(1500)
                .prise("douche-bien-etre")
                .client("première personne", mc -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    WikiStudio.ath(mc, false);
                });
    }

    private static void pompe(Lieu lieu, int x, int y, int z, Direction sens) {
        lieu.poser(x, y, z, bloc("create:mechanical_pump").defaultBlockState().setValue(BlockStateProperties.FACING, sens));
    }

    private static void moteur(Lieu lieu, BlockPos engrenage, Direction.Axis axe, BlockPos moteur, Direction sens) {
        lieu.poser(engrenage.getX(), engrenage.getY(), engrenage.getZ(),
                bloc("create:cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS, axe));
        lieu.poser(moteur.getX(), moteur.getY(), moteur.getZ(),
                bloc("create:creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING, sens));
    }

    // --- le phare ------------------------------------------------------------------------------------------------------

    /**
     * Hauteur du phare au-dessus de l'origine : le monde plat de l'atelier a son sol à y = -61 (origine à -60) ; le phare
     * est posé deux blocs au-dessus du minimum (mer à 63 + 8), soit y = 73. La préparation le vérifie.
     */
    private static final int SOMMET_REL = 133;

    private void phare(Atelier atelier) {
        atelier.scene("phare")
                .serveur("tour de pierre, phare au sommet, nuit", lieu -> {
                    ile(lieu);
                    int sommet = MachineConfig.seaLevel() + LightConfig.lighthouseMinAboveSea() + 2 - lieu.origine().getY();
                    if (sommet != SOMMET_REL) {
                        throw new IllegalStateException("origine à y = " + lieu.origine().getY() + " : sommet " + sommet
                                + " au lieu de " + SOMMET_REL);
                    }
                    for (int y = 0; y < sommet; y++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                lieu.poser(dx, y, dz, (Math.abs(dx) + Math.abs(dz) == 2 ? Blocks.STONE_BRICKS
                                        : Blocks.WHITE_CONCRETE).defaultBlockState());
                            }
                        }
                    }
                    lieu.poser(0, sommet, 0, LightRegistry.LIGHTHOUSE.get().defaultBlockState());
                    alimenter(lieu, new BlockPos(0, sommet, 0));
                    // Deux phares de plus sur des colonnes à la même hauteur : l'un sans courant, l'autre en panne.
                    for (int x : new int[] {-40, 40}) {
                        for (int y = 0; y < sommet; y++) {
                            lieu.poser(x, y, 0, Blocks.STONE_BRICKS.defaultBlockState());
                        }
                        lieu.poser(x, sommet, 0, LightRegistry.LIGHTHOUSE.get().defaultBlockState());
                    }
                    alimenter(lieu, new BlockPos(40, sommet, 0));
                    lieu.poser(6, 0, 3, LightRegistry.LIGHTHOUSE.get().defaultBlockState());
                    alimenter(lieu, new BlockPos(6, 0, 3));
                    lieu.monde().setDayTime(18000);
                    constat("phare posé à y = " + (lieu.origine().getY() + sommet) + " (mer à " + MachineConfig.seaLevel()
                            + ")");
                })
                .attendre("phare allumé", () -> allume(new BlockPos(0, SOMMET_REL, 0)), 20_000)
                .camera(new Vec3(-16.5, SOMMET_REL - 7.0, 20.5), new Vec3(0.5, SOMMET_REL - 2.0, 0.5))
                .pause(1500)
                .prise("phare-nuit")
                .camera(new Vec3(12.5, SOMMET_REL - 4.0, 16.5), new Vec3(0.5, SOMMET_REL + 0.5, 0.5))
                .sequence("phare-tour", 8_200, 200)
                .camera(new Vec3(3.1, SOMMET_REL + 1.3, 3.7), new Vec3(0.5, SOMMET_REL + 0.5, 0.5))
                .pause(1200)
                .prise("phare-pres-nuit")
                .serveur("le phare de droite en panne ; le grand phare retiré le temps des deux vues", lieu -> {
                    be(lieu, new BlockPos(40, SOMMET_REL, 0), ElectricMachineBlockEntity.class).breakDown(FaultType.FUSE);
                    lieu.poser(0, SOMMET_REL, 0, Blocks.AIR.defaultBlockState());
                })
                .regarder(new BlockPos(-40, SOMMET_REL, 0), new Vec3(0.8, 0.6, 2.0))
                .pause(800)
                .prise("phare-sans-courant")
                .regarder(new BlockPos(40, SOMMET_REL, 0), new Vec3(0.8, 0.6, 2.0))
                .pause(1500)
                .prise("phare-panne")
                .regarder(new BlockPos(6, 0, 3), new Vec3(0.8, 0.6, 2.0))
                .pause(800)
                .prise("phare-trop-bas")
                .serveur("jour, grand phare reposé", lieu -> {
                    lieu.monde().setDayTime(6000);
                    lieu.poser(0, SOMMET_REL, 0, LightRegistry.LIGHTHOUSE.get().defaultBlockState());
                })
                .attendre("phare éteint", () -> !allume(new BlockPos(0, SOMMET_REL, 0)), 10_000)
                .camera(new Vec3(3.1, SOMMET_REL + 1.3, 3.7), new Vec3(0.5, SOMMET_REL + 0.5, 0.5))
                .pause(1200)
                .prise("phare-jour");
    }

    private static boolean allume(BlockPos rel) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos o = WikiStudio.origineEnCours();
        if (o == null || mc.level == null) {
            return false;
        }
        BlockState s = mc.level.getBlockState(o.offset(rel));
        return s.hasProperty(LighthouseBlock.LIT) && s.getValue(LighthouseBlock.LIT);
    }

    // --- le détecteur de métaux ------------------------------------------------------------------------------------------

    private static final BlockPos CACHE = new BlockPos(0, -1, -2);

    private void detecteur(Atelier atelier) {
        atelier.scene("detecteur")
                .serveur("cache enfouie, détecteur en main", lieu -> {
                    ile(lieu);
                    lieu.poser(CACHE.getX(), CACHE.getY(), CACHE.getZ(), ExploreRegistry.CACHE.get().defaultBlockState());
                    if (!(lieu.monde().getBlockEntity(lieu.p(0, -1, -2)) instanceof CacheBlockEntity)) {
                        throw new IllegalStateException("cache non posée");
                    }
                    ItemStack d = new ItemStack(ExploreRegistry.METAL_DETECTOR.get());
                    DetectorItem.store(d, ExploreConfig.detectorCapacity() * 3 / 4);
                    ServerPlayer j = lieu.joueur();
                    j.getInventory().selected = 0;
                    j.getInventory().setItem(0, d);
                    INFOBULLES[0] = d.copy();
                })
                .client("première case tenue", mc -> mc.player.getInventory().selected = 0)
                .attendre("détecteur en main", () -> Minecraft.getInstance().player != null
                        && Minecraft.getInstance().player.getMainHandItem().is(ExploreRegistry.METAL_DETECTOR.get()))
                .camera(new Vec3(0.6, 1.62, 1.2), new Vec3(0.5, -0.6, -2.0))
                .client("main visible", mc -> {
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements(VanillaGuiOverlay.HOTBAR);
                })
                .repereBloc("cache", CACHE, 0.5, 1.55, 0.5)
                .pause(1200)
                .prise("detecteur-main")
                .client("interface masquée", mc -> WikiStudio.ath(mc, false))
                .echelle(3)
                .ouvrir("infobulle du détecteur", () -> new Infobulle(INFOBULLES[0]))
                .pause(400)
                .prise("detecteur-infobulle")
                .fermer();
    }

    // --- la lampe frontale ----------------------------------------------------------------------------------------------

    private void lampe(Atelier atelier) {
        atelier.scene("lampe")
                .serveur("grotte close, nuit", lieu -> {
                    ile(lieu);
                    lieu.monde().setDayTime(18000);
                    for (int x = -4; x <= 4; x++) {
                        for (int y = -1; y <= 4; y++) {
                            for (int z = 4; z <= 14; z++) {
                                boolean bord = x == -4 || x == 4 || y == -1 || y == 4 || z == 4 || z == 14;
                                lieu.poser(x, y, z, (bord ? (y == -1 ? Blocks.GRAVEL : Blocks.STONE) : Blocks.AIR)
                                        .defaultBlockState());
                            }
                        }
                    }
                    lieu.poser(-3, 0, 12, Blocks.COBBLESTONE.defaultBlockState());
                    lieu.poser(2, 0, 11, Blocks.IRON_ORE.defaultBlockState());
                    lieu.poser(3, 1, 13, Blocks.COAL_ORE.defaultBlockState());
                    ItemStack l = new ItemStack(ExploreRegistry.HEADLAMP.get());
                    HeadlampItem.store(l, ExploreConfig.headlampCapacity());
                    INFOBULLES[1] = l.copy();
                    lieu.joueur().setItemSlot(EquipmentSlot.HEAD, l);
                })
                .camera(new Vec3(0.5, 1.62, 6.5), new Vec3(0.5, 1.62, 12.0))
                .client("vue de face", mc -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    mc.gameRenderer.setRenderHand(false);
                })
                .pause(2500)
                .prise("lampe-avec")
                .serveur("lampe retirée", lieu -> lieu.joueur().setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY))
                .pause(2500)
                .prise("lampe-sans")
                .client("première personne", mc -> mc.options.setCameraType(CameraType.FIRST_PERSON))
                .echelle(3)
                .ouvrir("infobulle de la lampe", () -> new Infobulle(INFOBULLES[1]))
                .pause(400)
                .prise("lampe-infobulle")
                .fermer();
    }

    // --- le récepteur de signaux ----------------------------------------------------------------------------------------

    private void recepteur(Atelier atelier) {
        atelier.scene("recepteur")
                .serveur("trois récepteurs", lieu -> {
                    ile(lieu);
                    for (BlockPos p : new BlockPos[] {A, E, P}) {
                        lieu.poser(p.getX(), p.getY(), p.getZ(), ExploreRegistry.RECEIVER.get().defaultBlockState());
                    }
                    for (BlockPos p : new BlockPos[] {A, P}) {
                        BlockPos abs = lieu.p(p.getX(), p.getY(), p.getZ());
                        lieu.tenir(monde -> {
                            BlockEntity b = monde.getBlockEntity(abs);
                            if (b != null) {
                                b.getCapability(ForgeCapabilities.ENERGY, null).ifPresent(e -> e.receiveEnergy(200, false));
                            }
                        });
                        be(lieu, p, ReceiverBlockEntity.class).setFrequency(42);
                    }
                })
                .pause(1500)
                .serveur("deux messages sur la 42", lieu -> {
                    long t = lieu.monde().getGameTime();
                    for (BlockPos p : new BlockPos[] {A, P}) {
                        ReceiverBlockEntity r = be(lieu, p, ReceiverBlockEntity.class);
                        r.deliver(new SignalMessage(Math.max(1, t - 2400), 42, "Ici la tour radio. Le cargo arrive demain à l'aube, côté est."));
                        r.deliver(new SignalMessage(t, 42, "Signal inconnu capté au nord des ruines. Prudence."));
                    }
                })
                .attendre("message non lu", () -> recepteurEtat(P, ReceiverBlock.UNREAD))
                .regarder(P, VUE_ETAT)
                .pause(800)
                .prise("recepteur-message")
                .regarder(E, VUE_ETAT)
                .prise("recepteur-eteint")
                .regarder(A, new Vec3(0.0, 0.8, 2.3))
                .ouvrirBloc(A, Direction.SOUTH, AbstractContainerScreen.class)
                .pause(800)
                .repereInterface("frequence", mc -> gui(mc, 92, -9))
                .repereInterface("ecoute", mc -> gui(mc, 229, 46))
                .repereInterface("archive", mc -> gui(mc, 229, 100))
                .prise("recepteur-ecran")
                .fermer()
                .attendre("lu, à l'écoute", () -> recepteurEtat(A, ReceiverBlock.LISTENING)
                        && !recepteurEtat(A, ReceiverBlock.UNREAD))
                .regarder(A, VUE_ETAT)
                .pause(600)
                .prise("recepteur-ecoute");
    }

    private static boolean recepteurEtat(BlockPos rel, net.minecraft.world.level.block.state.properties.BooleanProperty p) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos o = WikiStudio.origineEnCours();
        if (o == null || mc.level == null) {
            return false;
        }
        BlockState s = mc.level.getBlockState(o.offset(rel));
        return s.hasProperty(p) && s.getValue(p);
    }

    // --- les mécanismes des ruines ------------------------------------------------------------------------------------

    private void mecanismes(Atelier atelier) {
        int[] t = {0};
        atelier.scene("mecanismes")
                .serveur("mécanisme dans un mur de ruine, porte de fer", lieu -> {
                    ile(lieu);
                    for (int x = -3; x <= 3; x++) {
                        for (int y = 0; y <= 3; y++) {
                            lieu.poser(x, y, -1, (y == 3 || (x + y) % 3 == 0 ? Blocks.CRACKED_STONE_BRICKS
                                    : Blocks.MOSSY_STONE_BRICKS).defaultBlockState());
                        }
                    }
                    lieu.poser(0, 0, -1, ExploreRegistry.MECHANISM.get().defaultBlockState());
                    lieu.poser(1, 0, -1, Blocks.IRON_DOOR.defaultBlockState()
                            .setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    lieu.poser(1, 1, -1, Blocks.IRON_DOOR.defaultBlockState()
                            .setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    for (int x = -3; x <= 3; x++) {
                        for (int y = 0; y <= 3; y++) {
                            if (x != 0 || y != 0) {
                                lieu.poser(x, y, -2, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                            }
                        }
                    }
                    lieu.poser(1, 0, -2, Blocks.AIR.defaultBlockState());
                    lieu.poser(1, 1, -2, Blocks.AIR.defaultBlockState());
                })
                .camera(new Vec3(0.8, 1.5, 3.4), new Vec3(0.4, 0.7, -1.0))
                .pause(800)
                .prise("mecanisme-repos")
                .sequence("mecanisme-activation", 12_500, 250, lieu -> {
                    t[0] = 0;
                    // La source : un Générateur Créatif posé derrière le mécanisme, dans le mur, à la demi-seconde.
                    lieu.tenir(monde -> {
                        if (t[0]++ == 10) {
                            lieu.poser(0, 0, -2, bloc(CREATIF).defaultBlockState());
                        }
                    });
                })
                .serveur("constat", lieu -> {
                    MechanismBlockEntity mb = be(lieu, new BlockPos(0, 0, -1), MechanismBlockEntity.class);
                    constat("mécanisme après 12 s à 100 FE par tic : actif " + mb.active() + ", porte "
                            + lieu.monde().getBlockState(lieu.p(1, 0, -1)).getValue(DoorBlock.OPEN));
                })
                .pause(500)
                .prise("mecanisme-actif");
    }

    // --- outils ------------------------------------------------------------------------------------------------------

    static ItemStack objet(String id) {
        var i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (i == null || i == Items.AIR) {
            throw new IllegalStateException(id + " absent (pack non chargé ?)");
        }
        return new ItemStack(i);
    }

    static ItemStack objet(String id, int n) {
        ItemStack s = objet(id);
        s.setCount(n);
        return s;
    }

    /** L'état par défaut du bloc, tourné vers le sud (vers la caméra) s'il a une face. */
    static BlockState face(Block b) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH) : s;
    }

    @SuppressWarnings("unchecked")
    static <T> T be(Lieu lieu, BlockPos rel, Class<T> type) {
        BlockEntity b = lieu.monde().getBlockEntity(lieu.p(rel.getX(), rel.getY(), rel.getZ()));
        if (!type.isInstance(b)) {
            throw new IllegalStateException("pas de " + type.getSimpleName() + " en " + rel.toShortString() + " : " + b);
        }
        return (T) b;
    }

    /** Relit la sauvegarde de l'entité de bloc après l'avoir changée (progression posée près de la fin, réserve…). */
    static void nbt(BlockEntity b, Consumer<CompoundTag> change) {
        CompoundTag tag = b.saveWithoutMetadata();
        change.accept(tag);
        b.load(tag);
        b.setChanged();
    }

    /** Pose une machine tournée vers le sud, garnie de quoi travailler ; {@code courant} : 100 FE par tic jusqu'à la fin. */
    private static void machine(Lieu lieu, BlockPos rel, Block bloc, boolean courant) {
        lieu.poser(rel.getX(), rel.getY(), rel.getZ(), face(bloc));
        BlockEntity b = lieu.monde().getBlockEntity(lieu.p(rel.getX(), rel.getY(), rel.getZ()));
        garnir(b);
        if (courant) {
            alimenter(lieu, rel);
        }
    }

    private static void garnir(BlockEntity be) {
        if (be instanceof FridgeBlockEntity f) {
            f.inventory().setStackInSlot(0, new ItemStack(Items.COOKED_BEEF, 8));
            f.inventory().setStackInSlot(1, new ItemStack(Items.COOKED_COD, 5));
            f.inventory().setStackInSlot(2, new ItemStack(Items.SALMON, 3));
            f.inventory().setStackInSlot(9, new ItemStack(Items.CHICKEN, 4));
            f.inventory().setStackInSlot(10, new ItemStack(Items.PORKCHOP, 6));
        } else if (be instanceof DesalinatorBlockEntity d) {
            d.waterTank().fill(new FluidStack(Fluids.WATER, 6000), IFluidHandler.FluidAction.EXECUTE);
            d.potableTank().fill(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 3000), IFluidHandler.FluidAction.EXECUTE);
            d.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 7));
        } else if (be instanceof DryerBlockEntity d) {
            d.inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT, new ItemStack(Items.BEEF, 8));
            d.inventory().setStackInSlot(DryerBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 4));
            d.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(FoodRegistry.SALTED_MEAT.get(), 3));
        } else if (be instanceof WaterHeaterBlockEntity w) {
            w.potable().fill(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 3000), IFluidHandler.FluidAction.EXECUTE);
            w.hot().fill(new FluidStack(TownRegistry.HOT_WATER.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        } else if (be instanceof IncubatorBlockEntity c) {
            for (int i = 0; i < IncubatorBlockEntity.SLOTS; i++) {
                c.inventory().setStackInSlot(i, new ItemStack(Items.EGG));
            }
        } else if (be instanceof CanteenPotBlockEntity m) {
            m.inventory().setStackInSlot(0, new ItemStack(Items.BEEF, 16));
            m.inventory().setStackInSlot(1, new ItemStack(Items.CARROT, 16));
            m.inventory().setStackInSlot(2, new ItemStack(Items.POTATO, 16));
            m.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_CONTAINER, new ItemStack(Items.BOWL, 48));
        } else if (be instanceof GalvanicBathBlockEntity g) {
            g.inventory().setStackInSlot(GalvanicBathBlockEntity.SLOT_PIECE, new ItemStack(RepairRegistry.FUSE.get(), 8));
            g.inventory().setStackInSlot(GalvanicBathBlockEntity.SLOT_ZINC, objet("create:zinc_ingot", 8));
        }
    }

    /** Un point de l'écran de conteneur ouvert, en pixels d'interface depuis son coin haut gauche. */
    @Nullable
    static double[] gui(Minecraft mc, double gx, double gy) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> s)) {
            return null;
        }
        return new double[] {s.getGuiLeft() + gx, s.getGuiTop() + gy};
    }

    /** Maj + clic du client sur la case {@code indice} du menu ouvert (le vrai paquet de clic). */
    private static void majClic(Minecraft mc, int indice) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> s)) {
            throw new IllegalStateException("aucun écran de conteneur pour le Maj + clic");
        }
        mc.gameMode.handleInventoryMouseClick(s.getMenu().containerId, indice, 0, ClickType.QUICK_MOVE, mc.player);
    }

    /** Un objet en grand et son infobulle réelle, sur un fond neutre : la prise ne garde que l'infobulle. */
    static final class Infobulle extends Screen {
        private final ItemStack pile;

        Infobulle(ItemStack pile) {
            super(Component.literal("infobulle"));
            this.pile = pile;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(0, 0, width, height, 0xFF1E2228);
            g.pose().pushPose();
            g.pose().translate(width / 2F - 120, height / 2F - 24, 0);
            g.pose().scale(3, 3, 1);
            g.renderItem(pile, 0, 0);
            g.renderItemDecorations(font, pile, 0, 0);
            g.pose().popPose();
            g.renderTooltip(font, pile, width / 2 - 60, height / 2 - 8);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
