package dev.oneforallsurvival.bench.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Le décor de l'établi : tôle sombre récupérée d'un avion, laiton chaud. Tout le dessin des panneaux et
 * toutes les couleurs passent par ici ; la Task 7 remplacera ces aplats par des textures en ne touchant
 * que cette classe. Les panneaux ne connaissent que ses méthodes.
 */
final class BenchTheme {

    // --- Tôle ---
    static final int METAL_DARK = 0xFF14110E;
    static final int METAL_SHADOW = 0xFF2A2622;
    static final int METAL = 0xFF3A362F;
    static final int METAL_LIGHT = 0xFF58524A;
    static final int WELL = 0xFF1B1916;
    static final int WELL_EDGE = 0xFF0C0B09;
    static final int WELL_LIP = 0xFF4A453D;
    static final int RIVET = 0xFF7A7367;
    static final int RIVET_SHADE = 0xFF1E1B17;

    // --- Laiton ---
    static final int BRASS = 0xFFC8963C;
    static final int BRASS_LIGHT = 0xFFE9C06E;
    static final int BRASS_DARK = 0xFF7A5519;
    static final int BRASS_DEEP = 0xFF3F2E12;

    // --- Textes ---
    static final int TEXT = 0xFFEAE3D6;
    static final int TEXT_DIM = 0xFF9C9588;
    static final int TEXT_OK = 0xFF8AD070;
    static final int TEXT_BAD = 0xFFF0614F;
    static final int TEXT_ON_BRASS = 0xFF1C140A;
    static final int TEXT_DISABLED = 0xFF7C766C;

    // --- Lignes de liste ---
    static final int ROW_HOVER = 0xFF2E2A25;
    static final int ROW_SELECTED = 0xFF4A3716;

    private BenchTheme() {}

    /** Tôle biseautée avec filet sombre : le cadre d'un grand panneau. */
    static void plate(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, METAL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, METAL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, METAL_LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, METAL_LIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, METAL_SHADOW);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, METAL_SHADOW);
    }

    /** Cuvette enfoncée : le fond des listes et des champs. */
    static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WELL_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
        g.fill(x, y + h, x + w, y + h + 1, WELL_LIP);
        g.fill(x + w, y, x + w + 1, y + h + 1, WELL_LIP);
    }

    /**
     * Une case d'objet : cuvette de 18 x 18 pixels dont {@code x}, {@code y} est le coin. L'objet, de 16 x 16,
     * occupe le centre ; le haut et la gauche sont dans l'ombre, le bas et la droite accrochent la lumière.
     */
    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, WELL_LIP);
        g.fill(x, y, x + 17, y + 17, WELL_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, WELL);
    }

    /** Un rivet : deux pixels de relief. */
    static void rivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, RIVET);
        g.fill(x + 1, y + 1, x + 2, y + 2, RIVET_SHADE);
    }

    /** Les quatre rivets d'angle d'un panneau. */
    static void rivets(GuiGraphics g, int x, int y, int w, int h) {
        rivet(g, x + 3, y + 3);
        rivet(g, x + w - 5, y + 3);
        rivet(g, x + 3, y + h - 5);
        rivet(g, x + w - 5, y + h - 5);
    }

    /** Filet de séparation en laiton sombre. */
    static void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, BRASS_DARK);
        g.fill(x, y + 1, x + w, y + 2, METAL_DARK);
    }

    /** Un bouton biseauté. {@code primary} : laiton plein, pour l'action principale. */
    static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hover,
            boolean enabled, boolean primary) {
        int face;
        int light;
        int shade;
        int text;
        if (!enabled) {
            face = 0xFF2F2C27;
            light = 0xFF3D3933;
            shade = 0xFF24211D;
            text = TEXT_DISABLED;
        } else if (primary) {
            face = hover ? 0xFFD9A649 : BRASS;
            light = BRASS_LIGHT;
            shade = BRASS_DARK;
            text = TEXT_ON_BRASS;
        } else {
            face = hover ? 0xFF5C554A : 0xFF48433B;
            light = METAL_LIGHT;
            shade = METAL_SHADOW;
            text = hover ? BRASS_LIGHT : TEXT;
        }
        g.fill(x, y, x + w, y + h, METAL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, light);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, light);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shade);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, shade);
        String shown = ellipsize(font, label, w - 4);
        int tx = x + (w - font.width(shown)) / 2;
        int ty = y + (h - 8) / 2 + 1;
        g.drawString(font, shown, tx, ty, text, false);
    }

    /** Barre de progression en laiton, creuse. {@code fraction} entre 0 et 1. */
    static void progress(GuiGraphics g, int x, int y, int w, int h, double fraction) {
        well(g, x, y, w, h);
        int inner = w - 4;
        int filled = (int) Math.round(inner * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x + 2, y + 2, x + 2 + filled, y + h - 2, BRASS);
            g.fill(x + 2, y + 2, x + 2 + filled, y + 3, BRASS_LIGHT);
            g.fill(x + 2, y + h - 3, x + 2 + filled, y + h - 2, BRASS_DARK);
        }
    }

    /** Barre de défilement : piste creuse et poignée de laiton. */
    static void scrollBar(GuiGraphics g, int x, int y, int w, int h, ScrollModel model, boolean hover) {
        g.fill(x, y, x + w, y + h, WELL_EDGE);
        g.fill(x + 1, y, x + w - 1, y + h, WELL);
        if (!model.scrollable()) {
            return;
        }
        int top = y + model.thumbTop(h);
        int height = model.thumbHeight(h);
        g.fill(x, top, x + w, top + height, hover ? BRASS_LIGHT : BRASS);
        g.fill(x, top, x + w, top + 1, BRASS_LIGHT);
        g.fill(x, top + height - 1, x + w, top + height, BRASS_DARK);
    }

    /** Le texte coupé par « ... » s'il dépasse {@code width} pixels. */
    static String ellipsize(Font font, String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        int dots = font.width("...");
        if (width <= dots) {
            return font.plainSubstrByWidth(text, width);
        }
        return font.plainSubstrByWidth(text, width - dots) + "...";
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Dessine un objet réduit au {@code scale} donné (0,75 : 12 pixels), pour les lignes serrées. */
    static void item(GuiGraphics g, Font font, net.minecraft.world.item.ItemStack stack, int x, int y, float scale,
            boolean decorations) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.renderItem(stack, 0, 0);
        if (decorations) {
            g.renderItemDecorations(font, stack, 0, 0);
        }
        g.pose().popPose();
    }
}
