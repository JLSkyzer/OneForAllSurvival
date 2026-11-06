package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.EngineerBenchBlock;
import dev.oneforallsurvival.bench.EngineerBenchBlockEntity;
import dev.oneforallsurvival.bench.WorkshopSavedData;
import dev.oneforallsurvival.bench.client.BenchClientState;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.repair.GalvanicBathBlockEntity;
import dev.oneforallsurvival.repair.GalvanicBathMenu;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.repair.WelderEnergy;
import dev.oneforallsurvival.repair.client.GalvanicBathScreen;
import dev.oneforallsurvival.role.RoleSavedData;
import dev.oneforallsurvival.salvage.SalvageRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
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
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Film de démonstration du métier de réparation (développement seulement, jamais empaqueté). Un client qui se
 * pilote seul dans un monde intégré avec le pack complet : vrais clics droits, vraies commandes, vrais paquets.
 * Une seule triche, annoncée à l'écran : la durée de vie des machines est raccourcie en mémoire le temps de
 * l'usure, et les flammes de panne, tirées au sort en jeu, sont forcées. Rien ne touche au fichier de réglages.
 * Mêmes images que {@link FilmEtabli} : 1280 x 656, un bandeau de légende, 10 images par seconde, dans
 * {@code frames-reparation}. Activé par {@code -Doneforallsurvival.demo.reparation}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmReparation {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.reparation") != null;
    private static final String MONDE = "film_reparation";
    private static final int COPIES = 40;
    private static final int FENTE_TESTEUR = 0;
    private static final int FENTE_POSTE = 1;
    private static final int FENTE_PIOCHE = 2;
    private static final int FENTE_VIDE = 8;
    /** Durée de vie (heures) du film : d'abord très vite jusqu'au seuil de fumée, puis assez lent pour la voir. */
    private static final double VIE_RAPIDE = 0.002;
    private static final double VIE_FUMEE = 0.008;
    private static final double VIE_NORMALE = 12.0;
    private static final double SEUIL_RALENTI = 21.0;

    private enum Phase { TITRE, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    private record Etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs,
            Supplier<String> valeurs) {}

    /** Ce que le serveur sait des machines, recopié à chaque tick pour que le client l'affiche sans courir. */
    private record Instantane(double etat, String panne, FaultType attendue, boolean protegee, String etatBloc, int energie,
            int capacite, int flammes, int file, String role, boolean debris, String butin, boolean bainEnMarche) {
        static final Instantane VIDE = new Instantane(100, null, FaultType.FUSE, false, "-", 0, 0, 0, 0, "-", true, "", false);
    }

    private static Phase phase = Phase.TITRE;
    private static boolean dansUneEtape;
    private static long debutPhaseNs;
    private static final List<Etape> ETAPES = new ArrayList<>();
    private static int indexEtape = -1;
    private static boolean actionFaite;
    private static long debutAction;
    private static long debutRepos;
    private static FilmCapture capture;
    private static String echec;

    private static volatile boolean preparationFaite;
    private static volatile boolean accelerer;
    private static BlockPos banc;
    private static BlockPos bain;
    private static BlockPos debris;
    private static volatile Instantane instantane = Instantane.VIDE;
    private static BenchRecipe recette;
    private static String dernierMessage = "";
    private static BlockPos minage;
    private static long debutMinage;
    private static boolean casseVue;
    private static int fileAvantReprise;
    private static int batterieAvant;
    private static Item pieceBrute;

    private FilmReparation() {}

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
            avancer(mc);
            // La première étape, le cadrage, n'est pas filmée.
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
        if (minage != null && mc.level != null && mc.gameMode != null) {
            // Le vrai geste du joueur qui garde le bouton enfoncé : mêmes appels et mêmes paquets.
            casseVue |= mc.gameMode.getDestroyStage() >= 0;
            mc.gameMode.continueDestroyBlock(minage, Direction.WEST);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (ACTIF) {
            dernierMessage = event.getMessage().getString();
            LOG.info("[film] message : {}", dernierMessage);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || banc == null) {
            return;
        }
        MinecraftServer serveur = event.getServer();
        ServerLevel monde = serveur.overworld();
        if (serveur.getPlayerList().getPlayers().isEmpty() || !(monde.getBlockEntity(banc) instanceof EngineerBenchBlockEntity be)) {
            return;
        }
        if (accelerer) {
            reglerDuree(be.condition() > SEUIL_RALENTI ? VIE_RAPIDE : VIE_FUMEE);
        }
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        StringBuilder butin = new StringBuilder();
        for (ItemEntity e : monde.getEntitiesOfClass(ItemEntity.class, new AABB(debris).inflate(3))) {
            butin.append(butin.length() == 0 ? "" : ", ").append(e.getItem().getCount()).append(" x ")
                    .append(BenchClientState.nameOf(e.getItem()));
        }
        boolean bainEnMarche = monde.getBlockEntity(bain) instanceof GalvanicBathBlockEntity b
                && b.state() == dev.oneforallsurvival.machine.MachineState.RUNNING;
        instantane = new Instantane(be.condition(), be.fault() == null ? null : be.fault().name(), be.expectedPart(),
                be.isProtected(), monde.getBlockState(banc).getValue(ElectricMachineBlock.STATE).name(),
                be.energy().getEnergyStored(), be.energy().getMaxEnergyStored(), be.flameTicksLeft(),
                WorkshopSavedData.get(serveur).of(joueur.getUUID()).queue().size(),
                RoleSavedData.get(serveur).book().roleOf(joueur.getUUID()).label(),
                !monde.getBlockState(debris).isAir(), butin.toString(), bainEnMarche);
    }

    private static void avancer(Minecraft mc) throws IOException {
        long maintenant = System.nanoTime();
        switch (phase) {
            case TITRE -> {
                if (mc.getOverlay() == null && mc.level == null && mc.screen != null) {
                    reglerLesOptions(mc);
                    LOG.info("[film] le jeu est prêt, création du monde");
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
                if (preparationFaite && !BenchClientState.catalog().isEmpty()) {
                    construireScenario(mc);
                    capture = new FilmCapture(mc.gameDirectory.toPath().resolve("frames-reparation"),
                            "One For All Survival | lot 2 | réparation");
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
        // La fumée et les flammes sont le sujet du film : toutes les particules.
        o.particles().set(net.minecraft.client.ParticleStatus.ALL);
        o.hideGui = false;
        mc.getToasts().clear();
        mc.mouseHandler.releaseMouse();
    }

    private static void creerLeMonde(Minecraft mc) {
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // Réglages en mémoire : le fichier de configuration n'est pas touché
    // ================================================================================================

    private static void regler(String champ, Object valeur) {
        try {
            Field f = MachineConfig.class.getDeclaredField(champ);
            f.setAccessible(true);
            Field cache = ForgeConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
            cache.setAccessible(true);
            cache.set(f.get(null), valeur);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void reglerDuree(double heures) {
        regler("LIFETIME_HOURS", heures);
    }

    // ================================================================================================
    // Préparation côté serveur
    // ================================================================================================

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos ici = joueur.blockPosition();
        int bx = ici.getX() + 4;
        int bz = ici.getZ();
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        bain = new BlockPos(bx, y, bz + 5);
        debris = new BlockPos(bx, y, bz - 5);
        monde.setBlock(bain, RepairRegistry.GALVANIC_BATH.get().defaultBlockState(), 3);
        monde.setBlock(debris, SalvageRegistry.DEBRIS_WIRING.get().defaultBlockState(), 3);
        monde.setDayTime(6000);
        Block source = BuiltInRegistries.BLOCK.get(new ResourceLocation("createaddition:creative_energy"));
        if (source == Blocks.AIR) {
            throw new IllegalStateException("createaddition:creative_energy absent : lancer avec -PwithPack");
        }
        // Une vraie source Create Crafts & Additions contre chaque machine ; le bain derrière, l'établi sur le côté.
        monde.setBlock(bain.east(), source.defaultBlockState(), 3);
        BlockPos pose = new BlockPos(bx, y, bz);
        monde.setBlock(pose.north(), source.defaultBlockState(), 3);
        monde.setBlock(pose, BenchRegistry.ENGINEER_BENCH.get().defaultBlockState()
                .setValue(EngineerBenchBlock.FACING, Direction.WEST), 3);

        List<BenchRecipe> toutes = new ArrayList<>(BenchCatalog.current().recipes());
        toutes.sort(Comparator.comparing(r -> r.id().toString()));
        recette = toutes.stream().filter(FilmReparation::simple).findFirst().orElseThrow();
        LOG.info("[film] recette de la file : {} ({} s)", recette.id(), recette.seconds());
        joueur.getInventory().setItem(FENTE_TESTEUR, new ItemStack(RepairRegistry.TESTER.get()));
        ItemStack poste = new ItemStack(RepairRegistry.WELDER.get());
        // Juste de quoi payer une réparation : la recharge vient après.
        WelderEnergy.store(poste, MachineConfig.repairEnergy());
        joueur.getInventory().setItem(FENTE_POSTE, poste);
        joueur.getInventory().setItem(FENTE_PIOCHE, new ItemStack(Items.STONE_PICKAXE));
        for (Ingredient i : recette.ingredients()) {
            joueur.getInventory().placeItemBackInInventory(i.getItems()[0].copyWithCount(COPIES));
        }
        // Les flammes sont tirées au sort en jeu (50 %) : le film les force, et le dit.
        regler("FLAME_CHANCE", 1.0);
        banc = pose;
        preparationFaite = true;
    }

    /** Une recette à deux ingrédients non ambigus au plus, assez longue pour occuper la machine. */
    private static boolean simple(BenchRecipe r) {
        return r.ingredients().size() <= 2 && r.seconds() >= 8 && r.seconds() <= 16
                && r.ingredients().stream().noneMatch(i -> i.getItems().length != 1)
                && !BuiltInRegistries.ITEM.getKey(r.result().getItem()).getNamespace().equals("oneforallsurvival");
    }

    // ================================================================================================
    // Outils du scénario
    // ================================================================================================

    /** Place le joueur debout, à {@code recul} blocs à l'ouest de la cible, le regard sur elle. */
    private static void regarder(Minecraft mc, BlockPos cible, double recul, double lateral) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        Vec3 centre = Vec3.atCenterOf(cible);
        Vec3 oeil = new Vec3(centre.x - recul, cible.getY() + 1.62, centre.z + lateral);
        double dx = centre.x - oeil.x;
        double dy = centre.y - oeil.y;
        double dz = centre.z - oeil.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0)
                .teleportTo(serveur.overworld(), oeil.x, oeil.y - 1.62, oeil.z, yaw, pitch));
    }

    private static void clicDroit(Minecraft mc, BlockPos pos) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
    }

    private static void etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs,
            Supplier<String> valeurs) {
        ETAPES.add(new Etape(nom, legende, action, pret, reposMs, valeurs));
    }

    private static String machine() {
        Instantane i = instantane;
        return "état " + String.format(Locale.FRANCE, "%.1f", i.etat()) + " % | bloc " + i.etatBloc() + " | panne "
                + (i.panne() == null ? "aucune" : i.panne()) + " | file " + i.file() + " | " + i.energie() + "/" + i.capacite() + " FE";
    }

    private static String poste(Minecraft mc) {
        return "poste " + WelderEnergy.stored(mc.player.getInventory().getItem(FENTE_POSTE)) + "/" + MachineConfig.welderCapacity() + " FE";
    }

    private static Item partieAttendue() {
        return RepairRegistry.partItem(instantane.attendue(), false);
    }

    /** La pièce brute qui n'est pas celle qu'il faut. */
    private static Item mauvaisePiece() {
        FaultType[] tous = FaultType.values();
        return RepairRegistry.partItem(tous[(instantane.attendue().ordinal() + 1) % tous.length], false);
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static String nom(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    private static void mainSecondaire(Minecraft mc, Item item) {
        mc.player.connection.sendCommand("item replace entity " + mc.player.getGameProfile().getName() + " weapon.offhand with " + id(item));
    }

    private static boolean tient(Minecraft mc, Item item) {
        return mc.player.getOffhandItem().is(item);
    }

    private static GalvanicBathMenu menuBain(Minecraft mc) {
        return mc.screen instanceof GalvanicBathScreen s ? s.getMenu() : null;
    }

    /** Un clic majuscule sur la première case de ce menu qui contient cet objet. */
    private static void clicMajuscule(Minecraft mc, Item item, int depuis) {
        GalvanicBathMenu menu = menuBain(mc);
        for (int i = depuis; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).getItem().is(item)) {
                mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
                return;
            }
        }
        throw new IllegalStateException("objet absent du menu du bain : " + item);
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    private static void construireScenario(Minecraft mc) {
        String nomJoueur = mc.player.getGameProfile().getName();

        etape("cadrage", "", () -> {
            regarder(mc, banc, 2.6, 0.0);
            mc.player.getInventory().selected = FENTE_TESTEUR;
        }, () -> true, 2000, () -> "");
        etape("role", "Commande réelle : /role set Dev ingenieur", () -> {
            mc.player.connection.sendCommand("role set " + nomJoueur + " ingenieur");
        }, () -> instantane.role().equals("Ingénieur"), 2500, () -> "rôle côté serveur : " + instantane.role() + " | " + machine());

        etape("neuve", "Machine neuve sous le testeur : rapport affiché, écran non ouvert", () -> clicDroit(mc, banc),
                () -> mc.screen == null && dernierMessage.contains("corrosion"), 4500,
                () -> machine() + " | écran ouvert : " + (mc.screen == null ? "non" : "oui"));

        etape("commande", "Main vide, l'écran s'ouvre : on commande 40 crafts", () -> {
            mc.player.getInventory().selected = FENTE_VIDE;
            clicDroit(mc, banc);
        }, () -> mc.screen instanceof EngineerBenchScreen, 1500, () -> "catalogue : " + BenchClientState.catalog().size() + " recettes");
        etape("commande2", "Main vide, l'écran s'ouvre : on commande 40 crafts", () -> {
            EngineerBenchScreen s = (EngineerBenchScreen) mc.screen;
            s.selectById(recette.id());
            s.setQuantity(COPIES);
            s.order();
        }, () -> BenchClientState.queue().size() >= COPIES, 1800, () -> "file : " + BenchClientState.queue().size() + " | " + machine());
        etape("marche", "En marche sur une source Create Crafts & Additions, encore à 100 %",
                () -> {
                    // Comme la touche Échap : le serveur doit savoir que le menu est fermé, sinon il ne synchronise plus la main secondaire.
                    mc.player.closeContainer();
                    mc.player.getInventory().selected = FENTE_TESTEUR;
                }, () -> mc.screen == null && instantane.etatBloc().equals("RUNNING"), 3000, FilmReparation::machine);

        etape("usure", "Usure accélérée (réglage de dev) : en jeu 12 h, ici quelques secondes", () -> {
            regarder(mc, banc, 2.0, 0.0);
            accelerer = true;
        }, () -> instantane.etat() < 20, 0, FilmReparation::machine);
        etape("fumee", "Sous 20 % : la machine fume et crache des étincelles, en marche", () -> {
        }, () -> instantane.panne() != null, 0, FilmReparation::machine);
        etape("panne", "État 0 : panne et flammes (forcées ici ; une chance sur deux en jeu)", () -> {
            accelerer = false;
            reglerDuree(VIE_NORMALE);
        }, () -> instantane.flammes() > 0, 5000, () -> machine() + " | flammes : " + instantane.flammes() + " ticks");

        etape("testeur", "Le testeur nomme la pièce à changer", () -> {
            mc.player.getInventory().selected = FENTE_TESTEUR;
            dernierMessage = "";
            clicDroit(mc, banc);
        }, () -> dernierMessage.contains("corrosion"), 4500,
                () -> "pièce attendue côté serveur : " + nom(partieAttendue()) + " | " + machine());

        final Item[] mauvaise = new Item[1];
        etape("mauvaise1", "Poste en main, mauvaise pièce en main secondaire (commande /item)", () -> {
            mauvaise[0] = mauvaisePiece();
            mc.player.getInventory().selected = FENTE_POSTE;
            mainSecondaire(mc, mauvaise[0]);
        }, () -> tient(mc, mauvaise[0]), 1500, () -> "main secondaire : " + nom(mauvaise[0]) + " | " + poste(mc));
        etape("mauvaise2", "Mauvaise pièce : refusée, rien n'est consommé", () -> {
            dernierMessage = "";
            clicDroit(mc, banc);
        }, () -> !dernierMessage.isEmpty(), 3500, () -> "message : " + dernierMessage + " | main secondaire : "
                + mc.player.getOffhandItem().getCount() + " x " + nom(mauvaise[0]) + " | " + poste(mc));

        final Item[] bonne = new Item[1];
        etape("bonne1", "La bonne pièce en main secondaire", () -> {
            bonne[0] = partieAttendue();
            mainSecondaire(mc, bonne[0]);
        }, () -> tient(mc, bonne[0]), 1500, () -> "main secondaire : " + nom(bonne[0]) + " | " + poste(mc) + " | " + machine());
        etape("bonne2", "Bonne pièce : machine réparée, pièce et 400 FE consommés", () -> {
            dernierMessage = "";
            fileAvantReprise = instantane.file();
            clicDroit(mc, banc);
        }, () -> instantane.panne() == null, 3000, () -> "message : " + dernierMessage + " | main secondaire : "
                + mc.player.getOffhandItem().getCount() + " pièce | " + poste(mc) + " | état " + String.format(Locale.FRANCE, "%.1f", instantane.etat()) + " %");
        etape("repartie", "La machine repart : en marche, la file avance", () -> {
        }, () -> instantane.etatBloc().equals("RUNNING") && instantane.file() < fileAvantReprise, 2500,
                () -> "file " + fileAvantReprise + " -> " + instantane.file() + " | " + machine());

        etape("recharge1", "Recharge du poste : accroupi, un clic sur la machine", () -> {
            batterieAvant = WelderEnergy.stored(mc.player.getInventory().getItem(FENTE_POSTE));
            mc.options.keyShift.setDown(true);
        }, () -> mc.player.isShiftKeyDown(), 1000, () -> "accroupi : " + (mc.player.isShiftKeyDown() ? "oui" : "non") + " | " + poste(mc));
        etape("recharge2", "Recharge : le courant passe de la machine à la batterie du poste", () -> {
            dernierMessage = "";
            clicDroit(mc, banc);
        }, () -> WelderEnergy.stored(mc.player.getInventory().getItem(FENTE_POSTE)) > batterieAvant, 3500,
                () -> "poste " + batterieAvant + " -> " + WelderEnergy.stored(mc.player.getInventory().getItem(FENTE_POSTE))
                        + " FE | message : " + dernierMessage);

        etape("debris_vue", "Un débris d'avion : le câblage", () -> {
            mc.options.keyShift.setDown(false);
            mc.player.getInventory().selected = FENTE_VIDE;
            regarder(mc, debris, 2.2, 0.0);
        }, () -> !mc.player.isShiftKeyDown(), 2200, () -> "bloc : " + mc.level.getBlockState(debris).getBlock().getName().getString());
        etape("debris_main", "À mains nues, on tape : aucune progression, le débris ne bouge pas", () -> {
            casseVue = false;
            minage = debris;
            debutMinage = System.nanoTime();
        }, () -> System.nanoTime() - debutMinage > 4_500_000_000L, 0,
                () -> "main vide | étape de casse : " + (mc.gameMode.getDestroyStage() < 0 ? "aucune" : String.valueOf(mc.gameMode.getDestroyStage()))
                        + " | débris en place : " + (instantane.debris() ? "oui" : "non"));
        etape("debris_pioche", "À la pioche : le démontage progresse", () -> {
            mc.gameMode.stopDestroyBlock();
            LOG.info("[film] à mains nues, casse vue : {}, débris en place : {}", casseVue, instantane.debris());
            mc.player.getInventory().selected = FENTE_PIOCHE;
        }, () -> !instantane.debris(), 0,
                () -> "pioche de pierre | étape de casse : " + mc.gameMode.getDestroyStage() + " / 9");
        etape("debris_butin", "Le débris tombe, le butin arrive dans l'inventaire", () -> {
            minage = null;
            mc.gameMode.stopDestroyBlock();
        }, () -> mc.player.getInventory().countItem(SalvageRegistry.AVIATION_WIRE.get()) > 0, 3500,
                () -> "dans l'inventaire : " + mc.player.getInventory().countItem(SalvageRegistry.AVIATION_WIRE.get()) + " x "
                        + nom(SalvageRegistry.AVIATION_WIRE.get()) + " | au sol : " + (instantane.butin().isEmpty() ? "rien" : instantane.butin()));

        etape("prevoir", "Le testeur annonce déjà la prochaine pièce, à presque 100 %", () -> {
            mc.player.getInventory().selected = FENTE_TESTEUR;
            regarder(mc, banc, 2.0, 0.0);
            dernierMessage = "";
        }, () -> true, 800, () -> "état " + String.format(Locale.FRANCE, "%.2f", instantane.etat()) + " % | prochaine pièce : " + nom(partieAttendue()));
        etape("prevoir2", "Le testeur annonce déjà la prochaine pièce, à presque 100 %", () -> {
            dernierMessage = "";
            pieceBrute = partieAttendue();
            clicDroit(mc, banc);
        }, () -> dernierMessage.contains("corrosion"), 3500,
                () -> "état " + String.format(Locale.FRANCE, "%.2f", instantane.etat()) + " % | prochaine pièce : " + nom(pieceBrute));

        Item zinc = BuiltInRegistries.ITEM.get(new ResourceLocation("create:zinc_ingot"));
        etape("bain_ouvre", "Le bain galvanique sur sa source : pièce brute et lingot de zinc", () -> {
            mc.player.connection.sendCommand("give " + nomJoueur + " " + id(pieceBrute));
            mc.player.connection.sendCommand("give " + nomJoueur + " " + id(zinc));
            mc.player.getInventory().selected = FENTE_VIDE;
            regarder(mc, bain, 2.2, 0.0);
        }, () -> mc.player.getInventory().countItem(pieceBrute) >= 1 && mc.player.getInventory().countItem(zinc) >= 1, 2200,
                () -> "pièce brute : " + nom(pieceBrute) + " | zinc : " + mc.player.getInventory().countItem(zinc));
        etape("bain_ecran", "Clic droit : l'écran du bain s'ouvre", () -> clicDroit(mc, bain), () -> menuBain(mc) != null, 1500,
                () -> "écran du bain ouvert : " + (menuBain(mc) != null ? "oui" : "non"));
        etape("bain_remplit", "La pièce et le zinc passent dans le bain : il se met en marche", () -> {
            clicMajuscule(mc, pieceBrute, GalvanicBathBlockEntity.SLOT_OUTPUT + 1);
            clicMajuscule(mc, zinc, GalvanicBathBlockEntity.SLOT_OUTPUT + 1);
        }, () -> instantane.bainEnMarche() && menuBain(mc).progress() > 0, 1000, FilmReparation::bainValeurs);
        etape("bain_travaille", "Le bain travaille : 200 ticks et 20 FE par tick", () -> {
        }, () -> menuBain(mc) != null && menuBain(mc).slots.get(GalvanicBathBlockEntity.SLOT_OUTPUT).hasItem(), 2000,
                FilmReparation::bainValeurs);
        etape("bain_sortie", "La pièce ressort traitée, on la récupère", () -> {
            Item sortie = menuBain(mc).slots.get(GalvanicBathBlockEntity.SLOT_OUTPUT).getItem().getItem();
            sortieBain = sortie;
            mc.gameMode.handleInventoryMouseClick(menuBain(mc).containerId, GalvanicBathBlockEntity.SLOT_OUTPUT, 0,
                    ClickType.QUICK_MOVE, mc.player);
        }, () -> mc.player.getInventory().countItem(sortieBain) >= 1, 2500,
                () -> "dans l'inventaire : " + mc.player.getInventory().countItem(sortieBain) + " x " + nom(sortieBain));

        etape("proteger1", "La pièce traitée passe en main secondaire (échange des mains)", () -> {
            mc.player.closeContainer();
            regarder(mc, banc, 2.0, 0.0);
            int fente = -1;
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).is(sortieBain)) {
                    fente = i;
                }
            }
            if (fente < 0) {
                throw new IllegalStateException("la pièce traitée n'est pas dans la barre d'action");
            }
            mc.player.getInventory().selected = fente;
            // La touche F du joueur : changer de main. Il faut que le serveur connaisse la fente choisie.
            mc.gameMode.tick();
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        }, () -> tient(mc, sortieBain), 1800, () -> "main secondaire : " + nom(sortieBain) + " | " + poste(mc));
        etape("proteger2", "Le poste soude la pièce traitée : machine protégée de la corrosion", () -> {
            mc.player.getInventory().selected = FENTE_POSTE;
            dernierMessage = "";
            clicDroit(mc, banc);
        }, () -> instantane.protegee(), 2500, () -> "message : " + dernierMessage + " | protégée côté serveur : "
                + (instantane.protegee() ? "oui" : "non") + " | " + poste(mc));
        etape("proteger3", "Le testeur le confirme : protégée", () -> {
            mc.player.getInventory().selected = FENTE_TESTEUR;
            dernierMessage = "";
            clicDroit(mc, banc);
        }, () -> dernierMessage.contains("corrosion"), 4000, () -> "message : " + dernierMessage + " | " + machine());
        etape("fin", "Fin de la démonstration : le métier de réparation, lot 2", () -> {
        }, () -> true, 1500, FilmReparation::machine);
    }

    private static Item sortieBain = Items.AIR;

    private static String bainValeurs() {
        Minecraft mc = Minecraft.getInstance();
        GalvanicBathMenu m = menuBain(mc);
        return m == null ? "écran fermé" : "progression " + m.progress() + " / " + m.total() + " ticks | énergie " + m.energy() + " / "
                + m.capacity() + " FE | sortie : " + (m.slots.get(GalvanicBathBlockEntity.SLOT_OUTPUT).hasItem()
                        ? nom(m.slots.get(GalvanicBathBlockEntity.SLOT_OUTPUT).getItem().getItem()) : "vide");
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
            LOG.info("[film] étape : {} | {}", e.nom(), machine());
            e.action().run();
            actionFaite = true;
            debutAction = maintenant;
            debutRepos = 0;
            return;
        }
        if (!e.pret().getAsBoolean()) {
            if (maintenant - debutAction > 60_000_000_000L) {
                throw new IllegalStateException("étape sans issue : " + e.nom() + " | " + machine() + " | message " + dernierMessage);
            }
            return;
        }
        if (debutRepos == 0) {
            debutRepos = maintenant;
        }
        if ((maintenant - debutRepos) / 1_000_000L < e.reposMs()) {
            return;
        }
        LOG.info("[film] étape finie : {} | {} | message {}", e.nom(), machine(), dernierMessage);
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
