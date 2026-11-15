package dev.oneforallsurvival.economy.quest;

import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Le carnet de quêtes d'un joueur : ses deux séries, sa relance du jour, son compteur de plafond de la semaine, ses
 * récompenses en attente et sa progression dans les quêtes d'événement. Règles pures sur un état sérialisable ; il vit
 * dans {@link Player#PERSISTED_NBT_TAG} (clé {@value #KEY}), le même fichier que l'inventaire où arrivent les pièces
 * d'une réclamation : la pièce et la marque « réclamée » sont écrites ensemble ou pas du tout. Forge recopie cette
 * clé à la mort.
 *
 * <p>Horloge : une série n'est remplacée que si la clé présente est strictement après la clé gardée, le plafond n'est
 * remis que si la semaine présente est strictement après celle du compteur, la relance n'est rendue que si le jour
 * présent est strictement après celui de la dernière relance. Une horloge qui recule met tout en pause ; un saut en
 * avant ne fait qu'un renouvellement.
 */
public final class QuestBook {

    public static final String KEY = "oneforallsurvival:quests";

    /** Événements vus au plus (identifiant#début) : de quoi ne jamais rendre un événement déjà retiré. */
    private static final int SEEN_MAX = 64;

    /**
     * Une quête finie d'une liste remplacée, réclamable plus tard ; {@code seriesKey} est la clé de sa série,
     * {@code origin} sa liste d'origine ({@code DAILY}, {@code WEEKLY} ou {@code EVENT}) et {@code eventKey} la date de
     * début de l'événement (0 sinon).
     */
    public record Pending(Quest quest, LocalDate seriesKey, QuestRef.Kind origin, long eventKey) {

        public Pending(Quest quest, LocalDate seriesKey) {
            this(quest, seriesKey, QuestRef.Kind.DAILY, 0L);
        }
    }

    @Nullable
    private LocalDate dailyKey;
    private final List<Quest> daily = new ArrayList<>();
    @Nullable
    private LocalDate weeklyKey;
    private final List<Quest> weekly = new ArrayList<>();
    @Nullable
    private LocalDate rerollDay;
    private int rerollsUsed;
    @Nullable
    private LocalDate capWeek;
    private long capPaid;
    /** De la plus ancienne à la plus récente. */
    private final List<Pending> pending = new ArrayList<>();
    /** Identifiant d'événement → progression du joueur. */
    private final Map<String, Quest> events = new LinkedHashMap<>();
    /** Identifiant d'événement → sa date de début (ms) : l'événement relancé n'est pas celui-ci. */
    private final Map<String, Long> eventStarts = new LinkedHashMap<>();
    /** Les événements déjà reçus, retirés ou non (« identifiant#début ») : un événement retiré ne revient pas. */
    private final LinkedHashSet<String> eventsSeen = new LinkedHashSet<>();
    /** Récompenses en attente oubliées (au-delà du maximum) au dernier {@link #refresh} ; jamais sauvegardé. */
    private int forgotten;

    public QuestBook() {}

    // ---------------------------------------------------------------------------------------------
    // Lecture
    // ---------------------------------------------------------------------------------------------

    @Nullable
    public LocalDate dailyKey() {
        return dailyKey;
    }

    public List<Quest> daily() {
        return List.copyOf(daily);
    }

    @Nullable
    public LocalDate weeklyKey() {
        return weeklyKey;
    }

    public List<Quest> weekly() {
        return List.copyOf(weekly);
    }

    @Nullable
    public LocalDate rerollDay() {
        return rerollDay;
    }

    public int rerollsUsed() {
        return rerollsUsed;
    }

    @Nullable
    public LocalDate capWeek() {
        return capWeek;
    }

    public long capPaid() {
        return capPaid;
    }

    public List<Pending> pending() {
        return List.copyOf(pending);
    }

    /** Les quêtes d'événement du joueur ; modifiable (la progression). */
    public Map<String, Quest> events() {
        return events;
    }

    /** La date de début de l'événement de cette quête, ou 0 s'il n'y en a pas. */
    public long eventStart(String eventId) {
        return eventStarts.getOrDefault(eventId, 0L);
    }

    /** Le jour (UTC) d'une date de début d'événement : la clé de série informative des références d'événement. */
    public static LocalDate eventDay(long startMillis) {
        return LocalDate.ofEpochDay(Math.floorDiv(startMillis, 86_400_000L));
    }

    /** Récompenses en attente oubliées au dernier {@link #refresh}, la plus ancienne d'abord. */
    public int forgotten() {
        return forgotten;
    }

    public List<Quest> series(Series series) {
        return series == Series.DAILY ? daily() : weekly();
    }

    @Nullable
    public LocalDate seriesKey(Series series) {
        return series == Series.DAILY ? dailyKey : weeklyKey;
    }

    // ---------------------------------------------------------------------------------------------
    // Renouvellement
    // ---------------------------------------------------------------------------------------------

    /**
     * Remplace chaque série dont la période est passée : la clé présente doit être strictement après la clé gardée
     * (une horloge qui recule ne change rien). Les quêtes finies et non réclamées d'une série remplacée passent dans
     * les récompenses en attente (au plus {@code pendingMax}, les plus anciennes oubliées) ; les autres expirent.
     *
     * @return vrai si quelque chose a changé
     */
    public boolean refresh(LocalDate today, LocalDate week, Function<Series, List<Quest>> drawer, int pendingMax) {
        return refresh(today, week, drawer, pendingMax, List.of());
    }

    /**
     * Comme {@link #refresh(LocalDate, LocalDate, Function, int)}, avec les événements en cours : un événement en
     * cours que le joueur n'a pas encore reçu lui est ajouté (une fois : jamais un événement déjà retiré), celui qui
     * n'est plus en cours, ou qui a été relancé (autre date de début), est retiré, sa quête finie et non réclamée
     * passant dans les récompenses en attente.
     */
    public boolean refresh(LocalDate today, LocalDate week, Function<Series, List<Quest>> drawer, int pendingMax,
            List<QuestEvent> running) {
        forgotten = 0;
        boolean changed = refreshEvents(running);
        if (dailyKey == null || today.isAfter(dailyKey)) {
            replace(daily, dailyKey, QuestRef.Kind.DAILY, drawer.apply(Series.DAILY));
            dailyKey = today;
            changed = true;
        }
        if (weeklyKey == null || week.isAfter(weeklyKey)) {
            replace(weekly, weeklyKey, QuestRef.Kind.WEEKLY, drawer.apply(Series.WEEKLY));
            weeklyKey = week;
            changed = true;
        }
        int max = Math.max(0, pendingMax);
        while (pending.size() > max) {
            pending.remove(0);
            forgotten++;
            changed = true;
        }
        return changed;
    }

    private void replace(List<Quest> series, @Nullable LocalDate oldKey, QuestRef.Kind origin, List<Quest> fresh) {
        if (oldKey != null) {
            for (Quest quest : series) {
                if (quest.complete() && !quest.claimed()) {
                    pending.add(new Pending(quest, oldKey, origin, 0L));
                }
            }
        }
        series.clear();
        series.addAll(fresh);
    }

    private boolean refreshEvents(List<QuestEvent> running) {
        Map<String, QuestEvent> byId = new LinkedHashMap<>();
        running.forEach(e -> byId.put(e.id(), e));
        boolean changed = false;
        for (Iterator<Map.Entry<String, Quest>> it = events.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Quest> entry = it.next();
            long start = eventStart(entry.getKey());
            QuestEvent live = byId.get(entry.getKey());
            if (live != null && live.startMillis() == start) {
                continue;
            }
            Quest quest = entry.getValue();
            if (quest.complete() && !quest.claimed()) {
                pending.add(new Pending(quest, eventDay(start), QuestRef.Kind.EVENT, start));
            }
            it.remove();
            eventStarts.remove(entry.getKey());
            changed = true;
        }
        for (QuestEvent event : byId.values()) {
            String mark = event.id() + "#" + event.startMillis();
            if (events.containsKey(event.id()) || eventsSeen.contains(mark)) {
                continue;
            }
            events.put(event.id(), event.toQuest());
            eventStarts.put(event.id(), event.startMillis());
            eventsSeen.add(mark);
            while (eventsSeen.size() > SEEN_MAX) {
                eventsSeen.remove(eventsSeen.iterator().next());
            }
            changed = true;
        }
        return changed;
    }

    /** Remplace une série entière (essais, et équipe). */
    public void setSeries(Series series, LocalDate key, List<Quest> quests) {
        List<Quest> list = series == Series.DAILY ? daily : weekly;
        list.clear();
        list.addAll(quests);
        if (series == Series.DAILY) {
            dailyKey = key;
        } else {
            weeklyKey = key;
        }
    }

    /** Remplace une quête d'une série (progression, tâche 3). */
    public void set(Series series, int index, Quest quest) {
        (series == Series.DAILY ? daily : weekly).set(index, quest);
    }

    /** Ajoute une récompense en attente (essais) ; les plus anciennes au-delà de {@code max} sont oubliées. */
    public void addPending(Quest quest, LocalDate seriesKey, int max) {
        addPending(new Pending(quest, seriesKey), max);
    }

    /** Comme {@link #addPending(Quest, LocalDate, int)}, avec la liste d'origine (essais). */
    public void addPending(Pending line, int max) {
        pending.add(line);
        while (pending.size() > Math.max(0, max)) {
            pending.remove(0);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Relance
    // ---------------------------------------------------------------------------------------------

    /** Relances déjà prises comptées pour ce jour : celles du dernier jour de relance, sauf s'il est passé. */
    public int rerollsUsedOn(LocalDate today) {
        return rerollDay != null && !today.isAfter(rerollDay) ? rerollsUsed : 0;
    }

    public boolean canReroll(LocalDate today, int perDay) {
        return rerollsUsedOn(today) < perDay;
    }

    /**
     * Remplace une quête non finie et non réclamée par une autre, tirée hors des modèles de la série (celui qu'elle
     * remplace compris) ; sa progression est perdue. L'appelant a vérifié {@link #canReroll}. Une relance qui ne
     * donne rien n'est pas comptée.
     */
    public Optional<Quest> reroll(Series series, int index, LocalDate today,
            Function<Set<String>, Optional<Quest>> drawer) {
        List<Quest> list = series == Series.DAILY ? daily : weekly;
        if (index < 0 || index >= list.size()) {
            return Optional.empty();
        }
        Quest current = list.get(index);
        if (current.complete() || current.claimed()) {
            return Optional.empty();
        }
        Set<String> excluded = new HashSet<>();
        list.forEach(q -> excluded.add(q.templateId()));
        Optional<Quest> fresh = drawer.apply(Set.copyOf(excluded));
        if (fresh.isEmpty()) {
            return Optional.empty();
        }
        list.set(index, fresh.get());
        if (rerollDay == null || today.isAfter(rerollDay)) {
            rerollDay = today;
            rerollsUsed = 1;
        } else {
            // Même jour, ou horloge reculée : la relance s'ajoute à celles du jour gardé.
            rerollsUsed++;
        }
        return fresh;
    }

    // ---------------------------------------------------------------------------------------------
    // Plafond
    // ---------------------------------------------------------------------------------------------

    /** Ce qui reste à gagner cette semaine ; {@link Long#MAX_VALUE} sans plafond ({@code cap == 0}). */
    public long capRemaining(LocalDate week, long cap) {
        if (cap <= 0L) {
            return Long.MAX_VALUE;
        }
        long paid = capWeek != null && !week.isAfter(capWeek) ? capPaid : 0L;
        return Math.max(0L, cap - paid);
    }

    /** Note un paiement ; une semaine strictement plus récente remet le compteur à zéro. */
    public void recordPaid(LocalDate week, long amount) {
        if (capWeek == null || week.isAfter(capWeek)) {
            capWeek = week;
            capPaid = amount;
        } else {
            capPaid = Math.addExact(capPaid, amount);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Références
    // ---------------------------------------------------------------------------------------------

    /** La quête que désigne cette référence, si elle désigne encore la même (liste, place, modèle, clé). */
    public Optional<Quest> find(QuestRef ref) {
        return switch (ref.kind()) {
            case DAILY -> inSeries(daily, dailyKey, ref);
            case WEEKLY -> inSeries(weekly, weeklyKey, ref);
            case PENDING -> {
                if (ref.index() < 0 || ref.index() >= pending.size()) {
                    yield Optional.empty();
                }
                Pending p = pending.get(ref.index());
                yield p.quest().templateId().equals(ref.templateId()) && p.seriesKey().equals(ref.seriesKey())
                        && p.origin() == ref.origin() && p.eventKey() == ref.eventKey()
                        ? Optional.of(p.quest()) : Optional.empty();
            }
            case EVENT -> events.containsKey(ref.templateId()) && eventStart(ref.templateId()) == ref.eventKey()
                    ? Optional.of(events.get(ref.templateId())) : Optional.empty();
        };
    }

    private static Optional<Quest> inSeries(List<Quest> list, @Nullable LocalDate key, QuestRef ref) {
        if (key == null || !key.equals(ref.seriesKey()) || ref.index() < 0 || ref.index() >= list.size()) {
            return Optional.empty();
        }
        Quest quest = list.get(ref.index());
        return quest.templateId().equals(ref.templateId()) ? Optional.of(quest) : Optional.empty();
    }

    /**
     * Marque réclamée la quête de cette référence ; une récompense en attente réclamée quitte la liste.
     *
     * @return faux si la référence ne désigne plus rien ou si la quête était déjà réclamée
     */
    public boolean markClaimed(QuestRef ref) {
        Optional<Quest> found = find(ref);
        if (found.isEmpty() || found.get().claimed()) {
            return false;
        }
        Quest claimed = found.get().claimedNow();
        switch (ref.kind()) {
            case DAILY -> daily.set(ref.index(), claimed);
            case WEEKLY -> weekly.set(ref.index(), claimed);
            case PENDING -> pending.remove(ref.index());
            case EVENT -> events.put(ref.templateId(), claimed);
        }
        return true;
    }

    /** La référence de la quête à cette place aujourd'hui. */
    public QuestRef ref(QuestRef.Kind kind, int index) {
        return switch (kind) {
            case DAILY -> new QuestRef(kind, index, daily.get(index).templateId(), dailyKey);
            case WEEKLY -> new QuestRef(kind, index, weekly.get(index).templateId(), weeklyKey);
            case PENDING -> new QuestRef(kind, index, pending.get(index).quest().templateId(),
                    pending.get(index).seriesKey(), pending.get(index).origin(), pending.get(index).eventKey());
            case EVENT -> throw new IllegalArgumentException("une quête d'événement se désigne par son identifiant");
        };
    }

    /** La référence de la quête de cet événement ; {@code IllegalArgumentException} si le joueur ne l'a pas. */
    public QuestRef eventRef(String eventId) {
        if (!events.containsKey(eventId)) {
            throw new IllegalArgumentException("pas de quête pour l'événement " + eventId);
        }
        return QuestRef.event(eventId, eventStart(eventId));
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        putDate(tag, "dailyKey", dailyKey);
        tag.put("daily", list(daily));
        putDate(tag, "weeklyKey", weeklyKey);
        tag.put("weekly", list(weekly));
        putDate(tag, "rerollDay", rerollDay);
        tag.putInt("rerollsUsed", rerollsUsed);
        putDate(tag, "capWeek", capWeek);
        tag.putLong("capPaid", capPaid);
        ListTag pendingTag = new ListTag();
        for (Pending p : pending) {
            CompoundTag line = p.quest().save();
            line.putString("seriesKey", p.seriesKey().toString());
            line.putString("origin", p.origin().name());
            line.putLong("eventKey", p.eventKey());
            pendingTag.add(line);
        }
        tag.put("pending", pendingTag);
        CompoundTag eventsTag = new CompoundTag();
        events.forEach((id, quest) -> {
            CompoundTag line = quest.save();
            line.putLong("eventStart", eventStart(id));
            eventsTag.put(id, line);
        });
        tag.put("events", eventsTag);
        ListTag seen = new ListTag();
        eventsSeen.forEach(mark -> seen.add(StringTag.valueOf(mark)));
        tag.put("eventsSeen", seen);
        return tag;
    }

    /** Relit un carnet ; ce qui est illisible (date, catégorie disparue) est écarté, jamais une exception. */
    public static QuestBook load(CompoundTag tag) {
        QuestBook book = new QuestBook();
        book.dailyKey = date(tag, "dailyKey");
        readList(tag.getList("daily", Tag.TAG_COMPOUND), book.daily);
        book.weeklyKey = date(tag, "weeklyKey");
        readList(tag.getList("weekly", Tag.TAG_COMPOUND), book.weekly);
        book.rerollDay = date(tag, "rerollDay");
        book.rerollsUsed = Math.max(0, tag.getInt("rerollsUsed"));
        book.capWeek = date(tag, "capWeek");
        book.capPaid = Math.max(0L, tag.getLong("capPaid"));
        for (Tag raw : tag.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) raw;
            LocalDate key = date(line, "seriesKey");
            Optional<Quest> quest = Quest.load(line);
            if (key != null && quest.isPresent()) {
                book.pending.add(new Pending(quest.get(), key, origin(line.getString("origin")),
                        line.getLong("eventKey")));
            }
        }
        CompoundTag eventsTag = tag.getCompound("events");
        for (String id : eventsTag.getAllKeys()) {
            CompoundTag line = eventsTag.getCompound(id);
            Quest.load(line).ifPresent(q -> {
                book.events.put(id, q);
                book.eventStarts.put(id, line.getLong("eventStart"));
            });
        }
        for (Tag raw : tag.getList("eventsSeen", Tag.TAG_STRING)) {
            book.eventsSeen.add(raw.getAsString());
        }
        return book;
    }

    /** La liste d'origine d'une attente relue ; illisible ou absente (ancien carnet) : journalière. */
    private static QuestRef.Kind origin(String name) {
        return QuestRef.Kind.WEEKLY.name().equals(name) ? QuestRef.Kind.WEEKLY
                : QuestRef.Kind.EVENT.name().equals(name) ? QuestRef.Kind.EVENT : QuestRef.Kind.DAILY;
    }

    private static ListTag list(List<Quest> quests) {
        ListTag out = new ListTag();
        quests.forEach(q -> out.add(q.save()));
        return out;
    }

    private static void readList(ListTag list, List<Quest> into) {
        for (Tag raw : list) {
            Quest.load((CompoundTag) raw).ifPresent(into::add);
        }
    }

    private static void putDate(CompoundTag tag, String key, @Nullable LocalDate date) {
        if (date != null) {
            tag.putString(key, date.toString());
        }
    }

    @Nullable
    private static LocalDate date(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return null;
        }
        try {
            return LocalDate.parse(tag.getString(key));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Le carnet du joueur, lu dans ses données persistantes (un joueur neuf a un carnet vierge). */
    public static QuestBook of(ServerPlayer player) {
        return load(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY));
    }

    /** Écrit le carnet dans les données persistantes du joueur, rien d'autre : le monde l'écrira avec lui. */
    public void saveTo(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(KEY, save());
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
