package dev.oneforallsurvival.dev;

import com.google.gson.JsonObject;
import dev.oneforallsurvival.client.web.WebAvailability;
import dev.oneforallsurvival.client.web.WebBridge;
import dev.oneforallsurvival.client.web.WebFailure;
import dev.oneforallsurvival.client.web.WebScreen;
import dev.oneforallsurvival.client.web.WebScreen.Rect;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneNetView;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import dev.oneforallsurvival.phone.PhoneWallpapers;
import dev.oneforallsurvival.phone.WallSocketBlock;
import dev.oneforallsurvival.phone.WallSocketBlockEntity;
import dev.oneforallsurvival.phone.client.PhoneClient;
import dev.oneforallsurvival.phone.client.PhoneFallbackScreen;
import dev.oneforallsurvival.phone.client.PhoneWebScreen;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaItem;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.ModMismatchDisconnectedScreen;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Essai réel du téléphone (développement seulement). Activé par {@code -Doneforallsurvival.telephoneShots=<dossier>}
 * (propriété Gradle {@code -PtelephoneShots=<dossier>}, relatif au dossier du jeu), inerte sinon. Le client crée un
 * monde plat neuf {@value #MONDE} (ou rejoint le serveur de {@code oneforallsurvival.telephoneShots.server}), se donne
 * un téléphone, puis joue le mode demandé ({@code oneforallsurvival.telephoneShots.mode}) par de vrais clics et une
 * vraie frappe relayés par l'écran ouvert : dans la page (MCEF), aux centres des boîtes du dernier {@code layout}
 * stable ; dans l'écran de repli, aux centres de ses boutons. Une image par écran.
 *
 * <ul>
 *   <li>{@code parcours} : assistant de première configuration fermé avant le code (le téléphone reste neuf), puis
 *   joué en entier (Bonjour, Configuration rapide, Fond d'écran, code, Tout est prêt ; au repli, « Fond suivant » puis
 *   le code), Réglages (fond, mode avion, changement du code), cinq faux codes (fenêtre et
 *   échelle changées écran ouvert), batterie, prise, effacement depuis l'écran, téléphone qui quitte la main
 *   ({@code PHONE_GONE}), {@code /phone reset} ({@code ERASED}), puis passage au repli en cours de route
 *   ({@code LOAD_ERROR}, {@code NO_READY}) ; sur un serveur, sans la prise ni l'effacement.</li>
 *   <li>{@code perf} : images par seconde et tas après GC sans téléphone, téléphone ouvert 60 s, après fermeture,
 *   puis cinq cycles ; horodatages dans {@code etapes.txt} pour {@code tools/telephone-memoire.ps1}.</li>
 *   <li>{@code webdisplays} : le minePad de WebDisplays sur la page d'essai du mod, puis le téléphone, puis encore le
 *   minePad ; aucun message étranger au pont du téléphone.</li>
 *   <li>{@code reseau} (lot T2, tâche 8) : un téléphone configuré (code cliqué, confirmé au clavier), deux antennes
 *   posées par un vrai {@code useItemOn} (« Mairie », fondée par le joueur ; « Lagon », d'un fondateur fictif) contre
 *   un bloc d'énergie créative, une carte émise ; puis la barre d'état sans carte (verrouillé, déverrouillé au
 *   clavier), l'appli « Antenne », la feuille « Carte SIM » (demande de carte, carte glissée par un vrai clic), le mode
 *   avion, les barres à 150 et 300 blocs, l'écran verrouillé avec l'opérateur, puis le retrait de la carte.</li>
 * </ul>
 *
 * <p>{@code options.txt} n'est jamais réécrit (taille de fenêtre, échelle et plafond en mémoire seulement). La touche
 * F10 n'est jamais touchée ; aucune adresse externe n'est ouverte. Aucun code saisi n'est écrit au journal : le pont du
 * téléphone est suivi par le nom du message et ses clés seulement.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TelephoneShots {
    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-telephone");
    private static final String DOSSIER = System.getProperty("oneforallsurvival.telephoneShots");
    private static final boolean ACTIF = DOSSIER != null && !DOSSIER.isBlank();
    private static final String MODE = System.getProperty("oneforallsurvival.telephoneShots.mode", "parcours").trim();
    @Nullable private static final String SERVEUR = System.getProperty("oneforallsurvival.telephoneShots.server");
    private static final boolean SANS_PLAFOND = Boolean.getBoolean("oneforallsurvival.telephoneShots.sansPlafond");
    /** Langue du client pour la passe ({@code -PtelephoneShotsLangue=fr_fr}) : en mémoire seulement, jamais écrite. */
    @Nullable private static final String LANGUE = System.getProperty("oneforallsurvival.telephoneShots.langue");
    private static final String PAGE_PROP = "oneforallsurvival.phonePage";
    private static final String PAGE_DEMANDEE = System.getProperty(PAGE_PROP, "").trim();
    /** Une page de téléphone autre que la vraie : le repli est attendu à chaque ouverture, même avec MCEF. */
    private static final boolean PAGE_CASSEE = !PAGE_DEMANDEE.isEmpty() && !PAGE_DEMANDEE.equals("phone/index.html");
    private static final String SELFTEST = "mod://oneforallsurvival/web/selftest.html";
    static final String MONDE = "lostisland-essai-telephone";
    private static final double GARDE_S = 1500;
    private static final String CODE = "4821";
    private static final String NOUVEAU = "1357";
    private static final String FAUX = "0000";
    /** Le fond choisi dans l'assistant de la première ouverture (les Réglages passent ensuite à « nuit »). */
    private static final String FOND_ASSISTANT = "lagon";
    private static final double REPLI_MAX_S = 5.0;
    private static final double NO_READY_MAX_S = 6.5;
    /** Avant la mesure « avant » : chunks construits et JIT chaud (à 20 s, les images par seconde montaient encore). */
    private static final double STABILISATION_S = 90;
    private static final String FERME = "message.oneforallsurvival.phone.closed.";

    private enum Phase { TITRE, ENTREE, CONNEXION, JOUEUR, ETAPES, SORTIE, RETOUR, FIN }

    private record Pas(String nom, BooleanSupplier pret, Runnable action, BooleanSupplier fini, double delai,
            String image) {}

    private static final long DEBUT_NS = System.nanoTime();
    private static final List<String> ECHECS = new ArrayList<>();
    private static final List<String> IMAGES = new ArrayList<>();
    private static final List<Pas> PAS = new ArrayList<>();
    private static final Set<String> REUSSIES = new LinkedHashSet<>();
    private static final Map<String, Double> MESURES = new LinkedHashMap<>();
    private static Phase phase = Phase.TITRE;
    private static long debutPhaseNs = System.nanoTime();
    private static boolean fait;
    private static int indice;
    private static boolean joue;
    private static long debutPasNs;
    private static long debutAttenteNs;
    private static FilmCapture images;
    private static volatile boolean termineEcrit;
    private static boolean web;
    private static int ouvertures;
    private static int batterieAvant = -1;
    private static int batterieApresBaisse = -1;
    private static int caseDuTelephone;
    private static volatile String etatPrise = "";
    @Nullable private static Map<String, int[]> miseEnPageNotee;
    @Nullable private static volatile BlockPos prise;
    // Suivi de l'écran ouvert : dernière et avant-dernière mise en page, pont du téléphone.
    @Nullable private static Screen ecranSuivi;
    @Nullable private static Map<String, int[]> miseCourante;
    @Nullable private static Map<String, int[]> misePrecedente;
    private static long changementNs;
    private static boolean webVu;
    private static int messagesDuPont;
    private static int messagesEtrangers;
    private static final Map<String, Integer> SORTES_DU_PONT = new LinkedHashMap<>();
    // Performance : images comptées à chaque fin de rendu.
    private static long imagesRendues;
    private static long imagesDebut;
    private static long mesureDebutNs;
    private static long ouvertureNs;
    private static long dernierCranNs;
    private static long sansMondeNs;
    private static int readyAvant;

    static {
        if (ACTIF) {
            Thread chien = new Thread(() -> {
                try {
                    Thread.sleep((long) ((GARDE_S + 40) * 1000));
                    LOG.error("[telephone] FAIL délai de garde de la JVM dépassé : arrêt forcé");
                    ecrireTermine("garde de la JVM");
                    Runtime.getRuntime().halt(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "telephone-garde");
            chien.setDaemon(true);
            chien.start();
        }
    }

    private TelephoneShots() {}

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || phase == Phase.FIN) {
            return;
        }
        imagesRendues++;
        Minecraft mc = Minecraft.getInstance();
        if (secondes(DEBUT_NS) > GARDE_S) {
            echec("garde", "délai de garde dépassé à la phase " + phase);
            finir(mc);
            return;
        }
        try {
            suivre(mc);
            avancer(mc, secondes(debutPhaseNs));
        } catch (RuntimeException e) {
            LOG.error("[telephone] exception à la phase {}", phase, e);
            echec("exception-" + phase.name().toLowerCase(Locale.ROOT), e.toString());
            finir(mc);
        }
    }

    private static void avancer(Minecraft mc, double s) {
        switch (phase) {
            case TITRE -> {
                if (mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen) {
                    mc.tell(() -> mc.setScreen(new TitleScreen()));
                } else if (mc.getOverlay() == null && mc.screen instanceof TitleScreen) {
                    boolean mcef = ModList.get().isLoaded("mcef");
                    if (mcef && !WebAvailability.ready() && s < 240) {
                        return; // MCEF s'initialise à l'écran titre
                    }
                    mc.options.pauseOnLostFocus = false; // en mémoire seulement
                    if (LANGUE != null && !LANGUE.isBlank()) {
                        // Le choix du gestionnaire de langues seulement : options.languageCode n'est pas touché, et
                        // options.txt n'est pas réécrit.
                        mc.getLanguageManager().setSelected(LANGUE.trim());
                        mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
                        journal("langue du client : " + mc.getLanguageManager().getSelected() + " (en mémoire), "
                                + "options.languageCode " + mc.options.languageCode);
                    }
                    try {
                        images = new FilmCapture(mc.gameDirectory.toPath().resolve(DOSSIER), "Lost Island | téléphone");
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    web = WebAvailability.ready() && !PAGE_CASSEE;
                    journal("titre atteint ; mode " + MODE + (SERVEUR == null ? ", monde solo" : ", serveur " + SERVEUR)
                            + " ; mcef chargé " + mcef + ", webdisplays chargé " + ModList.get().isLoaded("webdisplays")
                            + ", disponibilité " + WebAvailability.failure() + ", page "
                            + (PAGE_DEMANDEE.isEmpty() ? "phone/index.html" : PAGE_DEMANDEE) + ", parcours "
                            + (web ? "web" : "repli") + " après " + String.format(Locale.ROOT, "%.1f s", s)
                            + " ; PID " + ProcessHandle.current().pid());
                    // Une passe neuve : les horodatages d'une passe précédente dans le même dossier sont effacés.
                    ecrireFichier("etapes.txt", "", false);
                    ecrireFichier("pid.txt", ProcessHandle.current().pid() + "\n", false);
                    passer(SERVEUR == null ? Phase.ENTREE : Phase.CONNEXION);
                } else if (s > 600) {
                    echec("titre", "titre jamais atteint (écran " + mc.screen + ")");
                    finir(mc);
                }
            }
            case ENTREE -> entrer(mc);
            case CONNEXION -> {
                if (!fait) {
                    fait = true;
                    Screen parent = mc.screen;
                    mc.tell(() -> ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(SERVEUR),
                            new ServerData("Lost Island (essai du téléphone)", SERVEUR, false), false));
                    journal("connexion à " + SERVEUR);
                    passer(Phase.JOUEUR);
                }
            }
            case JOUEUR -> {
                String hors = horsDuMonde(mc);
                if (hors != null) {
                    echec("connexion", hors);
                    finir(mc);
                } else if (mc.player != null && mc.level != null && mc.screen == null
                        && (SERVEUR != null || mc.getSingleplayerServer() != null)) {
                    mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                    mc.getTutorial().stop();
                    taille(mc, 1280, 720);
                    echelle(mc, 2);
                    switch (MODE) {
                        case "perf" -> composerPerf(mc);
                        case "webdisplays" -> composerWebDisplays(mc);
                        case "reseau" -> composerReseau(mc);
                        default -> composer(mc);
                    }
                    passer(Phase.ETAPES);
                } else if (s > 180) {
                    echec("monde", "joueur absent après 180 s (écran " + mc.screen + ")");
                    finir(mc);
                }
            }
            case ETAPES -> {
                String hors = horsDuMonde(mc);
                if (hors != null) {
                    echec("deconnexion", "pendant les étapes : " + hors);
                    finir(mc);
                    return;
                }
                etapes(mc);
            }
            case SORTIE -> {
                if (!fait) {
                    fait = true;
                    mc.tell(() -> {
                        if (mc.screen != null) {
                            mc.setScreen(null);
                        }
                        mc.level.disconnect();
                        mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
                        mc.setScreen(new TitleScreen());
                        passer(Phase.RETOUR);
                    });
                }
            }
            case RETOUR -> {
                if (mc.screen instanceof TitleScreen || s > 120) {
                    finir(mc);
                }
            }
            default -> {
            }
        }
    }

    private static void entrer(Minecraft mc) {
        if (fait) {
            return;
        }
        fait = true;
        Path monde = mc.gameDirectory.toPath().resolve("saves").resolve(MONDE);
        if (Files.isDirectory(monde)) {
            try (Stream<Path> tout = Files.walk(monde)) {
                for (Path p : tout.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            } catch (IOException e) {
                throw new IllegalStateException("monde d'essai non effacé : " + monde, e);
            }
            journal("ancien monde d'essai effacé");
        }
        mc.tell(() -> {
            LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                    acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                            .createWorldDimensions());
            passer(Phase.JOUEUR);
        });
    }

    // --- Le parcours -----------------------------------------------------------------------------------------------

    /** Le décor et le téléphone en main, chargé au pourcentage donné. */
    private static void decor(Minecraft mc, int pourcent) {
        pas("decor", () -> {
            if (SERVEUR != null) {
                // Serveur dédié : le joueur d'essai est opérateur (ops.json du serveur, ou op par RCON).
                mc.player.connection.sendCommand("gamemode creative");
                mc.player.connection.sendCommand("difficulty peaceful");
                mc.player.connection.sendCommand("clear @s");
            }
            mc.player.connection.sendCommand("time set noon");
            mc.player.connection.sendCommand("gamerule doDaylightCycle false");
            if (SERVEUR != null) {
                int fe = 72000 * pourcent / 100;
                mc.player.connection.sendCommand("give @s oneforallsurvival:phone{energy:" + fe + "}");
            } else {
                mc.player.connection.sendCommand("give @s oneforallsurvival:phone");
            }
        }, () -> attendu(1.5), 4, null);
        if (SERVEUR == null) {
            pas("telephone-en-main", () -> serveur(mc, (srv, sp) -> {
                int i = caseDe(sp, PhoneRegistry.PHONE.get());
                if (i < 0) {
                    return;
                }
                ItemStack phone = sp.getInventory().removeItemNoUpdate(i);
                PhoneItem.store(phone, PhoneConfig.capacity() * pourcent / 100);
                sp.setItemInHand(InteractionHand.MAIN_HAND, phone);
            }), () -> mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get())
                    && PhoneItem.stored(mc.player.getMainHandItem()) > 0, 5, null);
        } else {
            // Le /give arrive quand il arrive : la case est choisie (vraie molette) dès que le téléphone est là.
            pas("telephone-en-main", () -> {}, () -> {
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getItem(i).is(PhoneRegistry.PHONE.get())) {
                        mc.player.getInventory().selected = i;
                        break;
                    }
                }
                return mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get())
                        && PhoneItem.stored(mc.player.getMainHandItem()) > 0;
            }, 8, null);
        }
        pas("case-du-telephone", () -> {
            caseDuTelephone = mc.player.getInventory().selected;
            journal("téléphone dans la case " + caseDuTelephone + " de la barre, "
                    + PhoneItem.stored(mc.player.getMainHandItem()) + " FE");
        }, () -> true, 1, null);
    }

    private static void composer(Minecraft mc) {
        boolean solo = SERVEUR == null;
        decor(mc, 30);

        // Un téléphone neuf fermé au milieu de l'assistant (fond « nuit » choisi, pas de code) reste neuf : la
        // réouverture reprend l'assistant au début, fond par défaut.
        ouvrir(mc, "ouverture-0", Lock.SETUP, "00-bonjour");
        assistantJusquAuFond(mc, "assistant-0", "nuit", false);
        pause("00b-fond-nuit-choisi-puis-ferme");
        echap(mc, "echap-0");

        // Première ouverture : l'assistant en entier, choix du code par clics, confirmation au clavier.
        ouvrir(mc, "ouverture-1", Lock.SETUP, "01-bonjour");
        pas("ouverture-1-neuf", () -> {}, () -> {
            if (!fond("aube") || (web && !cible(mc, "wz-start"))) {
                echec("ouverture-1-neuf", "après fermeture au milieu de l'assistant : " + resume()
                        + (mc.screen instanceof PhoneWebScreen w ? ", mise en page " + w.rects().keySet() : ""));
            }
            return true;
        }, 2, null);
        assistantJusquAuFond(mc, "assistant", FOND_ASSISTANT, true);
        assistantVersLeCode(mc, "assistant", true);
        cliquerCode(mc, "choix", CODE);
        pause("02-confirmez-le-code");
        pas("confirmation-au-clavier", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED), 10, null);
        toutEstPret(mc, "assistant", FOND_ASSISTANT, "02b-tout-est-pret");
        pause("03-accueil");
        pas("reglages", () -> stable(mc, id("app-settings", "settings")), () -> {
            noterMiseEnPage(mc);
            cliquer(mc, id("app-settings", "settings"));
        }, () -> cible(mc, id("wp-nuit", "wallpaper")) && nouvelleMiseEnPage(mc), 10, null);
        pause("04-reglages");
        if (web) {
            clic(mc, "fond-nuit", "wp-nuit", () -> fond("nuit") && attendu(0.3), 10, null);
        } else {
            for (String f : List.of("jungle", "nuit")) { // depuis le fond de l'assistant (lagon)
                clic(mc, "fond-" + f, "wallpaper", () -> fond(f) && attendu(0.3), 10, null);
            }
        }
        clic(mc, "mode-avion", id("row-airplane", "airplane"),
                () -> PhoneClient.last().map(PhoneStatePacket::airplane).orElse(false), 10, null);
        pause("05-reglages-nuit-avion");
        changerLeCode(mc);
        echap(mc, "echap-1");

        // Réouverture : verrouillée, fond nuit.
        ouvrir(mc, "ouverture-2", Lock.LOCKED, "07-verrouille-nuit");
        pas("fond-nuit-verrouille", () -> {}, () -> fond("nuit") && etat(Lock.LOCKED), 2, null);
        pas("batterie-depart", () -> {
            batterieAvant = PhoneClient.last().get().battery();
            journal("batterie à la réouverture : " + batterieAvant + " %");
        }, () -> true, 1, null);
        faux(mc, 1, 4, true, "08-faux-1-1280x720-echelle2");
        if (solo) {
            // L'échelle 4 demande au moins 960 px de haut : la fenêtre grandit d'abord, puis l'échelle passe de 2 à 4.
            pas("fenetre-1920x1040", () -> taille(mc, 1920, 1040), () -> attendu(1.5) && cible(mc, "key-0"), 5, null);
            pause("09-fenetre-1920x1040-echelle2");
            faux(mc, 2, 3, true, "10-faux-2-1920x1040-echelle2");
            pas("echelle-4", () -> echelle(mc, 4), () -> attendu(1.5) && cible(mc, "key-0")
                    && mc.getWindow().getGuiScale() == 4, 5, null);
            pause("11-echelle-4");
            faux(mc, 3, 2, true, "12-faux-3-echelle4");
            pas("echelle-2-1280x720", () -> {
                taille(mc, 1280, 720);
                echelle(mc, 2);
            }, () -> attendu(1.5) && cible(mc, "key-0"), 5, null);
        } else {
            faux(mc, 2, 3, true, null);
            faux(mc, 3, 2, true, null);
        }
        faux(mc, 4, 1, false, "13-faux-4-au-clavier");
        faux(mc, 5, 0, true, null);
        pas("blocage", () -> {}, () -> PhoneClient.last().map(p -> p.lockoutSeconds() > 0).orElse(false)
                && attendu(1.0), 5, "14-blocage");
        pas("blocage-decompte", () -> {}, () -> attendu(3.0), 5, "15-blocage-3s-plus-tard");
        if (solo) {
            pas("batterie-baisse", () -> {}, () -> {
                int b = PhoneClient.last().map(PhoneStatePacket::battery).orElse(batterieAvant);
                if (b < batterieAvant && attendu(1.0)) {
                    batterieApresBaisse = b;
                    return true;
                }
                return false;
            }, 75, "16-batterie-baisse");
            pas("batterie-baisse-releve", () -> journal("batterie : " + batterieAvant + " % puis "
                    + batterieApresBaisse + " % écran allumé"), () -> true, 1, null);
        }
        echap(mc, "echap-2");

        if (solo) {
            prise(mc);
            effacement(mc);
        }
        telephoneHorsMain(mc);
        resetParLEquipe(mc);
        pas("bilan-avis", () -> bilanAvis(mc, web ? 0 : 1), () -> true, 1, "20-chat");
        if (solo && web) {
            repliEnCoursDeRoute(mc);
        }
        // Avec MCEF, le repli en cours de route a donné un seul avis ; sans MCEF ou page cassée, un seul depuis le début.
        pas("bilan-avis-final", () -> bilanAvis(mc, web && !solo ? 0 : 1), () -> true, 1, null);
        bilanDuPont();
    }

    // --- Le réseau (lot T2, tâche 8) ---------------------------------------------------------------------------------

    private static final String MAIRIE = "Mairie";
    private static final String LAGON = "Lagon";
    @Nullable private static volatile BlockPos antenneMairie;
    @Nullable private static volatile BlockPos antenneLagon;
    private static volatile String idLagon = "";
    private static volatile String idMairie = "";
    private static volatile String etatCarte = "";
    private static volatile int fileRemplie = -1;
    private static volatile String reseauNote = "";
    private static volatile boolean antennesEnService;
    private static volatile boolean carteEmise;
    @Nullable private static volatile Vec3 departReseau;

    /**
     * La vue réseau en clair pour le journal, sans le numéro ni le titulaire de la carte d'essai (on ne dit que s'ils
     * sont là) : un numéro n'est jamais écrit dans un journal (revue de la tâche 8, M7).
     */
    private static String resumeReseau(PhoneNetView n) {
        return "sim=" + n.sim() + ", opérateur=" + n.operator() + ", barres=" + n.bars() + ", numéro "
                + (n.number().isEmpty() ? "absent" : "présent") + ", titulaire "
                + (n.holder().isEmpty() ? "absent" : "présent") + ", appli=" + n.founderApp() + ", cartes="
                + n.choices().size() + ", opérateurs=" + n.requestable().size();
    }

    /** La vue réseau du dernier état reçu. */
    private static PhoneNetView vueReseau() {
        return PhoneClient.last().map(PhoneStatePacket::net).orElse(PhoneNetView.NONE);
    }

    private static void composerReseau(Minecraft mc) {
        if (SERVEUR != null) {
            echec("reseau", "le mode reseau se joue en monde solo (gestes du serveur intégré)");
            return;
        }
        decor(mc, 80);
        // Un téléphone neuf configuré : code cliqué, confirmé au clavier.
        ouvrir(mc, "reseau-ouverture-0", Lock.SETUP, null);
        assistantJusquAuFond(mc, "reseau-assistant", "aube", false);
        assistantVersLeCode(mc, "reseau-assistant", false);
        cliquerCode(mc, "reseau-choix", CODE);
        pas("reseau-confirmation-au-clavier", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED), 10, null);
        toutEstPret(mc, "reseau-assistant", "aube", null);
        pas("reseau-sans-carte", () -> {}, () -> {
            PhoneNetView n = vueReseau();
            if (n.sim() != PhoneSims.Validity.NONE || n.founderApp()) {
                echec("reseau-sans-carte", "avant toute antenne : " + resumeReseau(n));
            }
            return true;
        }, 2, "r01-sans-carte-accueil");
        echap(mc, "reseau-echap-0");

        // Deux antennes posées par un vrai clic, contre un bloc d'énergie créative ; Mairie fondée par le joueur.
        antennes(mc);
        pas("reseau-carte-emise", () -> serveur(mc, (srv, sp) -> {
            Optional<PhoneOperator> mairie = PhoneOperators.get(srv).byFounder(sp.getUUID());
            if (mairie.isEmpty()) {
                return;
            }
            carteEmise = PhoneSims.issueTo(sp, mairie.get(), sp.getUUID(), sp.getGameProfile().getName(),
                    EconomyClock.now()).isPresent();
        }), () -> carteEmise, 5, null);

        // Verrouillé, sans carte : « Pas de SIM », rien d'autre ; déverrouillé au clavier.
        ouvrir(mc, "reseau-ouverture-1", Lock.LOCKED, "r02-verrouille-pas-de-sim");
        pas("reseau-verrouille-pas-de-sim", () -> {}, () -> {
            PhoneNetView n = vueReseau();
            journal("verrouillé sans carte : " + resumeReseau(n));
            if (n.sim() != PhoneSims.Validity.NONE || !n.number().isEmpty() || !n.choices().isEmpty()
                    || !n.requestable().isEmpty() || n.founderApp()) {
                echec("reseau-verrouille-pas-de-sim", "vue verrouillée trop bavarde : " + resumeReseau(n));
            }
            return true;
        }, 2, null);
        pas("reseau-deverrouillage-clavier", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, id("app-antenna", "app-antenna")), 10, null);
        pause("r03-accueil-icone-antenne");
        clic(mc, "reseau-appli-antenne", id("app-antenna", "app-antenna"),
                () -> PhoneClient.last().map(p -> p.last() == Result.NO_NETWORK).orElse(false), 10, null);
        pause("r04-appli-antenne-reponse");
        if (web) {
            clic(mc, "reseau-appli-antenne-ok", "alert-0", () -> stable(mc, "app-settings") && attendu(0.5), 10, null);
        }

        // La feuille « Carte SIM » : demande de carte à Lagon, puis la carte de Mairie glissée.
        clic(mc, "reseau-reglages", id("app-settings", "settings"), () -> cible(mc, id("row-sim", "sim")), 10, null);
        clic(mc, "reseau-feuille-sim", id("row-sim", "sim"), () -> cible(mc, "sim-request")
                && carteDansLaFeuille(mc) != null, 10, null);
        pause("r05-feuille-sim-sans-carte");
        clic(mc, "reseau-demander", "sim-request", () -> cible(mc, "request-" + idLagon), 10, null);
        pause("r06-liste-des-demandes");
        taillesDuRepli(mc, "r06b-liste-des-demandes");
        pas("reseau-demande-envoyee", () -> stable(mc, "request-" + idLagon), () -> cliquer(mc, "request-" + idLagon),
                () -> PhoneClient.last().map(p -> p.last() == Result.SIM_REQUESTED).orElse(false), 10,
                "r07-demande-envoyee");
        pas("reseau-demande-inscrite", () -> serveur(mc, (srv, sp) -> reseauNote = "demandes de Lagon : "
                + PhoneOperators.get(srv).requestsOf(idLagon).size()), () -> !reseauNote.isEmpty() && attendu(0.3), 3,
                null);
        pas("reseau-demande-relevee", () -> journal(reseauNote), () -> true, 1, null);
        pause(null);
        pas("reseau-file-remplie", () -> serveur(mc, (srv, sp) -> {
            PhoneOperators registre = PhoneOperators.get(srv);
            int n = 0;
            while (n < 10_000 && registre.request(idMairie, java.util.UUID.randomUUID(), "Demandeur" + n,
                    EconomyClock.now()).isPresent()) {
                n++;
            }
            fileRemplie = registre.requestsOf(idMairie).size();
        }), () -> fileRemplie > 0, 5, null);
        pas("reseau-file-relevee", () -> journal("file de Mairie remplie : " + fileRemplie + " demande(s)"),
                () -> true, 1, null);
        pas("reseau-file-pleine", () -> stable(mc, "request-" + idMairie), () -> cliquer(mc, "request-" + idMairie),
                () -> PhoneClient.last().map(p -> p.last() == Result.REQUESTS_FULL).orElse(false) && attendu(0.8), 10,
                "r07b-file-pleine");
        if (web) {
            clic(mc, "reseau-file-pleine-ok", "alert-0", () -> stable(mc, "sim-request") && attendu(0.5), 10, null);
        }
        taillesDuRepli(mc, "r07c-file-pleine");
        pas("reseau-glisser", () -> carteDansLaFeuille(mc) != null && stable(mc, carteDansLaFeuille(mc)),
                () -> cliquer(mc, carteDansLaFeuille(mc)), () -> {
                    PhoneNetView n = vueReseau();
                    return n.sim() == PhoneSims.Validity.ACTIVE && MAIRIE.equals(n.operator()) && n.bars() == 4;
                }, 10, null);
        pause("r08-carte-inseree-4-barres");
        pas("reseau-ligne", () -> {}, () -> {
            PhoneNetView n = vueReseau();
            journal("ligne : " + resumeReseau(n));
            if (n.number().isEmpty() || !mc.player.getGameProfile().getName().equals(n.holder())) {
                echec("reseau-ligne", "numéro ou titulaire absents déverrouillé : " + resumeReseau(n));
            }
            return true;
        }, 2, null);
        pas("reseau-demande-grisee", () -> {}, () -> {
            if (cible(mc, "sim-request")) {
                echec("reseau-demande-grisee", "« Demander une carte » proposée avec une carte active");
            }
            return true;
        }, 2, null);
        taillesDuRepli(mc, "r08a-carte-inseree");
        etatDeLaCarte(mc, "reseau-suspendue", dev.oneforallsurvival.phone.net.SimCard.State.SUSPENDED,
                PhoneSims.Validity.SUSPENDED, "r08b-carte-suspendue");
        taillesDuRepli(mc, "r08c-carte-suspendue");
        etatDeLaCarte(mc, "reseau-reactivee", dev.oneforallsurvival.phone.net.SimCard.State.ACTIVE,
                PhoneSims.Validity.ACTIVE, null);
        pas("reseau-reactivee-barres", () -> {}, () -> vueReseau().bars() == 4, 5, null);
        clic(mc, "reseau-fermer-feuille", id("sim-close", "back"), () -> cible(mc, id("row-airplane", "airplane")),
                10, null);

        // Mode avion : 0 barre et « Mode avion » à leur place ; puis retour.
        clic(mc, "reseau-mode-avion", id("row-airplane", "airplane"),
                () -> PhoneClient.last().map(p -> p.airplane() && p.net().bars() == 0).orElse(false), 10, null);
        pause("r09-mode-avion");
        clic(mc, "reseau-mode-avion-coupe", id("row-airplane", "airplane"),
                () -> PhoneClient.last().map(p -> !p.airplane() && p.net().bars() == 4).orElse(false), 25, null);

        // Les barres suivent le joueur (relevé du serveur toutes les 20 tics) : 2 barres à 150 blocs, 0 à 300.
        deplacer(mc, "reseau-150-blocs", 150, 2, "r10-deux-barres");
        deplacer(mc, "reseau-300-blocs", 300, 0, "r11-zero-barre");
        deplacer(mc, "reseau-retour", 0, 4, null);
        echap(mc, "reseau-echap-1");

        // Verrouillé avec une carte : l'opérateur et les barres, ni numéro ni titulaire.
        ouvrir(mc, "reseau-ouverture-2", Lock.LOCKED, "r12-verrouille-operateur");
        pas("reseau-verrouille-operateur", () -> {}, () -> {
            PhoneNetView n = vueReseau();
            journal("verrouillé avec carte : " + resumeReseau(n));
            if (n.sim() != PhoneSims.Validity.ACTIVE || !MAIRIE.equals(n.operator()) || n.bars() != 4
                    || !n.number().isEmpty() || !n.holder().isEmpty() || !n.choices().isEmpty() || n.founderApp()) {
                echec("reseau-verrouille-operateur", "vue verrouillée : " + resumeReseau(n));
            }
            return true;
        }, 2, null);

        // Retrait par la feuille.
        pas("reseau-deverrouillage-2", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, id("app-settings", "settings")), 10, null);
        clic(mc, "reseau-reglages-2", id("app-settings", "settings"), () -> cible(mc, id("row-sim", "sim")), 10, null);
        clic(mc, "reseau-feuille-sim-2", id("row-sim", "sim"), () -> cible(mc, "sim-remove"), 10, null);
        pause("r13-feuille-sim-carte-active");
        etatDeLaCarte(mc, "reseau-desactivee", dev.oneforallsurvival.phone.net.SimCard.State.DEACTIVATED,
                PhoneSims.Validity.DEACTIVATED, "r13b-carte-desactivee");
        taillesDuRepli(mc, "r13c-carte-desactivee");
        clic(mc, "reseau-retirer", "sim-remove", () -> {
            PhoneNetView n = vueReseau();
            return n.sim() == PhoneSims.Validity.NONE
                    && PhoneClient.last().map(p -> p.last() == Result.SIM_REMOVED || p.last() == Result.NONE)
                    .orElse(false);
        }, 10, null);
        pause("r14-carte-retiree");
        deplacer(mc, "reseau-loin-pour-la-liste", 300, 0, null);
        pas("reseau-loin-releve", () -> {}, () -> vueReseau().requestable().isEmpty() && attendu(1.5), 8, null);
        clic(mc, "reseau-liste-vide", "sim-request", () -> (web || (mc.screen instanceof PhoneFallbackScreen r
                        && r.widgets().containsKey("request-none"))) && attendu(1.0), 10,
                "r15-aucun-operateur");
        taillesDuRepli(mc, "r15b-aucun-operateur");
        deplacer(mc, "reseau-retour-2", 0, 0, null);
        echap(mc, "reseau-echap-2");
        bilanDuPont();
    }

    /**
     * Le serveur passe la carte du joueur à {@code etat} (comme le fera le panneau de l'opérateur, tâche 9) ; la vue
     * reçue (relevé toutes les 20 tics) doit porter {@code vu}.
     */
    private static void etatDeLaCarte(Minecraft mc, String nom, dev.oneforallsurvival.phone.net.SimCard.State etat,
            PhoneSims.Validity vu, @Nullable String image) {
        pas(nom, () -> serveur(mc, (srv, sp) -> {
            PhoneOperators registre = PhoneOperators.get(srv);
            etatCarte = registre.cardsOf(idMairie).stream().filter(c -> sp.getUUID().equals(c.holder())).findFirst()
                    .map(c -> registre.setState(c.simId(), etat) ? etat.name() : "refusé").orElse("aucune carte");
        }), () -> vueReseau().sim() == vu && attendu(1.0), 8, image);
        pas(nom + "-releve", () -> journal(nom + " : registre " + etatCarte + ", vue " + resumeReseau(vueReseau())),
                () -> true, 1, null);
    }

    /**
     * Repli seulement : la même feuille à l'interface de 320 × 240 (fenêtre 640 × 480, échelle 2) et à une grande
     * interface (fenêtre 1920 × 1040, échelle 2 : 960 × 520), puis retour à 1280 × 720. Rien n'est écrit dans
     * {@code options.txt}.
     */
    private static void taillesDuRepli(Minecraft mc, String image) {
        if (web) {
            return;
        }
        pas(image + "-320x240", () -> {
            taille(mc, 640, 480);
            echelle(mc, 2);
        }, () -> attendu(1.5) && mc.getWindow().getGuiScaledWidth() == 320, 6, image + "-320x240");
        pas(image + "-960x520", () -> {
            taille(mc, 1920, 1040);
            echelle(mc, 2);
        }, () -> attendu(1.5) && mc.getWindow().getGuiScaledWidth() == 960, 6, image + "-960x520");
        pas(image + "-retour", () -> {
            taille(mc, 1280, 720);
            echelle(mc, 2);
        }, () -> attendu(1.5) && mc.getWindow().getGuiScaledWidth() == 640, 6, null);
    }

    /** L'identifiant de la première carte de l'inventaire proposée par la feuille ({@code sim-choice-<case>}), ou nul. */
    @Nullable
    private static String carteDansLaFeuille(Minecraft mc) {
        List<PhoneSims.SimChoice> cartes = vueReseau().choices();
        return cartes.isEmpty() ? null : "sim-choice-" + cartes.get(0).slot();
    }

    /**
     * Les deux antennes : un bloc d'énergie créative posé par le serveur, puis l'antenne tenue en main posée contre lui
     * par un vrai {@code useItemOn} sur le sol. Mairie : fondée par le joueur (le nom se donne par le serveur, le
     * panneau arrive à la tâche 9). Lagon : un opérateur d'un fondateur fictif, que l'objet porte ({@code markOperator}).
     */
    private static void antennes(Minecraft mc) {
        pas("reseau-depart", () -> serveur(mc, (srv, sp) -> {
            departReseau = sp.position();
            BlockPos pied = sp.blockPosition();
            Block creatif = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createaddition", "creative_energy"));
            boolean pack = creatif != null && creatif != Blocks.AIR;
            sp.serverLevel().setBlockAndUpdate(pied.east(3), pack ? creatif.defaultBlockState()
                    : Blocks.STONE.defaultBlockState());
            sp.serverLevel().setBlockAndUpdate(pied.west(3), pack ? creatif.defaultBlockState()
                    : Blocks.STONE.defaultBlockState());
            PhoneOperators registre = PhoneOperators.get(srv);
            PhoneOperators.Result lagon = registre.found(java.util.UUID.randomUUID(), "Fondatrice", LAGON,
                    EconomyClock.now());
            if (lagon.founded()) {
                idLagon = lagon.operator().id();
            }
            ItemStack mairie = new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
            sp.getInventory().setItem(7, mairie);
            ItemStack autre = new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
            if (lagon.founded()) {
                AntennaItem.markOperator(autre, lagon.operator());
            }
            sp.getInventory().setItem(8, autre);
            antenneMairie = pied.east(2);
            antenneLagon = pied.west(2);
            journal("énergie : " + (pack ? "createaddition:creative_energy" : "pierre (pack absent)")
                    + " ; Lagon " + lagon.status());
        }), () -> antenneLagon != null && !idLagon.isEmpty()
                && mc.player.getInventory().getItem(7).is(PhoneRegistry.ANTENNA_ITEM.get())
                && mc.player.getInventory().getItem(8).is(PhoneRegistry.ANTENNA_ITEM.get()), 5, null);
        poserAntenne(mc, "reseau-pose-mairie", 7, () -> antenneMairie);
        pas("reseau-fonder-mairie", () -> serveur(mc, (srv, sp) -> {
            Optional<PhoneOperators.Result> r = AntennaBlock.found(sp, antenneMairie, MAIRIE);
            reseauNote = "fondation de Mairie : " + r.map(x -> x.status().name()).orElse("refusée");
            r.filter(PhoneOperators.Result::founded).ifPresent(x -> idMairie = x.operator().id());
        }), () -> reseauNote.startsWith("fondation") && attendu(0.3), 5, null);
        pas("reseau-fondation-relevee", () -> journal(reseauNote), () -> true, 1, null);
        poserAntenne(mc, "reseau-pose-lagon", 8, () -> antenneLagon);
        pas("reseau-telephone-en-main", () -> mc.player.getInventory().selected = caseDuTelephone,
                () -> mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get()) && attendu(0.3), 5, null);
        pas("reseau-antennes-en-service", () -> {}, () -> {
            serveur(mc, (srv, sp) -> antennesEnService = enService(sp.serverLevel(), antenneMairie)
                    && enService(sp.serverLevel(), antenneLagon));
            return antennesEnService;
        }, 20, "r00-antennes");
    }

    private static boolean enService(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AntennaBlockEntity be) || be.antennaId() == null) {
            return false;
        }
        return PhoneOperators.get(level.getServer()).antenna(be.antennaId())
                .map(r -> AntennaBlockEntity.inService(r, level.getGameTime())).orElse(false);
    }

    /** L'antenne de la case {@code slot} de la barre, posée par un vrai clic sur le sol sous {@code ou}. */
    private static void poserAntenne(Minecraft mc, String nom, int slot, java.util.function.Supplier<BlockPos> ou) {
        pas(nom, () -> {
            mc.player.getInventory().selected = slot;
            BlockPos sol = ou.get().below();
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false));
        }, () -> mc.level.getBlockState(ou.get()).is(PhoneRegistry.ANTENNA.get()) && attendu(0.5), 5, null);
    }

    /**
     * Le joueur est replacé à {@code dx} blocs à l'est du départ (le serveur le téléporte) ; le relevé du signal doit
     * donner {@code barres} barres dans les 3 s (20 tics par relevé).
     */
    private static void deplacer(Minecraft mc, String nom, int dx, int barres, @Nullable String image) {
        pas(nom, () -> serveur(mc, (srv, sp) -> {
            Vec3 d = departReseau;
            sp.teleportTo(sp.serverLevel(), d.x + dx, d.y, d.z, sp.getYRot(), sp.getXRot());
        }), () -> vueReseau().bars() == barres && attendu(1.0), 8, image);
        pas(nom + "-releve", () -> journal(nom + " : " + resumeReseau(vueReseau())), () -> true, 1, null);
    }

    /** La prise : un bloc d'énergie créative comme mur, la prise contre lui, le téléphone branché puis repris. */
    private static void prise(Minecraft mc) {
        pas("prise-posee", () -> poserLaPrise(mc), () -> prise != null && attendu(1.0), 5, null);
        pas("brancher", () -> utiliserLaPrise(mc), () -> mc.player.getMainHandItem().isEmpty()
                && mc.level.getBlockState(prise).getValue(WallSocketBlock.PLUGGED), 5, "17-branche");
        pas("charge", () -> {}, () -> {
            lireLaPrise(mc);
            return attendu(8.0);
        }, 12, "18-en-charge");
        pas("charge-releve", () -> journal("prise : " + etatPrise), () -> true, 1, null);
        pas("reprendre", () -> utiliserLaPrise(mc), () -> mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get()),
                5, null);
        ouvrir(mc, "ouverture-3", Lock.LOCKED, "19-batterie-remontee");
        pas("batterie-remontee", () -> {}, () -> {
            int b = PhoneClient.last().map(PhoneStatePacket::battery).orElse(-1);
            journal("batterie après la prise : " + b + " % (avant la prise : " + batterieApresBaisse + " %)");
            if (b <= batterieApresBaisse) {
                echec("batterie-remontee", "batterie " + b + " % après la prise, " + batterieApresBaisse + " % avant");
            }
            return true;
        }, 2, null);
    }

    /**
     * Effacement depuis l'écran : la fin du blocage attendue, déverrouillage, Réglages, Effacer, code demandé,
     * confirmation ; le serveur ferme l'écran ({@code ERASED}) ; la réouverture revient au choix du code.
     * L'écran est encore ouvert, verrouillé et bloqué, en entrant.
     */
    private static void effacement(Minecraft mc) {
        pas("fin-du-blocage", () -> {}, () -> stable(mc, "key-0") && attendu(1.0), 75, "21-fin-du-blocage");
        pas("fin-du-blocage-marge", () -> {}, () -> attendu(1.5), 3, null);
        cliquerCode(mc, "deverrouillage", NOUVEAU);
        pas("deverrouille", () -> {}, () -> etat(Lock.UNLOCKED) && cible(mc, id("app-settings", "settings")), 10,
                null);
        clic(mc, "reglages-2", id("app-settings", "settings"), () -> cible(mc, id("row-code", "erase")), 10, null);
        pause(null);
        if (web) {
            // « Effacer le téléphone » est en bas de la liste des Réglages : vraie molette au milieu de l'écran, un cran
            // à la fois, jusqu'à ce que la ligne paraisse dans la mise en page.
            pas("defiler-reglages", () -> {}, () -> {
                if (cible(mc, "row-erase")) {
                    return attendu(0.3);
                }
                if (System.nanoTime() - dernierCranNs < 250_000_000L) {
                    return false;
                }
                dernierCranNs = System.nanoTime();
                Rect f = ((PhoneWebScreen) mc.screen).frameRect();
                double x = f.x() + f.width() / 2.0;
                double y = f.y() + f.height() / 2.0;
                mc.screen.mouseMoved(x, y);
                mc.screen.mouseScrolled(x, y, -1.0);
                return false;
            }, 10, "21b-reglages-bas");
            clic(mc, "effacer", "row-erase", () -> cible(mc, "sheet-cancel") && cible(mc, "key-1"), 10, null);
            pause("22a-effacer-code-demande");
            cliquerCode(mc, "effacement", NOUVEAU);
            pas("effacement-confirmation", () -> {}, () -> stable(mc, "alert-1"), 10, null);
            pause("22-effacer-confirmation");
            clic(mc, "effacement-confirme", "alert-1", () -> fermeePar(mc, CloseReason.ERASED), 10, null);
        } else {
            pas("code-pour-effacer", () -> stable(mc, "old"), () -> remplir(mc, "old", NOUVEAU), () -> attendu(0.5), 3,
                    null);
            clic(mc, "effacer-arme", "erase", () -> attendu(0.5), 3, "22-effacer-confirmation");
            clic(mc, "effacement-confirme", "erase", () -> fermeePar(mc, CloseReason.ERASED), 10, null);
        }
        messageDeFermeture("effacement-ferme", CloseReason.ERASED, "23-efface-message");
        ouvrir(mc, "reouverture-choix-du-code", Lock.SETUP, "24-apres-effacement-bonjour");
        assistantJusquAuFond(mc, "assistant-2", "jungle", false);
        assistantVersLeCode(mc, "assistant-2", false);
        cliquerCode(mc, "rechoix", CODE);
        pause(null);
        cliquerCode(mc, "rechoix-confirmation", CODE);
        toutEstPret(mc, "assistant-2", "jungle", null);
        pas("re-deverrouille", () -> {}, () -> etat(Lock.UNLOCKED) && cible(mc, id("app-settings", "settings")), 10,
                null);
        echap(mc, "echap-4");
    }

    /** Le téléphone quitte la main, écran ouvert (vraie molette : case suivante) : le serveur ferme en {@code PHONE_GONE}. */
    private static void telephoneHorsMain(Minecraft mc) {
        ouvrir(mc, "ouverture-hors-main", null, null);
        pas("telephone-hors-main", () -> mc.player.getInventory().selected = (caseDuTelephone + 1) % 9,
                () -> fermeePar(mc, CloseReason.PHONE_GONE), 10, null);
        messageDeFermeture("telephone-hors-main-message", CloseReason.PHONE_GONE, "25-telephone-hors-main");
        pas("telephone-en-main-retour", () -> mc.player.getInventory().selected = caseDuTelephone,
                () -> mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get()) && attendu(0.5), 5, null);
    }

    /** {@code /phone reset} de l'équipe, écran ouvert : le serveur ferme en {@code ERASED} ; réouverture au choix du code. */
    private static void resetParLEquipe(Minecraft mc) {
        ouvrir(mc, "ouverture-reset", null, null);
        pas("reset-equipe", () -> mc.player.connection.sendCommand("phone reset"),
                () -> fermeePar(mc, CloseReason.ERASED), 10, null);
        messageDeFermeture("reset-equipe-ferme", CloseReason.ERASED, "26-reset-equipe");
        ouvrir(mc, "ouverture-apres-reset", Lock.SETUP, "27-apres-reset-bonjour");
        if (web) {
            adresseNonPermise(mc);
        }
        echap(mc, "echap-5");
    }

    /**
     * La page part vers une adresse non permise (locale : {@code about:blank#hors-page}, jamais une page externe) ;
     * {@code McefHooks.onAddressChange} doit la ramener à la page du téléphone, à la tâche suivante, sans plantage :
     * la page renvoie {@code ready} et l'écran reste celui du téléphone, sur le même état.
     */
    private static void adresseNonPermise(Minecraft mc) {
        pas("adresse-non-permise", () -> {
            readyAvant = SORTES_DU_PONT.getOrDefault("ready", 0);
            executerDansLaPage((PhoneWebScreen) mc.screen, "location.href = 'about:blank#hors-page';");
        }, () -> {
            if (!(mc.screen instanceof PhoneWebScreen w) || SORTES_DU_PONT.getOrDefault("ready", 0) <= readyAvant
                    || !cible(mc, "wz-start") || !attendu(1.0)) {
                return false;
            }
            journal("adresse non permise : la page est revenue (signal ready "
                    + SORTES_DU_PONT.getOrDefault("ready", 0) + ", avant " + readyAvant + "), état " + w.state().lock());
            return w.state().lock() == Lock.SETUP;
        }, 10, "27b-retour-apres-adresse-non-permise");
    }

    /** Un script dans la page de l'écran web, par le chemin de l'écran lui-même ({@code WebScreen.run}). */
    private static void executerDansLaPage(PhoneWebScreen w, String script) {
        try {
            java.lang.reflect.Method run = WebScreen.class.getDeclaredMethod("run", String.class);
            run.setAccessible(true);
            run.invoke(w, script);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("script non envoyé à la page : " + e);
        }
    }

    /**
     * Repli en cours de route (MCEF chargé, page qui casse) : une page absente ({@code LOAD_ERROR}), puis une page sans
     * signal {@code ready} ({@code NO_READY}) ; le repli s'ouvre sur le même état, un seul avis en tout.
     */
    private static void repliEnCoursDeRoute(Minecraft mc) {
        pas("page-absente", () -> System.setProperty(PAGE_PROP, "phone/absente.html"), () -> true, 1, null);
        ouvrirVersLeRepli(mc, "repli-load-error", REPLI_MAX_S, "28-repli-load-error");
        echap(mc, "echap-6");
        pas("page-sans-ready", () -> System.setProperty(PAGE_PROP, "phone/phone.css"), () -> true, 1, null);
        ouvrirVersLeRepli(mc, "repli-no-ready", NO_READY_MAX_S, "29-repli-no-ready");
        echap(mc, "echap-7");
        pas("page-normale", () -> System.clearProperty(PAGE_PROP), () -> true, 1, null);
    }

    /** Ouvre le téléphone sur une page qui casse : l'écran web d'abord, puis le repli sur le même état. */
    private static void ouvrirVersLeRepli(Minecraft mc, String nom, double max, String image) {
        pas(nom, () -> {
            ouvertures++;
            webVu = false;
            ouvertureNs = System.nanoTime();
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> mc.screen instanceof PhoneFallbackScreen && verifierLeRepli(mc, nom, max, true), max + 5, null);
        pas(nom + "-ecran", () -> {}, () -> attendu(0.8), 3, image);
    }

    /** Le repli ouvert : délai depuis l'usage, même état que le dernier reçu, écran web vu d'abord si demandé. */
    private static boolean verifierLeRepli(Minecraft mc, String nom, double max, boolean webAttenduDAbord) {
        PhoneFallbackScreen r = (PhoneFallbackScreen) mc.screen;
        double delai = secondes(ouvertureNs);
        PhoneStatePacket montre = r.state();
        PhoneStatePacket dernier = PhoneClient.last().orElse(null);
        boolean meme = dernier != null && montre.session() == dernier.session() && montre.lock() == dernier.lock()
                && montre.battery() == dernier.battery() && montre.wallpaper().equals(dernier.wallpaper());
        journal(String.format(Locale.ROOT, "%s : repli ouvert %.2f s après l'usage, écran web vu d'abord %s, état "
                + "montré %s %s %d %%, dernier reçu %s", nom, delai, webVu, montre.lock(), montre.wallpaper(),
                montre.battery(), resume()));
        if (delai > max) {
            echec(nom, String.format(Locale.ROOT, "repli après %.2f s, au plus %.1f s attendu", delai, max));
        }
        if (!meme) {
            echec(nom, "le repli ne montre pas le dernier état reçu");
        }
        if (webAttenduDAbord && !webVu) {
            echec(nom, "l'écran web n'a pas été ouvert avant le repli");
        }
        return true;
    }

    // --- Performance -----------------------------------------------------------------------------------------------

    private static void composerPerf(Minecraft mc) {
        decor(mc, 100);
        pas("perf-reglages", () -> {
            if (SANS_PLAFOND) {
                // En mémoire seulement : options.txt n'est pas réécrit.
                mc.options.enableVsync().set(false);
                mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(260);
                mc.getWindow().setFramerateLimit(260);
            }
            var f = mc.getWindow();
            journal(String.format(Locale.ROOT, "perf réglages : fenêtre %dx%d, échelle %.0f, synchro verticale %s, "
                    + "plafond %d img/s%s, distance de rendu %d, graphismes %s, Xmx %d Mo", f.getWidth(), f.getHeight(),
                    f.getGuiScale(), mc.options.enableVsync().get(), mc.options.framerateLimit().get(),
                    mc.options.framerateLimit().get() >= 260 ? " (illimité)" : "", mc.options.renderDistance().get(),
                    mc.options.graphicsMode().get(), Runtime.getRuntime().maxMemory() / (1024 * 1024)));
            serveur(mc, (srv, sp) -> sp.teleportTo(sp.serverLevel(), 0.5, sp.getY(), 0.5, 0f, 15f));
        }, () -> attendu(STABILISATION_S), STABILISATION_S + 5, "p01-avant");
        pas("perf-debut", () -> etape("perf-debut"), () -> true, 1, null);
        mesure("perf-avant-temoin", 30); // témoin : la mesure « avant » ne bouge plus d'une fenêtre à l'autre
        mesure("perf-avant", 30);
        tas("perf-tas-avant");
        ouvrirPerf(mc, "perf-ouverture", "p02-pendant");
        mesure("perf-pendant", 60);
        echap(mc, "perf-fermeture");
        mesure("perf-apres", 30);
        tas("perf-tas-apres");
        for (int n = 1; n <= 5; n++) {
            ouvrirPerf(mc, "perf-cycle-" + n + "-ouverture", null);
            pas("perf-cycle-" + n + "-ouvert", () -> {}, () -> attendu(10.0), 12, null);
            echap(mc, "perf-cycle-" + n + "-fermeture");
            pas("perf-cycle-" + n + "-ferme", () -> {}, () -> attendu(10.0), 12, null);
            tas("perf-cycle-" + n + "-tas");
        }
        pas("perf-bilan", () -> {
            double avant = MESURES.getOrDefault("perf-avant", 0.0);
            double pendant = MESURES.getOrDefault("perf-pendant", 0.0);
            double apres = MESURES.getOrDefault("perf-apres", 0.0);
            double tasAvant = MESURES.getOrDefault("perf-tas-avant", 0.0);
            double tasApres = MESURES.getOrDefault("perf-tas-apres", 0.0);
            double[] cycles = new double[5];
            for (int n = 1; n <= 5; n++) {
                cycles[n - 1] = MESURES.getOrDefault("perf-cycle-" + n + "-tas", 0.0);
                journal(String.format(Locale.ROOT, "perf cycle=%d tas=%.1f", n, cycles[n - 1]));
            }
            journal(String.format(Locale.ROOT, "perf avant=%.1f pendant=%.1f apres=%.1f tas_avant=%.1f "
                    + "tas_apres=%.1f (img/s ; Mo après GC)", avant, pendant, apres, tasAvant, tasApres));
            journal(String.format(Locale.ROOT, "perf rapports : pendant %.1f %% d'avant, après %.1f %% ; tas %+.1f Mo ;"
                    + " %+.1f Mo par ouverture sur cinq", 100 * pendant / avant, 100 * apres / avant,
                    tasApres - tasAvant, (cycles[4] - tasApres) / 5));
            List<String> manques = TelephoneBilan.perf(avant, pendant, apres, tasAvant, tasApres, cycles);
            if (manques.isEmpty()) {
                journal("perf : tous les seuils tiennent");
            }
            for (String m : manques) {
                echec("perf", m);
            }
        }, () -> true, 1, null);
        pas("perf-fin", () -> etape("perf-fin"), () -> true, 1, null);
    }

    private static void ouvrirPerf(Minecraft mc, String nom, @Nullable String image) {
        pas(nom, () -> {
            etape(nom);
            ouvertures++;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> ecranPret(mc), 20, null);
        pas(nom + "-pret", () -> etape(nom + "-pret"), () -> attendu(0.8), 3, image);
    }

    /** Images par seconde comptées pendant {@code s} secondes. */
    private static void mesure(String nom, double s) {
        pas(nom, () -> {
            etape(nom + "-debut");
            imagesDebut = imagesRendues;
            mesureDebutNs = System.nanoTime();
        }, () -> {
            double ecoule = secondes(mesureDebutNs);
            if (ecoule < s) {
                return false;
            }
            double fps = (imagesRendues - imagesDebut) / ecoule;
            MESURES.put(nom, fps);
            journal(String.format(Locale.ROOT, "%s : %d images en %.2f s, %.1f img/s", nom, imagesRendues - imagesDebut,
                    ecoule, fps));
            etape(nom + "-fin");
            return true;
        }, s + 5, null);
    }

    /** Tas après deux GC à une seconde d'écart. */
    private static void tas(String nom) {
        pas(nom, System::gc, () -> {
            if (!attendu(1.0)) {
                return false;
            }
            System.gc();
            Runtime rt = Runtime.getRuntime();
            double mo = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0);
            MESURES.put(nom, mo);
            journal(String.format(Locale.ROOT, "%s : tas après GC %.1f Mo (alloué %d Mo)", nom, mo,
                    rt.totalMemory() / (1024 * 1024)));
            etape(nom);
            return true;
        }, 5, null);
    }

    /** Horodatage d'une étape, au journal et dans {@code etapes.txt} (lu par {@code tools/telephone-memoire.ps1}). */
    private static void etape(String nom) {
        long t = System.currentTimeMillis();
        journal("etape " + nom + " " + t);
        ecrireFichier("etapes.txt", t + ";" + nom + "\n", true);
    }

    // --- WebDisplays -----------------------------------------------------------------------------------------------

    private static void composerWebDisplays(Minecraft mc) {
        decor(mc, 30);
        // Le téléphone d'abord : notre routeur de messages (oneforallQuery) est posé sur le client partagé de MCEF à la
        // première vue ; le navigateur du minePad, créé ensuite, reçoit donc la fonction et s'en sert.
        ouvrir(mc, "wd-telephone-avant", Lock.SETUP, "wd-00-telephone-avant-le-pad");
        echap(mc, "wd-telephone-avant-ferme");
        pas("wd-pad-donne", () -> serveur(mc, (srv, sp) -> {
            Item padObjet = ForgeRegistries.ITEMS.getValue(new ResourceLocation("webdisplays", "minepad"));
            if (padObjet == null || padObjet == Items.AIR) {
                return;
            }
            ItemStack pad = new ItemStack(padObjet);
            pad.getOrCreateTag().putString("PadURL", SELFTEST);
            ItemStack phone = sp.getMainHandItem().copy();
            sp.getInventory().setItem(caseDuTelephone, pad);
            sp.getInventory().setItem((caseDuTelephone + 1) % 9, phone);
        }), () -> mc.player.getMainHandItem().getItem() != PhoneRegistry.PHONE.get()
                && !mc.player.getMainHandItem().isEmpty(), 5, null);
        pas("wd-pad-cree", () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND), () -> attendu(2.0), 4,
                null);
        pas("wd-pad-ouvert", () -> {
            journal("minePad : étiquette " + mc.player.getMainHandItem().getTag());
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> ecranDeWebDisplays(mc) && attendu(6.0), 15, "wd-01-minepad-selftest");
        pas("wd-pad-ecran", () -> journal("écran de WebDisplays : " + mc.screen.getClass().getName()), () -> true, 1,
                null);
        // Le minePad ne se ferme que par Maj+Échap (GuiMinePad.keyChanged, lu par javap) : Échap seul va à la page.
        pas("wd-pad-ferme", () -> mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,
                GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), GLFW.GLFW_MOD_SHIFT), () -> mc.screen == null, 5, null);
        // Le minePad dans la main gauche (son navigateur reste vivant), le téléphone dans la main droite.
        pas("wd-pad-en-main-gauche", () -> serveur(mc, (srv, sp) -> {
            ItemStack pad = sp.getInventory().getItem(caseDuTelephone).copy();
            sp.setItemSlot(EquipmentSlot.OFFHAND, pad);
            sp.getInventory().setItem(caseDuTelephone, sp.getInventory().getItem((caseDuTelephone + 1) % 9).copy());
            sp.getInventory().setItem((caseDuTelephone + 1) % 9, ItemStack.EMPTY);
        }), () -> mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get()) && !mc.player.getOffhandItem().isEmpty()
                && attendu(3.0), 6, "wd-02-pad-en-main-gauche");
        ouvrir(mc, "wd-telephone", Lock.SETUP, "wd-03-telephone-bonjour");
        assistantJusquAuFond(mc, "wd-assistant", FOND_ASSISTANT, false);
        assistantVersLeCode(mc, "wd-assistant", false);
        cliquerCode(mc, "wd-choix", CODE);
        pause(null);
        cliquerCode(mc, "wd-confirmation", CODE);
        toutEstPret(mc, "wd-assistant", FOND_ASSISTANT, null);
        pas("wd-telephone-deverrouille", () -> {}, () -> etat(Lock.UNLOCKED)
                && cible(mc, id("app-settings", "settings")), 10, null);
        pause("wd-04-telephone-deverrouille-pad-vivant");
        clic(mc, "wd-reglages", id("app-settings", "settings"), () -> cible(mc, id("wp-nuit", "wallpaper")), 10, null);
        clic(mc, "wd-fond-nuit", id("wp-nuit", "wallpaper"), () -> fond(web ? "nuit" : "lagon") && attendu(0.3), 10,
                "wd-05-reglages-fond-change");
        echap(mc, "wd-telephone-ferme");
        pas("wd-pad-en-main-droite", () -> serveur(mc, (srv, sp) -> {
            ItemStack pad = sp.getOffhandItem().copy();
            ItemStack phone = sp.getMainHandItem().copy();
            sp.setItemSlot(EquipmentSlot.OFFHAND, phone);
            sp.getInventory().setItem(caseDuTelephone, pad);
        }), () -> !mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get())
                && mc.player.getOffhandItem().is(PhoneRegistry.PHONE.get()), 5, null);
        pas("wd-pad-encore", () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND),
                () -> ecranDeWebDisplays(mc) && attendu(4.0), 15, "wd-06-minepad-encore");
        pas("wd-pad-encore-ferme", () -> mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,
                GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), GLFW.GLFW_MOD_SHIFT), () -> mc.screen == null, 5, null);
        pas("wd-bilan-pont", () -> {
            if (messagesDuPont == 0) {
                echec("wd-bilan-pont", "aucun message du téléphone reçu : le pont n'a pas répondu");
            }
        }, () -> true, 1, null);
        bilanDuPont();
    }

    private static boolean ecranDeWebDisplays(Minecraft mc) {
        return mc.screen != null && mc.screen.getClass().getName().startsWith("net.montoyo.wd");
    }

    // --- Ouvertures, fermetures, codes -----------------------------------------------------------------------------

    /**
     * Ouvre le téléphone tenu (vrai usage de l'objet) et attend l'écran prêt, au verrou attendu ({@code null} : tout
     * verrou). Repli attendu (page cassée ou sans MCEF) : l'écran de repli, dans les {@value #REPLI_MAX_S} s, sur le
     * même état.
     */
    private static void ouvrir(Minecraft mc, String nom, @Nullable Lock attendu, @Nullable String image) {
        pas(nom, () -> {
            ouvertures++;
            webVu = false;
            ouvertureNs = System.nanoTime();
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> {
            Lock l = verrouDeLEcran(mc);
            if (l == null || (attendu != null && l != attendu) || !ecranPret(mc)) {
                return false;
            }
            if (!web) {
                return mc.screen instanceof PhoneFallbackScreen && verifierLeRepli(mc, nom, REPLI_MAX_S, PAGE_CASSEE);
            }
            return mc.screen instanceof PhoneWebScreen;
        }, 20, null);
        pas(nom + "-ecran", () -> journal(nom + " : écran " + mc.screen.getClass().getSimpleName()), () -> attendu(0.8),
                3, image);
    }

    private static void echap(Minecraft mc, String nom) {
        pas(nom, () -> {
            if (mc.screen != null) {
                mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), 0);
            }
        }, () -> mc.screen == null, 5, null);
    }

    /** Fermé par le serveur pour cette raison : plus d'écran, dernier état {@code CLOSE} de cette raison. */
    private static boolean fermeePar(Minecraft mc, CloseReason raison) {
        return mc.screen == null && PhoneClient.last().map(p -> p.kind() == Kind.CLOSE && p.reason() == raison)
                .orElse(false);
    }

    /** Le message de fermeture en barre d'action est celui de la raison. */
    private static void messageDeFermeture(String nom, CloseReason raison, String image) {
        String attendue = FERME + raison.name().toLowerCase(Locale.ROOT);
        pas(nom, () -> {}, () -> {
            String vue = messageEnBarre(Minecraft.getInstance());
            journal(nom + " : barre d'action « " + vue + " »");
            if (!attendue.equals(vue)) {
                echec(nom, "message en barre d'action « " + vue + " », « " + attendue + " » attendu");
            }
            return true;
        }, 2, image);
    }

    /**
     * L'assistant de première configuration d'un téléphone neuf ouvert, jusqu'au choix du fond compris. Page :
     * Bonjour (complet : deux autres langues, puis la fin du tour en français), Commencer, Configuration rapide
     * (complet : Retour essayé), Continuer, la vignette de {@code fond}. Repli : « Fond suivant » jusqu'à {@code fond}
     * (pas d'assistant animé). Rien n'est envoyé au serveur avant le code.
     */
    private static void assistantJusquAuFond(Minecraft mc, String nom, String fond, boolean complet) {
        if (!web) {
            int clics = (PhoneWallpapers.IDS.indexOf(fond) - PhoneWallpapers.IDS.indexOf(PhoneWallpapers.DEFAULT) + 4)
                    % 4;
            for (int i = 1; i <= clics; i++) {
                clic(mc, nom + "-fond-suivant-" + i, "setup-wallpaper", () -> attendu(0.4), 5, null);
            }
            pause(complet ? "01b-repli-fond-" + fond + "-avant-le-code" : null);
            return;
        }
        if (complet) {
            pas(nom + "-bonjour-2", () -> {}, () -> attendu(1.7), 3, "01b-bonjour-langue-2");
            pas(nom + "-bonjour-3", () -> {}, () -> attendu(1.95), 3, "01c-bonjour-langue-3");
            pas(nom + "-bonjour-fin", () -> {}, () -> attendu(11.0), 14, "01d-bonjour-fin-du-tour");
        }
        clic(mc, nom + "-commencer", "wz-start", () -> stable(mc, "wz-quick-next"), 10,
                complet ? "01e-configuration-rapide" : null);
        if (complet) {
            clic(mc, nom + "-retour-bonjour", "wz-quick-back", () -> stable(mc, "wz-start"), 10, "01e2-retour-bonjour");
            clic(mc, nom + "-recommencer", "wz-start", () -> stable(mc, "wz-quick-next"), 10, null);
        }
        clic(mc, nom + "-continuer", "wz-quick-next", () -> stable(mc, "wz-wp-next"), 10,
                complet ? "01f-fond-d-ecran" : null);
        clic(mc, nom + "-fond-" + fond, "wz-wp-" + fond, () -> attendu(0.8), 5,
                complet ? "01g-fond-choisi-" + fond : null);
    }

    /** Du fond au code : Continuer (page ; complet : Retour depuis le code essayé, saisie oubliée), rien au repli. */
    private static void assistantVersLeCode(Minecraft mc, String nom, boolean complet) {
        if (!web) {
            return;
        }
        clic(mc, nom + "-vers-le-code", "wz-wp-next", () -> stable(mc, "key-0"), 10, complet ? "01h-choix-du-code" : null);
        if (complet) {
            clic(mc, nom + "-retour-chiffre", "key-7", () -> attendu(0.3), 5, null);
            clic(mc, nom + "-retour-au-fond", "setup-back", () -> stable(mc, "wz-wp-next"), 10, "01i-retour-au-fond");
            clic(mc, nom + "-vers-le-code-2", "wz-wp-next", () -> stable(mc, "key-0"), 10, null);
        }
    }

    /**
     * Le code posé : le serveur dit UNLOCKED, avec le fond choisi et le joueur pour propriétaire. Page : « Tout est
     * prêt » ({@code image}), puis Commencer vers l'accueil ; repli : l'accueil directement.
     */
    private static void toutEstPret(Minecraft mc, String nom, String fond, @Nullable String image) {
        pas(nom + "-serveur", () -> {}, () -> etat(Lock.UNLOCKED) && fond(fond), 10, null);
        pas(nom + "-proprietaire", () -> {
            String attendu = mc.player.getGameProfile().getName();
            String vu = PhoneClient.last().map(PhoneStatePacket::ownerName).orElse("");
            journal(nom + " : configuré, fond " + fond + ", propriétaire « " + vu + " »");
            if (!attendu.equals(vu)) {
                echec(nom + "-proprietaire", "propriétaire « " + vu + " », « " + attendu + " » attendu");
            }
        }, () -> true, 1, null);
        if (web) {
            pas(nom + "-tout-est-pret", () -> {}, () -> stable(mc, "wz-done-start"), 10, image);
            clic(mc, nom + "-vers-l-accueil", "wz-done-start", () -> stable(mc, "app-settings"), 10, null);
        }
    }

    /** Un faux code de plus (par clics ou au clavier) ; attend la réponse, sauf blocage attendu au cinquième. */
    private static void faux(Minecraft mc, int n, int restants, boolean clics, @Nullable String image) {
        if (clics) {
            cliquerCode(mc, "faux-" + n, FAUX);
        } else {
            pas("faux-" + n + "-clavier", () -> stable(mc, "key-0"), () -> taper(mc, FAUX), () -> true, 10, null);
        }
        pas("faux-" + n + "-reponse", () -> {}, () -> {
            PhoneStatePacket p = PhoneClient.last().orElse(null);
            if (p == null || p.kind() != Kind.UPDATE) {
                return false;
            }
            // Un UPDATE de batterie (pourcentage qui change) peut suivre la réponse dans le même tic et la remplacer
            // dans PhoneClient.last() avec last = NONE : on juge sur ce que la réponse a changé (essais restants,
            // blocage), qui reste vrai dans l'UPDATE suivant.
            return restants > 0
                    ? (p.last() == Result.WRONG_CODE || p.last() == Result.NONE) && p.attemptsLeft() == restants
                            && p.lockoutSeconds() == 0
                    : (p.last() == Result.LOCKED_OUT || p.last() == Result.NONE) && p.lockoutSeconds() > 0
                            && p.lock() == Lock.LOCKED;
        }, 10, null);
        pas("faux-" + n + "-releve", () -> journal("faux code " + n + " : " + resume()), () -> attendu(1.0), 3, image);
    }

    private static void changerLeCode(Minecraft mc) {
        if (web) {
            pas("feuille-du-code", () -> stable(mc, "row-code"), () -> {
                noterMiseEnPage(mc);
                cliquer(mc, "row-code");
            }, () -> cible(mc, "sheet-cancel") && cible(mc, "key-1") && nouvelleMiseEnPage(mc), 10, null);
            cliquerCode(mc, "ancien", CODE);
            pause(null);
            cliquerCode(mc, "nouveau", NOUVEAU);
            pause(null);
            cliquerCode(mc, "confirmation", NOUVEAU);
            pas("modification-du-code", () -> {},
                    () -> PhoneClient.last().map(p -> p.last() == Result.CODE_CHANGED).orElse(false), 10, null);
        } else {
            pas("champs-du-code", () -> stable(mc, "old"), () -> {
                remplir(mc, "old", CODE);
                remplir(mc, "new", NOUVEAU);
                remplir(mc, "confirm", NOUVEAU);
            }, () -> attendu(0.5), 2, "06a-champs-du-code");
            clic(mc, "modification-du-code", "change",
                    () -> PhoneClient.last().map(p -> p.last() == Result.CODE_CHANGED).orElse(false), 10, null);
        }
        pause("06-code-modifie");
    }

    /** Quatre chiffres cliqués un par un, chacun sur une mise en page stable. */
    private static void cliquerCode(Minecraft mc, String nom, String code) {
        for (int i = 0; i < code.length(); i++) {
            String touche = "key-" + code.charAt(i);
            clic(mc, nom + "-chiffre-" + (i + 1), touche, () -> attendu(0.15), 10, null);
        }
        pas(nom + "-fin", () -> {}, () -> attendu(0.5), 2, null);
    }

    /** Un clic sur la commande nommée, une fois sa boîte stable. */
    private static void clic(Minecraft mc, String nom, String cible, BooleanSupplier fini, double delai,
            @Nullable String image) {
        pas(nom, () -> stable(mc, cible), () -> cliquer(mc, cible), fini, delai, image);
    }

    private static String id(String page, String repli) {
        return web ? page : repli;
    }

    // --- Gestes réels ----------------------------------------------------------------------------------------------

    /** Clic au centre de la commande nommée, par les méthodes de l'écran (comme la souris). */
    private static void cliquer(Minecraft mc, String nom) {
        double[] c = centre(mc, nom);
        Screen s = mc.screen;
        s.mouseMoved(c[0], c[1]);
        s.mouseClicked(c[0], c[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        s.mouseReleased(c[0], c[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** Frappe au clavier : touche enfoncée, caractère, touche relâchée (ordre de GLFW). */
    private static void taper(Minecraft mc, String texte) {
        Screen s = mc.screen;
        for (char c : texte.toCharArray()) {
            int key = GLFW.GLFW_KEY_0 + (c - '0');
            int scan = GLFW.glfwGetKeyScancode(key);
            s.keyPressed(key, scan, 0);
            s.charTyped(c, 0);
            s.keyReleased(key, scan, 0);
        }
    }

    /** Un champ de l'écran de repli : clic dedans, puis frappe. */
    private static void remplir(Minecraft mc, String champ, String texte) {
        cliquer(mc, champ);
        taper(mc, texte);
    }

    /**
     * Centre d'une commande, en coordonnées d'interface. Page : boîte CSS du dernier {@code layout} x agrandissement /
     * échelle d'interface, depuis le coin du cadre. Repli : centre du bouton ou du champ.
     */
    private static double[] centre(Minecraft mc, String nom) {
        if (mc.screen instanceof PhoneWebScreen w) {
            int[] b = w.rects().get(nom);
            if (b == null) {
                throw new IllegalStateException("« " + nom + " » absent de la mise en page " + w.rects().keySet());
            }
            double k = w.pageScale() / mc.getWindow().getGuiScale();
            Rect f = w.frameRect();
            return new double[] {f.x() + (b[0] + b[2] / 2.0) * k, f.y() + (b[1] + b[3] / 2.0) * k};
        }
        if (mc.screen instanceof PhoneFallbackScreen r) {
            AbstractWidget b = r.widgets().get(nom);
            if (b == null) {
                throw new IllegalStateException("« " + nom + " » absent de l'écran de repli " + r.widgets().keySet());
            }
            return new double[] {b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0};
        }
        throw new IllegalStateException("aucun écran du téléphone (écran " + mc.screen + ")");
    }

    /** La commande nommée est là (et active pour le repli). */
    private static boolean cible(Minecraft mc, String nom) {
        if (mc.screen instanceof PhoneWebScreen w) {
            return w.pageReady() && w.rects().containsKey(nom);
        }
        if (mc.screen instanceof PhoneFallbackScreen r) {
            AbstractWidget b = r.widgets().get(nom);
            return b != null && b.active;
        }
        return false;
    }

    /**
     * La commande nommée est là et sa boîte ne bouge plus : même rectangle dans les deux dernières mises en page, ou
     * aucune mise en page nouvelle depuis 0,8 s (la page envoie la sienne 720 ms après la fin des animations et ne
     * renvoie jamais deux fois la même). Repli : le bouton est là et actif.
     */
    private static boolean stable(Minecraft mc, String nom) {
        if (mc.screen instanceof PhoneWebScreen w) {
            if (!w.pageReady() || w.rects() != miseCourante) {
                return false;
            }
            int[] b = miseCourante.get(nom);
            if (b == null) {
                return false;
            }
            int[] avant = misePrecedente == null ? null : misePrecedente.get(nom);
            return Arrays.equals(b, avant) || secondes(changementNs) >= 0.8;
        }
        return cible(mc, nom);
    }

    /** À chaque image : l'écran suivi, ses deux dernières mises en page, et le pont du téléphone sous surveillance. */
    private static void suivre(Minecraft mc) {
        Screen s = mc.screen;
        if (s != ecranSuivi) {
            ecranSuivi = s;
            misePrecedente = null;
            miseCourante = null;
            changementNs = System.nanoTime();
            if (s instanceof PhoneWebScreen w) {
                webVu = true;
                surveillerLePont(w);
            }
        }
        if (s instanceof PhoneWebScreen w && w.rects() != miseCourante) {
            misePrecedente = miseCourante;
            miseCourante = w.rects();
            changementNs = System.nanoTime();
        }
    }

    /** Les messages que la page du téléphone peut envoyer, et leurs clés. */
    private static final Set<String> SORTES = Set.of("ready", "layout", "close", "unlock", "setup", "erase", "lock",
            "changeCode", "wallpaper", "airplane", "insertSim", "removeSim", "requestSim", "antennaApp");
    private static final Set<String> CLES = Set.of("a", "rects", "code", "old", "new", "id", "on", "wallpaper", "slot",
            "op");

    /**
     * Le pont de l'écran est entouré d'un témoin qui compte chaque message par sorte (jamais son contenu : il peut
     * porter un code) et note tout message étranger à la page du téléphone (celui de la page d'essai porte
     * {@code w}, {@code h}, {@code dpr}, {@code bouton}…).
     */
    private static void surveillerLePont(PhoneWebScreen w) {
        try {
            Field f = WebScreen.class.getDeclaredField("bridge");
            f.setAccessible(true);
            WebBridge vrai = (WebBridge) f.get(w);
            if (vrai instanceof Temoin) {
                return;
            }
            f.set(w, new Temoin(vrai));
        } catch (ReflectiveOperationException | RuntimeException e) {
            echec("pont-surveille", e.toString());
        }
    }

    private record Temoin(WebBridge vrai) implements WebBridge {
        @Override
        public void onMessage(JsonObject message) {
            String a = message.has("a") && message.get("a").isJsonPrimitive() ? message.get("a").getAsString() : "?";
            if (!SORTES.contains(a) || !CLES.containsAll(message.keySet())) {
                messagesEtrangers++;
                LOG.warn("[telephone] message étranger au pont du téléphone : a={} clés={}", a, message.keySet());
            } else {
                messagesDuPont++;
                SORTES_DU_PONT.merge(a, 1, Integer::sum);
            }
            vrai.onMessage(message);
        }

        @Override
        public void onFailure(WebFailure failure) {
            vrai.onFailure(failure);
        }
    }

    private static void bilanDuPont() {
        pas("bilan-du-pont", () -> {
            journal("pont du téléphone : " + messagesDuPont + " message(s) de la page " + SORTES_DU_PONT + ", "
                    + messagesEtrangers + " étranger(s)");
            if (messagesEtrangers > 0) {
                echec("pont", messagesEtrangers + " message(s) étranger(s) arrivé(s) au pont du téléphone");
            }
        }, () -> true, 1, null);
    }

    /** Le verrou de l'état montré par l'écran du téléphone ouvert, ou {@code null}. */
    @Nullable
    private static Lock verrouDeLEcran(Minecraft mc) {
        if (mc.screen instanceof PhoneWebScreen w) {
            return w.state().lock();
        }
        return mc.screen instanceof PhoneFallbackScreen r ? r.state().lock() : null;
    }

    /** La page a envoyé sa première mise en page (web) ; l'écran de repli est prêt dès qu'il est ouvert. */
    private static boolean ecranPret(Minecraft mc) {
        if (mc.screen instanceof PhoneWebScreen w) {
            return w.pageReady() && !w.rects().isEmpty();
        }
        return mc.screen instanceof PhoneFallbackScreen;
    }

    private static void noterMiseEnPage(Minecraft mc) {
        miseEnPageNotee = mc.screen instanceof PhoneWebScreen w ? w.rects() : null;
    }

    /** Une mise en page reçue depuis la note (page) ; toujours vrai pour le repli. */
    private static boolean nouvelleMiseEnPage(Minecraft mc) {
        return !(mc.screen instanceof PhoneWebScreen w) || w.rects() != miseEnPageNotee;
    }

    private static boolean etat(Lock lock) {
        return PhoneClient.last().map(p -> p.lock() == lock).orElse(false);
    }

    private static boolean fond(String id) {
        return PhoneClient.last().map(p -> id.equals(p.wallpaper())).orElse(false);
    }

    private static String resume() {
        return PhoneClient.last().map(p -> p.kind() + " " + p.lock() + " " + p.last() + " essais " + p.attemptsLeft()
                + " blocage " + p.lockoutSeconds() + " s batterie " + p.battery() + " % fond " + p.wallpaper()
                + " avion " + p.airplane() + (p.kind() == Kind.CLOSE ? " raison " + p.reason() : ""))
                .orElse("aucun état");
    }

    private static void taille(Minecraft mc, int l, int h) {
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), l, h);
        journal("fenêtre demandée " + l + "x" + h);
    }

    private static void echelle(Minecraft mc, int e) {
        mc.options.guiScale().set(e); // en mémoire seulement
        mc.resizeDisplay();
        journal("échelle d'interface " + e);
    }

    /** La clé du message en barre d'action (texte brut s'il n'est pas traduit), ou une chaîne vide. */
    private static String messageEnBarre(Minecraft mc) {
        try {
            Field f = Gui.class.getDeclaredField("overlayMessageString");
            f.setAccessible(true);
            Component c = (Component) f.get(mc.gui);
            if (c == null) {
                return "";
            }
            return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * La raison pour laquelle le client n'est plus (ou pas) dans un monde, ou {@code null} : un écran de déconnexion
     * (dont celui de Forge pour des mods différents, qui n'hérite pas de {@link DisconnectedScreen}), ou aucun monde
     * depuis plus de 5 s hors des écrans de connexion et de chargement.
     */
    @Nullable
    private static String horsDuMonde(Minecraft mc) {
        Screen s = mc.screen;
        if (s instanceof DisconnectedScreen d) {
            return "déconnecté : " + raison(d);
        }
        if (s instanceof ModMismatchDisconnectedScreen) {
            return "refusé, mods différents du serveur (" + s.getClass().getSimpleName() + ") : "
                    + s.getTitle().getString();
        }
        boolean attente = s instanceof ConnectScreen || s instanceof ReceivingLevelScreen
                || s instanceof LevelLoadingScreen || s instanceof GenericDirtMessageScreen
                || s instanceof ProgressScreen;
        if (mc.level != null || attente) {
            sansMondeNs = 0;
            return null;
        }
        if (sansMondeNs == 0) {
            sansMondeNs = System.nanoTime();
        }
        return secondes(sansMondeNs) > 5 ? "aucun monde depuis plus de 5 s (écran " + s + ")" : null;
    }

    private static String raison(DisconnectedScreen d) {
        try {
            Field f = DisconnectedScreen.class.getDeclaredField("reason");
            f.setAccessible(true);
            return ((Component) f.get(d)).getString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return d.getTitle().getString();
        }
    }

    // --- La prise --------------------------------------------------------------------------------------------------

    private interface SurLeServeur {
        void run(MinecraftServer srv, ServerPlayer sp);
    }

    private static void serveur(Minecraft mc, SurLeServeur corps) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) {
            throw new IllegalStateException("geste du serveur intégré demandé sur un serveur dédié");
        }
        java.util.UUID id = mc.player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp != null) {
                corps.run(srv, sp);
            }
        });
    }

    private static int caseDe(ServerPlayer sp, Item objet) {
        for (int s = 0; s < sp.getInventory().getContainerSize(); s++) {
            if (sp.getInventory().getItem(s).is(objet)) {
                return s;
            }
        }
        return -1;
    }

    /**
     * Deux blocs à l'est du joueur : le bloc d'énergie créative de Crafts &amp; Additions (il pousse son énergie dans
     * ses six voisins à chaque tic), et la prise collée contre lui, tournée vers le joueur.
     */
    private static void poserLaPrise(Minecraft mc) {
        serveur(mc, (srv, sp) -> {
            ServerLevel level = sp.serverLevel();
            BlockPos pied = sp.blockPosition();
            BlockPos socle = pied.east(3);
            BlockPos ici = pied.east(2);
            Block creatif = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createaddition", "creative_energy"));
            boolean pack = creatif != null && creatif != Blocks.AIR;
            level.setBlockAndUpdate(socle, pack ? creatif.defaultBlockState() : Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(ici, PhoneRegistry.WALL_SOCKET.get().defaultBlockState()
                    .setValue(WallSocketBlock.FACING, Direction.WEST));
            journal("prise posée en " + ici.toShortString() + " contre " + (pack ? "createaddition:creative_energy"
                    : "de la pierre (pack absent : aucune énergie)"));
            // Le joueur regarde la prise (vers l'est), pour la voir sur les images.
            sp.teleportTo(level, pied.getX() + 0.5, pied.getY(), pied.getZ() + 0.5, -90f, 35f);
            prise = ici;
        });
    }

    private static void utiliserLaPrise(Minecraft mc) {
        Vec3 face = Vec3.atCenterOf(prise).add(-0.4, 0, 0);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(face, Direction.WEST, prise,
                false));
    }

    private static void lireLaPrise(Minecraft mc) {
        serveur(mc, (srv, sp) -> {
            BlockEntity be = sp.serverLevel().getBlockEntity(prise);
            if (be instanceof WallSocketBlockEntity s) {
                int fe = be.getCapability(ForgeCapabilities.ENERGY).map(e -> e.getEnergyStored()).orElse(-1);
                etatPrise = "réserve " + fe + " FE, téléphone " + PhoneItem.stored(s.phone()) + " FE ("
                        + PhoneItem.stored(s.phone()) * 100 / PhoneConfig.capacity() + " %), état "
                        + be.getBlockState().getValue(dev.oneforallsurvival.machine.ElectricMachineBlock.STATE);
            }
        });
    }

    // --- Pas -------------------------------------------------------------------------------------------------------

    private static void pas(String nom, Runnable action, BooleanSupplier fini, double delai, String image) {
        PAS.add(new Pas(nom, () -> true, action, fini, delai, image));
    }

    private static void pas(String nom, BooleanSupplier pret, Runnable action, BooleanSupplier fini, double delai,
            String image) {
        PAS.add(new Pas(nom, pret, action, fini, delai, image));
    }

    /** Laisse la page finir ses animations (720 ms) avant l'image. */
    private static void pause(@Nullable String image) {
        pas((image == null ? "pause" : image) + "-attente", () -> {}, () -> attendu(1.0), 3, image);
    }

    private static void etapes(Minecraft mc) {
        if (indice >= PAS.size()) {
            passer(Phase.SORTIE);
            return;
        }
        Pas p = PAS.get(indice);
        if (!joue) {
            if (debutAttenteNs == 0) {
                debutAttenteNs = System.nanoTime();
                journal("pas « " + p.nom() + " »");
            }
            if (!p.pret().getAsBoolean()) {
                if (secondes(debutAttenteNs) > p.delai()) {
                    echec(p.nom(), "cible jamais stable en " + p.delai() + " s (écran " + mc.screen + ", état "
                            + resume() + (mc.screen instanceof PhoneWebScreen w ? ", mise en page "
                            + w.rects().keySet() : "") + ")");
                    suivant();
                }
                return;
            }
            joue = true;
            debutPasNs = System.nanoTime();
            try {
                p.action().run();
            } catch (IllegalStateException e) {
                echec(p.nom(), e.getMessage());
                suivant();
            }
            return;
        }
        double s = secondes(debutPasNs);
        if (p.fini().getAsBoolean()) {
            journal("pas « " + p.nom() + " » réussi en " + String.format(Locale.ROOT, "%.2f s", s));
            REUSSIES.add(p.nom());
            if (p.image() != null) {
                capturer(mc, p.image());
            }
            suivant();
        } else if (s > p.delai()) {
            echec(p.nom(), "condition non atteinte en " + p.delai() + " s (écran " + mc.screen + ", état " + resume()
                    + (mc.screen instanceof PhoneWebScreen w ? ", mise en page " + w.rects().keySet() : "") + ")");
            if (p.image() != null) {
                capturer(mc, p.image() + "-echec");
            }
            suivant();
        }
    }

    private static void suivant() {
        indice++;
        joue = false;
        debutAttenteNs = 0;
    }

    private static boolean attendu(double secondes) {
        return secondes(debutPasNs) >= secondes;
    }

    /** Les avis « mode simplifié » du chat : {@code attendus} en tout, ouvertures comptées. */
    private static void bilanAvis(Minecraft mc, int attendus) {
        StringBuilder detail = new StringBuilder();
        int tous = 0;
        for (WebFailure f : WebFailure.values()) {
            int n = avisDansLeChat(mc, f.translationKey());
            tous += n;
            if (n > 0) {
                detail.append(' ').append(f.name()).append('=').append(n);
            }
        }
        String bilan = "ouvertures " + ouvertures + ", avis « mode simplifié » dans le chat : " + tous
                + (detail.length() > 0 ? " (" + detail.toString().trim() + ")" : "");
        journal(bilan);
        if (tous != attendus) {
            echec("avis", bilan + ", " + attendus + " attendu(s)");
        }
    }

    @SuppressWarnings("unchecked")
    private static int avisDansLeChat(Minecraft mc, String cle) {
        try {
            Field f = ChatComponent.class.getDeclaredField("allMessages");
            f.setAccessible(true);
            int n = 0;
            for (GuiMessage m : (List<GuiMessage>) f.get(mc.gui.getChat())) {
                if (m.content().getContents() instanceof TranslatableContents t && cle.equals(t.getKey())) {
                    n++;
                }
            }
            return n;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- Journal et fin --------------------------------------------------------------------------------------------

    private static void capturer(Minecraft mc, String nom) {
        images.instantane(mc, nom);
        IMAGES.add(nom + ".png");
        var f = mc.getWindow();
        String cadre = mc.screen instanceof PhoneWebScreen w ? " ; cadre " + w.frameRect() + ", agrandissement "
                + String.format(Locale.ROOT, "%.3f", w.pageScale()) + ", boîtes " + w.rects().size()
                : mc.screen instanceof PhoneFallbackScreen r ? " ; cadre " + r.frameRect() + ", page " + r.page()
                : mc.screen != null ? " ; écran " + mc.screen.getClass().getSimpleName() : "";
        journal(String.format(Locale.ROOT, "image %s.png : fenêtre %dx%d, interface %dx%d échelle %.0f%s ; %s", nom,
                f.getWidth(), f.getHeight(), f.getGuiScaledWidth(), f.getGuiScaledHeight(), f.getGuiScale(), cadre,
                resume()));
    }

    private static void echec(String nom, String detail) {
        ECHECS.add(nom + " : " + detail);
        LOG.error("[telephone] FAIL {} : {}", nom, detail);
    }

    private static void passer(Phase suivante) {
        journal("phase " + phase + " -> " + suivante);
        phase = suivante;
        debutPhaseNs = System.nanoTime();
        fait = false;
    }

    private static void finir(Minecraft mc) {
        phase = Phase.FIN;
        if (mc.screen instanceof PhoneWebScreen || mc.screen instanceof PhoneFallbackScreen) {
            mc.setScreen(null);
        }
        if (images != null) {
            images.attendre();
        }
        List<String> manquantes = TelephoneBilan.manquantes(
                TelephoneBilan.attendues(MODE, SERVEUR != null, web), REUSSIES);
        for (String m : manquantes) {
            echec("etapes", "étape attendue manquante : " + m);
        }
        journal("bilan : " + (ECHECS.isEmpty() ? "TOUT PASSE" : ECHECS.size() + " ECHEC(S) " + ECHECS) + ", "
                + IMAGES.size() + " image(s), " + REUSSIES.size() + " pas réussis");
        ecrireTermine("normale");
        mc.stop();
    }

    private static void journal(String texte) {
        LOG.info("[telephone] [+{}] {}", String.format(Locale.ROOT, "%.3f s", secondes(DEBUT_NS)), texte);
    }

    private static double secondes(long ns) {
        return (System.nanoTime() - ns) / 1e9;
    }

    private static void ecrireFichier(String nom, String texte, boolean ajout) {
        try {
            Path dossier = Minecraft.getInstance().gameDirectory.toPath().resolve(DOSSIER);
            Files.createDirectories(dossier);
            if (ajout) {
                Files.writeString(dossier.resolve(nom), texte, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } else {
                Files.writeString(dossier.resolve(nom), texte, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("[telephone] {} non écrit", nom, e);
        }
    }

    private static synchronized void ecrireTermine(String fin) {
        if (termineEcrit || DOSSIER == null) {
            return;
        }
        termineEcrit = true;
        String json = "{\"fin\": \"" + fin + "\", \"echecs\": " + ECHECS.size() + ", \"images\": " + IMAGES.size()
                + "}\n";
        try {
            Path dossier = Minecraft.getInstance().gameDirectory.toPath().resolve(DOSSIER);
            Files.createDirectories(dossier);
            Files.writeString(dossier.resolve("termine.json"), json, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOG.error("[telephone] termine.json non écrit", e);
        }
    }
}
