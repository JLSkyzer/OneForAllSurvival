package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.client.debug.DebugHud;
import dev.oneforallsurvival.client.debug.IslandDebugCache;
import dev.oneforallsurvival.client.title.LostIslandTitleScreen;
import dev.oneforallsurvival.debug.IslandDebugInfo;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.windows.User32;

/**
 * Preuve en jeu du nouveau F3 (développement seulement, jamais empaqueté). Activé par
 * {@code -Dlostisland.debugShots=<dossier>} (propriété Gradle {@code -PdebugShots=<dossier>}, relatif au dossier du jeu),
 * inerte sinon. Une fois le titre affiché, le client se pilote seul dans le monde d'essai {@value #MONDE} (aucun autre
 * monde de {@code saves} n'est ouvert) : jour et nuit, machine, compteur et disjoncteur en Ingénieur puis en Citoyen,
 * {@code reducedDebugInfo}, pages de la vue détaillée, graphes, F3 + B, F1, échelles d'interface, retour au titre,
 * {@code termine.json} et fermeture.
 *
 * <p>Les touches passent par le vrai chemin : {@code WM_KEYDOWN} / {@code WM_KEYUP} postés à la fenêtre, reçus par GLFW
 * ({@code glfwGetKey} les voit), puis {@code KeyboardHandler.keyPress} et nos écouteurs ; la fenêtre n'a pas besoin du
 * focus et rien ne part vers une autre application.
 *
 * <p>Avec {@code -Dlostisland.debugShots.film=true} ({@code -PdebugShotsFilm}) : version filmée, plus lente, avec le
 * titre et le survol de « Rejoindre l'île » au début, sans aucune capture PNG (elles feraient hoqueter le film).
 *
 * <p>{@code options.txt} n'est jamais réécrit : {@code pauseOnLostFocus} et les autres réglages ne changent qu'en
 * mémoire, et aucune combinaison qui appelle {@code Options.save} (F3 + H, F3 + P) n'est pressée.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class F3Shots {
    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-f3-essai");
    private static final String DOSSIER = System.getProperty("lostisland.debugShots");
    private static final boolean ACTIF = DOSSIER != null && !DOSSIER.isBlank();
    private static final boolean FILM = Boolean.getBoolean("lostisland.debugShots.film");
    /** Seul monde que ce scénario crée ou ouvre. */
    static final String MONDE = "lostisland-essai-f3";
    private static final String CLE_REJOINDRE = "gui.oneforallsurvival.title.join";
    private static final double GARDE_S = 1500;
    private static final long ECART_TOUCHES_NS = 150_000_000L;

    private static final int VK_F1 = 0x70;
    private static final int SCAN_F1 = 0x3B;
    private static final int VK_F3 = 0x72;
    private static final int SCAN_F3 = 0x3D;
    private static final int VK_TAB = 0x09;
    private static final int SCAN_TAB = 0x0F;
    private static final int VK_ALT = 0x12;
    private static final int SCAN_ALT = 0x38;
    private static final int VK_B = 0x42;
    private static final int SCAN_B = 0x30;

    private static final int X_MACHINE = 0;
    private static final int X_COMPTEUR = 2;
    private static final int X_DISJONCTEUR = -2;
    private static final int Z_BLOCS = -3;

    private enum Phase { ATTENTE_TITRE, TITRE_FILM, ENTREE, ATTENTE_JOUEUR, PREPARATION, JEU, SORTIE, RETOUR, FIN }

    /** Un moment du scénario : {@code avant} au début, {@code apres} (contrôle, image) au bout de {@code attente} s. */
    private record Moment(String nom, double attente, Consumer<Minecraft> avant, Consumer<Minecraft> apres) {}

    private record Controle(String nom, boolean ok, double t, String detail) {}

    private record Touche(int vk, int scan, boolean bas) {}

    private static final long DEBUT_NS = System.nanoTime();
    private static final Deque<Touche> TOUCHES = new ArrayDeque<>();
    private static final List<Controle> CONTROLES = new ArrayList<>();
    private static final List<String> IMAGES = new ArrayList<>();
    private static final List<Moment> MOMENTS = new ArrayList<>();

    private static Phase phase = Phase.ATTENTE_TITRE;
    private static long debutPhaseNs = System.nanoTime();
    private static long prochaineToucheNs;
    private static int indice;
    private static boolean commence;
    private static boolean fait;
    private static int sousPas;
    private static FilmCapture images;
    private static BlockPos visee;
    private static BlockPos posMachine;
    private static BlockPos posCompteur;
    private static BlockPos posDisjoncteur;
    private static Vec3 posJoueur;
    private static boolean preparationFinie;
    private static int vuesVues;
    private static volatile boolean termineEcrit;

    static {
        if (ACTIF) {
            Thread chien = new Thread(() -> {
                try {
                    Thread.sleep((long) ((GARDE_S + 40) * 1000));
                    LOG.error("[f3-essai] FAIL délai de garde de la JVM dépassé : arrêt forcé");
                    ecrireTermine("garde de la JVM");
                    Runtime.getRuntime().halt(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "f3-essai-garde");
            chien.setDaemon(true);
            chien.start();
        }
    }

    private F3Shots() {}

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || phase == Phase.FIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (secondes(DEBUT_NS) > GARDE_S) {
            controle("garde", false, "délai de garde dépassé à la phase " + phase);
            finir(mc);
            return;
        }
        try {
            viserEncore(mc);
            if (!envoyerTouches(mc)) {
                avancer(mc, secondes(debutPhaseNs));
            }
        } catch (RuntimeException e) {
            LOG.error("[f3-essai] exception à la phase {}", phase, e);
            controle("exception-" + phase.name().toLowerCase(Locale.ROOT), false, e.toString());
            finir(mc);
        }
    }

    private static void avancer(Minecraft mc, double s) {
        switch (phase) {
            case ATTENTE_TITRE -> attendreLeTitre(mc, s);
            case TITRE_FILM -> titreFilme(mc, s);
            case ENTREE -> entrer(mc);
            case ATTENTE_JOUEUR -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.screen == null) {
                    journal("joueur présent dans « " + MONDE + " »");
                    // En mémoire seulement : Tutorial.setStep appellerait Options.save, pas stop.
                    mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                    mc.getTutorial().stop();
                    preparer(mc);
                    passer(Phase.PREPARATION);
                } else if (s > 120) {
                    controle("monde", false, "joueur absent après 120 s, écran " + nomEcran(mc.screen));
                    finir(mc);
                }
            }
            case PREPARATION -> {
                if (preparationFinie && s >= 3 && mc.level.getBlockState(posMachine).is(FoodRegistry.DESALINATOR.get())) {
                    controle("monde", true, "blocs posés, joueur en " + mc.player.blockPosition());
                    composerLeScenario();
                    passer(Phase.JEU);
                } else if (s > 60) {
                    controle("preparation", false, "blocs jamais vus par le client");
                    finir(mc);
                }
            }
            case JEU -> jouer(mc);
            case SORTIE -> sortir(mc, s);
            case RETOUR -> {
                if (mc.screen instanceof LostIslandTitleScreen && s >= 3) {
                    capturer(mc, "90-retour-titre", "titre après le monde, F3 fermé");
                    controle("retour-titre", true, nomEcran(mc.screen));
                    finir(mc);
                } else if (s > 180) {
                    controle("retour-titre", false, "titre non revenu, écran " + nomEcran(mc.screen));
                    finir(mc);
                }
            }
            default -> {
            }
        }
    }

    private static void attendreLeTitre(Minecraft mc, double s) {
        if (mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen) {
            journal("écran d'accessibilité du premier lancement : titre ouvert à sa place");
            mc.tell(() -> mc.setScreen(new TitleScreen()));
            return;
        }
        if (mc.getOverlay() == null && mc.screen instanceof LostIslandTitleScreen) {
            // En mémoire seulement (aucun Options.save ici) : la fenêtre peut ne pas avoir le focus.
            mc.options.pauseOnLostFocus = false;
            try {
                images = new FilmCapture(mc.gameDirectory.toPath().resolve(DOSSIER), "Lost Island | F3");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            controle("titre", true, String.format(Locale.ROOT, "titre atteint %.1f s après le début du jeu, film=%s", secondes(DEBUT_NS), FILM));
            passer(FILM ? Phase.TITRE_FILM : Phase.ENTREE);
        } else if (s > 900) {
            controle("titre", false, "titre jamais atteint, écran " + nomEcran(mc.screen));
            finir(mc);
        }
    }

    /** Pour le film : le titre, puis le curseur sur « Rejoindre l'île » (rappel de position de GLFW), puis ailleurs. */
    private static void titreFilme(Minecraft mc, double s) {
        long fenetre = mc.getWindow().getWindow();
        if (sousPas == 0 && s >= 8) {
            AbstractWidget rejoindre = widget(mc.screen, CLE_REJOINDRE);
            if (rejoindre == null) {
                controle("survol", false, "bouton introuvable");
                passer(Phase.ENTREE);
                return;
            }
            var f = mc.getWindow();
            rappelCurseur(fenetre, (rejoindre.getX() + rejoindre.getWidth() / 2.0) * f.getScreenWidth() / f.getGuiScaledWidth(),
                    (rejoindre.getY() + rejoindre.getHeight() / 2.0) * f.getScreenHeight() / f.getGuiScaledHeight());
            sousPas = 1;
            journal("curseur sur « Rejoindre l'île » (rappel GLFW)");
        } else if (sousPas == 1 && s >= 13) {
            AbstractWidget rejoindre = widget(mc.screen, CLE_REJOINDRE);
            controle("survol", rejoindre != null && rejoindre.isHovered(), "survolé=" + (rejoindre != null && rejoindre.isHovered()));
            rappelCurseur(fenetre, mc.getWindow().getScreenWidth() * 0.8, mc.getWindow().getScreenHeight() * 0.45);
            sousPas = 2;
        } else if (sousPas == 2 && s >= 15) {
            passer(Phase.ENTREE);
        }
    }

    private static void entrer(Minecraft mc) {
        if (fait) {
            return;
        }
        fait = true;
        boolean existe = mc.getLevelSource().levelExists(MONDE);
        Screen parent = mc.screen;
        journal(existe ? "ouverture du monde d'essai existant" : "création du monde plat d'essai");
        mc.tell(() -> {
            if (existe) {
                mc.createWorldOpenFlows().loadLevel(parent, MONDE);
            } else {
                // Sans triche : le propriétaire n'est pas opérateur (sinon RoleGate le traite en équipe, Citoyen ou non).
                LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, Difficulty.PEACEFUL, false,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                        acces -> acces.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            passer(Phase.ATTENTE_JOUEUR);
        });
    }

    /** Sur le thread du serveur intégré : joueur, blocs neufs (rien de cumulé d'un lancement à l'autre), objets. */
    private static void preparer(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        java.util.UUID id = mc.player.getUUID();
        serveur.execute(() -> {
            ServerLevel monde = serveur.overworld();
            ServerPlayer joueur = serveur.getPlayerList().getPlayer(id);
            monde.getChunk(0, 0);
            monde.getChunk(0, Z_BLOCS >> 4);
            int sol = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            posJoueur = new Vec3(0.5, sol, 0.5);
            posMachine = new BlockPos(X_MACHINE, sol, Z_BLOCS);
            posCompteur = new BlockPos(X_COMPTEUR, sol, Z_BLOCS);
            posDisjoncteur = new BlockPos(X_DISJONCTEUR, sol, Z_BLOCS);
            joueur.teleportTo(monde, posJoueur.x, posJoueur.y, posJoueur.z, 180f, 0f);
            for (BlockPos p : List.of(posMachine, posCompteur, posDisjoncteur)) {
                monde.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
            monde.setBlockAndUpdate(posMachine, FoodRegistry.DESALINATOR.get().defaultBlockState());
            monde.setBlockAndUpdate(posCompteur, GridRegistry.METER.get().defaultBlockState());
            monde.setBlockAndUpdate(posDisjoncteur, GridRegistry.BREAKER.get().defaultBlockState());
            if (monde.getBlockEntity(posMachine) instanceof ElectricMachineBlockEntity machine) {
                machine.takeDamage(25);
            } else {
                journal("le dessalinisateur n'est pas une machine électrique ?");
            }
            if (monde.getBlockEntity(posCompteur) instanceof MeterBlockEntity compteur) {
                compteur.setSubscriber("Mairie");
            }
            Inventory sac = joueur.getInventory();
            List<ItemStack> barre = List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_SWORD),
                    new ItemStack(Items.BREAD, 12), new ItemStack(Items.TORCH, 64), new ItemStack(Items.WATER_BUCKET),
                    new ItemStack(Items.COMPASS), new ItemStack(Items.CLOCK), new ItemStack(Items.OAK_LOG, 32),
                    new ItemStack(FoodRegistry.DESALINATOR.get()));
            for (int i = 0; i < barre.size(); i++) {
                sac.setItem(i, barre.get(i));
            }
            commande(serveur, "kill @e[type=minecraft:armor_stand]");
            commande(serveur, String.format(Locale.ROOT, "summon minecraft:armor_stand 1.5 %d -5.5", sol));
            commande(serveur, "gamerule doDaylightCycle false");
            commande(serveur, "gamerule reducedDebugInfo false");
            commande(serveur, "weather clear 100000");
            commande(serveur, "time set 1000");
            preparationFinie = true;
        });
    }

    /** {@code film} allonge chaque moment pour qu'on ait le temps de lire. */
    private static double duree(double s) {
        return FILM ? Math.max(s, 3.5) : s;
    }

    private static void ajouter(String nom, double attente, Consumer<Minecraft> avant, Consumer<Minecraft> apres) {
        MOMENTS.add(new Moment(nom, duree(attente), avant, apres));
    }

    private static void composerLeScenario() {
        ajouter("sans-f3", 1.5, mc -> {
            visee = posMachine;
            mc.gui.getChat().addMessage(Component.literal("Essai du F3 : le chat reste dessiné normalement."));
            role(mc, RoleBook.ENGINEER_ROLE_ID);
        }, mc -> capturer(mc, "01-sans-f3", "F3 fermé : barre, chat, viseur"));

        ajouter("f3-ouvert-jour", 3, mc -> {
            journal("F3 pressé et relâché (WM_KEYDOWN / WM_KEYUP postés à la fenêtre)");
            // Les messages d'arrivée (First Aid, progrès) cacheraient le bas des cartes de gauche.
            mc.gui.getChat().clearMessages(false);
            taper(VK_F3, SCAN_F3);
        }, mc -> {
            verifier("f3-ouvre", mc.options.renderDebug, "renderDebug=" + mc.options.renderDebug);
            IslandDebugInfo.Machine m = machineVue();
            verifier("ingenieur-usure", m != null && m.wearPercent() != null, "machine=" + m + ", opérateur=" + operateur(mc));
            capturer(mc, "02-f3-jour-machine-ingenieur", "jour, machine, Ingénieur : " + m);
        });

        ajouter("f3-nuit", 2, mc -> commande(mc.getSingleplayerServer(), "time set 18000"),
                mc -> capturer(mc, "03-f3-nuit", "nuit, jour du monde " + mc.level.getDayTime()));

        ajouter("citoyen-machine", 3, mc -> {
            commande(mc.getSingleplayerServer(), "time set 1000");
            role(mc, RoleBook.DEFAULT_ROLE_ID);
        }, mc -> {
            IslandDebugInfo.Machine m = machineVue();
            verifier("citoyen-sans-usure", m != null && m.wearPercent() == null && m.failure() == null,
                    "machine=" + m + ", rôle=" + roleActuel(mc) + ", opérateur=" + operateur(mc));
            capturer(mc, "04-f3-machine-citoyen", "machine, Citoyen : " + m);
        });

        ajouter("citoyen-compteur", 3, mc -> visee = posCompteur, mc -> {
            IslandDebugInfo.Meter m = IslandDebugCache.info() == null ? null : IslandDebugCache.info().meter();
            verifier("citoyen-compteur", m != null && m.index() == null && "Mairie".equals(m.subscriber()), "compteur=" + m);
            capturer(mc, "05-f3-compteur-citoyen", "compteur, Citoyen : " + m);
        });

        ajouter("ingenieur-compteur", 3, mc -> role(mc, RoleBook.ENGINEER_ROLE_ID), mc -> {
            IslandDebugInfo.Meter m = IslandDebugCache.info() == null ? null : IslandDebugCache.info().meter();
            verifier("ingenieur-compteur", m != null && m.index() != null, "compteur=" + m);
            capturer(mc, "06-f3-compteur-ingenieur", "compteur, Ingénieur : " + m);
        });

        ajouter("disjoncteur", 3, mc -> visee = posDisjoncteur, mc -> {
            IslandDebugInfo.Breaker b = IslandDebugCache.info() == null ? null : IslandDebugCache.info().breaker();
            verifier("disjoncteur", b != null && b.closed() && !b.sealed(), "disjoncteur=" + b);
            capturer(mc, "07-f3-disjoncteur", "disjoncteur : " + b);
        });

        ajouter("reduit-vrai", 3, mc -> {
            visee = posMachine;
            commande(mc.getSingleplayerServer(), "gamerule reducedDebugInfo true");
        }, mc -> {
            verifier("reduit-actif", mc.showOnlyReducedInfo() && IslandDebugCache.info() == null,
                    "réduit=" + mc.showOnlyReducedInfo() + ", cache=" + IslandDebugCache.info());
            capturer(mc, "08-f3-reduit", "reducedDebugInfo vrai");
        });

        ajouter("reduit-faux", 3, mc -> commande(mc.getSingleplayerServer(), "gamerule reducedDebugInfo false"), mc -> {
            verifier("reduit-retire", !mc.showOnlyReducedInfo() && machineVue() != null, "réduit=" + mc.showOnlyReducedInfo()
                    + ", machine=" + machineVue());
            capturer(mc, "09-f3-reduit-retire", "reducedDebugInfo faux");
        });

        // Pages de la vue détaillée : nombre lu dans DebugHud après la première pression.
        ajouter("detail-1", 1.5, mc -> f3Plus(VK_TAB, SCAN_TAB), F3Shots::controlerPage);
        for (int i = 0; i < 5; i++) {
            ajouter("detail-suite", 1.5, mc -> {
                if (entier("view") != 0) {
                    f3Plus(VK_TAB, SCAN_TAB);
                }
            }, F3Shots::controlerPage);
        }

        ajouter("hitbox-oui", 1.5, mc -> {
            journal("hitbox avant F3 + B : " + mc.getEntityRenderDispatcher().shouldRenderHitBoxes());
            f3Plus(VK_B, SCAN_B);
        }, mc -> {
            verifier("f3-b-oui", mc.getEntityRenderDispatcher().shouldRenderHitBoxes() && mc.options.renderDebug,
                    "hitbox=" + mc.getEntityRenderDispatcher().shouldRenderHitBoxes() + ", F3 ouvert=" + mc.options.renderDebug);
            capturer(mc, "20-f3-b-hitbox", "F3 + B : hitbox");
        });
        ajouter("hitbox-non", 1, mc -> f3Plus(VK_B, SCAN_B), mc -> verifier("f3-b-non",
                !mc.getEntityRenderDispatcher().shouldRenderHitBoxes() && mc.options.renderDebug,
                "hitbox=" + mc.getEntityRenderDispatcher().shouldRenderHitBoxes() + ", F3 ouvert=" + mc.options.renderDebug));

        ajouter("fermeture", 1.5, mc -> {
            mc.gui.getChat().addMessage(Component.literal("Essai du F3 : le chat reste dessiné normalement."));
            taper(VK_F3, SCAN_F3);
        }, mc -> {
            verifier("f3-ferme", !mc.options.renderDebug, "renderDebug=" + mc.options.renderDebug);
            capturer(mc, "21-f3-ferme", "F3 refermé : barre, chat, viseur après le dessin des cartes");
        });

        ajouter("graphes", 4, mc -> {
            journal("F3 + Alt : Alt enfoncé pendant le relâchement de F3");
            TOUCHES.add(new Touche(VK_ALT, SCAN_ALT, true));
            taper(VK_F3, SCAN_F3);
            TOUCHES.add(new Touche(VK_ALT, SCAN_ALT, false));
        }, mc -> {
            verifier("graphes", mc.options.renderDebug && mc.options.renderFpsChart, "renderDebug=" + mc.options.renderDebug
                    + ", renderFpsChart=" + mc.options.renderFpsChart);
            capturer(mc, "22-f3-graphes", "F3 + Alt : graphes");
        });

        ajouter("graphes-ferme", 1, mc -> taper(VK_F3, SCAN_F3), mc -> verifier("graphes-fermes",
                !mc.options.renderDebug && !mc.options.renderFpsChart, "renderDebug=" + mc.options.renderDebug));

        if (FILM) {
            return; // le film s'arrête là : le reste ne se regarde qu'en image
        }

        ajouter("f1-ouvert", 1, mc -> taper(VK_F3, SCAN_F3), mc -> verifier("f3-rouvert", mc.options.renderDebug, ""));
        ajouter("f1-cache", 1, mc -> taper(VK_F1, SCAN_F1), mc -> {
            verifier("f1", mc.options.hideGui, "hideGui=" + mc.options.hideGui);
            capturer(mc, "23-f1-f3-ouvert", "F1 avec le F3 ouvert : rien de dessiné");
        });
        ajouter("f1-rendu", 1, mc -> taper(VK_F1, SCAN_F1), mc -> verifier("f1-retour", !mc.options.hideGui, ""));

        ajouter("echelle-3", 2.5, mc -> mc.getWindow().setWindowed(1280, 720), mc -> capturer(mc, "24-echelle-3-1280x720",
                "échelle d'interface " + mc.getWindow().getGuiScale()));
        ajouter("echelle-4", 2.5, mc -> mc.getWindow().setWindowed(1920, 1080), mc -> capturer(mc, "25-echelle-4-1920x1080",
                "échelle d'interface " + mc.getWindow().getGuiScale()));
        ajouter("echelle-4-detail", 1.5, mc -> f3Plus(VK_TAB, SCAN_TAB), mc -> capturer(mc, "26-echelle-4-detail",
                "vue détaillée, page " + entier("view") + "/" + entier("pages")));
        ajouter("fenetre-rendue", 2, mc -> {
            taper(VK_F3, SCAN_F3);
            mc.getWindow().setWindowed(854, 480);
        }, mc -> verifier("f3-ferme-fin", !mc.options.renderDebug, "renderDebug=" + mc.options.renderDebug));
        ajouter("fin-monde", 1.5, mc -> {
        }, mc -> capturer(mc, "27-fin-sans-f3", "F3 fermé, avant la sortie : barre, chat, viseur"));
    }

    private static void controlerPage(Minecraft mc) {
        int vue = entier("view");
        int pages = entier("pages");
        verifier("tab-garde-f3-" + (++vuesVues), mc.options.renderDebug && !mc.options.keyPlayerList.isDown(),
                "renderDebug=" + mc.options.renderDebug + ", liste des joueurs=" + mc.options.keyPlayerList.isDown()
                        + ", vue " + vue + "/" + pages);
        if (vue != 0) {
            capturer(mc, String.format(Locale.ROOT, "1%d-detail-page-%d", vue, vue), "vue détaillée, page " + vue + "/" + pages);
        } else {
            journal("vue revenue à la normale après la page " + pages);
        }
    }

    private static void jouer(Minecraft mc) {
        if (indice >= MOMENTS.size()) {
            passer(Phase.SORTIE);
            return;
        }
        Moment m = MOMENTS.get(indice);
        if (!commence) {
            commence = true;
            debutPhaseNs = System.nanoTime();
            journal("moment « " + m.nom() + " »");
            m.avant().accept(mc);
            return;
        }
        if (secondes(debutPhaseNs) >= m.attente()) {
            m.apres().accept(mc);
            indice++;
            commence = false;
        }
    }

    /** La sortie suit les appels de {@code PauseScreen.onDisconnect}. */
    private static void sortir(Minecraft mc, double s) {
        if (FILM) {
            journal("fin du film");
            finir(mc);
            return;
        }
        if (!fait) {
            fait = true;
            visee = null;
            mc.tell(() -> {
                mc.level.disconnect();
                mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
                mc.setScreen(new TitleScreen());
                passer(Phase.RETOUR);
            });
        }
    }

    // --- Outils -------------------------------------------------------------------------------------------------

    /** La direction est reposée à chaque image : une souris bousculée ne change pas le bloc regardé. */
    private static void viserEncore(Minecraft mc) {
        if (visee == null || mc.player == null) {
            return;
        }
        Vec3 d = Vec3.atCenterOf(visee).subtract(mc.player.getEyePosition());
        float lacet = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float tangage = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        mc.player.setYRot(lacet);
        mc.player.setXRot(tangage);
        mc.player.yRotO = lacet;
        mc.player.xRotO = tangage;
        mc.player.setYHeadRot(lacet);
    }

    /** Une touche à la fois, espacées : Minecraft traite les touches dans sa file de tâches, une image plus tard. */
    private static boolean envoyerTouches(Minecraft mc) {
        if (TOUCHES.isEmpty()) {
            return false;
        }
        long maintenant = System.nanoTime();
        if (maintenant >= prochaineToucheNs) {
            Touche t = TOUCHES.poll();
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().getWindow());
            long lparam = 1L | ((long) t.scan() << 16) | (t.bas() ? 0L : (1L << 30) | (1L << 31));
            if (!User32.PostMessage(hwnd, t.bas() ? User32.WM_KEYDOWN : User32.WM_KEYUP, t.vk(), lparam)) {
                controle("postmessage", false, "PostMessage refusé pour vk=" + t.vk());
            }
            prochaineToucheNs = maintenant + ECART_TOUCHES_NS;
            if (TOUCHES.isEmpty()) {
                debutPhaseNs = maintenant + ECART_TOUCHES_NS; // l'attente du moment part de la dernière touche
            }
        }
        return true;
    }

    private static void taper(int vk, int scan) {
        TOUCHES.add(new Touche(vk, scan, true));
        TOUCHES.add(new Touche(vk, scan, false));
    }

    /** F3 enfoncé, la touche, F3 relâché : une combinaison complète. */
    private static void f3Plus(int vk, int scan) {
        TOUCHES.add(new Touche(VK_F3, SCAN_F3, true));
        taper(vk, scan);
        TOUCHES.add(new Touche(VK_F3, SCAN_F3, false));
    }

    private static void commande(MinecraftServer serveur, String ligne) {
        serveur.execute(() -> {
            int r = serveur.getCommands().performPrefixedCommand(serveur.createCommandSourceStack().withSuppressedOutput(), ligne);
            journal("commande « " + ligne + " » : " + r);
        });
    }

    private static void role(Minecraft mc, String id) {
        commande(mc.getSingleplayerServer(), "role set " + mc.getUser().getName() + " " + id);
    }

    private static String roleActuel(Minecraft mc) {
        return RoleSavedData.get(mc.getSingleplayerServer()).book().roleOf(mc.player.getUUID()).id();
    }

    private static boolean operateur(Minecraft mc) {
        ServerPlayer joueur = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        return joueur != null && joueur.hasPermissions(2);
    }

    private static IslandDebugInfo.Machine machineVue() {
        return IslandDebugCache.info() == null ? null : IslandDebugCache.info().machine();
    }

    /** L'état privé de {@link DebugHud} (vue, pages) : seule la preuve le lit. */
    private static int entier(String champ) {
        try {
            Field f = DebugHud.class.getDeclaredField(champ);
            f.setAccessible(true);
            return f.getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AbstractWidget widget(Screen ecran, String cle) {
        if (ecran == null) {
            return null;
        }
        for (GuiEventListener l : ecran.children()) {
            if (l instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t && cle.equals(t.getKey())) {
                return w;
            }
        }
        return null;
    }

    private static void rappelCurseur(long fenetre, double x, double y) {
        GLFWCursorPosCallback rappel = GLFW.glfwSetCursorPosCallback(fenetre, null);
        GLFW.glfwSetCursorPosCallback(fenetre, rappel);
        if (rappel != null) {
            rappel.invoke(fenetre, x, y);
        }
    }

    private static void capturer(Minecraft mc, String nom, String quoi) {
        if (FILM || images == null) {
            journal("moment filmé : " + quoi);
            return;
        }
        images.instantane(mc, nom);
        synchronized (F3Shots.class) {
            IMAGES.add(nom + ".png");
        }
        journal("image " + nom + ".png : " + quoi);
    }

    private static void verifier(String nom, boolean ok, String detail) {
        controle(nom, ok, detail);
    }

    private static void controle(String nom, boolean ok, String detail) {
        synchronized (F3Shots.class) {
            CONTROLES.add(new Controle(nom, ok, secondes(DEBUT_NS), detail));
        }
        if (ok) {
            journal("PASS " + nom + " : " + detail);
        } else {
            LOG.error("[f3-essai] FAIL {} : {}", nom, detail);
        }
    }

    private static void passer(Phase suivante) {
        journal("phase " + phase + " -> " + suivante);
        phase = suivante;
        debutPhaseNs = System.nanoTime();
        fait = false;
        sousPas = 0;
    }

    private static void finir(Minecraft mc) {
        phase = Phase.FIN;
        if (images != null) {
            images.attendre();
        }
        long echecs;
        synchronized (F3Shots.class) {
            echecs = CONTROLES.stream().filter(c -> !c.ok()).count();
        }
        journal("bilan : " + (echecs == 0 ? "TOUT PASSE" : echecs + " ECHEC(S)") + ", " + IMAGES.size() + " image(s)");
        ecrireTermine("normale");
        mc.stop();
    }

    private static void journal(String texte) {
        LOG.info("[f3-essai] [+{}] {}", String.format(Locale.ROOT, "%.3f s", secondes(DEBUT_NS)), texte);
    }

    private static double secondes(long ns) {
        return (System.nanoTime() - ns) / 1e9;
    }

    private static String nomEcran(Screen ecran) {
        return ecran == null ? "aucun" : ecran.getClass().getName();
    }

    private static synchronized void ecrireTermine(String fin) {
        if (termineEcrit || DOSSIER == null) {
            return;
        }
        termineEcrit = true;
        StringBuilder j = new StringBuilder("{\n  \"fin\": ").append(json(fin)).append(",\n  \"monde\": ").append(json(MONDE))
                .append(",\n  \"film\": ").append(FILM).append(",\n  \"echecs\": ")
                .append(CONTROLES.stream().filter(c -> !c.ok()).count()).append(",\n  \"controles\": [");
        for (int i = 0; i < CONTROLES.size(); i++) {
            Controle c = CONTROLES.get(i);
            j.append(i == 0 ? "\n" : ",\n").append(String.format(Locale.ROOT, "    {\"nom\": %s, \"ok\": %s, \"t\": %.3f, \"detail\": %s}",
                    json(c.nom()), c.ok(), c.t(), json(c.detail())));
        }
        j.append("\n  ],\n  \"images\": [");
        for (int i = 0; i < IMAGES.size(); i++) {
            j.append(i == 0 ? "" : ", ").append(json(IMAGES.get(i)));
        }
        j.append("]\n}\n");
        try {
            Path dossier = Minecraft.getInstance().gameDirectory.toPath().resolve(DOSSIER);
            Files.createDirectories(dossier);
            Files.writeString(dossier.resolve("termine.json"), j, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOG.error("[f3-essai] termine.json non écrit", e);
        }
    }

    private static String json(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
