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
            c.messagesRecus();

            helper.assertTrue(e.clore(J) == 1, "le jour J se clôt");
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
            helper.assertTrue(motif.contains("2") && facture.reason().contains(e.fournisseur().name()),
                    "motif : " + motif + " / " + facture.reason());
            PowerClient client = e.fournisseurs.clientOf(compteur).orElseThrow();
            helper.assertTrue(client.feToday() == 0L && client.feYesterday() == 2_300_000L,
                    "consommation remise à zéro, gardée pour hier : " + client);
            helper.assertTrue(e.fournisseur().metersYesterday() == 2_300_000L, "compteurs d'hier notés au poste");
            helper.assertTrue(e.releve(compteur).equals(List.of(StatementKind.BILLED, StatementKind.PAID)),
                    "relevé : " + e.releve(compteur));
            helper.assertTrue(e.facturation.unpaid(e.serveur, compteur) == 0L, "aucun impayé");
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

    // ---------------------------------------------------------------------------------------------
    // Lot B
    // ---------------------------------------------------------------------------------------------

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
            helper.assertTrue(e.facturation.unpaid(e.serveur, compteur) == 125L,
                    "colonne « impayés » : " + e.facturation.unpaid(e.serveur, compteur));
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
            helper.assertTrue(e.facturation.unpaid(e.serveur, compteur) == 0L, "plus d'impayé");
            helper.assertTrue(e.releve(compteur).equals(List.of(StatementKind.BILLED, StatementKind.REFUSED,
                    StatementKind.BILLED, StatementKind.PAID, StatementKind.PAID)), "relevé : " + e.releve(compteur));
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
            // Coupé à distance toute la journée : rien ne passe, l'abonnement (le contrat) reste dû.
            PowerClient k = e.fournisseurs.clientOf(coupe).orElseThrow();
            e.fournisseurs.putClient(new PowerClient(coupe, k.supplierId(), u, nom, nom, PowerClient.State.ACTIVE, 0L,
                    0L, Set.of(), true));

            helper.assertTrue(e.clore(J) == 1, "J clos");
            helper.assertTrue(e.factures(resilie).size() == 1 && e.factures(resilie).get(0).amount() == 20L,
                    "résilié : 200 000 FE à 5 c + 10 : " + e.factures(resilie));
            helper.assertTrue(e.factures(detache).size() == 1 && e.factures(detache).get(0).amount() == 10L,
                    "détaché : l'abonnement du jour : " + e.factures(detache));
            helper.assertTrue(e.factures(coupe).size() == 1 && e.factures(coupe).get(0).amount() == 10L,
                    "coupé : l'abonnement du jour : " + e.factures(coupe));
            helper.assertTrue(e.fournisseurs.clientOf(resilie).isEmpty(), "le résilié est détaché à la clôture");
            helper.assertTrue(!e.fournisseurs.isPairedAt(resilie, ici) && !e.fournisseurs.isPairedAt(disjoncteur, ici),
                    "son jumelage est oublié (compteur et disjoncteur)");
            helper.assertTrue(!e.fournisseurs.passes(resilie), "le courant ne passe plus");
            helper.assertTrue(e.fournisseurs.detached().isEmpty(), "le détaché est oublié une fois facturé");
            helper.assertTrue(e.fournisseur().metersYesterday() == 200_000L, "compteurs d'hier : "
                    + e.fournisseur().metersYesterday());

            helper.assertTrue(e.clore(J.plusDays(1)) == 1, "J + 1 clos");
            helper.assertTrue(e.factures(resilie).size() == 1 && e.factures(detache).size() == 1,
                    "ni le résilié ni le détaché ne sont plus facturés");
            helper.assertTrue(e.factures(coupe).size() == 2, "le coupé reste abonné : " + e.factures(coupe));
            helper.assertTrue(e.caisse() == 50L, "20 + 10 + 10 + 10 : " + e.caisse());
            helper.assertTrue(e.auCoffre(u) + e.caisse() + e.tresor() == avant, "somme conservée");
        });
    }
}
