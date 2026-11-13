package dev.oneforallsurvival.economy.invoice;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : émettre une facture de {@code amount} cuivres à {@code target}, pour {@code reason}. Le
 * serveur ne croit rien : {@link InvoiceService#issue} revérifie le carnet en main, la cible connectée et proche,
 * le montant, le motif et que ce n'est pas soi-même.
 */
public record InvoiceIssuePacket(UUID target, long amount, String reason) {

    /** Borne de lecture du motif : au-delà du plus grand motif réglable (200), un paquet géant est rejeté. */
    public static final int MAX_WIRE_REASON = 256;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(target);
        buf.writeLong(amount);
        buf.writeUtf(reason, MAX_WIRE_REASON);
    }

    public static InvoiceIssuePacket decode(FriendlyByteBuf buf) {
        return new InvoiceIssuePacket(buf.readUUID(), buf.readLong(), buf.readUtf(MAX_WIRE_REASON));
    }

    public static void handle(InvoiceIssuePacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        InvoiceService.IssueResult result = InvoiceService.issue(sender, packet.target(), packet.amount(), packet.reason());
        if (result != InvoiceService.IssueResult.DONE) {
            ServerPlayer target = sender.server.getPlayerList().getPlayer(packet.target());
            String name = target == null ? "?" : target.getGameProfile().getName();
            sender.displayClientMessage(Component.translatable(InvoiceService.MESSAGE + "issue."
                    + result.name().toLowerCase(Locale.ROOT), name), true);
        }
    }
}
