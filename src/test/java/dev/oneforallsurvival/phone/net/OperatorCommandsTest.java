package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Les parties pures de {@code /operator} (lot T2, tâche 11) : la complétion des noms d'opérateur et le motif gardé d'une
 * suppression. Les gestes eux-mêmes se jouent dans {@code MaireTelephoneGameTests}.
 */
class OperatorCommandsTest {

    @Test
    void laCompletionEstSansCasseEtMetLesGuillemets() {
        List<String> names = List.of("Mairie", "Réseau du Port", "mairie_bis", "Lagon");
        assertEquals(List.of("Mairie", "mairie_bis"), OperatorCommands.suggestions(names, "MAI", 10));
        // Un nom à espaces ou à accents ne se lit qu'entre guillemets : la complétion les met.
        assertEquals(List.of("\"Réseau du Port\""), OperatorCommands.suggestions(names, "rés", 10));
        // Le guillemet ouvrant déjà tapé ne gêne pas.
        assertEquals(List.of("\"Réseau du Port\""), OperatorCommands.suggestions(names, "\"Rés", 10));
        assertEquals(4, OperatorCommands.suggestions(names, "", 10).size());
        assertEquals(4, OperatorCommands.suggestions(names, null, 10).size());
    }

    @Test
    void laCompletionEstBornee() {
        List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            names.add("Op" + i);
        }
        assertEquals(OperatorCommands.MAX_SUGGESTIONS,
                OperatorCommands.suggestions(names, "op", OperatorCommands.MAX_SUGGESTIONS).size());
        assertEquals(List.of("Op0", "Op1"), OperatorCommands.suggestions(names, "Op", 2), "dans l'ordre reçu");
        assertTrue(OperatorCommands.suggestions(names, "op", 0).isEmpty());
        assertTrue(OperatorCommands.suggestions(names, "op", -3).isEmpty());
    }

    @Test
    void leMotifEstNettoyeEtBorne() {
        assertEquals("fraude", OperatorCommands.reason("  fraude  "));
        assertEquals("", OperatorCommands.reason(null));
        String hostile = "ab" + (char) 7 + "c" + (char) 0xA7 + "4rouge" + (char) 0x202E + "d";
        assertEquals("abc4rouged", OperatorCommands.reason(hostile), "contrôles, mise en forme et sens d'écriture ôtés");
        String longue = "x".repeat(OperatorCommands.MAX_REASON + 40);
        assertEquals(OperatorCommands.MAX_REASON, OperatorCommands.reason(longue).length());
        // Un caractère hors du plan de base compte pour un : jamais coupé en deux.
        String emoji = new String(Character.toChars(0x1F4DE));
        String motif = OperatorCommands.reason(emoji.repeat(OperatorCommands.MAX_REASON + 5));
        assertEquals(OperatorCommands.MAX_REASON, motif.codePointCount(0, motif.length()));
        assertTrue(Character.isSurrogate(motif.charAt(motif.length() - 1)) && Character.isLowSurrogate(
                motif.charAt(motif.length() - 1)), "la dernière paire est entière");
    }

    @Test
    void unMotifVideApresNettoyageEstRefuse() {
        char section = (char) 0xA7;
        String[] vides = {null, "", "   ", "" + section + section, " " + (char) 7 + (char) 0 + " ",
            "" + (char) 0x202E + (char) 0x200B + section + "\t", "" + (char) 0x3000};
        for (String v : vides) {
            assertFalse(OperatorCommands.validReason(v), "refusé : " + (v == null ? "null" : v.codePoints().boxed()
                    .map(Integer::toHexString).toList()));
        }
        assertTrue(OperatorCommands.validReason("fraude"));
        assertTrue(OperatorCommands.validReason(" " + section + "4x"), "« 4x » reste : recevable");
    }
}
