package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.debit.DailyDebit;
import dev.oneforallsurvival.economy.debit.DirectDebit;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status;
import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.economy.town.TreasuryLedger;
import dev.oneforallsurvival.grid.supplier.PowerBilling;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerPairing;
import dev.oneforallsurvival.grid.supplier.PowerSupplier;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers.StatementKind;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La facture quotidienne d'électricité (lot E6, tâche 4, feature 83) : à minuit, chaque client raccordé reçoit une
 * facture publique (consommation × prix + abonnement), prélevée automatiquement (tâche 1) ; la taxe va au trésor, le
 * reste à la caisse du poste. Tarifs changés dans la journée : à la facture suivante ; prix au-dessus d'un nouveau
 * plafond ramené à minuit ; serveur éteint deux jours : deux factures ; horloge qui recule : aucune.
 *
 * <p>Horloge fixe : chaque clôture passe une heure choisie ({@code minuit(jour) + 1 min}) à
 * {@link DailyDebit#closeDueDays}, sans toucher à l'horloge du serveur (que la paie surveille). Le registre des
 * fournisseurs et celui des factures sont détachés ; la facturation d'essai est installée sous le genre
 * {@code electricity} le temps d'un essai (tout se passe dans le même tic) puis celle du serveur est remise, et encore
 * à la fin du lot. À chaque essai, la somme (débiteur + caisse + trésor) est conservée.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class FactureElectriciteGameTests {

    private static final String LOT_A = "facture_elec_a";
    private static final String LOT_B = "facture_elec_b";

    private static final Vec3 POS_A = new Vec3(2.5, 0, 4.5);
    private static final Vec3 POS_B = new Vec3(5.5, 0, 4.5);

    /** Un jour quelconque, au milieu de l'année (pas de changement d'heure autour). */
    private static final LocalDate J = LocalDate.of(2026, 6, 14);
    private static final long MINUTE = 60_000L;

    private static int coffresPoses;

    private FactureElectriciteGameTests() {}

    @AfterBatch(batch = LOT_A)
    public static void apresA(ServerLevel level) {
        PowerBilling.install(PowerBilling.SERVER);
    }

    @AfterBatch(batch = LOT_B)
    public static void apresB(ServerLevel level) {
        PowerBilling.install(PowerBilling.SERVER);
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static long minuit(LocalDate jour) {
        return EconomyCalendar.boundaryAfter(jour, EconomyCalendar.configuredZone(), EconomyConfig.payHour());
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

    /** Ce qu'un essai a ouvert : tout est rendu à la fermeture, même si l'essai a échoué. */
    private static final class Essai implements AutoCloseable {
        final GameTestHelper helper;
        final MinecraftServer serveur;
        final InvoiceRegistry registre = InvoiceRegistry.load(new CompoundTag());
        final PowerSuppliers fournisseurs = new PowerSuppliers();
        final PowerBilling facturation = new PowerBilling(fournisseurs, registre);
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> comptes = new ArrayList<>();
        private final EnumMap<Coin, Long> tresorAvant;
        private final long publicAvant;
        private final long taxeAvant;
        PowerSupplier fournisseur;

        Essai(GameTestHelper helper) {
            this.helper = helper;
            this.serveur = helper.getLevel().getServer();
            this.tresorAvant = TreasuryAccount.get(serveur).balance();
            this.publicAvant = EconomyLedger.get(serveur).totalTransferred(MoneyTransfer.PUBLIC_BILL);
            this.taxeAvant = EconomyLedger.get(serveur).totalTransferred(MoneyTransfer.POWER_TAX);
            PowerBilling.install(facturation);
        }

        String nom(String court) {
            String suffixe = Long.toString(System.nanoTime(), 36);
            String nom = court + "_" + suffixe.substring(Math.max(0, suffixe.length() - (15 - court.length())));
            UUID uuid = identifiant(nom);
            comptes.add(uuid);
            EconomyLedger.get(serveur).markStartingGiven(uuid);
            return nom;
        }

        JoueurDeTest joueur(String court, Vec3 position) {
            return connecter(nom(court), position, true);
        }

        JoueurDeTest connecter(String nom, Vec3 position, boolean vider) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(position), vider, p -> {});
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            return j;
        }

        /** Fonde le fournisseur de l'essai, au nom de ce joueur. */
        PowerSupplier fonder(UUID fondateur, String nom) {
            fournisseur = fournisseurs.found(fondateur, nom, "Courant " + nom).supplier();
            helper.assertTrue(fournisseur != null, "fournisseur fondé");
            return fournisseur;
        }

        /** Un compteur actif, jumelé au fournisseur de l'essai, au nom de ce joueur. */
        UUID compteur(UUID joueur, String nom) {
            UUID compteur = UUID.randomUUID();
            fournisseurs.putClient(new PowerClient(compteur, fournisseur.id(), joueur, nom, nom,
                    PowerClient.State.ACTIVE, 0L, 0L, Set.of(), false));
            return compteur;
        }

        PowerSupplier fournisseur() {
            return fournisseurs.byId(fournisseur.id()).orElseThrow();
        }

        /**
         * Les tarifs de départ : posés pour le lendemain puis appliqués par la clôture de la veille de J (aucune facture
         * ce jour-là : tout était à 0).
         */
        void demarrer(long prix, long abonnement) {
            fournisseurs.setNextPrice(fournisseur.id(), prix);
            fournisseurs.setNextSubscription(fournisseur.id(), abonnement);
            registre.setLastDebitDay(J.minusDays(2));
            int clos = clore(J.minusDays(1));
            helper.assertTrue(clos == 1, "clôture de la veille : " + clos);
            helper.assertTrue(fournisseur().price() == prix && fournisseur().subscription() == abonnement,
                    "tarifs de départ : " + fournisseur());
            helper.assertTrue(registre.duePublic().isEmpty() && toutes().isEmpty(), "aucune facture la veille");
        }

        /** Clôt jusqu'au jour {@code jour} compris, à minuit plus une minute. */
        int clore(LocalDate jour) {
            return DailyDebit.closeDueDays(serveur, registre, new Payroll(), minuit(jour) + MINUTE);
        }

        void consommer(UUID compteur, int fe) {
            fournisseurs.recordMeter(compteur, fe);
        }

        /** Toutes les factures d'électricité du registre de l'essai, dans l'ordre d'émission. */
        List<Invoice> toutes() {
            List<Invoice> out = new ArrayList<>();
            for (UUID u : comptes) {
                out.addAll(registre.owedBy(u));
            }
            out.removeIf(f -> !PowerBilling.KIND.equals(f.payeeKind()));
            out.sort((a, b) -> Long.compare(a.issuedAt(), b.issuedAt()));
            return out;
        }

        /** Les factures d'un compteur. */
        List<Invoice> factures(UUID compteur) {
            return toutes().stream()
                    .filter(f -> PowerBilling.ref(f).map(r -> r.meter().equals(compteur)).orElse(false))
                    .collect(Collectors.toList());
        }

        Status statut(Invoice f) {
            return registre.get(f.id()).map(Invoice::status).orElse(null);
        }

        long caisse() {
            return CoinMath.valueOf(fournisseurs.till(fournisseur.id()));
        }

        long tresor() {
            return TreasuryAccount.get(serveur).total() - CoinMath.valueOf(tresorAvant);
        }

        long porte(JoueurDeTest j) {
            return j == null || !j.connecte() ? 0L : CoinMath.valueOf(PlayerCoins.held(j.joueur()));
        }

        long auCoffre(UUID joueur) {
            return SafeAccounts.get(serveur).total(joueur);
        }

        long enAttente(UUID joueur) {
            return CoinMath.valueOf(HeldGoods.get(serveur).pendingCoins(joueur));
        }

        /** La somme conservée : ce que porte le débiteur, son attente, son coffre-fort, la caisse, le trésor. */
        long somme(UUID joueur, JoueurDeTest enLigne) {
            return porte(enLigne) + enAttente(joueur) + auCoffre(joueur) + caisse() + tresor();
        }

        BlockPos coffreFort(UUID joueur, Map<Coin, Long> contenu) {
            int n;
            synchronized (FactureElectriciteGameTests.class) {
                n = coffresPoses++;
            }
            BlockPos pos = new BlockPos(2_000_000 + 16 * n, 64, 2_000_015);
            SafeAccounts comptesCoffres = SafeAccounts.get(serveur);
            comptesCoffres.setSafePos(joueur, GlobalPos.of(Level.OVERWORLD, pos));
            contenu.forEach((coin, count) -> comptesCoffres.deposit(joueur, coin, count));
            return pos;
        }

        List<StatementKind> releve(UUID compteur) {
            return fournisseurs.statement(compteur).stream().map(PowerSuppliers.StatementLine::kind)
                    .collect(Collectors.toList());
        }

        @Override
        public void close() {
            PowerBilling.install(PowerBilling.SERVER);
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.joueur().getInventory().clearContent();
                    j.quitter();
                }
            }
            HeldGoods held = HeldGoods.get(serveur);
            SafeAccounts coffres = SafeAccounts.get(serveur);
            for (UUID uuid : comptes) {
                held.takePending(uuid);
                for (Coin coin : Coin.values()) {
                    coffres.withdraw(uuid, coin, coffres.count(uuid, coin));
                }
                coffres.forgetSafePos(uuid);
            }
            // Le trésor revient à son état d'avant l'essai (la taxe d'essai en sort).
            TreasuryAccount tresor = TreasuryAccount.get(serveur);
            for (Coin coin : Coin.values()) {
                long plus = tresor.count(coin) - tresorAvant.getOrDefault(coin, 0L);
                if (plus > 0L) {
                    tresor.remove(coin, plus);
                }
            }
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

    // ---------------------------------------------------------------------------------------------
    // Lot A
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void aMinuitLaFactureValantConsommationFoisPrixPlusAbonnement(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest f = e.joueur("fe_fon", POS_A);
            JoueurDeTest c = e.joueur("fe_cli", POS_B);
            String nomC = c.joueur().getGameProfile().getName();
            e.fonder(f.uuid(), f.joueur().getGameProfile().getName());
            UUID compteur = e.compteur(c.uuid(), nomC);
            c.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 2));
            e.demarrer(5L, 10L);
            long avant = e.somme(c.uuid(), c);
            e.consommer(compteur, 2_300_000);
            e.fournisseurs.recordOut(e.fournisseur.id(), 2_500_000); // le poste a fait sortir plus que le compteur
            c.messagesRecus();

            helper.assertTrue(e.clore(J) == 1, "le jour J se clôt");
            helper.assertTrue(e.fournisseur().outYesterday() == 2_500_000L && e.fournisseur().outToday() == 0L,
                    "l'écart d'hier, valeurs non nulles : sortie 2 500 000 FE, compteurs 2 300 000 FE");
            List<Invoice> factures = e.factures(compteur);
            helper.assertTrue(factures.size() == 1, "une facture : " + factures);
            Invoice facture = factures.get(0);
            helper.assertTrue(facture.amount() == 125L, "2 300 000 FE à 5 c / 100 000 FE + 10 : " + facture.amount());
            helper.assertTrue(facture.debtor().equals(c.uuid()) && facture.issuer().equals(f.uuid()),
                    "débiteur le client, créancier le fondateur");
            helper.assertTrue(e.statut(facture) == Status.PAID, "prélevée : " + e.statut(facture));
            helper.assertTrue(e.caisse() == 125L, "les pièces arrivent dans la caisse du poste : " + e.caisse());
            helper.assertTrue(e.porte(c) == 75L, "200 moins 125 : " + e.porte(c));
            helper.assertTrue(e.tresor() == 0L, "taxe à 0 % : rien au trésor");
            helper.assertTrue(c.messagesRecus().contains(DirectDebit.MESSAGE + "paid.held"), "le client est prévenu");
            String motif = e.facturation.describe(facture).getString();
            helper.assertTrue(motif.contains("2300") && facture.reason().contains(e.fournisseur().name()),
                    "motif : " + motif + " / " + facture.reason());
            helper.assertTrue(facture.payeeRef().startsWith("2/"), "référence versionnée : " + facture.payeeRef());
            // Une référence de la première livraison (sans version) se lit toujours : une dette n'est jamais effacée.
            String ancienne = e.fournisseur().id() + "/" + compteur + "/0/" + J.toEpochDay() + "/100000/5/100000/0/"
                    + e.fournisseur().name();
            Invoice vieille = e.registre.issuePublic(PowerBilling.KIND, ancienne, f.uuid(), "fondateur", c.uuid(), nomC,
                    5L, "ancienne", minuit(J));
            helper.assertTrue(PowerBilling.ref(vieille).map(r -> r.meter().equals(compteur)).orElse(false)
                    && e.facturation.exists(e.serveur, ancienne), "ancienne référence lue");
            e.registre.cancel(vieille.id(), minuit(J));
            PowerClient client = e.fournisseurs.clientOf(compteur).orElseThrow();
            helper.assertTrue(client.feToday() == 0L && client.feYesterday() == 2_300_000L,
                    "consommation remise à zéro, gardée pour hier : " + client);
            helper.assertTrue(e.fournisseur().metersYesterday() == 2_300_000L, "compteurs d'hier notés au poste");
            helper.assertTrue(e.releve(compteur).equals(List.of(StatementKind.BILLED, StatementKind.PAID)),
                    "relevé : " + e.releve(compteur));
            helper.assertTrue(e.facturation.unpaid(e.serveur, client) == 0L, "aucun impayé");
            helper.assertTrue(e.somme(c.uuid(), c) == avant, "somme conservée : " + avant + " -> "
                    + e.somme(c.uuid(), c));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void prelevementDansLeCoffreFortAMinuit(GameTestHelper helper) {
        essai(helper, e -> {
            String nom = e.nom("fe_abs");
            JoueurDeTest c = e.connecter(nom, POS_A, true);
            UUID u = c.uuid();
            e.fonder(identifiant(e.nom("fe_fab")), "fondateur");
            UUID compteur = e.compteur(u, nom);
            BlockPos coffre = e.coffreFort(u, pieces(Coin.GOLD, 3));
            c.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 5));
            c.quitter();
            helper.assertTrue(e.serveur.getPlayerList().getPlayer(u) == null, "le client est déconnecté");
            helper.assertTrue(!helper.getLevel().hasChunk(coffre.getX() >> 4, coffre.getZ() >> 4),
                    "la zone du coffre-fort n'est pas chargée");
            e.demarrer(5L, 10L);
            long avant = e.auCoffre(u) + e.enAttente(u) + e.caisse() + e.tresor();
            e.consommer(compteur, 2_300_000);

            helper.assertTrue(e.clore(J) == 1, "le jour J se clôt");
            List<Invoice> factures = e.factures(compteur);
            helper.assertTrue(factures.size() == 1 && e.statut(factures.get(0)) == Status.PAID,
                    "facture prélevée : " + factures);
            helper.assertTrue(e.auCoffre(u) == 175L, "300 moins 125 au coffre-fort : " + e.auCoffre(u));
            helper.assertTrue(e.enAttente(u) == 0L, "rien en attente, rien au sol");
            helper.assertTrue(e.caisse() == 125L, "caisse du poste : " + e.caisse());
            helper.assertTrue(!helper.getLevel().hasChunk(coffre.getX() >> 4, coffre.getZ() >> 4),
                    "le prélèvement n'a pas chargé la zone");
            helper.assertTrue(e.auCoffre(u) + e.enAttente(u) + e.caisse() + e.tresor() == avant, "somme conservée");

            JoueurDeTest revenu = e.connecter(nom, POS_A, false);
            helper.assertTrue(e.porte(revenu) == 500L, "ce qu'il portait est intact : " + e.porte(revenu));
            revenu.messagesRecus();
            DirectDebit.announce(revenu.joueur(), e.registre);
            helper.assertTrue(revenu.messagesRecus().contains(DirectDebit.MESSAGE + "paid.safe"),
                    "il apprend le prélèvement fait en son absence");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unServeurEteintDeuxJoursEmetDeuxFactures(GameTestHelper helper) {
        essai(helper, e -> {
            String nom = e.nom("fe_eteint");
            UUID u = identifiant(nom);
            e.fonder(identifiant(e.nom("fe_fet")), "fondateur");
            UUID compteur1 = e.compteur(u, nom);
            UUID compteur2 = e.compteur(u, nom);
            e.coffreFort(u, pieces(Coin.GOLD, 5));
            e.demarrer(5L, 10L);
            long avant = e.auCoffre(u) + e.enAttente(u) + e.caisse() + e.tresor();
            e.consommer(compteur1, 2_300_000);

            // Le serveur s'éteint avant le minuit de J et redémarre le lendemain de J + 1 : deux jours à clore.
            int clos = e.clore(J.plusDays(1));
            helper.assertTrue(clos == 2, "deux jours clos au démarrage : " + clos);
            List<Long> montants1 = e.factures(compteur1).stream().map(Invoice::amount).collect(Collectors.toList());
            List<Long> montants2 = e.factures(compteur2).stream().map(Invoice::amount).collect(Collectors.toList());
            helper.assertTrue(montants1.equals(List.of(125L, 10L)), "compteur 1 : J puis J + 1 : " + montants1);
            helper.assertTrue(montants2.equals(List.of(10L, 10L)), "compteur 2 : l'abonnement chaque jour : " + montants2);
            helper.assertTrue(e.toutes().stream().allMatch(f -> e.statut(f) == Status.PAID), "toutes prélevées");
            helper.assertTrue(e.caisse() == 155L, "caisse : " + e.caisse());
            helper.assertTrue(e.auCoffre(u) == 345L, "500 moins 155 : " + e.auCoffre(u));
            helper.assertTrue(e.auCoffre(u) + e.enAttente(u) + e.caisse() + e.tresor() == avant, "somme conservée");
            helper.assertTrue(e.clore(J.plusDays(1)) == 0 && e.toutes().size() == 4, "rien de plus au tic suivant");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unChangementDePrixNeToucheQueLaFactureSuivante(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest c = e.joueur("fe_prix", POS_A);
            String nom = c.joueur().getGameProfile().getName();
            e.fonder(identifiant(e.nom("fe_fpr")), "fondateur");
            UUID compteur = e.compteur(c.uuid(), nom);
            e.coffreFort(c.uuid(), pieces(Coin.GOLD, 2));
            e.demarrer(5L, 0L);
            long avant = e.somme(c.uuid(), c);

            e.consommer(compteur, 100_000);
            // Changé dans la journée J : la facture qui clôt J garde l'ancien tarif.
            e.fournisseurs.setNextPrice(e.fournisseur.id(), 8L);
            e.fournisseurs.setNextSubscription(e.fournisseur.id(), 3L);
            helper.assertTrue(e.fournisseur().price() == 5L, "le prix du jour ne change pas tout de suite");
            c.messagesRecus();
            helper.assertTrue(e.clore(J) == 1, "J clos");
            helper.assertTrue(e.factures(compteur).get(0).amount() == 5L,
                    "la facture de J garde 5 c sans abonnement : " + e.factures(compteur));
            helper.assertTrue(e.fournisseur().price() == 8L && e.fournisseur().subscription() == 3L,
                    "le nouveau tarif entre en vigueur à minuit : " + e.fournisseur());
            helper.assertTrue(c.messagesRecus().contains(PowerPairing.MESSAGE + "tariff"), "le client est prévenu");

            e.consommer(compteur, 100_000);
            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            List<Long> montants = e.factures(compteur).stream().map(Invoice::amount).collect(Collectors.toList());
            helper.assertTrue(montants.equals(List.of(5L, 11L)), "la facture suivante au nouveau tarif : " + montants);
            helper.assertTrue(!c.messagesRecus().contains(PowerPairing.MESSAGE + "tariff"),
                    "tarif inchangé : pas d'avis");
            helper.assertTrue(e.somme(c.uuid(), c) == avant, "somme conservée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unPrixAuDessusDuNouveauPlafondEstRameneAMinuit(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest f = e.joueur("fe_plaf", POS_A);
            String nomC = e.nom("fe_cpl");
            UUID u = identifiant(nomC);
            e.fonder(f.uuid(), f.joueur().getGameProfile().getName());
            UUID compteur = e.compteur(u, nomC);
            e.coffreFort(u, pieces(Coin.GOLD, 2));
            e.demarrer(15L, 0L);

            e.consommer(compteur, 100_000);
            e.fournisseurs.setNextPriceCap(10L); // le maire baisse le plafond dans la journée J
            f.messagesRecus();
            helper.assertTrue(e.clore(J) == 1, "J clos");
            helper.assertTrue(e.factures(compteur).get(0).amount() == 15L, "la facture de J garde 15 c : "
                    + e.factures(compteur));
            helper.assertTrue(e.fournisseurs.priceCap() == 10L && e.fournisseurs.nextPriceCap().isEmpty(),
                    "le plafond entre en vigueur à minuit");
            helper.assertTrue(e.fournisseur().price() == 10L, "prix ramené au plafond : " + e.fournisseur().price());
            helper.assertTrue(f.messagesRecus().contains(PowerPairing.MESSAGE + "price_capped"), "fondateur prévenu");

            // Un prix du lendemain au-dessus du plafond est ramené de même.
            e.fournisseurs.setNextPrice(e.fournisseur.id(), 18L);
            e.consommer(compteur, 100_000);
            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            helper.assertTrue(e.factures(compteur).get(1).amount() == 10L, "facture de J + 1 au plafond : "
                    + e.factures(compteur));
            helper.assertTrue(e.fournisseur().price() == 10L, "18 ramené à 10 : " + e.fournisseur().price());
            helper.assertTrue(f.messagesRecus().contains(PowerPairing.MESSAGE + "price_capped"),
                    "fondateur prévenu de nouveau");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unCompteurRejumeleLeMemeJourNePaieQuUnAbonnement(GameTestHelper helper) {
        essai(helper, e -> {
            String nom = e.nom("fe_rej");
            UUID u = identifiant(nom);
            e.fonder(identifiant(e.nom("fe_frj")), "fondateur");
            UUID compteur = e.compteur(u, nom);
            e.coffreFort(u, pieces(Coin.GOLD, 5));
            e.demarrer(5L, 10L);
            long avant = e.auCoffre(u) + e.caisse() + e.tresor();

            // Le fournisseur déplace deux fois le compteur dans la journée : détaché, rejumelé, accepté, deux fois.
            e.consommer(compteur, 1_000_000);
            for (int fe : new int[] {800_000, 500_000}) {
                helper.assertTrue(e.fournisseurs.detach(compteur).isPresent(), "détaché");
                e.fournisseurs.putClient(new PowerClient(compteur, e.fournisseur.id(), u, nom, nom,
                        PowerClient.State.ACTIVE, 0L, 0L, Set.of(), false));
                e.consommer(compteur, fe);
            }
            helper.assertTrue(e.fournisseurs.detached().size() == 2, "deux morceaux détachés de la journée");

            helper.assertTrue(e.clore(J) == 1, "J clos");
            List<Invoice> factures = e.factures(compteur);
            helper.assertTrue(factures.size() == 1, "une seule facture pour le compteur et le jour : " + factures);
            helper.assertTrue(factures.get(0).amount() == 125L,
                    "2 300 000 FE cumulés à 5 c + un seul abonnement de 10 : " + factures.get(0).amount());
            helper.assertTrue(e.statut(factures.get(0)) == Status.PAID && e.caisse() == 125L, "prélevée une fois");
            helper.assertTrue(e.fournisseurs.detached().isEmpty(), "morceaux oubliés une fois facturés");
            helper.assertTrue(e.fournisseur().metersYesterday() == 2_300_000L, "compteurs d'hier : "
                    + e.fournisseur().metersYesterday());
            helper.assertTrue(e.auCoffre(u) + e.caisse() + e.tresor() == avant, "somme conservée");
        });
    }

    /**
     * Revue finale, I2 : la colonne « impayés » compte par contrat (débiteur, compteur, fournisseur, date du contrat). A
     * laisse deux factures dues sur un compteur détaché sans être démonté ; B, nouveau locataire du même compteur, n'en
     * hérite pas, ni A s'il revient plus tard, ni un autre fournisseur qui reprendrait le compteur. Les factures de B
     * comptent pour B seul.
     */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lesImpayesSontComptesParContrat(GameTestHelper helper) {
        essai(helper, e -> {
            String nomA = e.nom("fe_ctA");
            String nomB = e.nom("fe_ctB");
            UUID a = identifiant(nomA);
            UUID b = identifiant(nomB);
            e.fonder(identifiant(e.nom("fe_fct")), "fondateur");
            String id = e.fournisseur.id();
            UUID compteur = UUID.randomUUID();
            PowerClient ancien = new PowerClient(compteur, id, a, nomA, nomA, PowerClient.State.ACTIVE, 0L, 0L, Set.of(),
                    false, 1_000L);
            e.fournisseurs.putClient(ancien);
            e.demarrer(5L, 10L);
            e.consommer(compteur, 2_300_000);
            helper.assertTrue(e.clore(J) == 1, "J clos");
            helper.assertTrue(e.facturation.unpaid(e.serveur, ancien) == 125L, "A doit 125 : "
                    + e.facturation.unpaid(e.serveur, ancien));
            helper.assertTrue(PowerBilling.ref(e.factures(compteur).get(0)).orElseThrow().contract() == 1_000L
                    && e.factures(compteur).get(0).payeeRef().startsWith("2/"), "la facture porte la date du contrat");

            // Le fournisseur détache A au panneau (le compteur reste posé) ; sa journée reste due.
            helper.assertTrue(e.fournisseurs.detach(compteur).isPresent(), "A détaché");
            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            helper.assertTrue(e.facturation.unpaid(e.serveur, ancien) == 135L, "A doit 125 + 10 : "
                    + e.facturation.unpaid(e.serveur, ancien));

            // B, le nouveau locataire, inscrit puis jumelé sur le même compteur, chez le même fournisseur.
            PowerClient nouveau = new PowerClient(compteur, id, b, nomB, nomB, PowerClient.State.ACTIVE, 0L, 0L, Set.of(),
                    false, 2_000L);
            e.fournisseurs.putClient(nouveau);
            helper.assertTrue(e.facturation.unpaid(e.serveur, nouveau) == 0L,
                    "B n'hérite pas des dettes de A : " + e.facturation.unpaid(e.serveur, nouveau));
            PowerClient refait = new PowerClient(compteur, id, a, nomA, nomA, PowerClient.State.ACTIVE, 0L, 0L, Set.of(),
                    false, 3_000L);
            helper.assertTrue(e.facturation.unpaid(e.serveur, refait) == 0L,
                    "un contrat refait plus tard par A ne reprend pas les dettes du contrat passé");
            PowerSupplier autre = e.fournisseurs.found(identifiant(e.nom("fe_fct2")), "autre", "Autre " + nomA)
                    .supplier();
            PowerClient chezAutre = new PowerClient(compteur, autre.id(), a, nomA, nomA, PowerClient.State.ACTIVE, 0L, 0L,
                    Set.of(), false, 1_000L);
            helper.assertTrue(e.facturation.unpaid(e.serveur, chezAutre) == 0L,
                    "un autre fournisseur ne montre pas les dettes dues au premier");

            helper.assertTrue(e.clore(J.plusDays(2)) == 1, "J + 2 clos");
            helper.assertTrue(e.facturation.unpaid(e.serveur, nouveau) == 10L
                    && e.facturation.unpaid(e.serveur, ancien) == 135L,
                    "l'abonnement de B compte pour B seul : " + e.facturation.unpaid(e.serveur, nouveau) + " / "
                            + e.facturation.unpaid(e.serveur, ancien));
            // Une facture d'avant la date de contrat (référence version 1) compte pour un contrat d'avant cette donnée.
            String v1 = "1/" + id + "/" + compteur + "/0/" + J.toEpochDay() + "/0/5/100000/10/" + e.fournisseur().name();
            e.registre.issuePublic(PowerBilling.KIND, v1, UUID.randomUUID(), "fondateur", b, nomB, 10L, "v1",
                    minuit(J));
            PowerClient sansDate = new PowerClient(compteur, id, b, nomB, nomB, PowerClient.State.ACTIVE, 0L, 0L,
                    Set.of(), false);
            helper.assertTrue(e.facturation.unpaid(e.serveur, sansDate) == 10L
                    && e.facturation.unpaid(e.serveur, nouveau) == 10L, "référence v1 : contrat sans date seulement");
        });
    }

    /**
     * Revue finale, I5 : un client hors ligne est prévenu du changement de tarif à sa connexion (avis gardé), un seul avis
     * par changement entré en vigueur ; un tarif inchangé n'en laisse aucun.
     */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unChangementDeTarifPrevientLeClientAbsent(GameTestHelper helper) {
        essai(helper, e -> {
            String nom = e.nom("fe_abst");
            UUID u = identifiant(nom);
            e.fonder(identifiant(e.nom("fe_fabs")), "fondateur");
            UUID compteur = e.compteur(u, nom);
            UUID second = e.compteur(u, nom);
            e.coffreFort(u, pieces(Coin.GOLD, 2));
            e.demarrer(5L, 0L);
            e.fournisseurs.takeNotices(u); // l'avis des tarifs de départ
            helper.assertTrue(e.serveur.getPlayerList().getPlayer(u) == null, "le client est hors ligne");

            e.fournisseurs.setNextPrice(e.fournisseur.id(), 8L);
            e.fournisseurs.setNextSubscription(e.fournisseur.id(), 2L);
            helper.assertTrue(e.clore(J) == 1, "J clos");
            List<Component> avis = e.fournisseurs.takeNotices(u);
            helper.assertTrue(avis.size() == 1 && avis.get(0).getContents() instanceof TranslatableContents t
                    && t.getKey().equals(PowerPairing.MESSAGE + "tariff"),
                    "un seul avis gardé pour sa connexion, même avec deux compteurs : " + avis);

            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            helper.assertTrue(e.fournisseurs.takeNotices(u).isEmpty(), "tarif inchangé : aucun avis");
            helper.assertTrue(e.factures(second).size() == 1, "le second compteur paie son abonnement");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B
    // ---------------------------------------------------------------------------------------------

    /**
     * Revue finale, I4 : le voisin d'un compteur résilié (code d'un autre mod) lève une exception quand la clôture le
     * prévient. Les factures et les états de tous les compteurs sont faits quand même, une seule fois ; les tarifs du
     * lendemain entrent en vigueur, le jour est noté émis ; le lendemain, rien n'est refacturé.
     */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unVoisinEnFauteNeFaitRienRefacturer(GameTestHelper helper) {
        essai(helper, e -> {
            String nom = e.nom("fe_vois");
            UUID u = identifiant(nom);
            e.fonder(identifiant(e.nom("fe_fvo")), "fondateur");
            UUID resilie = e.compteur(u, nom);
            UUID autre = e.compteur(u, nom);
            UUID troisieme = e.compteur(u, nom);
            e.coffreFort(u, pieces(Coin.GOLD, 5));
            List<UUID> prevenus = new ArrayList<>();
            PowerBilling piegee = new PowerBilling(e.fournisseurs, e.registre, (serveur, compteur) -> {
                prevenus.add(compteur);
                throw new IllegalStateException("neighborChanged d'un câble d'un autre mod en faute");
            });
            PowerBilling.install(piegee);
            e.demarrer(5L, 10L);
            long avant = e.auCoffre(u) + e.caisse() + e.tresor();
            PowerClient r = e.fournisseurs.clientOf(resilie).orElseThrow();
            e.fournisseurs.putClient(new PowerClient(resilie, r.supplierId(), u, nom, nom, PowerClient.State.RESIGNING,
                    0L, 0L, Set.of(), false));
            e.consommer(resilie, 200_000);
            e.consommer(autre, 1_000_000);
            e.consommer(troisieme, 2_000_000);
            e.fournisseurs.setNextPrice(e.fournisseur.id(), 8L);

            helper.assertTrue(e.clore(J) == 1, "J clos");
            helper.assertTrue(prevenus.equals(List.of(resilie)), "le voisin du résilié a été prévenu (et a planté) : "
                    + prevenus);
            List<Long> montants = new ArrayList<>();
            for (UUID m : List.of(resilie, autre, troisieme)) {
                List<Invoice> f = e.factures(m);
                helper.assertTrue(f.size() == 1 && e.statut(f.get(0)) == Status.PAID, "une facture payée par compteur : "
                        + m + " " + f.stream().map(e::statut).collect(Collectors.toList()));
                montants.add(f.get(0).amount());
            }
            helper.assertTrue(montants.equals(List.of(20L, 60L, 110L)), "montants : " + montants);
            helper.assertTrue(e.fournisseurs.clientOf(resilie).isEmpty(), "la résiliation est faite");
            for (UUID m : List.of(autre, troisieme)) {
                PowerClient c = e.fournisseurs.clientOf(m).orElseThrow();
                helper.assertTrue(c.feToday() == 0L && c.feYesterday() > 0L, "jour clos : " + c);
            }
            helper.assertTrue(e.fournisseur().price() == 8L, "le tarif du lendemain est entré en vigueur");
            helper.assertTrue(J.equals(e.registre.lastIssuedDay()), "le jour est noté émis");

            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            helper.assertTrue(e.factures(resilie).size() == 1, "le résilié n'est plus facturé");
            for (UUID m : List.of(autre, troisieme)) {
                List<Invoice> f = e.factures(m);
                helper.assertTrue(f.size() == 2 && f.get(1).amount() == 10L,
                        "le lendemain, l'abonnement seul (aucune consommation refacturée) : " + f);
            }
            helper.assertTrue(e.auCoffre(u) + e.caisse() + e.tresor() == avant && e.caisse() == 210L,
                    "somme conservée : caisse " + e.caisse());
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laTaxeVaAuTresor(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest c = e.joueur("fe_taxe", POS_A);
            String nom = c.joueur().getGameProfile().getName();
            e.fonder(identifiant(e.nom("fe_ftx")), "fondateur");
            UUID compteur = e.compteur(c.uuid(), nom);
            c.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 2));
            e.fournisseurs.setNextTaxPercent(10); // en vigueur dès J (posé avant la clôture de la veille)
            e.demarrer(5L, 10L);
            helper.assertTrue(e.fournisseurs.taxPercent() == 10, "taxe de 10 % en vigueur");
            long avant = e.somme(c.uuid(), c);
            e.consommer(compteur, 2_300_000);
            e.fournisseurs.setNextTaxPercent(20); // changée pendant J : seulement pour la suite

            helper.assertTrue(e.clore(J) == 1, "J clos");
            Invoice facture = e.factures(compteur).get(0);
            helper.assertTrue(facture.amount() == 125L && e.statut(facture) == Status.PAID, "facture : " + facture);
            helper.assertTrue(e.tresor() == 12L, "12 au trésor (10 % arrondi en dessous) : " + e.tresor());
            helper.assertTrue(e.caisse() == 113L, "113 à la caisse : " + e.caisse());
            helper.assertTrue(e.fournisseurs.taxPercent() == 20, "la nouvelle taxe vaut pour le lendemain");
            boolean ligne = TreasuryAccount.get(e.serveur).ledger().page(0).stream()
                    .anyMatch(l -> l.kind() == TreasuryLedger.Kind.POWER_TAX && l.amount() == 12L);
            helper.assertTrue(ligne, "ligne POWER_TAX de 12 au registre du trésor");
            EconomyLedger registre = EconomyLedger.get(e.serveur);
            helper.assertTrue(registre.totalTransferred(MoneyTransfer.POWER_TAX) - e.taxeAvant == 12L,
                    "taxe notée au registre de l'économie");
            helper.assertTrue(registre.totalTransferred(MoneyTransfer.PUBLIC_BILL) - e.publicAvant == 125L,
                    "facture notée au registre de l'économie");
            helper.assertTrue(e.somme(c.uuid(), c) == avant, "somme conservée : " + avant + " -> "
                    + e.somme(c.uuid(), c));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unFournisseurSupprimeNeFacturePlus(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest c = e.joueur("fe_supp", POS_A);
            String nom = c.joueur().getGameProfile().getName();
            e.fonder(identifiant(e.nom("fe_fsu")), "fondateur");
            UUID compteur = e.compteur(c.uuid(), nom);
            e.demarrer(5L, 10L);
            e.consommer(compteur, 2_300_000);
            helper.assertTrue(e.clore(J) == 1, "J clos");
            Invoice due = e.factures(compteur).get(0);
            helper.assertTrue(e.statut(due) == Status.DUE, "sans pièces : refusée, reste due");

            // Suppression par le service (la commande du maire viendra à la tâche 6) : caisse vide, rien à verser.
            helper.assertTrue(e.fournisseurs.removeIfEmpty(e.fournisseur.id()), "fournisseur supprimé");
            e.consommer(compteur, 2_300_000);
            c.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 5));
            long avant = e.somme(c.uuid(), c);
            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            helper.assertTrue(e.toutes().size() == 1, "aucune nouvelle facture : " + e.toutes());
            helper.assertTrue(e.statut(due) == Status.CANCELLED, "la facture due est annulée : " + e.statut(due));
            helper.assertTrue(e.porte(c) == 500L && e.somme(c.uuid(), c) == avant, "rien n'a bougé");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneHorlogeQuiReculeNeFactureRien(GameTestHelper helper) {
        essai(helper, e -> {
            String nom = e.nom("fe_recul");
            UUID u = identifiant(nom);
            e.fonder(identifiant(e.nom("fe_fre")), "fondateur");
            UUID compteur = e.compteur(u, nom);
            e.coffreFort(u, pieces(Coin.GOLD, 5));
            e.demarrer(5L, 10L);
            long avant = e.auCoffre(u) + e.caisse() + e.tresor();
            e.consommer(compteur, 2_300_000);
            helper.assertTrue(e.clore(J) == 1 && e.toutes().size() == 1, "J clos, une facture");

            e.consommer(compteur, 1_000_000);
            Payroll paie = new Payroll();
            helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J) - 180L * MINUTE) == 0,
                    "l'horloge recule de trois heures : rien");
            helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J.minusDays(3))) == 0,
                    "l'horloge recule de trois jours : rien");
            helper.assertTrue(DailyDebit.closeDueDays(e.serveur, e.registre, paie, minuit(J) + 2L * MINUTE) == 0,
                    "elle revient : J est déjà clos");
            helper.assertTrue(e.toutes().size() == 1, "toujours une seule facture : " + e.toutes());
            helper.assertTrue(e.fournisseurs.clientOf(compteur).orElseThrow().feToday() == 1_000_000L,
                    "la consommation de J + 1 n'a pas été remise à zéro");
            helper.assertTrue(e.caisse() == 125L && e.auCoffre(u) + e.caisse() + e.tresor() == avant,
                    "une seule facture prélevée, somme conservée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unRefusPrevientLeClientEtLeFournisseur(GameTestHelper helper) {
        essai(helper, e -> {
            String nomF = e.nom("fe_frf");
            JoueurDeTest f = e.connecter(nomF, POS_A, true);
            JoueurDeTest c = e.joueur("fe_ref", POS_B);
            String nomC = c.joueur().getGameProfile().getName();
            e.fonder(f.uuid(), nomF);
            UUID compteur = e.compteur(c.uuid(), nomC);
            e.demarrer(5L, 10L);
            e.consommer(compteur, 2_300_000);
            f.messagesRecus();
            c.messagesRecus();

            helper.assertTrue(e.clore(J) == 1, "J clos");
            Invoice due = e.factures(compteur).get(0);
            helper.assertTrue(e.statut(due) == Status.DUE && e.caisse() == 0L, "refusée : rien ne bouge");
            helper.assertTrue(c.messagesRecus().contains(DirectDebit.MESSAGE + "refused"), "le client est prévenu");
            helper.assertTrue(f.messagesRecus().contains(PowerPairing.MESSAGE + "founder_unpaid"),
                    "le fournisseur est prévenu");
            PowerClient contrat = e.fournisseurs.clientOf(compteur).orElseThrow();
            helper.assertTrue(e.facturation.unpaid(e.serveur, contrat) == 125L,
                    "colonne « impayés » : " + e.facturation.unpaid(e.serveur, contrat));
            helper.assertTrue(e.releve(compteur).equals(List.of(StatementKind.BILLED, StatementKind.REFUSED)),
                    "relevé : " + e.releve(compteur));

            // Le fondateur absent l'apprend à sa connexion.
            f.quitter();
            JoueurDeTest revenu = e.connecter(nomF, POS_A, false);
            revenu.messagesRecus();
            e.facturation.announceFounder(revenu.joueur());
            helper.assertTrue(revenu.messagesRecus().contains(PowerPairing.MESSAGE + "founder_unpaid_summary"),
                    "impayés rappelés au fondateur à sa connexion");

            // Présentée à la minuit suivante : le client a de quoi payer.
            c.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 2));
            long avant = e.somme(c.uuid(), c);
            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            helper.assertTrue(e.statut(due) == Status.PAID, "la facture de J est payée à la minuit suivante");
            helper.assertTrue(e.factures(compteur).size() == 2 && e.factures(compteur).get(1).amount() == 10L,
                    "J + 1 : l'abonnement seul : " + e.factures(compteur));
            helper.assertTrue(e.caisse() == 135L && e.porte(c) == 65L, "125 + 10 : " + e.caisse() + " / "
                    + e.porte(c));
            helper.assertTrue(e.facturation.unpaid(e.serveur, contrat) == 0L, "plus d'impayé");
            helper.assertTrue(e.releve(compteur).equals(List.of(StatementKind.BILLED, StatementKind.REFUSED,
                    StatementKind.BILLED, StatementKind.PAID, StatementKind.PAID)), "relevé : " + e.releve(compteur));
            List<Long> dates = e.fournisseurs.statement(compteur).stream()
                    .map(PowerSuppliers.StatementLine::epochMillis).collect(Collectors.toList());
            long j0 = minuit(J) + MINUTE;
            long j1 = minuit(J.plusDays(1)) + MINUTE;
            helper.assertTrue(dates.equals(List.of(j0, j0, j1, j1, j1)), "relevé daté par l'heure de la clôture : "
                    + dates);
            helper.assertTrue(e.somme(c.uuid(), c) == avant, "somme conservée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unClientResilieDetacheOuCoupeEstFactureSelonSonContrat(GameTestHelper helper) {
        essai(helper, e -> {
            String nom = e.nom("fe_res");
            UUID u = identifiant(nom);
            e.fonder(identifiant(e.nom("fe_frs")), "fondateur");
            UUID resilie = e.compteur(u, nom);
            UUID detache = e.compteur(u, nom);
            UUID coupe = e.compteur(u, nom);
            UUID coupeEnCours = e.compteur(u, nom);
            UUID disjoncteur = UUID.randomUUID();
            GlobalPos ici = GlobalPos.of(Level.OVERWORLD, new BlockPos(3_000_000, 64, 3_000_000));
            e.coffreFort(u, pieces(Coin.GOLD, 5));
            e.demarrer(5L, 10L);
            long avant = e.auCoffre(u) + e.caisse() + e.tresor();

            // Résilié dans la journée : il consomme encore jusqu'à minuit, dernière facture, puis détaché.
            PowerClient r = e.fournisseurs.clientOf(resilie).orElseThrow();
            e.fournisseurs.putClient(new PowerClient(resilie, r.supplierId(), u, nom, nom, PowerClient.State.RESIGNING,
                    0L, 0L, Set.of(disjoncteur), false));
            e.fournisseurs.setPairedAt(resilie, ici);
            e.fournisseurs.setPairedAt(disjoncteur, ici);
            e.consommer(resilie, 200_000);
            // Démonté dans la journée sans rien consommer : l'abonnement du jour reste dû.
            helper.assertTrue(e.fournisseurs.detach(detache).isPresent(), "compteur détaché");
            // Coupé à distance toute la journée : rien ne passe, et pas d'abonnement (décision de Killian) : aucune facture.
            PowerClient k = e.fournisseurs.clientOf(coupe).orElseThrow();
            e.fournisseurs.putClient(new PowerClient(coupe, k.supplierId(), u, nom, nom, PowerClient.State.ACTIVE, 0L,
                    0L, Set.of(), true));
            // Coupé en cours de journée : ce qu'il a reçu avant la coupure est facturé, sans abonnement.
            e.consommer(coupeEnCours, 300_000);
            PowerClient kc = e.fournisseurs.clientOf(coupeEnCours).orElseThrow();
            e.fournisseurs.putClient(new PowerClient(coupeEnCours, kc.supplierId(), u, nom, nom,
                    PowerClient.State.ACTIVE, kc.feToday(), 0L, Set.of(), true));

            helper.assertTrue(e.clore(J) == 1, "J clos");
            helper.assertTrue(e.factures(resilie).size() == 1 && e.factures(resilie).get(0).amount() == 20L,
                    "résilié : 200 000 FE à 5 c + 10 : " + e.factures(resilie));
            helper.assertTrue(e.factures(detache).size() == 1 && e.factures(detache).get(0).amount() == 10L,
                    "détaché : l'abonnement du jour : " + e.factures(detache));
            helper.assertTrue(e.factures(coupe).isEmpty(), "coupé toute la journée : aucune facture : "
                    + e.factures(coupe));
            helper.assertTrue(e.factures(coupeEnCours).size() == 1 && e.factures(coupeEnCours).get(0).amount() == 15L,
                    "coupé en cours de journée : 300 000 FE à 5 c, sans abonnement : " + e.factures(coupeEnCours));
            helper.assertTrue(e.fournisseurs.clientOf(resilie).isEmpty(), "le résilié est détaché à la clôture");
            helper.assertTrue(!e.fournisseurs.isPairedAt(resilie, ici) && !e.fournisseurs.isPairedAt(disjoncteur, ici),
                    "son jumelage est oublié (compteur et disjoncteur)");
            helper.assertTrue(!e.fournisseurs.passes(resilie), "le courant ne passe plus");
            helper.assertTrue(e.fournisseurs.detached().isEmpty(), "le détaché est oublié une fois facturé");
            helper.assertTrue(e.fournisseur().metersYesterday() == 500_000L, "compteurs d'hier : "
                    + e.fournisseur().metersYesterday());

            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            helper.assertTrue(e.factures(resilie).size() == 1 && e.factures(detache).size() == 1,
                    "ni le résilié ni le détaché ne sont plus facturés");
            helper.assertTrue(e.factures(coupe).isEmpty() && e.factures(coupeEnCours).size() == 1,
                    "toujours coupés : pas d'abonnement : " + e.factures(coupe) + " / " + e.factures(coupeEnCours));
            helper.assertTrue(e.caisse() == 45L, "20 + 10 + 15 : " + e.caisse());
            helper.assertTrue(e.auCoffre(u) + e.caisse() + e.tresor() == avant, "somme conservée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneCaissePleineRendLeMontantEtLaFactureResteDue(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest f = e.joueur("fe_fpl", POS_A);
            JoueurDeTest c = e.joueur("fe_cpl", POS_B);
            String nomC = c.joueur().getGameProfile().getName();
            e.fonder(f.uuid(), f.joueur().getGameProfile().getName());
            UUID compteur = e.compteur(c.uuid(), nomC);
            c.joueur().getInventory().setItem(0, new ItemStack(Coin.GOLD.item(), 2));
            e.fournisseurs.setNextTaxPercent(10);
            e.demarrer(5L, 10L);
            // La caisse du poste pleine en cuivres : 113 à verser = 1 or, 1 argent, 3 cuivres ; les cuivres n'entrent pas.
            String id = e.fournisseur.id();
            long remplissage = e.fournisseurs.tillRoom(id, Coin.COPPER);
            e.fournisseurs.deposit(id, Coin.COPPER, remplissage);
            helper.assertTrue(e.fournisseurs.tillRoom(id, Coin.COPPER) == 0L, "caisse pleine en cuivres");
            EconomyLedger registre = EconomyLedger.get(e.serveur);
            long publicAvant = registre.totalTransferred(MoneyTransfer.PUBLIC_BILL);
            long taxeAvant = registre.totalTransferred(MoneyTransfer.POWER_TAX);
            long caisseAvant = e.caisse();
            long avant = e.porte(c) + e.enAttente(c.uuid()) + e.auCoffre(c.uuid()) + caisseAvant + e.tresor();
            e.consommer(compteur, 2_300_000);
            f.messagesRecus();

            helper.assertTrue(e.clore(J) == 1, "J clos");
            Invoice facture = e.factures(compteur).get(0);
            helper.assertTrue(facture.amount() == 125L && e.statut(facture) == Status.DUE,
                    "la facture reste due : " + e.statut(facture));
            helper.assertTrue(e.caisse() == caisseAvant && e.tresor() == 0L, "caisse et trésor inchangés : "
                    + (e.caisse() - caisseAvant) + " / " + e.tresor());
            helper.assertTrue(e.porte(c) + e.enAttente(c.uuid()) + e.auCoffre(c.uuid()) == 200L,
                    "le débiteur a tout récupéré : " + e.porte(c));
            helper.assertTrue(e.porte(c) + e.enAttente(c.uuid()) + e.auCoffre(c.uuid()) + e.caisse() + e.tresor()
                    == avant, "somme conservée au cuivre près");
            helper.assertTrue(registre.totalTransferred(MoneyTransfer.PUBLIC_BILL) == publicAvant
                    && registre.totalTransferred(MoneyTransfer.POWER_TAX) == taxeAvant, "rien noté au registre");
            helper.assertTrue(f.messagesRecus().contains(PowerPairing.MESSAGE + "till_full"), "fondateur prévenu");

            // Caisse vidée, trésor plein en cuivres : la taxe (12 = 1 argent, 2 cuivres) n'entre pas.
            helper.assertTrue(e.fournisseurs.withdraw(id, Coin.COPPER, remplissage), "caisse vidée");
            TreasuryAccount tresor = TreasuryAccount.get(e.serveur);
            tresor.add(Coin.COPPER, tresor.room(Coin.COPPER));
            long tresorPlein = tresor.total();
            long avant2 = e.porte(c) + e.enAttente(c.uuid()) + e.auCoffre(c.uuid()) + e.caisse() + tresorPlein;
            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            helper.assertTrue(e.statut(facture) == Status.DUE, "trésor plein : la facture reste due");
            helper.assertTrue(e.caisse() == 0L && tresor.total() == tresorPlein, "caisse et trésor inchangés");
            helper.assertTrue(e.porte(c) + e.enAttente(c.uuid()) + e.auCoffre(c.uuid()) + e.caisse() + tresor.total()
                    == avant2, "somme conservée au cuivre près (trésor plein)");
            helper.assertTrue(f.messagesRecus().contains(PowerPairing.MESSAGE + "till_full"), "fondateur prévenu");
        });
    }
}
