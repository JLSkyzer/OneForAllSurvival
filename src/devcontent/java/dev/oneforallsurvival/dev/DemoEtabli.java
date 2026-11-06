package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.NativeImage;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.EngineerBenchBlockEntity;
import dev.oneforallsurvival.bench.client.BenchClientState;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Capture de contrôle de l'écran de l'établi (développement seulement, jamais empaqueté). Un client qui se
 * pilote seul : il crée un monde, donne le rôle d'ingénieur, pose l'établi, ouvre le vrai écran par le
 * vrai clic droit du bloc, puis joue un scénario avec de vraies commandes (client vers serveur) et écrit
 * une image par état dans {@code etabli-captures}. Activé par {@code -Doneforallsurvival.demo.etabli}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DemoEtabli {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-demo-etabli");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.etabli") != null;
    private static final String MONDE = "demo_etabli";

    private enum Phase { TITRE, CREATION, ATTENTE_MONDE, PREPARATION, SCENARIO, FIN }

    /** Une étape : une action, une condition de prêt, un temps de repos, puis une image éventuelle. */
    private record Etape(String nom, Runnable action, BooleanSupplier pret, long reposMs, String image) {}

    private static Phase phase = Phase.TITRE;
    private static boolean dansUneEtape;
    private static long debutPhaseNs;
    private static final List<Etape> ETAPES = new ArrayList<>();
    private static int indexEtape = -1;
    private static boolean actionFaite;
    private static long debutRepos;
    private static Path dossier;
    private static BlockPos banc;
    private static volatile boolean preparationFaite;
    private static volatile boolean alimenter;
    private static boolean rouvrir;
    private static int images;
    private static String echec;

    // Les recettes choisies, par l'identifiant (le catalogue est le même côté serveur et côté client).
    private static BenchRecipe manque;
    private static BenchRecipe payable;
    private static BenchRecipe etiquette;
    private static final List<BenchRecipe> POUR_LA_FILE = new ArrayList<>();

    private DemoEtabli() {}

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
        mc.getToasts().clear(); // les notifications de recettes cacheraient le coin de l'écran
        try {
            avancer(mc);
        } catch (RuntimeException | IOException e) {
            LOG.error("[demo] échec en phase {}", phase, e);
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
            // La pause à la perte du focus ne doit jamais geler la démonstration : on la quitte, puis on rouvre l'écran.
            if (mc.level != null && mc.screen instanceof PauseScreen) {
                mc.setScreen(null);
                rouvrir = phase == Phase.SCENARIO;
            }
        }
    }

    /** Côté serveur : l'établi reçoit du courant tant que la démonstration le demande. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || !alimenter || banc == null) {
            return;
        }
        ServerLevel monde = event.getServer().overworld();
        if (monde.getBlockEntity(banc) instanceof EngineerBenchBlockEntity be) {
            be.energy().receiveEnergy(100, false);
        }
    }

    private static void avancer(Minecraft mc) throws IOException {
        long maintenant = System.nanoTime();
        switch (phase) {
            case TITRE -> {
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
                    dossier = mc.gameDirectory.toPath().resolve("etabli-captures");
                    Files.createDirectories(dossier);
                    phase = Phase.SCENARIO;
                    LOG.info("[demo] scénario prêt : {} étapes", ETAPES.size());
                }
            }
            case SCENARIO -> jouer(mc);
            default -> {
            }
        }
    }

    private static void reglerLesOptions(Minecraft mc) {
        var o = mc.options;
        o.onboardAccessibility = false;
        o.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        o.renderDistance().set(6);
        o.simulationDistance().set(6);
        o.guiScale().set(3);
        o.enableVsync().set(false);
        o.framerateLimit().set(60);
        o.particles().set(net.minecraft.client.ParticleStatus.MINIMAL);
        mc.getToasts().clear();
        mc.mouseHandler.releaseMouse();
    }

    private static void creerLeMonde(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
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
    // Préparation côté serveur : rôle, établi, recettes choisies, provisions
    // ================================================================================================

    /** Les objets distincts qu'un ingrédient accepte : sa clé de regroupement. */
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

    private static boolean avecEtiquette(BenchRecipe r) {
        return r.ingredients().stream().anyMatch(i -> i.getItems().length > 1);
    }

    private static boolean sansEtiquette(BenchRecipe r) {
        return r.ingredients().stream().noneMatch(i -> i.getItems().length != 1);
    }

    private static String mod(BenchRecipe r) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.result().getItem()).getNamespace();
    }

    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
        RoleSavedData.get(serveur).book().assign(joueur.getUUID(), RoleBook.ENGINEER_ROLE_ID);

        BlockPos ici = joueur.blockPosition();
        int y = monde.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, ici.getX() + 2,
                ici.getZ());
        banc = new BlockPos(ici.getX() + 2, y, ici.getZ());
        monde.setBlock(banc, BenchRegistry.ENGINEER_BENCH.get().defaultBlockState(), 3);

        List<BenchRecipe> toutes = new ArrayList<>(BenchCatalog.current().recipes());
        toutes.sort(Comparator.comparing(r -> r.id().toString()));
        List<BenchRecipe> create = toutes.stream().filter(r -> mod(r).equals("create")).toList();
        List<BenchRecipe> base = create.size() > 40 ? create : toutes;
        LOG.info("[demo] catalogue serveur : {} recettes, {} de Create", toutes.size(), create.size());

        manque = base.stream().filter(r -> distincts(r) >= 4 && sansEtiquette(r)).findFirst().orElse(base.get(0));
        payable = base.stream().filter(r -> distincts(r) == 2 && sansEtiquette(r) && r.ingredients().size() <= 4
                && r != manque).findFirst().orElse(base.get(1));
        etiquette = base.stream().filter(r -> avecEtiquette(r) && distincts(r) <= 3 && r != manque && r != payable)
                .findFirst().orElse(null);
        Set<String> vus = new HashSet<>();
        vus.add(payable.result().getItem().toString());
        for (BenchRecipe r : toutes) {
            if (POUR_LA_FILE.size() >= 4) {
                break;
            }
            if (distincts(r) <= 2 && r.ingredients().size() <= 3 && sansEtiquette(r) && r != payable && r != manque
                    && vus.add(r.result().getItem().toString()) && mod(r).equals(
                            POUR_LA_FILE.size() % 2 == 0 ? "create" : mod(r))) {
                POUR_LA_FILE.add(r);
            }
        }
        LOG.info("[demo] manque={} payable={} etiquette={} file={}", manque.id(), payable.id(),
                etiquette == null ? "-" : etiquette.id(), POUR_LA_FILE.stream().map(r -> r.id().toString()).toList());

        // Provisions : de quoi payer plusieurs exemplaires de ce qu'on commandera, une partie seulement du reste.
        donner(joueur, payable, 8, 0);
        for (BenchRecipe r : POUR_LA_FILE) {
            donner(joueur, r, 6, 0);
        }
        donner(joueur, manque, 1, 2);
        if (etiquette != null) {
            donner(joueur, etiquette, 1, 1);
        }
        preparationFaite = true;
    }

    /** Donne {@code fois} exemplaires du coût, sans les {@code sauf} derniers ingrédients distincts. */
    private static void donner(ServerPlayer joueur, BenchRecipe r, int fois, int sauf) {
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
        int garde = Math.max(0, lignes.size() - sauf);
        for (int k = 0; k < garde; k++) {
            ItemStack[] choix = lignes.get(k).getItems();
            if (choix.length > 0) {
                joueur.getInventory().add(choix[0].copyWithCount(nombres.get(k) * fois));
            }
        }
    }

    // ================================================================================================
    // Scénario
    // ================================================================================================

    private static EngineerBenchScreen ecran(Minecraft mc) {
        return mc.screen instanceof EngineerBenchScreen s ? s : null;
    }

    private static void ouvrirLeBanc(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        serveur.execute(() -> {
            ServerLevel monde = serveur.overworld();
            ServerPlayer joueur = serveur.getPlayerList().getPlayers().get(0);
            BlockState etat = monde.getBlockState(banc);
            etat.use(monde, joueur, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(banc), Direction.UP, banc, false));
        });
    }

    /** Place la souris (en pixels de fenêtre) : la démonstration survole sans vrai périphérique. */
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

    private static void etape(String nom, Runnable action, BooleanSupplier pret, long reposMs, String image) {
        ETAPES.add(new Etape(nom, action, pret, reposMs, image));
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

    private static void construireScenario(Minecraft mc) {
        // Mots de recherche tirés des noms réels, pour que la recherche donne des résultats quelle que soit la langue.
        etape("ouvrir", () -> ouvrirLeBanc(mc),
                () -> ecran(mc) != null && !BenchClientState.catalog().isEmpty(), 3000, "01_liste.png");
        etape("recherche", () -> {
            // Un mot tiré du nom réel du résultat : la recherche trouve toujours quelque chose, dans toute langue.
            String nom = BenchClientState.normalize(BenchClientState.displayName(clientDe(payable)));
            ecran(mc).setSearchText(nom.length() > 4 ? nom.substring(0, 4) : nom);
        }, () -> true, 900, "02_recherche.png");
        etape("manque", () -> {
            ecran(mc).setSearchText("");
            ecran(mc).selectById(manque.id());
            ecran(mc).setQuantity(1);
        }, () -> true, 900, "03_manque.png");
        etape("payable", () -> {
            ecran(mc).selectById(payable.id());
            ecran(mc).setQuantity(3);
        }, () -> true, 900, "04_payable.png");
        if (etiquette != null) {
            etape("etiquette", () -> {
                ecran(mc).selectById(etiquette.id());
                ecran(mc).setQuantity(2);
            }, () -> true, 900, "05_etiquette.png");
        }
        etape("categorie", () -> ecran(mc).setCategory("create"), () -> true, 900, "06_categorie.png");
        etape("tout", () -> ecran(mc).setCategory(""), () -> true, 300, null);

        // Commandes réelles : l'écran envoie, le serveur prélève et met en file.
        Item temoin = payable.ingredients().get(0).getItems()[0].getItem();
        int[] avant = new int[1];
        etape("commandes", () -> {
            avant[0] = compter(mc, temoin);
            alimenter = true;
            ecran(mc).selectById(payable.id());
            ecran(mc).setQuantity(3);
            ecran(mc).order();
        }, () -> true, 500, null);
        for (BenchRecipe r : POUR_LA_FILE) {
            etape("commande " + r.id(), () -> {
                ecran(mc).selectById(r.id());
                ecran(mc).setQuantity(3);
                ecran(mc).order();
            }, () -> true, 400, null);
        }
        etape("file", () -> LOG.info("[demo] inventaire de {} : {} avant commande, {} après", temoin, avant[0],
                compter(mc, temoin)), () -> BenchClientState.queue().size() >= 8, 1500, "07_file.png");

        // Survol : infobulles de la liste et du bouton grisé.
        etape("survol liste", () -> {
            ecran(mc).setCategory("");
            ecran(mc).selectById(payable.id());
            EngineerBenchScreen s = ecran(mc);
            souris(mc, s.getGuiLeft() + 88 + 50, s.getGuiTop() + 39 + 2 + 17 * 2 + 8);
        }, () -> true, 800, "08_infobulle_liste.png");
        etape("survol crafter grise", () -> {
            EngineerBenchScreen s = ecran(mc);
            s.selectById(manque.id());
            s.setQuantity(1);
            souris(mc, s.getGuiLeft() + s.getXSize() - 5 - 88, s.getGuiTop() + s.getYSize() - 5 - 66 - 3 - 5 - 8);
        }, () -> true, 800, "09_infobulle_crafter.png");

        // Refus du serveur : on force la commande impayable, que le bouton grisé interdirait.
        etape("refus", () -> {
            ecran(mc).selectById(manque.id());
            ecran(mc).order();
            souris(mc, 0, 0);
        }, () -> !BenchClientState.lastRefusal().isEmpty(), 1200, "10_refus.png");

        // Annulation : le dernier de la file est rendu à l'inventaire.
        int[] avantAnnulation = new int[1];
        Item[] temoinAnnulation = new Item[1];
        etape("annuler", () -> {
            var file = BenchClientState.queue();
            var derniere = file.get(file.size() - 1);
            // Le témoin : un ingrédient de la recette annulée, dont le nombre doit remonter d'une unité de coût.
            temoinAnnulation[0] = clientDe(derniere.recipe()).ingredients().get(0).getItems()[0].getItem();
            avantAnnulation[0] = compter(mc, temoinAnnulation[0]);
            LOG.info("[demo] file avant annulation : {} entrées", file.size());
            ecran(mc).cancel(derniere.id());
        }, () -> true, 1500, "11_apres_annulation.png");
        etape("bilan", () -> LOG.info("[demo] file après annulation : {} entrées ; {} : {} avant, {} après",
                BenchClientState.queue().size(), temoinAnnulation[0], avantAnnulation[0],
                compter(mc, temoinAnnulation[0])), () -> true, 100, null);
    }

    private static BenchRecipe clientDe(BenchRecipe serveur) {
        return clientDe(serveur.id());
    }

    private static BenchRecipe clientDe(net.minecraft.resources.ResourceLocation id) {
        for (BenchRecipe r : BenchClientState.catalog()) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        throw new IllegalStateException("recette absente du catalogue client : " + id);
    }

    private static long debutAction;

    private static void jouer(Minecraft mc) throws IOException {
        if (rouvrir && mc.screen == null) {
            rouvrir = false;
            ouvrirLeBanc(mc);
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
            if (!e.nom().equals("ouvrir") && ecran(mc) == null) {
                return; // attend que l'écran soit rouvert
            }
            LOG.info("[demo] étape : {}", e.nom());
            if (!e.nom().startsWith("survol")) {
                souris(mc, 1, 1); // le curseur réel traînerait une infobulle dans l'image
            }
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
        indexEtape++;
        actionFaite = false;
        if (indexEtape >= ETAPES.size()) {
            terminer(mc);
        }
    }

    // ================================================================================================
    // Images
    // ================================================================================================

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
            LOG.info("[demo] image {} ({} x {}, échelle GUI {}), file: {} entrées, recherche: {} résultats", fichier, l, h,
                    mc.getWindow().getGuiScale(), BenchClientState.queue().size(),
                    ecran(mc) == null ? -1 : ecran(mc).shownRecipes().size());
        }
    }

    private static void terminer(Minecraft mc) {
        phase = Phase.FIN;
        LOG.info("[demo] terminé : {} images, {}", images, echec == null ? "sans erreur" : echec);
        mc.stop();
    }
}
