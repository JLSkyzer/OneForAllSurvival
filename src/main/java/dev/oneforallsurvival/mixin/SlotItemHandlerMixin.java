package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.MoneyStorageRules;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Comme {@link SlotMixin}, pour la classe de Forge qui surcharge {@code mayPlace} sans appeler {@code Slot}. */
@Mixin(SlotItemHandler.class)
public abstract class SlotItemHandlerMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void oneforall$refuseMoney(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!MoneyStorageRules.mayHold((Slot) (Object) this, stack)) {
            cir.setReturnValue(false);
        }
    }
}
