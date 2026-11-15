package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.quest.QuestProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;

/**
 * Le clic majuscule sur la sortie du Cooking Pot de Farmer's Delight : {@code quickMoveStack} déplace la pile en place
 * ({@code moveItemStackTo}) sans {@code remove} ni {@code onQuickCraft}, puis appelle {@code onTake} avec le reste, et
 * {@code removeCount} vaut zéro (bytecode de 1.20.1-1.3.4). Le nombre pris est la pile rendue moins ce qui reste dans
 * la case.
 */
@Mixin(CookingPotMenu.class)
public abstract class CookingPotMenuMixin {

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void oneforall$questCooked(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack moved = cir.getReturnValue();
        if (index != CookingPotMenu.INDEX_OUTPUT || moved.isEmpty() || !(player instanceof ServerPlayer server)) {
            return;
        }
        int left = ((AbstractContainerMenu) (Object) this).slots.get(index).getItem().getCount();
        if (moved.getCount() > left) {
            QuestProgress.cooked(server, moved.copyWithCount(moved.getCount() - left));
        }
    }
}
