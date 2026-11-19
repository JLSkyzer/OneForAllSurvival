package dev.oneforallsurvival.economy.debit;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.economy.payroll.RoleTimeTracker;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * La clôture de minuit du prélèvement : chaque jour fini depuis le dernier présenté, chaque genre émet ses factures du
 * jour ({@link DayIssuer}) s'il est postérieur au plus grand jour déjà émis ({@link InvoiceRegistry#lastIssuedDay}),
 * puis toutes les factures publiques dues sont présentées, de la plus ancienne à la plus récente
 * ({@link DirectDebit#present}), puis le jour est noté présenté ({@link InvoiceRegistry#setLastDebitDay}). Jours par
 * {@link EconomyCalendar#payDay} (heure {@link EconomyConfig#payHour()}, minuit par défaut), rattrapage borné par
 * {@link EconomyConfig#payMaxMissedDays()}, comme la paie (E3).
 *
 * <p>Une seule horloge, une seule pause : en pause de la paie ({@link Payroll#paused()}, horloge qui a sauté), rien
 * n'est émis ni prélevé ; {@code /eco salary resume} et {@code resetday} lèvent la pause pour les deux. Premier
 * passage : la veille est notée close, rien d'autre. Horloge qui recule : rien. Un jour clos ne se rouvre pas, donc une
 * seconde clôture du même jour (minuit et démarrage dans le même tic, horloge qui recule puis revient) ne fait rien.
 *
 * <p><b>Point d'extension</b> (le même que {@link DebitPayees}) : un genre qui émet des factures chaque jour s'inscrit
 * par {@link #registerIssuer}. À ce lot, l'électricité ; viendront avec leurs lots les amendes et cautions (lot du
 * policier, créancier le trésor et le casier), l'impôt (lot du maire, le trésor) et l'abonnement du téléphone (lot du
 * téléphone, la caisse de l'antenne) — rien de tout cela n'est codé ici. Fil du serveur.
 */
public final class DailyDebit {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Un genre qui émet ses factures publiques d'un jour clos (dans le registre du serveur). */
    @FunctionalInterface
    public interface DayIssuer {
        /** Émet les factures du jour {@code day}, à l'instant {@code now}. Appelé une fois par jour clos. */
        void issue(MinecraftServer server, LocalDate day, long now);
    }

    private static final Map<String, DayIssuer> ISSUERS = new LinkedHashMap<>();

    private DailyDebit() {}

    /** Inscrit (ou remplace) l'émetteur quotidien d'un genre ; les émetteurs passent dans l'ordre d'inscription. */
    public static synchronized void registerIssuer(String kind, DayIssuer issuer) {
        if (kind == null || kind.isEmpty() || kind.indexOf(':') >= 0 || issuer == null) {
            throw new IllegalArgumentException("emetteur de factures publiques invalide : '" + kind + "'");
        }
        ISSUERS.put(kind, issuer);
    }

    /**
     * {@code /eco salary resetday} (l'heure était fausse) : le dernier jour <b>présenté</b> revient à la veille de
     * {@code today} s'il est plus tard, comme celui de la paie ({@code Payroll.rewindTo}) ; sinon plus aucune facture
     * due ne serait présentée jusqu'à ce jour du futur. Le plus grand jour <b>émis</b> ne bouge jamais : un jour déjà
     * facturé (abonnement compris) ne l'est pas une seconde fois ; la consommation des jours qui suivent s'accumule
     * ({@code feToday}) jusqu'à la prochaine émission. Un jour en retard n'est pas avancé. Rien n'est émis ni prélevé ici.
     */
    public static void rewindTo(InvoiceRegistry registry, LocalDate today) {
        LocalDate last = registry.lastDebitDay();
        LocalDate yesterday = today.minusDays(1);
        if (last != null && last.isAfter(yesterday)) {
            registry.setLastDebitDay(yesterday);
            LOGGER.warn("Prelevement remis a l'heure : dernier jour clos {} (avant : {})", yesterday, last);
        }
        issuingSuspendedUntil(registry, today).ifPresent(issued -> LOGGER.warn("Prelevement : jours deja emis "
                + "jusqu'au {} ; aucune facture publique emise avant la cloture du {} (presentation seule)", issued,
                issued.plusDays(1)));
    }

    /**
     * Le plus grand jour déjà émis, s'il n'est pas avant {@code today} : jusqu'à lui, les clôtures ne font que présenter
     * les factures dues (ni facture, ni résiliation, ni tarif du lendemain) ; l'émission reprend avec le jour suivant.
     * Vide si l'émission suit son cours.
     */
    public static Optional<LocalDate> issuingSuspendedUntil(InvoiceRegistry registry, LocalDate today) {
        LocalDate issued = registry.lastIssuedDay();
        return issued != null && !issued.isBefore(today) ? Optional.of(issued) : Optional.empty();
    }

    private static synchronized List<DayIssuer> issuers() {
        return new ArrayList<>(ISSUERS.values());
    }

    /** Comme {@link #closeDueDays(MinecraftServer, InvoiceRegistry, Payroll, long)}, avec la paie du serveur. */
    public static int closeDueDays(MinecraftServer server, InvoiceRegistry registry, long now) {
        return closeDueDays(server, registry, Payroll.get(server), now);
    }

    /**
     * Clôt chaque jour fini depuis {@link InvoiceRegistry#lastDebitDay()} (au plus
     * {@link EconomyConfig#payMaxMissedDays()}, les plus récents) : émission, présentation, jour noté clos.
     *
     * @param payroll la paie dont la pause arrête aussi le prélèvement
     * @return le nombre de jours clos
     */
    public static int closeDueDays(MinecraftServer server, InvoiceRegistry registry, Payroll payroll, long now) {
        if (payroll.paused()) {
            return 0;
        }
        LocalDate today = EconomyCalendar.payDay(now, EconomyCalendar.configuredZone(), EconomyConfig.payHour());
        LocalDate last = registry.lastDebitDay();
        if (last == null) {
            registry.setLastDebitDay(today.minusDays(1));
            return 0;
        }
        List<LocalDate> due = EconomyCalendar.daysToClose(last, today, EconomyConfig.payMaxMissedDays());
        if (due.isEmpty()) {
            return 0;
        }
        long skipped = due.get(0).toEpochDay() - last.toEpochDay() - 1L;
        if (skipped > 0L) {
            LOGGER.warn("Prelevement : {} jours manques au-dela du plafond de {} sont sautes (du {} au {})", skipped,
                    EconomyConfig.payMaxMissedDays(), last.plusDays(1), due.get(0).minusDays(1));
        }
        for (LocalDate day : due) {
            LocalDate issued = registry.lastIssuedDay();
            if (issued == null || day.isAfter(issued)) {
                for (DayIssuer issuer : issuers()) {
                    try {
                        issuer.issue(server, day, now);
                    } catch (RuntimeException e) {
                        // Un émetteur en faute ne bloque ni les autres ni le prélèvement des factures déjà dues.
                        LOGGER.error("Prelevement : emetteur en echec pour le {}", day, e);
                    }
                }
                registry.noteIssuedDay(day);
            } else {
                // Jour déjà émis (horloge en avance puis /eco salary resetday) : on présente seulement les factures dues.
                LOGGER.info("Prelevement du {} : deja emis (jusqu'au {}), presentation seule", day, issued);
            }
            int paid = 0;
            int refused = 0;
            for (Invoice invoice : registry.duePublic()) {
                DirectDebit.Outcome outcome;
                try {
                    outcome = DirectDebit.present(server, registry, invoice, now);
                } catch (RuntimeException e) {
                    // Jamais un plantage du tic (qui recommencerait à chaque démarrage) : la facture suivante passe.
                    LOGGER.error("Prelevement : presentation de la facture {} en echec", invoice.shortId(), e);
                    continue;
                }
                if (outcome == DirectDebit.Outcome.REFUSED) {
                    refused++;
                } else if (outcome.name().startsWith("PAID_")) {
                    paid++;
                }
            }
            registry.setLastDebitDay(day);
            LOGGER.info("Prelevement du {} : {} facture(s) payee(s), {} refusee(s)", day, paid, refused);
        }
        return due.size();
    }

    // ---------------------------------------------------------------------------------------------
    // Événements du serveur (branchés après ceux de la paie)
    // ---------------------------------------------------------------------------------------------

    /** Au démarrage, après le rattrapage de la paie : les jours manqués serveur éteint. */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        closeDueDays(server, InvoiceRegistry.get(server), EconomyClock.now());
    }

    /**
     * En fin de tick, toutes les {@link RoleTimeTracker#PERIOD_TICKS} (après la vérification d'horloge de la paie, qui
     * met en pause sur un saut) : les jours finis.
     */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % RoleTimeTracker.PERIOD_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        closeDueDays(server, InvoiceRegistry.get(server), EconomyClock.now());
    }
}
