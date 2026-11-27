package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.quest.Quest;
import dev.oneforallsurvival.economy.quest.QuestActionPacket;
import dev.oneforallsurvival.economy.quest.QuestBook;
import dev.oneforallsurvival.economy.quest.QuestCategory;
import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestService;
import dev.oneforallsurvival.economy.quest.QuestService.ClaimResult;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket;
import dev.oneforallsurvival.economy.quest.QuestTemplate;
import dev.oneforallsurvival.economy.quest.QuestTemplates;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La réclamation des quêtes sur un vrai joueur : pièces dans la bourse puis l'inventaire, comptées à la source
 * {@code QUEST} une seule fois, rien ne bouge si tout n'entre pas, plafond, références périmées, créatif, mort. Horloge
 * de l'économie fixe et réserve d'essai, posées pour le lot et rendues après.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class QuestClaimGameTests {

    private static final String LOT = "quetesReclamation";
    private static final String LOT_ECO = "quetesEco";
    /** Les paquets de l'écran (tâche 5) : traités sur place par {@link JoueurDeTest#envoyer} (leçon du 2026-11-12). */
    private static final String LOT_PAQUETS = "quetesPaquets";
    /** Mercredi 18 novembre 2026, midi (UTC) : loin de toute heure de renouvellement. */
    private static final long MAINTENANT = Instant.parse("2026-11-18T12:00:00Z").toEpochMilli();
    private static final Vec3 ICI = new Vec3(2.5, 1, 2.5);

    private QuestClaimGameTests() {}

    private static List<QuestTemplate> reserve() {
        List<QuestTemplate> out = new ArrayList<>();
        for (String id : List.of("essai_a", "essai_b", "essai_c", "essai_d", "essai_e", "essai_f", "essai_g")) {
            out.add(new QuestTemplate(id, QuestCategory.BREAK, null, 10, 20, 10L, 1.0D, Map.of(), OptionalInt.empty(),
                    null));
        }
        return out;
    }

    private static void avant() {
        EconomyClock.setClock(() -> MAINTENANT);
        QuestTemplates.setForTest(reserve());
    }

    private static void apres() {
        EconomyClock.reset();
        QuestTemplates.setForTest(null);
    }

    @BeforeBatch(batch = LOT)
    public static void avantLot(ServerLevel level) {
        avant();
    }

    @AfterBatch(batch = LOT)
    public static void apresLot(ServerLevel level) {
        apres();
    }

    @BeforeBatch(batch = LOT_ECO)
    public static void avantEco(ServerLevel level) {
        avant();
    }

    @AfterBatch(batch = LOT_ECO)
    public static void apresEco(ServerLevel level) {
        apres();
    }

    @BeforeBatch(batch = LOT_PAQUETS)
    public static void avantPaquets(ServerLevel level) {
        avant();
    }

    @AfterBatch(batch = LOT_PAQUETS)
    public static void apresPaquets(ServerLevel level) {
        apres();
    }

    private static Quest finie(String id, long reward) {
        return new Quest(id, QuestCategory.BREAK, null, 10, reward, 10, false, null);
    }

    /**
     * Un carnet neuf (le playerdata d'un passage précédent peut porter un plafond payé) avec ces journalières, posé
     * chez le joueur ; rend la référence de la première.
     */
    private static QuestRef poser(ServerPlayer joueur, Quest... daily) {
        QuestBook book = new QuestBook();
        book.setSeries(Series.DAILY, QuestService.today(MAINTENANT), List.of(daily));
        book.setSeries(Series.WEEKLY, QuestService.week(MAINTENANT), List.of());
        book.saveTo(joueur);
        return daily.length == 0 ? null : book.ref(QuestRef.Kind.DAILY, 0);
    }

    private static long porte(ServerPlayer joueur) {
        return CoinMath.valueOf(PlayerCoins.held(joueur));
    }

    private static List<ItemStack> copie(ServerPlayer joueur) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
            out.add(joueur.getInventory().getItem(i).copy());
        }
        return out;
    }

    private static boolean memes(List<ItemStack> a, List<ItemStack> b) {
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return a.size() == b.size();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void reclamerPaieDansLaBourseEtCompte(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QueteBourse", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            joueur.getInventory().setItem(4, new ItemStack(EconomyRegistry.PURSE.get()));
            QuestRef ref = poser(joueur, finie("essai_a", 37L));
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult r = QuestService.claim(joueur, ref, MAINTENANT, registre);
            helper.assertTrue(r == ClaimResult.PAID, "résultat : " + r);
            long bourse = Purse.total(joueur.getInventory().getItem(4));
            helper.assertTrue(bourse == 37L, "dans la bourse : " + bourse);
            helper.assertTrue(porte(joueur) == 37L, "porté : " + porte(joueur));
            helper.assertTrue(registre.totalCreated(MoneySource.QUEST) == 37L,
                    "registre : " + registre.totalCreated(MoneySource.QUEST));
            helper.assertTrue(registre.questClaims() == 1L, "réclamations : " + registre.questClaims());
            QuestBook relu = QuestBook.of(joueur);
            helper.assertTrue(relu.daily().get(0).claimed(), "quête non marquée");
            helper.assertTrue(relu.capPaid() == 37L, "plafond : " + relu.capPaid());
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void deuxReclamationsNePaientQuUneFois(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QueteDouble", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            QuestRef ref = poser(joueur, finie("essai_a", 25L));
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult r1 = QuestService.claim(joueur, ref, MAINTENANT, registre);
            ClaimResult r2 = QuestService.claim(joueur, ref, MAINTENANT, registre);
            helper.assertTrue(r1 == ClaimResult.PAID && r2 == ClaimResult.ALREADY_CLAIMED, r1 + " puis " + r2);
            helper.assertTrue(porte(joueur) == 25L, "porté : " + porte(joueur));
            helper.assertTrue(registre.totalCreated(MoneySource.QUEST) == 25L && registre.questClaims() == 1L,
                    "registre : " + registre.totalCreated(MoneySource.QUEST) + " en " + registre.questClaims());
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void inventairePleinRienNeBouge(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePlein", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            for (int i = 0; i < 36; i++) {
                joueur.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
            }
            QuestRef ref = poser(joueur, finie("essai_a", 25L));
            CompoundTag carnet = QuestBook.of(joueur).save();
            List<ItemStack> inventaire = copie(joueur);
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult r = QuestService.claim(joueur, ref, MAINTENANT, registre);
            helper.assertTrue(r == ClaimResult.NO_ROOM, "résultat : " + r);
            helper.assertTrue(memes(inventaire, copie(joueur)), "l'inventaire a bougé");
            helper.assertTrue(carnet.equals(QuestBook.of(joueur).save()), "le carnet a bougé");
            helper.assertTrue(registre.totalCreated(MoneySource.QUEST) == 0L && registre.questClaims() == 0L,
                    "registre : " + registre.totalCreated(MoneySource.QUEST));
            helper.assertTrue(helper.getLevel().getEntities(EconomyRegistry.coinDropType(),
                    joueur.getBoundingBox().inflate(4.0D), e -> true).isEmpty(), "des pièces sont tombées");
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void lePlafondReduitPuisArrete(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePlafond", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            long plafond = EconomyConfig.questWeeklyCap();
            helper.assertTrue(plafond == 1_000L, "plafond réglé : " + plafond);
            QuestRef ref = poser(joueur, finie("essai_a", 50L), finie("essai_b", 50L));
            QuestBook book = QuestBook.of(joueur);
            book.recordPaid(QuestService.week(MAINTENANT), 980L);
            book.saveTo(joueur);
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult r1 = QuestService.claim(joueur, ref, MAINTENANT, registre);
            helper.assertTrue(r1 == ClaimResult.CAPPED_PARTIAL, "première : " + r1);
            helper.assertTrue(porte(joueur) == 20L, "porté : " + porte(joueur));
            helper.assertTrue(registre.totalCreated(MoneySource.QUEST) == 20L,
                    "registre : " + registre.totalCreated(MoneySource.QUEST));
            // La suivante : plafond atteint, rien ne bouge, elle attend.
            QuestRef seconde = QuestBook.of(joueur).ref(QuestRef.Kind.DAILY, 1);
            CompoundTag carnet = QuestBook.of(joueur).save();
            List<ItemStack> inventaire = copie(joueur);
            ClaimResult r2 = QuestService.claim(joueur, seconde, MAINTENANT, registre);
            helper.assertTrue(r2 == ClaimResult.CAP_REACHED, "seconde : " + r2);
            helper.assertTrue(memes(inventaire, copie(joueur)), "l'inventaire a bougé");
            helper.assertTrue(carnet.equals(QuestBook.of(joueur).save()), "le carnet a bougé");
            helper.assertTrue(registre.totalCreated(MoneySource.QUEST) == 20L && registre.questClaims() == 1L,
                    "registre : " + registre.totalCreated(MoneySource.QUEST));
            Quest attend = QuestBook.of(joueur).daily().get(1);
            helper.assertTrue(attend.complete() && !attend.claimed(), "la quête n'attend plus : " + attend);
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneReferencePerimeeEstRefusee(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePerimee", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            QuestRef ref = poser(joueur, finie("essai_a", 25L));
            LocalDate hier = ref.seriesKey().minusDays(1);
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult r1 = QuestService.claim(joueur,
                    new QuestRef(QuestRef.Kind.DAILY, 0, ref.templateId(), hier), MAINTENANT, registre);
            ClaimResult r2 = QuestService.claim(joueur,
                    new QuestRef(QuestRef.Kind.DAILY, 0, "essai_b", ref.seriesKey()), MAINTENANT, registre);
            ClaimResult r3 = QuestService.claim(joueur,
                    new QuestRef(QuestRef.Kind.PENDING, 0, ref.templateId(), hier), MAINTENANT, registre);
            helper.assertTrue(r1 == ClaimResult.STALE && r2 == ClaimResult.STALE && r3 == ClaimResult.STALE,
                    r1 + ", " + r2 + ", " + r3);
            helper.assertTrue(porte(joueur) == 0L && registre.totalCreated(MoneySource.QUEST) == 0L, "payé");
            helper.assertTrue(!QuestBook.of(joueur).daily().get(0).claimed(), "quête marquée");
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void leCreatifNeReclamePas(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QueteCreatif", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            joueur.setGameMode(GameType.CREATIVE);
            QuestRef ref = poser(joueur, finie("essai_a", 25L));
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult r = QuestService.claim(joueur, ref, MAINTENANT, registre);
            helper.assertTrue(r == ClaimResult.CREATIVE, "résultat : " + r);
            helper.assertTrue(porte(joueur) == 0L && registre.totalCreated(MoneySource.QUEST) == 0L, "payé");
            helper.assertTrue(!QuestBook.of(joueur).daily().get(0).claimed(), "quête marquée");
            joueur.setGameMode(GameType.SURVIVAL);
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    /** Un spectateur ne réclame pas non plus : refus avant tout mouvement, rien payé, quête non marquée. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void leSpectateurNeReclamePas(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QueteSpectateur", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            joueur.setGameMode(GameType.SPECTATOR);
            QuestRef ref = poser(joueur, finie("essai_a", 25L));
            EconomyLedger registre = EconomyLedger.load(new CompoundTag());
            ClaimResult r = QuestService.claim(joueur, ref, MAINTENANT, registre);
            helper.assertTrue(r == ClaimResult.SPECTATOR, "résultat : " + r);
            helper.assertTrue(porte(joueur) == 0L && registre.totalCreated(MoneySource.QUEST) == 0L, "payé");
            helper.assertTrue(!QuestBook.of(joueur).daily().get(0).claimed(), "quête marquée");
            joueur.setGameMode(GameType.SURVIVAL);
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void laQueteSuitLeJoueurApresSaMort(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QueteMort", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            poser(joueur, new Quest("essai_a", QuestCategory.BREAK, null, 10, 25L, 5, false, null));
            QuestBook book = QuestBook.of(joueur);
            book.recordPaid(QuestService.week(MAINTENANT), 300L);
            book.saveTo(joueur);
            // Ce que fait la réapparition : un nouveau joueur, recopié de l'ancien (sans keepInventory).
            ServerPlayer neuf = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    joueur.getGameProfile());
            neuf.restoreFrom(joueur, false);
            QuestBook relu = QuestBook.of(neuf);
            helper.assertTrue(relu.daily().size() == 1 && relu.daily().get(0).progress() == 5,
                    "progression après la mort : " + relu.daily());
            helper.assertTrue(relu.capPaid() == 300L, "plafond après la mort : " + relu.capPaid());
            helper.assertTrue(relu.save().equals(book.save()), "carnet différent après la mort");
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_ECO)
    public static void ecoQuestsMontreLesTotaux(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QueteEco", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            EconomyLedger registre = EconomyLedger.get(server);
            long avant = registre.totalCreated(MoneySource.QUEST);
            long reclamations = registre.questClaims();
            QuestRef ref = poser(joueur, finie("essai_eco", 120L));
            ClaimResult r = QuestService.claim(joueur, ref, MAINTENANT, registre);
            helper.assertTrue(r == ClaimResult.PAID, "résultat : " + r);
            long total = registre.totalCreated(MoneySource.QUEST);
            helper.assertTrue(total == avant + 120L && registre.questClaims() == reclamations + 1L,
                    "registre du serveur : " + avant + " puis " + total);
            // 120 cuivres dépassent le seuil du journal (100) : une ligne « quête : essai_eco ».
            EconomyLedger.Entry ligne = registre.entries(0).get(0);
            helper.assertTrue(ligne.kind().equals("created.quest") && ligne.reason().equals("quête : essai_eco"),
                    "ligne du journal : " + ligne);
            // Un modèle livré (quests.json), dans un carnet neuf : lui a un libellé.
            ClaimResult livre = QuestService.claim(joueur, poser(joueur, finie("kill_zombies", 500L)), MAINTENANT,
                    registre);
            helper.assertTrue(livre == ClaimResult.PAID, "résultat du modèle livré : " + livre);
            total = registre.totalCreated(MoneySource.QUEST);
            List<Component> sortie = new ArrayList<>();
            CommandSourceStack equipe = server.createCommandSourceStack().withSource(new Ecoute(sortie))
                    .withPermission(2);
            int code = server.getCommands().performPrefixedCommand(equipe, "eco quests");
            helper.assertTrue(code == 1 && sortie.size() == 1, "réponse de /eco quests : " + code + ", " + sortie);
            String texte = sortie.get(0).getString();
            helper.assertTrue(texte.contains(CoinMath.format(total).getString()), "total absent : " + texte);
            helper.assertTrue(texte.contains("QueteEco"), "nom du joueur absent : " + texte);
            helper.assertTrue(texte.contains("essai_eco"), "modèle sans libellé, nommé par son identifiant : " + texte);
            // Le serveur lit en anglais : le libellé écrit en clair, jamais recalculé par la commande essayée.
            helper.assertTrue(texte.contains("Slay zombies") && !texte.contains("kill_zombies"),
                    "modèle livré : « Slay zombies » attendu, pas son identifiant : " + texte);
            // Dernière ligne : les modèles en service (la réserve d'essai).
            helper.assertTrue(texte.strip().endsWith(" " + reserve().size()), "modèles chargés absents : " + texte);
            List<Component> refus = new ArrayList<>();
            int code0 = server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSource(new Ecoute(refus)).withPermission(0), "eco quests");
            helper.assertTrue(code0 == 0, "/eco quests accepté sans le niveau 2");
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Paquets de l'écran (tâche 5)
    // ---------------------------------------------------------------------------------------------

    private static Quest enCours(String id) {
        return new Quest(id, QuestCategory.BREAK, null, 10, 25L, 3, false, null);
    }

    /** Le registre du serveur : (total QUEST, réclamations). */
    private static long[] registre(MinecraftServer server) {
        EconomyLedger l = EconomyLedger.get(server);
        return new long[] {l.totalCreated(MoneySource.QUEST), l.questClaims()};
    }

    @GameTest(template = "test/plancher", batch = LOT_PAQUETS)
    public static void unClicPerimeNeReclameRien(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePaquetPerime", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            // Ce que l'écran montrait : la série d'hier, finie. Entre l'affichage et le clic, le jour a tourné.
            LocalDate hier = QuestService.today(MAINTENANT).minusDays(1);
            QuestBook book = new QuestBook();
            book.setSeries(Series.DAILY, hier, List.of(finie("essai_a", 25L)));
            book.setSeries(Series.WEEKLY, QuestService.week(MAINTENANT), List.of());
            book.saveTo(joueur);
            QuestRef vue = book.ref(QuestRef.Kind.DAILY, 0);
            long[] avant = registre(server);
            simule.etatsQuetesRecus();
            simule.messagesRecus();
            simule.envoyer(QuestActionPacket.claim(vue));
            helper.assertTrue(porte(joueur) == 0L, "payé : " + porte(joueur));
            long[] apres = registre(server);
            helper.assertTrue(apres[0] == avant[0] && apres[1] == avant[1], "registre changé");
            QuestBook relu = QuestBook.of(joueur);
            helper.assertTrue(QuestService.today(MAINTENANT).equals(relu.dailyKey()), "série non renouvelée");
            // L'ancienne quête finie attend sa réclamation, intacte (ni payée ni perdue).
            helper.assertTrue(relu.pending().size() == 1 && !relu.pending().get(0).quest().claimed(),
                    "attente : " + relu.pending());
            List<QuestSyncPacket> etats = simule.etatsQuetesRecus();
            helper.assertTrue(etats.size() == 1 && etats.get(0).pendingCount() == 1, "état renvoyé : " + etats);
            helper.assertTrue(simule.messagesRecus().stream().noneMatch(m -> m.contains("quest.claim")),
                    "un clic périmé ne dit rien d'autre que l'état");
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_PAQUETS)
    public static void deuxPaquetsDeReclamationDansLeMemeTick(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePaquetDouble", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            QuestRef ref = poser(joueur, finie("essai_a", 37L));
            long[] avant = registre(server);
            simule.messagesRecus();
            simule.envoyer(QuestActionPacket.claim(ref));
            simule.envoyer(QuestActionPacket.claim(ref));
            helper.assertTrue(porte(joueur) == 37L, "porté : " + porte(joueur));
            long[] apres = registre(server);
            helper.assertTrue(apres[0] == avant[0] + 37L && apres[1] == avant[1] + 1L,
                    "registre : +" + (apres[0] - avant[0]) + " en " + (apres[1] - avant[1]));
            List<String> messages = simule.messagesRecus();
            helper.assertTrue(messages.equals(List.of("message.oneforallsurvival.quest.claim.paid",
                    "message.oneforallsurvival.quest.claim.already_claimed")), "messages : " + messages);
            List<QuestSyncPacket> etats = simule.etatsQuetesRecus();
            helper.assertTrue(!etats.isEmpty()
                    && etats.get(etats.size() - 1).quests().get(0).state() == QuestSyncPacket.State.CLAIMED,
                    "état : " + etats);
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_PAQUETS)
    public static void uneRelanceParPaquetPuisRefus(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePaquetRelance", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            helper.assertTrue(EconomyConfig.questRerollsPerDay() == 1,
                    "relances réglées : " + EconomyConfig.questRerollsPerDay());
            poser(joueur, enCours("essai_a"), enCours("essai_b"));
            QuestBook book = QuestBook.of(joueur);
            QuestRef premiere = book.ref(QuestRef.Kind.DAILY, 0);
            QuestRef seconde = book.ref(QuestRef.Kind.DAILY, 1);
            simule.messagesRecus();
            simule.envoyer(QuestActionPacket.reroll(premiere));
            String nouvelle = QuestBook.of(joueur).daily().get(0).templateId();
            helper.assertTrue(!nouvelle.equals("essai_a") && !nouvelle.equals("essai_b"), "relance : " + nouvelle);
            simule.envoyer(QuestActionPacket.reroll(seconde));
            QuestBook apres = QuestBook.of(joueur);
            helper.assertTrue(apres.daily().get(1).templateId().equals("essai_b")
                    && apres.daily().get(0).templateId().equals(nouvelle), "seconde relance acceptée : " + apres.daily());
            // La première relance rejouée (clic périmé sur la quête déjà remplacée) : rien, ni message.
            simule.envoyer(QuestActionPacket.reroll(premiere));
            helper.assertTrue(QuestBook.of(joueur).save().equals(apres.save()), "le carnet a bougé");
            List<String> messages = simule.messagesRecus();
            helper.assertTrue(messages.equals(List.of("message.oneforallsurvival.quest.reroll.done",
                    "message.oneforallsurvival.quest.reroll.none_left")), "messages : " + messages);
            List<QuestSyncPacket> etats = simule.etatsQuetesRecus();
            helper.assertTrue(!etats.isEmpty() && etats.get(etats.size() - 1).rerollsLeft() == 0,
                    "relances restantes : " + etats);
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_PAQUETS)
    public static void unIndexHorsDesBornesNeFaitRien(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePaquetBornes", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            QuestRef ref = poser(joueur, finie("essai_a", 25L), enCours("essai_b"));
            CompoundTag carnet = QuestBook.of(joueur).save();
            long[] avant = registre(server);
            simule.etatsQuetesRecus();
            simule.messagesRecus();
            int envois = 0;
            for (int index : new int[] {-1, 2, 99, Integer.MAX_VALUE}) {
                for (QuestRef.Kind kind : QuestRef.Kind.values()) {
                    QuestRef faux = new QuestRef(kind, index, ref.templateId(), ref.seriesKey(), null, 0L);
                    simule.envoyer(QuestActionPacket.claim(faux));
                    simule.envoyer(QuestActionPacket.reroll(faux));
                    envois += 2;
                }
            }
            // Un geste inconnu et un geste sans référence : seulement l'état.
            simule.envoyer(new QuestActionPacket(42, ref));
            simule.envoyer(new QuestActionPacket(QuestActionPacket.CLAIM, null));
            envois += 2;
            helper.assertTrue(porte(joueur) == 0L, "payé : " + porte(joueur));
            long[] apres = registre(server);
            helper.assertTrue(apres[0] == avant[0] && apres[1] == avant[1], "registre changé");
            helper.assertTrue(carnet.equals(QuestBook.of(joueur).save()), "le carnet a bougé");
            List<String> messages = simule.messagesRecus();
            helper.assertTrue(messages.isEmpty(), "message pour une référence qui ne désigne rien : " + messages);
            int etats = simule.etatsQuetesRecus().size();
            helper.assertTrue(etats == envois, "états renvoyés : " + etats + " pour " + envois + " paquets");
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_PAQUETS)
    public static void lEtatDecritLeCarnet(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePaquetEtat", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            poser(joueur, finie("essai_a", 50L), enCours("essai_b"));
            QuestBook book = QuestBook.of(joueur);
            book.recordPaid(QuestService.week(MAINTENANT), 980L);
            book.addPending(finie("essai_c", 10L), QuestService.today(MAINTENANT).minusDays(1), 8);
            book.saveTo(joueur);
            simule.etatsQuetesRecus();
            simule.envoyer(QuestActionPacket.open());
            simule.envoyer(QuestActionPacket.open());
            List<QuestSyncPacket> etats = simule.etatsQuetesRecus();
            helper.assertTrue(etats.size() == 1, "états pour deux ouvertures dans la seconde : " + etats.size());
            QuestSyncPacket etat = etats.get(0);
            helper.assertTrue(etat.capPaid() == 980L && etat.cap() == 1_000L && etat.pendingCount() == 1
                    && etat.rerollsLeft() == 1, "en-tête : " + etat);
            helper.assertTrue(etat.dailyResetIn() > 0L && etat.weeklyResetIn() >= etat.dailyResetIn(),
                    "renouvellements : " + etat.dailyResetIn() + ", " + etat.weeklyResetIn());
            helper.assertTrue(etat.quests().size() == 2, "quêtes : " + etat.quests());
            QuestSyncPacket.Entry finieVue = etat.quests().get(0);
            // Somme payable maintenant : ce que le plafond laisse (20 sur 50).
            helper.assertTrue(finieVue.ref().equals(QuestBook.of(joueur).ref(QuestRef.Kind.DAILY, 0))
                    && finieVue.payable() == 20L && finieVue.reward() == 50L
                    && finieVue.state() == QuestSyncPacket.State.COMPLETE, "finie : " + finieVue);
            QuestSyncPacket.Entry enCoursVue = etat.quests().get(1);
            helper.assertTrue(enCoursVue.progress() == 3 && enCoursVue.goal() == 10 && enCoursVue.payable() == 0L
                    && !enCoursVue.icon().isEmpty() && enCoursVue.state() == QuestSyncPacket.State.IN_PROGRESS,
                    "en cours : " + enCoursVue);
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_PAQUETS)
    public static void unEvenementSeReclameParPaquet(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        dev.oneforallsurvival.economy.quest.QuestBoard board = dev.oneforallsurvival.economy.quest.QuestBoard.get(server);
        String id = "essai_paquet_fete";
        // Le titre libre de l'équipe : un format et une clé de langue, qui doivent ressortir tels quels.
        String titre = "%s fête quest.oneforallsurvival.template.fish %1$s";
        long debut = MAINTENANT - 3_600_000L;
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePaquetFete", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            board.stopEvent(id);
            helper.assertTrue(board.startEvent(new dev.oneforallsurvival.economy.quest.QuestEvent(id,
                    QuestCategory.BREAK, null, 10, 30L, debut, MAINTENANT + 86_400_000L, titre, joueur.getUUID())).ok(),
                    "événement refusé");
            poser(joueur, enCours("essai_a"));
            QuestService.refresh(joueur, MAINTENANT);
            QuestBook book = QuestBook.of(joueur);
            helper.assertTrue(book.events().containsKey(id), "événement absent du carnet : " + book.events().keySet());
            book.events().put(id, book.events().get(id).withProgress(10));
            book.saveTo(joueur);
            QuestRef ref = book.eventRef(id);
            helper.assertTrue(ref.eventKey() == debut, "début : " + ref.eventKey());
            long[] avant = registre(server);
            simule.etatsQuetesRecus();
            simule.messagesRecus();

            // L'état décodé : la référence d'événement à l'identique, le titre littéral.
            simule.envoyer(QuestActionPacket.open());
            List<QuestSyncPacket> etats = simule.etatsQuetesRecus();
            helper.assertTrue(etats.size() == 1, "états : " + etats.size());
            QuestSyncPacket.Entry vue = etats.get(0).quests().stream()
                    .filter(e -> e.ref().kind() == QuestRef.Kind.EVENT).findFirst().orElse(null);
            helper.assertTrue(vue != null && vue.ref().equals(ref), "référence décodée : " + (vue == null ? null : vue.ref()));
            helper.assertTrue(vue.title().getContents() instanceof net.minecraft.network.chat.contents.LiteralContents
                    && vue.title().getString().equals(titre), "titre : " + vue.title());
            helper.assertTrue(vue.state() == QuestSyncPacket.State.COMPLETE && vue.payable() == 30L, "vue : " + vue);

            // Un début décalé (événement relancé sous le même identifiant) : rien, ni message.
            simule.envoyer(QuestActionPacket.claim(QuestRef.event(id, debut + 1L)));
            helper.assertTrue(porte(joueur) == 0L, "payé sur un début périmé : " + porte(joueur));
            helper.assertTrue(simule.messagesRecus().isEmpty(), "message pour un début périmé");

            // La vraie référence, deux fois : un seul paiement.
            simule.envoyer(QuestActionPacket.claim(vue.ref()));
            simule.envoyer(QuestActionPacket.claim(vue.ref()));
            helper.assertTrue(porte(joueur) == 30L, "porté : " + porte(joueur));
            long[] apres = registre(server);
            helper.assertTrue(apres[0] == avant[0] + 30L && apres[1] == avant[1] + 1L,
                    "registre : +" + (apres[0] - avant[0]) + " en " + (apres[1] - avant[1]));
            List<String> messages = simule.messagesRecus();
            helper.assertTrue(messages.equals(List.of("message.oneforallsurvival.quest.claim.paid",
                    "message.oneforallsurvival.quest.claim.already_claimed")), "messages : " + messages);
            helper.assertTrue(QuestBook.of(joueur).events().get(id).claimed(), "événement non marqué");
        } finally {
            board.stopEvent(id);
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_PAQUETS)
    public static void lAttenteSeReclameEnUnGeste(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePaquetAttente", helper.absoluteVec(ICI));
        try {
            ServerPlayer joueur = simule.joueur();
            poser(joueur);
            QuestBook book = QuestBook.of(joueur);
            LocalDate hier = QuestService.today(MAINTENANT).minusDays(1);
            book.addPending(finie("essai_a", 10L), hier, 8);
            book.addPending(finie("essai_b", 20L), hier, 8);
            book.addPending(finie("essai_c", 30L), hier, 8);
            book.saveTo(joueur);
            simule.messagesRecus();
            simule.envoyer(QuestActionPacket.claimPendingAll());
            helper.assertTrue(porte(joueur) == 60L, "porté : " + porte(joueur));
            helper.assertTrue(QuestBook.of(joueur).pending().isEmpty(), "attente : " + QuestBook.of(joueur).pending());
            List<String> messages = simule.messagesRecus();
            helper.assertTrue(messages.equals(List.of("message.oneforallsurvival.quest.pending.done")),
                    "messages : " + messages);
            // Plus rien en attente : un second geste ne paie rien.
            simule.envoyer(QuestActionPacket.claimPendingAll());
            helper.assertTrue(porte(joueur) == 60L, "payé deux fois : " + porte(joueur));
        } finally {
            simule.quitter();
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_PAQUETS, timeoutTicks = 60)
    public static void unJoueurMarqueSaleRecoitUnSeulEtat(GameTestHelper helper) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), "QuetePaquetSale", helper.absoluteVec(ICI));
        ServerPlayer joueur = simule.joueur();
        poser(joueur, enCours("essai_a"));
        simule.etatsQuetesRecus();
        QuestService.markDirty(joueur);
        QuestService.markDirty(joueur);
        QuestService.markDirty(joueur);
        helper.assertTrue(QuestService.isDirty(joueur), "non marqué");
        helper.runAfterDelay(QuestService.SYNC_TICKS + 1, () -> {
            try {
                int n = simule.etatsQuetesRecus().size();
                helper.assertTrue(n == 1, "états envoyés : " + n);
                helper.assertTrue(!QuestService.isDirty(joueur), "toujours marqué");
            } finally {
                simule.quitter();
            }
            helper.succeed();
        });
    }

    /** Une source de commande qui garde ce qu'on lui dit. */
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
}
