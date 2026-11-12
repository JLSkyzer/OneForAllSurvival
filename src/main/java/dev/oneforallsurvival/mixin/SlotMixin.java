package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.SlotOwner;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Une case refuse l'argent hors de l'inventaire du joueur et des écrans permis. Le clic, le maj-clic, la touche
 * numérique et la répartition par glissé passent tous par {@code mayPlace}. Les classes de cases qui surchargent
 * {@code mayPlace} sans appeler l'original ne sont pas atteintes ici : {@code SlotItemHandler} a son mixin, les
 * autres sont rattrapées par le balayage de {@code MoneyGuard}.
 */
@Mixin(Slot.class)
public abstract class SlotMixin implements SlotOwner {

    @Unique
    private AbstractContainerMenu oneforall$menu;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void oneforall$refuseMoney(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!MoneyStorageRules.mayHold((Slot) (Object) this, stack)) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public AbstractContainerMenu oneforall$menu() {
        return oneforall$menu;
    }

    @Override
    public void oneforall$setMenu(AbstractContainerMenu menu) {
        this.oneforall$menu = menu;
    }
}
