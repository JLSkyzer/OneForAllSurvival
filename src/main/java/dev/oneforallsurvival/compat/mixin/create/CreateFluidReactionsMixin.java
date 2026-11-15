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
 * Un tuyau de Create forme un bloc quand deux fluides se rencontrent (pierre, galet, obsidienne, et ce que d'autres mods
 * ajoutent par {@code PipeCollisionEvent}) : {@code FluidReactions.handlePipeFlowCollision} et
 * {@code handlePipeSpillCollision} le posent par {@code Level.setBlockAndUpdate}, sans {@code FluidPlaceBlockEvent} de
 * Forge (bytecode de Create 6.0.8, un appel chacun, offsets 68 et 54). C'est un générateur de pierre : la case est
 * marquée « posé », comme pour les fluides de vanilla.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.FluidReactions", remap = false)
public abstract class CreateFluidReactionsMixin {

    @Redirect(method = {"handlePipeFlowCollision", "handlePipeSpillCollision"}, remap = false, require = 0,
            at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate("
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean oneforall$formAndMark(Level level, BlockPos pos, BlockState state) {
        boolean placed = level.setBlockAndUpdate(pos, state);
        if (placed) {
            PlacedBlocks.mark(level, pos);
        }
        return placed;
    }
}
