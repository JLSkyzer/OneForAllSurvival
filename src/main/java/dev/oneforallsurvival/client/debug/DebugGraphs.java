package dev.oneforallsurvival.client.debug;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.client.title.TitleLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FrameTimer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Graphes de F3 + Alt aux couleurs du F3 : mêmes données et même échelle que vanilla ({@code drawChart}), une barre
 * par image ou par tick, repères 60 et 30 FPS (20 TPS pour le serveur intégré). Le camembert reste celui de vanilla.
 */
@Mod.EventBusSubscriber(modid = OneForAllSurvival.MODID, value = Dist.CLIENT)
public final class DebugGraphs {

    private static final int PAD = 4;
    private static final int TITLE_H = 12;
    /** Hauteur de la zone des barres : un peu plus que le repère de 30 FPS, placé à 60 px. */
    private static final int BARS_H = 66;
    private static final int CARD_H = TITLE_H + BARS_H + 2 * PAD;
    private static final int MAX_BARS = 240;
    private static final int FPS_SCALE = 30;
    private static final int FPS_DIVISOR = 60;
    private static final int TPS_SCALE = 60;
    private static final int TPS_DIVISOR = 20;

    /** Hauteur à laisser libre en bas de l'écran quand les graphes sont affichés. */
    static final int RESERVED_H = CARD_H + DebugLayout.MARGIN;

    private DebugGraphs() {}

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.FPS_GRAPH.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.renderDebug || !mc.options.renderFpsChart) {
            return;
        }
        event.setCanceled(true);
        if (mc.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        g.drawManaged(() -> {
            int width = g.guiWidth();
            int cardW = Math.min(MAX_BARS + 2 * PAD, width / 2 - DebugLayout.MARGIN - 1);
            drawCard(g, mc.font, mc.getFrameTimer(), DebugLayout.MARGIN, cardW, true);
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                drawCard(g, mc.font, server.getFrameTimer(), width - DebugLayout.MARGIN - cardW, cardW, false);
            }
        });
    }

    private static void drawCard(GuiGraphics g, Font font, FrameTimer timer, int x, int w, boolean fps) {
        int y = g.guiHeight() - DebugLayout.MARGIN - CARD_H;
        DebugPanels.card(g, font, new Rect(x, y, w, CARD_H), I18n.get(DebugFormat.key(fps ? "graph.fps" : "graph.tps")),
                false, 1.0, 1.0);

        long[] log = timer.getLog();
        int bars = Math.max(0, Math.min(w - 2 * PAD, log.length));
        int base = y + CARD_H - PAD;
        int left = x + PAD;
        int end = timer.getLogEnd();
        int scale = fps ? FPS_SCALE : TPS_SCALE;
        int divisor = fps ? FPS_DIVISOR : TPS_DIVISOR;
        int colorMax = fps ? DebugTheme.FRAME_COLOR_MAX : TPS_SCALE;

        long min = Long.MAX_VALUE;
        long max = 0;
        long sum = 0;
        for (int i = 0; i < bars; i++) {
            // La plus ancienne à gauche, comme vanilla.
            long nanos = log[timer.wrapIndex(end - bars + i + log.length)];
            int value = timer.scaleSampleTo(nanos, scale, divisor);
            int h = Math.max(0, Math.min(BARS_H, value));
            g.fill(left + i, base - h, left + i + 1, base, DebugTheme.sampleColor(Math.min(value, colorMax), colorMax));
            min = Math.min(min, nanos);
            max = Math.max(max, nanos);
            sum += nanos;
        }

        if (fps) {
            guide(g, font, left, bars, base - 30, "60 FPS");
            guide(g, font, left, bars, base - 60, "30 FPS");
        } else {
            guide(g, font, left, bars, base - 60, "20 TPS");
        }

        if (bars > 0) {
            String stats = I18n.get(DebugFormat.key("graph.stats"), millis(min), millis(sum / bars), millis(max));
            g.drawString(font, stats, x + w - PAD - DebugPanels.width(font, stats), y + PAD, DebugTheme.LABEL, false);
        }
    }

    private static void guide(GuiGraphics g, Font font, int x, int bars, int y, String label) {
        g.fill(x, y, x + bars, y + 1, DebugTheme.GUIDE);
        g.drawString(font, label, x + 2, y + 2, DebugTheme.VALUE, false);
    }

    private static long millis(long nanos) {
        return Math.round(nanos / 1_000_000.0);
    }
}
