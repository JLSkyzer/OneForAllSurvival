package dev.oneforallsurvival.food.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.food.FridgeMenu;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * L'écran de l'armoire : 27 cases, la barre de FE et l'état en toutes lettres. Il reprend le décor de l'établi
 * ({@link BenchTheme}). Les nombres viennent du serveur par le menu.
 */
public final class FridgeScreen extends AbstractContainerScreen<FridgeMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 182;
    private static final int ENERGY_X = 8;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_W = 50;
    private static final int ENERGY_H = 9;
    /** Bord droit du texte : la marge de 8 pixels du cadre. */
    private static final int RIGHT = WIDTH - 8;

    public FridgeScreen(FridgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 8;
        titleLabelY = 5;
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
        double energy = menu.capacity() <= 0 ? 0 : (double) menu.energy() / menu.capacity();
        BenchTheme.progress(g, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H, energy);
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
        int stateX = ENERGY_X + ENERGY_W + 6;
        String stateText = Component.translatable("gui.oneforallsurvival.fridge.state",
                Component.translatable("gui.oneforallsurvival.fridge.state." + state.getSerializedName())).getString();
        g.drawString(font, BenchTheme.ellipsize(font, stateText, RIGHT - stateX), stateX, ENERGY_Y + 1, color, false);
        // Le texte d'énergie sous la barre, coupé avant la marge plutôt que de déborder.
        String energy = Component.translatable("gui.oneforallsurvival.fridge.energy", menu.energy(),
                menu.capacity()).getString();
        g.drawString(font, BenchTheme.ellipsize(font, energy, RIGHT - ENERGY_X), ENERGY_X, ENERGY_Y + ENERGY_H + 3,
                BenchTheme.TEXT_DIM, false);
        g.drawString(font, playerInventoryTitle, 8, FridgeMenu.INVENTORY_Y - 12, BenchTheme.TEXT_DIM, false);
    }
}
