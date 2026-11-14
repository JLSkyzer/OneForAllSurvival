package dev.oneforallsurvival.economy.town;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Le tableau de la mairie : un panneau sur pieds qui affiche, sur sa face avant, le solde public du trésor et les
 * salaires dus par la ville (lus des comptes reçus du serveur, voir {@link TownFinances}). Aucune donnée, aucun écran :
 * un clic droit donne les mêmes comptes dans le chat.
 *
 * <ul>
 *   <li><b>Pose</b> ({@link TownBlockRules#mayPlaceBoard}) : le maire ou l'équipe, sans limite de nombre. Refusée
 *       sans joueur (distributeur) et pour un faux joueur.
 *   <li><b>Casse</b> ({@link TownBlockRules#moveRefusal}) : un ingénieur, le maire ou l'équipe ; en créatif il faut
 *       être accroupi ; les autres n'ont aucune progression et {@link #onBreak} annule tout bris. Comme le trésor :
 *       dureté -1, résistance maximale, piston bloqué, tags du Wither et du dragon ; le butin est le bloc seul.
 *   <li><b>Forme</b> : trois boîtes (le panneau et deux pieds), celles du modèle ; le bloc ne cache pas ses voisins
 *       ({@code noOcclusion} à l'enregistrement).
 * </ul>
 * Point d'extension du lot du maire : annonces et registre des décisions, dans {@link TownBoardBlockEntity}.
 */
public final class TownBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final String MESSAGE = "message.oneforallsurvival.town_board.";

    /**
     * Les boîtes du modèle tourné vers le nord (px) : le panneau, épais de 2, à 5 px du sol, et deux pieds de 2 x 2.
     * Doit rester celui de {@code models/block/town_board.json} ({@code laFormeSuitLeModele}).
     */
    private static final double[][] BOXES_NORTH = {
            {0, 5, 6, 16, 15, 8},
            {2, 0, 6, 4, 5, 8},
            {12, 0, 6, 14, 5, 8},
    };

    /** La forme de chaque orientation ; la rotation du blockstate est horaire vue d'en haut (nord vers est). */
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        double[][] boxes = BOXES_NORTH;
        for (Direction direction : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                shape = Shapes.or(shape, Shapes.box(b[0] / 16.0D, b[1] / 16.0D, b[2] / 16.0D, b[3] / 16.0D, b[4] / 16.0D,
                        b[5] / 16.0D));
            }
            SHAPES[direction.get2DDataValue()] = shape;
            double[][] turned = new double[boxes.length][];
            for (int i = 0; i < boxes.length; i++) {
                double[] b = boxes[i];
                turned[i] = new double[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
            }
            boxes = turned;
        }
    }

    public TownBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TownBoardBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------------------------------------
    // Pose
    // ---------------------------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player instanceof FakePlayer) {
            return null;
        }
        if (context.getLevel() instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && !TownBlockRules.mayPlaceBoard(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable(MESSAGE + "place_denied"), true);
            // Le client a prédit la pose et retiré l'objet de sa main : on lui renvoie l'inventaire réel.
            serverPlayer.containerMenu.sendAllDataToRemote();
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ---------------------------------------------------------------------------------------------
    // Clic droit : les comptes dans le chat, pour tout joueur
    // ---------------------------------------------------------------------------------------------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(TownFinances.describe(TownFinances.of(serverPlayer.server)));
        }
        return InteractionResult.CONSUME;
    }

    // ---------------------------------------------------------------------------------------------
    // Casse
    // ---------------------------------------------------------------------------------------------

    /** Dureté -1 : seuls ceux qui peuvent le déplacer ont une progression ; le serveur tranche ({@link #onBreak}). */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return TownBlockRules.moveProgress(player, state, pos);
    }

    /** Au début de la casse (côté serveur), dit pourquoi elle n'avancera pas. */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            tellWhyNot(serverPlayer);
        }
    }

    private static void tellWhyNot(ServerPlayer player) {
        String why = TownBlockRules.moveRefusal(player);
        if (why != null) {
            player.displayClientMessage(Component.translatable(MESSAGE + why), true);
        }
    }

    /**
     * Filet de sécurité : un bris qui n'est pas permis (pas le droit de déplacer, ou créatif sans être accroupi) est
     * annulé, quelle que soit la manière dont le joueur s'y prend. Branché sur le bus de Forge.
     */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof TownBoardBlock) || !(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        if (TownBlockRules.moveRefusal(event.getPlayer()) != null) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                tellWhyNot(player);
            }
        }
    }
}
