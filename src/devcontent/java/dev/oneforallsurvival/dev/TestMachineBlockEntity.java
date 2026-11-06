package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Machine d'essai : 10 FE par tick, un compteur de progression, toujours au travail. */
public class TestMachineBlockEntity extends ElectricMachineBlockEntity {

    private int progress;

    public TestMachineBlockEntity(BlockPos pos, BlockState state) {
        super(DevBlocks.testMachineType(), pos, state, 1000, 100);
    }

    @Override
    protected boolean wantsToWork() {
        return true;
    }

    @Override
    protected int energyPerTick() {
        return 10;
    }

    @Override
    protected void workTick() {
        progress++;
    }

    public int progress() {
        return progress;
    }

    /** Expose {@code setFault} aux GameTests, qui vivent dans un autre paquet. */
    public void panne(boolean fault) {
        setFault(fault);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = tag.getInt("progress");
    }
}
