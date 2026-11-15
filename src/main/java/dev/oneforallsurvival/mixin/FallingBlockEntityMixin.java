package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.quest.PlacedBlocks;
import dev.oneforallsurvival.economy.quest.QuestProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Un bloc qui tombe (sable, gravier, enclume, poudre de béton…) :
 * <ul>
 *   <li>au départ ({@code FallingBlockEntity.fall}), la pose récente qu'il avait fait compter est retirée
 *       ({@link QuestProgress#blockStartsFalling}) ;</li>
 *   <li>à l'arrivée, il redevient bloc par un {@code Level.setBlock} nu, sans événement de Forge (bytecode de
 *       {@code FallingBlockEntity.tick}, Forge 47.4.28, un seul appel) : sa case est marquée « posé ». Côté sûr : un
 *       gravier naturel qui tombe devient aussi « posé ».</li>
 * </ul>
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {

    @Inject(method = "fall", at = @At("HEAD"))
    private static void oneforall$undoPlacement(Level level, BlockPos pos, BlockState state,
            CallbackInfoReturnable<FallingBlockEntity> cir) {
        QuestProgress.blockStartsFalling(level, pos);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock("
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), index = 0)
    private BlockPos oneforall$markLanding(BlockPos pos) {
        PlacedBlocks.mark(((Entity) (Object) this).level(), pos);
        return pos;
    }
}
