package dev.oneforallsurvival.dev;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.User32;

/**
 * Un client qui se pilote seul (essais de bout en bout, captures, démonstrations) ouvre sa fenêtre sur un écran autre
 * que l'écran principal : Killian travaille sur le principal pendant que les essais tournent (2026-11-14).
 *
 * <p>Même condition que {@link SourisLibre} : une propriété d'automate ({@code oneforallsurvival.*} ou
 * {@code lostisland.*}) ; un {@code runClient} ordinaire n'est pas touché. La fenêtre est déplacée une seule fois, au
 * plus tôt : pendant la mise en place du client (fil principal), sinon à la première image. S'il n'y a qu'un écran,
 * rien ne bouge. Une fenêtre en plein écran n'est pas déplacée.
 */
public final class FenetreSecondEcran {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-fenetre");
    private static final boolean ACTIF = System.getProperties().stringPropertyNames().stream()
            .anyMatch(cle -> cle.startsWith("oneforallsurvival.") || cle.startsWith("lostisland."));
    /** Marge depuis le coin de la zone de travail, pour garder la barre de titre visible. */
    private static final int MARGE = 40;
    private static boolean fait;

    private FenetreSecondEcran() {}

    /**
     * Fin de la création de la fenêtre : sur le second écran, montrée sans prendre le premier plan, derrière les autres
     * fenêtres, et un clic dessus ne l'active pas.
     */
    public static void montrer(long handle) {
        fait = true;
        deplacer(handle);
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        long hwnd = Platform.get() == Platform.WINDOWS ? GLFWNativeWin32.glfwGetWin32Window(handle) : 0L;
        if (hwnd != 0L) {
            long style = User32.GetWindowLongPtr(hwnd, User32.GWL_EXSTYLE);
            User32.SetWindowLongPtr(hwnd, User32.GWL_EXSTYLE, style | User32.WS_EX_NOACTIVATE);
        }
        GLFW.glfwShowWindow(handle);
        if (hwnd != 0L) {
            User32.SetWindowPos(hwnd, User32.HWND_BOTTOM, 0, 0, 0, 0,
                    User32.SWP_NOMOVE | User32.SWP_NOSIZE | User32.SWP_NOACTIVATE);
        }
        LOG.info("[fenetre] montrée sans le focus, derrière les autres (focus : {})",
                GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
    }

    /** Appelé sur le fil principal (GLFW l'exige). */
    static void placer() {
        if (!ACTIF || fait) {
            return;
        }
        fait = true;
        Window fenetre = Minecraft.getInstance().getWindow();
        if (fenetre == null) {
            LOG.info("[fenetre] non déplacée (fenêtre absente)");
            return;
        }
        deplacer(fenetre.getWindow());
    }

    private static void deplacer(long handle) {
        if (GLFW.glfwGetWindowMonitor(handle) != 0L) {
            LOG.info("[fenetre] non déplacée (plein écran)");
            return;
        }
        PointerBuffer ecrans = GLFW.glfwGetMonitors();
        long principal = GLFW.glfwGetPrimaryMonitor();
        long autre = 0L;
        if (ecrans != null) {
            for (int i = 0; i < ecrans.limit(); i++) {
                if (ecrans.get(i) != principal) {
                    autre = ecrans.get(i);
                    break;
                }
            }
        }
        if (autre == 0L) {
            LOG.info("[fenetre] un seul écran");
            return;
        }
        int[] x = new int[1];
        int[] y = new int[1];
        int[] l = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetMonitorWorkarea(autre, x, y, l, h);
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
            GLFW.glfwRestoreWindow(handle);
        }
        int px = x[0] + MARGE;
        int py = y[0] + MARGE;
        GLFW.glfwSetWindowPos(handle, px, py);
        LOG.info("[fenetre] second écran ({}, {})", px, py);
    }

    /** Au plus tôt : la mise en place du client, renvoyée sur le fil principal. */
    @Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class AuChargement {
        private AuChargement() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            if (ACTIF) {
                event.enqueueWork(FenetreSecondEcran::placer);
            }
        }
    }

    /** Filet : la première image, si la mise en place n'a pas pu le faire. */
    @Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class APremiereImage {
        private APremiereImage() {}

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent event) {
            if (ACTIF && !fait && event.phase == TickEvent.Phase.START) {
                placer();
            }
        }
    }
}
