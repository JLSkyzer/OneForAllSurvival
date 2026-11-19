package dev.oneforallsurvival.phone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Ce que le serveur sait de chaque téléphone, par identifiant de téléphone ({@code phone_id} de la pile) : le
 * propriétaire, l'empreinte salée du code, les essais ratés, le blocage, le fond d'écran, le mode avion. Le code
 * lui-même n'est écrit nulle part. Sauvegardé avec le monde principal ; ne se touche que depuis le fil du serveur.
 */
public final class PhoneData extends SavedData {

    public static final String KEY = "oneforallsurvival_phones";

    /** Un téléphone sans propriétaire est neuf : il n'a pas encore de code. */
    public record PhoneRecord(@Nullable UUID owner, String ownerName, @Nullable String salt,
            @Nullable String codeHash, int failures, long lockedUntil, String wallpaper, boolean airplane) {

        static PhoneRecord fresh() {
            return new PhoneRecord(null, "", null, null, 0, 0L, PhoneWallpapers.DEFAULT, false);
        }
    }

    private final Map<UUID, PhoneRecord> phones = new HashMap<>();

    public PhoneData() {}

    public static PhoneData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PhoneData::load, PhoneData::new, KEY);
    }

    /** Le téléphone connu, ou un téléphone neuf (qui n'est pas pour autant enregistré). */
    public PhoneRecord record(UUID phoneId) {
        PhoneRecord known = phones.get(phoneId);
        return known != null ? known : PhoneRecord.fresh();
    }

    public void put(UUID phoneId, PhoneRecord record) {
        phones.put(phoneId, record);
        setDirty();
    }

    public void erase(UUID phoneId) {
        if (phones.remove(phoneId) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PhoneRecord> entry : phones.entrySet()) {
            PhoneRecord r = entry.getValue();
            CompoundTag line = new CompoundTag();
            line.putUUID("id", entry.getKey());
            if (r.owner() != null) {
                line.putUUID("owner", r.owner());
            }
            line.putString("ownerName", r.ownerName());
            if (r.salt() != null) {
                line.putString("salt", r.salt());
            }
            if (r.codeHash() != null) {
                line.putString("hash", r.codeHash());
            }
            line.putInt("failures", r.failures());
            line.putLong("lockedUntil", r.lockedUntil());
            line.putString("wallpaper", r.wallpaper());
            line.putBoolean("airplane", r.airplane());
            list.add(line);
        }
        tag.put("phones", list);
        return tag;
    }

    public static PhoneData load(CompoundTag tag) {
        PhoneData out = new PhoneData();
        for (Tag raw : tag.getList("phones", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) raw;
            if (!line.hasUUID("id")) {
                continue;
            }
            String wallpaper = line.getString("wallpaper");
            out.phones.put(line.getUUID("id"), new PhoneRecord(
                    line.hasUUID("owner") ? line.getUUID("owner") : null,
                    line.getString("ownerName"),
                    line.contains("salt", Tag.TAG_STRING) ? line.getString("salt") : null,
                    line.contains("hash", Tag.TAG_STRING) ? line.getString("hash") : null,
                    Math.max(0, line.getInt("failures")),
                    Math.max(0L, line.getLong("lockedUntil")),
                    PhoneWallpapers.isValid(wallpaper) ? wallpaper : PhoneWallpapers.DEFAULT,
                    line.getBoolean("airplane")));
        }
        return out;
    }
}
