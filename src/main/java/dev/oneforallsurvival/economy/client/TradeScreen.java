package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.trade.TradeActionPacket;
import dev.oneforallsurvival.economy.trade.TradeMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
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
 * La fenêtre d'échange, au décor de l'établi : deux colonnes (« Vous donnez » / « {nom} donne »), l'état des deux
 * confirmations, la case « Reçu », le bouton « Confirmer », puis l'inventaire ; à droite, le panneau « Pièces »
 * (+1 et +10 par coupure, prises dans les bourses d'abord). L'écran ne décide de rien : chaque bouton envoie un
 * {@link TradeActionPacket} avec la version qu'il a vue, que le serveur revalide. Les textes dont la longueur
 * dépend du nom ou de la langue sont mesurés ({@code font.width}) et rétrécissent pour tenir dans le cadre.
 */
public final class TradeScreen extends AbstractContainerScreen<TradeMenu> {

    private static final String GUI = "gui.oneforallsurvival.trade.";

    static final int MAIN_W = 176;
    static final int HEIGHT = TradeMenu.HOTBAR_Y + 16 + 8;
    static final int PAD = 8;
    static final int GRID_W = TradeMenu.GRID_COLUMNS * 18;
    static final int LABEL_Y = 19;
    static final int STATUS_Y = TradeMenu.GRID_Y + 3 * 18 + 3;
    static final int ACTION_Y = STATUS_Y + 12;
    static final int ACTION_H = 16;
    static final int PANEL_X = MAIN_W + 2;
    static final int PANEL_W = 74;
    static final int PANEL_H = 20 + 5 * 18 + 4;
    static final int COIN_ROW_Y = 20;
    static final int COIN_ROW = 18;

    /** Un bouton : son rectangle (coordonnées d'écran), sa bulle, le paquet qu'il envoie (nul : grisé). */
    private record Element(String id, int x, int y, int w, int h, List<Component> tooltip,
            @Nullable TradeActionPacket packet, String label, boolean primary) {
        boolean contains(double mx, double my) {
            return BenchTheme.inside(mx, my, x, y, w, h);
        }
    }

    public TradeScreen(TradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_X + PANEL_W;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = PAD;
        titleLabelY = 6;
        inventoryLabelX = PAD;
        inventoryLabelY = TradeMenu.INVENTORY_Y - 10;
    }

    /** Les pièces de cette coupure que le joueur porte (en vrac et dans ses bourses), d'après l'inventaire du client. */
    private long carried(Coin coin) {
        Inventory inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
        if (inventory == null) {
            return 0L;
        }
        long total = 0L;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == coin.item()) {
                total += stack.getCount();
            } else if (Purse.isPurse(stack)) {
                total += Purse.count(stack, coin);
            }
        }
        return total;
    }

    private List<Element> elements() {
        List<Element> out = new ArrayList<>();
        int version = menu.version();
        boolean receipt = menu.receipt();
        out.add(new Element("receipt", leftPos + PAD, topPos + ACTION_Y, 72, ACTION_H,
                List.of(Component.translatable(GUI + "receipt.tooltip")),
                TradeActionPacket.toggleReceipt(version), "", false));
        boolean confirmed = menu.selfConfirmed();
        List<Component> confirmTip = new ArrayList<>();
        confirmTip.add(Component.translatable(GUI + "confirm.tooltip"));
        if (confirmed) {
            confirmTip.add(Component.translatable(GUI + "confirm.done").withStyle(ChatFormatting.GREEN));
        }
        out.add(new Element("confirm", leftPos + TradeMenu.OTHER_X - 1, topPos + ACTION_Y, GRID_W, ACTION_H, confirmTip,
                confirmed ? null : TradeActionPacket.confirm(version),
                Component.translatable(GUI + (confirmed ? "confirmed" : "confirm")).getString(), true));
        for (int row = 0; row < Coin.values().length; row++) {
            Coin coin = Coin.values()[Coin.values().length - 1 - row];
            long have = carried(coin);
            int y = topPos + COIN_ROW_Y + row * COIN_ROW;
            int[] widths = {20, 24};
            int x = leftPos + PANEL_X + 24;
            for (int i = 0; i < TradeActionPacket.COIN_STEPS.length; i++) {
                int step = TradeActionPacket.COIN_STEPS[i];
                boolean can = have >= step;
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable(GUI + "coins.tooltip", step, Component.translatable(coin.unitKey(step)))
                        .withStyle(can ? ChatFormatting.WHITE : ChatFormatting.GRAY));
                if (!can) {
                    tip.add(Component.translatable(GUI + "coins.not_enough").withStyle(ChatFormatting.RED));
                }
                out.add(new Element("coins." + coin.key() + "." + step, x, y + 1, widths[i], 14, tip,
                        can ? TradeActionPacket.addCoins(coin, step) : null, "+" + step, false));
                x += widths[i] + 2;
            }
        }
        return out;
    }

    private Optional<Element> elementAt(double mx, double my) {
        for (Element element : elements()) {
            if (element.contains(mx, my)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    /** Le centre de l'élément {@code id} en coordonnées d'écran, pour les essais qui cliquent comme un joueur. */
    public Optional<int[]> elementCenter(String id) {
        for (Element element : elements()) {
            if (element.id().equals(id)) {
                return Optional.of(new int[] {element.x() + element.w() / 2, element.y() + element.h() / 2});
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Optional<Element> hit = elementAt(mx, my);
        if (hit.isPresent() && button == 0) {
            if (hit.get().packet() != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                BenchNetwork.CHANNEL.sendToServer(hit.get().packet());
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        Optional<Element> hover = elementAt(mx, my);
        if (hover.isPresent() && !hover.get().tooltip().isEmpty()) {
            g.renderComponentTooltip(font, hover.get().tooltip(), mx, my);
        } else {
            renderTooltip(g, mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, MAIN_W, imageHeight);
        BenchTheme.plate(g, leftPos + PANEL_X, topPos, PANEL_W, PANEL_H);
        BenchTheme.rivets(g, leftPos + PANEL_X, topPos, PANEL_W, PANEL_H);
        BenchTheme.rule(g, leftPos + PAD, topPos + 15, MAIN_W - 2 * PAD);
        BenchTheme.rule(g, leftPos + PANEL_X + 5, topPos + 15, PANEL_W - 10);
        BenchTheme.well(g, leftPos + TradeMenu.OWN_X - 2, topPos + TradeMenu.GRID_Y - 2, GRID_W + 2, 3 * 18 + 2);
        BenchTheme.well(g, leftPos + TradeMenu.OTHER_X - 2, topPos + TradeMenu.GRID_Y - 2, GRID_W + 2, 3 * 18 + 2);
        for (Slot slot : menu.slots) {
            BenchTheme.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        for (int row = 0; row < Coin.values().length; row++) {
            Coin coin = Coin.values()[Coin.values().length - 1 - row];
            BenchTheme.item(g, font, new ItemStack(coin.item()), leftPos + PANEL_X + 5, topPos + COIN_ROW_Y + row * COIN_ROW,
                    1.0F, false);
        }
        for (Element element : elements()) {
            boolean hover = element.contains(mx, my);
            if (element.id().equals("receipt")) {
                paintReceipt(g, element, hover);
            } else {
                BenchTheme.button(g, font, element.x(), element.y(), element.w(), element.h(), element.label(),
                        hover && element.packet() != null, element.packet() != null, element.primary());
            }
        }
    }

    /** La case à cocher « Reçu » : un carré et son libellé. */
    private void paintReceipt(GuiGraphics g, Element e, boolean hover) {
        int box = 10;
        int bx = e.x();
        int by = e.y() + (e.h() - box) / 2;
        g.fill(bx, by, bx + box, by + box, hover ? BenchTheme.BRASS_LIGHT : BenchTheme.BRASS_DARK);
        g.fill(bx + 1, by + 1, bx + box - 1, by + box - 1, BenchTheme.WELL);
        if (menu.receipt()) {
            g.fill(bx + 3, by + 3, bx + box - 3, by + box - 3, BenchTheme.TEXT_OK);
        }
        fitted(g, Component.translatable(GUI + "receipt").getString(), bx + box + 4, e.y() + (e.h() - 8) / 2 + 1,
                e.w() - box - 4, hover ? BenchTheme.BRASS_LIGHT : BenchTheme.TEXT);
    }

    /** Un texte qui rétrécit pour tenir dans {@code width} pixels. */
    private void fitted(GuiGraphics g, String text, int x, int y, int width, int color) {
        float scale = Math.min(1.0F, width / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - scale) * 4.0F, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        fitted(g, title.getString(), titleLabelX, titleLabelY, MAIN_W - 2 * PAD, BenchTheme.TEXT);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, BenchTheme.TEXT_DIM, false);
        fitted(g, Component.translatable(GUI + "coins").getString(), PANEL_X + 6, 6, PANEL_W - 12, BenchTheme.TEXT);
        fitted(g, Component.translatable(GUI + "you_give").getString(), TradeMenu.OWN_X - 1, LABEL_Y, GRID_W,
                BenchTheme.BRASS_LIGHT);
        fitted(g, Component.translatable(GUI + "they_give", menu.otherName()).getString(), TradeMenu.OTHER_X - 1, LABEL_Y,
                GRID_W, BenchTheme.BRASS_LIGHT);
        status(g, menu.selfConfirmed(), TradeMenu.OWN_X - 1);
        status(g, menu.otherConfirmed(), TradeMenu.OTHER_X - 1);
    }

    private void status(GuiGraphics g, boolean confirmed, int x) {
        String text = Component.translatable(GUI + (confirmed ? "status.confirmed" : "status.waiting")).getString();
        fitted(g, text, x, STATUS_Y, GRID_W, confirmed ? BenchTheme.TEXT_OK : BenchTheme.TEXT_DIM);
    }
}
