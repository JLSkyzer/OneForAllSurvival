package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * Le bain galvanique : une machine électrique dont le clic droit ouvre le menu à trois cases.
 * Le contenu est jeté quand le bloc disparaît, par quelque cause que ce soit.
 */
public final class GalvanicBathBlock extends ElectricMachineBlock {

    public GalvanicBathBlock(Properties properties) {
        super(properties, () -> RepairRegistry.GALVANIC_BATH_BE.get());
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
