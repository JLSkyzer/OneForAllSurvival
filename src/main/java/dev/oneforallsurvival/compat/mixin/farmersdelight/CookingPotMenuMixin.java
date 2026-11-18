package dev.oneforallsurvival.compat.mixin.farmersdelight;

import dev.oneforallsurvival.economy.quest.CookingInputMenu;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;

/**
 * L'écran du Cooking Pot de Farmer's Delight est un écran de cuisson ({@link CookingInputMenu}) : entrée créditable =
 * les six ingrédients (cases 0 à 5 ; jamais le récipient) ; sortie = la case {@code INDEX_OUTPUT}. Les gestes (clic,
 * clic majuscule, glisser, livre de recettes) sont lus par les mixins de {@code AbstractContainerMenu} et
 * {@code RecipeBookMenu}. Configuration à part, appliquée seulement avec la version de Farmer's Delight lue
 * ({@link dev.oneforallsurvival.compat.OptionalModMixinPlugin}).
 */
@Mixin(CookingPotMenu.class)
public abstract class CookingPotMenuMixin implements CookingInputMenu {

    @Override
    @Nullable
    public BlockEntity oneforall$cookingBlock() {
        return ((CookingPotMenu) (Object) this).blockEntity;
    }

    @Override
    public boolean oneforall$isCookingInput(int slotIndex) {
        return slotIndex >= 0 && slotIndex < CookingPotMenu.INDEX_MEAL;
    }

    @Override
    public boolean oneforall$isCookingOutput(int slotIndex) {
        return slotIndex == CookingPotMenu.INDEX_OUTPUT;
    }
}
