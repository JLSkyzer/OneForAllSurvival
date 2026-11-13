package dev.oneforallsurvival.dev;

import static dev.oneforallsurvival.dev.EtalStockGameTests.clicDroit;
import static dev.oneforallsurvival.dev.EtalStockGameTests.compter;
import static dev.oneforallsurvival.dev.EtalStockGameTests.donner;
import static dev.oneforallsurvival.dev.EtalStockGameTests.entite;
import static dev.oneforallsurvival.dev.EtalStockGameTests.menuGestion;
import static dev.oneforallsurvival.dev.EtalStockGameTests.objets;
import static dev.oneforallsurvival.dev.EtalStockGameTests.poserEtVerifier;
import static dev.oneforallsurvival.dev.EtalStockGameTests.valeurPortee;

import dev.oneforallsurvival.dev.EtalStockGameTests.Essai;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallActionPacket;
import dev.oneforallsurvival.economy.stall.StallBuyback;
import dev.oneforallsurvival.economy.stall.StallLedger;
import dev.oneforallsurvival.economy.stall.StallLedger.Kind;
import dev.oneforallsurvival.economy.stall.StallLedgerPacket;
import dev.oneforallsurvival.economy.stall.StallOffer;
import dev.oneforallsurvival.economy.stall.StallOwnerMenu;
import dev.oneforallsurvival.economy.stall.StallResult;
import dev.oneforallsurvival.economy.stall.StallShopMenu;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * L'étal de marché, seconde partie (lot E2, tâche 4) : achat, rachat, taxe, registre. Chaque achat et chaque rachat
 * passe par {@link StallActionPacket#apply} (ou par le réseau, {@link JoueurDeTest#envoyer}, quand le moment compte),
 * avec la vitrine réellement ouverte par un clic droit. « Rien ne bouge » se vérifie sur un relevé complet de
 * l'inventaire du joueur, du stock, de la caisse, du registre de l'étal et du registre global.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class EtalVenteGameTests {

    private static final String LOT_A = "etal_vente_a";
    private static final String LOT_B = "etal_vente_b";
    /** La taxe est un réglage global : son lot à lui, réglé avant et rendu après. */
    private static final String LOT_TAXE = "etal_vente_taxe";

    private static final BlockPos A = new BlockPos(1, 0, 1);

    private static Integer taxeAvant;

    private EtalVenteGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static ForgeConfigSpec.ConfigValue<Integer> reglageTaxe() {
        Object valeur = EconomyConfig.SPEC.getValues().get("salesTaxPercent");
        if (!(valeur instanceof ForgeConfigSpec.ConfigValue<?>)) {
            throw new IllegalStateException("réglage salesTaxPercent introuvable : " + valeur);
        }
        return (ForgeConfigSpec.ConfigValue<Integer>) valeur;
    }

    @BeforeBatch(batch = LOT_TAXE)
    public static void avantLaTaxe(ServerLevel level) {
        ForgeConfigSpec.ConfigValue<Integer> reglage = reglageTaxe();
        taxeAvant = reglage.get();
        reglage.set(10);
    }

    @AfterBatch(batch = LOT_TAXE)
    public static void apresLaTaxe(ServerLevel level) {
        if (taxeAvant != null) {
            reglageTaxe().set(taxeAvant);
            taxeAvant = null;
        }
    }

    /** Un étal posé par {@code proprio} en A, {@code pains} pains en stock, une offre : 4 pains pour 30 cuivres. */
    private static MarketStallBlockEntity etalDuPain(GameTestHelper helper, JoueurDeTest proprio, int pains) {
        poserEtVerifier(helper, proprio, A);
        MarketStallBlockEntity be = entite(helper, A);
        if (pains > 0) {
            be.stock().setStackInSlot(0, new ItemStack(Items.BREAD, pains));
        }
        helper.assertTrue(be.setOffer(0, new StallOffer(new ItemStack(Items.BREAD), 4, 30L)), "l'offre devait s'écrire");
        return be;
    }

    /** Un rachat : 8 blés pour 16 cuivres, {@code lots} lots. */
    private static void rachatDuBle(GameTestHelper helper, MarketStallBlockEntity be, int lots) {
        helper.assertTrue(be.setBuyback(0, new StallBuyback(new ItemStack(Items.WHEAT), 8, 16L, lots)), "le rachat devait s'écrire");
    }

    /** Ouvre la vitrine par un clic droit réel. */
    private static StallShopMenu vitrine(GameTestHelper helper, JoueurDeTest j) {
        clicDroit(helper, j, A);
        helper.assertTrue(j.joueur().containerMenu instanceof StallShopMenu, "la vitrine devait s'ouvrir, vu " + j.joueur().containerMenu);
        return (StallShopMenu) j.joueur().containerMenu;
    }

    /** Le paquet qu'enverrait le bouton « Acheter » de l'offre {@code index}, avec ce que l'écran affiche. */
    private static StallActionPacket achat(MarketStallBlockEntity be, int index) {
        StallOffer offre = be.offers().get(index);
        return StallActionPacket.buy(be.getBlockPos(), index, offre.price(), offre.lot(), be.version());
    }

    private static StallActionPacket vente(MarketStallBlockEntity be, int index) {
        StallBuyback rachat = be.buybacks().get(index);
        return StallActionPacket.sellBack(be.getBlockPos(), index, rachat.price(), rachat.lot(), be.version());
    }

    private static StallResult agir(JoueurDeTest j, StallActionPacket paquet) {
        return StallActionPacket.apply(j.joueur(), paquet);
    }

    private static void attendu(GameTestHelper helper, StallResult attendu, StallResult vu, String quoi) {
        helper.assertTrue(attendu == vu, quoi + " : " + attendu + " attendu, vu " + vu);
    }

    /** Un relevé de tout ce qui pourrait bouger : inventaire du joueur, stock, caisse, registres, sol. */
    private static String releve(GameTestHelper helper, JoueurDeTest j, MarketStallBlockEntity be) {
        StringBuilder out = new StringBuilder("inv[");
        Inventory inv = j.joueur().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                out.append(i).append('=').append(s.getCount()).append(' ').append(s.getItem()).append(s.getTag() == null ? "" : s.getTag())
                        .append(';');
            }
        }
        out.append("] stock[");
        for (int i = 0; i < be.stock().getSlots(); i++) {
            ItemStack s = be.stock().getStackInSlot(i);
            if (!s.isEmpty()) {
                out.append(i).append('=').append(s.getCount()).append(' ').append(s.getItem()).append(';');
            }
        }
        EconomyLedger global = EconomyLedger.get(helper.getLevel().getServer());
        out.append("] caisse").append(be.cash()).append(" registre=").append(be.ledger().size())
                .append(" ventes=").append(global.totalTransferred(MoneyTransfer.STALL_SALE))
                .append(" rachats=").append(global.totalTransferred(MoneyTransfer.STALL_BUYBACK))
                .append(" taxe=").append(global.totalTransferred(MoneyTransfer.SALES_TAX))
                .append(" tresor=").append(TreasuryAccount.get(helper.getLevel().getServer()).total())
                .append(" offres=").append(be.offers()).append(" rachatsOfferts=").append(be.buybacks())
                .append(" sol=").append(objets(helper).size());
        return out.toString();
    }

    private static void rienNABouge(GameTestHelper helper, String avant, JoueurDeTest j, MarketStallBlockEntity be) {
        String apres = releve(helper, j, be);
        helper.assertTrue(avant.equals(apres), "rien ne devait bouger :\navant " + avant + "\napres " + apres);
    }

    private static long valeurDe(EnumMap<Coin, Long> pieces) {
        return CoinMath.valueOf(pieces);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : achat
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lEtalVendVendeurAbsent(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("vente_absent_proprio");
            JoueurDeTest client = essai.joueur("vente_absent_client");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 8);
            EconomyLedger global = EconomyLedger.get(essai.serveur());
            long ventes = global.totalTransferred(MoneyTransfer.STALL_SALE);
            proprio.quitter();
            helper.assertTrue(essai.serveur().getPlayerList().getPlayer(proprio.uuid()) == null, "le propriétaire devait être parti");

            donner(client, Coin.COPPER, 30);
            vitrine(helper, client);
            helper.assertTrue(be.available(0) == 2, "2 lots en stock, vu " + be.available(0));
            attendu(helper, StallResult.DONE, agir(client, achat(be, 0)), "achat");
            helper.assertTrue(client.joueur().getInventory().countItem(Items.BREAD) == 4, "4 pains reçus");
            helper.assertTrue(valeurPortee(client) == 0L, "les 30 cuivres payés, reste " + valeurPortee(client));
            helper.assertTrue(be.cashValue() == 30L, "la caisse reçoit 30 cuivres, vu " + be.cash());
            helper.assertTrue(compter(be.stock(), Items.BREAD) == 4 && be.available(0) == 1, "4 pains restent, un lot");
            List<StallLedger.Line> lignes = be.ledger().page(0);
            helper.assertTrue(lignes.size() == 1, "une ligne au registre de l'étal : " + lignes);
            StallLedger.Line ligne = lignes.get(0);
            helper.assertTrue(ligne.kind() == Kind.SALE && client.uuid().equals(ligne.actor()) && "minecraft:bread".equals(ligne.itemId())
                    && ligne.count() == 4 && ligne.amount() == 30L, "ligne SALE : " + ligne);
            helper.assertTrue(global.totalTransferred(MoneyTransfer.STALL_SALE) - ventes == 30L, "transfert STALL_SALE de 30");
            helper.assertTrue(objets(helper).isEmpty(), "rien au sol");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lAcheteurPaieAvecSaBourseEtRecoitLaMonnaie(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("vente_bourse_proprio");
            JoueurDeTest client = essai.joueur("vente_bourse_client");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 4);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.GOLD, 1);
            client.joueur().getInventory().add(bourse);
            vitrine(helper, client);
            attendu(helper, StallResult.DONE, agir(client, achat(be, 0)), "achat");
            ItemStack laBourse = Purse.firstPurse(client.joueur()).orElseThrow();
            EnumMap<Coin, Long> contenu = Purse.contents(laBourse);
            helper.assertTrue(contenu.size() == 1 && contenu.getOrDefault(Coin.SILVER, 0L) == 7L,
                    "la bourse garde 7 argent, vu " + contenu);
            helper.assertTrue(PlayerCoins.heldLoose(client.joueur()).isEmpty(), "aucune pièce hors de la bourse : "
                    + PlayerCoins.heldLoose(client.joueur()));
            helper.assertTrue(client.joueur().getInventory().countItem(Items.BREAD) == 4, "4 pains reçus");
            helper.assertTrue(be.cashValue() == 30L && be.stock().isEmpty(), "caisse 30, stock vide");
            helper.assertTrue(objets(helper).isEmpty(), "rien au sol");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void pasAssezDArgentRienNeBouge(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("vente_pauvre_proprio");
            JoueurDeTest client = essai.joueur("vente_pauvre_client");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 8);
            donner(client, Coin.COPPER, 29);
            vitrine(helper, client);
            String avant = releve(helper, client, be);
            attendu(helper, StallResult.CANNOT_PAY, agir(client, achat(be, 0)), "29 cuivres pour 30");
            rienNABouge(helper, avant, client, be);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void inventairePleinRienNeBouge(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("vente_plein_proprio");
            JoueurDeTest client = essai.joueur("vente_plein_client");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 8);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.COPPER, 30);
            Inventory inv = client.joueur().getInventory();
            inv.setItem(0, bourse);
            for (int i = 1; i < 36; i++) {
                inv.setItem(i, new ItemStack(Items.DIRT, 64));
            }
            vitrine(helper, client);
            String avant = releve(helper, client, be);
            attendu(helper, StallResult.NO_ROOM, agir(client, achat(be, 0)), "inventaire plein");
            rienNABouge(helper, avant, client, be);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void stockInsuffisantRefuse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("vente_stock_proprio");
            JoueurDeTest client = essai.joueur("vente_stock_client");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 3);
            donner(client, Coin.COPPER, 30);
            StallShopMenu menu = vitrine(helper, client);
            helper.assertTrue(be.available(0) == 0, "3 pains : aucun lot de 4, vu " + be.available(0));
            menu.broadcastChanges();
            helper.assertTrue(menu.saleAvailable(0) == 0, "la vitrine montre 0 lot, vu " + menu.saleAvailable(0));
            String avant = releve(helper, client, be);
            attendu(helper, StallResult.OUT_OF_STOCK, agir(client, achat(be, 0)), "3 pains pour un lot de 4");
            rienNABouge(helper, avant, client, be);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void prixChangeEntreLAffichageEtLAchatRefuse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("vente_prix_proprio");
            JoueurDeTest client = essai.joueur("vente_prix_client");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 8);
            donner(client, Coin.COPPER, 100);
            vitrine(helper, client);
            StallActionPacket vu = achat(be, 0);
            int version = be.version();
            // Le propriétaire monte le prix pendant que le client regarde.
            helper.assertTrue(be.setOffer(0, new StallOffer(new ItemStack(Items.BREAD), 4, 40L)), "nouveau prix");
            helper.assertTrue(be.version() > version, "chaque écriture d'offre change la version");
            String avant = releve(helper, client, be);
            attendu(helper, StallResult.CHANGED, agir(client, vu), "prix vu 30, réel 40");
            // Même avec la version à jour, le prix vu ne correspond pas : refusé aussi.
            attendu(helper, StallResult.CHANGED,
                    agir(client, StallActionPacket.buy(be.getBlockPos(), 0, 30L, 4, be.version())), "prix périmé, version à jour");
            attendu(helper, StallResult.CHANGED,
                    agir(client, StallActionPacket.buy(be.getBlockPos(), 0, 40L, 2, be.version())), "lot vu faux");
            rienNABouge(helper, avant, client, be);
            // Même prix et même lot, mais l'objet a changé (et il est en stock) : la version seule refuse.
            be.stock().setStackInSlot(1, new ItemStack(Items.DIRT, 4));
            StallActionPacket vuAvantEchange = achat(be, 0);
            helper.assertTrue(be.setOffer(0, new StallOffer(new ItemStack(Items.DIRT), 4, 40L)), "autre objet, même prix");
            String avantEchange = releve(helper, client, be);
            attendu(helper, StallResult.CHANGED, agir(client, vuAvantEchange), "objet changé, prix et lot identiques");
            rienNABouge(helper, avantEchange, client, be);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void deuxAcheteursLeDernierLot(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("vente_deux_proprio");
            JoueurDeTest un = essai.joueur("vente_deux_un");
            JoueurDeTest deux = essai.joueur("vente_deux_deux");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 4);
            donner(un, Coin.COPPER, 30);
            donner(deux, Coin.COPPER, 30);
            vitrine(helper, un);
            vitrine(helper, deux);
            long total = valeurPortee(un) + valeurPortee(deux) + be.cashValue();
            un.messagesRecus();
            deux.messagesRecus();
            StallActionPacket p1 = achat(be, 0);
            StallActionPacket p2 = achat(be, 0);
            long tick = helper.getLevel().getGameTime();
            // Les deux paquets passent par le réseau, dans le même tick, l'un après l'autre.
            un.envoyer(p1);
            deux.envoyer(p2);
            helper.assertTrue(helper.getLevel().getGameTime() == tick, "les deux paquets devaient être traités dans le même tick");
            List<String> m1 = un.messagesRecus();
            List<String> m2 = deux.messagesRecus();
            String fait = StallActionPacket.messageKey(StallActionPacket.BUY, StallResult.DONE);
            String epuise = StallActionPacket.messageKey(StallActionPacket.BUY, StallResult.OUT_OF_STOCK);
            helper.assertTrue(m1.contains(fait) && m2.contains(epuise), "un DONE puis un OUT_OF_STOCK, vu " + m1 + " / " + m2);
            helper.assertTrue(un.joueur().getInventory().countItem(Items.BREAD) == 4
                    && deux.joueur().getInventory().countItem(Items.BREAD) == 0, "un seul lot livré");
            helper.assertTrue(valeurPortee(deux) == 30L, "le second garde ses 30 cuivres");
            helper.assertTrue(valeurPortee(un) + valeurPortee(deux) + be.cashValue() == total,
                    "argent conservé : " + total + " avant, " + (valeurPortee(un) + valeurPortee(deux) + be.cashValue()) + " après");
            helper.assertTrue(be.stock().isEmpty() && be.ledger().size() == 1, "stock vide, une seule vente");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unObjetAuNbtDifferentNeSeVendPas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("vente_nbt_proprio");
            JoueurDeTest client = essai.joueur("vente_nbt_client");
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            ItemStack nommee = new ItemStack(Items.DIAMOND_SWORD);
            nommee.setHoverName(Component.literal("Excalibur"));
            be.stock().setStackInSlot(0, nommee);
            helper.assertTrue(be.setOffer(0, new StallOffer(new ItemStack(Items.DIAMOND_SWORD), 1, 10L)), "offre");
            helper.assertTrue(be.available(0) == 0, "l'épée nommée n'est pas celle du modèle, vu " + be.available(0));
            donner(client, Coin.COPPER, 10);
            vitrine(helper, client);
            String avant = releve(helper, client, be);
            attendu(helper, StallResult.OUT_OF_STOCK, agir(client, achat(be, 0)), "modèle sans nom, stock nommé");
            rienNABouge(helper, avant, client, be);
            // Le modèle garde son NBT : l'épée nommée, elle, se vend.
            helper.assertTrue(be.setOffer(1, new StallOffer(nommee.copy(), 1, 10L)), "offre nommée");
            helper.assertTrue(be.available(1) == 1, "un lot de l'épée nommée");
            attendu(helper, StallResult.DONE, agir(client, achat(be, 1)), "épée nommée");
            helper.assertTrue(client.joueur().getInventory().countItem(Items.DIAMOND_SWORD) == 1, "l'épée livrée");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : rachat, droits, registre, conservation
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void rachatCaisseInsuffisanteRefuse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("rachat_caisse_proprio");
            JoueurDeTest vendeur = essai.joueur("rachat_caisse_vendeur");
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            rachatDuBle(helper, be, 3);
            be.addCash(Coin.SILVER, 1);
            vendeur.joueur().getInventory().add(new ItemStack(Items.WHEAT, 8));
            vitrine(helper, vendeur);
            String avant = releve(helper, vendeur, be);
            attendu(helper, StallResult.CASH_SHORT, agir(vendeur, vente(be, 0)), "10 cuivres en caisse pour 16");
            rienNABouge(helper, avant, vendeur, be);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void rachatStockPleinRefuse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("rachat_plein_proprio");
            JoueurDeTest vendeur = essai.joueur("rachat_plein_vendeur");
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            rachatDuBle(helper, be, 3);
            be.addCash(Coin.GOLD, 1);
            for (int i = 0; i < be.stock().getSlots(); i++) {
                be.stock().setStackInSlot(i, new ItemStack(Items.DIRT, 64));
            }
            // Une case presque pleine de blé : 4 de place, pas 8.
            be.stock().setStackInSlot(5, new ItemStack(Items.WHEAT, 60));
            vendeur.joueur().getInventory().add(new ItemStack(Items.WHEAT, 8));
            vitrine(helper, vendeur);
            String avant = releve(helper, vendeur, be);
            attendu(helper, StallResult.STOCK_FULL, agir(vendeur, vente(be, 0)), "stock plein");
            rienNABouge(helper, avant, vendeur, be);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void rachatEpuiseRefuse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("rachat_epuise_proprio");
            JoueurDeTest vendeur = essai.joueur("rachat_epuise_vendeur");
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            rachatDuBle(helper, be, 1);
            be.addCash(Coin.GOLD, 1);
            vendeur.joueur().getInventory().add(new ItemStack(Items.WHEAT, 16));
            vitrine(helper, vendeur);
            attendu(helper, StallResult.DONE, agir(vendeur, vente(be, 0)), "premier lot");
            helper.assertTrue(be.buybacks().get(0).lotsLeft() == 0, "plus aucun lot à racheter");
            String avant = releve(helper, vendeur, be);
            attendu(helper, StallResult.OUT_OF_STOCK, agir(vendeur, vente(be, 0)), "rachat épuisé");
            rienNABouge(helper, avant, vendeur, be);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leProprietaireNAchetePasChezLui(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("vente_soi_proprio");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 8);
            rachatDuBle(helper, be, 3);
            be.addCash(Coin.GOLD, 1);
            donner(proprio, Coin.COPPER, 30);
            proprio.joueur().getInventory().add(new ItemStack(Items.WHEAT, 8));
            // Accroupi, main vide (le jeu n'appelle pas le bloc pour un accroupi qui tient un objet) : le propriétaire
            // ouvre sa vitrine comme un client.
            proprio.joueur().getInventory().selected = 8;
            helper.assertTrue(proprio.joueur().getMainHandItem().isEmpty(), "main vide");
            proprio.joueur().setShiftKeyDown(true);
            try {
                vitrine(helper, proprio);
            } finally {
                proprio.joueur().setShiftKeyDown(false);
            }
            String avant = releve(helper, proprio, be);
            attendu(helper, StallResult.OWN_STALL, agir(proprio, achat(be, 0)), "achat chez soi");
            attendu(helper, StallResult.OWN_STALL, agir(proprio, vente(be, 0)), "revente à soi");
            rienNABouge(helper, avant, proprio, be);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unePieceRefuseeCommeModele(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("modele_proprio");
            JoueurDeTest autre = essai.joueur("modele_autre");
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            BlockPos abs = be.getBlockPos();
            clicDroit(helper, proprio, A);
            StallOwnerMenu menu = menuGestion(helper, proprio);
            int version = be.version();

            menu.setCarried(new ItemStack(Coin.GOLD.item(), 3));
            attendu(helper, StallResult.BAD_TEMPLATE, agir(proprio, StallActionPacket.setOffer(abs, 0, 1, 10L)), "pièce en vente");
            attendu(helper, StallResult.BAD_TEMPLATE, agir(proprio, StallActionPacket.setBuyback(abs, 0, 1, 10L, 5)), "pièce en rachat");
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.COPPER, 10);
            menu.setCarried(bourse);
            attendu(helper, StallResult.BAD_TEMPLATE, agir(proprio, StallActionPacket.setOffer(abs, 0, 1, 10L)), "bourse pleine");
            menu.setCarried(ItemStack.EMPTY);
            attendu(helper, StallResult.BAD_TEMPLATE, agir(proprio, StallActionPacket.setOffer(abs, 0, 1, 10L)), "curseur vide");
            helper.assertTrue(be.offers().get(0) == null && be.buybacks().get(0) == null && be.version() == version,
                    "aucune offre écrite, version inchangée");

            // Bornes : lot 1 à 64, prix 1 au maximum des factures, lots de rachat 1 à 9 999, offre dans les cases permises.
            menu.setCarried(new ItemStack(Items.BREAD, 5));
            attendu(helper, StallResult.BAD_REQUEST, agir(proprio, StallActionPacket.setOffer(abs, 0, 65, 10L)), "lot 65");
            attendu(helper, StallResult.BAD_REQUEST, agir(proprio, StallActionPacket.setOffer(abs, 0, 1, 0L)), "prix 0");
            attendu(helper, StallResult.BAD_REQUEST,
                    agir(proprio, StallActionPacket.setOffer(abs, 0, 1, EconomyConfig.invoiceMaxAmount() + 1)), "prix trop haut");
            attendu(helper, StallResult.BAD_REQUEST, agir(proprio, StallActionPacket.setBuyback(abs, 0, 1, 10L, 10_000)), "10 000 lots");
            attendu(helper, StallResult.BAD_REQUEST,
                    agir(proprio, StallActionPacket.setOffer(abs, EconomyConfig.stallMaxOffers(), 1, 10L)), "case au-delà");

            // Un autre joueur n'écrit rien, même avec la vitrine ouverte.
            vitrine(helper, autre);
            autre.joueur().containerMenu.setCarried(new ItemStack(Items.DIRT));
            attendu(helper, StallResult.NOT_ALLOWED, agir(autre, StallActionPacket.setOffer(abs, 0, 1, 10L)), "un autre");
            autre.joueur().containerMenu.setCarried(ItemStack.EMPTY);

            // Le pain : la copie de modèle est gardée, le curseur n'est pas touché.
            attendu(helper, StallResult.DONE, agir(proprio, StallActionPacket.setOffer(abs, 0, 4, 30L)), "pain");
            StallOffer offre = be.offers().get(0);
            helper.assertTrue(offre != null && offre.template().is(Items.BREAD) && offre.template().getCount() == 1
                    && offre.lot() == 4 && offre.price() == 30L, "offre écrite : " + offre);
            helper.assertTrue(menu.getCarried().is(Items.BREAD) && menu.getCarried().getCount() == 5, "le curseur garde ses 5 pains");
            helper.assertTrue(be.version() == version + 1, "version + 1");
            attendu(helper, StallResult.DONE, agir(proprio, StallActionPacket.setBuyback(abs, 2, 8, 16L, 5)), "rachat de pain");
            helper.assertTrue(be.buybacks().get(2) != null && be.buybacks().get(2).lotsLeft() == 5 && be.version() == version + 2,
                    "rachat écrit");
            attendu(helper, StallResult.DONE, agir(proprio, StallActionPacket.clearOffer(abs, 0)), "retirer l'offre");
            attendu(helper, StallResult.DONE, agir(proprio, StallActionPacket.clearBuyback(abs, 2)), "retirer le rachat");
            helper.assertTrue(be.offers().get(0) == null && be.buybacks().get(2) == null && be.version() == version + 4, "retirés");
            menu.setCarried(ItemStack.EMPTY);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leRegistreDeLEtalNEstEnvoyeQuAuProprietaire(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("registre_proprio");
            JoueurDeTest client = essai.joueur("registre_client");
            JoueurDeTest chef = essai.equipe("registre_chef");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 8);
            BlockPos abs = be.getBlockPos();
            donner(client, Coin.COPPER, 30);
            vitrine(helper, client);
            attendu(helper, StallResult.DONE, agir(client, achat(be, 0)), "une vente pour remplir le registre");
            proprio.registresEtalRecus();
            client.registresEtalRecus();
            chef.registresEtalRecus();

            // Le client, vitrine ouverte, demande le registre : refusé, rien reçu.
            client.envoyer(StallActionPacket.requestLedger(abs, 0));
            helper.assertTrue(client.registresEtalRecus().isEmpty(), "le client ne reçoit pas le registre");
            attendu(helper, StallResult.NOT_ALLOWED, agir(client, StallActionPacket.requestLedger(abs, 0)), "client");

            // Le propriétaire, gestion ouverte : il le reçoit, lui seul.
            clicDroit(helper, proprio, A);
            menuGestion(helper, proprio);
            proprio.envoyer(StallActionPacket.requestLedger(abs, 0));
            List<StallLedgerPacket> recus = proprio.registresEtalRecus();
            helper.assertTrue(recus.size() == 1, "une page reçue par le propriétaire, vu " + recus.size());
            StallLedgerPacket page = recus.get(0);
            helper.assertTrue(page.pos().equals(abs) && page.page() == 0 && page.pages() == 1 && page.lines().size() == 1
                    && page.lines().get(0).kind() == Kind.SALE && page.lines().get(0).amount() == 30L, "contenu de la page : " + page);
            helper.assertTrue(client.registresEtalRecus().isEmpty() && chef.registresEtalRecus().isEmpty(),
                    "personne d'autre ne reçoit la page du propriétaire");

            // Sans la gestion ouverte, même le propriétaire ne reçoit rien.
            proprio.joueur().closeContainer();
            attendu(helper, StallResult.NOT_OPEN, agir(proprio, StallActionPacket.requestLedger(abs, 0)), "gestion fermée");
            helper.assertTrue(proprio.registresEtalRecus().isEmpty(), "rien sans la gestion");

            // L'équipe, qui gère, le reçoit aussi.
            clicDroit(helper, chef, A);
            menuGestion(helper, chef);
            chef.envoyer(StallActionPacket.requestLedger(abs, 0));
            helper.assertTrue(chef.registresEtalRecus().size() == 1 && proprio.registresEtalRecus().isEmpty()
                    && client.registresEtalRecus().isEmpty(), "l'équipe reçoit sa page, les autres rien");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void aucunePieceCreeeNiDetruite(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("conserve_proprio");
            JoueurDeTest client = essai.joueur("conserve_client");
            JoueurDeTest vendeur = essai.joueur("conserve_vendeur");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 8);
            rachatDuBle(helper, be, 2);
            donner(client, Coin.GOLD, 1);
            vendeur.joueur().getInventory().add(new ItemStack(Items.WHEAT, 8));
            TreasuryAccount tresor = TreasuryAccount.get(essai.serveur());
            EconomyLedger global = EconomyLedger.get(essai.serveur());
            long circulation = global.inCirculation();
            long total = valeurPortee(client) + valeurPortee(vendeur) + valeurPortee(proprio) + be.cashValue() + tresor.total();

            vitrine(helper, client);
            attendu(helper, StallResult.DONE, agir(client, achat(be, 0)), "vente");
            vitrine(helper, vendeur);
            attendu(helper, StallResult.DONE, agir(vendeur, vente(be, 0)), "rachat payé sur les 30 de la vente");
            helper.assertTrue(valeurPortee(vendeur) == 16L && be.cashValue() == 14L, "vendeur 16, caisse 14 : "
                    + valeurPortee(vendeur) + " / " + be.cash());
            long apres = valeurPortee(client) + valeurPortee(vendeur) + valeurPortee(proprio) + be.cashValue() + tresor.total();
            helper.assertTrue(apres == total, "somme inventaires + caisse + trésor : " + total + " avant, " + apres + " après");
            helper.assertTrue(global.inCirculation() == circulation, "inCirculation inchangé : " + circulation + " -> "
                    + global.inCirculation());
            helper.assertTrue(objets(helper).isEmpty(), "rien au sol");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot de la taxe (10 % le temps du lot)
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_TAXE)
    public static void laTaxeArriveAuTresor(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            helper.assertTrue(EconomyConfig.salesTaxPercent() == 10, "la taxe devait être réglée à 10 %, vu "
                    + EconomyConfig.salesTaxPercent());
            JoueurDeTest proprio = essai.joueur("taxe_proprio");
            JoueurDeTest client = essai.joueur("taxe_client");
            MarketStallBlockEntity be = etalDuPain(helper, proprio, 8);
            donner(client, Coin.COPPER, 30);
            TreasuryAccount tresor = TreasuryAccount.get(essai.serveur());
            EconomyLedger global = EconomyLedger.get(essai.serveur());
            long tresorAvant = tresor.total();
            long taxe = global.totalTransferred(MoneyTransfer.SALES_TAX);
            long ventes = global.totalTransferred(MoneyTransfer.STALL_SALE);
            long circulation = global.inCirculation();
            vitrine(helper, client);
            attendu(helper, StallResult.DONE, agir(client, achat(be, 0)), "achat taxé");
            helper.assertTrue(tresor.total() - tresorAvant == 3L, "trésor + 3, vu " + (tresor.total() - tresorAvant));
            helper.assertTrue(be.cashValue() == 27L, "caisse + 27, vu " + be.cash());
            helper.assertTrue(valeurPortee(client) == 0L, "le client paie 30");
            helper.assertTrue(global.totalTransferred(MoneyTransfer.SALES_TAX) - taxe == 3L, "registre global : taxe 3");
            helper.assertTrue(global.totalTransferred(MoneyTransfer.STALL_SALE) - ventes == 27L, "registre global : vente 27");
            helper.assertTrue(global.inCirculation() == circulation, "aucune pièce créée ni détruite");
            helper.assertTrue(be.ledger().page(0).get(0).amount() == 30L, "la ligne de l'étal garde le prix payé");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_TAXE)
    public static void rachatPayeSurLaCaisse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("rachat_paye_proprio");
            JoueurDeTest vendeur = essai.joueur("rachat_paye_vendeur");
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            rachatDuBle(helper, be, 3);
            be.addCash(Coin.SILVER, 2);
            vendeur.joueur().getInventory().add(new ItemStack(Items.WHEAT, 10));
            EconomyLedger global = EconomyLedger.get(essai.serveur());
            long rachats = global.totalTransferred(MoneyTransfer.STALL_BUYBACK);
            long taxe = global.totalTransferred(MoneyTransfer.SALES_TAX);
            int version = be.version();
            vitrine(helper, vendeur);
            attendu(helper, StallResult.DONE, agir(vendeur, vente(be, 0)), "rachat");
            helper.assertTrue(vendeur.joueur().getInventory().countItem(Items.WHEAT) == 2, "8 blés vendus, 2 gardés");
            helper.assertTrue(valeurPortee(vendeur) == 16L, "16 cuivres reçus (aucune taxe sur un rachat), vu " + valeurPortee(vendeur));
            helper.assertTrue(compter(be.stock(), Items.WHEAT) == 8, "8 blés en stock");
            helper.assertTrue(be.cashValue() == 4L, "la caisse casse un argent : reste 4, vu " + be.cash());
            helper.assertTrue(be.buybacks().get(0).lotsLeft() == 2, "lotsLeft décrémenté");
            helper.assertTrue(be.version() == version, "un rachat ne change pas la version (pas une écriture d'offre)");
            helper.assertTrue(global.totalTransferred(MoneyTransfer.STALL_BUYBACK) - rachats == 16L, "transfert STALL_BUYBACK de 16");
            helper.assertTrue(global.totalTransferred(MoneyTransfer.SALES_TAX) == taxe, "aucune taxe sur un rachat");
            StallLedger.Line ligne = be.ledger().page(0).get(0);
            helper.assertTrue(ligne.kind() == Kind.BUYBACK && vendeur.uuid().equals(ligne.actor()) && ligne.count() == 8
                    && ligne.amount() == 16L && "minecraft:wheat".equals(ligne.itemId()), "ligne BUYBACK : " + ligne);
            helper.assertTrue(objets(helper).isEmpty(), "rien au sol");
            helper.succeed();
        }
    }
}
