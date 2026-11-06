package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleTeams;
import java.util.ArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Les équipes de scoreboard qui portent les préfixes de rôle. Le scoreboard appartient au serveur entier :
 * chaque test s'exécute dans son propre lot (donc seul) et retire ce qu'il a créé.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class RoleGameTests {

    private RoleGameTests() {}

    /** Retire les équipes {@code ofas_*} et celles que le test a nommées. */
    private static void nettoyer(Scoreboard scoreboard, String... autres) {
        for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
            if (team.getName().startsWith("ofas_")) {
                scoreboard.removePlayerTeam(team);
            }
        }
        for (String nom : autres) {
            PlayerTeam team = scoreboard.getPlayerTeam(nom);
            if (team != null) {
                scoreboard.removePlayerTeam(team);
            }
        }
    }

    @GameTest(template = "test/plancher", batch = "roles_prefixe")
    public static void syncCreeUneEquipeParRole(GameTestHelper helper) {
        Scoreboard scoreboard = helper.getLevel().getServer().getScoreboard();
        try {
            RoleTeams.syncTeams(scoreboard, RoleBook.withBuiltins());
            PlayerTeam team = scoreboard.getPlayerTeam("ofas_ingenieur");
            helper.assertTrue(team != null, "l'équipe ofas_ingenieur n'existe pas");
            String prefixe = team.getPlayerPrefix().getString();
            helper.assertTrue("[Ingénieur] ".equals(prefixe), "préfixe inattendu : " + prefixe);
            helper.succeed();
        } finally {
            nettoyer(scoreboard);
        }
    }

    @GameTest(template = "test/plancher", batch = "roles_orphelines")
    public static void syncRetireLesEquipesOrphelines(GameTestHelper helper) {
        Scoreboard scoreboard = helper.getLevel().getServer().getScoreboard();
        try {
            scoreboard.addPlayerTeam("ofas_fantome");
            RoleTeams.syncTeams(scoreboard, RoleBook.withBuiltins());
            helper.assertTrue(scoreboard.getPlayerTeam("ofas_fantome") == null,
                    "l'équipe orpheline ofas_fantome existe encore");
            helper.succeed();
        } finally {
            nettoyer(scoreboard, "ofas_fantome");
        }
    }

    @GameTest(template = "test/plancher", batch = "roles_autres")
    public static void syncNeTouchePasAuxAutresEquipes(GameTestHelper helper) {
        Scoreboard scoreboard = helper.getLevel().getServer().getScoreboard();
        try {
            PlayerTeam staff = scoreboard.addPlayerTeam("staff");
            scoreboard.addPlayerToTeam("Moderateur", staff);
            RoleTeams.syncTeams(scoreboard, RoleBook.withBuiltins());
            PlayerTeam apres = scoreboard.getPlayerTeam("staff");
            helper.assertTrue(apres != null, "l'équipe staff a disparu");
            helper.assertTrue(apres.getPlayers().contains("Moderateur"),
                    "l'équipe staff a perdu ses membres");
            helper.succeed();
        } finally {
            nettoyer(scoreboard, "staff");
            scoreboard.removePlayerFromTeam("Moderateur");
        }
    }

    @GameTest(template = "test/plancher", batch = "roles_remplace")
    public static void applyPlayerRemplaceLEquipePrecedente(GameTestHelper helper) {
        Scoreboard scoreboard = helper.getLevel().getServer().getScoreboard();
        RoleBook book = RoleBook.withBuiltins();
        try {
            RoleTeams.syncTeams(scoreboard, book);
            RoleTeams.applyPlayer(scoreboard, "Dev", book.role("ingenieur").orElseThrow());
            RoleTeams.applyPlayer(scoreboard, "Dev", book.role("citoyen").orElseThrow());
            PlayerTeam team = scoreboard.getPlayersTeam("Dev");
            helper.assertTrue(team != null && "ofas_citoyen".equals(team.getName()),
                    "équipe de Dev : " + (team == null ? "aucune" : team.getName()));
            helper.succeed();
        } finally {
            scoreboard.removePlayerFromTeam("Dev");
            nettoyer(scoreboard);
        }
    }
}
