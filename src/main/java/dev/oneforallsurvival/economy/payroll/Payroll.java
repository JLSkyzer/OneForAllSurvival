package dev.oneforallsurvival.economy.payroll;

import com.mojang.logging.LogUtils;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 * paie, joueur et clé de salaire, et le dernier jour réglé. Tout vit dans ce seul fichier, écrit par le jeu avec le
 * reste du monde (aucune écriture forcée) : le temps du jour en cours survit à une relance comme le reste.
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

    /** Par jour, par joueur. */
    private final Map<LocalDate, Map<UUID, PlayerDay>> days = new TreeMap<>();
    @Nullable
    private LocalDate lastClosed;

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

    public void setLastClosed(LocalDate day) {
        if (!day.equals(lastClosed)) {
            lastClosed = day;
            setDirty();
        }
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
