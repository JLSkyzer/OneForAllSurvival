package dev.oneforallsurvival.economy.debit;

import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Le créancier d'un genre de facture publique ({@code electricity}, plus tard amendes, impôt, abonnement du téléphone) :
 * il dit si une référence existe encore, reçoit les pièces d'un prélèvement payé et apprend les refus. Inscrit par
 * {@link DebitPayees#register}. Fil du serveur.
 */
public interface DebitPayee {

    /**
     * La référence ({@code payeeRef} de la facture) désigne-t-elle encore un créancier ? Faux : la facture est
     * annulée à sa présentation, aucune pièce ne bouge.
     */
    boolean exists(MinecraftServer server, String ref);

    /**
     * Le prélèvement est payé : verse les {@code amount} cuivres au créancier (aucune pièce créée ni détruite : elles
     * viennent d'être retirées au débiteur) et note le paiement là où la facture a été émise. La facture est déjà
     * marquée payée.
     */
    void receive(MinecraftServer server, Invoice invoice, long amount);

    /**
     * Le prélèvement est refusé (total insuffisant) pour la première fois : prévenir le créancier. Rien n'a bougé ; la
     * facture reste due. Les refus suivants de la même facture ne l'appellent pas (ils ne font que compter,
     * {@code InvoiceRegistry#refusals}).
     *
     * @param now l'heure de la clôture, horloge de l'économie
     */
    void refused(MinecraftServer server, Invoice invoice, long now);

    /** Le motif lisible de la facture, traduit chez le client (clé de langue et arguments). */
    Component describe(Invoice invoice);
}
