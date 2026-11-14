package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.town.TreasuryActionPacket;
import dev.oneforallsurvival.economy.town.TreasuryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * L'écran du trésor : l'écran commun ({@link CoinAccountScreen}), comme celui du coffre-fort. Un retrait (clic droit,
 * maj compris) est une dépense publique : il passe d'abord par {@link TreasuryReasonScreen}, qui demande le motif,
 * puis revient ici. Le reste (dépôts, change) part tout de suite, sans motif.
 */
public final class TreasuryScreen extends CoinAccountScreen<TreasuryMenu> {

    /** Le dernier motif confirmé, proposé à la dépense suivante (pratique pour plusieurs retraits d'affilée). */
    private String lastReason = "";

    public TreasuryScreen(TreasuryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void send(CoinRequest request) {
        if (request.action() == CoinRequest.WITHDRAW && minecraft != null) {
            minecraft.setScreen(new TreasuryReasonScreen(this, request, lastReason));
            return;
        }
        sendWithReason(request, "");
    }

    /** Envoie la demande avec son motif (appelé par {@link TreasuryReasonScreen} une fois le motif saisi). */
    void sendWithReason(CoinRequest request, String reason) {
        if (!reason.isEmpty()) {
            lastReason = reason;
        }
        BenchNetwork.CHANNEL.sendToServer(new TreasuryActionPacket(menu.pos(), request, reason));
    }

    @Override
    protected Component withdrawLine(int amount, Component unit) {
        return Component.translatable("gui.oneforallsurvival.treasury.right_withdraw", amount, unit);
    }
}
