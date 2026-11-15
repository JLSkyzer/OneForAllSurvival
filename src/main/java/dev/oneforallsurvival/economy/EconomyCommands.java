package dev.oneforallsurvival.economy;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.payroll.SalaryCommands;
import dev.oneforallsurvival.economy.town.TreasuryCommands;
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
 * {@code /eco give|take <joueur> <montant> <motif>}, {@code /eco stats}, {@code /eco pending}, {@code /eco ledger [page]}, {@code /eco treasury ...},
 * {@code /eco salary list|set <clé> <taux>|owed [page]|resume|resetday}, {@code /eco loot}, {@code /eco quests}, {@code /eco quest template|event|show ...}.
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
                .then(Commands.literal("pending").executes(EconomyCommands::pending))
                .then(Commands.literal("loot").executes(EconomyCommands::loot))
                .then(Commands.literal("quests").executes(EconomyCommands::quests))
                .then(dev.oneforallsurvival.economy.quest.QuestCommands.ecoBranch())
                .then(TreasuryCommands.ecoBranch())
                .then(SalaryCommands.ecoBranch())
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

    /** Le tableau de bord : créé, détruit, coffres-forts, salaires dus, circulation, échanges, trésor, attente. */
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
        dev.oneforallsurvival.economy.payroll.Payroll payroll =
                dev.oneforallsurvival.economy.payroll.Payroll.get(source.getServer());
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.salaries_owed",
                CoinMath.format(payroll.owedTotal()), payroll.debts().size()));
        if (payroll.paused()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.paused",
                    String.valueOf(payroll.pausedSeen()), String.valueOf(payroll.pausedExpected())));
        }
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

    /** Lignes au plus de {@code /eco pending} ; au-delà, le nombre des autres. */
    public static final int PENDING_LINES = 20;

    /**
     * Le détail de l'attente, joueur par joueur, de la plus grosse valeur à la plus petite : la valeur des pièces et des
     * bourses, et le nombre d'objets. Une attente qui grossit ou qui stationne se voit ici.
     */
    private static int pending(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        HeldGoods held = HeldGoods.get(server);
        List<HeldGoods.OwnerPending> lines = held.pendingByOwner();
        MutableComponent out = Component.translatable("command.oneforallsurvival.eco.pending.title",
                CoinMath.format(held.pendingMass()));
        if (lines.isEmpty()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.pending.none"));
        }
        for (int i = 0; i < Math.min(PENDING_LINES, lines.size()); i++) {
            HeldGoods.OwnerPending line = lines.get(i);
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.pending.line",
                    authorName(server, line.owner()), CoinMath.format(line.value()), line.items()));
        }
        if (lines.size() > PENDING_LINES) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.pending.more",
                    lines.size() - PENDING_LINES));
        }
        source.sendSuccess(() -> out, false);
        return 1;
    }

    /** Joueurs au plus dans {@code /eco loot}. */
    public static final int LOOT_TOP = 10;

    /**
     * Le butin des mobs : le total créé, le nombre de morts payantes, le total par palier et les {@value #LOOT_TOP}
     * joueurs qui ont le plus gagné. Une ferme ou un abus se voit ici sans lire le journal.
     */
    private static int loot(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        EconomyLedger ledger = EconomyLedger.get(server);
        MutableComponent out = Component.translatable("command.oneforallsurvival.eco.loot.title",
                CoinMath.format(ledger.totalCreated(MoneySource.MOB_LOOT)), ledger.lootKills());
        java.util.Map<String, Long> tiers = new java.util.TreeMap<>(ledger.lootByTier());
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.loot.tiers"));
        if (tiers.isEmpty()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.loot.none"));
        }
        tiers.forEach((tier, amount) -> out.append("\n").append(Component.translatable(
                "command.oneforallsurvival.eco.stats.line", tier, CoinMath.format(amount))));
        List<java.util.Map.Entry<UUID, Long>> players = ledger.lootByPlayer().entrySet().stream()
                .sorted(java.util.Map.Entry.<UUID, Long>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().toString()))
                .limit(LOOT_TOP).toList();
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.loot.players", LOOT_TOP));
        if (players.isEmpty()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.loot.none"));
        }
        for (java.util.Map.Entry<UUID, Long> line : players) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.line",
                    playerName(server, line.getKey()), CoinMath.format(line.getValue())));
        }
        if (source.getEntity() instanceof ServerPlayer me) {
            long earned = dev.oneforallsurvival.economy.loot.MobLootLimits.get(server)
                    .hourlyEarned(me.getUUID(), EconomyClock.now());
            long cap = EconomyConfig.mobLootHourlyCap();
            out.append("\n").append(cap > 0L
                    ? Component.translatable("command.oneforallsurvival.eco.loot.hour", CoinMath.format(earned),
                            CoinMath.format(cap))
                    : Component.translatable("command.oneforallsurvival.eco.loot.hour.nocap", CoinMath.format(earned)));
        }
        source.sendSuccess(() -> out, false);
        return 1;
    }

    /** Joueurs et modèles au plus dans {@code /eco quests}. */
    public static final int QUESTS_TOP = 10;

    /**
     * Les récompenses de quêtes : le total créé, le nombre de réclamations, les {@value #QUESTS_TOP} joueurs et les
     * {@value #QUESTS_TOP} modèles qui ont le plus rapporté, et le nombre de modèles en service.
     */
    private static int quests(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        EconomyLedger ledger = EconomyLedger.get(server);
        MutableComponent out = Component.translatable("command.oneforallsurvival.eco.quests.title",
                CoinMath.format(ledger.totalCreated(MoneySource.QUEST)), ledger.questClaims());
        List<java.util.Map.Entry<UUID, Long>> players = ledger.questByPlayer().entrySet().stream()
                .sorted(java.util.Map.Entry.<UUID, Long>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().toString()))
                .limit(QUESTS_TOP).toList();
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.quests.players", QUESTS_TOP));
        if (players.isEmpty()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.quests.none"));
        }
        for (java.util.Map.Entry<UUID, Long> line : players) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.line",
                    playerName(server, line.getKey()), CoinMath.format(line.getValue())));
        }
        List<java.util.Map.Entry<String, Long>> templates = ledger.questByTemplate().entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(java.util.Map.Entry::getKey))
                .limit(QUESTS_TOP).toList();
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.quests.templates", QUESTS_TOP));
        if (templates.isEmpty()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.quests.none"));
        }
        for (java.util.Map.Entry<String, Long> line : templates) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.stats.line", line.getKey(),
                    CoinMath.format(line.getValue())));
        }
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.quests.events",
                dev.oneforallsurvival.economy.quest.QuestBoard.get(server).activeEvents(EconomyClock.now()).size()));
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.quests.loaded",
                dev.oneforallsurvival.economy.quest.QuestTemplates.current().size()));
        source.sendSuccess(() -> out, false);
        return 1;
    }

    /** Le nom d'un joueur : en ligne, sinon le cache des profils du serveur, sinon son identifiant entier. */
    private static Component playerName(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        java.util.Optional<GameProfile> profile = server.getProfileCache() == null ? java.util.Optional.empty()
                : server.getProfileCache().get(player);
        return Component.literal(profile.map(GameProfile::getName).orElse(player.toString()));
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
        // Le cache des profils peut manquer (serveur de GameTest) : l'identifiant court suffit alors.
        java.util.Optional<GameProfile> profile = server.getProfileCache() == null ? java.util.Optional.empty()
                : server.getProfileCache().get(author);
        return Component.literal(profile.map(GameProfile::getName).orElse(author.toString().substring(0, 8)));
    }

    /** Un montant qui peut être négatif (créé moins détruit si des pièces non vues naître ont brûlé). */
    private static Component signed(long amount) {
        return amount < 0L ? Component.literal("-").append(CoinMath.format(-amount)) : CoinMath.format(amount);
    }
}
