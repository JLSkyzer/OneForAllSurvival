package dev.oneforallsurvival.dev;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.economy.payroll.RoleTimeTracker;
import dev.oneforallsurvival.economy.payroll.SalaryRates;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le temps passé dans un rôle payé (lot E3, tâche 3). Chaque essai a son {@link RoleTimeTracker} et sa
 * {@link Payroll} détachés, et passe ses instants à la main : rien n'attend une vraie seconde ni un vrai minuit. Les
 * rôles, le maire et les taux sont ceux du serveur ; chaque essai les rend comme il les a trouvés, sans rien attendre
 * entre deux (synchrone), pour ne pas gêner les essais voisins du lot.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class TempsDeRoleGameTests {

    private static final String LOT_A = "temps_de_role_a";
    private static final String LOT_B = "temps_de_role_b";

    private TempsDeRoleGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Un instant au milieu d'un jour de paie (6 h après son début) : deux heures plus tard, c'est le même jour. */
    private static long midi() {
        ZoneId zone = EconomyCalendar.configuredZone();
        return EconomyCalendar.boundaryAfter(LocalDate.of(2026, 6, 14), zone, EconomyConfig.payHour()) + 6L * 3_600_000L;
    }

    private static LocalDate jourDe(long t) {
        return EconomyCalendar.payDay(t, EconomyCalendar.configuredZone(), EconomyConfig.payHour());
    }

    /** Un essai avec un joueur connecté, son rôle posé ; rôle et maire rendus à la fin. */
    private interface Corps {
        void run(MinecraftServer serveur, JoueurDeTest joueur, RoleBook roles);
    }

    private static void avecJoueur(GameTestHelper helper, String nom, String role, Corps corps) {
        MinecraftServer serveur = helper.getLevel().getServer();
        RoleSavedData donnees = RoleSavedData.get(serveur);
        RoleBook roles = donnees.book();
        Optional<UUID> ancienMaire = roles.mayor();
        JoueurDeTest joueur = null;
        String ancienRole = null;
        try {
            joueur = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
            ancienRole = roles.roleOf(joueur.uuid()).id();
            roles.assign(joueur.uuid(), role);
            corps.run(serveur, joueur, roles);
            helper.succeed();
        } finally {
            if (joueur != null && ancienRole != null) {
                roles.assign(joueur.uuid(), ancienRole);
            }
            if (ancienMaire.isPresent()) {
                roles.appointMayor(ancienMaire.get());
            } else {
                roles.removeMayor();
            }
            donnees.setDirty();
            if (joueur != null && joueur.connecte()) {
                joueur.quitter();
            }
        }
    }

    private static long secondes(Payroll paie, UUID joueur, LocalDate jour, String cle) {
        return paie.secondsFor(joueur, jour).getOrDefault(cle, 0L);
    }

    // ---------------------------------------------------------------------------------------------
    // Échantillons
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unPolicierConnecteAccumuleSonTemps(GameTestHelper helper) {
        avecJoueur(helper, "RolePolicier", "policier", (serveur, j, roles) -> {
            helper.assertTrue(SalaryRates.rateFor("policier") > 0L, "le policier a un taux");
            RoleTimeTracker suivi = new RoleTimeTracker();
            Payroll paie = Payroll.load(new CompoundTag());
            long t = midi();
            suivi.onLoggedIn(j.joueur(), t);
            for (int i = 1; i <= 10; i++) {
                suivi.sample(serveur, paie, t + i * 1_000L);
            }
            helper.assertTrue(paie.secondsFor(j.uuid(), jourDe(t)).equals(Map.of("policier", 10L)),
                    "10 s sous policier, vu " + paie.secondsFor(j.uuid(), jourDe(t)));
            helper.assertTrue(paie.rateSecondsFor(j.uuid(), jourDe(t)).get("policier")
                    == 10L * SalaryRates.rateFor("policier"), "cuivre-secondes au taux du policier");
            helper.assertTrue(paie.isDirty(), "la paie est marquée sale");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unCitoyenSansTauxNAccumuleRien(GameTestHelper helper) {
        avecJoueur(helper, "RoleCitoyen", RoleBook.DEFAULT_ROLE_ID, (serveur, j, roles) -> {
            helper.assertTrue(SalaryRates.rateFor(RoleBook.DEFAULT_ROLE_ID) == 0L, "le citoyen n'a pas de taux");
            roles.removeMayor();
            RoleTimeTracker suivi = new RoleTimeTracker();
            Payroll paie = Payroll.load(new CompoundTag());
            long t = midi();
            suivi.onLoggedIn(j.joueur(), t);
            for (int i = 1; i <= 10; i++) {
                suivi.sample(serveur, paie, t + i * 1_000L);
            }
            helper.assertTrue(paie.secondsFor(j.uuid(), jourDe(t)).isEmpty(),
                    "rien pour un citoyen, vu " + paie.secondsFor(j.uuid(), jourDe(t)));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leMaireAccumuleDeuxSalaires(GameTestHelper helper) {
        avecJoueur(helper, "RoleMaire", RoleBook.ENGINEER_ROLE_ID, (serveur, j, roles) -> {
            long ancienTaux = SalaryRates.rateFor(RoleBook.ENGINEER_ROLE_ID);
            // Par défaut, seule la police est salariée (décision du 2026-11-15) : le maire reçoit ici un taux fixé par
            // l'équipe, comme le ferait /eco salary set mayor.
            long ancienTauxMaire = SalaryRates.rateFor(SalaryRates.MAYOR_KEY);
            try {
                SalaryRates.set(RoleBook.ENGINEER_ROLE_ID, 30L);
                SalaryRates.set(SalaryRates.MAYOR_KEY, 60L);
                roles.appointMayor(j.uuid());
                long tauxMaire = SalaryRates.rateFor(SalaryRates.MAYOR_KEY);
                helper.assertTrue(tauxMaire == 60L, "le maire a le taux fixé, vu " + tauxMaire);
                RoleTimeTracker suivi = new RoleTimeTracker();
                Payroll paie = Payroll.load(new CompoundTag());
                long t = midi();
                suivi.onLoggedIn(j.joueur(), t);
                for (int i = 1; i <= 10; i++) {
                    suivi.sample(serveur, paie, t + i * 1_000L);
                }
                LocalDate jour = jourDe(t);
                helper.assertTrue(paie.secondsFor(j.uuid(), jour).equals(Map.of("ingenieur", 10L, "mayor", 10L)),
                        "deux salaires, vu " + paie.secondsFor(j.uuid(), jour));
                helper.assertTrue(paie.rateSecondsFor(j.uuid(), jour)
                        .equals(Map.of("ingenieur", 300L, "mayor", 10L * tauxMaire)),
                        "chacun à son taux, vu " + paie.rateSecondsFor(j.uuid(), jour));
                // Destitué : seul le métier compte ensuite.
                roles.removeMayor();
                suivi.sample(serveur, paie, t + 15_000L);
                helper.assertTrue(secondes(paie, j.uuid(), jour, "ingenieur") == 15L
                        && secondes(paie, j.uuid(), jour, "mayor") == 10L, "après la destitution : "
                        + paie.secondsFor(j.uuid(), jour));
            } finally {
                SalaryRates.set(RoleBook.ENGINEER_ROLE_ID, ancienTaux);
                SalaryRates.set(SalaryRates.MAYOR_KEY, ancienTauxMaire);
            }
            // Vérifié avant le succeed() d'avecJoueur, qui suit le corps.
            helper.assertTrue(SalaryRates.rateFor(RoleBook.ENGINEER_ROLE_ID) == ancienTaux
                    && SalaryRates.rateFor(SalaryRates.MAYOR_KEY) == ancienTauxMaire, "taux de l'ingénieur et du maire rendus");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unRoleChangeEnCoursDeJourneeComptePourChacun(GameTestHelper helper) {
        avecJoueur(helper, "RoleChange", "policier", (serveur, j, roles) -> {
            roles.removeMayor();
            // Le médecin n'est pas salarié par défaut (seule la police l'est) : l'équipe lui fixe un taux le temps de l'essai.
            long ancienTaux = SalaryRates.rateFor("medecin");
            try {
                SalaryRates.set("medecin", 60L);
                helper.assertTrue(SalaryRates.rateFor("medecin") == 60L, "le médecin a le taux fixé");
                RoleTimeTracker suivi = new RoleTimeTracker();
                Payroll paie = Payroll.load(new CompoundTag());
                long t = midi();
                suivi.onLoggedIn(j.joueur(), t);
                for (int i = 1; i <= 5; i++) {
                    suivi.sample(serveur, paie, t + i * 1_000L);
                }
                roles.assign(j.uuid(), "medecin");
                for (int i = 6; i <= 10; i++) {
                    suivi.sample(serveur, paie, t + i * 1_000L);
                }
                helper.assertTrue(paie.secondsFor(j.uuid(), jourDe(t)).equals(Map.of("policier", 5L, "medecin", 5L)),
                        "5 s chacun, vu " + paie.secondsFor(j.uuid(), jourDe(t)));
            } finally {
                SalaryRates.set("medecin", ancienTaux);
            }
            helper.assertTrue(SalaryRates.rateFor("medecin") == ancienTaux, "taux du médecin rendu");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unEcartDeDeuxHeuresNeCompteQue60s(GameTestHelper helper) {
        avecJoueur(helper, "RoleFige", "policier", (serveur, j, roles) -> {
            RoleTimeTracker suivi = new RoleTimeTracker();
            Payroll paie = Payroll.load(new CompoundTag());
            long t = midi();
            suivi.onLoggedIn(j.joueur(), t);
            suivi.sample(serveur, paie, t + 2L * 3_600_000L);
            helper.assertTrue(secondes(paie, j.uuid(), jourDe(t), "policier") == 60L,
                    "60 s au plus, vu " + paie.secondsFor(j.uuid(), jourDe(t)));
            // L'échantillon suivant repart de là : une seconde de plus, pas deux heures.
            suivi.sample(serveur, paie, t + 2L * 3_600_000L + 1_000L);
            helper.assertTrue(secondes(paie, j.uuid(), jourDe(t), "policier") == 61L, "61 s ensuite");
            // Une horloge qui recule ne compte rien et ne retire rien.
            suivi.sample(serveur, paie, t);
            suivi.sample(serveur, paie, t + 1_000L);
            helper.assertTrue(secondes(paie, j.uuid(), jourDe(t), "policier") == 62L,
                    "recul : rien, puis 1 s, vu " + paie.secondsFor(j.uuid(), jourDe(t)));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unEchantillonACheval(GameTestHelper helper) {
        avecJoueur(helper, "RoleMinuit", "policier", (serveur, j, roles) -> {
            RoleTimeTracker suivi = new RoleTimeTracker();
            Payroll paie = Payroll.load(new CompoundTag());
            ZoneId zone = EconomyCalendar.configuredZone();
            LocalDate jour = LocalDate.of(2026, 6, 14);
            long frontiere = EconomyCalendar.boundaryAfter(jour, zone, EconomyConfig.payHour());
            suivi.onLoggedIn(j.joueur(), frontiere - 30_000L);
            suivi.sample(serveur, paie, frontiere + 20_000L);
            helper.assertTrue(secondes(paie, j.uuid(), jour, "policier") == 30L
                    && secondes(paie, j.uuid(), jour.plusDays(1), "policier") == 20L,
                    "30 s la veille, 20 s le lendemain, vu " + paie.secondsFor(j.uuid(), jour) + " / "
                    + paie.secondsFor(j.uuid(), jour.plusDays(1)));
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leResteDesMillisecondesPasseDUnEchantillonALAutre(GameTestHelper helper) {
        avecJoueur(helper, "RoleReste", "policier", (serveur, j, roles) -> {
            RoleTimeTracker suivi = new RoleTimeTracker();
            Payroll paie = Payroll.load(new CompoundTag());
            long t = midi();
            suivi.onLoggedIn(j.joueur(), t);
            // Trois échantillons de 1,5 s : 1 s (0,5 reporté), 2 s, 1 s (0,5 reporté) = 4 s, et non 3.
            suivi.sample(serveur, paie, t + 1_500L);
            suivi.sample(serveur, paie, t + 3_000L);
            suivi.sample(serveur, paie, t + 4_500L);
            helper.assertTrue(secondes(paie, j.uuid(), jourDe(t), "policier") == 4L,
                    "4 s pour 4,5 s, vu " + paie.secondsFor(j.uuid(), jourDe(t)));
            suivi.sample(serveur, paie, t + 5_000L);
            helper.assertTrue(secondes(paie, j.uuid(), jourDe(t), "policier") == 5L, "la demi-seconde reportée compte");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lePlafondJournalierEstTenuParLeSuivi(GameTestHelper helper) {
        avecJoueur(helper, "RolePlafond", "policier", (serveur, j, roles) -> {
            roles.removeMayor();
            RoleTimeTracker suivi = new RoleTimeTracker();
            Payroll paie = Payroll.load(new CompoundTag());
            long max = EconomyConfig.salaryMaxHoursPerDay() * 3_600L;
            // Départ juste après le début du jour : le plafond est atteint bien avant la frontière.
            ZoneId zone = EconomyCalendar.configuredZone();
            long t = EconomyCalendar.boundaryAfter(LocalDate.of(2026, 6, 13), zone, EconomyConfig.payHour()) + 1_000L;
            LocalDate jour = jourDe(t);
            suivi.onLoggedIn(j.joueur(), t);
            long pas = max / 60L + 30L;
            for (long i = 1; i <= pas; i++) {
                suivi.sample(serveur, paie, t + i * 60_000L);
            }
            helper.assertTrue(jourDe(t + pas * 60_000L).equals(jour), "toujours le même jour");
            helper.assertTrue(secondes(paie, j.uuid(), jour, "policier") == max,
                    "plafond de " + max + " s, vu " + paie.secondsFor(j.uuid(), jour));
            helper.assertTrue(paie.rateSecondsFor(j.uuid(), jour).get("policier") == max * SalaryRates.rateFor("policier"),
                    "cuivre-secondes plafonnées aussi");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Connexion, déconnexion, relance
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laDeconnexionCrediteJusquALInstant(GameTestHelper helper) {
        avecJoueur(helper, "RoleDepart", "policier", (serveur, j, roles) -> {
            RoleTimeTracker suivi = new RoleTimeTracker();
            Payroll paie = Payroll.load(new CompoundTag());
            long t = midi();
            suivi.onLoggedIn(j.joueur(), t);
            suivi.sample(serveur, paie, t + 1_000L);
            suivi.onLoggedOut(j.joueur(), paie, t + 1_000L + 30_500L);
            helper.assertTrue(secondes(paie, j.uuid(), jourDe(t), "policier") == 31L,
                    "1 s + 30 s à la déconnexion, vu " + paie.secondsFor(j.uuid(), jourDe(t)));
            // Oublié : un échantillon tardif (joueur encore dans la liste) repart de zéro.
            suivi.sample(serveur, paie, t + 600_000L);
            helper.assertTrue(secondes(paie, j.uuid(), jourDe(t), "policier") == 31L, "rien après la déconnexion");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void rienNeCompteHorsLigne(GameTestHelper helper) {
        MinecraftServer serveur = helper.getLevel().getServer();
        RoleSavedData donnees = RoleSavedData.get(serveur);
        RoleBook roles = donnees.book();
        RoleTimeTracker suivi = new RoleTimeTracker();
        Payroll paie = Payroll.load(new CompoundTag());
        long t = midi();
        Vec3 ou = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), "RoleAbsent", ou);
        UUID id = j.uuid();
        String ancienRole = roles.roleOf(id).id();
        try {
            roles.assign(id, "policier");
            suivi.onLoggedIn(j.joueur(), t);
            suivi.sample(serveur, paie, t + 10_000L);
            suivi.onLoggedOut(j.joueur(), paie, t + 10_000L);
            j.quitter();
            // Hors ligne une heure : les échantillons ne le voient pas.
            for (int i = 1; i <= 5; i++) {
                suivi.sample(serveur, paie, t + 10_000L + i * 600_000L);
            }
            helper.assertTrue(secondes(paie, id, jourDe(t), "policier") == 10L, "rien hors ligne");
            j = JoueurDeTest.rejoindre(helper.getLevel(), "RoleAbsent", ou);
            long retour = t + 3_610_000L;
            suivi.onLoggedIn(j.joueur(), retour);
            suivi.sample(serveur, paie, retour + 5_000L);
            helper.assertTrue(secondes(paie, id, jourDe(t), "policier") == 15L,
                    "10 s + 5 s après le retour, vu " + paie.secondsFor(id, jourDe(t)));
            helper.succeed();
        } finally {
            roles.assign(id, ancienRole);
            donnees.setDirty();
            if (j.connecte()) {
                j.quitter();
            }
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leTempsDuJourSurvitAUneRelance(GameTestHelper helper) {
        avecJoueur(helper, "RoleRelance", "policier", (serveur, j, roles) -> {
            RoleTimeTracker suivi = new RoleTimeTracker();
            Payroll paie = Payroll.load(new CompoundTag());
            long t = midi();
            LocalDate jour = jourDe(t);
            suivi.onLoggedIn(j.joueur(), t);
            for (int i = 1; i <= 20; i++) {
                suivi.sample(serveur, paie, t + i * 1_000L);
            }
            Map<String, Long> avant = paie.secondsFor(j.uuid(), jour);
            Map<String, Long> cuivreSecondes = paie.rateSecondsFor(j.uuid(), jour);
            // La relance : la paie relue depuis sa sauvegarde, un suivi neuf (sa mémoire est transitoire).
            Payroll relue = Payroll.load(paie.save(new CompoundTag()));
            helper.assertTrue(relue.secondsFor(j.uuid(), jour).equals(avant)
                    && relue.rateSecondsFor(j.uuid(), jour).equals(cuivreSecondes),
                    "mêmes secondes après relecture : " + relue.secondsFor(j.uuid(), jour) + " pour " + avant);
            RoleTimeTracker apres = new RoleTimeTracker();
            long reprise = t + 120_000L;
            apres.onLoggedIn(j.joueur(), reprise);
            apres.sample(serveur, relue, reprise + 5_000L);
            helper.assertTrue(secondes(relue, j.uuid(), jour, "policier") == 25L,
                    "20 s + 5 s après la relance, vu " + relue.secondsFor(j.uuid(), jour));
        });
    }

    // ---------------------------------------------------------------------------------------------
    // /eco salary
    // ---------------------------------------------------------------------------------------------

    private static final class Ecoute implements CommandSource {
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

        String cle() {
            Component c = messages.isEmpty() ? Component.empty() : messages.get(0);
            if (!(c.getContents() instanceof TranslatableContents) && !c.getSiblings().isEmpty()) {
                c = c.getSiblings().get(0);
            }
            return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
        }

        String tout() {
            StringBuilder out = new StringBuilder();
            for (Component c : messages) {
                out.append(c.getString()).append('\n');
            }
            messages.clear();
            return out.toString();
        }
    }

    private static int lancer(MinecraftServer serveur, CommandSourceStack source, String commande) {
        try {
            return serveur.getCommands().getDispatcher().execute(commande, source);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void ecoSalarySetEtList(GameTestHelper helper) {
        MinecraftServer serveur = helper.getLevel().getServer();
        long policier = SalaryRates.rateFor("policier");
        long maire = SalaryRates.rateFor(SalaryRates.MAYOR_KEY);
        Ecoute ecoute = new Ecoute();
        CommandSourceStack equipe = serveur.createCommandSourceStack().withSource(ecoute).withPermission(2);
        // Le monde d'essai est neuf à chaque passage : sa config est celle par défaut, où seule la police est salariée.
        helper.assertTrue(policier == 60L && maire == 0L && SalaryRates.rateFor("medecin") == 0L,
                "défauts : policier " + policier + ", maire " + maire + ", médecin " + SalaryRates.rateFor("medecin"));
        try {
            helper.assertTrue(lancer(serveur, equipe, "eco salary set policier 120") == 1, "set policier 120 : "
                    + ecoute.cle());
            ecoute.tout();
            helper.assertTrue(SalaryRates.rateFor("policier") == 120L, "taux lu : " + SalaryRates.rateFor("policier"));
            helper.assertTrue(SalaryRates.configured().contains("policier=120"),
                    "écrit dans la config : " + SalaryRates.configured());
            helper.assertTrue(lancer(serveur, equipe, "eco salary list") == 1, "list");
            String liste = ecoute.tout();
            helper.assertTrue(liste.contains("policier") && liste.contains("Policier"), "list : " + liste);
            String ligne = Component.translatable("command.oneforallsurvival.eco.salary.rate",
                    dev.oneforallsurvival.economy.CoinMath.format(120L)).getString();
            helper.assertTrue(liste.contains(ligne), "taux de 120 affiché (" + ligne + ") : " + liste);

            helper.assertTrue(lancer(serveur, equipe, "eco salary set inconnu 5") == 0, "clé inconnue refusée");
            helper.assertTrue("command.oneforallsurvival.eco.salary.unknown_key".equals(ecoute.cle()),
                    "refus : " + ecoute.cle());
            ecoute.tout();
            helper.assertTrue(SalaryRates.rateFor("inconnu") == 0L
                    && SalaryRates.configured().stream().noneMatch(s -> s.startsWith("inconnu")), "rien d'écrit");
            helper.assertTrue(lancer(serveur, equipe, "eco salary set policier 1000001") != 1, "taux trop grand refusé");
            ecoute.tout();

            helper.assertTrue(lancer(serveur, equipe, "eco salary set mayor 45") == 1, "set mayor 45");
            ecoute.tout();
            helper.assertTrue(SalaryRates.rateFor(SalaryRates.MAYOR_KEY) == 45L, "maire à 45");
            helper.assertTrue(EconomyConfig.mayorSalaryPerHour() == 45L, "config du maire à 45");
            helper.assertTrue(lancer(serveur, equipe, "eco salary set mayor 0") == 1, "set mayor 0");
            ecoute.tout();
            helper.assertTrue(SalaryRates.rateFor(SalaryRates.MAYOR_KEY) == 0L, "maire à 0");
            helper.assertTrue(EconomyConfig.mayorSalaryPerHour() == 0L, "config du maire à 0");

            // Réservé à l'équipe.
            CommandSourceStack sansDroit = serveur.createCommandSourceStack().withSource(ecoute).withPermission(0);
            helper.assertTrue(lancer(serveur, sansDroit, "eco salary list") != 1, "/eco salary réservé à l'équipe");
        } finally {
            SalaryRates.set("policier", policier);
            SalaryRates.set(SalaryRates.MAYOR_KEY, maire);
        }
        helper.assertTrue(SalaryRates.rateFor("policier") == policier
                && SalaryRates.rateFor(SalaryRates.MAYOR_KEY) == maire, "taux rendus");
        helper.succeed();
    }
}
