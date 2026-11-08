package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.grid.MeterLedger.Account;
import dev.oneforallsurvival.grid.MeterLedger.Event;
import dev.oneforallsurvival.grid.MeterLedger.EventKind;
import dev.oneforallsurvival.grid.MeterLedger.Previous;
import dev.oneforallsurvival.grid.MeterLedger.Reading;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Le registre des abonnés, sauvegardé dans le monde principal : pour chaque abonné (nom normalisé), le dernier relevé
 * ou l'index d'inscription de chacun de ses compteurs, et les événements notés depuis (compteur déposé, scellé brisé).
 * Un événement d'un type inconnu (« renamed » d'une version de développement du lot 4) est ignoré à la relecture.
 * La règle vit dans {@link MeterLedger}.
 */
public final class SubscriberSavedData extends SavedData {

    public static final String KEY = "oneforallsurvival_subscribers";

    private static final String TAG_ACCOUNTS = "accounts";
    private static final String TAG_NAME = "name";
    /** Ancien format : un seul dernier relevé par abonné. Relu, plus écrit. */
    private static final String TAG_PREVIOUS = "previous";
    private static final String TAG_METERS = "meters";
    private static final String TAG_BASELINE = "baseline";
    private static final String TAG_NOTIFIED = "notified";
    private static final String TAG_SERIAL = "serial";
    private static final String TAG_INDEX = "index";
    private static final String TAG_DAY = "day";
    private static final String TAG_DATE = "real_date";
    private static final String TAG_EVENTS = "events";
    private static final String TAG_KIND = "kind";

    private final MeterLedger ledger = new MeterLedger();

    /** Instance autonome (essais) ; en jeu, passer par {@link #get(MinecraftServer)}. */
    public SubscriberSavedData() {}

    public static SubscriberSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SubscriberSavedData::load,
                SubscriberSavedData::new, KEY);
    }

    /** Le jour de jeu courant du serveur, à partir de 1. */
    public static long today(MinecraftServer server) {
        return MeterLedger.gameDay(server.overworld().getDayTime());
    }

    /**
     * Note un événement au registre de l'abonné, daté du jour de jeu courant. Pour tout bloc qui laisse une trace au
     * relevé suivant : le compteur déposé, le disjoncteur dont le scellé est brisé.
     */
    public static void note(MinecraftServer server, String subscriber, EventKind kind) {
        note(server, subscriber, kind, null);
    }

    /** Comme {@link #note(MinecraftServer, String, EventKind)}, pour un événement qui concerne le compteur {@code serial}. */
    public static void note(MinecraftServer server, String subscriber, EventKind kind, @Nullable UUID serial) {
        get(server).record(subscriber, kind, today(server), serial);
    }

    @Nullable
    public Previous previous(String subscriber, UUID serial) {
        return ledger.previous(subscriber, serial);
    }

    public List<Event> events(String subscriber) {
        return ledger.events(subscriber);
    }

    public void record(String subscriber, EventKind kind, long day) {
        record(subscriber, kind, day, null);
    }

    public void record(String subscriber, EventKind kind, long day, @Nullable UUID serial) {
        ledger.record(subscriber, kind, day, serial);
        setDirty();
    }

    /** Inscription ou changement d'abonné d'un compteur, avec son relevé de clôture : voir {@link MeterLedger#inscribe}. */
    @Nullable
    public Reading inscribe(String from, String to, UUID serial, long index, long day, String realDate) {
        Reading closing = ledger.inscribe(from, to, serial, index, day, realDate);
        setDirty();
        return closing;
    }

    /** Relève : voir {@link MeterLedger#take}. */
    public Reading take(String subscriber, UUID serial, long index, long day, String realDate) {
        Reading reading = ledger.take(subscriber, serial, index, day, realDate);
        setDirty();
        return reading;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag accounts = new ListTag();
        for (Map.Entry<String, Account> entry : ledger.accounts().entrySet()) {
            CompoundTag account = new CompoundTag();
            account.putString(TAG_NAME, entry.getKey());
            ListTag meters = new ListTag();
            for (Previous previous : entry.getValue().meters().values()) {
                CompoundTag p = new CompoundTag();
                p.putUUID(TAG_SERIAL, previous.serial());
                p.putLong(TAG_INDEX, previous.index());
                p.putLong(TAG_DAY, previous.day());
                p.putString(TAG_DATE, previous.realDate());
                if (previous.baseline()) {
                    p.putBoolean(TAG_BASELINE, true);
                }
                meters.add(p);
            }
            account.put(TAG_METERS, meters);
            ListTag events = new ListTag();
            for (Event event : entry.getValue().events()) {
                CompoundTag e = new CompoundTag();
                e.putString(TAG_KIND, event.kind().name().toLowerCase(Locale.ROOT));
                e.putLong(TAG_DAY, event.day());
                if (event.serial() != null) {
                    e.putUUID(TAG_SERIAL, event.serial());
                }
                if (event.notified()) {
                    e.putBoolean(TAG_NOTIFIED, true);
                }
                events.add(e);
            }
            account.put(TAG_EVENTS, events);
            accounts.add(account);
        }
        tag.put(TAG_ACCOUNTS, accounts);
        return tag;
    }

    public static SubscriberSavedData load(CompoundTag tag) {
        SubscriberSavedData data = new SubscriberSavedData();
        ListTag accounts = tag.getList(TAG_ACCOUNTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < accounts.size(); i++) {
            CompoundTag account = accounts.getCompound(i);
            Map<UUID, Previous> meters = new LinkedHashMap<>();
            // Sauvegarde d'avant le relevé par compteur : un seul « previous », qui devient l'entrée de son compteur.
            if (account.contains(TAG_PREVIOUS, Tag.TAG_COMPOUND)) {
                Previous previous = previousOf(account.getCompound(TAG_PREVIOUS));
                if (previous != null) {
                    meters.put(previous.serial(), previous);
                }
            }
            ListTag meterList = account.getList(TAG_METERS, Tag.TAG_COMPOUND);
            for (int j = 0; j < meterList.size(); j++) {
                Previous previous = previousOf(meterList.getCompound(j));
                if (previous != null) {
                    meters.put(previous.serial(), previous);
                }
            }
            List<Event> events = new ArrayList<>();
            ListTag list = account.getList(TAG_EVENTS, Tag.TAG_COMPOUND);
            for (int j = 0; j < list.size(); j++) {
                CompoundTag e = list.getCompound(j);
                EventKind kind = kindOf(e.getString(TAG_KIND));
                if (kind != null) {
                    events.add(new Event(kind, e.getLong(TAG_DAY), e.hasUUID(TAG_SERIAL) ? e.getUUID(TAG_SERIAL) : null,
                            e.getBoolean(TAG_NOTIFIED)));
                }
            }
            data.ledger.restore(account.getString(TAG_NAME), new MeterLedger.Account(meters, events));
        }
        return data;
    }

    @Nullable
    private static Previous previousOf(CompoundTag p) {
        if (!p.hasUUID(TAG_SERIAL)) {
            return null;
        }
        return new Previous(p.getUUID(TAG_SERIAL), p.getLong(TAG_INDEX), p.getLong(TAG_DAY), p.getString(TAG_DATE),
                p.getBoolean(TAG_BASELINE));
    }

    @Nullable
    private static EventKind kindOf(String name) {
        for (EventKind kind : EventKind.values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return kind;
            }
        }
        return null;
    }
}
