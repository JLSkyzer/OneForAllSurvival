package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.DecayState;
import dev.oneforallsurvival.food.DesalinatorBlock;
import dev.oneforallsurvival.food.DesalinatorBlockEntity;
import dev.oneforallsurvival.food.DesalinatorMenu;
import dev.oneforallsurvival.food.DryerBlock;
import dev.oneforallsurvival.food.DryerBlockEntity;
import dev.oneforallsurvival.food.DryerMenu;
import dev.oneforallsurvival.food.DryingRecipe;
import dev.oneforallsurvival.food.FoodDecay;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import dev.oneforallsurvival.food.FridgeBlock;
import dev.oneforallsurvival.food.FridgeBlockEntity;
import dev.oneforallsurvival.food.FridgeMenu;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
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
 * Film de démonstration du lot 3 (développement seulement, jamais empaqueté). Un client qui se pilote seul dans un monde
 * intégré avec le pack complet : vrais clics droits, clics de menu, commande {@code /coast}, vraie source Create Crafts &
 * Additions. Deux réglages de dev, annoncés à l'écran : le temps du monde est accéléré (2 h de jeu par seconde) pour que les
 * steaks vieillissent sous les yeux, et la durée du séchage est ramenée de 60 à 5 s, en mémoire seulement. Ni le fichier de
 * réglages ni les recettes livrées ne sont touchés. Mêmes images que {@link FilmReparation} : 1280 x 656, un bandeau de
 * légende, 10 images par seconde, dans {@code frames-vivres}. Activé par {@code -Doneforallsurvival.demo.vivres}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmVivres {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.vivres") != null;
    private static final String MONDE = "film_vivres";
    private static final String ZONE = "film_vivres";
    /** Ticks de jeu ajoutés à l'horloge du monde à chaque tick serveur accéléré : 2 h de jeu par seconde réelle. */
    private static final long TEMPS_PAR_TICK = 7200;
    private static final double TICKS_PAR_HEURE = 72000.0;
    private static final int DUREE_SECHAGE = 100;
    private static final int H_STEAK = 0;
    private static final int H_BOUTEILLES = 1;
    private static final int H_BOEUF = 2;
    private static final int H_CABILLAUD = 3;
    private static final int H_SEL = 4;
    private static final int H_VIDE = 8;
    private static final double GARDE_S = 480;

    private enum Phase { TITRE, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    private record Etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {}

    /** Ce que le serveur sait des machines, recopié à chaque tick pour que le client l'affiche sans courir. */
    private record Instantane(double heures, String dehors, String jumeau, double ageJumeau, String armoire, int eau, int potable,
            int sel, int pleines, String desal, int progSechoir, String sechoir, Item sortieSechoir) {
        static final Instantane VIDE = new Instantane(0, "-", "-", 0, "-", 0, 0, 0, 0, "-", 0, "-", Items.AIR);
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
    private static volatile boolean accelerer;
    private static volatile long origineTemps;
    private static BlockPos armoire;
    private static BlockPos desal;
    private static BlockPos sechoir;
    private static volatile Instantane instantane = Instantane.VIDE;
    private static String dernierMessage = "";

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

    private FilmVivres() {}

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
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (ACTIF) {
            dernierMessage = event.getMessage().getString();
            LOG.info("[film] message : {}", dernierMessage);
        }
    }

    private static String nomEtat(ItemStack s, long now) {
        if (s.isEmpty()) {
            return "VIDE";
        }
        if (s.is(FoodRegistry.SPOILED_FOOD.get())) {
            return "DÉCHET";
        }
        if (!FoodStamp.isPerishable(s)) {
            return "NON PÉRISSABLE";
        }
        DecayState d = FoodStamp.state(s, now);
        return d == DecayState.FRESH ? "FRAIS" : d == DecayState.STALE ? "RASSIS" : "AVARIÉ";
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || armoire == null || !preparationFaite) {
            return;
        }
        MinecraftServer serveur = event.getServer();
        ServerLevel monde = serveur.overworld();
        if (serveur.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        if (accelerer) {
            var donnees = serveur.getWorldData().overworldData();
            donnees.setGameTime(donnees.getGameTime() + TEMPS_PAR_TICK);
        }
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        long now = monde.getGameTime();
        ItemStack dehors = joueur.getInventory().getItem(H_STEAK);
        String jumeau = "-";
        double ageJumeau = 0;
        String etatArmoire = "-";
        if (monde.getBlockEntity(armoire) instanceof FridgeBlockEntity f) {
            ItemStack s = f.inventory().getStackInSlot(0);
            jumeau = nomEtat(s, now);
            ageJumeau = FoodStamp.isStamped(s) ? (now - FoodStamp.stamp(s)) / TICKS_PAR_HEURE : 0;
            etatArmoire = monde.getBlockState(armoire).getValue(ElectricMachineBlock.STATE).name();
        }
        int eau = 0;
        int potable = 0;
        int sel = 0;
        int pleines = 0;
        String etatDesal = "-";
        if (monde.getBlockEntity(desal) instanceof DesalinatorBlockEntity d) {
            eau = d.waterTank().getFluidAmount();
            potable = d.potableTank().getFluidAmount();
            sel = d.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_SALT).getCount();
            pleines = d.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_FILLED).getCount();
            etatDesal = monde.getBlockState(desal).getValue(ElectricMachineBlock.STATE).name();
        }
        int progression = 0;
        String etatSechoir = "-";
        Item sortie = Items.AIR;
        if (monde.getBlockEntity(sechoir) instanceof DryerBlockEntity r) {
            progression = r.progress();
            etatSechoir = monde.getBlockState(sechoir).getValue(ElectricMachineBlock.STATE).name();
            sortie = r.inventory().getStackInSlot(DryerBlockEntity.SLOT_OUTPUT).getItem();
        }
        instantane = new Instantane((now - origineTemps) / TICKS_PAR_HEURE, nomEtat(dehors, now), jumeau, ageJumeau, etatArmoire,
                eau, potable, sel, pleines, etatDesal, progression, etatSechoir, sortie);
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
                if (preparationFaite) {
                    construireScenario(mc);
                    capture = new FilmCapture(mc.gameDirectory.toPath().resolve("frames-vivres"),
                            "One For All Survival | lot 3 | vivres");
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
    // Préparation côté serveur
    // ================================================================================================

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos ici = joueur.blockPosition();
        int bx = ici.getX() + 6;
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, ici.getZ());
        armoire = new BlockPos(bx, y, ici.getZ() - 8);
        desal = new BlockPos(bx, y, ici.getZ());
        sechoir = new BlockPos(bx, y, ici.getZ() + 8);
        monde.setDayTime(6000);
        Block source = BuiltInRegistries.BLOCK.get(new ResourceLocation("createaddition:creative_energy"));
        if (source == Blocks.AIR) {
            throw new IllegalStateException("createaddition:creative_energy absent : lancer avec -PwithPack");
        }
        monde.setBlock(armoire, FoodRegistry.FRIDGE.get().defaultBlockState().setValue(FridgeBlock.FACING, Direction.WEST), 3);
        monde.setBlock(desal, FoodRegistry.DESALINATOR.get().defaultBlockState().setValue(DesalinatorBlock.FACING, Direction.WEST), 3);
        monde.setBlock(sechoir, FoodRegistry.DRYER.get().defaultBlockState().setValue(DryerBlock.FACING, Direction.WEST), 3);
        monde.setBlock(armoire.east(), source.defaultBlockState(), 3);
        monde.setBlock(desal.east(), source.defaultBlockState(), 3);
        monde.setBlock(sechoir.east(), source.defaultBlockState(), 3);

        // Deux steaks jumeaux, de la même date : l'un dans l'armoire, l'autre dans l'inventaire du joueur.
        long maintenant = monde.getGameTime();
        origineTemps = maintenant;
        ItemStack steak = new ItemStack(Items.COOKED_BEEF);
        FoodStamp.setStamp(steak, FoodDecay.roundDown(maintenant));
        ((FridgeBlockEntity) monde.getBlockEntity(armoire)).inventory().setStackInSlot(0, steak.copy());
        joueur.getInventory().setItem(H_STEAK, steak.copy());
        joueur.getInventory().setItem(H_BOUTEILLES, new ItemStack(Items.GLASS_BOTTLE, 4));
        joueur.getInventory().setItem(H_SEL, new ItemStack(FoodRegistry.SALT.get(), 1));

        // Le séchage, ramené de 60 s à 5 s, en mémoire seulement : les recettes livrées ne changent pas.
        try {
            Field ticks = DryingRecipe.class.getDeclaredField("ticks");
            ticks.setAccessible(true);
            for (DryingRecipe r : serveur.getRecipeManager().getAllRecipesFor(FoodRegistry.DRYING_TYPE.get())) {
                ticks.setInt(r, DUREE_SECHAGE);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        preparationFaite = true;
    }

    // ================================================================================================
    // Outils du scénario
    // ================================================================================================

    /** Place le joueur debout, à {@code recul} blocs à l'ouest de la cible, le regard sur elle. */
    private static void regarder(Minecraft mc, BlockPos cible, double recul) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        Vec3 centre = Vec3.atCenterOf(cible);
        Vec3 oeil = new Vec3(centre.x - recul, cible.getY() + 1.62, centre.z);
        double dx = centre.x - oeil.x;
        double dy = centre.y - oeil.y;
        double dz = centre.z - oeil.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        serveur.execute(() -> serveur.getPlayerList().getPlayers().get(0)
                .teleportTo(serveur.overworld(), oeil.x, oeil.y - 1.62, oeil.z, yaw, pitch));
    }

    private static void clicDroit(Minecraft mc, BlockPos pos) {
        mc.player.getInventory().selected = H_VIDE;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
    }

    private static void etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs, Supplier<String> valeurs) {
        ETAPES.add(new Etape(nom, legende, action, pret, reposMs, valeurs));
    }

    private static AbstractContainerMenu menu(Minecraft mc) {
        return mc.player.containerMenu;
    }

    private static boolean ouvert(Minecraft mc, Class<? extends AbstractContainerMenu> type) {
        return type.isInstance(menu(mc)) && mc.screen instanceof AbstractContainerScreen<?>;
    }

    /** Place le curseur (réel pour le jeu : ses coordonnées internes) sur la première case qui répond au test. */
    private static void survoler(Minecraft mc, Predicate<Slot> test) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> s)) {
            return;
        }
        for (Slot sl : s.getMenu().slots) {
            if (test.test(sl)) {
                souris(mc, s.getGuiLeft() + sl.x + 8, s.getGuiTop() + sl.y + 8);
                if (capture != null) {
                    // Gros plan sur l'infobulle, qui s'ouvre à droite et sous le curseur.
                    double e = mc.getWindow().getGuiScale();
                    capture.grosPlan((s.getGuiLeft() + sl.x + 8) * e + 150, (s.getGuiTop() + sl.y + 8) * e + 50, 2.5);
                }
                return;
            }
        }
        throw new IllegalStateException("case a survoler introuvable dans " + s.getMenu().getClass().getSimpleName());
    }

    private static void souris(Minecraft mc, double xGui, double yGui) {
        double echelle = mc.getWindow().getGuiScale();
        try {
            for (String nom : new String[] {"xpos", "ypos"}) {
                Field f = net.minecraft.client.MouseHandler.class.getDeclaredField(nom);
                f.setAccessible(true);
                f.setDouble(mc.mouseHandler, nom.equals("xpos") ? xGui * echelle : yGui * echelle);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Predicate<Slot> dansInventaire(Minecraft mc, Predicate<ItemStack> p) {
        return sl -> sl.container == mc.player.getInventory() && p.test(sl.getItem());
    }

    /** La case de la machine (et non de l'inventaire du joueur) de ce rang, quand elle contient quelque chose. */
    private static Predicate<Slot> machine(Minecraft mc, int rang) {
        return sl -> sl.container != mc.player.getInventory() && sl.getContainerSlot() == rang && sl.hasItem();
    }

    private static void deplacer(Minecraft mc, Predicate<Slot> test) {
        for (Slot sl : menu(mc).slots) {
            if (test.test(sl)) {
                mc.gameMode.handleInventoryMouseClick(menu(mc).containerId, sl.index, 0, ClickType.QUICK_MOVE, mc.player);
                return;
            }
        }
        throw new IllegalStateException("case a deplacer introuvable");
    }

    private static String heures() {
        return String.format(Locale.FRANCE, "t = +%.1f h", instantane.heures());
    }

    private static String steaks() {
        Instantane i = instantane;
        return heures() + " | steak dehors : " + i.dehors() + " | steak de l'armoire : " + i.jumeau() + " (âge "
                + String.format(Locale.FRANCE, "%.1f", i.ageJumeau()) + " h) | armoire " + i.armoire();
    }

    private static String desalValeurs() {
        Instantane i = instantane;
        return "dessalinisateur " + i.desal() + " | eau de mer " + i.eau() + " mB | eau potable " + i.potable() + " mB | sel " + i.sel()
                + " | bouteilles pleines " + i.pleines();
    }

    private static String sechoirValeurs() {
        Instantane i = instantane;
        return "séchoir " + i.sechoir() + " | progression " + i.progSechoir() + "/" + DUREE_SECHAGE + " ticks | sortie : "
                + (i.sortieSechoir() == Items.AIR ? "vide" : new ItemStack(i.sortieSechoir()).getHoverName().getString());
    }

    private static MinecraftServer serveur(Minecraft mc) {
        return mc.getSingleplayerServer();
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    private static void construireScenario(Minecraft mc) {
        etape("cadrage", "", () -> regarder(mc, armoire, 2.6), () -> true, 2000, () -> "");

        // --- Scène 1 : deux steaks jumeaux, l'un à l'armoire alimentée
        etape("armoire_vue", "Deux steaks cuits, de la même date : l'un dans l'armoire", () -> {
            mc.player.getInventory().selected = H_VIDE;
        }, () -> true, 2500, FilmVivres::steaks);
        etape("armoire_ouvre", "Clic droit sur l'armoire alimentée : elle s'ouvre", () -> clicDroit(mc, armoire),
                () -> ouvert(mc, FridgeMenu.class), 1500, FilmVivres::steaks);
        etape("survol_dehors", "Le steak de l'inventaire : frais", () -> survoler(mc, dansInventaire(mc, s -> s.is(Items.COOKED_BEEF))),
                () -> true, 2500, FilmVivres::steaks);
        etape("survol_armoire", "Son jumeau, dans l'armoire : frais aussi",
                () -> survoler(mc, sl -> sl.container != mc.player.getInventory() && sl.getContainerSlot() == 0),
                () -> true, 2500, FilmVivres::steaks);
        etape("accelere1", "Temps accéléré (dev) : 1 s = 2 h de jeu. Dehors, il vieillit", () -> {
            survoler(mc, dansInventaire(mc, s -> true).and(sl -> sl.getContainerSlot() == H_STEAK));
            accelerer = true;
        }, () -> instantane.dehors().equals("DÉCHET") && instantane.heures() >= 12.5, 2000, FilmVivres::steaks);
        etape("bilan1", "Dehors : un déchet. Dans l'armoire, le jumeau est encore frais", () -> {
            accelerer = false;
            survoler(mc, sl -> sl.container != mc.player.getInventory() && sl.getContainerSlot() == 0);
        }, () -> true, 4000, FilmVivres::steaks);

        // --- Scène 2 : le courant coupé
        etape("coupure", "Courant coupé : source retirée, réserve vidée", () -> {
            MinecraftServer s = serveur(mc);
            s.execute(() -> {
                ServerLevel monde = s.overworld();
                monde.setBlock(armoire.east(), Blocks.AIR.defaultBlockState(), 3);
                if (monde.getBlockEntity(armoire) instanceof FridgeBlockEntity f) {
                    CompoundTag tag = f.saveWithoutMetadata();
                    tag.putInt("energy", 0);
                    f.load(tag);
                }
            });
        }, () -> instantane.armoire().equals(MachineState.NO_POWER.name()), 3500, FilmVivres::steaks);
        etape("coupure_dehors", "Aucune perte : le steak est toujours dans l'armoire", () -> {
            capture.pleinCadre();
            mc.player.closeContainer();
        }, () -> mc.screen == null, 2500, FilmVivres::steaks);
        etape("coupure_rouvre", "On rouvre l'armoire : sans courant, le froid ne protège plus", () -> clicDroit(mc, armoire),
                () -> ouvert(mc, FridgeMenu.class), 1500, FilmVivres::steaks);
        etape("accelere2", "Temps accéléré (dev) : le jumeau repart à vitesse normale", () -> {
            survoler(mc, sl -> sl.container != mc.player.getInventory() && sl.getContainerSlot() == 0);
            accelerer = true;
        }, () -> instantane.jumeau().equals("DÉCHET"), 1500, FilmVivres::steaks);
        etape("bilan2", "Le jumeau est devenu un déchet à son tour", () -> accelerer = false, () -> true, 3500, FilmVivres::steaks);
        etape("fermeture1", "", () -> { capture.pleinCadre(); mc.player.closeContainer(); }, () -> mc.screen == null, 300, FilmVivres::steaks);

        // --- Scène 3 : le dessalinisateur
        etape("desal_vue", "Le dessalinisateur, alimenté, sans eau : il attend la côte", () -> regarder(mc, desal, 2.6),
                () -> true, 2500, FilmVivres::desalValeurs);
        etape("desal_zone", "Commande réelle : /coast zone add (zone côtière)", () -> {
            BlockPos d = desal;
            mc.player.connection.sendCommand("coast zone add " + ZONE + " " + (d.getX() - 2) + " " + (d.getY() - 2) + " " + (d.getZ() - 2)
                    + " " + (d.getX() + 2) + " " + (d.getY() + 2) + " " + (d.getZ() + 2));
        }, () -> dernierMessage.length() > 0, 1500, FilmVivres::desalValeurs);
        etape("desal_ouvre", "Clic droit : l'eau de mer arrive seule, il se met au travail", () -> clicDroit(mc, desal),
                () -> ouvert(mc, DesalinatorMenu.class), 500, FilmVivres::desalValeurs);
        etape("desal_eau", "Une côte, du courant : eau potable, puis sel au 4e cycle (dev)", () -> {
            // Le sel tombe au quatrième cycle : le compteur est avancé à 3 pour ne pas attendre 15 s (réglage de dev).
            serveur(mc).execute(() -> {
                if (serveur(mc).overworld().getBlockEntity(desal) instanceof DesalinatorBlockEntity d) {
                    d.debugSetCycles(3);
                }
            });
        }, () -> instantane.potable() > 0 && instantane.sel() >= 1, 2500, FilmVivres::desalValeurs);
        etape("desal_bouteilles", "Quatre bouteilles vides dans la machine : elles se remplissent", () -> {
            deplacer(mc, dansInventaire(mc, s -> s.is(Items.GLASS_BOTTLE)));
        }, () -> instantane.pleines() >= 1, 2500, FilmVivres::desalValeurs);
        etape("desal_reprise", "On reprend le sel et l'eau potable en bouteille", () -> {
            deplacer(mc, machine(mc, DesalinatorBlockEntity.SLOT_SALT));
            deplacer(mc, machine(mc, DesalinatorBlockEntity.SLOT_FILLED));
        }, () -> mc.player.getInventory().countItem(FoodRegistry.POTABLE_WATER_BOTTLE.get()) >= 1
                && mc.player.getInventory().countItem(FoodRegistry.SALT.get()) >= 1, 2500, FilmVivres::desalValeurs);
        etape("fermeture2", "", () -> { capture.pleinCadre(); mc.player.closeContainer(); }, () -> mc.screen == null, 300, FilmVivres::desalValeurs);

        // --- Scène 4 : le séchoir et les rations
        etape("sechoir_plein", "", () -> capture.pleinCadre(), () -> true, 0, FilmVivres::sechoirValeurs);
        etape("sechoir_vue", "Le séchoir : un steak cru, du sel, du courant", () -> {
            regarder(mc, sechoir, 2.6);
            // Les aliments crus arrivent maintenant : ils auraient avarié pendant le temps accéléré.
            serveur(mc).execute(() -> {
                ServerPlayer j = serveur(mc).getPlayerList().getPlayers().get(0);
                j.getInventory().setItem(H_BOEUF, new ItemStack(Items.BEEF, 1));
                j.getInventory().setItem(H_CABILLAUD, new ItemStack(Items.COD, 1));
            });
        }, () -> mc.player.getInventory().countItem(Items.BEEF) >= 1 && mc.player.getInventory().countItem(Items.COD) >= 1, 2000, FilmVivres::sechoirValeurs);
        etape("sechoir_ouvre", "Clic droit : l'écran du séchoir", () -> clicDroit(mc, sechoir),
                () -> ouvert(mc, DryerMenu.class), 1000, FilmVivres::sechoirValeurs);
        etape("sechoir_sale", "Boeuf cru + sel : séchage raccourci à 5 s (dev)", () -> {
            deplacer(mc, dansInventaire(mc, s -> s.is(Items.BEEF)));
            deplacer(mc, dansInventaire(mc, s -> s.is(FoodRegistry.SALT.get())));
        }, () -> instantane.sortieSechoir() == FoodRegistry.SALTED_MEAT.get(), 1500, FilmVivres::sechoirValeurs);
        etape("sechoir_reprise1", "La ration salée sort : elle ne périmera jamais", () -> {
            deplacer(mc, machine(mc, DryerBlockEntity.SLOT_OUTPUT));
        }, () -> mc.player.getInventory().countItem(FoodRegistry.SALTED_MEAT.get()) >= 1, 1000, FilmVivres::sechoirValeurs);
        etape("sechoir_retire_sel", "Le sel qui reste est repris : le prochain séchage sera sans sel", () -> {
            for (Slot sl : menu(mc).slots) {
                if (machine(mc, DryerBlockEntity.SLOT_SALT).test(sl)) {
                    deplacer(mc, machine(mc, DryerBlockEntity.SLOT_SALT));
                    return;
                }
            }
        }, () -> menu(mc).getSlot(DryerBlockEntity.SLOT_SALT).getItem().isEmpty(), 500, FilmVivres::sechoirValeurs);
        etape("sechoir_sec", "Cabillaud sans sel : une ration séchée, qui périme lentement", () -> {
            deplacer(mc, dansInventaire(mc, s -> s.is(Items.COD)));
        }, () -> instantane.sortieSechoir() == FoodRegistry.DRIED_FISH.get(), 1500, FilmVivres::sechoirValeurs);
        etape("sechoir_reprise2", "La ration séchée sort", () -> {
            deplacer(mc, machine(mc, DryerBlockEntity.SLOT_OUTPUT));
        }, () -> mc.player.getInventory().countItem(FoodRegistry.DRIED_FISH.get()) >= 1, 1000, FilmVivres::sechoirValeurs);
        etape("rations_sale", "Ration salée : aucune ligne d'état, elle ne périme pas", () -> {
            survoler(mc, dansInventaire(mc, s -> s.is(FoodRegistry.SALTED_MEAT.get())));
        }, () -> true, 3500, FilmVivres::sechoirValeurs);
        etape("rations_seche", "Ration séchée : frais, et 8 fois plus lente à vieillir", () -> {
            survoler(mc, dansInventaire(mc, s -> s.is(FoodRegistry.DRIED_FISH.get())));
        }, () -> true, 3500, FilmVivres::sechoirValeurs);
        etape("fin", "Fin du lot 3 : armoire, dessalinisateur, séchoir", () -> { capture.pleinCadre(); mc.player.closeContainer(); },
                () -> mc.screen == null, 2000, FilmVivres::sechoirValeurs);
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
            LOG.info("[film] étape : {} | {}", e.nom(), steaks());
            e.action().run();
            actionFaite = true;
            debutAction = maintenant;
            debutRepos = 0;
            return;
        }
        if (!e.pret().getAsBoolean()) {
            if (maintenant - debutAction > 60_000_000_000L) {
                throw new IllegalStateException("étape sans issue : " + e.nom() + " | " + steaks() + " | " + desalValeurs() + " | "
                        + sechoirValeurs() + " | message " + dernierMessage);
            }
            return;
        }
        if (debutRepos == 0) {
            debutRepos = maintenant;
        }
        if ((maintenant - debutRepos) / 1_000_000L < e.reposMs()) {
            return;
        }
        LOG.info("[film] étape finie : {} | {} | {} | {}", e.nom(), steaks(), desalValeurs(), sechoirValeurs());
        indexEtape++;
        actionFaite = false;
        if (indexEtape >= ETAPES.size()) {
            terminer(mc);
        }
    }

    private static void terminer(Minecraft mc) {
        phase = Phase.FIN;
        accelerer = false;
        int n = 0;
        if (capture != null) {
            capture.terminer(echec);
            n = capture.images();
        }
        LOG.info("[film] terminé : {} images, {}", n, echec == null ? "sans erreur" : echec);
        mc.stop();
    }
}
