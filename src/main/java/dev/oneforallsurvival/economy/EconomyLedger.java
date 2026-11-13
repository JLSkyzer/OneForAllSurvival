package dev.oneforallsurvival.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Le registre de l'économie, unique pour le serveur (données du monde principal) : le total créé par source, le
 * total détruit par cause, le total transféré par genre, les lignes du journal (les plus anciennes effacées au-delà de
 * {@link EconomyConfig#ledgerMaxLines()}, jamais les totaux) et les joueurs qui ont reçu leur somme de départ.
 * Ne se touche que depuis le fil du serveur.
 */
public final class EconomyLedger extends SavedData {

    public static final String KEY = "oneforallsurvival_economy";
    /** Lignes par page de {@link #entries(int)}. */
    public static final int PAGE_SIZE = 10;
    /** Longueur maximale du motif gardé. */
    public static final int MAX_REASON = 200;

    /**
     * Une ligne du journal. {@code kind} vaut {@link MoneySource#kind()} ou {@link MoneySink#kind()}
     * ({@code created.staff}, {@code destroyed.fire}) ; {@code epochMillis} est l'heure réelle.
     */
    public record Entry(long epochMillis, @Nullable UUID author, long amount, String kind, String reason) {}

    private final long[] created = new long[MoneySource.values().length];
    private final long[] destroyed = new long[MoneySink.values().length];
    /** Transferts entre personnes : ni créés ni détruits, ils ne comptent pas dans {@link #inCirculation()}. */
    private final long[] transferred = new long[MoneyTransfer.values().length];
    /** Du plus ancien au plus récent. */
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final Set<UUID> startingGiven = new HashSet<>();

    EconomyLedger() {}

    public static EconomyLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EconomyLedger::load, EconomyLedger::new, KEY);
    }

    // ---------------------------------------------------------------------------------------------
    // Écriture
    // ---------------------------------------------------------------------------------------------

    /** Compte des pièces créées. Un montant nul ou négatif n'est pas noté. */
    public void created(MoneySource source, long amount, @Nullable UUID author, String reason) {
        created(source, amount, author, reason, EconomyConfig.ledgerMaxLines(), System.currentTimeMillis());
    }

    /** Compte des pièces détruites. Un montant nul ou négatif n'est pas noté. */
    public void destroyed(MoneySink sink, long amount, @Nullable UUID author, String reason) {
        destroyed(sink, amount, author, reason, EconomyConfig.ledgerMaxLines(), System.currentTimeMillis());
    }

    /**
     * Compte un transfert d'argent (entre deux personnes ou vers le trésor) : aucune pièce n'est créée ni détruite.
     * Un montant nul ou négatif n'est pas noté.
     */
    public void transferred(MoneyTransfer type, long amount, @Nullable UUID author, String reason) {
        transferred(type, amount, author, reason, EconomyConfig.ledgerMaxLines(), System.currentTimeMillis());
    }

    void transferred(MoneyTransfer type, long amount, @Nullable UUID author, String reason, int maxLines, long now) {
        if (amount <= 0L) {
            return;
        }
        transferred[type.ordinal()] = Math.addExact(transferred[type.ordinal()], amount);
        add(new Entry(now, author, amount, type.kind(), clip(reason)), maxLines);
    }

    void created(MoneySource source, long amount, @Nullable UUID author, String reason, int maxLines, long now) {
        if (amount <= 0L) {
            return;
        }
        created[source.ordinal()] = Math.addExact(created[source.ordinal()], amount);
        add(new Entry(now, author, amount, source.kind(), clip(reason)), maxLines);
    }

    void destroyed(MoneySink sink, long amount, @Nullable UUID author, String reason, int maxLines, long now) {
        if (amount <= 0L) {
            return;
        }
        destroyed[sink.ordinal()] = Math.addExact(destroyed[sink.ordinal()], amount);
        add(new Entry(now, author, amount, sink.kind(), clip(reason)), maxLines);
    }

    private void add(Entry entry, int maxLines) {
        entries.addLast(entry);
        while (entries.size() > Math.max(1, maxLines)) {
            entries.removeFirst();
        }
        setDirty();
    }

    private static String clip(String reason) {
        String text = reason == null ? "" : reason;
        return text.length() <= MAX_REASON ? text : text.substring(0, MAX_REASON);
    }

    /**
     * Note que ce joueur a reçu sa somme de départ.
     *
     * @return vrai si l'identifiant était inconnu, donc si la somme est due
     */
    public boolean markStartingGiven(UUID player) {
        if (!startingGiven.add(player)) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean hasReceivedStarting(UUID player) {
        return startingGiven.contains(player);
    }

    // ---------------------------------------------------------------------------------------------
    // Lecture
    // ---------------------------------------------------------------------------------------------

    public long totalCreated(MoneySource source) {
        return created[source.ordinal()];
    }

    public long totalDestroyed(MoneySink sink) {
        return destroyed[sink.ordinal()];
    }

    /** Total des transferts de ce genre depuis le début. */
    public long totalTransferred(MoneyTransfer type) {
        return transferred[type.ordinal()];
    }

    /**
     * Créé moins détruit : ce qui existe encore, d'après les comptes. Peut être négatif si des pièces que le
     * registre n'a jamais vues naître (mode créatif, par exemple) ont brûlé.
     */
    public long inCirculation() {
        long total = 0L;
        for (long value : created) {
            total += value;
        }
        for (long value : destroyed) {
            total -= value;
        }
        return total;
    }

    public int entryCount() {
        return entries.size();
    }

    /** Nombre de pages (au moins 1). */
    public int pageCount() {
        return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Une page du journal, de la ligne la plus récente à la plus ancienne ; la page 0 est la plus récente. */
    public List<Entry> entries(int page) {
        List<Entry> out = new ArrayList<>(PAGE_SIZE);
        if (page < 0) {
            return out;
        }
        long skip = (long) page * PAGE_SIZE;
        Iterator<Entry> newestFirst = entries.descendingIterator();
        for (long i = 0; i < skip && newestFirst.hasNext(); i++) {
            newestFirst.next();
        }
        while (out.size() < PAGE_SIZE && newestFirst.hasNext()) {
            out.add(newestFirst.next());
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    /** Les totaux sont gardés par nom de source ou de cause : l'ordre des énumérations peut changer. */
    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag createdTag = new CompoundTag();
        for (MoneySource source : MoneySource.values()) {
            createdTag.putLong(source.name(), created[source.ordinal()]);
        }
        CompoundTag destroyedTag = new CompoundTag();
        for (MoneySink sink : MoneySink.values()) {
            destroyedTag.putLong(sink.name(), destroyed[sink.ordinal()]);
        }
        CompoundTag transferredTag = new CompoundTag();
        for (MoneyTransfer type : MoneyTransfer.values()) {
            transferredTag.putLong(type.name(), transferred[type.ordinal()]);
        }
        tag.put("created", createdTag);
        tag.put("transferred", transferredTag);
        tag.put("destroyed", destroyedTag);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag line = new CompoundTag();
            line.putLong("t", entry.epochMillis());
            if (entry.author() != null) {
                line.putUUID("a", entry.author());
            }
            line.putLong("n", entry.amount());
            line.putString("k", entry.kind());
            line.putString("r", entry.reason());
            list.add(line);
        }
        tag.put("entries", list);
        ListTag started = new ListTag();
        for (UUID uuid : startingGiven) {
            started.add(NbtUtils.createUUID(uuid));
        }
        tag.put("starting", started);
        return tag;
    }

    public static EconomyLedger load(CompoundTag tag) {
        EconomyLedger ledger = new EconomyLedger();
        CompoundTag createdTag = tag.getCompound("created");
        for (MoneySource source : MoneySource.values()) {
            ledger.created[source.ordinal()] = createdTag.getLong(source.name());
        }
        CompoundTag destroyedTag = tag.getCompound("destroyed");
        for (MoneySink sink : MoneySink.values()) {
            ledger.destroyed[sink.ordinal()] = destroyedTag.getLong(sink.name());
        }
        CompoundTag transferredTag = tag.getCompound("transferred");
        for (MoneyTransfer type : MoneyTransfer.values()) {
            ledger.transferred[type.ordinal()] = transferredTag.getLong(type.name());
        }
        for (Tag raw : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) raw;
            ledger.entries.addLast(new Entry(line.getLong("t"), line.hasUUID("a") ? line.getUUID("a") : null,
                    line.getLong("n"), line.getString("k").toLowerCase(Locale.ROOT), line.getString("r")));
        }
        for (Tag raw : tag.getList("starting", Tag.TAG_INT_ARRAY)) {
            ledger.startingGiven.add(NbtUtils.loadUUID(raw));
        }
        return ledger;
    }
}
