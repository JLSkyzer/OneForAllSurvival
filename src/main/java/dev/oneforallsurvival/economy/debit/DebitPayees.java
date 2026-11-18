package dev.oneforallsurvival.economy.debit;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les créanciers des factures publiques, par genre : le <b>point d'extension</b> du prélèvement automatique (feature
 * 86). Un genre inscrit ici peut émettre des factures publiques ({@code InvoiceRegistry.issuePublic}), que
 * {@link DirectDebit} présente à minuit et dont il verse les pièces au créancier par {@link DebitPayee#receive}.
 *
 * <p>À ce lot (E6), seule l'électricité ({@code electricity}, caisse du poste du fournisseur) s'inscrit. Viendront par
 * ce même point, avec leurs lots, sans rien coder ici :
 * <ul>
 *   <li>les <b>amendes et cautions</b> (lot du policier) : créancier le trésor, le paiement noté au casier ;</li>
 *   <li>l'<b>impôt</b> (lot du maire) : créancier le trésor ;</li>
 *   <li>l'<b>abonnement du téléphone</b> (lot du téléphone) : créancier la caisse de l'antenne.</li>
 * </ul>
 * Chacun s'inscrit ici et, s'il émet des factures chaque jour, par {@link DailyDebit#registerIssuer}.
 */
public final class DebitPayees {

    private static final Map<String, DebitPayee> PAYEES = new ConcurrentHashMap<>();

    private DebitPayees() {}

    /**
     * Inscrit (ou remplace) le créancier d'un genre. Le genre ne contient pas « : » (il commence le {@code payee}
     * d'une facture : {@code <genre>:<référence>}).
     */
    public static void register(String kind, DebitPayee payee) {
        if (kind == null || kind.isEmpty() || kind.indexOf(':') >= 0 || payee == null) {
            throw new IllegalArgumentException("genre de facture publique invalide : '" + kind + "'");
        }
        PAYEES.put(kind, payee);
    }

    /** Le créancier de ce genre, s'il est inscrit. */
    public static Optional<DebitPayee> get(String kind) {
        return kind == null ? Optional.empty() : Optional.ofNullable(PAYEES.get(kind));
    }
}
