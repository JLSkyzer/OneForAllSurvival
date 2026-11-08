package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

/**
 * Le parafoudre : jamais au travail, il ne consomme rien et ne s'use donc pas de lui-même. Il perd
 * {@link GridConfig#arresterWearPerStrike()} points par éclair encaissé ({@link LightningEvents}) ; à 0 il tombe en
 * panne et ne protège plus rien jusqu'à sa réparation, comme toute machine.
 */
public final class SurgeArresterBlockEntity extends ElectricMachineBlockEntity {

    public SurgeArresterBlockEntity(BlockPos pos, BlockState state) {
        super(GridRegistry.SURGE_ARRESTER_BE.get(), pos, state, 0, 0);
    }

    /** En état de protéger : sans panne. */
    public boolean isActive() {
        return fault() == null;
    }

    /** Un éclair encaissé, une seule fois par éclair. */
    void absorbStrike() {
        takeDamage(GridConfig.arresterWearPerStrike());
    }

    @Override
    protected boolean wantsToWork() {
        return false;
    }

    @Override
    protected int energyPerTick() {
        return 0;
    }

    @Override
    protected void workTick() {}

    /** Sans courant : aucune réserve à exposer, les câbles ne s'y branchent pas. */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }
}
