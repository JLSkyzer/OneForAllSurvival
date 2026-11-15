package dev.oneforallsurvival.economy.payroll;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.CommerceDelivery.Destination;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * La paie, gardée par le serveur (données du monde principal) : le temps compté dans chaque rôle payé, par jour de
 * paie, joueur et clé de salaire, le dernier jour réglé, les salaires dus (la dette de la ville, par ancienneté) et
 * les salaires versés à un joueur absent, pas encore annoncés. Tout vit dans ce seul fichier, écrit par le jeu avec le
 * reste du monde (aucune écriture forcée) : le temps du jour en cours survit à une relance, et un jour réglé ne se
 * règle pas une seconde fois après une relecture (dernier jour réglé, dettes et temps vont ensemble).
 *
 * <p>Aucun temps ne reste sur un jour déjà réglé : un crédit sur un jour au plus égal au dernier réglé (horloge qui a
 * reculé au-delà d'une frontière) va au jour qui suit le dernier réglé, et {@link #setLastClosed} y reporte ce qui
 * resterait sur les jours qu'il déclare réglés (jours sautés au-delà du plafond de rattrapage, ancienne sauvegarde).
 *
 * <p>Par compteur : les secondes comptées (plafonnées par jour et par salaire) et les « cuivre-secondes » (chaque
 * seconde ajoute le taux du moment), d'où le salaire du jour par {@link SalaryMath#amount}. NBT : jours en
 * {@link LocalDate#toString()}, clés par nom. Fil du serveur.
 */
public final class Payroll extends SavedData {

    public static final String KEY = "oneforallsurvival_payroll";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Un compteur : secondes comptées et cuivre-secondes. */
    private static final class Counter {
        long seconds;
        long rateSeconds;
    }

    /** Ce qu'un joueur a compté un jour : son nom au dernier crédit, et un compteur par clé. */
    private static final class PlayerDay {
        String name = "";
        final Map<String, Counter> counters = new TreeMap<>();
    }

    /**
     * Un salaire dû par la ville : le temps compté et le montant d'un jour, pour un joueur et une clé. Les ids
     * croissent avec l'ancienneté et ne reviennent jamais.
     */
    public record Debt(long id, UUID player, String playerName, String key, LocalDate day, long seconds, long amount) {}

    /** Par jour, par joueur. */
    private final TreeMap<LocalDate, Map<UUID, PlayerDay>> days = new TreeMap<>();
    @Nullable
    private LocalDate lastClosed;
    /** Les dettes, par id (donc par ancienneté). */
    private final TreeMap<Long, Debt> debts = new TreeMap<>();
    private long nextDebtId = 1L;
    /** Les salaires versés à un joueur absent, par destination, pas encore annoncés. */
    private final Map<UUID, EnumMap<Destination, Long>> unseenPaid = new HashMap<>();
    /**
     * Paie en pause (horloge qui a sauté pendant que le serveur tournait) : le jour de paie vu, et celui qu'on
     * attendait ; nuls hors pause. Sauvegardés : une relance ne lève pas la pause, seule l'équipe la lève.
     */
    @Nullable
    private LocalDate pausedSeen;
    @Nullable
    private LocalDate pausedExpected;
    /** Transitoire : l'instant de la dernière vérification de l'horloge par le serveur en marche ; nul au démarrage. */
    @Nullable
    private Long lastCheck;

    /** Une paie vide (monde neuf, ou essai détaché). */
    public Payroll() {}

    public static Payroll get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Payroll::load, Payroll::new, KEY);
    }

    /**
     * Compte {@code seconds} de plus pour ce joueur, ce jour et cette clé, au taux {@code rate} (cuivres par heure),
     * sans dépasser {@code maxSeconds} pour ce compteur. Rien n'est noté (ni sali) si rien n'est ajouté.
     */
    public void credit(UUID player, String name, LocalDate day, String key, long seconds, long rate, long maxSeconds) {
        if (lastClosed != null && !day.isAfter(lastClosed)) {
            // L'horloge a reculé au-delà d'une frontière déjà réglée : ce jour ne sera plus jamais clos.
            day = lastClosed.plusDays(1);
        }
        PlayerDay existing = playerDay(player, day);
        long already = existing == null ? 0L : existing.counters.containsKey(key) ? existing.counters.get(key).seconds : 0L;
        long add = SalaryMath.creditable(already, seconds, maxSeconds);
        if (add <= 0L) {
            return;
        }
        PlayerDay entry = days.computeIfAbsent(day, d -> new LinkedHashMap<>())
                .computeIfAbsent(player, p -> new PlayerDay());
        entry.name = name;
        Counter counter = entry.counters.computeIfAbsent(key, k -> new Counter());
        counter.seconds += add;
        counter.rateSeconds = saturatedAdd(counter.rateSeconds, saturatedMul(add, Math.max(0L, rate)));
        setDirty();
    }

    /** Les secondes comptées ce jour, par clé (vide si rien). */
    public Map<String, Long> secondsFor(UUID player, LocalDate day) {
        PlayerDay entry = playerDay(player, day);
        if (entry == null) {
            return Map.of();
        }
        Map<String, Long> out = new LinkedHashMap<>();
        entry.counters.forEach((key, counter) -> out.put(key, counter.seconds));
        return Collections.unmodifiableMap(out);
    }

    /** Les cuivre-secondes comptées ce jour, par clé (vide si rien) ; le salaire en est {@link SalaryMath#amount}. */
    public Map<String, Long> rateSecondsFor(UUID player, LocalDate day) {
        PlayerDay entry = playerDay(player, day);
        if (entry == null) {
            return Map.of();
        }
        Map<String, Long> out = new LinkedHashMap<>();
        entry.counters.forEach((key, counter) -> out.put(key, counter.rateSeconds));
        return Collections.unmodifiableMap(out);
    }

    /** Le nom du joueur à son dernier crédit ce jour, vide si inconnu. */
    public String nameOf(UUID player, LocalDate day) {
        PlayerDay entry = playerDay(player, day);
        return entry == null ? "" : entry.name;
    }

    /** Le dernier jour de paie réglé ; nul tant qu'aucun ne l'a été (premier démarrage). */
    @Nullable
    public LocalDate lastClosed() {
        return lastClosed;
    }

    /**
     * Déclare {@code day} réglé. Le temps qui resterait sur un jour au plus égal à {@code day} (jours sautés au-delà
     * du plafond de rattrapage) est reporté sur le jour suivant, jamais laissé sur un jour qui ne sera plus clos ;
     * le report reste soumis au plafond de 8 h par jour et par salaire (le surplus n'est pas payé, le journal le dit).
     */
    public void setLastClosed(LocalDate day) {
        if (!day.equals(lastClosed)) {
            lastClosed = day;
            carryClosedDays();
            setDirty();
        }
    }

    /**
     * Reporte sur le lendemain du dernier jour réglé les compteurs des jours au plus égaux à lui, sans dépasser le
     * plafond du jour qui les reçoit ({@link #mergeInto}).
     *
     * @return vrai si un compteur a bougé
     */
    private boolean carryClosedDays() {
        if (lastClosed == null) {
            return false;
        }
        LocalDate target = lastClosed.plusDays(1);
        // Les jours à reporter sont relevés d'abord : ajouter le jour cible pendant le parcours ferait échouer
        // l'itérateur dès qu'il y a deux jours à reporter.
        List<LocalDate> old = new ArrayList<>(days.headMap(target, false).keySet());
        for (LocalDate day : old) {
            LOGGER.warn("Paie : le temps du {} (jour deja regle) est reporte au {}", day, target);
            mergeInto(target, days.remove(day));
        }
        return !old.isEmpty();
    }

    /**
     * Ajoute ces compteurs à ceux du jour {@code target}, chacun sans dépasser le plafond de
     * {@link EconomyConfig#salaryMaxHoursPerDay()} heures ; les cuivre-secondes suivent au prorata du temps gardé. Le
     * temps au-delà du plafond n'est pas compté (comme pour un crédit ordinaire) et le journal le dit.
     */
    private void mergeInto(LocalDate target, Map<UUID, PlayerDay> from) {
        long maxSeconds = EconomyConfig.salaryMaxHoursPerDay() * SalaryMath.SECONDS_PER_HOUR;
        for (Map.Entry<UUID, PlayerDay> player : from.entrySet()) {
            PlayerDay into = days.computeIfAbsent(target, d -> new LinkedHashMap<>())
                    .computeIfAbsent(player.getKey(), p -> new PlayerDay());
            if (into.name.isEmpty()) {
                into.name = player.getValue().name;
            }
            player.getValue().counters.forEach((key, counter) -> {
                Counter c = into.counters.computeIfAbsent(key, k -> new Counter());
                long add = SalaryMath.creditable(c.seconds, counter.seconds, maxSeconds);
                if (add < counter.seconds) {
                    LOGGER.warn("Paie : {} s de {} ({}) au-dela du plafond du {} ne sont pas reportees",
                            counter.seconds - add, player.getValue().name, key, target);
                }
                c.seconds += add;
                c.rateSeconds = saturatedAdd(c.rateSeconds, share(counter.rateSeconds, add, counter.seconds));
            });
            into.counters.values().removeIf(c -> c.seconds <= 0L && c.rateSeconds <= 0L);
        }
        Map<UUID, PlayerDay> players = days.get(target);
        if (players != null) {
            players.values().removeIf(p -> p.counters.isEmpty());
            if (players.isEmpty()) {
                days.remove(target);
            }
        }
    }

    /** {@code total × part ÷ whole}, arrondi vers le bas, sans débordement (part au plus whole). */
    private static long share(long total, long part, long whole) {
        if (part >= whole) {
            return total;
        }
        if (part <= 0L || whole <= 0L) {
            return 0L;
        }
        return total / whole * part + total % whole * part / whole;
    }

    // ---------------------------------------------------------------------------------------------
    // Pause sur saut d'horloge
    // ---------------------------------------------------------------------------------------------

    /** La paie est-elle en pause (horloge qui a sauté, en attente de l'équipe) ? */
    public boolean paused() {
        return pausedSeen != null;
    }

    /** Le jour de paie lu au saut ; nul hors pause. */
    @Nullable
    public LocalDate pausedSeen() {
        return pausedSeen;
    }

    /** Le jour de paie attendu au saut (celui de la vérification d'avant) ; nul hors pause. */
    @Nullable
    public LocalDate pausedExpected() {
        return pausedExpected;
    }

    /** Met la paie en pause : aucun jour n'est clos tant que l'équipe ne l'a pas levée. */
    public void pause(LocalDate seen, LocalDate expected) {
        pausedSeen = seen;
        pausedExpected = expected;
        setDirty();
    }

    /** Lève la pause. */
    public void unpause() {
        if (pausedSeen != null || pausedExpected != null) {
            pausedSeen = null;
            pausedExpected = null;
            setDirty();
        }
    }

    /** L'instant de la dernière vérification de l'horloge, nul depuis le chargement (transitoire, pas sauvegardé). */
    @Nullable
    public Long lastCheck() {
        return lastCheck;
    }

    /** Note l'instant de cette vérification de l'horloge (transitoire). */
    public void markChecked(long now) {
        lastCheck = now;
    }

    /**
     * L'horloge était fausse : ramène le dernier jour réglé à la veille de {@code today} (jamais plus tard que celui
     * noté : un retard se rattrape normalement), reporte sur {@code today} le temps compté sur des jours postérieurs
     * (au plafond du jour), et lève la pause. Rien n'est payé ici ; les dettes déjà créées restent dues ou payées une
     * seule fois, et un jour déjà clos n'a plus de compteur à reclore.
     */
    public void rewindTo(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        if (lastClosed == null || lastClosed.isAfter(yesterday)) {
            lastClosed = yesterday;
        }
        List<LocalDate> future = new ArrayList<>(days.tailMap(today, false).keySet());
        for (LocalDate day : future) {
            LOGGER.warn("Paie : le temps du {} (jour a venir) est reporte au {}", day, today);
            mergeInto(today, days.remove(day));
        }
        carryClosedDays();
        pausedSeen = null;
        pausedExpected = null;
        setDirty();
    }

    // ---------------------------------------------------------------------------------------------
    // Dettes et avis
    // ---------------------------------------------------------------------------------------------

    /**
     * Arrête le jour : chaque compteur de ce jour au montant non nul devient une dette (ordre du joueur, UUID, puis de
     * la clé ; ids croissants), et les compteurs du jour sont retirés. Un jour sans compteur ne donne rien.
     *
     * @return les dettes créées
     */
    public List<Debt> closeDay(LocalDate day) {
        Map<UUID, PlayerDay> players = days.remove(day);
        if (players == null) {
            return List.of();
        }
        List<Debt> created = new ArrayList<>();
        for (Map.Entry<UUID, PlayerDay> player : new TreeMap<>(players).entrySet()) {
            for (Map.Entry<String, Counter> counter : player.getValue().counters.entrySet()) {
                long amount = SalaryMath.amount(counter.getValue().rateSeconds);
                if (amount <= 0L) {
                    continue;
                }
                Debt debt = new Debt(nextDebtId++, player.getKey(), player.getValue().name, counter.getKey(), day,
                        counter.getValue().seconds, amount);
                debts.put(debt.id(), debt);
                created.add(debt);
            }
        }
        setDirty();
        return created;
    }

    /** Toutes les dettes, de la plus ancienne à la plus récente. */
    public List<Debt> debts() {
        return List.copyOf(debts.values());
    }

    public void removeDebt(long id) {
        if (debts.remove(id) != null) {
            setDirty();
        }
    }

    /** La dette de la ville, en cuivres (saturée). */
    public long owedTotal() {
        long total = 0L;
        for (Debt debt : debts.values()) {
            total = saturatedAdd(total, debt.amount());
        }
        return total;
    }

    /** Les dettes envers ce joueur, de la plus ancienne à la plus récente. */
    public List<Debt> owedTo(UUID player) {
        List<Debt> out = new ArrayList<>();
        for (Debt debt : debts.values()) {
            if (debt.player().equals(player)) {
                out.add(debt);
            }
        }
        return out;
    }

    /** Note un salaire versé à un joueur absent, annoncé à sa connexion. */
    public void addUnseenPaid(UUID player, long amount, Destination destination) {
        if (amount <= 0L) {
            return;
        }
        unseenPaid.computeIfAbsent(player, p -> new EnumMap<>(Destination.class))
                .merge(destination, amount, Payroll::saturatedAdd);
        setDirty();
    }

    /** Lit et oublie les salaires versés pendant l'absence de ce joueur (vide s'il n'y en a pas). */
    public EnumMap<Destination, Long> takeUnseenPaid(UUID player) {
        EnumMap<Destination, Long> seen = unseenPaid.remove(player);
        if (seen == null) {
            return new EnumMap<>(Destination.class);
        }
        setDirty();
        return seen;
    }

    @Nullable
    private PlayerDay playerDay(UUID player, LocalDate day) {
        Map<UUID, PlayerDay> players = days.get(day);
        return players == null ? null : players.get(player);
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }

    private static long saturatedMul(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return high != 0L || low < 0L ? Long.MAX_VALUE : low;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag dayList = new ListTag();
        for (Map.Entry<LocalDate, Map<UUID, PlayerDay>> day : days.entrySet()) {
            ListTag players = new ListTag();
            for (Map.Entry<UUID, PlayerDay> player : day.getValue().entrySet()) {
                ListTag counters = new ListTag();
                for (Map.Entry<String, Counter> counter : player.getValue().counters.entrySet()) {
                    CompoundTag c = new CompoundTag();
                    c.putString("key", counter.getKey());
                    c.putLong("seconds", counter.getValue().seconds);
                    c.putLong("rateSeconds", counter.getValue().rateSeconds);
                    counters.add(c);
                }
                CompoundTag p = new CompoundTag();
                p.putUUID("player", player.getKey());
                p.putString("name", player.getValue().name);
                p.put("counters", counters);
                players.add(p);
            }
            CompoundTag d = new CompoundTag();
            d.putString("day", day.getKey().toString());
            d.put("players", players);
            dayList.add(d);
        }
        tag.put("days", dayList);
        if (lastClosed != null) {
            tag.putString("lastClosed", lastClosed.toString());
        }
        ListTag debtList = new ListTag();
        for (Debt debt : debts.values()) {
            CompoundTag d = new CompoundTag();
            d.putLong("id", debt.id());
            d.putUUID("player", debt.player());
            d.putString("name", debt.playerName());
            d.putString("key", debt.key());
            d.putString("day", debt.day().toString());
            d.putLong("seconds", debt.seconds());
            d.putLong("amount", debt.amount());
            debtList.add(d);
        }
        tag.put("debts", debtList);
        tag.putLong("nextDebtId", nextDebtId);
        ListTag unseen = new ListTag();
        for (Map.Entry<UUID, EnumMap<Destination, Long>> entry : unseenPaid.entrySet()) {
            for (Map.Entry<Destination, Long> part : entry.getValue().entrySet()) {
                CompoundTag u = new CompoundTag();
                u.putUUID("player", entry.getKey());
                u.putString("to", part.getKey().name());
                u.putLong("amount", part.getValue());
                unseen.add(u);
            }
        }
        tag.put("unseenPaid", unseen);
        if (pausedSeen != null && pausedExpected != null) {
            tag.putString("pausedSeen", pausedSeen.toString());
            tag.putString("pausedExpected", pausedExpected.toString());
        }
        return tag;
    }

    /** Relit une sauvegarde ; un jour, un joueur ou un compteur illisibles sont ignorés avec un avertissement. */
    public static Payroll load(CompoundTag tag) {
        Payroll payroll = new Payroll();
        for (Tag rawDay : tag.getList("days", Tag.TAG_COMPOUND)) {
            CompoundTag d = (CompoundTag) rawDay;
            LocalDate day = parseDay(d.getString("day"));
            if (day == null) {
                continue;
            }
            for (Tag rawPlayer : d.getList("players", Tag.TAG_COMPOUND)) {
                CompoundTag p = (CompoundTag) rawPlayer;
                if (!p.hasUUID("player")) {
                    LOGGER.warn("Paie : temps du {} ignore, joueur sans identifiant", day);
                    continue;
                }
                PlayerDay entry = new PlayerDay();
                entry.name = p.getString("name");
                for (Tag rawCounter : p.getList("counters", Tag.TAG_COMPOUND)) {
                    CompoundTag c = (CompoundTag) rawCounter;
                    String key = c.getString("key");
                    if (key.isEmpty()) {
                        continue;
                    }
                    Counter counter = new Counter();
                    counter.seconds = Math.max(0L, c.getLong("seconds"));
                    counter.rateSeconds = Math.max(0L, c.getLong("rateSeconds"));
                    entry.counters.put(key, counter);
                }
                if (!entry.counters.isEmpty()) {
                    payroll.days.computeIfAbsent(day, k -> new LinkedHashMap<>()).put(p.getUUID("player"), entry);
                }
            }
        }
        if (tag.contains("lastClosed", Tag.TAG_STRING)) {
            payroll.lastClosed = parseDay(tag.getString("lastClosed"));
        }
        long maxId = 0L;
        for (Tag raw : tag.getList("debts", Tag.TAG_COMPOUND)) {
            CompoundTag d = (CompoundTag) raw;
            LocalDate day = parseDay(d.getString("day"));
            long amount = d.getLong("amount");
            if (day == null || !d.hasUUID("player") || d.getString("key").isEmpty() || amount <= 0L) {
                LOGGER.warn("Paie : dette illisible ignoree : {}", d);
                continue;
            }
            Debt debt = new Debt(d.getLong("id"), d.getUUID("player"), d.getString("name"), d.getString("key"), day,
                    Math.max(0L, d.getLong("seconds")), amount);
            payroll.debts.put(debt.id(), debt);
            maxId = Math.max(maxId, debt.id());
        }
        payroll.nextDebtId = Math.max(Math.max(1L, tag.getLong("nextDebtId")), maxId + 1L);
        for (Tag raw : tag.getList("unseenPaid", Tag.TAG_COMPOUND)) {
            CompoundTag u = (CompoundTag) raw;
            Destination to;
            try {
                to = Destination.valueOf(u.getString("to").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (u.hasUUID("player") && u.getLong("amount") > 0L) {
                payroll.unseenPaid.computeIfAbsent(u.getUUID("player"), p -> new EnumMap<>(Destination.class))
                        .merge(to, u.getLong("amount"), Payroll::saturatedAdd);
            }
        }
        if (tag.contains("pausedSeen", Tag.TAG_STRING) && tag.contains("pausedExpected", Tag.TAG_STRING)) {
            LocalDate seen = parseDay(tag.getString("pausedSeen"));
            LocalDate expected = parseDay(tag.getString("pausedExpected"));
            if (seen != null && expected != null) {
                payroll.pausedSeen = seen;
                payroll.pausedExpected = expected;
            }
        }
        // Une sauvegarde d'avant le report : du temps laissé sur un jour réglé va au jour suivant.
        if (payroll.carryClosedDays()) {
            payroll.setDirty();
        }
        return payroll;
    }

    @Nullable
    private static LocalDate parseDay(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            LOGGER.warn("Paie : jour illisible ignore : '{}'", text);
            return null;
        }
    }
}
