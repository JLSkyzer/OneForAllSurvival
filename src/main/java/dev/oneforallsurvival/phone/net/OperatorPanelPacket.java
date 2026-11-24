package dev.oneforallsurvival.phone.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur vers client : la vue de l'opérateur ({@link OperatorView}), envoyée à l'ouverture du panneau, après chaque
 * geste et au renvoi périodique d'un panneau ouvert ({@link OperatorPanels}), jamais deux fois de suite identique. Le
 * client ouvre le panneau Minecraft pour la source {@code BLOCK} ; la source {@code PHONE} est routée à la tâche 10
 * (appli « Antenne », repli).
 */
public record OperatorPanelPacket(OperatorView view) {

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
    }

    public static OperatorPanelPacket decode(FriendlyByteBuf buf) {
        return new OperatorPanelPacket(OperatorView.decode(buf));
    }

    public static void handle(OperatorPanelPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.oneforallsurvival.phone.net.client.OperatorPanelScreen.accept(packet.view()));
    }
}
