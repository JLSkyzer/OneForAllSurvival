package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.SafeActionPacket;
import dev.oneforallsurvival.economy.SafeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** L'écran du coffre-fort : l'écran commun ({@link CoinAccountScreen}), ses demandes vont dans un {@link SafeActionPacket}. */
public final class SafeScreen extends CoinAccountScreen<SafeMenu> {

    public SafeScreen(SafeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void send(CoinRequest request) {
        BenchNetwork.CHANNEL.sendToServer(new SafeActionPacket(menu.pos(), request));
    }
}
