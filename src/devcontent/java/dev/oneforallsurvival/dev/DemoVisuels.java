package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Capture de contrôle des visuels du lot 2 (développement seulement, jamais empaqueté). Un client qui se pilote
 * seul : il monte le bain dans ses quatre états réels, les cinq débris, un mur de cadres avec tous les objets,
 * puis ouvre l'écran du bain, l'établi et la catégorie JEI, et écrit une image par vue dans
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
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("oneforallsurvival", nom));
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

        // De quoi remplir l'inventaire pour la vue en cases.
        joueur.getInventory().clearContent();
        for (int i = 0; i < OBJETS.length && i < 36; i++) {
            joueur.getInventory().setItem(i, new ItemStack(objet(OBJETS[i])));
        }
        joueur.setGameMode(GameType.CREATIVE);
        joueur.getAbilities().flying = true;
        joueur.onUpdateAbilities();
        preparationFaite = true;
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    /** Tous les objets en grand, leur nom dessous : la vérité du modèle de chaque objet, et de sa clé de langue. */
    private static final class Grille extends Screen {
        Grille() {
            super(Component.literal("grille"));
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(0, 0, width, height, 0xFF8B8B8B);
            int colonnes = 6;
            int cw = width / colonnes;
            int ch = height / 4;
            for (int i = 0; i < OBJETS.length; i++) {
                int x = (i % colonnes) * cw;
                int y = (i / colonnes) * ch;
                ItemStack pile = new ItemStack(objet(OBJETS[i]));
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

        etape("grille", () -> {
            mc.options.hideGui = false;
            mc.setScreen(new Grille());
        }, () -> true, 800, "objets_grille.png");
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
        etape("jei bain", () -> {
            fermer(mc);
            montrerDansJei(mc, "GALVANIC_TYPE");
        }, () -> ecranJei(mc), 1500, "jei_bain.png");
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
