package dev.oneforallsurvival.dev;

import static dev.oneforallsurvival.dev.FilmMaya.*;
import static dev.oneforallsurvival.dev.FilmTournage.*;

import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.client.BenchClientState;
import dev.oneforallsurvival.bench.client.BenchStockScreen;
import dev.oneforallsurvival.bench.client.EngineerBenchScreen;
import dev.oneforallsurvival.client.title.LostIslandTitleScreen;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinActions;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.SafeMenu;
import dev.oneforallsurvival.economy.SafeStore;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.client.InvoiceFormScreen;
import dev.oneforallsurvival.economy.client.StallShopScreen;
import dev.oneforallsurvival.economy.client.TownFinancesCache;
import dev.oneforallsurvival.economy.client.TradeScreen;
import dev.oneforallsurvival.economy.client.TreasuryReasonScreen;
import dev.oneforallsurvival.economy.client.TreasuryScreen;
import dev.oneforallsurvival.economy.invoice.InvoiceItem;
import dev.oneforallsurvival.economy.invoice.InvoicePayPacket;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket;
import dev.oneforallsurvival.economy.quest.client.QuestClientCache;
import dev.oneforallsurvival.economy.quest.client.QuestKeys;
import dev.oneforallsurvival.economy.quest.client.QuestScreen;
import dev.oneforallsurvival.economy.quest.client.QuestScreenLayout;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallShopMenu;
import dev.oneforallsurvival.economy.town.TreasuryLedger;
import dev.oneforallsurvival.economy.trade.TradeActionPacket;
import dev.oneforallsurvival.economy.trade.TradeMenu;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FridgeBlock;
import dev.oneforallsurvival.food.FridgeBlockEntity;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerPanelPacket;
import dev.oneforallsurvival.grid.supplier.PowerSupplier;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelLayout;
import dev.oneforallsurvival.grid.supplier.client.PowerPanelScreen;
import dev.oneforallsurvival.repair.RepairRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Le film monté de toute l'économie (lots E1 à E6) pour la communauté de Lost Island RP (développement seulement, jamais
 * empaqueté). Lancement : {@code ./gradlew runDemoEconomieFilm -PwithPack -PsansJei} ; activé par
 * {@code -Doneforallsurvival.demo.economie}, inerte sinon. Cahier : {@code docs/superpowers/plans/2026-11-19-film-economie.md}.
 *
 * <p>Même chaîne que {@link FilmBandeAnnonce} (briques communes : {@link FilmTournage}, {@link FilmPrise},
 * {@link FilmMaya}) : un client qui se pilote seul dans un monde neuf, avec le pack ; une petite ville bâtie sur un
 * terrain plat (la place de marché de la bande-annonce, la mairie et son trésor, deux maisons raccordées, le poste du
 * fournisseur, un champ de blé, un enclos) ; Maya et Hugo en joueurs simulés ({@link JoueurDeTest}). Huit scènes, chacune
 * précédée d'un carton titre. Images dans {@code plans-economie/film/} du dossier du jeu, montées par
 * {@code tools/bande-annonce-montage.ps1}.
 *
 * <p>Ce qui est montré est vrai : les montants des légendes sont lus au serveur (ou dans la copie du client que le serveur
 * tient à jour) au moment du tournage, jamais écrits d'avance ; les commandes de l'équipe sont jouées par le serveur et
 * leur réponse s'affiche chez le joueur. Le temps du monde n'avance pas : seule l'horloge de l'économie saute à minuit
 * ({@link HorlogeDEssai}, comme l'essai de bout en bout), et la légende le dit.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmEconomie {

    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.economie") != null;
    private static final String MONDE = "film_economie";
    private static final long GRAINE = Long.getLong("oneforallsurvival.demo.economie.graine", 7_320_514L);
    private static final double GARDE_S = 2700;
    private static final double RYTHME = Double.parseDouble(System.getProperty("oneforallsurvival.demo.economie.rythme", "1.35"));
    private static final String HUGO = "Hugo";
    private static final String FOURNISSEUR = "Lumière du Port";
    private static final long PRIX = 20;
    private static final long ABONNEMENT = 15;
    private static final String MOTIF = "Lanternes de la place";
    private static final String MINUIT = "Minuit (horloge avancée pour le film)";
    private static final ResourceLocation RECETTE_POSTE = new ResourceLocation("oneforallsurvival", "power_station");

    private enum Phase { CHARGEMENT, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    private static Phase phase = Phase.CHARGEMENT;
    private static long debutPhaseNs = System.nanoTime();
    private static final long DEBUT_NS = System.nanoTime();
    private static boolean dansUneImage;
    private static String echec;
    private static volatile boolean preparationFaite;
    private static Style lienAccepter;
    private static final List<String> CLES = Collections.synchronizedList(new ArrayList<>());

    private static JoueurDeTest hugo;
    private static long valeurAvant;
    private static int compteAvant;
    private static int origine;

    // Valeurs lues au serveur (fil du serveur), montrées par les légendes.
    private static volatile long salaireMaya = -1;
    private static volatile long minutesMaya;
    private static volatile int lignesTresorAvant;
    private static volatile int facturesAvant;
    private static volatile long factureMaya = -1;
    private static volatile boolean factureHugoDue;
    private static volatile long caisse;
    private static volatile long gainButin;
    private static volatile String chasse = "";
    private static final List<UUID> ZOMBIES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean attendreVague;
    private static long derniereMortNs;
    private static long dernierSondageNs;
    private static int cible;
    private static int vagues;
    private static UUID zombieOeuf;
    private static long porteAvantOeuf;

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

    private FilmEconomie() {}

    // ================================================================================================
    // Boucle
    // ================================================================================================

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || phase == Phase.FIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            appliquerCamera(mc);
            appliquerCurseur(mc);
            return;
        }
        if (dansUneImage) {
            return;
        }
        dansUneImage = true;
        try {
            if ((System.nanoTime() - DEBUT_NS) / 1e9 > GARDE_S) {
                throw new IllegalStateException("delai de garde du film depasse en phase " + phase);
            }
            if (phase != Phase.CHARGEMENT) {
                mc.getToasts().clear();
            }
            avancer(mc);
            Etape e = phase == Phase.FILM ? etapeFilmee() : null;
            if (e != null && e.carton() != null) {
                double avance = actionFaite ? (System.nanoTime() - debutAction) / (e.reposMs() * rythme * 1e6) : 0;
                prise.carton(e.carton()[0], e.carton()[1], e.carton()[2], Math.min(1, avance));
            } else if (e != null) {
                String legende;
                try {
                    legende = e.legende().get();
                } catch (RuntimeException ex) {
                    legende = "";
                }
                prise.capturer(mc, e.titre(), legende, curseurVisible(mc) ? new double[] {curX, curY} : null, false);
            } else if (prise != null) {
                prise.couper();
            }
        } catch (RuntimeException | IOException e) {
            LOG.error("[film] échec en phase {} (étape {})", phase, nomEtape(), e);
            echec = e.toString();
            terminer(mc);
        } finally {
            dansUneImage = false;
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END) {
            return;
        }
        for (JoueurDeTest j : new JoueurDeTest[] {maya, hugo}) {
            if (j != null && j.connecte()) {
                j.lire();
                j.messagesRecus();
                j.textesRecus();
            }
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (!ACTIF) {
            return;
        }
        dernierChat = event.getMessage().getString();
        LOG.info("[film] message : {}", dernierChat);
        cles(event.getMessage());
        event.getMessage().visit((style, texte) -> {
            ClickEvent clic = style.getClickEvent();
            if (clic != null && clic.getAction() == ClickEvent.Action.RUN_COMMAND && clic.getValue().startsWith("/trade accept ")) {
                lienAccepter = style;
            }
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static void cles(Component c) {
        if (c.getContents() instanceof TranslatableContents t) {
            CLES.add(t.getKey());
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component inner) {
                    cles(inner);
                }
            }
        }
        for (Component enfant : c.getSiblings()) {
            cles(enfant);
        }
    }

    private static boolean cleRecue(String cle, int depuis) {
        synchronized (CLES) {
            for (int i = depuis; i < CLES.size(); i++) {
                if (CLES.get(i).equals(cle)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Vrai au plus quatre fois par seconde : les lectures au serveur des conditions d'attente ne l'inondent pas. */
    private static boolean sonder() {
        long n = System.nanoTime();
        if (n - dernierSondageNs < 250_000_000L) {
            return false;
        }
        dernierSondageNs = n;
        return true;
    }

    private static void avancer(Minecraft mc) throws IOException {
        double s = (System.nanoTime() - debutPhaseNs) / 1e9;
        switch (phase) {
            case CHARGEMENT -> {
                if (mc.getOverlay() != null) {
                    return;
                }
                if (mc.screen instanceof AccessibilityOnboardingScreen) {
                    mc.tell(() -> mc.setScreen(new TitleScreen()));
                } else if (mc.screen instanceof LostIslandTitleScreen || mc.screen instanceof TitleScreen) {
                    reglerLesOptions(mc);
                    prise = new FilmPrise(mc.gameDirectory.toPath().resolve("plans-economie/film"), true);
                    passer(Phase.CREATION);
                } else if (s > 900) {
                    throw new IllegalStateException("titre jamais atteint, écran " + mc.screen);
                }
            }
            case CREATION -> {
                passer(Phase.ATTENTE_MONDE);
                creerLeMonde(mc);
            }
            case ATTENTE_MONDE -> {
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null
                        && mc.getSingleplayerServer().isReady() && s > 4) {
                    mc.setScreen(null);
                    passer(Phase.PREPARATION);
                    MinecraftServer serveur = mc.getSingleplayerServer();
                    serveur.execute(() -> preparer(serveur));
                } else if (s > 300) {
                    throw new IllegalStateException("monde jamais chargé");
                }
            }
            case PREPARATION -> {
                if (preparationFaite && mc.player.blockPosition().distSqr(p(2, 0, 2)) < 400
                        && mc.level.getBlockState(FilmBandeAnnonce.table()).is(Blocks.CRAFTING_TABLE)
                        && mc.level.getBlockState(poste().west()).is(alternateurBloc()) && s > 12) {
                    mc.gui.getChat().clearMessages(false);
                    construireScenario(mc);
                    passer(Phase.FILM);
                    LOG.info("[film] scénario prêt : {} étapes, ville en {}", ETAPES.size(), o);
                } else if (s > 300) {
                    throw new IllegalStateException("ville jamais prête (preparation " + preparationFaite + ", joueur "
                            + mc.player.blockPosition() + ", table " + mc.level.getBlockState(FilmBandeAnnonce.table()) + ", alternateur "
                            + mc.level.getBlockState(poste().west()) + ")");
                }
            }
            case FILM -> jouer(mc);
            default -> {
            }
        }
    }

    private static void passer(Phase suivante) {
        LOG.info("[film] phase {} -> {}", phase, suivante);
        phase = suivante;
        debutPhaseNs = System.nanoTime();
    }

    private static void reglerLesOptions(Minecraft mc) {
        var opt = mc.options;
        opt.pauseOnLostFocus = false;
        opt.onboardAccessibility = false;
        opt.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        opt.renderDistance().set(12);
        opt.simulationDistance().set(8);
        opt.guiScale().set(2);
        opt.enableVsync().set(false);
        opt.framerateLimit().set(120);
        opt.hideGui = false;
        opt.gamma().set(0.6);
        opt.bobView().set(false);
        mc.resizeDisplay();
        mc.getToasts().clear();
    }

    private static void creerLeMonde(Minecraft mc) {
        java.nio.file.Path monde = mc.gameDirectory.toPath().resolve("saves").resolve(MONDE);
        if (java.nio.file.Files.isDirectory(monde)) {
            try (var tout = java.nio.file.Files.walk(monde)) {
                for (java.nio.file.Path f : tout.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    java.nio.file.Files.delete(f);
                }
            } catch (IOException e) {
                throw new IllegalStateException("ancien monde non effacé : " + monde, e);
            }
        }
        GameRules regles = new GameRules();
        regles.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        regles.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        regles.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        regles.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
        regles.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
        // Sans triche : le joueur n'est pas de l'équipe ; les commandes de l'équipe passent par le serveur.
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SURVIVAL, false, Difficulty.PEACEFUL, false, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(GRAINE, true, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // La ville
    // ================================================================================================

    // Le quartier de la mairie (au nord de la rue) et celui de l'électricité (au sud), à l'est de la place de marché.
    private static BlockPos tresor() {
        return p(33, 0, -8);
    }

    private static BlockPos tableau() {
        return p(41, 0, -6);
    }

    private static BlockPos moteur() {
        return p(31, 0, 11);
    }

    private static BlockPos poste() {
        return p(33, 0, 11);
    }

    private static BlockPos etabli() {
        return p(35, 0, 8);
    }

    private static BlockPos compteur(boolean deMaya) {
        return p(38, 0, deMaya ? 11 : 17);
    }

    private static BlockPos disjoncteur(boolean deMaya) {
        return p(39, 0, deMaya ? 11 : 17);
    }

    private static BlockPos frigo(boolean deMaya) {
        return p(40, 0, deMaya ? 11 : 17);
    }

    /** L'allée entre les deux maisons : le joueur s'y tient face au poste, aux compteurs, aux disjoncteurs. */
    private static double zAllee() {
        return o.getZ() + 14.5;
    }

    private static BlockPos ble(int i) {
        return p(47 + i, 0, -10);
    }

    private static BlockPos zombie(int i) {
        return p(52 + i, 0, 8);
    }

    private static Block alternateurBloc() {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation("createaddition", "alternator"));
    }

    private static Item objet(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
    }

    /** Sur le thread du serveur : site, décor, règles, joueur, Maya. */
    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer moi = serveur.getPlayerList().getPlayers().get(0);
        o = chercherSite(monde, moi.blockPosition());
        LOG.info("[film] ville en {}", o);
        FilmBandeAnnonce.batir(monde);
        batirVille(monde);
        monde.setDayTime(3500);
        monde.setWeatherParameters(1_000_000, 0, false, false);
        commande(serveur, "gamerule doDaylightCycle false");
        commande(serveur, "gamerule doMobSpawning false");
        commande(serveur, "gamerule doInsomnia false");
        commande(serveur, "gamerule doImmediateRespawn true");
        for (Entity e : monde.getEntities((Entity) null, new AABB(o).inflate(70), e -> !(e instanceof Player))) {
            e.discard();
        }
        // Ce que le joueur porte : sa somme de départ (reçue à l'entrée) et quelques objets vanilla.
        List<ItemStack> garder = new ArrayList<>();
        for (ItemStack s : moi.getInventory().items) {
            if (CoinMath.valueOf(s) > 0) {
                garder.add(s.copy());
            }
        }
        moi.getInventory().clearContent();
        for (ItemStack s : garder) {
            moi.getInventory().add(s);
        }
        moi.teleportTo(monde, o.getX() + 2.5, o.getY(), o.getZ() + 2.5, 180F, 10F);
        moi.setGameMode(GameType.SURVIVAL);
        preparationFaite = true;
    }

    /** Le terrain le plus plat, sec et au-dessus de la mer, assez large pour toute la ville (hauteurs du générateur). */
    private static BlockPos chercherSite(ServerLevel monde, BlockPos depart) {
        ChunkGenerator gen = monde.getChunkSource().getGenerator();
        var etat = monde.getChunkSource().randomState();
        BlockPos meilleur = null;
        int meilleurScore = Integer.MAX_VALUE;
        for (int dx = -384; dx <= 384; dx += 48) {
            for (int dz = -384; dz <= 384; dz += 48) {
                int cx = depart.getX() + dx;
                int cz = depart.getZ() + dz;
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                boolean eau = false;
                for (int ix = -20; ix <= 58; ix += 13) {
                    for (int iz = -14; iz <= 22; iz += 9) {
                        int h = gen.getBaseHeight(cx + ix, cz + iz, Heightmap.Types.WORLD_SURFACE_WG, monde, etat);
                        int f = gen.getBaseHeight(cx + ix, cz + iz, Heightmap.Types.OCEAN_FLOOR_WG, monde, etat);
                        eau |= h != f;
                        min = Math.min(min, h);
                        max = Math.max(max, h);
                    }
                }
                int score = (max - min) * 10 + (eau ? 1000 : 0) + (min < 65 ? 500 : 0) + (max > 100 ? 200 : 0)
                        + (Math.abs(dx) + Math.abs(dz)) / 48;
                if (score < meilleurScore) {
                    meilleurScore = score;
                    meilleur = new BlockPos(cx, Math.round((min + max) / 2f), cz);
                }
            }
        }
        LOG.info("[film] site retenu {} (score {})", meilleur, meilleurScore);
        return meilleur;
    }

    private static void poser(ServerLevel monde, BlockPos pos, BlockState etat) {
        monde.setBlock(pos, etat, 2);
    }

    private static void remplir(ServerLevel monde, int x1, int y1, int z1, int x2, int y2, int z2, BlockState etat) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    poser(monde, p(x, y, z), etat);
                }
            }
        }
    }

    /** Murs (sans le dedans) d'une boîte, de {@code y1} à {@code y2}. */
    private static void murs(ServerLevel monde, int x1, int z1, int x2, int z2, int y1, int y2, BlockState etat) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x == x1 || x == x2 || z == z1 || z == z2) {
                    for (int y = y1; y <= y2; y++) {
                        poser(monde, p(x, y, z), etat);
                    }
                }
            }
        }
    }

    private static BlockState pilier() {
        return Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    }

    /** Le quartier est de la ville : rue, mairie, champ, enclos, poste et deux maisons raccordées. */
    private static void batirVille(ServerLevel monde) {
        MinecraftServer serveur = monde.getServer();
        BlockState air = Blocks.AIR.defaultBlockState();
        remplir(monde, 25, 0, -16, 58, 26, 24, air);
        remplir(monde, 25, -5, -16, 58, -2, 24, Blocks.DIRT.defaultBlockState());
        remplir(monde, 25, -1, -16, 58, -1, 24, Blocks.GRASS_BLOCK.defaultBlockState());
        remplir(monde, -20, 0, 21, 24, 26, 24, air);
        remplir(monde, -20, -5, 21, 24, -2, 24, Blocks.DIRT.defaultBlockState());
        remplir(monde, -20, -1, 21, 24, -1, 24, Blocks.GRASS_BLOCK.defaultBlockState());
        // La rue, de la place à l'enclos, et l'allée des maisons.
        Random r = new Random(23);
        for (int x = 16; x <= 56; x++) {
            for (int z = 2; z <= 5; z++) {
                poser(monde, p(x, -1, z), (r.nextInt(9) == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).defaultBlockState());
            }
        }
        for (int x = 30; x <= 45; x++) {
            for (int z = 13; z <= 15; z++) {
                poser(monde, p(x, -1, z), (r.nextInt(7) == 0 ? Blocks.GRAVEL : Blocks.DIRT_PATH).defaultBlockState());
            }
        }
        for (int z = 6; z <= 12; z++) {
            poser(monde, p(36, -1, z), Blocks.DIRT_PATH.defaultBlockState());
        }

        // La mairie : une halle de pierre ouverte sur la rue, le trésor au fond.
        remplir(monde, 28, -1, -10, 38, -1, -4, Blocks.POLISHED_ANDESITE.defaultBlockState());
        BlockState pierre = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = 28; x <= 38; x++) {
            remplir(monde, x, 0, -10, x, 4, -10, pierre);
        }
        for (int z = -10; z <= -4; z++) {
            remplir(monde, 28, 0, z, 28, 4, z, pierre);
            remplir(monde, 38, 0, z, 38, 4, z, pierre);
        }
        for (int x : new int[] {28, 38}) {
            remplir(monde, x, 0, -4, x, 4, -4, pilier());
        }
        remplir(monde, 28, 5, -10, 38, 5, -4, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        remplir(monde, 27, 5, -11, 39, 5, -11, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        remplir(monde, 27, 5, -3, 39, 5, -3, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        remplir(monde, 29, 4, -4, 37, 4, -4, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = 30; x <= 36; x += 3) {
            poser(monde, p(x, 2, -10), Blocks.GLASS_PANE.defaultBlockState());
            poser(monde, p(x, 3, -9), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
        for (int x = 30; x <= 36; x += 6) {
            poser(monde, p(x, 3, -5), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        poser(monde, p(29, 0, -9), Blocks.BOOKSHELF.defaultBlockState());
        poser(monde, p(37, 0, -9), Blocks.BOOKSHELF.defaultBlockState());
        poser(monde, p(29, 1, -9), Blocks.BOOKSHELF.defaultBlockState());
        poser(monde, p(37, 1, -9), Blocks.BOOKSHELF.defaultBlockState());
        poser(monde, p(30, 0, -9), Blocks.LECTERN.defaultBlockState());
        poser(monde, p(36, 0, -7), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        poser(monde, p(30, 0, -7), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        for (int[] c : new int[][] {{26, -3}, {39, -1}, {44, -1}}) {
            poser(monde, p(c[0], 0, c[1]), Blocks.OAK_FENCE.defaultBlockState());
            poser(monde, p(c[0], 1, c[1]), Blocks.OAK_FENCE.defaultBlockState());
            poser(monde, p(c[0], 2, c[1]), Blocks.LANTERN.defaultBlockState());
        }

        // Le champ de blé, mûr, à l'est de la mairie.
        for (int x = 45; x <= 53; x++) {
            poser(monde, p(x, -1, -14), Blocks.WATER.defaultBlockState());
            for (int z = -13; z <= -10; z++) {
                poser(monde, p(x, -1, z), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
                poser(monde, p(x, 0, z), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
            }
        }
        for (int x = 44; x <= 54; x++) {
            poser(monde, p(x, 0, -15), Blocks.OAK_FENCE.defaultBlockState());
        }
        poser(monde, p(44, 0, -12), Blocks.HAY_BLOCK.defaultBlockState());
        poser(monde, p(44, 0, -11), Blocks.COMPOSTER.defaultBlockState());

        // L'enclos des zombies : une palissade au nord, contre laquelle ils se tiennent.
        for (int x = 50; x <= 57; x++) {
            poser(monde, p(x, 0, 7), Blocks.SPRUCE_FENCE.defaultBlockState());
            poser(monde, p(x, 1, 7), Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        for (int z = 7; z <= 12; z++) {
            poser(monde, p(50, 0, z), Blocks.SPRUCE_FENCE.defaultBlockState());
            poser(monde, p(57, 0, z), Blocks.SPRUCE_FENCE.defaultBlockState());
        }

        // Le poste du fournisseur : le moteur dans sa cabane, l'alternateur de Crafts & Additions, puis la place du poste.
        BlockState planche = Blocks.SPRUCE_PLANKS.defaultBlockState();
        remplir(monde, 29, -1, 9, 34, -1, 13, Blocks.COBBLESTONE.defaultBlockState());
        murs(monde, 29, 9, 31, 13, 0, 2, planche);
        remplir(monde, 30, 0, 10, 30, 1, 12, planche);
        remplir(monde, 31, 0, 10, 31, 1, 10, planche);
        remplir(monde, 31, 0, 12, 31, 1, 12, planche);
        poser(monde, p(31, 1, 11), planche);
        remplir(monde, 28, 3, 8, 32, 3, 14, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        remplir(monde, 29, 0, 9, 29, 2, 9, pilier());
        remplir(monde, 29, 0, 13, 29, 2, 13, pilier());
        remplir(monde, 31, 0, 9, 31, 2, 9, pilier());
        remplir(monde, 31, 0, 13, 31, 2, 13, pilier());
        // Posés par le code et non par /setblock : la ville est loin du joueur, ses chunks ne sont pas encore « chargés »
        // pour une commande (elle refuserait), alors que setBlock les charge.
        monde.setBlock(p(32, 0, 11), ReseauxDev.avec(alternateurBloc().defaultBlockState(), "facing", "west"), 3);
        monde.setBlock(etabli(), ReseauxDev.avec(dev.oneforallsurvival.bench.BenchRegistry.ENGINEER_BENCH.get().defaultBlockState(),
                "facing", "south"), 3);
        poser(monde, p(34, 0, 7), Blocks.BARREL.defaultBlockState());
        poser(monde, p(33, 0, 7), Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));

        // Les deux maisons : compteur et disjoncteur dehors, contre le mur ouest ; le frigo dans le mur, ouvert sur la pièce.
        maison(monde, true);
        maison(monde, false);
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /** Une maison : murs x = 40 à 44, le compteur au nom de l'abonné, son disjoncteur, un frigo garni de viande crue. */
    private static void maison(ServerLevel monde, boolean deMaya) {
        MinecraftServer serveur = monde.getServer();
        int z1 = deMaya ? 7 : 16;
        int z2 = deMaya ? 12 : 21;
        int porte = deMaya ? z2 : z1;
        BlockState mur = (deMaya ? Blocks.BIRCH_PLANKS : Blocks.OAK_PLANKS).defaultBlockState();
        remplir(monde, 40, -1, z1, 44, -1, z2, Blocks.SPRUCE_PLANKS.defaultBlockState());
        murs(monde, 40, z1, 44, z2, 0, 2, mur);
        for (int[] c : new int[][] {{40, z1}, {44, z1}, {40, z2}, {44, z2}}) {
            remplir(monde, c[0], 0, c[1], c[0], 2, c[1], pilier());
        }
        remplir(monde, 39, 3, z1 - 1, 45, 3, z2 + 1, (deMaya ? Blocks.SPRUCE_SLAB : Blocks.DARK_OAK_SLAB).defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        poser(monde, p(42, 0, porte), air());
        poser(monde, p(42, 1, porte), air());
        poser(monde, p(44, 1, (z1 + z2) / 2), Blocks.GLASS_PANE.defaultBlockState());
        poser(monde, p(42, 1, deMaya ? z1 : z2), Blocks.GLASS_PANE.defaultBlockState());
        poser(monde, p(43, 0, deMaya ? z1 + 1 : z2 - 1), Blocks.RED_BED.defaultBlockState());
        poser(monde, p(41, 2, (z1 + z2) / 2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        poser(monde, p(43, 0, porte + (deMaya ? 1 : -1)), Blocks.POTTED_POPPY.defaultBlockState());
        String nom = deMaya ? MAYA : HUGO;
        BlockPos c = compteur(deMaya);
        BlockPos d = disjoncteur(deMaya);
        avecAbonne(monde, c, ReseauxDev.avec(GridRegistry.METER.get().defaultBlockState(), "facing", "east"), nom);
        avecAbonne(monde, d, ReseauxDev.avec(ReseauxDev.avec(GridRegistry.BREAKER.get().defaultBlockState(), "facing", "east"),
                "closed", "true"), nom);
        BlockPos f = frigo(deMaya);
        poser(monde, f, FoodRegistry.FRIDGE.get().defaultBlockState().setValue(FridgeBlock.FACING, Direction.EAST));
        serveur.execute(() -> {
            if (monde.getBlockEntity(f) instanceof FridgeBlockEntity be) {
                be.inventory().setStackInSlot(0, new ItemStack(Items.BEEF, 6));
                be.inventory().setStackInSlot(1, new ItemStack(Items.COD, 4));
            }
        });
    }

    /** Le bloc posé, puis l'abonné écrit dans son entité (comme {@code /setblock ...{subscriber:"..."}}). */
    private static void avecAbonne(ServerLevel monde, BlockPos pos, BlockState etat, String nom) {
        monde.setBlock(pos, etat, 3);
        var be = monde.getBlockEntity(pos);
        if (be == null) {
            throw new IllegalStateException("aucune entité de bloc en " + pos);
        }
        net.minecraft.nbt.CompoundTag t = be.saveWithoutMetadata();
        t.putString("subscriber", nom);
        be.load(t);
        be.setChanged();
        monde.sendBlockUpdated(pos, etat, etat, 3);
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    // ================================================================================================
    // Outils propres au film
    // ================================================================================================

    private static UUID uuidDe(String nom) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + nom).getBytes(StandardCharsets.UTF_8));
    }

    private static void spectateur(Minecraft mc, Vec3 de, Vec3 vise) {
        sur(mc, () -> moiServeur(mc).setGameMode(GameType.SPECTATOR));
        mc.options.hideGui = true;
        travelling(de, vise, de, vise, 1);
    }

    private static void survie(Minecraft mc, double x, double z, float yaw, float pitch) {
        finCamera();
        mc.options.hideGui = false;
        sur(mc, () -> moiServeur(mc).setGameMode(GameType.SURVIVAL));
        aller(mc, x, o.getY(), z, yaw, pitch);
    }

    private static boolean enSurvie(Minecraft mc, double x, double z) {
        return !mc.player.isSpectator() && pres(mc, x, z);
    }

    /** Un passage de caméra en spectateur, l'interface masquée (mise en place, puis le plan filmé). */
    private static void passage(Minecraft mc, String nom, Supplier<String> legende, Vec3 de, Vec3 cible, Vec3 a, Vec3 cible2, long ms) {
        prep(nom + "-camera", () -> spectateur(mc, de, cible), () -> mc.player.isSpectator());
        planV(nom, legende, () -> travelling(de, cible, a, cible2, ms), FilmTournage::travellingFini, 200);
    }

    private static void horlogeAvantMinuit(int secondes) {
        long reel = System.currentTimeMillis();
        long now = EconomyClock.now();
        ZoneId zone = EconomyCalendar.configuredZone();
        LocalDate jour = EconomyCalendar.payDay(now, zone, EconomyConfig.payHour());
        long frontiere = EconomyCalendar.boundaryAfter(jour, zone, EconomyConfig.payHour());
        HorlogeDEssai.regler(frontiere - secondes * 1_000L - reel);
        LOG.info("[film] horloge de l'économie avancée : frontière du {} dans {} s (décalage {} ms)", jour, secondes,
                HorlogeDEssai.decalage());
    }

    /** Sur le thread du serveur : le lien « [Accepter] » de la demande d'accord reçue par ce joueur, joué en son nom. */
    private static boolean accord(JoueurDeTest j) {
        String lien = null;
        List<Component> recus = new ArrayList<>(j.composantsALaConnexion());
        recus.addAll(j.composantsRecus());
        for (Component c : recus) {
            for (String cmd : JoueurDeTest.commandes(c)) {
                if (cmd.startsWith("/power consent ")) {
                    lien = cmd;
                }
            }
        }
        if (lien == null) {
            return false;
        }
        ServerPlayer p = j.joueur();
        int r = p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), lien.substring(1));
        LOG.info("[film] accord de {} : {} -> {}", p.getGameProfile().getName(), lien, r);
        return r > 0;
    }

    private static PowerPanelScreen panneau(Minecraft mc) {
        return mc.screen instanceof PowerPanelScreen s && s.layout() != null ? s : null;
    }

    private static PowerPanelPacket.Client rang(Minecraft mc, String abonne) {
        PowerPanelScreen s = panneau(mc);
        if (s == null) {
            return null;
        }
        for (PowerPanelPacket.Client c : s.state().clients()) {
            if (c.subscriber().equalsIgnoreCase(abonne)) {
                return c;
            }
        }
        return null;
    }

    private static int[] centre(PowerPanelLayout.Rect r) {
        if (r == null) {
            throw new IllegalStateException("bouton absent du panneau");
        }
        return new int[] {r.x() + r.w() / 2, r.y() + r.h() / 2};
    }

    /** Le bouton {@code b} (0 : accepter, couper ou rétablir) de la ligne de cet abonné. */
    private static int[] boutonRang(Minecraft mc, String abonne, int b) {
        PowerPanelScreen s = panneau(mc);
        List<PowerPanelPacket.Client> clients = s.state().clients();
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).subscriber().equalsIgnoreCase(abonne)) {
                return centre(s.layout().rows().get(i).buttons().get(b));
            }
        }
        throw new IllegalStateException("aucune ligne pour " + abonne);
    }

    private static List<QuestSyncPacket.Entry> lignesQuete(QuestRef.Kind genre) {
        List<QuestSyncPacket.Entry> out = new ArrayList<>();
        QuestSyncPacket etat = QuestClientCache.get();
        if (etat != null) {
            for (QuestSyncPacket.Entry e : etat.quests()) {
                if (e.ref().kind() == genre) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    private static QuestSyncPacket.Entry journaliere(int place) {
        return lignesQuete(QuestRef.Kind.DAILY).stream().filter(e -> e.ref().index() == place).findFirst().orElse(null);
    }

    private static int[] boutonQuete(Minecraft mc, int place) {
        QuestScreenLayout.Layout l = ((QuestScreen) mc.screen).layout();
        List<QuestSyncPacket.Entry> rangees = lignesQuete(QuestRef.Kind.DAILY);
        for (int r = 0; r < rangees.size(); r++) {
            if (rangees.get(r).ref().index() == place) {
                QuestScreenLayout.Rect b = l.sections().get(0).rows().get(r).button();
                return new int[] {b.x() + b.w() / 2, b.y() + b.h() / 2};
            }
        }
        throw new IllegalStateException("aucun bouton pour la journalière " + place);
    }

    private static void toucheJ(Minecraft mc, int action) {
        int code = QuestKeys.OPEN.getKey().getValue();
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, GLFW.glfwGetKeyScancode(code), action, 0);
    }

    private static void regarder(Minecraft mc, Vec3 point) {
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
    }

    private static Entity entite(Minecraft mc, UUID id) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getUUID().equals(id)) {
                return e;
            }
        }
        return null;
    }

    // ================================================================================================
    // Le scénario
    // ================================================================================================

    private static void construireScenario(Minecraft mc) {
        rythme = RYTHME;
        delaiPas = 45;
        fin = () -> terminer(Minecraft.getInstance());
        ouverture(mc);
        monnaie(mc);
        commerce(mc);
        tresor(mc);
        butin(mc);
        quetes(mc);
        electricite(mc);
        equipe(mc);
    }

    // --- 1. Ouverture ---------------------------------------------------------------------------------

    private static void ouverture(Minecraft mc) {
        // Maya entre dès le début, policière : son temps de service se compte jusqu'à minuit. Elle range sa somme de départ
        // dans son coffre-fort (posé d'un clic, « Déposer tout » par le chemin des écrans, comme l'essai de bout en bout).
        prep("maya-entre", () -> sur(mc, () -> {
            MinecraftServer s = serveur(mc);
            // Les deux chênes du décor de la place qui masquaient le travelling d'ouverture sont retirés.
            ServerLevel monde = s.overworld();
            remplir(monde, -1, 0, 13, 9, 22, 22, air());
            remplir(monde, 15, 0, 10, 25, 22, 20, air());
            mayaEntre(s, FilmBandeAnnonce.coffreFortMaya().getX() + 0.5, FilmBandeAnnonce.coffreFortMaya().getZ() + 2.5, 180F);
            mayaPoseBloc(s, FilmBandeAnnonce.coffreFortMaya(), EconomyRegistry.SAFE_ITEM.get());
            SafeAccounts comptes = SafeAccounts.get(s);
            CoinActions.apply(maya(), new SafeStore(comptes, maya().getUUID()), CoinRequest.DEPOSIT_ALL, null, 0, false);
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), "role set " + MAYA + " policier");
            maya().teleportTo(s.overworld(), o.getX() + 6.5, o.getY(), o.getZ() + 1.5, 160F, 0F);
            LOG.info("[film] Maya : coffre-fort {} cuivres, porte {}", comptes.total(maya().getUUID()),
                    CoinMath.valueOf(PlayerCoins.held(maya())));
        }), () -> mayaVue(mc) != null && mc.level.getBlockState(FilmBandeAnnonce.coffreFortMaya()).is(EconomyRegistry.SAFE.get()));
        // Au-dessus des arbres de la place (chênes plantés par le décor de la bande-annonce).
        prep("ouverture-camera", () -> spectateur(mc, v(-14, 17, 24), v(2, 0, -1)), () -> mc.player.isSpectator());
        carton("carton-1", "Lost Island", "L'économie", "Monnaie, commerce, trésor, butin, quêtes, électricité", 2600);
        scene("Lost Island — l'économie");
        plan("ville-marche", "Une petite ville de l'île : la place de marché…", () -> travelling(v(-14, 17, 24), v(2, 0, -1), v(12, 17, 22),
                v(33, 1, -6), 5200), FilmTournage::travellingFini, 0);
        plan("ville-quartier", "… la mairie et son trésor, deux maisons raccordées, le poste du fournisseur", () -> travelling(v(12, 17, 22),
                v(33, 1, -6), v(26, 13, 30), v(38, 0, 13), 5200), FilmTournage::travellingFini, 300);
        prep("ouverture-fin", () -> survie(mc, o.getX() + 0.5, o.getZ() + 3.5, 180, 15), () -> enSurvie(mc, o.getX() + 0.5, o.getZ() + 3.5));
    }

    // --- 2. La monnaie (E1) ---------------------------------------------------------------------------

    private static void monnaie(Minecraft mc) {
        prep("monnaie-prep", () -> {
            mc.gui.getChat().clearMessages(false);
            tenirVide(mc);
        }, () -> true);
        carton("carton-2", "Scène 2 · lot E1", "La monnaie", "Cinq pièces, le change, la bourse, le coffre-fort", 2200);
        scene("La monnaie");
        plan("eco-give", "L'équipe injecte de l'argent : /eco give, joué par le serveur, tracé au registre", () -> {
            valeurAvant = valeurPortee(mc);
            commandeVue(mc, "eco give " + mc.player.getGameProfile().getName() + " 11111 Bienvenue sur l'île");
        }, () -> valeurPortee(mc) == valeurAvant + 11111, 2000);
        plan("inventaire", "Cinq pièces, du cuivre au rubis : chacune vaut dix fois la précédente", () -> {
            mc.gui.getChat().clearMessages(false);
            mc.setScreen(new InventoryScreen(mc.player));
            placerCurseur(330, 120);
        }, () -> mc.screen instanceof InventoryScreen, 300);
        for (Coin c : new Coin[] {Coin.COPPER, Coin.RUBY}) {
            survol("piece-" + c.key(), "Cinq pièces, du cuivre au rubis : chacune vaut dix fois la précédente", () -> {
                int i = caseJoueur(mc, st -> st.is(c.item()));
                if (i < 0) {
                    throw new IllegalStateException("pas de pièce " + c);
                }
                return centreCase(mc, i);
            }, 1000);
        }
        prep("inventaire-ferme", () -> fermerEcran(mc), () -> mc.screen == null);

        // Le change à la table.
        prep("table-devant", () -> {
            tenirVide(mc);
            devant(mc, FilmBandeAnnonce.table(), 35);
        }, () -> devantPret(mc, FilmBandeAnnonce.table()));
        plan("table", "Le change se fait à la table de fabrication", () -> clicDroit(mc, FilmBandeAnnonce.table(), Direction.SOUTH),
                () -> mc.screen instanceof CraftingScreen, 300);
        clic("or-pris", "Une pièce d'or dans la grille…", () -> centreCase(mc, caseJoueur(mc, st -> st.is(Coin.GOLD.item()))), 0,
                () -> !mc.player.containerMenu.getCarried().isEmpty(), 100);
        clic("or-pose", "Une pièce d'or dans la grille…", () -> centreCase(mc, 1), 1,
                () -> !mc.player.containerMenu.getSlot(0).getItem().isEmpty(), 100);
        clic("or-rendu", "Une pièce d'or dans la grille…", () -> centreCase(mc, caseOuVide(mc, st -> st.is(Coin.GOLD.item()))), 0,
                () -> mc.player.containerMenu.getCarried().isEmpty(), 500);
        clic("change", "… donne dix pièces d'argent, à valeur égale", () -> centreCase(mc, 0), 0,
                () -> mc.player.containerMenu.getSlot(1).getItem().isEmpty() && !mc.player.containerMenu.getCarried().isEmpty(), 700);
        clic("change-range", "… donne dix pièces d'argent, à valeur égale", () -> centreCase(mc, caseOuVide(mc, st -> st.is(Coin.SILVER.item()))),
                0, () -> mc.player.containerMenu.getCarried().isEmpty(), 800);
        prep("table-ferme", () -> fermerEcran(mc), () -> mc.screen == null);

        // La bourse qui ramasse les pièces au sol.
        BlockPos t = FilmBandeAnnonce.tremieSol();
        prep("bourse-donnee", () -> {
            sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(EconomyRegistry.PURSE.get())));
            aller(mc, t.getX() + 0.5, o.getY(), t.getZ() + 3.5, 180, 28);
        }, () -> bourse(mc) == 0 && compter(mc, EconomyRegistry.PURSE.get()) == 1 && pres(mc, t.getX() + 0.5, t.getZ() + 3.5));
        plan("pieces-tombent", "Des pièces au sol : une trémie ne les prend pas, seuls les joueurs les ramassent", () -> sur(mc, () -> {
            ServerLevel monde = serveur(mc).overworld();
            for (CoinDropEntity piece : List.of(CoinDropEntity.at(monde, t.getX() + 0.4, t.getY() + 1.6, t.getZ() + 0.5, Coin.GOLD, 3),
                    CoinDropEntity.at(monde, t.getX() + 0.6, t.getY() + 2.0, t.getZ() + 0.5, Coin.SILVER, 4))) {
                piece.setDeltaMovement(Vec3.ZERO);
                monde.addFreshEntity(piece);
            }
            EconomyLedger.get(serveur(mc)).created(MoneySource.STAFF, 340, null, "Pièces perdues sur la place");
        }), () -> true, 2200);
        planV("ramasse", () -> "La bourse les range d'elle-même : " + fmt(bourse(mc)),
                () -> mc.options.keyUp.setDown(true), () -> {
                    boolean pris = bourse(mc) == 340;
                    mc.options.keyUp.setDown(!pris);
                    return pris;
                }, 1800);
        prep("ramasse-fin", () -> mc.options.keyUp.setDown(false), () -> true);

        // Le coffre-fort et son écran.
        BlockPos fort = FilmBandeAnnonce.coffreFortMoi();
        prep("fort-donne", () -> {
            sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(EconomyRegistry.SAFE_ITEM.get())));
            devant(mc, fort, 40);
        }, () -> compter(mc, EconomyRegistry.SAFE_ITEM.get()) == 1 && devantPret(mc, fort));
        plan("fort-pose", "Un coffre-fort à soi : un seul par joueur", () -> {
            tenir(mc, st -> st.is(EconomyRegistry.SAFE_ITEM.get()));
            clicDroit(mc, fort.below(), Direction.UP);
        }, () -> mc.level.getBlockState(fort).is(EconomyRegistry.SAFE.get()), 900);
        prep("fort-main-vide", () -> {
            tenirVide(mc);
            placerCurseur(320, 300);
        }, () -> true);
        plan("fort-ouvert", "Son compte est gardé par le serveur, pas par le bloc", () -> clicDroit(mc, fort, Direction.SOUTH),
                () -> menuOuvert(mc, SafeMenu.class), 900);
        clic("fort-depose", "« Déposer tout » : la bourse et l'inventaire se vident dans le coffre-fort", () -> centreElement(mc, "deposit_all"), 0,
                () -> menuOuvert(mc, SafeMenu.class) && valeurPortee(mc) == 0, 1300);
        clic("fort-change", "Le change, au coffre-fort aussi : une émeraude cassée en dix pièces d'or", () -> centreElement(mc, "break.emerald"), 0,
                () -> ((SafeMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu()).count(Coin.EMERALD) == 0, 1300);
        clic("fort-retire", "Clic droit : dix pièces d'or en ressortent", () -> centreElement(mc, "row.gold.10"), 1,
                () -> valeurPortee(mc) == 1000, 1300);
        prep("fort-ferme", () -> fermerEcran(mc), () -> mc.screen == null);

        // Un coffre ordinaire qui refuse la pièce : une pièce d'or sortie de la bourse.
        BlockPos coffre = FilmBandeAnnonce.coffre();
        prep("coffre-devant", () -> {
            tenirVide(mc);
            devant(mc, coffre, 35);
        }, () -> devantPret(mc, coffre));
        plan("coffre", "Un coffre ordinaire n'accepte pas l'argent…", () -> clicDroit(mc, coffre, Direction.SOUTH),
                () -> mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu() != mc.player.inventoryMenu, 300);
        clic("coffre-prend", "Un coffre ordinaire n'accepte pas l'argent…", () -> {
            origine = caseJoueur(mc, st -> st.is(Coin.GOLD.item()));
            return centreCase(mc, origine);
        }, 0, () -> !mc.player.containerMenu.getCarried().isEmpty(), 200);
        clic("coffre-refuse", "… les pièces restent au curseur", () -> centreCase(mc, 4), 0,
                () -> !mc.player.containerMenu.getCarried().isEmpty() && mc.player.containerMenu.getSlot(4).getItem().isEmpty(), 1400);
        clic("coffre-rend", "… les pièces restent au curseur", () -> centreCase(mc, origine), 0,
                () -> mc.player.containerMenu.getCarried().isEmpty(), 300);
        prep("coffre-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
    }

    // --- 3. Le commerce (E2) --------------------------------------------------------------------------

    private static TradeMenu menuEchange(Minecraft mc) {
        return ((TradeScreen) mc.screen).getMenu();
    }

    private static int dansEchange(Minecraft mc, boolean sienne, Item objet) {
        int debut = sienne ? TradeMenu.OWN_START : TradeMenu.OTHER_START;
        int n = 0;
        for (int s = debut; s < debut + TradeMenu.OFFER_SIZE; s++) {
            ItemStack st = menuEchange(mc).getSlot(s).getItem();
            if (st.is(objet)) {
                n += st.getCount();
            }
        }
        return n;
    }

    private static int[] rangeeVitrine(Minecraft mc, int rangee) {
        int[] g = origineEcran(mc);
        return new int[] {g[0] + 8 + 154 - 42 - 2 + 21, g[1] + StallShopMenu.LIST_Y + rangee * StallShopMenu.ROW_H + (StallShopMenu.ROW_H - 14) / 2 + 7};
    }

    private static MarketStallBlockEntity etalClient(Minecraft mc, BlockPos pos) {
        return mc.level.getBlockEntity(pos) instanceof MarketStallBlockEntity be ? be : null;
    }

    private static void commerce(Minecraft mc) {
        BlockPos etal = FilmBandeAnnonce.etalMaya();
        // Maya pose et garnit son étal hors champ (les mêmes gestes que dans la bande-annonce).
        prep("maya-etal", () -> sur(mc, () -> {
            ServerPlayer j = maya();
            j.getInventory().add(new ItemStack(Items.BREAD, 18));
            j.getInventory().add(new ItemStack(Items.APPLE, 9));
            j.getInventory().add(new ItemStack(Items.COOKED_COD, 6));
            j.getInventory().add(new ItemStack(Items.WHEAT, 1));
            mayaPoseBloc(serveur(mc), etal, EconomyRegistry.MARKET_STALL_ITEM.get());
            mayaGarnit(etal);
        }), () -> {
            MarketStallBlockEntity be = etalClient(mc, etal);
            return be != null && be.offers().get(2) != null;
        });
        prep("echange-place", () -> {
            mc.gui.getChat().clearMessages(false);
            // Ses pains du stock de l'étal y restent : elle en reprend six pour l'échange.
            sur(mc, () -> maya().getInventory().add(new ItemStack(Items.BREAD, 6)));
            mayaVa(mc, o.getX() + 0.5, o.getZ() + 2.5, 0F);
            aller(mc, o.getX() + 0.5, o.getY(), o.getZ() + 5.0, 180, 5);
            lienAccepter = null;
        }, () -> pres(mc, o.getX() + 0.5, o.getZ() + 5.0) && mayaVue(mc) != null && mayaVue(mc).distanceTo(mc.player) < 3.5);
        carton("carton-3", "Scène 3 · lot E2", "Le commerce", "L'échange, l'étal, la facture", 2200);
        scene("Le commerce");
        plan("demande", "Maya propose un échange (/trade) : un lien [Accepter] arrive", () -> sur(mc,
                () -> serveur(mc).getCommands().performPrefixedCommand(maya().createCommandSourceStack(), "trade " + mc.player.getGameProfile().getName())),
                () -> lienAccepter != null, 1400);
        plan("accepte", "Un clic sur [Accepter] : la fenêtre d'échange s'ouvre chez les deux", () -> {
            ChatScreen chat = new ChatScreen("");
            mc.setScreen(chat);
            chat.handleComponentClicked(lienAccepter);
            if (mc.screen == chat) {
                mc.setScreen(null);
            }
            placerCurseur(300, 260);
        }, () -> mc.screen instanceof TradeScreen, 600);
        plan("maya-pose", "Maya pose cinq pains", () -> sur(mc, () -> mayaPoseDansEchange(Items.BREAD, 5)),
                () -> dansEchange(mc, false, Items.BREAD) == 5, 700);
        clic("plus-or", "Il verse une pièce d'or", () -> centreElement(mc, "coins.gold.1"), 0,
                () -> dansEchange(mc, true, Coin.GOLD.item()) == 1, 500);
        clic("confirme", "Deux confirmations : la sienne…", () -> centreElement(mc, "confirm"), 0, () -> menuEchange(mc).selfConfirmed(), 700);
        plan("fait", "… et celle de Maya : l'échange a lieu, d'un bloc", () -> {
            compteAvant = compter(mc, EconomyRegistry.RECEIPT.get());
            sur(mc, () -> {
                TradeMenu m = (TradeMenu) maya().containerMenu;
                maya.envoyer(TradeActionPacket.confirm(m.version()));
            });
        }, () -> mc.screen == null && compter(mc, Items.BREAD) >= 5, 1200);

        // Achat à l'étal de Maya : ses offres sur le comptoir, puis la vitrine.
        passage(mc, "comptoir", () -> "L'étal de Maya : ses offres se lisent sur le comptoir",
                new Vec3(etal.getX() + 2.1, etal.getY() + 0.9 - 1.62, etal.getZ() + 2.4), new Vec3(etal.getX() + 0.5, etal.getY() + 0.6, etal.getZ() + 0.6),
                new Vec3(etal.getX() - 1.1, etal.getY() + 0.9 - 1.62, etal.getZ() + 2.4), new Vec3(etal.getX() + 0.5, etal.getY() + 0.6, etal.getZ() + 0.6),
                3600);
        prep("comptoir-fin", () -> survie(mc, etal.getX() + 0.5, etal.getZ() + 2.5, 180, 20), () -> enSurvie(mc, etal.getX() + 0.5, etal.getZ() + 2.5));
        prep("vitrine-devant", () -> {
            mc.gui.getChat().clearMessages(false);
            mayaVa(mc, etal.getX() + 2.5, etal.getZ() + 1.8, 110F);
            devant(mc, etal, 20);
            tenirVide(mc);
            placerCurseur(320, 300);
        }, () -> devantPret(mc, etal));
        plan("vitrine", "L'étal de Maya : clic droit, la vitrine", () -> clicDroit(mc, etal, Direction.SOUTH),
                () -> mc.screen instanceof StallShopScreen s && s.getMenu().saleAvailable(0) > 0, 1000);
        prep("avant-achat", () -> valeurAvant = valeurPortee(mc), () -> true);
        clicV("acheter", () -> valeurPortee(mc) == valeurAvant ? "« Acheter » : 4 pains pour 30 cuivres, payés d'une pièce d'or"
                : "« Acheter » : payé " + fmt(valeurAvant - valeurPortee(mc)) + ", la monnaie revient dans la bourse", () -> rangeeVitrine(mc, 0), 0,
                () -> valeurPortee(mc) == valeurAvant - 30, 1500);
        prep("vitrine-ferme", () -> fermerEcran(mc), () -> mc.screen == null);

        // La facture rédigée au carnet, payée par Maya.
        prep("facture-place", () -> {
            sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(EconomyRegistry.INVOICE_BOOK.get())));
            mayaVa(mc, o.getX() + 0.5, o.getZ() + 2.5, 0F);
            aller(mc, o.getX() + 0.5, o.getY(), o.getZ() + 5.0, 180, 5);
            mc.gui.getChat().clearMessages(false);
        }, () -> compter(mc, EconomyRegistry.INVOICE_BOOK.get()) == 1 && pres(mc, o.getX() + 0.5, o.getZ() + 5.0)
                && mayaVue(mc) != null && mayaVue(mc).distanceTo(mc.player) < 3.5);
        plan("carnet", "Le carnet de factures, utilisé sur Maya", () -> {
            tenir(mc, st -> st.is(EconomyRegistry.INVOICE_BOOK.get()));
            Player cibleJ = mayaVue(mc);
            mc.gameMode.interactAt(mc.player, cibleJ, new EntityHitResult(cibleJ), InteractionHand.MAIN_HAND);
            mc.gameMode.interact(mc.player, cibleJ, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }, () -> mc.screen instanceof InvoiceFormScreen, 500);
        plan("formulaire", "Le montant, le motif…", () -> {
            taper(mc, "80");
            mc.screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
            taper(mc, "Cinq pains au four");
            placerCurseur(200, 200);
        }, () -> true, 1200);
        clic("emettre", "« Émettre » : Maya reçoit la facture", () -> new int[] {champ(mc.screen, "left") + 10 + 47, champ(mc.screen, "top") + 108 + 8},
                0, () -> mc.screen == null, 400);
        planV("maya-paie", () -> valeurPortee(mc) == valeurAvant ? "Maya la paie…"
                : "Maya la paie : " + fmt(valeurPortee(mc) - valeurAvant) + " arrivent, la facture devient un reçu", () -> {
            valeurAvant = valeurPortee(mc);
            sur(mc, () -> {
                ServerPlayer j = maya();
                for (int i = 0; i < 36; i++) {
                    ItemStack st = j.getInventory().getItem(i);
                    if (st.is(EconomyRegistry.INVOICE.get())) {
                        maya.envoyer(new InvoicePayPacket(i, InvoiceItem.idOf(st).orElse(null)));
                        return;
                    }
                }
                LOG.warn("[film] Maya n'a pas de facture à payer");
            });
        }, () -> valeurPortee(mc) == valeurAvant + 80, 2000);
    }

    // --- 4. Le trésor et les salaires (E3) ------------------------------------------------------------

    private static void tresor(Minecraft mc) {
        String moi = mc.player.getGameProfile().getName();
        prep("maire", () -> {
            mc.gui.getChat().clearMessages(false);
            MinecraftServer s = serveur(mc);
            commande(s, "role mayor set " + moi);
            sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(EconomyRegistry.TREASURY_ITEM.get())));
            mayaVa(mc, o.getX() + 36.5, o.getZ() - 1.0, 150F);
            aller(mc, tresor().getX() + 0.5, o.getY(), tresor().getZ() + 3.5, 180, 30);
        }, () -> compter(mc, EconomyRegistry.TREASURY_ITEM.get()) == 1 && devantPretA(mc, tresor(), 3.5));
        carton("carton-4", "Scène 4 · lot E3", "Le trésor et les salaires", "La caisse de la ville, la dépense motivée, la paie de minuit", 2200);
        scene("Le trésor et les salaires");
        passage(mc, "mairie", () -> "La mairie : Elio en est le maire (nommé par l'équipe, /role mayor)", v(33.5, 2.0, 2.5), v(33.5, 1, -8),
                v(33.5, 0.6, -3.2), v(33.5, 0.8, -8), 3600);
        prep("mairie-fin", () -> survie(mc, tresor().getX() + 0.5, tresor().getZ() + 3.5, 180, 30),
                () -> enSurvie(mc, tresor().getX() + 0.5, tresor().getZ() + 3.5));
        prep("tresor-main", () -> tenir(mc, st -> st.is(EconomyRegistry.TREASURY_ITEM.get())), () -> true);
        plan("tresor-pose", "Le maire pose le trésor de la ville, à la mairie", () -> {
            tenir(mc, st -> st.is(EconomyRegistry.TREASURY_ITEM.get()));
            clicDroit(mc, tresor().below(), Direction.UP);
        }, () -> mc.level.getBlockState(tresor()).is(EconomyRegistry.TREASURY.get()), 1000);
        prep("tresor-main-vide", () -> {
            tenirVide(mc);
            placerCurseur(320, 300);
        }, () -> true);
        plan("tresor-ouvre", "Clic droit : la caisse de la ville, ouverte par le maire", () -> clicDroit(mc, tresor(), Direction.SOUTH),
                () -> mc.screen instanceof TreasuryScreen, 700);
        for (int i = 1; i <= 2; i++) {
            int n = i;
            clic("depot-" + n, "Il y dépose deux pièces d'or", () -> centreElement(mc, "row.gold.1"), 0,
                    () -> ((TreasuryScreen) mc.screen).getMenu().total() == 100L * n, n == 2 ? 700 : 150);
        }
        clic("depense", "Une dépense : clic droit, la fenêtre du motif s'ouvre", () -> centreElement(mc, "row.gold.1"), 1,
                () -> mc.screen instanceof TreasuryReasonScreen, 600);
        plan("motif", "Le motif est tapé : il ira au registre du trésor", () -> {
            taper(mc, MOTIF);
            placerCurseur(curX, curY);
        }, () -> true, 900);
        clic("confirmer", "« Confirmer » : la pièce sort du trésor, motif noté", () -> {
            Object s = mc.screen;
            int gauche = champ(s, "left");
            int haut = champ(s, "top");
            int pad = champStatique(s, "PAD");
            int boutonsY = champStatique(s, "BUTTONS_Y");
            int largeur = champStatique(s, "BUTTON_W");
            int hauteur = champStatique(s, "BUTTON_H");
            return new int[] {gauche + pad + largeur / 2, haut + boutonsY + hauteur / 2};
        }, 0, () -> mc.screen instanceof TreasuryScreen t && t.getMenu().total() == 100, 1300);
        prep("tresor-ferme", () -> fermerEcran(mc), () -> mc.screen == null);

        // Le tableau de la mairie.
        prep("tableau-prend", () -> {
            sur(mc, () -> serveur(mc).getCommands().performPrefixedCommand(moiServeur(mc).createCommandSourceStack(), "treasury board"));
            aller(mc, tableau().getX() + 0.5, o.getY(), tableau().getZ() + 3.5, 180, 25);
        }, () -> compter(mc, EconomyRegistry.TOWN_BOARD_ITEM.get()) == 1 && devantPretA(mc, tableau(), 3.5));
        plan("tableau-pose", "Le tableau de la mairie, posé devant la halle…", () -> {
            tenir(mc, st -> st.is(EconomyRegistry.TOWN_BOARD_ITEM.get()));
            clicDroit(mc, tableau().below(), Direction.UP);
        }, () -> mc.level.getBlockState(tableau()).is(EconomyRegistry.TOWN_BOARD.get())
                && mc.level.getBlockState(tableau().east().above()).is(EconomyRegistry.TOWN_BOARD.get()), 500);
        prep("tableau-regard", () -> {
            tenirVide(mc);
            aller(mc, tableau().getX() + 0.5, o.getY(), tableau().getZ() + 3.0, 180, 12);
        }, () -> devantPretA(mc, tableau(), 3.0));
        planV("tableau", () -> "… montre à tous le solde du trésor : " + fmt(TownFinancesCache.get() == null ? 0 : TownFinancesCache.get().balance()),
                () -> {}, () -> TownFinancesCache.get() != null && TownFinancesCache.get().balance() == 100, 1600);

        // Minuit : la paie de Maya, policière depuis le début du tournage.
        prep("minuit-releve", () -> sur(mc, () -> {
            MinecraftServer s = serveur(mc);
            lignesTresorAvant = TreasuryAccount.get(s).ledger().size();
            LocalDate jour = EconomyCalendar.payDay(EconomyClock.now(), EconomyCalendar.configuredZone(), EconomyConfig.payHour());
            long secondes = Payroll.get(s).secondsFor(maya().getUUID(), jour).values().stream().mapToLong(Long::longValue).max().orElse(0);
            minutesMaya = Math.round(secondes / 60.0);
            salaireMaya = -1;
            LOG.info("[film] avant minuit : Maya {} s de service ({}), registre du trésor {} lignes", secondes,
                    Payroll.get(s).secondsFor(maya().getUUID(), jour), lignesTresorAvant);
        }), () -> true);
        planV("minuit", () -> salaireMaya < 0 ? MINUIT + " : les salaires du jour sont réglés"
                : MINUIT + " : Maya, policière, payée " + fmt(salaireMaya) + " pour " + minutesMaya + " min de service",
                () -> sur(mc, () -> horlogeAvantMinuit(3)), () -> {
                    if (sonder()) sur(mc, () -> {
                        TreasuryAccount t = TreasuryAccount.get(serveur(mc));
                        long paye = 0;
                        boolean vu = false;
                        for (TreasuryLedger.Line l : lignesDepuis(t, lignesTresorAvant)) {
                            if (l.kind() == TreasuryLedger.Kind.SALARY_PAID && maya().getUUID().equals(l.actor())) {
                                paye += l.amount();
                                vu = true;
                            }
                        }
                        if (vu) {
                            salaireMaya = paye;
                        }
                    });
                    return salaireMaya > 0 && TownFinancesCache.get() != null && TownFinancesCache.get().balance() == 100 - salaireMaya;
                }, 2600);
    }

    private static List<TreasuryLedger.Line> lignesDepuis(TreasuryAccount t, int avant) {
        List<TreasuryLedger.Line> out = new ArrayList<>();
        List<TreasuryLedger.Line> toutes = new ArrayList<>();
        for (int page = 0; ; page++) {
            List<TreasuryLedger.Line> lignes = t.ledger().page(page);
            if (lignes.isEmpty()) {
                break;
            }
            toutes.addAll(lignes);
        }
        // Les pages vont de la plus récente à la plus ancienne : les nouvelles lignes sont en tête.
        int nouvelles = t.ledger().size() - avant;
        for (int i = 0; i < Math.min(nouvelles, toutes.size()); i++) {
            out.add(toutes.get(i));
        }
        return out;
    }

    private static boolean devantPretA(Minecraft mc, BlockPos bloc, double recul) {
        return pres(mc, bloc.getX() + 0.5, bloc.getZ() + recul);
    }

    private static int champStatique(Object ecran, String nom) {
        try {
            java.lang.reflect.Field f = ecran.getClass().getDeclaredField(nom);
            f.setAccessible(true);
            return f.getInt(java.lang.reflect.Modifier.isStatic(f.getModifiers()) ? null : ecran);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("champ " + nom + " illisible : " + e);
        }
    }

    // --- 5. Le butin (E4) -------------------------------------------------------------------------------

    /** Sur le thread du serveur : une vague de quatre zombies nés « dans la nature » (apparition NATURAL), figés, casqués. */
    private static void vague(MinecraftServer s) {
        ServerLevel monde = s.overworld();
        ZOMBIES.clear();
        for (int i = 0; i < 4; i++) {
            Entity e = EntityType.ZOMBIE.spawn(monde, zombie(i), MobSpawnType.NATURAL);
            if (e instanceof Zombie z) {
                preparerZombie(z);
                ZOMBIES.add(z.getUUID());
            }
        }
        vagues++;
        LOG.info("[film] vague {} : {} zombies sauvages", vagues, ZOMBIES.size());
    }

    private static void preparerZombie(Zombie z) {
        z.setBaby(false);
        if (z.getVehicle() != null) {
            z.getVehicle().discard();
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            z.setItemSlot(slot, ItemStack.EMPTY);
        }
        // Un casque de cuir : le jour, le soleil ne les brûle pas (leur mort serait sans tueur).
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        z.setDropChance(EquipmentSlot.HEAD, 0F);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.setYRot(0F);
        z.setYHeadRot(0F);
    }

    private static boolean piecesAuSol(Minecraft mc) {
        return pieceProche(mc) != null;
    }

    private static CoinDropEntity pieceProche(Minecraft mc) {
        AABB zone = new AABB(p(48, -1, 5)).expandTowards(12, 4, 9);
        CoinDropEntity proche = null;
        for (CoinDropEntity c : mc.level.getEntitiesOfClass(CoinDropEntity.class, zone)) {
            if (proche == null || c.distanceToSqr(mc.player) < proche.distanceToSqr(mc.player)) {
                proche = c;
            }
        }
        return proche;
    }

    /** Un coup d'épée sur ce zombie si l'épée est rechargée ; vrai quand il est mort. */
    private static boolean frapper(Minecraft mc, UUID id) {
        Entity e = entite(mc, id);
        if (e == null || !e.isAlive()) {
            return true;
        }
        regarder(mc, e.getEyePosition());
        if (mc.player.getAttackStrengthScale(0.5F) >= 1.0F) {
            mc.gameMode.attack(mc.player, e);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        return false;
    }

    private static void butin(Minecraft mc) {
        double px = o.getX() + 54.0;
        double pz = o.getZ() + 10.6;
        prep("butin-prep", () -> {
            mc.gui.getChat().clearMessages(false);
            MinecraftServer s = serveur(mc);
            commande(s, "difficulty easy");
            sur(mc, () -> {
                ServerPlayer j = moiServeur(mc);
                ItemStack epee = new ItemStack(Items.DIAMOND_SWORD);
                epee.enchant(Enchantments.SHARPNESS, 5);
                j.getInventory().add(epee);
                j.getInventory().add(new ItemStack(Items.ZOMBIE_SPAWN_EGG));
                j.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 1, false, false));
                vague(s);
            });
            aller(mc, px, o.getY(), pz, 180, 20);
        }, () -> compter(mc, Items.DIAMOND_SWORD) == 1 && pres(mc, px, pz) && ZOMBIES.size() == 4 && entite(mc, ZOMBIES.get(3)) != null);
        carton("carton-5", "Scène 5 · lot E4", "Le butin", "Ce que lâchent les créatures sauvages", 2200);
        scene("Le butin");
        passage(mc, "enclos", () -> "Quatre zombies nés dans la nature (apparition naturelle), figés pour le film", v(53.5, 4.5, 15.5),
                v(53.5, 0.5, 8), v(53.5, 3.0, 13.0), v(53.5, 0.8, 8), 3200);
        prep("enclos-fin", () -> survie(mc, px, pz, 180, 20), () -> enSurvie(mc, px, pz));
        plan("zombies", "Des zombies nés dans la nature : un sur trois en moyenne lâche des pièces", () -> {
            tenir(mc, st -> st.is(Items.DIAMOND_SWORD));
            valeurAvant = valeurPortee(mc);
            cible = 0;
            chasse = "";
        }, () -> true, 1400);
        planV("chasse", () -> chasse.isEmpty() ? "Le joueur les tue à l'épée…" : chasse, () -> {
            attendreVague = false;
            derniereMortNs = 0;
        }, () -> {
            long gain = valeurPortee(mc) - valeurAvant;
            CoinDropEntity piece = pieceProche(mc);
            if (piece != null) {
                // Des pièces au sol : il va les ramasser.
                regarder(mc, piece.position().add(0, 0.2, 0));
                mc.options.keyUp.setDown(true);
                return false;
            }
            mc.options.keyUp.setDown(false);
            if (gain > 0) {
                gainButin = gain;
                chasse = "Des pièces tombent, le joueur les ramasse : " + fmt(gain);
                return true;
            }
            if (attendreVague) {
                if (ZOMBIES.size() == 4 && ZOMBIES.stream().allMatch(id -> entite(mc, id) != null)) {
                    attendreVague = false;
                    cible = 0;
                }
                return false;
            }
            while (cible < ZOMBIES.size() && entite(mc, ZOMBIES.get(cible)) == null) {
                cible++;
            }
            if (cible < ZOMBIES.size()) {
                if (frapper(mc, ZOMBIES.get(cible))) {
                    derniereMortNs = System.nanoTime();
                }
                return false;
            }
            // Vague finie : les pièces d'une mort arrivent chez le client un peu après elle.
            if (System.nanoTime() - derniereMortNs < 1_200_000_000L) {
                return false;
            }
            if (vagues < 3) {
                attendreVague = true;
                sur(mc, () -> vague(serveur(mc)));
                return false;
            }
            chasse = "Douze zombies, aucune pièce : la chance n'y était pas";
            return true;
        }, 2000);
        // Il a pu avancer jusqu'à la palissade pour ramasser les pièces : il revient à sa place, l'œuf en main.
        prep("oeuf-recul", () -> {
            mc.options.keyUp.setDown(false);
            aller(mc, px, o.getY(), pz + 0.8, 180, 30);
        }, () -> pres(mc, px, pz + 0.8));
        prep("oeuf-prep", () -> {
            tenir(mc, st -> st.is(Items.ZOMBIE_SPAWN_EGG));
            regarder(mc, Vec3.atCenterOf(zombie(1)).add(0, -0.5, 0));
            porteAvantOeuf = valeurPortee(mc);
            sur(mc, () -> {
                for (Entity e : serveur(mc).overworld().getEntitiesOfClass(CoinDropEntity.class, new AABB(p(48, -1, 5)).expandTowards(12, 4, 9))) {
                    e.discard();
                }
            });
        }, () -> true);
        plan("oeuf", "Un zombie sorti d'un œuf d'apparition…", () -> {
            zombieOeuf = null;
            clicDroit(mc, zombie(1).below(), Direction.UP);
        }, () -> {
            if (zombieOeuf == null && sonder()) {
                sur(mc, () -> {
                    ServerLevel monde = serveur(mc).overworld();
                    for (Zombie z : monde.getEntitiesOfClass(Zombie.class, new AABB(zombie(1)).inflate(2.5))) {
                        if (!ZOMBIES.contains(z.getUUID()) && z.isAlive()) {
                            preparerZombie(z);
                            zombieOeuf = z.getUUID();
                        }
                    }
                });
            }
            return zombieOeuf != null && entite(mc, zombieOeuf) != null;
        }, 700);
        prep("oeuf-epee", () -> tenir(mc, st -> st.is(Items.DIAMOND_SWORD)), () -> true);
        planV("oeuf-rien", () -> entite(mc, zombieOeuf) != null ? "… tué lui aussi"
                : "… ne lâche rien : " + (valeurPortee(mc) == porteAvantOeuf && !piecesAuSol(mc) ? "aucune pièce ne tombe" : "?"),
                () -> {}, () -> {
                    if (!frapper(mc, zombieOeuf)) {
                        return false;
                    }
                    // Mort : le regard redescend là où il est tombé.
                    regarder(mc, Vec3.atBottomCenterOf(zombie(1)).add(0, 0.3, 0));
                    return System.nanoTime() - debutAction > 2_500_000_000L;
                }, 1500);
        prep("butin-fin", () -> {
            commande(serveur(mc), "difficulty peaceful");
            sur(mc, () -> {
                ServerPlayer j = moiServeur(mc);
                j.removeAllEffects();
                j.getInventory().clearOrCountMatchingItems(st -> st.is(Items.DIAMOND_SWORD) || st.is(Items.ZOMBIE_SPAWN_EGG)
                        || st.is(Items.ROTTEN_FLESH), 64 * 36, j.inventoryMenu.getCraftSlots());
            });
        }, () -> compter(mc, Items.DIAMOND_SWORD) == 0);
    }

    // --- 6. Les quêtes (E5) ---------------------------------------------------------------------------

    private static void quetes(Minecraft mc) {
        String moi = mc.player.getGameProfile().getName();
        double px = o.getX() + 50.0;
        double pz = o.getZ() - 8.3;
        prep("quetes-prep", () -> {
            mc.gui.getChat().clearMessages(false);
            commande(serveur(mc), "e2equetes imposer " + moi + " daily 0 harvest_wheat 6");
            tenirVide(mc);
            aller(mc, px, o.getY(), pz, 180, 40);
        }, () -> pres(mc, px, pz) && journaliere(0) != null && "harvest_wheat".equals(journaliere(0).ref().templateId()));
        prep("j-appui", () -> toucheJ(mc, GLFW.GLFW_PRESS), () -> System.nanoTime() - debutAction > 150_000_000L);
        carton("carton-6", "Scène 6 · lot E5", "Les quêtes", "Trois journalières, cinq hebdomadaires, payées par le serveur", 2200);
        scene("Les quêtes");
        plan("j", "Touche J : ses quêtes du jour et de la semaine", () -> {
            toucheJ(mc, GLFW.GLFW_RELEASE);
            placerCurseur(330, 330);
        }, () -> mc.screen instanceof QuestScreen q && q.layout() != null, 3000);
        prep("j-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
        for (int i = 0; i < 6; i++) {
            int k = i;
            planV("recolte-" + k, () -> {
                QuestSyncPacket.Entry e = journaliere(0);
                return "Une journalière de récolte (choisie pour le film) : elle avance au geste, "
                        + (e == null ? "?" : e.progress() + " / " + e.goal());
            }, () -> {
                regarder(mc, Vec3.atCenterOf(ble(k)));
                mc.gameMode.startDestroyBlock(ble(k), Direction.UP);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }, () -> mc.level.getBlockState(ble(k)).isAir(), k == 5 ? 1500 : 550);
        }
        prep("recolte-fin", () -> {}, () -> journaliere(0) != null && journaliere(0).progress() >= 6);
        prep("j2-appui", () -> toucheJ(mc, GLFW.GLFW_PRESS), () -> System.nanoTime() - debutAction > 150_000_000L);
        plan("j2", "Finie : « Réclamer »", () -> {
            toucheJ(mc, GLFW.GLFW_RELEASE);
            valeurAvant = valeurPortee(mc);
            placerCurseur(330, 330);
        }, () -> mc.screen instanceof QuestScreen q && q.layout() != null, 500);
        clicV("reclamer", () -> valeurPortee(mc) == valeurAvant ? "Finie : « Réclamer »"
                : "La récompense va dans la bourse : +" + fmt(valeurPortee(mc) - valeurAvant),
                () -> boutonQuete(mc, 0), 0, () -> valeurPortee(mc) > valeurAvant
                        && journaliere(0) != null && journaliere(0).state() == QuestSyncPacket.State.CLAIMED, 2000);
        prep("quetes-fin", () -> fermerEcran(mc), () -> mc.screen == null);
    }

    // --- 7. L'électricité (E6) ------------------------------------------------------------------------

    private static void electricite(Minecraft mc) {
        String moi = mc.player.getGameProfile().getName();
        double xPoste = poste().getX() + 0.5;
        prep("elec-prep", () -> {
            mc.gui.getChat().clearMessages(false);
            MinecraftServer s = serveur(mc);
            commande(s, "role set " + moi + " ingenieur");
            sur(mc, () -> {
                ServerPlayer j = moiServeur(mc);
                j.getInventory().add(new ItemStack(objet("create:copper_sheet"), 3));
                j.getInventory().add(new ItemStack(Items.REDSTONE));
                j.getInventory().add(new ItemStack(GridRegistry.METER_ITEM.get()));
                j.getInventory().add(new ItemStack(objet("create:brass_casing")));
                j.getInventory().add(new ItemStack(GridRegistry.BREAKER_ITEM.get()));
                j.getInventory().add(new ItemStack(RepairRegistry.COIL.get(), 2));
                j.getInventory().add(new ItemStack(RepairRegistry.TESTER.get()));
                LOG.info("[film] recette du poste à l'établi : {}", BenchCatalog.current().byId(RECETTE_POSTE)
                        .map(r -> r.seconds() + " s").orElse("absente"));
                // Hugo entre (somme de départ notée donnée : il n'a rien) ; Maya rentre chez elle.
                EconomyLedger.get(s).markStartingGiven(uuidDe(HUGO));
                hugo = JoueurDeTest.rejoindre(s.overworld(), HUGO, new Vec3(o.getX() + 42.5, o.getY(), o.getZ() + 15.0), false, p -> {});
                hugo.joueur().teleportTo(s.overworld(), o.getX() + 43.5, o.getY(), o.getZ() + 15.2, 160F, 0F);
                maya().teleportTo(s.overworld(), o.getX() + 41.5, o.getY(), o.getZ() + 13.4, 200F, 0F);
            });
            placerCurseur(320, 300);
            aller(mc, etabli().getX() + 0.5, o.getY(), etabli().getZ() + 2.4, 180, 30);
        }, () -> compter(mc, RepairRegistry.TESTER.get()) == 1 && devantPretA(mc, etabli(), 2.4) && joueurVu(mc, HUGO) != null);
        carton("carton-7", "Scène 7 · lot E6", "L'électricité", "Le poste, le raccordement, la facture de minuit", 2200);
        scene("L'électricité");
        plan("etabli", "L'ingénieur fabrique son poste à l'établi", () -> clicDroit(mc, etabli(), Direction.SOUTH),
                () -> mc.screen instanceof EngineerBenchScreen, 300);
        plan("recette", "L'ingénieur fabrique son poste à l'établi", () -> {
            EngineerBenchScreen e = (EngineerBenchScreen) mc.screen;
            e.selectById(RECETTE_POSTE);
            e.setQuantity(1);
        }, () -> true, 1200);
        clic("commande", "Le poste : compteur, disjoncteur, bobines, tôles de cuivre", () -> ((EngineerBenchScreen) mc.screen).hoverPoint("crafter"), 0,
                () -> !BenchClientState.queue().isEmpty(), 1000);
        prep("fabrication", () -> delaiPas = 240, () -> BenchClientState.queue().isEmpty() && BenchClientState.stockItems() >= 1);
        prep("fabrication-fin", () -> delaiPas = 45, () -> true);
        clic("stock", "Fini : le poste attend dans le stock de l'établi", () -> ((EngineerBenchScreen) mc.screen).hoverPoint("stock"), 0,
                () -> mc.screen instanceof BenchStockScreen b && b.getMenu().totalStacks() >= 1, 500);
        clic("stock-prise", "Fini : le poste attend dans le stock de l'établi", () -> ((BenchStockScreen) mc.screen).slotCenter(0), 0,
                () -> !mc.player.containerMenu.getCarried().isEmpty() || compter(mc, GridRegistry.POWER_STATION_ITEM.get()) == 1, 300);
        prep("stock-range", () -> {
            if (!mc.player.containerMenu.getCarried().isEmpty()) {
                clicMenu(mc, caseJoueur(mc, ItemStack::isEmpty), 0, net.minecraft.world.inventory.ClickType.PICKUP);
            }
        }, () -> compter(mc, GridRegistry.POWER_STATION_ITEM.get()) == 1 && mc.player.containerMenu.getCarried().isEmpty());
        prep("etabli-ferme", () -> fermerEcran(mc), () -> mc.screen == null);

        // La pose : regard à l'est, l'entrée contre l'alternateur, la sortie vers les maisons.
        prep("poste-devant", () -> aller(mc, xPoste, o.getY(), zAllee(), -90, 35), () -> pres(mc, xPoste, zAllee()));
        plan("poste-pose", "Posé contre l'alternateur : l'entrée derrière, la sortie devant", () -> {
            tenir(mc, st -> st.is(GridRegistry.POWER_STATION_ITEM.get()));
            clicDroit(mc, poste().below(), Direction.UP);
        }, () -> mc.level.getBlockState(poste()).is(GridRegistry.POWER_STATION.get()), 1300);

        // Le panneau : nom du fournisseur, puis tarifs.
        prep("panneau-devant", () -> {
            tenirVide(mc);
            aller(mc, xPoste, o.getY(), zAllee(), 180, 30);
            placerCurseur(320, 300);
        }, () -> pres(mc, xPoste, zAllee()));
        plan("panneau", "Clic droit sur le poste : son panneau", () -> clicDroit(mc, poste(), Direction.SOUTH),
                () -> panneau(mc) != null, 500);
        plan("nom", "Le nom du fournisseur…", () -> taper(mc, FOURNISSEUR), () -> true, 500);
        clic("fonder", "Le nom du fournisseur…", () -> centre(panneau(mc).layout().foundButton()), 0,
                () -> panneau(mc) != null && FOURNISSEUR.equals(panneau(mc).state().name()) && panneau(mc).layout().price() != null, 600);
        clic("prix-champ", "… et ses tarifs : le prix des 100 000 FE, l'abonnement du jour", () -> centre(panneau(mc).layout().price().box()), 0,
                () -> true, 100);
        plan("prix-tape", "… et ses tarifs : le prix des 100 000 FE, l'abonnement du jour", () -> taper(mc, Long.toString(PRIX)), () -> true, 200);
        clic("prix-ok", "… et ses tarifs : le prix des 100 000 FE, l'abonnement du jour", () -> centre(panneau(mc).layout().price().button()), 0,
                () -> panneau(mc) != null && panneau(mc).state().nextPrice() == PRIX, 200);
        clic("abo-champ", "… et ses tarifs : le prix des 100 000 FE, l'abonnement du jour", () -> centre(panneau(mc).layout().subscription().box()), 0,
                () -> true, 100);
        plan("abo-tape", "… et ses tarifs : le prix des 100 000 FE, l'abonnement du jour", () -> taper(mc, Long.toString(ABONNEMENT)), () -> true, 200);
        clic("abo-ok", "Les tarifs valent au lendemain", () -> centre(panneau(mc).layout().subscription().button()), 0,
                () -> panneau(mc) != null && panneau(mc).state().nextSubscription() == ABONNEMENT, 1500);
        // Hors champ : les tarifs du lendemain entrent en vigueur tout de suite (clôture du jour du fournisseur, comme à
        // minuit, jouée par le serveur comme l'essai de bout en bout), puis le fil suspendu et le moteur de la cabane.
        prep("tarifs", () -> {
            fermerEcran(mc);
            sur(mc, () -> {
                PowerSuppliers r = PowerSuppliers.get(serveur(mc));
                r.byFounder(mc.player.getUUID()).ifPresent(f -> LOG.info("[film] tarifs en vigueur : {}", r.closeSupplierDay(f.id(), 0L)));
            });
            commande(serveur(mc), "e2eelec reseau " + xyz(poste()) + " " + xyz(compteur(true)) + " " + xyz(compteur(false)) + " false");
        }, () -> System.nanoTime() - debutAction > 1_600_000_000L);
        prep("fils", () -> {
            commande(serveur(mc), "e2eelec reseau " + xyz(poste()) + " " + xyz(compteur(true)) + " " + xyz(compteur(false)) + " true");
            commande(serveur(mc), "setblock " + xyz(moteur()) + " create:creative_motor[facing=east]{ScrollValue:256}");
        }, () -> System.nanoTime() - debutAction > 2_000_000_000L);
        passage(mc, "reseau", () -> "Le fil suspendu de Crafts & Additions mène le courant aux maisons", v(31.5, 2.2, 16.5), v(37, 0.5, 13),
                v(36.5, 2.0, 16.8), v(39, 0.5, 13), 3500);
        prep("reseau-fin", () -> survie(mc, xPoste, zAllee(), 180, 30), () -> enSurvie(mc, xPoste, zAllee()));

        // Le raccordement de Maya : testeur sur le poste, sur son compteur, sur son disjoncteur ; puis son accord.
        int[] avant = {0};
        plan("testeur-poste", "Le testeur, posé sur le poste, retient le fournisseur…", () -> {
            mc.gui.getChat().clearMessages(false);
            avant[0] = CLES.size();
            tenir(mc, st -> st.is(RepairRegistry.TESTER.get()));
            clicDroit(mc, poste(), Direction.SOUTH);
        }, () -> cleRecue("message.oneforallsurvival.power.hold", avant[0]), 1000);
        prep("compteur-devant", () -> aller(mc, compteur(true).getX() + 0.5, o.getY(), zAllee(), 180, 30),
                () -> pres(mc, compteur(true).getX() + 0.5, zAllee()));
        plan("testeur-compteur", "… puis jumelle le compteur de Maya", () -> {
            avant[0] = CLES.size();
            clicDroit(mc, compteur(true), Direction.SOUTH);
        }, () -> cleRecue("message.oneforallsurvival.power.paired", avant[0]), 1000);
        prep("poste-retour", () -> aller(mc, xPoste, o.getY(), zAllee(), 180, 30), () -> pres(mc, xPoste, zAllee()));
        prep("poste-reprise", () -> {
            avant[0] = CLES.size();
            clicDroit(mc, poste(), Direction.SOUTH);
        }, () -> cleRecue("message.oneforallsurvival.power.hold", avant[0]));
        prep("disjoncteur-devant", () -> aller(mc, disjoncteur(true).getX() + 0.5, o.getY(), zAllee(), 180, 30),
                () -> pres(mc, disjoncteur(true).getX() + 0.5, zAllee()));
        plan("testeur-disjoncteur", "… et son disjoncteur", () -> {
            avant[0] = CLES.size();
            clicDroit(mc, disjoncteur(true), Direction.SOUTH);
        }, () -> cleRecue("message.oneforallsurvival.power.breaker_paired", avant[0]), 900);
        plan("accord-maya", "Maya reçoit la demande et donne son accord (« [Accepter] ») : personne n'est facturé sans", () -> {},
                () -> {
                    if (sonder()) sur(mc, () -> {
                        if (!accordMaya) {
                            accordMaya = accord(maya);
                        }
                    });
                    return accordMaya;
                }, 1800);

        // Hugo, le voisin : le même raccordement, hors champ.
        raccordementHorsChamp(mc, false, avant);

        // Le panneau : accepter les deux demandes ; tarifs, clients, consommation.
        prep("panneau2-devant", () -> {
            tenirVide(mc);
            aller(mc, xPoste, o.getY(), zAllee(), 180, 30);
            placerCurseur(320, 300);
        }, () -> pres(mc, xPoste, zAllee()));
        plan("panneau2", "Au panneau, les demandes à accepter", () -> clicDroit(mc, poste(), Direction.SOUTH),
                () -> panneau(mc) != null && rang(mc, MAYA) != null && rang(mc, MAYA).state() == PowerClient.State.AWAITING_ACCEPT
                        && rang(mc, HUGO) != null && rang(mc, HUGO).state() == PowerClient.State.AWAITING_ACCEPT, 900);
        clic("accepte-maya", "« Accepter » : le courant passe chez Maya et se compte", () -> boutonRang(mc, MAYA, 0), 0,
                () -> rang(mc, MAYA) != null && rang(mc, MAYA).state() == PowerClient.State.ACTIVE, 500);
        clic("accepte-hugo", "« Accepter » : le courant passe chez Maya et se compte", () -> boutonRang(mc, HUGO, 0), 0,
                () -> rang(mc, HUGO) != null && rang(mc, HUGO).state() == PowerClient.State.ACTIVE, 300);
        prep("panneau2-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
        prep("conso-attente", () -> {}, () -> System.nanoTime() - debutAction > 6_000_000_000L);
        prep("panneau3", () -> clicDroit(mc, poste(), Direction.SOUTH), () -> panneau(mc) != null && rang(mc, MAYA) != null
                && rang(mc, MAYA).feToday() > 0);
        planV("conso", () -> {
            PowerPanelPacket.Client m = rang(mc, MAYA);
            PowerPanelPacket.Client h = rang(mc, HUGO);
            return "Le panneau : tarifs, clients, consommation du jour (Maya " + (m == null ? "?" : m.feToday()) + " FE, Hugo "
                    + (h == null ? "?" : h.feToday()) + " FE)";
        }, () -> {}, () -> true, 2600);
        prep("panneau3-ferme", () -> fermerEcran(mc), () -> mc.screen == null);

        // Maya s'en va ; minuit.
        prep("maya-part-camera", () -> spectateur(mc, v(37.5, 1.4, 16.2), v(41.5, 1.0, 13.0)), () -> mc.player.isSpectator());
        planV("maya-part", () -> mayaVue(mc) == null ? "Maya se déconnecte : sa facture sera prélevée même absente"
                : "Maya se déconnecte…", () -> travelling(v(37.5, 1.4, 16.2), v(41.5, 1.0, 13.0), v(36.8, 1.4, 16.0), v(41.5, 1.0, 13.0), 3000),
                () -> {
                    if (maya != null && System.nanoTime() - debutAction > 900_000_000L) {
                        sur(mc, () -> {
                            if (maya != null) {
                                maya.quitter();
                                maya = null;
                            }
                        });
                    }
                    return travellingFini() && mayaVue(mc) == null;
                }, 300);
        prep("minuit-elec-releve", () -> sur(mc, () -> {
            factureMaya = -1;
            factureHugoDue = false;
            facturesAvant = (int) InvoiceRegistry.get(serveur(mc)).issuedBy(mc.player.getUUID()).stream().filter(f -> f.isPublic()).count();
        }), () -> true);
        prep("minuit-elec-camera", () -> survie(mc, xPoste + 0.5, zAllee(), 180, 30), () -> enSurvie(mc, xPoste + 0.5, zAllee()));
        planV("minuit-elec", () -> factureMaya < 0 ? MINUIT + " : les factures d'électricité sont émises"
                : MINUIT + " : Maya, absente, paie " + fmt(factureMaya) + " depuis son coffre-fort",
                () -> sur(mc, () -> horlogeAvantMinuit(3)), () -> {
                    if (sonder()) sur(mc, () -> {
                        MinecraftServer s = serveur(mc);
                        List<InvoiceRegistry.Invoice> f = InvoiceRegistry.get(s).issuedBy(mc.player.getUUID()).stream()
                                .filter(InvoiceRegistry.Invoice::isPublic).toList();
                        if (f.size() >= facturesAvant + 2) {
                            for (InvoiceRegistry.Invoice x : f) {
                                if (x.debtorName().equals(MAYA) && x.status() == InvoiceRegistry.Status.PAID) {
                                    factureMaya = x.amount();
                                }
                                if (x.debtorName().equals(HUGO) && x.status() == InvoiceRegistry.Status.DUE) {
                                    factureHugoDue = true;
                                }
                            }
                            caisse = PowerSuppliers.get(s).byFounder(mc.player.getUUID()).map(PowerSupplier::tillValue).orElse(-1L);
                        }
                    });
                    return factureMaya > 0 && factureHugoDue;
                }, 2400);

        // Hugo n'a pas payé : coupé au panneau.
        prep("panneau4", () -> {
            tenirVide(mc);
            placerCurseur(320, 300);
            clicDroit(mc, poste(), Direction.SOUTH);
        }, () -> panneau(mc) != null && rang(mc, HUGO) != null && rang(mc, HUGO).unpaid() > 0);
        planV("impaye", () -> "Caisse du poste : " + fmt(caisse) + " ; Hugo n'a pas payé : " + fmt(rang(mc, HUGO) == null ? 0 : rang(mc, HUGO).unpaid()) + " d'impayés",
                () -> {}, () -> true, 1600);
        clic("coupe", "« Couper » : son disjoncteur s'ouvre à distance", () -> boutonRang(mc, HUGO, 0), 0,
                () -> rang(mc, HUGO) != null && rang(mc, HUGO).cut() && !mc.level.getBlockState(disjoncteur(false)).getValue(BreakerBlock.CLOSED),
                1800);
        prep("panneau4-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
    }

    private static boolean accordMaya;
    private static boolean accordHugo;

    /** Le raccordement de la maison de Hugo, par les mêmes vrais clics du testeur, sans le filmer ; puis son accord. */
    private static void raccordementHorsChamp(Minecraft mc, boolean deMaya, int[] avant) {
        double xPoste = poste().getX() + 0.5;
        for (BlockPos bloc : new BlockPos[] {compteur(deMaya), disjoncteur(deMaya)}) {
            String cle = bloc.equals(compteur(deMaya)) ? "message.oneforallsurvival.power.paired" : "message.oneforallsurvival.power.breaker_paired";
            prep("hugo-poste-" + bloc.getX(), () -> aller(mc, xPoste, o.getY(), zAllee(), 180, 30), () -> pres(mc, xPoste, zAllee()));
            prep("hugo-retenu-" + bloc.getX(), () -> {
                avant[0] = CLES.size();
                clicDroit(mc, poste(), Direction.SOUTH);
            }, () -> cleRecue("message.oneforallsurvival.power.hold", avant[0]));
            prep("hugo-devant-" + bloc.getX(), () -> aller(mc, bloc.getX() + 0.5, o.getY(), zAllee(), 0, 30),
                    () -> pres(mc, bloc.getX() + 0.5, zAllee()));
            prep("hugo-jumele-" + bloc.getX(), () -> {
                avant[0] = CLES.size();
                clicDroit(mc, bloc, Direction.NORTH);
            }, () -> cleRecue(cle, avant[0]));
        }
        prep("hugo-accord", () -> {}, () -> {
            if (sonder()) sur(mc, () -> {
                if (!accordHugo) {
                    accordHugo = accord(hugo);
                }
            });
            return accordHugo;
        });
    }

    // --- 8. Vu par l'équipe ---------------------------------------------------------------------------

    private static void equipe(Minecraft mc) {
        prep("equipe-prep", () -> {
            mc.gui.getChat().clearMessages(false);
            tenirVide(mc);
        }, () -> true);
        carton("carton-8", "Scène 8", "Vu par l'équipe", "Tout se compte, au cuivre près", 2000);
        scene("Vu par l'équipe");
        plan("stats", "/eco stats : créé, détruit, gardé, en circulation, électricité payée", () -> {
            mc.setScreen(new ChatScreen(""));
            commandeVue(mc, "eco stats");
        }, () -> dernierChat.length() > 0, 4200);
        plan("power", "/eco power : chaque fournisseur, sa caisse, ses factures dues", () -> {
            mc.gui.getChat().clearMessages(false);
            commandeVue(mc, "eco power");
        }, () -> true, 3800);
        prep("equipe-fin", () -> {
            mc.setScreen(null);
            mc.gui.getChat().clearMessages(false);
        }, () -> mc.screen == null);
        scene("Lost Island — l'économie");
        passage(mc, "fin", () -> "", v(36, 3, 18), v(36, 1, 4), v(20, 16, 32), v(22, 1, 0), 6000);
    }

    // ================================================================================================
    // Fin
    // ================================================================================================

    private static void terminer(Minecraft mc) {
        phase = Phase.FIN;
        finCamera();
        mc.options.keyUp.setDown(false);
        int n = 0;
        if (prise != null) {
            prise.terminer(echec);
            n = prise.images();
        }
        LOG.info("[film] terminé : {} images, {}", n, echec == null ? "sans erreur" : echec);
        mc.stop();
    }
}
