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
import dev.oneforallsurvival.phone.net.OperatorActionPacket;
import dev.oneforallsurvival.phone.net.client.OperatorPanelLayout;
import dev.oneforallsurvival.phone.net.client.OperatorPanelScreen;
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
import net.minecraftforge.client.event.InputEvent;
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
 *   <li>{@code antenne} (lot T2, tâche 10) : l'appli « Antenne » du fondateur de Mairie, par de vrais clics (page) ou
 *   le panneau Minecraft par-dessus le repli : onglets, émission, suspension, tarif refusé puis accepté tapés au
 *   clavier, mise à jour en direct quand la source de Mairie est coupée, retour au téléphone. Dans la page, « Désactiver »
 *   touché deux fois en un dixième de seconde (rien ne part) puis confirmé, les montants relus, et l'icône grisée (le
 *   message seul) ; la page répond à ces relevés par un message {@code essai} que le témoin du pont garde (tâche 16).</li>
 *   <li>{@code prise} (lot T2, tâche 16, avec le pack) : la prise à travers son mur, câblée par de vrais clics
 *   (connecteurs de Crafts &amp; Additions, bobine de cuivre), puis le connecteur du mur remis sur « aucun » à la clé de
 *   Create ; valeurs relues au serveur.</li>
 *   <li>{@code clavier} (retours d'essai du 27/11) : les vraies touches, données par le gestionnaire de clavier du jeu
 *   ({@code KeyboardHandler.keyPress}, codes GLFW et {@code glfwGetKeyScancode}, caractère tapé comme le rappel de
 *   GLFW) : code tapé au pavé numérique (un chiffre effacé par Retour arrière), puis à la rangée du haut avec les
 *   caractères d'un clavier AZERTY sans Maj ; numéro composé au pavé et appelé par l'Entrée du pavé (relu au
 *   serveur) ; enfin la touche liée à {@code key.push_to_talk} (et {@code key.whisper} si elle est liée), téléphone
 *   ouvert : l'événement {@code InputEvent.Key} de Forge part à l'appui et au relâchement, et pas pour un chiffre.</li>
 *   <li>{@code faceid} (retours d'essai du 27/11, lot B) : Face ID activé dans les Réglages par de vrais clics et le
 *   code au clavier, puis, téléphone chargé à 100 % (la batterie passe à 99 % et son {@code UPDATE} arrive avant que la
 *   page soit prête, ce que l'essai vérifie), réouverture déverrouillée par le visage (animation relue dans la page) et
 *   ouverture tête couverte (« Visage non reconnu » relu dans la page) ; puis « Partager mon numéro » : la liste montre
 *   le second joueur d'essai, un vrai clic lui envoie le numéro, il accepte par le lien du chat, la fiche est relue dans
 *   son carnet et le joueur reçoit « accepté » dans son chat ; enfin, carte suspendue, la feuille ne montre personne et
 *   dit pourquoi.</li>
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
                        case "antenne" -> {
                            if (SERVEUR != null) {
                                composerAntenneServeur(mc);
                            } else {
                                composerAntenne(mc);
                            }
                        }
                        case "signal" -> composerSignal(mc);
                        case "appel" -> composerAppel(mc);
                        case "clavier" -> composerClavier(mc);
                        case "faceid" -> composerFaceId(mc);
                        case "urgence" -> composerUrgence(mc);
                        case "prise" -> composerPriseMur(mc);
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
                + (n.holder().isEmpty() ? "absent" : "présent") + ", appli=" + n.founderApp() + ", ouvrable="
                + n.founderAppAvailable() + ", cartes="
                + n.choices().size() + ", opérateurs=" + n.requestable().size();
    }

    /** La vue réseau du dernier état reçu. */
    private static PhoneNetView vueReseau() {
        return PhoneClient.last().map(PhoneStatePacket::net).orElse(PhoneNetView.NONE);
    }

    // --- L'appli « Antenne » (lot T2, tâche 10) ------------------------------------------------------------------------

    private static volatile boolean ligneFaite;
    private static volatile String idDemande = "";
    private static volatile String idCible = "";
    private static volatile int appliDemandes = -1;
    private static volatile String appliCarte = "";
    private static volatile long appliDemain = -1L;
    private static volatile boolean appliPanneau;
    private static volatile long tarifEssaye = -1L;
    @Nullable private static volatile BlockPos antenneSeconde;
    private static volatile boolean secondeEnService;

    /**
     * Mode {@code antenne} : un téléphone configuré, Mairie fondée par le joueur (antenne posée par un vrai clic, contre
     * un bloc d'énergie créative), sa ligne glissée dans le téléphone, une demande de carte et deux cartes de plus ;
     * puis l'appli « Antenne » par un vrai clic, les onglets, une émission, une suspension, un tarif refusé (au-dessus du
     * plafond) puis un tarif accepté tapés au clavier, la mise à jour en direct quand la source de Mairie est coupée,
     * et le retour au téléphone. Dans la page (MCEF) par les boîtes du {@code layout} ; sans MCEF, le panneau Minecraft
     * ouvert par-dessus l'écran de repli, par les centres de ses onglets et boutons.
     */
    private static void composerAntenne(Minecraft mc) {
        if (SERVEUR != null) {
            echec("antenne", "le mode antenne se joue en monde solo (gestes du serveur intégré)");
            return;
        }
        decor(mc, 80);
        ouvrir(mc, "antenne-ouverture-0", Lock.SETUP, null);
        assistantJusquAuFond(mc, "antenne-assistant", "aube", false);
        assistantVersLeCode(mc, "antenne-assistant", false);
        cliquerCode(mc, "antenne-choix", CODE);
        pas("antenne-confirmation-au-clavier", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED), 10, null);
        toutEstPret(mc, "antenne-assistant", "aube", null);
        echap(mc, "antenne-echap-0");
        antennes(mc);
        // Une seconde antenne de Mairie, sa propre source : la première coupée, le téléphone garde ses barres et l'appli
        // montre en direct l'antenne passée hors tension ; la seconde coupée ensuite, le serveur ferme l'appli.
        pas("antenne-seconde-depart", () -> serveur(mc, (srv, sp) -> {
            BlockPos pied = BlockPos.containing(departReseau);
            Block creatif = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createaddition", "creative_energy"));
            boolean pack = creatif != null && creatif != Blocks.AIR;
            sp.serverLevel().setBlockAndUpdate(pied.north(3), pack ? creatif.defaultBlockState()
                    : Blocks.STONE.defaultBlockState());
            sp.getInventory().setItem(6, new ItemStack(PhoneRegistry.ANTENNA_ITEM.get()));
            antenneSeconde = pied.north(2);
        }), () -> antenneSeconde != null && mc.player.getInventory().getItem(6).is(PhoneRegistry.ANTENNA_ITEM.get()),
                5, null);
        poserAntenne(mc, "antenne-pose-seconde", 6, () -> antenneSeconde);
        pas("antenne-telephone-en-main-2", () -> mc.player.getInventory().selected = caseDuTelephone,
                () -> mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get()) && attendu(0.3), 5, null);
        pas("antenne-seconde-en-service", () -> {}, () -> {
            serveur(mc, (srv, sp) -> secondeEnService = enService(sp.serverLevel(), antenneSeconde)
                    && PhoneOperators.get(srv).byId(idMairie).map(o -> PhoneOperators.get(srv).antennasOf(o.id()).size())
                    .orElse(0) == 2);
            return secondeEnService;
        }, 20, null);
        pas("antenne-ligne", () -> serveur(mc, (srv, sp) -> {
            PhoneOperators registre = PhoneOperators.get(srv);
            Optional<PhoneOperator> mairie = registre.byId(idMairie);
            ItemStack phone = sp.getMainHandItem();
            if (mairie.isEmpty() || !phone.is(PhoneRegistry.PHONE.get())) {
                return;
            }
            if (PhoneSims.issueTo(sp, mairie.get(), sp.getUUID(), sp.getGameProfile().getName(), EconomyClock.now())
                    .isEmpty()) {
                return;
            }
            int ou = -1;
            for (int s = 0; s < sp.getInventory().getContainerSize(); s++) {
                if (sp.getInventory().getItem(s).getItem() instanceof dev.oneforallsurvival.phone.net.SimCardItem) {
                    ou = s;
                }
            }
            if (ou < 0 || PhoneSims.insert(sp, phone, ou) != PhoneSims.Insert.DONE) {
                return;
            }
            dev.oneforallsurvival.phone.net.SimCard cible = registre.issue(idMairie, null, "",
                    net.minecraft.util.RandomSource.create(), EconomyClock.now());
            registre.issue(idMairie, java.util.UUID.randomUUID(), "Jonas Mercier",
                    net.minecraft.util.RandomSource.create(), EconomyClock.now());
            idCible = cible.simId().toString();
            registre.request(idMairie, java.util.UUID.randomUUID(), "Inès Lebrun", EconomyClock.now())
                    .ifPresent(d -> idDemande = d.id().toString());
            ligneFaite = !idDemande.isEmpty();
        }), () -> ligneFaite, 5, null);
        ouvrir(mc, "antenne-ouverture-1", Lock.LOCKED, null);
        pas("antenne-deverrouillage", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, "app-antenna") && vueReseau().founderAppAvailable()
                        && attendu(0.6), 15,
                null); // le frein du téléphone (2 tics) : l'appli ne part pas dans le tic du déverrouillage
        clic(mc, "antenne-appli", "app-antenna", () -> appliPrete(mc), 15, null);
        pause("a01-appli-reseau");
        ongletAppli(mc, "antenne-onglet-cartes", "cards", OperatorPanelLayout.Tab.CARDS, "a02-appli-cartes");
        boutonAppli(mc, "antenne-emettre", () -> "op-issue-" + idDemande, OperatorActionPacket.Action.ISSUE,
                () -> idDemande, () -> {
                    lireAppli(mc);
                    return appliDemandes == 0 && attendu(0.8);
                }, "a03-demande-emise");
        boutonAppli(mc, "antenne-suspendre", () -> "op-card-" + idCible, OperatorActionPacket.Action.SUSPEND,
                () -> idCible, () -> {
                    lireAppli(mc);
                    return "SUSPENDED".equals(appliCarte) && attendu(0.8);
                }, "a04-carte-suspendue");
        // Tâche 15 : « Désactiver » ; le premier appui n'envoie rien (« Confirmer ? »), le second dans les 5 s envoie.
        // Dans la page (tâche 16), le premier est un double appui rapide : le second, avant 400 ms, ne confirme pas.
        if (web) {
            doubleAppui(mc);
        } else {
            boutonAppli(mc, "antenne-desactiver-arme", () -> "op-deact-" + idCible,
                    OperatorActionPacket.Action.DEACTIVATE, () -> idCible, () -> {
                        lireAppli(mc);
                        return "SUSPENDED".equals(appliCarte) && attendu(0.6);
                    }, "a04b-desactiver-confirmer");
        }
        boutonAppli(mc, "antenne-desactiver-confirme", () -> "op-deact-" + idCible,
                OperatorActionPacket.Action.DEACTIVATE, () -> idCible, () -> {
                    lireAppli(mc);
                    boolean parti = !web || SORTES_DU_PONT.getOrDefault("opDeactivate", 0) == desactiverAvant + 1;
                    return "DEACTIVATED".equals(appliCarte) && parti && attendu(0.8);
                }, "a04c-carte-desactivee");
        ongletAppli(mc, "antenne-onglet-tarif", "tariff", OperatorPanelLayout.Tab.TARIFF, null);
        saisirTarif(mc, "antenne-tarif-refuse", true, "a05-tarif-refuse");
        saisirTarif(mc, "antenne-tarif", false, "a06-tarif-au-lendemain");
        if (web) {
            montants(mc);
        }
        pas("antenne-retour", () -> {
            if (mc.screen instanceof PhoneWebScreen w && w.antennaSession() >= 0 && w.rects().containsKey("home-bar")) {
                cliquer(mc, "home-bar");
            } else if (mc.screen instanceof OperatorPanelScreen p) {
                p.keyPressed(GLFW.GLFW_KEY_ESCAPE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), 0);
            }
        }, () -> {
            lireAppli(mc);
            boolean telephone = mc.screen instanceof PhoneWebScreen w ? w.antennaSession() < 0
                    : mc.screen instanceof PhoneFallbackScreen;
            return telephone && !appliPanneau && attendu(1.0);
        }, 10, "a07-retour-au-telephone");
        clic(mc, "antenne-appli-2", "app-antenna", () -> appliPrete(mc), 15, null);
        pas("antenne-source-coupee", () -> serveur(mc, (srv, sp) -> couper(sp, antenneMairie)),
                () -> appliPrete(mc) && sourceVue(mc) && attendu(1.0), 30, "a08-source-coupee-en-direct");
        pas("antenne-source-releve", () -> journal("source de la principale coupée : " + resumeAppli(mc)), () -> true, 1,
                null);
        pas("antenne-seconde-coupee", () -> serveur(mc, (srv, sp) -> couper(sp, antenneSeconde)), () -> {
            boolean fermee = mc.screen instanceof PhoneWebScreen w ? w.antennaSession() < 0
                    : mc.screen instanceof PhoneFallbackScreen;
            return (fermee || attendu(25)) && attendu(1.0);
        }, 30, null);
        // La page referme l'appli en 0,42 s et MCEF repeint ensuite : l'image une seconde après la fermeture.
        pause("a09-plus-de-barre");
        pas("antenne-seconde-releve", () -> journal("les deux sources coupées : " + resumeAppli(mc) + ", barres "
                + vueReseau().bars()), () -> true, 1, null);
        // Tâche 15 : l'icône grisée touchée n'ouvre rien ; seul le message « Pas de réseau. » répond.
        if (web) {
            iconeGrisee(mc);
        } else {
            clic(mc, "antenne-icone-grisee", "app-antenna", () -> mc.screen instanceof PhoneFallbackScreen && attendu(1.0), 10,
                    "a10-icone-grisee-message");
        }
        echap(mc, "antenne-echap-fin");
        bilanDuPont();
    }

    // --- Les appels (lot T3, tâche 11a) -------------------------------------------------------------------------------

    /** Le nom du contact, saisi caractère par caractère dans le champ natif de la page (accents compris). */
    private static final String CONTACT = "Zoé Hélène";
    /** Un numéro qu'aucune carte ne porte (les numéros émis sont tirés au hasard). */
    private static final String INCONNU = "0600000000";
    @Nullable private static volatile JoueurDeTest correspondant;
    @Nullable private static volatile java.util.UUID fondateurLagon;
    @Nullable private static volatile java.util.UUID simJoueur;
    private static volatile String numeroJoueur = "";
    private static volatile String numeroCorr = "";
    private static volatile int appelCorr;
    private static final Map<String, String> LU = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<String> FINS_APPEL = java.util.Collections.synchronizedList(new ArrayList<>());
    @Nullable private static dev.oneforallsurvival.phone.call.CallListener ecouteAppels;
    private static volatile String veille = "";
    private static int chiffres;
    private static long dernierAppuiNs;
    private static int composesAvant;

    private interface Lecture {
        String lire(MinecraftServer srv, ServerPlayer sp);
    }

    /** Une lecture au serveur intégré, rangée sous {@code cle} : la valeur de la lecture précédente de cette clé. */
    private static String lu(Minecraft mc, String cle, Lecture l) {
        serveur(mc, (srv, sp) -> LU.put(cle, l.lire(srv, sp)));
        return LU.getOrDefault(cle, "");
    }

    private static java.util.Optional<dev.oneforallsurvival.phone.call.PhoneCallStatePacket> appel() {
        return PhoneClient.lastCall();
    }

    private static boolean phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase ph) {
        return appel().map(c -> c.phase() == ph).orElse(false);
    }

    private static String cleDuMessage(@Nullable Component c) {
        if (c == null) {
            return "";
        }
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /** La consommation des deux antennes (Mairie, Lagon), dernier tic. */
    private static String consommation(ServerLevel level) {
        StringBuilder b = new StringBuilder();
        for (BlockPos pos : new BlockPos[] {antenneMairie, antenneLagon}) {
            if (level.getBlockEntity(pos) instanceof AntennaBlockEntity be && be.antennaId() != null) {
                b.append(PhoneOperators.get(level.getServer()).antenna(be.antennaId())
                        .map(r -> r.last().toString()).orElse("?"));
            } else {
                b.append('?');
            }
            b.append(' ');
        }
        return b.toString().trim();
    }

    @Nullable
    private static ServerPlayer corr() {
        JoueurDeTest j = correspondant;
        return j == null ? null : j.joueur();
    }

    /** Le correspondant compose le numéro du joueur ; l'identifiant de l'appel est gardé. */
    private static void corrAppelle(Minecraft mc) {
        serveur(mc, (srv, sp) -> {
            ServerPlayer c = corr();
            dev.oneforallsurvival.phone.call.CallService.Dial d = dev.oneforallsurvival.phone.call.CallService.dial(c,
                    c.getMainHandItem(), false, numeroJoueur);
            appelCorr = d.callId();
            journal("le correspondant compose : " + d.outcome() + " " + cleDuMessage(d.message()));
        });
    }

    /** Frappe d'un texte : touche, caractère, relâche pour les lettres ASCII et l'espace ; le caractère seul sinon. */
    private static void ecrire(Minecraft mc, String texte) {
        Screen s = mc.screen;
        for (char c : texte.toCharArray()) {
            int key = c == ' ' ? GLFW.GLFW_KEY_SPACE
                    : (c < 128 && Character.isLetter(c)) ? Character.toUpperCase(c) : -1;
            int scan = key < 0 ? 0 : GLFW.glfwGetKeyScancode(key);
            if (key >= 0) {
                s.keyPressed(key, scan, Character.isUpperCase(c) ? GLFW.GLFW_MOD_SHIFT : 0);
            }
            s.charTyped(c, Character.isUpperCase(c) ? GLFW.GLFW_MOD_SHIFT : 0);
            if (key >= 0) {
                s.keyReleased(key, scan, 0);
            }
        }
    }

    /** Le numéro tapé touche par touche sur le clavier de la page (vrais clics sur {@code dial-<chiffre>}). */
    private static void auClavier(Minecraft mc, String nom, java.util.function.Supplier<String> numero,
            @Nullable String image) {
        pas(nom, () -> stable(mc, "dial-0"), () -> {
            chiffres = 0;
            dernierAppuiNs = 0;
        }, () -> {
            String n = numero.get();
            if (chiffres < n.length()) {
                String touche = "dial-" + n.charAt(chiffres);
                if ((dernierAppuiNs == 0 || secondes(dernierAppuiNs) >= 0.2) && stable(mc, touche)) {
                    cliquer(mc, touche);
                    chiffres++;
                    dernierAppuiNs = System.nanoTime();
                }
                return false;
            }
            return secondes(dernierAppuiNs) >= 0.8;
        }, 20, image);
    }

    /**
     * Décor, correspondant simulé (fondateur de Lagon, téléphone en main), téléphone du joueur mis en service et
     * déverrouillé, antennes, une carte pour chacun ; les pas sont nommés {@code <pre>-...}.
     */
    private static void preparerAppels(Minecraft mc, String pre) {
        decor(mc, 80);
        pas(pre + "-correspondant", () -> serveur(mc, (srv, sp) -> {
            JoueurDeTest j = JoueurDeTest.rejoindre(sp.serverLevel(), "Maelle",
                    Vec3.atBottomCenterOf(sp.blockPosition().west(4)));
            fondateurLagon = j.uuid();
            correspondant = j;
            ecouteAppels = new dev.oneforallsurvival.phone.call.CallListener() {
                @Override
                public void changed(dev.oneforallsurvival.phone.call.CallBook.Call call) {}

                @Override
                public void ended(dev.oneforallsurvival.phone.call.CallBook.Call call,
                        dev.oneforallsurvival.phone.call.CallBook.End reason) {
                    FINS_APPEL.add(call.id() + ":" + reason);
                }
            };
            dev.oneforallsurvival.phone.call.CallService.addListener(ecouteAppels);
        }), () -> correspondant != null, 5, null);
        ouvrir(mc, pre + "-ouverture-0", Lock.SETUP, null);
        assistantJusquAuFond(mc, pre + "-assistant", "aube", false);
        assistantVersLeCode(mc, pre + "-assistant", false);
        cliquerCode(mc, pre + "-choix", CODE);
        pas(pre + "-confirmation-au-clavier", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED), 10, null);
        toutEstPret(mc, pre + "-assistant", "aube", null);
        echap(mc, pre + "-echap-0");
        antennes(mc);
        pas(pre + "-lignes", () -> serveur(mc, (srv, sp) -> {
            PhoneOperators registre = PhoneOperators.get(srv);
            Optional<PhoneOperator> mairie = registre.byId(idMairie);
            Optional<PhoneOperator> lagon = registre.byId(idLagon);
            ServerPlayer c = corr();
            if (mairie.isEmpty() || lagon.isEmpty() || c == null) {
                return;
            }
            // Le téléphone en main d'abord : la carte émise va dans la première case libre, celle de la main
            // quand elle est vide (passe 2 : la carte du correspondant écrasée par son téléphone).
            ItemStack phoneC = new ItemStack(PhoneRegistry.PHONE.get());
            PhoneItem.store(phoneC, PhoneConfig.capacity());
            c.setItemInHand(InteractionHand.MAIN_HAND, phoneC);
            PhoneSims.issueTo(sp, mairie.get(), sp.getUUID(), sp.getGameProfile().getName(), EconomyClock.now());
            PhoneSims.issueTo(c, lagon.get(), c.getUUID(), "Maëlle", EconomyClock.now());
            for (ServerPlayer p : List.of(sp, c)) {
                for (int s = 0; s < p.getInventory().getContainerSize(); s++) {
                    if (p.getInventory().getItem(s).getItem() instanceof dev.oneforallsurvival.phone.net.SimCardItem) {
                        PhoneSims.Insert r = PhoneSims.insert(p, p.getMainHandItem(), s);
                        journal("carte de " + p.getGameProfile().getName() + " : " + r);
                        break;
                    }
                }
            }
            PhoneSims.line(srv, sp.getMainHandItem()).ifPresent(l -> {
                numeroJoueur = l.number();
                simJoueur = l.simId();
            });
            PhoneSims.line(srv, c.getMainHandItem()).ifPresent(l -> numeroCorr = l.number());
        }), () -> !numeroJoueur.isEmpty() && !numeroCorr.isEmpty(), 5, null);
    }

    /**
     * Mode {@code appel} (lot T3, tâche 11a) : un téléphone configuré avec une carte de Mairie, un correspondant simulé
     * ({@link JoueurDeTest}) qui a fondé Lagon et porte un téléphone avec sa carte, à 4 blocs ; l'application Téléphone
     * par de vrais clics sur les boîtes de la page : onglets, contact ajouté (nom accentué tapé dans le champ natif),
     * appel composé au clavier, décroché du correspondant, Silence et Haut-parleur, île, raccroché, appels entrants
     * (déverrouillé, verrouillé refusé, verrouillé répondu), numéro inconnu. Chaque étape relue au serveur intégré.
     */
    private static void composerAppel(Minecraft mc) {
        if (SERVEUR != null || !web) {
            echec("appel", "le mode appel se joue en monde solo, dans la page (MCEF)");
            return;
        }
        preparerAppels(mc, "appel");

        // 1. Déverrouiller, l'application Téléphone, ses trois onglets.
        ouvrir(mc, "appel-ouverture-1", Lock.LOCKED, null);
        pas("appel-deverrouillage", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, "app-phone") && attendu(0.6), 15, null);
        clic(mc, "appel-appli", "app-phone", () -> stable(mc, "tel-tab-recents") && attendu(1.0), 10, null);
        clic(mc, "appel-onglet-recents", "tel-tab-recents", () -> stable(mc, "tel-tab-keypad") && attendu(0.8), 10,
                "t01-recents");
        clic(mc, "appel-onglet-contacts", "tel-tab-contacts", () -> stable(mc, "contact-add") && attendu(0.8), 10,
                "t02-contacts");
        clic(mc, "appel-onglet-clavier", "tel-tab-keypad", () -> stable(mc, "dial-call") && attendu(0.8), 10,
                "t03-clavier");

        // 2. Un contact : nom accentué dans le champ natif, numéro du correspondant.
        clic(mc, "appel-onglet-contacts-2", "tel-tab-contacts", () -> stable(mc, "contact-add") && attendu(0.4), 10,
                null);
        clic(mc, "appel-contact-nouveau", "contact-add", () -> stable(mc, "cform-name") && attendu(0.6), 10, null);
        clic(mc, "appel-contact-champ-nom", "cform-name", () -> attendu(0.3), 5, null);
        pas("appel-contact-nom", () -> ecrire(mc, CONTACT), () -> attendu(0.4), 3, null);
        clic(mc, "appel-contact-champ-numero", "cform-number", () -> attendu(0.3), 5, null);
        pas("appel-contact-numero", () -> taper(mc, numeroCorr), () -> attendu(0.6), 3, "t04-contact-saisi");
        pas("appel-contact-saisie-relue", () -> releveDemande = false, () -> {
            JsonObject r = demander(mc, "{nom:(document.getElementById('cform-name')||{}).value||'',chiffres:"
                    + "((document.getElementById('cform-number')||{}).value||'').replace(/\\s+/g,'').length}");
            if (r == null) {
                return false;
            }
            String nom = r.get("nom").getAsString();
            int n = r.get("chiffres").getAsInt();
            journal("feuille du contact : nom « " + nom + " », " + n + " chiffre(s) (" + numeroCorr.length()
                    + " attendus)");
            if (!CONTACT.equals(nom) || n != numeroCorr.length()) {
                echec("appel-contact-saisie-relue", "nom « " + nom + " », " + n + " chiffre(s)");
            }
            return true;
        }, 5, null);
        clic(mc, "appel-contact-enregistrer", "cform-save", () -> {
            String s = lu(mc, "contact", (srv, sp) -> simJoueur == null ? "?" : String.valueOf(
                    dev.oneforallsurvival.phone.call.PhoneDirectory.get(srv).contacts(simJoueur).stream()
                            .anyMatch(c -> c.number().equals(numeroCorr) && c.name().equals(CONTACT))));
            return "true".equals(s) && !cible(mc, "cform-save") && stable(mc, "contact-0") && attendu(0.8);
        }, 10, "t05-contact-dans-la-liste");

        // 3. Composer au clavier, bouton vert : « Appel… », la sonnerie au serveur.
        clic(mc, "appel-onglet-clavier-2", "tel-tab-keypad", () -> stable(mc, "dial-call") && attendu(0.4), 10, null);
        auClavier(mc, "appel-clavier-numero", () -> numeroCorr, "t06-numero-au-clavier");
        pas("appel-veille", () -> LU.remove("veille"), () -> {
            veille = lu(mc, "veille", (srv, sp) -> consommation(sp.serverLevel()));
            return !veille.isEmpty() && attendu(0.2);
        }, 3, null);
        pas("appel-veille-releve", () -> journal("consommation des antennes avant l'appel : " + veille), () -> true, 1,
                null);
        clic(mc, "appel-bouton-vert", "dial-call", () -> {
            String s = lu(mc, "sonne", (srv, sp) -> dev.oneforallsurvival.phone.call.CallService.callOf(corr())
                    .map(x -> x.state().name()).orElse("aucun"));
            return phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.OUTGOING) && "RINGING".equals(s)
                    && stable(mc, "call-hangup") && attendu(0.8);
        }, 10, "t07-appel-sortant");

        // 4. Le correspondant décroche ; Silence et Haut-parleur relus au serveur.
        pas("appel-decroche", () -> serveur(mc, (srv, sp) -> dev.oneforallsurvival.phone.call.CallService.callOf(corr())
                .ifPresent(x -> dev.oneforallsurvival.phone.call.CallService.answer(corr(), x.id()))),
                () -> phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.ACTIVE) && attendu(3.3), 10,
                "t08-en-cours-minuteur");
        clic(mc, "appel-silence", "call-mute", () -> "true".equals(lu(mc, "muet", (srv, sp) ->
                dev.oneforallsurvival.phone.call.CallService.callOf(sp).map(x -> String.valueOf(x.callerMuted()))
                        .orElse("?"))) && attendu(0.5), 10, null);
        clic(mc, "appel-haut-parleur", "call-speaker", () -> "true true".equals(lu(mc, "hp", (srv, sp) ->
                dev.oneforallsurvival.phone.call.CallService.callOf(sp).map(x -> x.callerMuted() + " "
                        + x.callerSpeaker()).orElse("?"))) && attendu(0.8), 10, "t09-silence-haut-parleur");

        // 5. Quitter l'écran d'appel ; bouton vert champ vide pendant l'appel ; île ; raccrocher.
        clic(mc, "appel-quitter-ecran", "home-bar", () -> !cible(mc, "call-hangup") && stable(mc, "dial-call")
                && attendu(0.8), 10, "t10-clavier-pendant-l-appel");
        pas("appel-champ-vide", () -> stable(mc, "dial-back"), () -> dernierAppuiNs = 0, () -> {
            if (cible(mc, "dial-back")) {
                if ((dernierAppuiNs == 0 || secondes(dernierAppuiNs) >= 0.2) && stable(mc, "dial-back")) {
                    cliquer(mc, "dial-back");
                    dernierAppuiNs = System.nanoTime();
                }
                return false;
            }
            return secondes(dernierAppuiNs) >= 0.8;
        }, 15, null);
        pas("appel-vert-pendant-l-appel", () -> stable(mc, "dial-call"), () -> {
            composesAvant = SORTES_DU_PONT.getOrDefault("callDial", 0);
            cliquer(mc, "dial-call");
        }, () -> stable(mc, "call-hangup") && attendu(0.8), 10, "t11-vert-champ-vide-ramene-a-l-appel");
        pas("appel-vert-releve", () -> releveDemande = false, () -> {
            JsonObject r = demander(mc, "{champ:" + texteDe("dial-number") + "}");
            if (r == null) {
                return false;
            }
            int envoyes = SORTES_DU_PONT.getOrDefault("callDial", 0) - composesAvant;
            String champ = r.get("champ").getAsString();
            journal("bouton vert champ vide pendant l'appel : callDial envoyé(s) " + envoyes + ", champ "
                    + (champ.isEmpty() ? "vide" : "rempli (" + champ.replaceAll("\\D", "").length() + " chiffres)"));
            if (envoyes != 0 || !champ.isEmpty()) {
                echec("appel-vert-releve", "champ « rempli » ou appel composé : " + envoyes + " envoi(s)");
            }
            return true;
        }, 5, null);
        clic(mc, "appel-quitter-ecran-2", "home-bar", () -> !cible(mc, "call-hangup") && stable(mc, "tel-close")
                && attendu(0.6), 10, null);
        clic(mc, "appel-fermer-appli", "tel-close", () -> stable(mc, "island") && stable(mc, "app-phone")
                && attendu(1.0), 10, "t12-ile-sur-l-accueil");
        clic(mc, "appel-ile", "island", () -> stable(mc, "call-hangup") && attendu(0.8), 10, "t13-retour-par-l-ile");
        clic(mc, "appel-raccrocher", "call-hangup",
                () -> phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.NONE) && attendu(0.7), 10,
                "t14-fin-de-l-appel");
        pas("appel-fin-serveur", () -> {}, () -> {
            String s = lu(mc, "fin", (srv, sp) -> dev.oneforallsurvival.phone.call.CallService.callOf(sp).isPresent()
                    + "|" + consommation(sp.serverLevel()));
            if (!s.startsWith("false|")) {
                return false;
            }
            boolean revenue = s.substring(6).equals(veille);
            if (revenue || attendu(6)) {
                String fin = appel().map(c -> cleDuMessage(c.message())).orElse("");
                journal("fin de l'appel : message « " + fin + " », fins au serveur " + FINS_APPEL
                        + ", consommation " + s.substring(6) + " (veille " + veille + ")");
                if (!revenue) {
                    echec("appel-fin-serveur", "consommation " + s.substring(6) + " au lieu de " + veille);
                }
                if (!fin.endsWith("call.ended.hung_up")) {
                    echec("appel-fin-serveur", "message de fin « " + fin + " »");
                }
                return true;
            }
            return false;
        }, 10, null);

        // 6. Appels entrants : déverrouillé ; puis verrouillé, refusé ; puis verrouillé, répondu.
        pas("appel-entrant", () -> corrAppelle(mc), () -> phase(
                dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.INCOMING) && stable(mc, "call-answer")
                && attendu(0.8), 10, "t15-entrant-deverrouille");
        pas("appel-entrant-abandon", () -> serveur(mc, (srv, sp) ->
                dev.oneforallsurvival.phone.call.CallService.hangUp(corr(), appelCorr)),
                () -> phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.NONE) && attendu(3.0), 10, null);
        echap(mc, "appel-echap-1");
        ouvrir(mc, "appel-ouverture-2", Lock.LOCKED, null);
        pas("appel-entrant-verrouille", () -> corrAppelle(mc), () -> phase(
                dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.INCOMING) && stable(mc, "call-decline")
                && attendu(0.8), 10, "t16-entrant-verrouille");
        clic(mc, "appel-refuser", "call-decline", () -> FINS_APPEL.contains(appelCorr + ":DECLINED")
                && phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.NONE) && attendu(0.7), 10,
                "t17-refuse");
        pas("appel-refus-releve", () -> {}, () -> attendu(2.5), 4, null);
        pas("appel-entrant-3", () -> corrAppelle(mc), () -> phase(
                dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.INCOMING) && stable(mc, "call-answer")
                && attendu(0.5), 10, null);
        clic(mc, "appel-repondre", "call-answer", () -> "ACTIVE".equals(lu(mc, "repondu", (srv, sp) ->
                dev.oneforallsurvival.phone.call.CallService.callOf(sp).map(x -> x.state().name()).orElse("aucun")))
                && phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.ACTIVE) && stable(mc, "call-hangup")
                && attendu(2.3), 10, "t18-repondu-ecran-verrouille");
        clic(mc, "appel-raccrocher-2", "call-hangup", () -> FINS_APPEL.contains(appelCorr + ":HUNG_UP")
                && phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.NONE) && attendu(3.0), 10, null);

        // 7. Un numéro inconnu.
        pas("appel-deverrouillage-2", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, "app-phone") && attendu(0.6), 15, null);
        clic(mc, "appel-appli-2", "app-phone", () -> stable(mc, "tel-tab-keypad") && attendu(1.0), 10, null);
        clic(mc, "appel-onglet-clavier-3", "tel-tab-keypad", () -> stable(mc, "dial-call") && attendu(0.4), 10, null);
        auClavier(mc, "appel-clavier-inconnu", () -> INCONNU, null);
        pas("appel-inconnu", () -> stable(mc, "dial-call"), () -> {
            composesAvant = SORTES_DU_PONT.getOrDefault("callDial", 0);
            cliquer(mc, "dial-call");
        }, () -> {
            String k = appel().map(c -> cleDuMessage(c.message())).orElse("");
            return SORTES_DU_PONT.getOrDefault("callDial", 0) > composesAvant && !k.isEmpty()
                    && !k.contains("call.ended") && attendu(0.9);
        }, 10, "t19-numero-inconnu");
        pas("appel-inconnu-releve", () -> journal("numéro inconnu : message « "
                + appel().map(c -> cleDuMessage(c.message())).orElse("") + " », appels au serveur "
                + dev.oneforallsurvival.phone.call.CallService.calls().size()), () -> attendu(2.5), 4, null);
        echap(mc, "appel-echap-fin");
        pas("appel-correspondant-sort", () -> serveur(mc, (srv, sp) -> {
            if (ecouteAppels != null) {
                dev.oneforallsurvival.phone.call.CallService.removeListener(ecouteAppels);
            }
            JoueurDeTest j = correspondant;
            if (j != null && j.connecte()) {
                j.quitter();
            }
            correspondant = null;
        }), () -> correspondant == null, 5, null);
        bilanDuPont();
    }

    // --- Face ID et « Partager mon numéro » (retours d'essai du 27/11, lot B) ------------------------------------------

    /** L'instant où la page de l'ouverture par le visage a répondu (0 avant). */
    private static long faceVueNs;
    /** Un {@code UPDATE} a remplacé l'{@code OPEN} avant que la page de l'ouverture en cours soit prête. */
    private static boolean updateAvantPret;

    /** Vrai une fois le téléphone tenu vu plein par le client. */
    private static boolean batteriePleine(Minecraft mc) {
        return PhoneItem.stored(mc.player.getMainHandItem()) >= PhoneConfig.capacity();
    }

    /**
     * Charge à 100 % le téléphone tenu (écran fermé) : la première dépense le fait passer à 99 %, et l'{@code UPDATE}
     * qui le dit arrive avant que la page soit prête.
     */
    private static void chargerAFond(Minecraft mc, String nom) {
        pas(nom, () -> serveur(mc, (srv, sp) -> PhoneItem.store(sp.getMainHandItem(), PhoneConfig.capacity())),
                () -> batteriePleine(mc) && attendu(0.5), 5, null);
    }

    /**
     * Ouvre le téléphone par un vrai clic droit, note si un {@code UPDATE} a précédé la page prête, puis évalue
     * {@code objet} dans la page ; {@code juger} reçoit sa réponse une fois. L'image est prise {@code delai} secondes
     * après.
     */
    private static void ouvertureParLeVisage(Minecraft mc, String nom, String objet,
            java.util.function.Consumer<JsonObject> juger, double delai, String image) {
        pas(nom, () -> {
            faceVueNs = 0;
            updateAvantPret = false;
            releveDemande = false;
            ouvertures++;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> {
            if (!(mc.screen instanceof PhoneWebScreen w)) {
                return false;
            }
            if (!w.pageReady()) {
                updateAvantPret |= w.state().kind() == Kind.UPDATE;
                return false;
            }
            JsonObject r = demander(mc, objet);
            if (r == null) {
                return false;
            }
            if (faceVueNs == 0) {
                faceVueNs = System.nanoTime();
                journal(nom + " : page prête ; UPDATE reçu avant : " + updateAvantPret + " ; " + resume());
                if (!updateAvantPret) {
                    echec(nom, "aucun UPDATE avant la page prête : l'essai ne prouve rien");
                }
                juger.accept(r);
            }
            return secondes(faceVueNs) >= delai;
        }, 20, image);
    }
    /** Le numéro d'entité du second joueur d'essai, lu au serveur intégré (-1 avant). */
    private static int idCorr = -1;
    private static volatile String partage = "";

    private static boolean dernierResultat(Result r) {
        return PhoneClient.last().map(p -> p.last() == r).orElse(false);
    }

    /**
     * Mode {@code faceid} : page (MCEF), monde solo. Le téléphone configuré (code {@value #CODE}, carte de Mairie) et le
     * second joueur d'essai (Maelle, carte de Lagon) de {@link #preparerAppels}.
     */
    private static void composerFaceId(Minecraft mc) {
        if (SERVEUR != null || !web) {
            echec("faceid", "le mode faceid se joue en monde solo, dans la page (MCEF)");
            return;
        }
        preparerAppels(mc, "faceid");

        // 1. Face ID activé dans les Réglages : l'interrupteur, puis le code au clavier.
        ouvrir(mc, "faceid-ouverture-1", Lock.LOCKED, null);
        pas("faceid-deverrouillage", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, "app-settings") && attendu(0.6), 15, null);
        clic(mc, "faceid-reglages", "app-settings", () -> stable(mc, "row-faceid") && attendu(0.8), 10,
                "f00-reglages-face-id-coupe");
        clic(mc, "faceid-interrupteur", "row-faceid", () -> stable(mc, "key-0") && attendu(0.8), 10,
                "f01-code-pour-face-id");
        pas("faceid-code", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> dernierResultat(Result.FACE_ID_ON) && PhoneClient.last().map(PhoneStatePacket::faceId)
                        .orElse(false) && stable(mc, "row-faceid") && attendu(1.2), 10, "f02-face-id-active");
        pas("faceid-relu-au-serveur", () -> {}, () -> {
            String v = lu(mc, "faceid-actif", (srv, sp) -> PhoneItem.phoneId(sp.getMainHandItem())
                    .map(id -> Boolean.toString(dev.oneforallsurvival.phone.PhoneData.get(srv).record(id).faceId()))
                    .orElse("?"));
            if ("false".equals(v)) {
                echec("faceid-relu-au-serveur", "Face ID coupé au serveur");
                return true;
            }
            return "true".equals(v);
        }, 5, null);
        echap(mc, "faceid-echap-1");

        // 2. Réouverture à 100 %, visage découvert : déverrouillé par le serveur, animation sur l'écran verrouillé, puis
        // l'accueil.
        chargerAFond(mc, "faceid-batterie-pleine");
        ouvertureParLeVisage(mc, "faceid-ouverture-visage", "{c:document.getElementById('phone').className}", r -> {
            String classes = r.get("c").getAsString();
            journal("ouverture par le visage : classes « " + classes + " »");
            if (!classes.contains("face-scan")) {
                echec("faceid-ouverture-visage", "pas d'animation Face ID : classes « " + classes + " »");
            }
        }, 0.45, "f03-face-id-animation");
        pas("faceid-accueil", () -> {}, () -> etat(Lock.UNLOCKED) && stable(mc, "app-phone") && attendu(1.0), 10,
                "f04-accueil-par-le-visage");
        echap(mc, "faceid-echap-2");

        // 3. Tête couverte, de nouveau à 100 % : le pavé, « Visage non reconnu ».
        pas("faceid-casque", () -> serveur(mc, (srv, sp) -> sp.setItemSlot(EquipmentSlot.HEAD,
                new ItemStack(Items.IRON_HELMET))), () -> !mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), 5,
                null);
        chargerAFond(mc, "faceid-batterie-pleine-casque");
        ouvertureParLeVisage(mc, "faceid-non-reconnu", "{msg:" + texteDe("lock-msg") + "}", r -> {
            String msg = r.get("msg").getAsString();
            journal("tête couverte : « " + msg + " »");
            if (!"Visage non reconnu".equals(msg)) {
                echec("faceid-non-reconnu", "message « " + msg + " »");
            }
        }, 1.0, "f05-visage-non-reconnu");
        pas("faceid-decouvert", () -> serveur(mc, (srv, sp) -> sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY)),
                () -> mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), 5, null);
        pas("faceid-code-apres-casque", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, "app-phone") && attendu(0.8), 15, null);

        // 4. « Partager mon numéro » : le second joueur à 4 blocs au nord, en vue (les antennes sont à l'est et à l'ouest).
        pas("partage-place", () -> serveur(mc, (srv, sp) -> {
            ServerPlayer c = corr();
            if (c != null) {
                BlockPos ici = sp.blockPosition().north(4);
                c.teleportTo(sp.serverLevel(), ici.getX() + 0.5, ici.getY(), ici.getZ() + 0.5, 180f, 0f);
                idCorr = c.getId();
                correspondant.composantsRecus();
            }
        }), () -> idCorr >= 0, 5, null);
        clic(mc, "partage-appli", "app-phone", () -> stable(mc, "tel-tab-contacts") && attendu(1.0), 10, null);
        clic(mc, "partage-contacts", "tel-tab-contacts", () -> stable(mc, "contact-share") && attendu(0.8), 10,
                "f06-contacts");
        clic(mc, "partage-ouvrir", "contact-share", () -> stable(mc, "share-" + idCorr) && attendu(1.0), 10,
                "f07-partager-mon-numero");
        // Le numéro d'entité n'est connu qu'au pas « partage-place » : la cible se lit au moment du pas.
        pas("partage-envoyer", () -> stable(mc, "share-" + idCorr), () -> cliquer(mc, "share-" + idCorr), () -> {
            String recu = lu(mc, "offre-recue", (srv, sp) -> {
                List<String> liens = new ArrayList<>();
                for (Component c : correspondant.composantsRecus()) {
                    liens.addAll(JoueurDeTest.commandes(c));
                }
                if (!liens.isEmpty()) {
                    partage = liens.get(0);
                }
                return partage;
            });
            return recu.startsWith("/phoneshare accept ") && attendu(0.9);
        }, 10, "f08-numero-envoye");
        pas("partage-accepte", () -> serveur(mc, (srv, sp) -> {
            ServerPlayer c = corr();
            journal("le second joueur clique : " + partage);
            srv.getCommands().performPrefixedCommand(c.createCommandSourceStack(), partage.substring(1));
        }), () -> {
            String fiche = lu(mc, "fiche", (srv, sp) -> {
                ServerPlayer c = corr();
                return c == null ? "?" : PhoneSims.line(srv, c.getMainHandItem())
                        .flatMap(l -> dev.oneforallsurvival.phone.call.PhoneDirectory.get(srv).nameOf(l.simId(),
                                numeroJoueur)).orElse("");
            });
            return !fiche.isEmpty() && avisDansLeChat(mc, "message.oneforallsurvival.call.share.accepted") > 0
                    && attendu(0.8);
        }, 10, "f09-accepte");
        pas("partage-bilan", () -> journal("fiche de Maelle : « " + LU.getOrDefault("fiche", "") + " » pour le numéro du"
                + " joueur ; « accepté » dans le chat du joueur : " + avisDansLeChat(mc,
                "message.oneforallsurvival.call.share.accepted")), () -> true, 1, null);

        // 5. Carte suspendue : la feuille ne montre personne, et dit pourquoi.
        clic(mc, "partage-fermer", "share-close", () -> stable(mc, "contact-share") && attendu(0.8), 10, null);
        pas("partage-carte-suspendue", () -> serveur(mc, (srv, sp) -> PhoneOperators.get(srv).setState(simJoueur,
                dev.oneforallsurvival.phone.net.SimCard.State.SUSPENDED)), () -> PhoneClient.last()
                        .map(p -> p.net().sim() == PhoneSims.Validity.SUSPENDED).orElse(false) && attendu(0.8), 10,
                null);
        clic(mc, "partage-rouvrir", "contact-share", () -> stable(mc, "share-close") && attendu(0.6), 10, null);
        pas("partage-sans-carte", () -> releveDemande = false, () -> {
            JsonObject r = demander(mc, "{msg:" + texteDe("share-msg") + ",liste:" + texteDe("share-list") + "}");
            if (r == null || !attendu(0.5)) {
                return false;
            }
            String msg = r.get("msg").getAsString();
            String liste = r.get("liste").getAsString();
            journal("carte suspendue : mot « " + msg + " », liste « " + liste + " »");
            if (!Component.translatable("message.oneforallsurvival.call.share.no_line").getString().equals(msg)
                    || !"Carte SIM active requise".equals(liste)) {
                echec("partage-sans-carte", "mot « " + msg + " », liste « " + liste + " »");
            }
            return true;
        }, 8, "f10-sans-carte-active");
        echap(mc, "partage-echap");
        pas("faceid-correspondant-sort", () -> serveur(mc, (srv, sp) -> {
            if (ecouteAppels != null) {
                dev.oneforallsurvival.phone.call.CallService.removeListener(ecouteAppels);
            }
            JoueurDeTest j = correspondant;
            if (j != null && j.connecte()) {
                j.quitter();
            }
            correspondant = null;
        }), () -> correspondant == null, 5, null);
        bilanDuPont();
    }

    // --- Le clavier et le voice chat (retours d'essai du 27/11) --------------------------------------------------------

    /** Les caractères que tape la rangée du haut d'un clavier AZERTY sans Maj, de 0 à 9. */
    private static final String AZERTY = "à&é\"'(-è_ç";
    /** Les événements {@code InputEvent.Key} de Forge relevés pendant le mode {@code clavier} : « touche:action ». */
    private static final List<String> TOUCHES_FORGE = new ArrayList<>();

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (ACTIF && "clavier".equals(MODE)) {
            TOUCHES_FORGE.add(event.getKey() + ":" + event.getAction());
        }
    }

    /**
     * Une touche réelle : appui puis relâchement par le gestionnaire de clavier du jeu (comme le rappel de GLFW), et le
     * caractère {@code c} entre les deux s'il n'est pas nul (comme le rappel de caractère de GLFW).
     */
    private static void toucheReelle(Minecraft mc, int key, char c) {
        long fenetre = mc.getWindow().getWindow();
        int scan = GLFW.glfwGetKeyScancode(key);
        mc.keyboardHandler.keyPress(fenetre, key, scan, GLFW.GLFW_PRESS, 0);
        if (c != 0) {
            try {
                java.lang.reflect.Method m = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("charTyped",
                        long.class, int.class, int.class);
                m.setAccessible(true);
                m.invoke(mc.keyboardHandler, fenetre, (int) c, 0);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("caractère non tapé", e);
            }
        }
        mc.keyboardHandler.keyPress(fenetre, key, scan, GLFW.GLFW_RELEASE, 0);
    }

    /** Des chiffres au pavé numérique (le caractère du chiffre, pavé verrouillé en chiffres). */
    private static void auPave(Minecraft mc, String chiffres) {
        for (char c : chiffres.toCharArray()) {
            toucheReelle(mc, GLFW.GLFW_KEY_KP_0 + (c - '0'), c);
        }
    }

    /** Des chiffres à la rangée du haut d'un clavier AZERTY, sans Maj : la touche du chiffre, le caractère « & é " ' ( … ». */
    private static void rangeeAzerty(Minecraft mc, String chiffres) {
        for (char c : chiffres.toCharArray()) {
            toucheReelle(mc, GLFW.GLFW_KEY_0 + (c - '0'), AZERTY.charAt(c - '0'));
        }
    }

    @Nullable
    private static net.minecraft.client.KeyMapping liaison(Minecraft mc, String nom) {
        for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
            if (nom.equals(k.getName())) {
                return k;
            }
        }
        return null;
    }

    /** Vrai une fois le téléphone déverrouillé ; un faux code parti fait échouer le pas (un chiffre compté deux fois). */
    private static boolean deverrouilleSansFauxCode(Minecraft mc, String nom) {
        if (etat(Lock.UNLOCKED) && cible(mc, "app-phone") && attendu(0.6)) {
            return true;
        }
        if (PhoneClient.last().map(p -> p.last() == Result.WRONG_CODE).orElse(false)) {
            echec(nom, "un faux code est parti : " + resume());
            return true;
        }
        return false;
    }

    /**
     * Mode {@code clavier} : page (MCEF), monde solo. Téléphone configuré (code {@value #CODE}), correspondant simulé ;
     * le code tapé au pavé puis à la rangée du haut (AZERTY sans Maj), un numéro composé au pavé et appelé par
     * l'Entrée du pavé, puis la touche « parler » du voice chat téléphone ouvert. Effets relus au serveur et dans la page.
     */
    private static void composerClavier(Minecraft mc) {
        if (SERVEUR != null || !web) {
            echec("clavier", "le mode clavier se joue en monde solo, dans la page (MCEF)");
            return;
        }
        preparerAppels(mc, "clavier");

        // 1. Le code au pavé numérique : un 9 effacé par Retour arrière, puis le code. Un chiffre compté deux fois, ou
        // un Retour arrière perdu, enverrait un faux code.
        ouvrir(mc, "clavier-ouverture-1", Lock.LOCKED, "k00-verrouille");
        pas("clavier-pave-code", () -> stable(mc, "key-0"), () -> {
            journal("pavé numérique : scancodes KP_0 " + GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_KP_0) + ", KP_9 "
                    + GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_KP_9) + ", Entrée du pavé "
                    + GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_KP_ENTER) + ", Retour arrière "
                    + GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_BACKSPACE));
            auPave(mc, "9");
            toucheReelle(mc, GLFW.GLFW_KEY_BACKSPACE, (char) 0);
            auPave(mc, CODE);
        }, () -> deverrouilleSansFauxCode(mc, "clavier-pave-code"), 10, "k01-deverrouille-au-pave");
        echap(mc, "clavier-echap-1");

        // 2. Le code à la rangée du haut, caractères d'un clavier AZERTY sans Maj.
        ouvrir(mc, "clavier-ouverture-2", Lock.LOCKED, null);
        pas("clavier-rangee-code", () -> stable(mc, "key-0"), () -> {
            journal("rangée du haut : scancodes 0 " + GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_0) + ", 1 "
                    + GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_1) + ", 9 " + GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_9));
            rangeeAzerty(mc, CODE);
        }, () -> deverrouilleSansFauxCode(mc, "clavier-rangee-code"), 10, "k02-deverrouille-rangee-azerty");

        // 3. Le numéro au pavé (un chiffre de trop effacé), appelé par l'Entrée du pavé.
        clic(mc, "clavier-appli", "app-phone", () -> stable(mc, "tel-tab-keypad") && attendu(1.0), 10, null);
        clic(mc, "clavier-onglet-clavier", "tel-tab-keypad", () -> stable(mc, "dial-call") && attendu(0.6), 10, null);
        pas("clavier-numero-au-pave", () -> stable(mc, "dial-call"), () -> {
            auPave(mc, numeroCorr + "7");
            toucheReelle(mc, GLFW.GLFW_KEY_BACKSPACE, (char) 0);
        }, () -> attendu(0.8), 5, "k03-numero-au-pave");
        pas("clavier-numero-relu", () -> releveDemande = false, () -> {
            JsonObject r = demander(mc, "{champ:" + texteDe("dial-number") + "}");
            if (r == null) {
                return false;
            }
            String vus = r.get("champ").getAsString().replaceAll("\\D", "");
            journal("numéro au pavé : " + vus.length() + " chiffre(s), attendu " + numeroCorr.length()
                    + (vus.equals(numeroCorr) ? " (identique)" : " (différent)"));
            if (!vus.equals(numeroCorr)) {
                echec("clavier-numero-relu", vus.length() + " chiffre(s) au lieu de " + numeroCorr.length());
            }
            return true;
        }, 5, null);
        pas("clavier-entree-du-pave", () -> stable(mc, "dial-call"), () -> {
            composesAvant = SORTES_DU_PONT.getOrDefault("callDial", 0);
            toucheReelle(mc, GLFW.GLFW_KEY_KP_ENTER, (char) 0);
        }, () -> {
            String s = lu(mc, "sonne-clavier", (srv, sp) -> dev.oneforallsurvival.phone.call.CallService
                    .callOf(corr()).map(x -> x.state().name()).orElse("aucun"));
            return phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.OUTGOING) && "RINGING".equals(s)
                    && stable(mc, "call-hangup") && attendu(0.8);
        }, 10, "k04-appel-par-l-entree-du-pave");
        pas("clavier-un-seul-appel", () -> {
            int n = SORTES_DU_PONT.getOrDefault("callDial", 0) - composesAvant;
            journal("Entrée du pavé : " + n + " callDial envoyé(s)");
            if (n != 1) {
                echec("clavier-un-seul-appel", n + " callDial au lieu de 1");
            }
        }, () -> true, 1, null);
        clic(mc, "clavier-raccrocher", "call-hangup",
                () -> phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.NONE) && attendu(2.5), 10, null);

        // 4. Le voice chat, téléphone ouvert : l'événement de Forge part pour « parler » (appui et relâchement), pas
        // pour un chiffre, et l'écran reste le téléphone.
        pas("clavier-voix", () -> mc.screen instanceof PhoneWebScreen, () -> {
            net.minecraft.client.KeyMapping parler = liaison(mc, "key.push_to_talk");
            net.minecraft.client.KeyMapping chuchoter = liaison(mc, "key.whisper");
            if (parler == null || chuchoter == null) {
                echec("clavier-voix", "liaisons du voice chat absentes (Simple Voice Chat non chargé ?)");
                return;
            }
            // Simple Voice Chat ne lie « parler » et « chuchoter » à aucune touche par défaut : liées ici en mémoire
            // seulement (options.txt n'est jamais réécrit par l'automate), puis rendues. « Chuchoter » prend un chiffre
            // du pavé : une touche du voice chat l'emporte sur le téléphone, quelle qu'elle soit.
            com.mojang.blaze3d.platform.InputConstants.Key avantParler = parler.getKey();
            com.mojang.blaze3d.platform.InputConstants.Key avantChuchoter = chuchoter.getKey();
            if (parler.isUnbound()) {
                parler.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_CAPS_LOCK));
            }
            if (chuchoter.isUnbound()) {
                chuchoter.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_KP_5));
            }
            net.minecraft.client.KeyMapping.resetMapping();
            try {
                int touche = parler.getKey().getValue();
                int t = chuchoter.getKey().getValue();
                TOUCHES_FORGE.clear();
                toucheReelle(mc, GLFW.GLFW_KEY_KP_1, '1');
                List<String> pourUnChiffre = new ArrayList<>(TOUCHES_FORGE);
                TOUCHES_FORGE.clear();
                toucheReelle(mc, touche, (char) 0);
                List<String> pourParler = new ArrayList<>(TOUCHES_FORGE);
                TOUCHES_FORGE.clear();
                toucheReelle(mc, t, (char) 0);
                List<String> pourChuchoter = new ArrayList<>(TOUCHES_FORGE);
                journal("voice chat : « parler » liée à " + parler.getKey().getName() + ", événements " + pourParler
                        + " ; « chuchoter » liée à " + chuchoter.getKey().getName() + ", événements " + pourChuchoter
                        + " ; pour un chiffre du pavé : " + pourUnChiffre + " ; écran "
                        + mc.screen.getClass().getSimpleName());
                if (!pourParler.equals(List.of(touche + ":" + GLFW.GLFW_PRESS, touche + ":" + GLFW.GLFW_RELEASE))) {
                    echec("clavier-voix", "« parler » : événements " + pourParler);
                }
                if (!pourChuchoter.equals(List.of(t + ":" + GLFW.GLFW_PRESS, t + ":" + GLFW.GLFW_RELEASE))) {
                    echec("clavier-voix", "« chuchoter » : événements " + pourChuchoter);
                }
                if (!pourUnChiffre.isEmpty()) {
                    echec("clavier-voix", "un chiffre est sorti de l'écran : " + pourUnChiffre);
                }
                if (!(mc.screen instanceof PhoneWebScreen)) {
                    echec("clavier-voix", "l'écran n'est plus le téléphone : " + mc.screen);
                }
            } finally {
                parler.setKey(avantParler);
                chuchoter.setKey(avantChuchoter);
                net.minecraft.client.KeyMapping.resetMapping();
            }
        }, () -> attendu(0.5), 5, "k05-voix-telephone-ouvert");
        echap(mc, "clavier-echap-fin");
        pas("clavier-correspondant-sort", () -> serveur(mc, (srv, sp) -> {
            if (ecouteAppels != null) {
                dev.oneforallsurvival.phone.call.CallService.removeListener(ecouteAppels);
            }
            JoueurDeTest j = correspondant;
            if (j != null && j.connecte()) {
                j.quitter();
            }
            correspondant = null;
        }), () -> correspondant == null, 5, null);
        bilanDuPont();
    }

    // --- Les urgences (lot T3, tâche 11b) -----------------------------------------------------------------------------

    /** L'état de l'appel du joueur au serveur intégré : {@code ACTIVE}, {@code RINGING}… ou {@code aucun}. */
    private static String etatAuServeur(Minecraft mc, String cle) {
        return lu(mc, cle, (srv, sp) -> dev.oneforallsurvival.phone.call.CallService.callOf(sp)
                .map(x -> x.state().name()).orElse("aucun"));
    }

    /** Les chiffres composés au clavier du repli (champ privé, lu pour l'essai). */
    private static String chiffresComposes(Minecraft mc) {
        if (!(mc.screen instanceof PhoneFallbackScreen r)) {
            return "?";
        }
        try {
            java.lang.reflect.Field f = PhoneFallbackScreen.class.getDeclaredField("dialed");
            f.setAccessible(true);
            return f.get(r).toString();
        } catch (ReflectiveOperationException e) {
            return "?" + e;
        }
    }

    /**
     * Mode {@code urgence} (lot T3, tâche 11b), page (MCEF) ou repli : le correspondant simulé a le rôle
     * {@code policier} et un téléphone chargé ; écran verrouillé, « Urgence », « Police 17 » touché, le policier décroche
     * au serveur, appel actif relu, raccroché. Puis l'accueil (« Téléphone »), le clavier (17 et 15), un appel entrant
     * du correspondant, répondu (en cours), raccroché. Clics au centre des boîtes de la page ou des boutons du repli.
     */
    private static void composerUrgence(Minecraft mc) {
        if (SERVEUR != null) {
            echec("urgence", "le mode urgence se joue en monde solo");
            return;
        }
        preparerAppels(mc, "urgence");
        pas("urgence-policier", () -> serveur(mc, (srv, sp) -> {
            ServerPlayer c = corr();
            dev.oneforallsurvival.role.RoleSavedData roles = dev.oneforallsurvival.role.RoleSavedData.get(srv);
            roles.book().assign(c.getUUID(), "policier");
            roles.setDirty();
            PhoneItem.ensurePhoneId(c.getMainHandItem());
            dev.oneforallsurvival.phone.call.EmergencyService.forgetCooldown();
            LU.put("role", roles.book().roleOf(c.getUUID()).id());
        }), () -> "policier".equals(LU.get("role")), 5, null);

        // 1. Écran verrouillé, « Urgence », « Police 17 », le policier décroche, raccroché.
        ouvrir(mc, "urgence-ouverture", Lock.LOCKED, null);
        pas("urgence-ecran-verrouille", () -> {}, () -> stable(mc, "lock-sos") && attendu(1.0), 10,
                "u01-verrouille-bouton-urgence");
        clic(mc, "urgence-bouton", "lock-sos", () -> stable(mc, "sos-17") && stable(mc, "sos-15")
                && stable(mc, "sos-cancel") && attendu(0.8), 10, "u02-panneau-urgence");
        clic(mc, "urgence-police-17", "sos-17", () -> {
            String s = lu(mc, "implique", (srv, sp) -> String.valueOf(
                    dev.oneforallsurvival.phone.call.EmergencyService.involved(sp.getUUID())));
            return phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.OUTGOING)
                    && appel().map(c -> "17".equals(c.number())).orElse(false) && "true".equals(s)
                    && stable(mc, "call-hangup") && attendu(0.8);
        }, 10, "u03-appel-police-17");
        pas("urgence-policier-decroche", () -> serveur(mc, (srv, sp) -> {
            ServerPlayer c = corr();
            int id = appel().map(x -> x.callId()).orElse(-1);
            boolean pris = dev.oneforallsurvival.phone.call.EmergencyService.answer(c,
                    PhoneItem.phoneId(c.getMainHandItem()).orElseThrow(), id);
            journal("le policier décroche l'urgence " + id + " : " + pris);
        }), () -> phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.ACTIVE)
                && "ACTIVE".equals(etatAuServeur(mc, "urgence-active")) && stable(mc, "call-hangup") && attendu(3.3),
                10, "u04-urgence-en-cours");
        pas("urgence-active-releve", () -> LU.remove("urgence-detail"), () -> {
            String s = lu(mc, "urgence-detail", (srv, sp) -> dev.oneforallsurvival.phone.call.CallService.callOf(sp)
                    .map(x -> x.state() + " " + x).orElse("aucun"));
            if (s.isEmpty()) {
                return false;
            }
            journal("appel d'urgence au serveur : " + s + " ; client " + appel().map(c -> c.phase() + " "
                    + c.number()).orElse(""));
            if (!s.startsWith("ACTIVE")) {
                echec("urgence-active-releve", "appel au serveur : " + s);
            }
            return true;
        }, 5, null);
        clic(mc, "urgence-raccrocher", "call-hangup", () -> phase(
                dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.NONE)
                && "aucun".equals(etatAuServeur(mc, "urgence-fin")) && attendu(0.7), 10, "u05-urgence-raccrochee");
        pas("urgence-fin-releve", () -> journal("fin de l'urgence : message « " + appel().map(c -> cleDuMessage(
                c.message())).orElse("") + " », fins au serveur " + FINS_APPEL), () -> attendu(2.5), 4, null);

        // 2. L'accueil, le clavier (17 et 15), un appel entrant répondu puis raccroché.
        pas("urgence-deverrouillage", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && stable(mc, "app-phone") && attendu(1.0), 15, "u06-accueil-telephone");
        if (web) {
            clic(mc, "urgence-appli", "app-phone", () -> stable(mc, "tel-tab-keypad") && attendu(1.0), 10, null);
            clic(mc, "urgence-clavier", "tel-tab-keypad", () -> stable(mc, "dial-call") && attendu(0.8), 10,
                    "u07-clavier");
        } else {
            clic(mc, "urgence-clavier", "app-phone", () -> stable(mc, "dial-call") && stable(mc, "dial-17")
                    && stable(mc, "dial-15") && attendu(0.8), 10, "u07-clavier");
        }
        if (!web) {
            // Relecture de la tâche 7 (C1) : la touche « 1 » cliquée garde le focus ; l'appel entrant reconstruit
            // l'écran et la retire. Espace ne doit plus l'actionner sous l'appel.
            clic(mc, "urgence-touche-1", "key-1", () -> chiffresComposes(mc).equals("1") && attendu(0.5), 5, null);
        }
        pas("urgence-entrant", () -> corrAppelle(mc), () -> phase(
                dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.INCOMING) && stable(mc, "call-answer")
                && attendu(0.8), 10, "u08-appel-entrant");
        if (!web) {
            pas("urgence-espace-sous-l-appel", () -> {
                Screen s = mc.screen;
                LU.put("focus-avant", String.valueOf(s.getFocused() == null ? "aucun"
                        : s.children().contains(s.getFocused()) ? "présent" : "retiré"));
                int scan = GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_SPACE);
                s.keyPressed(GLFW.GLFW_KEY_SPACE, scan, 0);
                s.charTyped(' ', 0);
                s.keyReleased(GLFW.GLFW_KEY_SPACE, scan, 0);
            }, () -> attendu(0.8), 5, null);
            pas("urgence-espace-releve", () -> {}, () -> {
                String champ = chiffresComposes(mc);
                boolean entrant = phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.INCOMING);
                journal("Espace sous l'appel entrant : focus avant « " + LU.get("focus-avant") + " », après "
                        + (mc.screen.getFocused() == null ? "aucun" : "un bouton") + ", chiffres composés « " + champ
                        + " », appel " + appel().map(c -> c.phase().name()).orElse("?"));
                if (!champ.equals("1") || !entrant) {
                    echec("urgence-espace-releve", "Espace a agi sous l'appel : « " + champ + " », entrant " + entrant);
                }
                return true;
            }, 3, null);
        }
        clic(mc, "urgence-repondre", "call-answer", () -> "ACTIVE".equals(etatAuServeur(mc, "repondu"))
                && phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.ACTIVE)
                && stable(mc, "call-hangup") && attendu(2.3), 10, "u09-appel-en-cours");
        clic(mc, "urgence-raccrocher-2", "call-hangup", () -> FINS_APPEL.contains(appelCorr + ":HUNG_UP")
                && phase(dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase.NONE) && attendu(1.5), 10,
                "u10-appel-raccroche");
        echap(mc, "urgence-echap-fin");
        pas("urgence-correspondant-sort", () -> serveur(mc, (srv, sp) -> {
            if (ecouteAppels != null) {
                dev.oneforallsurvival.phone.call.CallService.removeListener(ecouteAppels);
            }
            JoueurDeTest j = correspondant;
            if (j != null && j.connecte()) {
                j.quitter();
            }
            correspondant = null;
        }), () -> correspondant == null, 5, null);
        bilanDuPont();
    }

    // --- Les relevés dans la page (tâche 16) ----------------------------------------------------------------------------

    /** La dernière réponse de la page à un relevé de l'essai (message {@code essai}), gardée par le témoin du pont. */
    @Nullable private static volatile JsonObject releve;
    private static boolean releveDemande;
    private static double[] boutonDesactiver;
    private static long premierAppuiNs;
    private static boolean secondAppui;
    private static int desactiverAvant;

    /**
     * La page évalue {@code objet} (un littéral JavaScript) et le renvoie par le pont ; le témoin le garde dans
     * {@link #releve} sans le transmettre au jeu. Demandé une fois par pas : {@code null} tant que rien n'est revenu.
     */
    @Nullable
    private static JsonObject demander(Minecraft mc, String objet) {
        if (!releveDemande && mc.screen instanceof PhoneWebScreen w) {
            releveDemande = true;
            releve = null;
            executerDansLaPage(w, "(function(){var r=" + objet + ";r.a='essai';window.oneforall.send(r);})();");
        }
        return releve;
    }

    private static String texteDe(String id) {
        return "((document.getElementById('" + id + "')||{}).textContent||'')";
    }

    /**
     * « Désactiver » touché deux fois en un dixième de seconde : le premier appui arme (« Confirmer ? »), le second ne
     * confirme pas. La page compte elle-même les clics reçus sur le bouton et leur écart : le « rien ne part » ne vaut
     * que si les deux lui sont arrivés.
     */
    private static void doubleAppui(Minecraft mc) {
        pas("antenne-desactiver-temoin", () -> executerDansLaPage((PhoneWebScreen) mc.screen,
                "window.__appuis=[];document.addEventListener('click',function(e){if(e.target.closest&&"
                        + "e.target.closest('[id^=\"op-deact-\"]')){window.__appuis.push(performance.now());}},true);"),
                () -> attendu(0.3), 3, null);
        pas("antenne-desactiver-double", () -> stable(mc, "op-deact-" + idCible), () -> {
            desactiverAvant = SORTES_DU_PONT.getOrDefault("opDeactivate", 0);
            boutonDesactiver = centre(mc, "op-deact-" + idCible);
            secondAppui = false;
            releveDemande = false;
            premierAppuiNs = System.nanoTime();
            cliquerEn(mc, boutonDesactiver);
        }, () -> {
            if (!secondAppui) {
                if (secondes(premierAppuiNs) >= 0.1) {
                    secondAppui = true;
                    cliquerEn(mc, boutonDesactiver);
                }
                return false;
            }
            lireAppli(mc);
            if (secondes(premierAppuiNs) < 1.0) {
                return false;
            }
            JsonObject r = demander(mc, "{n:window.__appuis.length,ecart:window.__appuis.length>1?"
                    + "Math.round(window.__appuis[1]-window.__appuis[0]):-1,libelle:" + texteDe("op-deact-" + idCible) + "}");
            if (r == null) {
                return false;
            }
            int n = r.get("n").getAsInt();
            int ecart = r.get("ecart").getAsInt();
            String libelle = r.get("libelle").getAsString();
            int envoyes = SORTES_DU_PONT.getOrDefault("opDeactivate", 0) - desactiverAvant;
            String bilan = "double appui : " + n + " clic(s) reçus par la page, écart " + ecart + " ms, bouton « " + libelle
                    + " », opDeactivate envoyé(s) " + envoyes + ", carte " + appliCarte + " au serveur";
            journal(bilan);
            if (n != 2 || ecart < 0 || ecart >= 400 || !"Confirmer ?".equals(libelle) || envoyes != 0
                    || !"SUSPENDED".equals(appliCarte)) {
                echec("antenne-desactiver-double", bilan);
            }
            return true;
        }, 8, "a04b-desactiver-confirmer");
    }

    /** Les montants de l'onglet Tarif tels que la page les écrit : les coupures formatées par le jeu, jamais les cuivres bruts. */
    private static void montants(Minecraft mc) {
        pas("antenne-montants", () -> releveDemande = false, () -> {
            JsonObject r = demander(mc, "{jour:" + texteDe("ant-tariff-today") + ",demain:" + texteDe("ant-tariff-next")
                    + ",plafond:" + texteDe("ant-cap") + "}");
            dev.oneforallsurvival.phone.net.OperatorView v = vueAppli(mc);
            if (r == null || v == null || v.nextTariff() == null) {
                return false;
            }
            String jour = dev.oneforallsurvival.economy.CoinMath.format(v.tariff()).getString();
            String demain = dev.oneforallsurvival.economy.CoinMath.format(v.nextTariff()).getString();
            String plafond = dev.oneforallsurvival.economy.CoinMath.format(v.cap()).getString();
            String bilan = "montants dans la page : jour « " + r.get("jour").getAsString() + " », demain « "
                    + r.get("demain").getAsString() + " » (" + v.nextTariff() + " cuivres), plafond « "
                    + r.get("plafond").getAsString() + " » (" + v.cap() + " cuivres)";
            journal(bilan);
            if (!jour.equals(r.get("jour").getAsString()) || !demain.equals(r.get("demain").getAsString())
                    || !plafond.equals(r.get("plafond").getAsString()) || v.nextTariff() != tarifEssaye) {
                echec("antenne-montants", bilan + " ; attendus « " + jour + " », « " + demain + " », « " + plafond + " »");
            }
            return true;
        }, 6, "a06b-montants");
    }

    /**
     * L'icône grisée touchée dans la page : le geste part, l'appli ne se montre à aucun instant (un témoin de la page
     * surveille la classe {@code ant-open}), le panneau reste fermé au serveur, et seule l'alerte « Pas de réseau. »
     * répond ; « OK » rend l'accueil.
     */
    private static void iconeGrisee(Minecraft mc) {
        pas("antenne-grisee-temoin", () -> executerDansLaPage((PhoneWebScreen) mc.screen,
                "window.__appliVue=false;new MutationObserver(function(){if(document.querySelector('.ant-open')){"
                        + "window.__appliVue=true;}}).observe(document.documentElement,{attributes:true,subtree:true,"
                        + "attributeFilter:['class']});"), () -> attendu(0.3), 3, null);
        pas("antenne-icone-grisee", () -> stable(mc, "app-antenna"), () -> {
            appliAvant = SORTES_DU_PONT.getOrDefault("antennaApp", 0);
            cliquer(mc, "app-antenna");
        }, () -> {
            lireAppli(mc);
            return mc.screen instanceof PhoneWebScreen w && w.antennaSession() < 0 && stable(mc, "alert-0")
                    && attendu(1.2);
        }, 10, "a10-icone-grisee-message");
        pas("antenne-icone-grisee-releve", () -> releveDemande = false, () -> {
            lireAppli(mc);
            JsonObject r = demander(mc, "{vue:window.__appliVue,grise:document.getElementById('app-antenna').classList"
                    + ".contains('off'),titre:" + texteDe("alert-title") + ",message:" + texteDe("alert-msg") + "}");
            if (r == null) {
                return false;
            }
            int envoyes = SORTES_DU_PONT.getOrDefault("antennaApp", 0) - appliAvant;
            String bilan = "icône grisée : grisée " + r.get("grise").getAsBoolean() + ", appli montrée "
                    + r.get("vue").getAsBoolean() + ", alerte « " + r.get("titre").getAsString() + " » « "
                    + r.get("message").getAsString() + " », antennaApp envoyé(s) " + envoyes + ", panneau au serveur "
                    + appliPanneau;
            journal(bilan);
            if (!r.get("grise").getAsBoolean() || r.get("vue").getAsBoolean() || envoyes != 1 || appliPanneau
                    || !"Pas de réseau.".equals(r.get("message").getAsString())) {
                echec("antenne-icone-grisee-releve", bilan);
            }
            return true;
        }, 6, null);
        clic(mc, "antenne-icone-grisee-ok", "alert-0", () -> mc.screen instanceof PhoneWebScreen w && w.antennaSession() < 0
                && stable(mc, "app-antenna") && !w.rects().containsKey("op-close") && attendu(1.0), 10,
                "a11-accueil-apres-le-message");
    }

    // --- La prise à travers son mur (lot T2, tâche 16) ------------------------------------------------------------------

    @Nullable private static volatile BlockPos filMur;
    private static volatile String filValeurs = "";
    private static volatile int filTelephone = -1;
    private static volatile int filReserve = -1;
    private static volatile String filMode = "";
    private static int filDepart;
    private static int filAvant;
    private static long filCleNs;

    private static BlockPos prisePos() {
        return filMur.west();
    }

    private static BlockPos connecteurMur() {
        return filMur.above();
    }

    private static BlockPos filSource() {
        return filMur.offset(0, -1, 3);
    }

    private static BlockPos connecteurSource() {
        return filSource().above();
    }

    /** La charge du téléphone branché, la réserve de la prise et le mode du connecteur du mur, relus au serveur. */
    private static void lireLeFil(Minecraft mc) {
        serveur(mc, (srv, sp) -> {
            ServerLevel level = sp.serverLevel();
            filMode = dev.oneforallsurvival.dev.ReseauxDev.propriete(level.getBlockState(connecteurMur()), "mode")
                    .toLowerCase(Locale.ROOT);
            if (level.getBlockEntity(prisePos()) instanceof WallSocketBlockEntity s) {
                filTelephone = s.phone().isEmpty() ? -1 : PhoneItem.stored(s.phone());
                filReserve = s.energy().getEnergyStored();
                filValeurs = "téléphone " + filTelephone + " FE, réserve de la prise " + filReserve
                        + " FE, connecteur du mur « " + filMode + " », prise " + s.state() + ", tic " + level.getGameTime();
            } else {
                filValeurs = "aucune prise ; connecteur du mur « " + filMode + " »";
            }
        });
    }

    /** Un vrai clic droit du client sur la face {@code face} du bloc, l'objet de la case {@code slot} en main. */
    private static void utiliser(Minecraft mc, int slot, BlockPos pos, Direction face) {
        mc.player.getInventory().selected = slot;
        Vec3 point = Vec3.atCenterOf(pos).add(face.getStepX() * 0.3, face.getStepY() * 0.3, face.getStepZ() * 0.3);
        var r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, pos, false));
        journal("clic droit en " + pos.toShortString() + " face " + face + ", main " + mc.player.getMainHandItem() + " : " + r);
    }

    private static boolean bloc(Minecraft mc, BlockPos pos, String id) {
        return id.equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(mc.level.getBlockState(pos).getBlock())));
    }

    /** Les valeurs montrées sur l'image du pas en cours, déjà écrites en barre d'action. */
    private static boolean releveMontre;
    private static int filMontre = -1;
    private static String filModeMontre = "";

    /**
     * Un pas qui relit le serveur pendant {@code duree} secondes : une demi-seconde avant la fin, les valeurs sont figées,
     * écrites en barre d'action (l'image les porte) et au journal, puis jugées par {@code juger}.
     */
    private static void relever(Minecraft mc, String nom, double duree, java.util.function.Supplier<String> titre,
            Runnable juger, String image) {
        pas(nom, () -> {
            releveMontre = false;
            filAvant = filTelephone;
        }, () -> {
            if (!releveMontre) {
                lireLeFil(mc);
                if (attendu(duree - 0.5)) {
                    releveMontre = true;
                    filMontre = filTelephone;
                    filModeMontre = filMode;
                    journal(titre.get() + " : " + filValeurs);
                    mc.gui.setOverlayMessage(Component.literal("Relevé serveur : téléphone " + filMontre
                            + " FE, connecteur du mur « " + filModeMontre + " »"), false);
                    juger.run();
                }
                return false;
            }
            return attendu(duree);
        }, duree + 3, image);
    }

    private static void composerPriseMur(Minecraft mc) {
        if (SERVEUR != null) {
            echec("prise", "le mode prise se joue en monde solo (gestes du serveur intégré)");
            return;
        }
        decor(mc, 5);
        pas("fil-decor", () -> serveur(mc, (srv, sp) -> {
            ServerLevel level = sp.serverLevel();
            BlockPos pied = sp.blockPosition();
            BlockPos mur = pied.offset(3, 1, 0);
            for (BlockPos air : BlockPos.betweenClosed(pied.offset(1, 0, -2), pied.offset(4, 3, 4))) {
                level.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
            }
            // Un pan de mur de planches (aucun bloc-entité) ; la source, trois blocs plus loin, de l'air entre les deux.
            for (BlockPos p : BlockPos.betweenClosed(mur.offset(0, -1, -1), mur.offset(0, 0, 1))) {
                level.setBlockAndUpdate(p, Blocks.OAK_PLANKS.defaultBlockState());
            }
            Block creatif = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createaddition", "creative_energy"));
            level.setBlockAndUpdate(mur.offset(0, -1, 3), creatif.defaultBlockState());
            sp.getInventory().setItem(1, new ItemStack(PhoneRegistry.WALL_SOCKET_ITEM.get()));
            sp.getInventory().setItem(2, new ItemStack(ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("createaddition", "connector")), 2));
            sp.getInventory().setItem(3, new ItemStack(ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("createaddition", "copper_spool")), 4));
            sp.getInventory().setItem(4, new ItemStack(ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("create", "wrench"))));
            sp.setGameMode(GameType.SURVIVAL);
            sp.teleportTo(level, pied.getX() + 0.5, pied.getY(), pied.getZ() + 1.5, -90f, 8f);
            filMur = mur;
        }), () -> filMur != null && bloc(mc, filSource(), "createaddition:creative_energy")
                && bloc(mc, filMur, "minecraft:oak_planks") && !mc.player.getInventory().getItem(4).isEmpty()
                && !mc.player.getInventory().getItem(2).isEmpty() && attendu(2.0), 10, null);
        pas("fil-prise-posee", () -> utiliser(mc, 1, filMur, Direction.WEST),
                () -> mc.level.getBlockState(prisePos()).is(PhoneRegistry.WALL_SOCKET.get()) && attendu(0.5), 5, null);
        pas("fil-telephone-branche", () -> utiliser(mc, caseDuTelephone, prisePos(), Direction.WEST),
                () -> mc.player.getInventory().getItem(caseDuTelephone).isEmpty()
                        && mc.level.getBlockState(prisePos()).getValue(WallSocketBlock.PLUGGED) && attendu(0.5), 5, null);
        pas("fil-connecteur-source", () -> utiliser(mc, 2, filSource(), Direction.UP),
                () -> bloc(mc, connecteurSource(), "createaddition:connector") && attendu(0.5), 5, null);
        pas("fil-connecteur-mur", () -> utiliser(mc, 2, filMur, Direction.UP),
                () -> bloc(mc, connecteurMur(), "createaddition:connector") && attendu(0.5), 5, null);
        // Témoin : sans fil, rien n'arrive (la source est à trois blocs, rien ne la touche).
        pas("fil-sans-fil", () -> lireLeFil(mc), () -> {
            lireLeFil(mc);
            return attendu(1.0) && filTelephone >= 0;
        }, 5, null);
        relever(mc, "fil-sans-fil-releve", 4.0, () -> "sans fil, 4 s de plus (avant : " + filAvant + " FE)", () -> {
            if (filMontre != filAvant || filReserve != 0) {
                echec("fil-sans-fil-releve", "sans fil, du courant est arrivé : " + filValeurs + ", avant " + filAvant);
            }
        }, "p01-sans-fil");
        pas("fil-bobine-source", () -> utiliser(mc, 3, connecteurSource(), Direction.UP), () -> attendu(0.8), 5, null);
        pas("fil-bobine-mur", () -> utiliser(mc, 3, connecteurMur(), Direction.UP), () -> attendu(0.8), 5, null);
        pas("fil-en-charge", () -> {
            filDepart = filTelephone; // la dernière valeur lue sans fil
            mc.player.getInventory().selected = 8;
        }, () -> {
            lireLeFil(mc);
            return filTelephone >= filDepart + 2000 && "push".equals(filMode);
        }, 20, null);
        relever(mc, "fil-en-charge-releve", 3.0, () -> "connecteur sur le mur, 3 s après " + filAvant + " FE", () -> {
            if (filMontre <= filAvant || !"push".equals(filModeMontre)) {
                echec("fil-en-charge-releve", "la charge ne monte plus : " + filValeurs + ", avant " + filAvant);
            }
        }, "p02-en-charge");
        // La clé de Create sur le connecteur du mur : de « sortie » à « aucun » en un clic.
        pas("fil-cle", () -> {
            filCleNs = System.nanoTime();
            utiliser(mc, 4, connecteurMur(), Direction.UP);
        }, () -> {
            lireLeFil(mc);
            return "none".equals(filMode) && attendu(0.5);
        }, 5, "p03-cle");
        // La réserve déjà arrivée (4 000 FE au plus) finit dans le téléphone ; ensuite plus rien ne bouge.
        pas("fil-reserve-videe", () -> mc.player.getInventory().selected = 8, () -> {
            lireLeFil(mc);
            return attendu(8.0);
        }, 12, null);
        relever(mc, "fil-reste-sur-aucun", 8.0, () -> String.format(Locale.ROOT, "%.0f s après la clé (téléphone 8 s plus tôt : "
                + "%d FE)", secondes(filCleNs), filAvant), () -> {
            // Le téléphone n'est pas plein : une charge qui ne bouge plus dit bien que rien n'arrive.
            if (!"none".equals(filModeMontre) || filMontre != filAvant || filMontre >= PhoneConfig.capacity()) {
                echec("fil-reste-sur-aucun", "le connecteur ou la charge ont changé (capacité " + PhoneConfig.capacity()
                        + ") : " + filValeurs + ", avant " + filAvant);
            }
        }, "p04-reste-sur-aucun");
    }

    // --- Serveur dédié : le signal à deux téléphones et l'appli « Antenne » (lot T2, tâche 13) ------------------------

    private static int appliAvant;
    private static int fermeAvant;
    private static int tarifsAvant;

    private static final java.util.regex.Pattern CLE_VALEUR =
            java.util.regex.Pattern.compile("(\\w+)=(\\[[^\\]]*\\]|\\{[^}]*\\}|[^ ]+)");
    @Nullable private static volatile BlockPos origine;
    private static volatile int barresServeur = -2;
    private static volatile String noteServeur = "";

    /** Une commande par la console RCON du serveur dédié, sa réponse lue en {@code cle=valeur}. */
    private static Map<String, String> rconLu(String commande) {
        String r = E2eReseau.rcon(commande);
        Map<String, String> out = new LinkedHashMap<>();
        java.util.regex.Matcher m = CLE_VALEUR.matcher(r);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static List<String> liste(@Nullable String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.length() < 2) {
            return out;
        }
        for (String x : s.substring(1, s.length() - 1).split(";")) {
            if (!x.isBlank()) {
                out.add(x);
            }
        }
        return out;
    }

    private static String nomJoueur(Minecraft mc) {
        return mc.player.getGameProfile().getName();
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static BlockPos antenneM() {
        return origine;
    }

    /** Lagon à 64 blocs de Mairie (quatre chunks) : chargée dès que le joueur est entre les deux. */
    private static BlockPos antenneL() {
        return origine.offset(0, 0, 64);
    }

    /** La seconde antenne de Mairie, sa propre source : la première coupée, le téléphone garde une barre. */
    private static BlockPos antenneM2() {
        return origine.offset(-3, 0, 0);
    }

    /** Les barres que le serveur calcule pour le téléphone de la case {@code slot} du joueur, à sa place. */
    private static int barresDuServeur(Minecraft mc, int slot) {
        for (String t : liste(rconLu("e2etel etat " + nomJoueur(mc)).get("telephones"))) {
            String[] p = t.split(":");
            if (Integer.parseInt(p[0]) == slot) {
                return Integer.parseInt(p[3]);
            }
        }
        return -1;
    }

    /** Le joueur (créatif, en vol) est téléporté par la console ; il ne tombe pas. */
    private static void allerA(Minecraft mc, String nom, BlockPos ou) {
        pas(nom, () -> {
            if (mc.screen != null) {
                mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), 0);
            }
            E2eReseau.rcon("tp " + nomJoueur(mc) + " " + (ou.getX() + 0.5) + " " + ou.getY() + " " + (ou.getZ() + 0.5));
        }, () -> {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            return mc.screen == null && mc.player.blockPosition().distManhattan(ou) <= 2 && attendu(1.5);
        }, 15, null);
    }

    /** Le téléphone de la case {@code slot} ouvert d'un vrai usage : les barres reçues égalent celles du serveur. */
    private static void releverBarres(Minecraft mc, String nom, int slot, int attendues, @Nullable String image) {
        pas(nom + "-case", () -> mc.player.getInventory().selected = slot,
                () -> mc.player.getInventory().selected == slot && attendu(0.2), 3, null);
        ouvrir(mc, nom, Lock.LOCKED, image);
        pas(nom + "-barres", () -> barresServeur = barresDuServeur(mc, slot), () -> {
            int vues = vueReseau().bars();
            journal(nom + " : barres reçues " + vues + ", serveur " + barresServeur + ", attendues " + attendues + " ; "
                    + resumeReseau(vueReseau()));
            if (vues != barresServeur || (attendues >= 0 && vues != attendues)) {
                echec(nom + "-barres", "reçues " + vues + ", serveur " + barresServeur + ", attendues " + attendues);
            }
            return true;
        }, 3, null);
        echap(mc, nom + "-echap");
    }

    /** Un téléphone neuf de la case {@code slot} : assistant, code, puis la carte de {@code operateur} glissée. */
    private static void configurer(Minecraft mc, String nom, int slot, String operateur) {
        pas(nom + "-case", () -> mc.player.getInventory().selected = slot,
                () -> mc.player.getInventory().selected == slot && attendu(0.2), 3, null);
        ouvrir(mc, nom + "-ouverture", Lock.SETUP, null);
        assistantJusquAuFond(mc, nom + "-assistant", "aube", false);
        assistantVersLeCode(mc, nom + "-assistant", false);
        cliquerCode(mc, nom + "-choix", CODE);
        pas(nom + "-confirmation-au-clavier", () -> stable(mc, "key-0"), () -> taper(mc, CODE), () -> etat(Lock.UNLOCKED), 10,
                null);
        toutEstPret(mc, nom + "-assistant", "aube", null);
        glisserCarte(mc, nom, operateur, null);
        echap(mc, nom + "-echap");
    }

    /** Réglages, feuille « Carte SIM », la carte de {@code operateur} glissée par un vrai clic ; retour aux Réglages. */
    private static void glisserCarte(Minecraft mc, String nom, String operateur, @Nullable String image) {
        clic(mc, nom + "-reglages", id("app-settings", "settings"), () -> cible(mc, id("row-sim", "sim")), 10, null);
        clic(mc, nom + "-feuille-sim", id("row-sim", "sim"), () -> carteDe(operateur) != null, 10, null);
        pas(nom + "-glisser", () -> carteDe(operateur) != null && stable(mc, carteDe(operateur)),
                () -> cliquer(mc, carteDe(operateur)), () -> {
                    PhoneNetView n = vueReseau();
                    return n.sim() == PhoneSims.Validity.ACTIVE && operateur.equals(n.operator()) && attendu(0.6);
                }, 10, image);
        clic(mc, nom + "-fermer-feuille", id("sim-close", "back"), () -> cible(mc, id("row-airplane", "airplane")), 10, null);
    }

    /** La commande {@code sim-choice-<case>} de la carte de cet opérateur dans la feuille, ou nul. */
    @Nullable
    private static String carteDe(String operateur) {
        for (PhoneSims.SimChoice c : vueReseau().choices()) {
            if (operateur.equals(c.operator()) && c.validity() == PhoneSims.Validity.ACTIVE) {
                return "sim-choice-" + c.slot();
            }
        }
        return null;
    }

    /**
     * Mode {@code signal} (serveur dédié) : la console fonde Mairie au nom du joueur et Lagon au nom de Fondatrice
     * (simulée), chacune avec sa source d'énergie créative ; deux téléphones configurés par de vrais clics, A avec la
     * carte de Mairie, B avec celle de Lagon (remises par la console). À 0, 100, 200 et 300 blocs de chaque antenne, A
     * puis B ouverts d'un vrai usage : barres reçues = barres du serveur. Source de Lagon coupée : B à 0, A inchangé.
     * Mode avion sur A par un vrai clic, carte retirée puis remise par la feuille. Sans MCEF (repli) : le relevé à 100
     * blocs seulement, puis « Antenne » qui ouvre le panneau Minecraft, Échap qui rend le téléphone, et un changement de
     * dimension sous le panneau qui ferme la session.
     */
    private static void composerSignal(Minecraft mc) {
        if (SERVEUR == null) {
            echec("signal", "le mode signal se joue sur un serveur dédié (-PtelephoneShotsServer)");
            return;
        }
        // L'origine se fixe à la composition : les pas qui suivent en calculent leurs places.
        BlockPos ici0 = mc.player.blockPosition();
        int x0 = ici0.getX() + 2000;
        x0 -= Math.floorMod(x0, 16);
        origine = new BlockPos(x0, 150, ici0.getZ());
        pas("signal-preparation", () -> {
            String n = nomJoueur(mc);
            for (String c : List.of("op " + n, "gamemode creative " + n, "clear " + n, "difficulty peaceful", "time set noon",
                    "gamerule doDaylightCycle false", "gamerule doMobSpawning false",
                    "give " + n + " oneforallsurvival:phone{energy:57600} 1", "give " + n + " oneforallsurvival:phone{energy:57600} 1")) {
                E2eReseau.rcon(c);
            }
            // Entre Mairie et Lagon : les deux chunks se chargent.
            E2eReseau.rcon("tp " + n + " " + (origine.getX() + 3) + " " + origine.getY() + " " + (origine.getZ() + 32));
        }, () -> attendu(5.0) && mc.player.getInventory().getItem(1).is(PhoneRegistry.PHONE.get())
                && "true".equals(rconLu("e2etel antenne " + xyz(antenneL())).get("charge"))
                && "true".equals(rconLu("e2etel antenne " + xyz(antenneM2())).get("charge")), 60, null);
        pas("signal-antennes", () -> {
            String n = nomJoueur(mc);
            noteServeur = String.join(" | ",
                    E2eReseau.rcon("e2etel fonder " + n + " " + xyz(antenneM()) + " " + MAIRIE),
                    E2eReseau.rcon("e2etel source " + xyz(antenneM().west()) + " true"),
                    // La seconde antenne posée par le fondateur rejoint Mairie (la fondation, elle, est refusée : déjà fait).
                    E2eReseau.rcon("e2etel fonder " + n + " " + xyz(antenneM2()) + " " + MAIRIE),
                    E2eReseau.rcon("e2etel source " + xyz(antenneM2().west()) + " true"),
                    E2eReseau.rcon("e2etel entrer Fondatrice " + xyz(antenneL().east(2))),
                    E2eReseau.rcon("e2etel fonder Fondatrice " + xyz(antenneL()) + " " + LAGON),
                    E2eReseau.rcon("e2etel source " + xyz(antenneL().west()) + " true"),
                    E2eReseau.rcon("e2etel quitter Fondatrice"),
                    E2eReseau.rcon("e2etel remettre " + MAIRIE + " " + n),
                    E2eReseau.rcon("e2etel remettre " + LAGON + " " + n));
            journal("préparation : " + noteServeur);
        }, () -> attendu(2.0) && "2".equals(rconLu("e2etel operateur " + MAIRIE).get("enService"))
                && "1".equals(rconLu("e2etel operateur " + LAGON).get("enService")), 30, null);
        pas("signal-antennes-en-service", () -> journal("Mairie " + rconLu("e2etel antenne " + xyz(antenneM())) + " ; Lagon "
                + rconLu("e2etel antenne " + xyz(antenneL()))), () -> true, 1, null);
        configurer(mc, "signal-a", 0, MAIRIE);
        configurer(mc, "signal-b", 1, LAGON);
        int[] distances = web ? new int[] {0, 100, 200, 300} : new int[] {100};
        int[] attendues = {4, 3, 2, 0};
        for (int i = 0; i < distances.length; i++) {
            int d = distances[i];
            int a = web ? attendues[i] : 3;
            allerA(mc, "signal-mairie-" + d, antenneM().offset(d, 0, 2));
            releverBarres(mc, "signal-mairie-" + d + "-a", 0, a, "s-mairie-" + d + "-a");
            releverBarres(mc, "signal-mairie-" + d + "-b", 1, -1, "s-mairie-" + d + "-b");
            if (web) {
                allerA(mc, "signal-lagon-" + d, antenneL().offset(d, 0, 2));
                releverBarres(mc, "signal-lagon-" + d + "-a", 0, -1, "s-lagon-" + d + "-a");
                releverBarres(mc, "signal-lagon-" + d + "-b", 1, a, "s-lagon-" + d + "-b");
            }
        }
        // Source de Lagon coupée : B à 0, A inchangé (près de Mairie, Lagon à 62 blocs : B avait 4 barres).
        allerA(mc, "signal-coupure-place", antenneM().offset(0, 0, 2));
        pas("signal-lagon-coupee", () -> {
            E2eReseau.rcon("e2etel source " + xyz(antenneL().west()) + " false");
            E2eReseau.rcon("e2etel vider " + xyz(antenneL()));
        }, () -> attendu(1.0) && "0".equals(rconLu("e2etel operateur " + LAGON).get("enService")), 20, null);
        releverBarres(mc, "signal-coupee-b", 1, 0, "s-lagon-coupee-b");
        releverBarres(mc, "signal-coupee-a", 0, 4, "s-lagon-coupee-a");
        pas("signal-lagon-retablie", () -> E2eReseau.rcon("e2etel source " + xyz(antenneL().west()) + " true"),
                () -> attendu(1.0) && "1".equals(rconLu("e2etel operateur " + LAGON).get("enService")), 20, null);
        // Mode avion sur A par un vrai clic, puis la carte retirée et remise par la feuille.
        pas("signal-a-case", () -> mc.player.getInventory().selected = 0, () -> attendu(0.2), 3, null);
        ouvrir(mc, "signal-a-ouverture", Lock.LOCKED, null);
        pas("signal-a-deverrouillage", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, id("app-settings", "settings")) && attendu(0.6), 15, null);
        clic(mc, "signal-a-reglages", id("app-settings", "settings"), () -> cible(mc, id("row-airplane", "airplane")), 10, null);
        clic(mc, "signal-mode-avion", id("row-airplane", "airplane"),
                () -> PhoneClient.last().map(p -> p.airplane() && p.net().bars() == 0).orElse(false) && attendu(0.8), 10, null);
        pause("s-mode-avion");
        clic(mc, "signal-mode-avion-coupe", id("row-airplane", "airplane"),
                () -> PhoneClient.last().map(p -> !p.airplane() && p.net().bars() == 4).orElse(false) && attendu(0.8), 25, null);
        // Le frein du téléphone (2 tics) : chaque geste attend la réponse du précédent (tâche 10).
        clic(mc, "signal-feuille-sim", id("row-sim", "sim"), () -> cible(mc, "sim-remove") && attendu(0.8), 10, null);
        clic(mc, "signal-retirer", "sim-remove", () -> vueReseau().sim() == PhoneSims.Validity.NONE && attendu(0.6), 10,
                "s-carte-retiree");
        pas("signal-remettre", () -> carteDe(MAIRIE) != null && stable(mc, carteDe(MAIRIE)), () -> cliquer(mc, carteDe(MAIRIE)),
                () -> vueReseau().sim() == PhoneSims.Validity.ACTIVE && MAIRIE.equals(vueReseau().operator())
                        && vueReseau().bars() == 4 && attendu(0.6), 10, "s-carte-remise");
        clic(mc, "signal-fermer-feuille", id("sim-close", "back"), () -> cible(mc, id("row-airplane", "airplane")), 10, null);
        if (!web) {
            // Repli : « Antenne » ouvre le panneau Minecraft par-dessus le repli ; Échap rend le téléphone.
            clic(mc, "signal-retour-accueil", "back", () -> cible(mc, "app-antenna"), 10, null);
            clic(mc, "signal-appli-antenne", "app-antenna", () -> appliPrete(mc) && attendu(1.0), 15, "s-repli-panneau-antenne");
            pas("signal-echap-panneau", () -> panneau(mc) != null,
                    () -> panneau(mc).keyPressed(GLFW.GLFW_KEY_ESCAPE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), 0),
                    () -> mc.screen instanceof PhoneFallbackScreen && attendu(1.0)
                            && "false".equals(rconLu("e2etel etat " + nomJoueur(mc)).get("panneau"))
                            && "true".equals(rconLu("e2etel etat " + nomJoueur(mc)).get("session")), 10, "s-repli-echap");
            // Le panneau du repli remplacé par un autre écran (changement de dimension) : la session se ferme.
            clic(mc, "signal-appli-antenne-2", "app-antenna", () -> appliPrete(mc) && attendu(1.0), 15, null);
            pas("signal-dimension", () -> E2eReseau.rcon("execute in minecraft:the_nether run tp " + nomJoueur(mc) + " 0 100 0"),
                    () -> {
                        Map<String, String> e = rconLu("e2etel etat " + nomJoueur(mc));
                        return attendu(3.0) && !(mc.screen instanceof OperatorPanelScreen) && "false".equals(e.get("panneau"))
                                && "false".equals(e.get("session"));
                    }, 30, null);
            pas("signal-dimension-releve", () -> journal("changement de dimension sous le panneau : écran " + mc.screen
                    + ", serveur " + rconLu("e2etel etat " + nomJoueur(mc))), () -> true, 1, null);
            pas("signal-retour-monde", () -> E2eReseau.rcon("execute in minecraft:overworld run tp " + nomJoueur(mc) + " "
                    + xyz(antenneM().offset(0, 0, 2))), () -> attendu(4.0) && mc.player.level().dimension()
                    == net.minecraft.world.level.Level.OVERWORLD, 30, null);
        } else {
            echap(mc, "signal-echap-fin");
        }
        bilanDuPont();
    }

    /**
     * Mode {@code antenne} sur un serveur dédié (après le mode {@code signal}, même monde : le joueur a fondé Mairie, le
     * téléphone de la case 0 porte sa carte) : l'appli « Antenne » par un vrai clic, les trois onglets, une carte
     * suspendue puis réactivée, un tarif refusé puis enregistré au clavier ; la vue de la page comparée à la console
     * (cartes, consommation) ; la source de Mairie coupée, vue en direct ; la page rechargée appli ouverte (le serveur
     * ferme le panneau) ; « Accueil » avant la première vue.
     */
    private static void composerAntenneServeur(Minecraft mc) {
        // L'antenne de Mairie (fondée au mode signal) relue à la composition : les places des pas en découlent.
        Map<String, String> m0 = rconLu("e2etel operateur " + MAIRIE);
        origine = null;
        for (String a : liste(m0.get("antennes"))) {
            // La principale (la première posée au mode signal) : position:en service:détail:FE du jour:principale.
            String[] champs = a.split(":");
            if ("true".equals(champs[4])) {
                String[] t = champs[0].split(",");
                origine = new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]));
            }
        }
        if (origine == null || !nomJoueur(mc).equals(m0.get("fondateur"))) {
            echec("antenne-serveur", "le mode antenne suit le mode signal : Mairie absente ou d'un autre (" + m0 + ")");
            return;
        }
        pas("antenne-serveur-depart", () -> {
            String n = nomJoueur(mc);
            int x = mc.player.blockPosition().getX();
            E2eReseau.rcon("op " + n);
            E2eReseau.rcon("gamemode creative " + n);
            E2eReseau.rcon("e2etel cartes " + MAIRIE + " 3");
            mc.player.getInventory().selected = 0;
            journal("départ x=" + x + " ; Mairie en " + xyz(origine));
        }, () -> attendu(1.0) && mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get()), 10, null);
        allerA(mc, "antenne-serveur-place", antenneM().offset(3, 0, 2));
        ouvrir(mc, "antenne-ouverture-1", Lock.LOCKED, null);
        pas("antenne-deverrouillage", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, "app-antenna") && vueReseau().founderAppAvailable()
                        && attendu(0.6), 15, null);
        clic(mc, "antenne-appli", "app-antenna", () -> appliPrete(mc), 15, null);
        pause("a01-appli-reseau");
        pas("antenne-vue-console", () -> {}, () -> {
            dev.oneforallsurvival.phone.net.OperatorView v = vueAppli(mc);
            Map<String, String> m = rconLu("e2etel operateur " + MAIRIE);
            int total = 0;
            for (String e : m.get("etats").replace("{", "").replace("}", "").split(",")) {
                if (e.contains("=")) {
                    total += Integer.parseInt(e.split("=")[1].trim());
                }
            }
            // L'antenne principale, comparée à elle-même : la page et la console ne rangent pas leurs listes pareil.
            String console = "";
            for (String x : liste(m.get("antennes"))) {
                if (x.startsWith(origine.getX() + "," + origine.getY() + "," + origine.getZ() + ":")) {
                    console = x.split(":")[2];
                }
            }
            var a = v.antennas().stream().filter(x -> x.pos().equals(origine)).findFirst().orElseThrow().load();
            String page = a.standby() + "+" + a.clients() + "+" + a.traffic();
            journal("vue de l'appli : " + v.cardTotal() + " carte(s), consommation " + page + " ; console : " + total
                    + " carte(s), " + console + ", actives " + m.get("actives"));
            if (v.cardTotal() != total || !page.equals(console)) {
                echec("antenne-vue-console", "page " + v.cardTotal() + " / " + page + ", console " + total + " / " + console);
            }
            for (var c : v.cards()) {
                if (c.holderName().startsWith("Essai")) {
                    idCible = c.simId().toString();
                }
            }
            return !idCible.isEmpty();
        }, 5, null);
        ongletAppli(mc, "antenne-onglet-cartes", "cards", OperatorPanelLayout.Tab.CARDS, "a02-appli-cartes");
        boutonAppli(mc, "antenne-suspendre", () -> "op-card-" + idCible, OperatorActionPacket.Action.SUSPEND,
                () -> idCible, () -> {
                    lireAppli(mc);
                    return "SUSPENDED".equals(appliCarte) && attendu(0.8);
                }, "a03-carte-suspendue");
        boutonAppli(mc, "antenne-reactiver", () -> "op-card-" + idCible, OperatorActionPacket.Action.REACTIVATE,
                () -> idCible, () -> {
                    lireAppli(mc);
                    return "ACTIVE".equals(appliCarte) && attendu(0.8);
                }, "a04-carte-reactivee");
        ongletAppli(mc, "antenne-onglet-tarif", "tariff", OperatorPanelLayout.Tab.TARIFF, null);
        saisirTarif(mc, "antenne-tarif-refuse", true, "a05-tarif-refuse");
        saisirTarif(mc, "antenne-tarif", false, "a06-tarif-au-lendemain-montants");
        ongletAppli(mc, "antenne-onglet-reseau", "network", OperatorPanelLayout.Tab.NETWORK, null);
        pas("antenne-source-coupee", () -> {
            E2eReseau.rcon("e2etel source " + xyz(antenneM().west()) + " false");
            E2eReseau.rcon("e2etel vider " + xyz(antenneM()));
        }, () -> appliPrete(mc) && sourceVue(mc) && attendu(1.0), 30, "a07-source-coupee-en-direct");
        pas("antenne-source-retablie", () -> E2eReseau.rcon("e2etel source " + xyz(antenneM().west()) + " true"),
                () -> appliPrete(mc) && !sourceVue(mc) && attendu(1.0), 30, "a08-source-retablie");
        // La page rechargée, appli ouverte : la page rouverte ne connaît plus le panneau ; le serveur doit le fermer.
        pas("antenne-rechargement", () -> mc.screen instanceof PhoneWebScreen,
                () -> executerDansLaPage((PhoneWebScreen) mc.screen, "location.reload();"), () -> {
                    lireAppli(mc);
                    return attendu(4.0) && !appliPanneau && stable(mc, "app-settings");
                }, 20, "a09-apres-rechargement");
        // « Accueil » avant la première vue : l'icône puis la flèche de retour dans le même instant.
        // Le panneau est déjà fermé après le rechargement : seul le pont prouve que l'appli s'est ouverte (antennaApp) et
        // refermée (opClose) par ces deux clics (revue de la tâche 13, I2).
        pas("antenne-accueil-avant-vue", () -> mc.screen instanceof PhoneWebScreen && stable(mc, "app-antenna"), () -> {
            appliAvant = SORTES_DU_PONT.getOrDefault("antennaApp", 0);
            fermeAvant = SORTES_DU_PONT.getOrDefault("opClose", 0);
            executerDansLaPage((PhoneWebScreen) mc.screen,
                    "document.getElementById('app-antenna').click();document.getElementById('op-close').click();");
        }, () -> {
            lireAppli(mc);
            return attendu(3.0) && !appliPanneau && ((PhoneWebScreen) mc.screen).antennaSession() < 0
                    && SORTES_DU_PONT.getOrDefault("antennaApp", 0) == appliAvant + 1
                    && SORTES_DU_PONT.getOrDefault("opClose", 0) == fermeAvant + 1;
        }, 20, "a10-accueil-avant-la-vue");
        pas("antenne-accueil-avant-vue-releve", () -> journal("« Accueil » avant la vue : antennaApp " + appliAvant + " -> "
                + SORTES_DU_PONT.getOrDefault("antennaApp", 0) + ", opClose " + fermeAvant + " -> "
                + SORTES_DU_PONT.getOrDefault("opClose", 0) + ", panneau au serveur " + appliPanneau), () -> true, 1, null);
        echap(mc, "antenne-echap-fin");
        bilanDuPont();
    }

    @Nullable
    private static OperatorPanelScreen panneau(Minecraft mc) {
        return mc.screen instanceof OperatorPanelScreen p && p.back() != null ? p : null;
    }

    @Nullable
    private static dev.oneforallsurvival.phone.net.OperatorView vueAppli(Minecraft mc) {
        if (mc.screen instanceof PhoneWebScreen w) {
            return w.antennaView();
        }
        OperatorPanelScreen p = panneau(mc);
        return p == null ? null : p.view();
    }

    /** L'appli ouverte et sa vue reçue (page : boîtes stables ; repli : le panneau Minecraft par-dessus). */
    private static boolean appliPrete(Minecraft mc) {
        if (mc.screen instanceof PhoneWebScreen w) {
            return w.antennaView() != null && stable(mc, "op-tab-cards");
        }
        return panneau(mc) != null;
    }

    /** Ce que le serveur tient : demandes de Mairie, état de la carte visée, tarif de demain, panneau ouvert. */
    private static void lireAppli(Minecraft mc) {
        if (SERVEUR != null) {
            // Serveur dédié : relu par la console RCON (E2eTelephoneServeur).
            Map<String, String> op = rconLu("e2etel operateur " + MAIRIE);
            appliDemandes = liste(op.get("demandes")).size();
            appliDemain = op.get("tarifDemain") == null || "-".equals(op.get("tarifDemain")) ? -1L
                    : Long.parseLong(op.get("tarifDemain"));
            appliCarte = idCible.isEmpty() ? "" : rconLu("e2etel carte " + idCible).getOrDefault("etat", "");
            appliPanneau = "true".equals(rconLu("e2etel etat " + mc.player.getGameProfile().getName()).get("panneau"));
            return;
        }
        serveur(mc, (srv, sp) -> {
            PhoneOperators r = PhoneOperators.get(srv);
            appliDemandes = r.requestsOf(idMairie).size();
            appliCarte = idCible.isEmpty() ? "" : r.card(java.util.UUID.fromString(idCible))
                    .map(c -> c.state().name()).orElse("");
            appliDemain = r.byId(idMairie).map(o -> o.nextTariff() == null ? -1L : o.nextTariff()).orElse(-1L);
            appliPanneau = dev.oneforallsurvival.phone.net.OperatorPanels.session(sp.getUUID()).isPresent();
        });
    }

    private static String resumeAppli(Minecraft mc) {
        dev.oneforallsurvival.phone.net.OperatorView v = vueAppli(mc);
        String ecran = mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName();
        if (v == null) {
            return "écran " + ecran + ", aucune vue de l'appli (fermée)";
        }
        StringBuilder etats = new StringBuilder();
        for (var a : v.antennas()) {
            etats.append(a.main() ? "principale " : "").append(a.state()).append(' ');
        }
        return "écran " + ecran + ", numéro " + v.session() + ", antennes " + etats.toString().trim()
                + (panneau(mc) != null ? ", ligne d'état « " + panneau(mc).status() + " »" : "");
    }

    /** La source de l'antenne en {@code pos} coupée (bloc d'énergie remplacé par de la pierre), sa réserve vidée. */
    private static void couper(ServerPlayer sp, BlockPos pos) {
        BlockPos source = pos.equals(antenneMairie) ? pos.east() : pos.north();
        sp.serverLevel().setBlockAndUpdate(source, Blocks.STONE.defaultBlockState());
        if (sp.serverLevel().getBlockEntity(pos) instanceof AntennaBlockEntity be) {
            be.drainEnergy(Integer.MAX_VALUE);
        }
    }

    /** La vue de l'appli, toujours ouverte, montre une antenne qui n'est plus en marche (mise à jour en direct). */
    private static boolean sourceVue(Minecraft mc) {
        dev.oneforallsurvival.phone.net.OperatorView v = vueAppli(mc);
        return v != null && v.antennas().stream().anyMatch(a -> a.state() != dev.oneforallsurvival.machine.MachineState.RUNNING);
    }

    private static void cliquerEn(Minecraft mc, @Nullable double[] c) {
        if (c == null) {
            throw new IllegalStateException("commande absente du panneau (écran " + mc.screen + ")");
        }
        Screen s = mc.screen;
        s.mouseMoved(c[0], c[1]);
        s.mouseClicked(c[0], c[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        s.mouseReleased(c[0], c[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void ongletAppli(Minecraft mc, String nom, String page, OperatorPanelLayout.Tab tab,
            @Nullable String image) {
        if (web) {
            clic(mc, nom, "op-tab-" + page, () -> attendu(0.9), 10, image);
            return;
        }
        pas(nom, () -> panneau(mc) != null, () -> cliquerEn(mc, panneau(mc).centreOf(tab, null, "")),
                () -> panneau(mc) != null && panneau(mc).tab() == tab && attendu(0.5), 5, image);
    }

    private static void boutonAppli(Minecraft mc, String nom, java.util.function.Supplier<String> idPage,
            OperatorActionPacket.Action action, java.util.function.Supplier<String> arg, BooleanSupplier fini,
            @Nullable String image) {
        if (web) {
            pas(nom, () -> stable(mc, idPage.get()), () -> cliquer(mc, idPage.get()), fini, 10, image);
            return;
        }
        pas(nom, () -> panneau(mc) != null && panneau(mc).centreOf(null, action, arg.get()) != null,
                () -> cliquerEn(mc, panneau(mc).centreOf(null, action, arg.get())), fini, 10, image);
    }

    /**
     * Le tarif de demain tapé au clavier : au-dessus du plafond ({@code refus} : le serveur le refuse, le tarif de
     * demain ne bouge pas, la ligne d'état le dit), sinon le plus petit de 25 et du plafond (le serveur le note).
     */
    private static void saisirTarif(Minecraft mc, String nom, boolean refus, String image) {
        pas(nom + "-champ", () -> web ? stable(mc, "op-tariff-field") : panneau(mc) != null
                && panneau(mc).centreOf(null, null, "") != null, () -> {
                    dev.oneforallsurvival.phone.net.OperatorView v = vueAppli(mc);
                    long cap = v == null ? 0L : v.cap();
                    tarifEssaye = refus ? cap + 1 : Math.min(25L, cap);
                    if (web) {
                        cliquer(mc, "op-tariff-field");
                    } else {
                        cliquerEn(mc, panneau(mc).centreOf(null, null, ""));
                    }
                }, () -> attendu(0.3), 5, null);
        pas(nom + "-clavier", () -> true, () -> taper(mc, Long.toString(tarifEssaye)), () -> attendu(0.4), 3, null);
        pas(nom + "-enregistrer", () -> web ? stable(mc, "op-tariff-save") : panneau(mc) != null,
                () -> {
                    tarifsAvant = SORTES_DU_PONT.getOrDefault("opTariff", 0);
                    if (web) {
                        cliquer(mc, "op-tariff-save");
                    } else {
                        cliquerEn(mc, panneau(mc).centreOf(null, OperatorActionPacket.Action.SET_TARIFF, ""));
                    }
                }, () -> {
                    lireAppli(mc);
                    boolean statut = web || (panneau(mc) != null && !panneau(mc).status().isEmpty());
                    // Le geste est parti (page : un opTariff de plus au pont) et le serveur a répondu ce qu'il fallait (barre
                    // d'action : refus au-dessus du plafond, ou tarif noté) : sans geste, rien de cela n'arrive (revue, I2).
                    String attendue = dev.oneforallsurvival.phone.net.OperatorActions.MESSAGE + (refus ? "tariff_above_cap" : "tariff_set");
                    boolean parti = !web || SORTES_DU_PONT.getOrDefault("opTariff", 0) == tarifsAvant + 1;
                    return (refus ? appliDemain != tarifEssaye : appliDemain == tarifEssaye) && statut && parti
                            && attendue.equals(messageEnBarre(mc)) && attendu(1.0);
                }, 10, image);
        pas(nom + "-releve", () -> journal(nom + " : tapé " + tarifEssaye + ", demain au serveur " + appliDemain
                + " ; " + resumeAppli(mc)), () -> true, 1, null);
    }

    private static void composerReseau(Minecraft mc) {
        if (SERVEUR != null) {
            echec("reseau", "le mode reseau se joue en monde solo (gestes du serveur intégré)");
            return;
        }
        decor(mc, 80);
        largeursReelles(mc);
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
            if (n.sim() != PhoneSims.Validity.NONE || n.founderApp() || n.founderAppAvailable()) {
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
                    || !n.requestable().isEmpty() || n.founderApp() || n.founderAppAvailable()) {
                echec("reseau-verrouille-pas-de-sim", "vue verrouillée trop bavarde : " + resumeReseau(n));
            }
            return true;
        }, 2, null);
        pas("reseau-deverrouillage-clavier", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, id("app-antenna", "app-antenna")), 10, null);
        // Le fondateur sans carte : l'icône montrée, grisée ; le toucher part et reçoit « Pas de réseau ».
        pas("reseau-icone-grisee", () -> {}, () -> {
            PhoneNetView n = vueReseau();
            journal("icône Antenne sans carte : " + resumeReseau(n));
            if (!n.founderApp() || n.founderAppAvailable()) {
                echec("reseau-icone-grisee", "icône montrée et grisée attendue : " + resumeReseau(n));
            }
            return true;
        }, 2, null);
        pause("r03-accueil-icone-antenne-grisee");
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
                // Le bandeau « Demande envoyée » s'affiche après la réponse : l'image attend qu'il soit à l'écran.
                () -> PhoneClient.last().map(p -> p.last() == Result.SIM_REQUESTED).orElse(false) && attendu(1.2), 10,
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
                    || !n.number().isEmpty() || !n.holder().isEmpty() || !n.choices().isEmpty() || n.founderApp()
                    || n.founderAppAvailable()) {
                echec("reseau-verrouille-operateur", "vue verrouillée : " + resumeReseau(n));
            }
            return true;
        }, 2, null);

        // Retrait par la feuille.
        pas("reseau-deverrouillage-2", () -> stable(mc, "key-0"), () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, id("app-settings", "settings")), 10, null);
        // Avec la carte de Mairie et du signal : l'icône vive.
        pas("reseau-icone-vive", () -> {}, () -> {
            PhoneNetView n = vueReseau();
            journal("icône Antenne avec sa carte : " + resumeReseau(n));
            if (!n.founderApp() || !n.founderAppAvailable()) {
                echec("reseau-icone-vive", "icône vive attendue : " + resumeReseau(n));
            }
            return true;
        }, 2, null);
        pause("r12b-accueil-icone-antenne-vive");
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
        }), () -> vueReseau().sim() == vu, 8, null);
        // La page renvoie sa mise en page 720 ms après le rendu : un clic plus tôt viserait l'ancienne (la feuille
        // change de hauteur avec l'état de la carte). Une seconde de pause, puis l'image.
        pause(image);
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

    /**
     * Les textes fixes de l'écran de repli (lot T2) dans la langue du client : la largeur réelle de la police du jeu
     * ({@code Font.width}) ne doit dépasser nulle part la borne {@code FontEstimate} qui sert aux essais JUnit
     * ({@code PhoneFallbackTextsTest}) ; sinon ces essais ne prouveraient rien.
     */
    private static void largeursReelles(Minecraft mc) {
        pas("reseau-largeurs", () -> {
            String k = "screen.oneforallsurvival.phone.";
            List<Component> textes = new ArrayList<>();
            for (String nom : List.of("settings", "antenna_app", "sim_remove", "sim_request", "no_choices",
                    "no_requestable", "back", "change_code", "lock", "erase", "erase_confirm", "sim_inserted",
                    "sim_removed", "sim_unknown", "inventory_full", "sim_requested", "already_requested", "no_network",
                    "requests_full", "not_owner", "plan", "no_sim", "sim_unrecognized", "sim_suspended", "sim_inactive",
                    "no_service")) {
                textes.add(Component.translatable(k + nom));
            }
            textes.add(Component.translatable("message.oneforallsurvival.relay.caller_airplane"));
            textes.add(Component.translatable("tooltip.oneforallsurvival.phone.no_sim"));
            textes.add(Component.translatable(k + "sim", "06 12 34 56 78"));
            for (String oui : List.of("on", "off")) {
                textes.add(Component.translatable(k + "airplane", Component.translatable(k + oui)));
            }
            for (String fond : PhoneWallpapers.IDS) {
                textes.add(Component.translatable(k + "wallpaper", Component.translatable(k + "wallpaper." + fond)));
            }
            int depasse = 0;
            StringBuilder plus = new StringBuilder();
            for (Component c : textes) {
                String s = c.getString();
                int reel = mc.font.width(s);
                int estime = dev.oneforallsurvival.phone.client.FontEstimate.width(s);
                if (reel > estime) {
                    depasse++;
                    plus.append(" « ").append(s).append(" » ").append(reel).append(" > ").append(estime).append(';');
                }
                if (s.equals(textes.get(0).getString()) || reel > estime) {
                    journal("largeur « " + s + " » : réelle " + reel + ", estimée " + estime);
                }
            }
            journal("largeurs : " + textes.size() + " textes fixes, " + depasse + " plus large(s) que l'estimation"
                    + plus);
            if (depasse > 0) {
                echec("reseau-largeurs", depasse + " texte(s) plus large(s) que FontEstimate :" + plus);
            }
        }, () -> true, 1, null);
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
            // Mode appel : Lagon est fondé par le correspondant simulé, qui peut ainsi s'émettre sa carte.
            java.util.UUID fondatrice = fondateurLagon != null ? fondateurLagon : java.util.UUID.randomUUID();
            PhoneOperators.Result lagon = registre.found(fondatrice, fondateurLagon != null ? "Maelle"
                    : "Fondatrice", LAGON, EconomyClock.now());
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
            "changeCode", "wallpaper", "airplane", "insertSim", "removeSim", "requestSim", "antennaApp", "opTariff",
            "opIssue", "opRefuse", "opAnonymous", "opSuspend", "opReactivate", "opReplace", "opDeactivate", "opMain",
            "opPage", "opClose", "callDial", "callAnswer", "callDecline", "callHangup", "callMute", "callSpeaker",
            "contactSave", "contactRemove", "phoneApp", "textFocus", "faceId", "shareScan", "shareSend");
    private static final Set<String> CLES = Set.of("a", "rects", "code", "old", "new", "id", "on", "wallpaper", "slot",
            "op", "value", "n", "number", "name", "to");

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
            if ("essai".equals(a)) {
                releve = message; // la réponse à un relevé de l'automate (tâche 16) : gardée, jamais transmise au jeu
                return;
            }
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
        ecrireReperes(mc, nom);
    }

    /**
     * À côté de l'image, {@code <nom>.json} : le centre de chaque boîte de la page (MCEF) visible dans le cadre du
     * téléphone, en pourcentage de l'image, au format des repères de l'atelier du wiki ({@code build_visuals.py} les
     * ramène au recadrage).
     */
    private static void ecrireReperes(Minecraft mc, String nom) {
        if (!(mc.screen instanceof PhoneWebScreen w) || !w.pageReady()) {
            return;
        }
        var f = mc.getWindow();
        double k = w.pageScale() / f.getGuiScale();
        Rect c = w.frameRect();
        JsonObject points = new JsonObject();
        for (Map.Entry<String, int[]> e : new java.util.TreeMap<>(w.rects()).entrySet()) {
            int[] b = e.getValue();
            double gx = c.x() + (b[0] + b[2] / 2.0) * k;
            double gy = c.y() + (b[1] + b[3] / 2.0) * k;
            if (b[2] <= 0 || b[3] <= 0 || gx < c.x() || gy < c.y() || gx > c.x() + c.width() || gy > c.y() + c.height()) {
                continue;
            }
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            a.add(Math.round(gx * f.getGuiScale() / f.getWidth() * 10000) / 100.0);
            a.add(Math.round(gy * f.getGuiScale() / f.getHeight() * 10000) / 100.0);
            points.add(e.getKey(), a);
        }
        JsonObject o = new JsonObject();
        o.addProperty("image", nom + ".png");
        o.addProperty("largeur", f.getWidth());
        o.addProperty("hauteur", f.getHeight());
        o.addProperty("echelle", (int) f.getGuiScale());
        o.add("reperes", points);
        ecrireFichier(nom + ".json", new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n",
                false);
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
        if (mc.screen instanceof PhoneWebScreen || mc.screen instanceof PhoneFallbackScreen
                || mc.screen instanceof OperatorPanelScreen) {
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
