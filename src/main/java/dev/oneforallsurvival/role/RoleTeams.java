package dev.oneforallsurvival.role;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Une équipe de scoreboard par rôle : le préfixe d'équipe affiche le rôle devant le pseudo, dans le
 * chat comme dans la liste des joueurs.
 *
 * <p>Un joueur n'appartient qu'à une équipe. Le maire quitte donc l'équipe de son métier pour une équipe
 * à lui seul, dont le préfixe porte les deux : {@code [Maire] [Ingénieur] }.
 */
public final class RoleTeams {

    public static final String PREFIX = "ofas_";
    /**
     * Équipe du maire. Le point est interdit dans un identifiant de rôle : aucun rôle créé ne peut
     * prendre ce nom, et le balayage des équipes {@code ofas_} orphelines ne la voit pas.
     */
    public static final String MAYOR_TEAM = "ofas.maire";
    public static final String MAYOR_LABEL = "Maire";
    public static final ChatFormatting MAYOR_COLOR = ChatFormatting.LIGHT_PURPLE;

    private RoleTeams() {
    }

    public static String teamName(String roleId) {
        return PREFIX + roleId;
    }

    private static MutableComponent tag(String label, ChatFormatting color) {
        return Component.literal("[" + label + "] ").withStyle(color);
    }

    /** Préfixe du maire : son statut, puis son métier dans la couleur du métier. */
    public static Component mayorPrefix(Role job) {
        return tag(MAYOR_LABEL, MAYOR_COLOR).append(tag(job.label(), job.color()));
    }

    /**
     * Crée ou met à jour l'équipe de chaque rôle et retire les équipes {@code ofas_} sans rôle. L'équipe du
     * maire existe seulement s'il y a un maire ; elle est vidée à chaque passage, et {@link #applyPlayer}
     * y replace le maire connecté : un ancien maire hors ligne n'y reste donc pas.
     */
    public static void syncTeams(Scoreboard scoreboard, RoleBook book) {
        Set<String> wanted = new HashSet<>();
        for (Role role : book.roles()) {
            String name = teamName(role.id());
            wanted.add(name);
            PlayerTeam team = scoreboard.getPlayerTeam(name);
            if (team == null) {
                team = scoreboard.addPlayerTeam(name);
            }
            team.setPlayerPrefix(tag(role.label(), role.color()));
        }
        for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
            if (team.getName().startsWith(PREFIX) && !wanted.contains(team.getName())) {
                scoreboard.removePlayerTeam(team); // retire aussi ses membres
            }
        }
        syncMayorTeam(scoreboard, book);
    }

    private static void syncMayorTeam(Scoreboard scoreboard, RoleBook book) {
        PlayerTeam team = scoreboard.getPlayerTeam(MAYOR_TEAM);
        Optional<UUID> mayor = book.mayor();
        if (mayor.isEmpty()) {
            if (team != null) {
                scoreboard.removePlayerTeam(team); // le préfixe disparaît avec l'équipe
            }
            return;
        }
        if (team == null) {
            team = scoreboard.addPlayerTeam(MAYOR_TEAM);
        }
        team.setPlayerPrefix(mayorPrefix(book.roleOf(mayor.get())));
        for (String member : new ArrayList<>(team.getPlayers())) {
            scoreboard.removePlayerFromTeam(member, team);
        }
    }

    /** Comme {@link #applyPlayer(Scoreboard, String, Role, boolean)} pour un joueur qui n'est pas maire. */
    public static void applyPlayer(Scoreboard scoreboard, String playerName, Role role) {
        applyPlayer(scoreboard, playerName, role, false);
    }

    /**
     * Place le joueur dans l'équipe du maire s'il l'est, sinon dans celle de son rôle ; une équipe
     * précédente, quelle qu'elle soit, est quittée.
     */
    public static void applyPlayer(Scoreboard scoreboard, String playerName, Role role, boolean mayor) {
        PlayerTeam team = scoreboard.getPlayerTeam(mayor ? MAYOR_TEAM : teamName(role.id()));
        if (team == null) {
            return; // syncTeams n'est pas encore passé
        }
        if (scoreboard.getPlayersTeam(playerName) != team) {
            scoreboard.addPlayerToTeam(playerName, team); // quitte l'ancienne équipe
        }
    }

    /** Applique un joueur d'après le registre : son rôle, et son statut de maire. */
    public static void applyPlayer(Scoreboard scoreboard, String playerName, UUID player, RoleBook book) {
        applyPlayer(scoreboard, playerName, book.roleOf(player), book.isMayor(player));
    }
}
