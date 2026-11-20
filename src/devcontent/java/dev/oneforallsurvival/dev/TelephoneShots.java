package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.client.web.WebAvailability;
import dev.oneforallsurvival.client.web.WebFailure;
import dev.oneforallsurvival.client.web.WebScreen.Rect;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import dev.oneforallsurvival.phone.WallSocketBlock;
import dev.oneforallsurvival.phone.WallSocketBlockEntity;
import dev.oneforallsurvival.phone.client.PhoneClient;
import dev.oneforallsurvival.phone.client.PhoneFallbackScreen;
import dev.oneforallsurvival.phone.client.PhoneWebScreen;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
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
import net.minecraft.world.item.ItemStack;
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
 * monde plat neuf {@value #MONDE}, se donne un téléphone ({@code /give}) chargé à 30 %, puis joue le parcours par de
 * vrais clics et une vraie frappe relayés par l'écran ouvert : dans la page (MCEF), aux centres des boîtes du dernier
 * {@code layout} ; dans l'écran de repli (sans MCEF), aux centres de ses boutons. Choix du code, Réglages (fond
 * {@code nuit}, mode avion, changement du code), Échap, réouverture verrouillée, cinq faux codes avec redimensionnement
 * de la fenêtre et échelle d'interface de 2 à 4 écran ouvert, batterie qui baisse, prise alimentée par un bloc
 * d'énergie créative (Crafts &amp; Additions, pack), batterie remontée. Une image par écran.
 *
 * <p>{@code options.txt} n'est jamais réécrit (taille de fenêtre et échelle en mémoire seulement). La touche F10 n'est
 * jamais touchée. Aucun code saisi n'est écrit au journal. La tâche 11 complète cet automate (performance, WebDisplays,
 * serveur dédié).
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TelephoneShots {
    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-telephone");
    private static final String DOSSIER = System.getProperty("oneforallsurvival.telephoneShots");
    private static final boolean ACTIF = DOSSIER != null && !DOSSIER.isBlank();
    static final String MONDE = "lostisland-essai-telephone";
    private static final double GARDE_S = 900;
    private static final String CODE = "4821";
    private static final String NOUVEAU = "1357";
    private static final String FAUX = "0000";

    private enum Phase { TITRE, ENTREE, JOUEUR, ETAPES, SORTIE, RETOUR, FIN }

    private record Pas(String nom, Runnable action, BooleanSupplier fini, double delai, String image) {}

    private static final long DEBUT_NS = System.nanoTime();
    private static final List<String> ECHECS = new ArrayList<>();
    private static final List<String> IMAGES = new ArrayList<>();
    private static final List<Pas> PAS = new ArrayList<>();
    private static Phase phase = Phase.TITRE;
    private static long debutPhaseNs = System.nanoTime();
    private static boolean fait;
    private static int indice;
    private static boolean joue;
    private static long debutPasNs;
    private static FilmCapture images;
    private static volatile boolean termineEcrit;
    private static boolean web;
    private static int ouvertures;
    private static int batterieAvant = -1;
    private static int batterieApresBaisse = -1;
    private static volatile String etatPrise = "";
    @Nullable private static Map<String, int[]> miseEnPageNotee;
    @Nullable private static volatile BlockPos prise;

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
        Minecraft mc = Minecraft.getInstance();
        if (secondes(DEBUT_NS) > GARDE_S) {
            echec("garde", "délai de garde dépassé à la phase " + phase);
            finir(mc);
            return;
        }
        try {
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
                    try {
                        images = new FilmCapture(mc.gameDirectory.toPath().resolve(DOSSIER), "Lost Island | téléphone");
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    web = WebAvailability.ready();
                    journal("titre atteint ; mcef chargé " + mcef + ", disponibilité " + WebAvailability.failure()
                            + ", parcours " + (web ? "web" : "repli") + " après "
                            + String.format(Locale.ROOT, "%.1f s", s));
                    passer(Phase.ENTREE);
                } else if (s > 600) {
                    echec("titre", "titre jamais atteint (écran " + mc.screen + ")");
                    finir(mc);
                }
            }
            case ENTREE -> entrer(mc);
            case JOUEUR -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.screen == null) {
                    mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                    mc.getTutorial().stop();
                    taille(mc, 1280, 720);
                    echelle(mc, 2);
                    composer(mc);
                    passer(Phase.ETAPES);
                } else if (s > 180) {
                    echec("monde", "joueur absent après 180 s");
                    finir(mc);
                }
            }
            case ETAPES -> etapes(mc);
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

    private static void composer(Minecraft mc) {
        pas("decor", () -> {
            mc.player.connection.sendCommand("time set noon");
            mc.player.connection.sendCommand("gamerule doDaylightCycle false");
            mc.player.connection.sendCommand("give @s oneforallsurvival:phone");
        }, () -> attendu(1.0), 3, null);
        pas("telephone-en-main-30%", () -> serveur(mc, (srv, sp) -> {
            int i = -1;
            for (int s = 0; s < sp.getInventory().getContainerSize(); s++) {
                if (sp.getInventory().getItem(s).is(PhoneRegistry.PHONE.get())) {
                    i = s;
                    break;
                }
            }
            if (i < 0) {
                return;
            }
            ItemStack phone = sp.getInventory().removeItemNoUpdate(i);
            PhoneItem.store(phone, PhoneConfig.capacity() * 30 / 100);
            sp.setItemInHand(InteractionHand.MAIN_HAND, phone);
        }), () -> mc.player.getMainHandItem().is(PhoneRegistry.PHONE.get())
                && PhoneItem.stored(mc.player.getMainHandItem()) > 0, 5, null);

        // Première ouverture : choix du code par clics, confirmation au clavier.
        ouvrir(mc, "ouverture-1", Lock.SETUP, "01-choix-du-code");
        cliquerCode(mc, "choix", CODE);
        pause("02-confirmez-le-code");
        pas("confirmation-au-clavier", () -> taper(mc, CODE),
                () -> etat(Lock.UNLOCKED) && cible(mc, web ? "app-settings" : "settings"), 10, null);
        pause("03-accueil");
        pas("reglages", () -> {
            noterMiseEnPage(mc);
            cliquer(mc, web ? "app-settings" : "settings");
        }, () -> cible(mc, web ? "wp-nuit" : "wallpaper") && nouvelleMiseEnPage(mc), 10, null);
        pause("04-reglages");
        if (web) {
            pas("fond-nuit", () -> cliquer(mc, "wp-nuit"), () -> fond("nuit") && attendu(0.3), 10, null);
        } else {
            for (String f : List.of("lagon", "jungle", "nuit")) {
                pas("fond-" + f, () -> cliquer(mc, "wallpaper"), () -> fond(f) && attendu(0.3), 10, null);
            }
        }
        pas("mode-avion", () -> cliquer(mc, web ? "row-airplane" : "airplane"),
                () -> PhoneClient.last().map(PhoneStatePacket::airplane).orElse(false), 10, null);
        pause("05-reglages-nuit-avion");
        changerLeCode(mc);
        pas("echap-1", () -> echap(mc), () -> mc.screen == null, 5, null);

        // Réouverture : verrouillée, fond nuit.
        ouvrir(mc, "ouverture-2", Lock.LOCKED, "07-verrouille-nuit");
        pas("fond-nuit-verrouille", () -> {}, () -> fond("nuit") && etat(Lock.LOCKED), 2, null);
        pas("batterie-depart", () -> {
            batterieAvant = PhoneClient.last().get().battery();
            journal("batterie à la réouverture : " + batterieAvant + " %");
        }, () -> true, 1, null);
        faux(mc, 1, 4, true, "08-faux-1-1280x720-echelle2");
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
        faux(mc, 4, 1, false, "13-faux-4-au-clavier");
        faux(mc, 5, 0, true, null);
        pas("blocage", () -> {}, () -> PhoneClient.last().map(p -> p.lockoutSeconds() > 0).orElse(false)
                && attendu(1.0), 5, "14-blocage");
        pas("blocage-decompte", () -> {}, () -> attendu(3.0), 5, "15-blocage-3s-plus-tard");
        pas("batterie-baisse", () -> {}, () -> {
            int b = PhoneClient.last().map(PhoneStatePacket::battery).orElse(batterieAvant);
            if (b < batterieAvant && attendu(1.0)) {
                batterieApresBaisse = b;
                return true;
            }
            return false;
        }, 75, "16-batterie-baisse");
        pas("batterie-baisse-releve", () -> journal("batterie : " + batterieAvant + " % puis " + batterieApresBaisse
                + " % écran allumé"), () -> true, 1, null);
        pas("echap-2", () -> echap(mc), () -> mc.screen == null, 5, null);

        // La prise : un bloc d'énergie créative comme mur, la prise contre lui, le téléphone branché puis repris.
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
        pas("echap-3", () -> echap(mc), () -> mc.screen == null, 5, null);
        pas("bilan-avis", () -> {
            int absent = avisDansLeChat(mc, WebFailure.ABSENT.translationKey());
            int tous = 0;
            for (WebFailure f : WebFailure.values()) {
                tous += avisDansLeChat(mc, f.translationKey());
            }
            String bilan = "ouvertures " + ouvertures + ", avis « mode simplifié » dans le chat : " + tous
                    + " (dont fallback.absent " + absent + ")";
            journal(bilan);
            if (web ? tous != 0 : (tous != 1 || ouvertures < 3)) {
                echec("avis", bilan);
            }
        }, () -> true, 1, "20-chat");
    }

    /** Ouvre le téléphone tenu (vrai usage de l'objet) et attend l'écran prêt, au verrou attendu. */
    private static void ouvrir(Minecraft mc, String nom, Lock attendu, String image) {
        pas(nom, () -> {
            ouvertures++;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }, () -> verrouDeLEcran(mc) == attendu && ecranPret(mc), 20, null);
        pas(nom + "-ecran", () -> journal(nom + " : écran " + mc.screen.getClass().getSimpleName()), () -> attendu(0.8),
                3, image);
    }

    /** Un faux code de plus (par clics ou au clavier) ; attend la réponse, sauf blocage attendu au cinquième. */
    private static void faux(Minecraft mc, int n, int restants, boolean clics, @Nullable String image) {
        pas("faux-" + n, () -> {
            if (clics) {
                for (char c : FAUX.toCharArray()) {
                    cliquer(mc, "key-" + c);
                }
            } else {
                taper(mc, FAUX);
            }
        }, () -> {
            PhoneStatePacket p = PhoneClient.last().orElse(null);
            if (p == null || p.kind() != Kind.UPDATE) {
                return false;
            }
            return restants > 0
                    ? p.last() == Result.WRONG_CODE && p.attemptsLeft() == restants
                    : p.last() == Result.LOCKED_OUT;
        }, 10, null);
        pas("faux-" + n + "-releve", () -> journal("faux code " + n + " : " + resume()), () -> attendu(1.0), 3, image);
    }

    private static void changerLeCode(Minecraft mc) {
        if (web) {
            pas("feuille-du-code", () -> {
                noterMiseEnPage(mc);
                cliquer(mc, "row-code");
            }, () -> cible(mc, "sheet-cancel") && cible(mc, "key-1") && nouvelleMiseEnPage(mc), 10, null);
            cliquerCode(mc, "ancien", CODE);
            pause(null);
            cliquerCode(mc, "nouveau", NOUVEAU);
            pause(null);
            pas("confirmation", () -> {
                for (char c : NOUVEAU.toCharArray()) {
                    cliquer(mc, "key-" + c);
                }
            }, () -> PhoneClient.last().map(p -> p.last() == Result.CODE_CHANGED).orElse(false), 10, null);
        } else {
            pas("champs-du-code", () -> {
                remplir(mc, "old", CODE);
                remplir(mc, "new", NOUVEAU);
                remplir(mc, "confirm", NOUVEAU);
            }, () -> attendu(0.5), 2, "06a-champs-du-code");
            pas("modifier", () -> cliquer(mc, "change"),
                    () -> PhoneClient.last().map(p -> p.last() == Result.CODE_CHANGED).orElse(false), 10, null);
        }
        pause("06-code-modifie");
    }

    /** Quatre chiffres cliqués un par un, puis une petite pause pour la page. */
    private static void cliquerCode(Minecraft mc, String nom, String code) {
        pas("code-" + nom, () -> {
            for (char c : code.toCharArray()) {
                cliquer(mc, "key-" + c);
            }
        }, () -> attendu(0.6), 2, null);
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

    private static void echap(Minecraft mc) {
        if (mc.screen != null) {
            mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), 0);
        }
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
                + " avion " + p.airplane()).orElse("aucun état");
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

    // --- La prise --------------------------------------------------------------------------------------------------

    private interface SurLeServeur {
        void run(MinecraftServer srv, ServerPlayer sp);
    }

    private static void serveur(Minecraft mc, SurLeServeur corps) {
        MinecraftServer srv = mc.getSingleplayerServer();
        java.util.UUID id = mc.player.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp != null) {
                corps.run(srv, sp);
            }
        });
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
        PAS.add(new Pas(nom, action, fini, delai, image));
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
            joue = true;
            debutPasNs = System.nanoTime();
            journal("pas « " + p.nom() + " »");
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
    }

    private static boolean attendu(double secondes) {
        return secondes(debutPasNs) >= secondes;
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
                : mc.screen instanceof PhoneFallbackScreen r ? " ; cadre " + r.frameRect() + ", page " + r.page() : "";
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
        journal("bilan : " + (ECHECS.isEmpty() ? "TOUT PASSE" : ECHECS.size() + " ECHEC(S) " + ECHECS) + ", "
                + IMAGES.size() + " image(s)");
        ecrireTermine("normale");
        mc.stop();
    }

    private static void journal(String texte) {
        LOG.info("[telephone] [+{}] {}", String.format(Locale.ROOT, "%.3f s", secondes(DEBUT_NS)), texte);
    }

    private static double secondes(long ns) {
        return (System.nanoTime() - ns) / 1e9;
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
