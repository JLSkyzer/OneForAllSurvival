package dev.oneforallsurvival.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.oneforallsurvival.client.title.LostIslandTitleScreen;
import dev.oneforallsurvival.client.web.WebAvailability;
import dev.oneforallsurvival.client.web.WebFailure;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Essai réel du socle navigateur (développement seulement). Activé par {@code -Doneforallsurvival.webShots=<dossier>}
 * (propriété Gradle {@code -PwebShots=<dossier>}, relatif au dossier du jeu), inerte sinon. Le client crée un monde plat
 * neuf {@value #MONDE}, puis :
 * <ul>
 *   <li>avec MCEF : {@code /webessai}, captures de la page, clic, molette, clavier, Échap, échelle 1,5, dix
 *       réouvertures, chaque geste prouvé par le message que la page renvoie ;
 *   <li>sans MCEF : deux {@code /webessai}, aucun écran, un seul avis de repli dans le chat.
 * </ul>
 * Puis il ferme le jeu. {@code options.txt} n'est jamais réécrit (taille de fenêtre et échelle en mémoire seulement).
 * La touche F10 (exemple de MCEF qui ouvre une page externe) n'est jamais touchée.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WebEssaiShots {
    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-web-essai");
    private static final String DOSSIER = System.getProperty("oneforallsurvival.webShots");
    private static final boolean ACTIF = DOSSIER != null && !DOSSIER.isBlank();
    static final String MONDE = "lostisland-essai-web";
    private static final double GARDE_S = 900;
    private static final int REOUVERTURES = 10;

    private enum Phase { TITRE, ENTREE, JOUEUR, ETAPES, SORTIE, RETOUR, FIN }

    /** Un pas : une action jouée une fois, puis une condition attendue (délai en secondes), puis une image facultative. */
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
    private static int depuis;
    private static int reouvertes;
    private static FilmCapture images;
    private static volatile boolean termineEcrit;

    static {
        if (ACTIF) {
            Thread chien = new Thread(() -> {
                try {
                    Thread.sleep((long) ((GARDE_S + 40) * 1000));
                    LOG.error("[web-essai] FAIL délai de garde de la JVM dépassé : arrêt forcé");
                    ecrireTermine("garde de la JVM");
                    Runtime.getRuntime().halt(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "web-essai-garde");
            chien.setDaemon(true);
            chien.start();
        }
    }

    private WebEssaiShots() {}

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
            LOG.error("[web-essai] exception à la phase {}", phase, e);
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
                        images = new FilmCapture(mc.gameDirectory.toPath().resolve(DOSSIER), "Lost Island | web");
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    journal("titre atteint (" + mc.screen.getClass().getSimpleName() + ", titre du mod : "
                            + (mc.screen instanceof LostIslandTitleScreen) + ") ; mcef chargé " + mcef
                            + ", disponibilité " + WebAvailability.failure() + " après " + String.format(Locale.ROOT, "%.1f s", s));
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
                    mc.getWindow().setWindowed(1280, 720);
                    mc.options.guiScale().set(2); // en mémoire seulement
                    mc.resizeDisplay();
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
            LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, Difficulty.PEACEFUL, false,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                    acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                            .createWorldDimensions());
            passer(Phase.JOUEUR);
        });
    }

    // --- Les pas ---------------------------------------------------------------------------------------------------

    private static void composer(Minecraft mc) {
        if (!ModList.get().isLoaded("mcef")) {
            composerSansMcef(mc);
            return;
        }
        ouvrir(mc, "ouvrir-1.0", "webessai", 1.0, "a-page-echelle-1.0");
        pas("clic", () -> {
            marquer();
            cliquer(mc, "bouton");
        }, () -> recu("clic", null), 10, null);
        pause("b-clic");
        pas("molette", () -> {
            marquer();
            double[] c = centre(mc, "liste");
            ecran(mc).mouseMoved(c[0], c[1]);
            ecran(mc).mouseScrolled(c[0], c[1], -4);
        }, () -> recu("defile", null), 10, null);
        pause("c-molette");
        pas("clavier", () -> {
            marquer();
            cliquer(mc, "champ");
            for (char ch : "Bonjour éx".toCharArray()) {
                ecran(mc).charTyped(ch, 0);
            }
            ecran(mc).keyPressed(GLFW.GLFW_KEY_BACKSPACE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_BACKSPACE), 0);
            ecran(mc).keyReleased(GLFW.GLFW_KEY_BACKSPACE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_BACKSPACE), 0);
        }, () -> recu("texte", "Bonjour é"), 10, null);
        pause("d-clavier");
        echap(mc, "echap-1.0", "e-echap-ferme");
        ouvrir(mc, "ouvrir-1.5", "webessai 1.5", 1.5, "f-page-echelle-1.5");
        echap(mc, "echap-1.5", null);
        for (int i = 1; i <= REOUVERTURES; i++) {
            int n = i;
            pas("reouverture-" + n, () -> {
                marquer();
                mc.player.connection.sendCommand("webessai");
            }, () -> ecran(mc) != null && ecran(mc).pret() && recuDepuis("ready"), 15, null);
            pas("reouverture-" + n + "-echap", () -> appuyerEchap(mc),
                    () -> {
                        boolean ok = mc.screen == null;
                        if (ok && n > reouvertes) {
                            reouvertes = n;
                        }
                        return ok;
                    }, 5, null);
        }
        pas("bilan-reouvertures", () -> journal("réouvertures réussies : " + reouvertes + " sur " + REOUVERTURES
                + " ; replis vus : " + WebEssai.REPLIS), () -> true, 1, null);
    }

    private static void composerSansMcef(Minecraft mc) {
        pas("sans-mcef-1", () -> mc.player.connection.sendCommand("webessai"), () -> WebEssai.REPLIS.size() >= 1, 5, null);
        pas("attente-1", () -> {}, () -> attendu(1.0), 3, null);
        pas("sans-mcef-2", () -> mc.player.connection.sendCommand("webessai"), () -> WebEssai.REPLIS.size() >= 2, 5, null);
        pas("attente-2", () -> {}, () -> attendu(1.0), 3, "z-sans-mcef-chat");
        pas("bilan-sans-mcef", () -> {
            int avis = avisDansLeChat(mc, WebFailure.ABSENT.translationKey());
            String bilan = "écran " + (mc.screen == null ? "aucun" : mc.screen.getClass().getName()) + ", replis "
                    + WebEssai.REPLIS + ", avis « fallback.absent » dans le chat : " + avis;
            journal(bilan);
            if (mc.screen != null || avis != 1 || WebEssai.REPLIS.size() != 2) {
                echec("sans-mcef", bilan);
            }
        }, () -> true, 1, null);
    }

    /** Ouvre la page et attend le signal {@code ready}, puis une mise en page à l'agrandissement demandé. */
    private static void ouvrir(Minecraft mc, String nom, String commande, double echelle, String image) {
        pas(nom, () -> {
            marquer();
            mc.player.connection.sendCommand(commande);
        }, () -> ecran(mc) != null && ecran(mc).pret() && recuDepuis("ready"), 20, null);
        pas(nom + "-agrandissement", () -> {}, () -> {
            JsonObject g = derniereMiseEnPage();
            return g != null && Math.abs(g.get("dpr").getAsDouble() - echelle) < 0.01;
        }, 5, null);
        pas(nom + "-image", () -> {}, () -> attendu(1.0), 3, image);
    }

    /** Laisse la page repeindre ce qu'elle vient de recevoir avant l'image. */
    private static void pause(String image) {
        pas(image + "-attente", () -> {}, () -> attendu(0.8), 2, image);
    }

    private static void echap(Minecraft mc, String nom, String image) {
        pas(nom, () -> appuyerEchap(mc), () -> mc.screen == null, 5, null);
        if (image != null) {
            pas(nom + "-image", () -> {}, () -> attendu(0.5), 2, image);
        }
    }

    private static void appuyerEchap(Minecraft mc) {
        WebEssai.Ecran e = ecran(mc);
        if (e != null) {
            e.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }
    }

    private static void pas(String nom, Runnable action, BooleanSupplier fini, double delai, String image) {
        PAS.add(new Pas(nom, action, fini, delai, image));
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
            p.action().run();
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
            echec(p.nom(), "condition non atteinte en " + p.delai() + " s (écran " + mc.screen + ", derniers reçus "
                    + derniers() + ")");
            if (p.image() != null) {
                capturer(mc, p.image() + "-echec");
            }
            if (mc.screen != null) {
                mc.setScreen(null);
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

    // --- Outils de l'écran -----------------------------------------------------------------------------------------

    private static WebEssai.Ecran ecran(Minecraft mc) {
        return mc.screen instanceof WebEssai.Ecran e ? e : null;
    }

    private static void marquer() {
        depuis = WebEssai.RECUS.size();
    }

    private static boolean recuDepuis(String action) {
        return recu(action, null);
    }

    private static boolean recu(String action, String valeur) {
        List<JsonObject> l = WebEssai.RECUS;
        for (int i = depuis; i < l.size(); i++) {
            JsonObject m = l.get(i);
            if (action.equals(m.get("a").getAsString())
                    && (valeur == null || (m.has("v") && valeur.equals(m.get("v").getAsString())))) {
                return true;
            }
        }
        return false;
    }

    /** Dernière mise en page envoyée par la page ({@code ready} ou {@code layout}), ou {@code null}. */
    private static JsonObject derniereMiseEnPage() {
        List<JsonObject> l = WebEssai.RECUS;
        for (int i = l.size() - 1; i >= 0; i--) {
            String a = l.get(i).get("a").getAsString();
            if ("ready".equals(a) || "layout".equals(a)) {
                return l.get(i);
            }
        }
        return null;
    }

    private static JsonObject dernierReady() {
        JsonObject g = derniereMiseEnPage();
        if (g == null) {
            throw new IllegalStateException("aucune mise en page reçue");
        }
        return g;
    }

    /** Centre d'un élément de la page, en coordonnées d'interface : boîte CSS x devicePixelRatio / échelle d'interface. */
    private static double[] centre(Minecraft mc, String element) {
        JsonObject ready = dernierReady();
        JsonArray b = ready.getAsJsonArray(element);
        double dpr = ready.get("dpr").getAsDouble();
        double gui = mc.getWindow().getGuiScale();
        WebEssai.Ecran e = ecran(mc);
        double x = e.cadre().x() + (b.get(0).getAsDouble() + b.get(2).getAsDouble() / 2) * dpr / gui;
        double y = e.cadre().y() + (b.get(1).getAsDouble() + b.get(3).getAsDouble() / 2) * dpr / gui;
        journal(String.format(Locale.ROOT, "centre de « %s » : css %s, dpr %.3f, interface (%.1f, %.1f)", element, b, dpr, x, y));
        return new double[] {x, y};
    }

    private static void cliquer(Minecraft mc, String element) {
        double[] c = centre(mc, element);
        WebEssai.Ecran e = ecran(mc);
        e.mouseMoved(c[0], c[1]);
        e.mouseClicked(c[0], c[1], 0);
        e.mouseReleased(c[0], c[1], 0);
    }

    private static String derniers() {
        List<JsonObject> l = WebEssai.RECUS;
        return l.subList(Math.max(0, l.size() - 3), l.size()).toString();
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
        String detail = String.format(Locale.ROOT, "fenêtre %dx%d, interface %dx%d échelle %.0f", f.getWidth(), f.getHeight(),
                f.getGuiScaledWidth(), f.getGuiScaledHeight(), f.getGuiScale());
        JsonElement ready = WebEssai.RECUS.isEmpty() ? null : dernierReadyOuNul();
        journal("image " + nom + ".png : " + detail + (ready == null ? "" : " ; dernière mise en page " + ready));
    }

    private static JsonObject dernierReadyOuNul() {
        try {
            return dernierReady();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static void echec(String nom, String detail) {
        ECHECS.add(nom + " : " + detail);
        LOG.error("[web-essai] FAIL {} : {}", nom, detail);
    }

    private static void passer(Phase suivante) {
        journal("phase " + phase + " -> " + suivante);
        phase = suivante;
        debutPhaseNs = System.nanoTime();
        fait = false;
    }

    private static void finir(Minecraft mc) {
        phase = Phase.FIN;
        if (mc.screen instanceof WebEssai.Ecran) {
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
        LOG.info("[web-essai] [+{}] {}", String.format(Locale.ROOT, "%.3f s", secondes(DEBUT_NS)), texte);
    }

    private static double secondes(long ns) {
        return (System.nanoTime() - ns) / 1e9;
    }

    private static synchronized void ecrireTermine(String fin) {
        if (termineEcrit || DOSSIER == null) {
            return;
        }
        termineEcrit = true;
        String json = "{\"fin\": \"" + fin + "\", \"echecs\": " + ECHECS.size() + ", \"images\": " + IMAGES.size() + "}\n";
        try {
            Path dossier = Minecraft.getInstance().gameDirectory.toPath().resolve(DOSSIER);
            Files.createDirectories(dossier);
            Files.writeString(dossier.resolve("termine.json"), json, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOG.error("[web-essai] termine.json non écrit", e);
        }
    }
}
