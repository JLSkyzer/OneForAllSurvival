package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.MoneyStorageRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.VanillaInventoryCodeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge fait pousser les entonnoirs et les droppers dans les blocs qui exposent un stockage d'objets (sans
 * conteneur vanilla, comme un coffre) par un chemin à lui, qui ne passe pas par {@code HopperBlockEntity.addItem}.
 * L'argent n'y passe pas non plus.
 */
@Mixin(VanillaInventoryCodeHooks.class)
public abstract class VanillaInventoryCodeHooksMixin {

    @Inject(method = "putStackInInventoryAllSlots", at = @At("HEAD"), cancellable = true, remap = false)
    private static void oneforall$noMoneyPushed(BlockEntity source, Object destination, IItemHandler destInventory,
            ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (MoneyStorageRules.isMoney(stack)) {
            cir.setReturnValue(stack);
        }
    }

    @Inject(method = "dropperInsertHook", at = @At("HEAD"), cancellable = true, remap = false)
    private static void oneforall$noMoneyDropped(Level level, BlockPos pos, DispenserBlockEntity dropper, int slot,
            ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (MoneyStorageRules.isMoney(stack)) {
            cir.setReturnValue(false);
        }
    }
}
