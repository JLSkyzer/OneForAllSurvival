package dev.oneforallsurvival.economy.stall;

/**
 * Ce que répond l'étal à un achat, un rachat ou une écriture d'offre. Seul {@link #DONE} a fait bouger quelque
 * chose ; tout autre résultat est un refus décidé avant le premier changement.
 *
 * <p>Les trois derniers ({@link #CASH_FULL}, {@link #NOT_ALLOWED}, {@link #BAD_REQUEST}) s'ajoutent à la liste du
 * plan : une caisse au plafond, un joueur qui ne gère pas l'étal, une demande hors des bornes.
 */
public enum StallResult {
    DONE,
    /** Aucune offre (ou aucun rachat) à cette case. */
    NO_OFFER,
    /** L'offre affichée n'est plus celle de l'étal (prix, lot ou version). */
    CHANGED,
    /** Pas un lot entier en stock ; pour un rachat, plus aucun lot à racheter. */
    OUT_OF_STOCK,
    /** L'acheteur ne porte pas la somme. */
    CANNOT_PAY,
    /** Ce qui arrive ne tient pas dans l'inventaire de celui qui le reçoit. */
    NO_ROOM,
    /** La caisse ne peut pas payer le rachat. */
    CASH_SHORT,
    /** Le stock n'a pas la place du lot racheté. */
    STOCK_FULL,
    /** Le vendeur n'a pas le lot, objets identiques au modèle. */
    NOTHING_TO_SELL,
    /** Le propriétaire n'achète pas chez lui et ne s'y revend pas. */
    OWN_STALL,
    TOO_FAR,
    /** L'écran voulu (vitrine, ou gestion) n'est pas ouvert sur cet étal. */
    NOT_OPEN,
    /** Le joueur est parti, ou l'étal n'est plus là. */
    GONE,
    /** Le modèle d'une offre : vide, ou de l'argent. */
    BAD_TEMPLATE,
    /** La caisse dépasserait son plafond par coupure. */
    CASH_FULL,
    /** Le joueur ne gère pas cet étal. */
    NOT_ALLOWED,
    /** Une action inconnue ou une valeur hors des bornes. */
    BAD_REQUEST
}
