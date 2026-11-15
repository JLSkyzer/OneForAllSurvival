package dev.oneforallsurvival.dev;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.economy.payroll.PayrollService;
import dev.oneforallsurvival.economy.payroll.RoleTimeTracker;
import dev.oneforallsurvival.economy.payroll.SalaryRates;
import dev.oneforallsurvival.economy.town.TreasuryLedger;
import dev.oneforallsurvival.economy.town.TreasuryLedger.Kind;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La paie de minuit, les dettes de la ville et les jours manqués (lot E3, tâche 4). La paie et le trésor sont des
 * instances détachées ({@code load(new CompoundTag())}) et l'heure se passe à la main : rien n'attend un vrai minuit.
 * Coffres-forts, attente, registre global, rôles et joueurs sont ceux du serveur : chaque essai est synchrone et rend
 * ce qu'il a changé dans le même tick. Les essais qui lisent la paie du serveur (connexion, {@code /eco stats}) y
 * posent une dette d'un jour lointain ({@link #LOINTAIN}) et l'en retirent avant de rendre la main. Seul
 * {@link #unDepotPaieLaFileAuTickSuivant} laisse tourner le tick du serveur, dans un lot à lui, horloge figée.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class SalaireGameTests {

    private static final String LOT_A = "salaire_a";
    private static final String LOT_B = "salaire_b";
    private static final String LOT_C = "salaire_c";
    private static final String LOT_TICK = "salaire_tick";
    private static final String LOT_PAUSE = "salaire_pause";

    /** Un jour de paie quelconque, au milieu de l'année (pas de changement d'heure autour). */
    private static final LocalDate J = LocalDate.of(2026, 6, 14);
    /** Un jour que la paie du serveur ne clôt pas pendant les essais. */
    private static final LocalDate LOINTAIN = LocalDate.of(2999, 1, 1);
    private static final long HEURE = 3_600_000L;

    private SalaireGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static ZoneId zone() {
        return EconomyCalendar.configuredZone();
    }

    /** Le début du jour de paie {@code jour} (la frontière qui le suit la veille). */
    private static long debut(LocalDate jour) {
        return EconomyCalendar.boundaryAfter(jour.minusDays(1), zone(), EconomyConfig.payHour());
    }

    /** Le « minuit » qui clôt {@code jour}. */
    private static long minuit(LocalDate jour) {
        return EconomyCalendar.boundaryAfter(jour, zone(), EconomyConfig.payHour());
    }

    private static long maxSecondes() {
        return EconomyConfig.salaryMaxHoursPerDay() * 3_600L;
    }

    /** Une heure comptée à {@code taux} cuivres par heure : un salaire de {@code taux} à la clôture. */
    private static void heure(Payroll paie, UUID joueur, String nom, LocalDate jour, String cle, long taux) {
        paie.credit(joueur, nom, jour, cle, 3_600L, taux, maxSecondes());
    }

    private static TreasuryAccount tresor(long argent) {
        TreasuryAccount t = TreasuryAccount.load(new CompoundTag());
        t.add(Coin.SILVER, argent / 10L);
        t.add(Coin.COPPER, argent % 10L);
        return t;
    }

    private static List<TreasuryLedger.Line> lignes(TreasuryAccount t, Kind kind) {
        List<TreasuryLedger.Line> out = new ArrayList<>();
        for (int page = 0; page < t.ledger().pageCount(); page++) {
            for (TreasuryLedger.Line line : t.ledger().page(page)) {
                if (line.kind() == kind) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    private static long porte(JoueurDeTest j) {
        return CoinMath.valueOf(PlayerCoins.held(j.joueur()));
    }

    private static long enAttente(MinecraftServer serveur, UUID joueur) {
        return CoinMath.valueOf(HeldGoods.get(serveur).pendingFor(joueur).coins());
    }

    private static String texte(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    /**
     * Un essai : joueurs connectés (rôle posé), coffres-forts notés ; tout est rendu à la fin (rôles, maire,
     * coffres-forts vidés et oubliés, attente reprise, joueurs partis, dettes posées sur la paie du serveur).
     */
    private static final class Essai implements AutoCloseable {
        final GameTestHelper helper;
        final MinecraftServer serveur;
        final RoleSavedData donnees;
        final RoleBook roles;
        final Optional<UUID> ancienMaire;
        final Map<UUID, String> anciensRoles = new HashMap<>();
        final List<JoueurDeTest> joueurs = new ArrayList<>();
        final List<UUID> coffres = new ArrayList<>();
        final List<UUID> touches = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            this.serveur = helper.getLevel().getServer();
            this.donnees = RoleSavedData.get(serveur);
            this.roles = donnees.book();
            this.ancienMaire = roles.mayor();
        }

        JoueurDeTest joueur(String nom, String role) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
            garder(j, role);
            return j;
        }

        /** Rejoint sans vider l'inventaire (la livraison à la connexion se lit). */
        JoueurDeTest revenir(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)),
                    false, p -> {});
            joueurs.add(j);
            return j;
        }

        void garder(JoueurDeTest j, String role) {
            joueurs.add(j);
            anciensRoles.putIfAbsent(j.uuid(), roles.roleOf(j.uuid()).id());
            touches.add(j.uuid());
            roles.assign(j.uuid(), role);
        }

        /** Un coffre-fort noté pour ce joueur (le compte marche sans bloc). */
        void coffreFort(UUID joueur) {
            SafeAccounts.get(serveur).setSafePos(joueur,
                    GlobalPos.of(Level.OVERWORLD, new BlockPos(0, -60, 0).offset(coffres.size(), 0, 0)));
            coffres.add(joueur);
        }

        SafeAccounts comptes() {
            return SafeAccounts.get(serveur);
        }

        @Override
        public void close() {
            SafeAccounts comptes = comptes();
            for (UUID id : touches) {
                for (Map.Entry<Coin, Long> e : comptes.balance(id).entrySet()) {
                    if (e.getValue() > 0L) {
                        comptes.withdraw(id, e.getKey(), e.getValue());
                    }
                }
                comptes.forgetSafePos(id);
                HeldGoods.get(serveur).takePending(id);
                Payroll paie = Payroll.get(serveur);
                for (Payroll.Debt d : paie.owedTo(id)) {
                    paie.removeDebt(d.id());
                }
                paie.takeUnseenPaid(id);
            }
            anciensRoles.forEach((id, role) -> roles.assign(id, role));
            if (ancienMaire.isPresent()) {
                roles.appointMayor(ancienMaire.get());
            } else {
                roles.removeMayor();
            }
            donnees.setDirty();
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.joueur().getInventory().clearContent();
                    j.quitter();
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
    // Lot A : où va un salaire, dettes
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unSalaireArriveAuCoffreFortAMinuit(GameTestHelper helper) {
        essai(helper, e -> {
            helper.assertTrue(SalaryRates.rateFor("policier") == 60L, "policier à 60 par heure, vu "
                    + SalaryRates.rateFor("policier"));
            JoueurDeTest j = e.joueur("SalCoffre", "policier");
            e.roles.removeMayor();
            e.coffreFort(j.uuid());
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            TreasuryAccount tresor = tresor(100L);
            RoleTimeTracker suivi = new RoleTimeTracker();
            long t = debut(J) + 6L * HEURE;
            suivi.onLoggedIn(j.joueur(), t);
            for (int i = 1; i <= 60; i++) {
                suivi.sample(e.serveur, paie, t + i * 60_000L);
            }
            helper.assertTrue(paie.secondsFor(j.uuid(), J).equals(Map.of("policier", 3_600L)),
                    "1 h comptée, vu " + paie.secondsFor(j.uuid(), J));
            EconomyLedger registre = EconomyLedger.get(e.serveur);
            long circulation = registre.inCirculation();
            long salaires = registre.totalTransferred(MoneyTransfer.SALARY);
            long coffre = e.comptes().total(j.uuid());
            j.textesRecus();

            int clos = PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J));
            helper.assertTrue(clos == 1, "un jour clos, vu " + clos);
            helper.assertTrue(e.comptes().total(j.uuid()) - coffre == 60L, "coffre-fort + 60, vu "
                    + (e.comptes().total(j.uuid()) - coffre));
            helper.assertTrue(tresor.total() == 40L, "trésor 40, vu " + tresor.total());
            helper.assertTrue(porte(j) == 0L, "rien dans l'inventaire");
            List<TreasuryLedger.Line> payes = lignes(tresor, Kind.SALARY_PAID);
            helper.assertTrue(payes.size() == 1 && payes.get(0).amount() == 60L
                    && j.uuid().equals(payes.get(0).actor()), "ligne SALARY_PAID de 60 : " + payes);
            helper.assertTrue(lignes(tresor, Kind.SALARY_OWED).size() == 1, "la dette du jour est notée");
            helper.assertTrue(registre.totalTransferred(MoneyTransfer.SALARY) - salaires == 60L, "transfert SALARY de 60");
            helper.assertTrue(registre.inCirculation() == circulation, "rien de créé");
            helper.assertTrue(paie.debts().isEmpty() && paie.lastClosed().equals(J), "plus de dette, J clos");
            String attendu = texte(PayrollService.MESSAGE + "paid", J.toString(),
                    texte(PayrollService.MESSAGE + "role_time", "Policier", 1, 0), CoinMath.format(60L),
                    Component.translatable(PayrollService.MESSAGE + "to.safe"));
            List<String> recus = j.textesRecus();
            helper.assertTrue(recus.contains(attendu), "avis « " + attendu + " », reçu " + recus);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void sansCoffreFortConnecteDansLInventaire(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalInventaire", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalInventaire", J, "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            j.textesRecus();
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J));
            helper.assertTrue(porte(j) == 60L, "60 dans l'inventaire, vu " + porte(j));
            helper.assertTrue(e.comptes().total(j.uuid()) == 0L && enAttente(e.serveur, j.uuid()) == 0L,
                    "ni coffre-fort ni attente");
            helper.assertTrue(tresor.total() == 40L, "trésor 40");
            String vers = Component.translatable(PayrollService.MESSAGE + "to.inventory").getString();
            List<String> recus = j.textesRecus();
            helper.assertTrue(recus.stream().anyMatch(s -> s.contains(vers)), "avis « " + vers + " » : " + recus);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void sansCoffreFortAbsentAttendSaConnexion(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalAbsent", "policier");
            UUID id = j.uuid();
            j.quitter();
            // La paie du serveur, pour que la connexion la lise : une dette d'un jour lointain, seule de la file.
            Payroll paie = Payroll.get(e.serveur);
            helper.assertTrue(paie.debts().isEmpty(), "aucune dette au serveur avant l'essai : " + paie.debts());
            heure(paie, id, "SalAbsent", LOINTAIN, "policier", 60L);
            paie.closeDay(LOINTAIN);
            TreasuryAccount tresor = tresor(100L);
            int payes = PayrollService.payDebts(e.serveur, paie, tresor);
            helper.assertTrue(payes == 1 && tresor.total() == 40L, "payé : " + payes + ", trésor " + tresor.total());
            helper.assertTrue(enAttente(e.serveur, id) == 60L, "60 en attente, vu " + enAttente(e.serveur, id));

            JoueurDeTest retour = e.revenir("SalAbsent");
            helper.assertTrue(porte(retour) == 60L, "livré à la connexion, porte " + porte(retour));
            helper.assertTrue(enAttente(e.serveur, id) == 0L, "plus rien en attente");
            helper.assertTrue(retour.messagesALaConnexion().contains(PayrollService.MESSAGE + "absent_paid"),
                    "avis « versé pendant votre absence » : " + retour.messagesALaConnexion());
            helper.assertTrue(paie.takeUnseenPaid(id).isEmpty(), "l'avis n'est lu qu'une fois");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void coffreFortAuPlafondLeResteVaALInventaire(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalPlafond", "policier");
            e.coffreFort(j.uuid());
            e.comptes().deposit(j.uuid(), Coin.SILVER, SafeAccounts.MAX_PER_COIN - 2L);
            long coffre = e.comptes().total(j.uuid());
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalPlafond", J, "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            j.textesRecus();
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J));
            helper.assertTrue(e.comptes().total(j.uuid()) - coffre == 20L, "2 argent au coffre-fort, vu "
                    + (e.comptes().total(j.uuid()) - coffre));
            helper.assertTrue(porte(j) == 40L, "4 argent dans l'inventaire, vu " + porte(j));
            helper.assertTrue(tresor.total() == 40L && enAttente(e.serveur, j.uuid()) == 0L, "rien en attente");
            // Revue finale, M4 : une part est allée à l'inventaire, l'avis le dit.
            String vers = Component.translatable(PayrollService.MESSAGE + "to.inventory").getString();
            List<String> recus = j.textesRecus();
            helper.assertTrue(recus.stream().anyMatch(s -> s.contains(vers)), "avis « " + vers + " » : " + recus);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unSalaireImpayeResteDu(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalImpaye", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalImpaye", J, "policier", 60L);
            TreasuryAccount tresor = tresor(30L);
            long salaires = EconomyLedger.get(e.serveur).totalTransferred(MoneyTransfer.SALARY);
            j.textesRecus();
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J));
            helper.assertTrue(tresor.total() == 30L, "trésor intact, vu " + tresor.total());
            helper.assertTrue(porte(j) == 0L, "rien payé");
            helper.assertTrue(paie.debts().size() == 1 && paie.owedTotal() == 60L, "dette de 60 : " + paie.debts());
            helper.assertTrue(lignes(tresor, Kind.SALARY_OWED).size() == 1
                    && lignes(tresor, Kind.SALARY_PAID).isEmpty(), "ligne SALARY_OWED, aucune SALARY_PAID");
            helper.assertTrue(EconomyLedger.get(e.serveur).totalTransferred(MoneyTransfer.SALARY) == salaires,
                    "une dette n'est pas un transfert");
            String avis = texte(PayrollService.MESSAGE + "owed", J.toString(), CoinMath.format(60L));
            List<String> recus = j.textesRecus();
            helper.assertTrue(recus.contains(avis), "avis « " + avis + " » : " + recus);

            // /eco stats lit la paie du serveur : la même dette y est posée le temps de la commande.
            Payroll serveur = Payroll.get(e.serveur);
            helper.assertTrue(serveur.debts().isEmpty(), "aucune dette au serveur avant l'essai");
            heure(serveur, j.uuid(), "SalImpaye", LOINTAIN, "policier", 60L);
            serveur.closeDay(LOINTAIN);
            List<Component> sortie = new ArrayList<>();
            CommandSourceStack equipe = e.serveur.createCommandSourceStack().withSource(new Ecoute(sortie))
                    .withPermission(2);
            try {
                e.serveur.getCommands().getDispatcher().execute("eco stats", equipe);
            } catch (CommandSyntaxException ex) {
                throw new AssertionError("eco stats : " + ex.getMessage());
            }
            String stats = sortie.isEmpty() ? "" : sortie.get(0).getString();
            String ligne = texte("command.oneforallsurvival.eco.stats.salaries_owed", CoinMath.format(60L), 1);
            helper.assertTrue(stats.contains(ligne), "/eco stats « " + ligne + " » : " + stats);
            helper.assertTrue(ligne.contains(CoinMath.format(60L).getString() + " (1)"), "ligne : " + ligne);
            // La file (/eco salary owed) et la dette du trésor (/eco treasury) lisent la même dette.
            sortie.clear();
            try {
                e.serveur.getCommands().getDispatcher().execute("eco salary owed", equipe);
                e.serveur.getCommands().getDispatcher().execute("eco treasury", equipe);
            } catch (CommandSyntaxException ex) {
                throw new AssertionError("eco salary owed / eco treasury : " + ex.getMessage());
            }
            String file = sortie.size() < 2 ? "" : sortie.get(0).getString();
            helper.assertTrue(file.contains(LOINTAIN.toString()) && file.contains("SalImpaye") && file.contains("policier")
                    && file.contains(texte("command.oneforallsurvival.eco.salary.duration", 1, 0))
                    && file.contains(CoinMath.format(60L).getString()), "/eco salary owed : " + file);
            String etat = sortie.size() < 2 ? "" : sortie.get(1).getString();
            String dettes = texte("command.oneforallsurvival.eco.treasury.debts", CoinMath.format(60L), 1);
            helper.assertTrue(etat.contains(dettes), "/eco treasury « " + dettes + " » : " + etat);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void pasDePaiementPartiel(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalPartiel", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalPartiel", J, "policier", 60L);
            TreasuryAccount tresor = tresor(59L);
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J));
            helper.assertTrue(tresor.total() == 59L, "59 restent, vu " + tresor.total());
            helper.assertTrue(porte(j) == 0L && paie.owedTotal() == 60L, "rien versé, 60 dus");
            helper.assertTrue(PayrollService.payDebts(e.serveur, paie, tresor) == 0, "toujours rien");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lesDettesSePaientParAnciennete(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalAnciennete", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalAnciennete", J, "policier", 60L);
            // 20 et non 10 (brief) : avec 10, les 70 du second temps paieraient aussi la jeune, « J payée seule »
            // ne pourrait pas se voir.
            heure(paie, j.uuid(), "SalAnciennete", J.plusDays(1), "policier", 20L);
            TreasuryAccount tresor = tresor(0L);
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J.plusDays(1)));
            helper.assertTrue(paie.debts().size() == 2 && paie.debts().get(0).day().equals(J),
                    "deux dettes, J d'abord : " + paie.debts());
            // 40 : la jeune (20) serait payable, mais elle ne passe pas devant la vieille (60).
            tresor.add(Coin.SILVER, 4L);
            helper.assertTrue(PayrollService.payDebts(e.serveur, paie, tresor) == 0, "rien payé avec 40");
            helper.assertTrue(tresor.total() == 40L && paie.owedTotal() == 80L && porte(j) == 0L, "trésor 40, 80 dus");
            // 70 : J seule ; les 10 qui restent ne paient pas la jeune.
            tresor.add(Coin.SILVER, 3L);
            helper.assertTrue(PayrollService.payDebts(e.serveur, paie, tresor) == 1, "une dette payée");
            helper.assertTrue(porte(j) == 60L && tresor.total() == 10L, "J payée seule : porte " + porte(j));
            helper.assertTrue(paie.debts().size() == 1 && paie.debts().get(0).day().equals(J.plusDays(1)),
                    "reste J + 1 : " + paie.debts());
            // 80 de plus : J + 1.
            tresor.add(Coin.SILVER, 8L);
            helper.assertTrue(PayrollService.payDebts(e.serveur, paie, tresor) == 1 && paie.debts().isEmpty(),
                    "J + 1 payée");
            helper.assertTrue(porte(j) == 80L && tresor.total() == 70L, "porte " + porte(j) + ", trésor " + tresor.total());
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leMaireToucheSonSalaireEnPlusDeSonMetier(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalMaire", "policier");
            e.roles.appointMayor(j.uuid());
            // Le maire n'est pas salarié par défaut (seule la police l'est) : l'équipe lui fixe 60 par heure le temps du
            // comptage, rendu aussitôt (essai synchrone).
            long ancienTauxMaire = SalaryRates.rateFor(SalaryRates.MAYOR_KEY);
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            RoleTimeTracker suivi = new RoleTimeTracker();
            long t = debut(J) + 6L * HEURE;
            try {
                SalaryRates.set(SalaryRates.MAYOR_KEY, 60L);
                long tauxMaire = SalaryRates.rateFor(SalaryRates.MAYOR_KEY);
                helper.assertTrue(tauxMaire == 60L, "maire à 60, vu " + tauxMaire);
                suivi.onLoggedIn(j.joueur(), t);
                for (int i = 1; i <= 60; i++) {
                    suivi.sample(e.serveur, paie, t + i * 60_000L);
                }
            } finally {
                SalaryRates.set(SalaryRates.MAYOR_KEY, ancienTauxMaire);
            }
            helper.assertTrue(SalaryRates.rateFor(SalaryRates.MAYOR_KEY) == ancienTauxMaire, "taux du maire rendu");
            TreasuryAccount tresor = tresor(200L);
            j.textesRecus();
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J));
            helper.assertTrue(porte(j) == 120L && tresor.total() == 80L, "deux salaires : porte " + porte(j)
                    + ", trésor " + tresor.total());
            helper.assertTrue(lignes(tresor, Kind.SALARY_PAID).size() == 2, "deux lignes SALARY_PAID");
            String maire = Component.translatable("command.oneforallsurvival.eco.salary.mayor").getString();
            List<String> recus = j.textesRecus();
            helper.assertTrue(recus.stream().anyMatch(s -> s.contains("Policier"))
                    && recus.stream().anyMatch(s -> s.contains(maire)), "rôles libellés : " + recus);
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : jours manqués, horloge, relecture, conservation
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void deuxJoursManquesDeuxCloturesUneSeuleFois(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalManque", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J);
            heure(paie, j.uuid(), "SalManque", J.plusDays(1), "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            long demarrage = debut(J.plusDays(3)) + HEURE;
            int clos = PayrollService.closeDueDays(e.serveur, paie, tresor, demarrage);
            helper.assertTrue(clos == 2, "deux jours clos, vu " + clos);
            List<TreasuryLedger.Line> jours = lignes(tresor, Kind.DAY_CLOSED);
            helper.assertTrue(jours.size() == 2, "deux lignes DAY_CLOSED : " + jours);
            // page(0) va du plus récent au plus ancien.
            TreasuryLedger.Line j1 = jours.get(1);
            TreasuryLedger.Line j2 = jours.get(0);
            helper.assertTrue(j1.reason().startsWith(J.plusDays(1).toString()) && j1.amount() == 60L
                    && j1.reason().contains(" 1 "), "J + 1, 1 salaire : " + j1);
            helper.assertTrue(j2.reason().startsWith(J.plusDays(2).toString()) && j2.amount() == 0L
                    && j2.reason().contains(" 0 "), "J + 2, 0 salaire : " + j2);
            helper.assertTrue(lignes(tresor, Kind.SALARY_PAID).size() == 1 && porte(j) == 60L, "payé une fois");
            helper.assertTrue(PayrollService.closeDueDays(e.serveur, paie, tresor, demarrage + 1_000L) == 0,
                    "second appel : rien");
            helper.assertTrue(lignes(tresor, Kind.DAY_CLOSED).size() == 2 && porte(j) == 60L, "rien de plus");
            helper.assertTrue(J.plusDays(2).equals(paie.lastClosed()), "dernier clos J + 2 : " + paie.lastClosed());
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneClotureRelueNeSeRejouePas(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalRelue", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalRelue", J, "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J) + 5_000L);
            // Arrêt brutal juste après : relecture de la paie et du trésor, même fichier pour chacun.
            Payroll paieRelue = Payroll.load(paie.save(new CompoundTag()));
            TreasuryAccount tresorRelu = TreasuryAccount.load(tresor.save(new CompoundTag()));
            helper.assertTrue(J.equals(paieRelue.lastClosed()), "J clos après relecture");
            int clos = PayrollService.closeDueDays(e.serveur, paieRelue, tresorRelu, minuit(J) + 60_000L);
            helper.assertTrue(clos == 0, "rien à reclore, vu " + clos);
            helper.assertTrue(PayrollService.closeDueDays(e.serveur, paieRelue, tresorRelu, minuit(J) + 20L * HEURE) == 0,
                    "toujours rien dans la journée");
            helper.assertTrue(lignes(tresorRelu, Kind.DAY_CLOSED).size() == 1
                    && lignes(tresorRelu, Kind.SALARY_PAID).size() == 1, "une clôture, un paiement");
            helper.assertTrue(porte(j) == 60L && tresorRelu.total() == 40L, "payé une fois");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneHorlogeQuiReculeNeRepaiePas(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalRecul", "policier");
            e.roles.removeMayor();
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalRecul", J, "policier", 60L);
            TreasuryAccount tresor = tresor(200L);
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J) + HEURE);
            helper.assertTrue(porte(j) == 60L, "payé à minuit");
            // L'horloge recule de deux heures, de l'autre côté de la frontière déjà close.
            long recule = minuit(J) - HEURE;
            helper.assertTrue(PayrollService.closeDueDays(e.serveur, paie, tresor, recule) == 0, "rien ne se clôt");
            helper.assertTrue(J.equals(paie.lastClosed()), "dernier clos inchangé");
            // Le temps compté pendant le recul n'est ni perdu ni laissé sur J : il va à J + 1.
            RoleTimeTracker suivi = new RoleTimeTracker();
            suivi.onLoggedIn(j.joueur(), recule);
            for (int i = 1; i <= 30; i++) {
                suivi.sample(e.serveur, paie, recule + i * 60_000L);
            }
            helper.assertTrue(paie.secondsFor(j.uuid(), J).isEmpty(), "rien sur J clos : " + paie.secondsFor(j.uuid(), J));
            helper.assertTrue(paie.secondsFor(j.uuid(), J.plusDays(1)).getOrDefault("policier", 0L) == 1_800L,
                    "30 min sur J + 1 : " + paie.secondsFor(j.uuid(), J.plusDays(1)));
            helper.assertTrue(PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J) + 2L * HEURE) == 0,
                    "toujours J");
            helper.assertTrue(lignes(tresor, Kind.DAY_CLOSED).size() == 1 && porte(j) == 60L, "J payé une seule fois");
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J.plusDays(1)));
            helper.assertTrue(porte(j) == 90L, "les 30 min payées à la clôture de J + 1 : porte " + porte(j));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void quatreCentsJoursEteintsSontBornes(GameTestHelper helper) {
        essai(helper, e -> {
            helper.assertTrue(EconomyConfig.payMaxMissedDays() == 400, "plafond 400, vu " + EconomyConfig.payMaxMissedDays());
            JoueurDeTest j = e.joueur("SalEteint", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J);
            heure(paie, j.uuid(), "SalEteint", J.plusDays(1), "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            // Éteint le soir de J + 1, rallumé 500 jours plus tard : J + 1 à J + 500 sont manqués.
            int clos = PayrollService.closeDueDays(e.serveur, paie, tresor, debut(J.plusDays(501)) + HEURE);
            helper.assertTrue(clos == 400, "400 clôtures, vu " + clos);
            helper.assertTrue(lignes(tresor, Kind.DAY_CLOSED).size() == 400, "400 lignes DAY_CLOSED");
            helper.assertTrue(J.plusDays(500).equals(paie.lastClosed()), "dernier clos J + 500 : " + paie.lastClosed());
            // Le temps du jour sauté n'est pas perdu : payé une fois, au premier jour gardé.
            helper.assertTrue(lignes(tresor, Kind.SALARY_PAID).size() == 1 && porte(j) == 60L,
                    "le salaire de J + 1 payé une fois : porte " + porte(j));
            helper.assertTrue(PayrollService.lastSkippedDays() == 100L, "100 jours sautés avec un avertissement, vu "
                    + PayrollService.lastSkippedDays());
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void premierDemarrageRienDeRetroactif(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalPremier", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            heure(paie, j.uuid(), "SalPremier", J, "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            int clos = PayrollService.closeDueDays(e.serveur, paie, tresor, debut(J) + 12L * HEURE);
            helper.assertTrue(clos == 0, "rien au premier démarrage, vu " + clos);
            helper.assertTrue(J.minusDays(1).equals(paie.lastClosed()), "dernier clos = la veille : " + paie.lastClosed());
            helper.assertTrue(tresor.ledger().size() == 0 && paie.debts().isEmpty() && porte(j) == 0L, "rien d'autre");
            helper.assertTrue(paie.secondsFor(j.uuid(), J).getOrDefault("policier", 0L) == 3_600L, "le jour garde son temps");
            helper.assertTrue(PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J)) == 1 && porte(j) == 60L,
                    "J se clôt à son minuit");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void sansEmplacementLesSalairesPassent(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalSansBloc", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalSansBloc", J, "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            helper.assertTrue(tresor.position().isEmpty(), "aucun bloc du trésor posé");
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J));
            helper.assertTrue(porte(j) == 60L && tresor.total() == 40L, "payé sans emplacement");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unJoueurRenommeRecoitSonSalaire(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalNouveauNom", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            // Compté sous son ancien nom, même UUID.
            heure(paie, j.uuid(), "SalAncienNom", J, "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J));
            helper.assertTrue(porte(j) == 60L, "payé à l'UUID, porte " + porte(j));
            List<TreasuryLedger.Line> payes = lignes(tresor, Kind.SALARY_PAID);
            helper.assertTrue(payes.size() == 1 && j.uuid().equals(payes.get(0).actor()), "ligne à son UUID : " + payes);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void aucunePieceCreeeNiDetruite(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("SalConsA", "policier");
            JoueurDeTest b = e.joueur("SalConsB", "medecin");
            UUID idB = b.uuid();
            b.quitter();
            e.coffreFort(a.uuid());
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, a.uuid(), "SalConsA", J, "policier", 60L);
            heure(paie, idB, "SalConsB", J, "medecin", 30L);
            heure(paie, a.uuid(), "SalConsA", J, SalaryRates.MAYOR_KEY, 60L);
            TreasuryAccount tresor = tresor(100L);
            EconomyLedger registre = EconomyLedger.get(e.serveur);
            long circulation = registre.inCirculation();
            long avant = tresor.total() + e.comptes().total(a.uuid()) + porte(a) + enAttente(e.serveur, idB);
            PayrollService.closeDueDays(e.serveur, paie, tresor, minuit(J));
            long apres = tresor.total() + e.comptes().total(a.uuid()) + porte(a) + enAttente(e.serveur, idB);
            helper.assertTrue(apres == avant, "masse " + avant + " puis " + apres);
            helper.assertTrue(registre.inCirculation() == circulation, "en circulation inchangé");
            helper.assertTrue(paie.owedTotal() > 0L && tresor.total() < 60L, "une dette reste : " + paie.debts()
                    + ", trésor " + tresor.total());
            long payes = 0L;
            for (TreasuryLedger.Line l : lignes(tresor, Kind.SALARY_PAID)) {
                payes += l.amount();
            }
            helper.assertTrue(payes == 100L - tresor.total(), "le trésor a perdu exactement ce qu'il a payé");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot C : connexion
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void laConnexionAnnonceLesDettes(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalDette", "policier");
            UUID id = j.uuid();
            j.quitter();
            Payroll paie = Payroll.get(e.serveur);
            heure(paie, id, "SalDette", LOINTAIN, "policier", 60L);
            heure(paie, id, "SalDette", LOINTAIN, SalaryRates.MAYOR_KEY, 30L);
            paie.closeDay(LOINTAIN);
            JoueurDeTest retour = e.revenir("SalDette");
            helper.assertTrue(retour.messagesALaConnexion().contains(PayrollService.MESSAGE + "login_owed"),
                    "avis des dettes : " + retour.messagesALaConnexion());
            String attendu = texte(PayrollService.MESSAGE + "login_owed", CoinMath.format(90L), 2);
            helper.assertTrue(retour.textesALaConnexion().contains(attendu), "« " + attendu + " » : "
                    + retour.textesALaConnexion());
            // /treasury, pour lui : ce que la ville lui doit.
            retour.textesRecus();
            try {
                e.serveur.getCommands().getDispatcher().execute("treasury", retour.joueur().createCommandSourceStack());
            } catch (CommandSyntaxException ex) {
                throw new AssertionError("treasury : " + ex.getMessage());
            }
            String vous = texte("command.oneforallsurvival.treasury.owed_you", CoinMath.format(90L), 2);
            List<String> recus = retour.textesRecus();
            helper.assertTrue(recus.stream().anyMatch(s -> s.contains(vous)), "/treasury « " + vous + " » : " + recus);
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot de la pause : horloge qui saute pendant que le serveur tourne (revue finale, Important 1)
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_PAUSE)
    public static void unSautDHorlogeMetLaPaieEnPause(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalSaut", "policier");
            e.roles.removeMayor();
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalSaut", J, "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            long t = debut(J) + HEURE;
            helper.assertTrue(PayrollService.onClock(e.serveur, paie, tresor, t) == 0, "rien à clore au départ");
            // L'horloge du système saute de dix jours pendant que le serveur tourne.
            long saut = t + 10L * 24L * HEURE;
            int clos = PayrollService.onClock(e.serveur, paie, tresor, saut);
            helper.assertTrue(clos == 0, "aucun jour clos pendant le saut, vu " + clos);
            helper.assertTrue(paie.paused(), "paie en pause");
            helper.assertTrue(J.plusDays(10).equals(paie.pausedSeen()) && J.equals(paie.pausedExpected()),
                    "date vue J + 10, attendue J : " + paie.pausedSeen() + " / " + paie.pausedExpected());
            helper.assertTrue(porte(j) == 0L && tresor.total() == 100L && paie.debts().isEmpty(), "rien de payé");
            helper.assertTrue(tresor.ledger().size() == 0, "registre intact : " + tresor.ledger().size());
            helper.assertTrue(J.minusDays(1).equals(paie.lastClosed()), "dernier clos inchangé : " + paie.lastClosed());
            helper.assertTrue(paie.secondsFor(j.uuid(), J).getOrDefault("policier", 0L) == 3_600L, "le temps de J reste");
            // Toujours en pause aux vérifications suivantes, même sans nouveau saut.
            helper.assertTrue(PayrollService.onClock(e.serveur, paie, tresor, saut + 1_000L) == 0 && paie.paused()
                    && tresor.ledger().size() == 0, "la pause tient");
            // /eco stats le dit (la paie du serveur n'est pas celle-ci : le texte se lit sur la clé de langue).
            String ligne = texte("command.oneforallsurvival.eco.stats.paused", J.plusDays(10).toString(), J.toString());
            helper.assertTrue(!ligne.equals("command.oneforallsurvival.eco.stats.paused"), "ligne « Paie en pause » traduite");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_PAUSE)
    public static void reprendreClotLesJoursUneFois(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalReprise", "policier");
            e.roles.removeMayor();
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalReprise", J, "policier", 60L);
            TreasuryAccount tresor = tresor(100L);
            long t = debut(J) + HEURE;
            PayrollService.onClock(e.serveur, paie, tresor, t);
            long saut = t + 10L * 24L * HEURE;
            PayrollService.onClock(e.serveur, paie, tresor, saut);
            helper.assertTrue(paie.paused(), "en pause");
            // L'heure est juste : on clôt les jours dus, comme un rattrapage.
            int clos = PayrollService.resume(e.serveur, paie, tresor, saut + 1_000L);
            helper.assertTrue(clos == 10, "dix jours clos (J à J + 9), vu " + clos);
            helper.assertTrue(!paie.paused() && J.plusDays(9).equals(paie.lastClosed()), "reprise : " + paie.lastClosed());
            helper.assertTrue(lignes(tresor, Kind.DAY_CLOSED).size() == 10 && porte(j) == 60L, "payé une fois");
            helper.assertTrue(PayrollService.onClock(e.serveur, paie, tresor, saut + 2_000L) == 0 && !paie.paused(),
                    "plus rien, pas de nouvelle pause");
            helper.assertTrue(PayrollService.resume(e.serveur, paie, tresor, saut + 3_000L) == 0, "une seconde reprise : rien");
            helper.assertTrue(lignes(tresor, Kind.DAY_CLOSED).size() == 10 && lignes(tresor, Kind.SALARY_PAID).size() == 1
                    && porte(j) == 60L, "toujours une seule fois");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_PAUSE)
    public static void ramenerLaDateApresLeRetourDeLHeure(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalRetour", "policier");
            e.roles.removeMayor();
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.minusDays(1));
            heure(paie, j.uuid(), "SalRetour", J, "policier", 60L);
            TreasuryAccount tresor = tresor(200L);
            long t = debut(J) + HEURE;
            PayrollService.onClock(e.serveur, paie, tresor, t);
            long saut = t + 10L * 24L * HEURE;
            PayrollService.onClock(e.serveur, paie, tresor, saut);
            helper.assertTrue(paie.paused(), "en pause");
            // Pendant le saut, une demi-heure comptée sur le jour faux (J + 10).
            paie.credit(j.uuid(), "SalRetour", J.plusDays(10), "policier", 1_800L, 60L, maxSecondes());
            // L'heure revient ; l'équipe ramène la date.
            long retour = t + HEURE;
            PayrollService.resetDay(e.serveur, paie, tresor, retour);
            helper.assertTrue(!paie.paused(), "pause levée");
            helper.assertTrue(J.minusDays(1).equals(paie.lastClosed()), "dernier clos : la veille, vu " + paie.lastClosed());
            helper.assertTrue(paie.secondsFor(j.uuid(), J).getOrDefault("policier", 0L) == 5_400L,
                    "1 h 30 sur J, rien de perdu : " + paie.secondsFor(j.uuid(), J));
            helper.assertTrue(paie.secondsFor(j.uuid(), J.plusDays(10)).isEmpty(), "plus rien sur J + 10");
            helper.assertTrue(porte(j) == 0L && tresor.ledger().size() == 0, "rien de payé ni noté");
            helper.assertTrue(PayrollService.onClock(e.serveur, paie, tresor, retour + 1_000L) == 0 && !paie.paused(),
                    "pas de nouvelle pause après le retour");
            // Les clôtures reprennent dès le minuit suivant.
            helper.assertTrue(PayrollService.onClock(e.serveur, paie, tresor, minuit(J)) == 1, "J se clôt à son minuit");
            helper.assertTrue(porte(j) == 90L, "1 h 30 payée une fois : porte " + porte(j));
            helper.assertTrue(PayrollService.onClock(e.serveur, paie, tresor, minuit(J) + 1_000L) == 0
                    && lignes(tresor, Kind.SALARY_PAID).size() == 1 && porte(j) == 90L, "aucun double paiement");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_PAUSE)
    public static void ramenerLaDateApresDesJoursClosParErreur(GameTestHelper helper) {
        essai(helper, e -> {
            // Le scénario de la revue : des jours futurs ont été clos (horloge en avance d'une ancienne version), puis
            // l'heure est revenue ; le temps compté depuis va au lendemain du dernier clos.
            JoueurDeTest j = e.joueur("SalErreur", "policier");
            e.roles.removeMayor();
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J.plusDays(9));
            TreasuryAccount tresor = tresor(200L);
            heure(paie, j.uuid(), "SalErreur", J, "policier", 60L);
            helper.assertTrue(paie.secondsFor(j.uuid(), J.plusDays(10)).getOrDefault("policier", 0L) == 3_600L,
                    "compté au lendemain du dernier clos");
            long maintenant = debut(J) + 2L * HEURE;
            helper.assertTrue(PayrollService.onClock(e.serveur, paie, tresor, maintenant) == 0, "rien ne se clôt");
            PayrollService.resetDay(e.serveur, paie, tresor, maintenant);
            helper.assertTrue(J.minusDays(1).equals(paie.lastClosed()), "dernier clos ramené : " + paie.lastClosed());
            helper.assertTrue(paie.secondsFor(j.uuid(), J).getOrDefault("policier", 0L) == 3_600L, "1 h sur J");
            helper.assertTrue(PayrollService.onClock(e.serveur, paie, tresor, minuit(J)) == 1 && porte(j) == 60L,
                    "J se clôt à son vrai minuit");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_PAUSE)
    public static void unDemarrageApresDixJoursEteintRattrapeSansPause(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalEteint10", "policier");
            e.roles.removeMayor();
            Payroll avant = Payroll.load(new CompoundTag());
            avant.setLastClosed(J.minusDays(1));
            heure(avant, j.uuid(), "SalEteint10", J, "policier", 60L);
            avant.markChecked(debut(J) + HEURE);
            // Arrêt, relance dix jours plus tard : la vérification d'avant ne survit pas à la relecture.
            Payroll paie = Payroll.load(avant.save(new CompoundTag()));
            TreasuryAccount tresor = tresor(100L);
            long demarrage = debut(J.plusDays(10)) + HEURE;
            int clos = PayrollService.startUp(e.serveur, paie, tresor, demarrage);
            helper.assertTrue(clos == 10 && !paie.paused(), "rattrapage normal, sans pause : " + clos);
            helper.assertTrue(porte(j) == 60L && lignes(tresor, Kind.DAY_CLOSED).size() == 10, "payé une fois");
            helper.assertTrue(PayrollService.onClock(e.serveur, paie, tresor, demarrage + 1_000L) == 0 && !paie.paused(),
                    "le premier tick ne prend pas le démarrage pour un saut");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_PAUSE)
    public static void auDemarrageLaFileEstPayee(GameTestHelper helper) {
        essai(helper, e -> {
            // Revue finale, M3 : de l'argent arrivé juste avant un arrêt paie la file dès le démarrage, même sans jour
            // à clore.
            JoueurDeTest j = e.joueur("SalFile", "policier");
            Payroll paie = Payroll.load(new CompoundTag());
            paie.setLastClosed(J);
            heure(paie, j.uuid(), "SalFile", J, "policier", 60L);
            paie.closeDay(J.plusDays(1));
            helper.assertTrue(paie.debts().size() == 1, "une dette : " + paie.debts());
            TreasuryAccount tresor = tresor(100L);
            int clos = PayrollService.startUp(e.serveur, paie, tresor, debut(J.plusDays(1)) + HEURE);
            helper.assertTrue(clos == 0, "aucun jour à clore, vu " + clos);
            helper.assertTrue(paie.debts().isEmpty() && porte(j) == 60L && tresor.total() == 40L,
                    "la file est payée au démarrage : dettes " + paie.debts() + ", porte " + porte(j));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_PAUSE)
    public static void enPauseLeDemarrageNeClotRien(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest j = e.joueur("SalPauseRelance", "policier");
            Payroll avant = Payroll.load(new CompoundTag());
            avant.setLastClosed(J.minusDays(1));
            heure(avant, j.uuid(), "SalPauseRelance", J, "policier", 60L);
            avant.pause(J.plusDays(10), J);
            Payroll paie = Payroll.load(avant.save(new CompoundTag()));
            TreasuryAccount tresor = tresor(100L);
            int clos = PayrollService.startUp(e.serveur, paie, tresor, debut(J.plusDays(10)) + HEURE);
            helper.assertTrue(clos == 0 && paie.paused(), "une relance ne lève pas la pause : " + clos);
            helper.assertTrue(porte(j) == 0L && tresor.ledger().size() == 0, "rien de payé ni noté");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot du tick : la vraie paie du serveur, horloge figée
    // ---------------------------------------------------------------------------------------------

    @BeforeBatch(batch = LOT_TICK)
    public static void avantTick(ServerLevel level) {
        long fige = System.currentTimeMillis();
        EconomyClock.setClock(() -> fige);
    }

    @AfterBatch(batch = LOT_TICK)
    public static void apresTick(ServerLevel level) {
        EconomyClock.reset();
    }

    @GameTest(template = "test/plancher", batch = LOT_TICK, timeoutTicks = 100)
    public static void unDepotPaieLaFileAuTickSuivant(GameTestHelper helper) {
        Essai e = new Essai(helper);
        TreasuryAccount tresor = TreasuryAccount.get(e.serveur);
        EnumMap<Coin, Long> solde = tresor.balance();
        Payroll paie = Payroll.get(e.serveur);
        JoueurDeTest j = e.joueur("SalTick", RoleBook.DEFAULT_ROLE_ID);
        Runnable rendre = () -> {
            for (Map.Entry<Coin, Long> c : tresor.balance().entrySet()) {
                tresor.remove(c.getKey(), c.getValue());
            }
            solde.forEach(tresor::add);
            e.close();
        };
        try {
            helper.assertTrue(paie.debts().isEmpty(), "aucune dette au serveur avant l'essai : " + paie.debts());
            for (Map.Entry<Coin, Long> c : solde.entrySet()) {
                tresor.remove(c.getKey(), c.getValue());
            }
            tresor.takeFundsArrived();
            heure(paie, j.uuid(), "SalTick", LOINTAIN, "policier", 60L);
            paie.closeDay(LOINTAIN);
            tresor.credit(Map.of(Coin.SILVER, 6L), Kind.TAX, null, "", "taxe d'essai");
            // Pas dans la transaction qui verse.
            helper.assertTrue(paie.debts().size() == 1 && tresor.total() == 60L && porte(j) == 0L,
                    "rien payé dans l'appel de credit");
        } catch (RuntimeException | AssertionError ex) {
            rendre.run();
            throw ex;
        }
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(paie.debts().isEmpty(), "payée au tick suivant : " + paie.debts());
                helper.assertTrue(tresor.total() == 0L && porte(j) == 60L, "trésor " + tresor.total() + ", porte "
                        + porte(j));
            } finally {
                rendre.run();
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
