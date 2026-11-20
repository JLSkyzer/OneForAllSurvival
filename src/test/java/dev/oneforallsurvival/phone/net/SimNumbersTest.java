package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class SimNumbersTest {

    /** Les chiffres 1 à 8 en pleine chasse (U+FF11 à U+FF18), construits sans séquence d'échappement. */
    private static String pleineChasse() {
        StringBuilder sb = new StringBuilder("06");
        for (int i = 1; i <= 8; i++) {
            sb.appendCodePoint(0xFF10 + i);
        }
        return sb.toString();
    }

    @Test
    void milleTiragesDeLaFormeZeroSixEtHuitChiffresTousDistincts() {
        RandomSource random = RandomSource.create(20261120L);
        Set<String> pris = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String n = SimNumbers.draw(random, pris::contains);
            assertTrue(n.matches("06[0-9]{8}"), n);
            assertTrue(pris.add(n), "numéro déjà pris : " + n);
        }
        assertEquals(1000, pris.size());
    }

    @Test
    void leNumeroResteEnAsciiQuelleQueSoitLaLangueParDefaut() {
        Locale origine = Locale.getDefault();
        try {
            // Sur un JDK 17, ar-SA et fa-IR écrivent les chiffres de %d en chiffres arabes ou persans.
            for (String tag : new String[] {"ar-SA", "fa-IR"}) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                RandomSource random = RandomSource.create(20261121L);
                for (int i = 0; i < 50; i++) {
                    String n = SimNumbers.draw(random, x -> false);
                    assertTrue(n.matches("06[0-9]{8}"), tag + " : " + n);
                    assertEquals(Optional.of(n), SimNumbers.parse(n), tag + " : " + n);
                }
            }
        } finally {
            Locale.setDefault(origine);
        }
    }

    @Test
    void unNumeroDejaPrisEstRetire() {
        // Les trois premiers numéros de la suite sont retenus : le tirage doit sortir un autre numéro.
        RandomSource temoin = RandomSource.create(7L);
        Set<String> trois = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            trois.add(SimNumbers.draw(temoin, x -> false));
        }
        RandomSource random = RandomSource.create(7L);
        String n = SimNumbers.draw(random, trois::contains);
        assertFalse(trois.contains(n));
        assertTrue(n.matches("06[0-9]{8}"));
    }

    @Test
    void unTakenToujoursVraiLeve() {
        RandomSource random = RandomSource.create(1L);
        assertThrows(IllegalStateException.class, () -> SimNumbers.draw(random, n -> true));
    }

    @Test
    void ilEssaieJusqua1000FoisPuisAbandonne() {
        RandomSource random = RandomSource.create(1L);
        int[] appels = {0};
        assertThrows(IllegalStateException.class, () -> SimNumbers.draw(random, n -> {
            appels[0]++;
            return true;
        }));
        assertEquals(1000, appels[0]);
    }

    @Test
    void formatEnCinqPaires() {
        assertEquals("06 12 34 56 78", SimNumbers.format("0612345678"));
    }

    @Test
    void formatEtParseSontReciproques() {
        assertEquals(Optional.of("0612345678"), SimNumbers.parse(SimNumbers.format("0612345678")));
    }

    @Test
    void formatRendTelQuelCeQuiNEstPasUnNumero() {
        assertEquals("abc", SimNumbers.format("abc"));
        assertEquals("", SimNumbers.format(""));
        assertEquals("0712345678", SimNumbers.format("0712345678"));
        assertNull(SimNumbers.format(null));
    }

    @Test
    void parseAvecOuSansEspaces() {
        assertEquals(Optional.of("0612345678"), SimNumbers.parse("06 12 34 56 78"));
        assertEquals(Optional.of("0612345678"), SimNumbers.parse("0612345678"));
    }

    @Test
    void parseRefuseCeQuiNEstPasUnNumeroDeCarte() {
        assertEquals(Optional.empty(), SimNumbers.parse("0712345678"));
        assertEquals(Optional.empty(), SimNumbers.parse("06123"));
        assertEquals(Optional.empty(), SimNumbers.parse("06 12 34 56 7a"));
        assertEquals(Optional.empty(), SimNumbers.parse("061234567890"));
        assertEquals(Optional.empty(), SimNumbers.parse(""));
        assertEquals(Optional.empty(), SimNumbers.parse("   "));
        assertEquals(Optional.empty(), SimNumbers.parse(null));
    }

    @Test
    void parseRefuseLesChiffresPleineChasse() {
        String pleine = pleineChasse();
        assertEquals(10, pleine.length());
        assertEquals(Optional.empty(), SimNumbers.parse(pleine));
    }

    @Test
    void parseRefuseLesTabulationsEtLesFinsDeLigne() {
        assertEquals(Optional.empty(), SimNumbers.parse("06\t12345678"));
        assertEquals(Optional.empty(), SimNumbers.parse("0612345678\n"));
    }
}
