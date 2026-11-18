package dev.oneforallsurvival.grid.client;

import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.client.CoinAccountScreen;
import dev.oneforallsurvival.grid.supplier.PowerTillMenu;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * La caisse d'un poste de fournisseur : l'écran commun ({@link CoinAccountScreen}). Ses demandes partent par le bouton
 * de menu du jeu ({@link PowerTillMenu#button}) ; aucun dépôt (la caisse ne reçoit que des pièces prélevées), et retrait
 * et change grisés pour qui n'est ni le fondateur ni de l'équipe.
 */
public final class PowerTillScreen extends CoinAccountScreen<PowerTillMenu> {

    public PowerTillScreen(PowerTillMenu menu, Inventory inventory, Component title) {
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
        return menu.mayTake() ? null : "till_not_yours";
    }
}
