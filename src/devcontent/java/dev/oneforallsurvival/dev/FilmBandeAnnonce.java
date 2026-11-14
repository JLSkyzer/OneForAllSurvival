package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.oneforallsurvival.client.title.LostIslandTitleScreen;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.PurseMenu;
import dev.oneforallsurvival.economy.ReceiptItem;
import dev.oneforallsurvival.economy.SafeMenu;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import dev.oneforallsurvival.economy.client.InvoiceFormScreen;
import dev.oneforallsurvival.economy.client.InvoiceScreen;
import dev.oneforallsurvival.economy.client.StallCashScreen;
import dev.oneforallsurvival.economy.client.StallOwnerScreen;
import dev.oneforallsurvival.economy.client.StallShopScreen;
import dev.oneforallsurvival.economy.client.TradeScreen;
import dev.oneforallsurvival.economy.invoice.InvoiceIssuePacket;
import dev.oneforallsurvival.economy.invoice.InvoiceItem;
import dev.oneforallsurvival.economy.invoice.InvoicePayPacket;
import dev.oneforallsurvival.economy.stall.MarketStallBlock;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallActionPacket;
import dev.oneforallsurvival.economy.stall.StallCashPacket;
import dev.oneforallsurvival.economy.stall.StallOffer;
import dev.oneforallsurvival.economy.stall.StallOwnerMenu;
import dev.oneforallsurvival.economy.stall.StallShopMenu;
import dev.oneforallsurvival.economy.trade.TradeActionPacket;
import dev.oneforallsurvival.economy.trade.TradeMenu;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;

/**
 * Sneak peek (aperçu) pour la communauté de Lost Island RP : chargement, titre, F3, liste des joueurs, puis la monnaie
 * et le commerce (développement seulement, jamais empaqueté). Lancement : {@code ./gradlew runDemoBandeAnnonceFilm -PsansJei}
 * (sans la liste d'objets de JEI à côté des écrans) ; le cadre de First Aid est coupé dans
 * {@code run-demo/bande-annonce/config/firstaid-client.toml} ({@code overlayMode = "OFF"}). Activée par {@code -Doneforallsurvival.demo.bandeAnnonce} (tâche {@code runDemoBandeAnnonceFilm}), inerte sinon.
 *
 * <p>Un seul lancement filme tout : l'overlay du chargement (la fenêtre anticipée s'enregistre elle-même par
 * {@code lostisland.frames}), l'écran titre et le survol de « Rejoindre l'île », puis, dans un monde neuf, une petite place
 * de marché bâtie sur un terrain plat trouvé près de l'apparition : le F3, la liste des joueurs, tout le lot E1 (pièces,
 * change, pièce au sol, coffre qui refuse, bourse, coffre-fort, somme de départ, {@code /eco}) et tout le lot E2 (échange,
 * étal, facture). Le second joueur, Maya, est une connexion simulée côté serveur ({@link JoueurDeTest}) : ses gestes passent
 * par les paquets d'un client ({@code envoyer}) ou les mêmes appels que l'essai de bout en bout.
 *
 * <p>Images : la fenêtre du jeu (1280 x 720) lue dans son tampon à 30 images par seconde, en JPEG, seulement pendant les
 * plans (rien entre deux plans : la mise en place est coupée au montage), avec les légendes et un curseur dessinés par
 * dessus. Une image tardive est recopiée pour garder la cadence. Dossier {@code plans/} du dossier du jeu, avec
 * {@code plans.csv} (première image de chaque plan) et {@code termine.json}. Le montage est fait par
 * {@code tools/bande-annonce-montage.ps1}.
 *
 * <p>Seuls les objets de la monnaie, du commerce et des objets vanilla apparaissent (inventaire vidé à l'arrivée) ;
 * le joueur n'est pas opérateur (monde sans triche) : les commandes de l'équipe sont jouées par le serveur avec le niveau
 * 4, leur réponse s'affiche chez le joueur.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FilmBandeAnnonce {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-film");
    private static final boolean ACTIF = System.getProperty("oneforallsurvival.demo.bandeAnnonce") != null;
    private static final String MONDE = "bande_annonce";
    private static final long GRAINE = Long.getLong("oneforallsurvival.demo.bandeAnnonce.graine", 7_320_514L);
    private static final String MAYA = "Maya";
    private static final double GARDE_S = 1500;
    private static final int LARGEUR = 1280;
    private static final int HAUTEUR = 720;
    private static final long CADENCE_NS = 1_000_000_000L / 30;
    private static final String CLE_REJOINDRE = "gui.oneforallsurvival.title.join";
    /** Facteur des temps de pose de chaque pas (rythme du montage), réglable sans recompiler. */
    private static final double RYTHME = Double.parseDouble(System.getProperty("oneforallsurvival.demo.bandeAnnonce.rythme", "0.85"));

    private enum Phase { CHARGEMENT, TITRE, CREATION, ATTENTE_MONDE, PREPARATION, FILM, FIN }

    /** Un pas : {@code tourne} faux = mise en place, non filmée. */
    private record Etape(String nom, boolean tourne, String titre, Supplier<String> legende, Runnable action, BooleanSupplier pret,
            long reposMs) {}

    private static final long DEBUT_NS = System.nanoTime();
    private static Phase phase = Phase.CHARGEMENT;
    private static long debutPhaseNs = System.nanoTime();
    private static boolean dansUneImage;
    private static final List<Etape> ETAPES = new ArrayList<>();
    private static int indexEtape = -1;
    private static boolean actionFaite;
    private static long debutAction;
    private static long debutRepos;
    private static String titreCourant = "";
    private static Prise prise;
    private static Prise priseChargement;
    private static String echec;
    private static volatile boolean preparationFaite;
    private static String dernierChat = "";
    private static Style lienAccepter;
    private static int sousPasTitre;

    /** Origine de la place : sol de la place, au pied du mur du fond. */
    private static BlockPos o;
    private static JoueurDeTest maya;
    private static final List<PlayerInfo> FAUX = new ArrayList<>();
    private static int origine;
    private static long valeurAvant;
    private static int compteAvant;

    // Mouvement de caméra (spectateur) et curseur virtuel.
    private static Vec3 cameraDe;
    private static Vec3 cameraA;
    private static float[] regardDe;
    private static float[] regardA;
    private static long cameraDebutNs;
    private static long cameraDureeNs;
    private static double curX = 320;
    private static double curY = 180;
    private static double curDeX;
    private static double curDeY;
    private static double curAX = 320;
    private static double curAY = 180;
    private static long curDebutNs;
    private static long curDureeNs = 1;
    private static boolean tabTenue;
    private static boolean casserTenu;
    private static BlockPos aCasser;

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

    private FilmBandeAnnonce() {}

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
            if (tabTenue) {
                mc.options.keyPlayerList.setDown(true);
            }
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
            if (phase != Phase.CHARGEMENT && phase != Phase.TITRE) {
                mc.getToasts().clear();
            }
            avancer(mc);
            if (phase == Phase.FILM && indexEtape >= 0 && indexEtape < ETAPES.size() && ETAPES.get(indexEtape).tourne()) {
                Etape e = ETAPES.get(indexEtape);
                String legende;
                try {
                    legende = e.legende().get();
                } catch (RuntimeException ex) {
                    legende = "";
                }
                prise.capturer(mc, e.titre(), legende, curseurVisible(mc) ? new double[] {curX, curY} : null,
                        mc.options.renderDebug || tabTenue);
            } else if (prise != null && phase != Phase.TITRE) {
                prise.couper();
            }
        } catch (RuntimeException | IOException e) {
            LOG.error("[film] échec en phase {} (étape {})", phase, indexEtape >= 0 && indexEtape < ETAPES.size()
                    ? ETAPES.get(indexEtape).nom() : "-", e);
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
        if (casserTenu && aCasser != null && mc.gameMode != null && mc.player != null) {
            mc.gameMode.continueDestroyBlock(aCasser, Direction.SOUTH);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (ACTIF && event.phase == TickEvent.Phase.END && maya != null && maya.connecte()) {
            maya.lire();
            maya.messagesRecus();
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (!ACTIF) {
            return;
        }
        dernierChat = event.getMessage().getString();
        LOG.info("[film] message : {}", dernierChat);
        event.getMessage().visit((style, texte) -> {
            ClickEvent clic = style.getClickEvent();
            if (clic != null && clic.getAction() == ClickEvent.Action.RUN_COMMAND && clic.getValue().startsWith("/trade accept ")) {
                lienAccepter = style;
            }
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static void avancer(Minecraft mc) throws IOException {
        long maintenant = System.nanoTime();
        double s = (maintenant - debutPhaseNs) / 1e9;
        switch (phase) {
            case CHARGEMENT -> {
                if (priseChargement == null) {
                    priseChargement = new Prise(mc.gameDirectory.toPath().resolve("plans/chargement-jeu"), false);
                    LOG.info("[film] overlay du chargement filmé");
                }
                if (mc.getOverlay() != null) {
                    priseChargement.capturer(mc, "", "", null, false);
                } else if (mc.screen instanceof AccessibilityOnboardingScreen) {
                    mc.tell(() -> mc.setScreen(new TitleScreen()));
                } else if (mc.screen instanceof LostIslandTitleScreen) {
                    priseChargement.terminer(null);
                    reglerLesOptions(mc);
                    prise = new Prise(mc.gameDirectory.toPath().resolve("plans/film"), true);
                    prise.plan("titre", "");
                    passer(Phase.TITRE);
                } else if (s > 900) {
                    throw new IllegalStateException("titre jamais atteint, écran " + mc.screen);
                }
            }
            case TITRE -> titre(mc, s);
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
                        && mc.level.getBlockState(p(-9, 0, -5)).is(Blocks.CRAFTING_TABLE) && s > 10) {
                    mc.gui.getChat().clearMessages(false);
                    construireScenario(mc);
                    passer(Phase.FILM);
                    LOG.info("[film] scénario prêt : {} étapes, place en {}", ETAPES.size(), o);
                } else if (s > 240) {
                    throw new IllegalStateException("place jamais prête (preparation " + preparationFaite + ", joueur " + mc.player.blockPosition()
                            + ")");
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

    /** L'écran titre filmé : 2 s seul, le curseur glisse sur « Rejoindre l'île », 3 s de survol, puis le monde. */
    private static void titre(Minecraft mc, double s) {
        if (!(mc.screen instanceof LostIslandTitleScreen)) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.tell(() -> mc.setScreen(new TitleScreen()));
            }
            return;
        }
        Optional<AbstractWidget> rejoindre = widget(mc.screen, CLE_REJOINDRE);
        if (sousPasTitre == 0) {
            curX = mc.getWindow().getGuiScaledWidth() * 0.82;
            curY = mc.getWindow().getGuiScaledHeight() * 0.80;
            curAX = curX;
            curAY = curY;
            sousPasTitre = 1;
        } else if (sousPasTitre == 1 && s > 2.5 && rejoindre.isPresent()) {
            AbstractWidget w = rejoindre.get();
            viser(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, 900);
            sousPasTitre = 2;
        } else if (sousPasTitre == 2 && s > 7.0) {
            sousPasTitre = 3;
            passer(Phase.CREATION);
            return;
        }
        prise.capturer(mc, "", "", sousPasTitre >= 2 ? new double[] {curX, curY} : null, false);
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
        Path monde = mc.gameDirectory.toPath().resolve("saves").resolve(MONDE);
        if (Files.isDirectory(monde)) {
            try (Stream<Path> tout = Files.walk(monde)) {
                for (Path f : tout.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(f);
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
        // Sans triche : le joueur n'est pas de l'équipe (l'étal et le coffre-fort d'un autre lui résistent).
        LevelSettings reglages = new LevelSettings(MONDE, GameType.SURVIVAL, false, Difficulty.PEACEFUL, false, regles,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(GRAINE, true, false),
                acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value()
                        .createWorldDimensions());
    }

    // ================================================================================================
    // Le décor : une place de marché sur un terrain plat
    // ================================================================================================

    private static BlockPos p(int dx, int dy, int dz) {
        return o.offset(dx, dy, dz);
    }

    private static BlockPos table() {
        return p(-9, 0, -5);
    }

    private static BlockPos coffre() {
        return p(-7, 0, -5);
    }

    private static BlockPos tremieSol() {
        return p(-2, 0, 3);
    }

    private static BlockPos etalMaya() {
        return p(-3, 1, -5);
    }

    private static BlockPos etalMoi() {
        return p(3, 0, -5);
    }

    private static BlockPos coffreFortMoi() {
        return p(8, 0, -5);
    }

    private static BlockPos coffreFortMaya() {
        return p(11, 0, -5);
    }

    /** Sur le thread du serveur : site, décor, règles, joueur. */
    private static void preparer(MinecraftServer serveur) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer moi = serveur.getPlayerList().getPlayers().get(0);
        BlockPos site = chercherSite(monde, moi.blockPosition());
        o = site;
        LOG.info("[film] place en {}", o);
        batir(monde);
        monde.setDayTime(2600);
        monde.setWeatherParameters(1_000_000, 0, false, false);
        commande(serveur, "gamerule doDaylightCycle false");
        commande(serveur, "gamerule doMobSpawning false");
        commande(serveur, "gamerule doInsomnia false");
        commande(serveur, "gamerule doImmediateRespawn true");
        // Les animaux de la place sont retirés sans rien lâcher (un /kill laisserait laine et viande, ramassées ensuite).
        for (net.minecraft.world.entity.Entity e : monde.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(o).inflate(48), e -> !(e instanceof Player))) {
            e.discard();
        }
        // Ce que le joueur porte : seulement sa somme de départ (reçue à l'entrée) et des objets vanilla de marché.
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
        moi.getInventory().setItem(20, new ItemStack(Items.BREAD, 16));
        moi.getInventory().setItem(21, new ItemStack(Items.CARROT, 4));
        moi.getInventory().setItem(22, new ItemStack(Items.WHEAT, 8));
        moi.teleportTo(monde, o.getX() + 2.5, o.getY(), o.getZ() + 2.5, 180F, 10F);
        moi.setGameMode(GameType.SURVIVAL);
        preparationFaite = true;
    }

    /** Le terrain le plus plat, sec et au-dessus de la mer, dans un rayon de 384 blocs (hauteurs du générateur, sans charger). */
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
                for (int ix = -20; ix <= 24; ix += 11) {
                    for (int iz = -14; iz <= 18; iz += 8) {
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

    private static void batir(ServerLevel monde) {
        BlockState air = Blocks.AIR.defaultBlockState();
        // Terrain : un plateau d'herbe, sans rien au-dessus.
        remplir(monde, -20, 0, -16, 24, 26, 20, air);
        remplir(monde, -20, -5, -16, 24, -2, 20, Blocks.DIRT.defaultBlockState());
        remplir(monde, -20, -1, -16, 24, -1, 20, Blocks.GRASS_BLOCK.defaultBlockState());
        // Le sol de la place : chemin de terre, avec de la terre brute et des pavés.
        Random r = new Random(17);
        for (int x = -11; x <= 15; x++) {
            for (int z = -6; z <= 9; z++) {
                int t = r.nextInt(20);
                Block b = t < 16 ? Blocks.DIRT_PATH : t < 19 ? Blocks.COARSE_DIRT : Blocks.GRAVEL;
                poser(monde, p(x, -1, z), b.defaultBlockState());
            }
        }
        // Le mur du fond (z = -6), en planches de sapin, poteaux de chêne, auvent de dalles.
        remplir(monde, -11, 0, -6, 15, 3, -6, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = -11; x <= 15; x += 4) {
            remplir(monde, x, 0, -6, x, 4, -6, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        }
        for (int x = -9; x <= 13; x += 4) {
            poser(monde, p(x, 2, -6), Blocks.GLASS_PANE.defaultBlockState());
        }
        remplir(monde, -11, 4, -6, 15, 4, -6, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        remplir(monde, -11, 4, -5, 15, 4, -4, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        for (int x = -6; x <= 13; x += 6) {
            poser(monde, p(x, 3, -4), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        // Le coin de l'artisan : table, coffre, tonneaux.
        poser(monde, table(), Blocks.CRAFTING_TABLE.defaultBlockState());
        poser(monde, coffre(), Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
        poser(monde, p(-11, 0, -5), Blocks.BARREL.defaultBlockState());
        poser(monde, p(-10, 0, -5), Blocks.BARREL.defaultBlockState());
        poser(monde, p(-10, 1, -5), Blocks.BARREL.defaultBlockState());
        // Le support de l'étal de Maya : une trémie qui tire vers le bas, sous l'étal.
        poser(monde, etalMaya().below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        // La trémie de la pièce au sol, au milieu de la place.
        poser(monde, tremieSol(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        // Le pourtour : poteaux à lanterne, foin, tonneaux, fleurs.
        for (int[] c : new int[][] {{-11, 9}, {15, 9}, {-11, -2}, {15, -2}}) {
            poser(monde, p(c[0], 0, c[1]), Blocks.OAK_FENCE.defaultBlockState());
            poser(monde, p(c[0], 1, c[1]), Blocks.OAK_FENCE.defaultBlockState());
            poser(monde, p(c[0], 2, c[1]), Blocks.LANTERN.defaultBlockState());
        }
        poser(monde, p(-10, 0, 6), Blocks.HAY_BLOCK.defaultBlockState());
        poser(monde, p(-10, 0, 7), Blocks.HAY_BLOCK.defaultBlockState());
        poser(monde, p(-9, 0, 7), Blocks.HAY_BLOCK.defaultBlockState());
        poser(monde, p(-10, 1, 7), Blocks.HAY_BLOCK.defaultBlockState());
        poser(monde, p(14, 0, 6), Blocks.BARREL.defaultBlockState());
        poser(monde, p(14, 0, 7), Blocks.COMPOSTER.defaultBlockState());
        poser(monde, p(13, 0, 7), Blocks.POTTED_POPPY.defaultBlockState());
        poser(monde, p(-11, 0, 3), Blocks.POTTED_DANDELION.defaultBlockState());
        poser(monde, p(15, 0, 3), Blocks.POTTED_BLUE_ORCHID.defaultBlockState());
        for (int x = -12; x <= 16; x += 3) {
            poser(monde, p(x, 0, 11), Blocks.ROSE_BUSH.defaultBlockState());
            poser(monde, p(x, 1, 11), Blocks.ROSE_BUSH.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }
        // Quelques arbres autour (fonctionnalités de génération vanilla).
        MinecraftServer serveur = monde.getServer();
        for (int[] c : new int[][] {{-17, 14}, {20, 14}, {-17, -11}, {21, -12}, {4, 17}}) {
            commande(serveur, String.format(Locale.ROOT, "place feature minecraft:fancy_oak %d %d %d", o.getX() + c[0], o.getY(),
                    o.getZ() + c[1]));
        }
    }

    // ================================================================================================
    // Outils
    // ================================================================================================

    private static MinecraftServer serveur(Minecraft mc) {
        return mc.getSingleplayerServer();
    }

    private static ServerPlayer moiServeur(Minecraft mc) {
        return serveur(mc).getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void sur(Minecraft mc, Runnable travail) {
        serveur(mc).execute(travail);
    }

    /** Une commande jouée par le serveur au niveau 4 (la console de l'équipe). */
    private static void commande(MinecraftServer serveur, String ligne) {
        serveur.execute(() -> serveur.getCommands().performPrefixedCommand(serveur.createCommandSourceStack().withSuppressedOutput(), ligne));
    }

    /** Une commande de l'équipe jouée au nom du joueur : la réponse s'affiche dans son chat. */
    private static void commandeVue(Minecraft mc, String ligne) {
        sur(mc, () -> serveur(mc).getCommands().performPrefixedCommand(moiServeur(mc).createCommandSourceStack().withPermission(4), ligne));
    }

    private static void aller(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        sur(mc, () -> moiServeur(mc).teleportTo(serveur(mc).overworld(), x, y, z, yaw, pitch));
    }

    /** Le joueur deux blocs devant le bloc (au sud), regard au nord vers lui. */
    private static void devant(Minecraft mc, BlockPos bloc, float pitch) {
        aller(mc, bloc.getX() + 0.5, o.getY(), bloc.getZ() + 2.5, 180, pitch);
    }

    private static boolean pres(Minecraft mc, double x, double z) {
        return Math.abs(mc.player.getX() - x) < 0.6 && Math.abs(mc.player.getZ() - z) < 0.6;
    }

    private static boolean devantPret(Minecraft mc, BlockPos bloc) {
        return pres(mc, bloc.getX() + 0.5, bloc.getZ() + 2.5);
    }

    private static void clicDroit(Minecraft mc, BlockPos pos, Direction face) {
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        mc.player.swing(InteractionHand.MAIN_HAND);
        LOG.info("[film] clic droit en {} : {}", pos, res);
    }

    private static void tenirVide(Minecraft mc) {
        for (int i = 8; i >= 0; i--) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        // Barre pleine : la case 8 part dans une case libre du sac.
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, 8, ClickType.SWAP, mc.player);
                mc.player.getInventory().selected = 8;
                return;
            }
        }
        throw new IllegalStateException("inventaire plein");
    }

    /** L'objet en main ; pris dans le sac (échange avec une case vide de la barre) s'il n'est pas dans la barre. */
    private static void tenir(Minecraft mc, Predicate<ItemStack> voulu) {
        for (int i = 0; i < 9; i++) {
            if (voulu.test(mc.player.getInventory().getItem(i))) {
                mc.player.getInventory().selected = i;
                return;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (voulu.test(mc.player.getInventory().getItem(i))) {
                for (int h = 8; h >= 0; h--) {
                    if (mc.player.getInventory().getItem(h).isEmpty()) {
                        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, h, ClickType.SWAP, mc.player);
                        mc.player.getInventory().selected = h;
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("objet absent de l'inventaire");
    }

    private static void clicMenu(Minecraft mc, int index, int bouton, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, index, bouton, type, mc.player);
    }

    private static int caseJoueur(Minecraft mc, Predicate<ItemStack> voulu) {
        for (Slot s : mc.player.containerMenu.slots) {
            if (s.container == mc.player.getInventory() && s.getContainerSlot() < 36 && voulu.test(s.getItem())) {
                return s.index;
            }
        }
        return -1;
    }

    /** La case du menu qui porte l'objet, sinon la première case vide de l'inventaire. */
    private static int caseOuVide(Minecraft mc, Predicate<ItemStack> voulu) {
        int i = caseJoueur(mc, voulu);
        return i >= 0 ? i : caseJoueur(mc, ItemStack::isEmpty);
    }

    /** Les pièces en vrac dans l'inventaire (hors bourses). */
    private static long libre(Minecraft mc) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s);
        }
        return n;
    }

    private static long valeurPortee(Minecraft mc) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += CoinMath.valueOf(s) + Purse.total(s);
        }
        return n;
    }

    private static long bourse(Minecraft mc) {
        long n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            n += Purse.total(s);
        }
        return n;
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

    private static String fmt(long cuivres) {
        return CoinMath.format(cuivres).getString();
    }

    private static boolean menuOuvert(Minecraft mc, Class<?> menu) {
        return mc.screen instanceof AbstractContainerScreen<?> s && menu.isInstance(s.getMenu());
    }

    private static int[] origineEcran(Minecraft mc) {
        AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.screen;
        return new int[] {s.getGuiLeft(), s.getGuiTop()};
    }

    private static int champ(Object ecran, String nom) {
        try {
            Field f = ecran.getClass().getDeclaredField(nom);
            f.setAccessible(true);
            return f.getInt(ecran);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("champ " + nom + " illisible : " + e);
        }
    }

    private static void taper(Minecraft mc, String texte) {
        for (char c : texte.toCharArray()) {
            mc.screen.charTyped(c, 0);
        }
    }

    private static void effacerChamp(Minecraft mc) {
        mc.screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        for (int i = 0; i < 6; i++) {
            mc.screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        }
    }

    private static Player mayaVue(Minecraft mc) {
        for (Player j : mc.level.players()) {
            if (MAYA.equals(j.getGameProfile().getName())) {
                return j;
            }
        }
        return null;
    }

    private static Optional<AbstractWidget> widget(Screen ecran, String cle) {
        for (GuiEventListener l : ecran.children()) {
            if (l instanceof AbstractWidget w && w.visible && w.getMessage().getContents() instanceof TranslatableContents t
                    && cle.equals(t.getKey())) {
                return Optional.of(w);
            }
        }
        return Optional.empty();
    }

    // --- curseur virtuel ----------------------------------------------------------------------------

    /** Le curseur glisse vers ce point (coordonnées d'interface) en {@code ms}. */
    private static void viser(double x, double y, long ms) {
        curDeX = curX;
        curDeY = curY;
        curAX = x;
        curAY = y;
        curDebutNs = System.nanoTime();
        curDureeNs = Math.max(1, ms) * 1_000_000L;
    }

    private static boolean arrive() {
        return System.nanoTime() - curDebutNs >= curDureeNs;
    }

    private static boolean curseurVisible(Minecraft mc) {
        return mc.screen != null && !(mc.screen instanceof ChatScreen);
    }

    /** Chaque image : la position du curseur, donnée au jeu par le rappel de GLFW (le vrai curseur ne bouge pas). */
    private static void appliquerCurseur(Minecraft mc) {
        double t = Math.min(1.0, (System.nanoTime() - curDebutNs) / (double) curDureeNs);
        double e = t * t * (3 - 2 * t);
        curX = curDeX + (curAX - curDeX) * e;
        curY = curDeY + (curAY - curDeY) * e;
        if (mc.screen == null) {
            return;
        }
        var f = mc.getWindow();
        long fenetre = f.getWindow();
        GLFWCursorPosCallback rappel = GLFW.glfwSetCursorPosCallback(fenetre, null);
        GLFW.glfwSetCursorPosCallback(fenetre, rappel);
        if (rappel != null) {
            rappel.invoke(fenetre, curX * f.getScreenWidth() / f.getGuiScaledWidth(), curY * f.getScreenHeight() / f.getGuiScaledHeight());
        }
    }

    /** Un vrai clic de souris sur l'écran ouvert, là où est le curseur. */
    private static void cliquer(Minecraft mc, int bouton) {
        String ecran = mc.screen.getClass().getSimpleName();
        boolean pris = mc.screen.mouseClicked(curX, curY, bouton);
        // Un bouton comme « Émettre » ferme l'écran pendant le clic.
        if (mc.screen != null) {
            mc.screen.mouseReleased(curX, curY, bouton);
        }
        LOG.info("[film] clic {} en ({}, {}) sur {} : {}", bouton, Math.round(curX), Math.round(curY), ecran, pris);
    }

    private static int[] centreCase(Minecraft mc, int index) {
        int[] g = origineEcran(mc);
        Slot s = mc.player.containerMenu.getSlot(index);
        return new int[] {g[0] + s.x + 8, g[1] + s.y + 8};
    }

    private static int[] centreElement(Minecraft mc, String id) {
        Optional<int[]> c;
        if (mc.screen instanceof CoinAccountScreen<?> s) {
            c = s.elementCenter(id);
        } else if (mc.screen instanceof TradeScreen s) {
            c = s.elementCenter(id);
        } else if (mc.screen instanceof StallCashScreen s) {
            c = s.elementCenter(id);
        } else {
            c = Optional.empty();
        }
        return c.orElseThrow(() -> new IllegalStateException("aucun élément " + id + " sur " + mc.screen));
    }

    // --- caméra (spectateur) ------------------------------------------------------------------------

    private static float[] regard(Vec3 de, Vec3 vers) {
        double dx = vers.x - de.x;
        double dy = vers.y - de.y;
        double dz = vers.z - de.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new float[] {yaw, pitch};
    }

    /** Un travelling de {@code de} à {@code a} en regardant {@code cible} puis {@code cible2}, en {@code ms}. */
    private static void travelling(Vec3 de, Vec3 cible, Vec3 a, Vec3 cible2, long ms) {
        cameraDe = de;
        cameraA = a;
        // Le regard part de l'œil, 1,62 au-dessus de la position donnée (les pieds).
        regardDe = regard(de.add(0, 1.62, 0), cible);
        regardA = regard(a.add(0, 1.62, 0), cible2);
        float d = regardA[0] - regardDe[0];
        while (d > 180) {
            d -= 360;
        }
        while (d < -180) {
            d += 360;
        }
        regardA[0] = regardDe[0] + d;
        cameraDebutNs = System.nanoTime();
        cameraDureeNs = ms * 1_000_000L;
    }

    private static boolean travellingFini() {
        return cameraDe == null || System.nanoTime() - cameraDebutNs >= cameraDureeNs;
    }

    private static void appliquerCamera(Minecraft mc) {
        if (cameraDe == null || mc.player == null) {
            return;
        }
        double t = Math.min(1.0, (System.nanoTime() - cameraDebutNs) / (double) cameraDureeNs);
        double e = t * t * (3 - 2 * t);
        Vec3 pos = cameraDe.add(cameraA.subtract(cameraDe).scale(e));
        float yaw = (float) (regardDe[0] + (regardA[0] - regardDe[0]) * e);
        float pitch = (float) (regardDe[1] + (regardA[1] - regardDe[1]) * e);
        Player j = mc.player;
        j.setPos(pos.x, pos.y, pos.z);
        j.xo = pos.x;
        j.yo = pos.y;
        j.zo = pos.z;
        j.xOld = pos.x;
        j.yOld = pos.y;
        j.zOld = pos.z;
        j.setDeltaMovement(Vec3.ZERO);
        j.setYRot(yaw);
        j.yRotO = yaw;
        j.setXRot(pitch);
        j.xRotO = pitch;
        j.setYHeadRot(yaw);
    }

    private static void finCamera() {
        cameraDe = null;
    }

    private static Vec3 v(double dx, double dy, double dz) {
        return new Vec3(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
    }

    // --- Maya ---------------------------------------------------------------------------------------

    private static ServerPlayer maya() {
        return maya.joueur();
    }

    /** Sur le thread du serveur : Maya entre (ou revient) à cette place, regard vers le joueur au sud. */
    private static void mayaEntre(MinecraftServer serveur, double x, double z, float yaw) {
        ServerLevel monde = serveur.overworld();
        maya = JoueurDeTest.rejoindre(monde, MAYA, new Vec3(x, o.getY(), z), false, j -> j.setYRot(yaw));
        maya().teleportTo(monde, x, o.getY(), z, yaw, 0F);
        maya().setYHeadRot(yaw);
    }

    private static void mayaVa(Minecraft mc, double x, double z, float yaw) {
        sur(mc, () -> {
            maya().teleportTo(serveur(mc).overworld(), x, o.getY(), z, yaw, 0F);
            maya().setYHeadRot(yaw);
        });
    }

    private static boolean mayaEnMainVide(ServerPlayer j) {
        for (int i = 0; i < 9; i++) {
            if (j.getInventory().getItem(i).isEmpty()) {
                j.getInventory().selected = i;
                return true;
            }
        }
        return false;
    }

    private static int caseMaya(ServerPlayer j, Item objet) {
        for (int i = 0; i < 36; i++) {
            if (j.getInventory().getItem(i).is(objet)) {
                return i;
            }
        }
        return -1;
    }

    /** Maya pose un objet de bloc sur le sol sous {@code pos} (clic droit sur la face du dessus). */
    private static void mayaPoseBloc(MinecraftServer serveur, BlockPos pos, Item objet) {
        ServerLevel monde = serveur.overworld();
        ServerPlayer j = maya();
        j.teleportTo(monde, pos.getX() + 0.5, o.getY(), pos.getZ() + 2.5, 180F, 30F);
        mayaEnMainVide(j);
        j.getInventory().setItem(j.getInventory().selected, new ItemStack(objet));
        BlockPos sol = pos.below();
        // Accroupie : un clic sur un bloc qui s'ouvre (la trémie sous son étal) pose l'objet au lieu d'ouvrir le bloc.
        j.setShiftKeyDown(true);
        var r = j.gameMode.useItemOn(j, monde, j.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false));
        j.setShiftKeyDown(false);
        j.swing(InteractionHand.MAIN_HAND, true);
        LOG.info("[film] Maya pose {} en {} : {}", objet, pos, r);
    }

    /** Maya pose {@code n} objets dans sa moitié de la fenêtre d'échange (clics du menu, comme un client). */
    private static void mayaPoseDansEchange(Item objet, int n) {
        ServerPlayer j = maya();
        if (!(j.containerMenu instanceof TradeMenu menu)) {
            throw new IllegalStateException("Maya n'a pas la fenêtre d'échange");
        }
        int depuis = caseMaya(j, objet);
        int cible = -1;
        for (int s = TradeMenu.OWN_START; s < TradeMenu.OWN_START + TradeMenu.OFFER_SIZE && cible < 0; s++) {
            ItemStack dans = menu.getSlot(s).getItem();
            if (dans.isEmpty() || dans.is(objet)) {
                cible = s;
            }
        }
        int menuDepuis = TradeMenu.slotOfInventory(depuis);
        menu.clicked(menuDepuis, 0, ClickType.PICKUP, j);
        for (int i = 0; i < n; i++) {
            menu.clicked(cible, 1, ClickType.PICKUP, j);
        }
        menu.clicked(menuDepuis, 0, ClickType.PICKUP, j);
        menu.broadcastChanges();
    }

    private static int caseGestion(int inventaire) {
        return StallOwnerMenu.INVENTORY_START + (inventaire < 9 ? 27 + inventaire : inventaire - 9);
    }

    /**
     * Maya garnit son étal par les gestes de la gestion : trois offres de vente (le modèle tenu au curseur), un rachat de blé,
     * le stock rangé (majuscule-clic), puis 1 or en caisse.
     */
    private static void mayaGarnit(BlockPos pos) {
        ServerPlayer j = maya();
        if (!MarketStallBlock.openManage(j, pos) || !(j.containerMenu instanceof StallOwnerMenu menu)) {
            throw new IllegalStateException("la gestion ne s'ouvre pas pour Maya");
        }
        Object[][] offres = {{Items.BREAD, 4, 30L}, {Items.APPLE, 3, 12L}, {Items.COOKED_COD, 2, 15L}};
        for (int i = 0; i < offres.length; i++) {
            int c = caseGestion(caseMaya(j, (Item) offres[i][0]));
            menu.clicked(c, 0, ClickType.PICKUP, j);
            LOG.info("[film] offre de Maya {} : {}", i,
                    StallActionPacket.apply(j, StallActionPacket.setOffer(pos, i, (Integer) offres[i][1], (Long) offres[i][2])));
            menu.clicked(c, 0, ClickType.PICKUP, j);
        }
        int ble = caseGestion(caseMaya(j, Items.WHEAT));
        menu.clicked(ble, 0, ClickType.PICKUP, j);
        LOG.info("[film] rachat de Maya : {}", StallActionPacket.apply(j, StallActionPacket.setBuyback(pos, 0, 8, 16L, 5)));
        menu.clicked(ble, 0, ClickType.PICKUP, j);
        for (Item it : List.of(Items.BREAD, Items.APPLE, Items.COOKED_COD)) {
            menu.clicked(caseGestion(caseMaya(j, it)), 0, ClickType.QUICK_MOVE, j);
        }
        menu.broadcastChanges();
        j.closeContainer();
        if (MarketStallBlock.openCash(j, pos)) {
            LOG.info("[film] caisse de Maya : {}", StallCashPacket.apply(j, StallCashPacket.deposit(pos, Coin.GOLD, 1)));
            j.closeContainer();
        }
    }

    // --- liste des joueurs : des joueurs factices côté client (comme TabShots) -----------------------

    @SuppressWarnings("unchecked")
    private static void installerFaux(Minecraft mc, Object[][] faux) {
        ClientPacketListener connexion = mc.player.connection;
        Map<UUID, PlayerInfo> carte = (Map<UUID, PlayerInfo>) lireChamp(connexion, "playerInfoMap");
        Set<PlayerInfo> listes = (Set<PlayerInfo>) lireChamp(connexion, "listedPlayers");
        for (Object[] f : faux) {
            String nom = (String) f[0];
            UUID id = UUID.nameUUIDFromBytes(("bande-annonce:" + nom).getBytes(StandardCharsets.UTF_8));
            PlayerInfo info = new PlayerInfo(new GameProfile(id, nom), false);
            appeler(info, "setLatency", int.class, (Integer) f[2]);
            appeler(info, "setGameMode", GameType.class, GameType.SURVIVAL);
            carte.put(id, info);
            listes.add(info);
            FAUX.add(info);
        }
    }

    @SuppressWarnings("unchecked")
    private static void retirerFaux(Minecraft mc) {
        if (FAUX.isEmpty() || mc.player == null) {
            return;
        }
        ClientPacketListener connexion = mc.player.connection;
        Map<UUID, PlayerInfo> carte = (Map<UUID, PlayerInfo>) lireChamp(connexion, "playerInfoMap");
        Set<PlayerInfo> listes = (Set<PlayerInfo>) lireChamp(connexion, "listedPlayers");
        for (PlayerInfo info : FAUX) {
            carte.remove(info.getProfile().getId());
            listes.remove(info);
        }
        FAUX.clear();
    }

    private static Object lireChamp(Object objet, String nom) {
        try {
            Field f = objet.getClass().getDeclaredField(nom);
            f.setAccessible(true);
            return f.get(objet);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> void appeler(Object objet, String nom, Class<T> type, T valeur) {
        try {
            Method m = objet.getClass().getDeclaredMethod(nom, type);
            m.setAccessible(true);
            m.invoke(objet, valeur);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Les autres joueurs de la liste : pseudo, rôle (équipe {@code ofas_<rôle>}), ping. Métiers du quotidien seulement. */
    private static final Object[][] FAUX_JOUEURS = {
            {"Naufrage_Tom", "citoyen", 47}, {"Nina_Potager", "agriculteur", 64}, {"Paysan_Lou", "agriculteur", 97},
            {"Docteur_Ash", "medecin", 38}, {"Capitaine_Rex", "policier", 23}, {"Pecheur_Noe", "pecheur", 61},
            {"Mia_Survie", "citoyen", 31}, {"Sable_Noir", "citoyen", 212}, {"Jade_Lagon", "pecheur", 88},
            {"Hugo_Radeau", "citoyen", 23}, {"Lila_RP", "medecin", 76}, {"Coco_Gab", "citoyen", 110}};

    // ================================================================================================
    // Le scénario
    // ================================================================================================

    private static void scene(String titre) {
        titreCourant = titre;
    }

    private static void plan(String nom, String legende, Runnable action, BooleanSupplier pret, long reposMs) {
        ETAPES.add(new Etape(nom, true, titreCourant, () -> legende, action, pret, reposMs));
    }

    private static void planV(String nom, Supplier<String> legende, Runnable action, BooleanSupplier pret, long reposMs) {
        ETAPES.add(new Etape(nom, true, titreCourant, legende, action, pret, reposMs));
    }

    private static void prep(String nom, Runnable action, BooleanSupplier pret) {
        ETAPES.add(new Etape(nom, false, "", () -> "", action, pret, 400));
    }

    /** Deux pas : le curseur glisse vers la cible, puis le clic (bouton 0 ou 1). */
    private static void clic(String nom, String legende, Supplier<int[]> cible, int bouton, BooleanSupplier pret, long reposMs) {
        plan(nom + "-vise", legende, () -> {
            int[] c = cible.get();
            viser(c[0], c[1], 450);
        }, FilmBandeAnnonce::arrive, 150);
        // Après le clic, le curseur s'écarte un peu : l'infobulle du bouton (souvent « indisponible » une fois fait) disparaît.
        plan(nom, legende, () -> {
            cliquer(Minecraft.getInstance(), bouton);
            viser(curAX + 26, curAY + 34, 350);
        }, pret, reposMs);
    }

    /** Survol d'un point (infobulle), sans clic. */
    private static void survol(String nom, String legende, Supplier<int[]> cible, long reposMs) {
        plan(nom, legende, () -> {
            int[] c = cible.get();
            viser(c[0], c[1], 450);
        }, FilmBandeAnnonce::arrive, reposMs);
    }

    private static void fermerEcran(Minecraft mc) {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }
        mc.setScreen(null);
    }

    private static void construireScenario(Minecraft mc) {
        arrivee(mc);
        f3(mc);
        liste(mc);
        piecesEtChange(mc);
        sol(mc);
        bourseEtEcran(mc);
        coffreFort(mc);
        equipe(mc);
        echange(mc);
        etalDuJoueur(mc);
        facture(mc);
        venteChezLEtalDuJoueur(mc);
        etalDeMaya(mc);
        fin(mc);
    }

    // --- Arrivée sur la place -----------------------------------------------------------------------

    private static void arrivee(Minecraft mc) {
        prep("spectateur", () -> {
            sur(mc, () -> moiServeur(mc).setGameMode(GameType.SPECTATOR));
            mc.options.hideGui = true;
            travelling(v(-16, 9, 15), v(0, 1, -5), v(-16, 9, 15), v(0, 1, -5), 1);
        }, () -> mc.player.isSpectator());
        scene("Lost Island — sneak peek");
        plan("place", "Une place de marché, sur l'île", () -> travelling(v(-16, 9, 15), v(0, 1, -5), v(7, 3.2, 7), v(1, 1, -5), 7000),
                FilmBandeAnnonce::travellingFini, 600);
        prep("survie", () -> {
            finCamera();
            mc.options.hideGui = false;
            sur(mc, () -> moiServeur(mc).setGameMode(GameType.SURVIVAL));
            aller(mc, o.getX() + 0.5, o.getY(), o.getZ() + 1.5, 160, 8);
        }, () -> !mc.player.isSpectator() && pres(mc, o.getX() + 0.5, o.getZ() + 1.5));
    }

    // --- F3 ---------------------------------------------------------------------------------------

    private static void f3(Minecraft mc) {
        prep("f3-regard", () -> {
            mc.gui.getChat().clearMessages(false);
            tenirVide(mc);
            aller(mc, table().getX() + 1.5, o.getY(), table().getZ() + 3.0, 165, 25);
        }, () -> pres(mc, table().getX() + 1.5, table().getZ() + 3.0));
        scene("Le F3, refait");
        plan("f3", "Un menu de débogage repensé, lisible d'un coup d'œil", () -> mc.options.renderDebug = true, () -> true, 6000);
        prep("f3-ferme", () -> mc.options.renderDebug = false, () -> !mc.options.renderDebug);
    }

    // --- TAB ---------------------------------------------------------------------------------------

    private static void liste(Minecraft mc) {
        prep("tab-roles", () -> {
            MinecraftServer s = serveur(mc);
            commande(s, "role create pecheur dark_aqua Pêcheur");
            commande(s, "role set " + mc.player.getGameProfile().getName() + " citoyen");
            for (Object[] f : FAUX_JOUEURS) {
                commande(s, "team join ofas_" + f[1] + " " + f[0]);
            }
            installerFaux(mc, FAUX_JOUEURS);
        }, () -> true);
        scene("La liste des joueurs");
        plan("tab", "Touche TAB : les joueurs de l'île, rangés par métier", () -> tabTenue = true, () -> true, 5000);
        prep("tab-fin", () -> {
            tabTenue = false;
            mc.options.keyPlayerList.setDown(false);
            retirerFaux(mc);
        }, () -> true);
    }

    // --- E1 : les pièces et le change --------------------------------------------------------------

    private static void piecesEtChange(Minecraft mc) {
        prep("depart", () -> {
            mc.gui.getChat().clearMessages(false);
            tenirVide(mc);
            aller(mc, o.getX() + 0.5, o.getY(), o.getZ() + 3.5, 180, 15);
        }, () -> pres(mc, o.getX() + 0.5, o.getZ() + 3.5));
        scene("La monnaie");
        planV("somme-depart", () -> "Chaque nouveau joueur reçoit 500 cuivres à sa première arrivée : " + fmt(valeurPortee(mc)),
                () -> {}, () -> valeurPortee(mc) == 500, 3000);
        plan("eco-give", "L'équipe peut injecter de l'argent : /eco give, tracé au registre", () -> {
            valeurAvant = valeurPortee(mc);
            mc.gui.getChat().clearMessages(false);
            commandeVue(mc, "eco give " + mc.player.getGameProfile().getName() + " 11111 Bienvenue sur l'île");
        }, () -> valeurPortee(mc) == valeurAvant + 11111, 2600);
        plan("inventaire", "Cinq pièces, du cuivre au rubis", () -> {
            mc.setScreen(null);
            mc.setScreen(new InventoryScreen(mc.player));
        }, () -> mc.screen instanceof InventoryScreen, 600);
        for (Coin c : Coin.values()) {
            survol("piece-" + c.key(), "Cinq pièces, du cuivre au rubis : chacune vaut dix fois la précédente", () -> {
                int i = caseJoueur(mc, st -> st.is(c.item()));
                if (i < 0) {
                    throw new IllegalStateException("pas de pièce " + c);
                }
                return centreCase(mc, i);
            }, 900);
        }
        prep("inventaire-ferme", () -> fermerEcran(mc), () -> mc.screen == null);

        // Le change à la table, vers le bas.
        prep("table-devant", () -> {
            tenirVide(mc);
            devant(mc, table(), 35);
        }, () -> devantPret(mc, table()));
        plan("table", "Le change se fait à la table de fabrication", () -> clicDroit(mc, table(), Direction.SOUTH),
                () -> mc.screen instanceof CraftingScreen, 700);
        clic("or-pris", "Une pièce d'or dans la grille…", () -> centreCase(mc, caseJoueur(mc, st -> st.is(Coin.GOLD.item()))), 0,
                () -> !mc.player.containerMenu.getCarried().isEmpty(), 200);
        clic("or-pose", "Une pièce d'or dans la grille…", () -> centreCase(mc, 1), 1,
                () -> !mc.player.containerMenu.getSlot(0).getItem().isEmpty(), 300);
        clic("or-rendu", "Une pièce d'or dans la grille…", () -> centreCase(mc, caseOuVide(mc, st -> st.is(Coin.GOLD.item()))), 0,
                () -> mc.player.containerMenu.getCarried().isEmpty(), 900);
        clic("change", "… donne dix pièces d'argent, à valeur égale", () -> centreCase(mc, 0), 0,
                () -> mc.player.containerMenu.getSlot(1).getItem().isEmpty() && !mc.player.containerMenu.getCarried().isEmpty(), 900);
        clic("change-range", "… donne dix pièces d'argent, à valeur égale", () -> centreCase(mc, caseOuVide(mc, st -> st.is(Coin.SILVER.item()))),
                0, () -> mc.player.containerMenu.getCarried().isEmpty(), 1200);
        prep("table-ferme", () -> fermerEcran(mc), () -> mc.screen == null);

        // L'argent refusé dans un coffre.
        prep("coffre-devant", () -> devant(mc, coffre(), 35), () -> devantPret(mc, coffre()));
        plan("coffre", "Un coffre ordinaire…", () -> clicDroit(mc, coffre(), Direction.SOUTH),
                () -> mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu() != mc.player.inventoryMenu, 700);
        clic("coffre-prend", "… n'accepte pas l'argent", () -> {
            origine = caseJoueur(mc, st -> st.is(Coin.SILVER.item()));
            return centreCase(mc, origine);
        }, 0, () -> !mc.player.containerMenu.getCarried().isEmpty(), 300);
        clic("coffre-refuse", "… n'accepte pas l'argent : la pièce reste au curseur", () -> centreCase(mc, 4), 0,
                () -> !mc.player.containerMenu.getCarried().isEmpty() && mc.player.containerMenu.getSlot(4).getItem().isEmpty(), 1800);
        clic("coffre-rend", "… n'accepte pas l'argent : la pièce reste au curseur", () -> centreCase(mc, origine), 0,
                () -> mc.player.containerMenu.getCarried().isEmpty(), 500);
        prep("coffre-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
    }

    // --- E1 : la pièce au sol ------------------------------------------------------------------------

    private static void sol(Minecraft mc) {
        prep("bourse-donnee", () -> {
            sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(EconomyRegistry.PURSE.get())));
            aller(mc, tremieSol().getX() + 0.5, o.getY(), tremieSol().getZ() + 3.5, 180, 28);
        }, () -> bourse(mc) == 0 && compter(mc, EconomyRegistry.PURSE.get()) == 1 && pres(mc, tremieSol().getX() + 0.5, tremieSol().getZ() + 3.5));
        scene("La monnaie");
        plan("pieces-tombent", "Des pièces tombent sur une trémie : elle ne les prend pas", () -> sur(mc, () -> {
            ServerLevel monde = serveur(mc).overworld();
            BlockPos t = tremieSol();
            // Sans élan : les pièces tombent droit dans la trémie et y restent.
            for (CoinDropEntity piece : List.of(CoinDropEntity.at(monde, t.getX() + 0.4, t.getY() + 1.6, t.getZ() + 0.5, Coin.GOLD, 3),
                    CoinDropEntity.at(monde, t.getX() + 0.6, t.getY() + 2.0, t.getZ() + 0.5, Coin.SILVER, 4))) {
                piece.setDeltaMovement(Vec3.ZERO);
                monde.addFreshEntity(piece);
            }
            EconomyLedger.get(serveur(mc)).created(MoneySource.STAFF, 340, null, "Pièces perdues sur la place");
        }), () -> true, 3500);
        // Il avance vraiment (touche d'avance tenue) jusqu'à ce que les pièces soient dans sa bourse.
        planV("ramasse", () -> "Seuls les joueurs les ramassent, et la bourse les range d'elle-même : " + fmt(bourse(mc)),
                () -> mc.options.keyUp.setDown(true), () -> {
                    // La trémie l'arrête ; les pièces dedans sont à portée de ramassage.
                    boolean pris = bourse(mc) == 340;
                    mc.options.keyUp.setDown(!pris);
                    return pris;
                }, 2500);
        prep("ramasse-fin", () -> mc.options.keyUp.setDown(false), () -> true);
    }

    // --- E1 : la bourse ---------------------------------------------------------------------------

    private static PurseMenu menuBourse(Minecraft mc) {
        return (PurseMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu();
    }

    private static void bourseEtEcran(Minecraft mc) {
        prep("bourse-main", () -> {
            tenir(mc, Purse::isPurse);
            curX = 320;
            curY = 300;
            curAX = curX;
            curAY = curY;
        }, () -> Purse.isPurse(mc.player.getMainHandItem()));
        scene("La bourse");
        plan("bourse-ecran", "Clic droit, la bourse en main : le total, une ligne par pièce", () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND),
                () -> menuOuvert(mc, PurseMenu.class), 1500);
        survol("bourse-boutons", "Déposer ou retirer 1, 10 ou 64 pièces : clic gauche, clic droit", () -> centreElement(mc, "row.silver.10"), 1800);
        clic("deposer-tout", "« Déposer tout » : toutes les pièces de l'inventaire y entrent", () -> centreElement(mc, "deposit_all"), 0,
                () -> libre(mc) == 0, 1500);
        clic("regrouper", "« Regrouper » : dix pièces deviennent une pièce de la suivante", () -> centreElement(mc, "group"), 0,
                () -> menuBourse(mc).count(Coin.SILVER) < 10, 1500);
        clic("change-bourse", "Le panneau « Change » : une émeraude cassée en dix pièces d'or", () -> centreElement(mc, "break.emerald"), 0,
                () -> menuBourse(mc).count(Coin.EMERALD) == 0, 1800);
        prep("bourse-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
    }

    // --- E1 : le coffre-fort ----------------------------------------------------------------------

    private static SafeMenu menuCoffreFort(Minecraft mc) {
        return (SafeMenu) ((AbstractContainerScreen<?>) mc.screen).getMenu();
    }

    private static void coffreFort(Minecraft mc) {
        prep("fort-donne", () -> {
            sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(EconomyRegistry.SAFE_ITEM.get())));
            devant(mc, coffreFortMoi(), 40);
        }, () -> compter(mc, EconomyRegistry.SAFE_ITEM.get()) == 1 && devantPret(mc, coffreFortMoi()));
        scene("Le coffre-fort");
        plan("fort-pose", "Un coffre-fort à soi, contre le mur", () -> {
            tenir(mc, st -> st.is(EconomyRegistry.SAFE_ITEM.get()));
            clicDroit(mc, coffreFortMoi().below(), Direction.UP);
        }, () -> mc.level.getBlockState(coffreFortMoi()).is(EconomyRegistry.SAFE.get()), 1500);
        prep("fort-main-vide", () -> {
            tenirVide(mc);
            curX = 320;
            curY = 300;
            curAX = curX;
            curAY = curY;
        }, () -> true);
        plan("fort-ouvert", "Son compte est gardé par le serveur, pas par le bloc", () -> clicDroit(mc, coffreFortMoi(), Direction.SOUTH),
                () -> menuOuvert(mc, SafeMenu.class), 1500);
        clic("fort-depose", "« Déposer tout » : la bourse et l'inventaire se vident dans le coffre-fort", () -> centreElement(mc, "deposit_all"), 0,
                () -> menuOuvert(mc, SafeMenu.class) && valeurPortee(mc) == 0 && menuCoffreFort(mc).total() > 0, 1800);
        clic("fort-retire", "Clic gauche dépose, clic droit retire : dix pièces d'or reviennent", () -> centreElement(mc, "row.gold.10"), 1,
                () -> valeurPortee(mc) == 1000, 1800);
        clic("fort-change", "Le change est aussi au coffre-fort : un rubis en dix émeraudes", () -> centreElement(mc, "break.ruby"), 0,
                () -> menuCoffreFort(mc).count(Coin.RUBY) == 0, 1800);
        prep("fort-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
        prep("fort-second-donne", () -> {
            sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(EconomyRegistry.SAFE_ITEM.get())));
            aller(mc, coffreFortMoi().getX() - 1.5, o.getY(), coffreFortMoi().getZ() + 2.5, 180, 40);
        }, () -> compter(mc, EconomyRegistry.SAFE_ITEM.get()) == 1);
        plan("fort-second", "Un seul par joueur : le second ne se pose pas", () -> {
            tenir(mc, st -> st.is(EconomyRegistry.SAFE_ITEM.get()));
            clicDroit(mc, coffreFortMoi().west(2).below(), Direction.UP);
        }, () -> true, 2500);
        prep("fort-second-range", () -> {
            sur(mc, () -> moiServeur(mc).getInventory().clearOrCountMatchingItems(st -> st.is(EconomyRegistry.SAFE_ITEM.get()), 64,
                    moiServeur(mc).inventoryMenu.getCraftSlots()));
            tenirVide(mc);
        }, () -> compter(mc, EconomyRegistry.SAFE_ITEM.get()) == 0);
        // Maya arrive et pose le sien.
        prep("maya-entre", () -> sur(mc, () -> {
            mayaEntre(serveur(mc), coffreFortMaya().getX() + 0.5, coffreFortMaya().getZ() + 2.5, 180F);
            mayaPoseBloc(serveur(mc), coffreFortMaya(), EconomyRegistry.SAFE_ITEM.get());
            maya().teleportTo(serveur(mc).overworld(), coffreFortMaya().getX() + 2.5, o.getY(), coffreFortMaya().getZ() + 2.0, 120F, 0F);
            maya().setYHeadRot(120F);
        }), () -> mayaVue(mc) != null && mc.level.getBlockState(coffreFortMaya()).is(EconomyRegistry.SAFE.get()));
        prep("maya-fort-devant", () -> {
            mc.gui.getChat().clearMessages(false);
            devant(mc, coffreFortMaya(), 35);
        }, () -> devantPret(mc, coffreFortMaya()));
        plan("fort-inviolable", "Celui de Maya ne s'ouvre pas pour un autre joueur", () -> clicDroit(mc, coffreFortMaya(), Direction.SOUTH),
                () -> mc.screen == null, 2600);
    }

    // --- E1 : l'équipe -----------------------------------------------------------------------------

    private static void equipe(Minecraft mc) {
        prep("equipe-prep", () -> mc.gui.getChat().clearMessages(false), () -> true);
        scene("Vu par l'équipe");
        plan("stats", "/eco stats : créé, détruit, gardé dans les coffres-forts, en circulation", () -> {
            mc.setScreen(new ChatScreen(""));
            commandeVue(mc, "eco stats");
        }, () -> dernierChat.length() > 0, 3800);
        plan("ledger", "/eco ledger : chaque pièce créée ou détruite, avec l'auteur et le motif", () -> {
            mc.gui.getChat().clearMessages(false);
            commandeVue(mc, "eco ledger");
        }, () -> true, 3800);
        prep("equipe-fin", () -> {
            mc.setScreen(null);
            mc.gui.getChat().clearMessages(false);
        }, () -> mc.screen == null);
    }

    // --- E2 : l'échange ----------------------------------------------------------------------------

    private static TradeMenu menuEchange(Minecraft mc) {
        return ((TradeScreen) mc.screen).getMenu();
    }

    private static boolean echangeOuvert(Minecraft mc) {
        return mc.screen instanceof TradeScreen;
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

    private static void echange(Minecraft mc) {
        prep("echange-place", () -> {
            sur(mc, () -> {
                maya().getInventory().add(new ItemStack(Items.BREAD, 6));
                maya().getInventory().add(new ItemStack(Items.APPLE, 2));
            });
            mayaVa(mc, o.getX() + 0.5, o.getZ() + 2.5, 0F);
            aller(mc, o.getX() + 0.5, o.getY(), o.getZ() + 5.0, 180, 5);
            lienAccepter = null;
            mc.gui.getChat().clearMessages(false);
        }, () -> pres(mc, o.getX() + 0.5, o.getZ() + 5.0) && mayaVue(mc) != null && mayaVue(mc).distanceTo(mc.player) < 3.5);
        scene("L'échange");
        plan("demande", "Maya propose un échange : /trade, ou accroupi, main vide, clic droit", () -> sur(mc,
                () -> serveur(mc).getCommands().performPrefixedCommand(maya().createCommandSourceStack(), "trade " + mc.player.getGameProfile().getName())),
                () -> lienAccepter != null, 2200);
        plan("accepte", "Un clic sur [Accepter] : la fenêtre d'échange s'ouvre chez les deux", () -> {
            ChatScreen chat = new ChatScreen("");
            mc.setScreen(chat);
            chat.handleComponentClicked(lienAccepter);
            if (mc.screen == chat) {
                mc.setScreen(null);
            }
        }, () -> echangeOuvert(mc), 1200);
        plan("maya-pose", "Maya pose cinq pains", () -> sur(mc, () -> mayaPoseDansEchange(Items.BREAD, 5)),
                () -> dansEchange(mc, false, Items.BREAD) == 5, 1200);
        clic("plus-or-1", "Les pièces se versent depuis la bourse : « +1 » or", () -> centreElement(mc, "coins.gold.1"), 0,
                () -> dansEchange(mc, true, Coin.GOLD.item()) == 1, 500);
        clic("plus-or-2", "Les pièces se versent depuis la bourse : « +1 » or", () -> centreElement(mc, "coins.gold.1"), 0,
                () -> dansEchange(mc, true, Coin.GOLD.item()) == 2, 800);
        clic("recu", "« Reçu » : chacun en recevra un", () -> centreElement(mc, "receipt"), 0, () -> menuEchange(mc).receipt(), 900);
        clic("confirme", "Il confirme…", () -> centreElement(mc, "confirm"), 0, () -> menuEchange(mc).selfConfirmed(), 1200);
        plan("tombe", "… Maya ajoute une pomme : toute confirmation tombe", () -> sur(mc, () -> mayaPoseDansEchange(Items.APPLE, 1)),
                () -> dansEchange(mc, false, Items.APPLE) == 1 && !menuEchange(mc).selfConfirmed(), 2200);
        clic("reconfirme", "Il confirme ce qu'il voit", () -> centreElement(mc, "confirm"), 0, () -> menuEchange(mc).selfConfirmed(), 800);
        plan("fait", "Maya confirme aussi : l'échange a lieu, d'un bloc", () -> {
            compteAvant = compter(mc, EconomyRegistry.RECEIPT.get());
            sur(mc, () -> {
                TradeMenu m = (TradeMenu) maya().containerMenu;
                maya.envoyer(TradeActionPacket.confirm(m.version()));
            });
        }, () -> mc.screen == null && compter(mc, EconomyRegistry.RECEIPT.get()) == compteAvant + 1, 900);
        plan("recu-vu", "Le reçu garde la trace de l'échange", () -> {
            mc.setScreen(new InventoryScreen(mc.player));
            curX = 420;
            curY = 120;
            curAX = curX;
            curAY = curY;
        }, () -> mc.screen instanceof InventoryScreen, 300);
        survol("recu-infobulle", "Le reçu garde la trace de l'échange",
                () -> centreCase(mc, caseJoueur(mc, st -> st.getItem() instanceof ReceiptItem)), 2600);
        prep("recu-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
    }

    // --- E2 : l'étal du joueur ---------------------------------------------------------------------

    private static StallOwnerMenu gestion(Minecraft mc) {
        return ((StallOwnerScreen) mc.screen).getMenu();
    }

    private static int[] onglet(Minecraft mc, int i) {
        int[] g = origineEcran(mc);
        int w = (160 - 3 * 2) / 4;
        return new int[] {g[0] + 8 + i * (w + 2) + w / 2, g[1] + 18 + 7};
    }

    private static MarketStallBlockEntity etalClient(Minecraft mc, BlockPos pos) {
        return mc.level.getBlockEntity(pos) instanceof MarketStallBlockEntity be ? be : null;
    }

    private static void etalDuJoueur(Minecraft mc) {
        prep("etal-donne", () -> {
            sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(EconomyRegistry.MARKET_STALL_ITEM.get())));
            devant(mc, etalMoi(), 40);
        }, () -> compter(mc, EconomyRegistry.MARKET_STALL_ITEM.get()) == 1 && devantPret(mc, etalMoi()));
        scene("L'étal");
        plan("etal-pose", "Un étal, posé contre le mur : il appartient à celui qui le pose", () -> {
            tenir(mc, st -> st.is(EconomyRegistry.MARKET_STALL_ITEM.get()));
            clicDroit(mc, etalMoi().below(), Direction.UP);
        }, () -> etalClient(mc, etalMoi()) != null, 1300);
        prep("etal-main-vide", () -> {
            tenirVide(mc);
            curX = 320;
            curY = 300;
            curAX = curX;
            curAY = curY;
        }, () -> true);
        plan("gestion", "Clic droit : la gestion de son étal", () -> clicDroit(mc, etalMoi(), Direction.SOUTH),
                () -> mc.screen instanceof StallOwnerScreen, 900);
        plan("stock-vise", "Le stock : 27 cases, rangées d'un majuscule-clic", () -> {
            origine = caseJoueur(mc, st -> st.is(Items.BREAD));
            int[] c = centreCase(mc, origine);
            viser(c[0], c[1], 450);
        }, FilmBandeAnnonce::arrive, 150);
        plan("stock", "Le stock : 27 cases, rangées d'un majuscule-clic", () -> clicMenu(mc, origine, 0, ClickType.QUICK_MOVE),
                () -> gestion(mc).getSlot(StallOwnerMenu.STOCK_START).getItem().is(Items.BREAD), 1200);
        clic("onglet-offres", "Les offres : jusqu'à 9 ventes et 9 rachats", () -> onglet(mc, 1), 0,
                () -> gestion(mc).view() == StallOwnerMenu.VIEW_OFFERS, 600);
        clic("case-offre", "Les offres : jusqu'à 9 ventes et 9 rachats", () -> {
            int[] g = origineEcran(mc);
            return new int[] {g[0] + StallOwnerMenu.STOCK_X - 1 + 9, g[1] + 50 + 9};
        }, 0, () -> true, 300);
        // Le modèle : un pain pris au curseur (le joueur en garde dans son inventaire).
        prep("modele-pain", () -> sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(Items.BREAD, 2))),
                () -> caseJoueur(mc, st -> st.is(Items.BREAD)) >= 0);
        clic("modele", "Le modèle tenu au curseur, le lot et le prix tapés", () -> {
            origine = caseJoueur(mc, st -> st.is(Items.BREAD));
            return centreCase(mc, origine);
        }, 0, () -> gestion(mc).getCarried().is(Items.BREAD), 300);
        clic("champ-lot", "Le modèle tenu au curseur, le lot et le prix tapés", () -> {
            int[] g = origineEcran(mc);
            return new int[] {g[0] + 8 + 3 + 6, g[1] + 70 + 2 + 3};
        }, 0, () -> true, 200);
        plan("tape-lot", "Le modèle tenu au curseur, le lot et le prix tapés", () -> {
            effacerChamp(mc);
            taper(mc, "2");
        }, () -> true, 400);
        clic("champ-prix", "Le modèle tenu au curseur, le lot et le prix tapés", () -> {
            int[] g = origineEcran(mc);
            return new int[] {g[0] + 8 + 24 + 2 + 3 + 10, g[1] + 70 + 2 + 3};
        }, 0, () -> true, 200);
        plan("tape-prix", "Le modèle tenu au curseur, le lot et le prix tapés", () -> taper(mc, "20"), () -> true, 500);
        clic("definir", "« Définir » : 2 pains pour 20 cuivres", () -> {
            int[] g = origineEcran(mc);
            return new int[] {g[0] + 8 + (160 - 40 - 40 - 4) + 2 + 20, g[1] + 84 + 6};
        }, 0, () -> {
            MarketStallBlockEntity be = etalClient(mc, etalMoi());
            StallOffer offre = be == null ? null : be.offers().get(0);
            return offre != null && offre.lot() == 2 && offre.price() == 20;
        }, 1200);
        clic("modele-rendu", "« Définir » : 2 pains pour 20 cuivres", () -> centreCase(mc, origine), 0,
                () -> gestion(mc).getCarried().isEmpty(), 300);
        // Le rachat : des carottes.
        clic("sous-onglet-rachat", "Un rachat : l'étal achète des carottes, payées par sa caisse", () -> {
            int[] g = origineEcran(mc);
            return new int[] {g[0] + 8 + 64 + 31, g[1] + 36 + 6};
        }, 0, () -> true, 400);
        clic("modele-carotte", "Un rachat : l'étal achète des carottes, payées par sa caisse", () -> {
            origine = caseJoueur(mc, st -> st.is(Items.CARROT));
            return centreCase(mc, origine);
        }, 0, () -> gestion(mc).getCarried().is(Items.CARROT), 300);
        clic("rachat-lot", "Un rachat : l'étal achète des carottes, payées par sa caisse", () -> {
            int[] g = origineEcran(mc);
            return new int[] {g[0] + 8 + 3 + 6, g[1] + 70 + 2 + 3};
        }, 0, () -> true, 200);
        plan("rachat-tape-lot", "Un rachat : 4 carottes pour 8 cuivres, 3 fois", () -> {
            effacerChamp(mc);
            taper(mc, "4");
        }, () -> true, 300);
        // Champs du rachat : lot (24), prix (98), nombre de lots (34), séparés de 2, à partir de x = 8 (StallOwnerScreen).
        clic("rachat-prix", "Un rachat : 4 carottes pour 8 cuivres, 3 fois", () -> {
            int[] g = origineEcran(mc);
            return new int[] {g[0] + 8 + 24 + 2 + 49, g[1] + 70 + 2 + 3};
        }, 0, () -> true, 150);
        plan("rachat-tape-prix", "Un rachat : 4 carottes pour 8 cuivres, 3 fois", () -> {
            effacerChamp(mc);
            taper(mc, "8");
        }, () -> true, 300);
        clic("rachat-lots", "Un rachat : 4 carottes pour 8 cuivres, 3 fois", () -> {
            int[] g = origineEcran(mc);
            return new int[] {g[0] + 8 + 24 + 2 + 98 + 2 + 17, g[1] + 70 + 2 + 3};
        }, 0, () -> true, 150);
        plan("rachat-tape-lots", "Un rachat : 4 carottes pour 8 cuivres, 3 fois", () -> {
            effacerChamp(mc);
            taper(mc, "3");
        }, () -> true, 600);
        clic("rachat-definir", "Un rachat : 4 carottes pour 8 cuivres, 3 fois", () -> {
            int[] g = origineEcran(mc);
            return new int[] {g[0] + 8 + (160 - 40 - 40 - 4) + 2 + 20, g[1] + 84 + 6};
        }, 0, () -> {
            MarketStallBlockEntity be = etalClient(mc, etalMoi());
            return be != null && be.buybacks().get(0) != null;
        }, 1200);
        clic("carotte-rendue", "Un rachat : 4 carottes pour 8 cuivres, 3 fois", () -> centreCase(mc, origine), 0,
                () -> gestion(mc).getCarried().isEmpty(), 300);
        clic("onglet-caisse", "La caisse : la monnaie de l'étal, à part de la sienne", () -> onglet(mc, 2), 0,
                () -> mc.screen instanceof StallCashScreen, 800);
        clic("caisse-depot", "La caisse : 1 or déposé pour rendre la monnaie et payer les rachats", () -> centreElement(mc, "row.gold.1"), 0,
                () -> mc.screen instanceof StallCashScreen s && s.getMenu().total() == 100, 1600);
        prep("caisse-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
        comptoir(mc, "comptoir", etalMoi(), "Les premières offres se voient sur le comptoir, sans ouvrir l'étal");
    }

    /** Un passage de caméra à hauteur du comptoir (sous l'auvent de l'étal), en spectateur, l'interface masquée. */
    private static void comptoir(Minecraft mc, String nom, BlockPos etal, String legende) {
        prep(nom + "-camera", () -> {
            sur(mc, () -> moiServeur(mc).setGameMode(GameType.SPECTATOR));
            mc.options.hideGui = true;
            Vec3 de = new Vec3(etal.getX() + 1.9, etal.getY() + 0.85 - 1.62, etal.getZ() + 2.3);
            Vec3 vise = new Vec3(etal.getX() + 0.5, etal.getY() + 0.6, etal.getZ() + 0.6);
            travelling(de, vise, de, vise, 1);
        }, () -> mc.player.isSpectator());
        plan(nom, legende, () -> {
            Vec3 vise = new Vec3(etal.getX() + 0.5, etal.getY() + 0.6, etal.getZ() + 0.6);
            travelling(new Vec3(etal.getX() + 1.9, etal.getY() + 0.85 - 1.62, etal.getZ() + 2.3), vise,
                    new Vec3(etal.getX() - 0.9, etal.getY() + 0.85 - 1.62, etal.getZ() + 2.3), vise, 3200);
        }, FilmBandeAnnonce::travellingFini, 300);
        prep(nom + "-fin", () -> {
            finCamera();
            mc.options.hideGui = false;
            sur(mc, () -> moiServeur(mc).setGameMode(GameType.SURVIVAL));
            aller(mc, etal.getX() + 0.5, o.getY(), etal.getZ() + 3.0, 180, 20);
        }, () -> !mc.player.isSpectator() && pres(mc, etal.getX() + 0.5, etal.getZ() + 3.0));
    }

    // --- E2 : la facture ---------------------------------------------------------------------------

    private static void facture(Minecraft mc) {
        prep("facture-place", () -> {
            sur(mc, () -> moiServeur(mc).getInventory().add(new ItemStack(EconomyRegistry.INVOICE_BOOK.get())));
            mayaVa(mc, o.getX() + 0.5, o.getZ() + 2.5, 0F);
            aller(mc, o.getX() + 0.5, o.getY(), o.getZ() + 5.0, 180, 5);
            mc.gui.getChat().clearMessages(false);
        }, () -> compter(mc, EconomyRegistry.INVOICE_BOOK.get()) == 1 && pres(mc, o.getX() + 0.5, o.getZ() + 5.0));
        scene("La facture");
        plan("carnet", "Le carnet de factures, utilisé sur un joueur proche", () -> {
            tenir(mc, st -> st.is(EconomyRegistry.INVOICE_BOOK.get()));
            Player cible = mayaVue(mc);
            mc.gameMode.interactAt(mc.player, cible, new EntityHitResult(cible), InteractionHand.MAIN_HAND);
            mc.gameMode.interact(mc.player, cible, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }, () -> mc.screen instanceof InvoiceFormScreen, 900);
        plan("formulaire", "Le formulaire : le montant, le motif", () -> {
            taper(mc, "150");
            mc.screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
            taper(mc, "Trois paniers de légumes");
        }, () -> true, 1500);
        clic("emettre", "« Émettre » : Maya reçoit la facture", () -> new int[] {champ(mc.screen, "left") + 10 + 47, champ(mc.screen, "top") + 108 + 8},
                0, () -> mc.screen == null, 600);
        plan("maya-paie", "Maya la paie : les pièces arrivent, la facture devient un reçu", () -> {
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
        }, () -> valeurPortee(mc) == valeurAvant + 150, 2200);
        plan("facture-recue", "À son tour, Maya facture : la facture arrive dans l'inventaire", () -> sur(mc, () -> {
            ServerPlayer j = maya();
            mayaEnMainVide(j);
            j.getInventory().setItem(j.getInventory().selected, new ItemStack(EconomyRegistry.INVOICE_BOOK.get()));
            maya.envoyer(new InvoiceIssuePacket(mc.player.getUUID(), 120, "Location de la place au marché"));
        }), () -> compter(mc, EconomyRegistry.INVOICE.get()) == 1, 1500);
        plan("facture-ecran", "Utilisée, elle s'ouvre : émettrice, montant, motif", () -> {
            tenir(mc, st -> st.is(EconomyRegistry.INVOICE.get()));
            origine = mc.player.getInventory().selected;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            curX = 320;
            curY = 300;
            curAX = curX;
            curAY = curY;
        }, () -> mc.screen instanceof InvoiceScreen, 1600);
        clic("payer", "« Payer » : tout ou rien, depuis la bourse", () -> new int[] {champ(mc.screen, "left") + 10 + 47,
                champ(mc.screen, "top") + champ(mc.screen, "buttonsY") + 8}, 0,
                () -> mc.screen == null && mc.player.getInventory().getItem(origine).getItem() instanceof ReceiptItem, 1500);
        plan("liste-factures", "/invoice list : ses factures, émises et reçues", () -> {
            mc.gui.getChat().clearMessages(false);
            mc.setScreen(new ChatScreen(""));
            sur(mc, () -> serveur(mc).getCommands().performPrefixedCommand(moiServeur(mc).createCommandSourceStack(), "invoice list"));
        }, () -> true, 4000);
        prep("liste-ferme", () -> {
            mc.setScreen(null);
            mc.gui.getChat().clearMessages(false);
        }, () -> mc.screen == null);
    }

    // --- E2 : Maya achète à l'étal du joueur, le registre --------------------------------------------

    private static void venteChezLEtalDuJoueur(Minecraft mc) {
        prep("vente-place", () -> {
            sur(mc, () -> {
                ServerPlayer j = maya();
                j.teleportTo(serveur(mc).overworld(), etalMoi().getX() + 0.5, o.getY(), etalMoi().getZ() + 2.0, 180F, 30F);
                j.setYHeadRot(180F);
            });
            aller(mc, etalMoi().getX() + 3.5, o.getY(), etalMoi().getZ() + 4.0, 135, 20);
        }, () -> pres(mc, etalMoi().getX() + 3.5, etalMoi().getZ() + 4.0));
        scene("L'étal");
        plan("maya-achete", "Maya achète 2 pains : le prix va dans la caisse", () -> sur(mc, () -> {
            ServerPlayer j = maya();
            BlockPos pos = etalMoi();
            if (MarketStallBlock.openShop(j, pos) && serveur(mc).overworld().getBlockEntity(pos) instanceof MarketStallBlockEntity be) {
                StallOffer offre = be.offers().get(0);
                maya.envoyer(StallActionPacket.buy(pos, 0, offre.price(), offre.lot(), be.version()));
                j.closeContainer();
            }
        }), () -> true, 1800);
        prep("registre-devant", () -> {
            devant(mc, etalMoi(), 35);
            tenirVide(mc);
        }, () -> devantPret(mc, etalMoi()));
        plan("gestion-2", "Le registre : chaque vente, chaque rachat, chaque mouvement", () -> clicDroit(mc, etalMoi(), Direction.SOUTH),
                () -> mc.screen instanceof StallOwnerScreen, 400);
        clic("onglet-registre", "Le registre : chaque vente, chaque rachat, chaque mouvement", () -> onglet(mc, 3), 0,
                () -> gestion(mc).view() == StallOwnerMenu.VIEW_LEDGER, 3200);
        prep("registre-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
    }

    // --- E2 : l'étal de Maya, vendeur absent ---------------------------------------------------------

    private static StallShopMenu vitrine(Minecraft mc) {
        return ((StallShopScreen) mc.screen).getMenu();
    }

    private static int[] rangeeVitrine(Minecraft mc, int rangee) {
        int[] g = origineEcran(mc);
        return new int[] {g[0] + 8 + 154 - 42 - 2 + 21, g[1] + StallShopMenu.LIST_Y + rangee * StallShopMenu.ROW_H + (StallShopMenu.ROW_H - 14) / 2 + 7};
    }

    private static int[] ongletVitrine(Minecraft mc, int t) {
        int[] g = origineEcran(mc);
        int w = (176 - 16 - 2) / 2;
        return new int[] {g[0] + 8 + t * (w + 2) + w / 2, g[1] + 16 + 6};
    }

    private static void etalDeMaya(Minecraft mc) {
        prep("maya-etal-prep", () -> {
            sur(mc, () -> {
                ServerPlayer j = maya();
                j.getInventory().add(new ItemStack(Items.BREAD, 12));
                j.getInventory().add(new ItemStack(Items.APPLE, 9));
                j.getInventory().add(new ItemStack(Items.COOKED_COD, 6));
                j.getInventory().add(new ItemStack(Items.WHEAT, 1));
            });
            aller(mc, etalMaya().getX() + 2.5, o.getY(), etalMaya().getZ() + 5.0, 155, 15);
        }, () -> pres(mc, etalMaya().getX() + 2.5, etalMaya().getZ() + 5.0));
        scene("L'étal de Maya");
        plan("maya-pose-etal", "Maya pose son étal et le garnit", () -> sur(mc, () -> mayaPoseBloc(serveur(mc), etalMaya(), EconomyRegistry.MARKET_STALL_ITEM.get())),
                () -> etalClient(mc, etalMaya()) != null, 1200);
        plan("maya-garnit", "Maya pose son étal et le garnit : ses offres apparaissent sur le comptoir", () -> sur(mc, () -> mayaGarnit(etalMaya())),
                () -> {
                    MarketStallBlockEntity be = etalClient(mc, etalMaya());
                    return be != null && be.offers().get(2) != null;
                }, 2200);
        comptoir(mc, "comptoir-maya", etalMaya(), "Ses offres sur le comptoir : pain, pommes, cabillaud");
        plan("maya-part", "Maya se déconnecte : l'étal reste ouvert, vendeur absent", () -> sur(mc, () -> {
            maya.quitter();
            maya = null;
        }), () -> mayaVue(mc) == null, 2200);
        prep("vitrine-devant", () -> {
            devant(mc, etalMaya(), 20);
            tenirVide(mc);
            curX = 320;
            curY = 300;
            curAX = curX;
            curAY = curY;
        }, () -> devantPret(mc, etalMaya()));
        plan("vitrine", "Clic droit : la vitrine, ce qui se vend et ce qui se rachète", () -> clicDroit(mc, etalMaya(), Direction.SOUTH),
                () -> mc.screen instanceof StallShopScreen && vitrine(mc).saleAvailable(0) > 0, 1500);
        prep("avant-achat", () -> valeurAvant = valeurPortee(mc), () -> true);
        clic("acheter", "« Acheter » : 4 pains pour 30 cuivres, payés d'une pièce d'or", () -> rangeeVitrine(mc, 0), 0,
                () -> valeurPortee(mc) == valeurAvant - 30, 1500);
        clic("onglet-rachete", "L'onglet « Rachète » : l'étal achète du blé", () -> ongletVitrine(mc, 1), 0,
                () -> vitrine(mc).buybackState(0) == StallShopMenu.BUYBACK_OPEN, 900);
        clic("vendre", "« Vendre » : 8 blés contre 16 cuivres, payés par la caisse", () -> rangeeVitrine(mc, 0), 0,
                () -> compter(mc, Items.WHEAT) == 0, 1500);
        prep("vitrine-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
        prep("monnaie-bourse", () -> {
            tenir(mc, Purse::isPurse);
        }, () -> Purse.isPurse(mc.player.getMainHandItem()));
        planV("monnaie-rendue", () -> "La monnaie est rendue dans la bourse : " + fmt(bourse(mc)),
                () -> {
                    curX = 330;
                    curY = 330;
                    curAX = curX;
                    curAY = curY;
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }, () -> menuOuvert(mc, PurseMenu.class), 2600);
        prep("bourse-ferme-2", () -> {
            fermerEcran(mc);
            tenirVide(mc);
        }, () -> mc.screen == null);
        plan("incassable", "Plein, l'étal ne se casse pas : seul son propriétaire, une fois vide", () -> {
            aCasser = etalMaya();
            mc.gameMode.startDestroyBlock(aCasser, Direction.SOUTH);
            casserTenu = true;
        }, () -> System.nanoTime() - debutAction > 2_500_000_000L, 200);
        prep("incassable-fin", () -> {
            casserTenu = false;
            mc.gameMode.stopDestroyBlock();
            aller(mc, etalMaya().getX() + 0.5, o.getY(), etalMaya().getZ() + 2.0, 180, 50);
        }, () -> etalClient(mc, etalMaya()) != null && pres(mc, etalMaya().getX() + 0.5, etalMaya().getZ() + 2.0));
        plan("tremie", "La trémie dessous ne le vide pas", () -> clicDroit(mc, etalMaya().below(), Direction.SOUTH),
                () -> mc.screen instanceof HopperScreen, 2400);
        prep("tremie-ferme", () -> fermerEcran(mc), () -> mc.screen == null);
    }

    // --- Fin ---------------------------------------------------------------------------------------

    private static void fin(Minecraft mc) {
        prep("fin-spectateur", () -> {
            sur(mc, () -> moiServeur(mc).setGameMode(GameType.SPECTATOR));
            mc.options.hideGui = true;
            travelling(v(2, 2.5, 4), v(0, 1, -5), v(2, 2.5, 4), v(0, 1, -5), 1);
        }, () -> mc.player.isSpectator());
        scene("Lost Island — sneak peek");
        plan("fin", "", () -> travelling(v(2, 2.5, 4), v(0, 1, -5), v(-6, 12, 18), v(1, 1, -5), 6000),
                FilmBandeAnnonce::travellingFini, 1500);
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
            LOG.info("[film] étape : {}", e.nom());
            if (e.tourne()) {
                prise.plan(e.nom(), e.titre());
            }
            e.action().run();
            actionFaite = true;
            debutAction = maintenant;
            debutRepos = 0;
            return;
        }
        boolean pret;
        try {
            pret = e.pret().getAsBoolean();
        } catch (RuntimeException ex) {
            pret = false;
        }
        if (!pret) {
            if (maintenant - debutAction > 30_000_000_000L) {
                throw new IllegalStateException("étape sans issue : " + e.nom() + " | écran " + mc.screen + " | porté " + valeurPortee(mc)
                        + " | dernier message « " + dernierChat + " »");
            }
            return;
        }
        if (debutRepos == 0) {
            debutRepos = maintenant;
        }
        if ((maintenant - debutRepos) / 1_000_000L < e.reposMs() * RYTHME) {
            return;
        }
        LOG.info("[film] étape finie : {}", e.nom());
        indexEtape++;
        actionFaite = false;
        if (indexEtape >= ETAPES.size()) {
            terminer(mc);
        }
    }

    private static void terminer(Minecraft mc) {
        phase = Phase.FIN;
        tabTenue = false;
        casserTenu = false;
        finCamera();
        int n = 0;
        if (priseChargement != null) {
            priseChargement.terminer(null);
        }
        if (prise != null) {
            prise.terminer(echec);
            n = prise.images();
        }
        LOG.info("[film] terminé : {} images, {}", n, echec == null ? "sans erreur" : echec);
        mc.stop();
    }

    // ================================================================================================
    // Les images : 30 par seconde, JPEG, légendes et curseur dessinés
    // ================================================================================================

    /** Une suite d'images à cadence fixe dans un dossier ; {@code legendes} faux : l'image du jeu seule. */
    private static final class Prise {
        private final Path dossier;
        private final boolean legendes;
        private final ThreadPoolExecutor ecrivains = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(48), new ThreadPoolExecutor.CallerRunsPolicy());
        private long prochaineNs;
        private int numero;
        private int copies;
        private long debutPlanNs;
        private long debutTitreNs;
        private String titre = "";
        private boolean coupe = true;
        private volatile double[] gros;

        Prise(Path dossier, boolean legendes) throws IOException {
            this.dossier = dossier;
            this.legendes = legendes;
            Files.createDirectories(dossier);
            try (Stream<Path> vieux = Files.list(dossier)) {
                for (Path f : vieux.toList()) {
                    Files.deleteIfExists(f);
                }
            }
        }

        int images() {
            return numero;
        }

        void grosPlan(double cx, double cy, double facteur) {
            gros = new double[] {cx, cy, facteur};
        }

        void pleinCadre() {
            gros = null;
        }

        /** Le début d'un pas filmé : noté dans {@code plans.csv}. */
        void plan(String nom, String titrePlan) {
            if (!titrePlan.equals(titre)) {
                titre = titrePlan;
                debutTitreNs = System.nanoTime();
            }
            try {
                Files.writeString(dossier.resolve("plans.csv"), (numero + 1) + ";" + nom + ";" + titrePlan + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOG.warn("[film] plans.csv non écrit", e);
            }
        }

        /** Rien n'est filmé : la prochaine image ouvre un nouveau plan (fondu d'entrée). */
        void couper() {
            if (!coupe) {
                coupe = true;
                prochaineNs = 0;
            }
        }

        void capturer(Minecraft mc, String titrePlan, String legende, double[] curseur, boolean enBas) {
            long maintenant = System.nanoTime();
            if (coupe) {
                coupe = false;
                debutPlanNs = maintenant;
                prochaineNs = maintenant;
            }
            if (maintenant < prochaineNs) {
                return;
            }
            // Images en retard : la même est écrite autant de fois qu'il le faut (au plus 6), pour garder 30 par seconde.
            int fois = 1 + (int) Math.min(5, (maintenant - prochaineNs) / CADENCE_NS);
            prochaineNs += (long) fois * CADENCE_NS;
            if (maintenant - prochaineNs > 6 * CADENCE_NS) {
                prochaineNs = maintenant + CADENCE_NS;
            }
            copies += fois - 1;
            int premier = numero + 1;
            numero += fois;
            // Coupe franche entre deux plans : le seul fondu voulu est celui de la fin (au montage).
            double fondu = 1.0;
            double apparition = Math.min(1.0, (maintenant - debutTitreNs) / 400_000_000.0);
            float echelle = (float) mc.getWindow().getGuiScale();
            double[] zoom = gros;
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int l = image.getWidth();
                int h = image.getHeight();
                int[] rgb = new int[l * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < l; x++) {
                        int p = image.getPixelRGBA(x, y);
                        rgb[y * l + x] = ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
                    }
                }
                ecrivains.execute(() -> ecrire(premier, fois, rgb, l, h, titrePlan, legende, curseur, echelle, fondu, apparition, zoom, enBas));
            }
        }

        private void ecrire(int premier, int fois, int[] rgb, int l, int h, String titrePlan, String legende, double[] curseur,
                float echelle, double fondu, double apparition, double[] zoom, boolean enBas) {
            BufferedImage img = new BufferedImage(LARGEUR, HAUTEUR, BufferedImage.TYPE_INT_RGB);
            BufferedImage jeu = new BufferedImage(l, h, BufferedImage.TYPE_INT_RGB);
            jeu.setRGB(0, 0, l, h, rgb, 0, l);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (zoom != null) {
                    int sw = (int) Math.round(l / zoom[2]);
                    int sh = (int) Math.round(h / zoom[2]);
                    int sx = (int) Math.max(0, Math.min(l - sw, zoom[0] * l / LARGEUR - sw / 2.0));
                    int sy = (int) Math.max(0, Math.min(h - sh, zoom[1] * h / HAUTEUR - sh / 2.0));
                    g.drawImage(jeu, 0, 0, LARGEUR, HAUTEUR, sx, sy, sx + sw, sy + sh, null);
                } else {
                    g.drawImage(jeu, 0, 0, LARGEUR, HAUTEUR, null);
                }
                if (curseur != null && zoom == null) {
                    dessinerCurseur(g, curseur[0] * echelle * LARGEUR / l, curseur[1] * echelle * HAUTEUR / h);
                }
                if (legendes && !titrePlan.isEmpty()) {
                    dessinerLegende(g, titrePlan, legende, apparition, enBas);
                }
                if (fondu < 1.0) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1.0 - fondu)));
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, LARGEUR, HAUTEUR);
                }
            } finally {
                g.dispose();
            }
            Path premierFichier = dossier.resolve(String.format(Locale.ROOT, "f%05d.jpg", premier));
            try {
                ecrireJpeg(img, premierFichier);
                for (int i = 1; i < fois; i++) {
                    Files.copy(premierFichier, dossier.resolve(String.format(Locale.ROOT, "f%05d.jpg", premier + i)));
                }
            } catch (IOException ex) {
                LOG.error("[film] image non écrite : {}", premierFichier, ex);
            }
        }

        private static void ecrireJpeg(BufferedImage img, Path fichier) throws IOException {
            ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = w.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.93f);
            try (ImageOutputStream sortie = ImageIO.createImageOutputStream(fichier.toFile())) {
                w.setOutput(sortie);
                w.write(null, new IIOImage(img, null, null), param);
            } finally {
                w.dispose();
            }
        }

        /** Une flèche blanche bordée de noir, la pointe sur le point du clic. */
        private static void dessinerCurseur(Graphics2D g, double x, double y) {
            int px = (int) Math.round(x);
            int py = (int) Math.round(y);
            Polygon fleche = new Polygon(new int[] {0, 0, 5, 9, 12, 8, 14}, new int[] {0, 19, 14, 22, 21, 13, 13}, 7);
            fleche.translate(px, py);
            g.setColor(Color.WHITE);
            g.fillPolygon(fleche);
            g.setStroke(new BasicStroke(1.6f));
            g.setColor(Color.BLACK);
            g.drawPolygon(fleche);
        }

        /**
         * Le titre de la scène (or) et la légende du pas, sur un dégradé sombre : en haut à gauche (la barre d'action et le chat
         * restent libres), en bas quand le haut est pris (F3, liste des joueurs).
         */
        private static void dessinerLegende(Graphics2D g, String titrePlan, String legende, double apparition, boolean enBas) {
            float a = (float) apparition;
            int hauteur = 112;
            int y0 = enBas ? HAUTEUR - hauteur : 0;
            int base = enBas ? HAUTEUR - 82 : 22;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f * a));
            g.setPaint(enBas ? new GradientPaint(0, y0, new Color(0, 0, 0, 0), 0, HAUTEUR, new Color(8, 12, 18, 235))
                    : new GradientPaint(0, 0, new Color(8, 12, 18, 235), 0, hauteur, new Color(0, 0, 0, 0)));
            g.fillRect(0, y0, LARGEUR, hauteur);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.setColor(new Color(0xE0, 0x9A, 0x2B));
            g.fillRect(40, base, 5, 54);
            g.setFont(new Font("Segoe UI", Font.BOLD, 28));
            g.setColor(new Color(0xF2, 0xC2, 0x6B));
            g.drawString(titrePlan, 58, base + 26);
            if (!legende.isEmpty()) {
                g.setFont(new Font("Segoe UI", Font.PLAIN, 21));
                g.setColor(Color.WHITE);
                g.drawString(legende, 58, base + 54);
            }
            g.setComposite(AlphaComposite.SrcOver);
        }

        void terminer(String echecFinal) {
            ecrivains.shutdown();
            try {
                ecrivains.awaitTermination(3, TimeUnit.MINUTES);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            String e = echecFinal == null ? "null" : "\"" + echecFinal.replace('"', '\'').replace('\\', '/') + "\"";
            try {
                Files.writeString(dossier.resolve("termine.json"), "{\"images\":" + numero + ",\"copies\":" + copies + ",\"fps\":30,\"echec\":"
                        + e + "}", StandardCharsets.UTF_8);
            } catch (IOException ex) {
                LOG.error("[film] termine.json non écrit", ex);
            }
        }
    }
}
