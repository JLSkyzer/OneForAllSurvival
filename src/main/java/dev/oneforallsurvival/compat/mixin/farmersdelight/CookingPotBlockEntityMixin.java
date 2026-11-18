package dev.oneforallsurvival.compat.mixin.farmersdelight;

import dev.oneforallsurvival.economy.quest.CookingBlock;
import dev.oneforallsurvival.economy.quest.CookingCredit;
import dev.oneforallsurvival.economy.quest.QuestCategory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

/**
 * Le Cooking Pot vu par le crédit de cuisine ({@link CookingCredit}) : entrée = cases 0 à 5, sorties = le plat en
 * attente de récipient (case 6) et la sortie (case 8). Une cuisson est {@code processCooking} (lu au bytecode de
 * 1.20.1-1.3.4 : plat posé en case 6, un de chaque ingrédient retiré par {@code shrink}). Configuration à part,
 * appliquée seulement avec la version lue ({@link dev.oneforallsurvival.compat.OptionalModMixinPlugin}).
 */
@Mixin(CookingPotBlockEntity.class)
public abstract class CookingPotBlockEntityMixin implements CookingBlock {

    private static final int MEAL = 6;
    private static final int OUTPUT = 8;
    private static final int INGREDIENTS = 6;

    private ItemStackHandler oneforall$inventory() {
        return ((CookingPotBlockEntity) (Object) this).getInventory();
    }

    @Override
    public int oneforall$cookingInputs() {
        int n = 0;
        for (int i = 0; i < INGREDIENTS; i++) {
            n += oneforall$inventory().getStackInSlot(i).getCount();
        }
        return n;
    }

    @Override
    public int oneforall$cookingOutputs() {
        return oneforall$inventory().getStackInSlot(MEAL).getCount() + oneforall$inventory().getStackInSlot(OUTPUT).getCount();
    }

    @Override
    public QuestCategory oneforall$cookingCategory() {
        return QuestCategory.COOK;
    }

    @Inject(method = "processCooking", at = @At("HEAD"), remap = false)
    private void oneforall$cookBegin(CookingPotRecipe recipe, CookingPotBlockEntity pot,
            CallbackInfoReturnable<Boolean> cir) {
        CookingCredit.beginCook((BlockEntity) (Object) this);
    }

    @Inject(method = "processCooking", at = @At("RETURN"), remap = false)
    private void oneforall$cookEnd(CookingPotRecipe recipe, CookingPotBlockEntity pot,
            CallbackInfoReturnable<Boolean> cir) {
        CookingCredit.endCook((BlockEntity) (Object) this);
    }
}
