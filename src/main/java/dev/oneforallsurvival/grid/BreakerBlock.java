package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.grid.MeterLedger.EventKind;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.role.RoleGate;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le disjoncteur d'abonné, posé en ligne sur un câble (sortie devant, entrée derrière).
 * <ul>
 *   <li>Clic droit main vide : bascule, pour tout le monde ; refusé s'il est scellé.</li>
 *   <li>Ingénieur accroupi, main vide : ouvre l'écran d'inscription de l'abonné, ou retire le scellé (détruit).</li>
 *   <li>Ingénieur, scellé en main, disjoncteur inscrit et coupé : le scelle. Enclenché ou sans abonné : refusé.</li>
 * </ul>
 * Les autres joueurs reçoivent le message réservé à l'ingénieur pour tout geste d'ingénieur.
 */
public final class BreakerBlock extends InlineEnergyBlock {

    /** Vrai : le courant passe. */
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    /** Vrai : un scellé interdit de le réenclencher. */
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    public static final String MESSAGE = "message.oneforallsurvival.breaker.";

    public BreakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CLOSED, true).setValue(SEALED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CLOSED, SEALED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreakerBlockEntity(pos, state);
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getMainHandItem();
        boolean seal = held.is(GridRegistry.SEAL.get());
        if (!held.isEmpty() && !seal) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (seal) {
            seal(state, level, pos, serverPlayer, held);
        } else if (player.isShiftKeyDown()) {
            engineerGesture(state, level, pos, serverPlayer);
        } else {
            toggle(state, level, pos, serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    private static void toggle(BlockState state, Level level, BlockPos pos, ServerPlayer player) {
        if (state.getValue(SEALED)) {
            player.displayClientMessage(Component.translatable(MESSAGE + "sealed"), true);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.3F, 1.6F);
            return;
        }
        boolean nowClosed = !state.getValue(CLOSED);
        level.setBlock(pos, state.setValue(CLOSED, nowClosed), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, nowClosed ? 0.6F : 0.5F);
        player.displayClientMessage(Component.translatable(MESSAGE + (nowClosed ? "closed" : "cut")), true);
    }

    /** Accroupi, main vide : retirer le scellé s'il y en a un, sinon inscrire l'abonné. Réservé à l'ingénieur. */
    private static void engineerGesture(BlockState state, Level level, BlockPos pos, ServerPlayer player) {
        if (!RoleGate.requireEngineer(player)) {
            return;
        }
        if (state.getValue(SEALED)) {
            level.setBlock(pos, state.setValue(SEALED, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.5F, 1.0F);
            player.displayClientMessage(Component.translatable(MESSAGE + "unsealed"), true);
        } else if (level.getBlockEntity(pos) instanceof BreakerBlockEntity breaker) {
            SubscriberNamePacket.openScreen(player, pos, breaker);
        }
    }

    /**
     * Un scellé en main : seulement sur un disjoncteur inscrit, coupé et pas déjà scellé, et par l'ingénieur. Sans
     * abonné, sa casse ne serait notée au registre de personne : refusé.
     */
    private static void seal(BlockState state, Level level, BlockPos pos, ServerPlayer player, ItemStack held) {
        if (!RoleGate.requireEngineer(player)) {
            return;
        }
        if (state.getValue(SEALED)) {
            player.displayClientMessage(Component.translatable(MESSAGE + "already_sealed"), true);
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof BreakerBlockEntity breaker) || breaker.subscriber().isEmpty()) {
            player.displayClientMessage(Component.translatable(MESSAGE + "seal_needs_subscriber"), true);
            return;
        }
        if (state.getValue(CLOSED)) {
            player.displayClientMessage(Component.translatable(MESSAGE + "seal_needs_cut"), true);
            return;
        }
        level.setBlock(pos, state.setValue(SEALED, true), Block.UPDATE_ALL);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.3F, 1.8F);
        player.displayClientMessage(Component.translatable(MESSAGE + "seal_done"), true);
    }

    /**
     * Un disjoncteur inscrit garde son abonné dans son objet ({@code BlockEntityTag}, relu à la pose). Le scellé,
     * lui, ne suit jamais : l'objet rendu est libre.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BreakerBlockEntity breaker
                && !breaker.subscriber().isEmpty()) {
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    BlockItem.setBlockEntityData(drop, breaker.getType(), breaker.saveWithoutMetadata());
                }
            }
        }
        return drops;
    }

    /**
     * Un disjoncteur scellé et inscrit qui disparaît (cassé, remplacé) laisse « scellé brisé » au registre de son
     * abonné ; le relevé suivant le signale. Pas pour un simple changement d'état, ni pour un déplacement par piston.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving && state.getValue(SEALED) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof BreakerBlockEntity breaker && !breaker.subscriber().isEmpty()) {
            SubscriberSavedData.note(serverLevel.getServer(), breaker.subscriber(), EventKind.SEAL_BROKEN);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
