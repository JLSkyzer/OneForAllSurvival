package dev.oneforallsurvival.compat.mixin.farmersdelight;

import dev.oneforallsurvival.economy.quest.CookingInputMenu;
import dev.oneforallsurvival.economy.quest.QuestProgress;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotResultSlot;

/**
 * Le clic majuscule sur la sortie du Cooking Pot de Farmer's Delight : {@code quickMoveStack} déplace la pile en place
 * ({@code moveItemStackTo}) sans {@code remove} ni {@code onQuickCraft}, puis appelle {@code onTake} avec le reste, et
 * {@code removeCount} vaut zéro (bytecode de 1.20.1-1.3.4). Le nombre pris est la pile rendue moins ce qui reste dans
 * la case. L'écran est aussi un écran de cuisson ({@link CookingInputMenu}) : entrées = les six ingrédients et le
 * récipient ; seule la part mise par le joueur compte. Configuration à part, appliquée seulement avec la version de
 * Farmer's Delight lue ({@link dev.oneforallsurvival.compat.OptionalModMixinPlugin}).
 */
@Mixin(CookingPotMenu.class)
public abstract class CookingPotMenuMixin implements CookingInputMenu {

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void oneforall$questCooked(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack moved = cir.getReturnValue();
        if (moved.isEmpty() || !(player instanceof ServerPlayer server) || index < 0
                || index >= ((AbstractContainerMenu) (Object) this).slots.size()) {
            return;
        }
        // La case est reconnue à sa classe, pas à la constante INDEX_OUTPUT (recopiée à la compilation).
        Slot slot = ((AbstractContainerMenu) (Object) this).slots.get(index);
        if (!(slot instanceof CookingPotResultSlot)) {
            return;
        }
        int left = slot.getItem().getCount();
        if (moved.getCount() > left) {
            QuestProgress.cooked(server, ((CookingPotMenu) (Object) this).blockEntity,
                    moved.copyWithCount(moved.getCount() - left));
        }
    }

    @Override
    @Nullable
    public BlockEntity oneforall$cookingBlock() {
        // Côté client aussi, le menu tient un bloc (celui du monde du client) : jamais lu, faute de joueur du serveur.
        return ((CookingPotMenu) (Object) this).blockEntity;
    }

    /** Les six ingrédients (cases 0 à 5) et le récipient ; ni le plat en attente, ni la sortie. */
    @Override
    public boolean oneforall$isCookingInput(int slotIndex) {
        return slotIndex < CookingPotMenu.INDEX_MEAL || slotIndex == CookingPotMenu.INDEX_CONTAINER;
    }
}
