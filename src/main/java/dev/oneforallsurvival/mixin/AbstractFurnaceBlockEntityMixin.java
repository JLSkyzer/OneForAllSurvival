package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.quest.CookingBlock;
import dev.oneforallsurvival.economy.quest.CookingCredit;
import dev.oneforallsurvival.economy.quest.QuestCategory;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Four, fumoir et haut fourneau vus par le crédit de cuisine ({@link CookingCredit}) : entrée = case 0, sortie =
 * case 2 ; une cuisson est {@code burn} (méthode d'instance chez Forge 47.4.28, lue au bytecode), qui consomme
 * l'entrée sans passer par {@code setItem} ; tout autre changement du contenu (trémie, entonnoir de Create, transfert
 * de JEI par {@code Slot.safeTake}, clic) passe par {@code setItem}/{@code removeItem} et borne le crédit.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin implements CookingBlock {

    @Shadow
    protected NonNullList<ItemStack> items;

    @Override
    public int oneforall$cookingInputs() {
        return items.get(0).getCount();
    }

    @Override
    public int oneforall$cookingOutputs() {
        return items.get(2).getCount();
    }

    @Override
    @Nullable
    public QuestCategory oneforall$cookingCategory() {
        Object self = this;
        if (self instanceof SmokerBlockEntity) {
            return QuestCategory.PRESERVE;
        }
        return self instanceof FurnaceBlockEntity ? QuestCategory.COOK : null;
    }

    @Inject(method = "burn", at = @At("HEAD"))
    private void oneforall$cookBegin(RegistryAccess access, @Nullable Recipe<?> recipe, NonNullList<ItemStack> stacks,
            int max, CallbackInfoReturnable<Boolean> cir) {
        CookingCredit.beginCook((BlockEntity) (Object) this);
    }

    @Inject(method = "burn", at = @At("RETURN"))
    private void oneforall$cookEnd(RegistryAccess access, @Nullable Recipe<?> recipe, NonNullList<ItemStack> stacks,
            int max, CallbackInfoReturnable<Boolean> cir) {
        CookingCredit.endCook((BlockEntity) (Object) this);
    }

    @Inject(method = "setItem", at = @At("RETURN"))
    private void oneforall$boundSet(int slot, ItemStack stack, CallbackInfo ci) {
        CookingCredit.bound((BlockEntity) (Object) this);
    }

    @Inject(method = "removeItem", at = @At("RETURN"))
    private void oneforall$boundRemove(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        CookingCredit.bound((BlockEntity) (Object) this);
    }

    @Inject(method = "removeItemNoUpdate", at = @At("RETURN"))
    private void oneforall$boundRemoveAll(int slot, CallbackInfoReturnable<ItemStack> cir) {
        CookingCredit.bound((BlockEntity) (Object) this);
    }
}
