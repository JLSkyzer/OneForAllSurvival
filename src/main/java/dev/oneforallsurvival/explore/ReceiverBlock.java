package dev.oneforallsurvival.explore;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * Le récepteur de signaux. {@code listening} : il a de quoi écouter (voyant vert) ; {@code unread} : un message est
 * arrivé depuis la dernière ouverture de l'écran. Il s'inscrit à la {@link ReceiverIndex} en se posant et en sort en
 * partant. Sans recette : l'équipe le donne.
 */
public final class ReceiverBlock extends Block implements EntityBlock {

    public static final BooleanProperty LISTENING = BooleanProperty.create("listening");
    public static final BooleanProperty UNREAD = BooleanProperty.create("unread");

    public ReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LISTENING, false).setValue(UNREAD, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LISTENING, UNREAD);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReceiverBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ExploreRegistry.RECEIVER_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((ReceiverBlockEntity) be).serverTick((ServerLevel) lvl, pos, st);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level instanceof ServerLevel server) {
            ReceiverIndex.get(server).add(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            ReceiverIndex.get(server).remove(pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ReceiverBlockEntity be) {
            be.markRead();
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, p) -> new ReceiverMenu(id, inventory, be, pos),
                            Component.translatable(getDescriptionId())),
                    buf -> ReceiverMenu.writeOpenData(buf, pos, be));
        }
        return InteractionResult.CONSUME;
    }
}
