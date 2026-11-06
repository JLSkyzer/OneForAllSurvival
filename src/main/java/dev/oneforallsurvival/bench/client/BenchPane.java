package dev.oneforallsurvival.bench.client;

import net.minecraft.client.gui.GuiGraphics;

/** Un panneau rectangulaire de l'écran : il se place, se dessine et traite sa part de la souris. */
abstract class BenchPane {

    protected int x;
    protected int y;
    protected int w;
    protected int h;

    void place(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        onPlaced();
    }

    /** Appelé après chaque placement : recalcule ce qui dépend de la taille. */
    protected void onPlaced() {}

    boolean contains(double mx, double my) {
        return BenchTheme.inside(mx, my, x, y, w, h);
    }

    abstract void render(GuiGraphics g, int mx, int my);

    boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    boolean mouseDragged(double mx, double my) {
        return false;
    }

    void mouseReleased() {}

    boolean mouseScrolled(double mx, double my, double delta) {
        return false;
    }

    /** Dessine l'infobulle de ce qui est sous la souris ; vrai si elle en a dessiné une. */
    boolean tooltip(GuiGraphics g, int mx, int my) {
        return false;
    }
}
