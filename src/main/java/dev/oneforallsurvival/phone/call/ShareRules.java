package dev.oneforallsurvival.phone.call;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Qui l'envoyeur d'un numéro (« AirDrop », retours d'essai du 27/11) voit dans sa liste, règle pure : un autre joueur à
 * {@value #RANGE} blocs au plus, en vue (aucun mur entre les yeux), ni spectateur ni invisible, qui porte un téléphone
 * à lui ({@link #own}) avec une carte SIM. {@link ShareService} la joue à la liste et la rejoue à l'envoi ;
 * l'acceptation, elle, n'exige plus la proximité.
 */
public final class ShareRules {

    /** La portée, en blocs (distance entre les positions des deux joueurs). */
    public static final double RANGE = 8.0;
    /** Lignes de la liste au plus (les plus proches d'abord). */
    public static final int MAX_ROWS = 16;

    private ShareRules() {}

    /**
     * « Son téléphone » : un téléphone porté ne compte pour la liste et pour recevoir une fiche que s'il est enregistré au
     * nom de ce joueur, ou sans propriétaire. Celui d'un autre ne reçoit rien et ne dit rien de son carnet.
     *
     * @param owner le propriétaire enregistré du téléphone, nul s'il n'en a pas
     * @param player le joueur qui le porte
     */
    public static boolean own(@Nullable UUID owner, UUID player) {
        return owner == null || owner.equals(player);
    }

    /**
     * @param distanceSqr le carré de la distance entre les deux joueurs
     * @param inSight aucun bloc entre leurs yeux
     * @param spectator le destinataire est en mode spectateur
     * @param invisible le destinataire est invisible (effet ou commande)
     * @param hasCard le destinataire porte un téléphone à lui ({@link #own}) dont la carte peut recevoir une fiche
     */
    public static boolean listed(double distanceSqr, boolean inSight, boolean spectator, boolean invisible,
            boolean hasCard) {
        return distanceSqr <= RANGE * RANGE && inSight && !spectator && !invisible && hasCard;
    }
}
