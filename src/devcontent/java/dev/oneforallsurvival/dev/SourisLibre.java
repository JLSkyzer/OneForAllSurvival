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
 * Un client qui se pilote seul (démonstrations, films, essais de bout en bout, captures) ne garde jamais la souris.
 * Minecraft la capture à l'ouverture de sa fenêtre et à chaque écran fermé en jeu : sous Windows, le curseur reste
 * alors enfermé dans la fenêtre, y compris à travers un bureau à distance (2026-11-11, Killian bloqué dans RustDesk).
 * Ces scripts n'en ont pas besoin : ils posent eux-mêmes regard et clics.
 *
 * <p>À chaque image et à chaque tick, si le jeu a repris la souris, elle est rendue sans passer par
 * {@link MouseHandler#releaseMouse()}, qui replacerait le curseur au centre de la fenêtre : le drapeau du jeu est
 * baissé et le curseur repasse en mode normal là où il est. Actif seulement si une propriété d'automate est passée au
 * lancement ({@code oneforallsurvival.*} ou {@code lostisland.*}) : un {@code runClient} ordinaire n'est pas touché.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SourisLibre {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-souris");
    private static final boolean ACTIF = System.getProperties().stringPropertyNames().stream()
            .anyMatch(cle -> cle.startsWith("oneforallsurvival.") || cle.startsWith("lostisland."));
    /** Le drapeau de capture du jeu (noms officiels : devcontent ne tourne qu'en développement). */
    private static final Field CAPTUREE = champ();
    private static int rendues;

    private SourisLibre() {}

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
