package dev.oneforallsurvival.town.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.food.client.PotableWaterRendering;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.town.WaterHeaterBlockEntity;
import dev.oneforallsurvival.town.WaterHeaterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * L'écran du chauffe-eau : deux jauges (l'eau potable à gauche, l'eau chaude à droite), la barre de FE et l'état en
 * toutes lettres. Il reprend le décor de l'établi ({@link BenchTheme}). Les nombres viennent du serveur par le menu ;
 * le survol d'une jauge donne le détail en mB.
 */
public final class WaterHeaterScreen extends AbstractContainerScreen<WaterHeaterMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 182;
    private static final int GAUGE_W = 16;
    private static final int GAUGE_H = 64;
    private static final int GAUGE_Y = 18;
    private static final int POTABLE_X = 8;
    private static final int HOT_X = WIDTH - 8 - GAUGE_W;
    private static final int TEXT_X = 34;
    private static final int STATE_Y = 24;
    private static final int ENERGY_Y = 56;
    private static final int ENERGY_W = 50;
    private static final int ENERGY_H = 9;
    /** Bord droit du texte avant la jauge d'eau chaude. */
    private static final int RIGHT = HOT_X - 4;

    public WaterHeaterScreen(WaterHeaterMenu menu, Inventory inventory, Component title) {
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
        if (BenchTheme.inside(mx, my, leftPos + TEXT_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H)) {
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.water_heater.energy", menu.energy(),
                    menu.capacity()), mx, my);
        } else if (BenchTheme.inside(mx, my, leftPos + POTABLE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H)) {
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.water_heater.potable",
                    menu.potableAmount(), WaterHeaterBlockEntity.TANK_CAPACITY), mx, my);
        } else if (BenchTheme.inside(mx, my, leftPos + HOT_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H)) {
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.water_heater.hot",
                    menu.hotAmount(), WaterHeaterBlockEntity.TANK_CAPACITY), mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            BenchTheme.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        gauge(g, leftPos + POTABLE_X, topPos + GAUGE_Y, menu.potableAmount(), PotableWaterRendering.TINT);
        gauge(g, leftPos + HOT_X, topPos + GAUGE_Y, menu.hotAmount(), HotWaterRendering.TINT);
        arrow(g, leftPos + TEXT_X + 12, topPos + GAUGE_Y + 36);
        double energy = menu.capacity() <= 0 ? 0 : (double) menu.energy() / menu.capacity();
        BenchTheme.progress(g, leftPos + TEXT_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H, energy);
    }

    /** Une jauge verticale creuse, remplie par le bas ; une graduation tous les quarts. */
    private static void gauge(GuiGraphics g, int x, int y, int amount, int tint) {
        BenchTheme.well(g, x, y, GAUGE_W, GAUGE_H);
        int inner = GAUGE_H - 2;
        double fraction = Math.max(0, Math.min(1, (double) amount / WaterHeaterBlockEntity.TANK_CAPACITY));
        int filled = (int) Math.round(inner * fraction);
        if (filled > 0) {
            g.fill(x + 1, y + 1 + inner - filled, x + GAUGE_W - 1, y + 1 + inner, tint);
        }
        for (int quarter = 1; quarter < 4; quarter++) {
            int ty = y + 1 + inner * quarter / 4;
            g.fill(x + GAUGE_W - 5, ty, x + GAUGE_W - 1, ty + 1, BenchTheme.METAL_LIGHT);
        }
    }

    /** Une flèche en laiton de l'eau potable vers l'eau chaude. */
    private static void arrow(GuiGraphics g, int x, int y) {
        g.fill(x, y + 1, x + 24, y + 3, BenchTheme.BRASS_DARK);
        g.fill(x + 20, y - 1, x + 22, y + 5, BenchTheme.BRASS_DARK);
        g.fill(x + 22, y, x + 24, y + 4, BenchTheme.BRASS_DARK);
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
        String stateText = Component.translatable("gui.oneforallsurvival.water_heater.state",
                Component.translatable("gui.oneforallsurvival.water_heater.state." + state.getSerializedName())).getString();
        g.drawString(font, BenchTheme.ellipsize(font, stateText, RIGHT - TEXT_X), TEXT_X, STATE_Y, color, false);
        int energyX = TEXT_X + ENERGY_W + 6;
        // Le court seulement : le détail est dans l'infobulle de la barre.
        String energy = Component.translatable("gui.oneforallsurvival.water_heater.energy.short", menu.energy())
                .getString();
        g.drawString(font, BenchTheme.ellipsize(font, energy, RIGHT - energyX), energyX, ENERGY_Y + 1,
                BenchTheme.TEXT_DIM, false);
        g.drawString(font, playerInventoryTitle, 8, WaterHeaterMenu.INVENTORY_Y - 12, BenchTheme.TEXT_DIM, false);
    }
}
