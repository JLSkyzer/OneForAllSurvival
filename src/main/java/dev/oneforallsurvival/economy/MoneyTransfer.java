package dev.oneforallsurvival.economy;

import java.util.Locale;

/**
 * Les mouvements d'argent entre deux personnes (ou vers le trésor) : ni création ni destruction, donc ni
 * {@link MoneySource} ni {@link MoneySink}. Chaque genre a son total dans le registre.
 */
public enum MoneyTransfer {
    /** Pièces échangées entre deux joueurs dans la fenêtre d'échange. */
    TRADE,
    /** Achat à un étal : le prix, moins la taxe, va à la caisse de l'étal. */
    STALL_SALE,
    /** Taxe sur les ventes, versée au compte du trésor. */
    SALES_TAX,
    /** Rachat par un étal : la caisse paie le joueur qui vend. */
    STALL_BUYBACK,
    /** Pièces retirées d'une caisse ou d'un compte par l'équipe. */
    STAFF_WITHDRAW,
    /** Facture payée. */
    INVOICE;

    /** Genre de la ligne du registre pour un transfert : {@code transfer.trade}. */
    public String kind() {
        return "transfer." + name().toLowerCase(Locale.ROOT);
    }

    /** Clé de langue du nom du genre. */
    public String langKey() {
        return "economy.oneforallsurvival.kind." + kind();
    }
}
