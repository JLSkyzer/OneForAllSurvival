package dev.oneforallsurvival.bench;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client vers serveur : commander {@code count} exemplaires d'une recette à l'établi {@code bench}. */
public record OrderPacket(ResourceLocation recipe, int count, BlockPos bench) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(recipe);
        buf.writeVarInt(count);
        buf.writeBlockPos(bench);
    }

    public static OrderPacket decode(FriendlyByteBuf buf) {
        return new OrderPacket(buf.readResourceLocation(), buf.readVarInt(), buf.readBlockPos());
    }
}
