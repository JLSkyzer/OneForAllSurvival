package dev.oneforallsurvival.mixin;

import com.mojang.datafixers.util.Pair;
import dev.oneforallsurvival.phone.PhonePrivacy;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lot T2, tâche 15 : le paquet d'équipement, construit par le serveur pour les clients qui voient une entité vivante
 * (début du suivi, {@code ServerEntity.sendPairingData} ; changement en cours, {@code LivingEntity.handleEquipmentChanges}),
 * ne porte plus rien de la carte SIM d'un téléphone ou d'une carte tenus ou portés ({@link PhonePrivacy}). Le paquet
 * part vers les autres seulement (le suivi exclut l'entité elle-même) ; le porteur reçoit sa pile entière par ses
 * paquets d'inventaire. Seul le constructeur du serveur ({@code (int, List)}) est visé, pas celui de la lecture.
 */
@Mixin(ClientboundSetEquipmentPacket.class)
public abstract class ClientboundSetEquipmentPacketMixin {

    @Shadow
    @Final
    @Mutable
    private List<Pair<EquipmentSlot, ItemStack>> slots;

    @Inject(method = "<init>(ILjava/util/List;)V", at = @At("RETURN"))
    private void oneforall$hidePhoneData(int entity, List<Pair<EquipmentSlot, ItemStack>> given, CallbackInfo ci) {
        this.slots = PhonePrivacy.forOthers(given);
    }
}
