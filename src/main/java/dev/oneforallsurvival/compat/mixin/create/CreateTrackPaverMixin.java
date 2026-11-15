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
 * La pose d'une voie de Create pave le dessous avec les blocs du joueur, par un {@code Level.setBlock} nu dans
 * {@code TrackPaver.placeBlockIfFree(Level, BlockPos, BlockState, boolean)} (bytecode de Create 6.0.8, un seul appel,
 * offset 40 ; appelé par {@code TrackPlacement.paveTracks}) : chaque case pavée est marquée « posé ».
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.trains.track.TrackPaver", remap = false)
public abstract class CreateTrackPaverMixin {

    @Redirect(method = "placeBlockIfFree", remap = false, require = 0, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean oneforall$paveAndMark(Level level, BlockPos pos, BlockState state, int flags) {
        boolean placed = level.setBlock(pos, state, flags);
        if (placed) {
            PlacedBlocks.mark(level, pos);
        }
        return placed;
    }
}
