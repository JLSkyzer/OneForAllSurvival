package dev.oneforallsurvival.grid.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.grid.LoadShedderBlockEntity;
import dev.oneforallsurvival.grid.LoadShedderMenu;
import dev.oneforallsurvival.grid.OutputStatus;
import dev.oneforallsurvival.machine.MachineState;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * L'écran du tableau de délestage, au décor de l'établi ({@link BenchTheme}) : une ligne par sortie, dans l'ordre où
 * elles sont servies, avec son rang, son nom, son état en couleur, ses FE du dernier tick et deux boutons pour la
 * monter ou la descendre d'un rang. Les nombres viennent du serveur par le menu ; un clic n'envoie qu'un numéro de
 * bouton, que le serveur applique.
 */
public final class LoadShedderScreen extends AbstractContainerScreen<LoadShedderMenu> {

    private static final String GUI = "gui.oneforallsurvival.load_shedder.";
    private static final String[] OUTPUT_KEYS = {"front", "left", "right", "top"};

    private static final int WIDTH = 230;
    private static final int HEIGHT = 124;
    private static final int STATE_Y = 18;
    private static final int HEADER_Y = 34;
    private static final int ROWS_Y = 46;
    private static final int ROW_H = 18;
    private static final int RANK_X = 10;
    private static final int NAME_X = 24;
    private static final int STATUS_X = 68;
    private static final int STATUS_W = 80;
    private static final int FE_RIGHT = 196;
    private static final int FE_W = 44;
    private static final int UP_X = 200;
    private static final int DOWN_X = 214;
    private static final int BUTTON = 12;

    public LoadShedderScreen(LoadShedderMenu menu, Inventory inventory, Component title) {
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

    /** Les sorties dans l'ordre de leurs rangs, la première servie en haut. */
    private Integer[] rows() {
        Integer[] outputs = {0, 1, 2, 3};
        Arrays.sort(outputs, Comparator.comparingInt(menu::rank));
        return outputs;
    }

    private static int rowY(int row) {
        return ROWS_Y + row * ROW_H;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        renderTooltip(g, mx, my);
        for (int row = 0; row < LoadShedderBlockEntity.OUTPUTS; row++) {
            int y = topPos + rowY(row);
            if (BenchTheme.inside(mx, my, leftPos + UP_X, y, BUTTON, BUTTON) && row > 0) {
                g.renderTooltip(font, Component.translatable(GUI + "up"), mx, my);
            } else if (BenchTheme.inside(mx, my, leftPos + DOWN_X, y, BUTTON, BUTTON)
                    && row < LoadShedderBlockEntity.OUTPUTS - 1) {
                g.renderTooltip(font, Component.translatable(GUI + "down"), mx, my);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rule(g, leftPos + 8, topPos + HEADER_Y + 10, imageWidth - 16);
        for (int row = 0; row < LoadShedderBlockEntity.OUTPUTS; row++) {
            int y = topPos + rowY(row);
            boolean canUp = row > 0;
            boolean canDown = row < LoadShedderBlockEntity.OUTPUTS - 1;
            int upX = leftPos + UP_X;
            int downX = leftPos + DOWN_X;
            BenchTheme.button(g, font, upX, y, BUTTON, BUTTON, "", canUp && BenchTheme.inside(mx, my, upX, y, BUTTON,
                    BUTTON), canUp, false);
            BenchTheme.button(g, font, downX, y, BUTTON, BUTTON, "", canDown && BenchTheme.inside(mx, my, downX, y,
                    BUTTON, BUTTON), canDown, false);
            arrow(g, upX, y, true, canUp ? BenchTheme.TEXT : BenchTheme.TEXT_DISABLED);
            arrow(g, downX, y, false, canDown ? BenchTheme.TEXT : BenchTheme.TEXT_DISABLED);
        }
    }

    /** Un petit triangle dessiné au pixel au centre d'un bouton : pas de glyphe qui dépende de la police. */
    private static void arrow(GuiGraphics g, int x, int y, boolean up, int color) {
        int cx = x + BUTTON / 2;
        int top = y + 4;
        for (int i = 0; i < 4; i++) {
            int half = up ? i : 3 - i;
            g.fill(cx - half - 1, top + i, cx + half + 1, top + i + 1, color);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, titleLabelY, BenchTheme.TEXT, false);
        MachineState[] states = MachineState.values();
        MachineState state = states[Math.floorMod(menu.stateOrdinal(), states.length)];
        String stateText = Component.translatable(GUI + "state",
                Component.translatable(GUI + "state." + state.getSerializedName())).getString();
        g.drawString(font, BenchTheme.ellipsize(font, stateText, imageWidth - 16), 8, STATE_Y,
                state == MachineState.FAULT ? BenchTheme.TEXT_BAD : BenchTheme.TEXT_DIM, false);
        g.drawString(font, Component.translatable(GUI + "header.rank").getString(), RANK_X - 2, HEADER_Y,
                BenchTheme.TEXT_DIM, false);
        g.drawString(font, BenchTheme.ellipsize(font, Component.translatable(GUI + "header.output").getString(),
                STATUS_X - NAME_X - 4), NAME_X, HEADER_Y, BenchTheme.TEXT_DIM, false);
        g.drawString(font, BenchTheme.ellipsize(font, Component.translatable(GUI + "header.status").getString(),
                STATUS_W), STATUS_X, HEADER_Y, BenchTheme.TEXT_DIM, false);
        String feHeader = Component.translatable(GUI + "header.energy").getString();
        g.drawString(font, feHeader, FE_RIGHT - font.width(feHeader), HEADER_Y, BenchTheme.TEXT_DIM, false);
        Integer[] rows = rows();
        for (int row = 0; row < rows.length; row++) {
            int output = rows[row];
            int y = rowY(row) + 2;
            g.drawString(font, String.valueOf(row + 1), RANK_X, y, BenchTheme.BRASS_LIGHT, false);
            String name = Component.translatable(GUI + "output." + OUTPUT_KEYS[output]).getString();
            g.drawString(font, BenchTheme.ellipsize(font, name, STATUS_X - NAME_X - 4), NAME_X, y, BenchTheme.TEXT, false);
            OutputStatus status = menu.status(output);
            String statusText = Component.translatable(GUI + "status." + status.key()).getString();
            g.drawString(font, BenchTheme.ellipsize(font, statusText, STATUS_W), STATUS_X, y, color(status), false);
            String fe = BenchTheme.ellipsize(font,
                    Component.translatable(GUI + "energy", menu.delivered(output)).getString(), FE_W);
            g.drawString(font, fe, FE_RIGHT - font.width(fe), y, BenchTheme.TEXT_DIM, false);
        }
    }

    private static int color(OutputStatus status) {
        return switch (status) {
            case FED -> BenchTheme.TEXT_OK;
            case PARTIAL -> BenchTheme.BRASS_LIGHT;
            case CUT -> BenchTheme.TEXT_BAD;
            default -> BenchTheme.TEXT_DIM;
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            Integer[] rows = rows();
            for (int row = 0; row < rows.length; row++) {
                int y = topPos + rowY(row);
                boolean up = row > 0 && BenchTheme.inside(mx, my, leftPos + UP_X, y, BUTTON, BUTTON);
                boolean down = row < rows.length - 1 && BenchTheme.inside(mx, my, leftPos + DOWN_X, y, BUTTON, BUTTON);
                if (up || down) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gameMode != null) {
                        mc.gameMode.handleInventoryButtonClick(menu.containerId, LoadShedderMenu.buttonId(rows[row], up));
                    }
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
