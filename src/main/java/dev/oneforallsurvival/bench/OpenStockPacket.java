package dev.oneforallsurvival.bench;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Client vers serveur : ouvrir le stock depuis le menu de l'établi {@code bench}. */
public record OpenStockPacket(BlockPos bench) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(bench);
    }

    public static OpenStockPacket decode(FriendlyByteBuf buf) {
        return new OpenStockPacket(buf.readBlockPos());
    }
}
