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
import dev.oneforallsurvival.economy.quest.QuestBook;
import dev.oneforallsurvival.economy.quest.QuestCategory;
import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestService;
import dev.oneforallsurvival.economy.quest.QuestService.ClaimResult;
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
        return book.ref(QuestRef.Kind.DAILY, 0);
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
            List<Component> sortie = new ArrayList<>();
            CommandSourceStack equipe = server.createCommandSourceStack().withSource(new Ecoute(sortie))
                    .withPermission(2);
            int code = server.getCommands().performPrefixedCommand(equipe, "eco quests");
            helper.assertTrue(code == 1 && sortie.size() == 1, "réponse de /eco quests : " + code + ", " + sortie);
            String texte = sortie.get(0).getString();
            helper.assertTrue(texte.contains(CoinMath.format(total).getString()), "total absent : " + texte);
            helper.assertTrue(texte.contains("QueteEco"), "nom du joueur absent : " + texte);
            helper.assertTrue(texte.contains("essai_eco"), "modèle absent : " + texte);
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
