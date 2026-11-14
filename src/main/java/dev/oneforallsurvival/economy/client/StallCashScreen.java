package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.stall.StallCashMenu;
import dev.oneforallsurvival.economy.stall.StallCashPacket;
import net.minecraft.client.Minecraft;
import dev.oneforallsurvival.bench.client.BenchTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * La caisse d'un étal : l'écran commun ({@link CoinAccountScreen}), ses demandes vont dans un {@link StallCashPacket}.
 * Un bouton, sous le panneau « Change », ramène à l'onglet Stock (bouton de menu du jeu, le serveur répond).
 */
public final class StallCashScreen extends CoinAccountScreen<StallCashMenu> {

    public StallCashScreen(StallCashMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private int backX() {
        return leftPos + PANEL_X + 8;
    }

    private int backY() {
        return topPos + PANEL_H + 8;
    }

    private static final int BACK_W = PANEL_W - 16;
    private static final int BACK_H = 16;

    /** Le bouton de retour au stock, au thème des autres boutons du commerce. */
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        boolean hover = BenchTheme.inside(mx, my, backX(), backY(), BACK_W, BACK_H);
        StallShopScreen.fittedButton(g, font, backX(), backY(), BACK_W, BACK_H,
                Component.translatable("gui.oneforallsurvival.stall.cash.back").getString(), hover, true, false);
        if (hover) {
            g.renderTooltip(font, Component.translatable("gui.oneforallsurvival.stall.cash.back.tooltip"), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && BenchTheme.inside(mx, my, backX(), backY(), BACK_W, BACK_H)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (Minecraft.getInstance().gameMode != null) {
                Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, StallCashMenu.BUTTON_STOCK);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void send(CoinRequest request) {
        BenchNetwork.CHANNEL.sendToServer(new StallCashPacket(menu.pos(), request));
    }
}
