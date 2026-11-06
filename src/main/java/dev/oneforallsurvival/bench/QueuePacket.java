package dev.oneforallsurvival.bench;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Serveur vers client : la file du joueur et le nombre d'objets qui l'attendent au stock. Envoyé à chaque
 * changement, et une fois par seconde tant que le menu est ouvert.
 */
public record QueuePacket(List<Row> rows, int stockItems) {

    /** Une fabrication en file. */
    public record Row(UUID id, ResourceLocation recipe, ItemStack result, int totalTicks, double doneTicks) {}

    public static QueuePacket of(PlayerWorkshop workshop) {
        List<Row> rows = new ArrayList<>(workshop.queue().size());
        for (WorkshopEntry e : workshop.queue()) {
            rows.add(new Row(e.id(), e.recipeId(), e.result().copy(), e.totalTicks(), e.doneTicks()));
        }
        long items = 0;
        for (ItemStack s : workshop.stock()) {
            items += s.getCount();
        }
        return new QueuePacket(rows, (int) Math.min(items, Integer.MAX_VALUE));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(stockItems);
        buf.writeVarInt(rows.size());
        for (Row r : rows) {
            buf.writeUUID(r.id());
            buf.writeResourceLocation(r.recipe());
            buf.writeItem(r.result());
            buf.writeVarInt(r.totalTicks());
            buf.writeDouble(r.doneTicks());
        }
    }

    public static QueuePacket decode(FriendlyByteBuf buf) {
        int stockItems = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > buf.readableBytes()) {
            throw new DecoderException("file de l'etabli : nombre de lignes invalide " + n);
        }
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUUID(), buf.readResourceLocation(), buf.readItem(), buf.readVarInt(),
                    buf.readDouble()));
        }
        return new QueuePacket(rows, stockItems);
    }
}
