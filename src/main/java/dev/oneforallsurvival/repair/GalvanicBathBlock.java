package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * Le bain galvanique : une machine électrique dont le clic droit ouvre le menu à trois cases.
 * Le contenu est jeté quand le bloc disparaît, par quelque cause que ce soit.
 */
public final class GalvanicBathBlock extends ElectricMachineBlock {

    /**
     * Les boîtes du modèle, en seizièmes de bloc : pieds, traverses, cuve remplie jusqu'au liquide, parois, rebord,
     * montants, barre, anode, lampes et boîtier. Elles collent aux éléments de {@code galvanic_bath_*.json}.
     */
    private static final VoxelShape SHAPE = Stream.of(
            Block.box(1, 0, 1, 4, 6, 4), Block.box(12, 0, 1, 15, 6, 4), Block.box(1, 0, 12, 4, 6, 15),
            Block.box(12, 0, 12, 15, 6, 15),
            Block.box(4, 2, 1.5, 12, 4, 3.5), Block.box(4, 2, 12.5, 12, 4, 14.5), Block.box(1.5, 2, 4, 3.5, 4, 12),
            Block.box(12.5, 2, 4, 14.5, 4, 12),
            Block.box(1, 6, 1, 15, 12, 15),
            Block.box(1, 12, 1, 15, 13.5, 2), Block.box(1, 12, 14, 15, 13.5, 15), Block.box(1, 12, 2, 2, 13.5, 14),
            Block.box(14, 12, 2, 15, 13.5, 14),
            Block.box(0.5, 13.5, 0.5, 15.5, 14.25, 2.25), Block.box(0.5, 13.5, 13.75, 15.5, 14.25, 15.5),
            Block.box(0.5, 13.5, 2.25, 2.25, 14.25, 13.75), Block.box(13.75, 13.5, 2.25, 15.5, 14.25, 13.75),
            Block.box(7.25, 14.25, 0.75, 8.75, 15, 2.25), Block.box(7.25, 14.25, 13.75, 8.75, 15, 15.25),
            Block.box(7.5, 15, 1, 8.5, 16, 15), Block.box(7.25, 12, 4, 8.75, 15, 12),
            Block.box(1, 14.25, 1, 4, 14.75, 4), Block.box(12, 14.25, 12, 15, 14.75, 15),
            Block.box(1.5, 14.75, 1.5, 3.5, 16, 3.5), Block.box(12.5, 14.75, 12.5, 14.5, 16, 14.5),
            Block.box(5, 7.5, 15, 11, 10.5, 16))
            .reduce(Shapes.empty(), Shapes::or);

    public GalvanicBathBlock(Properties properties) {
        super(properties, () -> RepairRegistry.GALVANIC_BATH_BE.get());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GalvanicBathBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof GalvanicBathBlockEntity be) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, p) -> new GalvanicBathMenu(id, inventory, be, pos),
                            Component.translatable(getDescriptionId())),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        // Un simple changement d'état (marche, arrêt) garde le même bloc : on ne jette rien.
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof GalvanicBathBlockEntity be) {
            be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
