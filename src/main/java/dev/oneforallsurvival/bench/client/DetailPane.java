package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.BenchOrders;
import dev.oneforallsurvival.bench.BenchRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Le détail de la recette choisie : résultat, temps, ingrédients (possédé / requis), quantité et bouton
 * Crafter. Les nombres d'inventaire sont recalculés quand l'inventaire, la recette ou la quantité changent.
 */
final class DetailPane extends BenchPane {

    private static final int PAD = 5;
    private static final int ING_ROW = 12;
    private static final int MAX_ING_ROWS = 4;
    private static final int BTN_H = 14;
    private static final int CRAFT_H_FULL = 16;
    private static final int CRAFT_H_COMPACT = 14;
    /** Une seconde et demie par objet : assez lent pour être lu, assez vif pour qu'on voie qu'ils défilent. */
    private static final long CYCLE_MS = 1200;

    private final EngineerBenchScreen screen;
    private final Font font;
    private final BenchScrollBar scroll = new BenchScrollBar();

    private BenchRecipe cachedRecipe;
    private int cachedSignature;
    private List<BenchCost.Line> lines = List.of();
    private int maxUnits;

    // Zones cliquables, posées au dessin.
    private int minusX;
    private int plusX;
    private int maxX;
    private int maxW;
    private int qtyX;
    private int qtyW;
    private int ingRows = MAX_ING_ROWS;
    private int ctlY;
    private int craftY;

    DetailPane(EngineerBenchScreen screen, Font font) {
        this.screen = screen;
        this.font = font;
    }

    /** Centre du bouton Crafter, en pixels d'interface (pour la démonstration de développement). */
    int[] craftCenter() {
        return new int[] {x + w / 2, craftY + craftH() / 2};
    }

    int maxUnits() {
        return maxUnits;
    }

    /** Remet les nombres à jour si la recette ou l'inventaire ont bougé depuis le dernier appel. */
    void refresh(BenchRecipe recipe, List<ItemStack> inventory) {
        int signature = BenchCost.signature(inventory);
        if (recipe == cachedRecipe && signature == cachedSignature) {
            return;
        }
        if (recipe != cachedRecipe) {
            scroll.model.scrollTo(0);
        }
        cachedRecipe = recipe;
        cachedSignature = signature;
        if (recipe == null) {
            lines = List.of();
            maxUnits = 0;
            return;
        }
        lines = BenchCost.lines(recipe, inventory);
        maxUnits = BenchCost.maxUnits(recipe, inventory, BenchOrders.MAX_COUNT);
        scroll.model.setSize(lines.size(), ingRows);
    }

    @Override
    protected void onPlaced() {
        // Le nombre de lignes d'ingrédients suit la hauteur : au moins une, au plus quatre.
        int below = BTN_H + 3 + craftH() + PAD + 2;
        ingRows = Math.max(1, Math.min(MAX_ING_ROWS, (h - ingY() - below) / ING_ROW));
        scroll.model.setSize(lines.size(), ingRows);
        scroll.place(x + w - PAD - BenchScrollBar.WIDTH, y + ingY(), ingRows * ING_ROW);
        ctlY = y + h - PAD - craftH() - 3 - BTN_H;
        craftY = y + h - PAD - craftH();
    }

    private int ingY() {
        return screen.compact() ? 41 : 53;
    }

    private int craftH() {
        return screen.compact() ? CRAFT_H_COMPACT : CRAFT_H_FULL;
    }

    private boolean craftable() {
        BenchRecipe recipe = screen.selected();
        int quantity = screen.quantity();
        return recipe != null && quantity <= maxUnits && quantity <= screen.queueFree();
    }

    // --- Dessin ----------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, int mx, int my) {
        BenchTheme.well(g, x, y, w, h);
        BenchRecipe recipe = screen.selected();
        if (recipe == null) {
            drawEmpty(g);
            return;
        }
        int quantity = screen.quantity();
        long tick = System.currentTimeMillis() / CYCLE_MS;

        // En-tête : le résultat, son nom et son mod.
        g.renderItem(recipe.result(), x + PAD, y + PAD);
        g.renderItemDecorations(font, recipe.result(), x + PAD, y + PAD);
        int textX = x + PAD + 21;
        int textW = w - PAD * 2 - 21;
        g.drawString(font, BenchTheme.ellipsize(font, BenchClientState.displayName(recipe), textW), textX, y + PAD + 1,
                BenchTheme.BRASS_LIGHT, false);
        g.drawString(font, BenchTheme.ellipsize(font, modLabel(recipe), textW), textX, y + PAD + 11,
                BenchTheme.TEXT_DIM, false);
        BenchTheme.rule(g, x + PAD, y + 24, w - PAD * 2);

        // Le temps, à la main, puis au courant de l'établi.
        String base = BenchFormat.duration(recipe.seconds());
        String timeText = Component.translatable("gui.oneforallsurvival.bench.time", base).getString();
        g.drawString(font, timeText, x + PAD, y + 29, BenchTheme.TEXT, false);
        double powered = screen.poweredSpeed();
        if (powered > 0 && Math.abs(powered - screen.manualSpeed()) > 1e-9) {
            long seconds = Math.max(1, Math.round(recipe.seconds() * screen.manualSpeed() / powered));
            String fast = Component.translatable("gui.oneforallsurvival.bench.time_powered",
                    BenchFormat.speed(powered, Locale.getDefault()), BenchFormat.duration(seconds)).getString();
            if (font.width(timeText) + font.width(fast) + 8 <= w - PAD * 2) {
                g.drawString(font, fast, x + w - PAD - font.width(fast), y + 29, BenchTheme.TEXT_DIM, false);
            }
        }

        // Les ingrédients.
        if (screen.compact()) {
            BenchTheme.rule(g, x + PAD, y + 38, w - PAD * 2);
        } else {
            g.drawString(font, Component.translatable("gui.oneforallsurvival.bench.ingredients").getString(), x + PAD,
                    y + 39, BenchTheme.BRASS, false);
            BenchTheme.rule(g, x + PAD, y + 50, w - PAD * 2);
        }
        renderIngredients(g, mx, my, quantity, tick);

        // Quantité et bouton.
        renderControls(g, mx, my, quantity);
    }

    private void drawEmpty(GuiGraphics g) {
        String text = Component.translatable("gui.oneforallsurvival.bench.pick").getString();
        List<FormattedCharSequence> wrapped = font.split(Component.literal(text), w - 24);
        int ty = y + h / 2 - wrapped.size() * 5;
        for (FormattedCharSequence line : wrapped) {
            g.drawString(font, line, x + (w - font.width(line)) / 2, ty, BenchTheme.TEXT_DIM, false);
            ty += 10;
        }
    }

    private static String modLabel(BenchRecipe recipe) {
        String id = BenchClientState.modOf(recipe);
        return ModList.get().getModContainerById(id).map(c -> c.getModInfo().getDisplayName()).orElse(id);
    }

    private int ingredientNameWidth(int countWidth) {
        int bar = scroll.model.scrollable() ? BenchScrollBar.WIDTH + 3 : 0;
        return w - PAD * 2 - 14 - countWidth - 4 - bar;
    }

    private void renderIngredients(GuiGraphics g, int mx, int my, int quantity, long tick) {
        int first = scroll.model.first();
        int bar = scroll.model.scrollable() ? BenchScrollBar.WIDTH + 3 : 0;
        int right = x + w - PAD - bar;
        for (int i = 0; i < ingRows && first + i < lines.size(); i++) {
            BenchCost.Line line = lines.get(first + i);
            int ry = y + ingY() + i * ING_ROW;
            ItemStack shown = line.shown(tick);
            boolean missing = line.missing(quantity);
            if (BenchTheme.inside(mx, my, x + PAD, ry, right - x - PAD, ING_ROW)) {
                g.fill(x + PAD, ry, right, ry + ING_ROW, BenchTheme.ROW_HOVER);
            }
            BenchTheme.item(g, font, shown, x + PAD + 1, ry, 0.75F, false);
            String count = line.have() + " / " + line.needed(quantity);
            int countW = font.width(count);
            g.drawString(font, count, right - countW - 1, ry + 2, missing ? BenchTheme.TEXT_BAD : BenchTheme.TEXT_OK,
                    false);
            String name = BenchTheme.ellipsize(font, BenchClientState.nameOf(shown), ingredientNameWidth(countW));
            g.drawString(font, name, x + PAD + 15, ry + 2, missing ? BenchTheme.TEXT : BenchTheme.TEXT, false);
        }
        if (scroll.model.scrollable()) {
            scroll.render(g, mx, my);
        }
    }

    private void renderControls(GuiGraphics g, int mx, int my, int quantity) {
        // « Quantité », moins, valeur, plus, puis Max.
        // Étroit, le mot « Quantité » cède sa place aux boutons ; l'infobulle le dit.
        boolean showLabel = w >= 150;
        int labelW = 0;
        if (showLabel) {
            String label = Component.translatable("gui.oneforallsurvival.bench.quantity").getString();
            labelW = Math.min(font.width(label), 44);
            g.drawString(font, BenchTheme.ellipsize(font, label, 44), x + PAD, ctlY + 4, BenchTheme.TEXT_DIM, false);
        }
        minusX = x + PAD + (showLabel ? labelW + 4 : 0);
        qtyX = minusX + BTN_H - 1;
        qtyW = showLabel ? 26 : 22;
        plusX = qtyX + qtyW - 1;
        maxX = plusX + BTN_H + 3;
        maxW = x + w - PAD - maxX;
        BenchTheme.button(g, font, minusX, ctlY, BTN_H, BTN_H, "-", hovering(mx, my, minusX, ctlY, BTN_H, BTN_H),
                quantity > 1, false);
        BenchTheme.well(g, qtyX, ctlY, qtyW, BTN_H);
        String q = Integer.toString(quantity);
        g.drawString(font, q, qtyX + (qtyW - font.width(q)) / 2, ctlY + 4, BenchTheme.TEXT, false);
        BenchTheme.button(g, font, plusX, ctlY, BTN_H, BTN_H, "+", hovering(mx, my, plusX, ctlY, BTN_H, BTN_H),
                quantity < BenchOrders.MAX_COUNT, false);
        String max = Component.translatable("gui.oneforallsurvival.bench.max", maxUnits).getString();
        BenchTheme.button(g, font, maxX, ctlY, maxW, BTN_H, max, hovering(mx, my, maxX, ctlY, maxW, BTN_H),
                maxUnits > 0, false);

        boolean enabled = craftable();
        String craft = Component.translatable("gui.oneforallsurvival.bench.craft").getString();
        if (quantity > 1) {
            craft += " x" + quantity;
        }
        int craftX = x + PAD;
        int craftW = w - PAD * 2;
        BenchTheme.button(g, font, craftX, craftY, craftW, craftH(), craft,
                enabled && hovering(mx, my, craftX, craftY, craftW, craftH()), enabled, true);
    }

    private static boolean hovering(int mx, int my, int bx, int by, int bw, int bh) {
        return BenchTheme.inside(mx, my, bx, by, bw, bh);
    }

    // --- Souris -------------------------------------------------------------------------------------

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my) || screen.selected() == null) {
            return contains(mx, my);
        }
        if (scroll.mouseClicked(mx, my)) {
            return true;
        }
        if (button != 0) {
            return true;
        }
        if (hovering((int) mx, (int) my, minusX, ctlY, BTN_H, BTN_H)) {
            screen.setQuantity(screen.quantity() - (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 8 : 1));
        } else if (hovering((int) mx, (int) my, plusX, ctlY, BTN_H, BTN_H)) {
            screen.setQuantity(screen.quantity() + (Screen.hasShiftDown() ? 8 : 1));
        } else if (hovering((int) mx, (int) my, maxX, ctlY, maxW, BTN_H)) {
            screen.setQuantity(Math.max(1, Math.min(maxUnits, screen.queueFree())));
        } else if (hovering((int) mx, (int) my, x + PAD, craftY, w - PAD * 2, craftH()) && craftable()) {
            screen.order();
        }
        return true;
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
        if (!contains(mx, my) || screen.selected() == null) {
            return false;
        }
        if (my >= ctlY && my < ctlY + BTN_H) {
            screen.setQuantity(screen.quantity() + (int) Math.signum(delta));
        } else {
            scroll.model.scrollBy(-delta);
        }
        return true;
    }

    // --- Infobulles ---------------------------------------------------------------------------------

    @Override
    boolean tooltip(GuiGraphics g, int mx, int my) {
        BenchRecipe recipe = screen.selected();
        if (recipe == null || !contains(mx, my)) {
            return false;
        }
        if (hovering(mx, my, x + PAD, y + PAD, w - PAD * 2, 18)) {
            List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), recipe.result()));
            g.renderComponentTooltip(font, tip, mx, my);
            return true;
        }
        int quantity = screen.quantity();
        if (hovering(mx, my, x + PAD, craftY, w - PAD * 2, craftH())) {
            g.renderComponentTooltip(font, craftTooltip(recipe, quantity), mx, my);
            return true;
        }
        if (hovering(mx, my, maxX, ctlY, maxW, BTN_H)) {
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.bench.max.tooltip", maxUnits,
                    Math.min(screen.queueFree(), BenchOrders.MAX_COUNT)), mx, my);
            return true;
        }
        if (hovering(mx, my, minusX, ctlY, plusX + BTN_H - minusX, BTN_H)) {
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.bench.quantity.tooltip"), mx, my);
            return true;
        }
        int first = scroll.model.first();
        int bar = scroll.model.scrollable() ? BenchScrollBar.WIDTH + 3 : 0;
        for (int i = 0; i < ingRows && first + i < lines.size(); i++) {
            int ry = y + ingY() + i * ING_ROW;
            if (hovering(mx, my, x + PAD, ry, w - PAD * 2 - bar, ING_ROW)) {
                BenchCost.Line line = lines.get(first + i);
                ItemStack shown = line.shown(System.currentTimeMillis() / CYCLE_MS);
                List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), shown));
                tip.add(Component.translatable("gui.oneforallsurvival.bench.have_need", line.have(),
                        line.needed(quantity)).withStyle(line.missing(quantity) ? ChatFormatting.RED
                                : ChatFormatting.GREEN));
                if (line.choices().size() > 1) {
                    tip.add(Component.translatable("gui.oneforallsurvival.bench.any_of", line.choices().size())
                            .withStyle(ChatFormatting.GRAY));
                }
                g.renderComponentTooltip(font, tip, mx, my);
                return true;
            }
        }
        return false;
    }

    /** Pourquoi le bouton est grisé, ou ce qu'il fera. */
    private List<Component> craftTooltip(BenchRecipe recipe, int quantity) {
        List<Component> tip = new ArrayList<>();
        if (screen.queueFree() <= 0) {
            tip.add(Component.translatable("gui.oneforallsurvival.bench.craft.queue_full", screen.queueSize(),
                    screen.queueMax()).withStyle(ChatFormatting.RED));
        } else if (quantity > maxUnits) {
            tip.add(Component.translatable("gui.oneforallsurvival.bench.craft.missing").withStyle(ChatFormatting.RED));
            boolean any = false;
            for (BenchCost.Line line : lines) {
                if (line.missing(quantity)) {
                    any = true;
                    tip.add(Component.literal(" - " + (line.needed(quantity) - line.have()) + " x "
                            + BenchClientState.nameOf(line.shown(System.currentTimeMillis() / CYCLE_MS)))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            if (!any) {
                tip.add(Component.translatable("gui.oneforallsurvival.bench.craft.shared")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (maxUnits > 0) {
                tip.add(Component.translatable("gui.oneforallsurvival.bench.craft.max_hint", maxUnits)
                        .withStyle(ChatFormatting.GOLD));
            }
        } else if (quantity > screen.queueFree()) {
            tip.add(Component.translatable("gui.oneforallsurvival.bench.craft.queue_room", screen.queueFree())
                    .withStyle(ChatFormatting.RED));
        } else {
            tip.add(Component.translatable("gui.oneforallsurvival.bench.craft.ok", quantity)
                    .withStyle(ChatFormatting.GREEN));
            tip.add(Component.translatable("gui.oneforallsurvival.bench.craft.total_time",
                    BenchFormat.duration((long) recipe.seconds() * quantity)).withStyle(ChatFormatting.GRAY));
        }
        return tip;
    }
}
