package dev.oneforallsurvival.phone.call;

import java.util.Optional;

/** Les règles du carnet d'une carte SIM ({@link PhoneDirectory}) : plafonds et nettoyage d'un nom. Règle pure. */
public final class ContactRules {

    /** Contacts gardés par carte. */
    public static final int MAX_CONTACTS = 100;

    /** Longueur d'un nom de contact, en unités UTF-16 ({@link String#length()}, ce que bornent les paquets). */
    public static final int MAX_NAME = 32;

    /** Appels gardés par carte ; au-delà, le plus ancien tombe. */
    public static final int MAX_RECENTS = 50;

    private static final int SECTION_SIGN = 0xA7;

    private ContactRules() {}

    /**
     * Nettoie un nom saisi par un joueur. Sont retirés : le signe des codes de couleur (U+00A7), les caractères de
     * contrôle (retour à la ligne et tabulation compris) et les caractères invisibles (formats Unicode, dont le sens
     * d'écriture forcé et l'espace sans largeur ; demi-paires isolées). Les espaces, insécables et séparateurs de ligne
     * compris, sont rognés aux bouts et réduits à une seule.
     *
     * @return le nom propre, ou vide s'il ne reste rien, s'il dépasse {@value #MAX_NAME} après nettoyage (jamais coupé)
     *         ou si {@code raw} est nul
     */
    public static Optional<String> cleanName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        StringBuilder name = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length(); ) {
            int c = raw.codePointAt(i);
            i += Character.charCount(c);
            if (removed(c)) {
                continue;
            }
            if (Character.isSpaceChar(c)) {
                // Une espace ne s'écrit que devant le caractère qui la suit, et jamais en tête.
                space = name.length() > 0;
                continue;
            }
            if (space) {
                name.append(' ');
                space = false;
            }
            name.appendCodePoint(c);
            if (name.length() > MAX_NAME) {
                return Optional.empty();
            }
        }
        return name.length() == 0 ? Optional.empty() : Optional.of(name.toString());
    }

    private static boolean removed(int c) {
        int type = Character.getType(c);
        return c == SECTION_SIGN || type == Character.CONTROL || type == Character.FORMAT
                || type == Character.SURROGATE;
    }
}
