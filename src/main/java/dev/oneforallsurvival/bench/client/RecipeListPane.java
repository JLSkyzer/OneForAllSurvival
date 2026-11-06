package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.BenchRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * La liste à défilement virtuel : seules les lignes visibles sont dessinées et interrogées, car le
 * catalogue dépasse mille recettes. Les noms viennent du cache de {@link BenchClientState}.
 */
final class RecipeListPane extends BenchPane {

    static final int ROW = 17;

    private final EngineerBenchScreen screen;
    private final Font font;
    private final BenchScrollBar scroll = new BenchScrollBar();
    private List<BenchRecipe> items = List.of();

    RecipeListPane(EngineerBenchScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
    }

    /** Remplace la liste montrée ; {@code keepScroll} garde la position (sinon retour en haut). */
    void setItems(List<BenchRecipe> items, boolean keepScroll) {
        this.items = items;
        double first = keepScroll ? scroll.model.first() : 0;
        scroll.model.setSize(items.size(), scroll.model.visible());
        scroll.model.scrollTo(first);
    }

    /** Fait défiler jusqu'à l'index de la recette dans la liste montrée, s'il y est. */
    void reveal(BenchRecipe recipe) {
        int index = indexOf(recipe);
        if (index >= 0) {
            scroll.model.reveal(index);
        }
    }

    int indexOf(BenchRecipe recipe) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == recipe) {
                return i;
            }
        }
        return -1;
    }

    List<BenchRecipe> items() {
        return items;
    }

    /** Déplace la sélection de {@code delta} lignes (clavier), en restant dans la liste. */
    void moveSelection(int delta) {
        if (items.isEmpty()) {
            return;
        }
        int current = indexOf(screen.selected());
        int next = current < 0 ? (delta > 0 ? 0 : items.size() - 1) : Math.max(0, Math.min(items.size() - 1, current + delta));
        screen.select(items.get(next));
        scroll.model.reveal(next);
    }

    void page(int direction) {
        moveSelection(direction * scroll.model.visible());
    }

    @Override
    protected void onPlaced() {
        int rows = Math.max(1, (h - 4) / ROW);
        scroll.model.setSize(items.size(), rows);
        scroll.place(x + w - BenchScrollBar.WIDTH - 2, y + 2, rows * ROW);
    }

    private int rowAt(double mx, double my) {
        int rel = (int) Math.floor((my - (y + 2)) / ROW);
        if (mx < x + 2 || mx >= x + w - BenchScrollBar.WIDTH - 3 || my < y + 2 || rel < 0 || rel >= scroll.model.visible()) {
            return -1;
        }
        int index = scroll.model.first() + rel;
        return index < items.size() ? index : -1;
    }

    @Override
    void render(GuiGraphics g, int mx, int my) {
        BenchTheme.well(g, x, y, w, h);
        int rowW = w - 4 - BenchScrollBar.WIDTH - 1;
        if (items.isEmpty()) {
            String key = BenchClientState.catalog().isEmpty() ? "gui.oneforallsurvival.bench.loading"
                    : "gui.oneforallsurvival.bench.no_results";
            String text = Component.translatable(key).getString();
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), w - 16);
            int ty = y + h / 2 - lines.size() * 5;
            for (var line : lines) {
                g.drawString(font, line, x + (w - font.width(line)) / 2, ty, BenchTheme.TEXT_DIM, false);
                ty += 10;
            }
        }
        int first = scroll.model.first();
        int hovered = rowAt(mx, my);
        for (int i = 0; i < scroll.model.visible() && first + i < items.size(); i++) {
            BenchRecipe recipe = items.get(first + i);
            int ry = y + 2 + i * ROW;
            boolean selected = recipe == screen.selected();
            if (selected) {
                g.fill(x + 2, ry, x + 2 + rowW, ry + ROW, BenchTheme.ROW_SELECTED);
                g.fill(x + 2, ry, x + 4, ry + ROW, BenchTheme.BRASS);
            } else if (first + i == hovered) {
                g.fill(x + 2, ry, x + 2 + rowW, ry + ROW, BenchTheme.ROW_HOVER);
            }
            g.renderItem(recipe.result(), x + 5, ry);
            boolean affordable = screen.affordable(recipe);
            int dot = affordable ? 9 : 0;
            String name = BenchTheme.ellipsize(font, BenchClientState.displayName(recipe), rowW - 24 - dot - 2);
            g.drawString(font, name, x + 25, ry + 5, selected ? BenchTheme.BRASS_LIGHT : BenchTheme.TEXT, false);
            if (affordable) {
                int dx = x + 2 + rowW - 8;
                g.fill(dx, ry + 6, dx + 5, ry + 11, BenchTheme.METAL_DARK);
                g.fill(dx + 1, ry + 7, dx + 4, ry + 10, BenchTheme.TEXT_OK);
            }
        }
        scroll.render(g, mx, my);
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        if (scroll.mouseClicked(mx, my)) {
            return true;
        }
        int index = button == 0 ? rowAt(mx, my) : -1;
        if (index >= 0) {
            screen.select(items.get(index));
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
        scroll.model.scrollBy(-delta * 2);
        return true;
    }

    @Override
    boolean tooltip(GuiGraphics g, int mx, int my) {
        int index = rowAt(mx, my);
        if (index < 0) {
            return false;
        }
        BenchRecipe recipe = items.get(index);
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), recipe.result()));
        lines.add(Component.translatable("gui.oneforallsurvival.bench.time", BenchFormat.duration(recipe.seconds()))
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        g.renderComponentTooltip(font, lines, mx, my);
        return true;
    }
}
