package dev.oneforallsurvival.phone;

/**
 * Face ID (retours d'essai du 27/11), règle pure : ce que l'ouverture d'un téléphone verrouillé fait du visage de celui
 * qui l'ouvre. Le serveur seul la joue, à l'ouverture seulement ({@link PhoneService#open}) : rallumer l'écran après
 * « Verrouiller » n'est pas une ouverture.
 *
 * <ul>
 *   <li>{@link Verdict#UNLOCK} : Face ID actif, un code posé, aucun blocage en cours, ouvert par le propriétaire,
 *       visage découvert (emplacement de tête vide) ;
 *   <li>{@link Verdict#NOT_RECOGNIZED} : les mêmes conditions, visage couvert : le pavé s'affiche, avec « Visage non
 *       reconnu » ;
 *   <li>{@link Verdict#NONE} sinon. Quelqu'un d'autre que le propriétaire n'en profite jamais, et n'apprend même pas que
 *       Face ID est actif (l'écran verrouillé ne dit rien des réglages) ; pendant un blocage, rien non plus.
 * </ul>
 */
public final class FaceId {

    public enum Verdict { NONE, UNLOCK, NOT_RECOGNIZED }

    private FaceId() {}

    /**
     * @param enabled Face ID est actif sur ce téléphone
     * @param hasCode le téléphone a un code (sinon il est neuf : l'assistant, pas l'écran verrouillé)
     * @param owner celui qui ouvre est le propriétaire
     * @param faceUncovered son emplacement de tête est vide
     * @param lockedOut une attente après trop de codes faux est en cours
     */
    public static Verdict atOpen(boolean enabled, boolean hasCode, boolean owner, boolean faceUncovered,
            boolean lockedOut) {
        if (!enabled || !hasCode || !owner || lockedOut) {
            return Verdict.NONE;
        }
        return faceUncovered ? Verdict.UNLOCK : Verdict.NOT_RECOGNIZED;
    }
}
