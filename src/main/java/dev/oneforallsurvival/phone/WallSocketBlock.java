package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;

/**
 * La prise murale : une plaque de 2 px fixée au mur, machine électrique de la ville, sur laquelle on branche un
 * téléphone pour le recharger. {@code FACING} est la face qui regarde la pièce ; le mur est derrière, du côté opposé.
 *
 * <p>Clic droit : téléphone en main et prise vide, la pile entière passe dans la prise ; main vide et prise occupée,
 * le téléphone revient en main ; tout le reste (autre objet, second téléphone) est refusé avec un message. Le téléphone
 * ne quitte la prise que par la main ou par {@link #onRemove} (casse, explosion, mur retiré) : jamais perdu, jamais
 * copié. Aucune capability d'objets, et un faux joueur est ignoré : ni entonnoir ni machine ne l'en sort.
 */
public final class WallSocketBlock extends ElectricMachineBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty PLUGGED = BooleanProperty.create("plugged");

    /**
     * La plaque : 8 px de large, 10 de haut, 2 d'épaisseur, contre le mur ; branchée, le téléphone dépasse d'1 px
     * (4 x 7). Mêmes boîtes que les modèles {@code wall_socket*}.
     */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> PLUGGED_SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape plate = box(facing, 4, 3, 0, 12, 13, 2);
            SHAPES.put(facing, plate);
            PLUGGED_SHAPES.put(facing, Shapes.or(plate, box(facing, 6, 4, 2, 10, 11, 3)));
        }
    }

    public WallSocketBlock(Properties properties) {
        super(properties, () -> PhoneRegistry.WALL_SOCKET_BE.get());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PLUGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, PLUGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(PLUGGED) ? PLUGGED_SHAPES : SHAPES).get(state.getValue(FACING));
    }

    /**
     * Une boîte décrite pour une prise qui regarde au sud (z = distance au mur), tournée vers {@code facing} comme le
     * fait l'état de bloc ({@code y} de 90 par quart de tour).
     */
    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case NORTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case WEST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallSocketBlockEntity(pos, state);
    }

    /** Contre la face cliquée d'un mur seulement : un clic sur le sol ou le plafond ne pose rien. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, face);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** Il faut derrière la prise un mur dont la face tournée vers la pièce est pleine. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    /** Le mur retiré, la prise tombe : le jeu la casse (elle laisse son objet) et {@link #onRemove} rend le téléphone. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        // Un faux joueur (déployeur de Create, par exemple) est une machine : il ne branche ni ne reprend rien.
        if (player instanceof FakePlayer || !(level.getBlockEntity(pos) instanceof WallSocketBlockEntity socket)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        // Le client ne reçoit pas le téléphone branché : il lit PLUGGED. Sans cela il croirait la prise vide, essaierait
        // aussi la main secondaire, et le serveur y brancherait aussitôt le téléphone qu'elle tient.
        boolean occupied = level.isClientSide ? state.getValue(PLUGGED) : !socket.phone().isEmpty();
        if (!occupied && held.isEmpty()) {
            // Rien à brancher ni à reprendre : la main secondaire peut tenir le téléphone.
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!occupied && held.is(PhoneRegistry.PHONE.get())) {
            socket.setPhone(held.copy());
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else if (occupied && held.isEmpty()) {
            player.setItemInHand(hand, socket.phone());
            socket.setPhone(ItemStack.EMPTY);
        } else {
            player.displayClientMessage(Component.translatable("message.oneforallsurvival.wall_socket.busy"), true);
        }
        return InteractionResult.CONSUME;
    }

    /** La prise quitte le monde (casse, explosion, mur retiré, remplacement) : le téléphone tombe, en un exemplaire. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WallSocketBlockEntity socket) {
            ItemStack phone = socket.phone();
            if (!phone.isEmpty()) {
                socket.setPhone(ItemStack.EMPTY);
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), phone);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
