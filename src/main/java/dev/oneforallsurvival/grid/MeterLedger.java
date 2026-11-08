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
 * La facture du compteur, sans Minecraft : la règle d'un relevé ({@link #read}) et le registre des abonnés, par nom
 * normalisé : pour chaque compteur de l'abonné (par numéro) son dernier relevé ou l'index de son inscription, et les
 * événements notés depuis. {@link SubscriberSavedData} le sauvegarde dans le monde.
 *
 * <p>Chaque FE compté par un compteur est facturé une fois, à un seul abonné : de l'index d'inscription
 * ({@link #inscribe}) au relevé, puis de relevé en relevé. Quand le compteur change d'abonné, l'ancien reçoit sur le
 * champ un relevé de clôture jusqu'à l'index du changement, et le nouveau part de cet index.
 */
public final class MeterLedger {

    /** Longueur maximale du nom d'un abonné. */
    public static final int MAX_NAME_LENGTH = 16;
    private static final DateTimeFormatter REAL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);
    private static final long TICKS_PER_DAY = 24_000L;

    /** Ce qu'un relevé signale. L'ordre est celui de l'affichage. */
    public enum Anomaly { METER_REMOVED, METER_CHANGED, INDEX_DECREASED, SEAL_BROKEN }

    /** Ce qu'un bloc note au registre d'un abonné entre deux relevés : un compteur déposé, un scellé brisé. */
    public enum EventKind { REMOVED, SEAL_BROKEN }

    /**
     * Un événement noté au registre, avec le jour de jeu où il s'est produit et le compteur concerné ({@code null} s'il
     * n'en concerne aucun, ou pour une sauvegarde ancienne). {@code notified} : déjà signalé sur un relevé, gardé
     * seulement pour reconnaître un compteur neuf qui remplace le compteur déposé.
     */
    public record Event(EventKind kind, long day, @Nullable UUID serial, boolean notified) {

        public Event(EventKind kind, long day) {
            this(kind, day, null, false);
        }
    }

    /**
     * Le point de départ du prochain relevé d'un compteur pour un abonné : numéro, index, jour de jeu, date réelle.
     * {@code baseline} : l'index noté à l'inscription, pas encore de relevé.
     */
    public record Previous(UUID serial, long index, long day, String realDate, boolean baseline) {

        /** Un vrai relevé. */
        public Previous(UUID serial, long index, long day, String realDate) {
            this(serial, index, day, realDate, false);
        }
    }

    /**
     * Un relevé : ce que porte le papier remis à l'ingénieur. {@code closing} : le relevé de clôture donné quand le
     * compteur passe à un autre abonné.
     */
    public record Reading(String subscriber, long index, long consumption, long day, String realDate,
            List<Anomaly> anomalies, boolean closing) {

        public Reading {
            anomalies = List.copyOf(anomalies);
        }

        /** Un relevé ordinaire. */
        public Reading(String subscriber, long index, long consumption, long day, String realDate,
                List<Anomaly> anomalies) {
            this(subscriber, index, consumption, day, realDate, anomalies, false);
        }
    }

    /** Le compte d'un abonné dans le registre : ses compteurs par numéro, ses événements depuis. */
    public record Account(Map<UUID, Previous> meters, List<Event> events) {

        public static final Account EMPTY = new Account(Map.of(), List.of());

        public Account {
            meters = Collections.unmodifiableMap(new LinkedHashMap<>(meters));
            events = List.copyOf(events);
        }
    }

    private final Map<String, Account> accounts = new LinkedHashMap<>();

    // ---------------------------------------------------------------------------------------------
    // Règles
    // ---------------------------------------------------------------------------------------------

    /**
     * Le relevé de {@code serial} à l'index {@code index}, depuis {@code previous}, le point de départ de ce compteur
     * pour cet abonné (relevé ou inscription). Sans point de départ, la consommation est l'index. Index en baisse :
     * rien n'est facturé (jamais de consommation négative) et l'anomalie le dit. Chaque événement donne son anomalie,
     * une seule fois : une dépose pas encore signalée dit « compteur déposé » ; la dépose d'un autre compteur (numéro
     * différent) dit aussi « compteur différent ».
     */
    public static Reading read(String subscriber, UUID serial, long index, long day, String realDate,
            @Nullable Previous previous, List<Event> events) {
        Set<Anomaly> anomalies = EnumSet.noneOf(Anomaly.class);
        long consumption;
        if (previous == null || !previous.serial().equals(serial)) {
            consumption = index;
        } else if (index < previous.index()) {
            anomalies.add(Anomaly.INDEX_DECREASED);
            consumption = 0;
        } else {
            consumption = index - previous.index();
        }
        for (Event event : events) {
            switch (event.kind()) {
                case REMOVED -> {
                    if (!event.notified()) {
                        anomalies.add(Anomaly.METER_REMOVED);
                    }
                    if (event.serial() != null && !event.serial().equals(serial)) {
                        anomalies.add(Anomaly.METER_CHANGED);
                    }
                }
                case SEAL_BROKEN -> anomalies.add(Anomaly.SEAL_BROKEN);
            }
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

    /** Le point de départ du compteur {@code serial} pour l'abonné, ou {@code null} s'il n'est pas à son compte. */
    @Nullable
    public Previous previous(String subscriber, UUID serial) {
        return account(normalize(subscriber)).meters().get(serial);
    }

    public List<Event> events(String subscriber) {
        return account(normalize(subscriber)).events();
    }

    /** Note un événement qui ne concerne aucun compteur en particulier ; il ressortira au relevé suivant. */
    public void record(String subscriber, EventKind kind, long day) {
        record(subscriber, kind, day, null);
    }

    /** Note un événement concernant le compteur {@code serial} au registre de l'abonné. */
    public void record(String subscriber, EventKind kind, long day, @Nullable UUID serial) {
        addEvent(normalize(subscriber), new Event(kind, day, serial, false));
    }

    /**
     * Le compteur {@code serial}, à l'index {@code index}, passe de l'abonné {@code from} (vide : aucun) à {@code to}.
     * L'ancien abonné est relevé une dernière fois jusqu'à cet index, comme par {@link #take} : c'est le relevé de
     * clôture, renvoyé pour être remis à l'ingénieur ; puis le compteur quitte son compte, où rien ne reste en attente
     * pour lui. Le nouvel abonné part de cet index : il ne paie rien de ce qui a été compté avant.
     *
     * @return le relevé de clôture, ou {@code null} pour une première inscription ou le même abonné (au nom normalisé
     *         près), qui ne changent rien
     */
    @Nullable
    public Reading inscribe(String from, String to, UUID serial, long index, long day, String realDate) {
        String oldKey = normalize(from);
        String newKey = normalize(to);
        if (oldKey.equals(newKey)) {
            return null;
        }
        Reading closing = null;
        if (!oldKey.isEmpty()) {
            Reading last = take(from, serial, index, day, realDate);
            closing = new Reading(last.subscriber(), last.index(), last.consumption(), last.day(), last.realDate(),
                    last.anomalies(), true);
            Account old = account(oldKey);
            Map<UUID, Previous> meters = new LinkedHashMap<>(old.meters());
            meters.remove(serial);
            accounts.put(oldKey, new Account(meters, old.events()));
        }
        Account account = account(newKey);
        Map<UUID, Previous> meters = new LinkedHashMap<>(account.meters());
        meters.put(serial, new Previous(serial, index, day, realDate, true));
        accounts.put(newKey, new Account(meters, account.events()));
        return closing;
    }

    /**
     * Relève le compteur {@code serial} : calcule le relevé depuis son point de départ pour cet abonné, le retient comme
     * nouveau point de départ et traite les événements. Scellé brisé et dépose sont signalés au premier relevé venu,
     * de n'importe quel compteur de l'abonné, une seule fois. La dépose d'un autre compteur reste ensuite au registre,
     * marquée signalée, jusqu'au premier relevé d'un compteur neuf (« compteur différent » : il l'a remplacé) ou au
     * relevé du compteur déposé s'il est reposé.
     */
    public Reading take(String subscriber, UUID serial, long index, long day, String realDate) {
        String key = normalize(subscriber);
        Account account = account(key);
        Previous previous = account.meters().get(serial);
        boolean firstReading = previous == null || previous.baseline();
        boolean replacementTaken = false;
        List<Event> taken = new ArrayList<>();
        List<Event> kept = new ArrayList<>();
        for (Event event : account.events()) {
            boolean otherMeterRemoved = event.kind() == EventKind.REMOVED && event.serial() != null
                    && !event.serial().equals(serial);
            if (!otherMeterRemoved) {
                taken.add(event);
            } else if (firstReading && !replacementTaken) {
                taken.add(event); // le compteur neuf remplace le compteur déposé
                replacementTaken = true;
            } else {
                if (!event.notified()) {
                    // Signalée sur ce relevé, sans « compteur différent » : un numéro nul ne désigne aucun compteur.
                    taken.add(new Event(EventKind.REMOVED, event.day(), null, false));
                }
                kept.add(new Event(event.kind(), event.day(), event.serial(), true));
            }
        }
        Reading reading = read(subscriber.trim(), serial, index, day, realDate, previous, taken);
        Map<UUID, Previous> meters = new LinkedHashMap<>(account.meters());
        meters.put(serial, new Previous(serial, index, day, realDate));
        accounts.put(key, new Account(meters, kept));
        return reading;
    }

    private Account account(String key) {
        return accounts.getOrDefault(key, Account.EMPTY);
    }

    private void addEvent(String key, Event event) {
        Account account = account(key);
        List<Event> events = new ArrayList<>(account.events());
        events.add(event);
        accounts.put(key, new Account(account.meters(), events));
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
