package dev.oneforallsurvival.bench;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le moteur de l'établi : 1000 FE de réserve, 20 FE par tick de travail. Il ne fabrique rien lui-même ;
 * le {@link WorkshopTicker} lit son état pour accélérer la tête de file qui lui est rattachée. Il ne
 * consomme donc que lorsqu'une tête de file l'attend.
 */
public final class EngineerBenchBlockEntity extends ElectricMachineBlockEntity {

    private static final int CAPACITY = 1000;
    private static final int MAX_RECEIVE = 100;
    private static final int ENERGY_PER_TICK = 20;

    public EngineerBenchBlockEntity(BlockPos pos, BlockState state) {
        super(BenchRegistry.ENGINEER_BENCH_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    @Override
    protected boolean wantsToWork() {
        return level instanceof ServerLevel serverLevel
                && WorkshopSavedData.get(serverLevel.getServer()).hasHeadAt(serverLevel.dimension(), getBlockPos());
    }

    @Override
    protected int energyPerTick() {
        return ENERGY_PER_TICK;
    }

    @Override
    protected void workTick() {
        // Le travail avance dans le ticker des ateliers, selon l'état de la machine.
    }
}
