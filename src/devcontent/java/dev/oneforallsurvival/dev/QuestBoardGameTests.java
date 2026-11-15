package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.quest.Quest;
import dev.oneforallsurvival.economy.quest.QuestBoard;
import dev.oneforallsurvival.economy.quest.QuestBook;
import dev.oneforallsurvival.economy.quest.QuestCategory;
import dev.oneforallsurvival.economy.quest.QuestCommands;
import dev.oneforallsurvival.economy.quest.QuestEvent;
import dev.oneforallsurvival.economy.quest.QuestGenerator;
import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import dev.oneforallsurvival.economy.quest.QuestProgress;
import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestService;
import dev.oneforallsurvival.economy.quest.QuestService.ClaimResult;
import dev.oneforallsurvival.economy.quest.QuestTarget;
import dev.oneforallsurvival.economy.quest.QuestTemplate;
import dev.oneforallsurvival.economy.quest.QuestTemplates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Les modèles et les événements de l'équipe : ajout (identifiant pris, modèle invalide), relecture après un arrêt du
 * monde, événement commun à tous avec progression propre, paiement sous le plafond hebdomadaire, fin d'un événement,
 * événement relancé sous le même identifiant, lecture par {@code /eco quest show}. Registre et tableau détachés
 * (jamais ceux du monde, sauf pour la commande elle-même, rangée aussitôt) ; tous les essais d'un lot s'exécutent sans
 * attendre, donc sans se croiser sur la réserve de l'équipe.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class QuestBoardGameTests {

    private static final String LOT_MODELES = "quetesBoardModeles";
    private static final String LOT_EVENEMENTS = "quetesBoardEvenements";
    /** Mercredi 18 novembre 2026, midi (UTC) : loin de toute heure de renouvellement. */
    private static final long MAINTENANT = Instant.parse("2026-11-18T12:00:00Z").toEpochMilli();
    private static final Vec3 ICI = new Vec3(2.5, 1, 2.5);
    private static final UUID AUTEUR = new UUID(7L, 8L);

    private QuestBoardGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Lots
    // ---------------------------------------------------------------------------------------------

    private static QuestTemplate modele(String id, QuestCategory categorie, String cible, int min, int max,
            long recompense) {
        return new QuestTemplate(id, categorie, cible == null ? null : QuestTarget.parse(cible).orElseThrow(), min, max,
                recompense, 1.0D, Map.of(), OptionalInt.empty(), null);
    }

    /** Sept modèles sans cible : de quoi tirer 3 journalières et 5 hebdomadaires. */
    private static List<QuestTemplate> reserve() {
        List<QuestTemplate> out = new ArrayList<>();
        for (String id : List.of("essai_a", "essai_b", "essai_c", "essai_d", "essai_e", "essai_f", "essai_g")) {
            out.add(modele(id, QuestCategory.BREAK, null, 10, 20, 10L));
        }
        return out;
    }

    @BeforeBatch(batch = LOT_EVENEMENTS)
    public static void avantEvenements(ServerLevel level) {
        EconomyClock.setClock(() -> MAINTENANT);
        QuestTemplates.setForTest(reserve());
    }

    @AfterBatch(batch = LOT_EVENEMENTS)
    public static void apresEvenements(ServerLevel level) {
        EconomyClock.reset();
        QuestTemplates.setForTest(null);
        QuestTemplates.setStaffTemplates(List.of());
    }

    @AfterBatch(batch = LOT_MODELES)
    public static void apresModeles(ServerLevel level) {
        QuestTemplates.setStaffTemplates(List.of());
    }

    // ---------------------------------------------------------------------------------------------
    // Outils
    // ---------------------------------------------------------------------------------------------

    private record Ecoute(List<Component> messages) implements CommandSource {
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
    }

    private static QuestBoard neuf() {
        return QuestBoard.load(new CompoundTag());
    }

    private static QuestEvent evenement(String id, QuestCategory categorie, int but, long recompense, long debut) {
        return new QuestEvent(id, categorie, null, but, recompense, debut, QuestCommands.endOf(debut, 1), "Fête", AUTEUR);
    }

    /** Un joueur d'essai en survie, au carnet neuf (son playerdata d'un passage précédent est effacé). */
    private static JoueurDeTest entrer(GameTestHelper helper, String nom) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(ICI));
        simule.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        new QuestBook().saveTo(simule.joueur());
        return simule;
    }

    private static int commande(MinecraftServer serveur, List<Component> sortie, int niveau, String texte) {
        CommandSourceStack source = serveur.createCommandSourceStack().withSource(new Ecoute(sortie))
                .withPermission(niveau);
        return serveur.getCommands().performPrefixedCommand(source, texte);
    }

    private static long porte(ServerPlayer joueur) {
        return CoinMath.valueOf(PlayerCoins.held(joueur));
    }

    // ---------------------------------------------------------------------------------------------
    // Modèles
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_MODELES)
    public static void unModeleAjouteEstTirable(GameTestHelper helper) {
        MinecraftServer serveur = helper.getLevel().getServer();
        List<Component> sortie = new ArrayList<>();
        try {
            int code = commande(serveur, sortie, 2, "eco quest template add essai_board break \"*\" 5 10 20 3");
            helper.assertTrue(code == 1, "ajout refusé : " + sortie);
            helper.assertTrue(QuestBoard.get(serveur).templates().stream().anyMatch(t -> t.id().equals("essai_board")),
                    "absent du tableau");
            List<QuestTemplate> reserve = QuestTemplates.current();
            helper.assertTrue(reserve.stream().anyMatch(t -> t.id().equals("essai_board")),
                    "absent de la réserve en service");
            // Tirable : sur toute la réserve, il sort.
            List<Quest> tirees = QuestGenerator.draw(reserve, "autre", Series.DAILY, reserve.size(), Set.of(), 5,
                    RandomSource.create(1L));
            helper.assertTrue(tirees.stream().anyMatch(q -> q.templateId().equals("essai_board")),
                    "jamais tiré sur " + reserve.size() + " modèles");
            sortie.clear();
            commande(serveur, sortie, 2, "eco quest template list");
            helper.assertTrue(sortie.size() == 1 && sortie.get(0).getString().contains("essai_board"),
                    "list : " + sortie);
            // Hors guillemets, « * » n'est pas lisible par Brigadier : la commande est refusée.
            helper.assertTrue(commande(serveur, new ArrayList<>(), 2, "eco quest template add essai_nu break * 5 10 20") == 0
                    && QuestBoard.get(serveur).templates().stream().noneMatch(t -> t.id().equals("essai_nu")),
                    "cible sans guillemets acceptée");
            helper.assertTrue(commande(serveur, new ArrayList<>(), 0, "eco quest template remove essai_board") == 0,
                    "retrait accepté sans le niveau 2");
        } finally {
            commande(serveur, new ArrayList<>(), 2, "eco quest template remove essai_board");
        }
        helper.assertTrue(QuestBoard.get(serveur).templates().stream().noneMatch(t -> t.id().equals("essai_board")),
                "modèle resté");
        helper.assertTrue(QuestTemplates.current().stream().noneMatch(t -> t.id().equals("essai_board")),
                "modèle resté dans la réserve");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_MODELES)
    public static void unIdentifiantPrisEstRefuse(GameTestHelper helper) {
        QuestBoard board = neuf();
        Set<String> fichier = QuestTemplates.fileIds();
        helper.assertTrue(!fichier.isEmpty(), "aucun modèle dans le fichier de données");
        String pris = fichier.iterator().next();
        QuestBoard.Result dansLeFichier = board.addTemplate(modele(pris, QuestCategory.BREAK, null, 5, 10, 20L));
        helper.assertTrue(!dansLeFichier.ok() && dansLeFichier.key().endsWith("refused.taken"),
                "identifiant du fichier accepté : " + dansLeFichier);
        helper.assertTrue(board.addTemplate(modele("essai_pris", QuestCategory.BREAK, null, 5, 10, 20L)).ok(),
                "premier ajout refusé");
        QuestBoard.Result dansLaListe = board.addTemplate(modele("essai_pris", QuestCategory.PLACE, null, 1, 2, 5L));
        helper.assertTrue(!dansLaListe.ok() && dansLaListe.key().endsWith("refused.taken"),
                "identifiant de la liste accepté : " + dansLaListe);
        helper.assertTrue(board.templates().size() == 1 && board.templates().get(0).category() == QuestCategory.BREAK,
                "le premier modèle a été remplacé : " + board.templates());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_MODELES)
    public static void unModeleInvalideEstRefuseEtLaReserveReste(GameTestHelper helper) {
        QuestBoard board = neuf();
        List<QuestTemplate> avant = QuestTemplates.current();
        List<QuestBoard.Result> refus = List.of(
                QuestCommands.addTemplate(board, "essai_livre", "deliver", "minecraft:bread", 1, 5, 10L, 1.0D),
                QuestCommands.addTemplate(board, "essai_ordre", "break", "*", 10, 5, 10L, 1.0D),
                QuestCommands.addTemplate(board, "essai_cible", "break", "minecraft:pierre_inconnue", 1, 5, 10L, 1.0D),
                QuestCommands.addTemplate(board, "essai_tag", "break", "#minecraft:tag_inconnu", 1, 5, 10L, 1.0D),
                QuestCommands.addTemplate(board, "essai_cat", "voler", "*", 1, 5, 10L, 1.0D),
                QuestCommands.addTemplate(board, "essai_poids", "break", "*", 1, 5, 10L, 0.0D),
                QuestCommands.addTemplate(board, "essai_metier", "job", "*", 1, 5, 10L, 1.0D),
                QuestCommands.addTemplate(board, "Mauvais Id", "break", "*", 1, 5, 10L, 1.0D));
        for (int i = 0; i < refus.size(); i++) {
            helper.assertTrue(!refus.get(i).ok(), "modèle invalide n°" + i + " accepté");
        }
        helper.assertTrue(board.templates().isEmpty(), "un modèle refusé est resté : " + board.templates());
        helper.assertTrue(QuestTemplates.current().equals(avant), "la réserve a changé");
        // Un événement à cible inconnue est refusé de même.
        QuestBoard.Result evenement = board.startEvent(new QuestEvent("essai_ev", QuestCategory.BREAK,
                QuestTarget.parse("minecraft:pierre_inconnue").orElseThrow(), 5, 10L, MAINTENANT,
                QuestCommands.endOf(MAINTENANT, 1), "", AUTEUR));
        helper.assertTrue(!evenement.ok() && board.activeEvents(MAINTENANT).isEmpty(), "événement à cible inconnue accepté");
        // Le témoin : un modèle valide passe.
        helper.assertTrue(QuestCommands.addTemplate(board, "essai_ok", "break", "#minecraft:logs", 2, 6, 10L, 2.0D).ok(),
                "modèle valide refusé");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_MODELES)
    public static void leModeleSurvitALaRelanceDuMonde(GameTestHelper helper) {
        QuestBoard board = neuf();
        helper.assertTrue(QuestCommands.addTemplate(board, "essai_relu", "place", "#minecraft:planks", 4, 12, 15L, 2.5D)
                .ok(), "ajout refusé");
        helper.assertTrue(board.startEvent(evenement("essai_fete", QuestCategory.KILL, 5, 40L, MAINTENANT)).ok(),
                "événement refusé");
        CompoundTag disque = board.save(new CompoundTag());
        QuestTemplates.setStaffTemplates(List.of());
        QuestBoard relu = QuestBoard.load(disque);
        helper.assertTrue(relu.templates().equals(board.templates()), "modèles relus : " + relu.templates());
        helper.assertTrue(QuestTemplates.current().stream().anyMatch(t -> t.id().equals("essai_relu")),
                "la réserve en service n'a pas retrouvé le modèle");
        helper.assertTrue(relu.activeEvents(MAINTENANT).equals(board.activeEvents(MAINTENANT))
                && relu.activeEvents(MAINTENANT).size() == 1, "événements relus : " + relu.activeEvents(MAINTENANT));
        helper.assertTrue(relu.save(new CompoundTag()).equals(disque), "relecture différente");
        helper.succeed();
    }

    /** Chaque changement réussi marque le tableau sale (sinon le monde ne l'écrit pas) ; un refus ne le touche pas. */
    @GameTest(template = "test/plancher", batch = LOT_MODELES)
    public static void leTableauEstMarqueSale(GameTestHelper helper) {
        QuestBoard board = neuf();
        helper.assertTrue(!board.isDirty(), "tableau neuf déjà sale");
        helper.assertTrue(board.addTemplate(modele("essai_sale", QuestCategory.BREAK, null, 5, 10, 20L)).ok(), "ajout");
        helper.assertTrue(board.isDirty(), "pas sale après un ajout");
        board.setDirty(false);
        helper.assertTrue(!board.addTemplate(modele("essai_sale", QuestCategory.BREAK, null, 5, 10, 20L)).ok(), "doublon");
        helper.assertTrue(!board.addTemplate(modele("essai_livre", QuestCategory.DELIVER, null, 5, 10, 20L)).ok(), "livraison");
        helper.assertTrue(!board.removeTemplate("inconnu") && !board.isDirty(), "sale après un refus de modèle");
        helper.assertTrue(board.removeTemplate("essai_sale") && board.isDirty(), "pas sale après un retrait");
        board.setDirty(false);
        QuestEvent fete = evenement("essai_sale", QuestCategory.BREAK, 5, 30L, MAINTENANT);
        helper.assertTrue(board.startEvent(fete).ok() && board.isDirty(), "pas sale après un lancement");
        board.setDirty(false);
        helper.assertTrue(!board.startEvent(fete).ok() && !board.isDirty(), "sale après un doublon d'événement");
        for (String titre : List.of("x".repeat(QuestBoard.MAX_TITLE + 1), "§cRouge", "deux\nlignes", "tab\tulation")) {
            QuestEvent mauvais = new QuestEvent("essai_titre", QuestCategory.BREAK, null, 5, 30L, MAINTENANT,
                    QuestCommands.endOf(MAINTENANT, 1), titre, AUTEUR);
            helper.assertTrue(!board.startEvent(mauvais).ok(), "titre accepté : " + titre);
        }
        helper.assertTrue(!board.isDirty(), "sale après des titres refusés");
        QuestEvent bon = new QuestEvent("essai_titre", QuestCategory.BREAK, null, 5, 30L, MAINTENANT,
                QuestCommands.endOf(MAINTENANT, 1), "x".repeat(QuestBoard.MAX_TITLE), AUTEUR);
        helper.assertTrue(board.startEvent(bon).ok(), "titre de la longueur maximale refusé");
        board.setDirty(false);
        helper.assertTrue(!board.stopEvent("inconnu") && !board.isDirty(), "sale après un arrêt sans événement");
        helper.assertTrue(board.stopEvent("essai_sale") && board.isDirty(), "pas sale après un arrêt");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Événements
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_EVENEMENTS)
    public static void unEvenementApparaitChezChacun(GameTestHelper helper) {
        QuestBoard board = neuf();
        helper.assertTrue(board.startEvent(evenement("fete", QuestCategory.BREAK, 5, 30L, MAINTENANT)).ok(),
                "événement refusé");
        JoueurDeTest a = entrer(helper, "EventA");
        JoueurDeTest b = entrer(helper, "EventB");
        try {
            List<QuestEvent> en = board.activeEvents(MAINTENANT);
            QuestService.refresh(a.joueur(), MAINTENANT, en);
            QuestService.refresh(b.joueur(), MAINTENANT, en);
            helper.assertTrue(QuestBook.of(a.joueur()).events().containsKey("fete"), "A ne voit pas l'événement");
            helper.assertTrue(QuestBook.of(b.joueur()).events().containsKey("fete"), "B ne voit pas l'événement");
            QuestProgress.add(a.joueur(), QuestCategory.BREAK, t -> true, 3);
            int chezA = QuestBook.of(a.joueur()).events().get("fete").progress();
            int chezB = QuestBook.of(b.joueur()).events().get("fete").progress();
            helper.assertTrue(chezA == 3 && chezB == 0, "progression : A " + chezA + ", B " + chezB);
            // Une seconde vue ne remet rien à zéro.
            QuestService.refresh(a.joueur(), MAINTENANT, en);
            helper.assertTrue(QuestBook.of(a.joueur()).events().get("fete").progress() == 3, "progrès perdu à la vue");
        } finally {
            a.quitter();
            b.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_EVENEMENTS)
    public static void unEvenementFiniSePaieSousLePlafond(GameTestHelper helper) {
        QuestBoard board = neuf();
        long plafond = EconomyConfig.questWeeklyCap();
        helper.assertTrue(plafond > 200L && plafond < 10_000L, "plafond de la semaine inattendu : " + plafond);
        helper.assertTrue(board.startEvent(evenement("fete", QuestCategory.BREAK, 5, 800L, MAINTENANT)).ok(), "fete");
        helper.assertTrue(board.startEvent(evenement("autre", QuestCategory.BREAK, 5, 100L, MAINTENANT)).ok(), "autre");
        JoueurDeTest simule = entrer(helper, "EventPaie");
        try {
            ServerPlayer joueur = simule.joueur();
            List<QuestEvent> en = board.activeEvents(MAINTENANT);
            QuestService.refresh(joueur, MAINTENANT, en);
            QuestBook book = QuestBook.of(joueur);
            // Il ne reste que 200 cuivres à gagner cette semaine.
            book.recordPaid(QuestService.week(MAINTENANT), plafond - 200L);
            book.events().replaceAll((id, q) -> q.withProgress(q.goal()));
            book.saveTo(joueur);
            QuestRef fete = book.eventRef("fete");
            QuestRef autre = book.eventRef("autre");
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult r1 = QuestService.claim(joueur, fete, MAINTENANT, registre, en);
            helper.assertTrue(r1 == ClaimResult.CAPPED_PARTIAL, "première réclamation : " + r1);
            helper.assertTrue(porte(joueur) == 200L, "porté : " + porte(joueur));
            helper.assertTrue(registre.totalCreated(MoneySource.QUEST) == 200L && registre.questClaims() == 1L,
                    "registre : " + registre.totalCreated(MoneySource.QUEST));
            helper.assertTrue(registre.questByTemplate().get("event:fete") == 200L,
                    "par modèle : " + registre.questByTemplate());
            ClaimResult encore = QuestService.claim(joueur, fete, MAINTENANT, registre, en);
            helper.assertTrue(encore == ClaimResult.ALREADY_CLAIMED, "seconde réclamation : " + encore);
            ClaimResult r2 = QuestService.claim(joueur, autre, MAINTENANT, registre, en);
            helper.assertTrue(r2 == ClaimResult.CAP_REACHED, "plafond atteint : " + r2);
            helper.assertTrue(porte(joueur) == 200L && registre.totalCreated(MoneySource.QUEST) == 200L,
                    "de l'argent est entré malgré le plafond : " + porte(joueur));
            helper.assertTrue(!QuestBook.of(joueur).events().get("autre").claimed(), "l'autre quête a été marquée");
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_EVENEMENTS)
    public static void unEvenementTermineEstRetire(GameTestHelper helper) {
        QuestBoard board = neuf();
        QuestEvent fete = evenement("fete", QuestCategory.BREAK, 5, 30L, MAINTENANT);
        helper.assertTrue(board.startEvent(fete).ok(), "événement refusé");
        JoueurDeTest simule = entrer(helper, "EventFin");
        try {
            ServerPlayer joueur = simule.joueur();
            QuestService.refresh(joueur, MAINTENANT, board.activeEvents(MAINTENANT));
            QuestProgress.add(joueur, QuestCategory.BREAK, t -> true, 5);
            QuestRef enCours = QuestBook.of(joueur).eventRef("fete");
            // L'horloge passe après la fin : plus en cours, retiré, la finie non réclamée attend.
            long apres = fete.endMillis() + 1L;
            helper.assertTrue(board.activeEvents(apres).isEmpty(), "l'événement n'est pas fini à l'heure");
            QuestService.refresh(joueur, apres, board.activeEvents(apres));
            QuestBook book = QuestBook.of(joueur);
            helper.assertTrue(book.events().isEmpty(), "l'événement fini est resté : " + book.events());
            int place = -1;
            for (int i = 0; i < book.pending().size(); i++) {
                if (book.pending().get(i).origin() == QuestRef.Kind.EVENT) {
                    place = i;
                }
            }
            helper.assertTrue(place >= 0, "la finie non réclamée n'attend pas : " + book.pending());
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult vieille = QuestService.claim(joueur, enCours, apres, registre, board.activeEvents(apres));
            helper.assertTrue(vieille == ClaimResult.STALE, "clic sur l'événement fini : " + vieille);
            ClaimResult attente = QuestService.claim(joueur, book.ref(QuestRef.Kind.PENDING, place), apres, registre,
                    board.activeEvents(apres));
            helper.assertTrue(attente == ClaimResult.PAID && porte(joueur) == 30L, "attente : " + attente + ", "
                    + porte(joueur));
            helper.assertTrue(registre.questByTemplate().getOrDefault("event:fete", 0L) == 30L,
                    "par modèle : " + registre.questByTemplate());
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_EVENEMENTS)
    public static void unEvenementRelanceNEstPasLAncien(GameTestHelper helper) {
        QuestBoard board = neuf();
        helper.assertTrue(board.startEvent(evenement("fete", QuestCategory.BREAK, 5, 30L, MAINTENANT)).ok(), "départ");
        QuestBoard.Result doublon = board.startEvent(evenement("fete", QuestCategory.BREAK, 5, 30L, MAINTENANT + 5L));
        helper.assertTrue(!doublon.ok() && doublon.key().endsWith("refused.running"), "doublon accepté : " + doublon);
        JoueurDeTest simule = entrer(helper, "EventRelance");
        try {
            ServerPlayer joueur = simule.joueur();
            QuestService.refresh(joueur, MAINTENANT, board.activeEvents(MAINTENANT));
            QuestProgress.add(joueur, QuestCategory.BREAK, t -> true, 5);
            QuestRef ancienne = QuestBook.of(joueur).eventRef("fete");
            // L'équipe l'arrête puis le relance sous le même identifiant.
            helper.assertTrue(board.stopEvent("fete"), "arrêt");
            long relance = MAINTENANT + 1000L;
            helper.assertTrue(board.startEvent(evenement("fete", QuestCategory.BREAK, 5, 30L, relance)).ok(), "relance");
            List<QuestEvent> en = board.activeEvents(MAINTENANT);
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult perime = QuestService.claim(joueur, ancienne, MAINTENANT, registre, en);
            helper.assertTrue(perime == ClaimResult.STALE, "clic périmé : " + perime);
            helper.assertTrue(porte(joueur) == 0L && registre.totalCreated(MoneySource.QUEST) == 0L,
                    "de l'argent est entré : " + porte(joueur));
            QuestBook book = QuestBook.of(joueur);
            Quest neuve = book.events().get("fete");
            helper.assertTrue(neuve != null && neuve.progress() == 0 && !neuve.claimed(),
                    "la nouvelle quête a été touchée : " + neuve);
            ClaimResult nouvelle = QuestService.claim(joueur, book.eventRef("fete"), MAINTENANT, registre, en);
            helper.assertTrue(nouvelle == ClaimResult.NOT_COMPLETE, "nouvelle quête : " + nouvelle);
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_EVENEMENTS)
    public static void showLitLesSeries(GameTestHelper helper) {
        QuestBoard board = neuf();
        helper.assertTrue(board.startEvent(evenement("fete", QuestCategory.BREAK, 5, 30L, MAINTENANT)).ok(), "départ");
        JoueurDeTest simule = entrer(helper, "EventShow");
        try {
            ServerPlayer joueur = simule.joueur();
            QuestService.refresh(joueur, MAINTENANT, board.activeEvents(MAINTENANT));
            QuestProgress.add(joueur, QuestCategory.BREAK, t -> true, 2);
            List<QuestCommands.Row> lignes = QuestCommands.rows(QuestBook.of(joueur));
            long journalieres = lignes.stream().filter(l -> l.section().equals("daily")).count();
            long hebdomadaires = lignes.stream().filter(l -> l.section().equals("weekly")).count();
            helper.assertTrue(journalieres == 3 && hebdomadaires == 5, "séries : " + journalieres + " et " + hebdomadaires);
            QuestCommands.Row fete = lignes.stream().filter(l -> l.section().equals("events")).findFirst().orElse(null);
            helper.assertTrue(fete != null && fete.templateId().equals("fete") && fete.progress() == 2
                    && fete.goal() == 5 && fete.reward() == 30L, "événement : " + fete);
            // La commande les écrit.
            List<Component> sortie = new ArrayList<>();
            int code = commande(helper.getLevel().getServer(), sortie, 2, "eco quest show " + joueur.getGameProfile().getName());
            helper.assertTrue(code == 1 && sortie.size() == 1, "commande : " + code + ", " + sortie);
            String texte = sortie.get(0).getString();
            helper.assertTrue(texte.contains("essai_") && texte.contains("2 / "), "texte : " + texte);
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }
}
