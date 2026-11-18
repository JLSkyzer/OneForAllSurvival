package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.Receipt;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.debit.DailyDebit;
import dev.oneforallsurvival.economy.debit.DirectDebit;
import dev.oneforallsurvival.economy.debit.DirectDebit.Outcome;
import dev.oneforallsurvival.economy.invoice.InvoiceItem;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status;
import dev.oneforallsurvival.economy.invoice.InvoiceService;
import dev.oneforallsurvival.economy.invoice.InvoiceService.CopyResult;
import dev.oneforallsurvival.economy.invoice.InvoiceService.IssueResult;
import dev.oneforallsurvival.economy.invoice.InvoiceService.PayResult;
import dev.oneforallsurvival.economy.payroll.Payroll;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le prélèvement automatique des factures publiques (lot E6, tâche 1, feature 86) : inventaire (bourses comprises),
 * puis attente, puis coffre-fort, même déconnecté et zone non chargée ; tout ou rien, monnaie rendue ; refus sans
 * mouvement, débiteur et créancier prévenus ; présenté à chaque minuit ; payable aussi par l'objet facture, une seule
 * fois ; une copie ne paie pas ; deux clôtures du même jour ne prélèvent pas deux fois ; une facture de joueur n'est
 * jamais prélevée ; un créancier disparu annule ; la pause de la paie arrête aussi le prélèvement. À chaque essai, la
 * somme (débiteur + attente + coffre-fort + créancier) est conservée.
 *
 * <p>Le créancier est {@link PayeurDEssai} (genre {@code test}) ; le registre des factures est détaché
 * ({@code InvoiceRegistry.load}) sauf pour le paiement par l'objet, qui lit celui du serveur.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PrelevementGameTests {

    private static final String LOT_A = "prelevement_a";
    private static final String LOT_B = "prelevement_b";

    private static final Vec3 POS_A = new Vec3(2.5, 0, 4.5);
    private static final Vec3 POS_B = new Vec3(5.5, 0, 4.5);

    /** Un jour quelconque, au milieu de l'année (pas de changement d'heure autour). */
    private static final LocalDate J = LocalDate.of(2026, 6, 14);
    private static final long MINUTE = 60_000L;

    /** Pour des coffres-forts notés à des endroits distincts. */
    private static int coffresPoses;

    private PrelevementGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Le « minuit » qui clôt {@code jour}. */
    private static long minuit(LocalDate jour) {
        return EconomyCalendar.boundaryAfter(jour, EconomyCalendar.configuredZone(), EconomyConfig.payHour());
    }

    /** Une aire close : sol, pourtour, plafond, vidée de tout ce qui n'est pas un joueur. */
    private static void boite(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                boolean bord = x == 0 || x == 7 || z == 0 || z == 7;
                helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
                for (int y = 0; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), bord ? Blocks.STONE : Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.GLOWSTONE);
        BlockPos a = helper.absolutePos(new BlockPos(0, -1, 0));
        BlockPos b = helper.absolutePos(new BlockPos(8, 8, 8));
        AABB zone = new AABB(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()).inflate(1.0D);
        for (Entity reste : helper.getLevel().getEntities((Entity) null, zone, e -> !(e instanceof ServerPlayer))) {
            reste.discard();
        }
    }

    private static UUID identifiant(String nom) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + nom).getBytes(StandardCharsets.UTF_8));
    }

    private static EnumMap<Coin, Long> pieces(Object... paires) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (int i = 0; i < paires.length; i += 2) {
            out.put((Coin) paires[i], ((Number) paires[i + 1]).longValue());
        }
        return out;
    }

    private static ItemStack bourse(Object... paires) {
        ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
        pieces(paires).forEach((coin, n) -> Purse.add(bourse, coin, n));
        return bourse;
    }

    /** Ce qu'un essai a ouvert : tout est rendu à la fermeture, même si l'essai a échoué. */
    private static final class Essai implements AutoCloseable {
        final GameTestHelper helper;
        final MinecraftServer serveur;
        /** Le registre détaché des factures de cet essai. */
        final InvoiceRegistry registre = InvoiceRegistry.load(new CompoundTag());
        final String ref;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> comptes = new ArrayList<>();
        private final List<Invoice> globales = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            this.serveur = helper.getLevel().getServer();
            PayeurDEssai.inscrire();
            this.ref = PayeurDEssai.creancier();
            boite(helper);
        }

        /** Un nom propre à cet essai (16 caractères au plus), noté « somme de départ déjà reçue ». */
        String nom(String court) {
            String suffixe = Long.toString(System.nanoTime(), 36);
            String nom = court + "_" + suffixe.substring(Math.max(0, suffixe.length() - (15 - court.length())));
            UUID uuid = identifiant(nom);
            comptes.add(uuid);
            EconomyLedger.get(serveur).markStartingGiven(uuid);
            return nom;
        }

        /** Un joueur de survie neuf, inventaire vide. */
        JoueurDeTest joueur(String court, Vec3 position) {
            return connecter(nom(court), position, true);
        }

        JoueurDeTest connecter(String nom, Vec3 position, boolean vider) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(position), vider, p -> {});
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            return j;
        }

        /** Un coffre-fort noté pour ce joueur, loin de tout (le compte marche sans bloc ni chunk chargé). */
        BlockPos coffreFort(UUID joueur, Map<Coin, Long> contenu) {
            int n;
            synchronized (PrelevementGameTests.class) {
                n = coffresPoses++;
            }
            // Dernière rangée d'un chunk lointain, jamais chargé par un joueur d'essai.
            BlockPos pos = new BlockPos(1_000_000 + 16 * n, 64, 1_000_015);
            SafeAccounts comptes = SafeAccounts.get(serveur);
            comptes.setSafePos(joueur, GlobalPos.of(Level.OVERWORLD, pos));
            contenu.forEach((coin, count) -> comptes.deposit(joueur, coin, count));
            return pos;
        }

        void attente(UUID joueur, List<ItemStack> objets, Map<Coin, Long> pieces) {
            HeldGoods.get(serveur).addPending(joueur, objets, pieces);
        }

        /** Une facture publique du créancier d'essai, dans le registre détaché. */
        Invoice facture(UUID debiteur, String nom, long montant) {
            return registre.issuePublic(PayeurDEssai.GENRE, ref, new UUID(0L, 86L), "Essai", debiteur, nom, montant,
                    "essai", System.currentTimeMillis());
        }

        /** Une facture publique du créancier d'essai, dans le registre du serveur (annulée à la fin si due). */
        Invoice factureGlobale(UUID debiteur, String nom, long montant) {
            Invoice f = InvoiceRegistry.get(serveur).issuePublic(PayeurDEssai.GENRE, ref, new UUID(0L, 86L), "Essai",
                    debiteur, nom, montant, "essai", System.currentTimeMillis());
            globales.add(f);
            return f;
        }

        long porte(@Nullable JoueurDeTest j) {
            return j == null || !j.connecte() ? 0L : CoinMath.valueOf(PlayerCoins.held(j.joueur()));
        }

        long enAttente(UUID joueur) {
            return CoinMath.valueOf(HeldGoods.get(serveur).pendingCoins(joueur));
        }

        long auCoffre(UUID joueur) {
            return SafeAccounts.get(serveur).total(joueur);
        }

        /** La somme conservée : ce que porte le débiteur (connecté), son attente, son coffre-fort, le créancier. */
        long somme(UUID joueur, @Nullable JoueurDeTest enLigne) {
            return porte(enLigne) + enAttente(joueur) + auCoffre(joueur) + PayeurDEssai.recu(ref);
        }

        Status statut(InvoiceRegistry r, Invoice f) {
            return r.get(f.id()).map(Invoice::status).orElse(null);
        }

        @Override
        public void close() {
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.joueur().getInventory().clearContent();
                    j.quitter();
                }
            }
            InvoiceRegistry global = InvoiceRegistry.get(serveur);
            for (Invoice f : globales) {
                global.cancel(f.id(), System.currentTimeMillis());
            }
            boite(helper);
            HeldGoods held = HeldGoods.get(serveur);
            SafeAccounts coffres = SafeAccounts.get(serveur);
            for (UUID uuid : comptes) {
                held.takePending(uuid);
                for (Coin coin : Coin.values()) {
                    coffres.withdraw(uuid, coin, coffres.count(uuid, coin));
                }
                coffres.forgetSafePos(uuid);
            }
            PayeurDEssai.oublier(ref);
        }
    }

    private interface Corps {
        void run(Essai essai);
    }

    private static void essai(GameTestHelper helper, Corps corps) {
        try (Essai essai = new Essai(helper)) {
            corps.run(essai);
        }
        helper.succeed();
    }

    /** La case de l'inventaire de {@code j} qui porte la facture {@code id}, ou -1. */
    private static int caseDe(JoueurDeTest j, UUID id) {
        for (int i = 0; i < j.joueur().getInventory().getContainerSize(); i++) {
            Optional<UUID> lu = InvoiceItem.idOf(j.joueur().getInventory().getItem(i));
            if (lu.isPresent() && lu.get().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : sources, refus, annulation, pause, factures de joueurs
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void dabordLInventaireBourseComprise(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_inv", POS_A);
            UUID u = j.uuid();
            j.joueur().getInventory().setItem(0, bourse(Coin.GOLD, 2));
            j.joueur().getInventory().setItem(1, new ItemStack(Coin.GOLD.item(), 1));
            e.attente(u, List.of(), pieces(Coin.GOLD, 5));
            e.coffreFort(u, pieces(Coin.GOLD, 5));
            long avant = e.somme(u, j);
            Invoice f = e.facture(u, j.joueur().getGameProfile().getName(), 250L);
            j.messagesRecus();

            Outcome r = DirectDebit.present(e.serveur, e.registre, f, System.currentTimeMillis());
            helper.assertTrue(r == Outcome.PAID_HELD, "payée par l'inventaire, vu " + r);
            helper.assertTrue(e.porte(j) == 50L, "300 portés moins 250 : 50, vu " + e.porte(j));
            helper.assertTrue(e.enAttente(u) == 500L && e.auCoffre(u) == 500L,
                    "attente et coffre-fort intacts : " + e.enAttente(u) + " / " + e.auCoffre(u));
            helper.assertTrue(PayeurDEssai.recu(e.ref) == 250L && PayeurDEssai.paiements(e.ref) == 1,
                    "le créancier reçoit 250 une fois, vu " + PayeurDEssai.recu(e.ref));
            helper.assertTrue(e.statut(e.registre, f) == Status.PAID, "payée : " + e.statut(e.registre, f));
            helper.assertTrue(j.messagesRecus().contains(DirectDebit.MESSAGE + "paid.held"), "avis « pris sur vous »");
            helper.assertTrue(e.somme(u, j) == avant, "somme conservée : " + avant + " -> " + e.somme(u, j));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void puisLAttente(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_att", POS_A);
            UUID u = j.uuid();
            j.joueur().getInventory().setItem(0, new ItemStack(Coin.COPPER.item(), 10));
            // En attente : une pièce en vrac et une bourse pleine.
            e.attente(u, List.of(bourse(Coin.GOLD, 3)), pieces(Coin.GOLD, 1));
            e.coffreFort(u, pieces(Coin.GOLD, 5));
            long avant = e.somme(u, j);
            Invoice f = e.facture(u, j.joueur().getGameProfile().getName(), 250L);
            j.messagesRecus();

            Outcome r = DirectDebit.present(e.serveur, e.registre, f, System.currentTimeMillis());
            helper.assertTrue(r == Outcome.PAID_PENDING, "payée par l'attente, vu " + r);
            helper.assertTrue(e.porte(j) == 10L, "l'inventaire (10) ne suffisait pas et reste intact, vu " + e.porte(j));
            helper.assertTrue(e.enAttente(u) == 150L, "400 en attente moins 250, vu " + e.enAttente(u));
            helper.assertTrue(e.auCoffre(u) == 500L, "coffre-fort intact, vu " + e.auCoffre(u));
            helper.assertTrue(PayeurDEssai.recu(e.ref) == 250L, "créancier : " + PayeurDEssai.recu(e.ref));
            helper.assertTrue(j.messagesRecus().contains(DirectDebit.MESSAGE + "paid.pending"), "avis « ce qui vous attend »");
            helper.assertTrue(e.somme(u, j) == avant, "somme conservée : " + avant + " -> " + e.somme(u, j));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void puisLeCoffreFort(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_cof", POS_A);
            UUID u = j.uuid();
            j.joueur().getInventory().setItem(0, new ItemStack(Coin.COPPER.item(), 10));
            e.attente(u, List.of(), pieces(Coin.SILVER, 2));
            e.coffreFort(u, pieces(Coin.GOLD, 3));
            long avant = e.somme(u, j);
            Invoice f = e.facture(u, j.joueur().getGameProfile().getName(), 250L);
            j.messagesRecus();

            Outcome r = DirectDebit.present(e.serveur, e.registre, f, System.currentTimeMillis());
            helper.assertTrue(r == Outcome.PAID_SAFE, "payée par le coffre-fort, vu " + r);
            helper.assertTrue(e.porte(j) == 10L && e.enAttente(u) == 20L, "inventaire et attente intacts : "
                    + e.porte(j) + " / " + e.enAttente(u));
            helper.assertTrue(e.auCoffre(u) == 50L, "300 moins 250, monnaie rendue au coffre-fort : " + e.auCoffre(u));
            helper.assertTrue(PayeurDEssai.recu(e.ref) == 250L, "créancier : " + PayeurDEssai.recu(e.ref));
            helper.assertTrue(j.messagesRecus().contains(DirectDebit.MESSAGE + "paid.safe"), "avis « coffre-fort »");
            helper.assertTrue(e.somme(u, j) == avant, "somme conservée : " + avant + " -> " + e.somme(u, j));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void deconnecteLeCoffreFortPaieMemeZoneNonChargee(GameTestHelper helper) {
        essai(helper, e -> {
            String nom = e.nom("pr_abs");
            JoueurDeTest j = e.connecter(nom, POS_A, true);
            UUID u = j.uuid();
            // Il porte assez sur lui, mais il part : ce qu'il porte ne compte plus.
            j.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 5));
            BlockPos coffre = e.coffreFort(u, pieces(Coin.GOLD, 3));
            j.quitter();
            helper.assertTrue(e.serveur.getPlayerList().getPlayer(u) == null, "le joueur est retiré de la liste");
            helper.assertTrue(!helper.getLevel().hasChunk(coffre.getX() >> 4, coffre.getZ() >> 4),
                    "la zone du coffre-fort n'est pas chargée");
            long avant = e.enAttente(u) + e.auCoffre(u) + PayeurDEssai.recu(e.ref);
            Invoice f = e.facture(u, nom, 250L);

            Outcome r = DirectDebit.present(e.serveur, e.registre, f, System.currentTimeMillis());
            helper.assertTrue(r == Outcome.PAID_SAFE, "absent : payée par le coffre-fort, vu " + r);
            helper.assertTrue(e.auCoffre(u) == 50L, "monnaie au coffre-fort, vu " + e.auCoffre(u));
            helper.assertTrue(e.enAttente(u) == 0L, "rien en attente (jamais au sol pour un absent)");
            helper.assertTrue(!helper.getLevel().hasChunk(coffre.getX() >> 4, coffre.getZ() >> 4),
                    "le prélèvement n'a pas chargé la zone");
            helper.assertTrue(e.enAttente(u) + e.auCoffre(u) + PayeurDEssai.recu(e.ref) == avant, "somme conservée");

            // De retour : il retrouve ce qu'il portait, et apprend le prélèvement fait pendant son absence.
            JoueurDeTest revenu = e.connecter(nom, POS_A, false);
            helper.assertTrue(e.porte(revenu) == 500L, "ce qu'il portait est intact : " + e.porte(revenu));
            revenu.messagesRecus();
            DirectDebit.announce(revenu.joueur(), e.registre);
            helper.assertTrue(revenu.messagesRecus().contains(DirectDebit.MESSAGE + "paid.safe"),
                    "avis du prélèvement fait pendant l'absence");
            DirectDebit.announce(revenu.joueur(), e.registre);
            helper.assertTrue(revenu.messagesRecus().isEmpty(), "l'avis ne se lit qu'une fois");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void sansAssezRienNeBougeEtLaFactureResteDue(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_ref", POS_A);
            UUID u = j.uuid();
            j.joueur().getInventory().setItem(0, bourse(Coin.COPPER, 10));
            e.attente(u, List.of(), pieces(Coin.SILVER, 2));
            e.coffreFort(u, pieces(Coin.GOLD, 1));
            EnumMap<Coin, Long> porte = PlayerCoins.held(j.joueur());
            EnumMap<Coin, Long> attente = HeldGoods.get(e.serveur).pendingCoins(u);
            EnumMap<Coin, Long> coffre = SafeAccounts.get(e.serveur).balance(u);
            long refusAvant = EconomyLedger.get(e.serveur).refusedDebits();
            long publicAvant = EconomyLedger.get(e.serveur).totalTransferred(MoneyTransfer.PUBLIC_BILL);
            Invoice f = e.facture(u, j.joueur().getGameProfile().getName(), 250L);
            j.messagesRecus();

            Outcome r = DirectDebit.present(e.serveur, e.registre, f, System.currentTimeMillis());
            helper.assertTrue(r == Outcome.REFUSED, "130 en tout pour 250 : refus, vu " + r);
            helper.assertTrue(PlayerCoins.held(j.joueur()).equals(porte), "inventaire identique");
            helper.assertTrue(HeldGoods.get(e.serveur).pendingCoins(u).equals(attente), "attente identique");
            helper.assertTrue(SafeAccounts.get(e.serveur).balance(u).equals(coffre), "coffre-fort identique");
            helper.assertTrue(e.statut(e.registre, f) == Status.DUE, "la facture reste due");
            helper.assertTrue(PayeurDEssai.recu(e.ref) == 0L && PayeurDEssai.refus(e.ref) == 1,
                    "le créancier ne reçoit rien et apprend le refus");
            helper.assertTrue(j.messagesRecus().contains(DirectDebit.MESSAGE + "refused"), "le débiteur est prévenu");
            helper.assertTrue(EconomyLedger.get(e.serveur).refusedDebits() >= refusAvant + 1L, "refus compté");
            helper.assertTrue(EconomyLedger.get(e.serveur).totalTransferred(MoneyTransfer.PUBLIC_BILL) == publicAvant,
                    "aucun transfert noté");

            // Absent au refus : il l'apprend à sa connexion, avec le lien de copie, une seule fois.
            String nom = e.nom("pr_refA");
            JoueurDeTest absent = e.connecter(nom, POS_B, true);
            UUID ua = absent.uuid();
            absent.quitter();
            Invoice g = e.facture(ua, nom, 40L);
            helper.assertTrue(DirectDebit.present(e.serveur, e.registre, g, System.currentTimeMillis()) == Outcome.REFUSED,
                    "absent sans rien : refus");
            JoueurDeTest revenu = e.connecter(nom, POS_B, true);
            revenu.messagesRecus();
            DirectDebit.announce(revenu.joueur(), e.registre);
            helper.assertTrue(revenu.messagesRecus().contains(DirectDebit.MESSAGE + "refused"), "refus lu à la connexion");
            DirectDebit.announce(revenu.joueur(), e.registre);
            helper.assertTrue(revenu.messagesRecus().isEmpty(), "le refus ne se lit qu'une fois");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unCreancierDisparuAnnuleLaFacture(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_dis", POS_A);
            UUID u = j.uuid();
            j.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 5));
            long avant = e.somme(u, j);
            Invoice f = e.facture(u, j.joueur().getGameProfile().getName(), 250L);
            PayeurDEssai.disparaitre(e.ref);
            j.messagesRecus();

            Outcome r = DirectDebit.present(e.serveur, e.registre, f, System.currentTimeMillis());
            helper.assertTrue(r == Outcome.CANCELLED, "créancier disparu : annulée, vu " + r);
            helper.assertTrue(e.statut(e.registre, f) == Status.CANCELLED, "statut : " + e.statut(e.registre, f));
            helper.assertTrue(e.porte(j) == 500L && PayeurDEssai.recu(e.ref) == 0L, "rien n'a bougé");
            helper.assertTrue(!e.registre.duePublic().contains(e.registre.get(f.id()).orElseThrow()), "plus due");
            helper.assertTrue(j.messagesRecus().contains(DirectDebit.MESSAGE + "cancelled"), "le débiteur est prévenu");
            helper.assertTrue(DirectDebit.present(e.serveur, e.registre, f, System.currentTimeMillis()) == Outcome.NOT_DUE,
                    "une facture annulée ne se présente plus");
            helper.assertTrue(e.somme(u, j) == avant, "somme conservée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void laPauseDeLaPaieArreteLePrelevement(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_pau", POS_A);
            UUID u = j.uuid();
            e.coffreFort(u, pieces(Coin.GOLD, 3));
            long avant = e.somme(u, j);
            Invoice f = e.facture(u, j.joueur().getGameProfile().getName(), 250L);
            Payroll paie = new Payroll();
            paie.pause(J.plus(3, java.time.temporal.ChronoUnit.DAYS), J);
            e.registre.setLastDebitDay(J.minusDays(1));
            long midi = minuit(J) + 12L * 60L * MINUTE;

            int clos = DailyDebit.closeDueDays(e.serveur, e.registre, paie, midi);
            helper.assertTrue(clos == 0, "en pause, aucun jour clos : " + clos);
            helper.assertTrue(e.statut(e.registre, f) == Status.DUE && e.auCoffre(u) == 300L, "rien n'est prélevé");
            helper.assertTrue(J.minusDays(1).equals(e.registre.lastDebitDay()), "le jour n'est pas noté clos");

            paie.unpause();
            clos = DailyDebit.closeDueDays(e.serveur, e.registre, paie, midi);
            helper.assertTrue(clos == 1, "pause levée : le jour J se clôt, vu " + clos);
            helper.assertTrue(e.statut(e.registre, f) == Status.PAID && e.auCoffre(u) == 50L, "prélevée à la reprise");
            helper.assertTrue(e.somme(u, j) == avant, "somme conservée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unJoueurNePeutPasEmettreDeFacturePrelevable(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("pr_emA", POS_A);
            JoueurDeTest b = e.joueur("pr_emB", POS_B);
            b.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 5));
            e.coffreFort(b.uuid(), pieces(Coin.GOLD, 5));
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EconomyRegistry.INVOICE_BOOK.get()));
            InvoiceService.resetCooldown(a.uuid());
            IssueResult emise = InvoiceService.issue(a.joueur(), b.uuid(), 250L, "facture du carnet");
            helper.assertTrue(emise == IssueResult.DONE, "émission : " + emise);
            InvoiceRegistry global = InvoiceRegistry.get(e.serveur);
            List<Invoice> dues = global.owedBy(b.uuid());
            Invoice f = dues.get(dues.size() - 1);
            long avant = e.porte(b) + e.auCoffre(b.uuid());
            helper.assertTrue(!f.isPublic() && f.payee().isEmpty(), "une facture de joueur est privée : " + f.payee());
            helper.assertTrue(!global.duePublic().contains(f), "jamais dans les factures publiques dues");
            helper.assertTrue(DirectDebit.present(e.serveur, global, f, System.currentTimeMillis()) == Outcome.NOT_DUE,
                    "jamais présentée");
            helper.assertTrue(f.equals(global.get(f.id()).orElseThrow()) && f.status() == Status.DUE, "toujours due");
            helper.assertTrue(e.porte(b) + e.auCoffre(b.uuid()) == avant, "rien n'est prélevé");
            // Elle se règle de main à main (E2), ce qui n'est jamais permis à une facture publique.
            helper.assertTrue(global.settleByHand(f.id(), a.uuid(), System.currentTimeMillis()), "réglée de main à main");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : minuit, l'objet facture, une seule fois
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneFactureDueEstPresenteeAChaqueMinuit(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_min", POS_A);
            UUID u = j.uuid();
            e.coffreFort(u, pieces(Coin.SILVER, 5));
            Payroll paie = new Payroll();
            e.registre.setLastDebitDay(J.minusDays(1));
            Invoice f = e.facture(u, j.joueur().getGameProfile().getName(), 250L);
            long avant = e.somme(u, j);
            j.messagesRecus();

            helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J) - MINUTE) == 0,
                    "à 23 h 59 le jour n'est pas fini");
            helper.assertTrue(PayeurDEssai.refus(e.ref) == 0, "pas encore présentée");
            helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J) + MINUTE) == 1,
                    "à 0 h 01 le jour J se clôt");
            helper.assertTrue(PayeurDEssai.refus(e.ref) == 1 && e.statut(e.registre, f) == Status.DUE,
                    "présentée une fois, refusée (50 au coffre-fort), toujours due");
            helper.assertTrue(j.messagesRecus().contains(DirectDebit.MESSAGE + "refused"), "avis du refus");

            SafeAccounts.get(e.serveur).deposit(u, Coin.GOLD, 3);
            avant += 300L;
            helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J.plusDays(1)) - MINUTE) == 0,
                    "à 23 h 59 du lendemain, rien");
            helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J.plusDays(1)) + MINUTE) == 1,
                    "à 0 h 01 du lendemain, le jour J + 1 se clôt");
            helper.assertTrue(e.statut(e.registre, f) == Status.PAID, "présentée une seconde fois, payée");
            helper.assertTrue(PayeurDEssai.refus(e.ref) == 1 && PayeurDEssai.paiements(e.ref) == 1
                    && PayeurDEssai.recu(e.ref) == 250L, "un refus puis un paiement");
            helper.assertTrue(e.auCoffre(u) == 100L, "350 moins 250 au coffre-fort, vu " + e.auCoffre(u));
            helper.assertTrue(j.messagesRecus().contains(DirectDebit.MESSAGE + "paid.safe"), "avis du paiement");
            helper.assertTrue(e.somme(u, j) == avant, "somme conservée : " + avant + " -> " + e.somme(u, j));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void elleSePaieAussiEnUtilisantLObjet(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_obj", POS_A);
            UUID u = j.uuid();
            j.joueur().getInventory().setItem(1, bourse(Coin.GOLD, 3));
            e.coffreFort(u, pieces(Coin.GOLD, 5));
            Invoice f = e.factureGlobale(u, j.joueur().getGameProfile().getName(), 250L);
            j.joueur().getInventory().setItem(0, InvoiceItem.of(f));
            long avant = e.somme(u, j);

            PayResult r = InvoiceService.pay(j.joueur(), 0);
            helper.assertTrue(r == PayResult.PAID_TO_INVENTORY, "payée par l'objet, vu " + r);
            helper.assertTrue(e.statut(InvoiceRegistry.get(e.serveur), f) == Status.PAID, "payée au registre");
            helper.assertTrue(PayeurDEssai.recu(e.ref) == 250L, "le créancier est crédité : " + PayeurDEssai.recu(e.ref));
            helper.assertTrue(e.porte(j) == 50L && e.auCoffre(u) == 500L,
                    "pris dans l'inventaire seulement : " + e.porte(j) + " / " + e.auCoffre(u));
            ItemStack recu = j.joueur().getInventory().getItem(0);
            helper.assertTrue(recu.is(EconomyRegistry.RECEIPT.get())
                    && Receipt.KIND_INVOICE_PAID.equals(Receipt.kind(recu)), "un reçu dans la case : " + recu);
            helper.assertTrue(e.somme(u, j) == avant, "somme conservée");

            // Pas assez sur soi : le coffre-fort n'est pas pris par l'objet, rien ne bouge.
            Invoice g = e.factureGlobale(u, j.joueur().getGameProfile().getName(), 300L);
            j.joueur().getInventory().setItem(2, InvoiceItem.of(g));
            helper.assertTrue(InvoiceService.pay(j.joueur(), 2) == PayResult.CANNOT_PAY, "50 sur soi pour 300");
            helper.assertTrue(e.statut(InvoiceRegistry.get(e.serveur), g) == Status.DUE && e.somme(u, j) == avant,
                    "rien n'a bougé");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneCopieNeSePaiePas(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_cop", POS_A);
            JoueurDeTest autre = e.joueur("pr_copX", POS_B);
            UUID u = j.uuid();
            j.joueur().getInventory().setItem(5, new ItemStack(Coin.GOLD.item(), 9));
            Invoice f = e.factureGlobale(u, j.joueur().getGameProfile().getName(), 250L);
            helper.assertTrue(InvoiceService.copy(autre.joueur(), f.id().toString()) == CopyResult.NOT_FOUND,
                    "un autre ne reçoit pas la copie");
            helper.assertTrue(caseDe(autre, f.id()) < 0, "rien chez l'autre");
            helper.assertTrue(InvoiceService.copy(j.joueur(), f.id().toString()) == CopyResult.DONE, "copie 1");
            int premiere = caseDe(j, f.id());
            // Un second papier de la même facture (comme une copie faite avant le tour 1, ou un papier dupliqué).
            j.joueur().getInventory().setItem(8, j.joueur().getInventory().getItem(premiere).copy());
            long avant = e.somme(u, j);

            helper.assertTrue(InvoiceService.pay(j.joueur(), premiere) == PayResult.PAID_TO_INVENTORY, "la première paie");
            long apres = e.porte(j);
            helper.assertTrue(apres == 650L, "900 moins 250 : " + apres);
            PayResult seconde = InvoiceService.pay(j.joueur(), 8);
            helper.assertTrue(seconde == PayResult.ALREADY_SETTLED, "une copie ne paie pas : " + seconde);
            helper.assertTrue(e.porte(j) == apres && PayeurDEssai.recu(e.ref) == 250L, "rien de plus n'a bougé");
            helper.assertTrue(InvoiceService.copy(j.joueur(), f.id().toString()) == CopyResult.NOT_DUE,
                    "plus de copie d'une facture payée");
            helper.assertTrue(e.somme(u, j) == avant, "somme conservée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneCopieAuPlusParFacture(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_cp1", POS_A);
            UUID u = j.uuid();
            Invoice f = e.factureGlobale(u, j.joueur().getGameProfile().getName(), 250L);
            HeldGoods held = HeldGoods.get(e.serveur);

            // Appels répétés (par l'identifiant complet ou court) : un seul papier au total.
            helper.assertTrue(InvoiceService.copy(j.joueur(), f.id().toString()) == CopyResult.DONE, "première copie");
            for (int i = 0; i < 20; i++) {
                CopyResult r = InvoiceService.copy(j.joueur(), i % 2 == 0 ? f.id().toString() : f.shortId());
                helper.assertTrue(r == CopyResult.ALREADY_HELD, "copie " + (i + 2) + " : " + r);
            }
            helper.assertTrue(j.joueur().getInventory().countItem(EconomyRegistry.INVOICE.get()) == 1,
                    "un seul papier dans l'inventaire");
            helper.assertTrue(!held.hasPending(u), "rien en attente");

            // Le papier en attente compte aussi : pas de second papier dans l'inventaire.
            int c = caseDe(j, f.id());
            ItemStack papier = j.joueur().getInventory().getItem(c);
            j.joueur().getInventory().setItem(c, ItemStack.EMPTY);
            held.addPending(u, List.of(papier), Map.of());
            helper.assertTrue(InvoiceService.copy(j.joueur(), f.id().toString()) == CopyResult.ALREADY_HELD,
                    "papier en attente : refus");
            helper.assertTrue(j.joueur().getInventory().countItem(EconomyRegistry.INVOICE.get()) == 0, "rien ajouté");
            held.takePending(u);

            // Inventaire plein : refus, et jamais d'envoi en attente.
            for (int i = 0; i < 36; i++) {
                j.joueur().getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.DIRT, 64));
            }
            for (int i = 0; i < 5; i++) {
                CopyResult r = InvoiceService.copy(j.joueur(), f.id().toString());
                helper.assertTrue(r == CopyResult.NO_ROOM, "inventaire plein : " + r);
            }
            helper.assertTrue(!held.hasPending(u), "l'attente ne grossit pas : " + held.pendingFor(u).items().size());
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unCreancierEnFauteNeDetruitRien(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_err", POS_A);
            UUID u = j.uuid();
            j.joueur().getInventory().setItem(3, new ItemStack(Coin.GOLD.item(), 2));
            e.attente(u, List.of(bourse(Coin.SILVER, 9)), pieces(Coin.SILVER, 3));
            e.coffreFort(u, pieces(Coin.GOLD, 4));
            PayeurDEssai.pieger(e.ref, true);
            long avant = e.somme(u, j);
            long publicAvant = EconomyLedger.get(e.serveur).totalTransferred(MoneyTransfer.PUBLIC_BILL);

            // Par la présentation (sources mêlées : inventaire, attente avec bourse, coffre-fort).
            Invoice f = e.facture(u, j.joueur().getGameProfile().getName(), 450L);
            Outcome r = DirectDebit.present(e.serveur, e.registre, f, System.currentTimeMillis());
            helper.assertTrue(r == Outcome.FAILED, "créancier en faute : " + r);
            helper.assertTrue(e.statut(e.registre, f) == Status.DUE, "la facture reste due");
            helper.assertTrue(e.somme(u, j) == avant, "aucune pièce détruite : " + avant + " -> " + e.somme(u, j));

            // Par la clôture de minuit : pas de plantage, la facture reste due.
            e.registre.setLastDebitDay(J.minusDays(1));
            int clos = DailyDebit.closeDueDays(e.serveur, e.registre, new Payroll(), minuit(J) + MINUTE);
            helper.assertTrue(clos == 1 && e.statut(e.registre, f) == Status.DUE, "clôture sans plantage : " + clos);
            helper.assertTrue(e.somme(u, j) == avant, "toujours rien de détruit");

            // Par l'objet.
            Invoice g = e.factureGlobale(u, j.joueur().getGameProfile().getName(), 100L);
            j.joueur().getInventory().setItem(30, InvoiceItem.of(g));
            long porte = e.porte(j);
            PayResult p = InvoiceService.pay(j.joueur(), 30);
            helper.assertTrue(p == PayResult.CANNOT_PAY, "objet, créancier en faute : " + p);
            helper.assertTrue(e.statut(InvoiceRegistry.get(e.serveur), g) == Status.DUE, "toujours due");
            helper.assertTrue(InvoiceItem.idOf(j.joueur().getInventory().getItem(30)).isPresent(), "pas de reçu");
            helper.assertTrue(e.porte(j) + e.enAttente(u) + e.auCoffre(u) == porte + e.enAttente(u) + e.auCoffre(u)
                    && e.somme(u, j) == avant, "rien de détruit par l'objet : " + e.somme(u, j));
            helper.assertTrue(EconomyLedger.get(e.serveur).totalTransferred(MoneyTransfer.PUBLIC_BILL) == publicAvant,
                    "aucun transfert noté");

            // Le créancier réparé : la même facture se paie normalement.
            PayeurDEssai.pieger(e.ref, false);
            helper.assertTrue(DirectDebit.present(e.serveur, e.registre, f, System.currentTimeMillis()).name()
                    .startsWith("PAID_"), "payée une fois le créancier réparé");
            helper.assertTrue(PayeurDEssai.recu(e.ref) == 450L && e.somme(u, j) == avant, "somme conservée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneFactureNeSePaieQuUneFois(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_une", POS_A);
            UUID u = j.uuid();
            j.joueur().getInventory().setItem(5, new ItemStack(Coin.GOLD.item(), 9));
            InvoiceRegistry global = InvoiceRegistry.get(e.serveur);
            long avant = e.somme(u, j);

            // Présentée à minuit, puis l'objet utilisé dans le même tic.
            Invoice f = e.factureGlobale(u, j.joueur().getGameProfile().getName(), 250L);
            j.joueur().getInventory().setItem(0, InvoiceItem.of(f));
            helper.assertTrue(DirectDebit.present(e.serveur, global, f, System.currentTimeMillis()) == Outcome.PAID_HELD,
                    "présentée et payée");
            PayResult r = InvoiceService.pay(j.joueur(), 0);
            helper.assertTrue(r == PayResult.ALREADY_SETTLED, "l'objet ne paie plus : " + r);

            // L'objet d'abord, puis la présentation dans le même tic.
            Invoice g = e.factureGlobale(u, j.joueur().getGameProfile().getName(), 250L);
            // Une case libre (la monnaie rendue au premier paiement a pris la case 1).
            j.joueur().getInventory().setItem(20, InvoiceItem.of(g));
            helper.assertTrue(InvoiceService.pay(j.joueur(), 20) == PayResult.PAID_TO_INVENTORY, "payée par l'objet");
            Outcome o = DirectDebit.present(e.serveur, global, g, System.currentTimeMillis());
            helper.assertTrue(o == Outcome.NOT_DUE, "plus présentée : " + o);

            helper.assertTrue(PayeurDEssai.paiements(e.ref) == 2 && PayeurDEssai.recu(e.ref) == 500L,
                    "deux factures, deux paiements : " + PayeurDEssai.recu(e.ref));
            helper.assertTrue(e.porte(j) == 400L, "900 moins 500 : " + e.porte(j));
            helper.assertTrue(e.somme(u, j) == avant, "somme conservée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void deuxCloturesDuMemeJourNePrelevePasDeuxFois(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("pr_two", POS_A);
            UUID u = j.uuid();
            e.coffreFort(u, pieces(Coin.GOLD, 10));
            Payroll paie = new Payroll();
            e.registre.setLastDebitDay(J.minusDays(1));
            long avant = e.somme(u, j);
            PayeurDEssai.Emission emission = PayeurDEssai.armer(e.registre, e.ref, u,
                    j.joueur().getGameProfile().getName(), 100L);
            try {
                helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J) + MINUTE) == 1,
                        "minuit : le jour J se clôt");
                helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J) + MINUTE) == 0,
                        "démarrage dans le même tic : rien");
                helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J) - 60L * MINUTE) == 0,
                        "horloge qui recule : rien");
                helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J) + 2L * MINUTE) == 0,
                        "horloge qui revient : rien");
                helper.assertTrue(emission.jours().equals(List.of(J)), "émis une seule fois pour J : " + emission.jours());
                helper.assertTrue(e.registre.owedBy(u).size() == 1, "une seule facture : " + e.registre.owedBy(u).size());
                helper.assertTrue(PayeurDEssai.paiements(e.ref) == 1 && e.auCoffre(u) == 900L,
                        "un seul prélèvement : " + e.auCoffre(u));

                helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J.plusDays(1)) + MINUTE)
                        == 1, "le lendemain se clôt");
                helper.assertTrue(emission.jours().equals(List.of(J, J.plusDays(1))), "jours : " + emission.jours());
                helper.assertTrue(PayeurDEssai.paiements(e.ref) == 2 && e.auCoffre(u) == 800L, "second prélèvement");
                helper.assertTrue(e.somme(u, j) == avant, "somme conservée");
            } finally {
                PayeurDEssai.desarmer(emission);
            }
        });
    }
}
