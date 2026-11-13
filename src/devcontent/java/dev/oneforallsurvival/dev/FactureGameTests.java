package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.Receipt;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.invoice.InvoiceFormPacket;
import dev.oneforallsurvival.economy.invoice.InvoiceIssuePacket;
import dev.oneforallsurvival.economy.invoice.InvoiceItem;
import dev.oneforallsurvival.economy.invoice.InvoicePayPacket;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status;
import dev.oneforallsurvival.economy.invoice.InvoiceService;
import dev.oneforallsurvival.economy.invoice.InvoiceService.IssueResult;
import dev.oneforallsurvival.economy.invoice.InvoiceService.PayResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La facture (lot E2, tâche 5, feature 72) : le carnet ouvre le formulaire, le serveur revalide tout à l'émission,
 * la facture arrive au débiteur (inventaire, sinon attente), seul le débiteur la paie, tout ou rien, monnaie rendue,
 * les pièces vont à l'émetteur (inventaire, coffre-fort, attente), la facture devient un reçu, une copie ne se paie
 * pas deux fois, un identifiant inconnu jamais ; l'émetteur peut la déclarer réglée de main à main ; le registre
 * survit au rechargement et oublie les factures réglées anciennes ; aucune pièce n'est créée ni détruite.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class FactureGameTests {

    private static final String LOT_A = "facture_a";
    private static final String LOT_B = "facture_b";
    private static final String LOT_C = "facture_c";

    private static final Vec3 POS_A = new Vec3(2.5, 0, 4.5);
    private static final Vec3 POS_B = new Vec3(5.5, 0, 4.5);
    private static final Vec3 POS_C = new Vec3(4.5, 0, 2.5);
    private static final BlockPos COFFRE = new BlockPos(1, 0, 1);

    private FactureGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

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

    /** Ce qu'un test a ouvert : tout est rendu à la fermeture, même si le test a échoué. */
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> comptes = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        InvoiceRegistry registre() {
            return InvoiceRegistry.get(serveur());
        }

        /** Un nom propre à cet essai (16 caractères au plus), noté « somme de départ déjà reçue ». */
        String nom(String court) {
            String suffixe = Long.toString(System.nanoTime(), 36);
            String nom = court + "_" + suffixe.substring(Math.max(0, suffixe.length() - (15 - court.length())));
            UUID uuid = identifiant(nom);
            comptes.add(uuid);
            EconomyLedger.get(serveur()).markStartingGiven(uuid);
            return nom;
        }

        /** Un joueur de survie neuf, inventaire vide. */
        JoueurDeTest joueur(String court, Vec3 position) {
            return connecter(nom(court), position, true);
        }

        /** Fait (re)venir ce joueur ; {@code vider} faux garde ce que l'entrée en jeu lui a livré. */
        JoueurDeTest connecter(String nom, Vec3 position, boolean vider) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(position), vider, p -> {});
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            return j;
        }

        @Override
        public void close() {
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            boite(helper);
            HeldGoods held = HeldGoods.get(serveur());
            SafeAccounts coffres = SafeAccounts.get(serveur());
            for (UUID uuid : comptes) {
                held.takePending(uuid);
                for (Coin coin : Coin.values()) {
                    coffres.withdraw(uuid, coin, coffres.count(uuid, coin));
                }
                coffres.forgetSafePos(uuid);
            }
        }
    }

    private static ItemStack carnet() {
        return new ItemStack(EconomyRegistry.INVOICE_BOOK.get());
    }

    private static ItemStack pieces(Coin coin, int nombre) {
        return new ItemStack(coin.item(), nombre);
    }

    private static long valeurPortee(JoueurDeTest j) {
        return CoinMath.valueOf(PlayerCoins.held(j.joueur()));
    }

    /** Émet une facture de {@code a} à {@code b} (carnet en main) et rend la facture enregistrée. */
    private static Invoice emettre(GameTestHelper helper, Essai essai, JoueurDeTest a, JoueurDeTest b, long montant,
            String motif) {
        a.joueur().setItemInHand(InteractionHand.MAIN_HAND, carnet());
        IssueResult r = InvoiceService.issue(a.joueur(), b.uuid(), montant, motif);
        helper.assertTrue(r == IssueResult.DONE, "émission : " + r);
        List<Invoice> dues = essai.registre().owedBy(b.uuid());
        helper.assertTrue(!dues.isEmpty(), "le registre doit connaître la facture");
        return dues.get(dues.size() - 1);
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

    private static boolean estUnRecu(ItemStack pile, String genre) {
        return pile.is(EconomyRegistry.RECEIPT.get()) && genre.equals(Receipt.kind(pile));
    }

    private static Status statut(Essai essai, UUID id) {
        return essai.registre().get(id).map(Invoice::status).orElse(null);
    }

    /** Remplit les 36 cases de terre : plus rien n'entre. */
    private static void remplir(ServerPlayer joueur) {
        for (int i = 0; i < 36; i++) {
            joueur.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Émettre
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leCarnetSurUnJoueurProcheOuvreLeFormulaire(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fa_formA", POS_A);
            JoueurDeTest b = essai.joueur("fa_formB", POS_B);
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, carnet());
            a.joueur().interactOn(b.joueur(), InteractionHand.MAIN_HAND);
            List<InvoiceFormPacket> recus = a.formulairesFactureRecus();
            helper.assertTrue(recus.size() == 1, "un formulaire attendu, vu " + recus.size());
            helper.assertTrue(recus.get(0).target().equals(b.uuid())
                    && recus.get(0).targetName().equals(b.joueur().getGameProfile().getName()),
                    "le formulaire vise B : " + recus.get(0));
            helper.assertTrue(b.formulairesFactureRecus().isEmpty(), "B ne reçoit rien");
            helper.assertTrue(a.joueur().getMainHandItem().is(EconomyRegistry.INVOICE_BOOK.get()), "le carnet ne s'use pas");
            helper.assertTrue(essai.registre().owedBy(b.uuid()).isEmpty(), "rien n'est émis avant le formulaire");

            // Sans carnet en main, le même geste n'ouvre rien.
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
            a.joueur().interactOn(b.joueur(), InteractionHand.MAIN_HAND);
            helper.assertTrue(a.formulairesFactureRecus().isEmpty(), "pas de formulaire sans carnet");

            // Le carnet se fabrique sur la table par n'importe qui (recette en données), jamais à l'établi.
            helper.assertTrue(carnet().is(dev.oneforallsurvival.bench.BenchCatalog.TABLE_ONLY), "carnet absent de table_only");
            helper.assertTrue(essai.serveur().getRecipeManager().byKey(
                    new net.minecraft.resources.ResourceLocation("oneforallsurvival", "invoice_book")).isPresent(),
                    "recette du carnet absente");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void tropLoinRefuse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fa_loinA", POS_A);
            JoueurDeTest b = essai.joueur("fa_loinB", POS_A.add(0, 0, 20));
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, carnet());
            a.joueur().interactOn(b.joueur(), InteractionHand.MAIN_HAND);
            helper.assertTrue(a.formulairesFactureRecus().isEmpty(), "pas de formulaire à 20 blocs");
            IssueResult r = InvoiceService.issue(a.joueur(), b.uuid(), 100L, "pain");
            helper.assertTrue(r == IssueResult.TOO_FAR, "à 20 blocs : " + r);
            helper.assertTrue(essai.registre().owedBy(b.uuid()).isEmpty(), "aucune facture");
            helper.assertTrue(b.joueur().getInventory().countItem(EconomyRegistry.INVOICE.get()) == 0, "B ne reçoit rien");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void montantsEtMotifsInvalidesRefuses(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fa_invA", POS_A);
            JoueurDeTest b = essai.joueur("fa_invB", POS_B);
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, carnet());
            long max = dev.oneforallsurvival.economy.EconomyConfig.invoiceMaxAmount();
            int motifMax = dev.oneforallsurvival.economy.EconomyConfig.invoiceReasonMax();
            helper.assertTrue(InvoiceService.issue(a.joueur(), b.uuid(), 0L, "pain") == IssueResult.BAD_AMOUNT, "0");
            helper.assertTrue(InvoiceService.issue(a.joueur(), b.uuid(), -5L, "pain") == IssueResult.BAD_AMOUNT, "-5");
            helper.assertTrue(InvoiceService.issue(a.joueur(), b.uuid(), max + 1L, "pain") == IssueResult.BAD_AMOUNT,
                    "max + 1");
            helper.assertTrue(InvoiceService.issue(a.joueur(), b.uuid(), 10L, "") == IssueResult.BAD_REASON, "vide");
            helper.assertTrue(InvoiceService.issue(a.joueur(), b.uuid(), 10L, "   ") == IssueResult.BAD_REASON, "blanc");
            helper.assertTrue(InvoiceService.issue(a.joueur(), b.uuid(), 10L, "x".repeat(motifMax + 1))
                    == IssueResult.BAD_REASON, "motif de " + (motifMax + 1));
            helper.assertTrue(InvoiceService.issue(a.joueur(), b.uuid(), 10L, "deux\nlignes") == IssueResult.BAD_REASON,
                    "saut de ligne");
            helper.assertTrue(InvoiceService.issue(a.joueur(), a.uuid(), 10L, "pain") == IssueResult.SELF, "soi-même");
            helper.assertTrue(InvoiceService.issue(a.joueur(), UUID.randomUUID(), 10L, "pain") == IssueResult.GONE,
                    "inconnu");
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(InvoiceService.issue(a.joueur(), b.uuid(), 10L, "pain") == IssueResult.NO_BOOK, "sans carnet");
            // Le même refus par le paquet du client : rien n'est émis.
            a.envoyer(new InvoiceIssuePacket(b.uuid(), 0L, "pain"));
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, carnet());
            a.envoyer(new InvoiceIssuePacket(b.uuid(), 10L, "x".repeat(motifMax + 1)));
            helper.assertTrue(essai.registre().owedBy(b.uuid()).isEmpty() && essai.registre().issuedBy(a.uuid()).isEmpty(),
                    "aucune facture ne devait naître");
            helper.assertTrue(InvoiceService.issue(a.joueur(), b.uuid(), max, "x".repeat(motifMax)) == IssueResult.DONE,
                    "les bornes elles-mêmes passent");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void emettreDonneUneFactureAuDebiteur(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fa_emA", POS_A);
            JoueurDeTest b = essai.joueur("fa_emB", POS_B);
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, carnet());
            a.messagesRecus();
            b.messagesRecus();
            a.envoyer(new InvoiceIssuePacket(b.uuid(), 250L, "Trois pains et une soupe"));
            List<Invoice> dues = essai.registre().owedBy(b.uuid());
            helper.assertTrue(dues.size() == 1, "une facture due : " + dues);
            Invoice f = dues.get(0);
            helper.assertTrue(f.status() == Status.DUE && f.amount() == 250L && f.issuer().equals(a.uuid())
                    && f.debtor().equals(b.uuid()) && f.reason().equals("Trois pains et une soupe"), "registre : " + f);
            helper.assertTrue(essai.registre().issuedBy(a.uuid()).size() == 1, "émise par A");
            int c = caseDe(b, f.id());
            helper.assertTrue(c >= 0, "la facture doit être chez B");
            ItemStack pile = b.joueur().getInventory().getItem(c);
            CompoundTag nbt = pile.getTag();
            helper.assertTrue(nbt != null && nbt.hasUUID("Id") && nbt.getUUID("Id").equals(f.id())
                    && nbt.getUUID("Issuer").equals(a.uuid()) && nbt.getUUID("Debtor").equals(b.uuid())
                    && nbt.getString("IssuerName").equals(a.joueur().getGameProfile().getName())
                    && nbt.getString("DebtorName").equals(b.joueur().getGameProfile().getName())
                    && nbt.getLong("Amount") == 250L && nbt.getString("Reason").equals("Trois pains et une soupe")
                    && nbt.getLong("IssuedAt") == f.issuedAt(), "NBT complet : " + nbt);
            helper.assertTrue(pile.getCount() == 1 && pile.getMaxStackSize() == 1, "une facture ne s'empile pas");
            helper.assertTrue(a.joueur().getMainHandItem().is(EconomyRegistry.INVOICE_BOOK.get()), "le carnet ne s'use pas");
            helper.assertTrue(a.messagesRecus().contains(InvoiceService.MESSAGE + "issued"), "A est prévenu");
            helper.assertTrue(b.messagesRecus().contains(InvoiceService.MESSAGE + "received"), "B est prévenu");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void inventairePleinLaFactureAttend(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fa_pleinA", POS_A);
            JoueurDeTest b = essai.joueur("fa_pleinB", POS_B);
            remplir(b.joueur());
            Invoice f = emettre(helper, essai, a, b, 40L, "corde");
            helper.assertTrue(caseDe(b, f.id()) == -1, "rien n'entre dans l'inventaire plein");
            helper.assertTrue(HeldGoods.get(essai.serveur()).hasPending(b.uuid()), "la facture attend");
            b.joueur().getInventory().setItem(5, ItemStack.EMPTY);
            CommerceDelivery.deliverPending(b.joueur());
            helper.assertTrue(caseDe(b, f.id()) == 5, "livrée dans la case libérée");
            helper.assertTrue(!HeldGoods.get(essai.serveur()).hasPending(b.uuid()), "plus rien n'attend");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Payer
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void payerPrendInventaireEtBoursesEtRendLaMonnaie(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fa_payA", POS_A);
            JoueurDeTest b = essai.joueur("fa_payB", POS_B);
            Invoice f = emettre(helper, essai, a, b, 250L, "laine");
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.GOLD, 2L);
            b.joueur().getInventory().setItem(10, bourse);
            b.joueur().getInventory().setItem(11, pieces(Coin.EMERALD, 1));
            helper.assertTrue(valeurPortee(b) == 1_200L, "B porte 1 200 : " + valeurPortee(b));
            long aAvant = valeurPortee(a);
            PayResult r = InvoiceService.pay(b.joueur(), caseDe(b, f.id()));
            helper.assertTrue(r == PayResult.PAID_TO_INVENTORY, "paiement : " + r);
            helper.assertTrue(valeurPortee(b) == 950L, "monnaie juste : 1 200 - 250 = 950, vu " + valeurPortee(b));
            helper.assertTrue(valeurPortee(a) - aAvant == 250L, "A reçoit 250 : " + (valeurPortee(a) - aAvant));
            helper.assertTrue(statut(essai, f.id()) == Status.PAID, "payée au registre");
            helper.assertTrue(!HeldGoods.get(essai.serveur()).hasPending(b.uuid()), "rien en attente pour B");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void pasAssezRienNeBouge(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fa_peuA", POS_A);
            JoueurDeTest b = essai.joueur("fa_peuB", POS_B);
            Invoice f = emettre(helper, essai, a, b, 250L, "laine");
            b.joueur().getInventory().setItem(10, pieces(Coin.GOLD, 2));
            b.joueur().getInventory().setItem(11, pieces(Coin.COPPER, 49));
            long aAvant = valeurPortee(a);
            long registreAvant = EconomyLedger.get(essai.serveur()).totalTransferred(MoneyTransfer.INVOICE);
            int c = caseDe(b, f.id());
            PayResult r = InvoiceService.pay(b.joueur(), c);
            helper.assertTrue(r == PayResult.CANNOT_PAY, "249 pour 250 : " + r);
            helper.assertTrue(valeurPortee(b) == 249L && valeurPortee(a) == aAvant, "aucune pièce ne bouge");
            helper.assertTrue(statut(essai, f.id()) == Status.DUE, "toujours due");
            helper.assertTrue(caseDe(b, f.id()) == c, "la facture reste une facture");
            helper.assertTrue(EconomyLedger.get(essai.serveur()).totalTransferred(MoneyTransfer.INVOICE) == registreAvant,
                    "rien au registre");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void seulLeDebiteurPaie(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fa_seulA", POS_A);
            JoueurDeTest b = essai.joueur("fa_seulB", POS_B);
            JoueurDeTest c = essai.joueur("fa_seulC", POS_C);
            Invoice f = emettre(helper, essai, a, b, 100L, "bois");
            // C tient une copie de la facture de B, et de quoi payer.
            ItemStack copie = b.joueur().getInventory().getItem(caseDe(b, f.id())).copy();
            c.joueur().getInventory().setItem(3, copie);
            c.joueur().getInventory().setItem(4, pieces(Coin.GOLD, 5));
            long aAvant = valeurPortee(a);
            PayResult r = InvoiceService.pay(c.joueur(), 3);
            helper.assertTrue(r == PayResult.NOT_DEBTOR, "C n'est pas le débiteur : " + r);
            helper.assertTrue(valeurPortee(c) == 500L && valeurPortee(a) == aAvant, "aucune pièce ne bouge");
            helper.assertTrue(statut(essai, f.id()) == Status.DUE, "toujours due");
            // L'émetteur non plus ne paie pas sa propre facture.
            a.joueur().getInventory().setItem(3, copie.copy());
            a.joueur().getInventory().setItem(4, pieces(Coin.GOLD, 5));
            helper.assertTrue(InvoiceService.pay(a.joueur(), 3) == PayResult.NOT_DEBTOR, "l'émetteur ne paie pas");
            helper.assertTrue(statut(essai, f.id()) == Status.DUE, "toujours due");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void emetteurConnecteRecoitDansSonInventaire(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fb_coA", POS_A);
            JoueurDeTest b = essai.joueur("fb_coB", POS_B);
            Invoice f = emettre(helper, essai, a, b, 1_234L, "outils");
            b.joueur().getInventory().setItem(10, pieces(Coin.EMERALD, 2));
            a.messagesRecus();
            PayResult r = InvoiceService.pay(b.joueur(), caseDe(b, f.id()));
            helper.assertTrue(r == PayResult.PAID_TO_INVENTORY, "paiement : " + r);
            helper.assertTrue(valeurPortee(a) == 1_234L, "A reçoit dans son inventaire : " + valeurPortee(a));
            helper.assertTrue(valeurPortee(b) == 766L, "B garde la monnaie : " + valeurPortee(b));
            helper.assertTrue(a.messagesRecus().contains(InvoiceService.MESSAGE + "paid.inventory"),
                    "A est prévenu tout de suite");
            helper.assertTrue(essai.registre().unseenPaid(a.uuid()).isEmpty(), "rien à aviser plus tard");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void emetteurHorsLigneAvecCoffreFort(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fb_cfA", POS_A);
            JoueurDeTest b = essai.joueur("fb_cfB", POS_B);
            Invoice f = emettre(helper, essai, a, b, 1_234L, "outils");
            SafeAccounts coffres = SafeAccounts.get(essai.serveur());
            coffres.setSafePos(a.uuid(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(COFFRE)));
            long coffreAvant = coffres.total(a.uuid());
            a.quitter();
            b.joueur().getInventory().setItem(10, pieces(Coin.EMERALD, 2));
            PayResult r = InvoiceService.pay(b.joueur(), caseDe(b, f.id()));
            helper.assertTrue(r == PayResult.PAID_TO_SAFE, "émetteur absent, coffre-fort : " + r);
            helper.assertTrue(coffres.total(a.uuid()) - coffreAvant == 1_234L,
                    "le coffre-fort reçoit la somme : " + (coffres.total(a.uuid()) - coffreAvant));
            helper.assertTrue(!HeldGoods.get(essai.serveur()).hasPending(a.uuid()), "rien en attente");
            helper.assertTrue(essai.registre().unseenPaid(a.uuid()).size() == 1, "un avis attend A");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void emetteurSansCoffreFortEnAttenteJusquaSaConnexion(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fb_attA", POS_A);
            JoueurDeTest b = essai.joueur("fb_attB", POS_B);
            String nomA = a.joueur().getGameProfile().getName();
            Invoice f = emettre(helper, essai, a, b, 1_234L, "outils");
            a.quitter();
            b.joueur().getInventory().setItem(10, pieces(Coin.EMERALD, 2));
            PayResult r = InvoiceService.pay(b.joueur(), caseDe(b, f.id()));
            helper.assertTrue(r == PayResult.PAID_PENDING, "émetteur absent sans coffre-fort : " + r);
            HeldGoods held = HeldGoods.get(essai.serveur());
            helper.assertTrue(CoinMath.valueOf(held.pendingFor(a.uuid()).coins()) == 1_234L,
                    "la somme attend A : " + held.pendingFor(a.uuid()).coins());
            List<Invoice> avis = essai.registre().unseenPaid(a.uuid());
            helper.assertTrue(avis.size() == 1 && avis.get(0).id().equals(f.id()), "un avis attend A : " + avis);
            helper.assertTrue(essai.registre().destinationOf(f.id()) == CommerceDelivery.Destination.PENDING,
                    "l'avis dit « en attente »");

            JoueurDeTest revenu = essai.connecter(nomA, POS_A, false);
            helper.assertTrue(valeurPortee(revenu) == 1_234L, "livré à la connexion : " + valeurPortee(revenu));
            helper.assertTrue(!held.hasPending(a.uuid()), "plus rien n'attend");
            helper.assertTrue(essai.registre().unseenPaid(a.uuid()).isEmpty(), "avisé à la connexion");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void payeeDevientUnRecu(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fb_recuA", POS_A);
            JoueurDeTest b = essai.joueur("fb_recuB", POS_B);
            Invoice f = emettre(helper, essai, a, b, 75L, "lait");
            b.joueur().getInventory().setItem(10, pieces(Coin.GOLD, 1));
            int c = caseDe(b, f.id());
            b.messagesRecus();
            // Par le paquet du client, comme le bouton « Payer ».
            b.envoyer(new InvoicePayPacket(c));
            helper.assertTrue(statut(essai, f.id()) == Status.PAID, "payée");
            ItemStack pile = b.joueur().getInventory().getItem(c);
            helper.assertTrue(estUnRecu(pile, Receipt.KIND_INVOICE_PAID), "la case porte un reçu de facture : " + pile
                    + " " + pile.getTag());
            helper.assertTrue(caseDe(b, f.id()) == -1, "plus aucune facture");
            helper.assertTrue(valeurPortee(b) == 25L && valeurPortee(a) == 75L, "75 payés, 25 rendus");
            helper.assertTrue(b.messagesRecus().contains(InvoiceService.MESSAGE + "pay.paid_to_inventory"),
                    "B voit le résultat");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneCopieNeSePaiePasDeuxFois(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fb_copA", POS_A);
            JoueurDeTest b = essai.joueur("fb_copB", POS_B);
            Invoice f = emettre(helper, essai, a, b, 300L, "fer");
            int c = caseDe(b, f.id());
            b.joueur().getInventory().setItem(20, b.joueur().getInventory().getItem(c).copy());
            b.joueur().getInventory().setItem(10, pieces(Coin.GOLD, 9));
            helper.assertTrue(InvoiceService.pay(b.joueur(), c) == PayResult.PAID_TO_INVENTORY, "premier paiement");
            long bApres = valeurPortee(b);
            long aApres = valeurPortee(a);
            long registre = EconomyLedger.get(essai.serveur()).totalTransferred(MoneyTransfer.INVOICE);
            PayResult r = InvoiceService.pay(b.joueur(), 20);
            helper.assertTrue(r == PayResult.ALREADY_SETTLED, "la copie : " + r);
            helper.assertTrue(valeurPortee(b) == bApres && valeurPortee(a) == aApres, "aucune pièce ne bouge");
            helper.assertTrue(EconomyLedger.get(essai.serveur()).totalTransferred(MoneyTransfer.INVOICE) == registre,
                    "rien de plus au registre");
            helper.assertTrue(estUnRecu(b.joueur().getInventory().getItem(20), Receipt.KIND_INVOICE_PAID),
                    "la copie devient un reçu");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unIdentifiantInconnuNeSePaiePas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fb_incA", POS_A);
            JoueurDeTest b = essai.joueur("fb_incB", POS_B);
            Invoice fausse = new Invoice(UUID.randomUUID(), a.uuid(), a.joueur().getGameProfile().getName(), b.uuid(),
                    b.joueur().getGameProfile().getName(), 100L, "faux", System.currentTimeMillis(), Status.DUE, 0L);
            b.joueur().getInventory().setItem(3, InvoiceItem.of(fausse));
            b.joueur().getInventory().setItem(10, pieces(Coin.GOLD, 5));
            PayResult r = InvoiceService.pay(b.joueur(), 3);
            helper.assertTrue(r == PayResult.UNKNOWN, "identifiant inconnu : " + r);
            helper.assertTrue(valeurPortee(b) == 500L && valeurPortee(a) == 0L, "aucune pièce ne bouge");
            helper.assertTrue(essai.registre().get(fausse.id()).isEmpty(), "le registre ne l'apprend pas");
            // Une pile qui n'est pas une facture, une case vide ou hors de l'inventaire : rien.
            helper.assertTrue(InvoiceService.pay(b.joueur(), 10) == PayResult.UNKNOWN, "des pièces ne sont pas une facture");
            helper.assertTrue(InvoiceService.pay(b.joueur(), 30) == PayResult.UNKNOWN, "case vide");
            helper.assertTrue(InvoiceService.pay(b.joueur(), 99) == PayResult.UNKNOWN, "case hors de l'inventaire");
            helper.assertTrue(InvoiceService.pay(b.joueur(), -1) == PayResult.UNKNOWN, "case négative");
            helper.assertTrue(valeurPortee(b) == 500L, "aucune pièce ne bouge");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void acquitterDeMainAMain(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fb_mainA", POS_A);
            JoueurDeTest b = essai.joueur("fb_mainB", POS_B);
            Invoice f = emettre(helper, essai, a, b, 500L, "charpente");
            int c = caseDe(b, f.id());
            long registre = EconomyLedger.get(essai.serveur()).totalTransferred(MoneyTransfer.INVOICE);
            MinecraftServer serveur = essai.serveur();
            // Le débiteur ne peut pas se déclarer quitte.
            serveur.getCommands().performPrefixedCommand(b.joueur().createCommandSourceStack(),
                    "invoice settle " + f.id());
            serveur.getCommands().performPrefixedCommand(b.joueur().createCommandSourceStack(),
                    "invoice settle " + f.id().toString().substring(0, 8));
            helper.assertTrue(!essai.registre().settleByHand(f.id(), b.uuid(), System.currentTimeMillis()),
                    "settleByHand par le débiteur");
            helper.assertTrue(statut(essai, f.id()) == Status.DUE, "toujours due");
            // La liste marche pour les deux.
            helper.assertTrue(serveur.getCommands().performPrefixedCommand(a.joueur().createCommandSourceStack(),
                    "invoice list") == 1, "/invoice list");
            // L'émetteur, par l'identifiant court.
            a.messagesRecus();
            serveur.getCommands().performPrefixedCommand(a.joueur().createCommandSourceStack(),
                    "invoice settle " + f.id().toString().substring(0, 8));
            helper.assertTrue(statut(essai, f.id()) == Status.SETTLED_BY_HAND, "réglée de main à main");
            helper.assertTrue(valeurPortee(a) == 0L && valeurPortee(b) == 0L, "aucune pièce ne bouge");
            helper.assertTrue(EconomyLedger.get(serveur).totalTransferred(MoneyTransfer.INVOICE) == registre,
                    "rien au registre des transferts");
            helper.assertTrue(b.joueur().getInventory().getItem(c).is(EconomyRegistry.INVOICE.get()),
                    "encore une facture avant le tick");
            for (int i = 0; i < 20; i++) {
                b.joueur().doTick();
            }
            helper.assertTrue(estUnRecu(b.joueur().getInventory().getItem(c), Receipt.KIND_INVOICE_BY_HAND),
                    "au tick suivant, un reçu « de main à main » : " + b.joueur().getInventory().getItem(c).getTag());
            b.joueur().getInventory().setItem(11, pieces(Coin.GOLD, 9));
            helper.assertTrue(InvoiceService.pay(b.joueur(), c) == PayResult.UNKNOWN, "un reçu ne se paie pas");
            helper.assertTrue(valeurPortee(b) == 900L, "aucune pièce ne bouge");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void leRegistreDesFacturesSurvitAuRechargement(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fc_rechA", POS_A);
            JoueurDeTest b = essai.joueur("fc_rechB", POS_B);
            Invoice due = emettre(helper, essai, a, b, 10L, "un motif « accentué »");
            Invoice payee = emettre(helper, essai, a, b, 20L, "deux");
            Invoice main = emettre(helper, essai, a, b, 30L, "trois");
            InvoiceRegistry registre = essai.registre();
            helper.assertTrue(registre.markPaid(payee.id(), 1_000L), "DUE -> PAID");
            helper.assertTrue(!registre.markPaid(payee.id(), 2_000L), "PAID -> PAID refusé");
            registre.noticeLater(payee.id(), CommerceDelivery.Destination.SAFE);
            helper.assertTrue(registre.settleByHand(main.id(), a.uuid(), 3_000L), "réglée de main à main");
            helper.assertTrue(!registre.markPaid(main.id(), 4_000L), "réglée -> PAID refusé");

            InvoiceRegistry relu = InvoiceRegistry.load(registre.save(new CompoundTag()));
            for (Invoice f : List.of(due, payee, main)) {
                helper.assertTrue(relu.get(f.id()).equals(registre.get(f.id())), "relu à l'identique : " + relu.get(f.id())
                        + " / " + registre.get(f.id()));
            }
            helper.assertTrue(relu.get(due.id()).get().reason().equals("un motif « accentué »"), "motif relu");
            helper.assertTrue(relu.get(payee.id()).get().status() == Status.PAID
                    && relu.get(payee.id()).get().settledAt() == 1_000L, "payée relue");
            helper.assertTrue(relu.get(main.id()).get().status() == Status.SETTLED_BY_HAND, "main à main relue");
            helper.assertTrue(relu.unseenPaid(a.uuid()).size() == 1 && relu.destinationOf(payee.id())
                    == CommerceDelivery.Destination.SAFE, "l'avis survit");
            helper.assertTrue(relu.owedBy(b.uuid()).size() == 3 && relu.issuedBy(a.uuid()).size() == 3, "listes relues");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void lesFacturesRegleesSontOubliees(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fc_oubA", POS_A);
            JoueurDeTest b = essai.joueur("fc_oubB", POS_B);
            Invoice vieille = emettre(helper, essai, a, b, 10L, "vieille");
            Invoice recente = emettre(helper, essai, a, b, 10L, "récente");
            Invoice due = emettre(helper, essai, a, b, 10L, "due");
            long jour = 86_400_000L;
            long garde = dev.oneforallsurvival.economy.EconomyConfig.invoiceKeepDays() * jour;
            long maintenant = System.currentTimeMillis();
            InvoiceRegistry registre = essai.registre();
            registre.markPaid(vieille.id(), maintenant - garde - jour);
            registre.markPaid(recente.id(), maintenant - garde + jour);
            registre.prune(maintenant);
            helper.assertTrue(registre.get(vieille.id()).isEmpty(), "réglée depuis plus longtemps : oubliée");
            helper.assertTrue(registre.get(recente.id()).isPresent(), "réglée depuis moins longtemps : gardée");
            helper.assertTrue(registre.get(due.id()).isPresent(), "une facture due n'est jamais oubliée");
            // Une vieille copie d'une facture oubliée ne se paie pas.
            b.joueur().getInventory().setItem(3, InvoiceItem.of(vieille));
            b.joueur().getInventory().setItem(10, pieces(Coin.GOLD, 1));
            helper.assertTrue(InvoiceService.pay(b.joueur(), 3) == PayResult.UNKNOWN, "copie d'une facture oubliée");
            helper.assertTrue(valeurPortee(b) == 100L, "aucune pièce ne bouge");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void aucunePieceCreeeNiDetruite(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("fc_zeroA", POS_A);
            JoueurDeTest b = essai.joueur("fc_zeroB", POS_B);
            EconomyLedger registre = EconomyLedger.get(essai.serveur());
            long enCirculation = registre.inCirculation();
            long factures = registre.totalTransferred(MoneyTransfer.INVOICE);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.SILVER, 7L);
            b.joueur().getInventory().setItem(10, bourse);
            b.joueur().getInventory().setItem(11, pieces(Coin.RUBY, 1));
            long total = valeurPortee(a) + valeurPortee(b);
            Invoice f = emettre(helper, essai, a, b, 4_321L, "maison");
            helper.assertTrue(InvoiceService.pay(b.joueur(), caseDe(b, f.id())) == PayResult.PAID_TO_INVENTORY, "payée");
            helper.assertTrue(valeurPortee(a) + valeurPortee(b) == total, "la somme des deux ne change pas : "
                    + total + " puis " + (valeurPortee(a) + valeurPortee(b)));
            helper.assertTrue(valeurPortee(a) == 4_321L, "A a reçu le montant");
            helper.assertTrue(registre.inCirculation() == enCirculation, "en circulation inchangé");
            helper.assertTrue(registre.totalTransferred(MoneyTransfer.INVOICE) - factures == 4_321L,
                    "transfert noté : " + (registre.totalTransferred(MoneyTransfer.INVOICE) - factures));
            helper.succeed();
        }
    }
}
