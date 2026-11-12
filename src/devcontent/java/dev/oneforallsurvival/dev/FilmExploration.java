package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.explore.CacheBlockEntity;
import dev.oneforallsurvival.explore.DetectorItem;
import dev.oneforallsurvival.explore.ExploreConfig;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.explore.HeadlampItem;
import dev.oneforallsurvival.explore.MechanismBlock;
import dev.oneforallsurvival.explore.MechanismBlockEntity;
import dev.oneforallsurvival.explore.ReceiverBlock;
import dev.oneforallsurvival.explore.ReceiverBlockEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Film de démonstration du lot 7 (développement seulement, jamais empaqueté). Un client qui se pilote seul dans un monde
 * intégré avec le pack complet : la recherche d'une cache au détecteur (le bip est annoncé à l'écran, le son ne se filme
 * pas), la lampe dans une grotte close, un message reçu, le mécanisme ancien qui s'éveille par un vrai câble de Create
 * Crafts & Additions et ouvre une porte de fer.
 *
 * <p>Le temps du monde n'est jamais accéléré et aucune durée n'est abrégée. Le détecteur et la lampe sont donnés chargés
 * (leur recharge est dans l'essai de bout en bout). Mêmes images que {@link FilmVille} : 1280 x 656, un bandeau de
 * légende, 10 images par seconde, dans {@code frames-exploration}. Activé par {@code -Doneforallsurvival.demo.exploration}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmExploration {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.exploration") != null;
    private static final String MONDE = "film_exploration";
    private static final double GARDE_S = 480;
    private static final String CREATIVE = "createaddition:creative_energy";
    private static final String CONNECTEUR = "createaddition:connector";
    private static final String BOBINE = "createaddition:copper_spool";
    private static final String MESSAGE = "Equipe vers terrain : la porte du fond s'ouvrira bientot.";
    private static final double PORTEE = 24;

    private enum Phase { TITRE, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    private record Etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {}

    /** Ce que le serveur sait, recopié à chaque tick pour le bandeau. */
    private record Instantane(boolean recepteurEcoute, int archive, boolean nonLu, int progression, int duree, int etage, boolean actif,
            boolean porteOuverte, int cachesIndexees) {
        static final Instantane VIDE = new Instantane(false, 0, false, 0, 0, 0, false, false, 0);
    }

    private static Phase phase = Phase.TITRE;
    private static boolean dansUneEtape;
    private static long debutPhaseNs;
    private static final long DEBUT_NS = System.nanoTime();
    private static final List<Etape> ETAPES = new ArrayList<>();
    private static int indexEtape = -1;
    private static boolean actionFaite;
    private static long debutAction;
    private static long debutRepos;
    private static FilmCapture capture;
    private static String echec;
    private static volatile boolean preparationFaite;
    private static volatile Instantane instantane = Instantane.VIDE;

    /** Le dernier bip reçu : heure (ns), numéro, intervalle depuis le précédent (ticks), hauteur. */
    private static volatile long dernierBipNs;
    private static volatile int nombreBips;
    private static volatile double dernierIntervalle;
    private static volatile double derniereHauteur;
    private static volatile String dernierChat = "";

    private static BlockPos o;
    private static BlockPos cache;
    private static BlockPos source;
    private static BlockPos recepteur;
    private static BlockPos sourceMecanisme;
    private static BlockPos mecanisme;
    private static BlockPos grotte;
    private static int butinAvant;

    static {
        if (ACTIF) {
            Thread chien = new Thread(() -> {
                try {
                    Thread.sleep((long) ((GARDE_S + 40) * 1000));
                    LOG.error("[film] delai de garde de la JVM depasse : arret force");
                    Runtime.getRuntime().halt(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "film-garde");
            chien.setDaemon(true);
            chien.start();
        }
    }

    private FilmExploration() {}

    // ================================================================================================
    // Boucle
    // ================================================================================================

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        var son = event.getOriginalSound();
        if (ACTIF && son != null && son.getLocation().getPath().equals("block.note_block.bit")) {
            long maintenant = System.nanoTime();
            if (dernierBipNs != 0) {
                dernierIntervalle = (maintenant - dernierBipNs) / 50e6;
            }
            dernierBipNs = maintenant;
            derniereHauteur = E2eExploration.hauteurDe(son);
            nombreBips++;
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || phase == Phase.FIN || event.phase != TickEvent.Phase.END || dansUneEtape) {
            return;
        }
        dansUneEtape = true;
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().clear();
        try {
            if ((System.nanoTime() - DEBUT_NS) / 1e9 > GARDE_S) {
                throw new IllegalStateException("delai de garde du film depasse en phase " + phase);
            }
            avancer(mc);
            if (phase == Phase.FILM && indexEtape >= 1 && indexEtape < ETAPES.size()) {
                Etape e = ETAPES.get(indexEtape);
                String valeurs;
                try {
                    valeurs = e.valeurs().get();
                } catch (RuntimeException ex) {
                    valeurs = "(valeur indisponible : " + ex.getClass().getSimpleName() + ")";
                }
                capture.capturer(mc, e.legende(), valeurs);
            }
        } catch (RuntimeException | IOException e) {
            LOG.error("[film] échec en phase {}", phase, e);
            echec = e.toString();
            terminer(mc);
        } finally {
            dansUneEtape = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || phase == Phase.FIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (ACTIF) {
            dernierChat = event.getMessage().getString();
            LOG.info("[film] message : {}", dernierChat);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || !preparationFaite) {
            return;
        }
        ServerLevel monde = event.getServer().overworld();
        boolean ecoute = false;
        int archive = 0;
        boolean nonLu = false;
        if (monde.getBlockEntity(recepteur) instanceof ReceiverBlockEntity r) {
            ecoute = r.listening();
            archive = r.archive().size();
            nonLu = monde.getBlockState(recepteur).getValue(ReceiverBlock.UNREAD);
        }
        int progression = 0;
        int duree = 0;
        int etage = 0;
        boolean actif = false;
        if (monde.getBlockEntity(mecanisme) instanceof MechanismBlockEntity m) {
            progression = m.progress();
            duree = m.durationTicks();
            actif = m.active();
            etage = monde.getBlockState(mecanisme).getValue(MechanismBlock.STAGE);
        }
        boolean porte = monde.getBlockState(mecanisme.south()).getBlock() == Blocks.IRON_DOOR
                && monde.getBlockState(mecanisme.south()).getValue(DoorBlock.OPEN);
        instantane = new Instantane(ecoute, archive, nonLu, progression, duree, etage, actif, porte,
                dev.oneforallsurvival.explore.CacheIndex.get(monde).size());
    }

    private static void avancer(Minecraft mc) throws IOException {
        long maintenant = System.nanoTime();
        switch (phase) {
            case TITRE -> {
                if (mc.getOverlay() == null && mc.level == null && mc.screen != null) {
                    reglerLesOptions(mc);
                    phase = Phase.CREATION;
                }
            }
            case CREATION -> {
                phase = Phase.ATTENTE_MONDE;
                creerLeMonde(mc);
                debutPhaseNs = maintenant;
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
                    capture = new FilmCapture(mc.gameDirectory.toPath().resolve("frames-exploration"),
                            "One For All Survival | lot 7 | exploration");
                    phase = Phase.FILM;
                    LOG.info("[film] scénario prêt : {} étapes", ETAPES.size());
                }
            }
            case FILM -> jouer(mc);
            default -> {
            }
        }
    }

    private static void reglerLesOptions(Minecraft mc) {
        var opt = mc.options;
        opt.pauseOnLostFocus = false;
        opt.onboardAccessibility = false;
        opt.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        opt.renderDistance().set(10);
        opt.simulationDistance().set(8);
        opt.guiScale().set(2);
        opt.enableVsync().set(false);
        opt.framerateLimit().set(60);
        opt.hideGui = false;
        opt.gamma().set(1.0);
        mc.getToasts().clear();
        mc.mouseHandler.releaseMouse();
    }

    private static void creerLeMonde(Minecraft mc) {
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        regles.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // Disposition
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static Block bloc(String id) {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
        if (b == Blocks.AIR) {
            throw new IllegalStateException(id + " absent : lancer avec -PwithPack");
        }
        return b;
    }

    private static void poser(ServerLevel monde, BlockPos pos, Block bloc) {
        monde.setBlock(pos, bloc.defaultBlockState(), 3);
    }

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos ici = joueur.blockPosition();
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, ici.getX(), ici.getZ() + 20);
        o = new BlockPos(ici.getX(), y, ici.getZ() + 20);
        monde.setDayTime(6000);

        cache = p(30, 0, 0);
        poser(monde, cache, ExploreRegistry.CACHE.get());
        if (monde.getBlockEntity(cache) instanceof CacheBlockEntity) {
            LOG.info("[film] cache posée en {}", cache);
        }
        // La grotte : sept blocs de large, close de pierre de toutes parts, au sud.
        grotte = p(28, 0, 14);
        for (int x = -1; x <= 7; x++) {
            for (int h = -1; h <= 4; h++) {
                for (int z = -1; z <= 7; z++) {
                    boolean interieur = x >= 0 && x <= 6 && h >= 0 && h <= 3 && z >= 0 && z <= 6;
                    monde.setBlock(grotte.offset(x, h, z), (interieur ? Blocks.AIR : Blocks.STONE).defaultBlockState(), 3);
                }
            }
        }
        // Le récepteur et sa source ; le mécanisme, sa source, sa porte de fer.
        recepteur = p(-6, 0, -10);
        poser(monde, recepteur, ExploreRegistry.RECEIVER.get());
        poser(monde, recepteur.west(), bloc(CREATIVE));
        sourceMecanisme = p(4, 0, -10);
        mecanisme = p(10, 0, -10);
        poser(monde, sourceMecanisme, bloc(CREATIVE));
        poser(monde, mecanisme, ExploreRegistry.MECHANISM.get());
        monde.setBlock(mecanisme.south(), Blocks.IRON_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.FACING, Direction.SOUTH), 3);
        monde.setBlock(mecanisme.south().above(), Blocks.IRON_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.FACING, Direction.SOUTH), 3);
        serveur.getCommands().performPrefixedCommand(serveur.createCommandSourceStack(),
                "mechanism set " + mecanisme.getX() + " " + mecanisme.getY() + " " + mecanisme.getZ() + " 40 8");

        joueur.getInventory().clearContent();
        ItemStack detecteur = new ItemStack(ExploreRegistry.METAL_DETECTOR.get());
        DetectorItem.store(detecteur, ExploreConfig.detectorCapacity());
        joueur.getInventory().setItem(0, detecteur);
        ItemStack lampe = new ItemStack(ExploreRegistry.HEADLAMP.get());
        HeadlampItem.store(lampe, ExploreConfig.headlampCapacity());
        joueur.getInventory().setItem(1, lampe);
        joueur.getInventory().setItem(2, new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(CONNECTEUR)), 2));
        joueur.getInventory().setItem(3, new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(BOBINE)), 2));
        preparationFaite = true;
    }

    // ================================================================================================
    // Outils du scénario
    // ================================================================================================

    private static void aller(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0).teleportTo(serveur.overworld(), x, y, z, yaw, pitch));
    }

    /** Place le joueur à l'œil {@code oeil}, le regard sur {@code centre}. */
    private static void regarder(Minecraft mc, Vec3 centre, Vec3 oeil) {
        double dx = centre.x - oeil.x;
        double dy = centre.y - oeil.y;
        double dz = centre.z - oeil.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        aller(mc, oeil.x, oeil.y - 1.62, oeil.z, yaw, pitch);
    }

    private static void etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {
        ETAPES.add(new Etape(nom, legende, action, pret, reposMs, valeurs));
    }

    private static void sur(Minecraft mc, Runnable travail) {
        mc.getSingleplayerServer().execute(travail);
    }

    private static void clicDroit(Minecraft mc, BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        LOG.info("[film] clic droit en {} : {}", pos, res);
    }

    private static void tenir(Minecraft mc, int fente) {
        mc.player.getInventory().selected = fente;
    }

    private static boolean menuOuvert(Minecraft mc) {
        return mc.screen instanceof AbstractContainerScreen<?>;
    }

    private static void grosPlanEcran(Minecraft mc) {
        double e = mc.getWindow().getGuiScale();
        capture.grosPlan(mc.getWindow().getGuiScaledWidth() / 2.0 * e, mc.getWindow().getGuiScaledHeight() / 2.0 * e, 1.9);
    }

    private static int lumiereTete(Minecraft mc) {
        return mc.player == null ? 0 : mc.level.getBrightness(LightLayer.BLOCK, BlockPos.containing(mc.player.getEyePosition()));
    }

    private static String detecteurValeurs(Minecraft mc) {
        double distance = mc.player == null || cache == null ? 0 : mc.player.position().distanceTo(Vec3.atCenterOf(cache));
        long depuis = dernierBipNs == 0 ? -1 : (System.nanoTime() - dernierBipNs) / 1_000_000L;
        boolean bip = depuis >= 0 && depuis < 350;
        return (bip ? "BIP  " : "     ") + "distance " + String.format("%.1f", distance) + " blocs | dernier bip : intervalle "
                + (nombreBips < 2 ? "-" : String.format("%.0f", dernierIntervalle)) + " ticks, hauteur " + String.format("%.2f", derniereHauteur)
                + " | bips recus " + nombreBips + " | caches dans l'index " + instantane.cachesIndexees();
    }

    private static String lampeValeurs(Minecraft mc) {
        boolean portee = mc.player != null && mc.player.getItemBySlot(EquipmentSlot.HEAD).is(ExploreRegistry.HEADLAMP.get());
        return "lampe " + (portee ? "portee" : "retiree") + " | lumiere de bloc a la tete : " + lumiereTete(mc) + "/15 | batterie "
                + (portee ? HeadlampItem.stored(mc.player.getItemBySlot(EquipmentSlot.HEAD)) : HeadlampItem.stored(mc.player.getInventory().getItem(1)))
                + " FE";
    }

    private static String recepteurValeurs() {
        Instantane i = instantane;
        return "recepteur : " + (i.recepteurEcoute() ? "a l'ecoute" : "coupe") + " | messages archives " + i.archive() + " | voyant non lu "
                + (i.nonLu() ? "allume" : "eteint") + (dernierChat.isEmpty() ? "" : " | chat : " + dernierChat);
    }

    private static String mecanismeValeurs() {
        Instantane i = instantane;
        return "mecanisme : progression " + String.format("%.1f", i.progression() / 20.0) + " / " + String.format("%.0f", i.duree() / 20.0) + " s, etage " + i.etage()
                + ", " + (i.actif() ? "ACTIF" : "inactif") + " | porte de fer " + (i.porteOuverte() ? "ouverte" : "fermee") + " | 40 FE par tick";
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    private static void construireScenario(Minecraft mc) {
        etape("cadrage", "", () -> aller(mc, cache.getX() + 0.5 - 22, o.getY(), cache.getZ() + 0.5, -90, 0), () -> true, 2500, () -> "");

        // --- Scène 1 : la recherche d'une cache au détecteur
        etape("d_main", "Le détecteur de métaux en main : à 22 blocs, un bip lent et grave", () -> {
            tenir(mc, 0);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }, () -> nombreBips >= 3, 1500, () -> detecteurValeurs(mc));
        double[] distances = {14, 8, 3};
        String[] mots = {"plus vite", "encore plus vite", "presque au contact : un crépitement aigu"};
        for (int i = 0; i < distances.length; i++) {
            double d = distances[i];
            etape("d_" + (int) d, "À " + (int) d + " blocs : " + mots[i],
                    () -> aller(mc, cache.getX() + 0.5 - d, o.getY(), cache.getZ() + 0.5, -90, 0),
                    () -> mc.player.position().distanceTo(Vec3.atCenterOf(cache)) < d + 1.5 && dernierBipNs != 0, 3500,
                    () -> detecteurValeurs(mc));
        }
        etape("d_contre", "Contre la cache : le joueur la casse à la main (survie)", () -> {
            aller(mc, cache.getX() - 0.35, o.getY(), cache.getZ() + 0.5, -90, 25);
            tenir(mc, 8);
        }, () -> {
            if (mc.player.position().distanceTo(Vec3.atCenterOf(cache)) > 1.3) {
                return false;
            }
            if (mc.level.getBlockState(cache).isAir()) {
                return true;
            }
            mc.gameMode.continueDestroyBlock(cache, Direction.WEST);
            return false;
        }, 500, () -> detecteurValeurs(mc));
        etape("d_butin", "Le butin tombe : il est ramassé, une seule fois", () -> butinAvant = butin(mc),
                () -> butin(mc) >= 2 && mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(cache).inflate(5)).isEmpty(), 2500,
                () -> "butin ramassé : " + butin(mc) + " objet(s) | caches dans l'index " + instantane.cachesIndexees());
        etape("d_silence", "Le détecteur se tait : la cache est sortie de l'index", () -> {
            tenir(mc, 0);
            sur(mc, () -> {});
        }, () -> true, 4500, () -> detecteurValeurs(mc));

        // --- Scène 2 : la lampe dans une grotte close
        etape("g_entree", "Une grotte close, de nuit : sans lampe, le noir", () -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            sur(mc, () -> mc.getSingleplayerServer().overworld().setDayTime(18000));
            aller(mc, grotte.getX() + 3.5, grotte.getY(), grotte.getZ() + 5.0, 180, 0);
        }, () -> mc.level.getBlockState(grotte.offset(3, 1, 3)).isAir() && lumiereTete(mc) == 0, 3500, () -> lampeValeurs(mc));
        etape("g_lampe", "La lampe frontale se porte sur la tête : une lumière suit le joueur", () -> {
            sur(mc, () -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.HEAD,
                    mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory().getItem(1).copy()));
        }, () -> lumiereTete(mc) >= 12, 4500, () -> lampeValeurs(mc));
        etape("g_marche", "Le joueur avance : la lumière le suit, l'ancienne disparaît", () -> aller(mc, grotte.getX() + 3.5, grotte.getY(), grotte.getZ() + 1.5, 0, 0),
                () -> lumiereTete(mc) >= 12, 3500, () -> lampeValeurs(mc));
        etape("g_premiere", "Première personne", () -> mc.options.setCameraType(CameraType.FIRST_PERSON), () -> true, 2500, () -> lampeValeurs(mc));
        etape("g_retire", "La lampe est retirée : plus aucune lumière", () -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            sur(mc, () -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
        }, () -> lumiereTete(mc) == 0, 3500, () -> lampeValeurs(mc));

        // --- Scène 3 : un message reçu
        etape("r_vue", "Un récepteur de signaux alimenté, à l'écoute", () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            sur(mc, () -> mc.getSingleplayerServer().overworld().setDayTime(6000));
            regarder(mc, Vec3.atCenterOf(recepteur), Vec3.atCenterOf(recepteur).add(0.8, 1.0, 4.0));
        }, () -> instantane.recepteurEcoute(), 2500, FilmExploration::recepteurValeurs);
        etape("r_envoi", "L'équipe envoie /signal send 1 : le voyant s'allume", () -> sur(mc, () -> {
            MinecraftServer serveur = mc.getSingleplayerServer();
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            serveur.getCommands().performPrefixedCommand(joueur.createCommandSourceStack().withPermission(4), "signal send 1 500 " + MESSAGE);
        }), () -> instantane.nonLu(), 3500, FilmExploration::recepteurValeurs);
        etape("r_ouvre", "Clic droit : l'écran du récepteur montre le message", () -> {
            tenir(mc, 8);
            clicDroit(mc, recepteur, Direction.SOUTH);
        }, () -> menuOuvert(mc), 1200, FilmExploration::recepteurValeurs);
        etape("r_ecran", "Le message est archivé, le voyant s'éteint", () -> grosPlanEcran(mc), () -> menuOuvert(mc), 5000, FilmExploration::recepteurValeurs);
        etape("r_ferme", "", () -> {
            capture.pleinCadre();
            mc.player.closeContainer();
        }, () -> mc.screen == null, 800, FilmExploration::recepteurValeurs);

        // --- Scène 4 : le mécanisme
        Vec3 vueMecanisme = Vec3.atCenterOf(mecanisme).add(-3.0, 1.2, 6.0);
        etape("m_vue", "Sans câble, le mécanisme ne reçoit rien (source à 6 blocs)", () -> {
            sur(mc, () -> mc.getSingleplayerServer().overworld().setDayTime(6000));
            regarder(mc, Vec3.atCenterOf(mecanisme.south()).add(-1.5, 0.0, 0.0), vueMecanisme);
        }, () -> true, 4500, FilmExploration::mecanismeValeurs);
        etape("m_connecteurs", "Deux connecteurs de Create Crafts & Additions, posés comme un joueur", () -> sur(mc, () -> {
            ServerLevel monde = mc.getSingleplayerServer().overworld();
            ReseauxDev.cliquer(monde, CONNECTEUR, sourceMecanisme, Direction.EAST, Direction.WEST, 1, null);
            ReseauxDev.cliquer(monde, CONNECTEUR, mecanisme, Direction.WEST, Direction.EAST, 1, null);
        }), () -> mc.level.getBlockState(sourceMecanisme.east()).getBlock() == bloc(CONNECTEUR)
                && mc.level.getBlockState(mecanisme.west()).getBlock() == bloc(CONNECTEUR), 3000, FilmExploration::mecanismeValeurs);
        etape("m_cable", "Un fil de cuivre entre les deux : le courant arrive", () -> sur(mc, () -> {
            ServerLevel monde = mc.getSingleplayerServer().overworld();
            ReseauxDev.tirerFil(monde, BOBINE, sourceMecanisme.east(), mecanisme.west(), new ArrayList<>());
        }), () -> instantane.progression() > 0, 1500, FilmExploration::mecanismeValeurs);
        etape("m_progresse", "40 FE par tick pendant 8 s : ses veines s'allument",
                () -> {}, () -> instantane.actif(), 1500, FilmExploration::mecanismeValeurs);
        etape("m_porte", "Actif : signal de redstone, la porte de fer s'ouvre", () -> {}, () -> instantane.porteOuverte(), 5000,
                FilmExploration::mecanismeValeurs);
        etape("m_casser", "Le joueur essaie de le casser en survie : il reste", () -> {
            aller(mc, mecanisme.getX() + 0.5, o.getY(), mecanisme.getZ() + 3.0, 180, 10);
            tenir(mc, 8);
        }, () -> {
            if (mc.player.position().distanceTo(Vec3.atCenterOf(mecanisme)) > 4.5) {
                return false;
            }
            mc.gameMode.continueDestroyBlock(mecanisme, Direction.SOUTH);
            return true;
        }, 3500, () -> {
            if (mc.gameMode != null && mc.player.position().distanceTo(Vec3.atCenterOf(mecanisme)) < 4.5) {
                mc.gameMode.continueDestroyBlock(mecanisme, Direction.SOUTH);
            }
            return mecanismeValeurs();
        });
        etape("fin", "Fin du lot 7 (temps réel, aucune durée abrégée)", () -> {}, () -> true, 2500, FilmExploration::mecanismeValeurs);
    }

    private static int butin(Minecraft mc) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (!s.isEmpty() && !s.is(ExploreRegistry.METAL_DETECTOR.get()) && !s.is(ExploreRegistry.HEADLAMP.get())
                    && !s.is(BuiltInRegistries.ITEM.get(new ResourceLocation(CONNECTEUR)))
                    && !s.is(BuiltInRegistries.ITEM.get(new ResourceLocation(BOBINE)))) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ================================================================================================
    // Déroulement
    // ================================================================================================

    private static void jouer(Minecraft mc) {
        if (indexEtape >= ETAPES.size()) {
            return;
        }
        long maintenant = System.nanoTime();
        if (indexEtape < 0) {
            indexEtape = 0;
            actionFaite = false;
        }
        Etape e = ETAPES.get(indexEtape);
        if (!actionFaite) {
            LOG.info("[film] étape : {} | {}", e.nom(), instantane);
            e.action().run();
            actionFaite = true;
            debutAction = maintenant;
            debutRepos = 0;
            return;
        }
        if (!e.pret().getAsBoolean()) {
            if (maintenant - debutAction > 90_000_000_000L) {
                throw new IllegalStateException("étape sans issue : " + e.nom() + " | " + instantane);
            }
            return;
        }
        if (debutRepos == 0) {
            debutRepos = maintenant;
        }
        if ((maintenant - debutRepos) / 1_000_000L < e.reposMs()) {
            return;
        }
        LOG.info("[film] étape finie : {} | {}", e.nom(), instantane);
        indexEtape++;
        actionFaite = false;
        if (indexEtape >= ETAPES.size()) {
            terminer(mc);
        }
    }

    private static void terminer(Minecraft mc) {
        phase = Phase.FIN;
        int n = 0;
        if (capture != null) {
            capture.terminer(echec);
            n = capture.images();
        }
        LOG.info("[film] terminé : {} images, {}", n, echec == null ? "sans erreur" : echec);
        mc.stop();
    }
}
