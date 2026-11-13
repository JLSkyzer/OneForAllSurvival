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

    public enum Status { DUE, PAID, SETTLED_BY_HAND }

    /** Une facture : qui la doit à qui, combien, pourquoi, quand, et où elle en est. Dates en millisecondes. */
    public record Invoice(UUID id, UUID issuer, String issuerName, UUID debtor, String debtorName, long amount,
            String reason, long issuedAt, Status status, long settledAt) {

        /** Les 8 premiers caractères de l'identifiant, pour les messages et {@code /invoice settle}. */
        public String shortId() {
            return shortId(id);
        }

        public static String shortId(UUID id) {
            return id.toString().substring(0, 8);
        }

        Invoice settled(Status newStatus, long when) {
            return new Invoice(id, issuer, issuerName, debtor, debtorName, amount, reason, issuedAt, newStatus, when);
        }
    }

    /** Dans l'ordre d'émission. */
    private final LinkedHashMap<UUID, Invoice> invoices = new LinkedHashMap<>();
    /** Les paiements dont l'émetteur n'a pas encore été avisé : facture, destination des pièces. */
    private final LinkedHashMap<UUID, CommerceDelivery.Destination> notices = new LinkedHashMap<>();
    /** Les destinations des paiements, pour les avis (gardées tant que la facture l'est). */
    private final Map<UUID, CommerceDelivery.Destination> destinations = new LinkedHashMap<>();

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

    /** Due → payée ; vrai seulement si la facture était due. */
    public boolean markPaid(UUID id, long now) {
        Invoice invoice = invoices.get(id);
        if (invoice == null || invoice.status() != Status.DUE) {
            return false;
        }
        invoices.put(id, invoice.settled(Status.PAID, now));
        setDirty();
        return true;
    }

    /** Due → réglée de main à main, par son émetteur seulement ; aucune pièce ne bouge. */
    public boolean settleByHand(UUID id, UUID issuer, long now) {
        Invoice invoice = invoices.get(id);
        if (invoice == null || invoice.status() != Status.DUE || !invoice.issuer().equals(issuer)) {
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
            list.add(line);
        }
        tag.put("invoices", list);
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
            Invoice invoice = new Invoice(line.getUUID("id"), line.getUUID("issuer"), line.getString("issuerName"),
                    line.getUUID("debtor"), line.getString("debtorName"), line.getLong("amount"),
                    line.getString("reason"), line.getLong("issuedAt"), status, line.getLong("settledAt"));
            registry.invoices.put(invoice.id(), invoice);
            CommerceDelivery.Destination destination = destination(line.getString("paidTo"));
            if (destination != null) {
                registry.destinations.put(invoice.id(), destination);
                if (line.getBoolean("unseen")) {
                    registry.notices.put(invoice.id(), destination);
                }
            }
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
