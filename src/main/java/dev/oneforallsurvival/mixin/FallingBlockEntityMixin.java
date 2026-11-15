package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.quest.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Un bloc qui tombe (sable, gravier, enclume, poudre de béton…) redevient bloc par un {@code Level.setBlock} nu, sans
 * événement de Forge (bytecode de {@code FallingBlockEntity.tick}, Forge 47.4.28, un seul appel) : sa case
 * d'atterrissage est marquée « posé ». Côté sûr : un gravier naturel qui tombe devient aussi « posé ».
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock("
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), index = 0)
    private BlockPos oneforall$markLanding(BlockPos pos) {
        PlacedBlocks.mark(((Entity) (Object) this).level(), pos);
        return pos;
    }
}
