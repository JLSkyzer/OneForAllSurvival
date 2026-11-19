package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.debit.DailyDebit;
import dev.oneforallsurvival.economy.debit.DebitPayee;
import dev.oneforallsurvival.economy.debit.DebitPayees;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Le créancier d'essai du prélèvement : le genre {@value #GENRE}, inscrit par les essais seulement (jamais par le
 * mod). Il garde ce qu'il reçoit, par référence, comme une caisse : ces pièces existent encore, la somme
 * (débiteur + attente + coffre-fort + créancier) se compte avec {@link #recu}. Son émetteur quotidien émet une facture
 * par jour clos dans les registres que les essais lui ont confiés ({@link #armer}). Fil du serveur, développement
 * seulement.
 */
final class PayeurDEssai implements DebitPayee {

    static final String GENRE = "test";
    static final PayeurDEssai INSTANCE = new PayeurDEssai();

    private static final Set<String> EXISTANTS = new HashSet<>();
    private static final Map<String, Long> RECU = new HashMap<>();
    private static final Map<String, Integer> RECUS = new HashMap<>();
    private static final Map<String, Integer> REFUS = new HashMap<>();
    /** Les créanciers piégés : leur {@code receive} lève une exception (créancier en faute). */
    private static final Set<String> PIEGES = new HashSet<>();
    /** Les émissions quotidiennes confiées par les essais : un registre, un débiteur, un montant par jour. */
    private static final List<Emission> EMISSIONS = new ArrayList<>();

    /** Une émission quotidienne : dans {@code registre}, {@code montant} à {@code debiteur} pour {@code ref}. */
    record Emission(InvoiceRegistry registre, String ref, UUID debiteur, String nom, long montant, List<LocalDate> jours) {}

    private PayeurDEssai() {}

    /** Inscrit le genre {@value #GENRE} et son émetteur (sans effet s'ils le sont déjà : même instance). */
    static void inscrire() {
        DebitPayees.register(GENRE, INSTANCE);
        DailyDebit.registerIssuer(GENRE, PayeurDEssai::emettre);
    }

    /** Une référence de créancier neuve, qui existe. */
    static String creancier() {
        String ref = UUID.randomUUID().toString().substring(0, 8);
        EXISTANTS.add(ref);
        return ref;
    }

    /** Le créancier {@code ref} n'existe plus (comme un fournisseur supprimé). */
    static void disparaitre(String ref) {
        EXISTANTS.remove(ref);
    }

    /** Le créancier {@code ref} échoue désormais à recevoir (ou plus, si {@code piege} est faux). */
    static void pieger(String ref, boolean piege) {
        if (piege) {
            PIEGES.add(ref);
        } else {
            PIEGES.remove(ref);
        }
    }

    /** Ce que le créancier {@code ref} a reçu, en cuivres. */
    static long recu(String ref) {
        return RECU.getOrDefault(ref, 0L);
    }

    /** Combien de paiements le créancier {@code ref} a reçus. */
    static int paiements(String ref) {
        return RECUS.getOrDefault(ref, 0);
    }

    /** Combien de refus le créancier {@code ref} a appris. */
    static int refus(String ref) {
        return REFUS.getOrDefault(ref, 0);
    }

    /** Confie une émission quotidienne ; la liste rendue reçoit chaque jour pour lequel l'émetteur a été appelé. */
    static Emission armer(InvoiceRegistry registre, String ref, UUID debiteur, String nom, long montant) {
        Emission emission = new Emission(registre, ref, debiteur, nom, montant, new ArrayList<>());
        EMISSIONS.add(emission);
        return emission;
    }

    static void desarmer(Emission emission) {
        EMISSIONS.remove(emission);
    }

    /** Oublie tout ce qui touche à {@code ref} (fin d'un essai). */
    static void oublier(String ref) {
        EXISTANTS.remove(ref);
        RECU.remove(ref);
        RECUS.remove(ref);
        REFUS.remove(ref);
        PIEGES.remove(ref);
    }

    private static void emettre(MinecraftServer server, LocalDate jour, long now) {
        for (Emission emission : new ArrayList<>(EMISSIONS)) {
            emission.jours().add(jour);
            emission.registre().issuePublic(GENRE, emission.ref(), new UUID(0L, 86L), "Essai", emission.debiteur(),
                    emission.nom(), emission.montant(), "essai du " + jour, now);
        }
    }

    @Override
    public boolean exists(MinecraftServer server, String ref) {
        return EXISTANTS.contains(ref);
    }

    @Override
    public void receive(MinecraftServer server, Invoice invoice, long amount) {
        if (PIEGES.contains(invoice.payeeRef())) {
            throw new IllegalStateException("créancier d'essai piégé : " + invoice.payeeRef());
        }
        RECU.merge(invoice.payeeRef(), amount, Long::sum);
        RECUS.merge(invoice.payeeRef(), 1, Integer::sum);
    }

    @Override
    public void refused(MinecraftServer server, Invoice invoice, long now) {
        REFUS.merge(invoice.payeeRef(), 1, Integer::sum);
    }

    @Override
    public Component describe(Invoice invoice) {
        return Component.literal("essai " + invoice.payeeRef());
    }
}
