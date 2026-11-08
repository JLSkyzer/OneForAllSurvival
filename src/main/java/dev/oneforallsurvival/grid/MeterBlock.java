package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.grid.MeterLedger.EventKind;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.role.RoleGate;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le compteur, posé en ligne sur un câble (sortie devant, entrée derrière). Clic droit main vide : l'ingénieur ouvre
 * l'écran d'inscription de l'abonné, les autres lisent à qui il appartient. Le relevé se fait au testeur.
 */
public final class MeterBlock extends InlineEnergyBlock {

    public MeterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MeterBlockEntity(pos, state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Le numéro est tiré à la pose ; un objet qui en portait un l'a déjà rendu au compteur. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MeterBlockEntity meter) {
            meter.serial();
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MeterBlockEntity meter) {
            if (RoleGate.canDoEngineerWork(serverPlayer)) {
                SubscriberNamePacket.openScreen(serverPlayer, pos, meter);
            } else {
                serverPlayer.displayClientMessage(meter.subscriber().isEmpty()
                        ? Component.translatable(MeterBlockEntity.MESSAGE + "no_subscriber")
                        : Component.translatable(MeterBlockEntity.MESSAGE + "owner", meter.subscriber()), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Un compteur qui a servi garde dans son objet index, numéro et abonné ({@code BlockEntityTag}, relu à la pose) :
     * on le démonte sans perdre son index. Un compteur neuf laisse un objet nu, qui s'empile.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MeterBlockEntity meter
                && !meter.isFactoryFresh()) {
            meter.serial();
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    BlockItem.setBlockEntityData(drop, meter.getType(), meter.saveWithoutMetadata());
                }
            }
        }
        return drops;
    }

    /**
     * Un compteur inscrit qui disparaît (cassé, remplacé) laisse « compteur déposé » au registre de son abonné ; le
     * relevé suivant le signale. Pas pour un simple changement d'état du même bloc, ni pour un déplacement par piston.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MeterBlockEntity meter && !meter.subscriber().isEmpty()) {
            SubscriberSavedData.note(serverLevel.getServer(), meter.subscriber(), EventKind.REMOVED, meter.serial());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
