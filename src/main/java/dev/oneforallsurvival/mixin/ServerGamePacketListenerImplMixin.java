package dev.oneforallsurvival.mixin;

import dev.oneforallsurvival.phone.net.CreativeSims;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Retours d'essai du 27/11 : la pile qu'envoie l'inventaire créatif passe par {@link CreativeSims} avant que Minecraft ne
 * la pose ou ne la jette, pour rendre le jeton de carte SIM vu chez ce joueur. Le point d'injection est l'enregistrement
 * de la pile du paquet dans sa variable locale : il vient après {@code PacketUtils.ensureRunningOnSameThread} (qui renvoie
 * le traitement au fil du serveur) et après le contrôle du mode créatif, avant toute pose (lu par javap dans
 * Forge 1.20.1-47.4.28).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @ModifyVariable(method = "handleSetCreativeModeSlot", at = @At("STORE"), ordinal = 0)
    private ItemStack oneforall$restoreSimToken(ItemStack sent) {
        return CreativeSims.onCreativeSlot(player, sent);
    }
}
