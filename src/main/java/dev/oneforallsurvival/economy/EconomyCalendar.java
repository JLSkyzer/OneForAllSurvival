package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Le calendrier réel de l'économie : à quel « jour de paie » appartient un instant, et quand ce jour se clôt. Un jour
 * de paie va de {@code hour} heures (heure locale du fuseau) au même moment le lendemain ; avec {@code hour = 0},
 * c'est le jour civil. Règles pures, sans état ni monde : la paie (E3) et les factures quotidiennes (E6) les
 * partagent.
 *
 * <p>Heure d'été : un jour de paie dure 23 ou 25 heures au changement d'heure, jamais deux jours ni zéro. Une heure de
 * paie qui tombe dans l'heure sautée au printemps fait commencer le jour au premier instant qui suit (3 h pour 2 h) ;
 * une heure vue deux fois à l'automne fait commencer le jour à sa première occurrence.
 */
public final class EconomyCalendar {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Les fuseaux illisibles déjà signalés : un avertissement par valeur, pas un par seconde. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private EconomyCalendar() {}

    /** Le fuseau réglé ({@code payTimeZone}) ; vide ou invalide → celui du système. */
    public static ZoneId configuredZone() {
        return zone(EconomyConfig.payTimeZone());
    }

    /**
     * Le fuseau d'un identifiant IANA ({@code Europe/Paris}) ; vide, ou invalide → le fuseau du système. Un identifiant
     * invalide est signalé une fois au journal.
     */
    public static ZoneId zone(@Nullable String configured) {
        String text = configured == null ? "" : configured.strip();
        if (text.isEmpty()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(text);
        } catch (DateTimeException e) {
            if (WARNED.add(text)) {
                LOGGER.warn("Economie : fuseau horaire '{}' illisible ({}), fuseau du systeme {} utilise", text,
                        e.getMessage(), ZoneId.systemDefault());
            }
            return ZoneId.systemDefault();
        }
    }

    /**
     * Le jour de paie qui contient cet instant : la date locale de l'instant, moins {@code hour} heures (heure murale).
     * Un instant avant l'heure de paie appartient au jour de la veille.
     */
    public static LocalDate payDay(long epochMillis, ZoneId zone, int hour) {
        LocalDateTime local = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone);
        return local.minusHours(hour).toLocalDate();
    }

    /** L'instant où ce jour de paie se clôt : le lendemain à {@code hour} heures, heure locale (premier instant du jour suivant). */
    public static long boundaryAfter(LocalDate day, ZoneId zone, int hour) {
        return ZonedDateTime.of(day.plusDays(1).atTime(hour, 0), zone).toInstant().toEpochMilli();
    }

    /**
     * Les jours à clore : strictement après {@code lastClosed} et strictement avant {@code today}, dans l'ordre, au plus
     * {@code maxDays} (les plus récents sont gardés). Vide si {@code lastClosed} est nul, si rien ne manque, ou si
     * l'horloge a reculé ({@code lastClosed} au jour ou après {@code today}).
     */
    public static List<LocalDate> daysToClose(@Nullable LocalDate lastClosed, LocalDate today, int maxDays) {
        if (lastClosed == null || maxDays <= 0 || !lastClosed.isBefore(today)) {
            return List.of();
        }
        long missing = ChronoUnit.DAYS.between(lastClosed, today) - 1L;
        long kept = Math.min(missing, maxDays);
        List<LocalDate> out = new ArrayList<>((int) Math.max(0L, kept));
        for (long i = kept; i >= 1L; i--) {
            out.add(today.minusDays(i));
        }
        return out;
    }
}
