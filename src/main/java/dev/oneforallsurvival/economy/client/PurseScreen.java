package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.PurseActionPacket;
import dev.oneforallsurvival.economy.PurseMenu;
import dev.oneforallsurvival.economy.SafeActionPacket;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * L'écran de la bourse : le total en haut, une ligne par coupure (la plus grande d'abord) avec le nombre, la
 * valeur et les boutons « retirer 1, 10, 64 », puis « Déposer tout » et « Regrouper » en bas. Au décor de
 * l'établi ({@link BenchTheme}). L'écran ne décide de rien : chaque bouton envoie un {@link PurseActionPacket} que
 * le serveur revalide, et les nombres affichés sont ceux que le serveur a envoyés.
 */
public final class PurseScreen extends AbstractContainerScreen<PurseMenu> {

    private static final String GUI = "gui.oneforallsurvival.purse.";
    private static final int WIDTH = 220;
    private static final int HEIGHT = 160;
    private static final int PAD = 8;
    private static final int TOTAL_Y = 19;
    private static final int ROWS_Y = 32;
    private static final int ROW_H = 20;
    private static final int ROW_BUTTON_W = 24;
    private static final int ROW_BUTTON_H = 14;
    private static final int ROW_BUTTON_GAP = 2;
    private static final int FOOT_Y = ROWS_Y + Coin.values().length * ROW_H + 4;
    private static final int FOOT_W = (WIDTH - 2 * PAD - 4) / 2;
    private static final int FOOT_H = 16;

    public PurseScreen(PurseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = PAD;
        titleLabelY = 5;
    }

    /** La coupure de la ligne {@code row} : la plus grande en haut. */
    private static Coin coinOfRow(int row) {
        return Coin.values()[Coin.values().length - 1 - row];
    }

    private int rowY(int row) {
        return topPos + ROWS_Y + row * ROW_H;
    }

    private int buttonX(int index) {
        int first = leftPos + WIDTH - PAD - SafeActionPacket.WITHDRAW_AMOUNTS.length * ROW_BUTTON_W
                - (SafeActionPacket.WITHDRAW_AMOUNTS.length - 1) * ROW_BUTTON_GAP;
        return first + index * (ROW_BUTTON_W + ROW_BUTTON_GAP);
    }

    private int buttonY(int row) {
        return rowY(row) + (ROW_H - ROW_BUTTON_H) / 2;
    }

    private int depositX() {
        return leftPos + PAD;
    }

    private int groupX() {
        return leftPos + PAD + FOOT_W + 4;
    }

    private int footY() {
        return topPos + FOOT_Y;
    }

    /** Y a-t-il de quoi regrouper (selon les valeurs vivantes) ? Le serveur a le dernier mot. */
    private boolean canGroup() {
        EnumMap<Coin, Long> counts = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            if (menu.count(coin) > 0) {
                counts.put(coin, menu.count(coin));
            }
        }
        return !CoinMath.group(counts, Coin::value).equals(counts);
    }

    private void send(PurseActionPacket packet) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        BenchNetwork.CHANNEL.sendToServer(packet);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int row = 0; row < Coin.values().length; row++) {
                Coin coin = coinOfRow(row);
                for (int i = 0; i < SafeActionPacket.WITHDRAW_AMOUNTS.length; i++) {
                    int amount = SafeActionPacket.WITHDRAW_AMOUNTS[i];
                    if (BenchTheme.inside(mx, my, buttonX(i), buttonY(row), ROW_BUTTON_W, ROW_BUTTON_H)
                            && menu.count(coin) >= amount) {
                        send(PurseActionPacket.withdraw(menu.slot(), coin, amount));
                        return true;
                    }
                }
            }
            if (BenchTheme.inside(mx, my, depositX(), footY(), FOOT_W, FOOT_H)) {
                send(PurseActionPacket.deposit(menu.slot()));
                return true;
            }
            if (BenchTheme.inside(mx, my, groupX(), footY(), FOOT_W, FOOT_H) && canGroup()) {
                send(PurseActionPacket.group(menu.slot()));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        if (BenchTheme.inside(mx, my, groupX(), footY(), FOOT_W, FOOT_H)) {
            g.renderTooltip(font, Component.translatable(GUI + "group.tooltip"), mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rule(g, leftPos + PAD, topPos + 16, WIDTH - 2 * PAD);
        BenchTheme.well(g, leftPos + PAD, topPos + ROWS_Y - 2, WIDTH - 2 * PAD, Coin.values().length * ROW_H + 2);
        for (int row = 0; row < Coin.values().length; row++) {
            Coin coin = coinOfRow(row);
            long count = menu.count(coin);
            BenchTheme.item(g, font, new ItemStack(coin.item()), leftPos + PAD + 3, rowY(row) + 2, 1.0F, false);
            g.drawString(font, count + " " + Component.translatable(coin.unitKey()).getString(), leftPos + PAD + 24,
                    rowY(row) + 2, count > 0 ? BenchTheme.TEXT : BenchTheme.TEXT_DIM, false);
            g.drawString(font, Component.translatable(GUI + "worth", CoinMath.valueOf(coin, count)), leftPos + PAD + 24,
                    rowY(row) + 11, BenchTheme.TEXT_DIM, false);
            for (int i = 0; i < SafeActionPacket.WITHDRAW_AMOUNTS.length; i++) {
                int amount = SafeActionPacket.WITHDRAW_AMOUNTS[i];
                boolean enabled = count >= amount;
                BenchTheme.button(g, font, buttonX(i), buttonY(row), ROW_BUTTON_W, ROW_BUTTON_H, String.valueOf(amount),
                        enabled && BenchTheme.inside(mx, my, buttonX(i), buttonY(row), ROW_BUTTON_W, ROW_BUTTON_H),
                        enabled, false);
            }
        }
        BenchTheme.button(g, font, depositX(), footY(), FOOT_W, FOOT_H,
                Component.translatable(GUI + "deposit_all").getString(),
                BenchTheme.inside(mx, my, depositX(), footY(), FOOT_W, FOOT_H), true, true);
        boolean group = canGroup();
        BenchTheme.button(g, font, groupX(), footY(), FOOT_W, FOOT_H, Component.translatable(GUI + "group").getString(),
                group && BenchTheme.inside(mx, my, groupX(), footY(), FOOT_W, FOOT_H), group, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, titleLabelY, BenchTheme.TEXT, false);
        g.drawString(font, Component.translatable(GUI + "total", CoinMath.format(menu.total())), PAD, TOTAL_Y,
                BenchTheme.BRASS_LIGHT, false);
    }
}
