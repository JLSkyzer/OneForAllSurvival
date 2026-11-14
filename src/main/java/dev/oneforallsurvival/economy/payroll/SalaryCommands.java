package dev.oneforallsurvival.economy.payroll;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.role.Role;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * La branche {@code salary} de {@code /eco} (niveau 2, exigé par {@code /eco}) : {@code list} (chaque clé, le libellé
 * du rôle et son taux par heure) et {@code set <clé> <taux>} (clé = identifiant d'un rôle existant ou
 * {@link SalaryRates#MAYOR_KEY} ; taux de 0 à 1 000 000 cuivres par heure, écrit dans la configuration).
 */
public final class SalaryCommands {

    private SalaryCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> ecoBranch() {
        return Commands.literal("salary")
                .executes(SalaryCommands::list)
                .then(Commands.literal("list").executes(SalaryCommands::list))
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
