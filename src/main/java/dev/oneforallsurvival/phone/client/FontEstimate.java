package dev.oneforallsurvival.phone.client;

/**
 * Une borne haute de la largeur d'un texte dans la police de Minecraft (avance de chaque caractère, en px d'interface),
 * sans Minecraft : elle sert aux essais JUnit de la mise en page de l'écran de repli ({@code PhoneFallbackLayoutTest}),
 * qui vérifient que chaque texte fixe tient. Avances de la police {@code ascii} de Minecraft 1.20.1 telles qu'on les
 * connaît (largeur du glyphe + 1) : 2 pour {@code i ! . , : ; ' |}, 3 pour {@code l `}, 4 pour l'espace et
 * {@code I t [ ] ( ) " *}, 5 pour {@code f k < > { }}, 7 pour {@code @ ~}, 6 pour tout autre caractère latin (accents
 * compris), 9 pour un symbole hors de l'alphabet latin (« ▸ », « ✈ », « … »).
 *
 * <p>C'est une estimation, pas une mesure : l'essai réel {@code TelephoneShots} (mode {@code reseau}) relève dans le jeu
 * la largeur réelle ({@code Font.width}) de chaque texte fixe et échoue si elle dépasse celle-ci.
 */
public final class FontEstimate {

    private FontEstimate() {}

    public static int width(String text) {
        if (text == null) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            w += advance(c);
            i += Character.charCount(c);
        }
        return w;
    }

    static int advance(int c) {
        switch (c) {
            case 'i', '!', '.', ',', ':', ';', '\'', '|' -> {
                return 2;
            }
            case 'l', '`' -> {
                return 3;
            }
            case ' ', 'I', 't', '[', ']', '(', ')', '"', '*' -> {
                return 4;
            }
            case 'f', 'k', '<', '>', '{', '}' -> {
                return 5;
            }
            case '@', '~' -> {
                return 7;
            }
            default -> {
                // Latin, chiffres et lettres accentuées : 6 ; tout autre symbole : 9 (borne large).
                return c < 0x250 ? 6 : 9;
            }
        }
    }
}
