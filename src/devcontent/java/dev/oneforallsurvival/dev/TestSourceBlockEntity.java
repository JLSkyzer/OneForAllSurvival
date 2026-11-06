package dev.oneforallsurvival.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

/** Accumulateur d'essai : 3000 FE qu'on peut tirer par extractEnergy, comme le ferait un autre mod. */
public class TestSourceBlockEntity extends BlockEntity {

    private final EnergyStorage storage = new EnergyStorage(5000, 0, 5000, 3000);
    private final LazyOptional<IEnergyStorage> cap = LazyOptional.of(() -> storage);

    public TestSourceBlockEntity(BlockPos pos, BlockState state) {
        super(DevBlocks.testSourceType(), pos, state);
    }

    public int stocke() {
        return storage.getEnergyStored();
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, Direction side) {
        return capability == ForgeCapabilities.ENERGY ? cap.cast() : super.getCapability(capability, side);
    }
}
