package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.SlotOwner;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apprend à chaque case l'écran qui l'ajoute, pour que {@code SlotMixin} sache si l'écran est un rangement permis. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "addSlot", at = @At("HEAD"))
    private void oneforall$tagSlot(Slot slot, CallbackInfoReturnable<Slot> cir) {
        ((SlotOwner) slot).oneforall$setMenu((AbstractContainerMenu) (Object) this);
    }
}
