package dev.oneforallsurvival.grid.supplier;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.debit.DailyDebit;
import dev.oneforallsurvival.economy.debit.DebitPayee;
import dev.oneforallsurvival.economy.debit.DebitPayees;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.town.TreasuryLedger;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers.StatementKind;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers.StatementLine;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * La facture quotidienne d'électricité (feature 83), créancier et émetteur du genre {@value #KIND} du prélèvement
 * automatique (feature 86, tâche 1).
 *
 * <p><b>Émission</b> ({@link #issue}, à la clôture de chaque jour par {@link DailyDebit}) : chaque client accepté
 * ({@code ACTIVE} ou {@code RESIGNING}), et chaque client détaché dans la journée qui l'était, reçoit une facture
 * publique de {@link PowerBillMath#amount} (consommation du jour × prix du jour + abonnement du jour), si elle n'est pas
 * nulle. Un client coupé à distance toute la journée ne consomme rien mais doit l'abonnement : c'est un contrat, le
 * cahier ne l'en dispense pas. Puis la consommation passe à « hier », un client en résiliation est détaché (son
 * jumelage oublié), les détachés sont oubliés, le plafond et la taxe du lendemain entrent en vigueur, puis les tarifs du
 * lendemain de chaque fournisseur (prix ramené au plafond, fondateur prévenu ; clients prévenus d'un changement), et la
 * sortie du poste et la somme des compteurs passent à « hier ». Une facture garde dans sa référence le taux de taxe du
 * jour qu'elle clôt : un changement de taxe ne touche que les factures suivantes, même pour une facture représentée.
 *
 * <p><b>Paiement</b> ({@link #receive}) : la taxe (arrondie en dessous) au trésor, ligne {@code POWER_TAX} ; le reste à
 * la caisse du poste ; une ligne « payée » au relevé du client. Si la caisse ou le trésor n'a pas la place, rien n'est
 * versé et une exception remonte : le prélèvement rend alors le montant au débiteur et la facture redevient due (voie
 * de la tâche 1) ; aucune pièce n'est créée ni détruite. <b>Refus</b> ({@link #refused}) : ligne au relevé, fondateur
 * prévenu ; la colonne « impayés » se lit dans le registre des factures ({@link #unpaid}), jamais en double.
 *
 * <p>Les registres sont ceux du serveur pour {@link #SERVER}, ou ceux donnés au constructeur (essais). Fil du serveur.
 */
public final class PowerBilling implements DebitPayee, DailyDebit.DayIssuer {

    /** Le genre des factures d'électricité ({@code payee} = {@code electricity:<référence>}). */
    public static final String KIND = "electricity";

    /** La facturation du serveur, sur ses registres ; inscrite au démarrage du mod. */
    public static final PowerBilling SERVER = new PowerBilling(null, null);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MESSAGE = PowerPairing.MESSAGE;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static volatile PowerBilling installed = SERVER;

    /**
     * La référence d'une facture d'électricité : tout ce qui la décrit, figé à l'émission. Écrite
     * {@code <fournisseur>/<compteur>/<taxe>/<jour>/<FE>/<prix>/<tranche>/<abonnement>/<nom du fournisseur>} (le nom
     * vient en dernier : il peut contenir « / »).
     *
     * @param taxPercent la taxe du jour facturé, retenue au paiement quel que soit le jour où il arrive
     * @param epochDay le jour facturé
     */
    public record Ref(String supplierId, UUID meter, int taxPercent, long epochDay, long fe, long price, long unitFe,
            long subscription, String supplierName) {

        String encode() {
            return String.join("/", supplierId, meter.toString(), Integer.toString(taxPercent), Long.toString(epochDay),
                    Long.toString(fe), Long.toString(price), Long.toString(unitFe), Long.toString(subscription),
                    supplierName);
        }

        /** Relit une référence ; vide si elle est abîmée (la facture est alors annulée à sa présentation). */
        static Optional<Ref> parse(@Nullable String text) {
            if (text == null) {
                return Optional.empty();
            }
            String[] parts = text.split("/", 9);
            if (parts.length != 9 || parts[0].isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Ref(parts[0], UUID.fromString(parts[1]), Integer.parseInt(parts[2]),
                        Long.parseLong(parts[3]), Long.parseLong(parts[4]), Long.parseLong(parts[5]),
                        Long.parseLong(parts[6]), Long.parseLong(parts[7]), parts[8]));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        public LocalDate day() {
            return LocalDate.ofEpochDay(epochDay);
        }
    }

    @Nullable
    private final PowerSuppliers suppliers;
    @Nullable
    private final InvoiceRegistry invoices;

    /**
     * @param suppliers le registre des fournisseurs, ou nul pour celui du serveur
     * @param invoices le registre des factures, ou nul pour celui du serveur
     */
    public PowerBilling(@Nullable PowerSuppliers suppliers, @Nullable InvoiceRegistry invoices) {
        this.suppliers = suppliers;
        this.invoices = invoices;
    }

    /** Inscrit cette facturation comme créancier et émetteur du genre {@value #KIND} (remplace la précédente). */
    public static void install(PowerBilling billing) {
        DebitPayees.register(KIND, billing);
        DailyDebit.registerIssuer(KIND, billing);
        installed = billing;
    }

    private PowerSuppliers suppliers(MinecraftServer server) {
        return suppliers != null ? suppliers : PowerSuppliers.get(server);
    }

    private InvoiceRegistry invoices(MinecraftServer server) {
        return invoices != null ? invoices : InvoiceRegistry.get(server);
    }

    /** La référence d'une facture d'électricité ; vide pour une autre facture ou une référence abîmée. */
    public static Optional<Ref> ref(Invoice invoice) {
        return KIND.equals(invoice.payeeKind()) ? Ref.parse(invoice.payeeRef()) : Optional.empty();
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }

    // ---------------------------------------------------------------------------------------------
    // Émission (clôture du jour)
    // ---------------------------------------------------------------------------------------------

    @Override
    public void issue(MinecraftServer server, LocalDate day, long now) {
        PowerSuppliers registry = suppliers(server);
        InvoiceRegistry bills = invoices(server);
        long unit = EconomyConfig.powerPriceUnitFe();
        Map<String, Long> metered = new HashMap<>();
        for (PowerSupplier supplier : registry.all()) {
            for (PowerClient client : registry.clients(supplier.id())) {
                metered.merge(supplier.id(), client.feToday(), PowerBilling::saturatedAdd);
                if (client.state().passes()) {
                    bill(registry, bills, supplier, client, day, now, unit);
                }
                registry.closeClientDay(client.meterSerial());
                if (client.state() == PowerClient.State.RESIGNING) {
                    // Dernière facture émise : détaché pour de bon (jumelage du compteur et des disjoncteurs oublié).
                    registry.removeClient(client.meterSerial());
                    PowerPairing.refreshMeter(server, client.meterSerial());
                    ServerPlayer player = server.getPlayerList().getPlayer(client.player());
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable(MESSAGE + "resigned_done", supplier.name(),
                                client.subscriber()));
                    }
                    LOGGER.info("Electricite : resiliation du compteur {} chez {} faite a la cloture du {}",
                            client.meterSerial(), supplier.name(), day);
                }
            }
        }
        for (PowerClient client : registry.detached()) {
            Optional<PowerSupplier> supplier = registry.byId(client.supplierId());
            if (supplier.isPresent()) {
                metered.merge(client.supplierId(), client.feToday(), PowerBilling::saturatedAdd);
                if (client.state().passes()) {
                    bill(registry, bills, supplier.get(), client, day, now, unit);
                }
            }
            registry.removeDetached(client);
        }
        registry.applyNextCapAndTax();
        for (PowerSupplier supplier : registry.all()) {
            registry.closeSupplierDay(supplier.id(), metered.getOrDefault(supplier.id(), 0L))
                    .ifPresent(closed -> announceTariff(server, registry, supplier, closed));
        }
    }

    private void bill(PowerSuppliers registry, InvoiceRegistry bills, PowerSupplier supplier, PowerClient client,
            LocalDate day, long now, long unit) {
        long amount = PowerBillMath.amount(client.feToday(), supplier.price(), unit, supplier.subscription());
        if (amount <= 0L) {
            return; // une journée à 0 cuivre n'émet pas de papier
        }
        Ref ref = new Ref(supplier.id(), client.meterSerial(), registry.taxPercent(), day.toEpochDay(), client.feToday(),
                supplier.price(), unit, supplier.subscription(), supplier.name());
        Invoice invoice = bills.issuePublic(KIND, ref.encode(), supplier.founder(), supplier.founderName(),
                client.player(), client.playerName(), amount, reason(ref), now);
        registry.noteStatement(client.meterSerial(),
                new StatementLine(now, StatementKind.BILLED, amount, invoice.shortId()));
        LOGGER.info("Electricite : facture {} de {} pour {} ({} FE le {}) : {} cuivres", invoice.shortId(),
                supplier.name(), client.playerName(), client.feToday(), day, amount);
    }

    /** Le motif écrit sur la facture (lu tel quel sur le papier) : fournisseur, jour, kFE, prix, abonnement. */
    private static String reason(Ref ref) {
        return "Électricité " + ref.supplierName() + " du " + DAY.format(ref.day()) + " : " + kfe(ref.fe())
                + " kFE à " + ref.price() + " c les " + ref.unitFe() + " FE, abonnement " + ref.subscription() + " c";
    }

    /** Des FE en milliers, au dixième inférieur : « 2300,0 ». */
    private static String kfe(long fe) {
        return (fe / 1000L) + "," + (fe % 1000L) / 100L;
    }

    private static void announceTariff(MinecraftServer server, PowerSuppliers registry, PowerSupplier supplier,
            PowerSuppliers.DayClosed closed) {
        if (closed.capped()) {
            tellFounder(server, supplier, Component.translatable(MESSAGE + "price_capped", supplier.name(),
                    CoinMath.format(closed.price()), EconomyConfig.powerPriceUnitFe()));
            LOGGER.info("Electricite : prix de {} ramene au plafond ({} cuivres)", supplier.name(), closed.price());
        }
        if (closed.changed()) {
            Set<UUID> told = new HashSet<>();
            for (PowerClient client : registry.clients(supplier.id())) {
                ServerPlayer player = server.getPlayerList().getPlayer(client.player());
                if (player != null && told.add(client.player())) {
                    player.sendSystemMessage(Component.translatable(MESSAGE + "tariff", supplier.name(),
                            CoinMath.format(closed.price()), EconomyConfig.powerPriceUnitFe(),
                            CoinMath.format(closed.subscription())));
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Créancier
    // ---------------------------------------------------------------------------------------------

    /** Le fournisseur de la référence existe-t-il encore ? Sinon la facture est annulée à sa présentation. */
    @Override
    public boolean exists(MinecraftServer server, String ref) {
        return Ref.parse(ref).map(r -> suppliers(server).byId(r.supplierId()).isPresent()).orElse(false);
    }

    @Override
    public void receive(MinecraftServer server, Invoice invoice, long amount) {
        Ref ref = Ref.parse(invoice.payeeRef())
                .orElseThrow(() -> new IllegalStateException("reference abimee : " + invoice.payeeRef()));
        PowerSuppliers registry = suppliers(server);
        PowerSupplier supplier = registry.byId(ref.supplierId())
                .orElseThrow(() -> new IllegalStateException("fournisseur disparu : " + ref.supplierId()));
        long tax = PowerBillMath.tax(amount, ref.taxPercent());
        EnumMap<Coin, Long> toTreasury = CoinMath.decompose(tax);
        EnumMap<Coin, Long> toTill = CoinMath.decompose(amount - tax);
        TreasuryAccount treasury = TreasuryAccount.get(server);
        boolean room = true;
        for (Map.Entry<Coin, Long> part : toTill.entrySet()) {
            room &= registry.tillRoom(supplier.id(), part.getKey()) >= part.getValue();
        }
        for (Map.Entry<Coin, Long> part : toTreasury.entrySet()) {
            room &= treasury.room(part.getKey()) >= part.getValue();
        }
        if (!room) {
            // Rien n'est versé : le prélèvement rend le montant au débiteur et la facture redevient due (tâche 1).
            tellFounder(server, supplier, Component.translatable(MESSAGE + "till_full", supplier.name(),
                    invoice.shortId()));
            throw new IllegalStateException("caisse de " + supplier.name() + " ou tresor plein pour la facture "
                    + invoice.shortId());
        }
        if (tax > 0L) {
            treasury.credit(toTreasury, TreasuryLedger.Kind.POWER_TAX, invoice.debtor(), invoice.debtorName(),
                    "taxe sur l'electricite, facture " + invoice.shortId() + " (" + supplier.name() + ")");
            EconomyLedger.get(server).transferred(MoneyTransfer.POWER_TAX, tax, invoice.debtor(),
                    "taxe sur l'electricite " + invoice.shortId() + " " + invoice.debtorName() + " -> tresor");
        }
        toTill.forEach((coin, count) -> registry.deposit(supplier.id(), coin, count));
        registry.noteStatement(ref.meter(),
                new StatementLine(System.currentTimeMillis(), StatementKind.PAID, amount, invoice.shortId()));
    }

    @Override
    public void refused(MinecraftServer server, Invoice invoice) {
        Optional<Ref> ref = Ref.parse(invoice.payeeRef());
        if (ref.isEmpty()) {
            return;
        }
        PowerSuppliers registry = suppliers(server);
        registry.noteStatement(ref.get().meter(), new StatementLine(System.currentTimeMillis(), StatementKind.REFUSED,
                invoice.amount(), invoice.shortId()));
        registry.byId(ref.get().supplierId()).ifPresent(supplier -> tellFounder(server, supplier,
                Component.translatable(MESSAGE + "founder_unpaid", invoice.debtorName(), invoice.shortId(),
                        CoinMath.format(invoice.amount()))));
    }

    @Override
    public Component describe(Invoice invoice) {
        Optional<Ref> ref = Ref.parse(invoice.payeeRef());
        if (ref.isEmpty()) {
            return Component.literal(invoice.reason());
        }
        Ref r = ref.get();
        return Component.translatable(MESSAGE + "bill", r.supplierName(), DAY.format(r.day()), kfe(r.fe()),
                CoinMath.format(r.price()), r.unitFe(), CoinMath.format(r.subscription()));
    }

    // ---------------------------------------------------------------------------------------------
    // Impayés
    // ---------------------------------------------------------------------------------------------

    /** Les impayés de ce compteur (colonne « impayés » du panneau) : la somme de ses factures d'électricité dues. */
    public long unpaid(MinecraftServer server, UUID meter) {
        long total = 0L;
        for (Invoice invoice : invoices(server).duePublic()) {
            if (ref(invoice).map(r -> r.meter().equals(meter)).orElse(false)) {
                total = saturatedAdd(total, invoice.amount());
            }
        }
        return total;
    }

    /** Rappelle au fondateur qui se connecte les impayés de ses clients (nombre et total), s'il y en a. */
    public void announceFounder(ServerPlayer player) {
        Optional<PowerSupplier> supplier = suppliers(player.server).byFounder(player.getUUID());
        if (supplier.isEmpty()) {
            return;
        }
        int count = 0;
        long total = 0L;
        for (Invoice invoice : invoices(player.server).duePublic()) {
            if (ref(invoice).map(r -> r.supplierId().equals(supplier.get().id())).orElse(false)) {
                count++;
                total = saturatedAdd(total, invoice.amount());
            }
        }
        if (count > 0) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "founder_unpaid_summary", supplier.get().name(),
                    count, CoinMath.format(total)));
        }
    }

    /** Branché sur la connexion : les impayés des clients du fondateur qui se connecte. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            installed.announceFounder(player);
        }
    }

    private static void tellFounder(MinecraftServer server, PowerSupplier supplier, Component message) {
        ServerPlayer founder = server.getPlayerList().getPlayer(supplier.founder());
        if (founder != null) {
            founder.sendSystemMessage(message);
        }
    }
}
