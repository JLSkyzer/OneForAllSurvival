package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * La carte SIM dans l'inventaire créatif (retours d'essai du 27/11). Le client ne reçoit jamais le jeton d'une carte,
 * seulement son empreinte publique ({@link SimCardItem#getShareTag}, {@link PhoneItem#getShareTag},
 * {@link SimCardItem#TAG_MARK}) ; or l'inventaire créatif renvoie au serveur la pile qu'il connaît
 * ({@code ServerboundSetCreativeModeSlotPacket}), qui remplace celle du serveur : sans retour du jeton, la carte, seule
 * ou logée dans un téléphone, deviendrait « non reconnue ».
 *
 * <p>À chaque paquet de l'inventaire créatif (fil du serveur, {@code ServerGamePacketListenerImplMixin}), avant que
 * Minecraft ne l'applique, le serveur note les jetons des cartes qu'il voit chez ce joueur (cases du menu de son
 * inventaire, dont celle qui va être remplacée, et la pile tenue), puis, si la pile reçue porte une empreinte dont il a
 * vu le jeton chez ce joueur, il lui rend exactement ce jeton et ôte l'empreinte. Le jeton suit donc la pile : deux piles
 * de la même carte aux jetons différents gardent chacune le sien, quel que soit l'ordre des paquets (Maj+clic dont la
 * destination arrive avant la source compris). Une empreinte inconnue de ce joueur ne rend rien : la pile reste telle
 * quelle. Le serveur ne prend jamais un jeton au registre sur la foi du client, et ne l'envoie pas au client. La pile
 * posée ou jetée est notée à son tour. Mémoire bornée ({@value #PER_PLAYER} jetons par joueur), oubliée à la déconnexion
 * et à l'arrêt.
 *
 * <p>Une copie créative d'une carte (clic du milieu) reçoit le même jeton : après une insertion, une seule vaut, comme
 * pour toute copie ({@link PhoneSims}).
 */
public final class CreativeSims {

    /** Les jetons retenus au plus par joueur. */
    static final int PER_PLAYER = 64;

    private static final CreativeSimTokens SEEN = new CreativeSimTokens(PER_PLAYER);

    private CreativeSims() {}

    /**
     * La pile que l'inventaire créatif de {@code player} pose dans une case (ou jette) : {@code sent}, avec le jeton de
     * son empreinte rendu s'il a été vu chez ce joueur. Fil du serveur seulement.
     */
    public static ItemStack onCreativeSlot(ServerPlayer player, ItemStack sent) {
        UUID who = player.getUUID();
        for (Slot slot : player.inventoryMenu.slots) {
            note(who, slot.getItem());
        }
        note(who, player.inventoryMenu.getCarried());
        note(who, player.containerMenu.getCarried());
        ItemStack fixed = restore(who, sent);
        note(who, fixed);
        return fixed;
    }

    private static void note(UUID player, ItemStack stack) {
        if (stack.getItem() instanceof SimCardItem) {
            SimCardItem.token(stack).ifPresent(token -> SEEN.note(player, token));
        } else if (stack.is(PhoneRegistry.PHONE.get())) {
            SimCardItem.token(PhoneItem.sim(stack)).ifPresent(token -> SEEN.note(player, token));
        }
    }

    /** {@code sent} telle quelle, ou sa copie dont la carte (seule ou logée) a retrouvé son jeton. */
    private static ItemStack restore(UUID player, ItemStack sent) {
        if (sent.getItem() instanceof SimCardItem) {
            ItemStack copy = sent.copy();
            if (copy.hasTag() && giveBack(player, copy.getTag())) {
                return copy;
            }
        } else if (sent.is(PhoneRegistry.PHONE.get())) {
            ItemStack card = PhoneItem.sim(sent);
            if (!card.isEmpty() && card.hasTag() && giveBack(player, card.getTag())) {
                ItemStack copy = sent.copy();
                PhoneItem.setSim(copy, card);
                return copy;
            }
        }
        return sent;
    }

    /**
     * Rend au NBT d'une carte le jeton de son empreinte, vu chez ce joueur, et ôte l'empreinte (une carte qui a déjà un
     * jeton la perd seulement : une pile du serveur ne garde pas les deux).
     *
     * @return vrai si le NBT a changé ; faux sans empreinte, ou si elle est inconnue de ce joueur
     */
    private static boolean giveBack(UUID player, CompoundTag card) {
        if (!card.hasUUID(SimCardItem.TAG_MARK)) {
            return false;
        }
        if (!card.hasUUID(SimCardItem.TAG_TOKEN)) {
            Optional<UUID> token = SEEN.token(player, card.getUUID(SimCardItem.TAG_MARK));
            if (token.isEmpty()) {
                return false;
            }
            card.putUUID(SimCardItem.TAG_TOKEN, token.get());
        }
        card.remove(SimCardItem.TAG_MARK);
        return true;
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SEEN.forget(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SEEN.clear();
    }
}
