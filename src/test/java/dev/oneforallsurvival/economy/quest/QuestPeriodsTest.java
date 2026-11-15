package dev.oneforallsurvival.economy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class QuestPeriodsTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), PARIS).toInstant().toEpochMilli();
    }

    @Test
    void avantSixHeuresCEstLaVeille() {
        // Mercredi 18 novembre 2026.
        assertEquals(LocalDate.of(2026, 11, 17), QuestPeriods.dayKey(at(2026, 11, 18, 5, 59), PARIS, 6));
        assertEquals(LocalDate.of(2026, 11, 18), QuestPeriods.dayKey(at(2026, 11, 18, 6, 0), PARIS, 6));
        assertEquals(LocalDate.of(2026, 11, 18), QuestPeriods.dayKey(at(2026, 11, 18, 23, 59), PARIS, 6));
        assertEquals(LocalDate.of(2026, 11, 18), QuestPeriods.dayKey(at(2026, 11, 19, 1, 0), PARIS, 6));
    }

    @Test
    void laSemaineCommenceLeLundi() {
        LocalDate lundiDAvant = LocalDate.of(2026, 11, 16);
        LocalDate lundi = LocalDate.of(2026, 11, 23);
        // Dimanche 22 à 23 h : semaine du lundi 16.
        assertEquals(lundiDAvant, QuestPeriods.weekKey(at(2026, 11, 22, 23, 0), PARIS, 6));
        // Lundi 23 à 5 h 59 : encore la semaine du 16 (le jour de quête est le dimanche).
        assertEquals(lundiDAvant, QuestPeriods.weekKey(at(2026, 11, 23, 5, 59), PARIS, 6));
        // Lundi 23 à 6 h : la nouvelle semaine.
        assertEquals(lundi, QuestPeriods.weekKey(at(2026, 11, 23, 6, 0), PARIS, 6));
        assertEquals(lundi, QuestPeriods.weekKey(at(2026, 11, 25, 12, 0), PARIS, 6));
    }

    @Test
    void heureDEteSansJourPerdu() {
        // Fin mars (29 mars 2026 : 2 h → 3 h) et fin octobre (25 octobre 2026 : 3 h → 2 h), heure de renouvellement
        // ordinaire (6) et heure dans l'heure sautée ou doublée (2).
        for (int hour : new int[] {6, 2}) {
            for (LocalDate start : new LocalDate[] {LocalDate.of(2026, 3, 27), LocalDate.of(2026, 10, 23)}) {
                long now = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                LocalDate previous = QuestPeriods.dayKey(now, PARIS, hour);
                long end = now + 5L * 24 * 3_600_000L;
                for (long t = now; t < end; t += 60_000L) {
                    LocalDate key = QuestPeriods.dayKey(t, PARIS, hour);
                    assertTrue(key.equals(previous) || key.equals(previous.plusDays(1)),
                            "heure " + hour + " : " + previous + " puis " + key + " à " + t);
                    previous = key;
                }
                // Chaque renouvellement mène au jour suivant, jamais deux ni zéro.
                long t = now;
                for (int i = 0; i < 5; i++) {
                    LocalDate key = QuestPeriods.dayKey(t, PARIS, hour);
                    long next = QuestPeriods.nextDailyReset(t, PARIS, hour);
                    assertTrue(next > t);
                    assertEquals(key.plusDays(1), QuestPeriods.dayKey(next, PARIS, hour));
                    assertEquals(key, QuestPeriods.dayKey(next - 1L, PARIS, hour));
                    t = next;
                }
            }
        }
    }

    @Test
    void prochainRenouvellement() {
        long mercredi = at(2026, 11, 18, 12, 0);
        assertEquals(at(2026, 11, 19, 6, 0), QuestPeriods.nextDailyReset(mercredi, PARIS, 6));
        assertEquals(at(2026, 11, 23, 6, 0), QuestPeriods.nextWeeklyReset(mercredi, PARIS, 6));
        // Avant 6 h, le renouvellement du jour est le matin même.
        assertEquals(at(2026, 11, 18, 6, 0), QuestPeriods.nextDailyReset(at(2026, 11, 18, 5, 0), PARIS, 6));
        // Lundi 5 h : la semaine change à 6 h le jour même.
        assertEquals(at(2026, 11, 23, 6, 0), QuestPeriods.nextWeeklyReset(at(2026, 11, 23, 5, 0), PARIS, 6));
        // Lundi 6 h : la suivante est le lundi d'après.
        assertEquals(at(2026, 11, 30, 6, 0), QuestPeriods.nextWeeklyReset(at(2026, 11, 23, 6, 0), PARIS, 6));
    }
}
