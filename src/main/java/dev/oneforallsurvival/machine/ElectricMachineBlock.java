package dev.oneforallsurvival.machine;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Bloc de base des machines électriques. Il porte l'état de la machine dans le BlockState et
 * fait tourner son block entity côté serveur seulement. La sous-classe fournit {@code newBlockEntity}.
 */
public abstract class ElectricMachineBlock extends BaseEntityBlock {

    public static final EnumProperty<MachineState> STATE = EnumProperty.create("state", MachineState.class);

    private final Supplier<BlockEntityType<? extends ElectricMachineBlockEntity>> blockEntityType;

    protected ElectricMachineBlock(Properties properties,
            Supplier<BlockEntityType<? extends ElectricMachineBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        registerDefaultState(stateDefinition.any().setValue(STATE, MachineState.STOPPED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, (BlockEntityType<ElectricMachineBlockEntity>) blockEntityType.get(),
                (lvl, pos, st, be) -> be.serverTick(lvl, pos, st));
    }
}
