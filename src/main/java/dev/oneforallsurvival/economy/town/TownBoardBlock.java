package dev.oneforallsurvival.economy.town;

import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import org.slf4j.Logger;

/**
 * Le tableau de la mairie : un panneau mince de 3 blocs de large et 2 de haut, sur deux pieds, qui affiche sur sa face
 * avant le solde public du trésor et les salaires dus par la ville (comptes reçus du serveur, voir {@link TownFinances}),
 * et plus tard les annonces et le registre des décisions du maire ({@link TownBoardBlockEntity#notices()}). Un clic droit
 * sur n'importe quelle partie donne les mêmes comptes dans le chat.
 *
 * <ul>
 *   <li><b>Six parties</b> ({@link TownBoardPart}) : la partie du bas au milieu est la maîtresse, posée à l'endroit
 *       visé ; le tableau s'étend d'un bloc à gauche, d'un à droite et d'un vers le haut, face au joueur. Seule la
 *       maîtresse porte une entité de bloc (rendu et données) ; les autres renvoient vers elle ({@link #masterPos}).
 *   <li><b>Pose</b> ({@link TownBlockRules#mayPlaceBoard}) : le maire ou l'équipe, sans limite de nombre, et seulement
 *       si les six emplacements sont libres et remplaçables (sinon : refus, message, objet gardé). Refusée sans joueur
 *       (distributeur) et pour un faux joueur.
 *   <li><b>Casse</b> ({@link TownBlockRules#moveRefusal}) : un ingénieur, le maire ou l'équipe ; en créatif il faut être
 *       accroupi ; les autres n'ont aucune progression et {@link #onBreak} annule tout bris. Casser une partie, quelle
 *       qu'elle soit, retire tout le tableau : chaque partie disparaît quand une voisine du tableau n'est plus là
 *       ({@link #updateShape}). Seule la maîtresse rend un objet (table de butin), et rien en créatif
 *       ({@link #playerWillDestroy}) : un tableau rend toujours <b>un seul</b> objet. Comme le trésor : dureté -1,
 *       résistance maximale (explosions), piston bloqué, tags du Wither et du dragon.
 *   <li><b>Parties orphelines</b> (posées par {@code /setblock}, ou ancien tableau d'un bloc d'avant ce format, dont
 *       l'état est relu comme une maîtresse seule) : vérifiées un tick après leur pose ou leur chargement ({@link #tick}).
 *       Une maîtresse seule se complète si les cinq autres emplacements sont de l'air, sinon elle se retire en rendant
 *       son objet ; une autre partie sans maîtresse se retire sans rien rendre.
 *   <li><b>Forme</b> : celle du modèle de chaque partie (panneau, chapeau, pieds), {@code noOcclusion} à
 *       l'enregistrement.
 * </ul>
 */
public final class TownBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final Logger LOG = LogUtils.getLogger();

    public static final String MESSAGE = "message.oneforallsurvival.town_board.";

    public static final EnumProperty<TownBoardPart> PART = EnumProperty.create("part", TownBoardPart.class);

    /**
     * Les boîtes du tableau entier tourné vers le nord, en pixels, la maîtresse occupant x 0 à 16 et y 0 à 16 (le
     * tableau va de x -16 à 32 et de y 0 à 32 ; la droite du lecteur est l'ouest, -x). Le panneau, épais de 2, de 4 à 30
     * px de haut ; un chapeau de 4 px de profondeur au-dessus ; deux pieds sous les colonnes de côté, sur un patin. Doit
     * rester celui de {@code tools/gen_textures_e3.py} (modèles des parties ; {@code laFormeSuitLeModele} le vérifie).
     */
    static final double[][] BOARD_NORTH = {
            {-16, 4, 6, 32, 30, 8},
            {-16, 30, 5, 32, 31, 9},
            {-11, 1, 6, -9, 4, 8},
            {-12, 0, 5, -8, 1, 9},
            {25, 1, 6, 27, 4, 8},
            {24, 0, 5, 28, 1, 9},
    };

    /** La forme de chaque partie dans chaque orientation ; la rotation du blockstate est horaire vue d'en haut. */
    private static final Map<TownBoardPart, VoxelShape[]> SHAPES = new EnumMap<>(TownBoardPart.class);

    static {
        for (TownBoardPart part : TownBoardPart.values()) {
            double[][] boxes = partBoxesNorth(part);
            VoxelShape[] shapes = new VoxelShape[4];
            for (Direction direction : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                VoxelShape shape = Shapes.empty();
                for (double[] b : boxes) {
                    shape = Shapes.or(shape, Shapes.box(b[0] / 16.0D, b[1] / 16.0D, b[2] / 16.0D, b[3] / 16.0D,
                            b[4] / 16.0D, b[5] / 16.0D));
                }
                shapes[direction.get2DDataValue()] = shape;
                double[][] turned = new double[boxes.length][];
                for (int i = 0; i < boxes.length; i++) {
                    double[] b = boxes[i];
                    turned[i] = new double[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
                }
                boxes = turned;
            }
            SHAPES.put(part, shapes);
        }
    }

    /** Les boîtes du tableau qui tombent dans cette partie, en pixels de la partie (orientation nord). */
    static double[][] partBoxesNorth(TownBoardPart part) {
        double x0 = -16.0D * part.column();
        double y0 = 16.0D * part.row();
        return java.util.Arrays.stream(BOARD_NORTH)
                .map(b -> new double[] {Math.max(b[0], x0) - x0, Math.max(b[1], y0) - y0, b[2],
                        Math.min(b[3], x0 + 16) - x0, Math.min(b[4], y0 + 16) - y0, b[5]})
                .filter(b -> b[0] < b[3] && b[1] < b[4])
                .toArray(double[][]::new);
    }

    public TownBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, TownBoardPart.MASTER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
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
        return SHAPES.get(state.getValue(PART))[state.getValue(FACING).get2DDataValue()];
    }

    /** Seule la maîtresse porte une entité de bloc. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART).isMaster() ? new TownBoardBlockEntity(pos, state) : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Les parties
    // ---------------------------------------------------------------------------------------------

    /** La position de la maîtresse du tableau dont {@code state} est une partie. */
    public static BlockPos masterPos(BlockState state, BlockPos pos) {
        return state.getValue(PART).master(pos, state.getValue(FACING));
    }

    /** L'état de la partie {@code part} d'un tableau tourné vers {@code facing}. */
    public BlockState partState(Direction facing, TownBoardPart part) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART, part);
    }

    /** Vrai si {@code state} est la partie {@code part} d'un tableau tourné vers {@code facing}. */
    private boolean isPart(BlockState state, Direction facing, TownBoardPart part) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
    }

    /** Vrai si les six parties du tableau de maîtresse {@code master} sont en place. */
    public boolean isWhole(BlockGetter level, BlockPos master, Direction facing) {
        for (TownBoardPart part : TownBoardPart.values()) {
            if (!isPart(level.getBlockState(part.from(master, facing)), facing, part)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pose les six parties d'un tableau, la maîtresse d'abord (démonstrations, essais, et maîtresse seule qui se
     * complète). Ne vérifie ni les droits ni la place : réservé au code du serveur.
     */
    public void placeWhole(Level level, BlockPos master, Direction facing) {
        level.setBlock(master, partState(facing, TownBoardPart.MASTER), Block.UPDATE_ALL);
        placeOthers(level, master, facing);
    }

    private void placeOthers(Level level, BlockPos master, Direction facing) {
        for (TownBoardPart part : TownBoardPart.values()) {
            if (!part.isMaster()) {
                level.setBlock(part.from(master, facing), partState(facing, part), Block.UPDATE_ALL);
            }
        }
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
            refuse(serverPlayer, "place_denied");
            return null;
        }
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos master = context.getClickedPos();
        if (!roomFor(context, master, facing, player)) {
            if (player instanceof ServerPlayer serverPlayer && context.getLevel() instanceof ServerLevel) {
                refuse(serverPlayer, "no_room");
            }
            return null;
        }
        return partState(facing, TownBoardPart.MASTER);
    }

    /** Les cinq autres emplacements sont dans le monde, libres de toute entité, et remplaçables. */
    private boolean roomFor(BlockPlaceContext context, BlockPos master, Direction facing, Player player) {
        Level level = context.getLevel();
        for (TownBoardPart part : TownBoardPart.values()) {
            if (part.isMaster()) {
                continue;
            }
            BlockPos pos = part.from(master, facing);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(BlockPlaceContext.at(context, pos, context.getClickedFace()))
                    || !level.isUnobstructed(partState(facing, part), pos, CollisionContext.of(player))) {
                return false;
            }
        }
        return true;
    }

    private static void refuse(ServerPlayer player, String why) {
        player.displayClientMessage(Component.translatable(MESSAGE + why), true);
        // Le client a peut-être prédit la pose et retiré l'objet de sa main : on lui renvoie l'inventaire réel.
        player.containerMenu.sendAllDataToRemote();
    }

    /** La maîtresse posée par un joueur : le serveur pose les cinq autres parties (le client les reçoit de lui). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && state.getValue(PART).isMaster()) {
            placeOthers(level, pos, state.getValue(FACING));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tenue du tableau : une partie part, tout part
    // ---------------------------------------------------------------------------------------------

    /**
     * Une partie dont une voisine du tableau n'est plus la bonne partie disparaît ; de proche en proche, tout le tableau
     * part. La maîtresse ainsi retirée rend son objet (sauf en créatif, voir {@link #playerWillDestroy}), les autres
     * parties ne rendent rien.
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        TownBoardPart expected = state.getValue(PART).neighbor(direction, facing);
        if (expected != null && !isPart(neighbor, facing, expected)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /**
     * En créatif, rien ne tombe : la maîtresse est retirée d'abord, sans butin, quand la partie cassée n'est pas elle
     * (sinon elle tomberait par {@link #updateShape} avec son objet).
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && !state.getValue(PART).isMaster()) {
            BlockPos master = masterPos(state, pos);
            BlockState masterState = level.getBlockState(master);
            if (isPart(masterState, state.getValue(FACING), TownBoardPart.MASTER)) {
                level.setBlock(master, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, master, Block.getId(masterState));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Toute partie nouvellement posée (y compris par {@code /setblock}) est vérifiée au tick suivant. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /**
     * Une partie dont le tableau n'est pas entier : la maîtresse se complète si les cinq autres emplacements sont de
     * l'air (ancien tableau d'un bloc, ou {@code /setblock}), sinon elle se retire en rendant son objet, et emporte ses
     * parties ; une autre partie laisse la maîtresse décider, ou se retire sans rien rendre s'il n'y en a pas.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        BlockPos master = masterPos(state, pos);
        if (isWhole(level, master, facing)) {
            return;
        }
        if (!state.getValue(PART).isMaster()) {
            if (isPart(level.getBlockState(master), facing, TownBoardPart.MASTER)) {
                level.scheduleTick(master, this, 1);
            } else {
                level.removeBlock(pos, false);
            }
            return;
        }
        boolean free = true;
        for (TownBoardPart part : TownBoardPart.values()) {
            BlockPos at = part.from(master, facing);
            if (!part.isMaster() && !isPart(level.getBlockState(at), facing, part)
                    && (!level.getBlockState(at).isAir() || level.isOutsideBuildHeight(at))) {
                free = false;
            }
        }
        if (free) {
            LOG.info("[tableau] tableau incomplet en {} : complété en 3 x 2", master);
            for (TownBoardPart part : TownBoardPart.values()) {
                BlockPos at = part.from(master, facing);
                if (!part.isMaster() && !isPart(level.getBlockState(at), facing, part)) {
                    level.setBlock(at, partState(facing, part), Block.UPDATE_ALL);
                }
            }
            return;
        }
        LOG.info("[tableau] tableau incomplet en {} sans la place de 3 x 2 : retiré, objet rendu", master);
        level.destroyBlock(master, true);
        for (TownBoardPart part : TownBoardPart.values()) {
            BlockPos at = part.from(master, facing);
            if (isPart(level.getBlockState(at), facing, part)) {
                level.removeBlock(at, false);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Clic droit : les comptes dans le chat, pour tout joueur, sur n'importe quelle partie
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
