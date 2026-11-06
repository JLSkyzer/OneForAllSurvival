package dev.oneforallsurvival.bench;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Client vers serveur : revenir du stock au menu de l'établi {@code bench}. */
public record OpenBenchPacket(BlockPos bench) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(bench);
    }

    public static OpenBenchPacket decode(FriendlyByteBuf buf) {
        return new OpenBenchPacket(buf.readBlockPos());
    }
}
