package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.PurseActionPacket;
import dev.oneforallsurvival.economy.PurseMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** L'écran de la bourse : l'écran commun ({@link CoinAccountScreen}), ses demandes vont dans un {@link PurseActionPacket}. */
public final class PurseScreen extends CoinAccountScreen<PurseMenu> {

    public PurseScreen(PurseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void send(CoinRequest request) {
        BenchNetwork.CHANNEL.sendToServer(new PurseActionPacket(menu.slot(), request));
    }
}
