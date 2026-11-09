package dev.oneforallsurvival.food.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.food.IncubatorBlockEntity;
import dev.oneforallsurvival.food.IncubatorMenu;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * L'écran de la couveuse : quatre places avec une barre de progression sous chacune, la barre de FE, l'état en toutes
 * lettres et, seulement en coupure, le décompte avant la perte de la couvée. Il reprend le décor de l'établi
 * ({@link BenchTheme}). Les nombres viennent du serveur par le menu.
 */
public final class IncubatorScreen extends AbstractContainerScreen<IncubatorMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 180;
    private static final int BAR_W = 26;
    private static final int BAR_H = 5;
    private static final int BAR_Y = IncubatorMenu.SLOT_Y + 22;
    private static final int STATE_Y = 52;
    private static final int OUTAGE_Y = 62;
    private static final int ENERGY_X = 36;
    private static final int ENERGY_Y = 72;
    private static final int ENERGY_W = 50;
    private static final int ENERGY_H = 9;
    /** Bord droit du texte : la marge de 8 pixels du cadre. */
    private static final int RIGHT = WIDTH - 8;

    public IncubatorScreen(IncubatorMenu menu, Inventory inventory, Component title) {
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
        for (int i = 0; i < IncubatorBlockEntity.SLOTS; i++) {
            double fraction = menu.total() <= 0 ? 0 : (double) menu.progress(i) / menu.total();
            BenchTheme.progress(g, leftPos + barX(i), topPos + BAR_Y, BAR_W, BAR_H, fraction);
        }
        double energy = menu.capacity() <= 0 ? 0 : (double) menu.energy() / menu.capacity();
        BenchTheme.progress(g, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H, energy);
    }

    /** La barre d'une place, centrée sous sa case de 18 pixels. */
    private static int barX(int slot) {
        return IncubatorMenu.SLOT_X + slot * IncubatorMenu.SLOT_STEP + (18 - BAR_W) / 2;
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
        label(g, Component.translatable("gui.oneforallsurvival.incubator.state",
                Component.translatable("gui.oneforallsurvival.incubator.state." + state.getSerializedName())),
                8, STATE_Y, color);
        // Le décompte n'apparaît qu'en coupure : sans courant et avec le compteur en marche.
        if (state == MachineState.NO_POWER && menu.outage() > 0) {
            int seconds = Math.max(0, (menu.outageTotal() - menu.outage() + 19) / 20);
            label(g, Component.translatable("gui.oneforallsurvival.incubator.outage", seconds), 8, OUTAGE_Y,
                    BenchTheme.TEXT_BAD);
        }
        // Le texte d'énergie s'aligne à droite, dans la marge, et se coupe avant la barre plutôt que de déborder.
        String energy = Component.translatable("gui.oneforallsurvival.incubator.energy", menu.energy(),
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
