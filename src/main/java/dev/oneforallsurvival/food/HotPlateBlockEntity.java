package dev.oneforallsurvival.food;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import vectorwing.farmersdelight.common.block.entity.HeatableBlockEntity;
import vectorwing.farmersdelight.common.tag.ModTags;

/**
 * La plaque électrique. Elle consomme du courant, et s'use, tant qu'un ustensile de Farmer's Delight (marmite, poêle)
 * est posé dessus, vide ou non, directement ou derrière un bloc conducteur de chaleur (une trémie). Sans ustensile
 * elle est à l'arrêt : ni consommation ni usure. Elle ne fait aucun travail propre : c'est l'état {@code LIT} du bloc,
 * égal à « en marche », qui chauffe l'ustensile, par la règle même de Farmer's Delight.
 */
public final class HotPlateBlockEntity extends ElectricMachineBlockEntity {

    public static final int CAPACITY = 2000;
    public static final int MAX_RECEIVE = 60;

    public HotPlateBlockEntity(BlockPos pos, BlockState state) {
        super(FoodRegistry.HOT_PLATE_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    @Override
    protected boolean wantsToWork() {
        if (level == null) {
            return false;
        }
        BlockPos above = worldPosition.above();
        if (level.getBlockEntity(above) instanceof HeatableBlockEntity) {
            return true;
        }
        return level.getBlockState(above).is(ModTags.Blocks.HEAT_CONDUCTORS)
                && level.getBlockEntity(above.above()) instanceof HeatableBlockEntity;
    }

    @Override
    protected int energyPerTick() {
        return FoodConfig.hotPlateEnergy();
    }

    @Override
    protected void workTick() {}

    /** La plaque est allumée exactement quand elle tourne ; l'état est relu au niveau, le socle vient d'y poser le sien. */
    @Override
    protected void afterTick(Level level, MachineState tickState) {
        BlockState current = level.getBlockState(worldPosition);
        boolean lit = tickState == MachineState.RUNNING;
        if (current.getBlock() instanceof HotPlateBlock && current.getValue(HotPlateBlock.LIT) != lit) {
            level.setBlock(worldPosition, current.setValue(HotPlateBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }
}
