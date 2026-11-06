package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.EngineerBenchBlock;
import dev.oneforallsurvival.bench.EngineerBenchBlockEntity;
import dev.oneforallsurvival.bench.PlayerWorkshop;
import dev.oneforallsurvival.bench.WorkshopEntry;
import dev.oneforallsurvival.bench.WorkshopSavedData;
import dev.oneforallsurvival.bench.client.BenchClientState;
import dev.oneforallsurvival.bench.client.BenchStockScreen;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.role.RoleSavedData;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
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
 * Film de démonstration de l'établi d'ingénieur (développement seulement, jamais empaqueté). Un client qui
 * se pilote seul dans un monde intégré avec le pack complet : vrais clics droits, vraie commande
 * {@code /role}, vraie source Create Crafts and Additions, vrais paquets vers le serveur. Chaque image
 * du jeu (1280 x 656) reçoit sous elle un bandeau de 64 pixels : la légende de l'étape et les valeurs lues
 * en direct. Les images partent dans {@code frames-etabli}, à 10 par seconde ; un encodeur les assemble.
 * Activé par {@code -Doneforallsurvival.demo.film}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmEtabli {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.film") != null;
    private static final String MONDE = "film_etabli";
    private static final int BANDEAU = 64;
    private static final long INTERVALLE_NS = 100_000_000L;

    private enum Phase { TITRE, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    /** Une étape du film : une action, une condition de prêt, un temps de pause, la légende et les valeurs en direct. */
    private record Etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs,
            Supplier<String> valeurs) {}

    /** Ce que le serveur sait de l'établi, recopié à chaque tick pour que le client l'affiche sans courir. */
    private record Instantane(String etat, int energie, int capacite, double vitesseMain, double vitesseAlimente,
            int file, String role, int tableRecettes, boolean aMesureMain, boolean aMesureAlimente) {
        static final Instantane VIDE = new Instantane("-", 0, 0, 0, 0, 0, "-", -1, false, false);
    }

    private static Phase phase = Phase.TITRE;
    private static boolean dansUneEtape;
    private static long debutPhaseNs;
    private static final List<Etape> ETAPES = new ArrayList<>();
    private static int indexEtape = -1;
    private static boolean actionFaite;
    private static long debutAction;
    private static long debutRepos;
    private static boolean rouvrir;

    private static volatile boolean preparationFaite;
    private static BlockPos banc;
    private static BlockPos table;
    private static volatile Instantane instantane = Instantane.VIDE;
    private static volatile int recettesTable = -1;
    private static long tickServeur;
    private static UUID teteVue;
    private static double teteDone;
    private static long teteTick;
    private static double mesureMain;
    private static double mesureAlimente;
    private static boolean aMesureMain;
    private static boolean aMesureAlimente;

    private static BenchRecipe recetteA;
    private static BenchRecipe recetteB;
    private static BenchRecipe recetteC;
    private static BenchRecipe recetteTable;
    private static String dernierMessage = "";

    private static String echec;

    // Valeurs relevées pendant le film, pour les légendes.
    private static int avantCommande;
    private static int avantAnnulation;
    private static int apresAnnulation;
    private static int avantRetrait;
    private static int apresRetrait;

    private FilmEtabli() {}

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
            if (phase == Phase.FILM) {
                capturerSiBesoin(mc);
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
        if (ACTIF && event.phase == TickEvent.Phase.END && phase != Phase.FIN) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.screen instanceof PauseScreen) {
                mc.setScreen(null);
                rouvrir = true;
            }
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (ACTIF) {
            dernierMessage = event.getMessage().getString();
            LOG.info("[film] chat : {}", dernierMessage);
        }
    }

    /** Côté serveur : recopie l'état de l'établi et mesure la vitesse réelle de la tête de file. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || banc == null) {
            return;
        }
        tickServeur++;
        try {
            lireLeServeur(event.getServer());
        } catch (RuntimeException e) {
            LOG.error("[film] lecture du serveur impossible", e);
        }
    }

    private static void lireLeServeur(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        if (serveur.getPlayerList().getPlayers().isEmpty() || !(monde.getBlockEntity(banc) instanceof EngineerBenchBlockEntity be)) {
            return;
        }
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        var etat = monde.getBlockState(banc).getValue(ElectricMachineBlock.STATE);
        PlayerWorkshop atelier = WorkshopSavedData.get(serveur).of(joueur.getUUID());
        List<WorkshopEntry> file = atelier.queue();
        if (!file.isEmpty()) {
            WorkshopEntry tete = file.get(0);
            if (!tete.id().equals(teteVue)) {
                teteVue = tete.id();
                teteDone = tete.doneTicks();
                teteTick = tickServeur;
            } else if (tickServeur - teteTick >= 10) {
                double vitesse = (tete.doneTicks() - teteDone) / (tickServeur - teteTick);
                if (etat == dev.oneforallsurvival.machine.MachineState.RUNNING) {
                    mesureAlimente = vitesse;
                    aMesureAlimente = true;
                } else {
                    mesureMain = vitesse;
                    aMesureMain = true;
                }
                teteDone = tete.doneTicks();
                teteTick = tickServeur;
            }
        } else {
            teteVue = null;
        }
        instantane = new Instantane(etat.name(), be.energy().getEnergyStored(), be.energy().getMaxEnergyStored(),
                mesureMain, mesureAlimente, file.size(),
                RoleSavedData.get(serveur).book().roleOf(joueur.getUUID()).label(), recettesTable, aMesureMain,
                aMesureAlimente);
    }

    private static void avancer(Minecraft mc) throws IOException {
        long maintenant = System.nanoTime();
        switch (phase) {
            case TITRE -> {
                if (mc.getOverlay() == null && mc.level == null && mc.screen != null) {
                    mc.options.pauseOnLostFocus = false;
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
                // Le catalogue est reçu à la connexion : le film ne démarre qu'avec lui.
                if (preparationFaite && !BenchClientState.catalog().isEmpty()) {
                    construireScenario(mc);
                    capture = new FilmCapture(mc.gameDirectory.toPath().resolve("frames-etabli"),
                            "One For All Survival | lot 1 | établi d'ingénieur");
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
        o.onboardAccessibility = false;
        o.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        o.renderDistance().set(8);
        o.simulationDistance().set(6);
        o.guiScale().set(2);
        o.enableVsync().set(false);
        o.framerateLimit().set(60);
        o.particles().set(net.minecraft.client.ParticleStatus.MINIMAL);
        o.hideGui = false;
        mc.getToasts().clear();
        mc.mouseHandler.releaseMouse();
    }

    private static void creerLeMonde(Minecraft mc) {
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // Préparation côté serveur : établi, table, recettes choisies, provisions
    // ================================================================================================

    private static List<Item> cle(Ingredient ingredient) {
        List<Item> items = new ArrayList<>();
        for (ItemStack s : ingredient.getItems()) {
            items.add(s.getItem());
        }
        return items;
    }

    private static int distincts(BenchRecipe r) {
        Set<List<Item>> set = new HashSet<>();
        for (Ingredient i : r.ingredients()) {
            set.add(cle(i));
        }
        return set.size();
    }

    private static boolean simple(BenchRecipe r) {
        return r.ingredients().stream().noneMatch(i -> i.getItems().length != 1) && distincts(r) <= 3
                && r.ingredients().size() <= 6
                && !BuiltInRegistries.ITEM.getKey(r.result().getItem()).getNamespace().equals("oneforallsurvival");
    }

    private static BenchRecipe choisir(List<BenchRecipe> toutes, int min, int max, Set<String> pris) {
        for (BenchRecipe r : toutes) {
            if (simple(r) && r.seconds() >= min && r.seconds() <= max && pris.add(r.result().getItem().toString())) {
                return r;
            }
        }
        for (BenchRecipe r : toutes) {
            if (simple(r) && pris.add(r.result().getItem().toString())) {
                return r;
            }
        }
        throw new IllegalStateException("aucune recette simple au catalogue");
    }

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        BlockPos ici = joueur.blockPosition();
        int bx = ici.getX() + 4;
        int bz = ici.getZ();
        int y = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        banc = new BlockPos(bx, y, bz);
        table = new BlockPos(bx, y, bz + 4);
        monde.setBlock(banc, BenchRegistry.ENGINEER_BENCH.get().defaultBlockState()
                .setValue(EngineerBenchBlock.FACING, Direction.WEST), 3);
        monde.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        monde.setDayTime(6000);

        List<BenchRecipe> toutes = new ArrayList<>(BenchCatalog.current().recipes());
        toutes.sort(Comparator.comparing(r -> r.id().toString()));
        Set<String> pris = new HashSet<>();
        recetteA = choisir(toutes, 8, 14, pris);
        recetteB = choisir(toutes, 6, 14, pris);
        recetteC = choisir(toutes, 5, 14, pris);
        recetteTable = toutes.stream().filter(r -> r.category().equals("crafting") && r.ingredients().size() == 4
                && r.ingredients().stream().noneMatch(i -> i.getItems().length != 1)
                && !pris.contains(r.result().getItem().toString())
                && !BuiltInRegistries.ITEM.getKey(r.result().getItem()).getNamespace().equals("oneforallsurvival"))
                .findFirst().orElseThrow();
        LOG.info("[film] A={} ({} s) B={} ({} s) C={} ({} s) table={}", recetteA.id(), recetteA.seconds(), recetteB.id(),
                recetteB.seconds(), recetteC.id(), recetteC.seconds(), recetteTable.id());
        donner(joueur, recetteA, 3);
        donner(joueur, recetteB, 3);
        donner(joueur, recetteC, 2);
        preparationFaite = true;
    }

    /** Donne {@code fois} exemplaires du coût de la recette. */
    private static void donner(ServerPlayer joueur, BenchRecipe r, int fois) {
        List<List<Item>> vus = new ArrayList<>();
        List<Ingredient> lignes = new ArrayList<>();
        List<Integer> nombres = new ArrayList<>();
        for (Ingredient i : r.ingredients()) {
            int idx = vus.indexOf(cle(i));
            if (idx < 0) {
                vus.add(cle(i));
                lignes.add(i);
                nombres.add(1);
            } else {
                nombres.set(idx, nombres.get(idx) + 1);
            }
        }
        for (int k = 0; k < lignes.size(); k++) {
            ItemStack[] choix = lignes.get(k).getItems();
            if (choix.length > 0) {
                joueur.getInventory().add(choix[0].copyWithCount(nombres.get(k) * fois));
            }
        }
    }

    // ================================================================================================
    // Outils du scénario
    // ================================================================================================

    private static EngineerBenchScreen ecran(Minecraft mc) {
        return mc.screen instanceof EngineerBenchScreen s ? s : null;
    }

    private static BenchStockScreen stock(Minecraft mc) {
        return mc.screen instanceof BenchStockScreen s ? s : null;
    }

    private static int compter(Minecraft mc, Item item) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static BenchRecipe clientDe(BenchRecipe serveur) {
        return clientDe(serveur.id());
    }

    private static BenchRecipe clientDe(ResourceLocation id) {
        for (BenchRecipe r : BenchClientState.catalog()) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        throw new IllegalStateException("recette absente du catalogue client : " + id);
    }

    private static Item premierIngredient(BenchRecipe r) {
        return r.ingredients().get(0).getItems()[0].getItem();
    }

    private static String nom(ItemStack pile) {
        return BenchClientState.nameOf(pile);
    }

    /** Place le joueur : yeux à {@code cible + decalage}, regard sur {@code cible}. */
    private static void poser(Minecraft mc, BlockPos cible, Vec3 decalage) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        Vec3 centre = new Vec3(cible.getX() + 0.5, cible.getY() + 0.5, cible.getZ() + 0.5);
        Vec3 oeil = centre.add(decalage);
        double dx = centre.x - oeil.x;
        double dy = centre.y - oeil.y;
        double dz = centre.z - oeil.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        serveur.execute(() -> {
            ServerLevel monde = serveur.overworld();
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            joueur.teleportTo(monde, oeil.x, oeil.y - 1.62, oeil.z, yaw, pitch);
        });
    }

    private static void clicDroit(Minecraft mc, BlockPos pos) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
    }

    private static void etape(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs,
            Supplier<String> valeurs) {
        ETAPES.add(new Etape(nom, legende, action, pret, reposMs, valeurs));
    }

    private static String vitesse(double v, boolean mesure) {
        return mesure ? String.format(java.util.Locale.FRANCE, "x%.2f", v) : "-";
    }

    private static String machine() {
        Instantane i = instantane;
        return "état " + i.etat() + " | énergie " + i.energie() + " / " + i.capacite() + " FE";
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    private static void construireScenario(Minecraft mc) {
        Vec3 faceBanc = new Vec3(-2.4, 0.5, 0.0);
        etape("monde", "Le bloc dans le monde : l'établi d'ingénieur",
                () -> poser(mc, banc, faceBanc), () -> true, 3500,
                () -> machine() + " | rôle : " + instantane.role() + " | position " + banc.toShortString());
        etape("monde2", "Machine électrique : arrêtée, sans courant, file vide",
                () -> poser(mc, banc, new Vec3(-1.5, 0.9, 1.5)), () -> true, 3000,
                () -> machine() + " | file : " + instantane.file());
        etape("role", "Commande réelle : /role set Dev ingenieur",
                () -> mc.player.connection.sendCommand("role set " + mc.player.getGameProfile().getName() + " ingenieur"),
                () -> instantane.role().equals("Ingénieur"), 2500,
                () -> "rôle côté serveur : " + instantane.role() + " | message : " + dernierMessage);

        // La table de craft : la recette a quitté la table.
        etape("table_approche", "Table de craft : essai avec une recette gérée par l'établi",
                () -> poser(mc, table, faceBanc), () -> true, 2000,
                () -> "recette essayée : " + nom(recetteTable.result()) + " (" + recetteTable.id() + ")");
        etape("table_ouvrir", "Clic droit sur la table de craft",
                () -> clicDroit(mc, table), () -> mc.screen instanceof CraftingScreen, 1500,
                () -> "écran de la table ouvert : " + (mc.screen instanceof CraftingScreen ? "oui" : "non"));
        etape("table_temoin", "Témoin : 2 planches, la table fabrique des bâtons",
                () -> remplirLaTable(mc, true),
                () -> mc.player.containerMenu.getSlot(0).hasItem(), 2800,
                () -> "grille : 2 planches | résultat : " + resultatTable(mc));
        etape("table_geree", "Même table, grille d'une recette gérée : aucun résultat, elle a quitté la table",
                () -> remplirLaTable(mc, false),
                () -> !mc.player.containerMenu.getSlot(0).hasItem() && mc.player.containerMenu.getSlot(1).hasItem(), 4500,
                () -> "grille : " + nom(recetteTable.result()) + " (4 ingrédients) | résultat : "
                        + resultatTable(mc) + " | recettes de table pour cet objet : " + instantane.tableRecettes()
                        + " | recette de l'établi : "
                        + (BenchCatalog.current().byId(recetteTable.id()).isPresent() ? "oui" : "non"));
        etape("table_fermer", "On quitte la table : la grille revient dans l'inventaire",
                () -> mc.setScreen(null), () -> mc.screen == null, 800,
                () -> "écran fermé");

        // L'établi.
        etape("banc_ouvrir", "Clic droit sur l'établi : l'écran s'ouvre",
                () -> {
                    poser(mc, banc, faceBanc);
                },
                () -> true, 1200, () -> "rôle : " + instantane.role());
        etape("banc_ecran", "L'écran de l'établi, catalogue reçu du serveur",
                () -> clicDroit(mc, banc),
                () -> ecran(mc) != null && !BenchClientState.catalog().isEmpty(), 2500,
                () -> "catalogue : " + BenchClientState.catalog().size() + " recettes (génération "
                        + BenchClientState.generation() + ") | vitesses serveur : main x" + BenchClientState.manualSpeed()
                        + ", alimenté x" + BenchClientState.poweredSpeed());
        String mot = "";
        final String[] motRecherche = {""};
        for (int k = 1; k <= 4; k++) {
            final int n = k;
            etape("recherche" + k, "Recherche d'un objet par son nom",
                    () -> {
                        if (n == 1) {
                            String complet = BenchClientState.normalize(BenchClientState.displayName(clientDe(recetteA)));
                            motRecherche[0] = complet.length() > 4 ? complet.substring(0, 4) : complet;
                        }
                        ecran(mc).setSearchText(motRecherche[0].substring(0, Math.min(n, motRecherche[0].length())));
                    },
                    () -> true, n == 4 ? 2200 : 450,
                    () -> "recherche « " + motRecherche[0].substring(0, Math.min(n, motRecherche[0].length())) + " » : "
                            + ecran(mc).shownRecipes().size() + " résultat(s) sur " + BenchClientState.catalog().size());
        }
        etape("recette", "Une recette choisie : coût et temps",
                () -> {
                    ecran(mc).setSearchText("");
                    ecran(mc).selectById(recetteA.id());
                    ecran(mc).setQuantity(2);
                },
                () -> true, 2600,
                () -> nom(clientDe(recetteA).result()) + " | temps à la main : " + recetteA.seconds()
                        + " s | quantité 2 | inventaire : " + compter(mc, premierIngredient(recetteA)) + " "
                        + nom(new ItemStack(premierIngredient(recetteA))));

        // Plusieurs commandes, puis l'annulation de la dernière.
        etape("commande_a", "Commande de plusieurs crafts (1/3)",
                () -> {
                    avantCommande = compter(mc, premierIngredient(recetteA));
                    ecran(mc).selectById(recetteA.id());
                    ecran(mc).setQuantity(2);
                    ecran(mc).order();
                },
                () -> !BenchClientState.queue().isEmpty(), 900,
                () -> "file : " + BenchClientState.queue().size() + " | " + nom(new ItemStack(premierIngredient(recetteA)))
                        + " : " + avantCommande + " -> " + compter(mc, premierIngredient(recetteA)));
        etape("commande_b", "Commande de plusieurs crafts (2/3)",
                () -> {
                    ecran(mc).selectById(recetteB.id());
                    ecran(mc).setQuantity(2);
                    ecran(mc).order();
                },
                () -> BenchClientState.queue().size() >= 4, 900,
                () -> "file : " + BenchClientState.queue().size() + " fabrication(s)");
        etape("commande_c", "Commande de plusieurs crafts (3/3)",
                () -> {
                    ecran(mc).selectById(recetteC.id());
                    ecran(mc).setQuantity(1);
                    ecran(mc).order();
                },
                () -> BenchClientState.queue().size() >= 5, 1800,
                () -> "file : " + BenchClientState.queue().size() + " fabrication(s) | " + machine());
        etape("annuler", "Annulation de la dernière : les objets reviennent",
                () -> {
                    var file = BenchClientState.queue();
                    var derniere = file.get(file.size() - 1);
                    Item temoin = premierIngredient(clientDe(derniere.recipe()));
                    avantAnnulation = compter(mc, temoin);
                    LOG.info("[film] annulation de {} : {} avant", derniere.recipe(), avantAnnulation);
                    ecran(mc).cancel(derniere.id());
                },
                () -> BenchClientState.queue().size() == 4, 600, () -> "file : " + BenchClientState.queue().size());
        etape("annuler_bilan", "Annulation : la file passe de 5 à 4, l'inventaire est rendu",
                () -> {
                    apresAnnulation = compter(mc, premierIngredient(recetteC));
                    LOG.info("[film] après annulation : {} ({} avant)", apresAnnulation, avantAnnulation);
                },
                () -> true, 2800,
                () -> nom(new ItemStack(premierIngredient(recetteC))) + " : " + avantAnnulation + " -> "
                        + compter(mc, premierIngredient(recetteC)) + " | file : " + BenchClientState.queue().size());

        // Vitesse à la main, puis alimentée par une vraie source.
        etape("main", "À la main : vitesse réelle mesurée sur la tête de file",
                () -> {
                },
                () -> instantane.aMesureMain(), 3500,
                () -> "mesuré " + vitesse(instantane.vitesseMain(), instantane.aMesureMain()) + " (réglage serveur x"
                        + BenchClientState.manualSpeed() + ") | " + machine());
        etape("courant", "On branche une source Create Crafts & Additions contre l'établi",
                () -> brancher(mc),
                () -> instantane.etat().equals("RUNNING"), 1500,
                () -> machine() + " | source : createaddition:creative_energy");
        etape("alimente", "Alimenté : l'établi travaille plus vite",
                () -> {
                },
                () -> instantane.aMesureAlimente(), 3500,
                () -> "mesuré " + vitesse(instantane.vitesseAlimente(), instantane.aMesureAlimente()) + " (réglage x"
                        + BenchClientState.poweredSpeed() + ") contre " + vitesse(instantane.vitesseMain(), true)
                        + " à la main | " + machine());
        etape("attente", "Les crafts terminés partent au stock du joueur",
                () -> {
                },
                () -> BenchClientState.queue().isEmpty() && BenchClientState.stockItems() >= 1, 1500,
                () -> "file : " + BenchClientState.queue().size() + " | objets en stock : "
                        + BenchClientState.stockItems() + " | " + machine());

        // Le stock.
        etape("stock_ouvrir", "Ouverture du stock",
                () -> {
                    int[] p = ecran(mc).hoverPoint("stock");
                    mc.screen.mouseClicked(p[0], p[1], 0);
                },
                () -> stock(mc) != null && stock(mc).getMenu().totalStacks() >= 1, 2500,
                () -> "stock : " + (stock(mc) == null ? 0 : stock(mc).getMenu().totalStacks()) + " pile(s), "
                        + BenchClientState.stockItems() + " objet(s)");
        etape("stock_prise", "Récupération : un clic sur la première pile",
                () -> {
                    var s = stock(mc);
                    ItemStack pile = s.getMenu().slots.get(0).getItem();
                    avantRetrait = compter(mc, pile.getItem());
                    LOG.info("[film] retrait de {} : {} avant", pile, avantRetrait);
                    mc.gameMode.handleInventoryMouseClick(s.getMenu().containerId, 0, 0, ClickType.QUICK_MOVE, mc.player);
                },
                () -> true, 2800,
                () -> "stock : " + (stock(mc) == null ? 0 : stock(mc).getMenu().totalStacks()) + " pile(s) | inventaire : "
                        + avantRetrait + " -> " + compter(mc, itemRetire(mc)));
        etape("stock_retour", "Retour à l'établi, puis fermeture",
                () -> {
                    apresRetrait = compter(mc, itemRetire(mc));
                    LOG.info("[film] après le retrait : {} ({} avant)", apresRetrait, avantRetrait);
                    int[] p = stock(mc).backPoint();
                    mc.screen.mouseClicked(p[0], p[1], 0);
                },
                () -> ecran(mc) != null, 1500, () -> "inventaire : " + avantRetrait + " -> " + apresRetrait);
        etape("fermer", "Fermeture de l'écran",
                () -> mc.setScreen(null), () -> mc.screen == null, 600, () -> "");

        // Le rôle Citoyen est refusé.
        etape("citoyen", "Commande réelle : /role set Dev citoyen",
                () -> mc.player.connection.sendCommand("role set " + mc.player.getGameProfile().getName() + " citoyen"),
                () -> instantane.role().equals("Citoyen"), 2000,
                () -> "rôle côté serveur : " + instantane.role() + " | message : " + dernierMessage);
        // Un opérateur garde la main pour dépanner (RoleGate) : on lui retire cette main par la vraie commande.
        etape("deop", "On retire les triches du monde : plus d'opérateur, donc plus de main sur l'établi",
                () -> {
                    MinecraftServer serveur = mc.getSingleplayerServer();
                    serveur.execute(() -> {
                        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
                        retirerLesTriches(serveur);
                        serveur.getPlayerList().sendPlayerPermissionLevel(joueur);
                    });
                },
                () -> !mc.player.hasPermissions(2), 1500,
                () -> "rôle : " + instantane.role() + " | opérateur : " + (mc.player.hasPermissions(2) ? "oui" : "non"));
        etape("refus", "Clic droit sur l'établi en Citoyen : refusé",
                () -> {
                    dernierMessage = "";
                    clicDroit(mc, banc);
                },
                () -> {
                    long n = (System.nanoTime() - debutAction) / 1_500_000_000L;
                    if (dernierMessage.isEmpty() && n > 0 && n != reclics) {
                        reclics = n;
                        clicDroit(mc, banc);
                    }
                    return !dernierMessage.isEmpty();
                }, 3500,
                () -> "écran ouvert : " + (mc.screen == null ? "non" : "oui") + " | rôle : " + instantane.role()
                        + " | message : " + dernierMessage);
        etape("fin", "Fin de la démonstration : établi d'ingénieur, lot 1",
                () -> {
                }, () -> true, 1800, () -> "rôle : " + instantane.role() + " | " + machine());
    }

    /** Dev : le monde n'autorise plus les commandes ; le propriétaire du solo cesse d'être opérateur. */
    private static void retirerLesTriches(MinecraftServer serveur) {
        try {
            var donnees = (net.minecraft.world.level.storage.PrimaryLevelData) serveur.getWorldData();
            Field champ = net.minecraft.world.level.storage.PrimaryLevelData.class.getDeclaredField("settings");
            champ.setAccessible(true);
            LevelSettings a = (LevelSettings) champ.get(donnees);
            champ.set(donnees, new LevelSettings(a.levelName(), a.gameType(), a.hardcore(), a.difficulty(), false,
                    a.gameRules(), a.getDataConfiguration()));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Item itemRetire(Minecraft mc) {
        return avantRetraitItem != null ? avantRetraitItem : Items.AIR;
    }

    private static Item avantRetraitItem;

    private static String resultatTable(Minecraft mc) {
        ItemStack r = mc.player.containerMenu.getSlot(0).getItem();
        return r.isEmpty() ? "(aucun)" : r.getCount() + " x " + nom(r);
    }

    /** Pose la grille sur la table ouverte, côté serveur : deux planches (témoin) ou la recette gérée. */
    private static void remplirLaTable(Minecraft mc, boolean temoin) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        serveur.execute(() -> {
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            if (!(joueur.containerMenu instanceof CraftingMenu menu)) {
                throw new IllegalStateException("la table n'est pas ouverte : " + joueur.containerMenu);
            }
            for (int i = 1; i <= 9; i++) {
                menu.getSlot(i).set(ItemStack.EMPTY);
            }
            if (temoin) {
                menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
                menu.getSlot(4).set(new ItemStack(Items.OAK_PLANKS));
            } else {
                int[] cases = recetteTable.ingredients().size() == 4 ? new int[] {1, 2, 4, 5}
                        : new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
                for (int k = 0; k < recetteTable.ingredients().size(); k++) {
                    menu.getSlot(cases[k]).set(recetteTable.ingredients().get(k).getItems()[0].copyWithCount(1));
                }
                long nb = 0;
                try {
                    var acces = serveur.registryAccess();
                    nb = serveur.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                            .filter(r -> ItemStack.isSameItem(r.getResultItem(acces), recetteTable.result())).count();
                } catch (RuntimeException e) {
                    LOG.warn("[film] décompte des recettes de table impossible", e);
                }
                recettesTable = (int) nb;
                LOG.info("[film] table : {} recette(s) de table fabriquent {} ; résultat de la grille : {}", nb,
                        recetteTable.result(), menu.getSlot(0).getItem());
            }
            menu.broadcastChanges();
        });
    }

    /** Une source Create Crafts & Additions posée contre l'établi : un vrai bloc, qui pousse son courant. */
    private static void brancher(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        serveur.execute(() -> {
            Block source = BuiltInRegistries.BLOCK.get(new ResourceLocation("createaddition:creative_energy"));
            if (source == Blocks.AIR) {
                throw new IllegalStateException("createaddition:creative_energy absent");
            }
            serveur.overworld().setBlock(banc.north(), source.defaultBlockState(), 3);
            LOG.info("[film] source posée en {}", banc.north());
        });
    }

    // ================================================================================================
    // Déroulement
    // ================================================================================================

    private static void jouer(Minecraft mc) {
        if (rouvrir && mc.screen == null) {
            rouvrir = false;
            return;
        }
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
            LOG.info("[film] étape : {}", e.nom());
            if (e.nom().equals("stock_prise")) {
                ItemStack pile = stock(mc).getMenu().slots.get(0).getItem();
                avantRetraitItem = pile.getItem();
            }
            e.action().run();
            actionFaite = true;
            debutAction = maintenant;
            debutRepos = 0;
            return;
        }
        if (!e.pret().getAsBoolean()) {
            if (maintenant - debutAction > 40_000_000_000L) {
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
        indexEtape++;
        actionFaite = false;
        if (indexEtape >= ETAPES.size()) {
            terminer(mc);
        }
    }

    private static FilmCapture capture;
    private static long reclics;

    private static void capturerSiBesoin(Minecraft mc) {
        if (indexEtape < 0 || indexEtape >= ETAPES.size()) {
            return;
        }
        Etape e = ETAPES.get(indexEtape);
        String valeurs;
        try {
            valeurs = e.valeurs().get();
        } catch (RuntimeException ex) {
            valeurs = "(valeur indisponible : " + ex.getClass().getSimpleName() + ")";
        }
        capture.capturer(mc, e.legende(), valeurs);
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
