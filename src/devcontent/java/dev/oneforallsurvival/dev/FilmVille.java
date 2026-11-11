package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.DesalinatorBlockEntity;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.light.LightRegistry;
import dev.oneforallsurvival.light.LighthouseBlock;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.town.ShowerHeadBlock;
import dev.oneforallsurvival.town.ShowerHeadBlockEntity;
import dev.oneforallsurvival.town.TownRegistry;
import dev.oneforallsurvival.town.WaterHeaterBlockEntity;
import dev.oneforallsurvival.town.WaterHeaterMenu;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Film de démonstration du lot 6 (développement seulement, jamais empaqueté). Un client qui se pilote seul dans un monde
 * intégré avec le pack complet : une douche, puis le phare de nuit. Vrais tuyaux de Create, vraies pompes entraînées par
 * des moteurs créatifs, vrai clic droit sur le chauffe-eau, vrai joueur sous la pomme.
 *
 * <p>Le temps du monde n'est jamais accéléré. Une seule durée est abrégée, en mémoire, et la légende à l'écran le dit : le
 * dessalinisateur reçoit d'avance 800 mB d'eau potable (il en produit 200 mB toutes les 5 s). Le reste (les 5 secondes
 * sous la pomme, le tour du phare en 8 s) est réel. Mêmes images que {@link FilmProtection} : 1280 x 656, un bandeau de
 * légende, 10 images par seconde, dans {@code frames-ville}. Activé par {@code -Doneforallsurvival.demo.ville}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmVille {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.ville") != null;
    private static final String MONDE = "film_ville";
    private static final double GARDE_S = 420;
    private static final int H_VIDE = 0;
    private static final String CREATIVE = "createaddition:creative_energy";
    private static final int PREREMPLI = 800;
    private static final int NUIT = 15000;
    private static final int JOUR = 6000;

    private enum Phase { TITRE, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    private record Etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {}

    /** Ce que le serveur sait, recopié à chaque tick pour le bandeau. */
    private record Instantane(int eauDesal, int potableDesal, String chauffeEau, int potableChauffeEau, int chaudeChauffeEau, int cuvePomme,
            int secondes, boolean pommeActive, String phare, boolean phareAllume) {
        static final Instantane VIDE = new Instantane(0, 0, "-", 0, 0, 0, 0, false, "-", false);
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

    private static BlockPos o;
    private static BlockPos phare;
    private static BlockPos plateforme;

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

    private FilmVille() {}

    // ================================================================================================
    // Boucle
    // ================================================================================================

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
            LOG.info("[film] message : {}", event.getMessage().getString());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || !preparationFaite) {
            return;
        }
        ServerLevel monde = event.getServer().overworld();
        int eau = 0;
        int potable = 0;
        if (monde.getBlockEntity(desal()) instanceof DesalinatorBlockEntity d) {
            eau = d.waterTank().getFluidAmount();
            potable = d.potableTank().getFluidAmount();
        }
        String chauffe = "-";
        int cp = 0;
        int cc = 0;
        if (monde.getBlockEntity(chauffeEau()) instanceof WaterHeaterBlockEntity h) {
            chauffe = h.state().name();
            cp = h.potable().getFluidAmount();
            cc = h.hot().getFluidAmount();
        }
        int cuve = 0;
        int secondes = 0;
        boolean active = false;
        if (monde.getBlockEntity(douche()) instanceof ShowerHeadBlockEntity s) {
            cuve = s.tank().getFluidAmount();
            for (ServerPlayer joueur : event.getServer().getPlayerList().getPlayers()) {
                secondes = Math.max(secondes, s.secondsUnder(joueur.getUUID()));
            }
            active = monde.getBlockState(douche()).getValue(ShowerHeadBlock.ACTIVE);
        }
        String etatPhare = "-";
        boolean allume = false;
        var etat = monde.getBlockState(phare);
        if (etat.is(LightRegistry.LIGHTHOUSE.get())) {
            etatPhare = etat.getValue(ElectricMachineBlock.STATE).name();
            allume = etat.getValue(LighthouseBlock.LIT);
        }
        instantane = new Instantane(eau, potable, chauffe, cp, cc, cuve, secondes, active, etatPhare, allume);
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
                    capture = new FilmCapture(mc.gameDirectory.toPath().resolve("frames-ville"),
                            "One For All Survival | lot 6 | vie de la ville");
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
        var o = mc.options;
        o.pauseOnLostFocus = false;
        o.onboardAccessibility = false;
        o.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        o.renderDistance().set(10);
        o.simulationDistance().set(8);
        o.guiScale().set(2);
        o.enableVsync().set(false);
        o.framerateLimit().set(60);
        o.hideGui = false;
        o.gamma().set(1.0);
        mc.getToasts().clear();
        mc.mouseHandler.releaseMouse();
    }

    private static void creerLeMonde(Minecraft mc) {
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        regles.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        // Pas de régénération naturelle : la vie du joueur ne bouge que par le Bien-être (qui ne la touche pas).
        regles.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, null);
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SURVIVAL, false, Difficulty.EASY, true, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // Disposition (relative à l'origine O : l'extrémité ouest de la chaîne d'eau, au niveau du sol)
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static BlockPos desal() {
        return p(5, 0, 0);
    }

    private static BlockPos chauffeEau() {
        return p(9, 0, 0);
    }

    private static BlockPos douche() {
        return p(11, 2, 0);
    }

    private static Block bloc(String id) {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
        if (b == Blocks.AIR) {
            throw new IllegalStateException(id + " absent : lancer avec -PwithPack");
        }
        return b;
    }

    private static void poser(ServerLevel monde, BlockPos pos, net.minecraft.world.level.block.state.BlockState etat) {
        monde.setBlock(pos, etat, 3);
    }

    private static void poser(ServerLevel monde, BlockPos pos, Block bloc) {
        poser(monde, pos, bloc.defaultBlockState());
    }

    private static void pompe(ServerLevel monde, BlockPos pos, Direction sens) {
        poser(monde, pos, bloc("create:mechanical_pump").defaultBlockState().setValue(BlockStateProperties.FACING, sens));
    }

    private static void moteurEtEngrenage(ServerLevel monde, BlockPos engrenage, Direction.Axis axe, BlockPos moteur, Direction sens) {
        poser(monde, engrenage, bloc("create:cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS, axe));
        poser(monde, moteur, bloc("create:creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING, sens));
    }

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos ici = joueur.blockPosition();
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, ici.getX() - 4, ici.getZ() + 8);
        o = new BlockPos(ici.getX() - 4, y, ici.getZ() + 8);
        monde.setDayTime(JOUR);

        // La chaîne : tuyaux et pompes d'abord, machines ensuite (les tuyaux relisent leurs voisins quand une machine arrive).
        Block tuyau = bloc("create:fluid_pipe");
        for (int[] mur : new int[][] {{-1, 0}, {0, -1}, {0, 1}, {1, -2}, {1, 2}, {2, -1}, {2, 1}}) {
            poser(monde, p(mur[0], 0, mur[1]), Blocks.STONE);
        }
        // Un puits de quatre sources : la pompe vide la source du bout, les trois autres la refont.
        for (int[] source : new int[][] {{0, 0}, {1, -1}, {1, 1}, {1, 0}}) {
            poser(monde, p(source[0], 0, source[1]), Blocks.WATER);
        }
        poser(monde, p(2, 0, 0), tuyau);
        pompe(monde, p(3, 0, 0), Direction.EAST);
        poser(monde, p(4, 0, 0), tuyau);
        poser(monde, p(6, 0, 0), tuyau);
        pompe(monde, p(7, 0, 0), Direction.EAST);
        poser(monde, p(8, 0, 0), tuyau);
        poser(monde, p(9, 1, 0), tuyau);
        pompe(monde, p(9, 2, 0), Direction.UP);
        poser(monde, p(9, 3, 0), tuyau);
        poser(monde, p(10, 3, 0), tuyau);
        poser(monde, p(11, 3, 0), tuyau);
        poser(monde, desal(), FoodRegistry.DESALINATOR.get());
        poser(monde, p(5, 0, -1), bloc(CREATIVE));
        poser(monde, chauffeEau(), TownRegistry.WATER_HEATER.get());
        poser(monde, p(9, 0, -1), bloc(CREATIVE));
        poser(monde, douche(), TownRegistry.SHOWER_HEAD.get());

        // Le phare : une tour de pierre depuis le sol jusqu'à plus de 8 blocs au-dessus de la mer (63), loin de la chaîne.
        int sommet = Math.max(y + 8, 63 + 8 + 2);
        phare = new BlockPos(o.getX() + 24, sommet, o.getZ() + 30);
        for (int h = y - 1; h < sommet; h++) {
            poser(monde, phare.atY(h), Blocks.STONE_BRICKS);
        }
        // Une plateforme pour le joueur, sur un pilier, à 10 blocs sous le phare et 16 blocs de lui : les faisceaux se voient d'en dessous.
        plateforme = phare.offset(16, -11, 16);
        for (int h = y - 1; h < plateforme.getY(); h++) {
            poser(monde, plateforme.atY(h), Blocks.STONE_BRICKS);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                poser(monde, plateforme.offset(dx, 0, dz), Blocks.STONE_BRICKS);
            }
        }

        joueur.getInventory().clearContent();
        preparationFaite = true;
    }

    // ================================================================================================
    // Outils du scénario
    // ================================================================================================

    private static void regarder(Minecraft mc, BlockPos cible, Vec3 oeil) {
        regarder(mc, Vec3.atCenterOf(cible), oeil);
    }

    /** Place le joueur à l'œil {@code oeil}, le regard sur {@code centre}. */
    private static void regarder(Minecraft mc, Vec3 centre, Vec3 oeil) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        double dx = centre.x - oeil.x;
        double dy = centre.y - oeil.y;
        double dz = centre.z - oeil.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0)
                .teleportTo(serveur.overworld(), oeil.x, oeil.y - 1.62, oeil.z, yaw, pitch));
    }

    private static Vec3 depuis(BlockPos cible, double dx, double dy, double dz) {
        return Vec3.atCenterOf(cible).add(dx, dy, dz);
    }

    private static void clicDroit(Minecraft mc, BlockPos pos) {
        mc.player.getInventory().selected = H_VIDE;
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.4, 0), Direction.UP, pos, false));
        LOG.info("[film] clic droit en {} : {}", pos, res);
    }

    private static void etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {
        ETAPES.add(new Etape(nom, legende, action, pret, reposMs, valeurs));
    }

    private static void sur(Minecraft mc, Runnable travail) {
        mc.getSingleplayerServer().execute(travail);
    }

    private static boolean menuOuvert(Minecraft mc, Class<?> type) {
        return mc.screen instanceof AbstractContainerScreen<?> s && type.isInstance(s.getMenu());
    }

    private static void grosPlanEcran(Minecraft mc) {
        double e = mc.getWindow().getGuiScale();
        capture.grosPlan(mc.getWindow().getGuiScaledWidth() / 2.0 * e, mc.getWindow().getGuiScaledHeight() / 2.0 * e, 1.9);
    }

    private static MobEffectInstance bienEtre(Minecraft mc) {
        return mc.player.getEffect(TownRegistry.WELL_BEING.get());
    }

    private static String chaineValeurs() {
        Instantane i = instantane;
        return "dessalinisateur eau " + i.eauDesal() + " / potable " + i.potableDesal() + " mB | chauffe-eau " + i.chauffeEau() + " potable "
                + i.potableChauffeEau() + " / chaude " + i.chaudeChauffeEau() + " mB | pomme " + i.cuvePomme() + " mB";
    }

    private static String doucheValeurs(Minecraft mc) {
        Instantane i = instantane;
        MobEffectInstance e = mc.player == null ? null : bienEtre(mc);
        return "pomme " + i.cuvePomme() + " mB, " + (i.pommeActive() ? "coule" : "à sec") + " | secondes d'eau chaude continues "
                + i.secondes() + "/5 | Bien-être : " + (e == null ? "aucun" : "oui, " + e.getDuration() / 20 + " s restantes") + " | vie "
                + (mc.player == null ? 0 : mc.player.getHealth()) + "/20";
    }

    private static String phareValeurs() {
        Instantane i = instantane;
        return "phare : " + i.phare() + " | " + (i.phareAllume() ? "allumé" : "éteint") + " | 32 FE par tick, 2 faisceaux, un tour en 8 s";
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    private static void construireScenario(Minecraft mc) {
        Vec3 vueChaine = depuis(p(5, 0, 0), 0.5, 3.2, 10.0);
        etape("cadrage", "", () -> regarder(mc, depuis(p(5, 0, 0), 0.5, 1.2, 0), vueChaine), () -> true, 2500, () -> "");

        // --- Scène 1 : la chaîne d'eau et la douche
        etape("c_vue", "Chaîne d'eau : puits, 3 pompes Create, machines, douche",
                () -> mc.player.getInventory().selected = H_VIDE, () -> true, 4000, () -> "pas encore de moteur | " + chaineValeurs());
        etape("c_moteurs", "ABRÉGÉ (film) : " + PREREMPLI + " mB d'eau potable déjà produits",
                () -> sur(mc, () -> {
                    ServerLevel monde = mc.getSingleplayerServer().overworld();
                    moteurEtEngrenage(monde, p(3, 1, 0), Direction.Axis.X, p(2, 1, 0), Direction.EAST);
                    moteurEtEngrenage(monde, p(7, 1, 0), Direction.Axis.X, p(6, 1, 0), Direction.EAST);
                    moteurEtEngrenage(monde, p(8, 2, 0), Direction.Axis.Y, p(8, 3, 0), Direction.DOWN);
                    ((DesalinatorBlockEntity) monde.getBlockEntity(desal())).potableTank()
                            .setFluid(new FluidStack(FoodRegistry.POTABLE_WATER.get(), PREREMPLI));
                }), () -> instantane.chaudeChauffeEau() > 0 || instantane.cuvePomme() > 0, 4500,
                () -> "3 moteurs créatifs en marche | " + chaineValeurs());
        etape("c_pres", "Le chauffe-eau", () -> regarder(mc, chauffeEau(), depuis(chauffeEau(), 0.0, 1.4, 3.0)), () -> true, 1500,
                FilmVille::chaineValeurs);
        etape("c_ouvre", "Clic droit sur le chauffe-eau : son écran", () -> clicDroit(mc, chauffeEau()),
                () -> menuOuvert(mc, WaterHeaterMenu.class), 1000, FilmVille::chaineValeurs);
        etape("c_ecran", "5 mB d'eau potable → eau chaude par tick, 40 FE par tick",
                () -> grosPlanEcran(mc), () -> menuOuvert(mc, WaterHeaterMenu.class), 5000, FilmVille::chaineValeurs);
        etape("c_ferme", "", () -> {
            capture.pleinCadre();
            mc.player.closeContainer();
        }, () -> mc.screen == null, 800, FilmVille::chaineValeurs);
        Vec3 vuePomme = depuis(douche(), 3.2, -0.6, 4.2);
        etape("c_pomme", "L'eau chaude monte par tuyau jusqu'à la pomme de douche",
                () -> regarder(mc, depuis(douche(), 0.0, -0.4, 0.0), vuePomme), () -> instantane.cuvePomme() >= 300, 3000,
                FilmVille::chaineValeurs);
        etape("d_dessous", "Sous la pomme : 5 secondes continues d'eau chaude", () -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            sur(mc, () -> {
                ServerPlayer joueur = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                joueur.teleportTo(mc.getSingleplayerServer().overworld(), douche().getX() + 0.5, o.getY(), douche().getZ() + 0.5, 180f, 15f);
            });
        }, () -> bienEtre(mc) != null, 3500, () -> doucheValeurs(mc));
        etape("d_effet", "Bien-être : +5 % marche, +10 % minage, aucun soin", () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            regarder(mc, depuis(douche(), 0, 0, 0), new Vec3(douche().getX() + 0.5, o.getY() + 1.62, douche().getZ() + 0.5));
        }, () -> true, 4500, () -> doucheValeurs(mc));

        // --- Scène 2 : le phare de nuit
        etape("p_nuit", "De nuit, le phare au sommet d'une tour libre", () -> {
            sur(mc, () -> {
                ServerLevel monde = mc.getSingleplayerServer().overworld();
                monde.setDayTime(NUIT);
                poser(monde, phare, LightRegistry.LIGHTHOUSE.get());
                poser(monde, phare.west(), bloc(CREATIVE));
            });
            regarder(mc, depuis(phare, 0, 0, 0), depuis(plateforme, 0, 2.62, 0));
        }, () -> instantane.phareAllume(), 3000, FilmVille::phareValeurs);
        etape("p_tour", "Deux faisceaux opposés, un tour en 8 s, visibles de loin", () -> {}, () -> true, 14000, FilmVille::phareValeurs);
        etape("p_toit", "Un toit de pierre : le phare s'éteint, il ne consomme plus",
                () -> sur(mc, () -> poser(mc.getSingleplayerServer().overworld(), phare.above(2), Blocks.STONE_BRICKS)),
                () -> !instantane.phareAllume(), 4000, FilmVille::phareValeurs);
        etape("p_rallume", "Le toit est retiré : le phare se rallume",
                () -> sur(mc, () -> poser(mc.getSingleplayerServer().overworld(), phare.above(2), Blocks.AIR)),
                () -> instantane.phareAllume(), 6000, FilmVille::phareValeurs);
        etape("fin", "Fin du lot 6 (temps réel, une seule durée abrégée)", () -> {}, () -> true, 2500,
                FilmVille::phareValeurs);
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
