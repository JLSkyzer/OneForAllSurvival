package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.SlotOwner;
import dev.oneforallsurvival.economy.quest.CookingCredit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Apprend à chaque case l'écran qui l'ajoute, pour que {@code SlotMixin} sache si l'écran est un rangement permis ; et
 * lit les entrées d'un écran de cuisson avant et après chaque clic du joueur (clic, clic majuscule, glisser, touche
 * numérique), pour son crédit de cuisine ({@link CookingCredit}).
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    /** Les entrées lues au début du clic en cours ; {@code null} hors d'un écran de cuisson. */
    @Unique
    private CookingCredit.Snapshot oneforall$cookingBefore;

    @Inject(method = "addSlot", at = @At("HEAD"))
    private void oneforall$tagSlot(Slot slot, CallbackInfoReturnable<Slot> cir) {
        ((SlotOwner) slot).oneforall$setMenu((AbstractContainerMenu) (Object) this);
    }

    @Inject(method = "clicked", at = @At("HEAD"))
    private void oneforall$cookingBefore(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        oneforall$cookingBefore = CookingCredit.before((AbstractContainerMenu) (Object) this, player);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void oneforall$cookingAfter(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        CookingCredit.Snapshot before = oneforall$cookingBefore;
        oneforall$cookingBefore = null;
        CookingCredit.after(before, (AbstractContainerMenu) (Object) this);
    }
}
