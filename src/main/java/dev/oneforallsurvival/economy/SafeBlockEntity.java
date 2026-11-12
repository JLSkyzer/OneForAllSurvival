package dev.oneforallsurvival.economy;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ce que le bloc retient : seulement son propriétaire. Le contenu n'est pas ici, il vit dans {@link SafeAccounts}.
 * Le propriétaire est envoyé aux clients, pour qu'ils prédisent la casse (ils ne voient pas le compte).
 * Un coffre-fort posé sans joueur (commande de l'équipe) n'a pas de propriétaire : seule l'équipe l'ouvre.
 */
public final class SafeBlockEntity extends BlockEntity {

    private static final String TAG_OWNER = "Owner";

    @Nullable
    private UUID owner;

    public SafeBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyRegistry.SAFE_BE.get(), pos, state);
    }

    /** Le propriétaire, ou {@code null} pour un coffre-fort posé sans joueur. */
    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
