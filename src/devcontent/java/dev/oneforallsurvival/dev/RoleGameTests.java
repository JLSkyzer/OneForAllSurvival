package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleGate;
import dev.oneforallsurvival.role.RoleSavedData;
import dev.oneforallsurvival.role.RoleTeams;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
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

    private static void commande(MinecraftServer server, String texte) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), texte);
    }

    /** Retire un rôle créé par un test, qu'il ait été supprimé ou non, et resynchronise les équipes. */
    private static void retirerRole(MinecraftServer server, String id) {
        RoleSavedData data = RoleSavedData.get(server);
        if (data.book().role(id).isPresent() && !data.book().role(id).get().builtin()) {
            data.book().deleteRole(id);
            data.setDirty();
        }
        RoleTeams.syncTeams(server.getScoreboard(), data.book());
    }

    @GameTest(template = "test/plancher", batch = "roles_commande_cycle")
    public static void createPuisDeleteMetAJourLeLivreEtLesEquipes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Scoreboard scoreboard = server.getScoreboard();
        try {
            commande(server, "role create pecheur blue Pêcheur");
            RoleBook book = RoleSavedData.get(server).book();
            helper.assertTrue(book.role("pecheur").isPresent(), "le rôle pecheur n'a pas été créé");
            PlayerTeam team = scoreboard.getPlayerTeam("ofas_pecheur");
            helper.assertTrue(team != null, "l'équipe ofas_pecheur n'existe pas");
            helper.assertTrue("[Pêcheur] ".equals(team.getPlayerPrefix().getString()),
                    "préfixe inattendu : " + team.getPlayerPrefix().getString());

            commande(server, "role delete pecheur");
            helper.assertTrue(book.role("pecheur").isEmpty(), "le rôle pecheur existe encore");
            helper.assertTrue(scoreboard.getPlayerTeam("ofas_pecheur") == null,
                    "l'équipe ofas_pecheur existe encore");
            helper.succeed();
        } finally {
            retirerRole(server, "pecheur");
            nettoyer(scoreboard);
        }
    }

    @GameTest(template = "test/plancher", batch = "roles_commande_builtin")
    public static void deleteDUnRoleCodeEnDurEchoueSansLever(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            commande(server, "role delete ingenieur");
            helper.assertTrue(RoleSavedData.get(server).book().role("ingenieur").isPresent(),
                    "le rôle ingenieur a été supprimé");
            helper.succeed();
        } finally {
            nettoyer(server.getScoreboard());
        }
    }

    @GameTest(template = "test/plancher", batch = "roles_commande_horsligne")
    public static void laCibleHorsLigneRedevientCitoyen(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID absent = UUID.fromString("00000000-0000-0000-0000-00000000a0a0");
        try {
            commande(server, "role create temporaire red Temporaire");
            RoleBook book = RoleSavedData.get(server).book();
            book.assign(absent, "temporaire");
            helper.assertTrue("temporaire".equals(book.roleOf(absent).id()), "l'attribution n'a pas pris");
            commande(server, "role delete temporaire");
            helper.assertTrue("citoyen".equals(book.roleOf(absent).id()),
                    "rôle après suppression : " + book.roleOf(absent).id());
            helper.succeed();
        } finally {
            retirerRole(server, "temporaire");
            nettoyer(server.getScoreboard());
        }
    }

    @GameTest(template = "test/plancher", batch = "roles_gate")
    public static void isEngineerNeRepondOuiQuePourIngenieur(GameTestHelper helper) {
        RoleBook book = RoleBook.withBuiltins();
        UUID ing = UUID.randomUUID();
        UUID cit = UUID.randomUUID();
        UUID med = UUID.randomUUID();
        book.assign(ing, "ingenieur");
        book.assign(med, "medecin");
        helper.assertTrue(RoleGate.isEngineer(book, ing), "l'ingénieur n'est pas reconnu");
        helper.assertTrue(!RoleGate.isEngineer(book, cit), "un citoyen est reconnu ingénieur");
        helper.assertTrue(!RoleGate.isEngineer(book, med), "un médecin est reconnu ingénieur");
        helper.succeed();
    }
}
