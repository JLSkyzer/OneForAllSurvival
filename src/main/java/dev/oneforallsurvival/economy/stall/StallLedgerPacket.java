package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.stall.StallLedger.Kind;
import dev.oneforallsurvival.economy.stall.StallLedger.Line;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur vers client : une page du registre de l'étal en {@code pos} (la plus récente d'abord, page 0). Envoyée
 * seulement au joueur qui l'a demandée, s'il gère l'étal et a sa gestion ouverte ({@link StallActionPacket}) ; jamais
 * à un autre joueur.
 */
public record StallLedgerPacket(BlockPos pos, int page, int pages, List<Line> lines) {

    public StallLedgerPacket {
        lines = List.copyOf(lines);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(page);
        buf.writeVarInt(pages);
        buf.writeVarInt(lines.size());
        for (Line line : lines) {
            buf.writeLong(line.epochMillis());
            buf.writeUUID(line.actor());
            buf.writeUtf(line.actorName(), 64);
            buf.writeEnum(line.kind());
            buf.writeUtf(line.itemId(), 256);
            buf.writeVarInt(line.count());
            buf.writeLong(line.amount());
        }
    }

    public static StallLedgerPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int page = buf.readVarInt();
        int pages = buf.readVarInt();
        int size = Math.min(StallLedger.PAGE_SIZE, Math.max(0, buf.readVarInt()));
        List<Line> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(new Line(buf.readLong(), buf.readUUID(), buf.readUtf(64), buf.readEnum(Kind.class), buf.readUtf(256),
                    buf.readVarInt(), buf.readLong()));
        }
        return new StallLedgerPacket(pos, page, pages, lines);
    }

    public static void handle(StallLedgerPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.oneforallsurvival.economy.client.StallLedgerCache.accept(packet));
    }
}
