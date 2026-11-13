package dev.oneforallsurvival.economy.stall;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.InventoryFit;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.stall.StallLedger.Kind;
import dev.oneforallsurvival.economy.stall.StallLedger.Line;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * L'achat et le rachat à un étal, sans le réseau : <b>vérifier, puis engager</b>. Tout ce qui peut échouer (offre
 * périmée, stock, argent, place chez le joueur, place au stock, caisse) est vérifié avant le premier changement ;
 * ensuite, plus rien ne peut échouer, et si l'imprévu arrive quand même (une place mal calculée), ce qui n'entre pas
 * va en attente ({@link CommerceDelivery#giveBack}), jamais au sol.
 *
 * <p>Aucune pièce n'est créée ni détruite : l'acheteur paie par {@link PlayerCoins#take}, le vendeur reçoit par
 * {@link PlayerCoins#receive} ; chaque mouvement entre personnes (ou vers le trésor) est un transfert du registre
 * global. La taxe ne frappe que la vente, jamais le rachat.
 *
 * <p>Les contrôles qui tiennent au réseau (joueur connecté, distance, écran ouvert, version vue) sont dans
 * {@link StallActionPacket#apply}.
 */
public final class StallTrade {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Les cases principales de l'inventaire (barre d'action comprise), où le vendeur prend ce qu'il vend. */
    private static final int MAIN_SLOTS = 36;

    private StallTrade() {}

    // ---------------------------------------------------------------------------------------------
    // Achat
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code buyer} achète un lot de l'offre {@code index}, au prix et au lot qu'il a vus.
     *
     * @return {@link StallResult#DONE} si tout a bougé ; tout autre résultat : rien n'a bougé
     */
    public static StallResult buy(ServerPlayer buyer, MarketStallBlockEntity stall, int index, long seenPrice, int seenLot) {
        if (!buyer.isAlive()) {
            return StallResult.GONE;
        }
        if (index < 0 || index >= MarketStallBlockEntity.offerSlots()) {
            return StallResult.NO_OFFER;
        }
        StallOffer offer = stall.offers().get(index);
        if (offer == null) {
            return StallResult.NO_OFFER;
        }
        if (offer.price() != seenPrice || offer.lot() != seenLot) {
            return StallResult.CHANGED;
        }
        if (buyer.getUUID().equals(stall.owner())) {
            return StallResult.OWN_STALL;
        }
        if (stall.countInStock(offer::matches) < offer.lot()) {
            return StallResult.OUT_OF_STOCK;
        }
        long price = offer.price();
        Optional<CoinMath.Payment> payment = paymentOf(buyer, price);
        if (payment.isEmpty()) {
            return StallResult.CANNOT_PAY;
        }
        List<ItemStack> incoming = new ArrayList<>(split(offer.template(), offer.lot()));
        incoming.addAll(coinStacks(payment.get().change()));
        if (!InventoryFit.fits(buyer, incoming)) {
            return StallResult.NO_ROOM;
        }
        long tax = CommerceMath.salesTax(price, EconomyConfig.salesTaxPercent());
        EnumMap<Coin, Long> toCash = CoinMath.decompose(price - tax);
        for (Map.Entry<Coin, Long> entry : toCash.entrySet()) {
            if (entry.getValue() > stall.cashRoom(entry.getKey())) {
                return StallResult.CASH_FULL;
            }
        }

        // --- Engager : plus rien ne peut échouer. ---
        if (!PlayerCoins.take(buyer, price)) {
            // Impossible après la vérification (même calcul) ; rien n'a bougé : take est tout ou rien.
            return StallResult.CANNOT_PAY;
        }
        List<ItemStack> goods = stall.takeFromStock(offer::matches, offer.lot());
        CommerceDelivery.giveBack(buyer, goods);
        if (tax > 0L) {
            TreasuryAccount.get(buyer.server).deposit(CoinMath.decompose(tax));
        }
        toCash.forEach(stall::addCash);

        String who = buyer.getGameProfile().getName();
        String where = "etal de " + ownerLabel(stall) + " en " + stall.getBlockPos().toShortString();
        EconomyLedger ledger = EconomyLedger.get(buyer.server);
        ledger.transferred(MoneyTransfer.STALL_SALE, price - tax, buyer.getUUID(), who + " -> " + where);
        if (tax > 0L) {
            ledger.transferred(MoneyTransfer.SALES_TAX, tax, buyer.getUUID(), "taxe sur l'achat de " + who + " a l'" + where);
        }
        stall.ledger().add(new Line(System.currentTimeMillis(), buyer.getUUID(), who, Kind.SALE, itemId(offer.template()),
                offer.lot(), price));
        stall.setChanged();
        LOGGER.debug("Economie : {} achete {} pour {} cuivres ({} de taxe)", who, offer, price, tax);
        return StallResult.DONE;
    }

    /** Le paiement que fera {@link PlayerCoins#take} : les bourses seules si elles suffisent, sinon tout ce qui est porté. */
    private static Optional<CoinMath.Payment> paymentOf(ServerPlayer player, long amount) {
        Optional<CoinMath.Payment> payment = CoinMath.makeChange(PlayerCoins.heldInPurses(player), amount);
        return payment.isPresent() ? payment : CoinMath.makeChange(PlayerCoins.held(player), amount);
    }

    // ---------------------------------------------------------------------------------------------
    // Rachat
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code seller} vend à l'étal un lot de l'offre de rachat {@code index}, payé sur la caisse.
     *
     * @return {@link StallResult#DONE} si tout a bougé ; tout autre résultat : rien n'a bougé
     */
    public static StallResult sellBack(ServerPlayer seller, MarketStallBlockEntity stall, int index, long seenPrice, int seenLot) {
        if (!seller.isAlive()) {
            return StallResult.GONE;
        }
        if (index < 0 || index >= MarketStallBlockEntity.offerSlots()) {
            return StallResult.NO_OFFER;
        }
        StallBuyback buyback = stall.buybacks().get(index);
        if (buyback == null) {
            return StallResult.NO_OFFER;
        }
        if (buyback.price() != seenPrice || buyback.lot() != seenLot) {
            return StallResult.CHANGED;
        }
        if (buyback.lotsLeft() <= 0) {
            return StallResult.OUT_OF_STOCK;
        }
        if (seller.getUUID().equals(stall.owner())) {
            return StallResult.OWN_STALL;
        }
        if (countInInventory(seller.getInventory(), buyback::matches) < buyback.lot()) {
            return StallResult.NOTHING_TO_SELL;
        }
        if (!stockHasRoom(stall, buyback.template(), buyback.lot())) {
            return StallResult.STOCK_FULL;
        }
        long price = buyback.price();
        Optional<EnumMap<Coin, Long>> box = CommerceMath.payFromBox(stall.cash(), price, Coin::value);
        if (box.isEmpty()) {
            return StallResult.CASH_SHORT;
        }
        EnumMap<Coin, Long> paid = CoinMath.decompose(price);
        if (!InventoryFit.fits(seller, coinStacks(paid))) {
            return StallResult.NO_ROOM;
        }

        // --- Engager : plus rien ne peut échouer. ---
        List<ItemStack> goods = takeFromInventory(seller.getInventory(), buyback::matches, buyback.lot());
        List<ItemStack> refused = new ArrayList<>();
        for (ItemStack stack : goods) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(stall.stock(), stack, false);
            if (!rest.isEmpty()) {
                refused.add(rest);
            }
        }
        if (!refused.isEmpty()) {
            // Impossible après la simulation ; ce qui n'est pas entré revient au vendeur, rien n'est perdu.
            LOGGER.warn("Economie : le stock de l'etal en {} a refuse {} apres la simulation", stall.getBlockPos(), refused);
            CommerceDelivery.giveBack(seller, refused);
        }
        stall.replaceCash(box.get());
        List<ItemStack> coinsLeft = new ArrayList<>();
        for (Map.Entry<Coin, Long> entry : paid.entrySet()) {
            long left = entry.getValue() - PlayerCoins.receive(seller, entry.getKey(), entry.getValue());
            if (left > 0L) {
                coinsLeft.addAll(coinStacks(Map.of(entry.getKey(), left)));
            }
        }
        if (!coinsLeft.isEmpty()) {
            CommerceDelivery.giveBack(seller, coinsLeft);
        }
        stall.consumeBuybackLot(index);

        String who = seller.getGameProfile().getName();
        UUID owner = stall.owner();
        EconomyLedger.get(seller.server).transferred(MoneyTransfer.STALL_BUYBACK, price, owner,
                "etal de " + ownerLabel(stall) + " en " + stall.getBlockPos().toShortString() + " -> " + who);
        stall.ledger().add(new Line(System.currentTimeMillis(), seller.getUUID(), who, Kind.BUYBACK, itemId(buyback.template()),
                buyback.lot(), price));
        stall.setChanged();
        return StallResult.DONE;
    }

    /** Le lot tient-il dans le stock ? Simulé sur une copie, rien n'est touché. */
    static boolean stockHasRoom(MarketStallBlockEntity stall, ItemStack template, int count) {
        StallStockHandler copy = new StallStockHandler();
        for (int i = 0; i < copy.getSlots(); i++) {
            copy.setStackInSlot(i, stall.stock().getStackInSlot(i).copy());
        }
        for (ItemStack stack : split(template, count)) {
            if (!ItemHandlerHelper.insertItemStacked(copy, stack, false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** {@code count} objets du modèle, en piles de taille maximale. */
    static List<ItemStack> split(ItemStack template, int count) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, template.getMaxStackSize());
        int left = count;
        while (left > 0) {
            int size = Math.min(left, max);
            out.add(template.copyWithCount(size));
            left -= size;
        }
        return out;
    }

    /** Des pièces en piles de 64 au plus (pour la simulation de place et la mise en attente). */
    static List<ItemStack> coinStacks(Map<Coin, Long> coins) {
        List<ItemStack> out = new ArrayList<>();
        for (Map.Entry<Coin, Long> entry : coins.entrySet()) {
            long left = entry.getValue();
            int max = new ItemStack(entry.getKey().item()).getMaxStackSize();
            while (left > 0L) {
                int size = (int) Math.min(left, max);
                out.add(new ItemStack(entry.getKey().item(), size));
                left -= size;
            }
        }
        return out;
    }

    private static int countInInventory(Inventory inventory, Predicate<ItemStack> matches) {
        int total = 0;
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && matches.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Retire exactement {@code count} objets des 36 cases principales ; l'appelant a vérifié qu'ils y sont. */
    private static List<ItemStack> takeFromInventory(Inventory inventory, Predicate<ItemStack> matches, int count) {
        List<ItemStack> out = new ArrayList<>();
        int left = count;
        for (int slot = 0; slot < MAIN_SLOTS && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && matches.test(stack)) {
                out.add(inventory.removeItem(slot, Math.min(left, stack.getCount())));
                left -= out.get(out.size() - 1).getCount();
            }
        }
        return out;
    }

    static String itemId(ItemStack stack) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    static String ownerLabel(MarketStallBlockEntity stall) {
        if (!stall.ownerName().isEmpty()) {
            return stall.ownerName();
        }
        return stall.owner() != null ? stall.owner().toString() : "sans proprietaire";
    }
}
