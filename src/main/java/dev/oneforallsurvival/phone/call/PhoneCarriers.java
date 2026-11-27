package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.net.PhoneSims;
import java.util.Optional;
import java.util.UUID;
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
 * <p>Deux copies d'un même téléphone chez deux joueurs : un seul porteur, le premier trouvé dans l'ordre de la liste
 * des joueurs. Fil du serveur seulement.
 */
public final class PhoneCarriers {

    /** Le joueur qui porte le téléphone, et la pile elle-même (pas une copie). */
    public record Carrier(ServerPlayer player, ItemStack phone) {}

    private PhoneCarriers() {}

    /** Le premier joueur connecté qui porte ce téléphone, carte active logée. */
    public static Optional<Carrier> find(MinecraftServer server, UUID phoneId) {
        if (phoneId == null) {
            return Optional.empty();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<ItemStack> phone = heldBy(player, phoneId);
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
        if (player == null || phoneId == null || player.isRemoved()) {
            return Optional.empty();
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (matches(player.server, stack, phoneId)) {
                return Optional.of(stack);
            }
        }
        ItemStack carried = player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
        return matches(player.server, carried, phoneId) ? Optional.of(carried) : Optional.empty();
    }

    private static boolean matches(MinecraftServer server, ItemStack stack, UUID phoneId) {
        return !stack.isEmpty() && stack.getItem() instanceof PhoneItem
                && PhoneItem.phoneId(stack).filter(phoneId::equals).isPresent()
                && PhoneSims.check(server, stack) == PhoneSims.Validity.ACTIVE;
    }
}
