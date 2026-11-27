package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.client.title.LostIslandTitleScreen;
import dev.oneforallsurvival.compat.CpuLoadSampler;
import dev.oneforallsurvival.compat.ReggadiumCpu;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
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

/**
 * Mesure de la saccade du panneau de Reggadium (lot C, développement seulement). Activé par
 * {@code -Doneforallsurvival.reggadiumShots=true} ({@code -PreggadiumShots}), avec Reggadium chargé
 * ({@code -PwithReggadium}). Du titre, le client ouvre le monde plat {@value #MONDE}, allume le panneau en mémoire
 * ({@code ExtraSettings.enabled}, sans toucher au fichier de réglages), puis :
 *
 * <ol>
 *   <li>relève pendant {@value #MESURE_S} s la durée de chaque image (début à début de {@code RenderTickEvent}) ;</li>
 *   <li>chronomètre {@value #APPELS} appels de {@code SystemPerformanceTracker.update()} sur le fil de rendu, la
 *       cadence de 500 ms remise à zéro avant chacun (le même appel que le panneau) ;</li>
 *   <li>écrit l'état de l'échantillonneur du mod ({@link ReggadiumCpu}) et ferme le client.</li>
 * </ol>
 * Lignes du journal préfixées {@code [reggadium-essai]}. La mesure « avant » se fait avec
 * {@code -PsansCompatReggadium} (mixin coupé).
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ReggadiumShots {
    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-reggadium-essai");
    private static final boolean ACTIF = Boolean.getBoolean("oneforallsurvival.reggadiumShots");
    static final String MONDE = "lostisland-essai-hud";
    static final double MESURE_S = 30;
    static final int APPELS = 10;
    private static final double STABILISATION_S = 10;
    private static final double GARDE_S = 1500;

    private enum Phase { TITRE, CHARGEMENT, STABILISATION, MESURE, APPELS, SORTIE, FIN }

    private static Phase phase = Phase.TITRE;
    private static final long DEBUT_NS = System.nanoTime();
    private static long debutPhaseNs = System.nanoTime();
    private static long derniereImageNs = -1L;
    private static final List<Double> IMAGES_MS = new ArrayList<>();
    private static final List<Double> APPELS_MS = new ArrayList<>();
    private static long dernierAppelNs;
    private static boolean demande;

    private ReggadiumShots() {}

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ACTIF || phase == Phase.FIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            long now = System.nanoTime();
            if (phase == Phase.MESURE && derniereImageNs > 0L) {
                IMAGES_MS.add((now - derniereImageNs) / 1e6);
            }
            derniereImageNs = now;
            return;
        }
        if (secondes(DEBUT_NS) > GARDE_S) {
            journal("ECHEC garde : " + GARDE_S + " s dépassées en phase " + phase);
            phase = Phase.FIN;
            mc.stop();
            return;
        }
        try {
            avancer(mc);
        } catch (RuntimeException e) {
            LOG.error("[reggadium-essai] exception en phase {}", phase, e);
            phase = Phase.FIN;
            mc.stop();
        }
    }

    private static void avancer(Minecraft mc) {
        double s = secondes(debutPhaseNs);
        switch (phase) {
            case TITRE -> {
                if (mc.getOverlay() != null) {
                    return;
                }
                if (mc.screen instanceof AccessibilityOnboardingScreen) {
                    mc.tell(() -> mc.setScreen(new TitleScreen()));
                    return;
                }
                if (!(mc.screen instanceof LostIslandTitleScreen) || demande) {
                    return;
                }
                demande = true;
                boolean existe = mc.getLevelSource().levelExists(MONDE);
                journal((existe ? "ouverture" : "création") + " du monde plat d'essai « " + MONDE + " »");
                passer(Phase.CHARGEMENT);
                mc.tell(() -> {
                    if (existe) {
                        mc.createWorldOpenFlows().loadLevel(mc.screen, MONDE);
                    } else {
                        LevelSettings reglages = new LevelSettings(MONDE, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT);
                        mc.createWorldOpenFlows().createFreshLevel(MONDE, reglages, new WorldOptions(0L, false, false),
                                acces -> acces.registryOrThrow(Registries.WORLD_PRESET)
                                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                    }
                });
            }
            case CHARGEMENT -> {
                if (mc.player != null && mc.level != null && mc.getOverlay() == null) {
                    if (mc.screen != null) {
                        mc.tell(() -> mc.setScreen(null));
                    }
                    boolean allume = panneau(true);
                    journal("joueur dans le monde ; panneau de Reggadium allumé en mémoire : " + allume
                            + " ; synchro verticale " + mc.options.enableVsync().get() + ", plafond "
                            + mc.options.framerateLimit().get() + " img/s ; mixin "
                            + (Boolean.parseBoolean(System.getProperty("oneforallsurvival.compat.reggadium", "true"))
                                    ? "actif" : "COUPÉ (mesure avant)"));
                    passer(Phase.STABILISATION);
                } else if (s > 300) {
                    journal("ECHEC : pas de joueur 300 s après la demande, écran " + mc.screen);
                    phase = Phase.FIN;
                    mc.stop();
                }
            }
            case STABILISATION -> {
                if (s >= STABILISATION_S) {
                    IMAGES_MS.clear();
                    derniereImageNs = -1L;
                    passer(Phase.MESURE);
                }
            }
            case MESURE -> {
                if (s >= MESURE_S) {
                    bilanImages();
                    passer(Phase.APPELS);
                }
            }
            case APPELS -> {
                if (APPELS_MS.size() >= APPELS) {
                    bilanAppels();
                    passer(Phase.SORTIE);
                } else if (System.nanoTime() - dernierAppelNs > 1_000_000_000L) {
                    dernierAppelNs = System.nanoTime();
                    APPELS_MS.add(chronometrerUpdate());
                }
            }
            case SORTIE -> {
                panneau(false);
                CpuLoadSampler e = ReggadiumCpu.current();
                journal("échantillonneur du mod : " + (e == null ? "jamais créé"
                        : "démarré=" + e.started() + ", mesures=" + e.samples() + ", charge processus="
                                + String.format(Locale.ROOT, "%.3f", e.processLoad())));
                journal("fin du client");
                phase = Phase.FIN;
                mc.tell(() -> {
                    if (mc.level != null) {
                        mc.level.disconnect();
                        mc.clearLevel();
                    }
                    mc.stop();
                });
            }
            default -> {}
        }
    }

    /** Allume ou éteint le panneau de Reggadium en mémoire. {@code false} si la classe ou le champ manque. */
    private static boolean panneau(boolean allume) {
        try {
            Field f = Class.forName("com.reggarf.mods.reggadium.extra.ExtraSettings").getField("enabled");
            f.setBoolean(null, allume);
            return f.getBoolean(null) == allume;
        } catch (ReflectiveOperationException | LinkageError e) {
            journal("panneau de Reggadium introuvable : " + e);
            return false;
        }
    }

    /** Durée d'un appel de {@code update()} sur ce fil (rendu), sa cadence de 500 ms remise à zéro avant. */
    private static double chronometrerUpdate() {
        try {
            Class<?> c = Class.forName("com.reggarf.mods.reggadium.extra.SystemPerformanceTracker");
            Field dernier = c.getDeclaredField("lastSampleTime");
            dernier.setAccessible(true);
            dernier.setLong(null, 0L);
            Method update = c.getMethod("update");
            long debut = System.nanoTime();
            update.invoke(null);
            return (System.nanoTime() - debut) / 1e6;
        } catch (ReflectiveOperationException | LinkageError e) {
            journal("update() de Reggadium introuvable : " + e);
            return -1.0;
        }
    }

    private static void bilanImages() {
        List<Double> tri = new ArrayList<>(IMAGES_MS);
        Collections.sort(tri);
        int n = tri.size();
        if (n == 0) {
            journal("ECHEC : aucune image relevée");
            return;
        }
        long plus50 = tri.stream().filter(v -> v > 50.0).count();
        long plus80 = tri.stream().filter(v -> v > 80.0).count();
        journal(String.format(Locale.ROOT,
                "images sur %.0f s : %d, médiane %.2f ms, 99e centile %.2f ms, max %.2f ms, > 50 ms : %d, > 80 ms : %d",
                MESURE_S, n, tri.get(n / 2), tri.get(Math.min(n - 1, (int) (n * 0.99))), tri.get(n - 1), plus50,
                plus80));
        StringBuilder pires = new StringBuilder();
        for (int i = n - 1; i >= Math.max(0, n - 10); i--) {
            pires.append(String.format(Locale.ROOT, "%.1f ", tri.get(i)));
        }
        journal("dix plus longues images (ms) : " + pires.toString().trim());
    }

    private static void bilanAppels() {
        StringBuilder sb = new StringBuilder();
        double max = 0;
        for (double v : APPELS_MS) {
            sb.append(String.format(Locale.ROOT, "%.3f ", v));
            max = Math.max(max, v);
        }
        journal(String.format(Locale.ROOT, "durée de SystemPerformanceTracker.update() sur le fil de rendu, %d appels "
                + "(ms) : %s; max %.3f ms", APPELS_MS.size(), sb, max));
    }

    private static void passer(Phase suivante) {
        journal("phase " + phase + " -> " + suivante);
        phase = suivante;
        debutPhaseNs = System.nanoTime();
    }

    private static double secondes(long depuisNs) {
        return (System.nanoTime() - depuisNs) / 1e9;
    }

    private static void journal(String ligne) {
        LOG.info("[reggadium-essai] {}", ligne);
    }
}
