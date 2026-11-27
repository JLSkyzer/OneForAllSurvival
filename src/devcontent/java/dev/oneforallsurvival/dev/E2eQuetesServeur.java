package dev.oneforallsurvival.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyCommands;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.quest.PlacedBlocks;
import dev.oneforallsurvival.economy.quest.Quest;
import dev.oneforallsurvival.economy.quest.QuestBook;
import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import dev.oneforallsurvival.economy.quest.QuestPeriods;
import dev.oneforallsurvival.economy.quest.QuestService;
import dev.oneforallsurvival.economy.quest.QuestTemplate;
import dev.oneforallsurvival.economy.quest.QuestTemplates;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le côté serveur de l'essai de bout en bout des quêtes (développement seulement, jamais empaqueté). Des commandes
 * réservées à la console (niveau 4, donc au RCON : le joueur de l'essai n'est pas opérateur pendant ses gestes), sur le
 * modèle d'{@link E2eButinServeur} :
 *
 * <ul>
 *   <li>{@code e2equetes imposer <joueur> daily|weekly <index> <modèle> <quantité>} : remplace une quête du carnet par une
 *       quête neuve de ce modèle (récompense calculée comme au tirage : {@link QuestTemplate#rewardFor}, multipliée par le
 *       facteur hebdomadaire), sans toucher à la clé de la série ; le joueur reçoit son état au prochain envoi groupé ;
 *   <li>{@code e2equetes carnet <joueur>} : le carnet tel qu'il est écrit dans les données du joueur (rien n'est
 *       renouvelé) ;
 *   <li>{@code e2equetes horloge lundi | rendre} : l'horloge de l'économie au lundi suivant, une minute après le
 *       renouvellement des hebdomadaires, par {@link HorlogeDEssai} ; {@code rendre} remet le décalage d'avant ;
 *   <li>{@code e2equetes marque <pos>} : la marque « posé par un joueur » de cette case ({@link PlacedBlocks}) ;
 *   <li>{@code e2equetes registre} : les quêtes du registre et la ligne « Quêtes » telle que {@code /eco stats}
 *       l'écrit (même composant, même langue que la console).
 * </ul>
 *
 * <p>Chaque réponse est une suite {@code cle=valeur} (sans espace, ou entre crochets) terminée par {@code FIN_E2E}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class E2eQuetesServeur {

    private static final String FIN = " FIN_E2E";
    /** Le décalage de l'horloge avant {@code horloge lundi}, rendu par {@code horloge rendre}. */
    private static long decalageAvantLundi;

    private E2eQuetesServeur() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("e2equetes").requires(s -> s.hasPermission(4))
                .then(Commands.literal("imposer").then(Commands.argument("joueur", StringArgumentType.word())
                        .then(Commands.argument("serie", StringArgumentType.word())
                                .then(Commands.argument("index", IntegerArgumentType.integer(0, 31))
                                        .then(Commands.argument("modele", StringArgumentType.word())
                                                .then(Commands.argument("quantite", IntegerArgumentType.integer(1, 100_000))
                                                        .executes(E2eQuetesServeur::imposer)))))))
                .then(Commands.literal("carnet").then(Commands.argument("joueur", StringArgumentType.word())
                        .executes(E2eQuetesServeur::carnet)))
                .then(Commands.literal("horloge")
                        .then(Commands.literal("lundi").executes(E2eQuetesServeur::lundi))
                        .then(Commands.literal("rendre").executes(E2eQuetesServeur::rendre)))
                .then(Commands.literal("marque").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(E2eQuetesServeur::marque)))
                .then(Commands.literal("registre").executes(E2eQuetesServeur::registre)));
    }

    private static int dire(CommandContext<CommandSourceStack> ctx, String texte) {
        ctx.getSource().sendSuccess(() -> Component.literal(texte + FIN), false);
        return 1;
    }

    private static String nettoyer(String texte) {
        return texte.replace(';', ',').replace('[', '(').replace(']', ')').replace('\n', '|');
    }

    private static ServerPlayer joueur(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur"));
    }

    private static String quete(Quest q) {
        return q.templateId() + ":" + q.progress() + "/" + q.goal() + ":" + q.reward() + ":" + q.claimed();
    }

    private static String liste(List<Quest> quetes) {
        StringBuilder sb = new StringBuilder();
        for (Quest q : quetes) {
            sb.append(quete(q)).append(';');
        }
        return "[" + sb + "]";
    }

    // --------------------------------------------------------------------------------------------- carnet

    private static int imposer(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = joueur(ctx);
        if (p == null) {
            return dire(ctx, "REFUS joueur absent");
        }
        String serie = StringArgumentType.getString(ctx, "serie");
        if (!"daily".equals(serie) && !"weekly".equals(serie)) {
            return dire(ctx, "REFUS serie daily ou weekly");
        }
        Series series = "daily".equals(serie) ? Series.DAILY : Series.WEEKLY;
        int index = IntegerArgumentType.getInteger(ctx, "index");
        int quantite = IntegerArgumentType.getInteger(ctx, "quantite");
        String id = StringArgumentType.getString(ctx, "modele");
        QuestTemplate t = QuestTemplates.current().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
        if (t == null) {
            return dire(ctx, "REFUS modele inconnu ou hors service");
        }
        // Le carnet d'abord à jour (comme au prochain tic de contrôle), puis la quête remplacée dans la série en place.
        QuestService.refresh(p, EconomyClock.now());
        QuestBook book = QuestBook.of(p);
        List<Quest> liste = book.series(series);
        if (index >= liste.size()) {
            return dire(ctx, "REFUS index hors de la serie (" + liste.size() + ")");
        }
        long recompense = t.rewardFor(quantite);
        if (series == Series.WEEKLY) {
            recompense *= t.weeklyFactor().orElse(EconomyConfig.questWeeklyFactor());
        }
        Quest q = new Quest(t.id(), t.category(), t.target(), quantite, recompense, 0, false, t.titleKey());
        book.set(series, index, q);
        book.saveTo(p);
        QuestService.markDirty(p);
        return dire(ctx, "IMPOSE serie=" + serie + " index=" + index + " q=" + quete(q) + " cle=" + book.seriesKey(series));
    }

    private static int carnet(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = joueur(ctx);
        if (p == null) {
            return dire(ctx, "REFUS joueur absent");
        }
        QuestBook b = QuestBook.of(p);
        StringBuilder attente = new StringBuilder();
        for (QuestBook.Pending a : b.pending()) {
            attente.append(quete(a.quest())).append(':').append(a.origin()).append(':').append(a.seriesKey()).append(';');
        }
        long now = EconomyClock.now();
        return dire(ctx, "CARNET jour=" + b.dailyKey() + " semaine=" + b.weeklyKey() + " aujourdhui=" + QuestService.today(now)
                + " cettesemaine=" + QuestService.week(now) + " relances=" + b.rerollsUsedOn(QuestService.today(now))
                + " plafond=" + b.capRemaining(QuestService.week(now), EconomyConfig.questWeeklyCap())
                + " paye=" + b.capPaid() + "@" + b.capWeek() + " journalieres=" + liste(b.daily()) + " hebdo=" + liste(b.weekly())
                + " attente=[" + attente + "] oublies=" + b.forgotten());
    }

    // --------------------------------------------------------------------------------------------- horloge

    private static String etat() {
        long now = EconomyClock.now();
        return "maintenant=" + java.time.Instant.ofEpochMilli(now) + " jour=" + QuestService.today(now) + " semaine="
                + QuestService.week(now) + " decalage=" + HorlogeDEssai.decalage();
    }

    /** Le lundi suivant, une minute après le renouvellement des hebdomadaires (heure {@code questResetHour}). */
    private static int lundi(CommandContext<CommandSourceStack> ctx) {
        long now = EconomyClock.now();
        long cible = QuestPeriods.nextWeeklyReset(now, EconomyCalendar.configuredZone(), EconomyConfig.questResetHour())
                + 60_000L;
        decalageAvantLundi = HorlogeDEssai.decalage();
        HorlogeDEssai.regler(cible - System.currentTimeMillis());
        return dire(ctx, "HORLOGE avant=" + java.time.Instant.ofEpochMilli(now) + " " + etat());
    }

    private static int rendre(CommandContext<CommandSourceStack> ctx) {
        HorlogeDEssai.regler(decalageAvantLundi);
        return dire(ctx, "HORLOGE " + etat());
    }

    // --------------------------------------------------------------------------------------------- lectures

    private static int marque(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        var level = ctx.getSource().getLevel();
        return dire(ctx, "MARQUE pose=" + PlacedBlocks.isPlaced(level, pos) + " bloc="
                + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath());
    }

    /**
     * Les quêtes du registre : total créé, réclamations, part de Dev, et la ligne « Quêtes » telle que {@code /eco stats}
     * l'écrit.
     */
    private static int registre(CommandContext<CommandSourceStack> ctx) {
        EconomyLedger ledger = EconomyLedger.get(ctx.getSource().getServer());
        long total = ledger.totalCreated(MoneySource.QUEST);
        long dev = ledger.questByPlayer().getOrDefault(E2eTresorServeur.uuidDe("Dev"), 0L);
        String ligne = Component.translatable("command.oneforallsurvival.eco.stats.line",
                Component.translatable(MoneySource.QUEST.langKey()), CoinMath.format(total)).getString();
        String ligneDev = Component.translatable("command.oneforallsurvival.eco.stats.line", "Dev", CoinMath.format(dev)).getString();
        StringBuilder modeles = new StringBuilder();
        ledger.questByTemplate().forEach((id, somme) -> modeles.append(" m_").append(id).append("=[").append(nettoyer(Component.translatable(
                "command.oneforallsurvival.eco.stats.line", EconomyCommands.templateLabel(id),
                CoinMath.format(somme)).getString())).append(']'));
        return dire(ctx, "REGISTRE total=" + total + " reclamations=" + ledger.questClaims() + " dev=" + dev + " ligne=["
                + nettoyer(ligne) + "] ligneDev=[" + nettoyer(ligneDev) + "]" + modeles);
    }
}
