package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.net.PhoneSims;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Qui porte un téléphone : le seul lien entre un numéro et un joueur est le {@code phoneId} que la carte garde au
 * registre ({@code SimCard.phoneId}). On cherche ce téléphone chez les joueurs connectés, dans tout l'inventaire (sac,
 * barre, armure, seconde main) et sous le curseur d'un écran ouvert ; jamais dans un coffre, au sol ou chez un joueur
 * parti. Un téléphone ne compte que si sa carte est {@link PhoneSims.Validity#ACTIVE} (une copie morte ne sonne pas).
 *
 * <p>Deux copies d'un même téléphone : un seul porteur, le premier qui convient dans l'ordre de la liste des joueurs ;
 * le filtre s'applique pendant la recherche, pile par pile, pour qu'une copie inutilisable (à plat) n'en cache pas une
 * autre. Fil du serveur seulement.
 */
public final class PhoneCarriers {

    /** Le joueur qui porte le téléphone, et la pile elle-même (pas une copie). */
    public record Carrier(ServerPlayer player, ItemStack phone) {}

    private PhoneCarriers() {}

    /** Le premier joueur connecté qui porte ce téléphone, carte active logée, dans une pile qui passe {@code ok}. */
    public static Optional<Carrier> find(MinecraftServer server, UUID phoneId,
            BiPredicate<ServerPlayer, ItemStack> ok) {
        if (phoneId == null) {
            return Optional.empty();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<ItemStack> phone = heldBy(player, phoneId, stack -> ok.test(player, stack));
            if (phone.isPresent()) {
                return Optional.of(new Carrier(player, phone.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * La pile de ce téléphone dans l'inventaire de ce joueur ou sous son curseur, si sa carte y est active. Sert à
     * vérifier qu'un bout d'appel porte toujours <b>son</b> téléphone, même si une copie est ailleurs.
     */
    public static Optional<ItemStack> heldBy(ServerPlayer player, UUID phoneId) {
        return heldBy(player, phoneId, stack -> true);
    }

    /** Comme {@link #heldBy(ServerPlayer, UUID)}, la première de ces piles qui passe {@code ok}. */
    public static Optional<ItemStack> heldBy(ServerPlayer player, UUID phoneId, Predicate<ItemStack> ok) {
        if (phoneId == null) {
            return Optional.empty();
        }
        return first(player, stack -> matches(player.server, stack, phoneId) && ok.test(stack));
    }

    /**
     * Comme {@link #heldBy}, sans exiger de carte : un appel d'urgence (feature 95) ne demande ni carte ni opérateur.
     */
    public static Optional<ItemStack> holding(ServerPlayer player, UUID phoneId) {
        return first(player, stack -> PhoneItem.phoneId(stack).filter(id -> id.equals(phoneId)).isPresent());
    }

    /** Le premier téléphone de ce joueur (inventaire, puis curseur) qui passe {@code ok}, carte ou non. */
    public static Optional<ItemStack> first(ServerPlayer player, Predicate<ItemStack> ok) {
        if (player == null || player.isRemoved()) {
            return Optional.empty();
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof PhoneItem && ok.test(stack)) {
                return Optional.of(stack);
            }
        }
        ItemStack carried = player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
        return !carried.isEmpty() && carried.getItem() instanceof PhoneItem && ok.test(carried)
                ? Optional.of(carried) : Optional.empty();
    }

    private static boolean matches(MinecraftServer server, ItemStack stack, UUID phoneId) {
        return !stack.isEmpty() && stack.getItem() instanceof PhoneItem
                && PhoneItem.phoneId(stack).filter(phoneId::equals).isPresent()
                && PhoneSims.check(server, stack) == PhoneSims.Validity.ACTIVE;
    }
}
