package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.oneforallsurvival.economy.payroll.SalaryMath;
import org.junit.jupiter.api.Test;

class SalaryMathTest {

    private static final long HEURE = 3_600L;

    @Test
    void soixanteCuivresParHeurePendant90Minutes() {
        assertEquals(90L, SalaryMath.amount(60L * 90L * 60L));
    }

    @Test
    void arrondiVersLeBas() {
        assertEquals(59L, SalaryMath.amount(60L * (HEURE - 1L)));
        assertEquals(0L, SalaryMath.amount(3_599L));
        assertEquals(0L, SalaryMath.amount(0L));
        assertEquals(0L, SalaryMath.amount(-5L));
    }

    @Test
    void decoupageALaFrontiere() {
        long minuit = 1_000_000_000_000L;
        long de = minuit - 30_000L;
        long a = minuit + 20_000L;
        assertArrayEquals(new long[] {30L, 20L, 0L}, SalaryMath.split(de, a, minuit));
        // Le reste en millisecondes est reporté, jamais perdu ni compté deux fois.
        assertArrayEquals(new long[] {30L, 20L, 700L}, SalaryMath.split(de, a + 700L, minuit));
        // Frontière hors de l'intervalle : tout d'un côté.
        assertArrayEquals(new long[] {50L, 0L, 0L}, SalaryMath.split(de, a, a + 1L));
        assertArrayEquals(new long[] {0L, 50L, 0L}, SalaryMath.split(de, a, de));
        // Rien, ou un temps qui recule : rien.
        assertArrayEquals(new long[] {0L, 0L, 0L}, SalaryMath.split(a, a, minuit));
        assertArrayEquals(new long[] {0L, 0L, 0L}, SalaryMath.split(a, de, minuit));
        assertArrayEquals(new long[] {0L, 0L, 999L}, SalaryMath.split(de, de + 999L, minuit));
    }

    @Test
    void plafondJournalier() {
        long huitHeures = 8L * HEURE;
        long septHeures59 = 7L * HEURE + 59L * 60L;
        assertEquals(60L, SalaryMath.creditable(septHeures59, 5L * 60L, huitHeures));
        assertEquals(0L, SalaryMath.creditable(huitHeures, 10L, huitHeures));
        assertEquals(0L, SalaryMath.creditable(huitHeures + 5L, 10L, huitHeures));
        assertEquals(10L, SalaryMath.creditable(0L, 10L, huitHeures));
        assertEquals(0L, SalaryMath.creditable(0L, -3L, huitHeures));
    }
}
