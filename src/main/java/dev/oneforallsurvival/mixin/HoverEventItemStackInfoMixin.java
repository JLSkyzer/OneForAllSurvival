package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.phone.PhonePrivacy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lot T2, tâche 15 (revue, point 1) : le survol d'un objet dans un message ({@code ItemStack.getDisplayName}, par
 * exemple le message de mort d'un tueur qui tient un téléphone ou une carte renommés, envoyé à tout le serveur) copie
 * {@code getTag()} entier, pas {@code getShareTag()}. Ici, le NBT copié passe par {@link PhonePrivacy} : plus de
 * {@code phone_id}, de carte logée, de jeton ni d'empreinte, de numéro, de titulaire ni d'opérateur ; tout autre objet
 * est inchangé.
 * La pile d'origine n'est jamais modifiée. Seul le constructeur {@code (ItemStack)} est visé.
 */
@Mixin(HoverEvent.ItemStackInfo.class)
public abstract class HoverEventItemStackInfoMixin {

    @Shadow
    @Final
    @Mutable
    private CompoundTag tag;

    @Inject(method = "<init>(Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void oneforall$hidePhoneData(ItemStack stack, CallbackInfo ci) {
        ItemStack shown = PhonePrivacy.forOthers(stack);
        if (shown != stack) {
            this.tag = shown.getTag();
        }
    }
}
