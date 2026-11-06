package dev.oneforallsurvival.coast;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Sauvegarde des {@link CoastZones} dans le monde principal. */
public final class CoastSavedData extends SavedData {

    public static final String KEY = "oneforallsurvival_coast";

    private final CoastZones zones;

    private CoastSavedData(CoastZones zones) {
        this.zones = zones;
    }

    public static CoastSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                tag -> new CoastSavedData(CoastZones.load(tag)),
                () -> new CoastSavedData(new CoastZones()),
                KEY);
    }

    /** Pour lire ; pour modifier, passer par {@link #add} et {@link #remove}, qui sauvent. */
    public CoastZones zones() {
        return zones;
    }

    public boolean add(String id, ResourceLocation dimension, BlockPos a, BlockPos b) {
        boolean added = zones.add(id, dimension, a, b);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean remove(String id) {
        boolean removed = zones.remove(id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.merge(zones.save());
        return tag;
    }
}
