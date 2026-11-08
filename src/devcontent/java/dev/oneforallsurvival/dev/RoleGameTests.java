package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleEvents;
import dev.oneforallsurvival.role.RoleGate;
import dev.oneforallsurvival.role.RoleSavedData;
import dev.oneforallsurvival.role.RoleTeams;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
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

    /** Retire les équipes {@code ofas_*}, celle du maire, et celles que le test a nommées. */
    private static void nettoyer(Scoreboard scoreboard, String... autres) {
        for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
            if (team.getName().startsWith("ofas_") || team.getName().equals(RoleTeams.MAYOR_TEAM)) {
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

    /** Le plus long identifiant permis (32 caractères) donne un nom d'équipe que vanilla accepte. */
    @GameTest(template = "test/plancher", batch = "roles_id_long")
    public static void unIdentifiantDe32CaracteresDonneUneEquipeUtilisable(GameTestHelper helper) {
        Scoreboard scoreboard = helper.getLevel().getServer().getScoreboard();
        String id = "a".repeat(32);
        RoleBook book = RoleBook.withBuiltins();
        try {
            book.createRole(id, "Long", net.minecraft.ChatFormatting.AQUA);
            RoleTeams.syncTeams(scoreboard, book);
            PlayerTeam team = scoreboard.getPlayerTeam(RoleTeams.teamName(id));
            helper.assertTrue(team != null, "l'équipe de l'identifiant long n'a pas été créée");
            RoleTeams.applyPlayer(scoreboard, "Dev", book.role(id).orElseThrow());
            PlayerTeam membre = scoreboard.getPlayersTeam("Dev");
            helper.assertTrue(membre == team, "Dev n'est pas dans l'équipe de l'identifiant long");
            helper.succeed();
        } finally {
            scoreboard.removePlayerFromTeam("Dev");
            nettoyer(scoreboard);
        }
    }

    /** Le maire quitte l'équipe de son métier pour celle du maire, dont le préfixe porte les deux. */
    @GameTest(template = "test/plancher", batch = "roles_maire_prefixe")
    public static void lePrefixeDuMaireCombineStatutEtMetier(GameTestHelper helper) {
        Scoreboard scoreboard = helper.getLevel().getServer().getScoreboard();
        RoleBook book = RoleBook.withBuiltins();
        UUID dev = UUID.randomUUID();
        try {
            book.assign(dev, "ingenieur");
            book.appointMayor(dev);
            RoleTeams.syncTeams(scoreboard, book);
            RoleTeams.applyPlayer(scoreboard, "Dev", dev, book);
            PlayerTeam team = scoreboard.getPlayersTeam("Dev");
            helper.assertTrue(team != null && RoleTeams.MAYOR_TEAM.equals(team.getName()),
                    "équipe du maire : " + (team == null ? "aucune" : team.getName()));
            String prefixe = team.getPlayerPrefix().getString();
            helper.assertTrue("[Maire] [Ingénieur] ".equals(prefixe), "préfixe du maire : " + prefixe);

            book.removeMayor();
            RoleTeams.syncTeams(scoreboard, book);
            helper.assertTrue(scoreboard.getPlayerTeam(RoleTeams.MAYOR_TEAM) == null,
                    "l'équipe du maire existe sans maire");
            RoleTeams.applyPlayer(scoreboard, "Dev", dev, book);
            PlayerTeam apres = scoreboard.getPlayersTeam("Dev");
            helper.assertTrue(apres != null && "ofas_ingenieur".equals(apres.getName()),
                    "équipe après le mandat : " + (apres == null ? "aucune" : apres.getName()));
            helper.succeed();
        } finally {
            scoreboard.removePlayerFromTeam("Dev");
            nettoyer(scoreboard);
        }
    }

    /** Source de commande qui garde les messages renvoyés, pour lire ce que l'équipe du serveur verrait. */
    private static final class SourceEcoute implements CommandSource {
        final List<Component> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        /** Clé et arguments (en texte) du dernier message, puis oubli des messages. */
        String dernier() {
            Component c = messages.isEmpty() ? Component.empty() : messages.get(messages.size() - 1);
            messages.clear();
            // sendFailure enveloppe le message dans un texte vide coloré en rouge.
            if (!(c.getContents() instanceof TranslatableContents) && !c.getSiblings().isEmpty()) {
                c = c.getSiblings().get(0);
            }
            if (c.getContents() instanceof TranslatableContents t) {
                StringBuilder sb = new StringBuilder(t.getKey());
                // Une exception dans la commande : son message est dans l'infobulle de « command.failed ».
                HoverEvent survol = c.getStyle().getHoverEvent();
                Component detail = survol == null ? null : survol.getValue(HoverEvent.Action.SHOW_TEXT);
                if (detail != null) {
                    sb.append(" (").append(detail.getString()).append(')');
                }
                for (Object arg : t.getArgs()) {
                    sb.append('|').append(arg instanceof Component a ? a.getString() : String.valueOf(arg));
                }
                return sb.toString();
            }
            return c.getString();
        }
    }

    private static String commande(MinecraftServer server, SourceEcoute ecoute, String texte) {
        CommandSourceStack source = server.createCommandSourceStack().withSource(ecoute);
        server.getCommands().performPrefixedCommand(source, texte);
        return ecoute.dernier();
    }

    private static String equipe(Scoreboard scoreboard, JoueurDeTest j) {
        PlayerTeam team = scoreboard.getPlayersTeam(j.joueur().getGameProfile().getName());
        return team == null ? "aucune" : team.getName();
    }

    /**
     * Le cycle complet par les commandes, avec de vrais joueurs connectés : nommer, changer le métier du
     * maire, le remplacer, relire la sauvegarde, le retirer. Le nom affiché (chat, liste des joueurs) suit.
     */
    @GameTest(template = "test/plancher", batch = "roles_maire_commandes", timeoutTicks = 100)
    public static void lesCommandesDuMaireNommentRemplacentEtRetirent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Scoreboard scoreboard = server.getScoreboard();
        RoleSavedData data = RoleSavedData.get(server);
        Optional<UUID> avant = data.book().mayor();
        SourceEcoute ecoute = new SourceEcoute();
        JoueurDeTest a = null;
        JoueurDeTest b = null;
        try {
            data.book().removeMayor();
            RoleEvents.refreshAll(server);
            a = JoueurDeTest.rejoindre(helper.getLevel(), "MaireEssaiA", helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
            b = JoueurDeTest.rejoindre(helper.getLevel(), "MaireEssaiB", helper.absoluteVec(new Vec3(3.5, 1.0, 2.5)));
            // Un sélecteur et non un pseudo : le serveur de GameTest n'a pas de cache de profils, et
            // GameProfileArgument ne résout un pseudo que par ce cache.
            String metier = commande(server, ecoute, "role set @a[name=MaireEssaiA] ingenieur");
            helper.assertTrue(metier.startsWith("command.oneforallsurvival.role.set|"), "métier de A : " + metier);
            commande(server, ecoute, "role set @a[name=MaireEssaiB] citoyen");

            String vide = commande(server, ecoute, "role mayor");
            helper.assertTrue("command.oneforallsurvival.role.mayor.none".equals(vide), "sans maire : " + vide);
            String rien = commande(server, ecoute, "role mayor remove");
            helper.assertTrue("command.oneforallsurvival.role.mayor.none_to_remove".equals(rien),
                    "retrait sans maire : " + rien);
            helper.assertTrue(!RoleGate.canDoMayorWork(a.joueur()), "un non-maire peut agir en maire");

            String nomme = commande(server, ecoute, "role mayor set @a[name=MaireEssaiA]");
            helper.assertTrue("command.oneforallsurvival.role.mayor.set|MaireEssaiA".equals(nomme), "nomination : " + nomme);
            helper.assertTrue(RoleGate.isMayor(a.joueur()) && RoleGate.canDoMayorWork(a.joueur()),
                    "MaireEssaiA n'est pas reconnu maire");
            helper.assertTrue("ingenieur".equals(data.book().roleOf(a.uuid()).id()), "le maire a perdu son métier");
            helper.assertTrue(RoleTeams.MAYOR_TEAM.equals(equipe(scoreboard, a)), "équipe de A : " + equipe(scoreboard, a));
            String affiche = a.joueur().getDisplayName().getString();
            helper.assertTrue("[Maire] [Ingénieur] MaireEssaiA".equals(affiche), "nom affiché de A : " + affiche);
            String deja = commande(server, ecoute, "role mayor set @a[name=MaireEssaiA]");
            helper.assertTrue("command.oneforallsurvival.role.mayor.already|MaireEssaiA".equals(deja), "renomination : " + deja);
            String qui = commande(server, ecoute, "role mayor");
            helper.assertTrue("command.oneforallsurvival.role.mayor.show|MaireEssaiA".equals(qui), "consultation : " + qui);

            commande(server, ecoute, "role set @a[name=MaireEssaiA] medecin");
            affiche = a.joueur().getDisplayName().getString();
            helper.assertTrue("[Maire] [Médecin] MaireEssaiA".equals(affiche), "après changement de métier : " + affiche);

            String remplace = commande(server, ecoute, "role mayor set @a[name=MaireEssaiB]");
            helper.assertTrue("command.oneforallsurvival.role.mayor.replaced|MaireEssaiB|MaireEssaiA".equals(remplace),
                    "remplacement : " + remplace);
            helper.assertTrue(!RoleGate.isMayor(a.joueur()) && RoleGate.isMayor(b.joueur()), "le maire n'a pas changé");
            helper.assertTrue("ofas_medecin".equals(equipe(scoreboard, a)), "équipe de l'ancien maire : " + equipe(scoreboard, a));
            affiche = a.joueur().getDisplayName().getString();
            helper.assertTrue("[Médecin] MaireEssaiA".equals(affiche), "nom de l'ancien maire : " + affiche);
            affiche = b.joueur().getDisplayName().getString();
            helper.assertTrue("[Maire] [Citoyen] MaireEssaiB".equals(affiche), "nom du nouveau maire : " + affiche);

            RoleBook relu = RoleBook.load(data.save(new CompoundTag()));
            helper.assertTrue(relu.isMayor(b.uuid()), "le maire ne survit pas à la sauvegarde");

            String retire = commande(server, ecoute, "role mayor remove");
            helper.assertTrue("command.oneforallsurvival.role.mayor.removed|MaireEssaiB".equals(retire), "retrait : " + retire);
            helper.assertTrue(data.book().mayor().isEmpty(), "il reste un maire");
            helper.assertTrue(scoreboard.getPlayerTeam(RoleTeams.MAYOR_TEAM) == null, "l'équipe du maire existe encore");
            affiche = b.joueur().getDisplayName().getString();
            helper.assertTrue("[Citoyen] MaireEssaiB".equals(affiche), "nom après le mandat : " + affiche);
            helper.succeed();
        } finally {
            for (JoueurDeTest j : new JoueurDeTest[] {a, b}) {
                if (j != null) {
                    data.book().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
                    j.quitter();
                }
            }
            data.book().removeMayor();
            avant.ifPresent(data.book()::appointMayor);
            data.setDirty();
            nettoyer(scoreboard);
        }
    }

    /** Sans le niveau 2, un joueur ne nomme pas de maire : la commande lui est cachée. */
    @GameTest(template = "test/plancher", batch = "roles_maire_permission", timeoutTicks = 100)
    public static void unJoueurOrdinaireNeNommePasDeMaire(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RoleSavedData data = RoleSavedData.get(server);
        Optional<UUID> avant = data.book().mayor();
        JoueurDeTest j = null;
        try {
            j = JoueurDeTest.rejoindre(helper.getLevel(), "MaireEssaiC", helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
            server.getCommands().performPrefixedCommand(j.joueur().createCommandSourceStack(),
                    "role mayor set @a[name=MaireEssaiC]");
            helper.assertTrue(!data.book().isMayor(j.uuid()), "un joueur ordinaire s'est nommé maire");
            helper.assertTrue(data.book().mayor().equals(avant), "le maire a changé");
            helper.succeed();
        } finally {
            if (j != null) {
                j.quitter();
            }
            nettoyer(server.getScoreboard());
        }
    }
}
