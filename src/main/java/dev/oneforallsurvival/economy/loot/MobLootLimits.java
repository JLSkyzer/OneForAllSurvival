package dev.oneforallsurvival.economy.loot;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Les bornes du butin des mobs, écrites avec le monde (aucune écriture forcée) : ce qu'a déjà payé chaque raid, tous
 * joueurs confondus ({@code raidLootCap}), et combien de fois chaque joueur a touché le butin de chaque type de boss
 * dans le jour de paie en cours ({@code bossLootPerDay}). Ne se touche que depuis le fil du serveur.
 */
public final class MobLootLimits extends SavedData {

    public static final String KEY = "oneforallsurvival_mob_loot_limits";

    /** Un compte de butins de boss, valable pour un seul jour de paie. */
    private record BossDay(LocalDate day, int count) {}

    /** Cuivres déjà payés par raid ({@code <dimension>#<identifiant>}). Les identifiants de raid ne reviennent pas. */
    private final Map<String, Long> raidPaid = new HashMap<>();
    /** Par joueur, par type de boss : le jour de paie et le nombre de butins touchés ce jour-là. */
    private final Map<UUID, Map<String, BossDay>> bosses = new HashMap<>();

    public MobLootLimits() {}

    public static MobLootLimits get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MobLootLimits::load, MobLootLimits::new, KEY);
    }

    // ---------------------------------------------------------------------------------------------
    // Raids
    // ---------------------------------------------------------------------------------------------

    /** Ce que ce raid peut encore payer sous le plafond, jamais négatif. */
    public long raidRemaining(String raid, long cap) {
        return Math.max(0L, cap - raidPaid.getOrDefault(raid, 0L));
    }

    public void addRaid(String raid, long amount) {
        if (amount <= 0L) {
            return;
        }
        raidPaid.merge(raid, amount, Math::addExact);
        setDirty();
    }

    // ---------------------------------------------------------------------------------------------
    // Boss
    // ---------------------------------------------------------------------------------------------

    /** Butins de ce type de boss touchés par ce joueur ce jour de paie. */
    public int bossCount(UUID player, String bossType, LocalDate day) {
        BossDay seen = bosses.getOrDefault(player, Map.of()).get(bossType);
        return seen != null && seen.day().equals(day) ? seen.count() : 0;
    }

    /** Vrai si ce joueur peut encore toucher le butin de ce type de boss ce jour de paie ({@code perDay} 0 : jamais). */
    public boolean bossAllowed(UUID player, String bossType, LocalDate day, int perDay) {
        return bossCount(player, bossType, day) < perDay;
    }

    /** Compte un butin de boss ; les jours passés de ce joueur sont oubliés. */
    public void addBoss(UUID player, String bossType, LocalDate day) {
        Map<String, BossDay> mine = bosses.computeIfAbsent(player, p -> new HashMap<>());
        mine.values().removeIf(seen -> seen.day().isBefore(day));
        BossDay seen = mine.get(bossType);
        int count = seen != null && seen.day().equals(day) ? seen.count() + 1 : 1;
        mine.put(bossType, new BossDay(day, count));
        setDirty();
    }

    /** Nombre de comptes de boss gardés, tous joueurs confondus (essais). */
    int bossEntries() {
        return bosses.values().stream().mapToInt(Map::size).sum();
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag raids = new CompoundTag();
        raidPaid.forEach(raids::putLong);
        tag.put("raids", raids);
        ListTag list = new ListTag();
        bosses.forEach((player, types) -> types.forEach((type, seen) -> {
            CompoundTag line = new CompoundTag();
            line.putUUID("u", player);
            line.putString("t", type);
            line.putString("d", seen.day().toString());
            line.putInt("n", seen.count());
            list.add(line);
        }));
        tag.put("bosses", list);
        return tag;
    }

    public static MobLootLimits load(CompoundTag tag) {
        MobLootLimits limits = new MobLootLimits();
        CompoundTag raids = tag.getCompound("raids");
        for (String raid : raids.getAllKeys()) {
            limits.raidPaid.put(raid, raids.getLong(raid));
        }
        for (Tag raw : tag.getList("bosses", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) raw;
            if (!line.hasUUID("u")) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(line.getString("d"));
                limits.bosses.computeIfAbsent(line.getUUID("u"), p -> new HashMap<>())
                        .put(line.getString("t"), new BossDay(day, line.getInt("n")));
            } catch (DateTimeParseException e) {
                // Une ligne illisible est oubliée : au pire, ce joueur touche ce boss une fois de plus aujourd'hui.
            }
        }
        return limits;
    }
}
