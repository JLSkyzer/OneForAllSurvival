package dev.oneforallsurvival.economy.quest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToLongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Les dernières poses comptées de chaque joueur, en mémoire seulement (perdues à l'arrêt : elles ne valent que
 * quelques secondes). Un joueur qui casse un bloc qu'il vient de poser retire la pose qu'il avait comptée : un seul bloc
 * posé et cassé en boucle ne fait jamais avancer une quête de pose.
 *
 * <p>Une entrée par quête avancée ({@code countedKey}, voir {@link QuestProgress}) ; au plus {@value #MAX} entrées par
 * joueur, les plus anciennes oubliées.
 */
public final class RecentPlacements {

    public static final int MAX = 256;

    private record Entry(ResourceKey<Level> dimension, long pos, long gameTime, String countedKey) {}

    private final Map<UUID, ArrayDeque<Entry>> byPlayer = new HashMap<>();

    public void record(UUID player, ResourceKey<Level> dimension, BlockPos pos, long gameTime, String countedKey) {
        ArrayDeque<Entry> entries = byPlayer.computeIfAbsent(player, id -> new ArrayDeque<>());
        entries.addLast(new Entry(dimension, pos.asLong(), gameTime, countedKey));
        while (entries.size() > MAX) {
            entries.removeFirst();
        }
    }

    /**
     * Retire la pose la plus récente de ce joueur à cette position et la rend si elle date de moins de
     * {@code windowTicks} ; une pose plus ancienne est retirée sans rien rendre. Plusieurs quêtes avancées par la même
     * pose : un appel par quête.
     */
    public Optional<String> takeIfRecent(UUID player, ResourceKey<Level> dimension, BlockPos pos, long gameTime,
            long windowTicks) {
        ArrayDeque<Entry> entries = byPlayer.get(player);
        if (entries == null) {
            return Optional.empty();
        }
        long packed = pos.asLong();
        for (Iterator<Entry> it = entries.descendingIterator(); it.hasNext();) {
            Entry entry = it.next();
            if (entry.pos() == packed && entry.dimension().equals(dimension)) {
                it.remove();
                if (entries.isEmpty()) {
                    byPlayer.remove(player);
                }
                long age = gameTime - entry.gameTime();
                return age >= 0 && age < windowTicks ? Optional.of(entry.countedKey()) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Une pose retenue rendue par {@link #takeAllAt}. */
    public record Taken(UUID player, String countedKey) {}

    /**
     * Retire toutes les poses retenues à cette position, tous joueurs confondus, et rend celles qui datent de moins de
     * {@code windowTicks} selon l'horloge de chaque joueur ({@code now}). Pour un bloc qui quitte sa case de lui-même
     * (bloc qui tombe) : sa pose n'a pas tenu.
     */
    public List<Taken> takeAllAt(ResourceKey<Level> dimension, BlockPos pos, ToLongFunction<UUID> now,
            long windowTicks) {
        List<Taken> out = new ArrayList<>();
        long packed = pos.asLong();
        for (Iterator<Map.Entry<UUID, ArrayDeque<Entry>>> players = byPlayer.entrySet().iterator(); players.hasNext();) {
            Map.Entry<UUID, ArrayDeque<Entry>> player = players.next();
            long gameTime = now.applyAsLong(player.getKey());
            for (Iterator<Entry> it = player.getValue().iterator(); it.hasNext();) {
                Entry entry = it.next();
                if (entry.pos() == packed && entry.dimension().equals(dimension)) {
                    it.remove();
                    long age = gameTime - entry.gameTime();
                    if (age >= 0 && age < windowTicks) {
                        out.add(new Taken(player.getKey(), entry.countedKey()));
                    }
                }
            }
            if (player.getValue().isEmpty()) {
                players.remove();
            }
        }
        return out;
    }

    /** Oublie les poses de ce joueur plus vieilles que la fenêtre : elles ne retireraient plus rien. */
    public void purge(UUID player, long gameTime, long windowTicks) {
        ArrayDeque<Entry> entries = byPlayer.get(player);
        if (entries == null) {
            return;
        }
        while (!entries.isEmpty() && gameTime - entries.peekFirst().gameTime() >= windowTicks) {
            entries.removeFirst();
        }
        if (entries.isEmpty()) {
            byPlayer.remove(player);
        }
    }

    public int size(UUID player) {
        ArrayDeque<Entry> entries = byPlayer.get(player);
        return entries == null ? 0 : entries.size();
    }

    public void clear() {
        byPlayer.clear();
    }
}
