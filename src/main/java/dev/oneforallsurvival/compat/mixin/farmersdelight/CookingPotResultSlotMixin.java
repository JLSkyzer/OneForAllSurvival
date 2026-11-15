package dev.oneforallsurvival.compat.mixin.farmersdelight;

import dev.oneforallsurvival.economy.quest.QuestProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotResultSlot;

/**
 * Un plat pris à la souris dans la case de sortie du Cooking Pot de Farmer's Delight compte pour la cuisine. Aucun
 * événement de Forge n'y est tiré. Lu au bytecode de Farmer's Delight 1.20.1-1.3.4 : {@code remove} ajoute à
 * {@code removeCount}, {@code onTake} appelle {@code checkTakeAchievements} (SRG {@code m_5845_}, surcharge de
 * {@code Slot}) qui le remet à zéro. Le clic majuscule ne passe pas par {@code remove} (le compte reste à zéro) : il
 * est compté par {@link CookingPotMenuMixin}. Configuration à part, appliquée seulement avec la version de Farmer's
 * Delight lue ({@link dev.oneforallsurvival.compat.OptionalModMixinPlugin}).
 */
@Mixin(CookingPotResultSlot.class)
public abstract class CookingPotResultSlotMixin {

    @Shadow(remap = false)
    @Final
    private Player player;

    @Shadow(remap = false)
    private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void oneforall$questCooked(ItemStack stack, CallbackInfo ci) {
        if (removeCount > 0 && player instanceof ServerPlayer server) {
            QuestProgress.cooked(server, stack.copyWithCount(removeCount));
        }
    }
}
