package dev.oneforallsurvival.compat.mixin.create;

import dev.oneforallsurvival.economy.quest.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Le rouleau mécanique de Create pave avec n'importe quel {@code BlockItem} du stockage de sa contraption, par un
 * {@code Level.setBlockAndUpdate} nu dans {@code RollerMovementBehaviour.tryFill(MovementContext, BlockPos,
 * BlockState)} (bytecode de Create 6.0.8, un seul appel, offset 138) : chaque case pavée est marquée « posé ».
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour", remap = false)
public abstract class CreatePavingMixin {

    @Redirect(method = "tryFill", remap = false, require = 0, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean oneforall$paveAndMark(Level level, BlockPos pos, BlockState state) {
        boolean placed = level.setBlockAndUpdate(pos, state);
        if (placed) {
            PlacedBlocks.mark(level, pos);
        }
        return placed;
    }
}
