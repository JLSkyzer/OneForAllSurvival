package dev.oneforallsurvival.grid;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * La facture du compteur, sans Minecraft : la règle d'un relevé ({@link #read}) et le registre des abonnés (dernier
 * relevé et événements depuis, par nom normalisé). {@link SubscriberSavedData} le sauvegarde dans le monde.
 */
public final class MeterLedger {

    /** Longueur maximale du nom d'un abonné. */
    public static final int MAX_NAME_LENGTH = 16;
    private static final DateTimeFormatter REAL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);
    private static final long TICKS_PER_DAY = 24_000L;

    /** Ce qu'un relevé signale. L'ordre est celui de l'affichage. */
    public enum Anomaly { METER_REMOVED, METER_CHANGED, INDEX_DECREASED, SEAL_BROKEN }

    /** Ce qu'un bloc note au registre d'un abonné entre deux relevés. */
    public enum EventKind { REMOVED, SEAL_BROKEN }

    /** Un événement noté au registre, avec le jour de jeu où il s'est produit. */
    public record Event(EventKind kind, long day) {}

    /** Le dernier relevé d'un abonné : numéro du compteur, index, jour de jeu, date réelle. */
    public record Previous(UUID serial, long index, long day, String realDate) {}

    /** Un relevé : ce que porte le papier remis à l'ingénieur. */
    public record Reading(String subscriber, long index, long consumption, long day, String realDate,
            List<Anomaly> anomalies) {

        public Reading {
            anomalies = List.copyOf(anomalies);
        }
    }

    /** Le compte d'un abonné dans le registre. */
    public record Account(@Nullable Previous previous, List<Event> events) {

        public Account {
            events = List.copyOf(events);
        }
    }

    private final Map<String, Account> accounts = new LinkedHashMap<>();

    // ---------------------------------------------------------------------------------------------
    // Règles
    // ---------------------------------------------------------------------------------------------

    /**
     * Le relevé de {@code serial} à l'index {@code index}. Premier relevé : la consommation est l'index. Compteur
     * changé depuis le précédent : la consommation est l'index du nouveau. Index en baisse sur le même compteur : rien
     * n'est facturé (jamais de consommation négative) et l'anomalie le dit. Chaque événement noté depuis donne son
     * anomalie, une seule fois.
     */
    public static Reading read(String subscriber, UUID serial, long index, long day, String realDate,
            @Nullable Previous previous, List<Event> events) {
        Set<Anomaly> anomalies = EnumSet.noneOf(Anomaly.class);
        long consumption;
        if (previous == null) {
            consumption = index;
        } else if (!previous.serial().equals(serial)) {
            anomalies.add(Anomaly.METER_CHANGED);
            consumption = index;
        } else if (index < previous.index()) {
            anomalies.add(Anomaly.INDEX_DECREASED);
            consumption = 0;
        } else {
            consumption = index - previous.index();
        }
        for (Event event : events) {
            anomalies.add(switch (event.kind()) {
                case REMOVED -> Anomaly.METER_REMOVED;
                case SEAL_BROKEN -> Anomaly.SEAL_BROKEN;
            });
        }
        return new Reading(subscriber, index, consumption, day, realDate, new ArrayList<>(anomalies));
    }

    /** La clé d'un abonné au registre : sans espaces autour, en minuscules. */
    public static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** Vrai pour 1 à 16 caractères (espaces autour retirés) : lettres, chiffres, espace, tiret. */
    public static boolean isValidName(@Nullable String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            return false;
        }
        return trimmed.codePoints().allMatch(MeterLedger::isNameChar);
    }

    /** Un caractère admis dans le nom d'un abonné : lettre, chiffre, espace ou tiret. */
    public static boolean isNameChar(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '-';
    }

    /** Le jour de jeu, à partir de 1. */
    public static long gameDay(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY) + 1;
    }

    /** La date réelle, {@code dd/MM/yyyy HH:mm} dans le fuseau donné. */
    public static String formatRealDate(Instant instant, ZoneId zone) {
        return REAL_DATE.withZone(zone).format(instant);
    }

    // ---------------------------------------------------------------------------------------------
    // Registre
    // ---------------------------------------------------------------------------------------------

    @Nullable
    public Previous previous(String subscriber) {
        Account account = accounts.get(normalize(subscriber));
        return account == null ? null : account.previous();
    }

    public List<Event> events(String subscriber) {
        Account account = accounts.get(normalize(subscriber));
        return account == null ? List.of() : account.events();
    }

    /** Note un événement au registre de l'abonné ; il ressortira au relevé suivant. */
    public void record(String subscriber, EventKind kind, long day) {
        String key = normalize(subscriber);
        Account account = accounts.get(key);
        List<Event> events = new ArrayList<>(account == null ? List.of() : account.events());
        events.add(new Event(kind, day));
        accounts.put(key, new Account(account == null ? null : account.previous(), events));
    }

    /** Relève : calcule le relevé, le retient comme le dernier de l'abonné et vide ses événements. */
    public Reading take(String subscriber, UUID serial, long index, long day, String realDate) {
        String key = normalize(subscriber);
        Reading reading = read(subscriber.trim(), serial, index, day, realDate, previous(key), events(key));
        accounts.put(key, new Account(new Previous(serial, index, day, realDate), List.of()));
        return reading;
    }

    /** Les comptes, par nom normalisé (lecture seule), pour la sauvegarde. */
    public Map<String, Account> accounts() {
        return Collections.unmodifiableMap(accounts);
    }

    /** Remet un compte relu d'une sauvegarde. */
    public void restore(String subscriber, Account account) {
        accounts.put(normalize(subscriber), account);
    }
}
