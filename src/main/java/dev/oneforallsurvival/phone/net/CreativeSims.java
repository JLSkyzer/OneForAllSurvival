package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * La carte SIM dans l'inventaire créatif (retours d'essai du 27/11). Le client ne reçoit jamais le jeton d'une carte
 * ({@link SimCardItem#getShareTag}, {@link PhoneItem#getShareTag}) ; or l'inventaire créatif renvoie au serveur la pile
 * qu'il connaît ({@code ServerboundSetCreativeModeSlotPacket}), qui remplace celle du serveur : la carte, seule ou logée
 * dans un téléphone, perdait son jeton et devenait « non reconnue ».
 *
 * <p>À chaque paquet de l'inventaire créatif (fil du serveur, {@code ServerGamePacketListenerImplMixin}), avant que
 * Minecraft ne l'applique, le serveur note les jetons des cartes qu'il voit chez ce joueur (cases du menu de son
 * inventaire, dont celle qui va être remplacée, et la pile tenue), puis rend à la pile reçue, si elle arrive sans jeton,
 * celui qu'il a vu chez ce joueur pour cette carte. Il ne le prend jamais au registre sur la foi du client, et ne
 * l'envoie toujours pas au client. Une pile reçue avec un jeton est laissée telle quelle. La pile posée ou jetée est
 * notée à son tour. Mémoire bornée ({@value #PER_PLAYER} cartes par joueur), oubliée à la déconnexion et à l'arrêt.
 *
 * <p>Une copie créative d'une carte (clic du milieu) reçoit le même jeton : après une insertion, une seule vaut, comme
 * pour toute copie ({@link PhoneSims}).
 */
public final class CreativeSims {

    /** Les cartes retenues au plus par joueur. */
    static final int PER_PLAYER = 64;

    private static final CreativeSimTokens SEEN = new CreativeSimTokens(PER_PLAYER);

    private CreativeSims() {}

    /**
     * La pile que l'inventaire créatif de {@code player} pose dans une case (ou jette) : {@code sent}, avec le jeton de
     * sa carte rendu s'il a été vu chez ce joueur. Fil du serveur seulement.
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
            noteCard(player, stack);
        } else if (stack.is(PhoneRegistry.PHONE.get())) {
            noteCard(player, PhoneItem.sim(stack));
        }
    }

    private static void noteCard(UUID player, ItemStack card) {
        Optional<UUID> id = SimCardItem.simId(card);
        Optional<UUID> token = SimCardItem.token(card);
        if (id.isPresent() && token.isPresent()) {
            SEEN.note(player, id.get(), token.get());
        }
    }

    private static ItemStack restore(UUID player, ItemStack sent) {
        if (sent.getItem() instanceof SimCardItem) {
            Optional<UUID> token = missing(player, sent);
            if (token.isPresent()) {
                ItemStack copy = sent.copy();
                copy.getOrCreateTag().putUUID(SimCardItem.TAG_TOKEN, token.get());
                return copy;
            }
        } else if (sent.is(PhoneRegistry.PHONE.get())) {
            ItemStack card = PhoneItem.sim(sent);
            Optional<UUID> token = card.isEmpty() ? Optional.empty() : missing(player, card);
            if (token.isPresent()) {
                card.getOrCreateTag().putUUID(SimCardItem.TAG_TOKEN, token.get());
                ItemStack copy = sent.copy();
                PhoneItem.setSim(copy, card);
                return copy;
            }
        }
        return sent;
    }

    /** Le jeton à rendre à cette carte : aucun si elle en porte déjà un, sinon celui vu chez ce joueur, s'il y en a un. */
    private static Optional<UUID> missing(UUID player, ItemStack card) {
        if (SimCardItem.token(card).isPresent()) {
            return Optional.empty();
        }
        return SimCardItem.simId(card).flatMap(id -> SEEN.token(player, id));
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SEEN.forget(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SEEN.clear();
    }
}
