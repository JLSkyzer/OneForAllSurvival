package dev.oneforallsurvival.grid.supplier;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyClock;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
 * nulle. Un client coupé à distance au moment de la clôture ne paie pas l'abonnement du jour (décision de Killian du
 * 2026-11-18) ; sa consommation d'avant la coupure reste facturée ; coupé toute la journée, il n'a pas de facture. Puis la consommation passe à « hier », un client en résiliation est détaché (son
 * jumelage oublié), les détachés sont oubliés, le plafond et la taxe du lendemain entrent en vigueur, puis les tarifs du
 * lendemain de chaque fournisseur (prix ramené au plafond, fondateur prévenu ; clients prévenus d'un changement, à leur
 * connexion s'ils sont absents), et la
 * sortie du poste et la somme des compteurs passent à « hier ». Une facture garde dans sa référence le taux de taxe du
 * jour qu'elle clôt : un changement de taxe ne touche que les factures suivantes, même pour une facture représentée.
 *
 * <p><b>Paiement</b> ({@link #receive}) : la taxe (arrondie en dessous) au trésor, ligne {@code POWER_TAX} ; le reste à
 * la caisse du poste ; une ligne « payée » au relevé du client. Si la caisse ou le trésor n'a pas la place, rien n'est
 * versé et une exception remonte : le prélèvement rend alors le montant au débiteur et la facture redevient due (voie
 * de la tâche 1) ; aucune pièce n'est créée ni détruite. <b>Refus</b> ({@link #refused}, au premier refus de chaque
 * facture) : ligne au relevé, fondateur prévenu ; la colonne « impayés » se lit dans le registre des factures, par
 * contrat ({@link #unpaid}), jamais en double.
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

    /** Version du format de la référence, écrite en tête. */
    static final String VERSION = "2";

    /**
     * La référence d'une facture d'électricité : tout ce qui la décrit, figé à l'émission. Écrite
     * {@code 2/<fournisseur>/<compteur>/<taxe>/<jour>/<FE>/<prix>/<tranche>/<abonnement>/<contrat>/<nom du fournisseur>}
     * (le nom vient en dernier : il peut contenir « / »).
     *
     * @param taxPercent la taxe du jour facturé, retenue au paiement quel que soit le jour où il arrive
     * @param epochDay le jour facturé
     * @param contract la date du contrat facturé ({@link PowerClient#since}) ; 0 pour une référence d'avant la version 2
     */
    public record Ref(String supplierId, UUID meter, int taxPercent, long epochDay, long fe, long price, long unitFe,
            long subscription, long contract, String supplierName) {

        /** Une référence sans date de contrat (essais, ancien format). */
        public Ref(String supplierId, UUID meter, int taxPercent, long epochDay, long fe, long price, long unitFe,
                long subscription, String supplierName) {
            this(supplierId, meter, taxPercent, epochDay, fe, price, unitFe, subscription, 0L, supplierName);
        }

        public String encode() {
            return String.join("/", VERSION, supplierId, meter.toString(), Integer.toString(taxPercent), Long.toString(epochDay),
                    Long.toString(fe), Long.toString(price), Long.toString(unitFe), Long.toString(subscription),
                    Long.toString(contract), supplierName);
        }

        /** Relit une référence ; vide si elle est abîmée (la facture est alors annulée à sa présentation). */
        static Optional<Ref> parse(@Nullable String text) {
            if (text == null) {
                return Optional.empty();
            }
            // Version 2 : « 2/ » en tête, date du contrat avant le nom. Version 1 (« 1/ ») et sans version (le format de
            // la première livraison, 4b803f6) : toujours lus, sans date de contrat, pour qu'une facture due ne devienne
            // jamais illisible (elle serait annulée à sa présentation). Un identifiant de fournisseur fait 12
            // caractères : jamais « 1 » ni « 2 ».
            boolean v2 = text.startsWith(VERSION + "/");
            String body = v2 ? text.substring(VERSION.length() + 1) : text.startsWith("1/") ? text.substring(2) : text;
            int fields = v2 ? 10 : 9;
            String[] parts = body.split("/", fields);
            if (parts.length != fields || parts[0].isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Ref(parts[0], UUID.fromString(parts[1]), Integer.parseInt(parts[2]),
                        Long.parseLong(parts[3]), Long.parseLong(parts[4]), Long.parseLong(parts[5]),
                        Long.parseLong(parts[6]), Long.parseLong(parts[7]), v2 ? Long.parseLong(parts[8]) : 0L,
                        parts[fields - 1]));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        public LocalDate day() {
            return LocalDate.ofEpochDay(epochDay);
        }
    }

    /**
     * Ce que fait la clôture d'un compteur qui a changé d'état (résilié, donc détaché) : prévenir ses voisins
     * ({@link PowerPairing#refreshMeter}), dont le code peut être celui d'un autre mod.
     */
    @FunctionalInterface
    public interface Neighbours {
        void refresh(MinecraftServer server, UUID meter);
    }

    @Nullable
    private final PowerSuppliers suppliers;
    @Nullable
    private final InvoiceRegistry invoices;
    private final Neighbours neighbours;

    /**
     * @param suppliers le registre des fournisseurs, ou nul pour celui du serveur
     * @param invoices le registre des factures, ou nul pour celui du serveur
     */
    public PowerBilling(@Nullable PowerSuppliers suppliers, @Nullable InvoiceRegistry invoices) {
        this(suppliers, invoices, PowerPairing::refreshMeter);
    }

    /** Comme {@link #PowerBilling(PowerSuppliers, InvoiceRegistry)}, avec les voisins donnés (essais). */
    public PowerBilling(@Nullable PowerSuppliers suppliers, @Nullable InvoiceRegistry invoices, Neighbours neighbours) {
        this.suppliers = suppliers;
        this.invoices = invoices;
        this.neighbours = neighbours;
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

    /** Un contrat du jour : un fournisseur, un compteur, un joueur. Clé du regroupement de la clôture. */
    private record Contract(String supplierId, UUID meter, UUID player) {}

    /**
     * Ce qu'un contrat a cumulé dans la journée : son débiteur (nul : rien à facturer), sa consommation, et ses lignes
     * du registre (le client en cours, les détachés du jour).
     */
    private static final class Day {
        @Nullable
        PowerClient debtor;
        long fe;
        final List<PowerClient> clients = new ArrayList<>();
        final List<PowerClient> detached = new ArrayList<>();
    }

    /**
     * Clôt le jour {@code day} : d'abord les transitions d'état, contrat par contrat (facture, consommation à « hier »,
     * résiliation faite, détachés oubliés), chacune sous {@code try} : un contrat en erreur est remis comme avant (sa
     * facture annulée si elle était émise) et garde sa consommation pour la clôture suivante, la clôture va à son terme et
     * rien n'est facturé deux fois. Puis, à part et chacun sous {@code try}, les effets de bord : voisins des compteurs
     * détachés (code d'autres mods, {@link Neighbours}), avis. Enfin le plafond, la taxe et les tarifs du lendemain.
     */
    @Override
    public void issue(MinecraftServer server, LocalDate day, long now) {
        PowerSuppliers registry = suppliers(server);
        InvoiceRegistry bills = invoices(server);
        long unit = EconomyConfig.powerPriceUnitFe();
        Map<String, Long> metered = new HashMap<>();
        // Regroupement par contrat (fournisseur, compteur, joueur) : un compteur détaché puis rejumelé le même jour, ou
        // détaché deux fois, reste UNE facture du jour (consommation cumulée, un seul abonnement). Un autre fournisseur
        // ou un autre joueur est un autre contrat.
        Map<Contract, Day> contracts = new LinkedHashMap<>();
        for (PowerSupplier supplier : registry.all()) {
            for (PowerClient client : registry.clients(supplier.id())) {
                metered.merge(supplier.id(), client.feToday(), PowerBilling::saturatedAdd);
                Day d = contracts.computeIfAbsent(contractOf(client), k -> new Day());
                d.fe = saturatedAdd(d.fe, client.feToday());
                d.clients.add(client);
                if (client.state().passes()) {
                    d.debtor = client; // le contrat en cours est le débiteur
                }
            }
        }
        for (PowerClient client : registry.detached()) {
            if (registry.byId(client.supplierId()).isEmpty()) {
                continue;
            }
            metered.merge(client.supplierId(), client.feToday(), PowerBilling::saturatedAdd);
            Day d = contracts.computeIfAbsent(contractOf(client), k -> new Day());
            d.fe = saturatedAdd(d.fe, client.feToday());
            d.detached.add(client);
            if (client.state().passes() && d.debtor == null) {
                d.debtor = client;
            }
        }
        for (PowerClient orphan : registry.detached()) {
            if (registry.byId(orphan.supplierId()).isEmpty()) {
                registry.removeDetached(orphan); // fournisseur supprimé : rien à facturer
            }
        }

        List<Runnable> effects = new ArrayList<>();
        for (Map.Entry<Contract, Day> entry : contracts.entrySet()) {
            closeContract(server, registry, bills, entry.getKey(), entry.getValue(), day, now, unit, effects);
        }
        for (Runnable effect : effects) {
            try {
                effect.run();
            } catch (RuntimeException e) {
                LOGGER.warn("Electricite : effet de la cloture du {} en echec (les factures et les etats sont faits)",
                        day, e);
            }
        }
        registry.applyNextCapAndTax();
        for (PowerSupplier supplier : registry.all()) {
            Optional<PowerSuppliers.DayClosed> closed = registry.closeSupplierDay(supplier.id(),
                    metered.getOrDefault(supplier.id(), 0L));
            if (closed.isPresent()) {
                try {
                    announceTariff(server, registry, supplier, closed.get());
                } catch (RuntimeException e) {
                    LOGGER.warn("Electricite : avis du tarif de {} en echec", supplier.name(), e);
                }
            }
        }
    }

    /**
     * Les transitions d'état d'un contrat ; ses effets de bord vont dans {@code effects}. En erreur : le contrat est remis
     * comme avant (facture émise annulée, clients remis avec leur consommation du jour, détachés gardés), journalisé en
     * WARN ; sa consommation sera facturée à la clôture suivante, une seule fois.
     */
    private void closeContract(MinecraftServer server, PowerSuppliers registry, InvoiceRegistry bills, Contract key,
            Day d, LocalDate day, long now, long unit, List<Runnable> effects) {
        Optional<PowerSupplier> supplier = registry.byId(key.supplierId());
        String name = supplier.map(PowerSupplier::name).orElse("?");
        Optional<Invoice> issued = Optional.empty();
        List<Runnable> mine = new ArrayList<>();
        try {
            if (d.debtor != null && supplier.isPresent()) {
                issued = bill(registry, bills, supplier.get(), d.debtor, d.fe, day, now, unit);
            }
            for (PowerClient client : d.clients) {
                registry.closeClientDay(client.meterSerial());
                if (client.state() == PowerClient.State.RESIGNING) {
                    // Dernière facture émise : détaché pour de bon (jumelage du compteur et des disjoncteurs oublié).
                    registry.removeClient(client.meterSerial());
                    UUID meter = client.meterSerial();
                    mine.add(() -> neighbours.refresh(server, meter));
                    mine.add(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayer(client.player());
                        if (player != null) {
                            player.sendSystemMessage(Component.translatable(MESSAGE + "resigned_done", name,
                                    client.subscriber()));
                        }
                    });
                    LOGGER.info("Electricite : resiliation du compteur {} chez {} faite a la cloture du {}", meter, name,
                            day);
                }
            }
            for (PowerClient gone : d.detached) {
                registry.removeDetached(gone);
            }
            effects.addAll(mine);
        } catch (RuntimeException e) {
            issued.ifPresent(invoice -> bills.cancel(invoice.id(), now));
            for (PowerClient client : d.clients) {
                if (registry.clientOf(client.meterSerial()).isPresent()) {
                    registry.putClient(client); // sa consommation du jour revient
                }
            }
            LOGGER.warn("Electricite : cloture du compteur {} chez {} en echec le {} ; sa consommation attend la "
                    + "cloture suivante", key.meter(), name, day, e);
        }
    }

    private static Contract contractOf(PowerClient client) {
        return new Contract(client.supplierId(), client.meterSerial(), client.player());
    }

    /** La facture du jour de ce contrat, si elle n'est pas nulle. */
    private Optional<Invoice> bill(PowerSuppliers registry, InvoiceRegistry bills, PowerSupplier supplier,
            PowerClient client, long fe, LocalDate day, long now, long unit) {
        // Coupé à distance au moment de la clôture : pas d'abonnement ce jour-là (décision de Killian, 2026-11-18) ;
        // ce qui est passé avant la coupure a été reçu et reste facturé.
        long subscription = client.cut() ? 0L : supplier.subscription();
        long amount = PowerBillMath.amount(fe, supplier.price(), unit, subscription);
        if (amount <= 0L) {
            return Optional.empty(); // une journée à 0 cuivre n'émet pas de papier
        }
        Ref ref = new Ref(supplier.id(), client.meterSerial(), registry.taxPercent(), day.toEpochDay(), fe,
                supplier.price(), unit, subscription, client.since(), supplier.name());
        Invoice invoice = bills.issuePublic(KIND, ref.encode(), supplier.founder(), supplier.founderName(),
                client.player(), client.playerName(), amount, reason(ref), now);
        registry.noteStatement(client.meterSerial(), supplier.id(), client.player(),
                new StatementLine(now, StatementKind.BILLED, amount, invoice.shortId()));
        LOGGER.info("Electricite : facture {} de {} pour {} ({} FE le {}) : {} cuivres", invoice.shortId(),
                supplier.name(), client.playerName(), fe, day, amount);
        return Optional.of(invoice);
    }

    /**
     * Le motif brut gardé au registre : neutre (nom du fournisseur et date), sans mot d'aucune langue. Le papier, le
     * reçu et {@code /invoice} affichent pour une facture publique le motif traduit ({@link #describe}).
     */
    private static String reason(Ref ref) {
        return ref.supplierName() + " " + DAY.format(ref.day());
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
            // Un avis par client et par changement entré en vigueur : tout de suite s'il est connecté, sinon à sa
            // connexion (cahier, feature 83 : « les clients en sont prévenus »).
            Set<UUID> told = new HashSet<>();
            for (PowerClient client : registry.clients(supplier.id())) {
                if (told.add(client.player())) {
                    PowerPairing.tell(server, registry, client.player(), Component.translatable(MESSAGE + "tariff",
                            supplier.name(), CoinMath.format(closed.price()), EconomyConfig.powerPriceUnitFe(),
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
        EconomyLedger.get(server).electricityPaid(amount);
        long when = invoice.settledAt() > 0L ? invoice.settledAt() : EconomyClock.now();
        registry.noteStatement(ref.meter(), ref.supplierId(), invoice.debtor(),
                new StatementLine(when, StatementKind.PAID, amount, invoice.shortId()));
    }

    @Override
    public void refused(MinecraftServer server, Invoice invoice, long now) {
        Optional<Ref> ref = Ref.parse(invoice.payeeRef());
        if (ref.isEmpty()) {
            return;
        }
        PowerSuppliers registry = suppliers(server);
        registry.noteStatement(ref.get().meter(), ref.get().supplierId(), invoice.debtor(),
                new StatementLine(now, StatementKind.REFUSED, invoice.amount(), invoice.shortId()));
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

    /**
     * Les impayés de ce contrat (colonne « impayés » du panneau) : la somme de ses factures d'électricité dues, comptées
     * par contrat (même compteur, même fournisseur, même débiteur, même date de contrat). Un nouveau locataire du
     * compteur n'hérite pas des dettes de l'ancien, ni un nouveau fournisseur de celles dues à l'ancien, ni un contrat
     * refait de celles du contrat passé. Une facture d'avant la version 2 de la référence (sans date) compte pour un
     * contrat d'avant cette donnée ({@code since} = 0).
     */
    public long unpaid(MinecraftServer server, PowerClient client) {
        long total = 0L;
        for (Invoice invoice : invoices(server).duePublic()) {
            if (invoice.debtor().equals(client.player()) && ref(invoice).map(r -> r.meter().equals(client.meterSerial())
                    && r.supplierId().equals(client.supplierId()) && r.contract() == client.since()).orElse(false)) {
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
