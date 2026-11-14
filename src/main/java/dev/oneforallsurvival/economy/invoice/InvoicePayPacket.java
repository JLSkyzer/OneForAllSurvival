package dev.oneforallsurvival.economy.invoice;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : payer la facture de la case {@code slot} de son inventaire (bouton « Payer » de l'écran de
 * la facture), dont l'identifiant affiché est {@code seen}. Le serveur relit la case (qui doit encore porter cette
 * facture), le registre et le débiteur ({@link InvoiceService#pay(ServerPlayer, int, UUID)}).
 */
public record InvoicePayPacket(int slot, UUID seen) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        buf.writeUUID(seen);
    }

    public static InvoicePayPacket decode(FriendlyByteBuf buf) {
        return new InvoicePayPacket(buf.readVarInt(), buf.readUUID());
    }

    public static void handle(InvoicePayPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        InvoiceService.PayResult result = InvoiceService.pay(sender, packet.slot(), packet.seen());
        sender.sendSystemMessage(Component.translatable(InvoiceService.MESSAGE + "pay."
                + result.name().toLowerCase(Locale.ROOT)));
    }
}
