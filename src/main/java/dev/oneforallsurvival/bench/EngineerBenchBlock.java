package dev.oneforallsurvival.bench;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.role.RoleGate;
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

/** L'établi d'ingénieur : une machine électrique dont le clic droit ouvre le menu de commande. */
public final class EngineerBenchBlock extends ElectricMachineBlock {

    public EngineerBenchBlock(Properties properties) {
        super(properties, () -> BenchRegistry.ENGINEER_BENCH_BE.get());
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
