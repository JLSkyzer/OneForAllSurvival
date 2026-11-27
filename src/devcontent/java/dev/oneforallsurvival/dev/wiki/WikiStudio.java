package dev.oneforallsurvival.dev.wiki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.oneforallsurvival.client.web.WebAvailability;
import dev.oneforallsurvival.dev.FilmCapture;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.ModMismatchDisconnectedScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * L'atelier de captures du wiki (développement seulement, jamais empaqueté) : un client qui se pilote seul, crée un
 * monde plat neuf (midi figé, temps clair, paisible, sans apparitions), joue les scènes des chapitres
 * ({@link Chapitre}) et écrit {@code <dossier>/<chapitre>/<prise>.png}, les repères projetés ({@code .json}), les
 * séquences ({@code <séquence>/f0000.png}) et les exports ({@code _export/}). Activé par
 * {@code -Doneforallsurvival.wiki=<dossier>} (tâche {@code runWikiStudio}) ; filtre
 * {@code -Doneforallsurvival.wiki.seul=<chapitre>[:<scène>][,…]}.
 *
 * <p>Garde : un pas qui dépasse son délai fait échouer sa scène (les suivantes continuent) ; un chien de garde (fil à
 * part) ferme la JVM si plus aucune image n'est rendue, si un pas reste bloqué au-delà de son délai, ou au-delà du
 * délai global. Les écrans de déconnexion (dont celui des mods différents) arrêtent l'atelier. Fin au journal :
 * {@code [wiki] terminé : N images, sans erreur} ou la liste des échecs ; bilan dans {@code <dossier>/termine.json}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WikiStudio {

    static final Logger LOG = LogManager.getLogger("oneforallsurvival-wiki");
    private static final String DOSSIER = System.getProperty("oneforallsurvival.wiki");
    static final boolean ACTIF = DOSSIER != null;
    private static final String SEUL = System.getProperty("oneforallsurvival.wiki.seul", "").trim();
    private static final String MONDE = "atelier-wiki";
    /** Délai global de la passe, en secondes ({@code -Doneforallsurvival.wiki.garde}, 45 min par défaut). */
    private static final double GARDE_S = Double.parseDouble(System.getProperty("oneforallsurvival.wiki.garde", "2700"));
    private static final int LARGEUR = 1280;
    private static final int HAUTEUR = 720;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Les chapitres connus de l'atelier ; chaque tâche de contenu ajoute le sien. */
    private static List<Chapitre> chapitres() {
        return List.of(new ScenesControle(), new WikiExports());
    }

    private enum Phase { TITRE, MONDE, SCENES, FIN }

    private static final long DEBUT_NS = System.nanoTime();
    private static volatile Phase phase = Phase.TITRE;
    private static long debutPhaseNs = System.nanoTime();
    private static boolean fait;
    private static final List<Scene.Pas> PAS = new ArrayList<>();
    private static int indice;
    private static boolean actionFaite;
    private static volatile long debutPasNs;
    private static long debutReposNs;
    private static int imagesDuPas;
    private static volatile long delaiPasNs;
    private static volatile long derniereImageNs;
    private static volatile long finNs;
    private static long numeroImage;
    private static Path racine;
    private static final Map<String, FilmCapture> CAPTURES = new LinkedHashMap<>();
    private static final List<String> ECHECS = new ArrayList<>();
    private static final List<String> SCENES_FAITES = new ArrayList<>();
    private static int images;
    private static volatile boolean termineEcrit;
    /** Les actions tenues à chaque tic du serveur pour la scène en cours. */
    private static final List<Consumer<ServerLevel>> TENIR = new CopyOnWriteArrayList<>();
    private static volatile String erreurServeur;
    private static int terrains;
    private static long mondePretNs;
    private static boolean dansUnPas;
    private static Field[] sourisXY;
    private static volatile boolean terrainPose;
    private static boolean athVisible;
    // Rendu du monde : nombre de chunks rendus et depuis quand il ne bouge plus.
    private static int chunksRendus = -1;
    private static long chunksStablesNs;
    private static boolean avertiRendu;
    // Séquence en cours.
    private static Sequence sequence;

    static {
        if (ACTIF) {
            Thread chien = new Thread(WikiStudio::garder, "wiki-garde");
            chien.setDaemon(true);
            chien.start();
        }
    }

    private WikiStudio() {}

    // --- boucle ----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || phase == Phase.FIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            numeroImage++;
            curseurHorsImage(mc);
            return;
        }
        derniereImageNs = System.nanoTime();
        mc.getToasts().clear();
        // La création du monde rend elle-même des images (écran de chargement) : pas de pas joué dans un pas.
        if (dansUnPas) {
            return;
        }
        dansUnPas = true;
        try {
            avancer(mc);
        } catch (RuntimeException e) {
            LOG.error("[wiki] exception à la phase {}", phase, e);
            echec("atelier", "exception à la phase " + phase + " : " + e);
            finir(mc);
        } finally {
            dansUnPas = false;
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || TENIR.isEmpty()) {
            return;
        }
        for (Consumer<ServerLevel> c : TENIR) {
            try {
                c.accept(event.getServer().overworld());
            } catch (RuntimeException e) {
                LOG.error("[wiki] action tenue", e);
                erreurServeur = "action tenue à chaque tic : " + e;
                TENIR.remove(c);
            }
        }
    }

    static long numeroImage() {
        return numeroImage;
    }

    private static void avancer(Minecraft mc) {
        double s = (System.nanoTime() - debutPhaseNs) / 1e9;
        switch (phase) {
            case TITRE -> {
                if (mc.getOverlay() != null || mc.level != null || mc.screen == null) {
                    if (s > 600) {
                        echec("titre", "écran titre jamais atteint (écran " + mc.screen + ")");
                        finir(mc);
                    }
                    return;
                }
                if (ModList.get().isLoaded("mcef") && !WebAvailability.ready() && s < 240) {
                    return; // MCEF s'initialise à l'écran titre
                }
                commencer(mc);
            }
            case MONDE -> {
                String hors = horsDuMonde(mc);
                if (hors != null) {
                    echec("monde", hors);
                    finir(mc);
                } else if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null
                        && mc.getSingleplayerServer().isReady()) {
                    if (mondePretNs == 0) {
                        mondePretNs = System.nanoTime();
                    }
                    // Trois secondes de marge : un écran posé à l'arrivée (accueil d'un mod) est refermé.
                    if (!fait && System.nanoTime() - mondePretNs > 3_000_000_000L) {
                        fait = true;
                        if (mc.screen != null) {
                            journal("écran refermé à l'arrivée : " + nomEcran(mc));
                            mc.setScreen(null);
                        }
                        MinecraftServer srv = mc.getSingleplayerServer();
                        srv.execute(() -> reglerLeMonde(srv));
                        construire();
                        passer(Phase.SCENES);
                    }
                } else if (s > 300) {
                    echec("monde", "joueur absent après 300 s (écran " + mc.screen + ")");
                    finir(mc);
                }
            }
            case SCENES -> {
                String hors = horsDuMonde(mc);
                if (hors != null) {
                    echec("deconnexion", hors);
                    finir(mc);
                    return;
                }
                if (sequence != null) {
                    sequence.capturer(mc);
                }
                jouer(mc);
            }
            default -> {
            }
        }
    }

    private static void commencer(Minecraft mc) {
        // Réglages en mémoire (options.txt de run-wiki porte déjà la langue et l'accueil sauté).
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        mc.options.bobView().set(false);
        mc.options.renderDistance().set(8);
        mc.options.simulationDistance().set(8);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(60);
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (!"fr_fr".equals(mc.getLanguageManager().getSelected())) {
            mc.getLanguageManager().setSelected("fr_fr");
            mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        }
        var f = mc.getWindow();
        if (f.getWidth() != LARGEUR || f.getHeight() != HAUTEUR) {
            GLFW.glfwSetWindowSize(f.getWindow(), LARGEUR, HAUTEUR);
        }
        racine = mc.gameDirectory.toPath().resolve(DOSSIER);
        journal("titre atteint ; langue " + mc.getLanguageManager().getSelected() + ", fenêtre " + f.getWidth() + "x"
                + f.getHeight() + ", filtre « " + SEUL + " », mcef " + ModList.get().isLoaded("mcef") + ", jei "
                + ModList.get().isLoaded("jei") + ", PID " + ProcessHandle.current().pid());
        Path monde = mc.gameDirectory.toPath().resolve("saves").resolve(MONDE);
        if (Files.isDirectory(monde)) {
            try (Stream<Path> tout = Files.walk(monde)) {
                for (Path p : tout.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            } catch (IOException e) {
                throw new IllegalStateException("ancien monde de l'atelier non effacé : " + monde, e);
            }
        }
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
        regles.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_DOINSOMNIA).set(false, null);
        regles.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        regles.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, null);
        regles.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, regles,
                WorldDataConfiguration.DEFAULT);
        // La phase change avant la création : elle rend des images (et rappelle cette boucle) jusqu'au monde prêt.
        passer(Phase.MONDE);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    private static void reglerLeMonde(MinecraftServer srv) {
        ServerLevel monde = srv.overworld();
        monde.setDayTime(6000);
        monde.setWeatherParameters(1_000_000, 0, false, false);
        ServerPlayer j = Scene.joueur(srv);
        j.setGameMode(GameType.CREATIVE);
        j.getAbilities().flying = true;
        j.onUpdateAbilities();
    }

    /** Les scènes retenues par le filtre, chapitre par chapitre. */
    private static void construire() {
        Atelier atelier = new Atelier();
        List<String> inconnus = new ArrayList<>();
        List<String[]> filtres = new ArrayList<>();
        for (String morceau : SEUL.split(",")) {
            if (!morceau.isBlank()) {
                filtres.add(morceau.trim().split(":", 2));
            }
        }
        for (String[] f : filtres) {
            if (chapitres().stream().noneMatch(c -> memeChapitre(c.id(), f[0]))) {
                inconnus.add(f[0]);
            }
        }
        for (Chapitre c : chapitres()) {
            atelier.chapitre(c.id());
            c.scenes(atelier);
        }
        for (Scene s : atelier.scenes()) {
            boolean garde = filtres.isEmpty() || filtres.stream().anyMatch(f -> memeChapitre(s.chapitre(), f[0])
                    && (f.length == 1 || f[1].equals(s.nom())));
            if (garde) {
                PAS.addAll(s.pas());
                SCENES_FAITES.add(s.chapitre() + ":" + s.nom());
            }
        }
        for (String i : inconnus) {
            echec("filtre", "chapitre inconnu « " + i + " »");
        }
        journal("scènes retenues : " + SCENES_FAITES + " (" + PAS.size() + " pas)");
        if (PAS.isEmpty()) {
            echec("filtre", "aucune scène retenue par « " + SEUL + " »");
        }
    }

    /** {@code _export} se demande aussi {@code export}. */
    private static boolean memeChapitre(String id, String demande) {
        return id.equals(demande) || id.equals("_" + demande);
    }

    private static void jouer(Minecraft mc) {
        if (indice >= PAS.size()) {
            finir(mc);
            return;
        }
        Scene.Pas p = PAS.get(indice);
        long maintenant = System.nanoTime();
        try {
            if (erreurServeur != null) {
                String e = erreurServeur;
                erreurServeur = null;
                throw new IllegalStateException(e);
            }
            if (!actionFaite) {
                if (p.nom().equals("terrain")) {
                    journal("scène " + p.scene().chapitre() + ":" + p.scene().nom());
                }
                journal("pas « " + p.nom() + " »");
                actionFaite = true;
                debutPasNs = maintenant;
                delaiPasNs = p.delaiMs() * 1_000_000L;
                debutReposNs = 0;
                imagesDuPas = 0;
                chunksRendus = -1;
                avertiRendu = false;
                p.action().run();
                return;
            }
            imagesDuPas++;
            if (!p.pret().getAsBoolean()) {
                if (maintenant - debutPasNs > delaiPasNs) {
                    throw new IllegalStateException("délai de " + p.delaiMs() / 1000 + " s dépassé (écran "
                            + nomEcran(mc) + ")");
                }
                return;
            }
            if (debutReposNs == 0) {
                debutReposNs = maintenant;
            }
            if ((maintenant - debutReposNs) / 1_000_000L < p.reposMs()) {
                return;
            }
            if (p.apres() != null) {
                p.apres().run();
            }
            suivant();
        } catch (RuntimeException e) {
            echouer(mc, p, e);
        }
    }

    private static void suivant() {
        indice++;
        actionFaite = false;
        delaiPasNs = 0;
    }

    /** La scène s'arrête : l'image de l'échec, l'écran fermé, les actions tenues retirées ; on passe à la suivante. */
    private static void echouer(Minecraft mc, Scene.Pas p, RuntimeException e) {
        Scene s = p.scene();
        LOG.error("[wiki] scène {}:{} en échec au pas « {} »", s.chapitre(), s.nom(), p.nom(), e);
        echec(s.chapitre() + ":" + s.nom(), "pas « " + p.nom() + " » : " + e.getMessage());
        if (sequence != null) {
            sequence.cap.attendre();
            sequence = null;
        }
        if (mc.level != null && racine != null) {
            capture(s.chapitre()).instantane(mc, s.nom() + "-echec");
        }
        fermerEcran(mc);
        TENIR.clear();
        while (indice < PAS.size() && PAS.get(indice).scene() == s) {
            indice++;
        }
        actionFaite = false;
        delaiPasNs = 0;
    }

    // --- terrain, caméra, interface --------------------------------------------------------------------------------

    static void tenir(Consumer<ServerLevel> chaqueTic) {
        TENIR.add(chaqueTic);
    }

    static void preparerTerrain(Scene s) {
        Minecraft mc = Minecraft.getInstance();
        fermerEcran(mc);
        ath(mc, false);
        echelle(mc, 2);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        TENIR.clear();
        terrainPose = false;
        int n = terrains++;
        MinecraftServer srv = mc.getSingleplayerServer();
        srv.execute(() -> {
            ServerLevel monde = srv.overworld();
            // Chaque terrain à 512 blocs du précédent : au-delà de la distance d'affichage (8 chunks).
            int x = 2048 + 512 * n;
            int z = 2048;
            monde.getChunk(x >> 4, z >> 4);
            BlockPos o = new BlockPos(x, monde.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            s.lieu(new Lieu(srv, o));
            monde.setDayTime(6000);
            monde.setWeatherParameters(1_000_000, 0, false, false);
            ServerPlayer j = Scene.joueur(srv);
            j.closeContainer();
            j.getInventory().clearContent();
            j.setGameMode(GameType.CREATIVE);
            j.getAbilities().flying = true;
            j.onUpdateAbilities();
            j.setNoGravity(true);
            j.setDeltaMovement(Vec3.ZERO);
            j.teleportTo(monde, x + 0.5, o.getY() + 4, z + 8.5, 180F, 20F);
            for (Entity e : monde.getEntities((Entity) null, new AABB(o).inflate(96), e -> !(e instanceof Player))) {
                e.discard();
            }
            journal("terrain " + n + " : origine " + o.toShortString());
            terrainPose = true;
        });
    }

    static boolean terrainPret(Scene s) {
        Minecraft mc = Minecraft.getInstance();
        if (!terrainPose || mc.player == null) {
            return false;
        }
        BlockPos o = s.lieu().origine();
        return mc.player.distanceToSqr(Vec3.atCenterOf(o)) < 30 * 30 && mc.levelRenderer.isChunkCompiled(o);
    }

    static void placerCamera(Vec3 oeil, float yaw, float pitch) {
        MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
        srv.execute(() -> {
            ServerPlayer j = Scene.joueur(srv);
            j.getAbilities().flying = true;
            j.onUpdateAbilities();
            j.setNoGravity(true);
            j.setDeltaMovement(Vec3.ZERO);
            j.teleportTo(srv.overworld(), oeil.x, oeil.y - j.getEyeHeight(), oeil.z, yaw, pitch);
        });
    }

    /** Le client est à l'œil voulu, regard posé, et le monde est rendu autour. */
    static boolean cameraPrete(Vec3 oeil, Vec3 cible) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getEyePosition().distanceTo(oeil) > 0.05) {
            return false;
        }
        float[] r = dev.oneforallsurvival.dev.FilmTournage.regard(oeil, cible);
        if (Math.abs(Mth.wrapDegrees(mc.player.getYRot() - r[0])) > 0.5 || Math.abs(mc.player.getXRot() - r[1]) > 0.5) {
            return false;
        }
        return mondeRendu(mc, BlockPos.containing(oeil), BlockPos.containing(cible));
    }

    /**
     * Les chunks de l'œil et de la cible construits, la file de construction vide et le nombre de chunks rendus
     * inchangé depuis une seconde. Au-delà de 20 s, les deux chunks suffisent (noté au journal).
     */
    private static boolean mondeRendu(Minecraft mc, BlockPos... points) {
        for (BlockPos p : points) {
            if (!mc.levelRenderer.isChunkCompiled(p)) {
                chunksRendus = -1;
                return false;
            }
        }
        int n = mc.levelRenderer.countRenderedChunks();
        long maintenant = System.nanoTime();
        if (n != chunksRendus || !mc.levelRenderer.hasRenderedAllChunks()) {
            chunksRendus = n;
            chunksStablesNs = maintenant;
        }
        if (maintenant - chunksStablesNs > 1_000_000_000L) {
            return true;
        }
        if (maintenant - debutPasNs > 20_000_000_000L) {
            if (!avertiRendu) {
                avertiRendu = true;
                journal("rendu pas stable après 20 s (" + mc.levelRenderer.getChunkStatistics() + ") : on continue");
            }
            return true;
        }
        return false;
    }

    static void ath(Minecraft mc, boolean visible) {
        athVisible = visible;
        mc.options.hideGui = !visible;
        mc.gameRenderer.setRenderHand(visible);
    }

    static void echelle(Minecraft mc, int e) {
        mc.options.guiScale().set(e); // en mémoire seulement
        mc.resizeDisplay();
    }

    static boolean echelleAtteinte(Minecraft mc, int e) {
        if ((int) mc.getWindow().getGuiScale() == e) {
            return true;
        }
        mc.resizeDisplay();
        if (System.nanoTime() - debutPasNs > 2_000_000_000L) {
            throw new IllegalStateException("échelle " + e + " impossible : le jeu donne "
                    + (int) mc.getWindow().getGuiScale() + " pour une fenêtre de " + mc.getWindow().getWidth() + "x"
                    + mc.getWindow().getHeight());
        }
        return false;
    }

    static void fermerEcran(Minecraft mc) {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        mc.setScreen(null);
    }

    /** Au moins trois images rendues depuis le début du pas (l'écran ou la caméra est en place). */
    static boolean imageStable() {
        return imagesDuPas >= 3;
    }

    /** La souris du jeu hors de l'image : aucun survol ni infobulle (la vraie souris ne bouge pas). */
    private static void curseurHorsImage(Minecraft mc) {
        try {
            if (sourisXY == null) {
                sourisXY = new Field[] {net.minecraft.client.MouseHandler.class.getDeclaredField("xpos"),
                    net.minecraft.client.MouseHandler.class.getDeclaredField("ypos")};
                for (Field f : sourisXY) {
                    f.setAccessible(true);
                }
            }
            for (Field f : sourisXY) {
                f.setDouble(mc.mouseHandler, -1000);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- prises ----------------------------------------------------------------------------------------------------

    static FilmCapture capture(String chapitre) {
        return CAPTURES.computeIfAbsent(chapitre, c -> {
            try {
                return new FilmCapture(racine.resolve(c), "atelier du wiki");
            } catch (IOException e) {
                throw new IllegalStateException("dossier de captures " + c, e);
            }
        });
    }

    static Path dossier(String chapitre) {
        return racine.resolve(chapitre);
    }

    static void prendre(Scene s, String nom, Map<String, Scene.Repere> reperes) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, double[]> vus = new LinkedHashMap<>();
        List<String> hors = new ArrayList<>();
        for (Map.Entry<String, Scene.Repere> r : reperes.entrySet()) {
            Optional<double[]> v = r.getValue().calculer(mc);
            if (v.isEmpty() || v.get()[0] < 0 || v.get()[0] > 100 || v.get()[1] < 0 || v.get()[1] > 100) {
                hors.add(r.getKey() + (v.isEmpty() ? " (invisible)" : String.format(Locale.ROOT, " (%.1f %%, %.1f %%)",
                        v.get()[0], v.get()[1])));
            } else {
                vus.put(r.getKey(), v.get());
            }
        }
        capture(s.chapitre()).instantane(mc, nom);
        images++;
        var f = mc.getWindow();
        Path json = dossier(s.chapitre()).resolve(nom + ".json");
        try {
            if (vus.isEmpty()) {
                Files.deleteIfExists(json);
            } else {
                JsonObject o = new JsonObject();
                o.addProperty("image", nom + ".png");
                o.addProperty("largeur", f.getWidth());
                o.addProperty("hauteur", f.getHeight());
                o.addProperty("echelle", (int) f.getGuiScale());
                JsonObject points = new JsonObject();
                for (Map.Entry<String, double[]> v : vus.entrySet()) {
                    JsonArray a = new JsonArray();
                    a.add(Math.round(v.getValue()[0] * 100) / 100.0);
                    a.add(Math.round(v.getValue()[1] * 100) / 100.0);
                    points.add(v.getKey(), a);
                }
                o.add("reperes", points);
                Files.writeString(json, GSON.toJson(o) + "\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("repères de " + nom + " non écrits", e);
        }
        StringBuilder detail = new StringBuilder();
        vus.forEach((k, v) -> detail.append(String.format(Locale.ROOT, " %s=(%.2f %%, %.2f %%)", k, v[0], v[1])));
        journal(String.format(Locale.ROOT, "image %s/%s.png : fenêtre %dx%d, échelle %d, écran %s%s", s.chapitre(), nom,
                f.getWidth(), f.getHeight(), (int) f.getGuiScale(), nomEcran(mc),
                detail.length() == 0 ? "" : " ; repères" + detail));
        if (!hors.isEmpty()) {
            throw new IllegalStateException("repères hors de l'image : " + hors);
        }
    }

    private static String nomEcran(Minecraft mc) {
        return mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName();
    }

    // --- séquences -------------------------------------------------------------------------------------------------

    private static final class Sequence {
        final FilmCapture cap;
        final String nom;
        final long debutNs = System.nanoTime();
        final long dureeNs;
        final long pasNs;
        long prochaineNs = debutNs;
        int n;
        volatile String erreur;
        boolean finie;

        Sequence(FilmCapture cap, String nom, long dureeMs, long pasMs) {
            this.cap = cap;
            this.nom = nom;
            this.dureeNs = dureeMs * 1_000_000L;
            this.pasNs = pasMs * 1_000_000L;
        }

        void capturer(Minecraft mc) {
            long maintenant = System.nanoTime();
            if (finie || maintenant < prochaineNs) {
                return;
            }
            cap.instantane(mc, String.format(Locale.ROOT, "f%04d", n++));
            prochaineNs += pasNs;
            if (prochaineNs < maintenant) {
                prochaineNs = maintenant; // image en retard : la cadence repart d'ici
            }
            if (maintenant - debutNs >= dureeNs) {
                finie = true;
            }
        }
    }

    static void commencerSequence(Scene s, String nom, long dureeMs, long pasMs, Consumer<Lieu> auDebut) {
        Path d = dossier(s.chapitre()).resolve(nom);
        try {
            Files.createDirectories(d);
            try (Stream<Path> vieux = Files.list(d)) {
                for (Path p : vieux.toList()) {
                    if (p.getFileName().toString().matches("f\\d{4}\\.png")) {
                        Files.delete(p);
                    }
                }
            }
            sequence = new Sequence(new FilmCapture(d, "atelier du wiki"), s.chapitre() + "/" + nom, dureeMs, pasMs);
        } catch (IOException e) {
            throw new IllegalStateException("dossier de la séquence " + d, e);
        }
        if (auDebut != null) {
            Sequence seq = sequence;
            Minecraft.getInstance().getSingleplayerServer().execute(() -> {
                try {
                    auDebut.accept(s.lieu());
                } catch (RuntimeException e) {
                    LOG.error("[wiki] début de la séquence {}", nom, e);
                    seq.erreur = e.toString();
                }
            });
        }
    }

    static boolean sequenceFinie() {
        if (sequence.erreur != null) {
            throw new IllegalStateException("début de la séquence : " + sequence.erreur);
        }
        return sequence.finie;
    }

    static void finirSequence() {
        Sequence seq = sequence;
        sequence = null;
        seq.cap.attendre();
        images += seq.n;
        journal("séquence " + seq.nom + " : " + seq.n + " images, une toutes les " + seq.pasNs / 1_000_000L + " ms");
    }

    // --- garde et fin ----------------------------------------------------------------------------------------------

    /**
     * La raison pour laquelle le client n'est plus dans un monde, ou {@code null} : écran de déconnexion (dont celui
     * de Forge pour des mods différents), ou aucun monde depuis plus de 5 s hors des écrans d'attente.
     */
    private static long sansMondeNs;

    private static String horsDuMonde(Minecraft mc) {
        Screen s = mc.screen;
        if (s instanceof DisconnectedScreen || s instanceof ModMismatchDisconnectedScreen) {
            return "déconnecté (" + s.getClass().getSimpleName() + ") : " + s.getTitle().getString();
        }
        boolean attente = s instanceof ConnectScreen || s instanceof ReceivingLevelScreen
                || s instanceof LevelLoadingScreen || s instanceof GenericDirtMessageScreen || s instanceof ProgressScreen;
        if (mc.level != null || attente) {
            sansMondeNs = 0;
            return null;
        }
        if (sansMondeNs == 0) {
            sansMondeNs = System.nanoTime();
        }
        return System.nanoTime() - sansMondeNs > 5_000_000_000L ? "aucun monde depuis plus de 5 s (écran " + s + ")"
                : null;
    }

    /** Le chien de garde : ferme la JVM si l'atelier n'avance plus. */
    private static void garder() {
        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
            long maintenant = System.nanoTime();
            String raison = null;
            if (phase == Phase.FIN) {
                if (finNs != 0 && maintenant - finNs > 120_000_000_000L) {
                    raison = "le client ne s'est pas fermé 120 s après la fin";
                }
            } else if ((maintenant - DEBUT_NS) / 1e9 > GARDE_S) {
                raison = "délai global de " + (long) GARDE_S + " s dépassé à la phase " + phase;
            } else if (derniereImageNs != 0 && maintenant - derniereImageNs > 120_000_000_000L) {
                raison = "plus aucune image rendue depuis 120 s (phase " + phase + ")";
            } else if (phase == Phase.SCENES && delaiPasNs > 0
                    && maintenant - debutPasNs > delaiPasNs + 90_000_000_000L) {
                raison = "pas n° " + indice + " bloqué au-delà de son délai";
            }
            if (raison != null) {
                LOG.error("[wiki] FAIL chien de garde : {} ; arrêt forcé", raison);
                synchronized (ECHECS) {
                    ECHECS.add("chien de garde : " + raison);
                }
                ecrireTermine("chien de garde");
                Runtime.getRuntime().halt(2);
            }
        }
    }

    private static void echec(String ou, String detail) {
        synchronized (ECHECS) {
            ECHECS.add(ou + " : " + detail);
        }
        LOG.error("[wiki] FAIL {} : {}", ou, detail);
    }

    private static void passer(Phase suivante) {
        journal("phase " + phase + " -> " + suivante);
        phase = suivante;
        debutPhaseNs = System.nanoTime();
        fait = false;
    }

    private static void finir(Minecraft mc) {
        if (phase == Phase.FIN) {
            return;
        }
        phase = Phase.FIN;
        finNs = System.nanoTime();
        TENIR.clear();
        if (sequence != null) {
            sequence.cap.attendre();
            sequence = null;
        }
        for (FilmCapture c : CAPTURES.values()) {
            c.attendre();
        }
        if (ECHECS.isEmpty()) {
            LOG.info("[wiki] terminé : {} images, sans erreur", images);
        } else {
            LOG.error("[wiki] terminé : {} images, {} échec(s) :", images, ECHECS.size());
            for (String e : ECHECS) {
                LOG.error("[wiki]   - {}", e);
            }
        }
        ecrireTermine("normale");
        mc.stop();
    }

    private static synchronized void ecrireTermine(String fin) {
        if (termineEcrit || racine == null) {
            return;
        }
        termineEcrit = true;
        JsonObject o = new JsonObject();
        o.addProperty("fin", fin);
        o.addProperty("images", images);
        o.addProperty("secondes", Math.round((System.nanoTime() - DEBUT_NS) / 1e9));
        JsonArray scenes = new JsonArray();
        SCENES_FAITES.forEach(scenes::add);
        o.add("scenes", scenes);
        JsonArray echecs = new JsonArray();
        synchronized (ECHECS) {
            ECHECS.forEach(echecs::add);
        }
        o.add("echecs", echecs);
        try {
            Files.createDirectories(racine);
            Files.writeString(racine.resolve("termine.json"), GSON.toJson(o) + "\n", StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOG.error("[wiki] termine.json non écrit", e);
        }
    }

    static void journal(String texte) {
        LOG.info("[wiki] [+{}] {}", String.format(Locale.ROOT, "%.1f s", (System.nanoTime() - DEBUT_NS) / 1e9), texte);
    }

    static Gson gson() {
        return GSON;
    }

    static Path racine() {
        return racine;
    }

    static boolean athVisible() {
        return athVisible;
    }

    /** L'origine du terrain de la scène en cours, ou {@code null} avant sa préparation. */
    static BlockPos origineEnCours() {
        if (indice >= PAS.size()) {
            return null;
        }
        try {
            return PAS.get(indice).scene().lieu().origine();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
