package dev.oneforallsurvival.economy.quest;

import dev.oneforallsurvival.economy.EconomyCalendar;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Les périodes des quêtes sur le calendrier réel : un jour de quête va de {@code hour} heures (heure locale du fuseau
 * {@code payTimeZone}) au même moment le lendemain, une semaine de quête commence le lundi à cette heure. Les règles
 * d'heure d'été sont celles d'{@link EconomyCalendar} : jamais de jour perdu ni doublé.
 */
public final class QuestPeriods {

    private QuestPeriods() {}

    /** Le jour de quête de cet instant : avant {@code hour} heures, c'est encore la veille. */
    public static LocalDate dayKey(long now, ZoneId zone, int hour) {
        return EconomyCalendar.payDay(now, zone, hour);
    }

    /** Le lundi de la semaine du jour de quête de cet instant (lundi avant {@code hour} heures : la semaine d'avant). */
    public static LocalDate weekKey(long now, ZoneId zone, int hour) {
        return dayKey(now, zone, hour).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** L'instant où les journalières de cet instant sont remplacées. */
    public static long nextDailyReset(long now, ZoneId zone, int hour) {
        return EconomyCalendar.boundaryAfter(dayKey(now, zone, hour), zone, hour);
    }

    /** L'instant où les hebdomadaires de cet instant sont remplacées : le lundi suivant, à {@code hour} heures. */
    public static long nextWeeklyReset(long now, ZoneId zone, int hour) {
        return EconomyCalendar.boundaryAfter(weekKey(now, zone, hour).plusDays(6), zone, hour);
    }
}
