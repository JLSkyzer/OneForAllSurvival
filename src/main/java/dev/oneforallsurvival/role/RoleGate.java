package dev.oneforallsurvival.role;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Réserve à l'Ingénieur : les crafts et usages d'ingénierie passent par ces tests. */
public final class RoleGate {

    public static final String ENGINEER_ONLY_KEY = "message.oneforallsurvival.engineer_only";

    private RoleGate() {
    }

    public static boolean isEngineer(RoleBook book, UUID player) {
        return RoleBook.ENGINEER_ROLE_ID.equals(book.roleOf(player).id());
    }

    /** Ingénieur, ou opérateur (niveau 2) qui garde la main pour dépanner. */
    public static boolean canDoEngineerWork(ServerPlayer player) {
        return player.hasPermissions(2)
                || isEngineer(RoleSavedData.get(player.server).book(), player.getUUID());
    }

    /** Comme {@link #canDoEngineerWork} ; prévient le joueur en cas de refus. */
    public static boolean requireEngineer(ServerPlayer player) {
        if (canDoEngineerWork(player)) {
            return true;
        }
        player.sendSystemMessage(Component.translatable(ENGINEER_ONLY_KEY));
        return false;
    }
}
