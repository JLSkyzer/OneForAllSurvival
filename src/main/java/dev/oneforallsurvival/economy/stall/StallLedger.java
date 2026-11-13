package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.EconomyConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Le registre d'un étal : les dernières opérations (ventes, rachats, objets et pièces posés ou retirés, gestes de
 * l'équipe), gardées dans le bloc. Les plus anciennes tombent au-delà de {@link EconomyConfig#stallLedgerLines()}.
 *
 * <p>Une ligne de l'équipe sur l'étal d'un autre ({@link Kind#STAFF}) porte des quantités signées : négatives pour
 * ce qu'elle retire, positives pour ce qu'elle pose. Les autres genres portent des quantités positives.
 */
public final class StallLedger {

    public enum Kind { SALE, BUYBACK, GOODS_OUT, GOODS_IN, CASH_OUT, CASH_IN, STAFF }

    /** Lignes par page de {@link #page}. */
    public static final int PAGE_SIZE = 20;

    private static final int MAX_NAME = 32;
    private static final int MAX_ITEM = 128;

    /**
     * @param actor qui a agi (l'acheteur, le vendeur, le propriétaire, l'équipe)
     * @param itemId l'identifiant de l'objet, ou vide pour de l'argent
     * @param count le nombre d'objets, 0 pour de l'argent
     * @param amount le montant en cuivres, 0 pour des objets seuls
     */
    public record Line(long epochMillis, UUID actor, String actorName, Kind kind, String itemId, int count, long amount) {
        public Line {
            actorName = clip(actorName, MAX_NAME);
            itemId = clip(itemId, MAX_ITEM);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("t", epochMillis);
            tag.putUUID("who", actor);
            tag.putString("name", actorName);
            tag.putString("kind", kind.name());
            tag.putString("item", itemId);
            tag.putInt("n", count);
            tag.putLong("sum", amount);
            return tag;
        }

        /** Une ligne relue, ou nul si elle est abîmée (sans acteur ni genre connu). */
        @Nullable
        static Line load(CompoundTag tag) {
            if (!tag.hasUUID("who")) {
                return null;
            }
            Kind kind;
            try {
                kind = Kind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
            return new Line(tag.getLong("t"), tag.getUUID("who"), tag.getString("name"), kind, tag.getString("item"),
                    tag.getInt("n"), tag.getLong("sum"));
        }
    }

    private static String clip(String text, int max) {
        String value = text == null ? "" : text;
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Du plus ancien au plus récent. */
    private final ArrayDeque<Line> lines = new ArrayDeque<>();

    public void add(Line line) {
        add(line, EconomyConfig.stallLedgerLines());
    }

    public void add(Line line, int maxLines) {
        lines.addLast(line);
        while (lines.size() > Math.max(1, maxLines)) {
            lines.removeFirst();
        }
    }

    public int size() {
        return lines.size();
    }

    /** Le nombre de pages (au moins une, même vide). */
    public int pages() {
        return Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Une page, la plus récente d'abord ({@code page} 0) ; vide au-delà de la dernière. */
    public List<Line> page(int page) {
        List<Line> out = new ArrayList<>();
        if (page < 0) {
            return out;
        }
        long skip = (long) page * PAGE_SIZE;
        Iterator<Line> newestFirst = lines.descendingIterator();
        long index = 0;
        while (newestFirst.hasNext() && out.size() < PAGE_SIZE) {
            Line line = newestFirst.next();
            if (index++ >= skip) {
                out.add(line);
            }
        }
        return out;
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Line line : lines) {
            list.add(line.save());
        }
        return list;
    }

    public void load(ListTag list) {
        lines.clear();
        for (int i = 0; i < list.size(); i++) {
            Line line = Line.load(list.getCompound(i));
            if (line != null) {
                lines.addLast(line);
            }
        }
    }
}
