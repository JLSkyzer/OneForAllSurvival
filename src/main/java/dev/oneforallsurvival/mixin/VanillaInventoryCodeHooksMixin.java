package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.MoneyStorageRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.VanillaInventoryCodeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge fait pousser et aspirer les entonnoirs et les droppers, vers et depuis les blocs qui exposent un stockage
 * d'objets, par un chemin à lui qui ne passe pas par {@code HopperBlockEntity.addItem}. L'argent n'y passe pas
 * non plus. Le dropper n'a pas de garde propre : {@code dropperInsertHook} appelle {@code putStackInInventoryAllSlots},
 * qui rend la pièce, et le dropper la garde.
 *
 * <p>Risque : ces cibles sont privées et propres à Forge 47.x ; avec {@code defaultRequire 1}, une mise à jour qui
 * renomme {@code putStackInInventoryAllSlots} ou la lambda d'{@code extractHook} fait échouer le chargement.
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

    /**
     * L'aspiration : {@code extractHook} extrait d'abord en simulation, puis dépose dans l'entonnoir par
     * {@code setItem} après {@code canPlaceItem} (que l'entonnoir ne surcharge pas). Une pièce vue en simulation
     * est ignorée : la case est sautée, rien n'est extrait.
     */
    @Redirect(method = "lambda$extractHook$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/items/IItemHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;"),
            remap = false)
    private static ItemStack oneforall$noMoneyPulled(IItemHandler handler, int slot, int amount, boolean simulate) {
        ItemStack seen = handler.extractItem(slot, amount, simulate);
        return MoneyStorageRules.isMoney(seen) && simulate ? ItemStack.EMPTY : seen;
    }
}
