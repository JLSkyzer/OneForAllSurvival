package dev.oneforallsurvival.phone.call;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Le nettoyage d'un nom de contact. Règle pure. Les caractères invisibles sont bâtis par leur point de code
 * ({@link #cp}) : aucun n'est écrit dans ce fichier.
 */
class ContactRulesTest {

    private static final int NUL = 0x00;
    private static final int BELL = 0x07;
    private static final int DELETE = 0x7F;
    private static final int NEXT_LINE_C1 = 0x85;
    private static final int LAST_C1 = 0x9F;
    private static final int NO_BREAK_SPACE = 0xA0;
    private static final int SECTION_SIGN = 0xA7;
    private static final int ZERO_WIDTH_SPACE = 0x200B;
    private static final int LINE_SEPARATOR = 0x2028;
    private static final int PARAGRAPH_SEPARATOR = 0x2029;
    private static final int RIGHT_TO_LEFT_OVERRIDE = 0x202E;
    private static final int IDEOGRAPHIC_SPACE = 0x3000;
    private static final int BYTE_ORDER_MARK = 0xFEFF;
    private static final int LONE_SURROGATE = 0xD800;
    /** Un caractère hors du premier plan : deux unités UTF-16. */
    private static final int EMOJI = 0x1F600;

    private static String cp(int... codePoints) {
        StringBuilder text = new StringBuilder();
        for (int codePoint : codePoints) {
            text.appendCodePoint(codePoint);
        }
        return text.toString();
    }

    private static Optional<String> clean(String raw) {
        return ContactRules.cleanName(raw);
    }

    @Test
    void lesPlafonds() {
        assertEquals(100, ContactRules.MAX_CONTACTS);
        assertEquals(32, ContactRules.MAX_NAME);
        assertEquals(50, ContactRules.MAX_RECENTS);
    }

    @Test
    void unNomOrdinaireEstGardeTelQuel() {
        assertEquals(Optional.of("Jean-Marc d'Été (garage)"), clean("Jean-Marc d'Été (garage)"));
        assertEquals(Optional.of("A"), clean("A"));
    }

    @Test
    void lesEspacesSontRognesEtReduits() {
        assertEquals(Optional.of("Jean Dupont"), clean("   Jean    Dupont   "));
        // Espace insécable, séparateurs de ligne et de paragraphe, espace idéographique : des espaces.
        assertEquals(Optional.of("a b c d e"), clean(cp(NO_BREAK_SPACE) + "a" + cp(NO_BREAK_SPACE, ' ') + "b"
                + cp(LINE_SEPARATOR) + "c" + cp(PARAGRAPH_SEPARATOR) + "d" + cp(IDEOGRAPHIC_SPACE) + "e"
                + cp(IDEOGRAPHIC_SPACE)));
    }

    @Test
    void lesCaracteresDeControleSontRetires() {
        assertEquals(Optional.of("abcde"),
                clean("a" + cp(NUL) + "b" + cp(BELL) + "c" + cp(DELETE) + "d" + cp(NEXT_LINE_C1, LAST_C1) + "e"));
        // Retour à la ligne et tabulation sont des caractères de contrôle : retirés, pas changés en espace.
        assertEquals(Optional.of("ab c"), clean("a\n\tb \r c"));
        // Un contrôle entre deux espaces ne laisse qu'une espace.
        assertEquals(Optional.of("a b"), clean("a " + cp(BELL) + " b"));
    }

    @Test
    void lesCodesDeCouleurSontDesamorces() {
        assertEquals(Optional.of("cRouge lgras"),
                clean(cp(SECTION_SIGN) + "cRouge " + cp(SECTION_SIGN) + "lgras" + cp(SECTION_SIGN)));
    }

    @Test
    void lesCaracteresInvisiblesSontRetires() {
        // Sens d'écriture forcé, espace sans largeur, marque d'ordre des octets, demi-paire isolée.
        assertEquals(Optional.of("Maman"), clean(cp(RIGHT_TO_LEFT_OVERRIDE) + "Ma" + cp(ZERO_WIDTH_SPACE) + "m"
                + cp(BYTE_ORDER_MARK) + "an" + cp(LONE_SURROGATE)));
        // Une vraie paire est un caractère : gardée.
        assertEquals(Optional.of("Zoé " + cp(EMOJI)), clean("Zoé " + cp(EMOJI)));
    }

    @Test
    void videApresNettoyageEstRefuse() {
        assertEquals(Optional.empty(), clean(null));
        assertEquals(Optional.empty(), clean(""));
        assertEquals(Optional.empty(), clean("     "));
        assertEquals(Optional.empty(), clean(cp(SECTION_SIGN, SECTION_SIGN, SECTION_SIGN)));
        assertEquals(Optional.empty(), clean("\n\t" + cp(NUL, BELL)));
        assertEquals(Optional.empty(), clean(cp(NO_BREAK_SPACE, ZERO_WIDTH_SPACE, ' ', RIGHT_TO_LEFT_OVERRIDE)));
    }

    @Test
    void trenteDeuxCaracteresPassentTrenteTroisNon() {
        assertEquals(Optional.of("a".repeat(32)), clean("a".repeat(32)));
        assertEquals(Optional.empty(), clean("a".repeat(33)));
        // L'espace compte dans la longueur.
        assertEquals(Optional.of("a".repeat(30) + " b"), clean("a".repeat(30) + " b"));
        assertEquals(Optional.empty(), clean("a".repeat(31) + " b"));
    }

    @Test
    void laLongueurSeJugeApresNettoyage() {
        String raw = "   " + cp(SECTION_SIGN) + "a".repeat(16) + "      " + "b".repeat(15) + cp(BELL) + "      ";
        assertEquals(Optional.of("a".repeat(16) + " " + "b".repeat(15)), clean(raw));
        assertEquals(Optional.of("x"), clean(" ".repeat(10_000) + "x" + cp(SECTION_SIGN).repeat(10_000)));
        assertEquals(Optional.empty(), clean("a".repeat(100_000)));
    }

    @Test
    void laLongueurSeCompteEnUnitesUtf16CommeDansLesPaquets() {
        assertEquals(Optional.of(cp(EMOJI).repeat(16)), clean(cp(EMOJI).repeat(16)));
        assertEquals(Optional.empty(), clean(cp(EMOJI).repeat(17)));
        assertEquals(Optional.empty(), clean("a".repeat(31) + cp(EMOJI)));
    }

    @Test
    void unNomPropreNeChangePlus() {
        String once = clean("  Jean " + cp(SECTION_SIGN) + "  " + cp(BELL) + " Dupont ").orElseThrow();
        assertEquals(Optional.of(once), clean(once));
    }
}
