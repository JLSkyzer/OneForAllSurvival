package dev.oneforallsurvival.economy.invoice;

import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.EconomyConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Le registre des factures : il fait foi. L'objet {@link InvoiceItem} n'est qu'une vue ; une facture se paie
 * seulement si son identifiant est ici, due, et payée par son débiteur. Sauvegardé avec le monde ({@value #KEY}).
 * Fil du serveur seulement.
 *
 * <p>Les factures réglées sont oubliées après {@link EconomyConfig#invoiceKeepDays()} jours ({@link #prune}) ;
 * une copie d'une facture oubliée porte alors un identifiant inconnu, qui ne se paie jamais. Une facture due n'est
 * jamais oubliée.
 */
public final class InvoiceRegistry extends SavedData {

    public static final String KEY = "oneforallsurvival_invoices";

    private static final long DAY_MILLIS = 86_400_000L;

    /**
     * {@link #CANCELLED} : facture publique dont le créancier n'existe plus (fournisseur supprimé, par exemple) ;
     * elle ne se paie plus, aucune pièce n'a bougé.
     */
    public enum Status { DUE, PAID, SETTLED_BY_HAND, CANCELLED }

    /**
     * Une facture : qui la doit à qui, combien, pourquoi, quand, et où elle en est. Dates en millisecondes.
     *
     * <p>{@code payee} vide : facture <b>privée</b> d'E2 (carnet, {@code /invoice}, paquets), jamais prélevée. Sinon
     * {@code <genre>:<référence>} (ex. {@code electricity:3f2a9c1b}) : facture <b>publique</b>, émise par le serveur
     * seulement ({@link #issuePublic}), prélevée à minuit par {@code DirectDebit} ; {@code issuer} est alors le
     * créancier (le fondateur du fournisseur, par exemple).
     */
    public record Invoice(UUID id, UUID issuer, String issuerName, UUID debtor, String debtorName, long amount,
            String reason, long issuedAt, Status status, long settledAt, String payee) {

        public Invoice {
            payee = payee == null ? "" : payee;
        }

        /** Une facture privée (E2). */
        public Invoice(UUID id, UUID issuer, String issuerName, UUID debtor, String debtorName, long amount,
                String reason, long issuedAt, Status status, long settledAt) {
            this(id, issuer, issuerName, debtor, debtorName, amount, reason, issuedAt, status, settledAt, "");
        }

        /** Les 8 premiers caractères de l'identifiant, pour les messages et {@code /invoice settle}. */
        public String shortId() {
            return shortId(id);
        }

        public static String shortId(UUID id) {
            return id.toString().substring(0, 8);
        }

        /** Vrai pour une facture publique (prélevable), faux pour une facture privée d'E2. */
        public boolean isPublic() {
            return !payee.isEmpty();
        }

        /** Le genre du créancier d'une facture publique ({@code electricity}) ; vide pour une facture privée. */
        public String payeeKind() {
            int colon = payee.indexOf(':');
            return colon < 0 ? payee : payee.substring(0, colon);
        }

        /** La référence du créancier dans son genre ({@code 3f2a9c1b}) ; vide pour une facture privée. */
        public String payeeRef() {
            int colon = payee.indexOf(':');
            return colon < 0 ? "" : payee.substring(colon + 1);
        }

        Invoice settled(Status newStatus, long when) {
            return new Invoice(id, issuer, issuerName, debtor, debtorName, amount, reason, issuedAt, newStatus, when,
                    payee);
        }
    }

    /** Dans l'ordre d'émission. */
    private final LinkedHashMap<UUID, Invoice> invoices = new LinkedHashMap<>();
    /** Les paiements dont l'émetteur n'a pas encore été avisé : facture, destination des pièces. */
    private final LinkedHashMap<UUID, CommerceDelivery.Destination> notices = new LinkedHashMap<>();
    /** Les destinations des paiements, pour les avis (gardées tant que la facture l'est). */
    private final Map<UUID, CommerceDelivery.Destination> destinations = new LinkedHashMap<>();

    /** Les prélèvements refusés dont le débiteur n'a pas encore été avisé (absent au refus). */
    private final java.util.LinkedHashSet<UUID> refusedUnseen = new java.util.LinkedHashSet<>();
    /** Les prélèvements payés en l'absence du débiteur : facture, source (nom de {@code DebitPlan.Source}). */
    private final LinkedHashMap<UUID, String> debitedUnseen = new LinkedHashMap<>();
    /**
     * Le dernier jour dont les factures dues ont été présentées par le prélèvement de minuit ({@code DailyDebit}) ; nul
     * avant le premier passage. {@code /eco salary resetday} peut le ramener en arrière.
     */
    @javax.annotation.Nullable
    private java.time.LocalDate lastDebitDay;
    /**
     * Le plus grand jour pour lequel les émetteurs ont émis leurs factures ; jamais ramené en arrière (un jour émis ne
     * l'est pas deux fois, même après une horloge en avance puis {@code resetday}). Nul : aucun jour émis noté.
     */
    @javax.annotation.Nullable
    private java.time.LocalDate lastIssuedDay;

    /** Vrai une fois le premier oubli fait après le chargement (en mémoire seulement). */
    private boolean pruned;

    InvoiceRegistry() {}

    /** Le registre du monde ; au premier appel, les factures réglées trop anciennes sont oubliées. */
    public static InvoiceRegistry get(MinecraftServer server) {
        InvoiceRegistry registry = server.overworld().getDataStorage()
                .computeIfAbsent(InvoiceRegistry::load, InvoiceRegistry::new, KEY);
        if (!registry.pruned) {
            registry.pruned = true;
            registry.prune(System.currentTimeMillis());
        }
        return registry;
    }

    // ---------------------------------------------------------------------------------------------
    // Écriture
    // ---------------------------------------------------------------------------------------------

    /** Enregistre une nouvelle facture, due, d'identifiant neuf. L'appelant a tout vérifié. */
    public Invoice issue(ServerPlayer issuer, ServerPlayer debtor, long amount, String reason, long now) {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (invoices.containsKey(id));
        Invoice invoice = new Invoice(id, issuer.getUUID(), issuer.getGameProfile().getName(), debtor.getUUID(),
                debtor.getGameProfile().getName(), amount, reason, now, Status.DUE, 0L);
        invoices.put(id, invoice);
        prune(now);
        setDirty();
        return invoice;
    }

    /**
     * Enregistre une facture <b>publique</b>, due, d'identifiant neuf : {@code payee} = {@code kind:ref}. Aucun chemin
     * joueur n'y mène (le carnet, {@code /invoice} et les paquets d'E2 n'appellent que {@link #issue}) : seul le
     * serveur l'appelle, pour un genre inscrit à {@code DebitPayees}. L'appelant a tout vérifié.
     *
     * @throws IllegalArgumentException genre vide ou contenant « : », montant pas strictement positif
     */
    public Invoice issuePublic(String kind, String ref, UUID creditorId, String creditorName, UUID debtor,
            String debtorName, long amount, String reason, long now) {
        if (kind == null || kind.isEmpty() || kind.indexOf(':') >= 0 || amount <= 0L) {
            throw new IllegalArgumentException("facture publique : genre '" + kind + "', montant " + amount);
        }
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (invoices.containsKey(id));
        Invoice invoice = new Invoice(id, creditorId, creditorName, debtor, debtorName, amount, reason, now, Status.DUE,
                0L, kind + ":" + (ref == null ? "" : ref));
        invoices.put(id, invoice);
        prune(now);
        setDirty();
        return invoice;
    }

    /** Due → payée ; vrai seulement si la facture était due. */
    public boolean markPaid(UUID id, long now) {
        Invoice invoice = invoices.get(id);
        if (invoice == null || invoice.status() != Status.DUE) {
            return false;
        }
        invoices.put(id, invoice.settled(Status.PAID, now));
        refusedUnseen.remove(id);
        setDirty();
        return true;
    }

    /**
     * Payée → de nouveau due : seulement pour défaire un prélèvement dont le créancier a échoué à recevoir les pièces
     * (rendues au débiteur par l'appelant). Vrai si la facture était payée.
     */
    public boolean reopen(UUID id) {
        Invoice invoice = invoices.get(id);
        if (invoice == null || invoice.status() != Status.PAID) {
            return false;
        }
        invoices.put(id, invoice.settled(Status.DUE, 0L));
        setDirty();
        return true;
    }

    /** Due → annulée (créancier disparu) ; vrai seulement si la facture était due. Aucune pièce ne bouge. */
    public boolean cancel(UUID id, long now) {
        Invoice invoice = invoices.get(id);
        if (invoice == null || invoice.status() != Status.DUE) {
            return false;
        }
        invoices.put(id, invoice.settled(Status.CANCELLED, now));
        refusedUnseen.remove(id);
        setDirty();
        return true;
    }

    /**
     * Due → réglée de main à main, par son émetteur seulement ; aucune pièce ne bouge. Jamais pour une facture
     * publique : son créancier la reçoit par le prélèvement, qui note le paiement là où elle a été émise.
     */
    public boolean settleByHand(UUID id, UUID issuer, long now) {
        Invoice invoice = invoices.get(id);
        if (invoice == null || invoice.status() != Status.DUE || !invoice.issuer().equals(issuer)
                || invoice.isPublic()) {
            return false;
        }
        invoices.put(id, invoice.settled(Status.SETTLED_BY_HAND, now));
        setDirty();
        return true;
    }

    /** Note où sont allées les pièces d'une facture payée, et que son émetteur doit encore en être avisé. */
    public void noticeLater(UUID id, CommerceDelivery.Destination destination) {
        if (invoices.containsKey(id)) {
            notices.put(id, destination);
            destinations.put(id, destination);
            setDirty();
        }
    }

    /** Note où sont allées les pièces (l'émetteur a déjà été avisé). */
    public void noteDestination(UUID id, CommerceDelivery.Destination destination) {
        if (invoices.containsKey(id)) {
            destinations.put(id, destination);
            setDirty();
        }
    }

    /** Les avis de cet émetteur ont été donnés. */
    public void markSeen(UUID issuer) {
        if (notices.keySet().removeIf(id -> {
            Invoice invoice = invoices.get(id);
            return invoice == null || invoice.issuer().equals(issuer);
        })) {
            setDirty();
        }
    }

    /** Un prélèvement de cette facture a été refusé en l'absence du débiteur : il l'apprendra à sa connexion. */
    public void noteRefusedUnseen(UUID id) {
        if (invoices.containsKey(id) && refusedUnseen.add(id)) {
            setDirty();
        }
    }

    /** Cette facture a été prélevée en l'absence du débiteur, depuis {@code source} : il l'apprendra à sa connexion. */
    public void noteDebitedUnseen(UUID id, String source) {
        if (invoices.containsKey(id)) {
            debitedUnseen.put(id, source);
            setDirty();
        }
    }

    /** Les refus non lus de ce débiteur dont la facture est encore due, retirés (les autres sont oubliés aussi). */
    public List<Invoice> takeRefusedUnseen(UUID debtor) {
        List<Invoice> out = new ArrayList<>();
        boolean changed = refusedUnseen.removeIf(id -> {
            Invoice invoice = invoices.get(id);
            if (invoice == null) {
                return true;
            }
            if (!invoice.debtor().equals(debtor)) {
                return false;
            }
            if (invoice.status() == Status.DUE) {
                out.add(invoice);
            }
            return true;
        });
        if (changed) {
            setDirty();
        }
        return out;
    }

    /** Les prélèvements faits en l'absence de ce débiteur : facture et source, retirés. */
    public Map<Invoice, String> takeDebitedUnseen(UUID debtor) {
        Map<Invoice, String> out = new LinkedHashMap<>();
        boolean changed = debitedUnseen.entrySet().removeIf(entry -> {
            Invoice invoice = invoices.get(entry.getKey());
            if (invoice == null) {
                return true;
            }
            if (!invoice.debtor().equals(debtor)) {
                return false;
            }
            out.put(invoice, entry.getValue());
            return true;
        });
        if (changed) {
            setDirty();
        }
        return out;
    }

    /** Le dernier jour clos par le prélèvement de minuit ; nul avant le premier passage. */
    @javax.annotation.Nullable
    public java.time.LocalDate lastDebitDay() {
        return lastDebitDay;
    }

    /** Le plus grand jour déjà émis ; nul si aucun n'est noté. */
    @javax.annotation.Nullable
    public java.time.LocalDate lastIssuedDay() {
        return lastIssuedDay;
    }

    /** Note un jour émis ; ne fait jamais reculer le plus grand jour émis. */
    public void noteIssuedDay(java.time.LocalDate day) {
        if (day != null && (lastIssuedDay == null || day.isAfter(lastIssuedDay))) {
            lastIssuedDay = day;
            setDirty();
        }
    }

    public void setLastDebitDay(java.time.LocalDate day) {
        if (!java.util.Objects.equals(lastDebitDay, day)) {
            lastDebitDay = day;
            setDirty();
        }
    }

    /** Oublie les factures réglées depuis plus de {@link EconomyConfig#invoiceKeepDays()} jours. */
    public void prune(long now) {
        long keep = EconomyConfig.invoiceKeepDays() * DAY_MILLIS;
        List<UUID> old = new ArrayList<>();
        for (Invoice invoice : invoices.values()) {
            if (invoice.status() != Status.DUE && now - invoice.settledAt() > keep) {
                old.add(invoice.id());
            }
        }
        for (UUID id : old) {
            invoices.remove(id);
            notices.remove(id);
            destinations.remove(id);
            refusedUnseen.remove(id);
            debitedUnseen.remove(id);
        }
        if (!old.isEmpty()) {
            setDirty();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lecture
    // ---------------------------------------------------------------------------------------------

    public Optional<Invoice> get(UUID id) {
        return Optional.ofNullable(invoices.get(id));
    }

    /** Les factures publiques dues, dans l'ordre d'émission (la plus ancienne d'abord). */
    public List<Invoice> duePublic() {
        List<Invoice> out = new ArrayList<>();
        for (Invoice invoice : invoices.values()) {
            if (invoice.status() == Status.DUE && invoice.isPublic()) {
                out.add(invoice);
            }
        }
        return out;
    }

    /** Les factures émises par ce joueur, de la plus ancienne à la plus récente. */
    public List<Invoice> issuedBy(UUID issuer) {
        List<Invoice> out = new ArrayList<>();
        for (Invoice invoice : invoices.values()) {
            if (invoice.issuer().equals(issuer)) {
                out.add(invoice);
            }
        }
        return out;
    }

    /** Les factures adressées à ce joueur, de la plus ancienne à la plus récente. */
    public List<Invoice> owedBy(UUID debtor) {
        List<Invoice> out = new ArrayList<>();
        for (Invoice invoice : invoices.values()) {
            if (invoice.debtor().equals(debtor)) {
                out.add(invoice);
            }
        }
        return out;
    }

    /** Les factures payées dont l'émetteur n'a pas encore été avisé. */
    public List<Invoice> unseenPaid(UUID issuer) {
        List<Invoice> out = new ArrayList<>();
        for (UUID id : notices.keySet()) {
            Invoice invoice = invoices.get(id);
            if (invoice != null && invoice.issuer().equals(issuer)) {
                out.add(invoice);
            }
        }
        return out;
    }

    /** Où sont allées les pièces de cette facture payée ; {@code null} si on ne le sait pas. */
    public CommerceDelivery.Destination destinationOf(UUID id) {
        return destinations.get(id);
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Invoice invoice : invoices.values()) {
            CompoundTag line = new CompoundTag();
            line.putUUID("id", invoice.id());
            line.putUUID("issuer", invoice.issuer());
            line.putString("issuerName", invoice.issuerName());
            line.putUUID("debtor", invoice.debtor());
            line.putString("debtorName", invoice.debtorName());
            line.putLong("amount", invoice.amount());
            line.putString("reason", invoice.reason());
            line.putLong("issuedAt", invoice.issuedAt());
            line.putString("status", invoice.status().name());
            line.putLong("settledAt", invoice.settledAt());
            CommerceDelivery.Destination destination = destinations.get(invoice.id());
            if (destination != null) {
                line.putString("paidTo", destination.name());
            }
            if (notices.containsKey(invoice.id())) {
                line.putBoolean("unseen", true);
            }
            if (invoice.isPublic()) {
                line.putString("payee", invoice.payee());
            }
            if (refusedUnseen.contains(invoice.id())) {
                line.putBoolean("refusedUnseen", true);
            }
            String debited = debitedUnseen.get(invoice.id());
            if (debited != null) {
                line.putString("debitedUnseen", debited);
            }
            list.add(line);
        }
        tag.put("invoices", list);
        if (lastDebitDay != null) {
            tag.putString("lastDebitDay", lastDebitDay.toString());
        }
        if (lastIssuedDay != null) {
            tag.putString("lastIssuedDay", lastIssuedDay.toString());
        }
        return tag;
    }

    public static InvoiceRegistry load(CompoundTag tag) {
        InvoiceRegistry registry = new InvoiceRegistry();
        for (Tag raw : tag.getList("invoices", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) raw;
            if (!line.hasUUID("id") || !line.hasUUID("issuer") || !line.hasUUID("debtor")) {
                continue;
            }
            Status status;
            try {
                status = Status.valueOf(line.getString("status").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                continue;
            }
            // Une sauvegarde d'avant le lot E6 n'a pas de « payee » : facture privée.
            Invoice invoice = new Invoice(line.getUUID("id"), line.getUUID("issuer"), line.getString("issuerName"),
                    line.getUUID("debtor"), line.getString("debtorName"), line.getLong("amount"),
                    line.getString("reason"), line.getLong("issuedAt"), status, line.getLong("settledAt"),
                    line.getString("payee"));
            registry.invoices.put(invoice.id(), invoice);
            if (line.getBoolean("refusedUnseen")) {
                registry.refusedUnseen.add(invoice.id());
            }
            if (line.contains("debitedUnseen", Tag.TAG_STRING)) {
                registry.debitedUnseen.put(invoice.id(), line.getString("debitedUnseen"));
            }
            CommerceDelivery.Destination destination = destination(line.getString("paidTo"));
            if (destination != null) {
                registry.destinations.put(invoice.id(), destination);
                if (line.getBoolean("unseen")) {
                    registry.notices.put(invoice.id(), destination);
                }
            }
        }
        if (tag.contains("lastDebitDay", Tag.TAG_STRING)) {
            try {
                registry.lastDebitDay = java.time.LocalDate.parse(tag.getString("lastDebitDay"));
            } catch (java.time.format.DateTimeParseException e) {
                registry.lastDebitDay = null; // relu comme un premier passage : la veille sera notée close
            }
        }
        if (tag.contains("lastIssuedDay", Tag.TAG_STRING)) {
            try {
                registry.lastIssuedDay = java.time.LocalDate.parse(tag.getString("lastIssuedDay"));
            } catch (java.time.format.DateTimeParseException e) {
                registry.lastIssuedDay = registry.lastDebitDay;
            }
        } else {
            // Une sauvegarde d'avant le tour 1 de la tâche 6 n'a qu'un jour : tout jour clos y avait été émis.
            registry.lastIssuedDay = registry.lastDebitDay;
        }
        return registry;
    }

    private static CommerceDelivery.Destination destination(String name) {
        for (CommerceDelivery.Destination destination : CommerceDelivery.Destination.values()) {
            if (destination.name().equals(name)) {
                return destination;
            }
        }
        return null;
    }
}
