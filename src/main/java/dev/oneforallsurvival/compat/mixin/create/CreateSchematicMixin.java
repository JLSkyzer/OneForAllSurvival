package dev.oneforallsurvival.compat.mixin.create;

import dev.oneforallsurvival.economy.quest.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La Schematicannon (et tout ce qui imprime un schéma) pose par {@code BlockHelper.placeSchematicBlock(Level,
 * BlockState, BlockPos, ItemStack, CompoundTag)} sans aucun {@code EntityPlaceEvent} (bytecode de Create 6.0.8) : la
 * case est marquée « posé » avant la pose (une pose qui échoue laisse une marque sans effet).
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.utility.BlockHelper", remap = false)
public abstract class CreateSchematicMixin {

    @Inject(method = "placeSchematicBlock", remap = false, require = 0, at = @At("HEAD"))
    private static void oneforall$markSchematic(Level level, BlockState state, BlockPos pos, ItemStack stack,
            CompoundTag data, CallbackInfo ci) {
        PlacedBlocks.mark(level, pos);
    }
}
