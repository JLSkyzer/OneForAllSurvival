package dev.oneforallsurvival.explore;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client vers serveur : régler le récepteur en {@code pos} sur {@code frequency}. Le serveur revalide tout : joueur
 * réel, chunk chargé, distance, bloc, bornes de la fréquence (1 à 9999).
 */
public record ReceiverFrequencyPacket(BlockPos pos, int frequency) {

    public static final String MESSAGE = "message.oneforallsurvival.receiver.";

    /** Ce qu'a donné le traitement d'un paquet. */
    public enum Result { SET, TOO_FAR, NOT_A_RECEIVER, OUT_OF_RANGE }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(frequency);
    }

    public static ReceiverFrequencyPacket decode(FriendlyByteBuf buf) {
        return new ReceiverFrequencyPacket(buf.readBlockPos(), buf.readVarInt());
    }

    /** Sur le thread du serveur : un client n'est jamais cru sur parole. */
    public static void handle(ReceiverFrequencyPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null) {
            return;
        }
        Result result = apply(sender, packet);
        if (result == Result.OUT_OF_RANGE) {
            sender.sendSystemMessage(Component.translatable(MESSAGE + "out_of_range",
                    ReceiverBlockEntity.MIN_FREQUENCY, ReceiverBlockEntity.MAX_FREQUENCY));
        }
    }

    /** Le contrôle et le réglage, sans le réseau. */
    public static Result apply(ServerPlayer sender, ReceiverFrequencyPacket packet) {
        ServerLevel level = sender.serverLevel();
        BlockPos pos = packet.pos();
        if (!level.isLoaded(pos) || sender.distanceToSqr(Vec3.atCenterOf(pos)) > ReceiverMenu.MAX_DISTANCE_SQR) {
            return Result.TOO_FAR;
        }
        if (!(level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver)) {
            return Result.NOT_A_RECEIVER;
        }
        return receiver.setFrequency(packet.frequency()) ? Result.SET : Result.OUT_OF_RANGE;
    }
}
