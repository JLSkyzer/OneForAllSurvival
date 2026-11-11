package dev.oneforallsurvival.explore;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Le mécanisme ancien, que l'équipe place dans les ruines. Dureté -1, résistance maximale et poussée au piston bloquée
 * (voir {@link ExploreRegistry}) : il ne se casse qu'en créatif. {@code stage} suit la progression (0 à 3) et
 * {@code active} le verrou : une fois vrai il ne retombe que par {@link MechanismBlockEntity#reset()}. Actif, il est une
 * source de redstone de 15 de tous côtés. Sans butin ni recette.
 */
public final class MechanismBlock extends Block implements EntityBlock {

    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, MechanismRules.MAX_STAGE);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** Lumière émise : les veines éclairent de plus en plus (0, 3, 5, 7 selon l'étage), 10 une fois actif. */
    public static int lightLevel(BlockState state) {
        if (state.getValue(ACTIVE)) {
            return 10;
        }
        return new int[] {0, 3, 5, 7}[state.getValue(STAGE)];
    }

    public MechanismBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, ACTIVE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechanismBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ExploreRegistry.MECHANISM_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((MechanismBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }
}
