package dev.oneforallsurvival.economy.town;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCommands;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.MoneySink;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.town.TreasuryLedger.Kind;
import dev.oneforallsurvival.role.RoleGate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Les commandes du trésor. {@code /treasury} (tout le monde : le solde public) et {@code /treasury log [page]} (le
 * maire et l'équipe) ; la branche {@code /eco treasury [give|take <montant> <motif> | log [page]]} de l'équipe
 * (niveau 2), branchée dans {@code /eco} par {@link #ecoBranch()}. Les montants sont des cuivres, affichés par
 * {@link CoinMath#format}.
 */
public final class TreasuryCommands {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Début du motif noté au registre global pour une opération de l'équipe sur le trésor. */
    private static final String GLOBAL_REASON = "tresor : ";

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** La racine {@code /treasury}, niveau 0 : le solde est public, le registre est réservé. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("treasury")
                .executes(TreasuryCommands::balance)
                .then(Commands.literal("log")
                        .executes(ctx -> mayorLog(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> mayorLog(ctx, IntegerArgumentType.getInteger(ctx, "page"))))));
    }

    /** La branche {@code treasury} de {@code /eco} ; {@code /eco} exige déjà le niveau 2. */
    public static LiteralArgumentBuilder<CommandSourceStack> ecoBranch() {
        return Commands.literal("treasury")
                .executes(TreasuryCommands::ecoStatus)
                .then(Commands.literal("give")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1L, EconomyCommands.MAX_AMOUNT))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(TreasuryCommands::give))))
                .then(Commands.literal("take")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1L, EconomyCommands.MAX_AMOUNT))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(TreasuryCommands::take))))
                .then(Commands.literal("log")
                        .executes(ctx -> log(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> log(ctx, IntegerArgumentType.getInteger(ctx, "page")))));
    }

    // ---------------------------------------------------------------------------------------------
    // /treasury
    // ---------------------------------------------------------------------------------------------

    private static int balance(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long total = TreasuryAccount.get(source.getServer()).total();
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.treasury.balance",
                CoinMath.format(total)), false);
        return 1;
    }

    private static int mayorLog(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack source = ctx.getSource();
        boolean allowed = source.getEntity() instanceof ServerPlayer player
                ? RoleGate.canDoMayorWork(player) : source.hasPermission(2);
        if (!allowed) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.treasury.mayor_only"));
            return 0;
        }
        return log(ctx, page);
    }

    // ---------------------------------------------------------------------------------------------
    // /eco treasury
    // ---------------------------------------------------------------------------------------------

    /** Le solde, l'emplacement du bloc et les dettes (les salaires dus : aucun à ce lot). */
    private static int ecoStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        TreasuryAccount account = TreasuryAccount.get(source.getServer());
        MutableComponent out = Component.translatable("command.oneforallsurvival.eco.treasury.balance",
                CoinMath.format(account.total()));
        out.append("\n").append(account.position()
                .map(pos -> Component.translatable("command.oneforallsurvival.eco.treasury.position", place(pos)))
                .orElseGet(() -> Component.translatable("command.oneforallsurvival.eco.treasury.no_position")));
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.treasury.debts", 0,
                CoinMath.format(0L)));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    private static String place(GlobalPos pos) {
        return pos.dimension().location() + " " + pos.pos().toShortString();
    }

    /** Crée la somme au trésor, comptée à la source {@link MoneySource#STAFF}. */
    private static int give(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long amount = LongArgumentType.getLong(ctx, "amount");
        String reason = reasonOf(ctx);
        if (reason == null) {
            return 0;
        }
        TreasuryAccount account = TreasuryAccount.get(source.getServer());
        EnumMap<Coin, Long> coins = CoinMath.decompose(amount);
        for (java.util.Map.Entry<Coin, Long> entry : coins.entrySet()) {
            if (entry.getValue() > account.room(entry.getKey())) {
                source.sendFailure(Component.translatable("command.oneforallsurvival.eco.treasury.full"));
                return 0;
            }
        }
        UUID author = authorOf(source);
        account.credit(coins, Kind.INJECTION, author, source.getTextName(), reason);
        EconomyLedger.get(source.getServer()).created(MoneySource.STAFF, amount, author, GLOBAL_REASON + reason);
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.eco.treasury.give.success",
                CoinMath.format(amount), reason), true);
        return 1;
    }

    /** Retire la somme du trésor, comptée à la cause {@link MoneySink#STAFF} ; jamais en partie. */
    private static int take(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long amount = LongArgumentType.getLong(ctx, "amount");
        String reason = reasonOf(ctx);
        if (reason == null) {
            return 0;
        }
        TreasuryAccount account = TreasuryAccount.get(source.getServer());
        long held = account.total();
        if (!account.payValue(amount)) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.eco.treasury.take.not_enough",
                    CoinMath.format(amount), CoinMath.format(held)));
            return 0;
        }
        UUID author = authorOf(source);
        account.note(new TreasuryLedger.Line(System.currentTimeMillis(), author, source.getTextName(), Kind.STAFF_TAKE,
                amount, reason));
        EconomyLedger.get(source.getServer()).destroyed(MoneySink.STAFF, amount, author, GLOBAL_REASON + reason);
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.eco.treasury.take.success",
                CoinMath.format(amount), reason), true);
        return 1;
    }

    /** Le motif, ou nul (et l'échec dit) s'il est vide ou plus long que {@link EconomyConfig#treasuryReasonMax()}. */
    @Nullable
    private static String reasonOf(CommandContext<CommandSourceStack> ctx) {
        String reason = StringArgumentType.getString(ctx, "reason").trim();
        int max = EconomyConfig.treasuryReasonMax();
        if (reason.isEmpty() || reason.length() > max) {
            ctx.getSource().sendFailure(Component.translatable("command.oneforallsurvival.eco.treasury.reason", max));
            return null;
        }
        return reason;
    }

    @Nullable
    private static UUID authorOf(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Le registre
    // ---------------------------------------------------------------------------------------------

    /** Une page du registre, de la ligne la plus récente à la plus ancienne. */
    private static int log(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack source = ctx.getSource();
        TreasuryLedger ledger = TreasuryAccount.get(source.getServer()).ledger();
        int pages = ledger.pageCount();
        if (page > pages) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.treasury.log.no_page", page, pages));
            return 0;
        }
        List<TreasuryLedger.Line> lines = ledger.page(page - 1);
        MutableComponent out = Component.translatable("command.oneforallsurvival.treasury.log.title", page, pages,
                ledger.size());
        if (lines.isEmpty()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.treasury.log.empty"));
        }
        for (TreasuryLedger.Line line : lines) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.treasury.log.line",
                    TIME.format(Instant.ofEpochMilli(line.epochMillis())), amountOf(line),
                    Component.translatable(kindKey(line.kind())), actorOf(line), line.reason()));
        }
        source.sendSuccess(() -> out, false);
        return 1;
    }

    /** Le montant signé : plus pour ce qui entre, moins pour ce qui sort, rien pour une ligne sans montant. */
    private static Component amountOf(TreasuryLedger.Line line) {
        if (line.amount() <= 0L) {
            return Component.empty();
        }
        String sign = switch (line.kind()) {
            case TAX, DEPOSIT, INJECTION -> "+";
            case SPEND, STAFF_TAKE, SALARY_PAID -> "-";
            default -> "";
        };
        return Component.literal(sign).append(CoinMath.format(line.amount()));
    }

    private static Component actorOf(TreasuryLedger.Line line) {
        return line.actorName().isEmpty()
                ? Component.translatable("command.oneforallsurvival.eco.ledger.system")
                : Component.literal(line.actorName());
    }

    /** Clé de langue du nom d'un genre de ligne. */
    public static String kindKey(Kind kind) {
        return "economy.oneforallsurvival.treasury.kind." + kind.name().toLowerCase(java.util.Locale.ROOT);
    }
}
