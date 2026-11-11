package dev.oneforallsurvival.light;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Le phare. Il veut travailler seulement en tour libre ({@link LightRules#isFreeTower}) : sous un toit ou trop bas il
 * ne consomme rien. Il n'a pas de travail à produire : sa lumière et ses faisceaux (côté client) suivent son état.
 */
public final class LighthouseBlockEntity extends ElectricMachineBlockEntity {

    public static final int CAPACITY = 4000;
    public static final int MAX_RECEIVE = LightConfig.MAX_MACHINE_RECEIVE;

    public LighthouseBlockEntity(BlockPos pos, BlockState state) {
        super(LightRegistry.LIGHTHOUSE_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    @Override
    protected boolean wantsToWork() {
        return level != null && LighthouseBlock.isFreeTower(level, worldPosition);
    }

    @Override
    protected int energyPerTick() {
        return LightConfig.lighthouseEnergy();
    }

    @Override
    protected void workTick() {}

    /** Allume le bloc quand la machine tourne, l'éteint sinon (arrêt, manque de courant, panne). */
    @Override
    protected void afterTick(Level level, MachineState tickState) {
        BlockState current = level.getBlockState(worldPosition);
        boolean lit = tickState == MachineState.RUNNING;
        if (current.is(LightRegistry.LIGHTHOUSE.get()) && current.getValue(LighthouseBlock.LIT) != lit) {
            level.setBlock(worldPosition, current.setValue(LighthouseBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** Les faisceaux portent à 96 blocs : le bloc se dessine tant que le monde l'est, où qu'il soit à l'écran. */
    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }
}
