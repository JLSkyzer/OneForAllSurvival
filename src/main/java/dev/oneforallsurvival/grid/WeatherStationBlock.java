package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.grid.WeatherForecast.Trend;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * La station météo : une machine électrique orientée dont la face avant affiche la tendance (soleil, nuage, éclair), et
 * qui envoie un signal de redstone de 15 sur toutes ses faces quand elle annonce un orage. Sans marche (sans courant,
 * en panne), la face est éteinte et le signal nul, quelle que soit la dernière tendance lue.
 */
public final class WeatherStationBlock extends ElectricMachineBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Trend> TREND = EnumProperty.create("trend", Trend.class);
    public static final int STORM_SIGNAL = 15;

    public WeatherStationBlock(Properties properties) {
        super(properties, () -> GridRegistry.WEATHER_STATION_BE.get());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(TREND, Trend.STABLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, TREND);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WeatherStationBlockEntity(pos, state);
    }

    /** Le signal que porte cet état : 15 en marche avec un orage annoncé, 0 sinon. */
    public static int signalOf(BlockState state) {
        return state.getValue(STATE) == MachineState.RUNNING && state.getValue(TREND) == Trend.STORM_SOON
                ? STORM_SIGNAL : 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return signalOf(state);
    }
}
