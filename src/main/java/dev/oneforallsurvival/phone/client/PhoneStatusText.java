package dev.oneforallsurvival.phone.client;

import dev.oneforallsurvival.phone.net.PhoneSims.Validity;

/**
 * Ce que le téléphone écrit de la carte et du réseau, sans Minecraft (testable) : la même règle pour l'écran de repli
 * et pour la page, dont la table {@code STATUS_TEXTS} de {@code html/phone/phone.js} porte les mêmes noms et, en
 * français, les mêmes textes que {@code fr_fr.json} (garde : {@code PhoneStatusTextTest}).
 *
 * <p>L'état vient toujours de la vue ({@code sim}), jamais d'un champ vide : le serveur ne remplit numéro, titulaire
 * et opérateur que pour une carte qui a une ligne (active ou suspendue), une carte désactivée n'a donc pas de numéro
 * sans être « non reconnue » pour autant.
 */
public final class PhoneStatusText {

    /** Un texte d'état ; {@link #OPERATOR} : le nom de l'opérateur lui-même, {@link #NUMBER} : le numéro. */
    public enum Shown {
        NO_SIM("screen.oneforallsurvival.phone.no_sim"),
        UNRECOGNIZED("screen.oneforallsurvival.phone.sim_unrecognized"),
        SUSPENDED("screen.oneforallsurvival.phone.sim_suspended"),
        INACTIVE("message.oneforallsurvival.relay.caller_sim_inactive"),
        NO_SERVICE("screen.oneforallsurvival.phone.no_service"),
        AIRPLANE("message.oneforallsurvival.relay.caller_airplane"),
        NO_CARD("tooltip.oneforallsurvival.phone.no_sim"),
        OPERATOR(null),
        NUMBER(null);

        private final String key;

        Shown(String key) {
            this.key = key;
        }

        /** La clé de langue du texte, ou {@code null} pour un texte venu de la vue (opérateur, numéro). */
        public String key() {
            return key;
        }
    }

    private PhoneStatusText() {}

    /**
     * La barre d'état. Verrouillé, la vue ne dit que « aucune », « non reconnue » ou « présente » ({@code ACTIVE}) et
     * le mode avion n'y est pas : 0 barre s'y lit « Pas de service », quelle qu'en soit la raison.
     */
    public static Shown carrier(Validity sim, boolean unlocked, boolean airplane, int bars, String operator) {
        if (unlocked && airplane) {
            return Shown.AIRPLANE;
        }
        return switch (sim) {
            case NONE -> Shown.NO_SIM;
            case UNKNOWN -> Shown.UNRECOGNIZED;
            case SUSPENDED -> unlocked ? Shown.SUSPENDED : service(bars, operator);
            case DEACTIVATED -> unlocked ? Shown.INACTIVE : service(bars, operator);
            case ACTIVE -> service(bars, operator);
        };
    }

    private static Shown service(int bars, String operator) {
        return bars > 0 && operator != null && !operator.isEmpty() ? Shown.OPERATOR : Shown.NO_SERVICE;
    }

    /** La carte logée en une ligne (Réglages, tête de la feuille « Carte SIM ») : le numéro d'une ligne, sinon l'état. */
    public static Shown card(Validity sim) {
        return switch (sim) {
            case NONE -> Shown.NO_CARD;
            case UNKNOWN -> Shown.UNRECOGNIZED;
            case DEACTIVATED -> Shown.INACTIVE;
            case ACTIVE, SUSPENDED -> Shown.NUMBER;
        };
    }

    /** Les barres se dessinent quand une ligne peut en avoir : carte présente (active), hors mode avion. */
    public static boolean barsShown(Validity sim, boolean unlocked, boolean airplane) {
        return !(unlocked && airplane) && sim == Validity.ACTIVE;
    }

    /**
     * « Demander une carte » n'est proposée ni avec une carte active ni en mode avion (le serveur ne propose alors
     * aucun opérateur) : grisée, dans la page comme dans le repli.
     */
    public static boolean requestOffered(Validity sim, boolean airplane) {
        return sim != Validity.ACTIVE && !airplane;
    }
}
