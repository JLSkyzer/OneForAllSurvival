package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import dev.oneforallsurvival.dev.ReseauxDev.Reseau;
import dev.oneforallsurvival.dev.ReseauxDev.Scene;
import dev.oneforallsurvival.machine.MachineState;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Démonstration filmée, en développement seulement (tâche {@code runDemo}) : un client qui se pilote
 * seul, crée un monde plat, monte une scène par réseau, déplace la caméra de scène en scène, affiche
 * en français ce qui se passe (valeurs lues en direct sur les block entities, côté serveur) et
 * enregistre des images PNG depuis l'intérieur du jeu, à environ 10 par seconde. Puis le client
 * s'arrête. L'encodage des images en vidéo se fait hors du jeu.
 *
 * <p>Inerte sans la propriété système {@code oneforallsurvival.demo}. Rien de tout cela n'est livré.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DemoReseaux {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-demo");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo") != null;

    private static final String MONDE = "demo_reseaux";
    private static final long INTERVALLE_CAPTURE_NS = 100_000_000L; // 10 images par seconde
    private static final int TRAVAIL_MINIMUM = 20;

    // --- Les cas filmés ---------------------------------------------------------------------------

    private record Cas(String cle, String reseauNom, String casNom, Reseau reseau, boolean enLigne, int z,
            String legende) {}

    private static final List<Cas> CAS = List.of(
            new Cas("temoin_direct", "Témoin", "source collée à la machine", Reseau.TEMOIN, false, 0,
                    "La source créative pousse son courant directement dans la machine."),
            new Cas("temoin_ligne", "Témoin", "avec un bloc en ligne", Reseau.TEMOIN, true, 16,
                    "Le bloc en ligne relaie le courant vers la machine, sans réserve à lui."),
            new Cas("ca_direct", "Create Crafts & Additions", "direct", Reseau.CREATE_ADDITION, false, 32,
                    "Connecteur sur la source, fil de cuivre, connecteur contre la machine."),
            new Cas("ca_ligne", "Create Crafts & Additions", "à travers le bloc en ligne", Reseau.CREATE_ADDITION,
                    true, 48, "Le dernier connecteur touche le bloc en ligne, qui alimente la machine."),
            new Cas("na_direct", "Create New Age", "direct", Reseau.NEW_AGE, false, 64,
                    "Connecteurs électriques et fil de cuivre ; celui de la source tire le courant."),
            new Cas("na_ligne", "Create New Age", "à travers le bloc en ligne", Reseau.NEW_AGE, true, 80,
                    "Le dernier connecteur touche le bloc en ligne, qui alimente la machine."),
            new Cas("ae_direct", "Advanced Electricity", "direct", Reseau.ADVANCED_ELECTRICITY, false, 96,
                    "Connecteur sur la source, fil, puis boîtier d'alimentation posé sur la machine."),
            new Cas("ae_ligne", "Advanced Electricity", "à travers le bloc en ligne", Reseau.ADVANCED_ELECTRICITY,
                    true, 112, "Le boîtier d'alimentation est posé sur le bloc en ligne, qui alimente la machine."));

    /** Un plan : le cas filmé, sa durée en secondes et l'instant où l'on tire le fil. */
    private record Plan(Cas cas, double duree, double cablage) {}

    private static final List<Plan> PLANS = new ArrayList<>();

    static {
        for (Cas c : CAS) {
            boolean temoin = c.reseau() == Reseau.TEMOIN;
            PLANS.add(new Plan(c, temoin ? 6.0 : 11.0, temoin ? -1 : 3.0));
        }
    }

    private static final double DUREE_BILAN = 9.0;

    // --- État du déroulé --------------------------------------------------------------------------

    private enum Phase { TITRE_JEU, CREATION, ATTENTE_MONDE, MISE_EN_PLACE, ECHAUFFEMENT, PLANS, BILAN, FIN }

    private static Phase phase = Phase.TITRE_JEU;
    private static boolean dansUneEtape;
    private static long debutPhaseNs;
    private static int indexPlan;
    private static long debutPlanNs;
    private static boolean cabledurPlan;
    private static int surface;
    private static final Map<String, Scene> scenes = new LinkedHashMap<>();
    private static volatile boolean scenesPretes;
    private static boolean teleporte;

    /** Mesures lues par le serveur à chaque tick : état, réserve, travail, et le meilleur travail vu. */
    private record Mesure(MachineState etat, int energie, int capacite, int progression, long transmis) {}

    private static volatile Map<String, Mesure> mesures = Map.of();
    private static final Map<String, Integer> meilleur = new java.util.concurrent.ConcurrentHashMap<>();

    // --- Capture ----------------------------------------------------------------------------------

    private static Path dossierImages;
    private static long derniereCaptureNs;
    private static int numeroImage;
    private static ThreadPoolExecutor ecrivains;
    private static boolean captureActive;

    // --- Projection des étiquettes 3D ------------------------------------------------------------

    private static Matrix4f projection;
    private static Matrix4f vue;
    private static Vec3 cameraMonde = Vec3.ZERO;

    private DemoReseaux() {}

    // ================================================================================================
    // Mise en route
    // ================================================================================================

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || phase == Phase.FIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            placerLaCamera(mc);
            return;
        }
        // Créer un monde rend des frames avant de rendre la main : la réentrance rejouerait la création.
        if (dansUneEtape) {
            return;
        }
        dansUneEtape = true;
        try {
            avancer(mc);
            capturerSiBesoin(mc);
        } catch (RuntimeException echec) {
            LOG.error("[demo] échec en phase {}", phase, echec);
            terminer(mc, "échec : " + echec);
        } finally {
            dansUneEtape = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (ACTIF && event.phase == TickEvent.Phase.END && phase != Phase.FIN) {
            Minecraft mc = Minecraft.getInstance();
            // La pause à la perte du focus ne doit jamais geler la démonstration.
            if (mc.level != null && mc.screen instanceof PauseScreen) {
                mc.setScreen(null);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || !scenesPretes) {
            return;
        }
        MinecraftServer serveur = event.getServer();
        ServerLevel monde = serveur.overworld();
        Map<String, Mesure> neuves = new LinkedHashMap<>();
        for (Map.Entry<String, Scene> e : scenes.entrySet()) {
            Scene s = e.getValue();
            if (monde.getBlockEntity(s.machine()) instanceof TestMachineBlockEntity m) {
                long transmis = 0;
                if (s.inline() != null && monde.getBlockEntity(s.inline()) instanceof TestInlineBlockEntity i) {
                    transmis = i.totalTransferred();
                }
                neuves.put(e.getKey(), new Mesure(m.state(), m.energy().getEnergyStored(),
                        m.energy().getMaxEnergyStored(), m.progress(), transmis));
                meilleur.merge(e.getKey(), m.progress(), Math::max);
            }
        }
        mesures = neuves;
    }

    private static void avancer(Minecraft mc) {
        long maintenant = System.nanoTime();
        switch (phase) {
            case TITRE_JEU -> {
                if (mc.getOverlay() == null && mc.level == null && mc.screen != null) {
                    mc.options.pauseOnLostFocus = false; // avant le chargement du monde
                    reglerLesOptions(mc);
                    LOG.info("[demo] le jeu est prêt, création du monde");
                    phase = Phase.CREATION;
                }
            }
            case CREATION -> {
                phase = Phase.ATTENTE_MONDE;
                creerLeMonde(mc);
                debutPhaseNs = System.nanoTime();
            }
            case ATTENTE_MONDE -> {
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null
                        && mc.getSingleplayerServer().isReady()) {
                    LOG.info("[demo] monde chargé");
                    mc.setScreen(null);
                    phase = Phase.MISE_EN_PLACE;
                    debutPhaseNs = maintenant;
                    mc.getSingleplayerServer().execute(() -> miseEnPlace(mc.getSingleplayerServer()));
                }
            }
            case MISE_EN_PLACE -> {
                if (scenesPretes) {
                    phase = Phase.ECHAUFFEMENT;
                    debutPhaseNs = maintenant;
                    teleporte = false;
                }
            }
            case ECHAUFFEMENT -> {
                // Laisse le temps aux chunks de s'afficher et aux connecteurs d'avoir leur premier tick.
                if (!teleporte) {
                    teleporterVers(mc, 0);
                    teleporte = true;
                }
                if (maintenant - debutPhaseNs > 9_000_000_000L) {
                    phase = Phase.PLANS;
                    indexPlan = 0;
                    debutPlanNs = maintenant;
                    cabledurPlan = false;
                    captureActive = true;
                    ouvrirLesCaptures(mc);
                    LOG.info("[demo] début du tournage");
                }
            }
            case PLANS -> jouerLePlan(mc, maintenant);
            case BILAN -> {
                if (maintenant - debutPlanNs > (long) (DUREE_BILAN * 1e9)) {
                    terminer(mc, null);
                }
            }
            default -> {
            }
        }
    }

    private static void reglerLesOptions(Minecraft mc) {
        var o = mc.options;
        o.onboardAccessibility = false;
        o.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        o.fov().set(60);
        o.renderDistance().set(8);
        o.simulationDistance().set(8);
        o.guiScale().set(2);
        o.enableVsync().set(false);
        o.framerateLimit().set(60);
        o.bobView().set(false);
        o.particles().set(net.minecraft.client.ParticleStatus.MINIMAL);
        o.entityShadows().set(false);
        mc.getToasts().clear();
        mc.mouseHandler.releaseMouse();
    }

    private static void creerLeMonde(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true,
                regles, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    /** Sur le thread du serveur : heure, sol, puis toutes les scènes ; les fils viennent plus tard. */
    private static void miseEnPlace(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        surface = monde.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
        commande(serveur, "time set 6000");
        commande(serveur, "weather clear");
        for (Cas c : CAS) {
            // Un socle clair sous chaque rangée, pour que les blocs se détachent de l'herbe.
            for (int x = -2; x <= 9; x++) {
                for (int dz = -3; dz <= 3; dz++) {
                    monde.setBlock(new BlockPos(x, surface - 1, c.z() + dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                }
            }
            Scene scene = ReseauxDev.construire(monde, new BlockPos(0, surface, c.z()), c.reseau(), c.enLigne());
            for (String ligne : scene.journal()) {
                LOG.info("[demo] {} | {}", c.cle(), ligne);
            }
            scenes.put(c.cle(), scene);
        }
        scenesPretes = true;
        LOG.info("[demo] scènes montées, surface y={}", surface);
    }

    private static void commande(MinecraftServer serveur, String ligne) {
        serveur.getCommands().performPrefixedCommand(serveur.createCommandSourceStack(), ligne);
    }

    // ================================================================================================
    // Les plans
    // ================================================================================================

    /** La position de l'œil de la caméra et son orientation à l'instant {@code t} du plan. */
    private record Pose(double x, double y, double z, float lacet, float tangage) {}

    private static Pose poseDuPlan(Plan plan, double t) {
        Cas c = plan.cas();
        boolean temoin = c.reseau() == Reseau.TEMOIN;
        double avancement = Math.min(1.0, t / plan.duree());
        // Un lent travelling latéral : de la vie sans dépaysement. La caméra regarde le nord (lacet 180),
        // vers le centre de la scène, un peu au-dessus du sol.
        double centreX = temoin ? (c.enLigne() ? 1.5 : 1.0) : (c.enLigne() ? 3.4 : 2.9);
        double largeur = temoin ? 0.4 : 1.0;
        double x = centreX - largeur / 2 + largeur * avancement;
        double recul = temoin ? 2.8 : 5.2;
        double hauteur = temoin ? 2.0 : 3.5;
        double cibleY = 0.8;
        float tangage = (float) Math.toDegrees(Math.atan2(hauteur - cibleY, recul));
        return new Pose(x, surface + hauteur, c.z() + 0.5 + recul - 0.3 * avancement, 180F, tangage);
    }

    private static void placerLaCamera(Minecraft mc) {
        if (mc.player == null || phase.ordinal() < Phase.ECHAUFFEMENT.ordinal() || phase == Phase.BILAN) {
            return;
        }
        Pose p = phase == Phase.ECHAUFFEMENT ? poseDuPlan(PLANS.get(0), 0) : poseDuPlan(PLANS.get(indexPlan),
                (System.nanoTime() - debutPlanNs) / 1e9);
        var j = mc.player;
        double y = p.y() - j.getEyeHeight();
        j.setPos(p.x(), y, p.z());
        j.xo = p.x();
        j.yo = y;
        j.zo = p.z();
        j.xOld = p.x();
        j.yOld = y;
        j.zOld = p.z();
        j.setDeltaMovement(Vec3.ZERO);
        j.setYRot(p.lacet());
        j.yRotO = p.lacet();
        j.setXRot(p.tangage());
        j.xRotO = p.tangage();
        j.setYHeadRot(p.lacet());
        j.yHeadRotO = p.lacet();
    }

    private static void teleporterVers(Minecraft mc, int plan) {
        Pose p = poseDuPlan(PLANS.get(plan), 0);
        String ligne = String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f", p.x(),
                p.y() - 1.62, p.z(), p.lacet(), p.tangage());
        MinecraftServer serveur = mc.getSingleplayerServer();
        serveur.execute(() -> commande(serveur, ligne));
    }

    private static void jouerLePlan(Minecraft mc, long maintenant) {
        Plan plan = PLANS.get(indexPlan);
        double t = (maintenant - debutPlanNs) / 1e9;
        if (!cabledurPlan && plan.cablage() >= 0 && t >= plan.cablage()) {
            cabledurPlan = true;
            Scene scene = scenes.get(plan.cas().cle());
            MinecraftServer serveur = mc.getSingleplayerServer();
            serveur.execute(() -> {
                ReseauxDev.cabler(serveur.overworld(), scene);
                for (String ligne : scene.journal()) {
                    LOG.info("[demo] {} | {}", plan.cas().cle(), ligne);
                }
            });
            LOG.info("[demo] {} : fil tiré à t={}", plan.cas().cle(), String.format("%.1f", t));
        }
        if (t >= plan.duree()) {
            Mesure m = mesures.get(plan.cas().cle());
            LOG.info("[demo] {} : fin du plan, {} {}/{} FE, travail {}", plan.cas().cle(),
                    m == null ? "?" : m.etat(), m == null ? 0 : m.energie(), m == null ? 0 : m.capacite(),
                    m == null ? 0 : m.progression());
            indexPlan++;
            debutPlanNs = maintenant;
            cabledurPlan = false;
            if (indexPlan >= PLANS.size()) {
                phase = Phase.BILAN;
                LOG.info("[demo] bilan");
            } else {
                teleporterVers(mc, indexPlan);
            }
        }
    }

    // ================================================================================================
    // Images
    // ================================================================================================

    private static void ouvrirLesCaptures(Minecraft mc) {
        dossierImages = mc.gameDirectory.toPath().resolve("frames");
        try {
            Files.createDirectories(dossierImages);
        } catch (IOException e) {
            throw new IllegalStateException("dossier d'images impossible : " + dossierImages, e);
        }
        ecrivains = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(24),
                new ThreadPoolExecutor.CallerRunsPolicy());
        derniereCaptureNs = 0;
        numeroImage = 0;
    }

    /** Fin de frame : au plus une image toutes les 100 ms, copiée ici, écrite ailleurs. */
    private static void capturerSiBesoin(Minecraft mc) {
        if (!captureActive || (phase != Phase.PLANS && phase != Phase.BILAN)) {
            return;
        }
        long maintenant = System.nanoTime();
        if (derniereCaptureNs != 0 && maintenant - derniereCaptureNs < INTERVALLE_CAPTURE_NS) {
            return;
        }
        derniereCaptureNs = derniereCaptureNs == 0 ? maintenant : derniereCaptureNs + INTERVALLE_CAPTURE_NS;
        if (maintenant - derniereCaptureNs > 5 * INTERVALLE_CAPTURE_NS) {
            derniereCaptureNs = maintenant; // en retard : on ne rattrape pas, on repart d'ici
        }
        int numero = ++numeroImage;
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int l = image.getWidth();
            int h = image.getHeight();
            int[] pixels = new int[l * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < l; x++) {
                    pixels[y * l + x] = image.getPixelRGBA(x, y);
                }
            }
            Path fichier = dossierImages.resolve(String.format("f%05d.png", numero));
            ecrivains.execute(() -> ecrire(fichier, pixels, l, h));
        }
    }

    private static void ecrire(Path fichier, int[] abgr, int l, int h) {
        BufferedImage img = new BufferedImage(l, h, BufferedImage.TYPE_INT_RGB);
        int[] rgb = new int[abgr.length];
        for (int i = 0; i < abgr.length; i++) {
            int p = abgr[i];
            rgb[i] = ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
        }
        img.setRGB(0, 0, l, h, rgb, 0, l);
        try {
            ImageIO.write(img, "png", fichier.toFile());
        } catch (IOException e) {
            LOG.error("[demo] image non écrite : {}", fichier, e);
        }
    }

    private static void terminer(Minecraft mc, String echec) {
        phase = Phase.FIN;
        captureActive = false;
        try {
            if (ecrivains != null) {
                ecrivains.shutdown();
                ecrivains.awaitTermination(3, TimeUnit.MINUTES);
            }
            if (dossierImages != null) {
                StringBuilder s = new StringBuilder();
                s.append("{\"images\":").append(numeroImage).append(",\"fps\":10");
                s.append(",\"echec\":").append(echec == null ? "null" : "\"" + echec.replace("\"", "'") + "\"");
                s.append(",\"cas\":[");
                boolean premier = true;
                for (Cas c : CAS) {
                    Mesure m = mesures.get(c.cle());
                    if (!premier) {
                        s.append(',');
                    }
                    premier = false;
                    s.append("{\"cle\":\"").append(c.cle()).append("\",\"meilleur_travail\":")
                            .append(meilleur.getOrDefault(c.cle(), 0))
                            .append(",\"etat_final\":\"").append(m == null ? "?" : m.etat()).append("\"}");
                }
                s.append("]}");
                Files.writeString(dossierImages.resolve("termine.json"), s.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException | InterruptedException e) {
            LOG.error("[demo] fin de capture", e);
        }
        LOG.info("[demo] terminé : {} images, {}", numeroImage, echec == null ? "sans erreur" : echec);
        mc.stop();
    }

    // ================================================================================================
    // Affichage
    // ================================================================================================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (ACTIF && event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            projection = new Matrix4f(event.getProjectionMatrix());
            vue = new Matrix4f(event.getPoseStack().last().pose());
            cameraMonde = event.getCamera().getPosition();
        }
    }

    /** Ni main, ni barre d'objets, ni viseur : seul notre affichage reste. */
    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (ACTIF) {
            event.setCanceled(true);
        }
    }

    private static int couleurEtat(MachineState e) {
        return switch (e) {
            case RUNNING -> 0xFF55FF55;
            case NO_POWER -> 0xFFFF5555;
            case FAULT -> 0xFFFFAA00;
            default -> 0xFFCCCCCC;
        };
    }

    private static String texteEtat(MachineState e) {
        return switch (e) {
            case RUNNING -> "EN MARCHE";
            case NO_POWER -> "SANS COURANT";
            case FAULT -> "EN PANNE";
            default -> "À L'ARRÊT";
        };
    }

    private static void texte(GuiGraphics g, String s, float x, float y, float echelle, int couleur) {
        var font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(echelle, echelle, 1F);
        g.drawString(font, s, 0, 0, couleur, true);
        g.pose().popPose();
    }

    private static int largeur(String s, float echelle) {
        return (int) (Minecraft.getInstance().font.width(s) * echelle);
    }

    private static void texteCentre(GuiGraphics g, String s, float centreX, float y, float echelle, int couleur) {
        texte(g, s, centreX - largeur(s, echelle) / 2F, y, echelle, couleur);
    }

    /** Projette un point du monde sur l'écran de jeu, ou {@code null} s'il est derrière la caméra. */
    private static float[] projeter(GuiGraphics g, double x, double y, double z) {
        if (projection == null || vue == null) {
            return null;
        }
        Vector4f v = new Vector4f((float) (x - cameraMonde.x), (float) (y - cameraMonde.y),
                (float) (z - cameraMonde.z), 1F);
        v.mul(vue);
        v.mul(projection);
        if (v.w <= 0.01F) {
            return null;
        }
        float nx = v.x / v.w;
        float ny = v.y / v.w;
        return new float[] {(nx * 0.5F + 0.5F) * g.guiWidth(), (1F - (ny * 0.5F + 0.5F)) * g.guiHeight()};
    }

    private static void etiquette3d(GuiGraphics g, String s, double x, double y, double z, int couleur) {
        float[] p = projeter(g, x, y, z);
        if (p == null) {
            return;
        }
        float e = 0.8F;
        int w = largeur(s, e);
        g.fill((int) (p[0] - w / 2F - 2), (int) p[1] - 2, (int) (p[0] + w / 2F + 2), (int) (p[1] + 9 * e + 2), 0xB0000000);
        texteCentre(g, s, p[0], p[1], e, couleur);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ACTIF) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        float centre = g.guiWidth() / 2F;
        if (phase == Phase.PLANS) {
            dessinerPlan(g, PLANS.get(indexPlan), centre);
        } else if (phase == Phase.BILAN) {
            dessinerBilan(g, centre);
        } else if (phase == Phase.ECHAUFFEMENT) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFF101820);
            texteCentre(g, "Préparation des scènes...", centre, g.guiHeight() / 2F - 8, 2F, 0xFFFFFFFF);
        }
    }

    private static void dessinerPlan(GuiGraphics g, Plan plan, float centre) {
        Cas c = plan.cas();
        Mesure m = mesures.get(c.cle());
        Scene scene = scenes.get(c.cle());
        double t = (System.nanoTime() - debutPlanNs) / 1e9;
        int largeurEcran = g.guiWidth();

        // Bandeau du haut : réseau, cas, puis l'état de la machine lu en direct.
        g.fill(0, 0, largeurEcran, scene.inline() != null ? 70 : 60, 0xE0000000);
        texteCentre(g, c.reseauNom(), centre, 3, 2.0F, 0xFFFFD866);
        texteCentre(g, "Cas : " + c.casNom(), centre, 23, 1.2F, 0xFFFFFFFF);
        if (m != null) {
            String ligne = "Machine d'essai : " + texteEtat(m.etat()) + "   |   " + m.energie() + " / " + m.capacite()
                    + " FE   |   travail : " + m.progression();
            texteCentre(g, ligne, centre, 39, 1.0F, couleurEtat(m.etat()));
            if (scene.inline() != null) {
                texteCentre(g, "Bloc en ligne : " + m.transmis() + " FE transmis jusqu'ici", centre, 52, 0.9F,
                        0xFF88CCFF);
            }
        }

        // Bandeau du bas : la légende, et le verdict dès qu'il est établi.
        g.fill(0, g.guiHeight() - 46, largeurEcran, g.guiHeight(), 0xC0000000);
        texteCentre(g, c.legende(), centre, g.guiHeight() - 42, 1.0F, 0xFFFFFFFF);
        texteCentre(g, "La machine d'essai n'a pas de texture : c'est le cube violet et noir.", centre,
                g.guiHeight() - 30, 0.8F, 0xFFAAAAAA);
        if (m != null) {
            boolean livre = m.etat() == MachineState.RUNNING && m.progression() >= TRAVAIL_MINIMUM;
            boolean temoin = c.reseau() == Reseau.TEMOIN;
            double depuisFil = plan.cablage() < 0 ? t : t - plan.cablage();
            if (livre) {
                texteCentre(g, "COURANT REÇU : la machine tourne", centre, g.guiHeight() - 18, 1.2F, 0xFF55FF55);
            } else if (depuisFil > 6.0) {
                texteCentre(g, "AUCUN COURANT n'arrive à la machine", centre, g.guiHeight() - 18, 1.2F, 0xFFFF5555);
            } else if (!temoin && plan.cablage() >= 0 && t < plan.cablage()) {
                texteCentre(g, "Pas encore de fil : la machine attend", centre, g.guiHeight() - 18, 1.2F, 0xFFCCCCCC);
            } else {
                texteCentre(g, "Le courant se met en route...", centre, g.guiHeight() - 18, 1.2F, 0xFFCCCCCC);
            }
        }

        // Étiquettes posées sur les blocs, à des hauteurs étagées pour ne pas se chevaucher.
        double y0 = surface;
        boolean temoin = scene.reseau() == Reseau.TEMOIN;
        etiquette3d(g, "SOURCE", scene.source().getX() + 0.5, y0 + 1.5, scene.source().getZ() + 0.5, 0xFFFFD866);
        if (!temoin) {
            String nomA = scene.reseau() == Reseau.NEW_AGE ? "CONNECTEUR (aspire)" : "CONNECTEUR";
            etiquette3d(g, nomA, scene.pointA().getX() + 0.5, y0 + 2.1, scene.pointA().getZ() + 0.5, 0xFFFFFFFF);
            etiquette3d(g, "FIL", (scene.pointA().getX() + scene.pointB().getX()) / 2.0 + 0.5, y0 + 1.5,
                    scene.pointA().getZ() + 0.5, 0xFFFFFFFF);
            String nomB = scene.reseau() == Reseau.ADVANCED_ELECTRICITY ? "BOÎTIER AE" : "CONNECTEUR";
            etiquette3d(g, nomB, scene.pointB().getX() + 0.5, y0 + 2.1, scene.pointB().getZ() + 0.5, 0xFFFFFFFF);
        }
        if (scene.inline() != null) {
            etiquette3d(g, "BLOC EN LIGNE", scene.inline().getX() + 0.5, y0 + (temoin ? 1.5 : 1.5),
                    scene.inline().getZ() + 0.5, 0xFF88CCFF);
        }
        etiquette3d(g, "MACHINE", scene.machine().getX() + 0.5, y0 + (scene.inline() != null ? 2.1 : 1.5),
                scene.machine().getZ() + 0.5, 0xFF55FF55);
        // Barre de progression du plan, discrète.
        float f = (float) Math.min(1.0, t / plan.duree());
        g.fill(0, g.guiHeight() - 3, (int) (largeurEcran * f), g.guiHeight(), 0xFFFFD866);
    }

    private static void dessinerBilan(GuiGraphics g, float centre) {
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xE0101820);
        texteCentre(g, "Bilan : le courant arrive-t-il dans la machine ?", centre, 8, 1.4F, 0xFFFFD866);
        float y = 36;
        for (Cas c : CAS) {
            Mesure m = mesures.get(c.cle());
            int travail = meilleur.getOrDefault(c.cle(), 0);
            boolean livre = travail >= TRAVAIL_MINIMUM;
            texte(g, c.reseauNom() + " : " + c.casNom(), 12, y, 0.75F, 0xFFFFFFFF);
            String droite = (livre ? "OUI" : "NON") + " (" + travail + " ticks de travail)";
            texte(g, droite, g.guiWidth() * 0.6F, y, 0.75F, livre ? 0xFF55FF55 : 0xFFFF5555);
            y += 17;
        }
        texteCentre(g, "Valeurs lues en direct sur les machines d'essai ; un tick de travail = 10 FE reçus.", centre,
                g.guiHeight() - 18, 0.8F, 0xFFAAAAAA);
    }
}
