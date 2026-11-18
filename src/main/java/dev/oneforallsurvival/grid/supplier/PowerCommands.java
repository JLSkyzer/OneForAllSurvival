package dev.oneforallsurvival.grid.supplier;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.role.RoleGate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * {@code /power} : les gestes des joueurs sur leurs raccordements (lot E6, tâche 3) et ceux du maire et de l'équipe
 * (tâche 6, features 83 et 107 du maire).
 *
 * <p>Joueurs (ouverts à tous ; chaque geste ne touche qu'aux raccordements de celui qui l'envoie) :
 * <ul>
 *   <li>{@code /power consent <code>} : accord donné, la demande part au fournisseur ;</li>
 *   <li>{@code /power refuse <code>} : refus, le compteur est détaché, personne n'est facturé ;</li>
 *   <li>{@code /power resign [fournisseur]} : résiliation (dernière facture à la minuit suivante, tâche 4) ;</li>
 *   <li>{@code /power list} : ses raccordements, prix et abonnement.</li>
 * </ul>
 *
 * <p>Maire ou équipe ({@link RoleGate#canDoMayorWork} ; la console aussi), refus clair sinon ; chaque geste accepté
 * laisse une ligne au journal du registre de l'économie, avec son auteur :
 * <ul>
 *   <li>{@code /power cap <cuivres>} : plafond du prix par tranche de {@code powerPriceUnitFe} FE, au lendemain ;</li>
 *   <li>{@code /power tax <pourcent>} : taxe sur l'électricité, de 0 à {@code powerTaxMaxPercent}, au lendemain ;</li>
 *   <li>{@code /power suppliers} : les fournisseurs (fondateur, clients, prix, abonnement) ;</li>
 *   <li>{@code /power set <fournisseur> price|subscription <valeur>} : tarif d'un fournisseur, au lendemain, sous le
 *       plafond du lendemain. Permis au maire même s'il est client de ce fournisseur : c'est par ces commandes, tracées,
 *       et non par le panneau ({@link PowerActionPacket#mayTariff}), qu'il le règle ;</li>
 *   <li>{@code /power owner <fournisseur> <joueur>} : nouveau fondateur (un joueur connu sans fournisseur) ;</li>
 *   <li>{@code /power remove <fournisseur> <motif>} puis {@code /power remove <fournisseur> confirm} dans les
 *       {@value #CONFIRM_MILLIS} ms : clients détachés, prévenus et coupés ; factures dues annulées ; caisse versée au
 *       fondateur ({@link CommerceDelivery#payToSafeFirst}, jamais détruite) ; jumelages oubliés.</li>
 * </ul>
 * Un nom de fournisseur qui contient des espaces s'écrit entre guillemets. Les méthodes publiques prennent leurs
 * registres en paramètre (essais) ; les commandes passent ceux du serveur. Fil du serveur.
 */
public final class PowerCommands {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "command.oneforallsurvival.power.";
    private static final String MESSAGE = PowerPairing.MESSAGE;

    /** Délai de confirmation d'une suppression : 60 s. */
    public static final long CONFIRM_MILLIS = 60_000L;

    /** Plafond le plus haut accepté par {@code /power cap}, en cuivres par tranche. */
    public static final long MAX_CAP = 1_000_000_000L;

    /** Longueur gardée d'un motif de suppression. */
    public static final int MAX_REASON = 100;

    /** Lignes par page de {@code /power suppliers} et {@code /eco power}. */
    public static final int PAGE_SIZE = 10;

    /** Issue d'un geste du maire ou de l'équipe. */
    public enum Result { DONE, REQUESTED, NOT_ALLOWED, UNKNOWN_SUPPLIER, OUT_OF_RANGE, ABOVE_CAP, UNKNOWN_PLAYER,
        HAS_SUPPLIER, NO_REQUEST, EXPIRED }

    /** Une suppression demandée, qui attend sa confirmation (non sauvegardée : un redémarrage l'oublie). */
    private record PendingRemoval(String supplierId, String reason, long until) {}

    private static final Map<UUID, PendingRemoval> PENDING = new HashMap<>();

    /** À l'arrêt du serveur : les demandes en attente ne passent pas d'un monde à l'autre (même JVM). */
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** Le nombre de pages pour {@code count} lignes (au moins une). */
    public static int pages(int count) {
        return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static final SuggestionProvider<CommandSourceStack> SUPPLIERS = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        for (PowerSupplier supplier : PowerSuppliers.get(ctx.getSource().getServer()).all()) {
            names.add(StringArgumentType.escapeIfRequired(supplier.name()));
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("power")
                .then(Commands.literal("consent").then(Commands.argument("code", StringArgumentType.word())
                        .executes(ctx -> consent(ctx, true))))
                .then(Commands.literal("refuse").then(Commands.argument("code", StringArgumentType.word())
                        .executes(ctx -> consent(ctx, false))))
                .then(Commands.literal("resign")
                        .executes(ctx -> resign(ctx, null))
                        .then(Commands.argument("supplier", StringArgumentType.greedyString())
                                .executes(ctx -> resign(ctx, StringArgumentType.getString(ctx, "supplier")))))
                .then(Commands.literal("list").executes(PowerCommands::list))
                .then(Commands.literal("suppliers").executes(ctx -> suppliers(ctx.getSource(), server(ctx), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> suppliers(ctx.getSource(), server(ctx),
                                        IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("cap").then(Commands.argument("copper", LongArgumentType.longArg())
                        .executes(ctx -> code(setCap(ctx.getSource(), server(ctx),
                                LongArgumentType.getLong(ctx, "copper"))))))
                .then(Commands.literal("tax").then(Commands.argument("percent", IntegerArgumentType.integer())
                        .executes(ctx -> code(setTax(ctx.getSource(), server(ctx),
                                IntegerArgumentType.getInteger(ctx, "percent"))))))
                .then(Commands.literal("set").then(Commands.argument("supplier", StringArgumentType.string())
                        .suggests(SUPPLIERS)
                        .then(Commands.literal("price").then(Commands.argument("value", LongArgumentType.longArg())
                                .executes(ctx -> code(setTariff(ctx.getSource(), server(ctx),
                                        StringArgumentType.getString(ctx, "supplier"), true,
                                        LongArgumentType.getLong(ctx, "value"))))))
                        .then(Commands.literal("subscription").then(Commands.argument("value", LongArgumentType.longArg())
                                .executes(ctx -> code(setTariff(ctx.getSource(), server(ctx),
                                        StringArgumentType.getString(ctx, "supplier"), false,
                                        LongArgumentType.getLong(ctx, "value"))))))))
                .then(Commands.literal("owner").then(Commands.argument("supplier", StringArgumentType.string())
                        .suggests(SUPPLIERS)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> code(setOwner(ctx.getSource(), server(ctx),
                                        StringArgumentType.getString(ctx, "supplier"),
                                        StringArgumentType.getString(ctx, "player")))))))
                .then(Commands.literal("remove").then(Commands.argument("supplier", StringArgumentType.string())
                        .suggests(SUPPLIERS)
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(PowerCommands::remove)))));
    }

    private static PowerSuppliers server(CommandContext<CommandSourceStack> ctx) {
        return PowerSuppliers.get(ctx.getSource().getServer());
    }

    private static int code(Result result) {
        return result == Result.DONE || result == Result.REQUESTED ? 1 : 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Gestes des joueurs (tâche 3)
    // ---------------------------------------------------------------------------------------------

    private static int consent(CommandContext<CommandSourceStack> ctx, boolean yes) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String code = StringArgumentType.getString(ctx, "code");
        PowerPairing.Outcome outcome = yes ? PowerPairing.consent(player, code) : PowerPairing.refuse(player, code);
        if (outcome != PowerPairing.Outcome.DONE) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "code_" + outcome.name().toLowerCase(Locale.ROOT)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + (yes ? "consented" : "refused")), false);
        return 1;
    }

    private static int resign(CommandContext<CommandSourceStack> ctx, String supplier) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int changed = PowerPairing.resign(player, supplier);
        if (changed == 0) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "resign_none"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "resigned", changed), false);
        return changed;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PowerSuppliers registry = PowerSuppliers.get(player.server);
        int count = 0;
        for (PowerSupplier supplier : registry.all()) {
            for (PowerClient client : registry.clients(supplier.id())) {
                if (!client.player().equals(player.getUUID())) {
                    continue;
                }
                count++;
                MutableComponent line = Component.translatable(KEY + "list_line", supplier.name(), client.subscriber(),
                        Component.translatable(KEY + "state." + client.state().name().toLowerCase(Locale.ROOT)),
                        CoinMath.format(supplier.price()), EconomyConfig.powerPriceUnitFe(),
                        CoinMath.format(supplier.subscription()));
                if (client.cut()) {
                    line.append(" ").append(Component.translatable(KEY + "list_cut").withStyle(ChatFormatting.RED));
                }
                Optional<String> code = registry.codeOf(client.meterSerial());
                if (client.state() == PowerClient.State.AWAITING_CONSENT && code.isPresent()) {
                    line.append(" ").append(Component.translatable(PowerPairing.MESSAGE + "button_consent")
                            .withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/power consent " + code.get()))));
                    line.append(" ").append(Component.translatable(PowerPairing.MESSAGE + "button_refuse")
                            .withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/power refuse " + code.get()))));
                }
                ctx.getSource().sendSuccess(() -> line, false);
            }
        }
        if (count == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "list_none"), false);
        }
        return count;
    }

    // ---------------------------------------------------------------------------------------------
    // Droits du maire et de l'équipe
    // ---------------------------------------------------------------------------------------------

    /**
     * Cette source peut-elle faire les gestes du maire ? Un joueur : le maire ou l'équipe (niveau 2), jamais un faux
     * joueur ; une source sans entité (console, RCON) : le niveau 2.
     */
    public static boolean mayGovern(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return !(player instanceof FakePlayer) && RoleGate.canDoMayorWork(player);
        }
        return source.getEntity() == null && source.hasPermission(2);
    }

    @Nullable
    private static UUID author(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
    }

    /**
     * La clé de confirmation d'une source : le joueur, sinon le nom de la source (console, RCON, bloc de commande nommé) ;
     * deux sources sans joueur de noms différents ne se confirment pas l'une l'autre.
     */
    private static UUID requester(CommandSourceStack source) {
        UUID author = author(source);
        return author != null ? author : UUID.nameUUIDFromBytes(("source:" + source.getTextName())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Le motif d'une ligne de journal : préfixé du nom de la source quand aucun joueur n'en est l'auteur. */
    private static String byWhom(CommandSourceStack source, String reason) {
        return author(source) != null ? reason : "[" + source.getTextName() + "] " + reason;
    }

    private static Result fail(CommandSourceStack source, Result result, Component message) {
        refuse(source, message);
        return result;
    }

    /** Un refus, par {@link CommandSourceStack#sendFailure} (qui respecte une source muette). */
    private static void refuse(CommandSourceStack source, Component message) {
        source.sendFailure(message);
    }

    private static Result denied(CommandSourceStack source) {
        return fail(source, Result.NOT_ALLOWED, Component.translatable(KEY + "not_mayor"));
    }

    private static Optional<PowerSupplier> find(CommandSourceStack source, PowerSuppliers registry, String name) {
        Optional<PowerSupplier> supplier = registry.byName(name);
        if (supplier.isEmpty()) {
            refuse(source, Component.translatable(KEY + "unknown_supplier", name));
        }
        return supplier;
    }

    private static void note(CommandSourceStack source, String kind, long amount, String reason) {
        EconomyLedger.get(source.getServer()).noted(kind, author(source), amount, byWhom(source, reason));
        LOGGER.info("Electricite : {} ({}) : {}", source.getTextName(), kind, reason);
    }

    // ---------------------------------------------------------------------------------------------
    // Plafond et taxe
    // ---------------------------------------------------------------------------------------------

    /** {@code /power cap} : le plafond du prix, au lendemain ; un fondateur au-dessus est prévenu tout de suite. */
    public static Result setCap(CommandSourceStack source, PowerSuppliers registry, long cap) {
        if (!mayGovern(source)) {
            return denied(source);
        }
        if (cap < 0L || cap > MAX_CAP) {
            return fail(source, Result.OUT_OF_RANGE, Component.translatable(KEY + "cap_out_of_range",
                    CoinMath.format(MAX_CAP)));
        }
        long unit = EconomyConfig.powerPriceUnitFe();
        registry.setNextPriceCap(cap);
        note(source, EconomyLedger.KIND_POWER_CAP, cap,
                "plafond de l'electricite : " + cap + " cuivres par " + unit + " FE, des demain");
        source.sendSuccess(() -> Component.translatable(KEY + "cap_set", CoinMath.format(cap), unit), true);
        MinecraftServer server = source.getServer();
        for (PowerSupplier supplier : registry.all()) {
            long next = supplier.nextPrice() != null ? supplier.nextPrice() : supplier.price();
            if (next > cap) {
                PowerPairing.tell(server, registry, supplier.founder(), Component.translatable(MESSAGE + "cap_lowered",
                        CoinMath.format(cap), unit, supplier.name()));
            }
        }
        return Result.DONE;
    }

    /** {@code /power tax} : la taxe sur l'électricité, de 0 à {@code powerTaxMaxPercent}, au lendemain. */
    public static Result setTax(CommandSourceStack source, PowerSuppliers registry, int percent) {
        if (!mayGovern(source)) {
            return denied(source);
        }
        int max = EconomyConfig.powerTaxMaxPercent();
        if (percent < 0 || percent > max) {
            return fail(source, Result.OUT_OF_RANGE, Component.translatable(KEY + "tax_above_max", max));
        }
        registry.setNextTaxPercent(percent);
        note(source, EconomyLedger.KIND_POWER_TAX, 0L, "taxe sur l'electricite : " + percent + " % des demain");
        source.sendSuccess(() -> Component.translatable(KEY + "tax_set", percent), true);
        return Result.DONE;
    }

    // ---------------------------------------------------------------------------------------------
    // Fournisseurs : liste, tarif, propriétaire
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /power suppliers [page]} : chaque fournisseur, son fondateur, ses clients, son prix et son abonnement,
     * {@value #PAGE_SIZE} par page (ordre de fondation), avec le total.
     *
     * @return le nombre de lignes montrées (au moins 1), 0 si refusé
     */
    public static int suppliers(CommandSourceStack source, PowerSuppliers registry, int page) {
        if (!mayGovern(source)) {
            denied(source);
            return 0;
        }
        long unit = EconomyConfig.powerPriceUnitFe();
        List<PowerSupplier> all = new ArrayList<>(registry.all());
        int pages = pages(all.size());
        if (page < 1 || page > pages) {
            refuse(source, Component.translatable(KEY + "no_page", page, pages));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "suppliers_title", all.size(), page, pages), false);
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "suppliers_none"), false);
        }
        List<PowerSupplier> shown = all.subList((page - 1) * PAGE_SIZE, Math.min(all.size(), page * PAGE_SIZE));
        for (PowerSupplier supplier : shown) {
            boolean changed = supplier.nextPrice() != null || supplier.nextSubscription() != null;
            long nextPrice = supplier.nextPrice() != null ? supplier.nextPrice() : supplier.price();
            long nextSubscription = supplier.nextSubscription() != null ? supplier.nextSubscription()
                    : supplier.subscription();
            Component next = changed ? Component.translatable(KEY + "suppliers_next", CoinMath.format(nextPrice),
                    CoinMath.format(nextSubscription)) : Component.empty();
            Component line = Component.translatable(KEY + "suppliers_line", supplier.name(), supplier.founderName(),
                    registry.clients(supplier.id()).size(), CoinMath.format(supplier.price()), unit,
                    CoinMath.format(supplier.subscription()), next);
            source.sendSuccess(() -> line, false);
        }
        return Math.max(1, shown.size());
    }

    /**
     * {@code /power set} : le prix (sous le plafond du lendemain) ou l'abonnement (0 à {@code powerMaxSubscription})
     * d'un fournisseur, au lendemain. Le fondateur est prévenu, et les clients connectés si le tarif change.
     */
    public static Result setTariff(CommandSourceStack source, PowerSuppliers registry, String name, boolean price,
            long value) {
        if (!mayGovern(source)) {
            return denied(source);
        }
        Optional<PowerSupplier> found = find(source, registry, name);
        if (found.isEmpty()) {
            return Result.UNKNOWN_SUPPLIER;
        }
        PowerSupplier supplier = found.get();
        long unit = EconomyConfig.powerPriceUnitFe();
        long before;
        if (price) {
            long cap = PowerActionPacket.nextCap(registry);
            if (value < 0L || value > cap) {
                return fail(source, Result.ABOVE_CAP, Component.translatable(KEY + "set_above_cap",
                        CoinMath.format(cap), unit));
            }
            before = supplier.nextPrice() != null ? supplier.nextPrice() : supplier.price();
            registry.setNextPrice(supplier.id(), value);
            note(source, EconomyLedger.KIND_POWER_PRICE, value,
                    supplier.name() + " : prix " + value + " cuivres par " + unit + " FE des demain");
            source.sendSuccess(() -> Component.translatable(KEY + "price_set", supplier.name(), CoinMath.format(value),
                    unit), true);
        } else {
            long max = EconomyConfig.powerMaxSubscription();
            if (value < 0L || value > max) {
                return fail(source, Result.OUT_OF_RANGE, Component.translatable(KEY + "subscription_above_max",
                        CoinMath.format(max)));
            }
            before = supplier.nextSubscription() != null ? supplier.nextSubscription() : supplier.subscription();
            registry.setNextSubscription(supplier.id(), value);
            note(source, EconomyLedger.KIND_POWER_SUBSCRIPTION, value,
                    supplier.name() + " : abonnement " + value + " cuivres par jour des demain");
            source.sendSuccess(() -> Component.translatable(KEY + "subscription_set", supplier.name(),
                    CoinMath.format(value)), true);
        }
        PowerSupplier now = registry.byId(supplier.id()).orElse(supplier);
        long nextPrice = now.nextPrice() != null ? now.nextPrice() : now.price();
        long nextSubscription = now.nextSubscription() != null ? now.nextSubscription() : now.subscription();
        PowerPairing.tell(source.getServer(), registry, now.founder(), Component.translatable(MESSAGE + "tariff_by_mayor",
                now.name(), CoinMath.format(nextPrice), unit, CoinMath.format(nextSubscription)));
        if (value != before) {
            PowerActionPacket.announceTariff(source.getServer(), registry, now.id());
        }
        return Result.DONE;
    }

    /**
     * {@code /power owner} : le fournisseur passe à un autre joueur, connu du serveur et sans fournisseur (un par
     * joueur). Caisse, poste et clients le suivent. L'ancien et le nouveau fondateur sont prévenus.
     */
    public static Result setOwner(CommandSourceStack source, PowerSuppliers registry, String name, String playerName) {
        if (!mayGovern(source)) {
            return denied(source);
        }
        Optional<PowerSupplier> found = find(source, registry, name);
        if (found.isEmpty()) {
            return Result.UNKNOWN_SUPPLIER;
        }
        PowerSupplier supplier = found.get();
        MinecraftServer server = source.getServer();
        Optional<GameProfile> profile = PowerPairing.knownPlayer(server, playerName);
        if (profile.isEmpty()) {
            return fail(source, Result.UNKNOWN_PLAYER, Component.translatable(KEY + "owner_unknown_player", playerName));
        }
        UUID heir = profile.get().getId();
        String heirName = profile.get().getName();
        if (heir.equals(supplier.founder())) {
            return fail(source, Result.HAS_SUPPLIER, Component.translatable(KEY + "owner_same", heirName,
                    supplier.name()));
        }
        if (!registry.setFounder(supplier.id(), heir, heirName)) {
            return fail(source, Result.HAS_SUPPLIER, Component.translatable(KEY + "owner_has_supplier", heirName));
        }
        note(source, EconomyLedger.KIND_POWER_OWNER, 0L,
                supplier.name() + " : " + supplier.founderName() + " -> " + heirName);
        source.sendSuccess(() -> Component.translatable(KEY + "owner_set", supplier.name(), heirName), true);
        PowerPairing.tell(server, registry, supplier.founder(), Component.translatable(MESSAGE + "owner_lost",
                supplier.name(), heirName));
        PowerPairing.tell(server, registry, heir, Component.translatable(MESSAGE + "owner_new", supplier.name(),
                supplier.founderName()));
        return Result.DONE;
    }

    // ---------------------------------------------------------------------------------------------
    // Suppression
    // ---------------------------------------------------------------------------------------------

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "supplier");
        String reason = StringArgumentType.getString(ctx, "reason").strip();
        long now = EconomyClock.now();
        PowerSuppliers registry = server(ctx);
        if (reason.equalsIgnoreCase("confirm")) {
            return code(confirmRemove(source, registry, InvoiceRegistry.get(source.getServer()), name, now));
        }
        return code(requestRemove(source, registry, name, reason, now));
    }

    /**
     * {@code /power remove <fournisseur> <motif>} : la suppression attend la confirmation de la même source, pour le même
     * fournisseur, dans les {@link #CONFIRM_MILLIS} ms ({@link #confirmRemove}). Rien ne bouge avant ; une nouvelle
     * demande remplace la précédente.
     */
    public static Result requestRemove(CommandSourceStack source, PowerSuppliers registry, String name, String reason,
            long now) {
        if (!mayGovern(source)) {
            return denied(source);
        }
        Optional<PowerSupplier> found = find(source, registry, name);
        if (found.isEmpty()) {
            return Result.UNKNOWN_SUPPLIER;
        }
        PowerSupplier supplier = found.get();
        String stripped = reason.strip();
        String motif = stripped.length() > MAX_REASON ? stripped.substring(0, MAX_REASON) : stripped;
        PENDING.put(requester(source), new PendingRemoval(supplier.id(), motif, now + CONFIRM_MILLIS));
        String confirm = "/power remove " + StringArgumentType.escapeIfRequired(supplier.name()) + " confirm";
        Component button = Component.translatable(KEY + "remove_confirm_button").withStyle(s -> s
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, confirm)));
        int clients = registry.clients(supplier.id()).size();
        source.sendSuccess(() -> Component.translatable(KEY + "remove_requested", supplier.name(), clients,
                CoinMath.format(supplier.tillValue()), motif, button), false);
        return Result.REQUESTED;
    }

    /**
     * {@code /power remove <fournisseur> confirm} : la suppression demandée par cette même source, pour ce fournisseur,
     * il y a au plus {@link #CONFIRM_MILLIS} ms ({@link #remove(MinecraftServer, PowerSuppliers, InvoiceRegistry,
     * PowerSupplier, String, UUID, long)}). Une demande échue est oubliée.
     */
    public static Result confirmRemove(CommandSourceStack source, PowerSuppliers registry, InvoiceRegistry invoices,
            String name, long now) {
        if (!mayGovern(source)) {
            return denied(source);
        }
        UUID key = requester(source);
        PendingRemoval pending = PENDING.get(key);
        Optional<PowerSupplier> supplier = registry.byName(name);
        if (pending == null || supplier.isEmpty() || !pending.supplierId().equals(supplier.get().id())) {
            return fail(source, Result.NO_REQUEST, Component.translatable(KEY + "remove_no_request"));
        }
        PENDING.remove(key);
        if (now > pending.until()) {
            return fail(source, Result.EXPIRED, Component.translatable(KEY + "remove_expired"));
        }
        Removal done = remove(source.getServer(), registry, invoices, supplier.get(), pending.reason(), author(source),
                author(source) == null ? source.getTextName() : null, now);
        source.sendSuccess(() -> Component.translatable(KEY + "removed", supplier.get().name(), done.clients(),
                done.cancelled(), CoinMath.format(done.cancelledTotal()), CoinMath.format(done.till())), true);
        return Result.DONE;
    }

    /**
     * Ce qu'a fait une suppression.
     *
     * @param clients les clients détachés (demandes comprises)
     * @param cancelled les factures dues annulées ({@code cancelledTotal} : leur total)
     * @param till la valeur de la caisse versée au fondateur
     * @param destination où elle est arrivée (la part la plus lointaine)
     */
    public record Removal(int clients, int cancelled, long cancelledTotal, long till,
            CommerceDelivery.Destination destination) {}

    /**
     * Supprime ce fournisseur, sans confirmation (l'appelant l'a obtenue) : ses factures d'électricité dues sont
     * annulées, ses clients (demandes comprises) détachés et coupés, sa caisse versée à son fondateur par
     * {@link CommerceDelivery#payToSafeFirst} (coffre-fort, inventaire, sinon en attente : aucune pièce détruite, la
     * valeur conservée au cuivre près), puis il est retiré du registre avec ses jumelages et ses détachés du jour.
     * Clients, débiteurs et fondateur sont prévenus (à leur connexion s'ils sont absents). Une ligne au journal.
     */
    public static Removal remove(MinecraftServer server, PowerSuppliers registry, InvoiceRegistry invoices,
            PowerSupplier supplier, String reason, @Nullable UUID author, long now) {
        return remove(server, registry, invoices, supplier, reason, author, null, now);
    }

    /**
     * Comme {@link #remove(MinecraftServer, PowerSuppliers, InvoiceRegistry, PowerSupplier, String, UUID, long)} ;
     * {@code sourceName} (console, RCON, bloc de commande) est noté au journal quand aucun joueur n'en est l'auteur.
     */
    public static Removal remove(MinecraftServer server, PowerSuppliers registry, InvoiceRegistry invoices,
            PowerSupplier supplier, String reason, @Nullable UUID author, @Nullable String sourceName, long now) {
        String id = supplier.id();
        // 1. Factures dues : annulées (aucune pièce ne bouge), comptées par débiteur pour l'avis.
        Map<UUID, Integer> cancelledBy = new LinkedHashMap<>();
        int cancelled = 0;
        long cancelledTotal = 0L;
        for (Invoice invoice : invoices.duePublic()) {
            if (PowerBilling.ref(invoice).map(r -> r.supplierId().equals(id)).orElse(false)
                    && invoices.cancel(invoice.id(), now)) {
                cancelled++;
                cancelledTotal = saturatedAdd(cancelledTotal, invoice.amount());
                cancelledBy.merge(invoice.debtor(), 1, Integer::sum);
            }
        }
        // 2. Clients : détachés (jumelages du compteur et des disjoncteurs oubliés), courant coupé, prévenus.
        List<PowerClient> clients = registry.clients(id);
        for (PowerClient client : clients) {
            registry.removeClient(client.meterSerial());
            PowerPairing.refreshMeter(server, client.meterSerial());
            int bills = cancelledBy.getOrDefault(client.player(), 0);
            PowerPairing.tell(server, registry, client.player(), Component.translatable(
                    MESSAGE + "supplier_removed_client", supplier.name(), reason, client.subscriber(), bills));
        }
        for (Map.Entry<UUID, Integer> debtor : cancelledBy.entrySet()) {
            if (clients.stream().noneMatch(c -> c.player().equals(debtor.getKey()))) {
                PowerPairing.tell(server, registry, debtor.getKey(), Component.translatable(
                        MESSAGE + "supplier_removed_bills", supplier.name(), reason, debtor.getValue()));
            }
        }
        // 3. Caisse : retirée en entier, puis versée au fondateur (jamais au sol, jamais détruite).
        PowerSupplier current = registry.byId(id).orElse(supplier);
        EnumMap<Coin, Long> till = current.till();
        long value = CoinMath.valueOf(till);
        till.forEach((coin, count) -> {
            if (!registry.withdraw(id, coin, count)) {
                throw new IllegalStateException("caisse de " + supplier.name() + " : retrait impossible de " + count
                        + " " + coin);
            }
        });
        CommerceDelivery.Destination destination = CommerceDelivery.payToSafeFirst(server, current.founder(), value);
        // 4. Le fournisseur, et ce qui le touchait encore (détachés du jour, codes, relevés, jumelages).
        if (!registry.removeIfEmpty(id)) {
            LOGGER.error("Electricite : {} n'a pas pu etre retire du registre apres la suppression", supplier.name());
        }
        PowerPairing.tell(server, registry, current.founder(), Component.translatable(
                MESSAGE + "supplier_removed_founder", supplier.name(), reason, clients.size(), CoinMath.format(value),
                Component.translatable(MESSAGE + "dest." + destination.name().toLowerCase(Locale.ROOT))));
        EconomyLedger.get(server).noted(EconomyLedger.KIND_POWER_REMOVE, author, value,
                (author == null && sourceName != null ? "[" + sourceName + "] " : "") + supplier.name() + " : "
                + reason + " (" + clients.size() + " client(s), " + cancelled + " facture(s) annulee(s), caisse a "
                + current.founderName() + ")");
        LOGGER.warn("Electricite : fournisseur {} supprime ({}) : {} client(s) detache(s), {} facture(s) annulee(s), "
                + "caisse de {} cuivres a {} ({})", supplier.name(), reason, clients.size(), cancelled, value,
                current.founderName(), destination);
        return new Removal(clients.size(), cancelled, cancelledTotal, value, destination);
    }

    // ---------------------------------------------------------------------------------------------
    // Tableau de bord (/eco power, /eco stats)
    // ---------------------------------------------------------------------------------------------

    /**
     * Une ligne du tableau de bord.
     *
     * @param gapYesterday sortie du poste hier moins la somme des compteurs hier, en FE (négatif possible)
     * @param dueCount les factures d'électricité dues de ce fournisseur ({@code dueTotal} : leur total)
     */
    public record SupplierRow(PowerSupplier supplier, int clients, long gapYesterday, int dueCount, long dueTotal) {}

    /**
     * Le tableau de bord de l'électricité. Les totaux sont la somme exacte des lignes (de toutes les pages) ; les
     * factures d'électricité dues d'un fournisseur qui n'est plus au registre sont comptées à part.
     *
     * @param tillTotal la valeur des caisses de toutes les lignes
     * @param dueCount les factures dues de toutes les lignes ({@code dueTotal} : leur total)
     * @param orphanCount les factures d'électricité dues sans fournisseur au registre ({@code orphanTotal} : leur total)
     */
    public record Board(List<SupplierRow> rows, long tillTotal, int dueCount, long dueTotal, int orphanCount,
            long orphanTotal) {}

    /** Le tableau de bord, lu dans ces registres. */
    public static Board board(PowerSuppliers registry, InvoiceRegistry invoices) {
        Map<String, long[]> due = new HashMap<>();
        for (Invoice invoice : invoices.duePublic()) {
            Optional<PowerBilling.Ref> ref = PowerBilling.ref(invoice);
            if (ref.isEmpty()) {
                continue;
            }
            long[] line = due.computeIfAbsent(ref.get().supplierId(), k -> new long[2]);
            line[0]++;
            line[1] = saturatedAdd(line[1], invoice.amount());
        }
        List<SupplierRow> rows = new ArrayList<>();
        long tills = 0L;
        int dueCount = 0;
        long dueTotal = 0L;
        for (PowerSupplier supplier : registry.all()) {
            long[] line = due.remove(supplier.id());
            if (line == null) {
                line = new long[2];
            }
            rows.add(new SupplierRow(supplier, registry.clients(supplier.id()).size(),
                    supplier.outYesterday() - supplier.metersYesterday(), (int) line[0], line[1]));
            tills = saturatedAdd(tills, supplier.tillValue());
            dueCount += (int) line[0];
            dueTotal = saturatedAdd(dueTotal, line[1]);
        }
        int orphanCount = 0;
        long orphanTotal = 0L;
        for (long[] line : due.values()) {
            orphanCount += (int) line[0];
            orphanTotal = saturatedAdd(orphanTotal, line[1]);
        }
        return new Board(List.copyOf(rows), tills, dueCount, dueTotal, orphanCount, orphanTotal);
    }

    /** {@code /eco power} : la première page. */
    public static MutableComponent boardReport(MinecraftServer server, PowerSuppliers registry,
            InvoiceRegistry invoices) {
        return boardReport(server, registry, invoices, 1);
    }

    /**
     * {@code /eco power [page]} : les totaux de toutes les pages, puis {@value #PAGE_SIZE} fournisseurs (caisse, écart
     * d'hier, factures dues), puis les factures dues sans fournisseur s'il y en a. Une page hors limites ne montre que
     * les totaux et la page la plus haute.
     */
    public static MutableComponent boardReport(MinecraftServer server, PowerSuppliers registry,
            InvoiceRegistry invoices, int page) {
        Board board = board(registry, invoices);
        int pages = pages(board.rows().size());
        MutableComponent out = Component.translatable("command.oneforallsurvival.eco.power.title", board.rows().size(),
                CoinMath.format(board.tillTotal()), board.dueCount(), CoinMath.format(board.dueTotal()));
        if (page < 1 || page > pages) {
            out.append("\n").append(Component.translatable(KEY + "no_page", page, pages));
            return out;
        }
        out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.power.page", page, pages));
        if (board.rows().isEmpty()) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.power.none"));
        }
        for (SupplierRow row : board.rows().subList((page - 1) * PAGE_SIZE,
                Math.min(board.rows().size(), page * PAGE_SIZE))) {
            PowerSupplier s = row.supplier();
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.power.line", s.name(),
                    s.founderName(), row.clients(), CoinMath.format(s.tillValue()), signed(row.gapYesterday()),
                    row.dueCount(), CoinMath.format(row.dueTotal())));
        }
        if (board.orphanCount() > 0) {
            out.append("\n").append(Component.translatable("command.oneforallsurvival.eco.power.orphans",
                    board.orphanCount(), CoinMath.format(board.orphanTotal())));
        }
        return out;
    }

    private static String signed(long fe) {
        return (fe > 0L ? "+" : "") + fe;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
