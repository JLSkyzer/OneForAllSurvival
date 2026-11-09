package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.fence.ElectrifierBlockEntity;
import dev.oneforallsurvival.fence.FenceRegistry;
import dev.oneforallsurvival.food.CanteenPotBlockEntity;
import dev.oneforallsurvival.food.CanteenPotMenu;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.HotPlateBlock;
import dev.oneforallsurvival.food.IncubatorBlockEntity;
import dev.oneforallsurvival.food.IncubatorMenu;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Film de démonstration du lot 5 (développement seulement, jamais empaqueté). Un client qui se pilote seul dans un monde
 * intégré avec le pack complet, la nuit : vrais clics droits, vrais clics majuscule dans les menus, vraies machines et
 * vraie clôture. Une éclosion, une couvée perdue, une plaque sous un Cooking Pot et une fournée de cantine, un zombie sur
 * la clôture (en coude) puis le courant coupé.
 *
 * <p>Le temps du monde n'est jamais accéléré ; en revanche les durées longues (vingt minutes d'incubation, soixante secondes de
 * coupure) sont abrégées en mémoire, par un état de la machine avancé à quelques secondes du terme, et la légende à l'écran le
 * dit chaque fois. Mêmes images que {@link FilmVivres} : 1280 x 656, un bandeau de légende, 10 images par seconde, dans
 * {@code frames-protection}. Activé par {@code -Doneforallsurvival.demo.protection}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmProtection {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.protection") != null;
    private static final String MONDE = "film_protection";
    private static final double GARDE_S = 480;
    private static final int H_VIDE = 0;
    private static final int H_OEUF = 1;
    private static final int H_BOEUF = 2;
    private static final int H_CAROTTE = 3;
    private static final int H_PATATE = 4;
    private static final int H_BOLS = 5;
    private static final String CREATIVE = "createaddition:creative_energy";
    private static final String POT = "farmersdelight:cooking_pot";
    private static final String RAGOUT = "farmersdelight:beef_stew";
    /** Ticks qu'il reste à l'œuf, ou à la coupure, pour finir : 6 s et 4 s. */
    private static final int RESTE_ECLOSION = 120;
    private static final int RESTE_COUPURE = 80;

    private enum Phase { TITRE, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    private record Etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {}

    /** Ce que le serveur sait, recopié à chaque tick pour le bandeau. */
    private record Instantane(String couveuse, int progression, int duree, int coupure, int seuil, boolean oeuf, int poussins,
            String etatCouveuse, String plaque, String cantine, int ragouts, String cloture, double vieZombie, double repousse,
            double vieMin) {
        static final Instantane VIDE = new Instantane("-", 0, 0, 0, 0, false, 0, "-", "-", "-", 0, "-", -1, 0, 1000);
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

    private static BlockPos couveuse;
    private static BlockPos sourceCouveuse;
    private static BlockPos plaque;
    private static BlockPos sourcePlaque;
    private static BlockPos cantine;
    private static BlockPos sourceCantine;
    private static BlockPos electrificateur;
    private static BlockPos sourceCloture;
    private static BlockPos coude;
    private static volatile Zombie zombie;
    private static volatile double zombiePlusProche = 1000;
    private static volatile double zombieRepousse;
    private static volatile double vieZombieMin = 1000;

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

    private FilmProtection() {}

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
        String nomEtat = "-";
        String couv = "-";
        int prog = 0;
        int duree = 0;
        int coupure = 0;
        int seuil = 0;
        boolean oeuf = false;
        if (monde.getBlockEntity(couveuse) instanceof IncubatorBlockEntity c) {
            prog = c.progress(0);
            duree = dev.oneforallsurvival.food.FoodConfig.incubatorHatchTicks();
            coupure = c.outageTicks();
            seuil = dev.oneforallsurvival.food.FoodConfig.incubatorOutageTicks();
            oeuf = !c.inventory().getStackInSlot(0).isEmpty();
            nomEtat = c.state().name();
            couv = (oeuf ? "œuf" : "vide") + " " + prog + "/" + duree + " ticks | coupure " + coupure + "/" + seuil + " ticks | " + nomEtat;
        }
        int poussins = 0;
        for (Chicken c : monde.getEntitiesOfClass(Chicken.class, new AABB(couveuse).inflate(6))) {
            if (c.isBaby()) {
                poussins++;
            }
        }
        BlockPosEtat plaqueEtat = etatDe(monde, plaque);
        String pl = plaqueEtat.nom() + (monde.getBlockState(plaque).is(FoodRegistry.HOT_PLATE.get())
                && monde.getBlockState(plaque).getValue(HotPlateBlock.LIT) ? " allumée" : " éteinte");
        String cant = "-";
        int ragouts = 0;
        if (monde.getBlockEntity(cantine) instanceof CanteenPotBlockEntity p) {
            ItemStack sortie = p.inventory().getStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT);
            ragouts = sortie.getCount();
            cant = p.state().name() + " | cuisson " + p.progress() + " | sortie " + sortie.getCount() + " ragoût(s)";
        }
        String clot = "-";
        if (monde.getBlockEntity(electrificateur) instanceof ElectrifierBlockEntity e) {
            clot = e.state().name() + " | réserve " + e.energy().getEnergyStored() + " FE";
        }
        Zombie z = zombie;
        double vie = z == null || !z.isAlive() ? -1 : z.getHealth();
        if (z != null && z.isAlive()) {
            // « Repoussé » : de combien le zombie s'éloigne vers le sud après avoir touché la barre au plus près.
            zombiePlusProche = Math.min(zombiePlusProche, z.getZ());
            zombieRepousse = Math.max(zombieRepousse, z.getZ() - zombiePlusProche);
            vieZombieMin = Math.min(vieZombieMin, z.getHealth());
        }
        instantane = new Instantane(couv, prog, duree, coupure, seuil, oeuf, poussins, nomEtat, pl, cant, ragouts, clot, vie,
                zombieRepousse, vieZombieMin);
    }

    private record BlockPosEtat(String nom) {}

    private static BlockPosEtat etatDe(ServerLevel monde, BlockPos p) {
        var s = monde.getBlockState(p);
        return new BlockPosEtat(s.hasProperty(ElectricMachineBlock.STATE) ? s.getValue(ElectricMachineBlock.STATE).name() : "-");
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
                    capture = new FilmCapture(mc.gameDirectory.toPath().resolve("frames-protection"),
                            "One For All Survival | lot 5 | vivres et protection");
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
        o.renderDistance().set(8);
        o.simulationDistance().set(6);
        o.guiScale().set(2);
        o.enableVsync().set(false);
        o.framerateLimit().set(60);
        o.hideGui = false;
        // La nuit : la luminosité au maximum pour que les machines se lisent (réglage du film, en mémoire).
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
        // Niveau facile : un zombie ne disparaît pas comme en paisible.
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SURVIVAL, false, Difficulty.EASY, true, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // Préparation côté serveur
    // ================================================================================================

    private static Block bloc(String id) {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
        if (b == Blocks.AIR) {
            throw new IllegalStateException(id + " absent : lancer avec -PwithPack");
        }
        return b;
    }

    private static void reserve(ElectricMachineBlockEntity m, int fe) {
        CompoundTag tag = m.saveWithoutMetadata();
        tag.putInt("energy", fe);
        m.load(tag);
    }

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos ici = joueur.blockPosition();
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, ici.getX() + 6, ici.getZ());
        BlockPos s = new BlockPos(ici.getX() + 6, y, ici.getZ());
        monde.setDayTime(15000);

        Block creative = bloc(CREATIVE);
        couveuse = s;
        sourceCouveuse = s.west();
        monde.setBlock(couveuse, FoodRegistry.INCUBATOR.get().defaultBlockState(), 3);
        monde.setBlock(sourceCouveuse, creative.defaultBlockState(), 3);

        sourcePlaque = s.offset(4, 0, 0);
        plaque = s.offset(5, 0, 0);
        monde.setBlock(plaque, FoodRegistry.HOT_PLATE.get().defaultBlockState(), 3);
        monde.setBlock(plaque.above(), bloc(POT).defaultBlockState(), 3);
        monde.setBlock(sourcePlaque, creative.defaultBlockState(), 3);

        sourceCantine = s.offset(8, 0, 0);
        cantine = s.offset(9, 0, 0);
        monde.setBlock(cantine, FoodRegistry.CANTEEN_POT.get().defaultBlockState(), 3);
        monde.setBlock(sourceCantine, creative.defaultBlockState(), 3);

        // La clôture : cinq clôtures vers l'est puis un coude de deux vers le sud.
        electrificateur = s.offset(0, 0, 10);
        sourceCloture = electrificateur.west();
        for (int i = 1; i <= 5; i++) {
            monde.setBlock(electrificateur.east(i), FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState(), 3);
        }
        coude = electrificateur.offset(5, 0, 0);
        monde.setBlock(coude.south(), FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState(), 3);
        monde.setBlock(coude.south(2), FenceRegistry.ELECTRIC_FENCE.get().defaultBlockState(), 3);
        monde.setBlock(electrificateur, FenceRegistry.ELECTRIFIER.get().defaultBlockState(), 3);
        monde.setBlock(sourceCloture, creative.defaultBlockState(), 3);
        // L'enclos du zombie, au sud de la ligne : pierre à l'ouest et au sud, trois blocs de haut ; le coude le ferme à l'est.
        for (int h = 0; h < 3; h++) {
            for (int dz = 1; dz <= 3; dz++) {
                monde.setBlock(electrificateur.offset(0, h, dz), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int dx = 1; dx <= 5; dx++) {
                monde.setBlock(electrificateur.offset(dx, h, 3), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        joueur.getInventory().clearContent();
        joueur.getInventory().setItem(H_OEUF, new ItemStack(Items.EGG, 1));
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

    private static void clicDroit(Minecraft mc, BlockPos pos, int fente) {
        mc.player.getInventory().selected = fente;
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.4, 0), Direction.UP, pos, false));
        LOG.info("[film] clic droit en {} avec {} : {}", pos, mc.player.getMainHandItem(), res);
    }

    private static void etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {
        ETAPES.add(new Etape(nom, legende, action, pret, reposMs, valeurs));
    }

    private static MinecraftServer serveur(Minecraft mc) {
        return mc.getSingleplayerServer();
    }

    private static void grosPlanEcran(Minecraft mc) {
        double e = mc.getWindow().getGuiScale();
        capture.grosPlan(mc.getWindow().getGuiScaledWidth() / 2.0 * e, mc.getWindow().getGuiScaledHeight() / 2.0 * e, 1.9);
    }

    private static boolean menuOuvert(Minecraft mc, Class<?> type) {
        return mc.screen instanceof AbstractContainerScreen<?> s && type.isInstance(s.getMenu());
    }

    /** Un clic majuscule sur la première pile du joueur qui répond à {@code item}. */
    private static void rentrer(Minecraft mc, net.minecraft.world.item.Item item) {
        var menu = ((AbstractContainerScreen<?>) mc.screen).getMenu();
        for (var slot : menu.slots) {
            if (slot.container == mc.player.getInventory() && slot.getItem().is(item)) {
                mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, mc.player);
                return;
            }
        }
        throw new IllegalStateException(item + " absent de l'inventaire");
    }

    private static void donner(Minecraft mc, int fente, ItemStack pile) {
        MinecraftServer s = serveur(mc);
        s.execute(() -> s.getPlayerList().getPlayers().get(0).getInventory().setItem(fente, pile));
    }

    private static boolean aDansLInventaire(Minecraft mc, net.minecraft.world.item.Item item, int nombre) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n >= nombre;
    }

    private static void sur(Minecraft mc, Runnable travail) {
        MinecraftServer s = serveur(mc);
        s.execute(travail);
    }

    private static void invoquerZombie(Minecraft mc, boolean sansIA) {
        sur(mc, () -> {
            ServerLevel monde = serveur(mc).overworld();
            Zombie z = EntityType.ZOMBIE.create(monde);
            z.setPersistenceRequired();
            z.setNoAi(sansIA);
            z.setSilent(true);
            z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            // Dans l'enclos, contre la barre : à 0,025 bloc d'elle, sous la marge de contact. Sans courant il est figé (sans IA).
            z.moveTo(electrificateur.getX() + 2.5, electrificateur.getY(), electrificateur.getZ() + 0.95, 180f, 0f);
            zombiePlusProche = 1000;
            zombieRepousse = 0;
            vieZombieMin = 1000;
            monde.addFreshEntity(z);
            zombie = z;
        });
    }

    private static String couveuseValeurs() {
        Instantane i = instantane;
        return "couveuse : " + i.couveuse() + " | poussins nés " + i.poussins();
    }

    private static String plaqueValeurs() {
        Instantane i = instantane;
        return "plaque : " + i.plaque() + " | marmite de cantine : " + i.cantine();
    }

    private static String clotureValeurs() {
        Instantane i = instantane;
        return "électrificateur : " + i.cloture() + " | zombie : " + (i.vieZombie() < 0 ? "mort ou absent" : (int) i.vieZombie() + "/20 points")
                + ", repoussé de " + String.format(java.util.Locale.FRANCE, "%.2f", i.repousse()) + " bloc";
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    private static void construireScenario(Minecraft mc) {
        Vec3 vueCouveuse = depuis(couveuse, -2.4, 1.3, -3.0);
        etape("cadrage", "", () -> regarder(mc, couveuse, vueCouveuse), () -> true, 2500, () -> "");

        // --- Scène 1 : une éclosion
        etape("c_vue", "Une couveuse électrique, alimentée par une source Create, de nuit", () -> mc.player.getInventory().selected = H_VIDE,
                () -> true, 3000, FilmProtection::couveuseValeurs);
        etape("c_ouvre", "Clic droit : le menu de la couveuse, quatre places, un œuf chacune",
                () -> clicDroit(mc, couveuse, H_VIDE), () -> menuOuvert(mc, IncubatorMenu.class), 800, FilmProtection::couveuseValeurs);
        etape("c_oeuf", "Un œuf entre dans la couveuse (clic majuscule)", () -> {
            grosPlanEcran(mc);
            rentrer(mc, Items.EGG);
        }, () -> instantane.oeuf(), 1500, FilmProtection::couveuseValeurs);
        etape("c_couve", "Elle couve : la progression avance, l'énergie coûte 8 FE par tick",
                () -> {}, () -> instantane.progression() > 40 && instantane.etatCouveuse().equals("RUNNING"), 3000, FilmProtection::couveuseValeurs);
        etape("c_abrege", "DURÉE ABRÉGÉE (film) : l'œuf a déjà couvé presque 20 min, il lui reste 6 s", () -> sur(mc, () -> {
            IncubatorBlockEntity c = (IncubatorBlockEntity) serveur(mc).overworld().getBlockEntity(couveuse);
            CompoundTag tag = c.saveWithoutMetadata();
            tag.putIntArray("progress", new int[] {dev.oneforallsurvival.food.FoodConfig.incubatorHatchTicks() - RESTE_ECLOSION, 0, 0, 0});
            c.load(tag);
        }), () -> instantane.progression() >= dev.oneforallsurvival.food.FoodConfig.incubatorHatchTicks() - RESTE_ECLOSION, 2000,
                FilmProtection::couveuseValeurs);
        etape("c_eclot", "L'œuf éclot : un poussin naît au-dessus de la couveuse, la place est libre", () -> {},
                () -> !instantane.oeuf() && instantane.poussins() >= 1, 1000, FilmProtection::couveuseValeurs);
        etape("c_ferme", "Le poussin est né, la couveuse est libre pour un autre œuf", () -> {
            capture.pleinCadre();
            mc.player.closeContainer();
        }, () -> mc.screen == null, 3500, FilmProtection::couveuseValeurs);

        // --- Scène 2 : une couvée perdue
        etape("p_oeuf2", "Un second œuf, dans la même couveuse", () -> {
            donner(mc, H_OEUF, new ItemStack(Items.EGG, 1));
            clicDroit(mc, couveuse, H_VIDE);
        }, () -> menuOuvert(mc, IncubatorMenu.class) && aDansLInventaire(mc, Items.EGG, 1), 600, FilmProtection::couveuseValeurs);
        etape("p_entre", "Il entre à son tour", () -> {
            grosPlanEcran(mc);
            rentrer(mc, Items.EGG);
        }, () -> instantane.oeuf() && instantane.etatCouveuse().equals("RUNNING"), 2000, FilmProtection::couveuseValeurs);
        etape("p_coupe", "Courant coupé : source retirée, réserve vidée, le compteur de coupure monte", () -> sur(mc, () -> {
            ServerLevel monde = serveur(mc).overworld();
            monde.setBlock(sourceCouveuse, Blocks.AIR.defaultBlockState(), 3);
            reserve((ElectricMachineBlockEntity) monde.getBlockEntity(couveuse), 0);
        }), () -> instantane.etatCouveuse().equals("NO_POWER") && instantane.coupure() > 60, 1500, FilmProtection::couveuseValeurs);
        etape("p_abrege", "DURÉE ABRÉGÉE (film) : le compteur de coupure est avancé à 56 s sur 60 s", () -> sur(mc, () -> {
            IncubatorBlockEntity c = (IncubatorBlockEntity) serveur(mc).overworld().getBlockEntity(couveuse);
            CompoundTag tag = c.saveWithoutMetadata();
            tag.putInt("outage", dev.oneforallsurvival.food.FoodConfig.incubatorOutageTicks() - RESTE_COUPURE);
            c.load(tag);
        }), () -> instantane.coupure() >= dev.oneforallsurvival.food.FoodConfig.incubatorOutageTicks() - RESTE_COUPURE, 800,
                FilmProtection::couveuseValeurs);
        etape("p_ferme", "On referme le menu et on regarde la couveuse", () -> {
            capture.pleinCadre();
            mc.player.closeContainer();
        }, () -> mc.screen == null, 300, FilmProtection::couveuseValeurs);
        etape("p_perdue", "La coupure dépasse 60 s : la couvée est perdue, fumée et son d'extinction", () -> {},
                () -> !instantane.oeuf(), 3500, FilmProtection::couveuseValeurs);

        // --- Scène 3 : la plaque sous un Cooking Pot, puis une fournée de cantine
        Vec3 vuePlaque = depuis(plaque, 2.0, 1.6, -3.4);
        etape("q_vue", "Plaque électrique sous un Cooking Pot (Farmer's Delight) : allumée tant qu'il est posé", () -> {
            mc.player.getInventory().selected = H_VIDE;
            regarder(mc, depuis(plaque, 2.0, 0.6, 0), vuePlaque);
        }, () -> instantane.plaque().contains("allumée"), 4000, FilmProtection::plaqueValeurs);
        etape("q_donne", "Une marmite de cantine à côté : un bœuf, une carotte, une pomme de terre et trois bols", () -> {
            donner(mc, H_BOEUF, new ItemStack(Items.BEEF, 1));
            donner(mc, H_CAROTTE, new ItemStack(Items.CARROT, 1));
            donner(mc, H_PATATE, new ItemStack(Items.POTATO, 1));
            donner(mc, H_BOLS, new ItemStack(Items.BOWL, 3));
        }, () -> aDansLInventaire(mc, Items.BOWL, 3) && aDansLInventaire(mc, Items.BEEF, 1), 1500, FilmProtection::plaqueValeurs);
        etape("q_vue2", "La marmite de cantine", () -> regarder(mc, cantine, depuis(cantine, -1.2, 1.4, -2.8)), () -> true, 2000,
                FilmProtection::plaqueValeurs);
        etape("q_ouvre", "Clic droit sur la marmite de cantine : 6 ingrédients, 1 récipient, la sortie",
                () -> clicDroit(mc, cantine, H_VIDE), () -> menuOuvert(mc, CanteenPotMenu.class), 800, FilmProtection::plaqueValeurs);
        etape("q_charge", "Les ingrédients et les bols entrent (clics majuscule) : un bol par portion exigé", () -> {
            grosPlanEcran(mc);
            rentrer(mc, Items.BEEF);
            rentrer(mc, Items.CARROT);
            rentrer(mc, Items.POTATO);
            rentrer(mc, Items.BOWL);
        }, () -> menuOuvert(mc, CanteenPotMenu.class) && ((CanteenPotMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).containersMissing() == 0
                && instantane.cantine().startsWith("RUNNING"), 2500, FilmProtection::plaqueValeurs);
        etape("q_cuit", "Elle cuit : 30 FE par tick, 3 portions pour un seul jeu d'ingrédients", () -> {},
                () -> instantane.ragouts() == 3, 3500, FilmProtection::plaqueValeurs);
        etape("q_prend", "Les trois ragoûts sont repris (clic majuscule sur la sortie)", () -> {
            var menu = ((AbstractContainerScreen<?>) mc.screen).getMenu();
            mc.gameMode.handleInventoryMouseClick(menu.containerId, CanteenPotBlockEntity.SLOT_OUTPUT, 0, ClickType.QUICK_MOVE, mc.player);
        }, () -> aDansLInventaire(mc, BuiltInRegistries.ITEM.get(new ResourceLocation(RAGOUT)), 3), 2500, FilmProtection::plaqueValeurs);
        etape("q_ferme", "", () -> {
            capture.pleinCadre();
            mc.player.closeContainer();
        }, () -> mc.screen == null, 2500, FilmProtection::plaqueValeurs);

        // --- Scène 4 : la clôture, un zombie, le courant coupé
        Vec3 vueCloture = depuis(electrificateur, 7.6, 1.9, 1.6);
        etape("f_vue", "Une clôture électrique en coude, alimentée par son électrificateur (vue du coude)", () -> {
            mc.player.getInventory().selected = H_VIDE;
            regarder(mc, depuis(electrificateur, 3.8, 0.6, 1.0), vueCloture);
        }, () -> instantane.cloture().startsWith("RUNNING"), 3500, FilmProtection::clotureValeurs);
        etape("f_zombie", "Un zombie dans un enclos, contre la clôture : 2 points par choc, repoussé", () -> invoquerZombie(mc, false),
                () -> instantane.vieMin() < 20 && instantane.repousse() >= 0.35, 3500,
                FilmProtection::clotureValeurs);
        etape("f_coupe", "Courant coupé : source retirée, réserve vidée, zombie neuf contre la clôture", () -> sur(mc, () -> {
            ServerLevel monde = serveur(mc).overworld();
            if (zombie != null) {
                zombie.discard();
            }
            monde.setBlock(sourceCloture, Blocks.AIR.defaultBlockState(), 3);
            reserve((ElectricMachineBlockEntity) monde.getBlockEntity(electrificateur), 0);
        }), () -> instantane.cloture().startsWith("NO_POWER"), 1500, FilmProtection::clotureValeurs);
        etape("f_zombie2", "Sans courant, plus rien : le zombie, figé contre la clôture, ne subit rien (8 s)", () -> invoquerZombie(mc, true),
                () -> System.nanoTime() - debutAction > 8_000_000_000L && instantane.vieZombie() == 20 && instantane.vieMin() == 20
                        && instantane.repousse() < 0.2,
                1500, FilmProtection::clotureValeurs);
        etape("fin", "Fin du lot 5 (temps réel ; durées longues abrégées et annoncées à l'écran)",
                () -> {}, () -> true, 2500, FilmProtection::clotureValeurs);
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
            if (maintenant - debutAction > 60_000_000_000L) {
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
