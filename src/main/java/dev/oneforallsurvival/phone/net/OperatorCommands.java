package dev.oneforallsurvival.phone.net;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.grid.supplier.PowerCommands;
import dev.oneforallsurvival.grid.supplier.PowerPairing;
import dev.oneforallsurvival.phone.PhoneConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * {@code /operator} : les gestes du maire et de l'équipe sur les opérateurs du téléphone (lot T2, tâche 11), calqués sur
 * {@link PowerCommands} ({@code /power}, lot E6).
 *
 * <p>Maire ou équipe seulement ({@link PowerCommands#mayGovern} : {@code RoleGate.canDoMayorWork}, jamais un faux
 * joueur ; la console et RCON au niveau 2) ; citoyen, ingénieur même outillé et fondateur lui-même sont refusés, avec un
 * message clair. Chaque geste accepté laisse une ligne au journal du registre de l'économie, avec son auteur (ou le nom
 * de sa source sans joueur) ; ni jeton de carte ni position de joueur n'y entre, ni dans aucun message.
 * <ul>
 *   <li>{@code /operator list [page]} : chaque opérateur, son fondateur, ses antennes en service et posées, ses cartes
 *       actives, son tarif du jour et du lendemain ;</li>
 *   <li>{@code /operator cap <cuivres>} : plafond des tarifs, de 0 à {@code phoneTariffMax}, <b>au lendemain</b> ;</li>
 *   <li>{@code /operator set <opérateur> tariff <cuivres>} : tarif d'un opérateur, au lendemain, sous le plafond du
 *       lendemain (jamais le jour même) ;</li>
 *   <li>{@code /operator owner <opérateur> <joueur>} : nouveau fondateur, un joueur connu qui n'a pas d'opérateur ;
 *       cartes, caisse et antennes suivent l'opérateur ; les panneaux ouverts de l'ancien fondateur se ferment ;</li>
 *   <li>{@code /operator remove <opérateur> <motif>} puis {@code /operator remove <opérateur> confirm} dans les
 *       {@value #CONFIRM_MILLIS} ms : la suppression complète ({@link #remove(MinecraftServer, PhoneOperators,
 *       PhoneOperator, String, UUID, String, Payer)}).</li>
 * </ul>
 * Un nom d'opérateur se reconnaît sans casse ; s'il contient des espaces, il s'écrit entre guillemets. Renvoyés au lot
 * T6 : la taxe du téléphone, {@code /eco stats} des abonnements et l'annulation des factures dues. Les méthodes publiques
 * prennent leur registre en paramètre (essais) ; les commandes passent celui du serveur. Fil du serveur.
 */
public final class OperatorCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Préfixe des retours d'une commande (à la source). */
    public static final String KEY = "command.oneforallsurvival.operator.";

    /** Préfixe des avis aux joueurs touchés (fondateurs, titulaires, demandeurs). */
    public static final String MESSAGE = "message.oneforallsurvival.operator.";

    /** Délai de confirmation d'une suppression : 60 s. */
    public static final long CONFIRM_MILLIS = 60_000L;

    /** Longueur gardée d'un motif de suppression. */
    public static final int MAX_REASON = 100;

    /** Le signe de mise en forme des textes du jeu (U+00A7), jamais gardé dans un motif. */
    private static final int SECTION_SIGN = 0xA7;

    /** Noms proposés au plus par la complétion. */
    public static final int MAX_SUGGESTIONS = 32;

    /** Issue d'un geste. */
    public enum Result { DONE, REQUESTED, NOT_ALLOWED, UNKNOWN_OPERATOR, OUT_OF_RANGE, ABOVE_CAP, UNKNOWN_PLAYER,
        HAS_OPERATOR, NO_REQUEST, EXPIRED, TILL_REFUSED }

    /** Une suppression demandée, qui attend sa confirmation (non sauvegardée : un redémarrage l'oublie). */
    private record PendingRemoval(String operatorId, String reason, long until) {}

    private static final Map<UUID, PendingRemoval> PENDING = new HashMap<>();

    /**
     * Verse une somme à un joueur ; {@link #SERVER_PAYER} en production. Un essai en passe un autre pour éprouver le
     * refus d'une suppression dont le versement échoue.
     */
    @FunctionalInterface
    public interface Payer {
        CommerceDelivery.Destination pay(MinecraftServer server, UUID payee, long amount);
    }

    /** Le versement de la caisse au fondateur : coffre-fort, inventaire, sinon en attente. */
    public static final Payer SERVER_PAYER = CommerceDelivery::payToSafeFirst;

    /** Une instance s'inscrit au bus de Forge pour {@link #onRegisterCommands}. */
    public OperatorCommands() {}

    /** À l'arrêt du serveur : les demandes en attente ne passent pas d'un monde à l'autre (même JVM). */
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Parties pures (JUnit)
    // ---------------------------------------------------------------------------------------------

    /**
     * Les noms proposés par la complétion : ceux qui commencent par {@code typed} (sans casse, guillemet ouvrant
     * ignoré), mis entre guillemets s'il le faut, dans l'ordre reçu, au plus {@code max}.
     */
    public static List<String> suggestions(Collection<String> names, String typed, int max) {
        String prefix = typed == null ? "" : typed.strip();
        if (prefix.startsWith("\"")) {
            prefix = prefix.substring(1);
        }
        prefix = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (out.size() >= Math.max(0, max)) {
                break;
            }
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(StringArgumentType.escapeIfRequired(name));
            }
        }
        return out;
    }

    /**
     * Le motif tel qu'il est gardé : sans caractère de contrôle ni signe de mise en forme ({@code §}), blancs du bord
     * ôtés, au plus {@value #MAX_REASON} caractères.
     */
    public static String reason(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        raw.codePoints().forEach(c -> {
            if (!Character.isISOControl(c) && c != SECTION_SIGN && Character.getType(c) != Character.FORMAT) {
                out.appendCodePoint(c);
            }
        });
        String stripped = out.toString().strip();
        if (stripped.codePointCount(0, stripped.length()) <= MAX_REASON) {
            return stripped;
        }
        return stripped.substring(0, stripped.offsetByCodePoints(0, MAX_REASON)).strip();
    }

    /** Le plafond qui borne un tarif demandé aujourd'hui : celui de demain s'il change, sinon celui du jour. */
    public static long nextCap(PhoneOperators registry) {
        return Math.min(PhoneConfig.phoneTariffMax(), registry.nextTariffCap().orElse(registry.tariffCap()));
    }

    // ---------------------------------------------------------------------------------------------
    // Enregistrement
    // ---------------------------------------------------------------------------------------------

    private static final SuggestionProvider<CommandSourceStack> OPERATORS = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        for (PhoneOperator op : PhoneOperators.get(ctx.getSource().getServer()).all()) {
            names.add(op.name());
        }
        return SharedSuggestionProvider.suggest(suggestions(names, builder.getRemaining(), MAX_SUGGESTIONS), builder);
    };

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("operator")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), registry(ctx), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> list(ctx.getSource(), registry(ctx),
                                        IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("cap").then(Commands.argument("copper", LongArgumentType.longArg())
                        .executes(ctx -> code(setCap(ctx.getSource(), registry(ctx),
                                LongArgumentType.getLong(ctx, "copper"))))))
                .then(Commands.literal("set").then(Commands.argument("operator", StringArgumentType.string())
                        .suggests(OPERATORS)
                        .then(Commands.literal("tariff").then(Commands.argument("copper", LongArgumentType.longArg())
                                .executes(ctx -> code(setTariff(ctx.getSource(), registry(ctx),
                                        StringArgumentType.getString(ctx, "operator"),
                                        LongArgumentType.getLong(ctx, "copper"))))))))
                .then(Commands.literal("owner").then(Commands.argument("operator", StringArgumentType.string())
                        .suggests(OPERATORS)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> code(setOwner(ctx.getSource(), registry(ctx),
                                        StringArgumentType.getString(ctx, "operator"),
                                        StringArgumentType.getString(ctx, "player")))))))
                .then(Commands.literal("remove").then(Commands.argument("operator", StringArgumentType.string())
                        .suggests(OPERATORS)
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(OperatorCommands::remove)))));
    }

    private static PhoneOperators registry(CommandContext<CommandSourceStack> ctx) {
        return PhoneOperators.get(ctx.getSource().getServer());
    }

    private static int code(Result result) {
        return result == Result.DONE || result == Result.REQUESTED ? 1 : 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Droits, sources, journal
    // ---------------------------------------------------------------------------------------------

    @Nullable
    private static UUID author(CommandSourceStack source) {
        return source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player ? player.getUUID() : null;
    }

    /**
     * La clé de confirmation d'une source : le joueur, sinon le nom de la source (console, RCON, bloc de commande) ; deux
     * sources sans joueur de noms différents ne se confirment pas l'une l'autre.
     */
    private static UUID requester(CommandSourceStack source) {
        UUID author = author(source);
        return author != null ? author : UUID.nameUUIDFromBytes(("source:" + source.getTextName())
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String byWhom(CommandSourceStack source, String reason) {
        return author(source) != null ? reason : "[" + source.getTextName() + "] " + reason;
    }

    private static Result fail(CommandSourceStack source, Result result, Component message) {
        source.sendFailure(message);
        return result;
    }

    private static Result denied(CommandSourceStack source) {
        return fail(source, Result.NOT_ALLOWED, Component.translatable(KEY + "not_mayor"));
    }

    private static Optional<PhoneOperator> find(CommandSourceStack source, PhoneOperators registry, String name) {
        Optional<PhoneOperator> op = registry.byName(name);
        if (op.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown_operator", name));
        }
        return op;
    }

    private static void note(CommandSourceStack source, String kind, long amount, String reason) {
        EconomyLedger.get(source.getServer()).noted(kind, author(source), amount, byWhom(source, reason));
        LOGGER.info("Telephone : {} ({}) : {}", source.getTextName(), kind, reason);
    }

    // ---------------------------------------------------------------------------------------------
    // Liste
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /operator list [page]} : le plafond du jour et de demain, puis chaque opérateur (ordre de fondation),
     * {@value PowerCommands#PAGE_SIZE} par page.
     *
     * @return le nombre de lignes d'opérateur montrées (au moins 1), 0 si refusé
     */
    public static int list(CommandSourceStack source, PhoneOperators registry, int page) {
        if (!PowerCommands.mayGovern(source)) {
            denied(source);
            return 0;
        }
        List<PhoneOperator> all = new ArrayList<>(registry.all());
        int pages = PowerCommands.pages(all.size());
        if (page < 1 || page > pages) {
            source.sendFailure(Component.translatable(KEY + "no_page", page, pages));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "list_title", all.size(), page, pages), false);
        long cap = registry.tariffCap();
        long next = registry.nextTariffCap().orElse(cap);
        source.sendSuccess(() -> Component.translatable(KEY + "list_cap", CoinMath.format(cap), CoinMath.format(next)),
                false);
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "list_none"), false);
        }
        Map<String, PhoneNetwork.RelayState> relays = new HashMap<>();
        for (PhoneNetwork.RelayState state : PhoneNetwork.relayStates(source.getServer())) {
            relays.put(state.operatorId(), state);
        }
        List<PhoneOperator> shown = all.subList((page - 1) * PowerCommands.PAGE_SIZE,
                Math.min(all.size(), page * PowerCommands.PAGE_SIZE));
        for (PhoneOperator op : shown) {
            // Le registre passé (essais) peut différer de celui du serveur : antennes comptées dans celui-ci.
            PhoneNetwork.RelayState relay = relays.get(op.id());
            int placed = registry.antennasOf(op.id()).size();
            int inService = relay == null ? 0 : Math.min(relay.inService(), placed);
            long tomorrow = op.nextTariff() != null ? op.nextTariff() : op.tariff();
            Component line = Component.translatable(KEY + "list_line", op.name(), op.founderName(), inService, placed,
                    registry.activeCards(op.id()), CoinMath.format(op.tariff()), CoinMath.format(tomorrow));
            source.sendSuccess(() -> line, false);
        }
        return Math.max(1, shown.size());
    }

    // ---------------------------------------------------------------------------------------------
    // Plafond et tarif (au lendemain)
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /operator cap} : le plafond des tarifs, de 0 à {@code phoneTariffMax}, au lendemain (clôture de minuit,
     * {@link OperatorDay}) ; le plafond du jour ne bouge pas. Un fondateur dont le tarif de demain le dépasse est prévenu
     * tout de suite (à sa connexion s'il est absent).
     */
    public static Result setCap(CommandSourceStack source, PhoneOperators registry, long cap) {
        if (!PowerCommands.mayGovern(source)) {
            return denied(source);
        }
        long max = PhoneConfig.phoneTariffMax();
        if (cap < 0L || cap > max) {
            return fail(source, Result.OUT_OF_RANGE, Component.translatable(KEY + "cap_out_of_range",
                    CoinMath.format(max)));
        }
        registry.setNextTariffCap(cap);
        note(source, EconomyLedger.KIND_PHONE_CAP, cap, "plafond des tarifs du telephone : " + cap
                + " cuivres par jour, des demain");
        source.sendSuccess(() -> Component.translatable(KEY + "cap_set", CoinMath.format(cap)), true);
        MinecraftServer server = source.getServer();
        for (PhoneOperator op : registry.all()) {
            long tomorrow = op.nextTariff() != null ? op.nextTariff() : op.tariff();
            if (tomorrow > cap) {
                OperatorActions.notify(server, registry, op.founder(), Component.translatable(MESSAGE + "cap_lowered",
                        CoinMath.format(cap), op.name()));
            }
        }
        return Result.DONE;
    }

    /**
     * {@code /operator set <opérateur> tariff} : le tarif de l'opérateur pour demain, sous le plafond de demain
     * ({@link #nextCap}) ; le tarif du jour ne bouge pas. Le fondateur est prévenu (à sa connexion s'il est absent).
     */
    public static Result setTariff(CommandSourceStack source, PhoneOperators registry, String name, long value) {
        if (!PowerCommands.mayGovern(source)) {
            return denied(source);
        }
        Optional<PhoneOperator> found = find(source, registry, name);
        if (found.isEmpty()) {
            return Result.UNKNOWN_OPERATOR;
        }
        PhoneOperator op = found.get();
        long max = nextCap(registry);
        if (value < 0L || value > max || !registry.setNextTariff(op.id(), value)) {
            return fail(source, Result.ABOVE_CAP, Component.translatable(KEY + "set_above_cap", CoinMath.format(max)));
        }
        note(source, EconomyLedger.KIND_PHONE_TARIFF, value, op.name() + " : tarif " + value
                + " cuivres par jour des demain");
        source.sendSuccess(() -> Component.translatable(KEY + "tariff_set", op.name(), CoinMath.format(value)), true);
        OperatorActions.notify(source.getServer(), registry, op.founder(), Component.translatable(
                MESSAGE + "tariff_by_mayor", op.name(), CoinMath.format(value)));
        return Result.DONE;
    }

    // ---------------------------------------------------------------------------------------------
    // Propriétaire
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /operator owner} : l'opérateur passe à un autre joueur, connu du serveur et sans opérateur (un par joueur).
     * Cartes, caisse et antennes le suivent : le nouveau fondateur émet, l'ancien plus. Les panneaux ouverts de l'ancien
     * fondateur sur cet opérateur (bloc et téléphone) se ferment ; l'ancien et le nouveau sont prévenus (à leur connexion
     * s'ils sont absents).
     */
    public static Result setOwner(CommandSourceStack source, PhoneOperators registry, String name, String playerName) {
        if (!PowerCommands.mayGovern(source)) {
            return denied(source);
        }
        Optional<PhoneOperator> found = find(source, registry, name);
        if (found.isEmpty()) {
            return Result.UNKNOWN_OPERATOR;
        }
        PhoneOperator op = found.get();
        MinecraftServer server = source.getServer();
        Optional<GameProfile> profile = PowerPairing.knownPlayer(server, playerName);
        if (profile.isEmpty()) {
            return fail(source, Result.UNKNOWN_PLAYER, Component.translatable(KEY + "owner_unknown_player", playerName));
        }
        UUID heir = profile.get().getId();
        String heirName = profile.get().getName();
        if (heir.equals(op.founder())) {
            return fail(source, Result.HAS_OPERATOR, Component.translatable(KEY + "owner_same", heirName, op.name()));
        }
        if (!registry.setFounder(op.id(), heir, heirName)) {
            return fail(source, Result.HAS_OPERATOR, Component.translatable(KEY + "owner_has_operator", heirName));
        }
        OperatorPanels.closeOperator(server, op.id(), op.founder());
        note(source, EconomyLedger.KIND_PHONE_OWNER, 0L, op.name() + " : " + op.founderName() + " -> " + heirName);
        source.sendSuccess(() -> Component.translatable(KEY + "owner_set", op.name(), heirName), true);
        OperatorActions.notify(server, registry, op.founder(), Component.translatable(MESSAGE + "owner_lost", op.name(),
                heirName));
        OperatorActions.notify(server, registry, heir, Component.translatable(MESSAGE + "owner_new", op.name(),
                op.founderName()));
        return Result.DONE;
    }

    // ---------------------------------------------------------------------------------------------
    // Suppression
    // ---------------------------------------------------------------------------------------------

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "operator");
        String reason = StringArgumentType.getString(ctx, "reason").strip();
        long now = EconomyClock.now();
        PhoneOperators registry = registry(ctx);
        if (reason.equalsIgnoreCase("confirm")) {
            return code(confirmRemove(source, registry, name, now));
        }
        return code(requestRemove(source, registry, name, reason, now));
    }

    /**
     * {@code /operator remove <opérateur> <motif>} : ne fait qu'armer la confirmation, liée à cette source et à cet
     * opérateur, pour {@link #CONFIRM_MILLIS} ms ({@link #confirmRemove}). Rien d'autre ne bouge ; une nouvelle demande de
     * la même source remplace la précédente.
     */
    public static Result requestRemove(CommandSourceStack source, PhoneOperators registry, String name, String reason,
            long now) {
        if (!PowerCommands.mayGovern(source)) {
            return denied(source);
        }
        Optional<PhoneOperator> found = find(source, registry, name);
        if (found.isEmpty()) {
            return Result.UNKNOWN_OPERATOR;
        }
        PhoneOperator op = found.get();
        String motif = reason(reason);
        PENDING.put(requester(source), new PendingRemoval(op.id(), motif, now + CONFIRM_MILLIS));
        String confirm = "/operator remove " + StringArgumentType.escapeIfRequired(op.name()) + " confirm";
        Component button = Component.translatable(KEY + "remove_confirm_button").withStyle(s -> s
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, confirm)));
        int cards = registry.activeCards(op.id());
        int antennas = registry.antennasOf(op.id()).size();
        source.sendSuccess(() -> Component.translatable(KEY + "remove_requested", op.name(), cards, antennas,
                CoinMath.format(CoinMath.valueOf(op.till())), motif, button), false);
        return Result.REQUESTED;
    }

    /**
     * {@code /operator remove <opérateur> confirm} : la suppression demandée par cette même source, pour cet opérateur,
     * il y a au plus {@link #CONFIRM_MILLIS} ms. Une demande échue, ou faite pour un autre opérateur, ne vaut pas ; la
     * demande est oubliée dans tous les cas, sauf quand le versement de la caisse refuse la suppression (rien n'a bougé :
     * on peut confirmer de nouveau dans le délai).
     */
    public static Result confirmRemove(CommandSourceStack source, PhoneOperators registry, String name, long now) {
        return confirmRemove(source, registry, name, now, SERVER_PAYER);
    }

    /** Comme {@link #confirmRemove(CommandSourceStack, PhoneOperators, String, long)}, avec ce versement (essais). */
    public static Result confirmRemove(CommandSourceStack source, PhoneOperators registry, String name, long now,
            Payer payer) {
        if (!PowerCommands.mayGovern(source)) {
            return denied(source);
        }
        UUID key = requester(source);
        PendingRemoval pending = PENDING.get(key);
        Optional<PhoneOperator> op = registry.byName(name);
        if (pending == null || op.isEmpty() || !pending.operatorId().equals(op.get().id())) {
            return fail(source, Result.NO_REQUEST, Component.translatable(KEY + "remove_no_request"));
        }
        if (now > pending.until()) {
            PENDING.remove(key);
            return fail(source, Result.EXPIRED, Component.translatable(KEY + "remove_expired"));
        }
        Optional<Removal> done = remove(source.getServer(), registry, op.get(), pending.reason(), author(source),
                author(source) == null ? source.getTextName() : null, payer);
        if (done.isEmpty()) {
            return fail(source, Result.TILL_REFUSED, Component.translatable(KEY + "remove_till_refused",
                    op.get().name()));
        }
        PENDING.remove(key);
        Removal r = done.get();
        source.sendSuccess(() -> Component.translatable(KEY + "removed", op.get().name(), r.cards(), r.antennas(),
                r.requests(), CoinMath.format(r.till())), true);
        return Result.DONE;
    }

    /**
     * Ce qu'a fait une suppression.
     *
     * @param cards les cartes désactivées par elle
     * @param antennas les antennes retirées du registre
     * @param requests les demandes de carte retirées
     * @param till la valeur de la caisse versée au fondateur
     * @param destination où elle est arrivée (la part la plus lointaine)
     */
    public record Removal(int cards, int antennas, int requests, long till, CommerceDelivery.Destination destination) {}

    /**
     * Supprime cet opérateur, sans confirmation (l'appelant l'a obtenue). Ordre, à respecter :
     * <ol type="a">
     *   <li>la caisse est retirée en entier et versée au fondateur ({@code payer}, en production
     *       {@link CommerceDelivery#payToSafeFirst} : coffre-fort, inventaire, sinon en attente) <b>avant tout le
     *       reste</b>. Si un retrait ou le versement échoue, les pièces retirées sont remises dans la caisse et la
     *       suppression est refusée : aucune pièce perdue, rien d'autre de modifié. C'est le seul point de refus ;</li>
     *   <li>toutes ses cartes sont désactivées ({@code DEACTIVATED}, pour toujours ; numéros réservés) ;</li>
     *   <li>ses antennes sont retirées du registre (leur bloc reste posé et s'arrête, {@code STOPPED}, sans plus rien
     *       consommer ; le ticket de leur chunk est rendu, {@link AntennaChunks#release}) ;</li>
     *   <li>ses demandes de carte sont retirées, et les demandeurs prévenus ;</li>
     *   <li>les panneaux ouverts sur lui (blocs et appli « Antenne » des téléphones) sont fermés ;</li>
     *   <li>le fondateur et les titulaires de ses cartes sont prévenus ;</li>
     *   <li>il est retiré du registre ; son fondateur devient l'ancien fondateur de ses antennes restées posées et de ses
     *       objets d'antenne ({@link PhoneOperators#formerFounder}).</li>
     * </ol>
     * Les étapes b à g ne peuvent pas échouer (un seul fil, la caisse est vide après a) : vu du joueur, la suppression
     * est entière ou n'a pas eu lieu. Un absent est prévenu à sa connexion. Une ligne au journal.
     *
     * @param sourceName le nom de la source (console, RCON, bloc de commande), noté au journal sans joueur auteur
     * @return ce qui a été fait, ou vide si la suppression est refusée (opérateur absent, caisse non versée)
     */
    public static Optional<Removal> remove(MinecraftServer server, PhoneOperators registry, PhoneOperator operator,
            String reason, @Nullable UUID author, @Nullable String sourceName, Payer payer) {
        String id = operator.id();
        Optional<PhoneOperator> found = registry.byId(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PhoneOperator current = found.get();
        // a. La caisse au fondateur, avant tout le reste ; tout échec remet les pièces et refuse.
        EnumMap<Coin, Long> till = current.till();
        long value = CoinMath.valueOf(till);
        EnumMap<Coin, Long> taken = new EnumMap<>(Coin.class);
        for (Map.Entry<Coin, Long> part : till.entrySet()) {
            if (!registry.withdraw(id, part.getKey(), part.getValue())) {
                putBack(registry, id, taken);
                LOGGER.error("Telephone : caisse de {} : retrait impossible, suppression refusee", current.name());
                return Optional.empty();
            }
            taken.put(part.getKey(), part.getValue());
        }
        CommerceDelivery.Destination destination;
        try {
            destination = payer.pay(server, current.founder(), value);
        } catch (RuntimeException e) {
            putBack(registry, id, taken);
            LOGGER.error("Telephone : caisse de {} non versee au fondateur, suppression refusee", current.name(), e);
            return Optional.empty();
        }
        if (destination == null) {
            putBack(registry, id, taken);
            LOGGER.error("Telephone : caisse de {} : versement sans destination, suppression refusee", current.name());
            return Optional.empty();
        }
        // b. Les cartes : désactivées pour toujours.
        List<SimCard> cards = registry.deactivateCards(id);
        // c. Les antennes : retirées du registre, ticket du chunk rendu ; le bloc reste et s'arrête.
        List<AntennaRecord> antennas = registry.antennasOf(id);
        for (AntennaRecord antenna : antennas) {
            registry.removeAntenna(antenna.id());
            ServerLevel level = server.getLevel(antenna.dimension());
            if (level != null) {
                AntennaChunks.release(level, antenna.pos());
            }
        }
        // d. Les demandes : retirées, demandeurs prévenus.
        List<SimRequest> requests = registry.requestsOf(id);
        for (SimRequest request : requests) {
            registry.takeRequest(request.id());
            OperatorActions.notify(server, registry, request.player(), Component.translatable(
                    MESSAGE + "removed_request", current.name()));
        }
        // e. Les panneaux ouverts sur lui : fermés.
        OperatorPanels.closeOperator(server, id, null);
        // f. Le fondateur et les titulaires : prévenus.
        OperatorActions.notify(server, registry, current.founder(), Component.translatable(MESSAGE + "removed_founder",
                current.name(), reason, cards.size(), CoinMath.format(value),
                Component.translatable(MESSAGE + "dest." + destination.name().toLowerCase(Locale.ROOT))));
        Set<UUID> holders = new LinkedHashSet<>();
        for (SimCard card : cards) {
            if (card.holder() != null && !card.holder().equals(current.founder())) {
                holders.add(card.holder());
            }
        }
        for (UUID holder : holders) {
            OperatorActions.notify(server, registry, holder, Component.translatable(MESSAGE + "removed_holder",
                    current.name(), reason));
        }
        // g. L'opérateur : retiré (sa caisse est vide, il existe : rien ne peut refuser ici).
        if (!registry.remove(id)) {
            LOGGER.error("Telephone : {} n'a pas pu etre retire du registre apres la suppression", current.name());
        }
        EconomyLedger.get(server).noted(EconomyLedger.KIND_PHONE_REMOVE, author, value,
                (author == null && sourceName != null ? "[" + sourceName + "] " : "") + current.name() + " : "
                + reason + " (" + cards.size() + " carte(s), " + antennas.size() + " antenne(s), " + requests.size()
                + " demande(s), caisse a " + current.founderName() + ")");
        LOGGER.warn("Telephone : operateur {} supprime ({}) : {} carte(s) desactivee(s), {} antenne(s), {} demande(s), "
                + "caisse de {} cuivres a {} ({})", current.name(), reason, cards.size(), antennas.size(),
                requests.size(), value, current.founderName(), destination);
        return Optional.of(new Removal(cards.size(), antennas.size(), requests.size(), value, destination));
    }

    private static void putBack(PhoneOperators registry, String id, EnumMap<Coin, Long> taken) {
        taken.forEach((coin, count) -> registry.deposit(id, coin, count));
    }
}
