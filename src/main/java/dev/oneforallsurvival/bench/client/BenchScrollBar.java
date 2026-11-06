package dev.oneforallsurvival.bench.client;

import net.minecraft.client.gui.GuiGraphics;

/** Une barre de défilement verticale : son modèle, sa place, et la poignée qu'on tire à la souris. */
final class BenchScrollBar {

    static final int WIDTH = 6;

    final ScrollModel model = new ScrollModel();
    private int x;
    private int y;
    private int h;
    private boolean dragging;
    private double grab;

    void place(int x, int y, int h) {
        this.x = x;
        this.y = y;
        this.h = h;
    }

    void render(GuiGraphics g, int mx, int my) {
        boolean hover = dragging || BenchTheme.inside(mx, my, x, y, WIDTH, h);
        BenchTheme.scrollBar(g, x, y, WIDTH, h, model, hover);
    }

    boolean mouseClicked(double mx, double my) {
        if (!model.scrollable() || !BenchTheme.inside(mx, my, x - 1, y, WIDTH + 2, h)) {
            return false;
        }
        double thumbTop = y + model.thumbTop(h);
        if (my >= thumbTop && my < thumbTop + model.thumbHeight(h)) {
            dragging = true;
            grab = my - thumbTop;
        } else {
            model.scrollBy(my < thumbTop ? -model.visible() : model.visible());
        }
        return true;
    }

    boolean mouseDragged(double my) {
        if (!dragging) {
            return false;
        }
        model.dragThumbTo(my - y - grab, h);
        return true;
    }

    void mouseReleased() {
        dragging = false;
    }
}
