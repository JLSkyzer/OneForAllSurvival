package dev.oneforallsurvival.economy.stall;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.network.NetworkHooks;
import dev.oneforallsurvival.economy.EconomyRegistry;

/**
 * L'étal de marché : n'importe qui le fabrique et le pose, le poseur en est le propriétaire, autant d'étals qu'il
 * veut.
 *
 * <ul>
 *   <li><b>Ouverture</b> : le propriétaire et l'équipe (niveau 2) ouvrent la gestion ({@link #mayManage}) ; les
 *       autres, la vitrine ({@link StallShopMenu}). Accroupi, main vide, celui qui gère ouvre aussi la vitrine, pour
 *       se voir comme un client.
 *   <li><b>Casse</b> : seulement quand le stock est vide <i>et</i> la caisse à zéro, et seulement par ceux qui
 *       gèrent ({@link #mayBreak}) : l'équipe vide d'abord, comme tout le monde. Le bloc est déclaré incassable
 *       (dureté -1, résistance maximale, piston bloqué, tags du Wither et du dragon) ; la progression de casse est
 *       rendue à ceux qui y ont droit par {@link #getDestroyProgress}, et {@link #onBreak} annule tout autre bris,
 *       créatif compris. Il ne lâche que son bloc : son contenu est nul quand il se casse.
 * </ul>
 *
 * <p>Forme provisoire : le comptoir seul (le modèle définitif, avec montants et auvent tenus dans le cube, vient à la
 * tâche des visuels). {@code noOcclusion()} et la forme sont écrits pour un bloc qui n'occupe pas le cube entier.
 */
public final class MarketStallBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final String MESSAGE = "message.oneforallsurvival.stall.";

    /** Distance maximale du joueur à l'étal, au carré (8 blocs) : celle des écrans et des paquets. */
    public static final double MAX_DISTANCE_SQR = 64.0D;

    /** Le comptoir : le bloc entier en largeur, 12 pixels de haut. */
    private static final VoxelShape COUNTER = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    public MarketStallBlock(Properties properties) {
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
        return COUNTER;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarketStallBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------------------------------------
    // Droits
    // ---------------------------------------------------------------------------------------------

    /**
     * Ce joueur gère-t-il l'étal de {@code owner} ? Le propriétaire et l'équipe (niveau 2).
     *
     * <p>Point d'extension : le crochetage du policier (lot suivant) s'ajoute ici. Gérer donne le droit de poser et
     * retirer des objets, déposer et retirer des pièces, rien de plus.
     */
    public static boolean mayManage(Player player, @Nullable UUID owner) {
        return player.hasPermissions(2) || (owner != null && owner.equals(player.getUUID()));
    }

    /** L'étal est-il vide : aucun objet en stock, aucune pièce en caisse ? */
    public static boolean isEmpty(MarketStallBlockEntity stall) {
        return stall.stock().isEmpty() && stall.cash().isEmpty();
    }

    /**
     * Ce joueur peut-il casser cet étal ? Il faut gérer l'étal, et qu'il soit vide. Côté client le contenu est
     * inconnu (jamais envoyé) : seul le droit compte, le serveur tranche.
     */
    public static boolean mayBreak(Player player, @Nullable MarketStallBlockEntity stall, boolean serverSide) {
        UUID owner = stall == null ? null : stall.owner();
        if (!mayManage(player, owner)) {
            return false;
        }
        return stall == null || !serverSide || isEmpty(stall);
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
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel && placer instanceof ServerPlayer player && !(player instanceof FakePlayer)
                && level.getBlockEntity(pos) instanceof MarketStallBlockEntity be) {
            be.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Ouverture
    // ---------------------------------------------------------------------------------------------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MarketStallBlockEntity be) {
            if (mayManage(serverPlayer, be.owner()) && !serverPlayer.isSecondaryUseActive()) {
                openManage(serverPlayer, pos);
            } else {
                openShop(serverPlayer, pos);
            }
        }
        return InteractionResult.CONSUME;
    }

    /** Ouvre la gestion (onglet Stock) de l'étal en {@code pos} si le joueur y a droit. @return vrai si elle s'est ouverte */
    public static boolean openManage(ServerPlayer player, BlockPos pos) {
        MarketStallBlockEntity be = manageable(player, pos);
        if (be == null) {
            return false;
        }
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, p) -> new StallOwnerMenu(id, inventory, pos, be), title(be)),
                buf -> buf.writeBlockPos(pos));
        return true;
    }

    /** Ouvre la vitrine de l'étal en {@code pos}, pour tout joueur. @return vrai si elle s'est ouverte */
    public static boolean openShop(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(EconomyRegistry.MARKET_STALL.get())
                || !(level.getBlockEntity(pos) instanceof MarketStallBlockEntity be)) {
            return false;
        }
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, p) -> new StallShopMenu(id, inventory, pos, be), title(be)),
                buf -> buf.writeBlockPos(pos));
        return true;
    }

    /** Ouvre la caisse de l'étal en {@code pos} si le joueur y a droit. @return vrai si elle s'est ouverte */
    public static boolean openCash(ServerPlayer player, BlockPos pos) {
        MarketStallBlockEntity be = manageable(player, pos);
        if (be == null) {
            return false;
        }
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, p) -> new StallCashMenu(id, inventory, pos, be), title(be)),
                buf -> buf.writeBlockPos(pos));
        return true;
    }

    /** L'étal en {@code pos} s'il est là, à portée, et que ce joueur le gère ; sinon nul (le joueur est prévenu si c'est le droit). */
    @Nullable
    private static MarketStallBlockEntity manageable(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(EconomyRegistry.MARKET_STALL.get())
                || !(level.getBlockEntity(pos) instanceof MarketStallBlockEntity be)) {
            return null;
        }
        if (!mayManage(player, be.owner())) {
            player.displayClientMessage(Component.translatable(MESSAGE + "not_yours"), true);
            return null;
        }
        return be;
    }

    private static Component title(MarketStallBlockEntity be) {
        String name = be.ownerName();
        return name.isEmpty() ? Component.translatable(EconomyRegistry.MARKET_STALL.get().getDescriptionId())
                : Component.translatable("container.oneforallsurvival.stall.of", name);
    }

    // ---------------------------------------------------------------------------------------------
    // Casse
    // ---------------------------------------------------------------------------------------------

    /**
     * Dureté -1 : seuls ceux qui ont le droit de le casser en ont une progression, calculée comme celle d'un bloc
     * de bois sans outil exigé. Pour tous les autres, zéro.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        MarketStallBlockEntity be = level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall ? stall : null;
        if (!mayBreak(player, be, level instanceof ServerLevel)) {
            return 0.0F;
        }
        return player.getDigSpeed(state, pos) / 2.5F / 30.0F;
    }

    /** Au début de la casse (côté serveur), dit pourquoi elle n'avancera pas. */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MarketStallBlockEntity be) {
            tellWhyNotBreakable(serverPlayer, be);
        }
    }

    private static void tellWhyNotBreakable(ServerPlayer player, MarketStallBlockEntity be) {
        if (mayBreak(player, be, true)) {
            return;
        }
        player.displayClientMessage(Component.translatable(MESSAGE + (mayManage(player, be.owner()) ? "not_empty" : "not_yours")),
                true);
    }

    /**
     * Filet de sécurité : quelle que soit la manière dont un joueur s'y prend (créatif compris), un bris qui n'est
     * pas permis par {@link #mayBreak} est annulé. Branché sur le bus de Forge.
     */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof MarketStallBlock) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof MarketStallBlockEntity be)) {
            return;
        }
        if (!mayBreak(event.getPlayer(), be, true)) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                tellWhyNotBreakable(player, be);
            }
        }
    }
}
