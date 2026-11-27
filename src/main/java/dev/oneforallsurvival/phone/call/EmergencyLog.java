package dev.oneforallsurvival.phone.call;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Le journal des appels d'urgence (feature 95), pour repérer les abus : borné à {@value #MAX} entrées, les plus
 * anciennes oubliées d'abord. Pur ; {@link EmergencyLogData} le sauvegarde dans le monde principal.
 */
public final class EmergencyLog {

    public static final int MAX = 200;
    /** Ce que {@code /phone urgences} montre. */
    public static final int SHOWN = 20;
    private static final int MAX_TEXT = 64;

    /**
     * Un appel d'urgence : quand ({@code EconomyClock}), qui, quel service, d'où, et qui a répondu (vide : personne).
     */
    public record Entry(long seq, long atMillis, UUID player, String playerName, String service, String dimension,
            int x, int y, int z, String answeredBy) {

        public Entry {
            Objects.requireNonNull(player, "player");
            playerName = bound(playerName);
            service = bound(service);
            dimension = bound(dimension);
            answeredBy = bound(answeredBy);
        }

        Entry answered(String name) {
            return new Entry(seq, atMillis, player, playerName, service, dimension, x, y, z, name);
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private long nextSeq = 1;

    static String bound(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

    /** Ajoute un appel (sans répondant) et rend son numéro de suite. */
    public long add(long atMillis, UUID player, String playerName, String service, String dimension, int x, int y,
            int z) {
        Entry e = new Entry(nextSeq++, atMillis, player, playerName, service, dimension, x, y, z, "");
        entries.addLast(e);
        while (entries.size() > MAX) {
            entries.removeFirst();
        }
        return e.seq();
    }

    /** Note qui a répondu à l'appel {@code seq} ; faux s'il n'est plus au journal. */
    public boolean answered(long seq, String name) {
        List<Entry> copy = new ArrayList<>(entries);
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).seq() == seq) {
                copy.set(i, copy.get(i).answered(name));
                entries.clear();
                entries.addAll(copy);
                return true;
            }
        }
        return false;
    }

    /** Les {@code n} derniers appels, du plus récent au plus ancien. */
    public List<Entry> latest(int n) {
        List<Entry> out = new ArrayList<>();
        Iterator<Entry> it = entries.descendingIterator();
        while (it.hasNext() && out.size() < n) {
            out.add(it.next());
        }
        return out;
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    /** Relecture : les entrées dans l'ordre, au plus {@value #MAX} (les dernières). */
    public void restore(List<Entry> saved, long next) {
        entries.clear();
        int skip = Math.max(0, saved.size() - MAX);
        for (int i = skip; i < saved.size(); i++) {
            entries.addLast(saved.get(i));
        }
        long highest = 0;
        for (Entry e : entries) {
            highest = Math.max(highest, e.seq());
        }
        nextSeq = Math.max(next, highest + 1);
    }

    public long nextSeq() {
        return nextSeq;
    }
}
