package dev.oneforallsurvival.phone.net;

/**
 * Le nombre de cartes actives d'un opérateur, gardé par une antenne : {@link PhoneOperators#activeCards} parcourt toutes
 * les cartes du serveur, et chaque antenne le demande à chaque tic. Le compte n'est refait que si le registre a changé
 * l'état d'une carte depuis ({@link PhoneOperators#cardRevision()}), si l'opérateur demandé n'est plus le même, ou si le
 * registre n'est plus le même objet (monde rechargé) : il n'est donc jamais en retard d'un tic.
 *
 * <p>Fil du serveur seulement, comme le registre.
 */
public final class ActiveCardCache {

    private PhoneOperators registry;
    private String operatorId;
    private int revision;
    private int count;
    private int recounts;

    /** Les cartes actives de {@code operatorId}, recomptées seulement si quelque chose a pu les changer. */
    public int count(PhoneOperators registry, String operatorId) {
        if (registry != this.registry || !operatorId.equals(this.operatorId) || registry.cardRevision() != revision) {
            this.registry = registry;
            this.operatorId = operatorId;
            this.revision = registry.cardRevision();
            this.count = registry.activeCards(operatorId);
            recounts++;
        }
        return count;
    }

    /** Combien de fois le compte a été refait (essais : le cache sert-il ?). */
    public int recounts() {
        return recounts;
    }
}
