package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.grid.MeterLedger.Account;
import dev.oneforallsurvival.grid.MeterLedger.Event;
import dev.oneforallsurvival.grid.MeterLedger.EventKind;
import dev.oneforallsurvival.grid.MeterLedger.Previous;
import dev.oneforallsurvival.grid.MeterLedger.Reading;
import java.util.ArrayList;
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
 * Le registre des abonnés, sauvegardé dans le monde principal : pour chaque abonné (nom normalisé), son dernier relevé
 * et les événements notés depuis (compteur déposé, scellé brisé). La règle vit dans {@link MeterLedger}.
 */
public final class SubscriberSavedData extends SavedData {

    public static final String KEY = "oneforallsurvival_subscribers";

    private static final String TAG_ACCOUNTS = "accounts";
    private static final String TAG_NAME = "name";
    private static final String TAG_PREVIOUS = "previous";
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
        get(server).record(subscriber, kind, today(server));
    }

    @Nullable
    public Previous previous(String subscriber) {
        return ledger.previous(subscriber);
    }

    public List<Event> events(String subscriber) {
        return ledger.events(subscriber);
    }

    public void record(String subscriber, EventKind kind, long day) {
        ledger.record(subscriber, kind, day);
        setDirty();
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
            Previous previous = entry.getValue().previous();
            if (previous != null) {
                CompoundTag p = new CompoundTag();
                p.putUUID(TAG_SERIAL, previous.serial());
                p.putLong(TAG_INDEX, previous.index());
                p.putLong(TAG_DAY, previous.day());
                p.putString(TAG_DATE, previous.realDate());
                account.put(TAG_PREVIOUS, p);
            }
            ListTag events = new ListTag();
            for (Event event : entry.getValue().events()) {
                CompoundTag e = new CompoundTag();
                e.putString(TAG_KIND, event.kind().name().toLowerCase(Locale.ROOT));
                e.putLong(TAG_DAY, event.day());
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
            Previous previous = null;
            if (account.contains(TAG_PREVIOUS, Tag.TAG_COMPOUND)) {
                CompoundTag p = account.getCompound(TAG_PREVIOUS);
                if (p.hasUUID(TAG_SERIAL)) {
                    previous = new Previous(p.getUUID(TAG_SERIAL), p.getLong(TAG_INDEX), p.getLong(TAG_DAY),
                            p.getString(TAG_DATE));
                }
            }
            List<Event> events = new ArrayList<>();
            ListTag list = account.getList(TAG_EVENTS, Tag.TAG_COMPOUND);
            for (int j = 0; j < list.size(); j++) {
                CompoundTag e = list.getCompound(j);
                EventKind kind = kindOf(e.getString(TAG_KIND));
                if (kind != null) {
                    events.add(new Event(kind, e.getLong(TAG_DAY)));
                }
            }
            data.ledger.restore(account.getString(TAG_NAME), new MeterLedger.Account(previous, events));
        }
        return data;
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
