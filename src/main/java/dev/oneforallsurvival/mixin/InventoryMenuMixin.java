package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.CoinRecipeGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Le maj-clic sur le résultat de la grille 2x2 du joueur déplace la pile avant d'appeler {@code onCraftedBy} :
 * le filet de {@code CoinItem} y arrive trop tard. On vérifie donc la garde avant le déplacement.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void oneforall$guardCoinCraft(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index == 0 && !CoinRecipeGuard.quickCraftAllowed((InventoryMenu) (Object) this, player)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
