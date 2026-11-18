package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.quest.CookingCredit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Le livre de recettes d'un four ou du Cooking Pot remplit l'entrée sans passer par {@code clicked} : c'est aussi un
 * geste du joueur à l'écran, lu avant et après pour son crédit de cuisine ({@link CookingCredit}).
 */
@Mixin(RecipeBookMenu.class)
public abstract class RecipeBookMenuMixin {

    @Unique
    private CookingCredit.Snapshot oneforall$placementBefore;

    @Inject(method = "handlePlacement", at = @At("HEAD"))
    private void oneforall$cookingBefore(boolean placeAll, Recipe<?> recipe, ServerPlayer player, CallbackInfo ci) {
        oneforall$placementBefore = CookingCredit.before((AbstractContainerMenu) (Object) this, player);
    }

    @Inject(method = "handlePlacement", at = @At("RETURN"))
    private void oneforall$cookingAfter(boolean placeAll, Recipe<?> recipe, ServerPlayer player, CallbackInfo ci) {
        CookingCredit.Snapshot before = oneforall$placementBefore;
        oneforall$placementBefore = null;
        CookingCredit.after(before, (AbstractContainerMenu) (Object) this);
    }
}
