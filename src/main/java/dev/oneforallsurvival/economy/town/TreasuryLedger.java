package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.economy.EconomyLedger;
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
 * Le registre du trésor : taxe, dépôts, dépenses, injections et retraits de l'équipe, salaires, clôtures de jour,
 * pose et casse du bloc. Il vit avec le solde, dans le même fichier de données du monde ({@code TreasuryAccount}) :
 * les deux ne divergent jamais. Les plus anciennes lignes tombent au-delà du plafond donné à {@link #add}.
 */
public final class TreasuryLedger {

    public enum Kind {
        TAX, DEPOSIT, SPEND, INJECTION, STAFF_TAKE, SALARY_PAID, SALARY_OWED, DAY_CLOSED, PLACED, REMOVED,
        /** Taxe sur l'électricité, retenue sur une facture d'électricité payée (lot E6). */
        POWER_TAX
    }

    /** Lignes par page de {@link #page}. */
    public static final int PAGE_SIZE = 10;

    private static final int MAX_NAME = 32;

    /**
     * @param actor qui a agi, ou nul (taxe, salaire, clôture, action de la console)
     * @param amount le montant en cuivres, 0 quand la ligne n'en porte pas (pose, casse)
     * @param reason le motif, coupé à {@link EconomyLedger#MAX_REASON} caractères
     */
    public record Line(long epochMillis, @Nullable UUID actor, String actorName, Kind kind, long amount, String reason) {
        public Line {
            actorName = clip(actorName, MAX_NAME);
            reason = clip(reason, EconomyLedger.MAX_REASON);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("t", epochMillis);
            if (actor != null) {
                tag.putUUID("who", actor);
            }
            tag.putString("name", actorName);
            tag.putString("kind", kind.name());
            tag.putLong("sum", amount);
            tag.putString("why", reason);
            return tag;
        }

        /** Une ligne relue, ou nulle si son genre est inconnu. */
        @Nullable
        static Line load(CompoundTag tag) {
            Kind kind;
            try {
                kind = Kind.valueOf(tag.getString("kind").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
            return new Line(tag.getLong("t"), tag.hasUUID("who") ? tag.getUUID("who") : null, tag.getString("name"),
                    kind, tag.getLong("sum"), tag.getString("why"));
        }
    }

    private static String clip(@Nullable String text, int max) {
        String value = text == null ? "" : text;
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Du plus ancien au plus récent. */
    private final ArrayDeque<Line> lines = new ArrayDeque<>();

    /** Ajoute une ligne ; les plus anciennes sont effacées d'abord au-delà de {@code maxLines} (au moins 1 gardée). */
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
    public int pageCount() {
        return Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Une page, la plus récente d'abord ; la page 0 est la plus récente, vide au-delà de la dernière. */
    public List<Line> page(int page) {
        List<Line> out = new ArrayList<>(PAGE_SIZE);
        if (page < 0) {
            return out;
        }
        long skip = (long) page * PAGE_SIZE;
        Iterator<Line> newestFirst = lines.descendingIterator();
        for (long i = 0; i < skip && newestFirst.hasNext(); i++) {
            newestFirst.next();
        }
        while (out.size() < PAGE_SIZE && newestFirst.hasNext()) {
            out.add(newestFirst.next());
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

    public static TreasuryLedger load(ListTag list) {
        TreasuryLedger ledger = new TreasuryLedger();
        for (int i = 0; i < list.size(); i++) {
            Line line = Line.load(list.getCompound(i));
            if (line != null) {
                ledger.lines.addLast(line);
            }
        }
        return ledger;
    }
}
