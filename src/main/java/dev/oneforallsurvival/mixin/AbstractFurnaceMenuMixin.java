package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.quest.CookingInputMenu;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Four, fumoir et haut fourneau : l'entrée est la case 0 ({@code INGREDIENT_SLOT}), la sortie la case 2 ; jamais le
 * combustible.
 * Côté serveur, le conteneur du menu est le bloc lui-même ; côté client, un conteneur vide (aucun bloc).
 */
@Mixin(AbstractFurnaceMenu.class)
public abstract class AbstractFurnaceMenuMixin implements CookingInputMenu {

    @Shadow
    @Final
    private Container container;

    @Override
    @Nullable
    public BlockEntity oneforall$cookingBlock() {
        return container instanceof BlockEntity block ? block : null;
    }

    @Override
    public boolean oneforall$isCookingInput(int slotIndex) {
        return slotIndex == AbstractFurnaceMenu.INGREDIENT_SLOT;
    }

    @Override
    public boolean oneforall$isCookingOutput(int slotIndex) {
        return slotIndex == AbstractFurnaceMenu.RESULT_SLOT;
    }
}
