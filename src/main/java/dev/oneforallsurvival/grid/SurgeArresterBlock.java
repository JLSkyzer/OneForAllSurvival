package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le parafoudre : une machine sans courant ni consommation qui encaisse la foudre à la place des machines voisines.
 * Il n'attire pas la foudre, le paratonnerre vanilla garde ce rôle.
 */
public final class SurgeArresterBlock extends ElectricMachineBlock {

    public SurgeArresterBlock(Properties properties) {
        super(properties, () -> GridRegistry.SURGE_ARRESTER_BE.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SurgeArresterBlockEntity(pos, state);
    }
}
