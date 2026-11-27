package dev.oneforallsurvival.economy.debit;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.InventoryFit;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.debit.DebitPlan.Source;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status;
import dev.oneforallsurvival.economy.invoice.InvoiceService.PayResult;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * Le prélèvement d'une facture publique (feature 86) : tout ou rien, dans l'ordre inventaire → attente → coffre-fort
 * ({@link DebitPlan}), monnaie rendue. Le plan est calculé et vérifié en entier avant de bouger une seule pièce ; puis
 * les pièces sont retirées, la monnaie rendue, la facture marquée payée, les pièces versées au créancier
 * ({@link DebitPayee#receive}) et le transfert noté ({@link MoneyTransfer#PUBLIC_BILL}). Aucune pièce n'est créée ni
 * détruite. Un joueur déconnecté est prélevé dans son attente et son coffre-fort (comptes gardés par le serveur, zone
 * chargée ou non) ; jamais une pièce au sol pour un absent.
 *
 * <p>Tout prend le registre des factures et l'heure en paramètre (les essais passent un registre détaché) ; l'attente
 * et les coffres-forts sont ceux du serveur. Fil du serveur.
 */
public final class DirectDebit {

    /** Préfixe des clés de langue des avis de prélèvement. */
    public static final String MESSAGE = "message.oneforallsurvival.debit.";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Ce qu'a donné une présentation. {@link #FAILED} : le créancier a échoué à recevoir ; le montant a été rendu au
     * débiteur et la facture est de nouveau due.
     */
    public enum Outcome { PAID_HELD, PAID_PENDING, PAID_SAFE, PAID_MIXED, REFUSED, CANCELLED, NOT_DUE, FAILED }

    private DirectDebit() {}

    /*
     * Les motifs écrits au registre de l'économie ({@code /eco ledger}) : le numéro court de la facture (celui de
     * {@code /invoice}, de quoi la retrouver), son motif lisible, le débiteur, puis l'issue en français. Jusqu'au
     * 27/11 la ligne portait la référence interne du créancier (« electricity:2/… ») et la source en anglais
     * (« (held) ») : les anciennes lignes restent telles quelles, rien ne relit ces motifs.
     */

    static String refusedReason(Invoice due) {
        return ledgerReason(due, "prélèvement refusé");
    }

    static String paidReason(Invoice due, Source source) {
        return ledgerReason(due, switch (source) {
            case HELD -> "prélevée sur les pièces portées";
            case PENDING -> "prélevée sur les pièces en attente";
            case SAFE -> "prélevée au coffre-fort";
            case MIXED -> "prélevée sur plusieurs réserves";
        });
    }

    static String paidByItemReason(Invoice due) {
        return ledgerReason(due, "payée avec l'objet facture");
    }

    private static String ledgerReason(Invoice due, String outcome) {
        return "facture publique " + due.shortId() + " (" + due.reason() + ") de " + due.debtorName() + " : " + outcome;
    }

    /** Genres non inscrits déjà signalés au journal (un avertissement par genre). */
    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** À l'arrêt du serveur : les avertissements repartent à neuf pour le monde suivant (même JVM). */
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        WARNED.clear();
    }

    private static void warnUnregistered(String kind) {
        if (WARNED.add(kind)) {
            LOGGER.warn("Prelevement : genre de facture publique '{}' non inscrit ; ses factures restent dues", kind);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Présenter une facture (minuit)
    // ---------------------------------------------------------------------------------------------

    /**
     * Présente cette facture publique à son débiteur. Genre pas (encore) inscrit : rien ne bouge, la facture reste due
     * ({@link Outcome#NOT_DUE}) ; un genre inscrit après le démarrage la retrouvera. Créancier disparu (référence
     * inconnue de son genre) : la facture est annulée ({@link Outcome#CANCELLED}). Total insuffisant
     * ({@link Outcome#REFUSED}) : rien ne bouge, le refus est compté sur la facture ({@link InvoiceRegistry#refusals}) ;
     * au premier refus seulement, le créancier est prévenu ({@link DebitPayee#refused}), le débiteur aussi (tout de suite
     * s'il est connecté, sinon à sa connexion) et une ligne va au journal. Sinon, payée.
     *
     * @return {@link Outcome#NOT_DUE} si la facture n'est pas (ou plus) une facture publique due de ce registre
     */
    public static Outcome present(MinecraftServer server, InvoiceRegistry registry, Invoice invoice, long now) {
        Optional<Invoice> current = registry.get(invoice.id());
        if (current.isEmpty() || current.get().status() != Status.DUE || !current.get().isPublic()) {
            return Outcome.NOT_DUE;
        }
        Invoice due = current.get();
        Optional<DebitPayee> payee = DebitPayees.get(due.payeeKind());
        if (payee.isEmpty()) {
            // Genre pas (encore) inscrit : un genre inscrit après le démarrage, ou un lot retiré. On ne sait rien du
            // créancier, donc on ne l'annule pas (ce serait irréversible) : la facture reste due, présentée plus tard.
            warnUnregistered(due.payeeKind());
            return Outcome.NOT_DUE;
        }
        if (!payee.get().exists(server, due.payeeRef())) {
            registry.cancel(due.id(), now);
            ServerPlayer debtor = server.getPlayerList().getPlayer(due.debtor());
            if (debtor != null) {
                debtor.sendSystemMessage(Component.translatable(MESSAGE + "cancelled", describe(due),
                        due.shortId(), CoinMath.format(due.amount())));
            }
            LOGGER.info("Prelevement : facture {} ({}) annulee, creancier disparu", due.shortId(), due.payee());
            return Outcome.CANCELLED;
        }

        UUID debtorId = due.debtor();
        ServerPlayer online = server.getPlayerList().getPlayer(debtorId);
        boolean present = online != null && online.isAlive() && !online.hasDisconnected();
        HeldGoods held = HeldGoods.get(server);
        SafeAccounts safes = SafeAccounts.get(server);
        Optional<DebitPlan> found = DebitPlan.plan(present ? PlayerCoins.held(online) : null,
                held.pendingCoins(debtorId), safes.balance(debtorId), due.amount());
        if (found.isEmpty()) {
            // Une ligne au journal, un avis au créancier et un au débiteur au premier refus de chaque facture seulement ;
            // les refus suivants (un impayé durable, présenté chaque nuit) ne font que compter sur la facture.
            int refusals = registry.noteRefusal(due.id());
            if (refusals <= 1) {
                payee.get().refused(server, due, now);
                EconomyLedger.get(server).debitRefused(debtorId, due.amount(), refusedReason(due), now);
                if (online != null) {
                    online.sendSystemMessage(refusedNotice(due, payee.get()));
                } else {
                    registry.noteRefusedUnseen(due.id());
                }
            }
            LOGGER.debug("Prelevement : facture {} refusee ({} cuivres, {}, refus n° {})", due.shortId(), due.amount(),
                    due.debtorName(), refusals);
            return Outcome.REFUSED;
        }

        // --- Engager : le plan tient en entier, plus rien ne peut échouer. ---
        DebitPlan plan = found.get();
        if (!plan.fromHeld().isEmpty()) {
            PlayerCoins.remove(online, plan.fromHeld());
        }
        if (!plan.fromPending().isEmpty()) {
            held.takeCoins(debtorId, plan.fromPending());
        }
        for (Map.Entry<Coin, Long> part : plan.fromSafe().entrySet()) {
            safes.withdraw(debtorId, part.getKey(), part.getValue());
        }
        giveChange(server, debtorId, present ? online : null, plan);
        registry.markPaid(due.id(), now);
        Invoice paid = registry.get(due.id()).orElse(due);
        if (!deliver(server, registry, payee.get(), paid)) {
            return Outcome.FAILED;
        }
        EconomyLedger.get(server).transferred(MoneyTransfer.PUBLIC_BILL, due.amount(), debtorId,
                paidReason(due, plan.single()));
        if (online != null) {
            online.sendSystemMessage(paidNotice(paid, payee.get(), plan.single().name()));
        } else {
            registry.noteDebitedUnseen(due.id(), plan.single().name());
        }
        LOGGER.debug("Prelevement : facture {} payee ({} cuivres, {}, {})", due.shortId(), due.amount(),
                plan.single(), due.debtorName());
        return switch (plan.single()) {
            case HELD -> Outcome.PAID_HELD;
            case PENDING -> Outcome.PAID_PENDING;
            case SAFE -> Outcome.PAID_SAFE;
            case MIXED -> Outcome.PAID_MIXED;
        };
    }

    /**
     * Verse le montant au créancier. S'il lève une exception, rien n'est perdu ni ne plante : le montant est rendu au
     * débiteur ({@link CommerceDelivery#payTo} : inventaire, coffre-fort, sinon attente ; jamais au sol), la facture
     * redevient due ({@link InvoiceRegistry#reopen}) et l'erreur va au journal.
     *
     * @return vrai si le créancier a reçu les pièces
     */
    private static boolean deliver(MinecraftServer server, InvoiceRegistry registry, DebitPayee payee, Invoice paid) {
        try {
            payee.receive(server, paid, paid.amount());
            return true;
        } catch (RuntimeException e) {
            CommerceDelivery.payTo(server, paid.debtor(), paid.amount());
            registry.reopen(paid.id());
            LOGGER.error("Prelevement : le creancier {} a echoue a recevoir la facture {} ; {} cuivres rendus a {}, "
                    + "facture de nouveau due", paid.payee(), paid.shortId(), paid.amount(), paid.debtorName(), e);
            return false;
        }
    }

    /**
     * Rend la monnaie là où le plan le dit. Sur le joueur : ce qui n'entre pas va en attente (jamais au sol). Au
     * coffre-fort : seulement s'il en a un et dans la limite du plafond, le reste en attente. En attente : en vrac.
     */
    private static void giveChange(MinecraftServer server, UUID debtor, @Nullable ServerPlayer online, DebitPlan plan) {
        EnumMap<Coin, Long> left = new EnumMap<>(plan.change());
        left.values().removeIf(count -> count <= 0L);
        if (left.isEmpty()) {
            return;
        }
        if (plan.changeTo() == Source.HELD && online != null) {
            for (Coin coin : new ArrayList<>(left.keySet())) {
                long rest = left.get(coin) - PlayerCoins.receive(online, coin, left.get(coin));
                if (rest > 0L) {
                    left.put(coin, rest);
                } else {
                    left.remove(coin);
                }
            }
        } else if (plan.changeTo() == Source.SAFE) {
            SafeAccounts safes = SafeAccounts.get(server);
            if (safes.safePos(debtor).isPresent()) {
                for (Coin coin : new ArrayList<>(left.keySet())) {
                    long fits = Math.min(left.get(coin), safes.room(debtor, coin));
                    if (fits > 0L) {
                        safes.deposit(debtor, coin, fits);
                    }
                    long rest = left.get(coin) - fits;
                    if (rest > 0L) {
                        left.put(coin, rest);
                    } else {
                        left.remove(coin);
                    }
                }
            }
        }
        if (!left.isEmpty()) {
            HeldGoods.get(server).addCoins(debtor, left);
            if (online != null) {
                online.sendSystemMessage(Component.translatable(CommerceDelivery.MESSAGE + "pending"));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Payer tout de suite (l'objet facture)
    // ---------------------------------------------------------------------------------------------

    /**
     * Le débiteur paie tout de suite cette facture publique, avec ce qu'il porte seulement (inventaire et bourses),
     * tout ou rien, monnaie rendue dans l'inventaire (refusé s'il n'y a pas la place). Appelé par
     * {@code InvoiceService.pay} pour une facture publique ; le reçu dans la case est à l'appelant.
     */
    public static PayResult payNow(ServerPlayer payer, Invoice invoice) {
        MinecraftServer server = payer.server;
        InvoiceRegistry registry = InvoiceRegistry.get(server);
        Optional<Invoice> current = registry.get(invoice.id());
        if (current.isEmpty() || !current.get().isPublic()) {
            return PayResult.UNKNOWN;
        }
        Invoice due = current.get();
        if (due.status() != Status.DUE) {
            return PayResult.ALREADY_SETTLED;
        }
        if (!due.debtor().equals(payer.getUUID())) {
            return PayResult.NOT_DEBTOR;
        }
        if (!payer.isAlive()) {
            return PayResult.CANNOT_PAY;
        }
        long now = EconomyClock.now();
        Optional<DebitPayee> payee = DebitPayees.get(due.payeeKind());
        if (payee.isEmpty()) {
            warnUnregistered(due.payeeKind());
            return PayResult.CANNOT_PAY; // genre pas inscrit : ni payée ni annulée
        }
        if (!payee.get().exists(server, due.payeeRef())) {
            registry.cancel(due.id(), now);
            return PayResult.ALREADY_SETTLED;
        }
        Optional<DebitPlan> found = DebitPlan.plan(PlayerCoins.held(payer), new EnumMap<>(Coin.class),
                new EnumMap<>(Coin.class), due.amount());
        if (found.isEmpty() || found.get().single() != Source.HELD) {
            return PayResult.CANNOT_PAY;
        }
        DebitPlan plan = found.get();
        if (!InventoryFit.fits(payer, coinStacks(plan.change()))) {
            return PayResult.NO_ROOM;
        }

        // --- Engager. ---
        PlayerCoins.remove(payer, plan.fromHeld());
        PlayerCoins.giveOrDrop(payer, plan.change()); // la place a été vérifiée : rien ne tombe
        registry.markPaid(due.id(), now);
        Invoice paid = registry.get(due.id()).orElse(due);
        if (!deliver(server, registry, payee.get(), paid)) {
            return PayResult.CANNOT_PAY;
        }
        EconomyLedger.get(server).transferred(MoneyTransfer.PUBLIC_BILL, due.amount(), payer.getUUID(),
                paidByItemReason(due));
        LOGGER.debug("Prelevement : facture {} payee par l'objet ({} cuivres, {})", due.shortId(), due.amount(),
                due.debtorName());
        return PayResult.PAID_TO_INVENTORY;
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

    // ---------------------------------------------------------------------------------------------
    // Avis
    // ---------------------------------------------------------------------------------------------

    /** Le motif lisible : celui du créancier inscrit, sinon le motif enregistré. */
    public static Component describe(Invoice invoice) {
        return DebitPayees.get(invoice.payeeKind()).map(p -> p.describe(invoice))
                .orElseGet(() -> Component.literal(invoice.reason()));
    }

    /** « Prélèvement refusé » avec le lien « [Recevoir la facture] » ({@code /invoice copy <id>}). */
    public static Component refusedNotice(Invoice invoice, DebitPayee payee) {
        Component link = Component.translatable(MESSAGE + "copy_link").withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/invoice copy " + invoice.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(MESSAGE + "copy_hover"))));
        return Component.translatable(MESSAGE + "refused", payee.describe(invoice), invoice.shortId(),
                CoinMath.format(invoice.amount()), link);
    }

    /** « Facture prélevée » : motif, numéro, montant, et d'où sont venues les pièces. */
    private static Component paidNotice(Invoice invoice, DebitPayee payee, String source) {
        return Component.translatable(MESSAGE + "paid." + source.toLowerCase(Locale.ROOT), payee.describe(invoice),
                invoice.shortId(), CoinMath.format(invoice.amount()));
    }

    /**
     * Branché sur la connexion, après la livraison des attentes : les refus non lus (factures encore dues, avec le
     * lien de copie) et les prélèvements faits pendant l'absence (montant et source).
     */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        announce(player, InvoiceRegistry.get(player.server));
    }

    /** Les avis de connexion de ce joueur, lus dans {@code registry} (une seule fois). */
    public static void announce(ServerPlayer player, InvoiceRegistry registry) {
        for (Map.Entry<Invoice, String> entry : registry.takeDebitedUnseen(player.getUUID()).entrySet()) {
            Invoice invoice = entry.getKey();
            DebitPayee payee = DebitPayees.get(invoice.payeeKind()).orElse(null);
            Component what = payee == null ? Component.literal(invoice.reason()) : payee.describe(invoice);
            player.sendSystemMessage(Component.translatable(MESSAGE + "paid." + entry.getValue().toLowerCase(Locale.ROOT),
                    what, invoice.shortId(), CoinMath.format(invoice.amount())));
        }
        for (Invoice invoice : registry.takeRefusedUnseen(player.getUUID())) {
            Optional<DebitPayee> payee = DebitPayees.get(invoice.payeeKind());
            if (payee.isPresent()) {
                player.sendSystemMessage(refusedNotice(invoice, payee.get()));
            }
        }
    }
}
