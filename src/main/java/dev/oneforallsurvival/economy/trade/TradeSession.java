package dev.oneforallsurvival.economy.trade;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.PlayerCoins;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Un échange en cours entre deux joueurs : leurs deux gardes (les objets posés, qui ont quitté l'inventaire et
 * vivent dans {@link dev.oneforallsurvival.economy.HeldGoods}), la case « reçu », les confirmations et la
 * <b>version</b>. Toute écriture dans une garde et chaque bascule du reçu font avancer la version et effacent les
 * deux confirmations ; une confirmation n'est reçue que sur la version courante. Fil du serveur seulement.
 */
public final class TradeSession {

    /** Ce que répond une confirmation. */
    public enum ConfirmResult {
        /** Notée ; on attend l'autre. */
        WAITING,
        /** Les deux ont confirmé : l'échange a eu lieu. */
        DONE,
        /** La version vue n'est plus la bonne : rien ne bouge. */
        STALE,
        /** L'un des deux n'a pas la place pour ce qu'il recevrait : rien ne bouge, les confirmations tombent. */
        NO_ROOM,
        /** L'un des deux n'est plus là (parti, trop loin, fenêtre fermée) : l'échange est annulé, tout est rendu. */
        CHANGED_HANDS
    }

    private final UUID id = UUID.randomUUID();
    private final MinecraftServer server;
    private final UUID first;
    private final UUID second;
    private final String firstName;
    private final String secondName;
    private final SimpleContainer firstOffer;
    private final SimpleContainer secondOffer;
    /** Le dernier contenu vu des deux gardes : une écriture qui ne change rien ne fait pas avancer la version. */
    private final List<ItemStack> seen = new ArrayList<>();
    private final Set<UUID> confirmed = new HashSet<>();
    private int version;
    private boolean receipt;
    private boolean ended;
    @Nullable
    private TradeMenu firstMenu;
    @Nullable
    private TradeMenu secondMenu;

    TradeSession(MinecraftServer server, ServerPlayer first, ServerPlayer second, SimpleContainer firstOffer,
            SimpleContainer secondOffer) {
        this.server = server;
        this.first = first.getUUID();
        this.second = second.getUUID();
        this.firstName = first.getGameProfile().getName();
        this.secondName = second.getGameProfile().getName();
        this.firstOffer = firstOffer;
        this.secondOffer = secondOffer;
        remember();
        firstOffer.addListener(changed -> contentsMayHaveChanged());
        secondOffer.addListener(changed -> contentsMayHaveChanged());
    }

    public UUID id() {
        return id;
    }

    public int version() {
        return version;
    }

    public boolean receipt() {
        return receipt;
    }

    MinecraftServer server() {
        return server;
    }

    UUID first() {
        return first;
    }

    UUID second() {
        return second;
    }

    public boolean involves(UUID player) {
        return first.equals(player) || second.equals(player);
    }

    /** La garde de ce joueur (ses objets posés). */
    public Container offer(UUID player) {
        return first.equals(player) ? firstOffer : secondOffer;
    }

    SimpleContainer offerOf(UUID player) {
        return first.equals(player) ? firstOffer : secondOffer;
    }

    public boolean confirmed(UUID player) {
        return confirmed.contains(player);
    }

    public UUID other(UUID player) {
        return first.equals(player) ? second : first;
    }

    String nameOf(UUID player) {
        return first.equals(player) ? firstName : secondName;
    }

    boolean ended() {
        return ended;
    }

    void end() {
        ended = true;
    }

    @Nullable
    TradeMenu menuOf(UUID player) {
        return first.equals(player) ? firstMenu : secondMenu;
    }

    void attach(UUID player, TradeMenu menu) {
        if (first.equals(player)) {
            firstMenu = menu;
        } else {
            secondMenu = menu;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Version
    // ---------------------------------------------------------------------------------------------

    private void remember() {
        seen.clear();
        for (SimpleContainer offer : List.of(firstOffer, secondOffer)) {
            for (int slot = 0; slot < offer.getContainerSize(); slot++) {
                seen.add(offer.getItem(slot).copy());
            }
        }
    }

    /**
     * Appelé à chaque écriture dans une garde (et après chaque clic de la fenêtre) : si le contenu a vraiment changé,
     * la version avance et les deux confirmations tombent.
     */
    void contentsMayHaveChanged() {
        if (ended) {
            return;
        }
        int i = 0;
        boolean same = true;
        for (SimpleContainer offer : List.of(firstOffer, secondOffer)) {
            for (int slot = 0; slot < offer.getContainerSize() && same; slot++) {
                same = ItemStack.matches(seen.get(i + slot), offer.getItem(slot));
            }
            i += offer.getContainerSize();
        }
        if (!same) {
            remember();
            changed();
        }
    }

    private void changed() {
        version++;
        confirmed.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Gestes
    // ---------------------------------------------------------------------------------------------

    /**
     * Confirme l'état de la version {@code seenVersion}. À la seconde confirmation sur la même version, l'échange a
     * lieu ({@link TradeService#execute}).
     */
    public ConfirmResult confirm(ServerPlayer player, int seenVersion) {
        if (ended || !involves(player.getUUID())) {
            return ConfirmResult.CHANGED_HANDS;
        }
        if (seenVersion != version) {
            return ConfirmResult.STALE;
        }
        confirmed.add(player.getUUID());
        if (!confirmed.contains(other(player.getUUID()))) {
            return ConfirmResult.WAITING;
        }
        return TradeService.execute(this);
    }

    /** Les confirmations tombent sans que la version avance (place manquante : rien n'a changé dans les gardes). */
    void dropConfirmations() {
        confirmed.clear();
    }

    /** Coche ou décoche le reçu ; ignoré sur une version périmée. @return vrai si la case a changé */
    public boolean toggleReceipt(ServerPlayer player, int seenVersion) {
        if (ended || !involves(player.getUUID()) || seenVersion != version) {
            return false;
        }
        receipt = !receipt;
        changed();
        return true;
    }

    /**
     * Verse dans sa garde {@code count} pièces de cette coupure, prises chez le joueur (bourses d'abord). Refusé, sans
     * rien bouger, s'il ne les porte pas ou si la garde n'a pas la place. Le retrait et la pose ont lieu dans le même
     * tick : aucune sauvegarde ne peut tomber entre les deux.
     */
    public boolean addCoins(ServerPlayer player, Coin coin, int count) {
        if (ended || !involves(player.getUUID()) || count <= 0) {
            return false;
        }
        if (PlayerCoins.held(player).getOrDefault(coin, 0L) < count) {
            return false;
        }
        SimpleContainer offer = offerOf(player.getUUID());
        ItemStack coins = new ItemStack(coin.item(), count);
        if (!fitsWhole(offer, coins)) {
            return false;
        }
        PlayerCoins.remove(player, Map.of(coin, (long) count));
        ItemStack left = offer.addItem(coins);
        if (!left.isEmpty()) {
            // Place calculée fausse : ce qui n'est pas entré revient au joueur (ou en attente), jamais au néant.
            CommerceDelivery.giveBack(player, List.of(left));
        }
        return true;
    }

    /** {@code canAddItem} ne regarde qu'une case ; ici toute la pile doit tenir, réparties sur plusieurs cases. */
    private static boolean fitsWhole(SimpleContainer offer, ItemStack stack) {
        int left = stack.getCount();
        for (int slot = 0; slot < offer.getContainerSize() && left > 0; slot++) {
            ItemStack in = offer.getItem(slot);
            if (in.isEmpty()) {
                left -= Math.min(left, Math.min(offer.getMaxStackSize(), stack.getMaxStackSize()));
            } else if (ItemStack.isSameItemSameTags(in, stack)) {
                left -= Math.min(left, Math.max(0, Math.min(offer.getMaxStackSize(), in.getMaxStackSize()) - in.getCount()));
            }
        }
        return left <= 0;
    }
}
