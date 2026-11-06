package dev.oneforallsurvival.coast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Les zones côtières déclarées à la main : des boîtes, par dimension. Pur, sans monde : la sauvegarde
 * est faite par {@link CoastSavedData}. Dans une dimension qui a des zones, seules elles comptent.
 */
public final class CoastZones {

    /** Une boîte, bornes incluses ; {@code min} et {@code max} sont déjà ordonnés. */
    public record Zone(String id, ResourceLocation dimension, BlockPos min, BlockPos max) {

        boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    private static final String TAG_ZONES = "zones";
    private static final String TAG_ID = "id";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_MIN = "min";
    private static final String TAG_MAX = "max";

    private final List<Zone> zones = new ArrayList<>();
    private int version;

    /** Ajoute une zone ; les coins peuvent être donnés dans n'importe quel ordre. Refuse un id déjà pris. */
    public boolean add(String id, ResourceLocation dimension, BlockPos a, BlockPos b) {
        if (zones.stream().anyMatch(z -> z.id().equals(id))) {
            return false;
        }
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        zones.add(new Zone(id, dimension, min, max));
        version++;
        return true;
    }

    public boolean remove(String id) {
        boolean removed = zones.removeIf(z -> z.id().equals(id));
        if (removed) {
            version++;
        }
        return removed;
    }

    public boolean hasZones(ResourceLocation dimension) {
        return zones.stream().anyMatch(z -> z.dimension().equals(dimension));
    }

    public boolean contains(ResourceLocation dimension, BlockPos pos) {
        return zones.stream().anyMatch(z -> z.dimension().equals(dimension) && z.contains(pos));
    }

    public List<Zone> list() {
        return Collections.unmodifiableList(zones);
    }

    /** Change à chaque ajout ou retrait réussi : sert à invalider les caches. */
    public int version() {
        return version;
    }

    public CompoundTag save() {
        ListTag list = new ListTag();
        for (Zone zone : zones) {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_ID, zone.id());
            tag.putString(TAG_DIMENSION, zone.dimension().toString());
            tag.putIntArray(TAG_MIN, new int[] {zone.min().getX(), zone.min().getY(), zone.min().getZ()});
            tag.putIntArray(TAG_MAX, new int[] {zone.max().getX(), zone.max().getY(), zone.max().getZ()});
            list.add(tag);
        }
        CompoundTag out = new CompoundTag();
        out.put(TAG_ZONES, list);
        return out;
    }

    public static CoastZones load(CompoundTag tag) {
        CoastZones loaded = new CoastZones();
        for (Tag raw : tag.getList(TAG_ZONES, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            int[] min = entry.getIntArray(TAG_MIN);
            int[] max = entry.getIntArray(TAG_MAX);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString(TAG_DIMENSION));
            if (dimension == null || min.length != 3 || max.length != 3) {
                continue; // entrée abîmée : on l'ignore plutôt que de perdre toutes les zones
            }
            loaded.add(entry.getString(TAG_ID), dimension, new BlockPos(min[0], min[1], min[2]),
                    new BlockPos(max[0], max[1], max[2]));
        }
        return loaded;
    }
}
