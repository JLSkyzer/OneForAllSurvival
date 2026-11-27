package dev.oneforallsurvival.phone.call;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Sauvegarde du {@link EmergencyLog} dans le monde principal ({@value #KEY}). */
public final class EmergencyLogData extends SavedData {

    public static final String KEY = "oneforallsurvival_emergencies";

    private final EmergencyLog log = new EmergencyLog();

    private EmergencyLogData() {}

    public static EmergencyLogData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EmergencyLogData::load, EmergencyLogData::new, KEY);
    }

    /** Le journal vivant ; l'appelant qui l'écrit appelle ensuite {@link #setDirty()}. */
    public EmergencyLog log() {
        return log;
    }

    private static EmergencyLogData load(CompoundTag tag) {
        EmergencyLogData data = new EmergencyLogData();
        List<EmergencyLog.Entry> entries = new ArrayList<>();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (!e.hasUUID("player")) {
                continue;
            }
            entries.add(new EmergencyLog.Entry(e.getLong("seq"), e.getLong("at"), e.getUUID("player"),
                    e.getString("name"), e.getString("service"), e.getString("dim"), e.getInt("x"), e.getInt("y"),
                    e.getInt("z"), e.getString("answered")));
        }
        data.log.restore(entries, tag.getLong("next"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (EmergencyLog.Entry entry : log.all()) {
            CompoundTag e = new CompoundTag();
            e.putLong("seq", entry.seq());
            e.putLong("at", entry.atMillis());
            e.putUUID("player", entry.player());
            e.putString("name", entry.playerName());
            e.putString("service", entry.service());
            e.putString("dim", entry.dimension());
            e.putInt("x", entry.x());
            e.putInt("y", entry.y());
            e.putInt("z", entry.z());
            e.putString("answered", entry.answeredBy());
            list.add(e);
        }
        tag.put("entries", list);
        tag.putLong("next", log.nextSeq());
        return tag;
    }
}
