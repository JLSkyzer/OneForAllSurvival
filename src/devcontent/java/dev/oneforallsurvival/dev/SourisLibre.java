package dev.oneforallsurvival.dev;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Un client qui se pilote seul (démonstrations, films, essais de bout en bout, captures) ne touche jamais à la souris.
 * Minecraft la capture à l'ouverture de sa fenêtre et à chaque écran fermé en jeu, après avoir replacé le curseur au
 * centre de sa fenêtre : sous Windows, le curseur saute puis reste enfermé, y compris à travers un bureau à distance
 * (2026-11-11, Killian bloqué dans RustDesk ; 2026-11-27, souris tirée pendant qu'il jouait). Ces scripts n'en ont pas
 * besoin : ils posent eux-mêmes regard et clics.
 *
 * <p>{@code CurseurIntactMixin} empêche tout déplacement et toute capture du curseur. Ici, à chaque image et à chaque
 * tick, le drapeau de capture du jeu est baissé, pour qu'un mouvement de la vraie souris au-dessus de la fenêtre ne
 * tourne pas le joueur d'essai ; sans passer par {@link MouseHandler#releaseMouse()}. Actif seulement si une propriété
 * d'automate est passée au lancement ({@code oneforallsurvival.*} ou {@code lostisland.*}) : un {@code runClient}
 * ordinaire n'est pas touché.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SourisLibre {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-souris");
    private static final boolean ACTIF = System.getProperties().stringPropertyNames().stream()
            .anyMatch(cle -> cle.startsWith("oneforallsurvival.") || cle.startsWith("lostisland."));
    /** Le drapeau de capture du jeu (noms officiels : devcontent ne tourne qu'en développement). */
    private static final Field CAPTUREE = champ();
    private static int rendues;
    private static int laisses;

    private SourisLibre() {}

    /** Vrai pour un client qui se pilote seul. */
    public static boolean actif() {
        return ACTIF;
    }

    /** Le jeu voulait prendre ou rendre le curseur : il est resté où il était. */
    public static void curseurLaisse() {
        if (laisses++ < 20) {
            LOG.info("[souris] curseur laissé en place ({} fois)", laisses);
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (ACTIF && event.phase == TickEvent.Phase.START) {
            rendre();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (ACTIF && event.phase == TickEvent.Phase.END) {
            rendre();
        }
    }

    private static void rendre() {
        Minecraft mc = Minecraft.getInstance();
        // La fenêtre n'a jamais le focus : sans cela, le jeu se mettrait en pause. En mémoire seulement.
        mc.options.pauseOnLostFocus = false;
        long fenetre = mc.getWindow().getWindow();
        boolean capturee = mc.mouseHandler.isMouseGrabbed();
        if (!capturee && GLFW.glfwGetInputMode(fenetre, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL) {
            return;
        }
        if (CAPTUREE == null) {
            mc.mouseHandler.releaseMouse();
        } else {
            try {
                CAPTUREE.setBoolean(mc.mouseHandler, false);
            } catch (IllegalAccessException e) {
                mc.mouseHandler.releaseMouse();
            }
            GLFW.glfwSetInputMode(fenetre, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        if (rendues++ < 20) {
            LOG.info("[souris] rendue ({} fois), mode du curseur {}", rendues,
                    GLFW.glfwGetInputMode(fenetre, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL ? "normal" : "capturé");
        }
    }

    private static Field champ() {
        try {
            Field f = MouseHandler.class.getDeclaredField("mouseGrabbed");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            LOG.warn("[souris] champ mouseGrabbed introuvable : repli sur releaseMouse (le curseur sera recentré)");
            return null;
        }
    }
}
