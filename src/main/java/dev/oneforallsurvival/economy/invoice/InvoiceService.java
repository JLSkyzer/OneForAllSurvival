package dev.oneforallsurvival.economy.invoice;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.InventoryFit;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Receipt;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * La facture (feature 72) : émettre, payer, régler de main à main, aviser l'émetteur. Fil du serveur seulement.
 *
 * <p>Payer : on vérifie tout (identifiant connu du registre, facture due, payeur = débiteur, pièces suffisantes,
 * place pour la monnaie rendue), puis on engage : {@link PlayerCoins#take}, {@link InvoiceRegistry#markPaid},
 * {@link CommerceDelivery#payTo} (inventaire de l'émetteur, sinon son coffre-fort, sinon l'attente), un transfert
 * {@link MoneyTransfer#INVOICE} au registre, et la facture devient un reçu dans la même case. Aucune pièce n'est
 * créée ni détruite. L'objet n'est qu'une vue : une copie d'une facture payée répond {@link PayResult#ALREADY_SETTLED}
 * et devient un reçu à son tour.
 */
public final class InvoiceService {

    public static final String MESSAGE = "message.oneforallsurvival.invoice.";

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum IssueResult { DONE, SELF, TOO_FAR, BAD_AMOUNT, BAD_REASON, NO_BOOK, GONE }

    /**
     * {@link #NO_ROOM} s'ajoute aux valeurs du plan : la monnaie rendue ne tiendrait pas dans l'inventaire du payeur
     * ({@link PlayerCoins#take} la jetterait à ses pieds) ; on refuse avant tout mouvement.
     */
    public enum PayResult { PAID_TO_INVENTORY, PAID_TO_SAFE, PAID_PENDING, NOT_DEBTOR, ALREADY_SETTLED, UNKNOWN, CANNOT_PAY, NO_ROOM }

    public enum SettleResult { DONE, NOT_FOUND, AMBIGUOUS, ALREADY_SETTLED }

    private InvoiceService() {}

    // ---------------------------------------------------------------------------------------------
    // Émettre
    // ---------------------------------------------------------------------------------------------

    /**
     * Le geste est-il permis : les deux vivants, pas soi-même, un carnet en main, la cible à portée
     * ({@link EconomyConfig#invoiceMaxDistance()}, même dimension) ? {@link IssueResult#DONE} si oui.
     */
    public static IssueResult check(ServerPlayer issuer, ServerPlayer debtor) {
        if (issuer.getUUID().equals(debtor.getUUID())) {
            return IssueResult.SELF;
        }
        if (!issuer.isAlive() || !debtor.isAlive() || debtor.hasDisconnected()
                || issuer.server.getPlayerList().getPlayer(debtor.getUUID()) != debtor) {
            return IssueResult.GONE;
        }
        if (!holdsBook(issuer)) {
            return IssueResult.NO_BOOK;
        }
        double max = EconomyConfig.invoiceMaxDistance();
        if (issuer.level() != debtor.level() || issuer.distanceToSqr(debtor) > max * max) {
            return IssueResult.TOO_FAR;
        }
        return IssueResult.DONE;
    }

    private static boolean holdsBook(ServerPlayer player) {
        return player.getMainHandItem().is(EconomyRegistry.INVOICE_BOOK.get())
                || player.getOffhandItem().is(EconomyRegistry.INVOICE_BOOK.get());
    }

    /** Le motif nettoyé (espaces aux bouts retirés), ou {@code null} s'il est refusé. */
    static String cleanReason(String reason) {
        if (reason == null) {
            return null;
        }
        for (int i = 0; i < reason.length(); i++) {
            char c = reason.charAt(i);
            // Ni saut de ligne ni caractère de contrôle ; ni « § », qui changerait la mise en forme de l'infobulle.
            if (Character.isISOControl(c) || c == '§') {
                return null;
            }
        }
        String clean = reason.strip();
        if (clean.isEmpty() || clean.length() > EconomyConfig.invoiceReasonMax()) {
            return null;
        }
        return clean;
    }

    /**
     * Émet une facture de {@code issuer} à {@code debtorId}. Tout est revérifié ici (le formulaire du client ne
     * prouve rien). La facture va dans l'inventaire du débiteur, sinon en attente ; les deux sont prévenus.
     */
    public static IssueResult issue(ServerPlayer issuer, UUID debtorId, long amount, String reason) {
        if (issuer.getUUID().equals(debtorId)) {
            return IssueResult.SELF;
        }
        ServerPlayer debtor = issuer.server.getPlayerList().getPlayer(debtorId);
        if (debtor == null) {
            return IssueResult.GONE;
        }
        IssueResult check = check(issuer, debtor);
        if (check != IssueResult.DONE) {
            return check;
        }
        if (amount < 1L || amount > EconomyConfig.invoiceMaxAmount()) {
            return IssueResult.BAD_AMOUNT;
        }
        String clean = cleanReason(reason);
        if (clean == null) {
            return IssueResult.BAD_REASON;
        }

        Invoice invoice = InvoiceRegistry.get(issuer.server).issue(issuer, debtor, amount, clean, System.currentTimeMillis());
        CommerceDelivery.giveBack(debtor, List.of(InvoiceItem.of(invoice)));
        issuer.sendSystemMessage(Component.translatable(MESSAGE + "issued", invoice.shortId(), invoice.debtorName(),
                CoinMath.format(amount)));
        debtor.sendSystemMessage(Component.translatable(MESSAGE + "received", invoice.issuerName(),
                CoinMath.format(amount), clean));
        LOGGER.debug("Economie : facture {} de {} a {} pour {} cuivres", invoice.shortId(), invoice.issuerName(),
                invoice.debtorName(), amount);
        return IssueResult.DONE;
    }

    // ---------------------------------------------------------------------------------------------
    // Payer
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code payer} paie la facture de la case {@code slot} de son inventaire (0 à 35, ou 40 pour la main gauche).
     * Tout autre résultat qu'un {@code PAID_*} : aucune pièce n'a bougé.
     */
    public static PayResult pay(ServerPlayer payer, int slot) {
        Inventory inventory = payer.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return PayResult.UNKNOWN;
        }
        ItemStack stack = inventory.getItem(slot);
        Optional<UUID> id = InvoiceItem.idOf(stack);
        if (id.isEmpty()) {
            return PayResult.UNKNOWN;
        }
        InvoiceRegistry registry = InvoiceRegistry.get(payer.server);
        Optional<Invoice> known = registry.get(id.get());
        if (known.isEmpty()) {
            return PayResult.UNKNOWN;
        }
        Invoice invoice = known.get();
        if (invoice.status() != Status.DUE) {
            // Une copie d'une facture réglée : elle devient le reçu qu'elle aurait dû être.
            inventory.setItem(slot, Receipt.forInvoice(invoice, invoice.status()));
            return PayResult.ALREADY_SETTLED;
        }
        if (!invoice.debtor().equals(payer.getUUID())) {
            return PayResult.NOT_DEBTOR;
        }
        if (!payer.isAlive()) {
            return PayResult.CANNOT_PAY;
        }
        Optional<CoinMath.Payment> payment = paymentOf(payer, invoice.amount());
        if (payment.isEmpty()) {
            return PayResult.CANNOT_PAY;
        }
        // La facture devient un reçu dans sa propre case : seule la monnaie rendue demande de la place.
        if (!InventoryFit.fits(payer, coinStacks(payment.get().change()))) {
            return PayResult.NO_ROOM;
        }

        // --- Engager : plus rien ne peut échouer. ---
        if (!PlayerCoins.take(payer, invoice.amount())) {
            return PayResult.CANNOT_PAY; // impossible après la vérification (même calcul) ; take est tout ou rien
        }
        long now = System.currentTimeMillis();
        registry.markPaid(invoice.id(), now);
        Invoice paid = registry.get(invoice.id()).orElse(invoice);
        MinecraftServer server = payer.server;
        CommerceDelivery.Destination destination = CommerceDelivery.payTo(server, invoice.issuer(), invoice.amount());
        EconomyLedger.get(server).transferred(MoneyTransfer.INVOICE, invoice.amount(), payer.getUUID(),
                "facture " + invoice.shortId() + " " + invoice.debtorName() + " -> " + invoice.issuerName());
        inventory.setItem(slot, Receipt.forInvoice(paid, Status.PAID));

        ServerPlayer issuer = server.getPlayerList().getPlayer(invoice.issuer());
        if (issuer != null) {
            issuer.sendSystemMessage(paidNotice(paid, destination));
            registry.noteDestination(invoice.id(), destination);
        } else {
            registry.noticeLater(invoice.id(), destination);
        }
        LOGGER.debug("Economie : facture {} payee par {} ({} cuivres, {})", invoice.shortId(), invoice.debtorName(),
                invoice.amount(), destination);
        return switch (destination) {
            case INVENTORY -> PayResult.PAID_TO_INVENTORY;
            case SAFE -> PayResult.PAID_TO_SAFE;
            case PENDING -> PayResult.PAID_PENDING;
        };
    }

    /** Le paiement que fera {@link PlayerCoins#take} : les bourses seules si elles suffisent, sinon tout ce qui est porté. */
    private static Optional<CoinMath.Payment> paymentOf(ServerPlayer player, long amount) {
        Optional<CoinMath.Payment> payment = CoinMath.makeChange(PlayerCoins.heldInPurses(player), amount);
        return payment.isPresent() ? payment : CoinMath.makeChange(PlayerCoins.held(player), amount);
    }

    /** Des pièces en piles de taille maximale, pour la simulation de place. */
    private static List<ItemStack> coinStacks(Map<Coin, Long> coins) {
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

    private static Component paidNotice(Invoice invoice, CommerceDelivery.Destination destination) {
        return Component.translatable(MESSAGE + "paid." + destination.name().toLowerCase(Locale.ROOT),
                invoice.debtorName(), invoice.shortId(), CoinMath.format(invoice.amount()));
    }

    // ---------------------------------------------------------------------------------------------
    // Vue : la facture suit le registre
    // ---------------------------------------------------------------------------------------------

    /** Si le registre dit cette facture réglée, elle devient un reçu dans la case de {@code player} qui la porte. */
    public static void refresh(ServerPlayer player, ItemStack stack) {
        Optional<UUID> id = InvoiceItem.idOf(stack);
        if (id.isEmpty()) {
            return;
        }
        Optional<Invoice> known = InvoiceRegistry.get(player.server).get(id.get());
        if (known.isEmpty() || known.get().status() == Status.DUE) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot) == stack) {
                inventory.setItem(slot, Receipt.forInvoice(known.get(), known.get().status()));
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Main à main
    // ---------------------------------------------------------------------------------------------

    /**
     * L'émetteur déclare réglée, de main à main, sa facture d'identifiant {@code text} (complet, ou les 8 premiers
     * caractères s'ils désignent une seule de ses factures). Aucune pièce ne bouge.
     */
    public static SettleResult settle(ServerPlayer issuer, String text) {
        InvoiceRegistry registry = InvoiceRegistry.get(issuer.server);
        List<Invoice> mine = registry.issuedBy(issuer.getUUID());
        List<Invoice> found = new ArrayList<>();
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (Invoice invoice : mine) {
            String full = invoice.id().toString();
            if (full.equals(wanted) || (wanted.length() == 8 && invoice.shortId().equals(wanted))) {
                found.add(invoice);
            }
        }
        if (found.isEmpty()) {
            return SettleResult.NOT_FOUND;
        }
        if (found.size() > 1) {
            return SettleResult.AMBIGUOUS;
        }
        Invoice invoice = found.get(0);
        if (!registry.settleByHand(invoice.id(), issuer.getUUID(), System.currentTimeMillis())) {
            return SettleResult.ALREADY_SETTLED;
        }
        ServerPlayer debtor = issuer.server.getPlayerList().getPlayer(invoice.debtor());
        if (debtor != null) {
            debtor.sendSystemMessage(Component.translatable(MESSAGE + "settled_by_hand", invoice.issuerName(),
                    invoice.shortId(), CoinMath.format(invoice.amount())));
        }
        LOGGER.debug("Economie : facture {} reglee de main a main par {}", invoice.shortId(), invoice.issuerName());
        return SettleResult.DONE;
    }

    // ---------------------------------------------------------------------------------------------
    // Avis à la connexion
    // ---------------------------------------------------------------------------------------------

    /** L'émetteur qui revient apprend les paiements reçus pendant son absence, et où sont ses pièces. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        InvoiceRegistry registry = InvoiceRegistry.get(player.server);
        List<Invoice> unseen = registry.unseenPaid(player.getUUID());
        for (Invoice invoice : unseen) {
            CommerceDelivery.Destination destination = registry.destinationOf(invoice.id());
            player.sendSystemMessage(paidNotice(invoice,
                    destination == null ? CommerceDelivery.Destination.PENDING : destination));
        }
        if (!unseen.isEmpty()) {
            registry.markSeen(player.getUUID());
        }
    }
}
