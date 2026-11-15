package dev.oneforallsurvival.economy.quest;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * La branche {@code /eco quest} de l'équipe (niveau de permission 2, celui de {@code /eco}) :
 * <ul>
 *   <li>{@code template add <id> <catégorie> <cible> <min> <max> <récompense> [poids]}, {@code remove <id>},
 *       {@code list} : les modèles de l'équipe, tirables comme ceux du fichier de données ;</li>
 *   <li>{@code event start <id> <catégorie> <cible> <quantité> <récompense> <jours> [titre]}, {@code stop <id>},
 *       {@code list} : les événements, communs à tous, qui finissent à l'heure de renouvellement, {@code jours} jours
 *       après (1 à 30) ;</li>
 *   <li>{@code show <joueur>} : les quêtes d'un joueur.</li>
 * </ul>
 * La cible est un identifiant du registre ({@code "minecraft:stone"}), un tag ({@code "#minecraft:logs"}) ou {@code "*"}
 * (aucune cible) : le guillemet est nécessaire, {@code :}, {@code #} et {@code *} n'étant pas permis hors guillemets
 * par Brigadier ; {@code -} (sans guillemets) vaut aussi « aucune cible ». Chaque geste est noté au journal du serveur
 * avec son auteur.
 */
public final class QuestCommands {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LANG = "command.oneforallsurvival.eco.quest.";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Durée maximale d'un événement, en jours. */
    public static final int MAX_DAYS = 30;

    private QuestCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> ecoBranch() {
        return Commands.literal("quest")
                .then(Commands.literal("template")
                        .then(Commands.literal("add")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests(QuestCommands::suggestCategories)
                                                .then(Commands.argument("target", StringArgumentType.string())
                                                        .then(Commands.argument("min", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                                                        .then(Commands.argument("reward", LongArgumentType.longArg(1L))
                                                                                .executes(ctx -> addTemplate(ctx, 1.0D))
                                                                                .then(Commands.argument("weight", DoubleArgumentType.doubleArg(0.0001D))
                                                                                        .executes(ctx -> addTemplate(ctx,
                                                                                                DoubleArgumentType.getDouble(ctx, "weight")))))))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                QuestBoard.get(ctx.getSource().getServer()).templates().stream()
                                                        .map(QuestTemplate::id), builder))
                                        .executes(QuestCommands::removeTemplate)))
                        .then(Commands.literal("list").executes(QuestCommands::listTemplates)))
                .then(Commands.literal("event")
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests(QuestCommands::suggestCategories)
                                                .then(Commands.argument("target", StringArgumentType.string())
                                                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("reward", LongArgumentType.longArg(1L))
                                                                        .then(Commands.argument("days", IntegerArgumentType.integer(1, MAX_DAYS))
                                                                                .executes(ctx -> startEvent(ctx, ""))
                                                                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                                                                        .executes(ctx -> startEvent(ctx,
                                                                                                StringArgumentType.getString(ctx, "title")))))))))))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                QuestBoard.get(ctx.getSource().getServer())
                                                        .activeEvents(EconomyClock.now()).stream().map(QuestEvent::id),
                                                builder))
                                        .executes(QuestCommands::stopEvent)))
                        .then(Commands.literal("list").executes(QuestCommands::listEvents)))
                .then(Commands.literal("show")
                        .then(Commands.argument("player", EntityArgument.player()).executes(QuestCommands::show)));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestCategories(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(QuestCategory.values()).filter(QuestCategory::counted)
                .map(QuestCategory::key), builder);
    }

    // ---------------------------------------------------------------------------------------------
    // Construction (sans source de commande : les essais l'appellent)
    // ---------------------------------------------------------------------------------------------

    /** Aucune cible : {@code *}, {@code -} ou rien. Illisible : {@code null}. */
    @Nullable
    private static QuestTarget target(@Nullable String text, boolean[] bad) {
        if (text == null || text.isBlank() || text.equals("*") || text.equals("-")) {
            return null;
        }
        QuestTarget parsed = QuestTarget.parse(text).orElse(null);
        bad[0] = parsed == null;
        return parsed;
    }

    /** Ajoute un modèle d'après les arguments de la commande ; les contrôles sont ceux du fichier de données. */
    public static QuestBoard.Result addTemplate(QuestBoard board, String id, String category, @Nullable String target,
            int min, int max, long reward, double weight) {
        QuestCategory kind = QuestCategory.byKey(category).orElse(null);
        if (kind == null) {
            return QuestBoard.Result.refused(LANG + "refused.invalid", "catégorie inconnue « " + category + " »");
        }
        boolean[] bad = new boolean[1];
        QuestTarget parsed = target(target, bad);
        if (bad[0]) {
            return QuestBoard.Result.refused(LANG + "refused.invalid", "cible invalide « " + target + " »");
        }
        return board.addTemplate(new QuestTemplate(id, kind, parsed, min, max, reward, weight, Map.of(),
                OptionalInt.empty(), null));
    }

    /**
     * Lance un événement d'après les arguments de la commande : il finit à l'heure de renouvellement, {@code days}
     * jours après le prochain renouvellement (1 : le prochain).
     */
    public static QuestBoard.Result startEvent(QuestBoard board, String id, String category, @Nullable String target,
            int quantity, long reward, int days, String title, UUID author, long now) {
        QuestCategory kind = QuestCategory.byKey(category).orElse(null);
        if (kind == null) {
            return QuestBoard.Result.refused(LANG + "refused.invalid", "catégorie inconnue « " + category + " »");
        }
        boolean[] bad = new boolean[1];
        QuestTarget parsed = target(target, bad);
        if (bad[0]) {
            return QuestBoard.Result.refused(LANG + "refused.invalid", "cible invalide « " + target + " »");
        }
        return board.startEvent(new QuestEvent(id, kind, parsed, quantity, reward, now, endOf(now, days), title, author));
    }

    /** La fin d'un événement lancé à {@code now} pour {@code days} jours : le renouvellement qui clôt le dernier jour. */
    public static long endOf(long now, int days) {
        ZoneId zone = EconomyCalendar.configuredZone();
        int hour = EconomyConfig.questResetHour();
        long end = QuestPeriods.nextDailyReset(now, zone, hour);
        for (int i = 1; i < days; i++) {
            end = QuestPeriods.nextDailyReset(end, zone, hour);
        }
        return end;
    }

    /** Une ligne de {@code show} : section, modèle, progression, but, récompense, réclamée. */
    public record Row(String section, String templateId, int progress, int goal, long reward, boolean claimed) {}

    /** Les quêtes d'un carnet, section par section : journalières, hebdomadaires, événements, en attente. */
    public static List<Row> rows(QuestBook book) {
        List<Row> out = new ArrayList<>();
        book.daily().forEach(q -> out.add(row("daily", q)));
        book.weekly().forEach(q -> out.add(row("weekly", q)));
        book.events().values().forEach(q -> out.add(row("events", q)));
        book.pending().forEach(p -> out.add(row("pending", p.quest())));
        return out;
    }

    private static Row row(String section, Quest q) {
        return new Row(section, q.templateId(), q.progress(), q.goal(), q.reward(), q.claimed());
    }

    // ---------------------------------------------------------------------------------------------
    // Commandes
    // ---------------------------------------------------------------------------------------------

    private static String author(CommandSourceStack source) {
        return source.getTextName() + (source.getEntity() instanceof ServerPlayer p ? " (" + p.getUUID() + ")" : "");
    }

    private static int refuse(CommandSourceStack source, QuestBoard.Result result) {
        source.sendFailure(Component.translatable(result.key(), result.args().toArray()));
        return 0;
    }

    private static String time(long millis) {
        return TIME.format(Instant.ofEpochMilli(millis).atZone(EconomyCalendar.configuredZone()));
    }

    private static String targetText(@Nullable QuestTarget target) {
        return target == null ? "*" : target.text();
    }

    private static int addTemplate(CommandContext<CommandSourceStack> ctx, double weight) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        QuestBoard board = QuestBoard.get(source.getServer());
        QuestBoard.Result result = addTemplate(board, id, StringArgumentType.getString(ctx, "category"),
                StringArgumentType.getString(ctx, "target"), IntegerArgumentType.getInteger(ctx, "min"),
                IntegerArgumentType.getInteger(ctx, "max"), LongArgumentType.getLong(ctx, "reward"), weight);
        if (!result.ok()) {
            LOGGER.info("Quetes : {} a tente d'ajouter le modele {} : refuse ({})", author(source), id, result.key());
            return refuse(source, result);
        }
        LOGGER.info("Quetes : {} a ajoute le modele {} ({} {} de {} a {}, {} cuivres, poids {})", author(source), id,
                StringArgumentType.getString(ctx, "category"), StringArgumentType.getString(ctx, "target"),
                IntegerArgumentType.getInteger(ctx, "min"), IntegerArgumentType.getInteger(ctx, "max"),
                LongArgumentType.getLong(ctx, "reward"), weight);
        source.sendSuccess(() -> Component.translatable(LANG + "template.added", id, board.templates().size()), true);
        return 1;
    }

    private static int removeTemplate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        if (!QuestBoard.get(source.getServer()).removeTemplate(id)) {
            source.sendFailure(Component.translatable(LANG + "template.unknown", id));
            return 0;
        }
        LOGGER.info("Quetes : {} a retire le modele {}", author(source), id);
        source.sendSuccess(() -> Component.translatable(LANG + "template.removed", id), true);
        return 1;
    }

    private static int listTemplates(CommandContext<CommandSourceStack> ctx) {
        List<QuestTemplate> list = QuestBoard.get(ctx.getSource().getServer()).templates();
        MutableComponent out = Component.translatable(LANG + "template.list.title", list.size());
        if (list.isEmpty()) {
            out.append("\n").append(Component.translatable(LANG + "list.none"));
        }
        for (QuestTemplate t : list) {
            out.append("\n").append(Component.translatable(LANG + "template.list.line", t.id(), t.category().key(),
                    targetText(t.target()), t.min(), t.max(), CoinMath.format(t.reward()), t.weight()));
        }
        ctx.getSource().sendSuccess(() -> out, false);
        return list.size();
    }

    private static int startEvent(CommandContext<CommandSourceStack> ctx, String title) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        int days = IntegerArgumentType.getInteger(ctx, "days");
        long now = EconomyClock.now();
        UUID who = source.getEntity() instanceof ServerPlayer p ? p.getUUID() : new UUID(0L, 0L);
        QuestBoard board = QuestBoard.get(source.getServer());
        QuestBoard.Result result = startEvent(board, id, StringArgumentType.getString(ctx, "category"),
                StringArgumentType.getString(ctx, "target"), IntegerArgumentType.getInteger(ctx, "quantity"),
                LongArgumentType.getLong(ctx, "reward"), days, title, who, now);
        if (!result.ok()) {
            LOGGER.info("Quetes : {} a tente de lancer l'evenement {} : refuse ({})", author(source), id, result.key());
            return refuse(source, result);
        }
        long end = endOf(now, days);
        LOGGER.info("Quetes : {} a lance l'evenement {} ({} {} x{}, {} cuivres, {} jours, fin {}, titre « {} »)",
                author(source), id, StringArgumentType.getString(ctx, "category"),
                StringArgumentType.getString(ctx, "target"), IntegerArgumentType.getInteger(ctx, "quantity"),
                LongArgumentType.getLong(ctx, "reward"), days, time(end), title);
        source.sendSuccess(() -> Component.translatable(LANG + "event.started", id,
                IntegerArgumentType.getInteger(ctx, "quantity"), StringArgumentType.getString(ctx, "category"),
                CoinMath.format(LongArgumentType.getLong(ctx, "reward")), time(end)), true);
        return 1;
    }

    private static int stopEvent(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        if (!QuestBoard.get(source.getServer()).stopEvent(id)) {
            source.sendFailure(Component.translatable(LANG + "event.unknown", id));
            return 0;
        }
        LOGGER.info("Quetes : {} a arrete l'evenement {}", author(source), id);
        source.sendSuccess(() -> Component.translatable(LANG + "event.stopped", id), true);
        return 1;
    }

    private static int listEvents(CommandContext<CommandSourceStack> ctx) {
        List<QuestEvent> list = QuestBoard.get(ctx.getSource().getServer()).activeEvents(EconomyClock.now());
        MutableComponent out = Component.translatable(LANG + "event.list.title", list.size());
        if (list.isEmpty()) {
            out.append("\n").append(Component.translatable(LANG + "list.none"));
        }
        for (QuestEvent e : list) {
            out.append("\n").append(Component.translatable(LANG + "event.list.line", e.id(), e.goal(),
                    e.category().key() + " " + targetText(e.target()), CoinMath.format(e.reward()), time(e.endMillis())));
        }
        ctx.getSource().sendSuccess(() -> out, false);
        return list.size();
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        long now = EconomyClock.now();
        QuestBook book = QuestBook.of(player);
        long cap = EconomyConfig.questWeeklyCap();
        long paid = cap <= 0L ? book.capPaid() : cap - book.capRemaining(QuestService.week(now), cap);
        MutableComponent out = Component.translatable(LANG + "show.title", player.getDisplayName(),
                CoinMath.format(paid), cap <= 0L ? "∞" : CoinMath.format(cap));
        String section = "";
        for (Row row : rows(book)) {
            if (!row.section().equals(section)) {
                section = row.section();
                out.append("\n").append(Component.translatable(LANG + "show." + section));
            }
            out.append("\n").append(Component.translatable(
                    LANG + (row.claimed() ? "show.line.claimed" : "show.line"), row.templateId(), row.progress(),
                    row.goal(), CoinMath.format(row.reward())));
        }
        source.sendSuccess(() -> out, false);
        return 1;
    }
}
