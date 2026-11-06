package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.BenchRecipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

/** Les onglets de catégories : « Tout », puis un par mod, du plus fourni au moins fourni. */
final class CategoryPane extends BenchPane {

    private static final int ROW = 14;

    /** Un onglet : {@code mod} vide pour « Tout ». */
    record Tab(String mod, String label, String fullName, int count) {}

    private final EngineerBenchScreen screen;
    private final Font font;
    private final BenchScrollBar scroll = new BenchScrollBar();
    private List<Tab> tabs = List.of();
    private List<BenchRecipe> builtFor;
    private int preferred = 60;

    CategoryPane(EngineerBenchScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
    }

    /** Reconstruit les onglets quand le catalogue a changé. */
    void rebuild(List<BenchRecipe> catalog) {
        if (catalog == builtFor) {
            return;
        }
        builtFor = catalog;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BenchRecipe recipe : catalog) {
            counts.merge(BenchClientState.modOf(recipe), 1, Integer::sum);
        }
        List<Tab> mods = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            mods.add(new Tab(e.getKey(), shortLabel(e.getKey()), modName(e.getKey()), e.getValue()));
        }
        mods.sort(Comparator.comparingInt(Tab::count).reversed().thenComparing(Tab::label));
        List<Tab> all = new ArrayList<>();
        String allLabel = Component.translatable("gui.oneforallsurvival.bench.all").getString();
        all.add(new Tab("", allLabel, allLabel, catalog.size()));
        all.addAll(mods);
        tabs = all;
        // Largeur voulue : le plus long libellé et le plus grand compteur, avec les marges.
        int widest = 0;
        int widestCount = 0;
        for (Tab tab : tabs) {
            widest = Math.max(widest, font.width(tab.label()));
            widestCount = Math.max(widestCount, font.width(Integer.toString(tab.count())));
        }
        preferred = widest + widestCount + 7 + 6 + 4 + 2;
        scroll.model.setSize(tabs.size(), scroll.model.visible());
    }

    /** Largeur qui laisse lire tous les libellés avec leur compteur. */
    int preferredWidth() {
        return preferred;
    }

    /**
     * Libellé court d'un mod : la clé {@code bench.oneforallsurvival.mod.<modid>} s'il existe (les noms des
     * mods du pack sont trop longs pour un onglet), sinon le nom du mod.
     */
    private static String shortLabel(String modId) {
        String key = "bench.oneforallsurvival.mod." + modId;
        return I18n.exists(key) ? I18n.get(key) : modName(modId);
    }

    private static String modName(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(modId);
    }

    @Override
    protected void onPlaced() {
        int rows = Math.max(1, (h - 4) / ROW);
        scroll.model.setSize(tabs.size(), rows);
        scroll.place(x + w - BenchScrollBar.WIDTH - 2, y + 2, rows * ROW);
    }

    private int tabAt(double mx, double my) {
        int rows = scroll.model.visible();
        int rel = (int) Math.floor((my - (y + 2)) / ROW);
        if (mx < x + 2 || mx >= x + w - 2 || my < y + 2 || rel < 0 || rel >= rows) {
            return -1;
        }
        int index = scroll.model.first() + rel;
        return index < tabs.size() ? index : -1;
    }

    @Override
    void render(GuiGraphics g, int mx, int my) {
        BenchTheme.well(g, x, y, w, h);
        int first = scroll.model.first();
        int hovered = tabAt(mx, my);
        int rowW = w - 4 - (scroll.model.scrollable() ? BenchScrollBar.WIDTH + 1 : 0);
        for (int i = 0; i < scroll.model.visible() && first + i < tabs.size(); i++) {
            Tab tab = tabs.get(first + i);
            int ry = y + 2 + i * ROW;
            boolean selected = tab.mod().equals(screen.category());
            if (selected) {
                g.fill(x + 2, ry, x + 2 + rowW, ry + ROW, BenchTheme.ROW_SELECTED);
                g.fill(x + 2, ry, x + 4, ry + ROW, BenchTheme.BRASS);
            } else if (first + i == hovered) {
                g.fill(x + 2, ry, x + 2 + rowW, ry + ROW, BenchTheme.ROW_HOVER);
            }
            String count = Integer.toString(tab.count());
            int countW = font.width(count);
            String label = BenchTheme.ellipsize(font, tab.label(), rowW - 8 - countW - 4);
            int color = selected ? BenchTheme.BRASS_LIGHT : BenchTheme.TEXT;
            g.drawString(font, label, x + 7, ry + 3, color, false);
            g.drawString(font, count, x + 2 + rowW - countW - 2, ry + 3, BenchTheme.TEXT_DIM, false);
        }
        if (scroll.model.scrollable()) {
            scroll.render(g, mx, my);
        }
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        if (scroll.mouseClicked(mx, my)) {
            return true;
        }
        int index = button == 0 ? tabAt(mx, my) : -1;
        if (index >= 0) {
            screen.setCategory(tabs.get(index).mod());
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
        int index = tabAt(mx, my);
        if (index < 0) {
            return false;
        }
        Tab tab = tabs.get(index);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(tab.fullName()));
        lines.add(Component.translatable("gui.oneforallsurvival.bench.tab.count", tab.count())
                .withStyle(ChatFormatting.GRAY));
        g.renderComponentTooltip(font, lines, mx, my);
        return true;
    }
}
