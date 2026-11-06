package dev.oneforallsurvival.bench.client;

/**
 * L'arithmétique d'une liste à défilement, par lignes entières : décalage, bornes, et géométrie de la
 * poignée d'une barre. Pure, donc testable ; le dessin et la souris sont ailleurs.
 */
public final class ScrollModel {

    private int total;
    private int visible;
    private double offset;

    public void setSize(int total, int visible) {
        this.total = Math.max(0, total);
        this.visible = Math.max(1, visible);
        this.offset = clamp(offset);
    }

    public int total() {
        return total;
    }

    public int visible() {
        return visible;
    }

    public int maxFirst() {
        return Math.max(0, total - visible);
    }

    /** Index de la première ligne affichée. */
    public int first() {
        return (int) Math.round(clamp(offset));
    }

    public boolean scrollable() {
        return total > visible;
    }

    public void scrollBy(double rows) {
        offset = clamp(offset + rows);
    }

    public void scrollTo(double first) {
        offset = clamp(first);
    }

    /** Fait défiler le moins possible pour que la ligne {@code index} soit affichée. */
    public void reveal(int index) {
        int first = first();
        if (index < first) {
            offset = clamp(index);
        } else if (index >= first + visible) {
            offset = clamp(index - visible + 1);
        }
    }

    /** Hauteur de la poignée dans une piste de {@code track} pixels : proportionnelle, jamais minuscule. */
    public int thumbHeight(int track) {
        if (!scrollable()) {
            return track;
        }
        int proportional = (int) Math.round((double) track * visible / total);
        return Math.max(Math.min(8, track), Math.min(track, proportional));
    }

    /** Position de la poignée, en pixels depuis le haut de la piste. */
    public int thumbTop(int track) {
        int free = track - thumbHeight(track);
        return maxFirst() == 0 ? 0 : (int) Math.round(free * clamp(offset) / maxFirst());
    }

    /**
     * Pose le décalage pour que le haut de la poignée soit à {@code thumbTopPixel} dans la piste.
     */
    public void dragThumbTo(double thumbTopPixel, int track) {
        int free = track - thumbHeight(track);
        if (free <= 0) {
            return;
        }
        offset = clamp(thumbTopPixel / free * maxFirst());
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(value, maxFirst()));
    }
}
