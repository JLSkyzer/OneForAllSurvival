package dev.oneforallsurvival.bench;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Client vers serveur : annuler une fabrication de sa file. */
public record CancelPacket(UUID entry) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(entry);
    }

    public static CancelPacket decode(FriendlyByteBuf buf) {
        return new CancelPacket(buf.readUUID());
    }
}
