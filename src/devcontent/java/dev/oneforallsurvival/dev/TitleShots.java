package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.client.title.LostIslandTitleScreen;
import dev.oneforallsurvival.client.title.SceneLifecycle;
import dev.oneforallsurvival.client.title.TitleScene;
import dev.oneforallsurvival.client.title.TitleWidgets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;

/**
 * Captures de contrôle de l'écran titre (développement seulement, jamais empaqueté). Activé par
 * {@code -Dlostisland.titleShots=<dossier>} (propriété Gradle {@code -PtitleShots=<dossier>}, relatif au dossier du
 * jeu), inerte sinon. Une fois le titre de Lost Island affiché (plus d'overlay), le client se pilote seul :
 *
 * <ol>
 *   <li>images à 0,5 / 2 / 6 / 30 s du titre, relevé de tous ses widgets (classe, position, origine) ;</li>
 *   <li>curseur sur « Rejoindre l'île » ({@code glfwSetCursorPos}, thread du jeu) et image de l'infobulle ;</li>
 *   <li>Options puis retour, liste des mods de Forge puis retour, une image de chaque écran ;</li>
 *   <li>aller-retour dans un monde plat d'essai au nom fixe {@value #MONDE} (jamais un autre monde de
 *       {@code saves}), sortie par les appels de {@code PauseScreen.onDisconnect}, images au retour, relevé des états
 *       de {@link TitleScene} et du temps jusqu'à la première image de la scène recréée ;</li>
 *   <li>avec {@code -Dlostisland.titleShots.server=<hôte:port>} ({@code -PtitleShotsServer=}) : connexion à ce
 *       serveur, puis déconnexion comme le bouton de la pause ({@code new JoinMultiplayerScreen(new TitleScreen())}),
 *       et l'écran obtenu (attendu : {@link LostIslandTitleScreen}) ;</li>
 *   <li>{@code termine.json} dans le dossier des images, puis fermeture du client.</li>
 * </ol>
 *
 * Chaque pas est écrit au journal, préfixé {@code [titre-essai]}, avec le temps écoulé depuis l'affichage du titre.
 * Tout le pilotage se fait à la fin d'une image (l'image capturée est celle qui vient d'être dessinée) ; les actions
 * sur les écrans passent par la file des tâches du jeu ({@code Minecraft.tell}), là où le jeu traite les vrais clics.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TitleShots {
    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-titre-essai");
    private static final String DOSSIER = System.getProperty("lostisland.titleShots");
    private static final boolean ACTIF = DOSSIER != null && !DOSSIER.isBlank();
    private static final String SERVEUR = System.getProperty("lostisland.titleShots.server", "").trim();
    /** Monde d'essai : seul monde que ce scénario crée ou ouvre (décision 3 du chantier 2). */
    static final String MONDE = "lostisland-essai";
    private static final String NOTRE_CLE = "gui.oneforallsurvival.title.";
    private static final String CLE_REJOINDRE = NOTRE_CLE + "join";
    private static final String CLE_OPTIONS = NOTRE_CLE + "options";
    private static final double[] INSTANTS_S = {0.5, 2, 6, 30};

    /** Délai de garde de tout l'essai (chargement du pack compris) ; un fil à part arrête la JVM 40 s plus tard. */
    private static final double GARDE_S = 1800;
    private static final double ATTENTE_TITRE_S = 900;

    private enum Etape {
        ATTENTE_TITRE, DEBUT, SURVOL, OPTIONS, OPTIONS_RETOUR, MODS, MODS_RETOUR, MONDE_ENTREE, MONDE_DEDANS,
        MONDE_RETOUR, MONDE_SCENE, SERVEUR_CONNEXION, SERVEUR_DEDANS, SERVEUR_RETOUR, RECUPERATION, FIN
    }

    /** Un pas contrôlé, consigné dans {@code termine.json}. */
    private record Pas(String nom, boolean ok, double t, String detail) {}

    private static final long DEBUT_NS = System.nanoTime();
    private static Etape etape = Etape.ATTENTE_TITRE;
    private static long debutEtapeNs = System.nanoTime();
    private static boolean fait;
    private static int sousPas;
    private static long titreNs = -1L;
    private static boolean actionEnCours;
    private static FilmCapture film;
    private static final List<Pas> PAS = new ArrayList<>();
    private static final List<String> IMAGES = new ArrayList<>();
    private static final List<String> ETATS_SCENE = new ArrayList<>();
    private static SceneLifecycle.State dernierEtat;
    private static Screen titre;
    private static byte[] optionsAvant;
    private static double[] cible;
    private static long retourMondeNs = -1L;
    private static long premiereImageMs = -1L;
    private static volatile boolean termineEcrit;
    private static Etape apresRecuperation = Etape.FIN;
    /** La dernière action demandée a été jouée par la file des tâches du jeu. */
    private static boolean actionFinie = true;
    /** Écran ouvert juste après la déconnexion d'un serveur (preuve de la décision 6). */
    private static String ecranApresDeconnexion = "?";

    static {
        if (ACTIF) {
            Thread chien = new Thread(() -> {
                try {
                    Thread.sleep((long) ((GARDE_S + 40) * 1000));
                    LOG.error("[titre-essai] FAIL délai de garde de la JVM dépassé : arrêt forcé");
                    ecrireTermine("garde de la JVM");
                    Runtime.getRuntime().halt(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "titre-essai-garde");
            chien.setDaemon(true);
            chien.start();
        }
    }

    private TitleShots() {}

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || etape == Etape.FIN || actionEnCours) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        suivreLaScene();
        if (secondesDepuis(DEBUT_NS) > GARDE_S) {
            echec("garde", "délai de garde de l'essai dépassé (" + GARDE_S + " s) à l'étape " + etape + ", écran "
                    + nomEcran(mc.screen));
            finir(mc);
            return;
        }
        try {
            avancer(mc);
        } catch (RuntimeException e) {
            LOG.error("[titre-essai] exception à l'étape {}", etape, e);
            echec("exception-" + etape.name().toLowerCase(Locale.ROOT), e.toString());
            recuperer(mc, Etape.FIN);
        }
    }

    // --- Scénario --------------------------------------------------------------------------------------

    private static void avancer(Minecraft mc) {
        if (!actionFinie) {
            return; // aucune étape n'avance tant que l'action demandée n'a pas été jouée
        }
        double s = secondesDepuis(debutEtapeNs);
        switch (etape) {
            case ATTENTE_TITRE -> {
                // Premier lancement d'un dossier neuf : l'écran d'accessibilité précède le titre.
                if (mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen) {
                    journal("écran d'accessibilité du premier lancement : titre ouvert à sa place");
                    agir(mc, "titre après l'accessibilité", () -> mc.setScreen(new TitleScreen()));
                    return;
                }
                if (mc.getOverlay() == null && mc.screen instanceof LostIslandTitleScreen) {
                    titreNs = System.nanoTime();
                    titre = mc.screen;
                    try {
                        film = new FilmCapture(mc.gameDirectory.toPath().resolve(DOSSIER), "Lost Island | écran titre");
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    pas("titre-affiche", true, String.format(Locale.ROOT,
                            "%s, %.1f s après le début du jeu, scène %s, images dans %s", nomEcran(mc.screen),
                            secondesDepuis(DEBUT_NS), TitleScene.state(), mc.gameDirectory.toPath().resolve(DOSSIER)));
                    passer(Etape.DEBUT);
                } else if (s > ATTENTE_TITRE_S) {
                    echec("titre-affiche", "titre de Lost Island jamais atteint en " + ATTENTE_TITRE_S + " s, écran "
                            + nomEcran(mc.screen) + ", overlay " + (mc.getOverlay() == null ? "aucun"
                            : mc.getOverlay().getClass().getName()));
                    finir(mc);
                }
            }
            case DEBUT -> {
                if (!(mc.screen instanceof LostIslandTitleScreen)) {
                    echec("titre-reste", "le titre a cédé la place à " + nomEcran(mc.screen) + " pendant les premières images");
                    recuperer(mc, Etape.SURVOL);
                    return;
                }
                if (sousPas < INSTANTS_S.length && s >= INSTANTS_S[sousPas]) {
                    double t = INSTANTS_S[sousPas];
                    String nom = String.format(Locale.ROOT, "%02d-titre-%ss", sousPas + 1,
                            t == Math.rint(t) ? String.valueOf((int) t) : String.valueOf(t));
                    capturer(mc, nom, "titre à " + t + " s, scène " + TitleScene.state());
                    sousPas++;
                }
                if (!fait && s >= 1.0) {
                    fait = true;
                    widgets(mc.screen, "titre au démarrage");
                }
                if (sousPas >= INSTANTS_S.length) {
                    passer(Etape.SURVOL);
                }
            }
            case SURVOL -> survoler(mc, s);
            case OPTIONS -> ouvrirPuisFermer(mc, s, CLE_OPTIONS, "06-options", "Options", Etape.OPTIONS_RETOUR);
            case OPTIONS_RETOUR -> {
                if (retourAuTitre(mc, s, "07-retour-options", "retour d'Options", Etape.MODS)) {
                    protegerLesOptions(mc);
                }
            }
            case MODS -> ouvrirPuisFermer(mc, s, TitleWidgets.MODS_KEY, "08-mods", "liste des mods", Etape.MODS_RETOUR);
            case MODS_RETOUR -> retourAuTitre(mc, s, "09-retour-mods", "retour de la liste des mods", Etape.MONDE_ENTREE);
            case MONDE_ENTREE -> entrerDansLeMonde(mc, s);
            case MONDE_DEDANS -> {
                if (dedans(mc, s, "10-dans-le-monde", "monde " + MONDE)) {
                    passer(Etape.MONDE_RETOUR);
                }
            }
            case MONDE_RETOUR -> {
                if (mc.screen instanceof LostIslandTitleScreen) {
                    titre = mc.screen;
                    capturer(mc, "11-retour-monde", "titre au retour du monde, scène " + TitleScene.state());
                    pas("retour-monde", true, nomEcran(mc.screen) + ", scène " + TitleScene.state());
                    passer(Etape.MONDE_SCENE);
                } else if (s > 180) {
                    echec("retour-monde", "titre non revenu 180 s après la sortie du monde, écran " + nomEcran(mc.screen));
                    recuperer(mc, Etape.SERVEUR_CONNEXION);
                }
            }
            case MONDE_SCENE -> {
                SceneLifecycle.State etat = TitleScene.state();
                if (premiereImageMs < 0L) {
                    if (etat == SceneLifecycle.State.TITLE) {
                        // TitleScene passe à TITLE juste avant de dessiner l'image : celle qui vient de finir la montre.
                        premiereImageMs = (System.nanoTime() - retourMondeNs) / 1_000_000L;
                        pas("scene-recreee", true, "première image de la scène recréée " + premiereImageMs
                                + " ms après le retour au titre");
                        debutEtapeNs = System.nanoTime();
                    } else if (etat == SceneLifecycle.State.UNAVAILABLE || s > 60) {
                        echec("scene-recreee", "pas de scène au retour du monde (état " + etat + " après "
                                + String.format(Locale.ROOT, "%.1f", s) + " s), fond de repli");
                        capturer(mc, "12-retour-monde-scene", "retour du monde sans scène, état " + etat);
                        widgets(mc.screen, "titre au retour du monde");
                        passer(Etape.SERVEUR_CONNEXION);
                    }
                    return;
                }
                // Le fondu d'arrivée (800 ms) fini, la scène est entière.
                if (s >= 1.5) {
                    capturer(mc, "12-retour-monde-scene", "scène recréée, fondu fini, état " + etat);
                    widgets(mc.screen, "titre au retour du monde");
                    passer(Etape.SERVEUR_CONNEXION);
                }
            }
            case SERVEUR_CONNEXION -> {
                if (SERVEUR.isEmpty()) {
                    journal("pas de serveur (propriété lostisland.titleShots.server absente) : preuve de la déconnexion sautée");
                    passer(Etape.FIN);
                    finir(mc);
                    return;
                }
                if (!fait) {
                    fait = true;
                    Screen parent = mc.screen;
                    agir(mc, "connexion à " + SERVEUR, () -> ConnectScreen.startConnecting(parent, mc,
                            ServerAddress.parseString(SERVEUR), new ServerData("Lost Island (essai)", SERVEUR, false), false));
                    passer(Etape.SERVEUR_DEDANS);
                }
            }
            case SERVEUR_DEDANS -> {
                if (mc.screen instanceof DisconnectedScreen) {
                    capturer(mc, "13-serveur-refus", "connexion refusée : " + mc.screen.getTitle().getString());
                    echec("serveur-connexion", "écran de déconnexion pendant la connexion à " + SERVEUR + " : "
                            + mc.screen.getTitle().getString());
                    recuperer(mc, Etape.FIN);
                    return;
                }
                if (mc.player == null && s > 120) {
                    capturer(mc, "13-serveur-refus", "connexion non établie en 120 s");
                    echec("serveur-connexion", "connexion à " + SERVEUR + " non établie en 120 s, écran " + nomEcran(mc.screen));
                    recuperer(mc, Etape.FIN);
                    return;
                }
                if (dedans(mc, s, "13-serveur", "serveur " + SERVEUR)) {
                    passer(Etape.SERVEUR_RETOUR);
                }
            }
            case SERVEUR_RETOUR -> {
                if (!fait) {
                    if (actionFinie) {
                        fait = true;
                        debutEtapeNs = System.nanoTime();
                    }
                    return;
                }
                if (s >= 2.0) {
                    boolean ok = mc.screen instanceof LostIslandTitleScreen;
                    capturer(mc, "14-deconnexion", "après la déconnexion : " + nomEcran(mc.screen));
                    pas("deconnexion-serveur", ok, "écran ouvert par setScreen(new JoinMultiplayerScreen(new TitleScreen())) : "
                            + ecranApresDeconnexion + " ; deux secondes plus tard : " + nomEcran(mc.screen)
                            + ", scène " + TitleScene.state());
                    if (ok) {
                        finir(mc);
                    } else {
                        recuperer(mc, Etape.FIN);
                    }
                }
            }
            case RECUPERATION -> {
                if (mc.screen instanceof LostIslandTitleScreen && mc.level == null) {
                    journal("titre retrouvé après un échec : suite à l'étape " + apresRecuperation);
                    titre = mc.screen;
                    Etape suite = apresRecuperation;
                    passer(suite);
                    if (suite == Etape.FIN) {
                        finir(mc);
                    }
                } else if (s > 60) {
                    echec("recuperation", "titre non retrouvé en 60 s, écran " + nomEcran(mc.screen));
                    finir(mc);
                }
            }
            default -> {
            }
        }
    }

    /** Le curseur sur « Rejoindre l'île », l'infobulle visible, une image ; puis le curseur sur le décor. */
    private static void survoler(Minecraft mc, double s) {
        AbstractWidget rejoindre = widget(mc.screen, CLE_REJOINDRE);
        if (rejoindre == null) {
            echec("survol", "bouton « Rejoindre l'île » introuvable sur " + nomEcran(mc.screen));
            passer(Etape.OPTIONS);
            return;
        }
        long fenetre = mc.getWindow().getWindow();
        if (!fait) {
            fait = true;
            cible = versFenetre(mc, rejoindre.getX() + rejoindre.getWidth() / 2.0, rejoindre.getY() + rejoindre.getHeight() / 2.0);
            GLFW.glfwSetCursorPos(fenetre, cible[0], cible[1]);
            journal(String.format(Locale.ROOT, "curseur placé par glfwSetCursorPos en (%.0f, %.0f) px de la fenêtre,"
                    + " centre du bouton (%d, %d, %d x %d), actif=%s", cible[0], cible[1], rejoindre.getX(),
                    rejoindre.getY(), rejoindre.getWidth(), rejoindre.getHeight(), rejoindre.active));
            return;
        }
        boolean place = Math.abs(mc.mouseHandler.xpos() - cible[0]) < 1.5 && Math.abs(mc.mouseHandler.ypos() - cible[1]) < 1.5;
        if (!place && sousPas == 0 && s > 0.5) {
            // Sans mouvement de la souris réelle, Windows peut ne pas renvoyer le déplacement au jeu : on appelle
            // directement le rappel de position du curseur installé par Minecraft (le même que GLFW appellerait).
            sousPas = 1;
            journal("position du curseur non reçue par le jeu après glfwSetCursorPos : rappel GLFW appelé directement");
            rappelCurseur(fenetre, cible[0], cible[1]);
            return;
        }
        if (rejoindre.isHovered() && s >= 1.0 || s > 5) {
            Tooltip infobulle = rejoindre.getTooltip();
            String texte = infobulle == null ? "(aucune infobulle)" : texte(infobulle.toCharSequence(mc));
            boolean ok = rejoindre.isHovered() && infobulle != null;
            capturer(mc, "05-survol-rejoindre", "survol de « Rejoindre l'île », infobulle « " + texte + " »");
            pas("survol-rejoindre", ok, String.format(Locale.ROOT, "survolé=%s, infobulle « %s », souris du jeu (%.0f, %.0f), %s",
                    rejoindre.isHovered(), texte, mc.mouseHandler.xpos(), mc.mouseHandler.ypos(),
                    sousPas == 0 ? "glfwSetCursorPos suffit" : "par le rappel GLFW"));
            // Curseur sur le décor, à droite : ni bouton ni infobulle dans les images suivantes.
            double[] decor = {mc.getWindow().getScreenWidth() * 0.8, mc.getWindow().getScreenHeight() * 0.45};
            GLFW.glfwSetCursorPos(fenetre, decor[0], decor[1]);
            if (sousPas != 0) {
                rappelCurseur(fenetre, decor[0], decor[1]);
            }
            passer(Etape.OPTIONS);
        }
    }

    /** Presse le bouton de clé {@code cle} du titre, capture l'écran ouvert, puis presse « Terminé ». */
    private static void ouvrirPuisFermer(Minecraft mc, double s, String cle, String image, String quoi, Etape suite) {
        if (!fait) {
            AbstractWidget bouton = widget(mc.screen, cle);
            if (bouton == null) {
                echec(image, "bouton « " + quoi + " » (" + cle + ") introuvable sur " + nomEcran(mc.screen));
                passer(suite == Etape.OPTIONS_RETOUR ? Etape.MODS : Etape.MONDE_ENTREE);
                return;
            }
            if (cle.equals(CLE_OPTIONS)) {
                optionsAvant = lireOptions(mc);
            }
            fait = true;
            agir(mc, "clic sur « " + quoi + " »", () -> presser(bouton));
            return;
        }
        if (!actionFinie || s < 1.0) {
            return;
        }
        if (mc.screen instanceof LostIslandTitleScreen || mc.screen == null) {
            if (s > 10) {
                echec(image, "« " + quoi + " » ne s'est pas ouvert, écran " + nomEcran(mc.screen));
                passer(suite);
            }
            return;
        }
        Screen ouvert = mc.screen;
        capturer(mc, image, quoi + " : " + nomEcran(ouvert));
        pas(image, true, quoi + " ouvert : " + nomEcran(ouvert));
        AbstractWidget termine = widget(ouvert, "gui.done");
        passer(suite);
        agir(mc, termine == null ? "fermeture de « " + quoi + " » (onClose)" : "clic sur « Terminé » de « " + quoi + " »",
                () -> {
                    if (termine != null) {
                        presser(termine);
                    } else {
                        ouvert.onClose();
                    }
                });
    }

    /** Attend le titre après un écran secondaire ; {@code true} le jour où le pas est consigné. */
    private static boolean retourAuTitre(Minecraft mc, double s, String image, String quoi, Etape suite) {
        if (!actionFinie || s < 1.0) {
            return false;
        }
        if (mc.screen instanceof LostIslandTitleScreen) {
            boolean meme = mc.screen == titre;
            capturer(mc, image, quoi + " : " + (meme ? "même écran titre" : "nouvel écran titre") + ", scène " + TitleScene.state());
            pas(image, true, quoi + " : " + nomEcran(mc.screen) + (meme ? ", même instance qu'avant" : ", autre instance")
                    + ", scène " + TitleScene.state());
            titre = mc.screen;
            passer(suite);
            return true;
        }
        if (s > 10) {
            capturer(mc, image, quoi + " : écran " + nomEcran(mc.screen));
            echec(image, quoi + " : écran " + nomEcran(mc.screen) + " au lieu du titre de Lost Island");
            recuperer(mc, suite);
            return true;
        }
        return false;
    }

    /**
     * Fermer Options fait réécrire {@code options.txt} par Minecraft ({@code OptionsScreen.removed}, {@code Options.save}).
     * L'essai ne change aucun réglage ; si le fichier a changé (ordre, clés), son contenu d'origine est remis.
     */
    private static void protegerLesOptions(Minecraft mc) {
        if (optionsAvant == null) {
            return;
        }
        byte[] apres = lireOptions(mc);
        if (Arrays.equals(optionsAvant, apres)) {
            journal("options.txt identique après Options");
            return;
        }
        try {
            Files.write(mc.gameDirectory.toPath().resolve("options.txt"), optionsAvant);
            journal("options.txt réécrit par Minecraft à la fermeture d'Options : contenu d'origine remis ("
                    + optionsAvant.length + " octets)");
        } catch (IOException e) {
            LOG.error("[titre-essai] options.txt d'origine non remis", e);
        }
    }

    private static byte[] lireOptions(Minecraft mc) {
        try {
            Path p = mc.gameDirectory.toPath().resolve("options.txt");
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Crée le monde plat d'essai, ou l'ouvre s'il existe déjà (même nom) ; aucun autre monde n'est touché. */
    private static void entrerDansLeMonde(Minecraft mc, double s) {
        if (!fait) {
            fait = true;
            boolean existe = mc.getLevelSource().levelExists(MONDE);
            Screen parent = mc.screen;
            agir(mc, existe ? "ouverture du monde d'essai existant « " + MONDE + " »"
                    : "création du monde plat d'essai « " + MONDE + " »", () -> {
                        if (existe) {
                            mc.createWorldOpenFlows().loadLevel(parent, MONDE);
                        } else {
                            LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                    true, new GameRules(), WorldDataConfiguration.DEFAULT);
                            mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                                    acces -> acces.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                        }
                    });
            passer(Etape.MONDE_DEDANS);
        }
    }

    /**
     * Dans un monde (solo ou serveur) : attend le joueur, une image trois secondes après, puis la sortie par les appels
     * de {@code PauseScreen.onDisconnect}. {@code true} une fois la sortie demandée.
     */
    private static boolean dedans(Minecraft mc, double s, String image, String ou) {
        if (mc.player == null || mc.level == null) {
            if (s > 300) {
                capturer(mc, image + "-echec", ou + " : pas de joueur en 300 s");
                echec(image, ou + " : joueur absent 300 s après la demande, écran " + nomEcran(mc.screen));
                recuperer(mc, etape == Etape.MONDE_DEDANS ? Etape.SERVEUR_CONNEXION : Etape.FIN);
            }
            return false;
        }
        if (sousPas == 0) {
            sousPas = 1;
            debutEtapeNs = System.nanoTime();
            journal(ou + " : joueur présent, écran " + nomEcran(mc.screen) + ", scène " + TitleScene.state());
            return false;
        }
        // Un écran ouvert par un mod à l'arrivée n'empêche pas la sortie : au bout de 20 s, on sort quand même.
        if (s < 3 || mc.screen != null && s < 20) {
            return false;
        }
        capturer(mc, image, ou + ", scène du titre " + TitleScene.state());
        pas(image, TitleScene.state() == SceneLifecycle.State.IN_WORLD || TitleScene.state() == SceneLifecycle.State.UNAVAILABLE,
                ou + " : joueur dans le monde, scène du titre " + TitleScene.state() + " (attendu IN_WORLD : libérée)");
        agir(mc, "sortie de " + ou + " comme le bouton de la pause", () -> deconnecterCommeLaPause(mc));
        return true;
    }

    /** Les appels de {@code PauseScreen.onDisconnect} de Minecraft 1.20.1 (lus au javap), Realms excepté. */
    private static void deconnecterCommeLaPause(Minecraft mc) {
        boolean local = mc.isLocalServer();
        mc.level.disconnect();
        if (local) {
            mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            mc.clearLevel();
        }
        TitleScreen titreVanilla = new TitleScreen();
        if (local) {
            // Le retour au titre commence ici : son init() demande la scène, recréée dans cet appel même.
            retourMondeNs = System.nanoTime();
            mc.setScreen(titreVanilla);
            journal(String.format(Locale.ROOT, "setScreen du titre au retour du monde : %.1f ms (recréation de la scène comprise)",
                    (System.nanoTime() - retourMondeNs) / 1e6));
        } else {
            mc.setScreen(new JoinMultiplayerScreen(titreVanilla));
            ecranApresDeconnexion = nomEcran(mc.screen);
        }
        journal("déconnexion faite (" + (local ? "solo" : "serveur") + "), écran ouvert : " + nomEcran(mc.screen));
    }

    /** Après un échec : retour au titre (sortie du monde s'il y en a un), puis l'étape {@code suite}. */
    private static void recuperer(Minecraft mc, Etape suite) {
        apresRecuperation = suite;
        passer(Etape.RECUPERATION);
        agir(mc, "retour au titre après un échec", () -> {
            if (mc.level != null) {
                deconnecterCommeLaPause(mc);
            } else if (!(mc.screen instanceof LostIslandTitleScreen)) {
                mc.setScreen(new TitleScreen());
            }
        });
    }

    /** Fin : écritures attendues, {@code termine.json}, bilan au journal, fermeture du client. */
    private static void finir(Minecraft mc) {
        etape = Etape.FIN;
        if (film != null) {
            film.attendre();
        }
        long echecs = PAS.stream().filter(p -> !p.ok()).count();
        journal("bilan : " + (echecs == 0 ? "TOUT PASSE" : echecs + " ECHEC(S)") + ", " + IMAGES.size() + " image(s)");
        ecrireTermine("normale");
        journal("fin du client");
        mc.stop();
    }

    // --- Outils ----------------------------------------------------------------------------------------

    /** Joue {@code action} dans la file des tâches du jeu, au début de la prochaine boucle, comme un vrai clic. */
    private static void agir(Minecraft mc, String quoi, Runnable action) {
        journal("action : " + quoi);
        actionFinie = false;
        mc.tell(() -> {
            actionEnCours = true;
            try {
                action.run();
            } catch (RuntimeException e) {
                LOG.error("[titre-essai] action « {} » en échec", quoi, e);
                echec("action", quoi + " : " + e);
            } finally {
                actionEnCours = false;
                actionFinie = true;
            }
        });
    }

    private static void presser(AbstractWidget w) {
        if (w instanceof Button b) {
            b.onPress();
        } else {
            w.onClick(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0);
        }
    }

    private static void capturer(Minecraft mc, String nom, String quoi) {
        if (film == null) {
            return;
        }
        film.instantane(mc, nom);
        IMAGES.add(nom + ".png");
        journal("image " + nom + ".png : " + quoi);
    }

    /** Relevé de chaque widget de l'écran : classe, position, clé, origine ; contrôle de la rangée des autres mods. */
    private static void widgets(Screen ecran, String quand) {
        if (ecran == null) {
            return;
        }
        journal("widgets du " + quand + " (" + ecran.width + " x " + ecran.height + " px d'interface) :");
        int basDesNotres = 0;
        int nos = 0;
        List<AbstractWidget> etrangers = new ArrayList<>();
        for (GuiEventListener l : ecran.children()) {
            if (!(l instanceof AbstractWidget w)) {
                journal("  " + l.getClass().getName() + " (pas un widget)");
                continue;
            }
            String cle = cle(w.getMessage());
            boolean notre = cle != null && cle.startsWith(NOTRE_CLE);
            TitleWidgets.Kind sorte = TitleWidgets.classify(cle, notre);
            journal(String.format(Locale.ROOT, "  %-8s %s x=%d y=%d l=%d h=%d visible=%s actif=%s clé=%s texte=« %s »",
                    sorte, w.getClass().getName(), w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.visible, w.active,
                    cle, w.getMessage().getString()));
            if (sorte == TitleWidgets.Kind.OURS) {
                nos++;
                basDesNotres = Math.max(basDesNotres, w.getY() + w.getHeight());
            } else if (sorte == TitleWidgets.Kind.FOREIGN && w.visible) {
                etrangers.add(w);
            }
        }
        StringBuilder detail = new StringBuilder();
        boolean ok = nos == 4;
        for (AbstractWidget w : etrangers) {
            boolean range = w.getY() >= basDesNotres && w.getX() >= 0 && w.getY() >= 0
                    && w.getX() + w.getWidth() <= ecran.width && w.getY() + w.getHeight() <= ecran.height;
            ok &= range;
            detail.append(w.getClass().getSimpleName()).append(range ? " rangé" : " HORS RANGÉE").append(" ; ");
        }
        pas("widgets-" + (quand.contains("retour") ? "retour" : "debut"), ok, nos + " boutons à nous (attendu 4), "
                + etrangers.size() + " widget(s) étranger(s) visibles"
                + (etrangers.isEmpty() ? " (aucun : lancement sans le pack ?)" : " sous nos boutons (y ≥ " + basDesNotres
                + ") et dans l'écran : " + detail));
    }

    private static String cle(Component message) {
        if (message == TitleScreen.COPYRIGHT_TEXT) {
            return TitleWidgets.COPYRIGHT_KEY;
        }
        return message.getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }

    private static AbstractWidget widget(Screen ecran, String cle) {
        if (ecran == null) {
            return null;
        }
        for (GuiEventListener l : ecran.children()) {
            if (l instanceof AbstractWidget w && w.visible && cle.equals(cle(w.getMessage()))) {
                return w;
            }
        }
        return null;
    }

    /** Point de l'interface (pixels mis à l'échelle) vers la fenêtre (pixels de la souris de GLFW). */
    private static double[] versFenetre(Minecraft mc, double x, double y) {
        var f = mc.getWindow();
        return new double[] {x * f.getScreenWidth() / f.getGuiScaledWidth(), y * f.getScreenHeight() / f.getGuiScaledHeight()};
    }

    /** Appelle le rappel de position du curseur installé par Minecraft, sans le changer. */
    private static void rappelCurseur(long fenetre, double x, double y) {
        GLFWCursorPosCallback rappel = GLFW.glfwSetCursorPosCallback(fenetre, null);
        GLFW.glfwSetCursorPosCallback(fenetre, rappel);
        if (rappel != null) {
            rappel.invoke(fenetre, x, y);
        }
    }

    private static String texte(List<FormattedCharSequence> lignes) {
        StringBuilder sb = new StringBuilder();
        for (FormattedCharSequence ligne : lignes) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            ligne.accept((i, style, point) -> {
                sb.appendCodePoint(point);
                return true;
            });
        }
        return sb.toString();
    }

    private static void suivreLaScene() {
        SceneLifecycle.State etat = TitleScene.state();
        if (etat != dernierEtat) {
            String ligne = String.format(Locale.ROOT, "%s -> %s", dernierEtat, etat);
            journal("scène : " + ligne + " (étape " + etape + ")");
            ETATS_SCENE.add(String.format(Locale.ROOT, "%.3f %s", tempsTitre(), ligne));
            dernierEtat = etat;
        }
    }

    private static void passer(Etape suivante) {
        if (suivante != etape) {
            journal("étape " + etape + " -> " + suivante);
        }
        etape = suivante;
        debutEtapeNs = System.nanoTime();
        fait = false;
        sousPas = 0;
    }

    private static void pas(String nom, boolean ok, String detail) {
        PAS.add(new Pas(nom, ok, tempsTitre(), detail));
        if (ok) {
            journal("PASS " + nom + " : " + detail);
        } else {
            LOG.error("[titre-essai] [{}] FAIL {} : {}", horodatage(), nom, detail);
        }
    }

    private static void echec(String nom, String detail) {
        pas(nom, false, detail);
    }

    private static void journal(String texte) {
        LOG.info("[titre-essai] [{}] {}", horodatage(), texte);
    }

    private static String horodatage() {
        return titreNs < 0L ? String.format(Locale.ROOT, "avant le titre, +%.3f s", secondesDepuis(DEBUT_NS))
                : String.format(Locale.ROOT, "titre +%.3f s", tempsTitre());
    }

    private static double tempsTitre() {
        return titreNs < 0L ? -1 : secondesDepuis(titreNs);
    }

    private static double secondesDepuis(long ns) {
        return (System.nanoTime() - ns) / 1e9;
    }

    private static String nomEcran(Screen ecran) {
        return ecran == null ? "aucun" : ecran.getClass().getName();
    }

    /** {@code termine.json} : pas faits, échecs, images, états de la scène ; une seule écriture. */
    private static synchronized void ecrireTermine(String fin) {
        if (termineEcrit || DOSSIER == null) {
            return;
        }
        termineEcrit = true;
        StringBuilder j = new StringBuilder("{\n  \"fin\": ").append(json(fin)).append(",\n  \"serveur\": ")
                .append(json(SERVEUR)).append(",\n  \"monde\": ").append(json(MONDE)).append(",\n  \"echecs\": ")
                .append(PAS.stream().filter(p -> !p.ok()).count()).append(",\n  \"retourMondePremiereImageMs\": ")
                .append(premiereImageMs).append(",\n  \"pas\": [");
        for (int i = 0; i < PAS.size(); i++) {
            Pas p = PAS.get(i);
            j.append(i == 0 ? "\n" : ",\n").append(String.format(Locale.ROOT, "    {\"nom\": %s, \"ok\": %s, \"t\": %.3f, \"detail\": %s}",
                    json(p.nom()), p.ok(), p.t(), json(p.detail())));
        }
        j.append("\n  ],\n  \"images\": [");
        for (int i = 0; i < IMAGES.size(); i++) {
            j.append(i == 0 ? "" : ", ").append(json(IMAGES.get(i)));
        }
        j.append("],\n  \"etatsScene\": [");
        for (int i = 0; i < ETATS_SCENE.size(); i++) {
            j.append(i == 0 ? "" : ", ").append(json(ETATS_SCENE.get(i)));
        }
        j.append("]\n}\n");
        try {
            Path dossier = Minecraft.getInstance().gameDirectory.toPath().resolve(DOSSIER);
            Files.createDirectories(dossier);
            Files.writeString(dossier.resolve("termine.json"), j, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOG.error("[titre-essai] termine.json non écrit", e);
        }
    }

    private static String json(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
