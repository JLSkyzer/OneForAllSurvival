package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EconomyCalendarTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalDate J = LocalDate.of(2026, 11, 14);

    private static long instant(String local, ZoneId zone) {
        return ZonedDateTime.of(LocalDateTime.parse(local), zone).toInstant().toEpochMilli();
    }

    @Test
    void minuitParDefaut() {
        assertEquals(J, EconomyCalendar.payDay(instant("2026-11-14T23:59:59", PARIS), PARIS, 0));
        assertEquals(J.plusDays(1), EconomyCalendar.payDay(instant("2026-11-15T00:00:00", PARIS), PARIS, 0));
        assertEquals(instant("2026-11-15T00:00:00", PARIS), EconomyCalendar.boundaryAfter(J, PARIS, 0));
    }

    @Test
    void heureDePaieA6h() {
        assertEquals(J.minusDays(1), EconomyCalendar.payDay(instant("2026-11-14T05:59:59", PARIS), PARIS, 6));
        assertEquals(J, EconomyCalendar.payDay(instant("2026-11-14T06:00:00", PARIS), PARIS, 6));
        assertEquals(instant("2026-11-15T06:00:00", PARIS), EconomyCalendar.boundaryAfter(J, PARIS, 6));
    }

    @Test
    void fuseauExplicite() {
        // 2026-11-15 03:00 à Paris = 2026-11-14 21:00 à New York.
        long t = instant("2026-11-15T03:00:00", PARIS);
        assertEquals(J.plusDays(1), EconomyCalendar.payDay(t, PARIS, 0));
        assertEquals(J, EconomyCalendar.payDay(t, NEW_YORK, 0));
        assertNotEquals(EconomyCalendar.boundaryAfter(J, PARIS, 0), EconomyCalendar.boundaryAfter(J, NEW_YORK, 0));
    }

    /** Le jour de paie de 23 h du passage à l'heure d'été : une seule frontière à chaque bout, à minuit local. */
    @Test
    void passageALHeureDEte() {
        LocalDate jour = LocalDate.of(2026, 3, 29);
        long debut = EconomyCalendar.boundaryAfter(jour.minusDays(1), PARIS, 0);
        long fin = EconomyCalendar.boundaryAfter(jour, PARIS, 0);
        assertEquals(instant("2026-03-29T00:00:00", PARIS), debut);
        assertEquals(instant("2026-03-30T00:00:00", PARIS), fin);
        assertEquals(Duration.ofHours(23).toMillis(), fin - debut);
        assertEquals(jour.minusDays(1), EconomyCalendar.payDay(debut - 1L, PARIS, 0));
        assertEquals(jour, EconomyCalendar.payDay(debut, PARIS, 0));
        assertEquals(jour, EconomyCalendar.payDay(fin - 1L, PARIS, 0));
        assertEquals(jour.plusDays(1), EconomyCalendar.payDay(fin, PARIS, 0));
        // Toutes les minutes du jour tombent sur ce seul jour (02 h 00 à 02 h 59 n'existent pas).
        for (long t = debut; t < fin; t += 60_000L) {
            assertEquals(jour, EconomyCalendar.payDay(t, PARIS, 0));
        }
        // Heure de paie dans le trou (2 h) : la frontière tombe au premier instant du jour suivant, 3 h.
        assertEquals(instant("2026-03-29T03:00:00", PARIS), EconomyCalendar.boundaryAfter(jour.minusDays(1), PARIS, 2));
        assertEquals(jour, EconomyCalendar.payDay(EconomyCalendar.boundaryAfter(jour.minusDays(1), PARIS, 2), PARIS, 2));
        assertEquals(jour.minusDays(1),
                EconomyCalendar.payDay(EconomyCalendar.boundaryAfter(jour.minusDays(1), PARIS, 2) - 1L, PARIS, 2));
    }

    /** Le jour de 25 h du retour à l'heure d'hiver : un seul jour de paie, 02 h 00 à 02 h 59 vues deux fois. */
    @Test
    void retourALHeureDHiver() {
        LocalDate jour = LocalDate.of(2026, 10, 25);
        long debut = EconomyCalendar.boundaryAfter(jour.minusDays(1), PARIS, 0);
        long fin = EconomyCalendar.boundaryAfter(jour, PARIS, 0);
        assertEquals(instant("2026-10-25T00:00:00", PARIS), debut);
        assertEquals(instant("2026-10-26T00:00:00", PARIS), fin);
        assertEquals(Duration.ofHours(25).toMillis(), fin - debut);
        for (long t = debut; t < fin; t += 60_000L) {
            assertEquals(jour, EconomyCalendar.payDay(t, PARIS, 0));
        }
        assertEquals(jour.plusDays(1), EconomyCalendar.payDay(fin, PARIS, 0));
        // Heure de paie dans l'heure vue deux fois (2 h) : la première fois fait la frontière, le jour ne recule pas.
        long frontiere = EconomyCalendar.boundaryAfter(jour.minusDays(1), PARIS, 2);
        assertEquals(jour.minusDays(1), EconomyCalendar.payDay(frontiere - 1L, PARIS, 2));
        for (long t = frontiere; t < frontiere + Duration.ofHours(3).toMillis(); t += 60_000L) {
            assertEquals(jour, EconomyCalendar.payDay(t, PARIS, 2));
        }
    }

    @Test
    void troisJoursManques() {
        assertEquals(List.of(J.plusDays(1), J.plusDays(2)), EconomyCalendar.daysToClose(J, J.plusDays(3), 400));
        assertEquals(List.of(), EconomyCalendar.daysToClose(J, J.plusDays(1), 400));
        assertEquals(List.of(), EconomyCalendar.daysToClose(null, J, 400));
    }

    @Test
    void horlogeQuiRecule() {
        assertEquals(List.of(), EconomyCalendar.daysToClose(J.plusDays(2), J, 400));
        assertEquals(List.of(), EconomyCalendar.daysToClose(J, J, 400));
    }

    @Test
    void plafondDesJoursManques() {
        List<LocalDate> jours = EconomyCalendar.daysToClose(J, J.plusDays(501), 400);
        assertEquals(400, jours.size());
        assertEquals(J.plusDays(101), jours.get(0));
        assertEquals(J.plusDays(500), jours.get(399));
        for (int i = 1; i < jours.size(); i++) {
            assertEquals(jours.get(i - 1).plusDays(1), jours.get(i), "jours consécutifs et croissants");
        }
    }

    @Test
    void fuseauInvalideDonneLeSysteme() {
        assertEquals(ZoneId.systemDefault(), EconomyCalendar.zone("Pas/UnFuseau"));
        assertEquals(ZoneId.systemDefault(), EconomyCalendar.zone("Pas/UnFuseau"));
        assertEquals(ZoneId.systemDefault(), EconomyCalendar.zone(""));
        assertEquals(ZoneId.systemDefault(), EconomyCalendar.zone("   "));
        assertEquals(PARIS, EconomyCalendar.zone("Europe/Paris"));
        assertTrue(EconomyCalendar.zone(" America/New_York ").equals(NEW_YORK));
    }
}
