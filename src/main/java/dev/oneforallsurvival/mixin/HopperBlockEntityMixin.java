package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.MoneyStorageRules;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Un entonnoir ne déplace jamais d'argent : ni vers un conteneur, ni depuis un conteneur ou un dropper, ni depuis
 * un objet au sol. Ceinture et bretelles : une pièce au sol est une entité du mod, qu'il ne voit pas, et aucune
 * pièce ne devrait entrer dans un entonnoir.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void oneforall$noMoneyMoved(@Nullable Container source, Container destination, ItemStack stack,
            @Nullable Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        if (MoneyStorageRules.isMoney(stack)) {
            cir.setReturnValue(stack);
        }
    }

    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void oneforall$noMoneyPickedUp(Container container, ItemEntity entity,
            CallbackInfoReturnable<Boolean> cir) {
        if (MoneyStorageRules.isMoney(entity.getItem())) {
            cir.setReturnValue(false);
        }
    }
}
