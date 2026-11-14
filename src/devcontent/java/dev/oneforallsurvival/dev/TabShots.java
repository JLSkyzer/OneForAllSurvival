package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.client.tab.PlayerListHud;
import dev.oneforallsurvival.client.tab.TabLayout;
import dev.oneforallsurvival.client.title.LostIslandTitleScreen;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Captures de contrôle de la liste des joueurs (développement seulement, jamais empaqueté). Activé par
 * {@code -Doneforallsurvival.tabShots=<dossier>} (propriété Gradle {@code -PtabShots=<dossier>}, relatif au dossier du
 * jeu), inerte sinon. Le client crée un monde plat neuf {@value #MONDE}, y rejoue les cas des maquettes
 * ({@code docs/superpowers/specs/2026-11-14-liste-joueurs/}) et ferme le jeu.
 *
 * <p>Les autres joueurs sont factices, côté client seulement : des {@link PlayerInfo} ajoutés à la liste de la connexion
 * (champs privés, noms officiels du développement). Leurs équipes, elles, passent par le vrai chemin : rôles créés par
 * {@code /role create}, {@code /team join} sur le serveur intégré, équipes envoyées au client par le jeu. Le maire est le
 * joueur réel, nommé par {@code /role mayor set}. La touche TAB est tenue par {@code KeyMapping.setDown}.
 *
 * <p>{@code options.txt} n'est jamais réécrit : l'échelle d'interface et la taille de la fenêtre ne changent qu'en mémoire.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TabShots {
    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-tab-essai");
    private static final String DOSSIER = System.getProperty("oneforallsurvival.tabShots");
    private static final boolean ACTIF = DOSSIER != null && !DOSSIER.isBlank();
    static final String MONDE = "lostisland-essai-tab";
    private static final double GARDE_S = 900;
    /** 18:40 du jour 14, l'heure des maquettes. */
    private static final long HEURE_MAQUETTES = 324667L;

    private enum Phase { ATTENTE_TITRE, ENTREE, ATTENTE_JOUEUR, JEU, SORTIE, RETOUR, FIN }

    /** Un joueur factice : pseudo, rôle ({@code null} : aucune équipe), ping, spectateur. */
    private record Faux(String nom, String role, int ping, boolean spectateur) {}

    /** Un cas : réglages serveur, joueurs factices, ping du joueur réel, fenêtre et échelle. */
    private record Cas(String nom, List<String> commandes, List<Faux> faux, int pingReel, int largeur, int hauteur, int echelle) {}

    private static final long DEBUT_NS = System.nanoTime();
    private static final List<String> IMAGES = new ArrayList<>();
    private static final List<String> ECHECS = new ArrayList<>();
    private static final List<Cas> CAS = new ArrayList<>();

    private static Phase phase = Phase.ATTENTE_TITRE;
    private static long debutPhaseNs = System.nanoTime();
    private static boolean fait;
    private static int indice;
    private static int etape;
    private static FilmCapture images;
    private static final List<PlayerInfo> INJECTES = new ArrayList<>();
    private static volatile boolean termineEcrit;

    static {
        if (ACTIF) {
            Thread chien = new Thread(() -> {
                try {
                    Thread.sleep((long) ((GARDE_S + 40) * 1000));
                    LOG.error("[tab-essai] FAIL délai de garde de la JVM dépassé : arrêt forcé");
                    ecrireTermine("garde de la JVM");
                    Runtime.getRuntime().halt(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "tab-essai-garde");
            chien.setDaemon(true);
            chien.start();
        }
    }

    private TabShots() {}

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
            LOG.error("[tab-essai] exception à la phase {}", phase, e);
            echec("exception-" + phase.name().toLowerCase(Locale.ROOT), e.toString());
            finir(mc);
        }
    }

    private static void avancer(Minecraft mc, double s) {
        switch (phase) {
            case ATTENTE_TITRE -> {
                if (mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen) {
                    mc.tell(() -> mc.setScreen(new TitleScreen()));
                } else if (mc.getOverlay() == null && mc.screen instanceof LostIslandTitleScreen) {
                    mc.options.pauseOnLostFocus = false; // en mémoire seulement
                    try {
                        images = new FilmCapture(mc.gameDirectory.toPath().resolve(DOSSIER), "Lost Island | TAB");
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    journal("titre atteint");
                    passer(Phase.ENTREE);
                } else if (s > 600) {
                    echec("titre", "titre jamais atteint");
                    finir(mc);
                }
            }
            case ENTREE -> entrer(mc);
            case ATTENTE_JOUEUR -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.screen == null) {
                    mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                    mc.getTutorial().stop();
                    preparer(mc);
                    composer(mc.getUser().getName());
                    passer(Phase.JEU);
                } else if (s > 120) {
                    echec("monde", "joueur absent après 120 s");
                    finir(mc);
                }
            }
            case JEU -> jouer(mc, s);
            case SORTIE -> {
                if (!fait) {
                    fait = true;
                    mc.options.keyPlayerList.setDown(false);
                    retirerFaux(mc);
                    mc.tell(() -> {
                        mc.level.disconnect();
                        mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
                        mc.setScreen(new TitleScreen());
                        passer(Phase.RETOUR);
                    });
                }
            }
            case RETOUR -> {
                if (mc.screen instanceof LostIslandTitleScreen || s > 120) {
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
        Screen parent = mc.screen;
        mc.tell(() -> {
            LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, Difficulty.PEACEFUL, false,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                    acces -> acces.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                            .createWorldDimensions());
            passer(Phase.ATTENTE_JOUEUR);
        });
    }

    private static void preparer(Minecraft mc) {
        MinecraftServer serveur = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        serveur.execute(() -> {
            ServerLevel monde = serveur.overworld();
            ServerPlayer joueur = serveur.getPlayerList().getPlayer(id);
            int sol = monde.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            joueur.teleportTo(monde, 0.5, sol, 0.5, 180f, -10f);
        });
        for (String c : List.of("gamerule doDaylightCycle false", "weather clear 100000", "time set " + HEURE_MAQUETTES,
                "role create casino light_purple Gérant de casino", "role create pecheur dark_aqua Pêcheur",
                "role create port dark_aqua Responsable du port de pêche",
                "scoreboard objectives add tabessai dummy")) {
            commande(serveur, c);
        }
    }

    // --- Les cas ---------------------------------------------------------------------------------------------------

    private static void composer(String moi) {
        // a : le joueur réel seul, sans maire ; un objectif affiché dans la liste rend la liste visible en solo (vanilla).
        ajouter("a-1-joueur", List.of("scoreboard objectives setdisplay list tabessai", "role mayor remove",
                "role set " + moi + " citoyen"), List.of(), 47, 1920, 1080, 2);

        List<Faux> b = List.of(faux("Docteur_Ash", "medecin", 38), faux("Lila_RP", "medecin", 76),
                faux("Bricoleur_Jo", "ingenieur", 51), faux("Agent_Kovak", "policier", 134), faux("Capitaine_Rex", "policier", 23),
                faux("Nina_Potager", "agriculteur", 64), faux("Paysan_Lou", "agriculteur", 97), faux("Mia_Survie", "citoyen", 31),
                faux("Naufrage_Tom", "citoyen", 47), faux("Sable_Noir", "citoyen", 212), faux("xX_Coco_Xx", "citoyen", 684));
        List<String> maireAgriculteur = List.of("scoreboard objectives setdisplay list", "role set " + moi + " agriculteur",
                "role mayor set " + moi);
        ajouter("b-12-joueurs", maireAgriculteur, b, 42, 1920, 1080, 2);
        ajouter("f-12-joueurs-echelle3", List.of(), b, 42, 1920, 1080, 3);
        ajouter("12-joueurs-echelle4", List.of(), b, 42, 1920, 1080, 4);
        ajouter("12-joueurs-854x480", List.of(), b, 42, 854, 480, 2);

        List<Faux> e = List.of(faux("Docteur_Ash", "medecin", 38), faux("WWWWWWWWWWWWWWWW", "policier", 88),
                faux("Lucky_Vince", "casino", 72), faux("Pecheur_Noe", "pecheur", 61), faux("Capitaine_Marin", "port", 143),
                faux("Mia_Survie", "citoyen", 31), faux("Nouveau_Venu", "citoyen", 0),
                new Faux("Admin_Fantome", "citoyen", 12, true));
        ajouter("e-cas-limites", List.of("role set " + moi + " citoyen", "role mayor set " + moi), e, 55, 1920, 1080, 2);

        List<Faux> c = quarante();
        List<String> maireIngenieur = List.of("role set " + moi + " ingenieur", "role mayor set " + moi);
        ajouter("c-40-joueurs", maireIngenieur, c, 81, 1920, 1080, 2);
        ajouter("g-40-joueurs-echelle3", List.of(), c, 81, 1920, 1080, 3);
        ajouter("40-joueurs-echelle4", List.of(), c, 81, 1920, 1080, 4);

        List<Faux> d = quatreVingts();
        List<String> maireCitoyen = List.of("role set " + moi + " citoyen", "role mayor set " + moi);
        ajouter("d-80-joueurs", maireCitoyen, d, 70, 1920, 1080, 2);
        ajouter("h-80-joueurs-echelle3", List.of(), d, 70, 1920, 1080, 3);
        ajouter("j-80-joueurs-echelle4", List.of(), d, 70, 1920, 1080, 4);
        ajouter("i-80-joueurs-854x480", List.of(), d, 70, 854, 480, 2);
    }

    private static Faux faux(String nom, String role, int ping) {
        return new Faux(nom, role, ping, false);
    }

    /** Les 39 autres joueurs de la maquette c (un spectateur), métiers et pings relevés sur l'image. */
    private static List<Faux> quarante() {
        Object[][] t = {
                {"Bambou214", "medecin", 34}, {"Noix911", "medecin", 115}, {"Palme_Cleo", "medecin", 131},
                {"RequinPaul", "medecin", 30}, {"TempeteTom", "medecin", 767}, {"VolcanGab", "medecin", 307},
                {"Ancre_Paul", "ingenieur", 118}, {"Noah_Mangue", "ingenieur", 118}, {"Bambou621", "policier", 25},
                {"Caillou_Noah", "policier", 417}, {"Jade_Sable", "policier", 0}, {"Kayak461", "policier", 256},
                {"Noix863", "agriculteur", 424}, {"Requin_Noah", "agriculteur", 130}, {"RequinLou", "agriculteur", 319},
                {"TortueLina", "agriculteur", 22}, {"AxelCoco", "pecheur", 124}, {"Cleo_Corail", "pecheur", 131},
                {"Lea_RP", "pecheur", 737}, {"Mila_Lagon", "pecheur", 823}, {"MousseRemi", "pecheur", 41},
                {"Nina_Marin", "pecheur", 131}, {"Volcan_Jade", "pecheur", 137}, {"Brume848", "citoyen", 94},
                {"Coco_Gab", "citoyen", 110}, {"Ema_RP", "citoyen", 74}, {"Enzo_RP", "citoyen", 70},
                {"Gab_Liane", "citoyen", 18}, {"Galet811", "citoyen", 125}, {"Hugo_Radeau", "citoyen", 23},
                {"Jade_Brume", "citoyen", 78}, {"Max_Marin", "citoyen", 404}, {"Noix931", "citoyen", 74},
                {"Palme_Enzo", "citoyen", 128}, {"TheoKayak", "citoyen", 61}, {"xX_Mila_Xx", "citoyen", 73},
                {"xX_Noah_Xx", "citoyen", 36}, {"xX_Sam_Xx", "citoyen", 734}};
        List<Faux> l = new ArrayList<>();
        for (Object[] r : t) {
            l.add(faux((String) r[0], (String) r[1], (Integer) r[2]));
        }
        l.add(new Faux("Corail883", "citoyen", 320, true));
        return l;
    }

    /** 79 autres joueurs (deux spectateurs), pseudos composés, métiers et pings répartis. */
    private static List<Faux> quatreVingts() {
        String[] debuts = {"Boussole", "Iris", "Lou", "Marin", "Tortue", "Volcan", "Lagon", "Sable", "Brume", "Recif",
                "Orage", "Palme", "Caillou", "Noix", "Requin", "Tempete"};
        String[] fins = {"Ema", "Leo", "Lina", "Cleo", "Jade", "Tom", "Ines", "Remi", "Zoe", "Max"};
        String[] roles = {"medecin", "ingenieur", "policier", "agriculteur", "pecheur", "citoyen"};
        List<Faux> l = new ArrayList<>();
        for (int i = 0; i < 79; i++) {
            String nom = debuts[i % debuts.length] + "_" + fins[(i * 7) % fins.length] + (i >= 64 ? Integer.toString(i) : "");
            int ping = i % 11 == 0 ? 0 : 20 + (i * 37) % 900;
            l.add(new Faux(nom, roles[(i * 5) % roles.length], ping, i == 77 || i == 78));
        }
        return l;
    }

    private static void ajouter(String nom, List<String> commandes, List<Faux> faux, int ping, int l, int h, int echelle) {
        CAS.add(new Cas(nom, commandes, faux, ping, l, h, echelle));
    }

    // --- Déroulé ---------------------------------------------------------------------------------------------------

    /** Étape 0 : réglages ; 1 : TAB tenue ; 2 : image. */
    private static void jouer(Minecraft mc, double s) {
        if (indice >= CAS.size()) {
            if (!fait) {
                // Contrôle final : TAB relâchée, la liste disparaît (rien de dessiné, comme vanilla).
                fait = true;
                mc.options.keyPlayerList.setDown(false);
                debutPhaseNs = System.nanoTime();
            } else if (s > 1.0) {
                capturer(mc, "z-tab-relachee", "TAB relâchée : aucune liste");
                passer(Phase.SORTIE);
            }
            return;
        }
        Cas cas = CAS.get(indice);
        if (etape == 0) {
            journal("cas « " + cas.nom() + " »");
            cas.commandes().forEach(c -> commande(mc.getSingleplayerServer(), c));
            for (Faux f : cas.faux()) {
                if (f.role() != null) {
                    commande(mc.getSingleplayerServer(), "team join ofas_" + f.role() + " " + f.nom());
                }
            }
            mc.getWindow().setWindowed(cas.largeur(), cas.hauteur());
            mc.options.guiScale().set(cas.echelle()); // en mémoire seulement
            mc.resizeDisplay();
            installerFaux(mc, cas.faux());
            etape = 1;
            debutPhaseNs = System.nanoTime();
            return;
        }
        // Tenue à chaque image : le ping réel et la touche, que rien d'autre ne doit remettre.
        PlayerInfo moi = mc.player.connection.getPlayerInfo(mc.player.getUUID());
        if (moi != null) {
            appeler(moi, "setLatency", int.class, cas.pingReel());
        }
        mc.options.keyPlayerList.setDown(true);
        if (etape == 1 && s > 2.5) {
            TabLayout.Result r = PlayerListHud.lastLayout();
            var f = mc.getWindow();
            String detail = String.format(Locale.ROOT, "fenêtre %dx%d, GUI %dx%d échelle %.0f ; %s", f.getWidth(), f.getHeight(),
                    f.getGuiScaledWidth(), f.getGuiScaledHeight(), f.getGuiScale(), decrire(r));
            if (r == null) {
                echec(cas.nom(), "liste jamais dessinée : " + detail);
            }
            capturer(mc, cas.nom(), detail);
            indice++;
            etape = 0;
        }
    }

    private static String decrire(TabLayout.Result r) {
        if (r == null) {
            return "aucune mise en page";
        }
        return String.format(Locale.ROOT, "%s %dx%d, cachés %d, panneau x=%d PW=%d PH=%d bas=%d, formes courtes %s",
                r.mode(), r.cols(), r.rows(), r.hidden(), r.x(), r.width(), r.height(), r.y() + r.height(), r.shortForms());
    }

    // --- Joueurs factices ------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static void installerFaux(Minecraft mc, List<Faux> faux) {
        retirerFaux(mc);
        ClientPacketListener connexion = mc.player.connection;
        Map<UUID, PlayerInfo> carte = (Map<UUID, PlayerInfo>) champ(connexion, "playerInfoMap");
        Set<PlayerInfo> listes = (Set<PlayerInfo>) champ(connexion, "listedPlayers");
        for (Faux f : faux) {
            UUID id = UUID.nameUUIDFromBytes(("tab-essai:" + f.nom()).getBytes(StandardCharsets.UTF_8));
            PlayerInfo info = new PlayerInfo(new GameProfile(id, f.nom()), false);
            appeler(info, "setLatency", int.class, f.ping());
            appeler(info, "setGameMode", GameType.class, f.spectateur() ? GameType.SPECTATOR : GameType.SURVIVAL);
            carte.put(id, info);
            listes.add(info);
            INJECTES.add(info);
        }
    }

    @SuppressWarnings("unchecked")
    private static void retirerFaux(Minecraft mc) {
        if (INJECTES.isEmpty() || mc.player == null) {
            return;
        }
        ClientPacketListener connexion = mc.player.connection;
        Map<UUID, PlayerInfo> carte = (Map<UUID, PlayerInfo>) champ(connexion, "playerInfoMap");
        Set<PlayerInfo> listes = (Set<PlayerInfo>) champ(connexion, "listedPlayers");
        for (PlayerInfo info : INJECTES) {
            carte.remove(info.getProfile().getId());
            listes.remove(info);
        }
        INJECTES.clear();
    }

    private static Object champ(Object objet, String nom) {
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

    // --- Outils ----------------------------------------------------------------------------------------------------

    private static void commande(MinecraftServer serveur, String ligne) {
        serveur.execute(() -> {
            int r = serveur.getCommands().performPrefixedCommand(serveur.createCommandSourceStack().withSuppressedOutput(), ligne);
            if (r == 0 && !ligne.startsWith("team join") && !ligne.startsWith("role mayor remove")) {
                LOG.warn("[tab-essai] commande « {} » : 0", ligne);
            }
        });
    }

    private static void capturer(Minecraft mc, String nom, String quoi) {
        images.instantane(mc, nom);
        IMAGES.add(nom + ".png");
        journal("image " + nom + ".png : " + quoi);
    }

    private static void echec(String nom, String detail) {
        ECHECS.add(nom + " : " + detail);
        LOG.error("[tab-essai] FAIL {} : {}", nom, detail);
    }

    private static void passer(Phase suivante) {
        journal("phase " + phase + " -> " + suivante);
        phase = suivante;
        debutPhaseNs = System.nanoTime();
        fait = false;
    }

    private static void finir(Minecraft mc) {
        phase = Phase.FIN;
        if (images != null) {
            images.attendre();
        }
        journal("bilan : " + (ECHECS.isEmpty() ? "TOUT PASSE" : ECHECS.size() + " ECHEC(S)") + ", " + IMAGES.size() + " image(s)");
        ecrireTermine("normale");
        mc.stop();
    }

    private static void journal(String texte) {
        LOG.info("[tab-essai] [+{}] {}", String.format(Locale.ROOT, "%.3f s", secondes(DEBUT_NS)), texte);
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
            LOG.error("[tab-essai] termine.json non écrit", e);
        }
    }
}
