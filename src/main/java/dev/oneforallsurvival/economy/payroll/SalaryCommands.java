package dev.oneforallsurvival.economy.payroll;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.debit.DailyDebit;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.role.Role;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/**
 * La branche {@code salary} de {@code /eco} (niveau 2, exigé par {@code /eco}) : {@code list} (chaque clé, le libellé
 * du rôle et son taux par heure), {@code set <clé> <taux>} (clé = identifiant d'un rôle existant ou
 * {@link SalaryRates#MAYOR_KEY} ; taux de 0 à 1 000 000 cuivres par heure, écrit dans la configuration) et
 * {@code owed [page]} (la file des salaires dus, de la plus ancienne dette à la plus récente), et, après un saut
 * d'horloge ({@link PayrollService#onClock}) : {@code resume} (l'heure est juste : les jours dus se closent) ou
 * {@code resetday} (l'heure était fausse : le dernier jour réglé revient à la veille, le temps des jours futurs passe
 * sur aujourd'hui). La pause vaut aussi pour le prélèvement automatique des factures publiques (lot E6,
 * {@link DailyDebit}) : les deux commandes la lèvent pour les deux, et {@code resetday} ramène aussi son jour.
 */
public final class SalaryCommands {

    private SalaryCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> ecoBranch() {
        return Commands.literal("salary")
                .executes(SalaryCommands::list)
                .then(Commands.literal("list").executes(SalaryCommands::list))
                .then(Commands.literal("owed")
                        .executes(ctx -> owed(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> owed(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("resume").executes(SalaryCommands::resume))
                .then(Commands.literal("resetday").executes(SalaryCommands::resetDay))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        keys(RoleSavedData.get(ctx.getSource().getServer()).book()), builder))
                                .then(Commands.argument("rate",
                                                LongArgumentType.longArg(0L, EconomyConfig.MAX_SALARY_PER_HOUR))
                                        .executes(SalaryCommands::set))));
    }

    /** Les clés possibles : chaque rôle, puis le maire. */
    private static Set<String> keys(RoleBook book) {
        Set<String> out = new LinkedHashSet<>();
        for (Role role : book.roles()) {
            out.add(role.id());
        }
        out.add(SalaryRates.MAYOR_KEY);
        return out;
    }

    /** Chaque rôle (payé ou non), le maire, puis les clés réglées d'un rôle qui n'existe plus. */
    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        RoleBook book = RoleSavedData.get(source.getServer()).book();
        Set<String> shown = keys(book);
        for (Map.Entry<String, Long> entry : SalaryRates.all().entrySet()) {
            shown.add(entry.getKey());
        }
        MutableComponent out = Component.translatable("command.oneforallsurvival.eco.salary.list.title",
                EconomyConfig.salaryMaxHoursPerDay());
        for (String key : shown) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.salary.list.line", key,
                    label(book, key), rate(SalaryRates.rateFor(key))));
        }
        source.sendSuccess(() -> out, false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String key = StringArgumentType.getString(ctx, "key");
        long rate = LongArgumentType.getLong(ctx, "rate");
        RoleBook book = RoleSavedData.get(source.getServer()).book();
        if (!SalaryRates.MAYOR_KEY.equals(key) && book.role(key).isEmpty()) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.eco.salary.unknown_key", key));
            return 0;
        }
        SalaryRates.set(key, rate);
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.eco.salary.set.success",
                label(book, key), rate(rate)), true);
        return 1;
    }

    /**
     * {@code /eco salary resume} : la paie en pause reprend, les jours dus se closent ; le prélèvement automatique, qui
     * partage la pause, reprend aussi (au tic suivant).
     */
    private static int resume(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Payroll payroll = Payroll.get(server);
        if (!payroll.paused()) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.eco.salary.resume.not_paused"));
            return 0;
        }
        int closed = PayrollService.resume(server, payroll, TreasuryAccount.get(server), EconomyClock.now());
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.eco.salary.resume.success", closed),
                true);
        return 1;
    }

    /**
     * {@code /eco salary resetday} : l'heure était fausse ; la date de paie, et celle du prélèvement automatique
     * ({@link DailyDebit#rewindTo}), reviennent à l'horloge actuelle.
     */
    private static int resetDay(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        Payroll payroll = Payroll.get(server);
        long now = EconomyClock.now();
        // Une seule horloge : le prélèvement automatique (lot E6) revient au même jour que la paie.
        int closed = PayrollService.resetDay(server, payroll, TreasuryAccount.get(server), InvoiceRegistry.get(server),
                now);
        String today = EconomyCalendar.payDay(now, EconomyCalendar.configuredZone(), EconomyConfig.payHour()).toString();
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.eco.salary.resetday.success",
                String.valueOf(payroll.lastClosed()), today, closed), true);
        return 1;
    }

    /** Lignes par page de {@code /eco salary owed}. */
    public static final int OWED_PAGE_SIZE = 10;

    /** La file des salaires dus, de la plus ancienne dette à la plus récente : jour, joueur, clé, temps, montant. */
    private static int owed(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack source = ctx.getSource();
        Payroll payroll = Payroll.get(source.getServer());
        RoleBook book = RoleSavedData.get(source.getServer()).book();
        List<Payroll.Debt> debts = payroll.debts();
        int pages = Math.max(1, (debts.size() + OWED_PAGE_SIZE - 1) / OWED_PAGE_SIZE);
        if (page > pages) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.eco.salary.owed.no_page", page, pages));
            return 0;
        }
        MutableComponent out = Component.translatable("command.oneforallsurvival.eco.salary.owed.title", page, pages,
                debts.size(), CoinMath.format(payroll.owedTotal()));
        if (debts.isEmpty()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.salary.owed.empty"));
        }
        int from = (page - 1) * OWED_PAGE_SIZE;
        for (Payroll.Debt debt : debts.subList(from, Math.min(debts.size(), from + OWED_PAGE_SIZE))) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.salary.owed.line",
                    debt.day().toString(), debt.playerName(), debt.key(), label(book, debt.key()),
                    duration(debt.seconds()), CoinMath.format(debt.amount())));
        }
        source.sendSuccess(() -> out, false);
        return 1;
    }

    /** Une durée en heures et minutes (« 1 h 5 min »). */
    public static Component duration(long seconds) {
        return Component.translatable("command.oneforallsurvival.eco.salary.duration", seconds / 3_600L,
                seconds % 3_600L / 60L);
    }

    /** Le libellé d'une clé : le rôle, « Maire » pour le statut, l'identifiant d'un rôle supprimé. */
    public static Component label(RoleBook book, String key) {
        if (SalaryRates.MAYOR_KEY.equals(key)) {
            return Component.translatable("command.oneforallsurvival.eco.salary.mayor");
        }
        Optional<Role> role = book.role(key);
        return Component.literal(role.map(Role::label).orElse(key));
    }

    /** Un taux par heure ; « aucun » pour 0. */
    private static Component rate(long perHour) {
        return perHour <= 0L ? Component.translatable("command.oneforallsurvival.eco.salary.none")
                : Component.translatable("command.oneforallsurvival.eco.salary.rate", CoinMath.format(perHour));
    }
}
