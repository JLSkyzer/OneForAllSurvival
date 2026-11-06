package dev.oneforallsurvival.role;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Une équipe de scoreboard par rôle : le préfixe d'équipe affiche le rôle devant le pseudo. */
public final class RoleTeams {

    public static final String PREFIX = "ofas_";

    private RoleTeams() {
    }

    public static String teamName(String roleId) {
        return PREFIX + roleId;
    }

    /** Crée ou met à jour l'équipe de chaque rôle et retire les équipes {@code ofas_} sans rôle. */
    public static void syncTeams(Scoreboard scoreboard, RoleBook book) {
        Set<String> wanted = new HashSet<>();
        for (Role role : book.roles()) {
            String name = teamName(role.id());
            wanted.add(name);
            PlayerTeam team = scoreboard.getPlayerTeam(name);
            if (team == null) {
                team = scoreboard.addPlayerTeam(name);
            }
            team.setPlayerPrefix(Component.literal("[" + role.label() + "] ").withStyle(role.color()));
        }
        for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
            if (team.getName().startsWith(PREFIX) && !wanted.contains(team.getName())) {
                scoreboard.removePlayerTeam(team); // retire aussi ses membres
            }
        }
    }

    /** Place le joueur dans l'équipe de son rôle ; une équipe précédente, quelle qu'elle soit, est quittée. */
    public static void applyPlayer(Scoreboard scoreboard, String playerName, Role role) {
        PlayerTeam team = scoreboard.getPlayerTeam(teamName(role.id()));
        if (team == null) {
            return; // syncTeams n'est pas encore passé
        }
        if (scoreboard.getPlayersTeam(playerName) != team) {
            scoreboard.addPlayerToTeam(playerName, team); // quitte l'ancienne équipe
        }
    }
}
