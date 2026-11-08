package dev.oneforallsurvival.client.debug;

import dev.oneforallsurvival.client.debug.DebugFormat.Row;
import dev.oneforallsurvival.client.debug.DebugFormat.Segment;
import dev.oneforallsurvival.client.title.TitleLayout.Rect;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FrameTimer;
import org.jetbrains.annotations.Nullable;

/**
 * Dessin des cartes en verre ambré. Tout passe par {@code GuiGraphics.fill} et {@code drawString}, avec
 * l'alpha dans les couleurs et les déplacements dans la pile de matrices (dépilée) : ni {@code setShaderColor},
 * ni scissor, rien à remettre dans l'état de rendu de Minecraft. À appeler dans {@code drawManaged}.
 */
final class DebugPanels {

    static final int LINE_H = 10;
    static final int PAD_X = 6;
    private static final int PAD_TOP = 4;
    private static final int TITLE_H = 11;
    private static final int PAD_BOTTOM = 3;
    /** Hauteur fixe d'une carte, en dehors de ses lignes. */
    static final int CHROME = PAD_TOP + TITLE_H + PAD_BOTTOM;

    private static final int FPS_H = 18;
    private static final int SPARK_H = 14;
    private static final int SPARK_SPACE = SPARK_H + 3;
    private static final int BAR_H = 4;
    private static final int BAR_SPACE = BAR_H + 3;
    /** En dessous de cet alpha, {@code Font} tiendrait la couleur pour opaque : on ne dessine pas. */
    private static final int MIN_TEXT_ALPHA = 5;
    private static final int MAX_WIDTHS = 2048;
    private static final Map<String, Integer> WIDTHS = new HashMap<>();

    private DebugPanels() {}

    /** Largeur d'un texte, gardée en mémoire : la plupart des morceaux ne changent pas d'une image à l'autre. */
    static int width(Font font, String text) {
        Integer known = WIDTHS.get(text);
        if (known != null) {
            return known;
        }
        if (WIDTHS.size() >= MAX_WIDTHS) {
            WIDTHS.clear();
        }
        int measured = font.width(text);
        WIDTHS.put(text, measured);
        return measured;
    }

    static void forgetWidths() {
        WIDTHS.clear();
    }

    static int rowWidth(Font font, Row row) {
        int w = 0;
        for (Segment s : row.segments()) {
            w += width(font, s.text());
        }
        return w;
    }

    /** Fond, bord et titre d'une carte, à coins adoucis d'un pixel. */
    static void card(GuiGraphics g, Font font, Rect r, String title, boolean island, double alpha, double titleScale) {
        int glass = DebugTheme.fade(DebugTheme.GLASS, alpha);
        int border = DebugTheme.fade(island ? DebugTheme.BORDER_ISLAND : DebugTheme.BORDER, alpha);
        int x2 = r.x() + r.w();
        int y2 = r.y() + r.h();
        g.fill(r.x() + 1, r.y() + 1, x2 - 1, y2 - 1, glass);
        g.fill(r.x() + 1, r.y(), x2 - 1, r.y() + 1, border);
        g.fill(r.x() + 1, y2 - 1, x2 - 1, y2, border);
        g.fill(r.x(), r.y() + 1, r.x() + 1, y2 - 1, border);
        g.fill(x2 - 1, r.y() + 1, x2, y2 - 1, border);

        int color = DebugTheme.fade(island ? DebugTheme.ISLAND : DebugTheme.AMBER, alpha);
        if (visible(color)) {
            g.pose().pushPose();
            g.pose().translate(r.x() + PAD_X, r.y() + PAD_TOP, 0);
            g.pose().scale((float) titleScale, (float) titleScale, 1);
            g.drawString(font, title.toUpperCase(Locale.ROOT), 0, 0, color, false);
            g.pose().popPose();
        }
    }

    /** Lignes de la carte en vue normale ; celles qui dépassent la hauteur de la carte ne sont pas dessinées. */
    static void rows(GuiGraphics g, Font font, Rect r, List<Row> rows, double alpha) {
        int count = Math.min(rows.size(), (r.h() - CHROME) / LINE_H);
        int y = r.y() + PAD_TOP + TITLE_H;
        for (int i = 0; i < count; i++) {
            drawRow(g, font, rows.get(i), r.x() + PAD_X, y, r.w() - 2 * PAD_X, alpha);
            y += LINE_H;
        }
    }

    /** Une ligne : un morceau trop long est coupé, le texte entier reste dans la vue détaillée. */
    private static void drawRow(GuiGraphics g, Font font, Row row, int x, int y, int maxW, double alpha) {
        int cx = x;
        for (Segment s : row.segments()) {
            int room = maxW - (cx - x);
            if (room <= 0) {
                return;
            }
            String shown = s.text();
            int w = width(font, shown);
            if (w > room) {
                shown = font.plainSubstrByWidth(shown, Math.max(0, room - width(font, "…"))) + "…";
                w = room;
            }
            text(g, font, shown, cx, y, toneColor(s.tone()), alpha);
            cx += w;
        }
    }

    /** Lignes d'un morceau de carte en vue détaillée, libellé en sourdine quand la ligne en a un. */
    static void detailLines(GuiGraphics g, Font font, Rect r, List<String> lines, int first, int count, double alpha) {
        int fit = Math.min(count, (r.h() - CHROME) / LINE_H);
        int y = r.y() + PAD_TOP + TITLE_H;
        int x = r.x() + PAD_X;
        for (int i = first; i < first + fit && i < lines.size(); i++) {
            String line = lines.get(i);
            int colon = line.indexOf(": ");
            if (colon > 0 && line.lastIndexOf('§', colon) < 0) {
                String label = line.substring(0, colon + 1);
                text(g, font, label, x, y, DebugTheme.LABEL, alpha);
                text(g, font, line.substring(colon + 1), x + width(font, label), y, DebugTheme.VALUE, alpha);
            } else {
                text(g, font, line, x, y, DebugTheme.VALUE, alpha);
            }
            y += LINE_H;
        }
    }

    /** Hauteur de la carte Performances. */
    static int performanceHeight(boolean memory, boolean footer) {
        return CHROME + FPS_H + SPARK_SPACE + (memory ? LINE_H + BAR_SPACE : 0) + (footer ? LINE_H : 0);
    }

    /** Le corps de la carte Performances : FPS en grand, temps d'image, mémoire, puis TPS ou ping. */
    static void performance(GuiGraphics g, Font font, Rect r, int fps, String fpsSuffix, FrameTimer timer,
            @Nullable DebugFormat.Memory memory, String memoryLabel, String memoryValue, @Nullable Row footer, double alpha) {
        int x = r.x() + PAD_X;
        int y = r.y() + PAD_TOP + TITLE_H;
        int inner = r.w() - 2 * PAD_X;
        int bottom = r.y() + r.h() - PAD_BOTTOM;

        if (y + FPS_H <= bottom) {
            String big = fps < 0 ? "?" : Integer.toString(fps);
            int color = DebugTheme.fade(DebugTheme.VALUE, alpha);
            if (visible(color)) {
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(2, 2, 1);
                g.drawString(font, big, 0, 0, color, false);
                g.pose().popPose();
            }
            text(g, font, fpsSuffix, x + 2 * width(font, big) + 4, y + 7, DebugTheme.LABEL, alpha);
        }
        y += FPS_H;

        if (y + SPARK_SPACE <= bottom) {
            spark(g, x, y, inner, timer, alpha);
        }
        y += SPARK_SPACE;

        if (memory != null && y + LINE_H + BAR_SPACE <= bottom) {
            text(g, font, memoryLabel, x, y, DebugTheme.LABEL, alpha);
            text(g, font, memoryValue, x + width(font, memoryLabel) + 3, y, DebugTheme.VALUE, alpha);
            y += LINE_H;
            int full = Math.max(0, Math.min(inner, inner * memory.percent() / 100));
            g.fill(x, y, x + inner, y + BAR_H, DebugTheme.fade(DebugTheme.TRACK, alpha));
            g.fill(x, y, x + full, y + BAR_H, DebugTheme.fade(DebugTheme.AMBER, alpha));
            g.fill(Math.max(x, x + full - 3), y, x + full, y + BAR_H, DebugTheme.fade(DebugTheme.ORANGE, alpha));
            y += BAR_SPACE;
        }
        if (footer != null && y + LINE_H <= bottom + PAD_BOTTOM) {
            drawRow(g, font, footer, x, y, inner, alpha);
        }
    }

    /** Petit graphe du temps d'image : la plus récente à droite, une barre tous les deux pixels. */
    private static void spark(GuiGraphics g, int x, int y, int w, FrameTimer timer, double alpha) {
        long[] log = timer.getLog();
        int end = timer.getLogEnd();
        int bars = Math.min(w / 2, log.length);
        int base = y + SPARK_H;
        for (int i = 0; i < bars; i++) {
            long nanos = log[timer.wrapIndex(end - 1 - i + log.length)];
            int h = DebugTheme.sparkHeight(nanos, SPARK_H);
            int bx = x + w - 1 - 2 * i;
            int color = DebugTheme.sampleColor(DebugTheme.frameValue(nanos), DebugTheme.FRAME_COLOR_MAX);
            g.fill(bx, base - h, bx + 1, base, DebugTheme.fade(color, alpha));
        }
    }

    /** La pastille qui rappelle la vue courante et sa touche. */
    static void hint(GuiGraphics g, Font font, String hint, int right, int y) {
        int w = width(font, hint);
        g.fill(right - w - 6, y, right, y + 11, DebugTheme.GLASS);
        g.drawString(font, hint, right - w - 3, y + 2, DebugTheme.LABEL, false);
    }

    private static void text(GuiGraphics g, Font font, String text, int x, int y, int argb, double alpha) {
        int color = DebugTheme.fade(argb, alpha);
        if (visible(color)) {
            g.drawString(font, text, x, y, color, false);
        }
    }

    private static boolean visible(int argb) {
        return (argb >>> 24) >= MIN_TEXT_ALPHA;
    }

    private static int toneColor(DebugFormat.Tone tone) {
        return switch (tone) {
            case LABEL -> DebugTheme.LABEL;
            case VALUE -> DebugTheme.VALUE;
            case X -> DebugTheme.AXIS_X;
            case Y -> DebugTheme.AXIS_Y;
            case Z -> DebugTheme.AXIS_Z;
        };
    }
}
