package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.economy.town.TownFinances.Snapshot;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Serveur vers client : les comptes publics de la ville (tableau de la mairie), publics par nature. */
public record TownFinancesPacket(long balance, long owedTotal, int owedCount) {

    public static TownFinancesPacket of(Snapshot snapshot) {
        return new TownFinancesPacket(snapshot.balance(), snapshot.owedTotal(), snapshot.owedCount());
    }

    public Snapshot snapshot() {
        return new Snapshot(balance, owedTotal, owedCount);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(balance);
        buf.writeLong(owedTotal);
        buf.writeVarInt(owedCount);
    }

    public static TownFinancesPacket decode(FriendlyByteBuf buf) {
        return new TownFinancesPacket(buf.readLong(), buf.readLong(), buf.readVarInt());
    }

    public static void handle(TownFinancesPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.oneforallsurvival.economy.client.TownFinancesCache.set(packet.snapshot()));
    }
}
