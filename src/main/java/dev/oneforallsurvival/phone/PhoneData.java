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
 * propriétaire, l'empreinte salée du code, les essais ratés, le blocage et son niveau, le fond d'écran, le mode avion,
 * Face ID (retours d'essai du 27/11 ; absent d'une sauvegarde plus ancienne, il se lit coupé). Le code lui-même n'est
 * écrit nulle part. Sauvegardé avec le monde principal ; ne se touche que depuis le fil du serveur.
 */
public final class PhoneData extends SavedData {

    public static final String KEY = "oneforallsurvival_phones";

    /**
     * Un téléphone sans propriétaire est neuf : il n'a pas encore de code.
     *
     * @param lockoutLevel les séries d'essais ratés depuis le dernier bon code ({@link PhoneLock}) ; 0 pour une
     *     sauvegarde qui ne le porte pas
     * @param faceId Face ID actif ({@link FaceId}) ; coupé pour un téléphone neuf, effacé, ou d'une sauvegarde qui ne
     *     le porte pas
     */
    public record PhoneRecord(@Nullable UUID owner, String ownerName, @Nullable String salt,
            @Nullable String codeHash, int failures, long lockedUntil, int lockoutLevel, String wallpaper,
            boolean airplane, boolean faceId) {

        /** Sans Face ID (coupé) : la forme d'avant les retours d'essai du 27/11, gardée pour les essais. */
        public PhoneRecord(@Nullable UUID owner, String ownerName, @Nullable String salt, @Nullable String codeHash,
                int failures, long lockedUntil, int lockoutLevel, String wallpaper, boolean airplane) {
            this(owner, ownerName, salt, codeHash, failures, lockedUntil, lockoutLevel, wallpaper, airplane, false);
        }

        static PhoneRecord fresh() {
            return new PhoneRecord(null, "", null, null, 0, 0L, 0, PhoneWallpapers.DEFAULT, false, false);
        }

        /** Le même téléphone, Face ID mis à {@code on}. */
        PhoneRecord withFaceId(boolean on) {
            return new PhoneRecord(owner, ownerName, salt, codeHash, failures, lockedUntil, lockoutLevel, wallpaper,
                    airplane, on);
        }
    }

    private final Map<UUID, PhoneRecord> phones = new HashMap<>();

    public PhoneData() {}

    public static PhoneData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PhoneData::load, PhoneData::new, KEY);
    }

    /**
     * Le téléphone connu, ou un téléphone neuf (qui n'est pas pour autant enregistré). Un propriétaire sans sel ou sans
     * empreinte est rendu comme « pas de code » : sel et empreinte à {@code null}, jamais un code vide accepté, et Face
     * ID coupé (il ne sert qu'à un téléphone qui a un code).
     */
    public PhoneRecord record(UUID phoneId) {
        PhoneRecord known = phones.get(phoneId);
        if (known == null) {
            return PhoneRecord.fresh();
        }
        if (known.salt() == null || known.codeHash() == null) {
            return new PhoneRecord(known.owner(), known.ownerName(), null, null, known.failures(), known.lockedUntil(),
                    known.lockoutLevel(), known.wallpaper(), known.airplane(), false);
        }
        return known;
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
            line.putInt("lockoutLevel", r.lockoutLevel());
            line.putString("wallpaper", r.wallpaper());
            line.putBoolean("airplane", r.airplane());
            line.putBoolean("faceId", r.faceId());
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
                    Math.max(0, line.getInt("lockoutLevel")),
                    PhoneWallpapers.isValid(wallpaper) ? wallpaper : PhoneWallpapers.DEFAULT,
                    line.getBoolean("airplane"),
                    // Absent d'une sauvegarde d'avant Face ID : getBoolean rend faux, Face ID coupé.
                    line.getBoolean("faceId")));
        }
        return out;
    }
}
