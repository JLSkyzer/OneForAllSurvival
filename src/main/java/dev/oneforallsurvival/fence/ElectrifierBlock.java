package dev.oneforallsurvival.fence;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** L'électrificateur : la machine qui alimente les clôtures électriques qui le touchent par une face. */
public final class ElectrifierBlock extends ElectricMachineBlock {

    public ElectrifierBlock(Properties properties) {
        super(properties, () -> FenceRegistry.ELECTRIFIER_BE.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectrifierBlockEntity(pos, state);
    }
}
