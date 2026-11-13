package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.stall.StallCashMenu;
import dev.oneforallsurvival.economy.stall.StallCashPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.oneforallsurvival.stall.cash.back"), button -> {
                    if (Minecraft.getInstance().gameMode != null) {
                        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, StallCashMenu.BUTTON_STOCK);
                    }
                })
                .bounds(leftPos + PANEL_X + 8, topPos + PANEL_H + 8, PANEL_W - 16, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.oneforallsurvival.stall.cash.back.tooltip")))
                .build());
    }

    @Override
    protected void send(CoinRequest request) {
        BenchNetwork.CHANNEL.sendToServer(new StallCashPacket(menu.pos(), request));
    }
}
