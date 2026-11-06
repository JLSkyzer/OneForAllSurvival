package dev.oneforallsurvival.bench;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.role.RoleGate;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** L'établi d'ingénieur : une machine électrique dont le clic droit ouvre le menu de commande. */
public final class EngineerBenchBlock extends ElectricMachineBlock {

    /** Le devant de l'établi (le côté de l'étau et de la lampe vers le joueur qui le pose). */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Les boîtes du modèle tourné vers le nord, en seizièmes de bloc : pieds et jambes des tréteaux, plateau,
     * étagère, panneau, étau et lampe. Elles collent aux éléments de {@code engineer_bench_*.json}.
     */
    private static final double[][] BOXES = {
        {0, 0, 0, 3, 2, 16}, {13, 0, 0, 16, 2, 16}, {0, 2, 4, 3, 9, 12}, {13, 2, 4, 16, 9, 12},
        {0, 9, 1, 16, 12, 15}, {3, 3, 3, 13, 4, 13}, {4, 4, 6, 12, 8, 10}, {6, 4, 4, 10, 8, 6}, {6, 4, 10, 10, 8, 12},
        {1, 12, 2, 10, 13, 9}, {11, 12, 2, 15, 15, 10}, {1, 12, 10, 6, 16, 15}, {3, 8, 15, 4, 13, 16},
    };
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // Une rotation de 90 degrés dans le sens horaire (vu d'en haut) envoie (x, z) vers (16 - z, x), comme "y": 90.
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int quarts = (d.get2DDataValue() + 2) % 4; // le nord (2DDataValue 2) est la pose du modèle
            VoxelShape shape = Shapes.empty();
            for (double[] b : BOXES) {
                double x1 = b[0];
                double z1 = b[2];
                double x2 = b[3];
                double z2 = b[5];
                for (int i = 0; i < quarts; i++) {
                    double nx1 = 16 - z2;
                    double nx2 = 16 - z1;
                    z1 = x1;
                    z2 = x2;
                    x1 = nx1;
                    x2 = nx2;
                }
                shape = Shapes.or(shape, Block.box(x1, b[1], z1, x2, b[4], z2));
            }
            SHAPES.put(d, shape);
        }
    }

    public EngineerBenchBlock(Properties properties) {
        super(properties, () -> BenchRegistry.ENGINEER_BENCH_BE.get());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EngineerBenchBlockEntity(pos, state);
    }

    /** Ouvre le menu de l'établi {@code pos} au joueur, avec le catalogue et la file ; le rôle est déjà vérifié. */
    static void open(ServerPlayer player, BlockPos pos) {
        BenchNetwork.sendCatalogIfNeeded(player);
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, p) -> new EngineerBenchMenu(id, inventory, pos),
                        Component.translatable(BenchRegistry.ENGINEER_BENCH.get().getDescriptionId())),
                buf -> buf.writeBlockPos(pos));
        BenchNetwork.sendQueue(player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // Réservé à l'ingénieur : le refus est annoncé par RoleGate.
        if (player instanceof ServerPlayer serverPlayer && RoleGate.requireEngineer(serverPlayer)) {
            open(serverPlayer, pos);
        }
        return InteractionResult.CONSUME;
    }
}
