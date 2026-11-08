package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.machine.InlineEnergyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le disjoncteur d'abonné : un bloc en ligne sans réserve ni usure, qui laisse passer le courant tant qu'il est
 * enclenché ({@link BreakerBlock#CLOSED}). Il porte le nom de l'abonné, qui suit l'objet quand on le démonte.
 */
public final class BreakerBlockEntity extends InlineEnergyBlockEntity implements SubscriberHolder {

    private static final String TAG_SUBSCRIBER = "subscriber";

    private String subscriber = "";

    public BreakerBlockEntity(BlockPos pos, BlockState state) {
        super(GridRegistry.BREAKER_BE.get(), pos, state, GridConfig.breakerMaxPerTick());
    }

    /** Le courant passe quand le disjoncteur est enclenché ; l'état vit dans le bloc, donc dans la sauvegarde du monde. */
    @Override
    public boolean isOpen() {
        return getBlockState().getValue(BreakerBlock.CLOSED);
    }

    @Override
    public String subscriber() {
        return subscriber;
    }

    @Override
    public void setSubscriber(String name) {
        subscriber = name.trim();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!subscriber.isEmpty()) {
            tag.putString(TAG_SUBSCRIBER, subscriber);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        subscriber = tag.getString(TAG_SUBSCRIBER);
    }
}
