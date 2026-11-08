package dev.oneforallsurvival.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Essai de l'écran de chargement (développement seulement, jamais empaqueté) : une fois l'écran titre atteint et la
 * scène Lost Island retirée, un rechargement des packs de ressources ({@code Minecraft.reloadResourcePacks}),
 * comme un changement de pack en jeu. Attendu : l'overlay de Mojang, sans plantage, sans rien de la scène libérée,
 * puis l'écran titre à nouveau. Activé par {@code -Dlostisland.reloadAfterTitle=true} ({@code -PreloadAfterTitle}).
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ReloadAfterTitle {
    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-reload-after-title");
    private static final boolean ACTIF = Boolean.getBoolean("lostisland.reloadAfterTitle");
    /** Ticks d'écran titre avant le rechargement : l'écran a le temps de s'afficher. */
    private static final int ATTENTE = 60;

    private enum Etat { ATTENTE_TITRE, RECHARGEMENT, FINI }

    private static Etat etat = Etat.ATTENTE_TITRE;
    private static int ticks;
    private static long debut;
    private static String overlayVu;

    private ReloadAfterTitle() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ACTIF || event.phase != TickEvent.Phase.END || etat == Etat.FINI) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Overlay overlay = mc.getOverlay();
        switch (etat) {
            case ATTENTE_TITRE -> {
                if (overlay != null || !(mc.screen instanceof TitleScreen)) {
                    ticks = 0;
                    return;
                }
                if (++ticks < ATTENTE) {
                    return;
                }
                LOG.info("écran titre atteint, rechargement des packs de ressources");
                debut = System.nanoTime();
                etat = Etat.RECHARGEMENT;
                mc.reloadResourcePacks().whenComplete((ok, erreur) -> LOG.info("rechargement terminé{}",
                        erreur == null ? "" : " avec une erreur : " + erreur));
            }
            case RECHARGEMENT -> {
                if (overlay != null) {
                    if (overlayVu == null) {
                        overlayVu = overlay.getClass().getName();
                        LOG.info("overlay du rechargement : {}", overlayVu);
                    }
                    return;
                }
                LOG.info("rechargement fini en {} s, overlay vu : {}, écran : {}",
                        String.format(java.util.Locale.ROOT, "%.2f", (System.nanoTime() - debut) / 1e9), overlayVu,
                        mc.screen == null ? "aucun" : mc.screen.getClass().getName());
                etat = Etat.FINI;
            }
            default -> {
            }
        }
    }
}
