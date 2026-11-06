package dev.oneforallsurvival.bench.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.oneforallsurvival.OneForAllSurvival;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Le décor de l'établi : tôle sombre récupérée d'un avion, laiton chaud. Tout le dessin des panneaux et
 * toutes les couleurs passent par ici, les panneaux ne connaissent que ses méthodes. Les surfaces sont des
 * textures de {@code textures/gui} : des tuiles de 16 x 16 répétées (le mode de répétition du GPU fait le
 * pavage en un seul appel), un cadre à coins de 5 pixels dont les arêtes se répètent, des boutons étirés.
 * Les couleurs restent celles de l'ancien décor pour garder le contraste du texte.
 */
public final class BenchTheme {

    // --- Tôle ---
    public static final int METAL_DARK = 0xFF14110E;
    public static final int METAL_SHADOW = 0xFF2A2622;
    public static final int METAL = 0xFF3A362F;
    public static final int METAL_LIGHT = 0xFF58524A;
    public static final int WELL = 0xFF1B1916;
    public static final int WELL_EDGE = 0xFF0C0B09;
    public static final int WELL_LIP = 0xFF4A453D;
    public static final int RIVET = 0xFF7A7367;
    public static final int RIVET_SHADE = 0xFF1E1B17;

    // --- Laiton ---
    public static final int BRASS = 0xFFC8963C;
    public static final int BRASS_LIGHT = 0xFFE9C06E;
    public static final int BRASS_DARK = 0xFF7A5519;
    public static final int BRASS_DEEP = 0xFF3F2E12;

    // --- Textes ---
    public static final int TEXT = 0xFFEAE3D6;
    public static final int TEXT_DIM = 0xFF9C9588;
    public static final int TEXT_OK = 0xFF8AD070;
    public static final int TEXT_BAD = 0xFFF0614F;
    public static final int TEXT_ON_BRASS = 0xFF1C140A;
    public static final int TEXT_DISABLED = 0xFF7C766C;

    // --- Lignes de liste ---
    public static final int ROW_HOVER = 0xFF2E2A25;
    public static final int ROW_SELECTED = 0xFF4A3716;

    private static ResourceLocation gui(String name) {
        return new ResourceLocation(OneForAllSurvival.MODID, "textures/gui/" + name + ".png");
    }

    private static final ResourceLocation TILE_METAL = gui("bench_metal");
    private static final ResourceLocation TILE_WELL = gui("bench_well");
    private static final ResourceLocation TILE_BRASS = gui("bench_brass");
    private static final ResourceLocation FRAME = gui("bench_frame");
    private static final ResourceLocation RIVET_SPRITE = gui("bench_rivet");
    private static final ResourceLocation SLOT_SPRITE = gui("bench_slot");
    private static final ResourceLocation BUTTON_SPRITE = gui("bench_button");
    /** Côté du cadre : coins de 5 pixels, arêtes de 6 répétées. */
    private static final int CORNER = 5;
    private static final int EDGE = 16 - 2 * CORNER;

    private BenchTheme() {}

    /** Pave le rectangle d'une tuile de 16 x 16 : la texture est lue au-delà de son bord et se répète. */
    private static void tile(GuiGraphics g, ResourceLocation tile, int x, int y, int w, int h) {
        if (w > 0 && h > 0) {
            g.blit(tile, x, y, 0, 0F, 0F, w, h, 16, 16);
        }
    }

    /** Un morceau {@code w} x {@code h} du cadre, pris à ({@code u}, {@code v}). */
    private static void frameBit(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        g.blit(FRAME, x, y, 0, u, v, w, h, 16, 16);
    }

    /** Tôle biseautée avec filet sombre : le cadre d'un grand panneau. */
    public static void plate(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        tile(g, TILE_METAL, x, y, w, h);
        int right = x + w - CORNER;
        int bottom = y + h - CORNER;
        for (int px = x + CORNER; px < right; px += EDGE) {
            int len = Math.min(EDGE, right - px);
            frameBit(g, px, y, CORNER, 0, len, CORNER);
            frameBit(g, px, bottom, CORNER, 16 - CORNER, len, CORNER);
        }
        for (int py = y + CORNER; py < bottom; py += EDGE) {
            int len = Math.min(EDGE, bottom - py);
            frameBit(g, x, py, 0, CORNER, CORNER, len);
            frameBit(g, right, py, 16 - CORNER, CORNER, CORNER, len);
        }
        frameBit(g, x, y, 0, 0, CORNER, CORNER);
        frameBit(g, right, y, 16 - CORNER, 0, CORNER, CORNER);
        frameBit(g, x, bottom, 0, 16 - CORNER, CORNER, CORNER);
        frameBit(g, right, bottom, 16 - CORNER, 16 - CORNER, CORNER, CORNER);
    }

    /** Cuvette enfoncée : le fond des listes et des champs. */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WELL_EDGE);
        tile(g, TILE_WELL, x + 1, y + 1, w - 2, h - 2);
        g.fill(x, y + h, x + w, y + h + 1, WELL_LIP);
        g.fill(x + w, y, x + w + 1, y + h + 1, WELL_LIP);
    }

    /**
     * Une case d'objet : cuvette de 18 x 18 pixels dont {@code x}, {@code y} est le coin. L'objet, de 16 x 16,
     * occupe le centre ; le haut et la gauche sont dans l'ombre, le bas et la droite accrochent la lumière.
     */
    public static void slot(GuiGraphics g, int x, int y) {
        g.blit(SLOT_SPRITE, x, y, 0, 0F, 0F, 18, 18, 18, 18);
    }

    /** Un rivet : une tête ronde de 4 pixels, posée sur le point ({@code x}, {@code y}). */
    public static void rivet(GuiGraphics g, int x, int y) {
        RenderSystem.enableBlend();
        g.blit(RIVET_SPRITE, x - 1, y - 1, 0, 0F, 0F, 4, 4, 4, 4);
    }

    /** Les quatre rivets d'angle d'un panneau. */
    public static void rivets(GuiGraphics g, int x, int y, int w, int h) {
        rivet(g, x + 3, y + 3);
        rivet(g, x + w - 5, y + 3);
        rivet(g, x + 3, y + h - 5);
        rivet(g, x + w - 5, y + h - 5);
    }

    /** Filet de séparation en laiton sombre. */
    public static void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, BRASS_DARK);
        g.fill(x, y + 1, x + w, y + 2, METAL_DARK);
    }

    /** Un bouton biseauté. {@code primary} : laiton plein, pour l'action principale. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hover,
            boolean enabled, boolean primary) {
        int kind;
        int text;
        if (!enabled) {
            kind = 0;
            text = TEXT_DISABLED;
        } else if (primary) {
            kind = hover ? 4 : 3;
            text = TEXT_ON_BRASS;
        } else {
            kind = hover ? 2 : 1;
            text = hover ? BRASS_LIGHT : TEXT;
        }
        nineButton(g, x, y, w, h, kind * 12);
        String shown = ellipsize(font, label, w - 4);
        int tx = x + (w - font.width(shown)) / 2;
        int ty = y + (h - 8) / 2 + 1;
        g.drawString(font, shown, tx, ty, text, false);
    }

    /** Le bouton de la feuille à la hauteur {@code v} : coins de 3 pixels intacts, arêtes et centre étirés. */
    private static void nineButton(GuiGraphics g, int x, int y, int w, int h, int v) {
        final int c = 3;
        int mw = w - 2 * c;
        int mh = h - 2 * c;
        g.blit(BUTTON_SPRITE, x + c, y + c, mw, mh, c, v + c, 6, 6, 12, 60);
        g.blit(BUTTON_SPRITE, x + c, y, mw, c, c, v, 6, c, 12, 60);
        g.blit(BUTTON_SPRITE, x + c, y + h - c, mw, c, c, v + 9, 6, c, 12, 60);
        g.blit(BUTTON_SPRITE, x, y + c, c, mh, 0, v + c, c, 6, 12, 60);
        g.blit(BUTTON_SPRITE, x + w - c, y + c, c, mh, 9, v + c, c, 6, 12, 60);
        g.blit(BUTTON_SPRITE, x, y, 0, 0F, v, c, c, 12, 60);
        g.blit(BUTTON_SPRITE, x + w - c, y, 0, 9F, v, c, c, 12, 60);
        g.blit(BUTTON_SPRITE, x, y + h - c, 0, 0F, v + 9, c, c, 12, 60);
        g.blit(BUTTON_SPRITE, x + w - c, y + h - c, 0, 9F, v + 9, c, c, 12, 60);
    }

    /** Barre de progression en laiton, creuse. {@code fraction} entre 0 et 1. */
    public static void progress(GuiGraphics g, int x, int y, int w, int h, double fraction) {
        well(g, x, y, w, h);
        int inner = w - 4;
        int filled = (int) Math.round(inner * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            tile(g, TILE_BRASS, x + 2, y + 2, filled, h - 4);
            g.fill(x + 2, y + 2, x + 2 + filled, y + 3, BRASS_LIGHT);
            g.fill(x + 2, y + h - 3, x + 2 + filled, y + h - 2, BRASS_DARK);
        }
    }

    /** Barre de défilement : piste creuse et poignée de laiton. */
    public static void scrollBar(GuiGraphics g, int x, int y, int w, int h, ScrollModel model, boolean hover) {
        g.fill(x, y, x + w, y + h, WELL_EDGE);
        g.fill(x + 1, y, x + w - 1, y + h, WELL);
        if (!model.scrollable()) {
            return;
        }
        int top = y + model.thumbTop(h);
        int height = model.thumbHeight(h);
        tile(g, TILE_BRASS, x, top, w, height);
        if (hover) {
            g.fill(x, top, x + w, top + height, 0x40FFE9B0);
        }
        g.fill(x, top, x + w, top + 1, BRASS_LIGHT);
        g.fill(x, top + height - 1, x + w, top + height, BRASS_DARK);
    }

    /** Le texte coupé par « ... » s'il dépasse {@code width} pixels. */
    public static String ellipsize(Font font, String text, int width) {
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

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Dessine un objet réduit au {@code scale} donné (0,75 : 12 pixels), pour les lignes serrées. */
    public static void item(GuiGraphics g, Font font, net.minecraft.world.item.ItemStack stack, int x, int y, float scale,
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
