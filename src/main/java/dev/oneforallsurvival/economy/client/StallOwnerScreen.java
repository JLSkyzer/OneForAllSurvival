package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.stall.StallOwnerMenu;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * La gestion d'un étal, au décor de l'établi : quatre onglets (Stock, Offres, Caisse, Registre), les 27 cases du
 * stock, l'inventaire. Seuls Stock et Caisse sont actifs à cette tâche ; Offres et Registre sont grisés (tâche
 * suivante). Un onglet est un bouton de menu du jeu : le serveur répond en ouvrant l'écran voulu, l'écran ne décide
 * de rien.
 */
public final class StallOwnerScreen extends AbstractContainerScreen<StallOwnerMenu> {

    private static final String GUI = "gui.oneforallsurvival.stall.";

    static final int MAIN_W = 176;
    static final int HEIGHT = StallOwnerMenu.HOTBAR_Y + 16 + 8;
    static final int PAD = 8;
    static final int TAB_Y = 18;
    static final int TAB_H = 14;
    static final int TAB_GAP = 2;
    static final int TAB_W = (MAIN_W - 2 * PAD - 3 * TAB_GAP) / 4;

    /** Un onglet : son nom, le bouton de menu qu'il déclenche (-1 : onglet courant), et s'il répond déjà. */
    private record Tab(String key, int button, boolean ready) {}

    private static final List<Tab> TABS = List.of(
            new Tab("stock", -1, true),
            new Tab("offers", StallOwnerMenu.BUTTON_OFFERS, false),
            new Tab("cash", StallOwnerMenu.BUTTON_CASH, true),
            new Tab("ledger", StallOwnerMenu.BUTTON_LEDGER, false));

    public StallOwnerScreen(StallOwnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MAIN_W;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = PAD;
        titleLabelY = 6;
        inventoryLabelX = PAD;
        inventoryLabelY = StallOwnerMenu.INVENTORY_Y - 10;
    }

    private int tabX(int index) {
        return leftPos + PAD + index * (TAB_W + TAB_GAP);
    }

    private Optional<Integer> tabAt(double mx, double my) {
        for (int i = 0; i < TABS.size(); i++) {
            if (BenchTheme.inside(mx, my, tabX(i), topPos + TAB_Y, TAB_W, TAB_H)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Optional<Integer> hit = tabAt(mx, my);
        if (hit.isPresent() && button == 0) {
            Tab tab = TABS.get(hit.get());
            if (tab.ready() && tab.button() >= 0 && Minecraft.getInstance().gameMode != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, tab.button());
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        Optional<Integer> hover = tabAt(mx, my);
        if (hover.isPresent() && !TABS.get(hover.get()).ready()) {
            g.renderTooltip(font, Component.translatable(GUI + "tab.soon"), mx, my);
        } else {
            renderTooltip(g, mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.rule(g, leftPos + PAD, topPos + 15, MAIN_W - 2 * PAD);
        BenchTheme.well(g, leftPos + StallOwnerMenu.STOCK_X - 2, topPos + StallOwnerMenu.STOCK_Y - 2, 9 * 18 + 2, 3 * 18 + 2);
        for (Slot slot : menu.slots) {
            BenchTheme.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        for (int i = 0; i < TABS.size(); i++) {
            Tab tab = TABS.get(i);
            boolean current = tab.button() < 0;
            boolean hover = BenchTheme.inside(mx, my, tabX(i), topPos + TAB_Y, TAB_W, TAB_H);
            BenchTheme.button(g, font, tabX(i), topPos + TAB_Y, TAB_W, TAB_H, Component.translatable(GUI + "tab." + tab.key()).getString(),
                    hover && tab.ready() && !current, tab.ready(), current);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        float scale = Math.min(1.0F, (MAIN_W - 2 * PAD) / (float) Math.max(1, font.width(title.getString())));
        g.pose().pushPose();
        g.pose().translate(titleLabelX, titleLabelY + (1.0F - scale) * 4.0F, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, title.getString(), 0, 0, BenchTheme.TEXT, false);
        g.pose().popPose();
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, BenchTheme.TEXT_DIM, false);
    }
}
