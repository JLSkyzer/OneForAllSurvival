package dev.oneforallsurvival.economy.town;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.TreasuryAccount;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;

/**
 * Le bloc du trésor de la ville : un seul dans le monde. Le contenu, le registre et l'emplacement vivent dans
 * {@link TreasuryAccount}, pas dans le bloc ; le bloc n'est que la porte.
 *
 * <ul>
 *   <li><b>Pose</b> ({@link TownBlockRules#mayPlaceTreasury}) : l'équipe ; le maire et l'ingénieur seulement s'il
 *       n'existe aucun trésor vivant. Refusée s'il n'y a pas de joueur (distributeur, automate) et tant qu'un trésor
 *       vivant existe. Acceptée : l'emplacement est noté et une ligne « Trésor posé » va au registre.
 *   <li><b>Ouverture</b> : le maire et l'équipe ({@link TownBlockRules#mayOpenTreasury}), et seulement le bloc posé
 *       à l'emplacement noté ; un bloc posé autrement ({@code /setblock}) n'ouvre rien et le dit.
 *   <li><b>Casse</b> : comme le coffre-fort, dureté -1, résistance maximale, piston bloqué, tags du Wither et du
 *       dragon ; la progression n'est rendue qu'à ceux qui peuvent le déplacer ({@link TownBlockRules#mayMove}),
 *       en créatif il faut être accroupi, et {@link #onBreak} annule tout autre bris. Cassé : l'emplacement est
 *       effacé, une ligne « Trésor retiré » va au registre, le solde ne bouge pas ; en survie l'objet tombe (seul le
 *       trésor noté le lâche, pas un bloc de {@code /setblock} ; le
 *       butin n'est que le bloc), en créatif rien. Reposé, il redevient le trésor unique, contenu et registre compris.
 * </ul>
 * Sans emplacement, la taxe et les salaires continuent ; seule l'ouverture est impossible.
 */
public final class TreasuryBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MESSAGE = "message.oneforallsurvival.treasury.";

    /** Distance maximale du joueur au trésor, au carré (8 blocs) : celle de l'écran et des paquets. */
    public static final double MAX_DISTANCE_SQR = 64.0D;

    public TreasuryBlock(Properties properties) {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TreasuryBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------------------------------------
    // Le trésor unique
    // ---------------------------------------------------------------------------------------------

    /**
     * Un trésor tient-il quelque part ? Lecture seule, comme {@code SafeBlock.hasLiveSafe} : sans emplacement noté,
     * non ; dans un chunk non chargé, on ne peut pas vérifier, il compte ; si le bloc n'y est plus (retiré sans que
     * le jeu l'ait su), la note est périmée et ne compte pas, et la prochaine pose la remplace.
     */
    public static boolean hasLiveTreasury(MinecraftServer server) {
        Optional<GlobalPos> noted = TreasuryAccount.get(server).position();
        if (noted.isEmpty()) {
            return false;
        }
        ServerLevel level = server.getLevel(noted.get().dimension());
        if (level == null) {
            return false;
        }
        BlockPos pos = noted.get().pos();
        if (!level.isLoaded(pos)) {
            return true;
        }
        return level.getBlockState(pos).is(EconomyRegistry.TREASURY.get());
    }

    /** Le bloc en {@code pos} est-il celui de l'emplacement noté ? */
    public static boolean isNoted(ServerLevel level, BlockPos pos) {
        return TreasuryAccount.get(level.getServer()).position()
                .filter(noted -> noted.equals(GlobalPos.of(level.dimension(), pos))).isPresent();
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
        if (context.getLevel() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = level.getServer();
            boolean live = hasLiveTreasury(server);
            Component refusal = null;
            if (live && TownBlockRules.mayPlaceTreasury(serverPlayer, false)) {
                BlockPos at = TreasuryAccount.get(server).position().map(GlobalPos::pos).orElse(BlockPos.ZERO);
                refusal = Component.translatable(MESSAGE + "exists", at.getX(), at.getY(), at.getZ());
            } else if (!TownBlockRules.mayPlaceTreasury(serverPlayer, live)) {
                refusal = Component.translatable(MESSAGE + "place_denied");
            }
            if (refusal != null) {
                serverPlayer.displayClientMessage(refusal, true);
                // Le client a prédit la pose et retiré l'objet de sa main : on lui renvoie l'inventaire réel.
                serverPlayer.containerMenu.sendAllDataToRemote();
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && placer instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            TreasuryAccount account = TreasuryAccount.get(server.getServer());
            account.setPosition(GlobalPos.of(server.dimension(), pos));
            String name = player.getGameProfile().getName();
            account.note(new TreasuryLedger.Line(System.currentTimeMillis(), player.getUUID(), name,
                    TreasuryLedger.Kind.PLACED, 0L, pos.toShortString()));
            LOGGER.info("Economie : {} a pose le tresor en {} ({})", name, pos.toShortString(), server.dimension().location());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Retrait (casse, /setblock...) : l'emplacement noté s'efface, la ligne va au registre
    // ---------------------------------------------------------------------------------------------

    /** Retient le joueur qui casse, pour la ligne du registre (créatif compris : {@code removeBlock} passe ici). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && server.getBlockEntity(pos) instanceof TreasuryBlockEntity be) {
            be.setBreaker(player);
            be.setNotedWhenBroken(isNoted(server, pos));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Le butin (l'objet du trésor) ne tombe que si le bloc cassé était le trésor noté, relevé juste avant le retrait
     * ({@link #playerWillDestroy}) : un bloc posé par {@code /setblock} ailleurs ne fait pas naître d'objet de trésor.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(be instanceof TreasuryBlockEntity treasury) || !treasury.notedWhenBroken()) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && isNoted(server, pos)) {
            TreasuryAccount account = TreasuryAccount.get(server.getServer());
            account.clearPosition(GlobalPos.of(server.dimension(), pos));
            Player breaker = level.getBlockEntity(pos) instanceof TreasuryBlockEntity be ? be.breaker() : null;
            String name = breaker == null ? "" : breaker.getGameProfile().getName();
            account.note(new TreasuryLedger.Line(System.currentTimeMillis(), breaker == null ? null : breaker.getUUID(),
                    name, TreasuryLedger.Kind.REMOVED, 0L, pos.toShortString()));
            LOGGER.info("Economie : tresor retire de {} ({}) par {}", pos.toShortString(), server.dimension().location(),
                    breaker == null ? "le jeu" : name);
        }
        super.onRemove(state, level, pos, newState, moving);
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
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, pos);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Ouvre l'écran du trésor en {@code pos} si le joueur y a droit et si c'est bien le trésor de la ville ; sinon le
     * prévient.
     *
     * @return vrai si l'écran s'est ouvert
     */
    public static boolean open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.getBlockState(pos).is(EconomyRegistry.TREASURY.get())) {
            return false;
        }
        if (!TownBlockRules.mayOpenTreasury(player)) {
            player.displayClientMessage(Component.translatable(MESSAGE + "open_denied"), true);
            return false;
        }
        if (!isNoted(level, pos)) {
            player.displayClientMessage(Component.translatable(MESSAGE + "not_the_treasury"), true);
            return false;
        }
        TreasuryAccount account = TreasuryAccount.get(level.getServer());
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, p) -> new TreasuryMenu(id, inventory, pos, account),
                        Component.translatable("container.oneforallsurvival.treasury")),
                buf -> buf.writeBlockPos(pos));
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Casse
    // ---------------------------------------------------------------------------------------------

    /**
     * Dureté -1 : seuls ceux qui peuvent le déplacer en ont une progression, calculée comme celle d'un bloc de dureté
     * 5 sans outil exigé. Côté client les rôles sont inconnus : la progression s'affiche, le serveur tranche.
     */
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

    /** La raison du refus, ou nulle si la casse est permise. */
    @Nullable
    private static String refusal(Player player) {
        return TownBlockRules.moveRefusal(player);
    }

    private static void tellWhyNot(ServerPlayer player) {
        String why = refusal(player);
        if (why != null) {
            player.displayClientMessage(Component.translatable(MESSAGE + why), true);
        }
    }

    /**
     * Filet de sécurité : quelle que soit la manière dont un joueur s'y prend (créatif compris), un bris qui n'est
     * pas permis (pas le droit de déplacer, ou créatif sans être accroupi) est annulé. Branché sur le bus de Forge.
     */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof TreasuryBlock) || !(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        if (refusal(event.getPlayer()) != null) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                tellWhyNot(player);
            }
        }
    }
}
