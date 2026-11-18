package dev.oneforallsurvival.economy.payroll;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.CommerceDelivery.Destination;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.town.TreasuryLedger;
import dev.oneforallsurvival.economy.town.TreasuryLedger.Kind;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * La paie de minuit et la dette de la ville. Chaque jour de paie fini est arrêté une seule fois, dans l'ordre (le
 * temps compté devient des salaires dus), puis la file des salaires dus est payée par ancienneté, salaire entier par
 * salaire entier, sur le trésor : la plus ancienne dette impayable arrête la file. La file est aussi payée au tick qui
 * suit une entrée d'argent au trésor ({@link TreasuryAccount#takeFundsArrived()}), jamais dans la transaction qui
 * verse. Un salaire payé est un transfert ({@link MoneyTransfer#SALARY}) : retiré exactement du trésor, livré par
 * {@link CommerceDelivery#payToSafeFirst} ; aucune pièce n'est créée, et une dette n'est pas de l'argent.
 *
 * <p>Tout prend ses données et l'heure en paramètre (les essais passent des instances détachées) ; les événements
 * lisent la paie et le trésor du serveur et {@link EconomyClock#now()}. Fil du serveur.
 */
public final class PayrollService {

    /** Préfixe des clés de langue des avis de salaire. */
    public static final String MESSAGE = "message.oneforallsurvival.salary.";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Le jour du dernier avertissement « horloge qui recule », pour ne pas le répéter chaque seconde. */
    @Nullable
    private static LocalDate warnedBehind;
    /** Combien de jours le dernier rattrapage a sautés (0 si aucun) ; lu par les essais. */
    private static long lastSkipped;

    private PayrollService() {}

    // ---------------------------------------------------------------------------------------------
    // Clôture
    // ---------------------------------------------------------------------------------------------

    /**
     * Arrête chaque jour de paie fini depuis le dernier réglé, dans l'ordre : dettes du jour (ligne
     * {@link Kind#SALARY_OWED} pour chacune), paiement de la file, avis de ce qui reste dû, ligne {@link Kind#DAY_CLOSED}
     * (montant : le total des salaires du jour), puis le jour est noté réglé. Premier appel (aucun jour réglé) : la
     * veille est notée réglée, rien d'autre. Au plus {@link EconomyConfig#payMaxMissedDays()} jours, les plus récents ;
     * les autres sont sautés avec un avertissement. Une horloge qui recule ne règle rien et avertit.
     *
     * @return le nombre de jours arrêtés
     */
    public static int closeDueDays(MinecraftServer server, Payroll payroll, TreasuryAccount treasury, long now) {
        ZoneId zone = EconomyCalendar.configuredZone();
        LocalDate today = EconomyCalendar.payDay(now, zone, EconomyConfig.payHour());
        LocalDate last = payroll.lastClosed();
        if (last == null) {
            payroll.setLastClosed(today.minusDays(1));
            return 0;
        }
        if (!today.isAfter(last)) {
            if (!today.equals(warnedBehind)) {
                warnedBehind = today;
                LOGGER.warn("Paie : l'horloge a recule (jour {} alors que le {} est deja regle) ; rien n'est regle. "
                        + "Si l'heure actuelle est juste : /eco salary resetday", today, last);
            }
            return 0;
        }
        List<LocalDate> due = EconomyCalendar.daysToClose(last, today, EconomyConfig.payMaxMissedDays());
        if (due.isEmpty()) {
            return 0;
        }
        long skipped = due.get(0).toEpochDay() - last.toEpochDay() - 1L;
        lastSkipped = skipped;
        if (skipped > 0L) {
            LOGGER.warn("Paie : {} jours manques au-dela du plafond de {} sont sautes (du {} au {}) ; leur temps compte "
                    + "va au {}", skipped, EconomyConfig.payMaxMissedDays(), last.plusDays(1), due.get(0).minusDays(1),
                    due.get(0));
            payroll.setLastClosed(due.get(0).minusDays(1));
        }
        for (LocalDate day : due) {
            List<Payroll.Debt> created = payroll.closeDay(day);
            long total = 0L;
            for (Payroll.Debt debt : created) {
                total = saturatedAdd(total, debt.amount());
                treasury.note(new TreasuryLedger.Line(now, debt.player(), debt.playerName(), Kind.SALARY_OWED,
                        debt.amount(), reason(debt)));
            }
            payDebts(server, payroll, treasury, now);
            List<Payroll.Debt> still = payroll.debts();
            for (Payroll.Debt debt : created) {
                if (still.contains(debt)) {
                    tellOwed(server, debt);
                }
            }
            treasury.note(new TreasuryLedger.Line(now, null, "", Kind.DAY_CLOSED, total,
                    day + " : " + created.size() + " salaire(s)"));
            payroll.setLastClosed(day);
        }
        return due.size();
    }

    /** Combien de jours le dernier rattrapage de {@link #closeDueDays} a sautés au-delà du plafond. */
    public static long lastSkippedDays() {
        return lastSkipped;
    }

    // ---------------------------------------------------------------------------------------------
    // Horloge qui saute : pause, reprise, remise à l'heure
    // ---------------------------------------------------------------------------------------------

    /** Un saut d'horloge plus grand que ceci, entre deux vérifications du serveur en marche, met la paie en pause. */
    public static final long JUMP_MILLIS = 26L * 3_600_000L;

    /**
     * La vérification de chaque seconde du serveur en marche. Si l'horloge a avancé de plus de {@link #JUMP_MILLIS}
     * depuis la vérification précédente (saut anormal : une horloge système réglée par erreur dans le futur), la paie
     * se met en pause au lieu de clore des jours qui n'existent peut-être pas : rien n'est clos, l'avertissement va au
     * journal et aux opérateurs connectés, et l'équipe choisit ({@code /eco salary resume} ou {@code resetday}). En
     * pause, rien ne se clôt ; hors pause, les jours finis se closent ({@link #closeDueDays}). La première vérification
     * après un chargement n'a pas de référence : le rattrapage du démarrage reste normal.
     *
     * @return le nombre de jours arrêtés
     */
    public static int onClock(MinecraftServer server, Payroll payroll, TreasuryAccount treasury, long now) {
        Long previous = payroll.lastCheck();
        payroll.markChecked(now);
        if (payroll.paused()) {
            return 0;
        }
        if (previous != null && now - previous > JUMP_MILLIS) {
            ZoneId zone = EconomyCalendar.configuredZone();
            LocalDate seen = EconomyCalendar.payDay(now, zone, EconomyConfig.payHour());
            LocalDate expected = EconomyCalendar.payDay(previous, zone, EconomyConfig.payHour());
            payroll.pause(seen, expected);
            LOGGER.warn("Paie en pause : l'horloge a saute de {} h pendant que le serveur tournait (jour {} au lieu du {}) ; "
                    + "rien n'est clos. Heure juste : /eco salary resume ; heure fausse : /eco salary resetday",
                    (now - previous) / 3_600_000L, seen, expected);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                warnPaused(player, payroll);
            }
            return 0;
        }
        return closeDueDays(server, payroll, treasury, now);
    }

    /**
     * Le démarrage : rattrapage des jours manqués serveur éteint (sauf en pause : une relance ne lève pas la pause),
     * puis paiement de la file (de l'argent arrivé au trésor juste avant un arrêt n'attend pas minuit), puis la
     * référence des vérifications suivantes.
     *
     * @return le nombre de jours arrêtés
     */
    public static int startUp(MinecraftServer server, Payroll payroll, TreasuryAccount treasury, long now) {
        int closed = 0;
        if (payroll.paused()) {
            LOGGER.warn("Paie en pause depuis un saut d'horloge (jour {} au lieu du {}) : rien n'est clos au demarrage. "
                    + "Heure juste : /eco salary resume ; heure fausse : /eco salary resetday",
                    payroll.pausedSeen(), payroll.pausedExpected());
        } else {
            closed = closeDueDays(server, payroll, treasury, now);
        }
        payDebts(server, payroll, treasury, now);
        payroll.markChecked(now);
        return closed;
    }

    /**
     * {@code /eco salary resume} : l'heure est juste. La pause est levée et les jours dus se closent, comme un
     * rattrapage (au plus {@link EconomyConfig#payMaxMissedDays()}).
     *
     * @return le nombre de jours arrêtés
     */
    public static int resume(MinecraftServer server, Payroll payroll, TreasuryAccount treasury, long now) {
        payroll.unpause();
        payroll.markChecked(now);
        int closed = closeDueDays(server, payroll, treasury, now);
        LOGGER.info("Paie reprise : {} jour(s) clos", closed);
        return closed;
    }

    /**
     * {@code /eco salary resetday} : l'heure était fausse. Le dernier jour réglé revient à la veille selon l'horloge
     * actuelle ({@link Payroll#rewindTo}), le temps compté sur des jours futurs passe sur aujourd'hui, la pause est
     * levée ; puis les jours éventuellement dus se closent. Rien n'est payé deux fois : un jour clos n'a plus de
     * compteur, et ses dettes restent les mêmes.
     *
     * @return le nombre de jours arrêtés
     */
    public static int resetDay(MinecraftServer server, Payroll payroll, TreasuryAccount treasury, long now) {
        return resetDay(server, payroll, treasury, null, now);
    }

    /**
     * Comme {@link #resetDay(MinecraftServer, Payroll, TreasuryAccount, long)}, et le prélèvement automatique de
     * {@code invoices} (s'il est donné) revient au même jour ({@code DailyDebit.rewindTo} : jour présenté seulement,
     * jamais le jour émis). Une seule horloge : la commande passe le registre du serveur.
     */
    public static int resetDay(MinecraftServer server, Payroll payroll, TreasuryAccount treasury,
            @javax.annotation.Nullable dev.oneforallsurvival.economy.invoice.InvoiceRegistry invoices, long now) {
        LocalDate today = EconomyCalendar.payDay(now, EconomyCalendar.configuredZone(), EconomyConfig.payHour());
        if (invoices != null) {
            dev.oneforallsurvival.economy.debit.DailyDebit.rewindTo(invoices, today);
        }
        LocalDate before = payroll.lastClosed();
        payroll.rewindTo(today);
        payroll.markChecked(now);
        warnedBehind = null;
        LOGGER.warn("Paie remise a l'heure : dernier jour regle {} (avant : {}), temps des jours a venir reporte au {}",
                payroll.lastClosed(), before, today);
        return closeDueDays(server, payroll, treasury, now);
    }

    /** L'avis de pause, à un opérateur (niveau 2) : jour vu, jour attendu, et les deux commandes. */
    public static void warnPaused(ServerPlayer player, Payroll payroll) {
        if (payroll.paused() && player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "paused", String.valueOf(payroll.pausedSeen()),
                    String.valueOf(payroll.pausedExpected())));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Paiement de la file
    // ---------------------------------------------------------------------------------------------

    /** Comme {@link #payDebts(MinecraftServer, Payroll, TreasuryAccount, long)}, à {@link EconomyClock#now()}. */
    public static int payDebts(MinecraftServer server, Payroll payroll, TreasuryAccount treasury) {
        return payDebts(server, payroll, treasury, EconomyClock.now());
    }

    /**
     * Paie la file par ancienneté : chaque salaire entier ou rien ; la première dette que le trésor ne peut pas payer
     * arrête la file (une plus jeune ne passe jamais devant). Un salaire payé : retiré du trésor, livré, dette
     * retirée, ligne {@link Kind#SALARY_PAID}, transfert {@link MoneyTransfer#SALARY}, avis au joueur.
     *
     * @return le nombre de salaires payés
     */
    public static int payDebts(MinecraftServer server, Payroll payroll, TreasuryAccount treasury, long now) {
        int paid = 0;
        for (Payroll.Debt debt : payroll.debts()) {
            if (!treasury.payValue(debt.amount())) {
                break;
            }
            payroll.removeDebt(debt.id());
            Destination to = CommerceDelivery.payToSafeFirst(server, debt.player(), debt.amount());
            treasury.note(new TreasuryLedger.Line(now, debt.player(), debt.playerName(), Kind.SALARY_PAID,
                    debt.amount(), reason(debt)));
            EconomyLedger.get(server).transferred(MoneyTransfer.SALARY, debt.amount(), null,
                    reason(debt) + " -> " + debt.playerName());
            tellPaid(server, payroll, debt, to);
            paid++;
        }
        return paid;
    }

    private static String reason(Payroll.Debt debt) {
        return "salaire " + debt.key() + " " + debt.day();
    }

    // ---------------------------------------------------------------------------------------------
    // Avis
    // ---------------------------------------------------------------------------------------------

    /** Connecté : un message ; absent : noté pour sa connexion. */
    private static void tellPaid(MinecraftServer server, Payroll payroll, Payroll.Debt debt, Destination to) {
        ServerPlayer online = server.getPlayerList().getPlayer(debt.player());
        if (online == null) {
            payroll.addUnseenPaid(debt.player(), debt.amount(), to);
            return;
        }
        online.sendSystemMessage(Component.translatable(MESSAGE + "paid", debt.day().toString(),
                roleTime(server, debt), CoinMath.format(debt.amount()),
                Component.translatable(MESSAGE + "to." + to.name().toLowerCase(Locale.ROOT))));
    }

    private static void tellOwed(MinecraftServer server, Payroll.Debt debt) {
        ServerPlayer online = server.getPlayerList().getPlayer(debt.player());
        if (online != null) {
            online.sendSystemMessage(Component.translatable(MESSAGE + "owed", debt.day().toString(),
                    CoinMath.format(debt.amount())));
        }
    }

    /** « Policier, 1 h 5 min » : le libellé du salaire et le temps compté. */
    private static Component roleTime(MinecraftServer server, Payroll.Debt debt) {
        RoleBook book = RoleSavedData.get(server).book();
        return Component.translatable(MESSAGE + "role_time", SalaryCommands.label(book, debt.key()),
                debt.seconds() / 3_600L, debt.seconds() % 3_600L / 60L);
    }

    /**
     * L'avis de connexion : ce que la ville doit au joueur, et les salaires versés pendant son absence (lus une seule
     * fois).
     */
    public static void announce(ServerPlayer player, Payroll payroll) {
        List<Payroll.Debt> owed = payroll.owedTo(player.getUUID());
        if (!owed.isEmpty()) {
            long total = 0L;
            for (Payroll.Debt debt : owed) {
                total = saturatedAdd(total, debt.amount());
            }
            player.sendSystemMessage(Component.translatable(MESSAGE + "login_owed", CoinMath.format(total),
                    owed.size()));
        }
        EnumMap<Destination, Long> paid = payroll.takeUnseenPaid(player.getUUID());
        for (Map.Entry<Destination, Long> part : paid.entrySet()) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "absent_paid", CoinMath.format(part.getValue()),
                    Component.translatable(MESSAGE + "where." + part.getKey().name().toLowerCase(Locale.ROOT))));
        }
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }

    // ---------------------------------------------------------------------------------------------
    // Événements du serveur
    // ---------------------------------------------------------------------------------------------

    /** Rattrapage des jours manqués serveur éteint, puis paiement de la file ({@link #startUp}). */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        startUp(server, Payroll.get(server), TreasuryAccount.get(server), EconomyClock.now());
    }

    /**
     * En fin de tick : toutes les {@link RoleTimeTracker#PERIOD_TICKS}, l'échantillon du temps de rôle puis la
     * vérification de l'horloge et la clôture des jours finis ({@link #onClock}) ; à chaque tick, la file est payée si de l'argent est arrivé au trésor depuis le tick
     * précédent.
     */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        Payroll payroll = Payroll.get(server);
        TreasuryAccount treasury = TreasuryAccount.get(server);
        if (server.getTickCount() % RoleTimeTracker.PERIOD_TICKS == 0) {
            long now = EconomyClock.now();
            RoleTimeTracker.server().sample(server, payroll, now);
            onClock(server, payroll, treasury, now);
        }
        if (treasury.takeFundsArrived()) {
            payDebts(server, payroll, treasury);
        }
    }

    /** Branché après la livraison des attentes ({@link CommerceDelivery#onLoggedIn}). */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Payroll payroll = Payroll.get(player.server);
            announce(player, payroll);
            warnPaused(player, payroll);
        }
    }
}
