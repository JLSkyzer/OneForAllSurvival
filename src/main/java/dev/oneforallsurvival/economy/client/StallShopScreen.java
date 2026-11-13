package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.bench.client.ScrollModel;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallActionPacket;
import dev.oneforallsurvival.economy.stall.StallBuyback;
import dev.oneforallsurvival.economy.stall.StallOffer;
import dev.oneforallsurvival.economy.stall.StallShopMenu;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * La vitrine d'un étal : deux onglets (ce que l'étal vend, ce qu'il rachète), une liste de cinq rangées qui défile,
 * l'inventaire du joueur. Chaque rangée : le modèle avec la quantité du lot, le prix en pièces, la disponibilité
 * envoyée par le serveur, et le bouton « Acheter » ou « Vendre », qui envoie le prix, le lot et la version
 * affichés : le serveur refuse si l'un d'eux a changé entre-temps.
 *
 * <p>Les offres sont lues sur l'entité du bloc côté client (le serveur l'envoie à tous les clients qui voient l'étal).
 * Les textes sont mesurés et rétrécis pour tenir ; le prix complet, en lettres, est dans l'infobulle.
 */
public final class StallShopScreen extends AbstractContainerScreen<StallShopMenu> {

    private static final String GUI = "gui.oneforallsurvival.stall.";

    static final int MAIN_W = 176;
    static final int HEIGHT = StallShopMenu.HOTBAR_Y + 16 + 8;
    static final int PAD = 8;
    static final int TAB_Y = 16;
    static final int TAB_H = 12;
    static final int SCROLL_W = 5;
    static final int LIST_W = MAIN_W - 2 * PAD - SCROLL_W - 1;
    static final int BUTTON_W = 42;
    static final int BUTTON_H = 14;
    static final int TEXT_X = 20;
    static final int TEXT_W = LIST_W - TEXT_X - BUTTON_W - 4;

    /** Une rangée affichée : la case de l'offre et ce qu'elle montre. */
    private record Row(int index, ItemStack template, int lot, long price, boolean buyback, int lotsLeft) {}

    private boolean buybackTab;
    private final ScrollModel scroll = new ScrollModel();

    public StallShopScreen(StallShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MAIN_W;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = PAD;
        titleLabelY = 5;
        inventoryLabelX = PAD;
        inventoryLabelY = StallShopMenu.INVENTORY_Y - 10;
    }

    @Nullable
    private MarketStallBlockEntity stall() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(menu.pos()) instanceof MarketStallBlockEntity be ? be : null;
    }

    private List<Row> rows(boolean buybacks) {
        List<Row> out = new ArrayList<>();
        MarketStallBlockEntity be = stall();
        if (be == null) {
            return out;
        }
        if (buybacks) {
            List<StallBuyback> list = be.buybacks();
            for (int i = 0; i < list.size(); i++) {
                StallBuyback b = list.get(i);
                if (b != null) {
                    out.add(new Row(i, b.template(), b.lot(), b.price(), true, b.lotsLeft()));
                }
            }
        } else {
            List<StallOffer> list = be.offers();
            for (int i = 0; i < list.size(); i++) {
                StallOffer o = list.get(i);
                if (o != null) {
                    out.add(new Row(i, o.template(), o.lot(), o.price(), false, 0));
                }
            }
        }
        return out;
    }

    private int tabW() {
        return (MAIN_W - 2 * PAD - 2) / 2;
    }

    private int listX() {
        return leftPos + PAD;
    }

    private int rowY(int visibleIndex) {
        return topPos + StallShopMenu.LIST_Y + visibleIndex * StallShopMenu.ROW_H;
    }

    private boolean canAct(Row row) {
        return row.buyback() ? menu.buybackState(row.index()) == StallShopMenu.BUYBACK_OPEN : menu.saleAvailable(row.index()) > 0;
    }

    /** Ce que dit la disponibilité d'une rangée. */
    private Component status(Row row) {
        if (!row.buyback()) {
            int lots = menu.saleAvailable(row.index());
            return lots > 0 ? Component.translatable(GUI + "shop.in_stock", lots) : Component.translatable(GUI + "shop.sold_out");
        }
        return switch (menu.buybackState(row.index())) {
            case StallShopMenu.BUYBACK_OPEN -> Component.translatable(GUI + "shop.lots_left", row.lotsLeft());
            case StallShopMenu.BUYBACK_CASH_SHORT -> Component.translatable(GUI + "shop.cash_short");
            case StallShopMenu.BUYBACK_STOCK_FULL -> Component.translatable(GUI + "shop.stock_full");
            default -> Component.translatable(GUI + "shop.exhausted");
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Souris
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int t = 0; t < 2; t++) {
                if (BenchTheme.inside(mx, my, leftPos + PAD + t * (tabW() + 2), topPos + TAB_Y, tabW(), TAB_H)) {
                    if (buybackTab != (t == 1)) {
                        buybackTab = t == 1;
                        scroll.scrollTo(0);
                        click();
                    }
                    return true;
                }
            }
            List<Row> rows = rows(buybackTab);
            scroll.setSize(rows.size(), StallShopMenu.ROWS);
            for (int v = 0; v < StallShopMenu.ROWS; v++) {
                int i = scroll.first() + v;
                if (i >= rows.size()) {
                    break;
                }
                Row row = rows.get(i);
                int bx = listX() + LIST_W - BUTTON_W - 2;
                int by = rowY(v) + (StallShopMenu.ROW_H - BUTTON_H) / 2;
                if (BenchTheme.inside(mx, my, bx, by, BUTTON_W, BUTTON_H)) {
                    if (canAct(row)) {
                        click();
                        send(row);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void send(Row row) {
        MarketStallBlockEntity be = stall();
        if (be == null) {
            return;
        }
        BenchNetwork.CHANNEL.sendToServer(row.buyback()
                ? StallActionPacket.sellBack(menu.pos(), row.index(), row.price(), row.lot(), be.version())
                : StallActionPacket.buy(menu.pos(), row.index(), row.price(), row.lot(), be.version()));
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (BenchTheme.inside(mx, my, listX(), topPos + StallShopMenu.LIST_Y, LIST_W + SCROLL_W + 1,
                StallShopMenu.ROWS * StallShopMenu.ROW_H)) {
            scroll.scrollBy(-delta);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ---------------------------------------------------------------------------------------------
    // Dessin
    // ---------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        List<Row> rows = rows(buybackTab);
        for (int v = 0; v < StallShopMenu.ROWS; v++) {
            int i = scroll.first() + v;
            if (i >= rows.size()) {
                break;
            }
            Row row = rows.get(i);
            int y = rowY(v);
            if (BenchTheme.inside(mx, my, listX() + 2, y + 2, 16, 16)) {
                g.renderTooltip(font, row.template().copyWithCount(row.lot()), mx, my);
                return;
            }
            if (BenchTheme.inside(mx, my, listX() + TEXT_X, y + 2, TEXT_W, 9)) {
                g.renderTooltip(font, CoinMath.format(row.price()), mx, my);
                return;
            }
        }
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.rule(g, leftPos + PAD, topPos + 14, MAIN_W - 2 * PAD);
        for (Slot slot : menu.slots) {
            BenchTheme.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        List<Row> sales = rows(false);
        List<Row> buys = rows(true);
        for (int t = 0; t < 2; t++) {
            int x = leftPos + PAD + t * (tabW() + 2);
            boolean current = (t == 1) == buybackTab;
            boolean hover = BenchTheme.inside(mx, my, x, topPos + TAB_Y, tabW(), TAB_H);
            String label = Component.translatable(GUI + (t == 0 ? "shop.tab.sales" : "shop.tab.buybacks"),
                    (t == 0 ? sales : buys).size()).getString();
            fittedButton(g, font, x, topPos + TAB_Y, tabW(), TAB_H, label, hover && !current, true, current);
        }
        List<Row> rows = buybackTab ? buys : sales;
        scroll.setSize(rows.size(), StallShopMenu.ROWS);
        int listTop = topPos + StallShopMenu.LIST_Y;
        int listH = StallShopMenu.ROWS * StallShopMenu.ROW_H;
        BenchTheme.well(g, listX() - 1, listTop - 1, LIST_W + 2, listH + 2);
        BenchTheme.scrollBar(g, listX() + LIST_W + 1, listTop - 1, SCROLL_W, listH + 2, scroll, false);
        if (rows.isEmpty()) {
            String none = BenchTheme.ellipsize(font, Component.translatable(GUI + "shop.none").getString(), LIST_W - 8);
            g.drawString(font, none, listX() + (LIST_W - font.width(none)) / 2, listTop + listH / 2 - 4, BenchTheme.TEXT_DIM, false);
            return;
        }
        for (int v = 0; v < StallShopMenu.ROWS; v++) {
            int i = scroll.first() + v;
            if (i >= rows.size()) {
                break;
            }
            drawRow(g, rows.get(i), rowY(v), mx, my);
        }
    }

    private void drawRow(GuiGraphics g, Row row, int y, int mx, int my) {
        int x = listX();
        if (BenchTheme.inside(mx, my, x, y, LIST_W, StallShopMenu.ROW_H)) {
            g.fill(x, y, x + LIST_W, y + StallShopMenu.ROW_H, BenchTheme.ROW_HOVER);
        }
        ItemStack shown = row.template().copyWithCount(row.lot());
        g.renderItem(shown, x + 2, y + 2);
        g.renderItemDecorations(font, shown, x + 2, y + 2);
        boolean able = canAct(row);
        PriceLabel.draw(g, font, row.price(), x + TEXT_X, y + 2, TEXT_W, able ? BenchTheme.TEXT : BenchTheme.TEXT_DIM);
        drawFitted(g, font, status(row).getString(), x + TEXT_X, y + 11, TEXT_W, able ? BenchTheme.TEXT_OK : BenchTheme.TEXT_BAD, 0.75F);
        int bx = x + LIST_W - BUTTON_W - 2;
        int by = y + (StallShopMenu.ROW_H - BUTTON_H) / 2;
        String label = Component.translatable(GUI + (row.buyback() ? "shop.sell" : "shop.buy")).getString();
        fittedButton(g, font, bx, by, BUTTON_W, BUTTON_H, label, able && BenchTheme.inside(mx, my, bx, by, BUTTON_W, BUTTON_H),
                able, true);
    }

    /** Un texte à l'échelle {@code base}, rétréci encore s'il dépasse {@code width} (jusqu'à 0,5), puis coupé. */
    static void drawFitted(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y, int width, int color,
            float base) {
        float scale = base;
        int natural = font.width(text);
        if (natural * scale > width) {
            scale = Math.max(0.5F, width / (float) Math.max(1, natural));
        }
        String shown = BenchTheme.ellipsize(font, text, (int) (width / scale));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, shown, 0, 0, color, false);
        g.pose().popPose();
    }

    /**
     * Un bouton du thème dont le libellé est rétréci pour tenir (jusqu'à 0,6) au lieu d'être coupé ; au-delà, coupé.
     * Couleurs du thème : grisé, laiton plein, survol.
     */
    static void fittedButton(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, int h, String label,
            boolean hover, boolean enabled, boolean primary) {
        BenchTheme.button(g, font, x, y, w, h, "", hover, enabled, primary);
        int color = !enabled ? BenchTheme.TEXT_DISABLED : primary ? BenchTheme.TEXT_ON_BRASS : hover ? BenchTheme.BRASS_LIGHT : BenchTheme.TEXT;
        int room = w - 4;
        float scale = Math.min(1.0F, Math.max(0.6F, room / (float) Math.max(1, font.width(label))));
        String shown = BenchTheme.ellipsize(font, label, (int) (room / scale));
        g.pose().pushPose();
        g.pose().translate(x + (w - font.width(shown) * scale) / 2.0F, y + (h - 8 * scale) / 2.0F + scale, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, shown, 0, 0, color, false);
        g.pose().popPose();
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
