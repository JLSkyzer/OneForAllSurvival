package dev.oneforallsurvival.food.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.food.CanteenPotMenu;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * L'écran de la marmite de cantine : six cases d'ingrédients, le récipient, la sortie, une flèche de progression,
 * la barre de FE, l'état en toutes lettres et, seulement s'il en manque, le nombre de récipients requis. Il reprend
 * le décor de l'établi ({@link BenchTheme}). Les nombres viennent du serveur par le menu.
 */
public final class CanteenPotScreen extends AbstractContainerScreen<CanteenPotMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 180;
    private static final int ARROW_X = 88;
    private static final int ARROW_Y = 30;
    private static final int ARROW_W = 34;
    private static final int ARROW_H = 10;
    private static final int STATE_X = 88;
    private static final int STATE_Y = 44;
    private static final int MISSING_Y = 54;
    private static final int ENERGY_X = 50;
    private static final int ENERGY_Y = 64;
    private static final int ENERGY_W = 40;
    private static final int ENERGY_H = 9;
    /** Bord droit du texte : la marge de 8 pixels du cadre. */
    private static final int RIGHT = WIDTH - 8;

    public CanteenPotScreen(CanteenPotMenu menu, Inventory inventory, Component title) {
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
        MachineState[] states = MachineState.values();
        MachineState state = states[Math.floorMod(menu.stateOrdinal(), states.length)];
        int color = switch (state) {
            case RUNNING -> BenchTheme.TEXT_OK;
            case FAULT -> BenchTheme.TEXT_BAD;
            default -> BenchTheme.TEXT;
        };
        label(g, Component.translatable("gui.oneforallsurvival.canteen_pot.state",
                Component.translatable("gui.oneforallsurvival.canteen_pot.state." + state.getSerializedName())),
                STATE_X, STATE_Y, color);
        if (menu.containersMissing() > 0) {
            label(g, Component.translatable("gui.oneforallsurvival.canteen_pot.containers", menu.containersNeeded()),
                    STATE_X, MISSING_Y, BenchTheme.TEXT_BAD);
        }
        // Le texte d'énergie s'aligne à droite, dans la marge, et se coupe avant la barre plutôt que de déborder.
        String energy = Component.translatable("gui.oneforallsurvival.canteen_pot.energy", menu.energy(),
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
