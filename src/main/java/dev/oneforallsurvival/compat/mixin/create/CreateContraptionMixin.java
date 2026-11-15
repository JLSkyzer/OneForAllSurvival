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
 * Une contraption de Create (piston mécanique, roulement, chariot…) qui se démonte repose ses blocs par un
 * {@code Level.setBlock} nu dans {@code Contraption.addBlocksToWorld(Level, StructureTransform)} (bytecode de Create
 * 6.0.8, un seul appel) : chaque case reposée est marquée « posé ». Configuration à part, appliquée seulement avec la
 * version de Create lue ({@link dev.oneforallsurvival.compat.OptionalModMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption", remap = false)
public abstract class CreateContraptionMixin {

    @Redirect(method = "addBlocksToWorld", remap = false, require = 0, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean oneforall$setAndMark(Level level, BlockPos pos, BlockState state, int flags) {
        boolean placed = level.setBlock(pos, state, flags);
        if (placed) {
            PlacedBlocks.mark(level, pos);
        }
        return placed;
    }
}
