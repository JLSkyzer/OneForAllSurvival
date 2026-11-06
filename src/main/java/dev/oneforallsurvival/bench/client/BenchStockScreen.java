package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.BenchStockMenu;
import dev.oneforallsurvival.bench.OpenBenchPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * L'écran du stock : 6 lignes de 9 cases qui glissent sur une liste sans fin, l'inventaire en dessous, le
 * nombre de piles en attente, une barre de défilement et un bouton de retour à l'établi. La taille est
 * fixe (188 x 227 pixels d'interface), faite pour tenir dans 320 x 240. Le décor passe par {@link BenchTheme}.
 *
 * <p>Le défilement se décide ici, tout de suite, puis le serveur est prévenu par un bouton de menu ; il
 * renvoie le contenu des cases par la synchronisation vanille.
 */
public final class BenchStockScreen extends AbstractContainerScreen<BenchStockMenu> {

    private static final int WIDTH = 188;
    private static final int HEIGHT = 227;
    private static final int FRAME = 5;
    private static final int TITLE_H = 14;
    private static final int BACK_W = 54;
    private static final int COUNT_Y = 135;

    private final BenchScrollBar bar = new BenchScrollBar();
    /** La dernière ligne annoncée au serveur : on ne l'importune que lorsqu'elle change. */
    private int sentLine;
    private int backX;
    private int backY;

    public BenchStockScreen(BenchStockMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        super.init();
        // Une fenêtre trop basse rogne le bas plutôt que le titre et le bouton de retour.
        topPos = Math.max(0, topPos);
        leftPos = Math.max(0, leftPos);
        bar.place(leftPos + BenchStockMenu.SLOT_X + BenchStockMenu.COLUMNS * BenchStockMenu.SLOT_PITCH + 3,
                topPos + BenchStockMenu.GRID_Y - 1, BenchStockMenu.ROWS * BenchStockMenu.SLOT_PITCH + 2);
        backX = leftPos + WIDTH - FRAME - BACK_W;
        backY = topPos + FRAME;
        bar.model.setSize(menu.totalLines(), BenchStockMenu.ROWS);
        bar.model.scrollTo(sentLine);
    }

    // ================================================================================================
    // Pour la démonstration de développement
    // ================================================================================================

    /** Où cliquer pour revenir à l'établi, en pixels d'interface. */
    public int[] backPoint() {
        return new int[] {backX + BACK_W / 2, backY + 6};
    }

    /** Le centre de la case {@code index} du menu, en pixels d'interface : pour survoler une case. */
    public int[] slotCenter(int index) {
        net.minecraft.world.inventory.Slot slot = menu.slots.get(index);
        return new int[] {leftPos + slot.x + 8, topPos + slot.y + 8};
    }

    /** La ligne que la fenêtre montre en haut. */
    public int firstLine() {
        return bar.model.first();
    }

    // ================================================================================================
    // Défilement
    // ================================================================================================

    /** Annonce au serveur la ligne que la barre vient de choisir, si elle a changé. */
    private void sendLine() {
        int line = bar.model.first();
        if (line != sentLine && minecraft != null && minecraft.gameMode != null) {
            sentLine = line;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BenchStockMenu.buttonForOffset(line));
        }
    }

    private void scrollBy(double lines) {
        bar.model.scrollBy(lines);
        sendLine();
    }

    // ================================================================================================
    // Dessin
    // ================================================================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        // Le stock grandit et rétrécit pendant que l'écran est ouvert : la barre suit.
        bar.model.setSize(menu.totalLines(), BenchStockMenu.ROWS);
        sendLine();
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        renderTooltip(g, mx, my);
        if (BenchTheme.inside(mx, my, backX, backY, BACK_W, TITLE_H - 1)) {
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.bench.stock.back.tooltip"), mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, imageWidth, imageHeight);

        String name = BenchTheme.ellipsize(font, title.getString(), WIDTH - FRAME * 2 - BACK_W - 12);
        g.drawString(font, name, leftPos + FRAME + 3, topPos + FRAME + 3, BenchTheme.BRASS_LIGHT, false);
        BenchTheme.button(g, font, backX, backY, BACK_W, TITLE_H - 1,
                Component.translatable("gui.oneforallsurvival.bench.stock.back").getString(),
                BenchTheme.inside(mx, my, backX, backY, BACK_W, TITLE_H - 1), true, false);

        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            BenchTheme.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        bar.render(g, mx, my);

        int total = menu.totalStacks();
        int textY = topPos + COUNT_Y;
        int gridW = BenchStockMenu.COLUMNS * BenchStockMenu.SLOT_PITCH;
        // Les lignes montrées, à droite ; le décompte prend la place qui reste, à gauche.
        String lines = "";
        if (total > 0 && bar.model.scrollable()) {
            int first = bar.model.first();
            lines = Component.translatable("gui.oneforallsurvival.bench.stock.lines", first + 1,
                    Math.min(first + BenchStockMenu.ROWS, bar.model.total()), bar.model.total()).getString();
            g.drawString(font, lines, leftPos + BenchStockMenu.SLOT_X + gridW - font.width(lines), textY,
                    BenchTheme.TEXT_DIM, false);
        }
        int pendingRoom = gridW - (lines.isEmpty() ? 0 : font.width(lines) + 8);
        g.drawString(font, BenchTheme.ellipsize(font,
                Component.translatable("gui.oneforallsurvival.bench.stock.pending", total).getString(), pendingRoom),
                leftPos + BenchStockMenu.SLOT_X, textY, total > 0 ? BenchTheme.TEXT : BenchTheme.TEXT_DIM, false);
        if (total == 0) {
            String empty = BenchTheme.ellipsize(font,
                    Component.translatable("gui.oneforallsurvival.bench.stock.empty").getString(), gridW - 8);
            g.drawString(font, empty, leftPos + BenchStockMenu.SLOT_X + (gridW - font.width(empty)) / 2,
                    topPos + BenchStockMenu.GRID_Y + BenchStockMenu.ROWS * BenchStockMenu.SLOT_PITCH / 2 - 4,
                    BenchTheme.TEXT_DIM, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // Pas d'étiquettes de menu : titre et décompte sont dessinés par renderBg.
    }

    // ================================================================================================
    // Entrée
    // ================================================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && BenchTheme.inside(mx, my, backX, backY, BACK_W, TITLE_H - 1)) {
            BenchNetwork.CHANNEL.sendToServer(new OpenBenchPacket(menu.pos()));
            return true;
        }
        if (button == 0 && bar.mouseClicked(mx, my)) {
            sendLine();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (bar.mouseDragged(my)) {
            sendLine();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        bar.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // Une ligne par cran ; Maj : une fenêtre entière.
        scrollBy(-delta * (hasShiftDown() ? BenchStockMenu.ROWS : 1));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scrollBy(-1);
            case GLFW.GLFW_KEY_DOWN -> scrollBy(1);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-BenchStockMenu.ROWS);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(BenchStockMenu.ROWS);
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
