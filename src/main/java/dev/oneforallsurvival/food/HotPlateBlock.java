package dev.oneforallsurvival.food;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * La plaque électrique : une machine sans menu qui chauffe l'ustensile de Farmer's Delight posé dessus. Elle porte
 * {@code LIT}, la propriété que Farmer's Delight lit sur ses sources de chaleur (le tag {@code heat_sources} la
 * contient). Elle ne brûle ni n'enflamme rien.
 */
public final class HotPlateBlock extends ElectricMachineBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Lumière d'une plaque allumée. */
    public static final int LIGHT_LEVEL = 7;

    public HotPlateBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? LIGHT_LEVEL : 0),
                () -> FoodRegistry.HOT_PLATE_BE.get());
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HotPlateBlockEntity(pos, state);
    }
}
