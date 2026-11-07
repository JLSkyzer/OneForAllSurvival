package dev.oneforallsurvival.food.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.food.DesalinatorBlockEntity;
import dev.oneforallsurvival.food.DesalinatorMenu;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * L'écran du dessalinisateur : deux jauges (l'eau à traiter à gauche, l'eau potable à droite), les trois cases, la
 * barre du cycle, la barre de FE et l'état en toutes lettres. Il reprend le décor de l'établi ({@link BenchTheme}).
 * Les nombres viennent du serveur par le menu ; le survol d'une jauge donne le détail en mB.
 */
public final class DesalinatorScreen extends AbstractContainerScreen<DesalinatorMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 182;
    private static final int GAUGE_W = 16;
    private static final int GAUGE_H = 64;
    private static final int GAUGE_Y = 18;
    private static final int WATER_X = 8;
    private static final int POTABLE_X = WIDTH - 8 - GAUGE_W;
    /** Teinte de l'eau à traiter dans sa jauge : le bleu de l'eau vanilla. */
    private static final int WATER_TINT = 0xFF3F76E4;
    private static final int TEXT_X = 34;
    private static final int STATE_Y = 17;
    private static final int CYCLE_X = TEXT_X;
    private static final int CYCLE_Y = 50;
    private static final int CYCLE_W = 84;
    private static final int CYCLE_H = 9;
    private static final int ENERGY_Y = 71;
    private static final int ENERGY_W = 50;
    private static final int ENERGY_H = 9;
    /** Bord droit du texte avant la jauge d'eau potable. */
    private static final int RIGHT = POTABLE_X - 4;

    public DesalinatorScreen(DesalinatorMenu menu, Inventory inventory, Component title) {
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
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.desalinator.energy", menu.energy(),
                    menu.capacity()), mx, my);
        } else if (BenchTheme.inside(mx, my, leftPos + WATER_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H)) {
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.desalinator.water",
                    menu.waterAmount(), DesalinatorBlockEntity.TANK_CAPACITY), mx, my);
        } else if (BenchTheme.inside(mx, my, leftPos + POTABLE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H)) {
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.desalinator.potable",
                    menu.potableAmount(), DesalinatorBlockEntity.TANK_CAPACITY), mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            BenchTheme.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        gauge(g, leftPos + WATER_X, topPos + GAUGE_Y, menu.waterAmount(), WATER_TINT);
        gauge(g, leftPos + POTABLE_X, topPos + GAUGE_Y, menu.potableAmount(), PotableWaterRendering.TINT);
        arrow(g, leftPos + DesalinatorMenu.EMPTY_X + 24, topPos + DesalinatorMenu.EMPTY_Y + 7);
        double cycle = menu.cycleLength() <= 0 ? 0 : (double) menu.progress() / menu.cycleLength();
        BenchTheme.progress(g, leftPos + CYCLE_X, topPos + CYCLE_Y, CYCLE_W, CYCLE_H, cycle);
        double energy = menu.capacity() <= 0 ? 0 : (double) menu.energy() / menu.capacity();
        BenchTheme.progress(g, leftPos + TEXT_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H, energy);
    }

    /** Une jauge verticale creuse, remplie par le bas ; une graduation tous les quarts. */
    private static void gauge(GuiGraphics g, int x, int y, int amount, int tint) {
        BenchTheme.well(g, x, y, GAUGE_W, GAUGE_H);
        int inner = GAUGE_H - 2;
        double fraction = Math.max(0, Math.min(1, (double) amount / DesalinatorBlockEntity.TANK_CAPACITY));
        int filled = (int) Math.round(inner * fraction);
        if (filled > 0) {
            g.fill(x + 1, y + 1 + inner - filled, x + GAUGE_W - 1, y + 1 + inner, tint);
        }
        for (int quarter = 1; quarter < 4; quarter++) {
            int ty = y + 1 + inner * quarter / 4;
            g.fill(x + GAUGE_W - 5, ty, x + GAUGE_W - 1, ty + 1, BenchTheme.METAL_LIGHT);
        }
    }

    /** Une flèche en laiton de la bouteille vide vers la pleine. */
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
        String stateText = Component.translatable("gui.oneforallsurvival.desalinator.state",
                Component.translatable("gui.oneforallsurvival.desalinator.state." + state.getSerializedName())).getString();
        g.drawString(font, BenchTheme.ellipsize(font, stateText, RIGHT - TEXT_X), TEXT_X, STATE_Y, color, false);
        int percent = menu.cycleLength() <= 0 ? 0
                : (int) Math.min(100, 100L * menu.progress() / menu.cycleLength());
        String cycleText = Component.translatable("gui.oneforallsurvival.desalinator.cycle", percent).getString();
        g.drawString(font, BenchTheme.ellipsize(font, cycleText, CYCLE_W), CYCLE_X, CYCLE_Y + CYCLE_H + 3,
                BenchTheme.TEXT_DIM, false);
        int energyX = TEXT_X + ENERGY_W + 6;
        // Le court seulement : la place à droite de la barre tient « 4000 FE », pas « 4000 / 4000 FE » ; le détail est
        // dans l'infobulle de la barre.
        String energy = Component.translatable("gui.oneforallsurvival.desalinator.energy.short", menu.energy())
                .getString();
        g.drawString(font, BenchTheme.ellipsize(font, energy, RIGHT - energyX), energyX, ENERGY_Y + 1,
                BenchTheme.TEXT_DIM, false);
        g.drawString(font, playerInventoryTitle, 8, DesalinatorMenu.INVENTORY_Y - 12, BenchTheme.TEXT_DIM, false);
    }
}
