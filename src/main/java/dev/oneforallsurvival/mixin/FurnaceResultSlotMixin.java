package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.economy.quest.QuestProgress;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ce qu'un joueur sort d'un four ou d'un fumoir compte pour la cuisine ou la conserve, au nombre réellement pris
 * ({@code removeCount}, que Minecraft tient pour l'expérience). L'{@code ItemSmeltedEvent} de Forge ne le donne pas :
 * il est tiré après la remise à zéro du compte, avec la pile d'origine au clic majuscule (puis une seconde fois avec
 * le reste) ; lu au bytecode de Forge 47.4.28.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {

    @Shadow
    @Final
    private Player player;

    @Shadow
    private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void oneforall$questOutput(ItemStack stack, CallbackInfo ci) {
        QuestProgress.furnaceOutput(player, ((Slot) (Object) this).container, stack, removeCount);
    }
}
