package dev.oneforallsurvival.role;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Branche les équipes de rôle sur le cycle de vie du serveur. */
public final class RoleEvents {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        RoleTeams.syncTeams(server.getScoreboard(), RoleSavedData.get(server).book());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RoleBook book = RoleSavedData.get(player.server).book();
            RoleTeams.applyPlayer(player.server.getScoreboard(), player.getGameProfile().getName(),
                    book.roleOf(player.getUUID()));
        }
    }

    /** Resynchronise les équipes puis réapplique chaque joueur connecté, après un changement de rôle. */
    public static void refreshAll(MinecraftServer server) {
        RoleBook book = RoleSavedData.get(server).book();
        RoleTeams.syncTeams(server.getScoreboard(), book);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RoleTeams.applyPlayer(server.getScoreboard(), player.getGameProfile().getName(),
                    book.roleOf(player.getUUID()));
        }
    }
}
