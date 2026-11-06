package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.QueuePacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * La file de fabrication : la tête avec sa barre et son temps restant, qui avancent à chaque image entre
 * deux paquets du serveur, puis les crafts en attente, un bouton Annuler par ligne.
 */
final class QueuePane extends BenchPane {

    static final int MAX_ROWS = 4;
    static final int MIN_ROWS = 3;
    private static final int ROW = 12;
    private static final int CANCEL_W = 46;
    private static final long REFUSAL_MS = 6000;

    private final EngineerBenchScreen screen;
    private final Font font;
    private final BenchScrollBar scroll = new BenchScrollBar();
    private int rows = MAX_ROWS;

    QueuePane(EngineerBenchScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
    }

    /** Hauteur du panneau pour {@code rows} lignes en attente visibles. */
    static int heightFor(int rows) {
        return 16 + rows * ROW + 2;
    }

    void setRows(int rows) {
        this.rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
    }

    @Override
    protected void onPlaced() {
        scroll.model.setSize(Math.max(0, BenchClientState.queue().size() - 1), rows);
        scroll.place(x + w - BenchScrollBar.WIDTH - 4, contentY(), rows * ROW);
    }

    private int contentY() {
        return y + 16;
    }

    /** Largeur de la carte de la tête : plus étroite quand le panneau l'est. */
    private int headW() {
        return Math.max(120, Math.min(168, w * 38 / 100));
    }

    /** Vrai quand il y a la place du grand dessin de la tête (icône, temps, barre). */
    private boolean tall() {
        return rows * ROW >= 48;
    }

    private int listX() {
        return x + 4 + headW() + 8;
    }

    private int listRight() {
        return x + w - 4 - BenchScrollBar.WIDTH - 3;
    }

    // --- Dessin ------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, int mx, int my) {
        BenchTheme.well(g, x, y, w, h);
        List<QueuePacket.Row> queue = BenchClientState.queue();
        scroll.model.setSize(Math.max(0, queue.size() - 1), rows);

        String title = Component.translatable("gui.oneforallsurvival.bench.queue").getString();
        g.drawString(font, title, x + 6, y + 4, BenchTheme.BRASS, false);
        String count = screen.queueMax() <= 0 ? Integer.toString(queue.size())
                : Component.translatable("gui.oneforallsurvival.bench.queue.count", queue.size(),
                        screen.queueMax()).getString();
        g.drawString(font, count, x + 6 + font.width(title) + 6, y + 4,
                screen.queueFree() <= 0 ? BenchTheme.TEXT_BAD : BenchTheme.TEXT_DIM, false);
        renderRefusal(g);
        BenchTheme.rule(g, x + 4, y + 13, w - 8);

        if (queue.isEmpty()) {
            String none = Component.translatable("gui.oneforallsurvival.bench.queue.empty").getString();
            g.drawString(font, BenchTheme.ellipsize(font, none, w - 16), x + 8, contentY() + 18, BenchTheme.TEXT_DIM,
                    false);
            return;
        }
        renderHead(g, mx, my, queue.get(0));
        g.fill(x + 4 + headW() + 3, contentY(), x + 4 + headW() + 4, contentY() + rows * ROW, BenchTheme.METAL_SHADOW);
        renderWaiting(g, mx, my, queue);
    }

    private void renderRefusal(GuiGraphics g) {
        String refusal = BenchClientState.lastRefusal();
        long age = (System.nanoTime() - BenchClientState.lastRefusalNs()) / 1_000_000L;
        if (refusal.isEmpty() || age > REFUSAL_MS) {
            return;
        }
        String shown = BenchTheme.ellipsize(font, refusal, w - 24 - 160);
        g.drawString(font, shown, x + w - 8 - font.width(shown), y + 4, BenchTheme.TEXT_BAD, false);
    }

    /**
     * Avancement estimé de la tête, qui progresse à chaque image entre deux paquets. Il s'arrête à 99 % tant
     * que le serveur n'a pas confirmé la fin : une tête n'affiche jamais « 100 % » en attendant.
     */
    private double headDone(QueuePacket.Row head) {
        double elapsed = (System.nanoTime() - BenchClientState.queueReceivedNs()) / 50_000_000.0;
        double estimate = head.doneTicks() + Math.max(0, elapsed) * screen.currentSpeed();
        double limit = Math.max(head.doneTicks(), head.totalTicks() * 0.99);
        return Math.min(estimate, Math.min(limit, head.totalTicks()));
    }

    private int headCancelY() {
        return tall() ? contentY() + 2 : contentY();
    }

    private void renderHead(GuiGraphics g, int mx, int my, QueuePacket.Row head) {
        int hw = headW();
        int cx = x + 4;
        int cy = contentY();
        int cancelX = cx + hw - CANCEL_W;
        double done = headDone(head);
        double ticksLeft = Math.max(0, head.totalTicks() - done);
        double speed = Math.max(0.01, screen.currentSpeed());
        String left = Component.translatable("gui.oneforallsurvival.bench.queue.remaining",
                BenchFormat.remaining(ticksLeft / speed)).getString();
        double fraction = head.totalTicks() <= 0 ? 1 : done / head.totalTicks();
        String percent = (int) Math.floor(fraction * 100) + " %";
        String name = BenchClientState.nameOf(head.result());
        if (tall()) {
            g.renderItem(head.result(), cx, cy);
            g.renderItemDecorations(font, head.result(), cx, cy);
            g.drawString(font, BenchTheme.ellipsize(font, name, cancelX - (cx + 21) - 4), cx + 21, cy + 4,
                    BenchTheme.BRASS_LIGHT, false);
            renderCancel(g, mx, my, cancelX, headCancelY(), CANCEL_W, 12);
            g.drawString(font, Component.translatable("gui.oneforallsurvival.bench.queue.running").getString(), cx,
                    cy + 21, BenchTheme.TEXT_DIM, false);
            g.drawString(font, left, cx + hw - font.width(left), cy + 21, BenchTheme.TEXT, false);
            BenchTheme.progress(g, cx, cy + 32, hw, 13, fraction);
            g.drawString(font, percent, cx + (hw - font.width(percent)) / 2, cy + 36, 0xFFFFFFFF, true);
        } else {
            // Version serrée : une ligne de nom, une barre qui porte le pourcentage et le temps restant.
            BenchTheme.item(g, font, head.result(), cx, cy, 0.75F, false);
            g.drawString(font, BenchTheme.ellipsize(font, name, cancelX - (cx + 15) - 4), cx + 15, cy + 2,
                    BenchTheme.BRASS_LIGHT, false);
            renderCancel(g, mx, my, cancelX, headCancelY(), CANCEL_W, 12);
            BenchTheme.progress(g, cx, cy + 16, hw, 13, fraction);
            String bar = BenchTheme.ellipsize(font, percent + " - " + left, hw - 6);
            g.drawString(font, bar, cx + (hw - font.width(bar)) / 2, cy + 20, 0xFFFFFFFF, true);
        }
    }

    private void renderWaiting(GuiGraphics g, int mx, int my, List<QueuePacket.Row> queue) {
        int first = scroll.model.first();
        int lx = listX();
        int right = listRight();
        for (int i = 0; i < rows && first + i + 1 < queue.size(); i++) {
            QueuePacket.Row row = queue.get(first + i + 1);
            int ry = contentY() + i * ROW;
            if (BenchTheme.inside(mx, my, lx, ry, right - lx, ROW)) {
                g.fill(lx, ry, right, ry + ROW, BenchTheme.ROW_HOVER);
            }
            BenchTheme.item(g, font, row.result(), lx + 1, ry, 0.75F, false);
            int cancelX = right - CANCEL_W;
            String time = BenchFormat.remaining(row.totalTicks() / Math.max(0.01, screen.currentSpeed()));
            int timeX = cancelX - 4 - font.width(time);
            g.drawString(font, time, timeX, ry + 2, BenchTheme.TEXT_DIM, false);
            String name = BenchClientState.nameOf(row.result());
            if (row.result().getCount() > 1) {
                name = row.result().getCount() + " x " + name;
            }
            g.drawString(font, BenchTheme.ellipsize(font, name, timeX - 4 - (lx + 16)), lx + 16, ry + 2, BenchTheme.TEXT,
                    false);
            renderCancel(g, mx, my, cancelX, ry, CANCEL_W, ROW - 1);
        }
        if (scroll.model.scrollable()) {
            scroll.render(g, mx, my);
        }
    }

    private void renderCancel(GuiGraphics g, int mx, int my, int bx, int by, int bw, int bh) {
        String label = Component.translatable("gui.oneforallsurvival.bench.queue.cancel").getString();
        BenchTheme.button(g, font, bx, by, bw, bh, label, BenchTheme.inside(mx, my, bx, by, bw, bh), true, false);
    }

    // --- Souris -------------------------------------------------------------------------------------

    /** L'index (dans la file entière) de la ligne dont le bouton Annuler est sous la souris, ou -1. */
    private int cancelAt(double mx, double my) {
        List<QueuePacket.Row> queue = BenchClientState.queue();
        if (queue.isEmpty()) {
            return -1;
        }
        int cx = x + 4;
        if (BenchTheme.inside(mx, my, cx + headW() - CANCEL_W, headCancelY(), CANCEL_W, 12)) {
            return 0;
        }
        int first = scroll.model.first();
        for (int i = 0; i < rows && first + i + 1 < queue.size(); i++) {
            int ry = contentY() + i * ROW;
            if (BenchTheme.inside(mx, my, listRight() - CANCEL_W, ry, CANCEL_W, ROW - 1)) {
                return first + i + 1;
            }
        }
        return -1;
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        if (scroll.mouseClicked(mx, my)) {
            return true;
        }
        int index = button == 0 ? cancelAt(mx, my) : -1;
        if (index >= 0) {
            screen.cancel(BenchClientState.queue().get(index).id());
            return true;
        }
        return contains(mx, my);
    }

    @Override
    boolean mouseDragged(double mx, double my) {
        return scroll.mouseDragged(my);
    }

    @Override
    void mouseReleased() {
        scroll.mouseReleased();
    }

    @Override
    boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my)) {
            return false;
        }
        scroll.model.scrollBy(-delta);
        return true;
    }

    @Override
    boolean tooltip(GuiGraphics g, int mx, int my) {
        List<QueuePacket.Row> queue = BenchClientState.queue();
        int cancel = cancelAt(mx, my);
        if (cancel >= 0) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(cancel == 0 ? "gui.oneforallsurvival.bench.queue.cancel.head"
                    : "gui.oneforallsurvival.bench.queue.cancel.waiting"));
            tip.add(Component.translatable("gui.oneforallsurvival.bench.queue.cancel.refund")
                    .withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, tip, mx, my);
            return true;
        }
        if (queue.isEmpty()) {
            return false;
        }
        if (BenchTheme.inside(mx, my, x + 4, contentY(), headW() - CANCEL_W - 2, tall() ? 20 : 14)) {
            return itemTip(g, queue.get(0).result(), mx, my);
        }
        int first = scroll.model.first();
        for (int i = 0; i < rows && first + i + 1 < queue.size(); i++) {
            int ry = contentY() + i * ROW;
            if (BenchTheme.inside(mx, my, listX(), ry, listRight() - CANCEL_W - listX(), ROW)) {
                return itemTip(g, queue.get(first + i + 1).result(), mx, my);
            }
        }
        return false;
    }

    private boolean itemTip(GuiGraphics g, ItemStack stack, int mx, int my) {
        g.renderComponentTooltip(font, Screen.getTooltipFromItem(Minecraft.getInstance(), stack), mx, my);
        return true;
    }
}
