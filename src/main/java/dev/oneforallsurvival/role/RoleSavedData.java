package dev.oneforallsurvival.role;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Sauvegarde du {@link RoleBook} dans le monde principal. */
public final class RoleSavedData extends SavedData {

    public static final String KEY = "oneforallsurvival_roles";

    private final RoleBook book;

    private RoleSavedData(RoleBook book) {
        this.book = book;
    }

    public static RoleSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                tag -> new RoleSavedData(RoleBook.load(tag)),
                () -> new RoleSavedData(RoleBook.withBuiltins()),
                KEY);
    }

    /** Le registre vivant ; l'appelant qui le modifie appelle ensuite {@link #setDirty()}. */
    public RoleBook book() {
        return book;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.merge(book.save());
        return tag;
    }
}
