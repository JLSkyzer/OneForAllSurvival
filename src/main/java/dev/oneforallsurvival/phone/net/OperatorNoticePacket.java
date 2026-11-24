package dev.oneforallsurvival.phone.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur vers client (lot T2, tâche 10) : un mot pour le panneau de l'opérateur ouvert sous le numéro {@code session}.
 * Soit le retour d'un geste ({@code message}, le même que la barre d'action, montré sur la ligne d'état du panneau et de
 * l'appli : revue de la tâche 9, mineur 2), soit sa fermeture par le serveur ({@code closed} : le panneau ne tient plus,
 * ou la caisse s'ouvre), pour que l'écran du client ne reste pas sur un numéro mort. Hors de {@link OperatorView} : la vue
 * reste la même pour les trois écrans, et ce mot ne la remplace pas.
 */
public record OperatorNoticePacket(int session, boolean closed, Component message) {

    public OperatorNoticePacket {
        message = message == null ? Component.empty() : message;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(session);
        buf.writeBoolean(closed);
        buf.writeComponent(message);
    }

    public static OperatorNoticePacket decode(FriendlyByteBuf buf) {
        return new OperatorNoticePacket(buf.readVarInt(), buf.readBoolean(), buf.readComponent());
    }

    public static void handle(OperatorNoticePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.oneforallsurvival.phone.net.client.OperatorPanelScreen.notice(packet));
    }
}
