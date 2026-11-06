package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
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

    /** Met en panne (fusible) ou répare, pour les GameTests, qui vivent dans un autre paquet. */
    public void panne(boolean fault) {
        if (fault) {
            breakDown(FaultType.FUSE);
        } else {
            clearFault();
        }
    }

    /** Fixe l'état d'usure, pour les GameTests. */
    public void etat(double valeur) {
        setCondition(valeur);
    }

    /** Ticks de flammes restants, pour les GameTests. */
    public int flammes() {
        return flameTicksLeft();
    }

    /** Allume les flammes sans tirage au sort, pour les GameTests. */
    public void allumer() {
        igniteFlames();
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
