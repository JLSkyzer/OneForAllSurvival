package dev.oneforallsurvival.food.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.food.DryerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * L'écran du séchoir : trois cases, une flèche de progression, la barre de FE et l'état en toutes
 * lettres. Il reprend le décor de l'établi ({@link BenchTheme}). Les nombres viennent du serveur par le menu.
 */
public final class DryerScreen extends AbstractContainerScreen<DryerMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 180;
    private static final int ARROW_X = 62;
    private static final int ARROW_Y = 38;
    private static final int ARROW_W = 66;
    private static final int ARROW_H = 10;
    private static final int ENERGY_X = 36;
    private static final int ENERGY_Y = 72;
    private static final int ENERGY_W = 50;
    private static final int ENERGY_H = 9;
    /** Bord droit du texte : la marge de 8 pixels du cadre. */
    private static final int RIGHT = WIDTH - 8;

    public DryerScreen(DryerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            BenchTheme.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        double fraction = menu.total() <= 0 ? 0 : (double) menu.progress() / menu.total();
        BenchTheme.progress(g, leftPos + ARROW_X, topPos + ARROW_Y, ARROW_W, ARROW_H, fraction);
        arrowHead(g, leftPos + ARROW_X + ARROW_W + 1, topPos + ARROW_Y + ARROW_H / 2);
        double energy = menu.capacity() <= 0 ? 0 : (double) menu.energy() / menu.capacity();
        BenchTheme.progress(g, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H, energy);
    }

    /** Une pointe de flèche pleine, de cinq pixels de haut, dont la base touche la barre. */
    private static void arrowHead(GuiGraphics g, int x, int centerY) {
        for (int i = 0; i < 3; i++) {
            g.fill(x + i, centerY - 2 + i, x + i + 1, centerY + 3 - i, BenchTheme.BRASS);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, titleLabelY, BenchTheme.TEXT, false);
        label(g, Component.translatable("gui.oneforallsurvival.dryer.salt"),
                DryerMenu.SALT_X + 22, DryerMenu.SALT_Y + 4, BenchTheme.TEXT_DIM);
        label(g, Component.translatable("gui.oneforallsurvival.dryer.input"),
                DryerMenu.INPUT_X + 22, DryerMenu.INPUT_Y + 4, BenchTheme.TEXT_DIM);
        MachineState[] states = MachineState.values();
        MachineState state = states[Math.floorMod(menu.stateOrdinal(), states.length)];
        int color = switch (state) {
            case RUNNING -> BenchTheme.TEXT_OK;
            case FAULT -> BenchTheme.TEXT_BAD;
            default -> BenchTheme.TEXT;
        };
        label(g, Component.translatable("gui.oneforallsurvival.dryer.state",
                Component.translatable("gui.oneforallsurvival.dryer.state." + state.getSerializedName())),
                ARROW_X, ARROW_Y + 24, color);
        // Le texte d'énergie s'aligne à droite, dans la marge, et se coupe avant la barre plutôt que de déborder.
        String energy = Component.translatable("gui.oneforallsurvival.dryer.energy", menu.energy(),
                menu.capacity()).getString();
        int minX = ENERGY_X + ENERGY_W + 4;
        int x = Math.max(minX, RIGHT - font.width(energy));
        g.drawString(font, BenchTheme.ellipsize(font, energy, RIGHT - x), x, ENERGY_Y + 1, BenchTheme.TEXT_DIM, false);
        g.drawString(font, playerInventoryTitle, 8, 86, BenchTheme.TEXT_DIM, false);
    }

    /** Un texte posé en ({@code x}, {@code y}), coupé avant la marge de droite. */
    private void label(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, BenchTheme.ellipsize(font, text.getString(), RIGHT - x), x, y, color, false);
    }
}
