package dev.oneforallsurvival.economy;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Les commandes de l'équipe pour l'économie, toutes au niveau de permission 2 :
 * {@code /eco give|take <joueur> <montant> <motif>}, {@code /eco stats}, {@code /eco ledger [page]}.
 * Les montants sont des cuivres ; l'affichage passe par {@link CoinMath#format}.
 */
public final class EconomyCommands {

    /** Montant maximal d'un {@code give} ou d'un {@code take}, en cuivres. */
    public static final long MAX_AMOUNT = 1_000_000_000L;

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(eco());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> eco() {
        return Commands.literal("eco").requires(s -> s.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1L, MAX_AMOUNT))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(EconomyCommands::give)))))
                .then(Commands.literal("take")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1L, MAX_AMOUNT))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(EconomyCommands::take)))))
                .then(Commands.literal("stats").executes(EconomyCommands::stats))
                .then(Commands.literal("ledger")
                        .executes(ctx -> ledger(ctx, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> ledger(ctx, IntegerArgumentType.getInteger(ctx, "page")))));
    }

    @Nullable
    private static UUID authorOf(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
    }

    /** Crée la somme chez le joueur, comptée à la source {@link MoneySource#STAFF}. */
    private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        String reason = StringArgumentType.getString(ctx, "reason");
        PlayerCoins.giveOrDrop(target, CoinMath.decompose(amount));
        EconomyLedger.get(source.getServer()).created(MoneySource.STAFF, amount, authorOf(source),
                target.getGameProfile().getName() + " : " + reason);
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.eco.give.success",
                CoinMath.format(amount), target.getDisplayName(), reason), true);
        return 1;
    }

    /**
     * Retire la somme de l'inventaire du joueur, monnaie rendue, comptée à la cause {@link MoneySink#STAFF}. Jamais
     * en partie : si le joueur ne porte pas assez, rien ne bouge.
     */
    private static int take(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        String reason = StringArgumentType.getString(ctx, "reason");
        long held = CoinMath.valueOf(PlayerCoins.held(target));
        if (!PlayerCoins.take(target, amount)) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.eco.take.not_enough",
                    target.getDisplayName(), CoinMath.format(amount), CoinMath.format(held)));
            return 0;
        }
        EconomyLedger.get(source.getServer()).destroyed(MoneySink.STAFF, amount, authorOf(source),
                target.getGameProfile().getName() + " : " + reason);
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.eco.take.success",
                CoinMath.format(amount), target.getDisplayName(), reason), true);
        return 1;
    }

    /** Le tableau de bord. Les lignes des lots suivants sont annoncées « pas encore en service ». */
    private static int stats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EconomyLedger ledger = EconomyLedger.get(source.getServer());
        MutableComponent out = Component.translatable("command.oneforallsurvival.eco.stats.title");
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.created"));
        for (MoneySource kind : MoneySource.values()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.line",
                    Component.translatable(kind.langKey()), CoinMath.format(ledger.totalCreated(kind))));
        }
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.destroyed"));
        for (MoneySink kind : MoneySink.values()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.line",
                    Component.translatable(kind.langKey()), CoinMath.format(ledger.totalDestroyed(kind))));
        }
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.vaults",
                CoinMath.format(SafeAccounts.get(source.getServer()).totalMass())));
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.later",
                Component.translatable("command.oneforallsurvival.eco.stats.not_yet")));
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.circulation",
                signed(ledger.inCirculation())));
        // Les transferts ne créent ni ne détruisent : ils suivent la ligne « en circulation », qui reste créé moins détruit.
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.transferred"));
        for (MoneyTransfer kind : MoneyTransfer.values()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.line",
                    Component.translatable(kind.langKey()), CoinMath.format(ledger.totalTransferred(kind))));
        }
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.treasury",
                CoinMath.format(TreasuryAccount.get(source.getServer()).total())));
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.pending",
                CoinMath.format(HeldGoods.get(source.getServer()).pendingMass())));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    /** Une page du registre, de la ligne la plus récente à la plus ancienne. */
    private static int ledger(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        EconomyLedger ledger = EconomyLedger.get(server);
        int pages = ledger.pageCount();
        if (page > pages) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.eco.ledger.no_page", page, pages));
            return 0;
        }
        List<EconomyLedger.Entry> entries = ledger.entries(page - 1);
        MutableComponent out = Component.translatable("command.oneforallsurvival.eco.ledger.title", page, pages,
                ledger.entryCount());
        if (entries.isEmpty()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.ledger.empty"));
        }
        for (EconomyLedger.Entry entry : entries) {
            // Un transfert n'a pas de signe : l'argent change de mains, il ne naît ni ne disparaît.
            String sign = entry.kind().startsWith("created.") ? "+" : entry.kind().startsWith("destroyed.") ? "-" : "";
            MutableComponent amount = Component.literal(sign).append(CoinMath.format(entry.amount()));
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.ledger.line",
                    TIME.format(Instant.ofEpochMilli(entry.epochMillis())), amount,
                    Component.translatable("economy.oneforallsurvival.kind." + entry.kind()),
                    authorName(server, entry.author()), entry.reason()));
        }
        source.sendSuccess(() -> out, false);
        return 1;
    }

    private static Component authorName(MinecraftServer server, @Nullable UUID author) {
        if (author == null) {
            return Component.translatable("command.oneforallsurvival.eco.ledger.system");
        }
        ServerPlayer online = server.getPlayerList().getPlayer(author);
        if (online != null) {
            return online.getName();
        }
        return Component.literal(server.getProfileCache().get(author).map(GameProfile::getName)
                .orElse(author.toString().substring(0, 8)));
    }

    /** Un montant qui peut être négatif (créé moins détruit si des pièces non vues naître ont brûlé). */
    private static Component signed(long amount) {
        return amount < 0L ? Component.literal("-").append(CoinMath.format(-amount)) : CoinMath.format(amount);
    }
}
