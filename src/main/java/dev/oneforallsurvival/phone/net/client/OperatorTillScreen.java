package dev.oneforallsurvival.phone.net.client;

import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import dev.oneforallsurvival.grid.supplier.PowerTillMenu;
import dev.oneforallsurvival.phone.net.OperatorTillMenu;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * La caisse d'un opérateur : l'écran commun ({@link CoinAccountScreen}), comme celle du poste de fournisseur. Ses
 * demandes partent par le bouton de menu du jeu ({@link PowerTillMenu#button}) ; aucun dépôt. Seuls le fondateur et
 * l'équipe l'ouvrent : le serveur rejoue chaque demande ({@link OperatorTillMenu#apply}).
 */
public final class OperatorTillScreen extends CoinAccountScreen<OperatorTillMenu> {

    public OperatorTillScreen(OperatorTillMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void send(CoinRequest request) {
        int button = PowerTillMenu.button(request);
        Minecraft mc = Minecraft.getInstance();
        if (button >= 0 && mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Nullable
    @Override
    protected String depositBlocked() {
        return "till_deposit";
    }

    @Nullable
    @Override
    protected String takeBlocked() {
        return null;
    }
}
